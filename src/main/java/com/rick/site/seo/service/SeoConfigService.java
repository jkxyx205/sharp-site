package com.rick.site.seo.service;

import com.rick.common.http.exception.BizException;
import com.rick.db.plugin.BaseServiceImpl;
import com.rick.site.seo.dao.SeoConfigDAO;
import com.rick.site.seo.dto.SeoFallback;
import com.rick.site.seo.dto.SeoView;
import com.rick.site.seo.entity.SeoConfig;
import com.rick.site.tenant.context.TenantContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SEO 元数据服务(TASK-1001 / 1002)。
 *
 * <p>按 (page_type, page_id, language) 唯一;page_id 为空时按 IS NULL 匹配(首页/列表页)。
 * tenant_id 由框架从 TenantContext 注入,所有查询自动按租户隔离(CLAUDE.md §4)。
 * {@link #resolve} 按当前语言取,缺失回退租户默认语言;
 * {@link #resolveView} 进一步与内容回退合并,产出前台 head 片段所需的 {@link SeoView}。
 *
 * @author Rick.Xu
 */
@Service
@Validated
public class SeoConfigService extends BaseServiceImpl<SeoConfigDAO, SeoConfig, Long> {

    // page_type 取值约定(无枚举,避免过度设计;新增类型在此追加即可)
    public static final String HOME = "home";
    public static final String PAGE = "page";
    public static final String PRODUCT = "product";
    public static final String ARTICLE = "article";
    public static final String PRODUCTS_LIST = "products_list";
    public static final String NEWS_LIST = "news_list";

    private static final String DEFAULT_ROBOTS = "index, follow";

    public SeoConfigService(SeoConfigDAO baseDAO) {
        super(baseDAO);
    }

    /** 当前租户的全部 SEO 配置(tenant_id 由框架追加)。 */
    public List<SeoConfig> listByTenant() {
        return baseDAO.select("1=1 ORDER BY page_type, page_id, language", Map.of());
    }

    /** 精确按 (page_type, page_id, language) 查;page_id 为空用 IS NULL。 */
    public Optional<SeoConfig> find(String pageType, Long pageId, String language) {
        if (pageId == null) {
            List<SeoConfig> found = baseDAO.select(
                    "page_type = :pageType AND page_id IS NULL AND language = :language",
                    Map.of("pageType", pageType, "language", language));
            return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
        }
        List<SeoConfig> found = baseDAO.select(
                "page_type = :pageType AND page_id = :pageId AND language = :language",
                Map.of("pageType", pageType, "pageId", pageId, "language", language));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /** 按当前语言解析,缺失回退租户默认语言;两者皆无返回 null。 */
    public SeoConfig resolve(String pageType, Long pageId, String language, String defaultLanguage) {
        return find(pageType, pageId, language)
                .or(() -> find(pageType, pageId, defaultLanguage))
                .orElse(null);
    }

    /** 幂等 upsert:按 (page_type, page_id, language) 命中则更新,否则新增。 */
    @Transactional(rollbackFor = Exception.class)
    public SeoConfig save(SeoConfig config) {
        config.setTenantId(TenantContext.requireTenantId());
        find(config.getPageType(), config.getPageId(), config.getLanguage())
                .ifPresent(existing -> config.setId(existing.getId()));
        return baseDAO.insertOrUpdate(config);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        baseDAO.selectById(id).orElseThrow(() -> new BizException("SEO 配置不存在: id=" + id));
        baseDAO.deleteById(id);
    }

    /** 合并 seo_config 与内容回退,产出前台 head 片段所需的 {@link SeoView}。 */
    public SeoView resolveView(String pageType, Long pageId, String language,
                               String defaultLanguage, SeoFallback fallback) {
        SeoConfig c = resolve(pageType, pageId, language, defaultLanguage);
        String title = first(c, SeoConfig::getTitle, fallback.title());
        String description = first(c, SeoConfig::getDescription, fallback.description());
        String keywords = c != null && StringUtils.isNotBlank(c.getKeywords()) ? c.getKeywords() : "";
        String canonical = first(c, SeoConfig::getCanonical, fallback.canonical());
        String robots = c != null && StringUtils.isNotBlank(c.getRobots()) ? c.getRobots() : DEFAULT_ROBOTS;
        String ogTitle = first(c, SeoConfig::getOgTitle, title);
        String ogDescription = first(c, SeoConfig::getOgDescription, description);
        String ogImage = first(c, SeoConfig::getOgImage, fallback.image());
        return new SeoView(title, description, keywords, canonical, robots, ogTitle, ogDescription, ogImage);
    }

    private static String first(SeoConfig c, java.util.function.Function<SeoConfig, String> getter, String fallback) {
        if (c == null) {
            return fallback;
        }
        String v = getter.apply(c);
        return StringUtils.isNotBlank(v) ? v : fallback;
    }
}
