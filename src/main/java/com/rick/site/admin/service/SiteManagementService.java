package com.rick.site.admin.service;

import com.rick.common.http.exception.BizException;
import com.rick.site.common.context.TenantQueryBypass;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.entity.TenantDomain;
import com.rick.site.tenant.service.TenantDomainService;
import com.rick.site.tenant.service.TenantService;
import com.rick.site.theme.service.ThemeManifestResolver;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 后台「网站管理」:跨租户的站点开通与拆除。
 *
 * <p><b>开通</b>({@link #create}):先建 tenant(无 tenant_id 列,不受上下文影响),
 * 再临时把 {@link TenantContext} 切到新租户,调用 {@link TenantDomainService#add}
 * 与 {@link AdminUserService#create}(二者均依赖上下文注入 tenant_id),完成后还原上下文。
 * 域名全局唯一校验用 {@link TenantQueryBypass} 跨租户旁路。
 *
 * <p><b>拆除</b>({@link #delete}):用 {@link NamedParameterJdbcTemplate} 原始 SQL
 * 物理删除——绕过 {@code SiteDatabaseConfig} 的 tenant_id 过滤与逻辑删除,
 * 按 i18n 子表(经父表子查询)→ 租户作用域表 → tenant 自身 的顺序清除,
 * 并禁止删除当前登录站点(防自删)。
 *
 * @author Rick.Xu
 */
@Service
public class SiteManagementService {

    /** 5 张 i18n 子表(无 tenant_id,经父表子查询删)→ 父表名。 */
    private static final List<String[]> I18N_CHILD_PARENT = List.of(
            new String[]{"article_i18n", "article", "article_id"},
            new String[]{"product_i18n", "product", "product_id"},
            new String[]{"category_i18n", "category", "category_id"},
            new String[]{"video_i18n", "video", "video_id"},
            new String[]{"tenant_config_i18n", "tenant_config", "tenant_config_id"});

    /** 10 张租户作用域表(有 tenant_id 列),物理删除。 */
    private static final List<String> TENANT_TABLES = List.of(
            "article", "product", "video", "media", "seo_config",
            "category", "publish_record", "tenant_config", "admin_user", "tenant_domain");

    private final TenantService tenantService;
    private final TenantDomainService tenantDomainService;
    private final AdminUserService adminUserService;
    private final ThemeManifestResolver manifestResolver;
    private final NamedParameterJdbcTemplate jdbc;

    public SiteManagementService(TenantService tenantService, TenantDomainService tenantDomainService,
                                 AdminUserService adminUserService, ThemeManifestResolver manifestResolver,
                                 NamedParameterJdbcTemplate jdbc) {
        this.tenantService = tenantService;
        this.tenantDomainService = tenantDomainService;
        this.adminUserService = adminUserService;
        this.manifestResolver = manifestResolver;
        this.jdbc = jdbc;
    }

    /** 全部租户列表(tenant 表无 tenant_id,select 不受限)。 */
    public List<Tenant> list() {
        return tenantService.selectAll();
    }

    /** 可选主题(themeId 下拉项)。 */
    public List<String> themeIds() {
        return manifestResolver.listThemeIds();
    }

    /**
     * 开通站点:建 tenant → 切上下文为新租户 → add 域名 + 建管理员 → 还原上下文。
     * 整体事务,任一步失败回滚(已切的上下文在 finally 还原)。
     */
    @Transactional(rollbackFor = Exception.class)
    public Tenant create(SiteCreateForm form) {
        String name = form.name() == null ? "" : form.name().trim();
        String code = form.code() == null ? "" : form.code().trim();
        String username = form.username() == null ? "" : form.username().trim();
        String password = form.password();
        String domain = form.domain() == null ? "" : form.domain().trim().toLowerCase(Locale.ROOT);
        String themeId = form.themeId();

        if (name.isBlank() || code.isBlank() || username.isBlank()
                || password == null || password.isBlank() || domain.isBlank()) {
            throw new BizException("站点编码/名称/域名/用户名/密码均不能为空");
        }
        if (!manifestResolver.listThemeIds().contains(themeId)) {
            throw new BizException("未知主题: " + themeId);
        }
        Optional<TenantDomain> existing = TenantQueryBypass.supply(
                () -> tenantDomainService.findByDomain(domain));
        if (existing.isPresent()) {
            throw new BizException("域名已存在: " + domain);
        }

        Tenant tenant = Tenant.builder()
                .code(code)
                .name(name)
                .themeId(themeId)
                .status((short) 1)
                .build();
        Tenant saved = tenantService.save(tenant);

        // 临时把上下文切到新租户:domain/admin_user 的 tenant_id 由框架据此注入
        Tenant previous = TenantContext.get().orElse(null);
        TenantContext.set(saved);
        try {
            tenantDomainService.add(domain, true);
            adminUserService.create(username, password);
        } finally {
            if (previous != null) {
                TenantContext.set(previous);
            } else {
                TenantContext.clear();
            }
        }
        return saved;
    }

    /**
     * 拆除站点:物理删除该 tenant 全部数据。顺序:i18n 子表(经父表子查询)→
     * 租户作用域表 → tenant 自身。禁止删除当前登录站点(防自删)。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long tenantId) {
        if (tenantId.equals(TenantContext.require().getId())) {
            throw new BizException("不能删除当前登录的站点");
        }
        tenantService.findById(tenantId)
                .orElseThrow(() -> new BizException("站点不存在: id=" + tenantId));

        Map<String, Object> p = Map.of("tid", tenantId);
        // 1) i18n 子表(无 tenant_id,经父表子查询删,父表此刻仍在)
        for (String[] cp : I18N_CHILD_PARENT) {
            jdbc.update("DELETE FROM " + cp[0] + " WHERE " + cp[2]
                    + " IN (SELECT id FROM " + cp[1] + " WHERE tenant_id = :tid)", p);
        }
        // 2) 租户作用域表(物理删,绕过 TableDAO 的 tenant 过滤与逻辑删除)
        for (String t : TENANT_TABLES) {
            jdbc.update("DELETE FROM " + t + " WHERE tenant_id = :tid", p);
        }
        // 3) tenant 自身
        jdbc.update("DELETE FROM tenant WHERE id = :tid", p);
    }

    /** 站点开通表单数据。 */
    public record SiteCreateForm(String code, String name, String themeId,
                                 String domain, String username, String password) {
    }
}
