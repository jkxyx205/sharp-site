package com.rick.site.theme.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 主题级 {@link MessageSource}(REQUIREMENTS.md §26.3)。
 *
 * <p>解析 Thymeleaf {@code #{key}} 文案,译文来自 {@code themes/{themeId}/meta/messages.json}
 * (JSON,结构 {@code {key: {locale: text}}})。注册为名为 {@code messageSource} 的 Bean,
 * Spring Boot 自动让 {@code SpringTemplateEngine} 使用本类解析 {@code #{}}。
 *
 * <p>解析顺序:当前主题文案 {@code [code][locale]} → {@code [code][defaultLocale]} → 返回 null
 * (交由调用方/Thymeleaf 默认 {@code ??code??} 处理,不抛异常以保证发布不中断)。
 *
 * <p>主题来源:动态请求由 {@link LocaleContextHolder} + {@link TenantContext};离线渲染由
 * {@code PublishService} 设置的 {@link TenantContext}。locale 由 Thymeleaf 上下文传入
 * (动态请求为 {@link LocaleContextHolder},离线为 {@code OfflineWebContext(locale)})。
 *
 * @author Rick.Xu
 */
@Component("messageSource")
public class ThemeMessageSource implements MessageSource {

    private static final Logger log = LoggerFactory.getLogger(ThemeMessageSource.class);

    private final ThemeManifestResolver manifestResolver;
    private final ThemeResolver themeResolver;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final ConcurrentMap<String, Map<String, Map<String, String>>> cache = new ConcurrentHashMap<>();

    public ThemeMessageSource(ThemeManifestResolver manifestResolver, ThemeResolver themeResolver,
                             ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.manifestResolver = manifestResolver;
        this.themeResolver = themeResolver;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
        String found = lookup(code, locale);
        return found != null ? found : defaultMessage;
    }

    @Override
    public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
        String found = lookup(code, locale);
        if (found == null) {
            throw new NoSuchMessageException(code, locale);
        }
        return found;
    }

    @Override
    public String getMessage(org.springframework.context.MessageSourceResolvable resolvable,
                             Locale locale) throws NoSuchMessageException {
        String[] codes = resolvable.getCodes();
        if (codes != null) {
            for (String code : codes) {
                String found = lookup(code, locale);
                if (found != null) {
                    return found;
                }
            }
        }
        String def = resolvable.getDefaultMessage();
        if (def != null) {
            return def;
        }
        String primary = (codes != null && codes.length > 0) ? codes[0] : null;
        throw new NoSuchMessageException(primary, locale);
    }

    /**
     * 查找文案。无当前主题 / 文案缺失时返回 null(由调用方处理)。
     */
    private String lookup(String code, Locale locale) {
        if (code == null) {
            return null;
        }
        String themeId = currentThemeId();
        if (themeId == null) {
            return null;
        }
        Map<String, Map<String, String>> bundle = bundle(themeId);
        if (bundle.isEmpty()) {
            return null;
        }
        Map<String, String> entry = bundle.get(code);
        if (entry == null) {
            return null;
        }
        String text = entry.get(localeToTag(locale));
        if (text == null) {
            String defaultLocale = manifestResolver.resolveByThemeId(themeId).defaultLocale();
            text = entry.get(defaultLocale);
        }
        return text;
    }

    private String currentThemeId() {
        Optional<Tenant> t = TenantContext.get();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return themeResolver.resolveTheme(t.get());
        } catch (Exception e) {
            log.debug("解析主题失败,文案回退 null: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Map<String, String>> bundle(String themeId) {
        return cache.computeIfAbsent(themeId, this::loadBundle);
    }

    private Map<String, Map<String, String>> loadBundle(String themeId) {
        Resource res = resourceLoader.getResource(
                "classpath:templates/themes/" + themeId + "/meta/messages.json");
        if (!res.exists()) {
            return Map.of();
        }
        try (InputStream in = res.getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<Map<String, Map<String, String>>>() {});
        } catch (IOException e) {
            log.warn("主题文案加载失败: themes/{}/meta/messages.json", themeId, e);
            return Map.of();
        }
    }

    /** {@link Locale} → 规范语种标签(zh-CN / en-US);{@link Locale#ROOT} → 空串。 */
    private static String localeToTag(Locale locale) {
        if (locale == null) {
            return "";
        }
        String tag = locale.toLanguageTag();
        return "und".equals(tag) ? "" : tag;
    }
}
