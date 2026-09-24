package com.rick.site.translate;

import java.util.Map;

/**
 * 内容多语言翻译服务(Phase:多语言同步)。
 *
 * <p>供新闻/产品/视频在后台勾选「同步到其他语言」时,把当前语种的 i18n 文案字段翻译到目标语种,
 * 再交由各业务服务的 {@code saveI18n} 写入。富文本 HTML 字段需保留标签结构;JSON 字段(如产品
 * specificationJson)只译字符串值、保留键与结构。
 *
 * <p>翻译失败应抛 {@link com.rick.common.http.exception.BizException};
 * {@code syncToLanguages} 在异步线程池里执行,异常由 Spring 异步未捕获处理器记录日志,
 * 不影响已保存的源语种行(源语种 saveI18n 在请求线程独立事务先行提交)。
 *
 * @author Rick.Xu
 */
public interface TranslationService {

    /**
     * 把 {@code fields}(字段名→文本)从 {@code sourceLang} 翻译到 {@code targetLang}。
     *
     * <p>空白值原样返回(不送译)。返回 map 与入参同 key,值为译文。
     *
     * @param fields     待译字段;key 为字段名(如 title/content/seoTitle),value 为原文
     * @param sourceLang 源语种(BCP-47,如 zh-CN)
     * @param targetLang 目标语种(BCP-47,如 en-US)
     * @return 字段名→译文;空白入参项原样回填
     */
    Map<String, String> translate(Map<String, String> fields, String sourceLang, String targetLang);
}
