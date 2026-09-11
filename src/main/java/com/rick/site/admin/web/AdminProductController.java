package com.rick.site.admin.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.catalog.service.CategoryService;
import com.rick.site.i18n.service.DefaultLocaleResolver;
import com.rick.site.product.entity.Product;
import com.rick.site.product.entity.ProductI18n;
import com.rick.site.product.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * 后台产品管理(TASK-0903 + Phase 17):列表(本租户作用域)+ CRUD + 多语言 i18n 编辑。
 *
 * <p>列表作用域来自 AdminContextFilter 写入的 TenantContext(认证管理员 tenantId)而非 Host,
 * 管理员在其它租户域名下也只见本租户产品。i18n 以 name 为必填主字段,某语言 name 为空时跳过
 * (保留展示回退到默认语言)。categoryId 下拉来自 {@code categoryService.listByType("PRODUCT")}。
 *
 * @author Rick.Xu
 */
@Controller
@RequestMapping("/admin/products")
public class AdminProductController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final DefaultLocaleResolver localeResolver;

    public AdminProductController(ProductService productService, CategoryService categoryService,
                                  DefaultLocaleResolver localeResolver) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.localeResolver = localeResolver;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("products", productService.listByTenant());
        return "admin/products";
    }

    @GetMapping({"/new", "/{id}/edit"})
    public String form(@PathVariable(required = false) Long id, Model model) {
        Product product;
        Map<String, ProductI18n> i18nMap;
        if (id == null) {
            product = new Product();
            product.setStatus((short) 1);
            product.setSort(0);
            i18nMap = Map.of();
        } else {
            product = productService.selectById(id).orElse(null);
            i18nMap = (product != null) ? productService.loadI18nMap(id) : Map.of();
        }
        model.addAttribute("product", product);
        model.addAttribute("i18nMap", i18nMap);
        model.addAttribute("categories", categoryService.listByType("PRODUCT"));
        addLanguages(model);
        return "admin/product-form";
    }

    @PostMapping("/save")
    public String save(Product product, HttpServletRequest req, RedirectAttributes ra) {
        try {
            Product saved = productService.saveProduct(product);
            for (String lang : localeResolver.supportedLanguages()) {
                String name = req.getParameter("name_" + lang);
                if (name == null || name.isBlank()) {
                    continue; // name 必填,空则跳过该语言(保留回退)
                }
                productService.saveI18n(saved.getId(), ProductI18n.builder()
                        .language(lang)
                        .name(name)
                        .subtitle(req.getParameter("subtitle_" + lang))
                        .description(req.getParameter("description_" + lang))
                        .content(req.getParameter("content_" + lang))
                        .specificationJson(req.getParameter("specificationJson_" + lang))
                        .seoTitle(req.getParameter("seoTitle_" + lang))
                        .seoDescription(req.getParameter("seoDescription_" + lang))
                        .build());
            }
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/products";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            productService.delete(id);
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/products";
    }

    private void addLanguages(Model model) {
        model.addAttribute("languages", localeResolver.supportedLanguages());
        model.addAttribute("defaultLanguage", localeResolver.defaultLanguage());
    }
}
