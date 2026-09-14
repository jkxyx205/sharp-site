package com.rick.site.seo.service;

import com.rick.common.http.exception.BizException;
import com.rick.db.plugin.BaseServiceImpl;
import com.rick.site.seo.dao.SeoConfigDAO;
import com.rick.site.seo.dto.SeoFallback;
import com.rick.site.seo.dto.SeoView;
import com.rick.site.seo.entity.SeoConfig;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.theme.model.ThemeManifest.ThemePage;
import com.rick.site.theme.model.ThemeManifest.ThemePageSeo;
import com.rick.site.theme.service.ThemeManifestResolver;
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
 * {@link #resolveView} 进一步与主题清单默认值及内容回退合并,产出前台 head 片段所需的 {@link SeoView}。
 *
 * <p>优先级链:DB seo_config > theme.json 的 page seo_config > 内容回退 {@link SeoFallback}。
 * 仅站点页(路径型 page_type,page_id 为空)在 theme.json 有条目;产品/新闻详情(page_type=product/article)
 * 在清单中无条目,主题默认为 null,行为不变。
 *
 * @author Rick.Xu
 */
@Service
@Validated
public class SeoConfigService extends BaseServiceImpl<SeoConfigDAO, SeoConfig, Long> {

    // page_type 取值约定:站点页面(首页/列表页/静态页)取其路径("/", "/products"、
    // "/news"、"/about"、"/contact"),page_id 恒为空;仅产品/新闻详情用下列常量 + page_id。
    public static final String PRODUCT = "product";
    public static final String ARTICLE = "article";

    private static final String DEFAULT_ROBOTS = "index, follow";

    private final ThemeManifestResolver manifestResolver;

    public SeoConfigService(SeoConfigDAO baseDAO, ThemeManifestResolver manifestResolver) {
        super(baseDAO);
        this.manifestResolver = manifestResolver;
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

    /**
     * 合并 DB seo_config、主题清单默认值与内容回退,产出前台 head 片段所需的 {@link SeoView}。
     *
     * <p>每个字段三层级联:DB > theme.json page seo_config > 内容回退 {@link SeoFallback}
     * (canonical/ogImage 回退内容回退;robots 兜底 "index, follow";ogTitle/ogDescription
     * 再回退到已解析出的 title/description)。主题字段留空即跳过,逐级向下回退。
     */
    public SeoView resolveView(String pageType, Long pageId, String language,
                               String defaultLanguage, SeoFallback fallback) {
        SeoConfig c = resolve(pageType, pageId, language, defaultLanguage);
        ThemePageSeo theme = themeDefault(pageType, language, defaultLanguage);
        // 先把主题默认与内容回退合并成 effective fallback,DB 缺失时逐级回退
        String fbTitle = or(theme, ThemePageSeo::title, fallback.title());
        String fbDesc = or(theme, ThemePageSeo::description, fallback.description());
        String fbKeywords = or(theme, ThemePageSeo::keywords, "");
        String title = first(c, SeoConfig::getTitle, fbTitle);
        String description = first(c, SeoConfig::getDescription, fbDesc);
        String keywords = c != null && StringUtils.isNotBlank(c.getKeywords()) ? c.getKeywords() : fbKeywords;
        String canonical = first(c, SeoConfig::getCanonical,
                or(theme, ThemePageSeo::canonical, fallback.canonical()));
        String robots = c != null && StringUtils.isNotBlank(c.getRobots())
                ? c.getRobots() : or(theme, ThemePageSeo::robots, DEFAULT_ROBOTS);
        // ogTitle/ogDescription:DB > 主题 > 已解析的 title/description
        String ogTitle = first(c, SeoConfig::getOgTitle, or(theme, ThemePageSeo::ogTitle, title));
        String ogDescription = first(c, SeoConfig::getOgDescription,
                or(theme, ThemePageSeo::ogDescription, description));
        String ogImage = first(c, SeoConfig::getOgImage,
                or(theme, ThemePageSeo::ogImage, fallback.image()));
        return new SeoView(title, description, keywords, canonical, robots, ogTitle, ogDescription, ogImage);
    }

    private static String or(ThemePageSeo theme, java.util.function.Function<ThemePageSeo, String> getter, String fallback) {
        if (theme == null) {
            return fallback;
        }
        String v = getter.apply(theme);
        return StringUtils.isNotBlank(v) ? v : fallback;
    }

    /**
     * 取主题清单中该页的 SEO 默认值:按 pageType==路径 命中站点页,再按语种(缺失回退默认语种)。
     * 产品/新闻详情(page_type=product/article)在清单中无条目,返回 null。无租户/解析异常返回 null。
     */
    private ThemePageSeo themeDefault(String pageType, String language, String defaultLanguage) {
        if (pageType == null) {
            return null;
        }
        return TenantContext.get().map(tenant -> {
            try {
                ThemePage page = manifestResolver.resolve(tenant).pages().stream()
                        .filter(p -> pageType.equals(p.path()))
                        .findFirst()
                        .orElse(null);
                return page == null ? null : page.seoFor(language, defaultLanguage).orElse(null);
            } catch (Exception e) {
                return null;
            }
        }).orElse(null);
    }

    private static String first(SeoConfig c, java.util.function.Function<SeoConfig, String> getter, String fallback) {
        if (c == null) {
            return fallback;
        }
        String v = getter.apply(c);
        return StringUtils.isNotBlank(v) ? v : fallback;
    }
}
