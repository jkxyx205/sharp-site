package com.rick.site.i18n.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-0302 / TASK-0303 验收测试:统一语言查询 + 默认语言回退。
 */
class I18nServiceTest {

    private final I18nService service = new DefaultI18nService();

    @Test
    void returnsRequestedWhenPresent() {
        Map<String, String> content = Map.of("zh-CN", "你好", "en-US", "Hello");
        assertThat(service.resolve(content, "zh-CN", "en-US")).hasValue("你好");
    }

    @Test
    void fallsBackToDefaultWhenRequestedAbsent() {
        Map<String, String> content = Map.of("en-US", "Hello");
        // 租户默认 zh-CN,但 zh-CN 内容缺失 → 回退 en-US
        assertThat(service.resolve(content, "zh-CN", "en-US")).hasValue("Hello");
    }

    @Test
    void returnsEmptyWhenNeitherPresent() {
        Map<String, String> content = Map.of("en-US", "Hello");
        assertThat(service.resolve(content, "ja-JP", "ko-KR")).isEmpty();
    }

    @Test
    void returnsEmptyWhenMapEmpty() {
        assertThat(service.resolve(Map.of(), "zh-CN", "en-US")).isEmpty();
        assertThat(service.resolve(null, "zh-CN", "en-US")).isEmpty();
    }

    @Test
    void requestedNullFallsBackToDefault() {
        Map<String, String> content = Map.of("en-US", "Hello");
        assertThat(service.resolve(content, null, "en-US")).hasValue("Hello");
    }
}
