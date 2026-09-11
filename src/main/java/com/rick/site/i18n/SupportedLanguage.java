package com.rick.site.i18n;

import java.util.Arrays;
import java.util.List;

/**
 * 平台支持语言的唯一来源(CLAUDE.md §7)。新增语言只需在此追加一个枚举常量:
 * 代码标签({@code code})、展示文案({@code label})、URL 段识别白名单({@link #CODES})、
 * 平台默认语种({@link #PLATFORM_DEFAULT})均由此派生,无需改其它 Java 代码。
 *
 * <p>语种判定唯一来源仍是 Theme(§26.1):{@code theme.json.locales} 必须是 {@link #CODES} 的子集;
 * 后台编辑/语言切换遍历取主题清单。本枚举仅约束平台级白名单与展示文案,避免散落于多处。
 *
 * @author Rick.Xu
 */
public enum SupportedLanguage {
    ZH_CN("zh-CN", "中文"),
    EN_US("en-US", "EN");

    /** 平台支持语言代码列表(URL 段识别白名单)。 */
    public static final List<String> CODES =
            Arrays.stream(values()).map(SupportedLanguage::code).toList();

    /** 无租户时的平台默认语言。 */
    public static final String PLATFORM_DEFAULT = EN_US.code;

    private final String code;
    private final String label;

    SupportedLanguage(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /** 展示文案;未知代码原样返回(与原 {@code default -> lang} 语义一致)。 */
    public static String labelOf(String code) {
        if (code == null) {
            return null;
        }
        for (SupportedLanguage l : values()) {
            if (l.code.equals(code)) {
                return l.label;
            }
        }
        return code;
    }
}
