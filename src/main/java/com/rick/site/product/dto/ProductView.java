package com.rick.site.product.dto;

import com.rick.site.product.entity.Product;
import com.rick.site.product.entity.ProductI18n;
import com.rick.site.product.service.ProductService.ResolvedProduct;

/**
 * 产品前台视图(合并 Product + 命中 i18n),供 Thymeleaf 单对象属性访问。
 *
 * <p>模板用 {@code ${product.name}} / {@code ${product.cover}} / {@code ${product.content}} 等;
 * record 访问器经 JavaBeans 内省被 SpEL 识别为属性。
 *
 * @author Rick.Xu
 */
public record ProductView(String slug, String cover, String name, String subtitle,
                          String description, String content, String specificationJson,
                          String seoTitle, String seoDescription) {

    /** 从解析结果构建;i18n 缺失时 name 回退为 slug,其余为空串。 */
    public static ProductView from(ResolvedProduct resolved) {
        Product product = resolved.product();
        ProductI18n i18n = resolved.i18n();
        return new ProductView(
                product.getSlug(),
                product.getCover(),
                i18n != null && i18n.getName() != null ? i18n.getName() : product.getSlug(),
                i18n != null ? i18n.getSubtitle() : null,
                i18n != null ? i18n.getDescription() : null,
                i18n != null ? i18n.getContent() : null,
                i18n != null ? i18n.getSpecificationJson() : null,
                i18n != null ? i18n.getSeoTitle() : null,
                i18n != null ? i18n.getSeoDescription() : null);
    }
}
