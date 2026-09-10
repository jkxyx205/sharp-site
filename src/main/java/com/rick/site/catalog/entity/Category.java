package com.rick.site.catalog.entity;

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
 * 分类(DATABASE.md §8 / TASK-0601),PRODUCT / NEWS 共用,type 区分。
 *
 * <p>每租户按 (type, slug) 唯一;parent_id 支持层级(可为空)。
 * 继承 {@link TenantBaseEntity}:tenant_id 框架注入,审计列自动填充,逻辑删除。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "category", comment = "分类")
public class Category extends TenantBaseEntity<Long> {

    @NotBlank
    @Length(max = 50)
    @Column(nullable = false, comment = "类型:PRODUCT / NEWS")
    String type;

    @Column(comment = "父分类ID,支持层级")
    Long parentId;

    @NotBlank
    @Length(max = 200)
    @Column(nullable = false, comment = "slug")
    String slug;

    @Column(nullable = false, comment = "排序,升序")
    Integer sort;

    @Column(nullable = false, comment = "状态:1启用,0停用")
    Short status;
}
