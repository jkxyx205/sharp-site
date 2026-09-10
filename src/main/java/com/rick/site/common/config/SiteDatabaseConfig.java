package com.rick.site.common.config;

import com.rick.db.repository.EntityDAOManager;
import com.rick.db.repository.TableDAO;
import com.rick.db.repository.model.EntityId;
import com.rick.db.repository.support.InsertUpdateCallback;
import com.rick.db.repository.support.baseinfo.ExtendInsertUpdateCallback;
import com.rick.db.repository.support.baseinfo.ExtendTableDAOImpl;
import com.rick.site.common.context.AdminUserContext;
import com.rick.site.common.context.TenantQueryBypass;
import com.rick.site.common.model.TenantIdGetter;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 统一字段处理(参考 sharp-platform component-starter DatabaseConfig)。
 *
 * <p>以 {@link ExtendTableDAOImpl} 作为 {@code @Primary TableDAO}:
 * <ul>
 *   <li>insert 自动填充 create_by/create_time/update_by/update_time/is_deleted,
 *       日期取当前时间,用户取 {@link AdminUserContext}(登录上下文,空则 1L);</li>
 *   <li>update 自动刷新 update_by/update_time,deleteById 转为逻辑删除(is_deleted=true);</li>
 *   <li>select(单表)自动追加 is_deleted = false 过滤;</li>
 *   <li>tenant_id 由 TenantContext 注入 insert/update 条件,防跨租户写入。</li>
 * </ul>
 *
 * <p>{@link #insertUpdateCallback()} 回填上述字段到实体对象,并在实体实现
 * {@link TenantIdGetter} 时回填 tenant_id。
 *
 * <p>与参考实现的差异:sharp-site 的 {@code tenant} 表本身无 tenant_id,
 * 故 update 的 tenant 隔离条件按表(实体是否实现 TenantIdGetter)动态判定,
 * 且仅在 TenantContext 存在时追加,避免污染 TenantResolver 解析阶段(此时上下文为空)。
 *
 * @author Rick.Xu
 */
@Configuration
public class SiteDatabaseConfig {

    public static final String TENANT_ID_COLUMN = "tenant_id";

    @Bean
    @Primary
    public TableDAO tableDAO(@Autowired(required = false) NamedParameterJdbcTemplate jdbcTemplate) {
        return new ExtendTableDAOImpl(jdbcTemplate) {

            /** 租户表名集合(实体实现 TenantIdGetter),惰性构建,DAO 在容器刷新期已全部注册 */
            private volatile Set<String> tenantTableNames;

            @Override
            public long getUserId() {
                return AdminUserContext.getUserId().orElse(1L);
            }

            @Override
            protected void addInsertInfo(Map<String, Object> paramMap) {
                TenantContext.get().map(Tenant::getId)
                        .ifPresent(tid -> paramMap.put(TENANT_ID_COLUMN, tid));
            }

            @Override
            public int update(String tableName, String columnsCondition, String condition,
                              Map<String, Object> paramMap) {
                // 父类会向 paramMap 写入 baseEntityInfo.* 审计参数,须可变;参考实现同样无条件拷贝
                Map<String, Object> merged = new HashMap<>(paramMap);
                Optional<Long> tid = TenantContext.get().map(Tenant::getId);
                if (tid.isPresent() && isTenantTable(tableName)) {
                    merged.put(TENANT_ID_COLUMN, tid.get());
                    return super.update(tableName, columnsCondition,
                            condition + " AND " + TENANT_ID_COLUMN + " = :" + TENANT_ID_COLUMN, merged);
                }
                return super.update(tableName, columnsCondition, condition, merged);
            }

            @Override
            public int update(String tableName, String columnsCondition, String condition, Object... args) {
                Optional<Long> tid = TenantContext.get().map(Tenant::getId);
                if (tid.isPresent() && isTenantTable(tableName)) {
                    return super.update(tableName, columnsCondition,
                            condition + " AND " + TENANT_ID_COLUMN + " = ?",
                            ArrayUtils.addAll(args, tid.get()));
                }
                return super.update(tableName, columnsCondition, condition, args);
            }

            private boolean isTenantTable(String tableName) {
                if (tenantTableNames == null) {
                    synchronized (this) {
                        if (tenantTableNames == null) {
                            tenantTableNames = EntityDAOManager.getAllEntityDAO().stream()
                                    .filter(dao -> TenantIdGetter.class.isAssignableFrom(
                                            dao.getTableMeta().getEntityClass()))
                                    .map(dao -> dao.getTableMeta().getTableName())
                                    .collect(Collectors.toSet());
                        }
                    }
                }
                return tenantTableNames.contains(tableName);
            }

            // ---- 查询自动追加 tenant_id(参考 update 的租户隔离,CLAUDE.md §4)----
            // 仅当 TenantContext 存在且 SQL 为单表租户表查询时追加;否则透传。
            // tenant 表本身无 tenant_id 列,解析阶段(TenantContext 为空)不追加,避免污染。

            private Long getTenantId() {
                return TenantContext.get().map(Tenant::getId).orElse(null);
            }

            /** 提取单表 SQL 的 FROM 表名(框架 addIsDeletedCondition 已限定单表,此处只取首表)。 */
            private static final Pattern FROM_PATTERN =
                    Pattern.compile("(?is)\\bfrom\\s+([a-z_][a-z0-9_]*)");

            private boolean isTenantTableSelect(String sql) {
                if (sql == null) {
                    return false;
                }
                Matcher m = FROM_PATTERN.matcher(sql);
                return m.find() && isTenantTable(m.group(1));
            }

            @Override
            public <E> List<E> select(Class<E> clazz, String sql, Object... args) {
                Long tid = getTenantId();
                if (tid != null && !TenantQueryBypass.isBypassed() && isTenantTableSelect(sql)) {
                    return super.select(clazz,
                            addIsDeletedCondition(sql, () -> " AND " + TENANT_ID_COLUMN + " = ?"),
                            ArrayUtils.addAll(args, tid));
                }
                return super.select(clazz, sql, args);
            }

            @Override
            public <E> List<E> select(String sql, Map<String, Object> paramMap,
                                      com.rick.db.repository.JdbcTemplateCallback<E> callback) {
                Long tid = getTenantId();
                if (tid != null && !TenantQueryBypass.isBypassed() && isTenantTableSelect(sql)) {
                    Map<String, Object> merged = new HashMap<>(paramMap);
                    merged.put(TENANT_ID_COLUMN, tid);
                    return super.select(
                            addIsDeletedCondition(sql, () -> " AND " + TENANT_ID_COLUMN + " = :" + TENANT_ID_COLUMN),
                            merged, callback);
                }
                return super.select(sql, paramMap, callback);
            }

            @Override
            public List<Map<String, Object>> select(String sql, Object... args) {
                Long tid = getTenantId();
                if (tid != null && !TenantQueryBypass.isBypassed() && isTenantTableSelect(sql)) {
                    return super.select(
                            addIsDeletedCondition(sql, () -> " AND " + TENANT_ID_COLUMN + " = ?"),
                            ArrayUtils.addAll(args, tid));
                }
                return super.select(sql, args);
            }
        };
    }

    @Bean
    public InsertUpdateCallback insertUpdateCallback() {
        return new ExtendInsertUpdateCallback() {
            @Override
            public void handler(boolean insert, EntityId<Long> entity, Map<String, Object> args) {
                super.handler(insert, entity, args);
                if (entity instanceof TenantIdGetter getter) {
                    Object tid = args.get(TENANT_ID_COLUMN);
                    if (tid == null) {
                        // update 路径 args 为属性名键
                        tid = args.get("tenantId");
                    }
                    if (tid instanceof Long id) {
                        getter.setTenantId(id);
                    }
                }
            }
        };
    }
}
