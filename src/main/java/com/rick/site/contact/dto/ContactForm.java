package com.rick.site.contact.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 联系表单提交 DTO(后台不直接暴露 Entity,CLAUDE.md §18)。
 *
 * <p>{@code topic} 可空:部分主题(modern 无 / xhope 有 select)含兴趣主题下拉,
 * 一并写入邮件正文,不做强校验。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
public class ContactForm {

    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String message;

    /** 兴趣主题(可空),仅 xhope 等主题表单携带。 */
    private String topic;
}
