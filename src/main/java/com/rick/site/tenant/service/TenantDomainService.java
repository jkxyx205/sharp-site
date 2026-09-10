package com.rick.site.tenant.service;

import com.rick.common.http.exception.BizException;
import com.rick.db.plugin.BaseServiceImpl;
import com.rick.site.common.context.TenantQueryBypass;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.dao.TenantDomainDAO;
import com.rick.site.tenant.entity.TenantDomain;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 租户域名服务(TASK-0102)。
 *
 * <p>业务规则(DATABASE.md §2):
 * <ul>
 *   <li>domain 全局唯一(部分唯一索引 uk_domain + 服务预检友好报错;逻辑删除后可重用)</li>
 *   <li>一个租户可有多个域名</li>
 *   <li>一个租户至多一个 primary 域名(事务内先清后设)</li>
 * </ul>
 *
 * <p>审计列由框架自动填充;tenant_id 由框架从 TenantContext 注入(上下文为空时用实体值)。
 * tenant_id 查询过滤由 SiteDatabaseConfig 统一追加;业务层不再显式带 tenant_id。
 * 所有写操作都校验域名归属(selectById 已按上下文租户隔离),防止跨租户修改。
 * {@link #findByDomain} 为跨租户全局唯一查询,经 {@link TenantQueryBypass} 跳过 tenant_id 过滤。
 */
@Service
@Validated
public class TenantDomainService extends BaseServiceImpl<TenantDomainDAO, TenantDomain, Long> {

    /** 主机名格式:label 1-63 字符,字母数字开头结尾,可含中划线;统一小写后校验 */
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*$");

    private static final int DOMAIN_MAX_LENGTH = 255;

    public TenantDomainService(TenantDomainDAO baseDAO) {
        super(baseDAO);
    }

    /** 当前上下文租户的域名(tenant_id 由 DatabaseConfig 统一追加)。 */
    public List<TenantDomain> listByTenant() {
        return baseDAO.select("1=1 ORDER BY is_primary DESC, id", Map.of());
    }

    /**
     * 跨租户全局查询:域名全局唯一,不可按 tenant_id 过滤。
     * 经 TenantQueryBypass 跳过租户过滤(逻辑删除 is_deleted 过滤仍保留),
     * 故无论上下文是否设置均能正确判定域名占用。
     */
    public Optional<TenantDomain> findByDomain(String domain) {
        String normalized = normalizeDomain(domain);
        List<TenantDomain> found = TenantQueryBypass.supply(() ->
                baseDAO.select("domain = :domain", Map.of("domain", normalized)));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /**
     * 添加域名。primary=true 时同事务内清除该(上下文)租户原有 primary。
     */
    @Transactional(rollbackFor = Exception.class)
    public TenantDomain add(String domain, boolean primary) {
        Long tenantId = TenantContext.requireTenantId();
        String normalized = normalizeDomain(domain);
        findByDomain(normalized).ifPresent(existing -> {
            throw new BizException("域名已被占用: " + normalized);
        });
        if (primary) {
            clearPrimary();
        }
        TenantDomain entity = TenantDomain.builder()
                .tenantId(tenantId)
                .domain(normalized)
                .isPrimary(primary ? (short) 1 : (short) 0)
                .status((short) 1)
                .build();
        return baseDAO.insert(entity);
    }

    /**
     * 设置主域名:先清除该(上下文)租户全部 primary,再置当前域名。
     */
    @Transactional(rollbackFor = Exception.class)
    public TenantDomain setPrimary(Long domainId) {
        TenantDomain owned = requireOwned(domainId);
        clearPrimary();
        owned.setIsPrimary((short) 1);
        return baseDAO.update(owned);
    }

    @Transactional(rollbackFor = Exception.class)
    public TenantDomain updateStatus(Long domainId, short status) {
        TenantDomain owned = requireOwned(domainId);
        owned.setStatus(status);
        return baseDAO.update(owned);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long domainId) {
        requireOwned(domainId);
        baseDAO.deleteById(domainId);
    }

    /** 域名规范化:trim + 小写 + 格式校验 */
    String normalizeDomain(String domain) {
        if (domain == null) {
            throw new BizException("域名不能为空");
        }
        String normalized = domain.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > DOMAIN_MAX_LENGTH
                || !DOMAIN_PATTERN.matcher(normalized).matches()) {
            throw new BizException("非法域名格式: " + domain);
        }
        return normalized;
    }

    /**
     * 清除(上下文)租户现有主域名(逻辑删除感知:仅清未删除的 primary)。
     * tenant_id 过滤由 SiteDatabaseConfig 的 update override 自动追加(上下文租户)。
     * 注意 sharp-database 部分更新约定:columns 传列名列表(框架拼 "col = :propertyName"),
     * update_time 列由框架注入 :baseEntityInfo.updateTime 参数并自动刷新。
     */
    private void clearPrimary() {
        baseDAO.update("is_primary, update_time",
                "is_primary = 1",
                Map.of("isPrimary", (short) 0));
    }

    /**
     * 加载域名;selectById 已按 TenantContext 隔离(跨租户查不到,视同不存在),
     * 无需再手动比对 tenantId,避免泄露其他租户数据。
     */
    private TenantDomain requireOwned(Long domainId) {
        return baseDAO.selectById(domainId)
                .orElseThrow(() -> new BizException("域名不存在: id=" + domainId));
    }
}
