package com.rick.site.theme.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rick.common.http.exception.BizException;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.theme.model.ThemeManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 主题清单解析器(REQUIREMENTS.md §26.1)。
 *
 * <p>按 {@link ThemeResolver} 解析 {@code themeId},从 classpath
 * {@code templates/themes/{themeId}/meta/theme.json} 加载 {@link ThemeManifest},按主题缓存。
 *
 * <p>语种与默认语种一律取自主题,与租户无关;租户选定 {@code theme_id} 即等于选定语种集合。
 *
 * @author Rick.Xu
 */
@Component
public class ThemeManifestResolver {

    private static final Logger log = LoggerFactory.getLogger(ThemeManifestResolver.class);

    private final ThemeResolver themeResolver;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final ConcurrentMap<String, ThemeManifest> cache = new ConcurrentHashMap<>();

    public ThemeManifestResolver(ThemeResolver themeResolver, ObjectMapper objectMapper,
                                ResourceLoader resourceLoader) {
        this.themeResolver = themeResolver;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    /** 解析租户当前主题的清单。 */
    public ThemeManifest resolve(Tenant tenant) {
        String themeId = themeResolver.resolveTheme(tenant);
        return resolveByThemeId(themeId);
    }

    /** 按主题 ID 取清单(缓存)。 */
    public ThemeManifest resolveByThemeId(String themeId) {
        return cache.computeIfAbsent(themeId, this::load);
    }

    /** 租户主题的默认语种(取代 {@code tenant.defaultLanguage})。 */
    public String defaultLocale(Tenant tenant) {
        return resolve(tenant).defaultLocale();
    }

    /** 租户主题的启用语种列表(取代平台固定列表,供后台/语言切换遍历)。 */
    public List<String> locales(Tenant tenant) {
        return resolve(tenant).locales();
    }

    private ThemeManifest load(String themeId) {
        Resource res = resourceLoader.getResource(
                "classpath:templates/themes/" + themeId + "/meta/theme.json");
        if (!res.exists()) {
            throw new BizException("主题清单缺失: themes/" + themeId + "/meta/theme.json");
        }
        try (InputStream in = res.getInputStream()) {
            return objectMapper.readValue(in, ThemeManifest.class);
        } catch (IOException e) {
            log.error("主题清单解析失败: themes/{}/meta/theme.json", themeId, e);
            throw new BizException("主题清单解析失败: themes/" + themeId + "/meta/theme.json");
        }
    }
}
