package com.rick.site.tenant.dao;

import com.rick.db.repository.EntityCodeDAOImpl;
import com.rick.site.tenant.entity.Tenant;
import org.springframework.stereotype.Repository;

/**
 * 租户 DAO(sharp-database 注解驱动,提供 selectByCode 等 code 查询能力)。
 */
@Repository
public class TenantDAO extends EntityCodeDAOImpl<Tenant, Long> {
}
