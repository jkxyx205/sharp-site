package com.rick.site.catalog.entity;

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
 * 分类多语言(DATABASE.md §9),经 category_id 关联租户;(category_id, language) 唯一。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "category_i18n", comment = "分类多语言")
public class CategoryI18n extends BaseEntity<Long> {

    @Column(nullable = false, comment = "分类ID")
    Long categoryId;

    @NotBlank
    @Length(max = 20)
    @Column(nullable = false, comment = "语言")
    String language;

    @NotBlank
    @Length(max = 500)
    @Column(nullable = false, comment = "名称")
    String name;

    @Column(comment = "描述")
    String description;
}
