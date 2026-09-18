package com.rick.site.admin.web;

import com.rick.site.admin.security.AdminPrincipal;
import com.rick.site.news.service.ArticleService;
import com.rick.site.product.service.ProductService;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.video.service.VideoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

/**
 * 后台认证与首页 Controller(TASK-0902)。
 *
 * <p>{@code GET /admin/login} 渲染登录页;{@code GET /admin/} 仪表盘:已发布文章数 + 上架产品数 + 已发布视频数。
 * username/tenantName 由 {@link AdminGlobalModel} 统一注入顶栏。
 *
 * @author Rick.Xu
 */
@Controller
public class AdminAuthController {

    private final ArticleService articleService;
    private final ProductService productService;
    private final VideoService videoService;

    public AdminAuthController(ArticleService articleService, ProductService productService,
                               VideoService videoService) {
        this.articleService = articleService;
        this.productService = productService;
        this.videoService = videoService;
    }

    @GetMapping("/admin/login")
    public String login() {
        return "admin/login";
    }

    @GetMapping("/admin/")
    public String index(Principal principal, Model model) {
        if (principal instanceof AdminPrincipal admin) {
            model.addAttribute("username", admin.getUsername());
            model.addAttribute("tenantId", admin.getTenantId());
        }
        // 当前租户信息(仪表盘展示):name/themeId 全局顶栏已注入,这里补 themeId + 状态。
        TenantContext.get().ifPresent(t -> {
            model.addAttribute("tenantName", t.getName());
            model.addAttribute("themeId", t.getThemeId());
            boolean active = t.getStatus() != null && t.getStatus() == 1;
            model.addAttribute("tenantStatusLabel", active ? "启用" : "停用");
            model.addAttribute("tenantStatusTag", active ? "tag-primary" : "tag-secondary");
        });
        model.addAttribute("articleCount", articleService.listPublished().size());
        model.addAttribute("productCount", productService.listEnabled().size());
        model.addAttribute("videoCount", videoService.listEnabled().size());
        return "admin/index";
    }
}
