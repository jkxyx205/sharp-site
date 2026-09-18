package com.rick.site.video.service;

import com.rick.common.http.exception.BizException;
import com.rick.db.plugin.BaseServiceImpl;
import com.rick.site.catalog.service.CategoryService;
import com.rick.site.i18n.service.I18nService;
import com.rick.site.page.service.HtmlSanitizer;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.video.dao.VideoDAO;
import com.rick.site.video.dao.VideoI18nDAO;
import com.rick.site.video.entity.Video;
import com.rick.site.video.entity.VideoI18n;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;

/**
 * 视频服务(后台 CRUD + 多语言 i18n;参照 {@link com.rick.site.product.service.ProductService},
 * 无规格 JSON 处理)。
 *
 * <p>租户隔离:查询显式带 tenant_id;按 slug 幂等 upsert,按 (video_id, language) 幂等 upsert i18n。
 * 富文本 content 保存前经 {@link HtmlSanitizer} 清洗。
 *
 * @author Rick.Xu
 */
@Service
@Validated
public class VideoService extends BaseServiceImpl<VideoDAO, Video, Long> {

    private final VideoI18nDAO i18nDAO;
    private final HtmlSanitizer sanitizer;
    private final I18nService i18nService;
    private final CategoryService categoryService;

    public VideoService(VideoDAO baseDAO, VideoI18nDAO i18nDAO, HtmlSanitizer sanitizer,
                        I18nService i18nService, CategoryService categoryService) {
        super(baseDAO);
        this.i18nDAO = i18nDAO;
        this.sanitizer = sanitizer;
        this.i18nService = i18nService;
        this.categoryService = categoryService;
    }

    public List<Video> listByTenant() {
        // tenant_id 由 SiteDatabaseConfig 统一追加;此处仅保留排序占位
        return baseDAO.select("1=1 ORDER BY sort, id", Map.of());
    }

    public List<Video> listEnabled() {
        return baseDAO.select("status = 1 ORDER BY sort, id", Map.of());
    }

    /** 后台按分类筛选(租户隔离由 SiteDatabaseConfig 统一追加)。 */
    public List<Video> listByCategory(Long categoryId) {
        return baseDAO.select("category_id = :categoryId ORDER BY sort, id",
                Map.of("categoryId", categoryId));
    }

    public Optional<Video> findBySlug(String slug) {
        List<Video> found = baseDAO.select("slug = :slug", Map.of("slug", slug));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Transactional(rollbackFor = Exception.class)
    public Video saveVideo(Video video) {
        video.setTenantId(TenantContext.requireTenantId());
        findBySlug(video.getSlug()).ifPresent(existing -> video.setId(existing.getId()));
        return baseDAO.insertOrUpdate(video);
    }

    @Transactional(rollbackFor = Exception.class)
    public VideoI18n saveI18n(Long videoId, VideoI18n i18n) {
        requireOwned(videoId);
        i18n.setVideoId(videoId);
        i18n.setContent(sanitizer.clean(i18n.getContent()));
        findByLanguage(videoId, i18n.getLanguage()).ifPresent(existing -> i18n.setId(existing.getId()));
        return i18nDAO.insertOrUpdate(i18n);
    }

    /** 逻辑删除:校验归属(跨租户查不到)后置 is_deleted。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long videoId) {
        requireOwned(videoId);
        baseDAO.deleteById(videoId);
    }

    /**
     * 列表展示(上架视频):仅返回在指定语种已维护 i18n 内容的视频。
     *
     * <p>多语言语义与 {@link com.rick.site.product.service.ProductService#listForDisplay} 一致:
     * 某视频在当前语种无 i18n 行 → 不展示。categorySlug/categoryName 来自视频所属 VIDEO 分类。
     */
    public List<ResolvedVideo> listForDisplay(String language, String defaultLanguage) {
        Map<Long, CategoryService.ResolvedCategory> catById = resolvedCategoryById(language, defaultLanguage);
        List<ResolvedVideo> out = new ArrayList<>();
        for (Video video : listEnabled()) {
            Map<String, VideoI18n> byLanguage = loadI18nMap(video.getId());
            Optional<VideoI18n> resolved = i18nService.resolve(byLanguage, language, defaultLanguage);
            if (resolved.isPresent() && language.equals(resolved.get().getLanguage())) {
                CategoryInfo ci = categoryInfo(catById, video.getCategoryId());
                out.add(new ResolvedVideo(video, resolved.get(), resolved.get().getLanguage(),
                        ci.slug(), ci.name()));
            }
        }
        return out;
    }

    /** 单视频展示:按 slug 查视频 + i18n(缺失回退默认语言)。与产品详情语义一致。 */
    public ResolvedVideo resolveForDisplay(String slug, String language, String defaultLanguage) {
        Video video = findBySlug(slug)
                .orElseThrow(() -> new BizException("视频不存在: " + slug));
        Map<String, VideoI18n> byLanguage = loadI18nMap(video.getId());
        Optional<VideoI18n> resolved = i18nService.resolve(byLanguage, language, defaultLanguage);
        String effectiveLanguage = resolved.map(VideoI18n::getLanguage).orElse(defaultLanguage);
        CategoryInfo ci = categoryInfo(resolvedCategoryById(language, defaultLanguage), video.getCategoryId());
        return new ResolvedVideo(video, resolved.orElse(null), effectiveLanguage, ci.slug(), ci.name());
    }

    /** 取所有启用 VIDEO 分类的 id→已解析(含 i18n 名称)映射,供视频解析 categorySlug/categoryName。 */
    private Map<Long, CategoryService.ResolvedCategory> resolvedCategoryById(String language, String defaultLanguage) {
        Map<Long, CategoryService.ResolvedCategory> map = new HashMap<>();
        for (CategoryService.ResolvedCategory rc
                : categoryService.resolveForDisplay("VIDEO", language, defaultLanguage).values()) {
            map.put(rc.category().getId(), rc);
        }
        return map;
    }

    /**
     * 取启用 VIDEO 分类的展示视图(slug + 已解析 i18n 名称),供视频页按分类遍历分组。
     * 与 {@link com.rick.site.product.service.ProductService#listCategoryViews} 对齐(VIDEO 类型)。
     */
    public List<CategoryView> listCategoryViews(String language, String defaultLanguage) {
        List<CategoryView> out = new ArrayList<>();
        for (CategoryService.ResolvedCategory rc
                : categoryService.resolveForDisplay("VIDEO", language, defaultLanguage).values()) {
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

    /** 已解析视频(Video + 命中语种 i18n + 分类展示信息),供 {@link com.rick.site.video.dto.VideoView#from} 构建。 */
    public record ResolvedVideo(Video video, VideoI18n i18n, String language,
                                String categorySlug, String categoryName) {
    }

    /** 分类展示视图(slug + 已解析 i18n 名称),用于视频页按分类遍历分组。 */
    public record CategoryView(String slug, String name) {
    }

    public Map<String, VideoI18n> loadI18nMap(Long videoId) {
        Map<String, VideoI18n> map = new LinkedHashMap<>();
        for (VideoI18n row : i18nDAO.select("video_id = :videoId", Map.of("videoId", videoId))) {
            map.put(row.getLanguage(), row);
        }
        return map;
    }

    Optional<VideoI18n> findByLanguage(Long videoId, String language) {
        List<VideoI18n> found = i18nDAO.select("video_id = :videoId AND language = :language",
                Map.of("videoId", videoId, "language", language));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    private Video requireOwned(Long videoId) {
        // selectById 已按 TenantContext 隔离(跨租户查不到),无需再手动比对 tenantId
        return baseDAO.selectById(videoId)
                .orElseThrow(() -> new BizException("视频不存在: id=" + videoId));
    }
}
