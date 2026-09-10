# sharp-site

多租户外贸 SaaS / CMS —— 按租户隔离的站点内容管理 + Thymeleaf 静态化发布。

基于 JDK 17 + Gradle + Spring Boot 3.5.7 + Thymeleaf + Spring Security + PostgreSQL,
复用内部组件 `sharp-database`(注解驱动 ORM)、`sharp-meta`、`sharp-fileupload`。

> 设计与约束详见 `docs/`(REQUIREMENTS / ARCHITECTURE / DATABASE / TASKS / CLAUDE)。
> 本 README 只覆盖启动与运维。

## 技术栈

| 层 | 选型 |
|---|---|
| 运行时 | JDK 17 |
| 构建 | Gradle(Wrapper) |
| 框架 | Spring Boot 3.5.7(Web / Thymeleaf / JDBC / Validation / AOP / Security) |
| 数据库 | PostgreSQL |
| ORM | `sharp-database`(注解驱动,基于 `NamedParameterJdbcTemplate`,非 JPA) |
| 文件上传 | `sharp-fileupload`(本地存储,可对接 OSS) |
| 模板 | Thymeleaf(`themes/modern`) |

## 启动方式

### 1. 准备 JDK 17

```bash
export JAVA_HOME="/path/to/jdk-17"
# macOS IntelliJ 自带 JBR 示例:
# export JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"
java -version   # 确认 17
```

### 2. 数据库连接

连接信息通过环境变量注入(由 `application.yml` 占位):
`JDBC.URL` / `JDBC.USERNAME` / `JDBC.PASSWORD`。

本地开发默认从 classpath 的 `.env.postgres.properties`(gitignored)读取,
该文件形如:

```properties
JDBC.URL=jdbc:postgresql://<host>:5432/sharp_site
JDBC.USERNAME=<user>
JDBC.PASSWORD=<password>
```

> 不要把凭据提交进仓库。`.env.postgres.properties` 位于 `src/main/resources/`,
> 已在 `.gitignore` 中。

### 3. 初始化数据库

应用**不会**自动执行 DDL。手工执行 `sql/schema.sql`:

```bash
psql -h <host> -U <user> -d sharp_site -f sql/schema.sql
# 脚本可重复执行:已存在的表报错可忽略
```

### 4. 构建与运行

```bash
./gradlew clean build        # 编译 + 全量测试
./gradlew bootRun            # 本地启动(默认端口 8080)
# 或构建后运行:
./gradlew bootBuildImage / java -jar build/libs/sharp-site-*.jar
```

应用启动后 `http://127.0.0.1:8080`。

## Gradle 构建命令

| 命令 | 作用 |
|---|---|
| `./gradlew clean build` | 清理 + 编译 + 全量测试 + 打包 |
| `./gradlew build -x test` | 跳过测试快速打包 |
| `./gradlew test` | 运行所有测试 |
| `./gradlew test --tests "com.rick.site.admin.*"` | 运行指定包测试 |
| `./gradlew bootRun` | 本地运行 |

> 测试 JVM 内多个缓存的 Spring 上下文各持一个 HikariPool。为避免连接数超出
> 远程库 `max_connections`,`build.gradle` 的 `test` 任务已将
> `spring.datasource.hikari.maximum-pool-size` 压到 3。生产部署请按库上限调整。

## 多租户与本地测试域名

租户由 Host 头解析(`TenantFilter`):请求 `a.localhost` → 解析到
`tenant_domain` 中 `domain = a.localhost` 的租户。

本地开发测试域名:

1. `hosts` 映射(或 MockMvc `setServerName`):
   ```
   127.0.0.1  a.localhost
   127.0.0.1  b.localhost
   ```
2. 在 `tenant_domain` 表为各租户绑定对应域名(后台 `/admin/domains` 管理)。
3. 管理员在所属租户域名下访问 `/admin/login`,按 `(tenant_id, username)` 登录;
   认证后 `TenantContext` 以管理员 tenantId 为权威来源(非 Host),防止越权。

## 静态化目录

发布服务将每个租户的静态站点落盘到:

```
{sharp.site.www-root}/{tenantId}/releases/{version}/   # 每个版本一个 release
{sharp.site.www-root}/{tenantId}/current               # 符号链接 → 当前 release
```

- `sharp.site.www-root` 配置项默认 `data/www`(开发),生产建议绝对路径如 `/data/www`。
- 发布流程:版本号生成 → `StaticSiteGenerator` 渲染 → 原子切换 `current` 符号链接
  (POSIX rename 原子,失败不影响线上版本)。
- 渲染复用前台 Thymeleaf 模板,经 `OfflineWebContext` 离线渲染到文件。

## 发布方式

后台 `/admin/publish`:

1. 进入后台 → 网站发布。
2. 点击发布 → 生成新版本号(`v001`、`v002`…),渲染静态文件,原子切换 `current`。
3. 成功记录 `SUCCESS`;失败记录 `FAILED` + 错误信息,`current` 不变(线上版本不受影响)。
4. 发布历史在 `/admin/publish` 列表查看。

## Nginx 配置

静态站点由 Nginx 直接托管,按 Host → 租户目录映射。模板见
[`nginx/sharp-site.conf.template`](nginx/sharp-site.conf.template):

```
domain ──► /data/www/{tenantId}/current
```

两种方式(模板内均有示例):

- **方式 A**(域名少):为每个域名生成独立 `server` 块,`root` 硬绑到该租户 `current`。
- **方式 B**(域名多):`map $host $tenant_id` 动态拼 `root`,统一一个 `server`。

域名 → tenantId 映射来源:应用库 `tenant_domain` 表。部署脚本可定期从该表
刷新 Nginx map 文件。租户隔离由 per-tenant 子目录 + `root` 约束保证。

## 项目结构

```
com.rick.site
├── tenant      租户 / 域名 / 配置 + TenantContext + TenantFilter
├── theme       主题解析(ThemeResolver)
├── i18n        多语言(LocaleResolver,支持 zh-CN / en-US)
├── home        首页板块
├── catalog     分类(PRODUCT / NEWS 共用)
├── product     产品 + i18n + 规格 JSONB
├── news        新闻/文章 + i18n
├── page        静态页面 + i18n + HTML 清洗
├── media       媒体上传(sharp-fileupload, MIME/扩展/大小校验)
├── seo         SEO 配置 + og 标签注入
├── admin       管理员认证(BCrypt / Spring Security)+ 后台 CRUD
├── publish     静态站点生成 + 发布 + 原子切换
├── preview     预览(认证后访问)
├── web         公开前台路由(按 Host 解析租户)
└── common      TenantBaseEntity / TenantContext / DatabaseConfig
```

## 更多

- 设计需求:`docs/REQUIREMENTS.md`
- 架构与分层:`docs/ARCHITECTURE.md`
- 数据库表结构:`docs/DATABASE.md` / `sql/schema.sql`
- 任务清单与 DoD:`docs/TASKS.md`
- 开发约束(安全 / 租户隔离 / 日志):`docs/CLAUDE.md`
