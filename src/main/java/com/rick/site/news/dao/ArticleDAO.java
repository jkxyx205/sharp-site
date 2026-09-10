package com.rick.site.news.dao;

import com.rick.db.repository.EntityDAOImpl;
import com.rick.site.news.entity.Article;
import org.springframework.stereotype.Repository;

/** 文章 DAO。 */
@Repository
public class ArticleDAO extends EntityDAOImpl<Article, Long> {
}
