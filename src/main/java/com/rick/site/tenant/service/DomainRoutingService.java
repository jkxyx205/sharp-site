package com.rick.site.tenant.service;

import com.rick.site.common.context.TenantQueryBypass;
import com.rick.site.tenant.dao.TenantDomainDAO;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.entity.TenantDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 域名 → 静态站点目录的路由维护(nginx 免手动配置)。
 *
 * <p>nginx 配置为单条 {@code root /{wwwRoot}/by-host/$host;},不再按域名逐一映射。
 * 本服务在 {@code by-host/} 目录下为每个启用域名创建一个符号链接:
 * <pre>by-host/{domain} → {wwwRoot}/{tenantCode}/current</pre>
 * {@code current} 是发布时原子切换的软链(见 {@link com.rick.site.publish.service.PublishService}),
 * 故域名软链建好后,后续每次发布自动指向最新 release,无需再动路由。
 *
 * <p>调用时机:绑定域名(add)、停用/启用(updateStatus)、删除(delete)时由
 * {@link TenantDomainService} 触发;启动时 {@link #resyncAll} 按 DB 全量重建修正漂移。
 *
 * <p>路由失败仅记日志、不抛出——DB 是事实来源,路由为派生状态;启动 resync 兜底。
 *
 * @author Rick.Xu
 */
@Service
public class DomainRoutingService {

    private static final Logger log = LoggerFactory.getLogger(DomainRoutingService.class);

    private static final short STATUS_ENABLED = 1;

    @Value("${sharp.site.www-root:data/www}")
    private String wwwRoot;

    private final TenantDomainDAO domainDAO;
    private final TenantService tenantService;

    public DomainRoutingService(TenantDomainDAO domainDAO, TenantService tenantService) {
        this.domainDAO = domainDAO;
        this.tenantService = tenantService;
    }

    /**
     * 启用域名:原子创建/替换 {@code by-host/{domain} → current} 软链。
     *
     * @param domain     已规范化的域名(小写)
     * @param tenantCode 域名所属租户编码
     */
    public void enable(String domain, String tenantCode) {
        try {
            Files.createDirectories(byHostDir());
            Path link = hostLink(domain);
            Path target = tenantCurrent(tenantCode);
            // 原子替换:先建临时链接再 rename,避免半切换(与 PublishService.atomicSwitch 同模式)
            Path tmp = byHostDir().resolve("." + domain + ".tmp");
            if (Files.isSymbolicLink(tmp) || Files.exists(tmp, LinkOption.NOFOLLOW_LINKS)) {
                Files.delete(tmp);
            }
            Files.createSymbolicLink(tmp, target);
            Files.move(tmp, link, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("域名路由启用: {} -> {}", domain, target);
        } catch (IOException e) {
            log.error("启用域名路由失败 {}: {}", domain, e.getMessage(), e);
        }
    }

    /** 停用/删除域名:移除 {@code by-host/{domain}} 软链(不存在则忽略)。 */
    public void disable(String domain) {
        try {
            Path link = hostLink(domain);
            if (Files.isSymbolicLink(link) || Files.exists(link, LinkOption.NOFOLLOW_LINKS)) {
                Files.delete(link);
                log.info("域名路由停用: {}", domain);
            }
        } catch (IOException e) {
            log.warn("移除域名路由失败 {}: {}", domain, e.getMessage());
        }
    }

    /**
     * 启动时按 DB 全量重建 by-host 软链:补建缺失、清理多余,修正手动删文件/迁移造成的漂移。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void resyncAll() {
        try {
            Files.createDirectories(byHostDir());
        } catch (IOException e) {
            log.error("创建 by-host 目录失败,路由 resync 中止: {}", e.getMessage());
            return;
        }
        Map<String, String> domainToCode = new HashMap<>();
        for (TenantDomain d : listAllEnabled()) {
            tenantService.findById(d.getTenantId())
                    .filter(t -> t.getStatus() != null && t.getStatus() == STATUS_ENABLED)
                    .map(Tenant::getCode)
                    .ifPresent(code -> domainToCode.put(d.getDomain(), code));
        }
        // 清理 by-host 下不在 DB(启用)的多余软链
        Set<String> keep = domainToCode.keySet();
        try (Stream<Path> paths = Files.list(byHostDir())) {
            paths.filter(Files::isSymbolicLink)
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        if (!keep.contains(name)) {
                            try {
                                Files.delete(p);
                                log.info("resync 清理多余路由: {}", name);
                            } catch (IOException e) {
                                log.warn("清理路由失败 {}: {}", name, e.getMessage());
                            }
                        }
                    });
        } catch (IOException e) {
            log.warn("列举 by-host 失败: {}", e.getMessage());
        }
        // 创建/刷新
        domainToCode.forEach(this::enable);
        log.info("域名路由 resync 完成,启用 {} 个域名", domainToCode.size());
    }

    /** 跨租户查询全部启用域名(resync 用,经 TenantQueryBypass 跳过租户隔离)。 */
    private List<TenantDomain> listAllEnabled() {
        return TenantQueryBypass.supply(() ->
                domainDAO.select("status = :status", Map.of("status", STATUS_ENABLED)));
    }

    private Path byHostDir() {
        return Paths.get(wwwRoot).resolve("by-host");
    }

    private Path hostLink(String domain) {
        return byHostDir().resolve(domain);
    }

    /** 软链目标:租户发布 current 目录(绝对路径,保证软链可移植)。 */
    private Path tenantCurrent(String tenantCode) {
        return Paths.get(wwwRoot).resolve(tenantCode).resolve("current").toAbsolutePath().normalize();
    }
}
