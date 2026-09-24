package com.rick.site.admin.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.catalog.service.CategoryService;
import com.rick.site.common.async.AsyncRunner;
import com.rick.site.i18n.service.DefaultLocaleResolver;
import com.rick.site.news.entity.Article;
import com.rick.site.news.entity.ArticleI18n;
import com.rick.site.news.service.ArticleService;
import com.rick.site.tenant.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
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
    private final AsyncRunner asyncRunner;

    public AdminArticleController(ArticleService articleService, CategoryService categoryService,
                                  DefaultLocaleResolver localeResolver, AsyncRunner asyncRunner) {
        this.articleService = articleService;
        this.categoryService = categoryService;
        this.localeResolver = localeResolver;
        this.asyncRunner = asyncRunner;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long categoryId, Model model) {
        model.addAttribute("categories", categoryService.listByType("NEWS"));
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("articles", categoryId == null ? articleService.listByTenant() : articleService.listByCategory(categoryId));
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
            article.setPublishTime(LocalDateTime.now().withSecond(0).withNano(0)); // 新建默认当前时间(分精度,与表单 datetime-local 一致),可改
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
                ArticleI18n source = ArticleI18n.builder()
                        .language(language)
                        .title(title)
                        .summary(req.getParameter("summary"))
                        .content(req.getParameter("content"))
                        .seoTitle(req.getParameter("seoTitle"))
                        .seoDescription(req.getParameter("seoDescription"))
                        .build();
                articleService.saveI18n(saved.getId(), source);
                if ("true".equals(req.getParameter("syncToOtherLanguages"))) {
                    // 翻译同步逐语种调 LLM,耗时较长,提交到异步线程池;请求线程立即返回。
                    // 租户上下文不隐式继承:在此捕获,异步任务体里 set/clear。
                    com.rick.site.tenant.entity.Tenant tenant = TenantContext.require();
                    java.util.List<String> targets = targetLanguages(language);
                    asyncRunner.run(() -> {
                        TenantContext.set(tenant);
                        try {
                            articleService.syncToLanguages(saved.getId(), source, targets);
                        } finally {
                            TenantContext.clear();
                        }
                    });
                }
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

    /** 同步目标语种:租户启用语种去掉当前编辑语种。 */
    private java.util.List<String> targetLanguages(String currentLang) {
        return localeResolver.supportedLanguages().stream()
                .filter(l -> !l.equals(currentLang))
                .toList();
    }
}
