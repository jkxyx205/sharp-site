package com.rick.site.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.catalog.service.CategoryService;
import com.rick.site.i18n.context.LocaleContext;
import com.rick.site.i18n.model.LocaleResolution;
import com.rick.site.news.dto.ArticleView;
import com.rick.site.news.service.ArticleService;
import com.rick.site.product.dto.ProductView;
import com.rick.site.product.service.ProductService;
import com.rick.site.seo.dto.SeoFallback;
import com.rick.site.seo.service.SeoConfigService;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.theme.model.ThemeManifest.ThemePage;
import com.rick.site.theme.service.ThemeManifestResolver;
import com.rick.site.video.dto.VideoView;
import com.rick.site.video.service.VideoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * 站点静态页面前台 Controller。
 *
 * <p>页面(about/contact 等)改由前端模板维护,清单见 {@code themes/{themeId}/meta/theme.json} 的
 * {@code pages}。后端只负责渲染:按当前有效路径在主题清单查找页面,渲染其 {@code template};
 * 正文文案由模板 + {@code messages.json} 文案键提供,不再有 {@code site_page} 表。
 *
 * <p>统一处理默认语言与 {@code /{locale}/} 前缀:
 * <ul>
 *   <li>{@code /about} → 默认语言</li>
 *   <li>{@code /zh-cn/about} → zh-CN</li>
 * </ul>
 * 语言与有效路径由 {@link LocaleContext}(LocaleFilter 写入)决定。SEO meta 由
 * {@link SeoConfigService} 解析(seo_config 覆盖,缺失回退页面 label/请求 URL);
 * 单页 SEO 的 {@code page_type} 取页面路径(如 {@code /about}),page_id 恒为空。
 *
 * <p>路径变量正则限定单段字母数字中划线,避开静态资源(含 . / 多段)。
 *
 * @author Rick.Xu
 */
@Controller
public class SitePageController {

    private final SeoConfigService seoService;
    private final ThemeManifestResolver manifestResolver;
    private final ProductService productService;
    private final ArticleService articleService;
    private final CategoryService categoryService;
    private final VideoService videoService;

    public SitePageController(SeoConfigService seoService, ThemeManifestResolver manifestResolver,
                             ProductService productService, ArticleService articleService,
                             CategoryService categoryService, VideoService videoService) {
        this.seoService = seoService;
        this.manifestResolver = manifestResolver;
        this.productService = productService;
        this.articleService = articleService;
        this.categoryService = categoryService;
        this.videoService = videoService;
    }

    @GetMapping(
            value = {
                    "/{path:[a-zA-Z0-9-]+}",
                    "/{locale:[a-z]{2}-[a-z]{2}}/{path:[a-zA-Z0-9-]+}"
            })
    public String page(HttpServletRequest request, Model model) {
        Tenant tenant = TenantContext.require();
        String dl = manifestResolver.defaultLocale(tenant);
        LocaleResolution loc = LocaleContext.get()
                .orElseThrow(() -> new BizException("当前请求未解析到语言"));
        String pagePath = loc.effectivePath();
        // 按路径在主题清单 pages 中查找;不在清单 → 404
        ThemePage page = manifestResolver.resolve(tenant).pages().stream()
                .filter(p -> p.path().equals(pagePath))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "页面不存在: " + pagePath));
        model.addAttribute("page", page);
        // 与 PreviewController.page 对齐:为通用页面(如 /info)注入全量产品/新闻列表,
        // 使模板内按 categorySlug 过滤的板块在线上/预览/静态三路渲染一致。
        model.addAttribute("products", productService.listForDisplay(loc.language(), dl).stream()
                .map(ProductView::from).toList());
        model.addAttribute("news", articleService.listForDisplay(loc.language(), dl).stream()
                .map(ArticleView::from).toList());
        // 视频:与产品同构,为通用页面注入全量视频列表(线上/预览/静态三路一致)。
        model.addAttribute("videos", videoService.listForDisplay(loc.language(), dl).stream()
                .map(VideoView::from).toList());
        model.addAttribute("categories", categoryService.selectAll());
        // 单页 SEO:page_type = 页面路径(如 /about),page_id 恒为空
        seoService.resolveView(page.path(), null, loc.language(), dl,
                new SeoFallback(page.label(), "", "", request.getRequestURL().toString())).applyTo(model);
        return page.template();
    }
}
