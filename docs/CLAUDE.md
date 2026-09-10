# CLAUDE.md

# 项目开发指令

你是本项目的主要 Coding Agent。

项目目标：

> 构建一个基于 JDK 17 + Gradle + Spring Boot 3.5.7 + Thymeleaf 的多租户外贸独立站 SaaS / CMS 平台。

数据库、字典、文件上传分别复用：

```gradle
api 'com.rick.db:sharp-database:0.0.1-SNAPSHOT'
api 'com.rick.meta:sharp-meta:0.0.1-SNAPSHOT'
api 'com.rick.fileupload:sharp-fileupload:0.0.1-SNAPSHOT'
```

实体必须实现 BaseEntityInfoGetter，通过 ExtendTableDAOImpl 自动注入相关信息。

系统默认 Java 可能是 1.8，构建与运行前必须：

```bash
export JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"  # JBR 17
```

详细需求必须以：

```text
REQUIREMENTS.md
ARCHITECTURE.md
DATABASE.md
TASKS.md
```

为准。

---

# 1. 必读文件

开始任何开发任务前：

1. 阅读 `REQUIREMENTS.md`
2. 阅读 `ARCHITECTURE.md`
3. 阅读 `DATABASE.md`
4. 阅读 `TASKS.md`
5. 检查当前项目代码和已有实现

不要只根据用户一句话直接修改代码。

---

# 2. 核心架构不可破坏

必须始终保持：

```text
Tenant
  ↓
Domain
  ↓
Theme
  ↓
Content
  ↓
i18n
  ↓
SEO
  ↓
Preview
  ↓
Static Publish
```

核心原则：

```text
Tenant ≠ Theme
Theme ≠ Content
Content ≠ Language
```

---

# 3. 禁止的实现方式

禁止：

```java
if (tenantId == 100) {
    return "themes/modern/index";
}

if (tenantId == 200) {
    return "themes/industrial/index";
}
```

禁止将 Tenant ID 与 Theme ID 绑定。

禁止将企业内容硬编码到 HTML 模板。

禁止 Controller 直接访问数据库。

禁止 Service 信任前端传来的 tenant_id。

禁止后台用户通过修改 URL 参数读取其他 Tenant 数据。

禁止明文保存密码。

禁止保存未清洗的富文本 HTML。

禁止为了方便而删除 tenant_id。

---

# 4. Tenant 隔离

所有业务数据必须属于当前 Tenant。

Tenant 必须来自：

```text
Host
或
Authenticated Admin User
```

不能来自普通请求参数。

正确：

```text
currentTenant
 ↓
Service
 ↓
WHERE tenant_id = currentTenant.id
```

错误：

```text
request.tenantId
 ↓
Service
```

每次请求结束必须清理 TenantContext。

---

# 5. Theme

Theme 由开发人员维护：

```text
src/main/resources/templates/themes/
```

例如：

```text
themes/
├── modern/
└── industrial/
```

多个 Tenant 可以共享一个 Theme。

修改 Theme 不得修改数据库中的企业内容。

---

# 6. 数据与模板分离

模板负责：

```text
HTML
CSS
JS
Layout
Visual Design
```

数据库负责：

```text
Company
Products
News
Pages
Media
SEO
Translations
```

不要把数据库内容写死在模板。

---

# 7. 多语言

Phase 2：

```text
en-US
zh-CN
```

必须使用 i18n 数据模型。

例如：

```text
product
product_i18n

article
article_i18n

site_page
site_page_i18n
```

不要复制整个模板目录来实现语言。

默认语言：

```text
/
```

其他语言：

```text
/{locale}/
```

具体规则遵循项目统一 LocaleResolver。

---

# 8. 静态化

正式网站优先静态 HTML。

生成：

```text
/data/www/{tenantId}/releases/{version}/
```

正式版本：

```text
/data/www/{tenantId}/current
```

发布必须：

```text
Generate
 ↓
Validate
 ↓
Atomic Switch
```

新版本失败不得修改 current。

---

# 9. 安全

所有用户输入必须验证。

特别是：

```text
HTML
File
URL
Slug
ID
```

富文本必须经过 HTML Sanitizer。

文件上传必须验证：

```text
MIME
Extension
Size
```

所有数据库操作防止 SQL Injection。

所有后台接口必须做认证和租户授权。

---

# 10. 开发方式

不要一次性实现整个系统。

严格按照：

```text
TASKS.md
```

逐项完成。

每完成一个任务：

```text
修改
 ↓
编译
 ↓
测试
 ↓
检查
```

如果测试失败，先修复，不继续堆积新功能。

---

# 11. 修改已有代码

如果项目已有：

- Security
- Database / `sharp-database`
- Meta / `sharp-meta`
- File upload / `sharp-fileupload`
- OSS
- Exception
- Response
- User
- Thymeleaf
- Common Utils

优先复用。

不要为了新功能重复造轮子。尤其不要替换上述 Sharp 组件。

如果必须修改已有架构：

1. 先说明原因
2. 说明影响范围
3. 再实施

---

# 12. 依赖管理

构建工具：**Gradle**（不要改用 Maven）。

不要随意增加依赖。

新增依赖前检查：

```text
当前是否已经存在
Spring Boot 是否已经提供
项目是否已有类似功能
Sharp 内部组件是否已覆盖
```

如果存在可复用方案，优先复用。

必须使用的 Sharp 组件：

```gradle
api 'com.rick.db:sharp-database:0.0.1-SNAPSHOT'       // 数据库操作
api 'com.rick.meta:sharp-meta:0.0.1-SNAPSHOT'         // 字典相关操作
api 'com.rick.fileupload:sharp-fileupload:0.0.1-SNAPSHOT' // 文件上传
```

禁止自行实现与上述组件重复的数据库访问层、字典层、上传层。

---

# 13. Java

目标：

```text
JDK 17
```

系统默认 Java 可能是 1.8。运行 Gradle、测试、启动应用前必须显式指定 JDK 17：

```bash
export JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"  # JBR 17
```

不要使用 JDK 21/22/23/24/25 特有 API。

除非项目明确升级。

优先使用稳定、简单、可维护的 Java 17 写法。

---

# 14. Spring Boot

使用：

```text
Spring Boot 3.5.7
Spring Framework 6.x
Gradle
```

遵循 Spring Boot 推荐方式。

不要使用已废弃的旧 Spring API。

---

# 15. Thymeleaf

模板应该：

```text
themes/{themeId}/{page}.html
```

公共结构：

```text
fragments/
```

例如：

```text
header
footer
seo
language
```

Controller 不应该硬编码大量模板路径。

---

# 16. Controller

Controller 只负责：

```text
Request
 ↓
Validation
 ↓
Service
 ↓
Response / View
```

不要在 Controller 写复杂业务逻辑。

---

# 17. Service

Service 负责：

- 业务规则
- 租户隔离
- 发布流程
- 数据组合
- i18n fallback
- SEO 数据组合

---

# 18. DTO

后台 API：

```text
Request DTO
Response DTO
```

不要直接把数据库 Entity 作为 API Contract。

---

# 19. 异常

不要吞异常：

```java
catch (Exception e) {
}
```

必须：

- 记录必要日志
- 返回合理错误
- 保留原始异常 cause

---

# 20. 日志

禁止输出：

```text
password
password_hash
session
access_token
secret
```

发布失败必须记录错误信息。

---

# 21. 测试

至少为关键逻辑编写测试：

```text
TenantResolver
TenantContext
ThemeResolver
LocaleResolver
Tenant Isolation
Product
Article
Publish
Static Generator
```

尤其要测试：

```text
Tenant 100 不能读取 Tenant 200
```

---

# 22. 不要过度设计

Phase 2 不实现：

```text
拖拽建站
模板市场
RBAC
计费
支付
自动 SSL
CDN
AI
```

如果代码结构为未来功能预留扩展点，可以。

但不要提前实现。

---

# 23. 用户体验

后台应该简单直接。

目标用户是企业网站管理员，不是程序员。

常见操作：

```text
新增产品
编辑产品
上传图片
修改公司信息
编辑页面
发布网站
```

应该尽可能简单。

---

# 24. 完成标准

开发完成必须满足：

```text
多租户正常
域名识别正常
Theme 正常
多语言正常
CMS 正常
图片上传正常
SEO 正常
Preview 正常
Static Publish 正常
Rollback 正常
Nginx 静态访问正常
租户隔离正常
```

最终必须能够从：

```text
创建 Tenant
 ↓
绑定 Domain
 ↓
选择 Theme
 ↓
配置企业信息
 ↓
维护产品
 ↓
维护新闻
 ↓
维护页面
 ↓
配置 SEO
 ↓
预览
 ↓
发布
 ↓
Nginx 提供静态网站
```

完整跑通。

---

# 25. Agent 输出要求

每完成一个 TASK，输出：

```text
## TASK-XXXX

### 完成内容
- ...

### 修改文件
- ...

### 数据库变化
- ...

### 测试
- ...

### 验证结果
- ...

### 遗留问题
- ...
```

不要输出大量与任务无关的解释。

如果发现需求、架构或数据库设计存在冲突：

```text
停止相关实现
说明冲突
提出最小修改方案
等待确认
```

不要擅自改变核心架构。
