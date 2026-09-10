package com.rick.site.page.service;

import com.rick.common.http.exception.BizException;
import com.rick.db.plugin.BaseServiceImpl;
import com.rick.site.i18n.service.I18nService;
import com.rick.site.page.dao.SitePageDAO;
import com.rick.site.page.dao.SitePageI18nDAO;
import com.rick.site.page.entity.SitePage;
import com.rick.site.page.entity.SitePageI18n;
import com.rick.site.tenant.context.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 站点页面服务(TASK-0401)。
 *
 * <p>租户隔离:所有查询显式带 {@code tenant_id};tenant_id 由框架从 TenantContext 注入,
 * 但调用方(后台)仍须传入 tenantId 做归属校验,不信任前端(CLAUDE.md §4)。
 * 富文本 content 保存前经 {@link HtmlSanitizer} 清洗(§9 / TASK-0404);
 * 展示时按当前语言查 i18n,缺失回退租户默认语言(TASK-0303)。
 *
 * @author Rick.Xu
 */
@Service
@Validated
public class SitePageService extends BaseServiceImpl<SitePageDAO, SitePage, Long> {

    private final SitePageI18nDAO i18nDAO;
    private final HtmlSanitizer sanitizer;
    private final I18nService i18nService;

    public SitePageService(SitePageDAO baseDAO, SitePageI18nDAO i18nDAO,
                           HtmlSanitizer sanitizer, I18nService i18nService) {
        super(baseDAO);
        this.i18nDAO = i18nDAO;
        this.sanitizer = sanitizer;
        this.i18nService = i18nService;
    }

    public List<SitePage> listByTenant() {
        return baseDAO.select("1=1 ORDER BY path", Map.of());
    }

    public Optional<SitePage> findByKey(String key) {
        List<SitePage> found = baseDAO.select("page_key = :key", Map.of("key", key));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    public Optional<SitePage> findByPath(String path) {
        List<SitePage> found = baseDAO.select("path = :path", Map.of("path", path));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /**
     * 保存页面(按 page_key 幂等 upsert)。tenant_id 由框架从 TenantContext 注入。
     */
    @Transactional(rollbackFor = Exception.class)
    public SitePage savePage(SitePage page) {
        page.setTenantId(TenantContext.requireTenantId());
        findByKey(page.getPageKey()).ifPresent(existing -> page.setId(existing.getId()));
        return baseDAO.insertOrUpdate(page);
    }

    /**
     * 保存某语言版本:校验页面归属、清洗富文本、按 (page_id, language) 幂等 upsert。
     */
    @Transactional(rollbackFor = Exception.class)
    public SitePageI18n saveI18n(Long pageId, SitePageI18n i18n) {
        requireOwned(pageId);
        i18n.setPageId(pageId);
        i18n.setContent(sanitizer.clean(i18n.getContent()));
        findByLanguage(pageId, i18n.getLanguage()).ifPresent(existing -> i18n.setId(existing.getId()));
        return i18nDAO.insertOrUpdate(i18n);
    }

    /**
     * 展示用:按路径加载页面,取当前语言 i18n(缺失回退租户默认语言)。
     *
     * @return 页面 + 命中的语言 + i18n 内容(可能 empty,页面存在但无任何 i18n)
     */
    public ResolvedPage resolveForDisplay(String path, String language, String defaultLanguage) {
        SitePage page = findByPath(path)
                .orElseThrow(() -> new BizException("页面不存在: " + path));
        Map<String, SitePageI18n> byLanguage = loadI18nMap(page.getId());
        Optional<SitePageI18n> resolved = i18nService.resolve(byLanguage, language, defaultLanguage);
        String effectiveLanguage = resolved.map(SitePageI18n::getLanguage).orElse(defaultLanguage);
        return new ResolvedPage(page, resolved.orElse(null), effectiveLanguage);
    }

    public Map<String, SitePageI18n> loadI18nMap(Long pageId) {
        Map<String, SitePageI18n> map = new LinkedHashMap<>();
        for (SitePageI18n row : i18nDAO.select("page_id = :pageId", Map.of("pageId", pageId))) {
            map.put(row.getLanguage(), row);
        }
        return map;
    }

    Optional<SitePageI18n> findByLanguage(Long pageId, String language) {
        List<SitePageI18n> found = i18nDAO.select("page_id = :pageId AND language = :language",
                Map.of("pageId", pageId, "language", language));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    private SitePage requireOwned(Long pageId) {
        // selectById 已按 TenantContext 隔离(跨租户查不到),无需再手动比对 tenantId
        return baseDAO.selectById(pageId)
                .orElseThrow(() -> new BizException("页面不存在: id=" + pageId));
    }

    /** 展示结果:页面 + 命中语言 + i18n(可能为 null)。 */
    public record ResolvedPage(SitePage page, SitePageI18n i18n, String language) {
    }
}
