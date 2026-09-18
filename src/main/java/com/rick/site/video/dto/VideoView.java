package com.rick.site.video.dto;

import com.rick.site.video.entity.Video;
import com.rick.site.video.entity.VideoI18n;
import com.rick.site.video.service.VideoService.ResolvedVideo;

/**
 * 视频前台视图(合并 Video + 命中 i18n),供 Thymeleaf 单对象属性访问。
 *
 * <p>参照 {@link com.rick.site.product.dto.ProductView}:较产品多 {@code videoUrl}
 * (视频地址链接)、无 {@code specificationJson}。record 访问器经 JavaBeans 内省被 SpEL
 * 识别为属性。{@code categorySlug} 来自视频所属分类(type=VIDEO)。
 *
 * @author Rick.Xu
 */
public record VideoView(String slug, String cover, String videoUrl, String name, String subtitle,
                        String description, String content, String seoTitle, String seoDescription,
                        String categorySlug, String categoryName) {

    /** 从解析结果构建;i18n 缺失时 name 回退为 slug,其余为 null。 */
    public static VideoView from(ResolvedVideo resolved) {
        Video video = resolved.video();
        VideoI18n i18n = resolved.i18n();
        return new VideoView(
                video.getSlug(),
                video.getCover(),
                video.getVideoUrl(),
                i18n != null && i18n.getName() != null ? i18n.getName() : video.getSlug(),
                i18n != null ? i18n.getSubtitle() : null,
                i18n != null ? i18n.getDescription() : null,
                i18n != null ? i18n.getContent() : null,
                i18n != null ? i18n.getSeoTitle() : null,
                i18n != null ? i18n.getSeoDescription() : null,
                resolved.categorySlug(),
                resolved.categoryName());
    }
}
