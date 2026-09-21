package com.rick.site.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.contact.dto.ContactForm;
import com.rick.site.contact.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * 联系表单提交处理(TODO §1 询盘)。
 *
 * <p>处理 {@code POST /contact} 与 {@code POST /{locale}/contact},返回 JSON
 * {@code {success: true|false}}。前台模板用 fetch 提交并据 success 内联显示
 * 成功/失败横幅——纯静态发布站与动态站均可用(静态站经 Nginx 代理 POST 到后端,
 * 无需 flash/session;GET 联系页仍静态服务)。
 *
 * <p>CSRF:本路径在 SecurityConfig 中豁免(静态发布站无 session,无法注入 CSRF token;
 * 公开未认证表单,改由服务端校验 + 后续限频/Honeypot 防刷,见 TODO §1/§10)。
 *
 * @author Rick.Xu
 */
@Controller
public class SiteContactController {

    private final ContactService contactService;

    public SiteContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping(value = {"/contact", "/{locale:[a-z]{2}-[a-z]{2}}/contact"})
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> submit(@Valid ContactForm form, BindingResult binding) {
        if (binding.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of("success", false));
        }
        try {
            contactService.send(form);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (BizException e) {
            return ResponseEntity.ok(Map.of("success", false));
        }
    }
}
