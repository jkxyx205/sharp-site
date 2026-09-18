package com.rick.site.video.entity;

import com.rick.db.repository.Column;
import com.rick.db.repository.Table;
import com.rick.site.common.model.TenantBaseEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.Length;

/**
 * 视频(媒体中心后台维护;参照 {@link com.rick.site.product.entity.Product})。
 *
 * <p>每租户按 slug 唯一;多语言内容存于 {@link VideoI18n}。
 * 较产品多一个 {@code videoUrl}(视频地址链接);无规格 JSON(specification)。
 * 继承 {@link TenantBaseEntity}:tenant_id 框架注入,审计列自动填充,逻辑删除。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "video", comment = "视频")
public class Video extends TenantBaseEntity<Long> {

    @Column(comment = "分类ID(VIDEO 分类)")
    Long categoryId;

    @NotBlank
    @Length(max = 200)
    @Column(nullable = false, comment = "slug(URL 标识,租户内唯一)")
    String slug;

    @Length(max = 1000)
    @Column(comment = "封面图URL")
    String cover;

    @Length(max = 1000)
    @Column(comment = "视频地址链接")
    String videoUrl;

    @Column(nullable = false, comment = "状态:1上架,0下架")
    Short status;

    @Column(nullable = false, comment = "排序,升序")
    Integer sort;
}
