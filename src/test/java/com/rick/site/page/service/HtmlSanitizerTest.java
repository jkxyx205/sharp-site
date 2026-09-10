package com.rick.site.page.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-0404 验收测试:HTML 清洗器移除 XSS 向量,保留合法富文本。
 */
class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @Test
    void stripsScriptTag() {
        String out = sanitizer.clean("<p>hi</p><script>alert(1)</script>");
        assertThat(out).contains("<p>hi</p>").doesNotContain("<script").doesNotContain("alert");
    }

    @Test
    void stripsEventHandlers() {
        String out = sanitizer.clean("<p onclick=\"alert(1)\">hi</p>");
        assertThat(out).doesNotContain("onclick");
    }

    @Test
    void stripsJavascriptProtocol() {
        String out = sanitizer.clean("<a href=\"javascript:alert(1)\">x</a>");
        assertThat(out).doesNotContain("javascript:").doesNotContain("alert");
    }

    @Test
    void stripsStyleAndObjectTags() {
        String out = sanitizer.clean("<style>.x{}</style><object data=evil></object><embed src=evil>");
        assertThat(out).doesNotContain("<style").doesNotContain("<object").doesNotContain("<embed");
    }

    @Test
    void preservesLegitimateRichText() {
        String html = "<h2>Title</h2><p>Body <strong>bold</strong> <em>italic</em></p>"
                + "<ul><li>a</li></ul><a href=\"https://example.com\">link</a>";
        String out = sanitizer.clean(html);
        assertThat(out).contains("<h2>Title</h2>")
                .contains("<strong>bold</strong>")
                .contains("href=\"https://example.com\"");
    }

    @Test
    void nullAndEmptyPassthrough() {
        assertThat(sanitizer.clean(null)).isNull();
        assertThat(sanitizer.clean("")).isEmpty();
    }
}
