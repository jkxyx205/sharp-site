package com.rick.site.page.entity;

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
 * 页面多语言内容(DATABASE.md §5 / TASK-0401)。
 *
 * <p>无 tenant_id,经 page_id 关联 {@link SitePage} 做租户隔离;
 * (page_id, language) 唯一。content 为富文本,保存前须经 {@code HtmlSanitizer} 清洗(CLAUDE.md §9)。
 * 继承 {@link BaseEntity}:审计列自动填充,逻辑删除。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "site_page_i18n", comment = "页面多语言内容")
public class SitePageI18n extends BaseEntity<Long> {

    @Column(nullable = false, comment = "页面ID")
    Long pageId;

    @NotBlank
    @Length(max = 20)
    @Column(nullable = false, comment = "语言,如 zh-CN / en-US")
    String language;

    @Length(max = 500)
    @Column(comment = "标题")
    String title;

    @Column(comment = "富文本正文(清洗后)")
    String content;

    @Length(max = 1000)
    @Column(comment = "封面图URL")
    String cover;
}
