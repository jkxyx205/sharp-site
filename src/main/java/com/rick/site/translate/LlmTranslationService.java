package com.rick.site.translate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rick.common.http.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Chat Completions 实现的 {@link TranslationService}。
 *
 * <p>一套请求格式可对接 OpenAI / DeepSeek / GLM / Moonshot 等多数供应商(国内可达);
 * {@code base-url} / {@code api-key} / {@code model} 由 {@code app.translation.*} 配置。
 *
 * <p>一次调用把所有字段以 JSON 发给模型,系统提示词要求:仅译文本值、保留 HTML 标签结构、
 * 对 JSON 字段只译字符串值保留键结构,以 {@code {字段名:译文}} JSON 返回。
 * 富文本 content 译回后仍由 {@code HtmlSanitizer} 在 saveI18n 内统一清洗。
 *
 * @author Rick.Xu
 */
@Component
public class LlmTranslationService implements TranslationService {

    private static final Logger log = LoggerFactory.getLogger(LlmTranslationService.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final RestClient client;

    public LlmTranslationService(@Value("${app.translation.enabled:false}") boolean enabled,
                                 @Value("${app.translation.base-url:https://api.openai.com/v1}") String baseUrl,
                                 @Value("${app.translation.api-key:}") String apiKey,
                                 @Value("${app.translation.model:gpt-4o-mini}") String model) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public Map<String, String> translate(Map<String, String> fields, String sourceLang, String targetLang) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            throw new BizException("翻译服务未配置(app.translation.api-key 缺失),无法同步到 " + targetLang);
        }

        // 仅送译非空白字段;空白项原样回填
        Map<String, String> toTranslate = new LinkedHashMap<>();
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (e.getValue() != null && !e.getValue().isBlank()) {
                toTranslate.put(e.getKey(), e.getValue());
            } else {
                result.put(e.getKey(), e.getValue());
            }
        }
        if (toTranslate.isEmpty()) {
            return result;
        }

        String content = callModel(toTranslate, sourceLang, targetLang);
        Map<String, String> translated = parseJsonObject(content);

        for (String key : toTranslate.keySet()) {
            String v = translated.get(key);
            result.put(key, (v != null && !v.isBlank()) ? v : toTranslate.get(key));
        }
        return result;
    }

    /** 发起一次 chat completions 调用,返回 message.content 文本。 */
    private String callModel(Map<String, String> fields, String sourceLang, String targetLang) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("temperature", 0.3);
        request.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt()),
                Map.of("role", "user", "content", userPayload(fields, sourceLang, targetLang))
        ));
        // 请求 JSON 对象输出;不支持该参数的供应商会忽略,解析端兼容 markdown 围栏
        request.put("response_format", Map.of("type", "json_object"));

        try {
            String body = client.post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
            return extractMessageContent(body);
        } catch (Exception e) {
            log.warn("LLM 翻译调用失败 {}→{}: {}", sourceLang, targetLang, e.getMessage());
            throw new BizException("翻译调用失败(" + sourceLang + "→" + targetLang + "):" + e.getMessage());
        }
    }

    private String systemPrompt() {
        return """
                你是网站内容专业翻译。把输入 JSON 对象里每个字符串值从源语言翻译到目标语言,输出**同结构 JSON 对象**(键不变,值为译文)。
                规则:
                1. 只翻译人类可读文本,不要翻译 URL、slug、纯数字、纯代码。
                2. 含 HTML 标签的值:只翻译标签间与属性内的可见文本,完整保留所有标签、属性与结构,不得增删标签。
                3. 值为 JSON 的字段:只翻译字符串叶值,保留键与结构。
                4. 空白或纯空白值原样返回。
                5. 仅输出 JSON 对象本身,不要 markdown 围栏,不要解释。""";
    }

    private String userPayload(Map<String, String> fields, String sourceLang, String targetLang) {
        try {
            return "源语言=" + sourceLang + ",目标语言=" + targetLang + "\n输入:\n" + JSON.writeValueAsString(fields);
        } catch (Exception e) {
            throw new BizException("翻译请求序列化失败:" + e.getMessage());
        }
    }

    /** 从 chat/completions 响应取 choices[0].message.content;容错解析。 */
    @SuppressWarnings("unchecked")
    private String extractMessageContent(String body) {
        if (body == null || body.isBlank()) {
            throw new BizException("翻译返回为空");
        }
        try {
            Map<String, Object> root = JSON.readValue(body, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) root.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new BizException("翻译返回无 choices:" + body);
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            Object content = message.get("content");
            if (content == null) {
                throw new BizException("翻译返回无 content:" + body);
            }
            return content.toString();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("翻译响应解析失败:" + e.getMessage());
        }
    }

    /** 把模型返回(may 含 ```json 围栏)解析为 字段名→译文 map。 */
    private Map<String, String> parseJsonObject(String content) {
        if (content == null || content.isBlank()) {
            throw new BizException("翻译返回内容为空");
        }
        String json = content.trim();
        // 去除 markdown 代码围栏
        if (json.startsWith("```")) {
            int firstNewline = json.indexOf('\n');
            if (firstNewline > 0) {
                json = json.substring(firstNewline + 1);
            }
            int fence = json.lastIndexOf("```");
            if (fence >= 0) {
                json = json.substring(0, fence);
            }
            json = json.trim();
        }
        try {
            Map<String, Object> map = JSON.readValue(json, Map.class);
            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : map.entrySet()) {
                out.put(e.getKey(), e.getValue() == null ? null : e.getValue().toString());
            }
            return out;
        } catch (Exception e) {
            throw new BizException("翻译结果 JSON 解析失败:" + e.getMessage());
        }
    }
}
