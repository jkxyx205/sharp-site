package com.rick.site.tenant.entity;

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
 * 企业固定信息多语言(DATABASE.md §3):company_name / company_name_short / address / copyright。
 *
 * <p>经 tenant_config_id 关联租户;(tenant_config_id, language) 唯一。缺失语种回退租户默认语种。
 * 联系方式(logo/phone/email/…/icp)跨语种共享,留在 {@link TenantConfig} base 行。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "tenant_config_i18n", comment = "企业固定信息多语言")
public class TenantConfigI18n extends BaseEntity<Long> {

    @Column(nullable = false, comment = "企业固定信息ID")
    Long tenantConfigId;

    @NotBlank
    @Length(max = 20)
    @Column(nullable = false, comment = "语言")
    String language;

    @NotBlank
    @Length(max = 500)
    @Column(nullable = false, comment = "公司名称")
    String companyName;

    @Length(max = 200)
    @Column(comment = "公司简称")
    String companyNameShort;

    @Length(max = 1000)
    @Column(comment = "地址")
    String address;

    @Length(max = 500)
    @Column(comment = "版权信息")
    String copyright;
}
