package com.rick.site.tenant.entity;

import com.rick.db.repository.Column;
import com.rick.db.repository.Table;
import com.rick.site.common.model.TenantBaseEntity;
import jakarta.validation.constraints.Email;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.Length;

/**
 * 企业固定信息(DATABASE.md §3),每租户一行(uk_tenant_config)。
 *
 * <p>继承 {@link TenantBaseEntity}:tenant_id 不可变,由框架从 TenantContext 注入(防伪造);
 * 审计列自动填充,逻辑删除。字段长度校验与 DDL 一致,防止超长直接打到数据库报错。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "tenant_config", comment = "企业固定信息")
public class TenantConfig extends TenantBaseEntity<Long> {

    @Length(max = 1000)
    @Column(comment = "Logo URL")
    String logo;

    @Length(max = 100)
    @Column(comment = "电话")
    String phone;

    @Length(max = 100)
    @Column(comment = "手机")
    String mobile;

    @Email
    @Length(max = 200)
    @Column(comment = "Email")
    String email;

    @Length(max = 200)
    @Column(comment = "WhatsApp")
    String whatsapp;

    @Length(max = 500)
    @Column(comment = "Facebook 链接")
    String facebook;

    @Length(max = 500)
    @Column(comment = "LinkedIn 链接")
    String linkedin;

    @Length(max = 500)
    @Column(comment = "YouTube 链接")
    String youtube;

    @Length(max = 200)
    @Column(comment = "ICP 备案号")
    String icp;
}
