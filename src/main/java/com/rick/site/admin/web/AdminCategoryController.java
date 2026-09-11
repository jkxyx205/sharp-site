package com.rick.site.admin.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.catalog.entity.Category;
import com.rick.site.catalog.entity.CategoryI18n;
import com.rick.site.catalog.service.CategoryService;
import com.rick.site.i18n.service.DefaultLocaleResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * 后台分类管理(Phase 17):列表 + CRUD + 多语言 i18n 编辑。PRODUCT / NEWS 共用,type 区分。
 *
 * <p>i18n 以 name 为必填主字段,空则跳过该语言。按 (type, slug) 幂等 upsert。
 *
 * @author Rick.Xu
 */
@Controller
@RequestMapping("/admin/categories")
public class AdminCategoryController {

    private final CategoryService categoryService;
    private final DefaultLocaleResolver localeResolver;

    public AdminCategoryController(CategoryService categoryService, DefaultLocaleResolver localeResolver) {
        this.categoryService = categoryService;
        this.localeResolver = localeResolver;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", categoryService.listByType("PRODUCT"));
        model.addAttribute("newsCategories", categoryService.listByType("NEWS"));
        return "admin/categories";
    }

    @GetMapping({"/new", "/{id}/edit"})
    public String form(@PathVariable(required = false) Long id,
                       @RequestParam(required = false) String lang, Model model) {
        String language = (lang == null || lang.isBlank()) ? localeResolver.defaultLanguage() : lang;
        Category category;
        Map<String, CategoryI18n> i18nMap;
        if (id == null) {
            category = new Category();
            category.setType("PRODUCT");
            category.setStatus((short) 1);
            category.setSort(0);
            i18nMap = Map.of();
        } else {
            category = categoryService.selectById(id).orElse(null);
            i18nMap = (category != null) ? categoryService.loadI18nMap(id) : Map.of();
        }
        model.addAttribute("category", category);
        model.addAttribute("i18nMap", i18nMap);
        model.addAttribute("currentLang", language);
        addLanguages(model);
        return "admin/category-form";
    }

    @PostMapping("/save")
    public String save(Category category, HttpServletRequest req, RedirectAttributes ra) {
        String language = req.getParameter("language");
        try {
            Category saved = categoryService.saveCategory(category);
            String name = req.getParameter("name");
            if (name != null && !name.isBlank()) {
                categoryService.saveI18n(saved.getId(), CategoryI18n.builder()
                        .language(language)
                        .name(name)
                        .description(req.getParameter("description"))
                        .build());
            }
            return "redirect:/admin/categories/" + saved.getId() + "/edit?lang=" + language;
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/categories";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            categoryService.delete(id);
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    private void addLanguages(Model model) {
        model.addAttribute("languages", localeResolver.supportedLanguages());
        model.addAttribute("defaultLanguage", localeResolver.defaultLanguage());
    }
}
