# TASKS.md

# Phase 2 开发任务清单

## 使用方式

编码 Agent 必须按顺序执行任务。

原则：

```text
一个任务
 ↓
实现
 ↓
编译
 ↓
测试
 ↓
验证
 ↓
下一个任务
```

不要一次性实现全部功能。

---

# Phase 0：项目确认

## TASK-0001 项目检查

检查：

- JDK 17（命令必须显式 `JAVA_HOME`，系统默认可能是 1.8）
- Gradle
- Spring Boot
- 当前包结构
- 当前数据库（应基于 `sharp-database`）
- 当前依赖（含 `sharp-database` / `sharp-meta` / `sharp-fileupload`）
- 当前配置
- 已有安全配置
- 已有 Thymeleaf 配置

JDK 示例：

```bash
export JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"  # JBR 17
```

要求：

不要覆盖已有有效代码。

输出：

```text
当前项目结构
当前技术栈
现有可复用模块
需要新增模块
```

## TASK-0002 建立开发基线

要求：

- 使用 Gradle 构建
- 显式 JDK 17 下项目可以启动
- 通过 `sharp-database` 连接数据库
- Thymeleaf 可以渲染
- 测试可以运行

验收：

```text
./gradlew build SUCCESS（JAVA_HOME=JDK 17）
application startup SUCCESS
```

---

# Phase 1：Tenant

## TASK-0101 Tenant Entity / Mapper / Service

实现：

- Tenant
- TenantRepository / Mapper
- TenantService

## TASK-0102 TenantDomain

实现：

- tenant_domain
- 域名 CRUD

## TASK-0103 TenantConfig

实现企业固定信息。

## TASK-0104 TenantResolver

实现：

```text
Host
 ↓
Tenant
```

测试：

```text
a.example.com → tenant 100
b.example.com → tenant 200
```

## TASK-0105 TenantContext

实现请求级 TenantContext。

必须：

- 设置
- 获取
- clear

增加线程复用安全测试。

---

# Phase 2：Theme

## TASK-0201 ThemeResolver

实现：

```text
Tenant.themeId
 ↓
ThemeResolver
 ↓
themes/{themeId}/...
```

禁止 Controller 根据 tenantId 判断模板。

## TASK-0202 modern Theme

建立：

```text
modern/
├── index.html
├── about.html
├── products.html
├── product-detail.html
├── news.html
├── news-detail.html
├── contact.html
└── fragments/
```

## TASK-0203 响应式

确保：

- Mobile
- Tablet
- Desktop

正常。

---

# Phase 3：i18n

## TASK-0301 LocaleResolver

支持：

```text
/
 /zh-cn/
```

默认语言来自 Tenant。

## TASK-0302 i18n Service

实现统一语言查询。

## TASK-0303 Language fallback

指定语言无内容时回退 Tenant 默认语言。

---

# Phase 4：普通页面

## TASK-0401 Page

实现：

- site_page
- site_page_i18n
- CRUD

## TASK-0402 Page 前台

实现：

```text
/about
/contact
```

## TASK-0403 富文本

接入富文本编辑器。

## TASK-0404 HTML Sanitizer

富文本保存前清洗。

增加 XSS 测试。

---

# Phase 5：首页区块

## TASK-0501 Home Section

实现：

- home_section
- home_section_i18n

## TASK-0502 首页

实现：

```text
Hero
Company
Products
Advantages
News
Contact
```

后台可以编辑区块内容。

---

# Phase 6：产品

## TASK-0601 Product Category

实现产品分类 CRUD。

## TASK-0602 Product

实现：

- Product CRUD
- slug
- status
- sort
- cover

## TASK-0603 Product i18n

实现：

- name
- subtitle
- description
- content
- specification

## TASK-0604 产品前台

实现：

```text
/products
/products/{slug}
```

## TASK-0605 产品 SEO

产品支持 SEO 字段。

---

# Phase 7：新闻

## TASK-0701 News Category

新闻分类 CRUD。

## TASK-0702 Article

新闻 CRUD。

## TASK-0703 Article i18n

实现多语言。

## TASK-0704 新闻前台

实现：

```text
/news
/news/{slug}
```

## TASK-0705 新闻 SEO

实现新闻 SEO。

---

# Phase 8：媒体

## TASK-0801 Media

实现：

- 上传（必须使用 `sharp-fileupload`）
- 列表
- 删除

依赖：

```gradle
api 'com.rick.fileupload:sharp-fileupload:0.0.1-SNAPSHOT'
```

## TASK-0802 文件安全

实现 / 确认 `sharp-fileupload` 已覆盖：

- MIME 校验
- 扩展名校验
- 大小限制

## TASK-0803 OSS

若 `sharp-fileupload` 已对接 OSS：

```text
Upload
 ↓
sharp-fileupload
 ↓
OSS（如已配置）
 ↓
media
```

---

# Phase 9：后台认证

## TASK-0901 Admin User

实现 admin_user。

## TASK-0902 Spring Security

实现：

```text
/admin/login
```

## TASK-0903 Tenant Scope

管理员只能操作所属 Tenant。

必须增加越权测试。

---

# Phase 10：SEO

## TASK-1001 SEO Config

实现 SEO CRUD。

## TASK-1002 HTML Meta

实现：

- title
- description
- canonical
- robots
- OG

## TASK-1003 Sitemap

实现：

```text
/sitemap.xml
```

## TASK-1004 Robots

实现：

```text
/robots.txt
```

---

# Phase 11：Preview

## TASK-1101 Preview

实现后台预览。

要求：

- 读取最新 draft
- 不影响 published
- 使用真实 Theme
- 支持多语言

---

# Phase 12：Static Generator

## TASK-1201 Generator 基础

实现：

```text
StaticSiteGenerator
```

## TASK-1202 首页静态化

生成：

```text
index.html
```

## TASK-1203 普通页面静态化

生成：

```text
about/index.html
contact/index.html
```

## TASK-1204 产品静态化

生成：

```text
products/index.html
products/{slug}/index.html
```

## TASK-1205 新闻静态化

生成：

```text
news/index.html
news/{slug}/index.html
```

## TASK-1206 Sitemap / Robots 静态化

生成：

```text
sitemap.xml
robots.txt
```

---

# Phase 13：Publish

## TASK-1301 PublishRecord

实现发布记录。

## TASK-1302 Version

版本目录：

```text
/data/www/{tenantId}/releases/v001/
```

## TASK-1303 Atomic Switch

实现：

```text
current -> releases/v001
```

发布成功后原子切换。

## TASK-1304 Publish Failure

模拟失败：

```text
v002 FAILED
```

要求：

```text
current -> v001
```

不能破坏线上版本。

## TASK-1305 Publish UI

后台显示：

- 发布
- 发布状态
- 发布时间
- 版本
- 错误信息

---

# Phase 14：Domain + Nginx

## TASK-1401 Domain Management

后台实现：

- 添加域名
- 删除域名
- 设置 primary

## TASK-1402 Nginx 静态目录

验证：

```text
domain
 ↓
/data/www/{tenant}/current
```

## TASK-1403 Domain Isolation

验证：

Tenant A 无法访问 Tenant B 静态文件。

---

# Phase 15：最终测试

## TASK-1501 Multi Tenant Test

至少：

```text
tenant 100
tenant 200
```

## TASK-1502 Theme Test

```text
100 → modern
200 → modern
300 → industrial
```

## TASK-1503 Language Test

```text
English
Chinese
```

## TASK-1504 CMS Test

验证：

- page
- product
- article
- category
- media

## TASK-1505 SEO Test

检查页面源码。

## TASK-1506 Static Test

检查所有静态 HTML。

## TASK-1507 Publish Rollback Test

验证失败不影响线上版本。

## TASK-1508 Security Test

验证：

- XSS
- SQL Injection
- 越权
- 租户隔离
- 文件上传
- 路径穿越

---

# Phase 16：代码整理

## TASK-1601

删除：

- 无用代码
- Debug
- 临时接口
- 测试数据

## TASK-1602

统一：

- Exception
- Response
- DTO
- Validation
- Logging

## TASK-1603

完善 README：

- 启动方式（含 `export JAVA_HOME=...` JDK 17）
- Gradle 构建命令
- 数据库（`sharp-database`）
- 本地测试域名
- 静态化目录
- 发布方式
- Nginx 配置说明

---

# Phase 18：按租户多语言 + 模板 i18n + 多语种静态发布 + 逻辑分页

> 需求依据：REQUIREMENTS.md §26。逐任务实现 → 编译 → 测试 → 验证。

## TASK-1801 按租户语种

- `tenant.languages` 列（VARCHAR，逗号分隔）+ 实体字段 + 迁移（存量 = default_language）。
- `Tenant.enabledLanguages()` 返回 List<String>；`isMultiLanguage()` = size > 1。
- `DefaultLocaleResolver`：默认语言仍来自 tenant；非默认语种段是否启用取决于 `languages`。
- 测试：多语言 vs 单语言租户的语言集合解析。

## TASK-1802 后台按租户语种编辑

- admin 5 个表单 `addLanguages`：由 `localeResolver.supportedLanguages()` 改为 `tenant.enabledLanguages()`。
- 单语言租户：表单不渲染语种选择/fieldset（单栏直接编辑）。
- 保存：仅按启用语种写 i18n 行。
- 测试：单语言租户编辑产品 → 仅该语种 i18n 行。

## TASK-1803 模板 i18n（messages.json）

- `MessageSource` bean（`classpath:i18n/messages`）+ Thymeleaf `#{}` 集成。
- `messages.properties` / `messages_zh_CN.properties` / `messages_en_US.properties`。
- modern 主题界面文案改 `#{key}`：nav / hero / 按钮 / footer。
- `LocaleFilter` 设置 `LocaleContextHolder`；`OfflineWebContext(locale)`。
- 测试：`#{}` 在 zh-cn / en-us 下分别解析为对应文案。

## TASK-1804 多语种静态发布

- `StaticSiteGenerator.generate`：按 `tenant.enabledLanguages()` 遍历；每语种生成 `/{L}/` 镜像。
- 多语言：根 `index.html` 重定向到默认语种；`sitemap.xml` 含各语种 URL。
- 单语言：保持根 `/` 发布。
- 测试：多语言发布产出 `{L}/index.html` 等；单语言发布产出根 `index.html`。

## TASK-1805 逻辑分页

- `StaticSiteGenerator` 产品/新闻列表分页生成 `products/page/{n}/index.html`、`news/page/{n}/index.html`。
- 列表模板加分页导航；仅渲染当前页条目。
- 动态 `/products?page=N`、`/news?page=N` 同样逻辑分页。
- 测试：N+1 条产品 → 产出 2 页静态文件；分页链接正确。

## TASK-1806 验收

- 多语言租户发布 → `/zh-cn/` 与 `/en-us/` 镜像齐全；`/` 重定向。
- 单语言租户发布 → 根 `index.html`，无 locale 目录。
- 列表多页 + 详情静态跳转。
- `./gradlew clean build` 全绿。

---

# Definition of Done

一个 Task 只有满足以下条件才算完成：

- 代码完成
- 编译通过
- 测试通过
- 无明显异常日志
- 不破坏现有功能
- 不破坏租户隔离
- 符合 ARCHITECTURE.md
- 不引入未经批准的技术栈
