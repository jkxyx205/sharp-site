package com.rick.site.tenant.dao;

import com.rick.db.repository.EntityDAOImpl;
import com.rick.site.tenant.entity.TenantDomain;
import org.springframework.stereotype.Repository;

/**
 * 租户域名 DAO。
 */
@Repository
public class TenantDomainDAO extends EntityDAOImpl<TenantDomain, Long> {
}
