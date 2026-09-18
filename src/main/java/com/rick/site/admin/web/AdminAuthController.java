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
        model.addAttribute("tenantName",
                TenantContext.get().map(t -> t.getName()).orElse(""));
        model.addAttribute("articleCount", articleService.listPublished().size());
        model.addAttribute("productCount", productService.listEnabled().size());
        model.addAttribute("videoCount", videoService.listEnabled().size());
        return "admin/index";
    }
}
