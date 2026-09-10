package com.rick.site.home.entity;

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
 * 首页区块多语言内容(DATABASE.md §7 / TASK-0501)。
 *
 * <p>无 tenant_id,经 section_id 关联 {@link HomeSection} 做租户隔离;(section_id, language) 唯一。
 * content 为富文本,保存前经 {@code HtmlSanitizer} 清洗(CLAUDE.md §9)。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "home_section_i18n", comment = "首页区块多语言内容")
public class HomeSectionI18n extends BaseEntity<Long> {

    @Column(nullable = false, comment = "区块ID")
    Long sectionId;

    @NotBlank
    @Length(max = 20)
    @Column(nullable = false, comment = "语言,如 zh-CN / en-US")
    String language;

    @Length(max = 500)
    @Column(comment = "标题")
    String title;

    @Length(max = 1000)
    @Column(comment = "副标题")
    String subtitle;

    @Column(comment = "富文本内容(清洗后)")
    String content;
}
