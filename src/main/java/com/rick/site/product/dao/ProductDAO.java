package com.rick.site.product.dao;

import com.rick.db.repository.EntityDAOImpl;
import com.rick.site.product.entity.Product;
import org.springframework.stereotype.Repository;

/** 产品 DAO。 */
@Repository
public class ProductDAO extends EntityDAOImpl<Product, Long> {
}
