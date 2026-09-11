package com.rick.site.product.service;

import com.rick.common.http.exception.BizException;
import com.rick.db.plugin.BaseServiceImpl;
import com.rick.site.i18n.service.I18nService;
import com.rick.site.page.service.HtmlSanitizer;
import com.rick.site.product.dao.ProductDAO;
import com.rick.site.product.dao.ProductI18nDAO;
import com.rick.site.product.entity.Product;
import com.rick.site.product.entity.ProductI18n;
import com.rick.site.tenant.context.TenantContext;
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

    public ProductService(ProductDAO baseDAO, ProductI18nDAO i18nDAO,
                          HtmlSanitizer sanitizer, I18nService i18nService) {
        super(baseDAO);
        this.i18nDAO = i18nDAO;
        this.sanitizer = sanitizer;
        this.i18nService = i18nService;
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
        return new ResolvedProduct(product, resolved.orElse(null), effectiveLanguage);
    }

    /**
     * 列表展示(启用产品):仅返回在指定语种已维护 i18n 内容的产品。
     *
     * <p>多语言语义:某产品在当前语种无 i18n 行 → 不展示(既不回退默认语种、也不回退 slug),
     * 避免在 zh-CN 列表里露出仅有 en-US 文案的产品。详情页仍走 {@link #resolveForDisplay},
     * 保留缺失回退默认语种的行为。
     */
    public List<ResolvedProduct> listForDisplay(String language, String defaultLanguage) {
        List<ResolvedProduct> out = new ArrayList<>();
        for (Product product : listEnabled()) {
            Map<String, ProductI18n> byLanguage = loadI18nMap(product.getId());
            Optional<ProductI18n> resolved = i18nService.resolve(byLanguage, language, defaultLanguage);
            if (resolved.isPresent() && language.equals(resolved.get().getLanguage())) {
                out.add(new ResolvedProduct(product, resolved.get(), resolved.get().getLanguage()));
            }
        }
        return out;
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

    /** 展示结果:产品 + 命中语言 + i18n(可能为 null)。 */
    public record ResolvedProduct(Product product, ProductI18n i18n, String language) {
    }
}
