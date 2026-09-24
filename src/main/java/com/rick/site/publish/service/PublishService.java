package com.rick.site.publish.service;

import com.rick.common.http.exception.BizException;
import com.rick.site.publish.entity.PublishRecord;
import com.rick.site.tenant.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 发布服务(TASK-1302 / 1303 / 1304,ARCHITECTURE §10,REQUIREMENTS §18)。
 *
 * <p>发布流程:版本号生成 → {@link StaticSiteGenerator#generate} 渲染静态文件 →
 * 原子切换 {@code current} 符号链接到新 release → 记录 SUCCESS。任一非 DB 步骤失败时,
 * {@code current} 不变(线上版本不受影响),记录 FAILED + 错误信息。
 *
 * <p><b>原子切换</b>:先在租户目录下创建临时符号链接 {@code .current-{version}.tmp} →
 * 新 release,再用 {@link Files#move} 以 {@code ATOMIC_MOVE + REPLACE_EXISTING} 替换
 * {@code current}。POSIX rename(2) 原子,故线上始终指向完整的新或旧 release,不会半切换。
 *
 * <p>事务边界:start/succeed/fail 各自独立事务提交,确保失败路径下记录状态持久
 * (publish 整体不加事务,避免回滚掉 fail 记录)。
 *
 * @author Rick.Xu
 */
@Service
public class PublishService {

    private static final Logger log = LoggerFactory.getLogger(PublishService.class);

    @Value("${sharp.site.www-root:data/www}")
    private String wwwRoot;

    private final StaticSiteGenerator generator;
    private final PublishRecordService recordService;

    public PublishService(StaticSiteGenerator generator, PublishRecordService recordService) {
        this.generator = generator;
        this.recordService = recordService;
    }

    /**
     * 执行一次发布,返回最终状态的发布记录(SUCCESS 或抛出失败异常)。
     */
    public PublishRecord publish() throws IOException {
        String tenantCode = TenantContext.requireTenantCode();
        String version = recordService.nextVersion();
        PublishRecord record = recordService.start(version);
        try {
            Path releaseDir = generator.generate(version);
            atomicSwitch(tenantCode, version, releaseDir);
            recordService.succeed(record.getId());
            return recordService.findByVersion(version).orElse(record);
        } catch (Exception e) {
            // §20:错误信息可记录,但不含敏感凭据;发布失败必须记录(§20)。
            log.error("发布失败 tenant={} version={}: {}", tenantCode, version, e.getMessage(), e);
            recordService.fail(record.getId(), e.getMessage());
            throw new BizException("发布失败: " + e.getMessage());
        }
    }

    /** {@code current} 符号链接目标(供校验/测试读取)。 */
    public Path currentPath(String tenantCode) {
        return Paths.get(wwwRoot).resolve(tenantCode).resolve("current");
    }

    /**
     * 把指定版本的发布产物目录打包成 zip 写入 {@code out}(供后台下载部署文件)。
     *
     * <p>版本归属已由调用方(AdminPublishController 经 {@code recordService.findByVersion}
     * 租户隔离查询)校验;此处仅按当前租户上下文定位 {@code releases/{version}} 目录,
     * 目录不存在或不可读时抛 {@link BizException}。zip 内条目用相对路径,避免 ZipSlip。
     *
     * @param version 租户内已存在的发布版本号(如 v001)
     * @param out     下载响应输出流(调用方负责在 finally 关闭)
     */
    public void writeReleaseZip(String version, OutputStream out) throws IOException {
        String tenantCode = TenantContext.requireTenantCode();
        Path releaseDir = Paths.get(wwwRoot).resolve(tenantCode).resolve("releases").resolve(version);
        if (!Files.isDirectory(releaseDir)) {
            throw new BizException("发布产物目录不存在: " + version);
        }
        Path root = releaseDir.toAbsolutePath().normalize();
        try (ZipOutputStream zos = new ZipOutputStream(out);
             Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> !p.equals(root) && Files.isRegularFile(p))
                    .forEach(p -> {
                        String entryName = root.relativize(p).toString()
                                .replace(File.separatorChar, '/');
                        try {
                            zos.putNextEntry(new ZipEntry(entryName));
                            Files.copy(p, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /** 原子切换 current → 新 release。 */
    private void atomicSwitch(String tenantCode, String version, Path releaseDir) throws IOException {
        Path tenantRoot = Paths.get(wwwRoot).resolve(tenantCode);
        Files.createDirectories(tenantRoot);
        Path current = tenantRoot.resolve("current");
        Path tmp = tenantRoot.resolve(".current-" + version + ".tmp");
        // 清理可能残留的临时链接
        if (Files.isSymbolicLink(tmp) || Files.exists(tmp, LinkOption.NOFOLLOW_LINKS)) {
            Files.delete(tmp);
        }
        Files.createSymbolicLink(tmp, releaseDir);
        Files.move(tmp, current, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
