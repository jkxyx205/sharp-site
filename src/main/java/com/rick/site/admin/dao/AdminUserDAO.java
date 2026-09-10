package com.rick.site.admin.dao;

import com.rick.db.repository.EntityDAOImpl;
import com.rick.site.admin.entity.AdminUser;
import org.springframework.stereotype.Repository;

/** 管理员用户 DAO。 */
@Repository
public class AdminUserDAO extends EntityDAOImpl<AdminUser, Long> {
}
