package com.rick.site.publish.entity;

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

import java.time.LocalDateTime;

/**
 * 发布记录(DATABASE.md §18 / TASK-1301)。
 *
 * <p>每次发布一条:版本号、状态(PENDING/SUCCESS/FAILED)、起止时间、失败原因。
 * 按 (租户, version) 唯一。tenant_id 框架注入(§4),查询自动隔离。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "publish_record", comment = "发布记录")
public class PublishRecord extends TenantBaseEntity<Long> {

    @NotBlank
    @Length(max = 100)
    @Column(nullable = false, comment = "版本号,租户内唯一,如 v001")
    String version;

    @NotBlank
    @Length(max = 30)
    @Column(nullable = false, comment = "状态:PENDING/SUCCESS/FAILED")
    String status;

    @Column(comment = "发布开始时间")
    LocalDateTime startedAt;

    @Column(comment = "发布结束时间")
    LocalDateTime finishedAt;

    @Column(comment = "失败原因(FAILED 时)")
    String errorMessage;
}
