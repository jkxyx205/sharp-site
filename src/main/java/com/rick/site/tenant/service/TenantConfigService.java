package com.rick.site.tenant.service;

import com.rick.common.http.exception.BizException;
import com.rick.db.plugin.BaseServiceImpl;
import com.rick.site.i18n.service.I18nService;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.dao.TenantConfigDAO;
import com.rick.site.tenant.dao.TenantConfigI18nDAO;
import com.rick.site.tenant.dto.TenantConfigView;
import com.rick.site.tenant.entity.TenantConfig;
import com.rick.site.tenant.entity.TenantConfigI18n;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 企业固定信息服务(TASK-0103)。
 *
 * <p>base 行每租户一行(部分唯一索引 uk_tenant_config),save 为按上下文租户的幂等 upsert。
 * tenant_id 由框架从 TenantContext 注入(防伪造,CLAUDE.md §4):查询自动追加 tenant_id 过滤,
 * 写入按上下文值注入;业务层不再显式传 tenantId,也不信任实体上携带的 tenantId。
 * 审计列由框架自动填充,返回前重新载入以保证 baseEntityInfo 与库一致。
 *
 * <p>company_name / company_name_short / address / copyright 按语种维护(见
 * {@link TenantConfigI18n}),按 (tenant_config_id, language) 幂等 upsert;展示按当前语种取,
 * 缺失回退租户默认语种(经 {@link I18nService#resolve})。联系方式共享于 base 行。
 */
@Service
@Validated
public class TenantConfigService extends BaseServiceImpl<TenantConfigDAO, TenantConfig, Long> {

    private final TenantConfigI18nDAO i18nDAO;
    private final I18nService i18nService;

    public TenantConfigService(TenantConfigDAO baseDAO, TenantConfigI18nDAO i18nDAO,
                               I18nService i18nService) {
        super(baseDAO);
        this.i18nDAO = i18nDAO;
        this.i18nService = i18nService;
    }

    /** 加载当前上下文租户的企业信息(tenant_id 由 DatabaseConfig 统一追加)。 */
    public Optional<TenantConfig> findByTenant() {
        List<TenantConfig> found = baseDAO.select("1=1", Map.of());
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /**
     * 保存企业信息 base 行:该(上下文)租户已有配置则全列更新(保留 id / tenantId / 审计列),
     * 否则新增。返回从库中重新载入的实体,baseEntityInfo 与库一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public TenantConfig save(TenantConfig config) {
        Long tenantId = TenantContext.requireTenantId();
        config.setTenantId(tenantId);
        Optional<TenantConfig> existing = findByTenant();
        if (existing.isPresent()) {
            config.setId(existing.get().getId());
        } else {
            config.setId(null);
        }
        baseDAO.insertOrUpdate(config);
        return findByTenant().orElseThrow();
    }

    /**
     * 保存某语种 i18n 行(company_name 等四字段)。按 (tenant_config_id, language) 幂等 upsert。
     * 校验归属:跨租户查不到 base 行即抛 {@link BizException}。
     */
    @Transactional(rollbackFor = Exception.class)
    public TenantConfigI18n saveI18n(Long configId, TenantConfigI18n i18n) {
        requireOwned(configId);
        i18n.setTenantConfigId(configId);
        findByLanguage(configId, i18n.getLanguage()).ifPresent(existing -> i18n.setId(existing.getId()));
        return i18nDAO.insertOrUpdate(i18n);
    }

    /** 某 base 行的全部语种 i18n(language → row),用于后台表单按语种取值。 */
    public Map<String, TenantConfigI18n> loadI18nMap(Long configId) {
        Map<String, TenantConfigI18n> map = new LinkedHashMap<>();
        for (TenantConfigI18n row : i18nDAO.select("tenant_config_id = :configId", Map.of("configId", configId))) {
            map.put(row.getLanguage(), row);
        }
        return map;
    }

    Optional<TenantConfigI18n> findByLanguage(Long configId, String language) {
        List<TenantConfigI18n> found = i18nDAO.select(
                "tenant_config_id = :configId AND language = :language",
                Map.of("configId", configId, "language", language));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /**
     * 前台/静态发布解析:取 base 行 + 按当前语种 i18n(缺失回退默认语种,再缺失为 null),
     * 合并为 {@link TenantConfigView}。无 base 行时返回 empty。
     */
    public Optional<TenantConfigView> resolveForDisplay(String language, String defaultLanguage) {
        Optional<TenantConfig> base = findByTenant();
        if (base.isEmpty()) {
            return Optional.empty();
        }
        Map<String, TenantConfigI18n> byLanguage = loadI18nMap(base.get().getId());
        Optional<TenantConfigI18n> i18n = i18nService.resolve(byLanguage, language, defaultLanguage);
        return Optional.of(TenantConfigView.from(base.get(), i18n.orElse(null)));
    }

    private TenantConfig requireOwned(Long configId) {
        // selectById 已按 TenantContext 隔离(跨租户查不到),无需再手动比对 tenantId
        return baseDAO.selectById(configId)
                .orElseThrow(() -> new BizException("企业固定信息不存在: id=" + configId));
    }
}
