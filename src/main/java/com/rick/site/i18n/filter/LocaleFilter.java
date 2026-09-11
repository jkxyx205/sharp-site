package com.rick.site.i18n.filter;

import com.rick.site.i18n.context.LocaleContext;
import com.rick.site.i18n.model.LocaleResolution;
import com.rick.site.i18n.service.LocaleResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * 语言解析过滤器(ARCHITECTURE.md §3:LocaleResolver → Controller)。
 *
 * <p>在 {@code TenantFilter}(@Order(-200))之后运行,从请求路径解析语言并写入 {@link LocaleContext}
 * 与 Spring {@link LocaleContextHolder}(后者供 Thymeleaf {@code #{}} 按 locale 解析);
 * finally 无条件 clear,保证线程复用不残留语言。
 *
 * @author Rick.Xu
 */
@Component
@Order(20)
public class LocaleFilter extends OncePerRequestFilter {

    private final LocaleResolver localeResolver;

    public LocaleFilter(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            LocaleResolution res = localeResolver.resolve(request);
            LocaleContext.set(res);
            LocaleContextHolder.setLocale(Locale.forLanguageTag(res.language()));
            filterChain.doFilter(request, response);
        } finally {
            LocaleContext.clear();
            LocaleContextHolder.resetLocaleContext();
        }
    }
}
