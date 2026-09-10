package com.rick.site.home.service;

import com.rick.common.http.exception.BizException;
import com.rick.db.plugin.BaseServiceImpl;
import com.rick.site.home.dao.HomeSectionDAO;
import com.rick.site.home.dao.HomeSectionI18nDAO;
import com.rick.site.home.entity.HomeSection;
import com.rick.site.home.entity.HomeSectionI18n;
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
 * 首页区块服务(TASK-0501)。
 *
 * <p>租户隔离:查询显式带 tenant_id;后台调用方传入 tenantId 做归属校验。
 * 富文本 content 保存前经 {@link com.rick.site.page.service.HtmlSanitizer} 清洗;
 * 展示时按当前语言取 i18n,缺失回退租户默认语言(TASK-0303)。
 *
 * @author Rick.Xu
 */
@Service
@Validated
public class HomeSectionService extends BaseServiceImpl<HomeSectionDAO, HomeSection, Long> {

    private final HomeSectionI18nDAO i18nDAO;
    private final com.rick.site.page.service.HtmlSanitizer sanitizer;
    private final I18nService i18nService;

    public HomeSectionService(HomeSectionDAO baseDAO, HomeSectionI18nDAO i18nDAO,
                              com.rick.site.page.service.HtmlSanitizer sanitizer, I18nService i18nService) {
        super(baseDAO);
        this.i18nDAO = i18nDAO;
        this.sanitizer = sanitizer;
        this.i18nService = i18nService;
    }

    public List<HomeSection> listByTenant() {
        return baseDAO.select("1=1 ORDER BY sort, id", Map.of());
    }

    public List<HomeSection> listEnabled() {
        return baseDAO.select("enabled = 1 ORDER BY sort, id", Map.of());
    }

    public Optional<HomeSection> findByKey(String key) {
        List<HomeSection> found = baseDAO.select("section_key = :key", Map.of("key", key));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Transactional(rollbackFor = Exception.class)
    public HomeSection saveSection(HomeSection section) {
        section.setTenantId(TenantContext.requireTenantId());
        findByKey(section.getSectionKey()).ifPresent(existing -> section.setId(existing.getId()));
        return baseDAO.insertOrUpdate(section);
    }

    @Transactional(rollbackFor = Exception.class)
    public HomeSectionI18n saveI18n(Long sectionId, HomeSectionI18n i18n) {
        requireOwned(sectionId);
        i18n.setSectionId(sectionId);
        i18n.setContent(sanitizer.clean(i18n.getContent()));
        findByLanguage(sectionId, i18n.getLanguage()).ifPresent(existing -> i18n.setId(existing.getId()));
        return i18nDAO.insertOrUpdate(i18n);
    }

    /**
     * 展示用:加载启用区块(排序),每块取当前语言 i18n(缺失回退默认语言),
     * 返回按 section_key 索引的映射(保持顺序)。
     */
    public Map<String, ResolvedSection> resolveForDisplay(String language, String defaultLanguage) {
        Map<String, ResolvedSection> map = new LinkedHashMap<>();
        for (HomeSection section : listEnabled()) {
            Map<String, HomeSectionI18n> byLanguage = loadI18nMap(section.getId());
            Optional<HomeSectionI18n> resolved = i18nService.resolve(byLanguage, language, defaultLanguage);
            String effectiveLanguage = resolved.map(HomeSectionI18n::getLanguage).orElse(defaultLanguage);
            map.put(section.getSectionKey(), new ResolvedSection(section, resolved.orElse(null), effectiveLanguage));
        }
        return map;
    }

    Map<String, HomeSectionI18n> loadI18nMap(Long sectionId) {
        Map<String, HomeSectionI18n> map = new LinkedHashMap<>();
        for (HomeSectionI18n row : i18nDAO.select("section_id = :sectionId", Map.of("sectionId", sectionId))) {
            map.put(row.getLanguage(), row);
        }
        return map;
    }

    Optional<HomeSectionI18n> findByLanguage(Long sectionId, String language) {
        List<HomeSectionI18n> found = i18nDAO.select("section_id = :sectionId AND language = :language",
                Map.of("sectionId", sectionId, "language", language));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    private HomeSection requireOwned(Long sectionId) {
        // selectById 已按 TenantContext 隔离(跨租户查不到),无需再手动比对 tenantId
        return baseDAO.selectById(sectionId)
                .orElseThrow(() -> new BizException("区块不存在: id=" + sectionId));
    }

    /** 展示结果:区块 + 命中语言 + i18n(可能为 null)。 */
    public record ResolvedSection(HomeSection section, HomeSectionI18n i18n, String language) {
    }
}
