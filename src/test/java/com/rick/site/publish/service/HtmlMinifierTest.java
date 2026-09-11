package com.rick.site.publish.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HtmlMinifier} 单元测试:验证注释删除、空白折叠,以及对 pre/textarea/script/style
 * 内容的保护(这些元素中空白有语义,不能压缩)。
 */
class HtmlMinifierTest {

    @Test
    void removesHtmlComments() {
        assertThat(HtmlMinifier.minify("<div><!-- note -->text</div>"))
                .isEqualTo("<div>text</div>");
    }

    @Test
    void collapsesRepeatedWhitespace() {
        String in = "<div>\n    <p>Hello</p>\n\n</div>";
        assertThat(HtmlMinifier.minify(in)).isEqualTo("<div> <p>Hello</p> </div>");
    }

    @Test
    void preservesSingleSpaceBetweenInlineElements() {
        // 单空格具有行内语义(如导航链接间距),折叠 2+ 时不波及单空格。
        String in = "<nav><a>Home</a> <a>About</a></nav>";
        assertThat(HtmlMinifier.minify(in)).isEqualTo("<nav><a>Home</a> <a>About</a></nav>");
    }

    @Test
    void preservesPreWhitespace() {
        String pre = "<pre>line1\n   line2</pre>";
        assertThat(HtmlMinifier.minify("<div>\n  " + pre + "\n</div>"))
                .isEqualTo("<div> " + pre + " </div>");
    }

    @Test
    void preservesStyleCssSpacing() {
        // CSS 中 margin: 0 0 12px 的值内空格不能折叠,否则规则失效。
        String style = "<style>.x { margin: 0 0 12px; }</style>";
        assertThat(HtmlMinifier.minify("<head>\n  " + style + "\n</head>"))
                .isEqualTo("<head> " + style + " </head>");
    }

    @Test
    void minifiesInlineCss() {
        // <style> 内容:删 CSS 注释 + 折叠缩进空白,但保留值内单空格。
        String html = """
                <head>
                  <style>
                    /* theme */
                    .hero {
                      margin: 0 0 12px;
                      font-family: "Segoe UI", Roboto;
                    }
                  </style>
                </head>
                """;
        String min = HtmlMinifier.minify(html);
        // CSS 注释删除;缩进折叠;值内空格保留。
        assertThat(min).contains("<style>.hero { margin: 0 0 12px; font-family: \"Segoe UI\", Roboto; }</style>");
        assertThat(min).doesNotContain("/* theme */");
    }

    @Test
    void preservesScriptContent() {
        String js = "<script>if (a  &&  b) { foo(); }</script>";
        // script 内容原样保留(含其中 2+ 空格);外围空白折叠。
        assertThat(HtmlMinifier.minify("<body>\n  " + js + "\n</body>"))
                .isEqualTo("<body> " + js + " </body>");
    }

    @Test
    void preservesTextareaContent() {
        String ta = "<textarea>  keep  spaces  </textarea>";
        assertThat(HtmlMinifier.minify("<form>\n  " + ta + "\n</form>"))
                .isEqualTo("<form> " + ta + " </form>");
    }

    @Test
    void nullAndEmptyPassthrough() {
        assertThat(HtmlMinifier.minify(null)).isNull();
        assertThat(HtmlMinifier.minify("")).isEqualTo("");
    }

    @Test
    void reducesSizeOnRealisticSnippet() {
        String html = """
                <!DOCTYPE html>
                <html>
                <!-- public head -->
                <head>
                    <style>
                        .hero { margin: 0 0 12px; }
                    </style>
                </head>
                <body>
                    <nav>
                        <a>Home</a> <a>About</a>
                    </nav>
                </body>
                </html>
                """;
        String min = HtmlMinifier.minify(html);
        assertThat(min.length()).isLessThan(html.length());
        // 关键内容不丢失
        assertThat(min).contains("<!DOCTYPE html>").contains("margin: 0 0 12px")
                .contains("<a>Home</a> <a>About</a>");
        // 注释已删除
        assertThat(min).doesNotContain("public head");
    }
}
