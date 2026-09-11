package com.rick.site.tenant.entity;

import com.rick.db.repository.Column;
import com.rick.db.repository.Table;
import com.rick.db.repository.model.BaseCodeEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

/**
 * 租户(DATABASE.md §1)。
 *
 * <p>继承 {@link BaseCodeEntity}:id 雪花算法,code 全局唯一,
 * 审计列(create_by/create_time/update_by/update_time/is_deleted)
 * 由 sharp-database 框架自动填充,逻辑删除。租户是平台级数据,本身无 tenant_id。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "tenant", comment = "租户")
public class Tenant extends BaseCodeEntity<Long> {

    @NotBlank
    @Column(nullable = false, comment = "租户名称")
    String name;

    @NotBlank
    @Column(nullable = false, comment = "主题ID,如 modern / industrial")
    String themeId;

    @Column(nullable = false, comment = "状态:1启用,0停用")
    Short status;
}
