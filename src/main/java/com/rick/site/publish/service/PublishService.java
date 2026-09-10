package com.rick.site.publish.service;

import com.rick.common.http.exception.BizException;
import com.rick.site.publish.entity.PublishRecord;
import com.rick.site.tenant.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;

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
        Long tenantId = TenantContext.requireTenantId();
        String version = recordService.nextVersion();
        PublishRecord record = recordService.start(version);
        try {
            Path releaseDir = generator.generate(version);
            atomicSwitch(tenantId, version, releaseDir);
            recordService.succeed(record.getId());
            return recordService.findByVersion(version).orElse(record);
        } catch (Exception e) {
            // §20:错误信息可记录,但不含敏感凭据;发布失败必须记录(§20)。
            log.error("发布失败 tenant={} version={}: {}", tenantId, version, e.getMessage(), e);
            recordService.fail(record.getId(), e.getMessage());
            throw new BizException("发布失败: " + e.getMessage());
        }
    }

    /** {@code current} 符号链接目标(供校验/测试读取)。 */
    public Path currentPath(Long tenantId) {
        return Paths.get(wwwRoot).resolve(tenantId.toString()).resolve("current");
    }

    /** 原子切换 current → 新 release。 */
    private void atomicSwitch(Long tenantId, String version, Path releaseDir) throws IOException {
        Path tenantRoot = Paths.get(wwwRoot).resolve(tenantId.toString());
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
