package com.rick.site;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 建表迁移(idempotent)。仅当 {@code -PsharpMigrate}(注入 {@code sharp.migrate=true})时执行:
 * <pre>./gradlew test --tests "com.rick.site.SchemaMigrationTest" -PsharpMigrate</pre>
 *
 * <p>手动执行;{@code ./gradlew build} 默认跳过。用应用自身 {@link DataSource}(凭据在
 * {@code .env.postgres.properties}),无需本地暴露连接串。语句全部幂等(IF NOT EXISTS / IF EXISTS)。
 *
 * <p>迁移内容:tenant_config 的 company_name/company_name_short/address/copyright
 * 迁入 tenant_config_i18n(按语种);旧列从 base 删除。
 *
 * @author Rick.Xu
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "sharp.migrate", matches = "true")
class SchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrateTenantConfigI18n() {
        // 1) 新建 tenant_config_i18n(若不存在)
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS tenant_config_i18n (
                    id BIGINT PRIMARY KEY,
                    tenant_config_id BIGINT NOT NULL,
                    language VARCHAR(20) NOT NULL,
                    company_name VARCHAR(500) NOT NULL,
                    company_name_short VARCHAR(200),
                    address VARCHAR(1000),
                    copyright VARCHAR(500),
                    create_by BIGINT,
                    create_time TIMESTAMP NOT NULL,
                    update_by BIGINT,
                    update_time TIMESTAMP NOT NULL,
                    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
                )""");
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_config_i18n
                ON tenant_config_i18n (tenant_config_id, language) WHERE is_deleted = false""");

        // 2) 从 base 删除已迁出的四列(幂等:列已不存在则跳过)
        dropColumnIfExists("tenant_config", "company_name");
        dropColumnIfExists("tenant_config", "company_name_short");
        dropColumnIfExists("tenant_config", "address");
        dropColumnIfExists("tenant_config", "copyright");
    }

    private void dropColumnIfExists(String table, String column) {
        boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.columns " +
                        "WHERE table_name = ? AND column_name = ?)",
                Boolean.class, table, column);
        if (Boolean.TRUE.equals(exists)) {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
        }
    }
}
