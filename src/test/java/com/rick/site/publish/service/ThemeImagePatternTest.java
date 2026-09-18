package com.rick.site.publish.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 StaticSiteGenerator.copyThemeImages 使用的 classpath 模式确实能枚举到主题图片。
 * 不需要 Spring 上下文 / DB,仅校验 PathMatchingResourcePatternResolver 在测试 classpath
 * (含 build/resources/main,即 src/main/resources 经 processResources 拷贝产物)下能解析
 * {@code classpath*:templates/themes/xhope/images/**}。
 */
class ThemeImagePatternTest {

    @Test
    void patternResolvesVacuumImage() throws IOException {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(
                "classpath*:templates/themes/xhope/images/**");
        boolean found = false;
        for (Resource r : resources) {
            System.out.println("[theme-image] " + r.getURL() + " readable=" + r.isReadable());
            String url = r.getURL().toString();
            if (url.endsWith("tp.jpeg") && r.isReadable()) {
                found = true;
            }
        }
        assertTrue(found, "未在 classpath 解析到 tp.jpeg —— copyThemeImages 模式需修正");
    }
}
