package com.rick.site.catalog.service;

import com.rick.common.http.exception.BizException;
import com.rick.db.plugin.BaseServiceImpl;
import com.rick.site.catalog.dao.CategoryDAO;
import com.rick.site.catalog.dao.CategoryI18nDAO;
import com.rick.site.catalog.entity.Category;
import com.rick.site.catalog.entity.CategoryI18n;
import com.rick.site.i18n.service.I18nService;
import com.rick.site.tenant.context.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 分类服务(TASK-0601),PRODUCT / NEWS 共用,type 区分。
 *
 * <p>租户隔离:查询显式带 tenant_id;按 (type, slug) 幂等 upsert,按 (category_id, language) 幂等 upsert i18n。
 *
 * @author Rick.Xu
 */
@Service
@Validated
public class CategoryService extends BaseServiceImpl<CategoryDAO, Category, Long> {

    private final CategoryI18nDAO i18nDAO;
    private final I18nService i18nService;

    public CategoryService(CategoryDAO baseDAO, CategoryI18nDAO i18nDAO, I18nService i18nService) {
        super(baseDAO);
        this.i18nDAO = i18nDAO;
        this.i18nService = i18nService;
    }

    public List<Category> listByType(String type) {
        return baseDAO.select("type = :type ORDER BY sort, id", Map.of("type", type));
    }

    public List<Category> listEnabled(String type) {
        return baseDAO.select("type = :type AND status = 1 ORDER BY sort, id", Map.of("type", type));
    }

    public Optional<Category> findBySlug(String type, String slug) {
        List<Category> found = baseDAO.select(
                "type = :type AND slug = :slug",
                Map.of("type", type, "slug", slug));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Transactional(rollbackFor = Exception.class)
    public Category saveCategory(Category category) {
        category.setTenantId(TenantContext.requireTenantId());
        findBySlug(category.getType(), category.getSlug())
                .ifPresent(existing -> category.setId(existing.getId()));
        return baseDAO.insertOrUpdate(category);
    }

    @Transactional(rollbackFor = Exception.class)
    public CategoryI18n saveI18n(Long categoryId, CategoryI18n i18n) {
        requireOwned(categoryId);
        i18n.setCategoryId(categoryId);
        findByLanguage(categoryId, i18n.getLanguage()).ifPresent(existing -> i18n.setId(existing.getId()));
        return i18nDAO.insertOrUpdate(i18n);
    }

    /** 展示:按 type 取启用分类(排序),每条取当前语言 i18n(缺失回退默认),按 slug 索引。 */
    public Map<String, ResolvedCategory> resolveForDisplay(String type,
                                                            String language, String defaultLanguage) {
        Map<String, ResolvedCategory> map = new LinkedHashMap<>();
        for (Category category : listEnabled(type)) {
            Map<String, CategoryI18n> byLanguage = loadI18nMap(category.getId());
            Optional<CategoryI18n> resolved = i18nService.resolve(byLanguage, language, defaultLanguage);
            String effectiveLanguage = resolved.map(CategoryI18n::getLanguage).orElse(defaultLanguage);
            map.put(category.getSlug(), new ResolvedCategory(category, resolved.orElse(null), effectiveLanguage));
        }
        return map;
    }

    Map<String, CategoryI18n> loadI18nMap(Long categoryId) {
        Map<String, CategoryI18n> map = new LinkedHashMap<>();
        for (CategoryI18n row : i18nDAO.select("category_id = :categoryId", Map.of("categoryId", categoryId))) {
            map.put(row.getLanguage(), row);
        }
        return map;
    }

    Optional<CategoryI18n> findByLanguage(Long categoryId, String language) {
        List<CategoryI18n> found = i18nDAO.select("category_id = :categoryId AND language = :language",
                Map.of("categoryId", categoryId, "language", language));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    private Category requireOwned(Long categoryId) {
        // selectById 已按 TenantContext 隔离(跨租户查不到),无需再手动比对 tenantId
        return baseDAO.selectById(categoryId)
                .orElseThrow(() -> new BizException("分类不存在: id=" + categoryId));
    }

    /** 展示结果:分类 + 命中语言 + i18n(可能为 null)。 */
    public record ResolvedCategory(Category category, CategoryI18n i18n, String language) {
    }
}
