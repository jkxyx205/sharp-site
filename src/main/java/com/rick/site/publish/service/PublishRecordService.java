package com.rick.site.publish.service;

import com.rick.db.plugin.BaseServiceImpl;
import com.rick.site.publish.dao.PublishRecordDAO;
import com.rick.site.publish.entity.PublishRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 发布记录服务(TASK-1301)。
 *
 * <p>记录每次发布的生命周期:start(PENDING) → succeed(SUCCESS) / fail(FAILED)。
 * 负责版本号生成(租户内自增 v001、v002…)。tenant_id 由框架注入/过滤(§4)。
 *
 * @author Rick.Xu
 */
@Service
public class PublishRecordService extends BaseServiceImpl<PublishRecordDAO, PublishRecord, Long> {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    public PublishRecordService(PublishRecordDAO baseDAO) {
        super(baseDAO);
    }

    /** 创建 PENDING 记录并返回(已写入 id)。 */
    @Transactional(rollbackFor = Exception.class)
    public PublishRecord start(String version) {
        PublishRecord record = PublishRecord.builder()
                .version(version)
                .status(STATUS_PENDING)
                .startedAt(LocalDateTime.now())
                .build();
        return baseDAO.insert(record);
    }

    /** 标记成功:status=SUCCESS + finishedAt。 */
    @Transactional(rollbackFor = Exception.class)
    public void succeed(Long id) {
        baseDAO.selectById(id).ifPresent(record -> {
            record.setStatus(STATUS_SUCCESS);
            record.setFinishedAt(LocalDateTime.now());
            baseDAO.update(record);
        });
    }

    /** 标记失败:status=FAILED + finishedAt + errorMessage(§20:不输出敏感信息,错误文本可记录)。 */
    @Transactional(rollbackFor = Exception.class)
    public void fail(Long id, String errorMessage) {
        baseDAO.selectById(id).ifPresent(record -> {
            record.setStatus(STATUS_FAILED);
            record.setFinishedAt(LocalDateTime.now());
            record.setErrorMessage(errorMessage);
            baseDAO.update(record);
        });
    }

    /** 当前租户发布记录(最新在前,按 id 倒序)。 */
    public List<PublishRecord> listByTenant() {
        return baseDAO.select("1=1 ORDER BY id DESC", Map.of());
    }

    /** 最新一条发布记录(若有)。 */
    public Optional<PublishRecord> latest() {
        List<PublishRecord> list = listByTenant();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** 按 version 查(租户内)。 */
    public Optional<PublishRecord> findByVersion(String version) {
        List<PublishRecord> found = baseDAO.select("version = :version", Map.of("version", version));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /**
     * 生成下一版本号:取本租户已用 version 的最大数字序号 +1,格式 {@code v001}。
     * 无历史记录时返回 {@code v001}。version 仅含数字时按数字递增,保证租户内唯一
     * (uk_tenant_version 唯一索引兜底)。
     */
    public String nextVersion() {
        int max = 0;
        for (PublishRecord r : listByTenant()) {
            int n = parseSeq(r.getVersion());
            if (n > max) {
                max = n;
            }
        }
        return String.format("v%03d", max + 1);
    }

    /** 解析 version 末尾数字(如 v002 → 2);无数字返回 0。 */
    private int parseSeq(String version) {
        if (version == null) {
            return 0;
        }
        int start = version.length();
        while (start > 0 && Character.isDigit(version.charAt(start - 1))) {
            start--;
        }
        if (start >= version.length()) {
            return 0;
        }
        try {
            return Integer.parseInt(version.substring(start));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
