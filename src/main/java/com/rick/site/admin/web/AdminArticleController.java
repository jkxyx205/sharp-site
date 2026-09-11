package com.rick.site.admin.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.catalog.service.CategoryService;
import com.rick.site.i18n.service.DefaultLocaleResolver;
import com.rick.site.news.entity.Article;
import com.rick.site.news.entity.ArticleI18n;
import com.rick.site.news.service.ArticleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * 后台新闻管理(Phase 17):列表 + CRUD + 多语言 i18n 编辑。
 *
 * <p>i18n 以 title 为必填主字段,某语言 title 为空时跳过(保留展示回退到默认语言)。
 * categoryId 下拉来自 {@code categoryService.listByType("NEWS")}。
 *
 * @author Rick.Xu
 */
@Controller
@RequestMapping("/admin/news")
public class AdminArticleController {

    private final ArticleService articleService;
    private final CategoryService categoryService;
    private final DefaultLocaleResolver localeResolver;

    public AdminArticleController(ArticleService articleService, CategoryService categoryService,
                                  DefaultLocaleResolver localeResolver) {
        this.articleService = articleService;
        this.categoryService = categoryService;
        this.localeResolver = localeResolver;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long categoryId, Model model) {
        model.addAttribute("categories", categoryService.listByType("NEWS"));
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("articles", categoryId == null ? List.of() : articleService.listByCategory(categoryId));
        return "admin/news";
    }

    @GetMapping({"/new", "/{id}/edit"})
    public String form(@PathVariable(required = false) Long id,
                       @RequestParam(required = false) String lang, Model model) {
        String language = (lang == null || lang.isBlank()) ? localeResolver.defaultLanguage() : lang;
        Article article;
        Map<String, ArticleI18n> i18nMap;
        if (id == null) {
            article = new Article();
            article.setStatus((short) 1);
            article.setSort(0);
            i18nMap = Map.of();
        } else {
            article = articleService.selectById(id).orElse(null);
            i18nMap = (article != null) ? articleService.loadI18nMap(id) : Map.of();
        }
        model.addAttribute("article", article);
        model.addAttribute("i18nMap", i18nMap);
        model.addAttribute("currentLang", language);
        model.addAttribute("categories", categoryService.listByType("NEWS"));
        addLanguages(model);
        return "admin/news-form";
    }

    @PostMapping("/save")
    public String save(Article article, HttpServletRequest req, RedirectAttributes ra) {
        String language = req.getParameter("language");
        try {
            Article saved = articleService.saveArticle(article);
            String title = req.getParameter("title");
            if (title != null && !title.isBlank()) {
                articleService.saveI18n(saved.getId(), ArticleI18n.builder()
                        .language(language)
                        .title(title)
                        .summary(req.getParameter("summary"))
                        .content(req.getParameter("content"))
                        .seoTitle(req.getParameter("seoTitle"))
                        .seoDescription(req.getParameter("seoDescription"))
                        .build());
            }
            return "redirect:/admin/news/" + saved.getId() + "/edit?lang=" + language;
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/news";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            articleService.delete(id);
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/news";
    }

    private void addLanguages(Model model) {
        model.addAttribute("languages", localeResolver.supportedLanguages());
        model.addAttribute("defaultLanguage", localeResolver.defaultLanguage());
    }
}
