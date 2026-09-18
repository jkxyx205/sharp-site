package com.rick.site.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 主题静态素材(图片等)对外暴露。
 *
 * <p>主题模板与素材同置于 {@code classpath:templates/themes/{themeId}/} 下
 * (theme.json / messages.json / 模板 / images)。模板由 Thymeleaf 渲染,但二进制素材
 * (如产品图)需要有 URL 供 {@code <img>} 直接引用,故映射
 * {@code /themes-images/**} → {@code classpath:/templates/themes/},使
 * {@code templates/themes/xhope/images/.../tp.jpeg} 以
 * {@code /themes-images/xhope/images/.../tp.jpeg} 访问(前台 / 预览均可)。
 *
 * <p>静态发布由 {@link com.rick.site.publish.service.StaticSiteGenerator} 将主题
 * {@code images} 目录拷贝到 releaseDir 对应位置,使离线站点同路径可达。
 * {@link com.rick.site.admin.config.SecurityConfig} 的 {@code anyRequest().permitAll()}
 * 已放行该路径。
 *
 * @author Rick.Xu
 */
@Configuration
public class ThemeResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/themes-images/**")
                .addResourceLocations("classpath:/templates/themes/");
    }
}
