package com.rick.site.tenant.service;

import com.rick.common.http.exception.BizException;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.entity.TenantDomain;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 租户解析(TASK-0104):HTTP Host → Tenant。
 *
 * <pre>
 * Host(可含端口)
 *  ↓ 剥离端口、规范化(小写)
 * tenant_domain(启用)
 *  ↓ tenant_id
 * tenant(启用)
 * </pre>
 *
 * <p>停用的域名或租户不参与解析(返回 empty,由上层决定 404/提示)。
 * 每请求两次唯一索引/PK 查询,暂不加缓存;如需优化按 ARCHITECTURE.md §11
 * 对 Tenant/Domain 做本地缓存并配套 CRUD 失效。
 */
@Service
public class TenantResolver {

    static final short STATUS_ENABLED = 1;

    private final TenantDomainService tenantDomainService;
    private final TenantService tenantService;

    public TenantResolver(TenantDomainService tenantDomainService, TenantService tenantService) {
        this.tenantDomainService = tenantDomainService;
        this.tenantService = tenantService;
    }

    public Optional<Tenant> resolveByHost(String host) {
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        String hostname = stripPort(host.trim());
        try {
            return tenantDomainService.findByDomain(hostname)
                    .filter(domain -> domain.getStatus() == STATUS_ENABLED)
                    .map(TenantDomain::getTenantId)
                    .flatMap(tenantService::findById)
                    .filter(tenant -> tenant.getStatus() == STATUS_ENABLED);
        } catch (BizException e) {
            // Host 头是任意外部输入:非法域名格式 = 无法解析,不是业务错误
            return Optional.empty();
        }
    }

    /** 剥离端口后缀(仅末尾的 :数字),其余交给域名规范化校验 */
    private String stripPort(String host) {
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.chars().skip(colon + 1).allMatch(Character::isDigit)
                && colon < host.length() - 1) {
            return host.substring(0, colon);
        }
        return host;
    }
}
