package com.rick.site.tenant.dao;

import com.rick.db.repository.EntityDAOImpl;
import com.rick.site.tenant.entity.TenantConfig;
import org.springframework.stereotype.Repository;

/**
 * 企业固定信息 DAO。
 */
@Repository
public class TenantConfigDAO extends EntityDAOImpl<TenantConfig, Long> {
}
