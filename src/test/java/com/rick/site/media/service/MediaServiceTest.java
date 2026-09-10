package com.rick.site.media.service;

import com.rick.common.http.exception.BizException;
import com.rick.site.media.entity.Media;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-0801/0802 验收:媒体上传(经 sharp-fileupload)+ 列表 + 删除 + 文件安全校验。
 */
@SpringBootTest
@Transactional
class MediaServiceTest {

    @Autowired
    private MediaService mediaService;

    @Autowired
    private TenantService tenantService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private Tenant createTenant(String code) {
        Tenant t = tenantService.save(Tenant.builder()
                .code(code).name(code).themeId("modern").defaultLanguage("en-US").build());
        TenantContext.set(t);
        return t;
    }

    private MultipartFile png(String filename) {
        return new MockMultipartFile("file", filename, "image/png",
                "png-bytes".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void uploadStoresAndRecordsMedia() throws Exception {
        Tenant t = createTenant("media-1");
        Media m = mediaService.upload(png("photo.png"), "Title", "alt");
        assertThat(m.getId()).isNotNull();
        assertThat(m.getUrl()).isNotBlank();
        assertThat(m.getObjectKey()).isNotBlank();
        assertThat(m.getMimeType()).isEqualTo("image/png");
        assertThat(m.getSize()).isPositive();
        assertThat(mediaService.listByTenant())
                .anySatisfy(row -> assertThat(row.getUrl()).isEqualTo(m.getUrl()));
    }

    @Test
    void deleteRemovesMediaAndIsTenantScoped() throws Exception {
        // tenant_id 由 TenantContext 注入插入(防伪造,CLAUDE.md §4),故按租户交替创建+上传;
        // 显式 tenantId 读取/删除归属由 selectById 按上下文租户隔离判定。
        Tenant a = createTenant("media-isol-a");
        Media ma = mediaService.upload(png("a.png"), null, null);

        Tenant b = createTenant("media-isol-b");
        Media mb = mediaService.upload(png("b.png"), null, null);

        // b 不能删 a 的媒体(requireOwned 经上下文隔离:selectById 按 tenant_id 过滤)
        assertThatThrownBy(() -> mediaService.delete(ma.getId()))
                .isInstanceOf(BizException.class);
        // a 删除自己的媒体:上下文切回 a(deleteById 按上下文租户隔离)
        TenantContext.set(a);
        mediaService.delete(ma.getId());
        assertThat(mediaService.getById(ma.getId())).isEmpty();
        TenantContext.set(b);
        assertThat(mediaService.getById(mb.getId())).isPresent();
    }

    @Test
    void rejectsBadExtension() {
        Tenant t = createTenant("media-2");
        MultipartFile exe = new MockMultipartFile("file", "evil.exe", "application/octet-stream",
                "x".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> mediaService.upload(exe, null, null))
                .isInstanceOf(BizException.class).hasMessageContaining("不支持的文件类型");
    }

    @Test
    void rejectsBadMime() {
        Tenant t = createTenant("media-3");
        // 扩展名 png 但 MIME 不在白名单 → 拒绝
        MultipartFile bad = new MockMultipartFile("file", "x.png", "application/x-msdownload",
                "x".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> mediaService.upload(bad, null, null))
                .isInstanceOf(BizException.class).hasMessageContaining("MIME");
    }

    @Test
    void rejectsOversize() {
        Tenant t = createTenant("media-4");
        // 真实内容使 isEmpty()=false,getSize() 覆盖为超限值,从而命中大小校验而非空文件校验
        MockMultipartFile big = new MockMultipartFile("file", "big.png", "image/png",
                "png-bytes".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public long getSize() {
                return 21L * 1024 * 1024;
            }
        };
        assertThatThrownBy(() -> mediaService.upload(big, null, null))
                .isInstanceOf(BizException.class).hasMessageContaining("大小");
    }

    @Test
    void rejectsEmptyFile() {
        Tenant t = createTenant("media-5");
        MockMultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> mediaService.upload(empty, null, null))
                .isInstanceOf(BizException.class).hasMessageContaining("空");
    }
}
