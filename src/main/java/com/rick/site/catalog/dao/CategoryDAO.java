package com.rick.site.catalog.dao;

import com.rick.db.repository.EntityDAOImpl;
import com.rick.site.catalog.entity.Category;
import org.springframework.stereotype.Repository;

/** 分类 DAO。 */
@Repository
public class CategoryDAO extends EntityDAOImpl<Category, Long> {
}
