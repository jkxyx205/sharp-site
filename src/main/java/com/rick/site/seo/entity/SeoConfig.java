package com.rick.site.seo.entity;

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

/**
 * SEO 元数据(DATABASE.md §16 / TASK-1001)。
 *
 * <p>按 (租户, page_type, page_id, language) 唯一;page_id 可空(首页/列表页)。
 * 继承 {@link TenantBaseEntity}:tenant_id 框架注入,审计列自动填充,逻辑删除。
 * 字段:title/description/keywords/canonical/robots/og_title/og_description/og_image。
 * 供前台 head 片段与静态生成器渲染 meta(CLAUDE.md §6 数据与模板分离)。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "seo_config", comment = "SEO 元数据")
public class SeoConfig extends TenantBaseEntity<Long> {

    @NotBlank
    @Length(max = 50)
    @Column(nullable = false, comment = "页面类型:home/page/product/article/products_list/news_list")
    String pageType;

    @Column(comment = "页面实体ID;首页/列表页为空")
    Long pageId;

    @NotBlank
    @Length(max = 20)
    @Column(nullable = false, comment = "语言,如 en-US/zh-CN")
    String language;

    @Length(max = 500)
    @Column(comment = "SEO 标题")
    String title;

    @Length(max = 2000)
    @Column(comment = "SEO 描述")
    String description;

    @Length(max = 2000)
    @Column(comment = "SEO 关键词")
    String keywords;

    @Length(max = 2000)
    @Column(comment = "canonical 绝对/相对链接")
    String canonical;

    @Length(max = 100)
    @Column(comment = "robots 指令,如 index, follow")
    String robots;

    @Length(max = 500)
    @Column(value = "og_title", comment = "Open Graph 标题")
    String ogTitle;

    @Length(max = 2000)
    @Column(value = "og_description", comment = "Open Graph 描述")
    String ogDescription;

    @Length(max = 2000)
    @Column(value = "og_image", comment = "Open Graph 图片URL")
    String ogImage;
}
