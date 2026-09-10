package com.rick.site.news.entity;

import com.rick.db.repository.Column;
import com.rick.db.repository.Table;
import com.rick.db.repository.model.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.Length;

/**
 * 新闻多语言(DATABASE.md §14 / TASK-0703 / TASK-0705 SEO)。
 *
 * <p>经 article_id 关联租户;(article_id, language) 唯一。
 * content 为富文本,保存前经 {@code HtmlSanitizer} 清洗(CLAUDE.md §9)。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "article_i18n", comment = "新闻多语言")
public class ArticleI18n extends BaseEntity<Long> {

    @Column(nullable = false, comment = "文章ID")
    Long articleId;

    @NotBlank
    @Length(max = 20)
    @Column(nullable = false, comment = "语言")
    String language;

    @NotBlank
    @Length(max = 500)
    @Column(nullable = false, comment = "标题")
    String title;

    @Column(comment = "摘要")
    String summary;

    @Column(comment = "富文本正文(清洗后)")
    String content;

    @Length(max = 500)
    @Column(comment = "SEO 标题")
    String seoTitle;

    @Length(max = 1000)
    @Column(comment = "SEO 描述")
    String seoDescription;
}
