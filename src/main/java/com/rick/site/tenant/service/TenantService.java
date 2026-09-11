package com.rick.site.tenant.service;

import com.rick.common.http.exception.BizException;
import com.rick.db.plugin.BaseCodeServiceImpl;
import com.rick.site.tenant.dao.TenantDAO;
import com.rick.site.tenant.entity.Tenant;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Optional;

/**
 * 租户服务(TASK-0101)。
 *
 * <p>租户是平台级数据,不属于任何租户自身,因此本服务不做 tenant_id 过滤;
 * 业务数据的租户隔离从 TenantDomain / TenantConfig 等下游表开始。
 *
 * <p>审计列(create_by/create_time/update_by/update_time/is_deleted)由
 * sharp-database 框架自动填充,本服务不再手工维护时间戳。
 *
 * <p>继承 BaseCodeServiceImpl 获得 selectById / selectByCode / selectAll 等
 * 整套 EntityDAO 委托方法;事务统一加在本层。
 */
@Service
@Validated
public class TenantService extends BaseCodeServiceImpl<TenantDAO, Tenant, Long> {

    /** 与 DDL 默认值保持一致 */
    static final short STATUS_ENABLED = 1;

    public TenantService(TenantDAO baseDAO) {
        super(baseDAO);
    }

    /**
     * 保存租户:id 为 null 走新增(code 必须唯一),否则走全列更新。
     * 新增时补默认值(status);语言配置由主题 theme.json 决定,租户不再持有。
     * create_time/update_time 由框架填充。
     */
    @Transactional(rollbackFor = Exception.class)
    public Tenant save(Tenant tenant) {
        if (tenant.getId() == null) {
            if (tenant.getStatus() == null) {
                tenant.setStatus(STATUS_ENABLED);
            }
            requireCodeAbsent(tenant.getCode(), null);
        } else {
            Tenant existing = baseDAO.selectById(tenant.getId())
                    .orElseThrow(() -> new BizException("租户不存在: id=" + tenant.getId()));
            if (StringUtils.isNotBlank(tenant.getCode()) && !tenant.getCode().equals(existing.getCode())) {
                requireCodeAbsent(tenant.getCode(), tenant.getId());
            } else {
                tenant.setCode(existing.getCode());
            }
        }
        return baseDAO.insertOrUpdate(tenant);
    }

    public Optional<Tenant> findById(Long id) {
        return baseDAO.selectById(id);
    }

    public Optional<Tenant> findByCode(String code) {
        return baseDAO.selectByCode(code);
    }

    private void requireCodeAbsent(String code, Long excludeId) {
        baseDAO.selectByCode(code).ifPresent(existing -> {
            if (excludeId == null || !existing.getId().equals(excludeId)) {
                throw new BizException("租户编码已存在: " + code);
            }
        });
    }
}
