package com.rick.site.publish.service;

import org.thymeleaf.context.AbstractContext;
import org.thymeleaf.context.IWebContext;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.IWebRequest;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 离线渲染用的最小 {@link IWebContext}(ARCHITECTURE §9 静态化)。
 *
 * <p>静态生成无 Servlet 请求,但 themes 模板的 {@code @{...}} 链接表达式经
 * {@code StandardLinkBuilder} 计算上下文路径时要求 Context 实现 IWebContext
 * (否则抛 "cannot be context relative")。本类报告空 applicationPath,使
 * {@code @{/products}} 渲染为根相对 {@code /products},适配 Nginx 根目录静态站点。
 *
 * <p><b>变量可见性关键</b>:Thymeleaf 的 {@code WebEngineContext.getVariable} 从
 * {@code exchange.getAttributeValue} 取值,而非 context 的变量 map。故本类的
 * {@link OfflineExchange} 将 attribute 读写委托回本 context 的变量集,使
 * {@code ctx.setVariable(...)} 设置的变量对模板/片段可见。
 *
 * @author Rick.Xu
 */
public class OfflineWebContext extends AbstractContext implements IWebContext {

    private final OfflineExchange exchange;

    public OfflineWebContext() {
        super();
        this.exchange = new OfflineExchange();
    }

    public OfflineWebContext(Locale locale) {
        super(locale);
        this.exchange = new OfflineExchange();
    }

    public OfflineWebContext(Locale locale, Map<String, Object> variables) {
        super(locale, variables);
        this.exchange = new OfflineExchange();
    }

    @Override
    public IWebExchange getExchange() {
        return exchange;
    }

    /** 把 exchange 属性读写桥接到本 context 的变量集(Thymeleaf WebEngineContext 经 exchange 取变量)。 */
    private final class OfflineExchange implements IWebExchange {

        private final IWebRequest request = new OfflineRequest();

        @Override
        public Object getAttributeValue(String name) {
            return OfflineWebContext.this.getVariable(name);
        }

        @Override
        public boolean containsAttribute(String name) {
            return OfflineWebContext.this.containsVariable(name);
        }

        @Override
        public int getAttributeCount() {
            return OfflineWebContext.this.getVariableNames().size();
        }

        @Override
        public Set<String> getAllAttributeNames() {
            return OfflineWebContext.this.getVariableNames();
        }

        @Override
        public Map<String, Object> getAttributeMap() {
            Map<String, Object> map = new java.util.HashMap<>();
            OfflineWebContext.this.getVariableNames()
                    .forEach(n -> map.put(n, OfflineWebContext.this.getVariable(n)));
            return map;
        }

        @Override
        public void setAttributeValue(String name, Object value) {
            OfflineWebContext.this.setVariable(name, value);
        }

        @Override
        public void removeAttribute(String name) {
            OfflineWebContext.this.removeVariable(name);
        }

        @Override
        public IWebRequest getRequest() {
            return request;
        }

        @Override
        public String transformURL(String url) {
            return url;
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }

        // 以下方法模板渲染不触及;返回 null/默认值。

        @Override
        public org.thymeleaf.web.IWebSession getSession() {
            return null;
        }

        @Override
        public org.thymeleaf.web.IWebApplication getApplication() {
            return null;
        }

        @Override
        public boolean hasSession() {
            return false;
        }

        @Override
        public java.security.Principal getPrincipal() {
            return null;
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public String getCharacterEncoding() {
            return null;
        }
    }

    /** 最小请求:applicationPath=""(根相对链接)。其余返回安全默认。 */
    private static final class OfflineRequest implements IWebRequest {

        @Override
        public String getApplicationPath() {
            return "";
        }

        @Override
        public String getMethod() {
            return "GET";
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public String getScheme() {
            return "https";
        }

        @Override
        public String getServerName() {
            return "localhost";
        }

        @Override
        public Integer getServerPort() {
            return 443;
        }

        @Override
        public String getPathWithinApplication() {
            return "/";
        }

        @Override
        public String getQueryString() {
            return null;
        }

        @Override
        public boolean containsHeader(String name) {
            return false;
        }

        @Override
        public int getHeaderCount() {
            return 0;
        }

        @Override
        public Set<String> getAllHeaderNames() {
            return java.util.Collections.emptySet();
        }

        @Override
        public Map<String, String[]> getHeaderMap() {
            return java.util.Collections.emptyMap();
        }

        @Override
        public String[] getHeaderValues(String name) {
            return new String[0];
        }

        @Override
        public boolean containsParameter(String name) {
            return false;
        }

        @Override
        public int getParameterCount() {
            return 0;
        }

        @Override
        public Set<String> getAllParameterNames() {
            return java.util.Collections.emptySet();
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return java.util.Collections.emptyMap();
        }

        @Override
        public String[] getParameterValues(String name) {
            return new String[0];
        }

        @Override
        public boolean containsCookie(String name) {
            return false;
        }

        @Override
        public int getCookieCount() {
            return 0;
        }

        @Override
        public Set<String> getAllCookieNames() {
            return java.util.Collections.emptySet();
        }

        @Override
        public Map<String, String[]> getCookieMap() {
            return java.util.Collections.emptyMap();
        }

        @Override
        public String[] getCookieValues(String name) {
            return new String[0];
        }
    }
}
