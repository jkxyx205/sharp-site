package com.rick.site.tenant.service;

import com.rick.db.plugin.BaseServiceImpl;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.dao.TenantConfigDAO;
import com.rick.site.tenant.entity.TenantConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 企业固定信息服务(TASK-0103)。
 *
 * <p>每租户一行(部分唯一索引 uk_tenant_config),save 为按上下文租户的幂等 upsert。
 * tenant_id 由框架从 TenantContext 注入(防伪造,CLAUDE.md §4):查询自动追加 tenant_id 过滤,
 * 写入按上下文值注入;业务层不再显式传 tenantId,也不信任实体上携带的 tenantId。
 * 审计列由框架自动填充,返回前重新载入以保证 baseEntityInfo 与库一致。
 */
@Service
@Validated
public class TenantConfigService extends BaseServiceImpl<TenantConfigDAO, TenantConfig, Long> {

    public TenantConfigService(TenantConfigDAO baseDAO) {
        super(baseDAO);
    }

    /** 加载当前上下文租户的企业信息(tenant_id 由 DatabaseConfig 统一追加)。 */
    public Optional<TenantConfig> findByTenant() {
        List<TenantConfig> found = baseDAO.select("1=1", Map.of());
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /**
     * 保存企业信息:该(上下文)租户已有配置则全列更新(保留 id / tenantId / 审计列),
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
}
