package com.rick.site.news.entity;

import com.rick.db.repository.Column;
import com.rick.db.repository.Table;
import com.rick.site.common.model.TenantBaseEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDateTime;

/**
 * 新闻/文章(DATABASE.md §13 / TASK-0702)。
 *
 * <p>每租户按 slug 唯一;多语言内容存于 {@link ArticleI18n}。
 * 继承 {@link TenantBaseEntity}:tenant_id 框架注入,审计列自动填充,逻辑删除。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "article", comment = "新闻")
public class Article extends TenantBaseEntity<Long> {

    @Column(comment = "分类ID(type=NEWS)")
    Long categoryId;

    @NotBlank
    @Length(max = 200)
    @Column(nullable = false, comment = "slug(URL 标识,租户内唯一)")
    String slug;

    @Length(max = 1000)
    @Column(comment = "封面图URL")
    String cover;

    @Length(max = 200)
    @Column(comment = "作者")
    String author;

    @Column(comment = "发布时间")
    LocalDateTime publishTime;

    @Column(nullable = false, comment = "状态:1发布,0草稿/下架")
    Short status;

    @Column(nullable = false, comment = "排序,升序")
    Integer sort;
}
