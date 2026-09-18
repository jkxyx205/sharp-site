package com.rick.site.video.entity;

import com.rick.db.repository.Column;
import com.rick.db.repository.Table;
import com.rick.db.repository.model.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.Length;

/**
 * 视频多语言(参照 {@link com.rick.site.product.entity.ProductI18n},无规格 JSON)。
 *
 * <p>经 video_id 关联租户;(video_id, language) 唯一。
 * content 为富文本,保存前经 {@code HtmlSanitizer} 清洗。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "video_i18n", comment = "视频多语言")
public class VideoI18n extends BaseEntity<Long> {

    @Column(nullable = false, comment = "视频ID")
    Long videoId;

    @NotBlank
    @Length(max = 20)
    @Column(nullable = false, comment = "语言")
    String language;

    @NotBlank
    @Length(max = 500)
    @Column(nullable = false, comment = "视频名称")
    String name;

    @Length(max = 1000)
    @Column(comment = "副标题")
    String subtitle;

    @Column(comment = "简介")
    String description;

    @Column(comment = "富文本正文(清洗后)")
    String content;

    @Length(max = 500)
    @Column(comment = "SEO 标题")
    String seoTitle;

    @Length(max = 1000)
    @Column(comment = "SEO 描述")
    String seoDescription;
}
