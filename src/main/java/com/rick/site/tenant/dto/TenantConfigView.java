package com.rick.site.tenant.dto;

import com.rick.site.tenant.entity.TenantConfig;
import com.rick.site.tenant.entity.TenantConfigI18n;

/**
 * 企业固定信息前台视图(合并 TenantConfig base + 命中 i18n)。
 *
 * <p>模板用 {@code ${config.companyName}} / {@code ${config.address}} / {@code ${config.email}} 等;
 * record 访问器经 JavaBeans 内省被 SpEL 识别为属性,与原 {@link TenantConfig} getter 同名,
 * 故 footer/header 片段无需改动。companyName/companyNameShort/address/copyright 来自 i18n
 * (缺失语种回退默认语种,再缺失为 null);logo/phone/…/icp 来自 base(跨语种共享)。
 *
 * @author Rick.Xu
 */
public record TenantConfigView(String logo, String companyName, String companyNameShort,
                               String address, String copyright,
                               String phone, String mobile, String email, String whatsapp,
                               String facebook, String linkedin, String youtube, String icp) {

    /** base 必填;i18n 可为 null(无任何语种文案时)。 */
    public static TenantConfigView from(TenantConfig base, TenantConfigI18n i18n) {
        return new TenantConfigView(
                base.getLogo(),
                i18n != null ? i18n.getCompanyName() : null,
                i18n != null ? i18n.getCompanyNameShort() : null,
                i18n != null ? i18n.getAddress() : null,
                i18n != null ? i18n.getCopyright() : null,
                base.getPhone(),
                base.getMobile(),
                base.getEmail(),
                base.getWhatsapp(),
                base.getFacebook(),
                base.getLinkedin(),
                base.getYoutube(),
                base.getIcp());
    }
}
