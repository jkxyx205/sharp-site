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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
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
    public String list(@RequestParam(required = false) Long categoryId, Model model) {
        model.addAttribute("categories", categoryService.listByType("PRODUCT"));
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("products", categoryId == null ? List.of() : productService.listByCategory(categoryId));
        return "admin/products";
    }

    @GetMapping({"/new", "/{id}/edit"})
    public String form(@PathVariable(required = false) Long id,
                       @RequestParam(required = false) String lang, Model model) {
        String language = (lang == null || lang.isBlank()) ? localeResolver.defaultLanguage() : lang;
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
        model.addAttribute("currentLang", language);
        model.addAttribute("categories", categoryService.listByType("PRODUCT"));
        addLanguages(model);
        return "admin/product-form";
    }

    @PostMapping("/save")
    public String save(Product product, HttpServletRequest req, RedirectAttributes ra) {
        String language = req.getParameter("language");
        try {
            Product saved = productService.saveProduct(product);
            String name = req.getParameter("name");
            if (name != null && !name.isBlank()) {
                productService.saveI18n(saved.getId(), ProductI18n.builder()
                        .language(language)
                        .name(name)
                        .subtitle(req.getParameter("subtitle"))
                        .description(req.getParameter("description"))
                        .content(req.getParameter("content"))
                        .specificationJson(req.getParameter("specificationJson"))
                        .seoTitle(req.getParameter("seoTitle"))
                        .seoDescription(req.getParameter("seoDescription"))
                        .build());
            }
            return "redirect:/admin/products/" + saved.getId() + "/edit?lang=" + language;
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/products";
        }
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
