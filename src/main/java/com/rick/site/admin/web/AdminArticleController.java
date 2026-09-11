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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    public String list(Model model) {
        model.addAttribute("articles", articleService.listByTenant());
        return "admin/news";
    }

    @GetMapping({"/new", "/{id}/edit"})
    public String form(@PathVariable(required = false) Long id, Model model) {
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
        model.addAttribute("categories", categoryService.listByType("NEWS"));
        addLanguages(model);
        return "admin/news-form";
    }

    @PostMapping("/save")
    public String save(Article article, HttpServletRequest req, RedirectAttributes ra) {
        try {
            Article saved = articleService.saveArticle(article);
            for (String lang : localeResolver.supportedLanguages()) {
                String title = req.getParameter("title_" + lang);
                if (title == null || title.isBlank()) {
                    continue;
                }
                articleService.saveI18n(saved.getId(), ArticleI18n.builder()
                        .language(lang)
                        .title(title)
                        .summary(req.getParameter("summary_" + lang))
                        .content(req.getParameter("content_" + lang))
                        .seoTitle(req.getParameter("seoTitle_" + lang))
                        .seoDescription(req.getParameter("seoDescription_" + lang))
                        .build());
            }
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/news";
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
