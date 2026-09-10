package com.rick.site;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-0002 基线验收测试：
 * 1. Spring 上下文可启动（sharp-database / sharp-meta / sharp-fileupload 自动配置生效）
 * 2. 通过 sharp-database 配置的 DataSource 可连接 PostgreSQL
 *
 * <p>Thymeleaf 渲染由各前台 ControllerTest 覆盖;原基线 {@code /} 已由
 * {@code SiteHomeController}(TASK-0502)替换,不再断言基线模板。
 */
@SpringBootTest
class SiteApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void databaseIsReachable() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(result).isEqualTo(1);
    }
}
