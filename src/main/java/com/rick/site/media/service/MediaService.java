package com.rick.site.media.service;

import com.rick.common.http.exception.BizException;
import com.rick.db.plugin.BaseServiceImpl;
import com.rick.fileupload.core.FileStore;
import com.rick.fileupload.core.model.FileMeta;
import com.rick.site.media.dao.MediaDAO;
import com.rick.site.media.entity.Media;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * 媒体服务(TASK-0801 / TASK-0802),上传/列表/删除。
 *
 * <p>上传复用 sharp-fileupload {@link FileStore}(本地存储,可对接 OSS);
 * 保存前做 MIME / 扩展名 / 大小校验(CLAUDE.md §9),大小硬上限由 Spring multipart 配置兜底。
 * 租户隔离:查询/删除显式带 tenant_id;groupName 按租户命名空间隔离文件。
 *
 * @author Rick.Xu
 */
@Service
public class MediaService extends BaseServiceImpl<MediaDAO, Media, Long> {

    /** 允许的扩展名白名单(图片 + 文档 + 压缩包)。 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "svg",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "zip");

    /** 允许的 MIME 前缀白名单。 */
    private static final Set<String> ALLOWED_MIME_PREFIXES = Set.of(
            "image/", "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument",
            "application/vnd.ms-excel", "application/vnd.ms-powerpoint",
            "text/plain", "application/zip", "application/x-zip-compressed");

    private static final long MAX_SIZE = 20L * 1024 * 1024;

    private final FileStore fileStore;

    public MediaService(MediaDAO baseDAO, FileStore fileStore) {
        super(baseDAO);
        this.fileStore = fileStore;
    }

    public List<Media> listByTenant() {
        return baseDAO.select("1=1 ORDER BY create_time DESC", Map.of());
    }

    /**
     * 上传文件:校验 → sharp-fileupload 存储 → 记录 media 元数据。
     * tenant_id 取自 TenantContext(认证上下文,Phase 9),不来自参数。
     *
     * @param file    multipart 文件
     * @param title   标题(可空)
     * @param altText 替代文本(可空)
     */
    @Transactional(rollbackFor = Exception.class)
    public Media upload(MultipartFile file, String title, String altText) throws IOException {
        validate(file);
        Tenant tenant = TenantContext.require();
        Long tenantId = tenant.getId();
        // 目录按租户 code 划分:/{tenant_code}/{文件名};与 media.url 一并暴露给前台
        String groupName = tenant.getCode();
        List<? extends FileMeta> metas = fileStore.upload(List.of(file), groupName);
        if (metas == null || metas.isEmpty()) {
            throw new BizException("文件上传失败");
        }
        FileMeta meta = metas.get(0);
        Media media = Media.builder()
                .tenantId(tenantId)
                .objectKey(meta.getPath())
                .url(meta.getUrl())
                .filename(originalName(file, meta))
                .mimeType(meta.getContentType())
                .size(meta.getSize())
                .title(title)
                .altText(altText)
                .build();
        return baseDAO.insert(media);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long mediaId) throws IOException {
        Tenant tenant = TenantContext.require();
        Media owned = requireOwned(mediaId);
        // 先回收底层文件(失败不阻断元数据逻辑删除:记录后继续)
        try {
            fileStore.delete(tenant.getCode(), owned.getObjectKey());
        } catch (Exception ignored) {
            // 文件缺失等不阻断;元数据仍逻辑删除
        }
        baseDAO.deleteById(mediaId);
    }

    public Optional<Media> getById(Long mediaId) {
        // selectById 已按 TenantContext 隔离(跨租户查不到),无需再手动比对 tenantId
        return baseDAO.selectById(mediaId);
    }

    // ---- 校验(TASK-0802)----

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("文件为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException("文件超过大小限制(20MB)");
        }
        String ext = extension(file.getOriginalFilename());
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BizException("不支持的文件类型: " + ext);
        }
        String mime = file.getContentType();
        if (mime == null || ALLOWED_MIME_PREFIXES.stream().noneMatch(mime::startsWith)) {
            throw new BizException("不支持的 MIME 类型: " + mime);
        }
    }

    private Media requireOwned(Long mediaId) {
        // selectById 已按 TenantContext 隔离(跨租户查不到),无需再手动比对 tenantId
        return baseDAO.selectById(mediaId)
                .orElseThrow(() -> new BizException("媒体不存在: id=" + mediaId));
    }

    private static String extension(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String originalName(MultipartFile file, FileMeta meta) {
        String name = file.getOriginalFilename();
        return name != null ? name : meta.getName();
    }
}
