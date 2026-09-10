# ARCHITECTURE.md

# 外贸独立站 SaaS 平台系统架构

## 1. 总体架构

```text
                         Internet
                            │
                     ┌──────▼──────┐
                     │    Nginx    │
                     └──────┬──────┘
                            │
             ┌──────────────┴──────────────┐
             │                             │
       Static Website                 Spring Boot
             │                        Admin / Preview
             │                             │
      /data/www/{tenant}/                  │
                                           │
                              ┌────────────┼────────────┐
                              │            │            │
                           Tenant        CMS         Publish
                              │            │            │
                              └────────────┼────────────┘
                                           │
                                      PostgreSQL
                                      (sharp-database)
                                           │
                              ┌────────────┼────────────┐
                              │            │            │
                           Content       i18n          SEO
                              │
                      sharp-fileupload → OSS
```

## 2. 核心模块

```text
com.xxx.website
├── admin
├── tenant
├── site
├── theme
├── page
├── product
├── news
├── category
├── media
├── i18n
├── seo
├── publish
└── common
```

模块职责：

### tenant
租户、域名、TenantContext、租户解析。

### theme
Theme 元数据和 Thymeleaf 模板解析。

### site
网站配置、企业信息、语言配置。

### page
普通页面和首页区块。

### product
产品及产品多语言。

### news
新闻及新闻多语言。

### category
产品 / 新闻分类。

### media
图片和媒体。上传能力复用 `sharp-fileupload`，不要自建上传实现。

### i18n
语言识别和语言切换。

### seo
SEO 元数据、Sitemap、robots。

### publish
静态化、版本、发布、回滚。

### admin
后台认证和管理页面。

## 3. 请求流程

### 动态网站请求

```text
HTTP Request
 ↓
TenantFilter / Interceptor
 ↓
HostTenantResolver
 ↓
TenantContext
 ↓
LocaleResolver
 ↓
Controller
 ↓
Service
 ↓
Repository
 ↓
ThemeResolver
 ↓
Thymeleaf
 ↓
HTML
```

### 静态网站请求

```text
Browser
 ↓
Nginx
 ↓
Tenant Domain
 ↓
/data/www/{tenant}/current
 ↓
HTML
```

正式生产访问优先走静态文件。

## 4. TenantContext

建议使用请求级上下文。

要求：

- 每次请求进入时设置
- 请求结束清理
- 不允许线程复用造成租户污染
- Service 层不能信任前端传来的 tenant_id
- tenant_id 必须来自当前认证 / 请求上下文

## 5. ThemeResolver

不要在 Controller 判断 tenantId。

接口建议：

```java
public interface ThemeResolver {
    String resolveTheme(Tenant tenant);
}
```

模板：

```text
themes/{themeId}/{page}.html
```

例如：

```text
themes/modern/index.html
themes/modern/about.html
```

## 6. 内容与模板

模板：

```text
HTML
CSS
JS
页面结构
```

数据库：

```text
企业名称
产品
新闻
页面内容
图片
SEO
```

禁止把企业内容硬编码进模板。

## 7. 多语言

Locale 解析统一处理。

建议：

```text
默认语言：/
其他语言：/{locale}/
```

例如：

```text
/
 /about

/zh-cn/
/zh-cn/about
```

Service 查询 i18n 数据时必须明确 language。

如果指定语言不存在：

1. 优先回退 Tenant 默认语言
2. 如果业务要求不可回退，则显示缺失状态

Phase 2 默认允许回退。

## 8. Controller

建议网站前台：

```text
SiteHomeController
SitePageController
ProductController
NewsController
SeoController
```

后台：

```text
AdminAuthController
AdminSiteController
AdminPageController
AdminProductController
AdminNewsController
AdminMediaController
AdminPublishController
```

## 9. 静态化

建议：

```text
StaticSiteGenerator
```

负责：

```text
generateHome()
generatePages()
generateProducts()
generateNews()
generateSitemap()
generateRobots()
```

渲染：

```text
Thymeleaf TemplateEngine
```

输出到：

```text
/data/www/{tenantId}/releases/{version}/
```

## 10. 发布

发布采用：

```text
prepare
 ↓
render
 ↓
validate
 ↓
write release
 ↓
atomic switch current
 ↓
record success
```

如果 render / validate / write 任一步失败：

```text
current 不改变
```

## 11. 缓存

Phase 2 Redis 非强制。

可以先使用：

- Spring Cache
- 本地缓存

优先缓存：

- Tenant
- TenantConfig
- Theme

不要过早缓存复杂 CMS 内容，避免发布后的缓存一致性问题。

## 12. 安全架构

所有管理接口：

```text
Authentication
 ↓
Authorization
 ↓
Tenant Scope
 ↓
Service
```

不能仅依赖前端传递 tenant_id。

富文本：

```text
Input
 ↓
HTML Sanitizer
 ↓
Database
```

文件：

```text
Upload
 ↓
MIME validation
 ↓
Extension validation
 ↓
Size validation
 ↓
Storage
```

## 13. Nginx

生产建议：

```text
domain
 ↓
Nginx
 ↓
/data/www/{tenant}/current
```

管理后台和预览：

```text
/admin/*
/preview/*
```

转发到 Spring Boot。

具体 Nginx 动态 tenant 映射方案可在部署文档中实现，但必须确保域名不能访问其他 Tenant 的目录。

## 14. 可扩展性

未来可以增加：

```text
Template Market
Online Builder
Tenant Billing
SSL Automation
CDN
Analytics
Lead Management
AI Content
```

但 Phase 2 不实现。

架构不能依赖这些未来功能。
