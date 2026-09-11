package com.rick.site.i18n.service;

import com.rick.site.i18n.model.LocaleResolution;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.theme.model.ThemeManifest;
import com.rick.site.theme.service.ThemeManifestResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 默认 LocaleResolver 实现(TASK-0301,Phase 18 修订)。
 *
 * <p>规则:路径首段若匹配支持的语言(大小写不敏感,如 {@code /zh-cn/...}),取该语言并剥去前缀;
 * 否则取主题默认语种(来自 {@code themes/{themeId}/meta/theme.json},无租户回退 {@link #PLATFORM_DEFAULT})。
 *
 * <p>语种与多语言判定的唯一来源是 Theme(§26.1):{@link #supportedLanguages()} 返回当前租户主题
 * 的 {@code theme.json.locales}(不再返回平台固定列表),后台编辑/语言切换据此遍历。
 *
 * <p>平台支持语种仍为 {@link #SUPPORTED_LANGUAGES}(zh-CN / en-US),作为 URL 段识别白名单,
 * 架构允许后续扩展(§13)。{@code theme.json.locales} 必须是其子集。
 *
 * @author Rick.Xu
 */
@Component
public class DefaultLocaleResolver implements LocaleResolver {

    /** Phase 2 平台支持语言;URL 段识别白名单。新增语言在此追加即可。 */
    static final List<String> SUPPORTED_LANGUAGES = List.of("zh-CN", "en-US");

    /** 无租户时的平台默认语言。 */
    static final String PLATFORM_DEFAULT = "en-US";

    private final ThemeManifestResolver manifestResolver;

    public DefaultLocaleResolver(ThemeManifestResolver manifestResolver) {
        this.manifestResolver = manifestResolver;
    }

    /**
     * 当前租户主题启用的语种(供后台多语言编辑遍历与语言切换);无租户回退平台列表。
     */
    public List<String> supportedLanguages() {
        return currentManifest()
                .map(ThemeManifest::locales)
                .filter(locs -> locs != null && !locs.isEmpty())
                .orElse(SUPPORTED_LANGUAGES);
    }

    /** 当前租户主题的默认语种(取代 {@code tenant.defaultLanguage});无租户回退平台默认。 */
    public String defaultLanguage() {
        return currentManifest()
                .map(ThemeManifest::defaultLocale)
                .filter(lang -> SUPPORTED_LANGUAGES.contains(normalize(lang)))
                .map(DefaultLocaleResolver::normalize)
                .orElse(PLATFORM_DEFAULT);
    }

    @Override
    public LocaleResolution resolve(HttpServletRequest request) {
        String path = request.getRequestURI();
        String defaultLanguage = currentManifest()
                .map(ThemeManifest::defaultLocale)
                .filter(lang -> SUPPORTED_LANGUAGES.contains(normalize(lang)))
                .map(DefaultLocaleResolver::normalize)
                .orElse(PLATFORM_DEFAULT);

        // 路径首段:/{locale}/ 或 /{locale}
        String remaining = path;
        if (path.length() > 1 && path.charAt(1) != '/') {
            int slash = path.indexOf('/', 1);
            String firstSegment = slash < 0 ? path.substring(1) : path.substring(1, slash);
            String matched = matchLanguage(firstSegment);
            if (matched != null) {
                String after = slash < 0 ? "/" : path.substring(slash);
                if (after.isEmpty()) {
                    after = "/";
                }
                return new LocaleResolution(matched, after);
            }
        }
        return new LocaleResolution(defaultLanguage, remaining);
    }

    /** 当前租户的主题清单(无租户/解析失败回退 empty)。 */
    private Optional<ThemeManifest> currentManifest() {
        return TenantContext.get().flatMap(t -> {
            try {
                return Optional.of(manifestResolver.resolve(t));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    /**
     * 路径段大小写不敏感匹配支持语言:zh-cn / zh-CN → zh-CN。
     *
     * @return 规范语言标签,或不匹配返回 null
     */
    private String matchLanguage(String segment) {
        if (segment == null || segment.isEmpty()) {
            return null;
        }
        String lower = segment.toLowerCase(Locale.ROOT);
        for (String lang : SUPPORTED_LANGUAGES) {
            if (lang.toLowerCase(Locale.ROOT).equals(lower)) {
                return lang;
            }
        }
        return null;
    }

    /** 规范化语言标签:zh-cn → zh-CN(首段小写-后段大写)。 */
    private static String normalize(String language) {
        int idx = language.indexOf('-');
        if (idx < 0 || idx == language.length() - 1) {
            return language.toLowerCase(Locale.ROOT);
        }
        return language.substring(0, idx).toLowerCase(Locale.ROOT)
                + "-"
                + language.substring(idx + 1).toUpperCase(Locale.ROOT);
    }
}
