package com.rick.site.product.service;

import com.rick.common.http.exception.BizException;
import com.rick.db.plugin.BaseServiceImpl;
import com.rick.site.catalog.service.CategoryService;
import com.rick.site.i18n.service.I18nService;
import com.rick.site.page.service.HtmlSanitizer;
import com.rick.site.product.dao.ProductDAO;
import com.rick.site.product.dao.ProductI18nDAO;
import com.rick.site.product.entity.Product;
import com.rick.site.product.entity.ProductI18n;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.translate.TranslationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;

/**
 * 产品服务(TASK-0602 / 0603 / 0605)。
 *
 * <p>租户隔离:查询显式带 tenant_id;按 slug 幂等 upsert,按 (product_id, language) 幂等 upsert i18n。
 * 富文本 content 保存前经 {@link HtmlSanitizer} 清洗;展示按当前语言取 i18n,缺失回退租户默认语言。
 *
 * @author Rick.Xu
 */
@Service
@Validated
public class ProductService extends BaseServiceImpl<ProductDAO, Product, Long> {

    private final ProductI18nDAO i18nDAO;
    private final HtmlSanitizer sanitizer;
    private final I18nService i18nService;
    private final CategoryService categoryService;
    private final TranslationService translationService;

    public ProductService(ProductDAO baseDAO, ProductI18nDAO i18nDAO,
                          HtmlSanitizer sanitizer, I18nService i18nService,
                          CategoryService categoryService, TranslationService translationService) {
        super(baseDAO);
        this.i18nDAO = i18nDAO;
        this.sanitizer = sanitizer;
        this.i18nService = i18nService;
        this.categoryService = categoryService;
        this.translationService = translationService;
    }

    public List<Product> listByTenant() {
        // tenant_id 由 SiteDatabaseConfig 统一追加;此处仅保留排序占位
        return baseDAO.select("1=1 ORDER BY sort, id", Map.of());
    }

    public List<Product> listEnabled() {
        return baseDAO.select("status = 1 ORDER BY sort, id", Map.of());
    }

    /** 后台按分类筛选(租户隔离由 SiteDatabaseConfig 统一追加)。 */
    public List<Product> listByCategory(Long categoryId) {
        return baseDAO.select("category_id = :categoryId ORDER BY sort, id",
                Map.of("categoryId", categoryId));
    }

    public Optional<Product> findBySlug(String slug) {
        List<Product> found = baseDAO.select("slug = :slug", Map.of("slug", slug));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Transactional(rollbackFor = Exception.class)
    public Product saveProduct(Product product) {
        product.setTenantId(TenantContext.requireTenantId());
        findBySlug(product.getSlug()).ifPresent(existing -> product.setId(existing.getId()));
        return baseDAO.insertOrUpdate(product);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductI18n saveI18n(Long productId, ProductI18n i18n) {
        requireOwned(productId);
        i18n.setProductId(productId);
        i18n.setContent(sanitizer.clean(i18n.getContent()));
        String spec = i18n.getSpecificationJson();
        i18n.setSpecificationJson((spec == null || spec.isBlank()) ? "{}" : spec);
        findByLanguage(productId, i18n.getLanguage()).ifPresent(existing -> i18n.setId(existing.getId()));
        return i18nDAO.insertOrUpdate(i18n);
    }

    /**
     * 把 {@code source} 语种文案翻译并覆盖写入 {@code targetLangs} 各语种的 i18n 行(覆盖式同步)。
     *
     * <p>逐语种调用 {@link TranslationService#translate},再用 {@link #saveI18n} 写入(复用幂等 upsert
     * 与 HtmlSanitizer 清洗;specificationJson 译文由 saveI18n 兜底为合法 JSON)。
     *
     * @param productId   已保存的产品主键
     * @param source      刚保存的源语种 i18n(取 language 与文案字段)
     * @param targetLangs 需同步的目标语种列表(不含源语种)
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncToLanguages(Long productId, ProductI18n source, List<String> targetLangs) {
        String sourceLang = source.getLanguage();
        Map<String, String> sourceFields = new LinkedHashMap<>();
        sourceFields.put("name", source.getName());
        sourceFields.put("subtitle", source.getSubtitle());
        sourceFields.put("description", source.getDescription());
        sourceFields.put("content", source.getContent());
        sourceFields.put("specificationJson", source.getSpecificationJson());
        sourceFields.put("seoTitle", source.getSeoTitle());
        sourceFields.put("seoDescription", source.getSeoDescription());
        for (String lang : targetLangs) {
            Map<String, String> t = translationService.translate(sourceFields, sourceLang, lang);
            saveI18n(productId, ProductI18n.builder()
                    .language(lang)
                    .name(t.get("name"))
                    .subtitle(t.get("subtitle"))
                    .description(t.get("description"))
                    .content(t.get("content"))
                    .specificationJson(t.get("specificationJson"))
                    .seoTitle(t.get("seoTitle"))
                    .seoDescription(t.get("seoDescription"))
                    .build());
        }
    }

    /** 逻辑删除:校验归属(跨租户查不到)后置 is_deleted。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long productId) {
        requireOwned(productId);
        baseDAO.deleteById(productId);
    }

    /** 单品展示:按 slug 查产品 + i18n(缺失回退默认语言)。 */
    public ResolvedProduct resolveForDisplay(String slug, String language, String defaultLanguage) {
        Product product = findBySlug(slug)
                .orElseThrow(() -> new BizException("产品不存在: " + slug));
        Map<String, ProductI18n> byLanguage = loadI18nMap(product.getId());
        Optional<ProductI18n> resolved = i18nService.resolve(byLanguage, language, defaultLanguage);
        String effectiveLanguage = resolved.map(ProductI18n::getLanguage).orElse(defaultLanguage);
        CategoryInfo ci = categoryInfo(resolvedCategoryById(language, defaultLanguage), product.getCategoryId());
        return new ResolvedProduct(product, resolved.orElse(null), effectiveLanguage, ci.slug(), ci.name());
    }

    /**
     * 列表展示(启用产品):仅返回在指定语种已维护 i18n 内容的产品。
     *
     * <p>多语言语义:某产品在当前语种无 i18n 行 → 不展示(既不回退默认语种、也不回退 slug),
     * 避免在 zh-CN 列表里露出仅有 en-US 文案的产品。详情页仍走 {@link #resolveForDisplay},
     * 保留缺失回退默认语种的行为。categorySlug/categoryName 来自产品所属 PRODUCT 分类,
     * 便于模板按分类过滤并以分类名作为板块标题。
     */
    public List<ResolvedProduct> listForDisplay(String language, String defaultLanguage) {
        Map<Long, CategoryService.ResolvedCategory> catById = resolvedCategoryById(language, defaultLanguage);
        List<ResolvedProduct> out = new ArrayList<>();
        for (Product product : listEnabled()) {
            Map<String, ProductI18n> byLanguage = loadI18nMap(product.getId());
            Optional<ProductI18n> resolved = i18nService.resolve(byLanguage, language, defaultLanguage);
            if (resolved.isPresent() && language.equals(resolved.get().getLanguage())) {
                CategoryInfo ci = categoryInfo(catById, product.getCategoryId());
                out.add(new ResolvedProduct(product, resolved.get(), resolved.get().getLanguage(),
                        ci.slug(), ci.name()));
            }
        }
        return out;
    }

    /** 取所有启用 PRODUCT 分类的 id→已解析(含 i18n 名称)映射,供产品解析 categorySlug/categoryName。 */
    private Map<Long, CategoryService.ResolvedCategory> resolvedCategoryById(String language, String defaultLanguage) {
        Map<Long, CategoryService.ResolvedCategory> map = new HashMap<>();
        for (CategoryService.ResolvedCategory rc
                : categoryService.resolveForDisplay("PRODUCT", language, defaultLanguage).values()) {
            map.put(rc.category().getId(), rc);
        }
        return map;
    }

    /**
     * 取启用 PRODUCT 分类的展示视图(slug + 已解析 i18n 名称),按 sort 排序。
     * 供产品页遍历所有分类、按分类分组展示产品(模板用 {@code cat.slug} 过滤全量产品)。
     */
    public List<CategoryView> listCategoryViews(String language, String defaultLanguage) {
        List<CategoryView> out = new ArrayList<>();
        for (CategoryService.ResolvedCategory rc
                : categoryService.resolveForDisplay("PRODUCT", language, defaultLanguage).values()) {
            out.add(new CategoryView(rc.category().getSlug(),
                    rc.i18n() != null ? rc.i18n().getName() : null));
        }
        return out;
    }

    /** 从已解析分类映射取 (slug, name);categoryId 为空或分类未启用 → (null, null)。 */
    private static CategoryInfo categoryInfo(Map<Long, CategoryService.ResolvedCategory> map, Long categoryId) {
        if (categoryId == null) {
            return new CategoryInfo(null, null);
        }
        CategoryService.ResolvedCategory rc = map.get(categoryId);
        if (rc == null) {
            return new CategoryInfo(null, null);
        }
        return new CategoryInfo(rc.category().getSlug(),
                rc.i18n() != null ? rc.i18n().getName() : null);
    }

    /** 分类展示信息(slug + 已解析 i18n 名称)。 */
    private record CategoryInfo(String slug, String name) {
    }

    public Map<String, ProductI18n> loadI18nMap(Long productId) {
        Map<String, ProductI18n> map = new LinkedHashMap<>();
        for (ProductI18n row : i18nDAO.select("product_id = :productId", Map.of("productId", productId))) {
            map.put(row.getLanguage(), row);
        }
        return map;
    }

    Optional<ProductI18n> findByLanguage(Long productId, String language) {
        List<ProductI18n> found = i18nDAO.select("product_id = :productId AND language = :language",
                Map.of("productId", productId, "language", language));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    private Product requireOwned(Long productId) {
        // selectById 已按 TenantContext 隔离(跨租户查不到),无需再手动比对 tenantId
        return baseDAO.selectById(productId)
                .orElseThrow(() -> new BizException("产品不存在: id=" + productId));
    }

    /** 展示结果:产品 + 命中语言 + i18n(可能为 null) + 分类 slug/name(所属 PRODUCT 分类,可能为 null)。 */
    public record ResolvedProduct(Product product, ProductI18n i18n, String language,
                                  String categorySlug, String categoryName) {
    }

    /** 分类展示视图(slug + 已解析 i18n 名称),用于产品页按分类遍历分组。 */
    public record CategoryView(String slug, String name) {
    }
}
