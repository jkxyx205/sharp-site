package com.rick.site.page.entity;

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
 * 站点页面(DATABASE.md §4 / TASK-0401)。
 *
 * <p>每租户按 page_key / path 唯一(部分唯一索引);正文存于 {@link SitePageI18n}。
 * 继承 {@link TenantBaseEntity}:tenant_id 由框架从 TenantContext 注入,审计列自动填充,逻辑删除。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "site_page", comment = "站点页面")
public class SitePage extends TenantBaseEntity<Long> {

    @NotBlank
    @Length(max = 100)
    @Column(nullable = false, comment = "页面键,如 about / contact")
    String pageKey;

    @NotBlank
    @Length(max = 500)
    @Column(nullable = false, comment = "前台路径,如 /about")
    String path;

    @NotBlank
    @Length(max = 200)
    @Column(nullable = false, comment = "渲染模板相对路径,如 themes/modern/about")
    String template;

    @Column(nullable = false, comment = "状态:1启用,0停用")
    Short status;
}
