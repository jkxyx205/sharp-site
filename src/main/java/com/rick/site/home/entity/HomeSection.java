package com.rick.site.home.entity;

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
 * 首页区块(DATABASE.md §6 / TASK-0501)。
 *
 * <p>每租户按 section_key 唯一(部分唯一索引);区块内容存于 {@link HomeSectionI18n}。
 * 区块键如 hero / company / products / advantages / news / cta。
 * 继承 {@link TenantBaseEntity}:tenant_id 框架注入,审计列自动填充,逻辑删除。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "home_section", comment = "首页区块")
public class HomeSection extends TenantBaseEntity<Long> {

    @NotBlank
    @Length(max = 100)
    @Column(nullable = false, comment = "区块键,如 hero / company")
    String sectionKey;

    @Column(nullable = false, comment = "排序,升序")
    Integer sort;

    @Column(nullable = false, comment = "是否启用:1启用,0停用")
    Short enabled;
}
