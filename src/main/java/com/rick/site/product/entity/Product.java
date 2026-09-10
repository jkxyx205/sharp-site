package com.rick.site.product.entity;

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
 * 产品(DATABASE.md §10 / TASK-0602)。
 *
 * <p>每租户按 slug 唯一;多语言内容存于 {@link ProductI18n}。
 * 继承 {@link TenantBaseEntity}:tenant_id 框架注入,审计列自动填充,逻辑删除。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "product", comment = "产品")
public class Product extends TenantBaseEntity<Long> {

    @Column(comment = "分类ID")
    Long categoryId;

    @NotBlank
    @Length(max = 200)
    @Column(nullable = false, comment = "slug(URL 标识,租户内唯一)")
    String slug;

    @Length(max = 1000)
    @Column(comment = "封面图URL")
    String cover;

    @Column(nullable = false, comment = "状态:1上架,0下架")
    Short status;

    @Column(nullable = false, comment = "排序,升序")
    Integer sort;
}
