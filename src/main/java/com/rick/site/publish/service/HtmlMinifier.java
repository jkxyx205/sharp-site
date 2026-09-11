package com.rick.site.publish.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 压缩器:发布时压缩静态页面体积。
 *
 * <p>处理:
 * <ol>
 *   <li>删除 HTML 注释 {@code <!-- ... -->}(每页重复的片段说明注释、SEO 注释等)。</li>
 *   <li>折叠连续空白(含缩进、换行、空行)为单个空格。</li>
 *   <li>对内联 {@code <style>} 内容做轻量 CSS 压缩:删 CSS 注释 + 折叠空白。</li>
 * </ol>
 *
 * <p>原样保留 {@code <pre>}/{@code <textarea>}/{@code <script>} 内容——这些元素中空白有语义
 * (预格式化文本、JS 字符串等),压缩会破坏含义。{@code <style>} 的 CSS 空白无语义,
 * 仅折叠空白与删注释(保留 {@code margin: 0 0 12px} 等值内单空格),不触碰选择器/标点,
 * 故不改变渲染。
 *
 * <p>策略保守:HTML 空白仅折叠为单个空格,不删除行内元素间单空格(浏览器本就如此折叠空白),
 * 因此属性值内单空格、导航链接间距等语义不变。
 *
 * @author Rick.Xu
 */
final class HtmlMinifier {

    /** 需提取的元素:pre/textarea/script 原样保留;style 单独做 CSS 压缩。 */
    private static final Pattern PRESERVE = Pattern.compile(
            "<(pre|textarea|script|style)\\b[^>]*>[\\s\\S]*?</\\1\\s*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT = Pattern.compile("<!--[\\s\\S]*?-->");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /** CSS 注释(不嵌套,可整块删除)。 */
    private static final Pattern CSS_COMMENT = Pattern.compile("/\\*[\\s\\S]*?\\*/");
    /**
     * 占位符定界:正文几乎不会出现该串,避免与正文文本相撞;且不含空白,不被空白折叠波及,
     * 也不是 HTML 注释,故能安全穿越注释删除与空白折叠两步。前后定界避免索引互为前缀。
     */
    private static final String PH_PREFIX = "SHARP-MINIFY-SLOT-";
    private static final String PH_SUFFIX = "-END";

    private HtmlMinifier() {
    }

    static String minify(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        // 1. 抽出需保留/压缩的块,避免被 HTML 注释删除与空白折叠波及。
        List<String> preserved = new ArrayList<>();
        Matcher m = PRESERVE.matcher(html);
        StringBuilder sb = new StringBuilder(html.length());
        while (m.find()) {
            preserved.add(transformBlock(m.group(1), m.group()));
            m.appendReplacement(sb, PH_PREFIX + (preserved.size() - 1) + PH_SUFFIX);
        }
        m.appendTail(sb);
        String out = sb.toString();
        // 2. 删除 HTML 注释。
        out = COMMENT.matcher(out).replaceAll("");
        // 3. 折叠连续空白为单个空格。
        out = WHITESPACE.matcher(out).replaceAll(" ");
        // 4. 还原块(定界符保证不误伤正文)。
        for (int i = preserved.size() - 1; i >= 0; i--) {
            out = out.replace(PH_PREFIX + i + PH_SUFFIX, preserved.get(i));
        }
        return out.trim();
    }

    /** style 块做轻量 CSS 压缩(删注释 + 折叠空白);其余元素原样保留。 */
    private static String transformBlock(String tag, String block) {
        if (!"style".equalsIgnoreCase(tag)) {
            return block;
        }
        // 提取 <style ...>BODY</style> 的 BODY 单独压缩,标签属性原样保留。
        int closeTag = block.indexOf('>');
        int openEnd = block.lastIndexOf("</");
        if (closeTag < 0 || openEnd < 0 || openEnd <= closeTag) {
            return block; // 结构异常,不冒险压缩
        }
        String head = block.substring(0, closeTag + 1);
        String body = block.substring(closeTag + 1, openEnd);
        String tail = block.substring(openEnd);
        body = CSS_COMMENT.matcher(body).replaceAll("");
        body = WHITESPACE.matcher(body).replaceAll(" ").trim();
        return head + body + tail;
    }
}
