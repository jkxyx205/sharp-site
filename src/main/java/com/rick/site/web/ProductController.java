package com.rick.site.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.i18n.context.LocaleContext;
import com.rick.site.i18n.model.LocaleResolution;
import com.rick.site.product.dto.ProductView;
import com.rick.site.product.service.ProductService;
import com.rick.site.product.service.ProductService.ResolvedProduct;
import com.rick.site.seo.dto.SeoFallback;
import com.rick.site.seo.service.SeoConfigService;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.theme.service.ThemeManifestResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 产品前台 Controller(TASK-0604 / TASK-0605 / TASK-1002)。
 *
 * <p>{@code /products} 与 {@code /{locale}/products} 列表;{@code /products/{slug}} 详情。
 * 语言来自 LocaleContext;i18n 缺失回退租户默认语言。SEO 字段(seoTitle/seoDescription)注入模型。
 * SEO meta 由 {@link SeoConfigService} 解析(seo_config 覆盖,缺失回退产品名/封面/请求 URL)。
 *
 * @author Rick.Xu
 */
@Controller
public class ProductController {

    private final ProductService productService;
    private final SeoConfigService seoService;
    private final ThemeManifestResolver manifestResolver;

    public ProductController(ProductService productService, SeoConfigService seoService,
                           ThemeManifestResolver manifestResolver) {
        this.productService = productService;
        this.seoService = seoService;
        this.manifestResolver = manifestResolver;
    }

    @GetMapping(value = {"/products", "/{locale:[a-z]{2}-[a-z]{2}}/products",
            "/products/", "/{locale:[a-z]{2}-[a-z]{2}}/products/"})
    public String list(HttpServletRequest request, Model model) {
        Tenant tenant = TenantContext.require();
        String dl = manifestResolver.defaultLocale(tenant);
        LocaleResolution loc = LocaleContext.get()
                .orElseGet(() -> new LocaleResolution(dl, "/products"));
        List<ProductView> products = productService.listForDisplay(
                        loc.language(), dl).stream()
                .map(ProductView::from).toList();
        model.addAttribute("products", products);
        seoService.resolveView(SeoConfigService.PRODUCTS_LIST, null, loc.language(), dl,
                new SeoFallback("Products", "", "", request.getRequestURL().toString())).applyTo(model);
        return "themes/modern/products";
    }

    @GetMapping(value = {"/products/{slug}", "/{locale:[a-z]{2}-[a-z]{2}}/products/{slug}"})
    public String detail(@org.springframework.web.bind.annotation.PathVariable String slug,
                          HttpServletRequest request, Model model) {
        Tenant tenant = TenantContext.require();
        String dl = manifestResolver.defaultLocale(tenant);
        LocaleResolution loc = LocaleContext.get()
                .orElseGet(() -> new LocaleResolution(dl, "/products/" + slug));
        ResolvedProduct resolved;
        try {
            resolved = productService.resolveForDisplay(
                    slug, loc.language(), dl);
        } catch (BizException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
        ProductView product = ProductView.from(resolved);
        model.addAttribute("product", product);
        String fbTitle = product.seoTitle() != null ? product.seoTitle() : product.name();
        String fbDesc = product.seoDescription() != null ? product.seoDescription()
                : (product.subtitle() != null ? product.subtitle() : "");
        seoService.resolveView(SeoConfigService.PRODUCT, resolved.product().getId(),
                loc.language(), dl,
                new SeoFallback(fbTitle, fbDesc, product.cover(), request.getRequestURL().toString())).applyTo(model);
        return "themes/modern/product-detail";
    }
}
