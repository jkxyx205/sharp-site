# 外贸独立站 SaaS 平台开发需求文档

**版本：v1.0**  
**阶段：Phase 2 MVP**

## 1. 项目目标

构建一套面向外贸企业的多租户独立站 SaaS / CMS 平台。

一套 Spring Boot 应用管理多个企业网站：

```text
Tenant
  ↓
Domain
  ↓
Theme
  ↓
Page / Product / Article
  ↓
i18n
  ↓
SEO
  ↓
Preview / Static Publish
  ↓
Nginx
```

核心原则：

- Tenant 与 Theme 解耦
- Theme 与 Content 解耦
- Content 与 Language 解耦
- 正式网站优先使用静态 HTML
- Spring Boot 负责后台、预览、CMS、发布和动态兜底
- Nginx 负责正式静态网站访问

## 2. 技术栈

必须使用：

- JDK 17
- Gradle
- Spring Boot 3.5.7
- Spring MVC
- Thymeleaf
- Spring Security
- PostgreSQL

### JDK 环境

系统默认 Java 可能是 1.8，工具链要求 **JDK 17**。所有 `gradle` / `java` 相关命令必须显式指定 JDK 17，例如：

```bash
export JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"  # JBR 17
```

不要依赖系统默认 `JAVA_HOME` 或 PATH 中的 Java 8。

### 内部组件依赖（Gradle）

优先复用已有 Sharp 组件，不要自行实现同等能力：

```gradle
api 'com.rick.db:sharp-database:0.0.1-SNAPSHOT'       // 数据库操作
api 'com.rick.meta:sharp-meta:0.0.1-SNAPSHOT'         // 字典相关操作
api 'com.rick.fileupload:sharp-fileupload:0.0.1-SNAPSHOT' // 文件上传
```

数据库访问、字典、文件上传分别通过上述依赖完成，禁止另起一套 ORM / 字典 / 上传实现，除非文档明确批准。

前台：

- HTML5
- CSS3
- JavaScript
- Thymeleaf
- Responsive Web Design

不要求 React / Vue / Angular。

## 3. Phase 2 必须完成

1. 多租户
2. 自定义域名绑定
3. Theme 模板系统
4. 首页
5. 普通页面
6. 产品分类和产品
7. 新闻分类和新闻
8. 企业固定信息
9. 图片 / 媒体管理
10. 中文、英文多语言
11. SEO
12. 后台用户名密码登录
13. 富文本编辑
14. 网站预览
15. 网站静态化
16. 网站发布
17. Sitemap
18. robots.txt
19. 发布记录
20. 发布失败不影响当前线上版本

## 4. 明确不做

Phase 2 不实现：

- 在线拖拽建站
- 在线修改 HTML/CSS
- 在线模板设计器
- 模板市场
- SaaS 套餐计费
- 在线支付
- 自动 SSL
- CDN 管理
- Google Analytics 在线配置
- Google Search Console 自动提交
- AI 建站
- AI 文章生成
- 用户注册
- 企业员工权限体系
- 多级 RBAC

页面编辑采用结构化字段 + 富文本，而不是直接编辑 HTML 模板。

## 5. 多租户

每个企业网站是一个 Tenant。

示例：

```text
tenant 100 → www.company-a.com → modern
tenant 200 → www.company-b.com → industrial
tenant 300 → www.company-c.com → modern
```

域名与租户为独立关系。

请求通过 HTTP Host 解析租户：

```text
Host
 ↓
TenantResolver
 ↓
Tenant
 ↓
Theme
 ↓
Controller / Service
 ↓
Thymeleaf
```

所有业务数据必须包含 tenant_id 或通过明确的租户关系进行隔离。

任何后台和前台查询都不得跨租户读取或修改数据。

## 6. Theme

Theme 是开发人员维护的网站设计模板。

推荐：

```text
src/main/resources/templates/themes/
├── modern/
│   ├── index.html
│   ├── about.html
│   ├── products.html
│   ├── product-detail.html
│   ├── news.html
│   ├── news-detail.html
│   ├── contact.html
│   └── fragments/
│
└── industrial/
    └── ...
```

Tenant 通过 theme_id 选择模板。

禁止在 Controller 中出现：

```java
if (tenantId == 100) { ... }
```

必须通过 ThemeResolver / TemplateResolver 动态解析模板。

同一个 Theme 可以被多个 Tenant 使用。

## 7. 网站页面

第一套 Theme 至少提供：

- 首页
- 关于我们
- 产品列表
- 产品详情
- 新闻列表
- 新闻详情
- 联系我们

建议 URL：

```text
/
/about
/products
/products/{slug}
/news
/news/{slug}
/contact
```

多语言建议：

```text
/
/about
/products

/zh-cn/
/zh-cn/about
/zh-cn/products

```

默认语言可不带语言前缀，其他语言使用前缀。

实际规则必须统一，由统一的 Locale / URL 组件处理。

## 8. 页面编辑

普通页面使用结构化数据。

例如：

```text
About
├── title
├── content
├── image
└── SEO
```

正文支持富文本。

富文本 HTML 必须经过 XSS 清洗。

禁止后台用户直接修改 Thymeleaf 模板。

## 9. 首页区块

首页由开发人员定义固定区块，例如：

```text
Hero
Company Introduction
Products
Advantages
News
Contact
Footer
```

后台只维护这些区块的数据。

Phase 2 不做拖拽区块编辑器。

## 10. 产品

支持：

- 产品分类
- 产品
- 产品详情
- 产品图片
- 产品简介
- 产品规格
- 富文本详情
- slug
- 排序
- 发布状态

产品支持多语言。

## 11. 新闻

支持：

- 新闻分类
- 新闻
- 封面
- 标题
- 摘要
- 正文
- 作者
- 发布时间
- slug
- 发布状态
- 排序

新闻支持多语言。

URL 使用 slug，不使用 `?id=123` 作为正式 SEO URL。

## 12. 企业信息

至少支持：

- Logo
- 公司名称
- 公司简称
- 地址
- 电话
- 手机
- Email
- WhatsApp
- Facebook
- LinkedIn
- YouTube
- Copyright

字段可根据实际实现调整。

## 13. 多语言

Phase 2：

- zh-CN
- en-US

架构必须允许以后增加：

- de-DE
- fr-FR
- es-ES
- it-IT
- ja-JP
- ko-KR

业务内容采用：

```text
product
product_i18n

article
article_i18n

page
page_i18n
```

不要为每种语言复制整套模板。

## 14. 图片 / 媒体

后台提供媒体管理。

支持：

- 上传
- 列表
- 删除
- URL / object key
- alt
- title

至少支持：

- JPG
- JPEG
- PNG
- WEBP
- SVG

上传时必须校验：

- MIME
- 扩展名
- 文件大小

禁止仅根据文件名判断文件类型。

文件上传必须使用 `sharp-fileupload`（`com.rick.fileupload:sharp-fileupload:0.0.1-SNAPSHOT`）。若组件已对接 OSS，图片优先上传 OSS，数据库保存 URL 或 object key。

## 15. SEO

每个页面和可索引内容必须支持：

- SEO Title
- SEO Description
- SEO Keywords
- Canonical
- Robots
- OG Title
- OG Description
- OG Image

页面 HTML 必须正确生成：

```html
<title>...</title>
<meta name="description" content="...">
<link rel="canonical" href="...">
```

同时支持：

```text
/sitemap.xml
/robots.txt
```

图片需要可配置 alt。

页面 URL 应可读。

## 16. 后台

后台入口：

```text
/admin/login
```

用户名 + 密码登录。

使用 Spring Security。

密码使用 BCrypt 等安全哈希，禁止明文保存。

后台主要菜单：

```text
Dashboard
网站设置
  ├── 企业信息
  ├── 网站配置
  └── 多语言
页面管理
产品管理
  ├── 产品分类
  └── 产品
新闻管理
  ├── 新闻分类
  └── 新闻
媒体管理
SEO
网站发布
```

## 17. 预览

后台修改数据后支持预览。

预览数据不能破坏正式网站。

建议：

```text
draft
published
```

或使用动态预览 + 已发布静态版本。

## 18. 静态化

发布流程：

```text
保存数据库
 ↓
点击发布
 ↓
读取 Tenant
 ↓
读取 Theme
 ↓
读取页面 / 产品 / 新闻 / SEO / i18n
 ↓
Thymeleaf 渲染
 ↓
生成 HTML
 ↓
生成 sitemap.xml
 ↓
生成 robots.txt
 ↓
生成静态资源引用
 ↓
发布新版本
```

推荐目录：

```text
/data/www/{tenantId}/
├── releases/
│   ├── v001/
│   └── v002/
└── current -> releases/v002
```

正式网站不应直接覆盖 current 中正在服务的文件。

发布必须具有版本概念。

## 19. 发布记录

至少记录：

```text
id
tenant_id
version
status
started_at
finished_at
error_message
created_at
```

状态：

```text
PENDING
RUNNING
SUCCESS
FAILED
```

如果新版本发布失败，current 必须继续指向旧版本。

## 20. 自定义域名

域名独立管理。

一个 Tenant 可以有多个域名：

```text
example.com
www.example.com
```

其中一个可以是 primary。

Phase 2 只实现域名绑定和 Host → Tenant 解析。

不要求自动 DNS 验证和自动 SSL。

## 21. 安全

必须考虑：

- XSS
- CSRF
- SQL Injection
- 文件上传安全
- 路径穿越
- 越权
- 租户隔离
- Session 安全
- 密码安全

所有后台操作都必须验证当前用户和当前 Tenant。

## 22. 代码规范

采用：

```text
Controller
 ↓
Service
 ↓
Repository / Mapper
 ↓
Database
```

Controller 不直接访问数据库。

复杂业务逻辑放 Service。

后台 API 使用 Request / Response DTO，不直接暴露 Entity。

## 23. 测试要求

每完成一个主要模块必须：

1. 编译
2. 单元测试 / 集成测试
3. 启动测试
4. 基本接口测试
5. 通过后再继续下一个模块

至少覆盖：

- Tenant 解析
- 租户数据隔离
- Theme 解析
- 多语言
- 产品 CRUD
- 新闻 CRUD
- 富文本 XSS 清洗
- 域名绑定
- 静态化
- 发布失败回滚
- Sitemap
- robots.txt

## 24. 最小验收

### 多租户

```text
tenant 100 → a.example.com
tenant 200 → b.example.com
```

两个域名显示不同企业内容。

### Theme

```text
tenant 100 → modern
tenant 200 → modern
tenant 300 → industrial
```

无需修改 Java 代码即可切换 Theme。

### 多语言

```text
/en/
/zh-cn/
```

产品、新闻、页面均能正确显示对应语言。

### CMS

后台修改企业信息、页面、产品、新闻后，预览可以立即看到。

### SEO

页面源码存在 title、description、canonical。

### 静态化

发布后生成：

```text
index.html
about/index.html
products/index.html
products/{slug}/index.html
news/index.html
news/{slug}/index.html
sitemap.xml
robots.txt
```

### 发布失败

新版本失败时，旧版本仍然正常访问。

### 租户安全

Tenant 100 无法读取、修改、删除 Tenant 200 数据。

---

# 25. 实施原则

本项目优先保证架构正确性，而不是快速堆积功能。

核心架构关系：

```text
Tenant
  ↓
Domain
  ↓
Theme
  ↓
Page / Product / Article
  ↓
i18n
  ↓
SEO
  ↓
Preview
  ↓
Static Publish
```

Tenant 与 Theme 必须解耦。

Theme 与 Content 必须解耦。

Content 与 Language 必须解耦。

正式网站优先使用静态 HTML。

Spring Boot 主要负责：

- Admin
- Preview
- CMS
- Publish
- Dynamic fallback

Nginx 主要负责：

- Static Website
- Domain Routing
- Static Asset

任何会破坏上述架构的实现方案，必须先说明原因，不得直接改变架构。
