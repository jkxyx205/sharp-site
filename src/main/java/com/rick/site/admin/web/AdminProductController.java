package com.rick.site.admin.web;

import com.rick.site.product.entity.Product;
import com.rick.site.product.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 后台产品列表 Controller(TASK-0903):证明管理员只能见本租户产品。
 *
 * <p>{@code GET /admin/products} 调 {@link ProductService#listByTenant()}(上下文驱动),
 * 作用域由 AdminContextFilter 从认证管理员 tenantId 写入的 TenantContext 决定,
 * 而非请求 Host —— 管理员在其它租户域名下访问也只会读到本租户产品。
 *
 * @author Rick.Xu
 */
@Controller
public class AdminProductController {

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/admin/products")
    public String list(Model model) {
        List<Product> products = productService.listByTenant();
        model.addAttribute("products", products);
        return "admin/products";
    }
}
