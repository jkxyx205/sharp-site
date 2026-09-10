package com.rick.site.product.entity;

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
 * 产品多语言(DATABASE.md §11 / TASK-0603 / TASK-0605 SEO)。
 *
 * <p>经 product_id 关联租户;(product_id, language) 唯一。
 * content 为富文本,保存前经 {@code HtmlSanitizer} 清洗(CLAUDE.md §9)。
 * specification_json 存规格 JSON 字符串;seo_title / seo_description 为产品 SEO 字段。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "product_i18n", comment = "产品多语言")
public class ProductI18n extends BaseEntity<Long> {

    @Column(nullable = false, comment = "产品ID")
    Long productId;

    @NotBlank
    @Length(max = 20)
    @Column(nullable = false, comment = "语言")
    String language;

    @NotBlank
    @Length(max = 500)
    @Column(nullable = false, comment = "产品名称")
    String name;

    @Length(max = 1000)
    @Column(comment = "副标题")
    String subtitle;

    @Column(comment = "简介")
    String description;

    @Column(comment = "富文本正文(清洗后)")
    String content;

    @Column(value = "specification_json", comment = "规格 JSON 字符串")
    String specificationJson;

    @Length(max = 500)
    @Column(comment = "SEO 标题")
    String seoTitle;

    @Length(max = 1000)
    @Column(comment = "SEO 描述")
    String seoDescription;
}
