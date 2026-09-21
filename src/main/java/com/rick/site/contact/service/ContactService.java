package com.rick.site.contact.service;

import com.rick.common.http.exception.BizException;
import com.rick.mail.core.MailHandler;
import com.rick.mail.core.mail.Email;
import com.rick.site.contact.dto.ContactForm;
import com.rick.site.tenant.entity.TenantConfig;
import com.rick.site.tenant.service.TenantConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

/**
 * 联系表单邮件发送(TODO §1 询盘的最小可用形态)。
 *
 * <p>访客提交 {@link ContactForm} → 经 sharp-mail {@link MailHandler} 发往当前租户
 * {@code tenant_config.email}。发件人取 {@code spring.mail.username}(SMTP 授权账号):
 * 163 等 ISP 要求 from 与认证账号一致,伪造访客邮箱为 from 会被拒收。
 * 访客邮箱写入正文(供企业回复),sharp-mail 的 {@code Email} 无 replyTo,故不设回复头。
 *
 * <p>失败时不暴露内部原因给访客:抛 {@link BizException},由 Controller 转 flash,
 * 模板显示本地化失败文案;详细异常记服务端日志。
 *
 * @author Rick.Xu
 */
@Service
public class ContactService {

    private static final Logger log = LoggerFactory.getLogger(ContactService.class);

    private final MailHandler mailHandler;
    private final TenantConfigService tenantConfigService;
    private final MailProperties mailProperties;

    public ContactService(MailHandler mailHandler, TenantConfigService tenantConfigService,
                          MailProperties mailProperties) {
        this.mailHandler = mailHandler;
        this.tenantConfigService = tenantConfigService;
        this.mailProperties = mailProperties;
    }

    /**
     * 发送联系表单邮件到当前租户 tenant_config.email。
     *
     * @throws BizException 租户未配置 email,或 SMTP 发送失败
     */
    public void send(ContactForm form) {
        TenantConfig config = tenantConfigService.findByTenant()
                .orElseThrow(() -> new BizException("企业联系邮箱未配置"));
        String to = config.getEmail();
        if (to == null || to.isBlank()) {
            throw new BizException("企业联系邮箱未配置,无法接收询盘");
        }
        String from = mailProperties.getUsername();
        // EmailBuilder.from(address, personal):第一参数为邮箱地址,第二为显示名
        // (MailHandlerImpl 内部 new InternetAddress(addr, personal));顺序反了会触发
        // AddressException: 空格/控制字符出现在 local-part。
        Email email = Email.builder()
                .from(from, "Website Inquiry")
                .to(to)
                .subject("Website Inquiry - " + form.getName())
                .plainText(buildBody(form))
                .build();
        try {
            mailHandler.send(email);
            log.info("联系表单邮件已发送: to={}, from={}, visitor={}", to, from, form.getEmail());
        } catch (MailException e) {
            log.error("联系表单邮件发送失败: to={}, visitor={}", to, form.getEmail(), e);
            throw new BizException("邮件发送失败,请稍后重试");
        }
    }

    private String buildBody(ContactForm form) {
        StringBuilder sb = new StringBuilder()
                .append("Name: ").append(form.getName()).append('\n')
                .append("Email: ").append(form.getEmail()).append('\n');
        if (form.getTopic() != null && !form.getTopic().isBlank()) {
            sb.append("Topic: ").append(form.getTopic()).append('\n');
        }
        sb.append('\n').append("Message:").append('\n').append(form.getMessage());
        return sb.toString();
    }
}
