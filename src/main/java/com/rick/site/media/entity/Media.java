package com.rick.site.media.entity;

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
 * 媒体文件(DATABASE.md §15 / TASK-0801),由 sharp-fileupload 上传后记录元数据。
 *
 * <p>继承 {@link TenantBaseEntity}:tenant_id 框架注入,审计列自动填充,逻辑删除。
 * object_key 为底层存储的路径(供 FileStore.delete 回收);url 为可访问地址。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "media", comment = "媒体文件")
public class Media extends TenantBaseEntity<Long> {

    @Length(max = 1000)
    @Column(comment = "底层存储对象 key(path)")
    String objectKey;

    @NotBlank
    @Length(max = 2000)
    @Column(nullable = false, comment = "可访问 URL")
    String url;

    @Length(max = 500)
    @Column(comment = "原始文件名")
    String filename;

    @Length(max = 200)
    @Column(comment = "MIME 类型")
    String mimeType;

    @Column(comment = "文件大小(字节)")
    Long size;

    @Length(max = 1000)
    @Column(comment = "替代文本")
    String altText;

    @Length(max = 500)
    @Column(comment = "标题")
    String title;
}
