package com.rick.site.news.service;

import com.rick.site.catalog.entity.Category;
import com.rick.site.catalog.service.CategoryService;
import com.rick.site.news.entity.Article;
import com.rick.site.news.entity.ArticleI18n;
import com.rick.site.news.service.ArticleService.ResolvedArticle;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-0701/0702/0703/0705 验收:新闻分类(复用 Category)+ 文章 CRUD + i18n + 清洗 + 租户隔离。
 */
@SpringBootTest
@Transactional
class ArticleServiceTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private TenantService tenantService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private Tenant createTenant(String code) {
        Tenant t = tenantService.save(Tenant.builder()
                .code(code).name(code).themeId("modern").build());
        TenantContext.set(t);
        return t;
    }

    private Article newArticle(String slug) {
        return Article.builder().slug(slug).author("Editor")
                .publishTime(LocalDateTime.now()).status((short) 1).sort(0).build();
    }

    @Test
    void newsCategoryCrudViaSharedCategoryService() {
        Tenant t = createTenant("news-1");
        Category c = categoryService.saveCategory(Category.builder()
                .type("NEWS").slug("company").sort(0).status((short) 1).build());
        assertThat(categoryService.findBySlug("NEWS", "company")).isPresent();
        assertThat(c.getType()).isEqualTo("NEWS");
    }

    @Test
    void articleUpsertBySlug() {
        Tenant t = createTenant("news-2");
        Article a1 = articleService.saveArticle(newArticle("fair-2026"));
        Article a2 = articleService.saveArticle(newArticle("fair-2026"));
        assertThat(a2.getId()).isEqualTo(a1.getId());
        assertThat(articleService.listByTenant()).hasSize(1);
    }

    @Test
    void i18nSanitizesContent() {
        Tenant t = createTenant("news-3");
        Article a = articleService.saveArticle(newArticle("post-1"));
        articleService.saveI18n(a.getId(), ArticleI18n.builder()
                .language("en-US").title("Post").summary("sum")
                .content("<p>ok</p><script>evil()</script>").seoTitle("Post SEO").build());
        ArticleI18n saved = articleService.findByLanguage(a.getId(), "en-US").orElseThrow();
        assertThat(saved.getContent()).contains("<p>ok</p>").doesNotContain("<script").doesNotContain("evil");
        assertThat(saved.getSeoTitle()).isEqualTo("Post SEO");
    }

    @Test
    void i18nFallsBackToDefault() {
        Tenant t = createTenant("news-4");
        Article a = articleService.saveArticle(newArticle("post-2"));
        articleService.saveI18n(a.getId(), ArticleI18n.builder()
                .language("en-US").title("EN title").content("<p>en</p>").build());
        ResolvedArticle resolved = articleService.resolveForDisplay("post-2", "zh-CN", "en-US");
        assertThat(resolved.language()).isEqualTo("en-US");
        assertThat(resolved.i18n().getTitle()).isEqualTo("EN title");
    }

    @Test
    void listForDisplayOnlyPublishedOrderedByPublishTimeDesc() {
        Tenant t = createTenant("news-5");
        Article older = articleService.saveArticle(newArticle("old"));
        older.setPublishTime(LocalDateTime.of(2026, 1, 1, 9, 0));
        articleService.saveArticle(older);
        Article newer = articleService.saveArticle(newArticle("new"));
        newer.setPublishTime(LocalDateTime.of(2026, 6, 1, 9, 0));
        articleService.saveArticle(newer);
        articleService.saveI18n(older.getId(), ArticleI18n.builder()
                .language("en-US").title("Old").build());
        articleService.saveI18n(newer.getId(), ArticleI18n.builder()
                .language("en-US").title("New").build());

        List<ResolvedArticle> list = articleService.listForDisplay("en-US", "en-US");
        assertThat(list).hasSize(2)
                .map(rp -> rp.i18n().getTitle()).containsExactly("New", "Old");
    }

    @Test
    void tenantIsolation() {
        Tenant a = createTenant("news-isol-a");
        Article pa = articleService.saveArticle(newArticle("shared"));
        articleService.saveI18n(pa.getId(), ArticleI18n.builder()
                .language("en-US").title("A").build());
        Tenant b = createTenant("news-isol-b");
        Article pb = articleService.saveArticle(newArticle("shared"));
        articleService.saveI18n(pb.getId(), ArticleI18n.builder()
                .language("en-US").title("B").build());

        // 各租户只能看到自己的文章(按 TenantContext 隔离,tenant_id 由 DatabaseConfig 追加)
        // 同一 slug 在两租户下解析到各自文章,互不可见
        TenantContext.set(a);
        assertThat(articleService.findBySlug("shared")).map(Article::getId).hasValue(pa.getId());
        TenantContext.set(b);
        // B 上下文按 slug 查到的是自己的 pb,而非 A 的 pa(越权读取隔离)
        assertThat(articleService.findBySlug("shared")).map(Article::getId).hasValue(pb.getId());
        // A 越权写 B 的文章 i18n 视同不存在(selectById 按上下文隔离)
        TenantContext.set(a);
        assertThatThrownBy(() -> articleService.saveI18n(pb.getId(), ArticleI18n.builder()
                .language("zh-CN").title("hijack").build()))
                .isInstanceOf(com.rick.common.http.exception.BizException.class);
    }
}
