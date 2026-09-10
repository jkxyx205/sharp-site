package com.rick.site.i18n.service;

import com.rick.site.i18n.model.LocaleResolution;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 默认 LocaleResolver 实现(TASK-0301)。
 *
 * <p>规则:路径首段若匹配支持的语言(大小写不敏感,如 {@code /zh-cn/...}),取该语言并剥去前缀;
 * 否则取 {@link Tenant#getDefaultLanguage()}(无租户回退 {@link #PLATFORM_DEFAULT})。
 *
 * <p>Phase 2 支持语言(架构允许后续在 {@link #SUPPORTED_LANGUAGES} 扩展):
 * zh-CN、en-US。未来可加 de-DE、fr-FR 等(REQUIREMENTS.md §13)。
 *
 * @author Rick.Xu
 */
@Component
public class DefaultLocaleResolver implements LocaleResolver {

    /** Phase 2 支持语言;新增语言在此追加即可。 */
    static final List<String> SUPPORTED_LANGUAGES = List.of("zh-CN", "en-US");

    /** 无租户时的平台默认语言。 */
    static final String PLATFORM_DEFAULT = "en-US";

    @Override
    public LocaleResolution resolve(HttpServletRequest request) {
        String path = request.getRequestURI();
        String defaultLanguage = TenantContext.get()
                .map(Tenant::getDefaultLanguage)
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
