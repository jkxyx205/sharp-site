package com.rick.site.page.service;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * 富文本 HTML 清洗器(CLAUDE.md §9 / TASK-0404)。
 *
 * <p>富文本保存前必须清洗,移除脚本注入、事件处理器、恶意样式与已知 XSS 向量。
 * 采用 jsoup {@link Safelist#relaxed()} 策略:允许常规排版标签(p/br/ul/ol/li/strong/
 * em/h1-h6/table/img/a 等),禁止 script/style/object/embed/on* 事件属性与 javascript: 协议。
 *
 * <p>独立于具体业务实体,任何富文本字段(content / 富文本 page 等)保存前统一调用 {@link #clean}。
 *
 * @author Rick.Xu
 */
@Component
public class HtmlSanitizer {

    private static final Safelist SAFELIST = Safelist.relaxed()
            .addAttributes(":all", "class", "style")
            .addProtocols("a", "href", "http", "https", "mailto", "tel")
            .addProtocols("img", "src", "http", "https", "data")
            .preserveRelativeLinks(false);

    /**
     * 清洗富文本 HTML;null/空串原样返回。
     *
     * @param html 原始富文本(可能含恶意标签)
     * @return 清洗后的安全 HTML
     */
    public String clean(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        return Jsoup.clean(html, "", SAFELIST);
    }
}
