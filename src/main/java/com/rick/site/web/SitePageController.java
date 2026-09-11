package com.rick.site.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.i18n.context.LocaleContext;
import com.rick.site.i18n.model.LocaleResolution;
import com.rick.site.page.service.SitePageService;
import com.rick.site.page.service.SitePageService.ResolvedPage;
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

/**
 * 站点页面前台 Controller(TASK-0402 / TASK-1002)。
 *
 * <p>统一处理默认语言与 {@code /{locale}/} 前缀:
 * <ul>
 *   <li>{@code /about} → 默认语言</li>
 *   <li>{@code /zh-cn/about} → zh-CN</li>
 * </ul>
 * 语言与有效路径由 {@link LocaleContext}(LocaleFilter 写入)决定,Controller 本身不分支语言;
 * 按有效路径查 {@code SitePage} + i18n(缺失回退租户默认语言),渲染页面声明的模板。
 * SEO meta 由 {@link SeoConfigService} 解析(seo_config 覆盖,缺失回退页面标题/封面/请求 URL)。
 *
 * <p>路径变量正则限定单段字母数字中划线,避开静态资源(含 . / 多段)。
 *
 * @author Rick.Xu
 */
@Controller
public class SitePageController {

    private final SitePageService pageService;
    private final SeoConfigService seoService;
    private final ThemeManifestResolver manifestResolver;

    public SitePageController(SitePageService pageService, SeoConfigService seoService,
                             ThemeManifestResolver manifestResolver) {
        this.pageService = pageService;
        this.seoService = seoService;
        this.manifestResolver = manifestResolver;
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
        ResolvedPage resolved;
        try {
            resolved = pageService.resolveForDisplay(
                    loc.effectivePath(), loc.language(), dl);
        } catch (BizException e) {
            // 页面不存在 → 404,而非 500
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
        model.addAttribute("page", resolved.page());
        model.addAttribute("content", resolved.i18n() != null ? resolved.i18n().getContent() : "");

        String fbTitle = resolved.i18n() != null && resolved.i18n().getTitle() != null
                ? resolved.i18n().getTitle() : resolved.page().getPageKey();
        String fbImage = resolved.i18n() != null ? resolved.i18n().getCover() : "";
        seoService.resolveView(SeoConfigService.PAGE, resolved.page().getId(),
                loc.language(), dl,
                new SeoFallback(fbTitle, "", fbImage, request.getRequestURL().toString())).applyTo(model);
        return resolved.page().getTemplate();
    }
}
