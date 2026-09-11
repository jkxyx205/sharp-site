# DATABASE.md

# 外贸独立站 SaaS 数据库设计

数据库：**PostgreSQL**。

以下为 Phase 2 推荐逻辑模型。字段类型可根据项目数据库规范微调，但关系和租户隔离原则不得改变。DDL 使用 PostgreSQL 语法。

---

## 0. 通用约定

所有业务表统一采用 sharp-database 框架标准审计列，由 `ExtendTableDAOImpl` 自动填充，业务代码不手工维护时间戳与操作人：

```text
create_by    BIGINT           -- 创建人(来自登录上下文 AdminUserContext,空回退 1L)
create_time  TIMESTAMP NOT NULL  -- 创建时间(默认当前时间)
update_by    BIGINT           -- 更新人
update_time  TIMESTAMP NOT NULL  -- 更新时间(默认当前时间,更新时自动刷新)
is_deleted   BOOLEAN NOT NULL DEFAULT FALSE  -- 逻辑删除标记
```

- **逻辑删除为默认策略**：`deleteById` 转为 `UPDATE ... SET is_deleted = true`；框架对单表 select 自动追加 `is_deleted = false` 过滤。
- **唯一约束使用部分唯一索引**：`CREATE UNIQUE INDEX uk_xxx ON tbl (cols) WHERE is_deleted = false;`，以便逻辑删除后可重新注册同名 slug / domain / code。
- **租户业务表必须含 `tenant_id BIGINT NOT NULL`**（不可变，由 `TenantContext` 注入，禁止信任前端参数）。
- **主键 `id` 为雪花算法 BIGINT**（继承 `EntityId` / `EntityIdCode`）。
- 实体继承 `BaseEntity` / `BaseCodeEntity`（平台级）或 `TenantBaseEntity`（租户级），自动具备上述审计列与 `tenant_id`。

---

## 1. tenant

```sql
CREATE TABLE tenant (
    id BIGINT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(100) NOT NULL,
    theme_id VARCHAR(100) NOT NULL,
    default_language VARCHAR(20) NOT NULL DEFAULT 'en-US',
    languages VARCHAR(100) NOT NULL DEFAULT 'en-US',
    status SMALLINT NOT NULL DEFAULT 1,
    create_by BIGINT,
    create_time TIMESTAMP NOT NULL,
    update_by BIGINT,
    update_time TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_tenant_code ON tenant (code) WHERE is_deleted = false;
```

租户是平台级数据，本身无 tenant_id。

## 2. tenant_domain

```sql
CREATE TABLE tenant_domain (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    domain VARCHAR(255) NOT NULL,
    is_primary SMALLINT NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 1,
    create_by BIGINT,
    create_time TIMESTAMP NOT NULL,
    update_by BIGINT,
    update_time TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_domain ON tenant_domain (domain) WHERE is_deleted = false;
CREATE INDEX idx_domain_tenant ON tenant_domain (tenant_id);
```

要求：

- domain 全局唯一
- 一个 Tenant 可以多个 domain
- 一个 Tenant 最多一个 primary domain

## 3. tenant_config / tenant_config_i18n

```sql
CREATE TABLE tenant_config (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    logo VARCHAR(1000),
    phone VARCHAR(100),
    mobile VARCHAR(100),
    email VARCHAR(200),
    whatsapp VARCHAR(200),
    facebook VARCHAR(500),
    linkedin VARCHAR(500),
    youtube VARCHAR(500),
    icp VARCHAR(200),
    create_by BIGINT,
    create_time TIMESTAMP NOT NULL,
    update_by BIGINT,
    update_time TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_tenant_config ON tenant_config (tenant_id) WHERE is_deleted = false;
```

联系方式 / 备案(logo/phone/mobile/email/whatsapp/facebook/linkedin/youtube/icp)跨语种共享,
留在 base 行(每租户一行)。公司名称 / 公司简称 / 地址 / 版权信息随语种变化,改由
`tenant_config_i18n` 按语种维护:

```sql
CREATE TABLE tenant_config_i18n (
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
);

CREATE UNIQUE INDEX uk_tenant_config_i18n ON tenant_config_i18n (tenant_config_id, language) WHERE is_deleted = false;
```

i18n 在 (tenant_config_id, language) 范围内唯一;展示按当前语种取,缺失回退租户默认语种
(经 `I18nService.resolve`,与 product / article 一致)。

## 4. 页面与首页区块(前端模板维护)

页面(about/contact 等)与首页区块(hero/company/cta 等)均由前端主题模板维护,
后端只渲染,不再有 `site_page` / `site_page_i18n` / `home_section` / `home_section_i18n` 表。

- 页面路径→模板清单见 `themes/{themeId}/meta/theme.json` 的 `pages`(首页/产品列表/新闻列表/关于/联系等)。
- 正文文案由模板 + `themes/{themeId}/meta/messages.json` 文案键提供(与现有 contact 一致)。
- 后台 SEO 编辑的页面清单直接取自 `pages`;单页/列表页 SEO 的 `page_type` = 页面路径(`/`、`/products`、`/news`、`/about`、`/contact`),`page_id` 恒为空。
- 产品/新闻列表仍由 `product` / `article` 表注入,详情页 SEO 用 `page_type=product/article` + `page_id`。

## 8. category

```sql
CREATE TABLE category (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    parent_id BIGINT,
    slug VARCHAR(200) NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 1,
    create_by BIGINT,
    create_time TIMESTAMP NOT NULL,
    update_by BIGINT,
    update_time TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_category_slug ON category (tenant_id, type, slug) WHERE is_deleted = false;
```

type：

```text
PRODUCT
NEWS
```

## 9. category_i18n

```sql
CREATE TABLE category_i18n (
    id BIGINT PRIMARY KEY,
    category_id BIGINT NOT NULL,
    language VARCHAR(20) NOT NULL,
    name VARCHAR(500) NOT NULL,
    description TEXT,
    create_by BIGINT,
    create_time TIMESTAMP NOT NULL,
    update_by BIGINT,
    update_time TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_category_language ON category_i18n (category_id, language) WHERE is_deleted = false;
```

## 10. product

```sql
CREATE TABLE product (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    category_id BIGINT,
    slug VARCHAR(200) NOT NULL,
    cover VARCHAR(1000),
    status SMALLINT NOT NULL DEFAULT 1,
    sort INT NOT NULL DEFAULT 0,
    create_by BIGINT,
    create_time TIMESTAMP NOT NULL,
    update_by BIGINT,
    update_time TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_product_slug ON product (tenant_id, slug) WHERE is_deleted = false;
```

## 11. product_i18n

```sql
CREATE TABLE product_i18n (
    id BIGINT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    language VARCHAR(20) NOT NULL,
    name VARCHAR(500) NOT NULL,
    subtitle VARCHAR(1000),
    description TEXT,
    content TEXT,
    specification_json JSONB,
    seo_title VARCHAR(500),
    seo_description VARCHAR(1000),
    create_by BIGINT,
    create_time TIMESTAMP NOT NULL,
    update_by BIGINT,
    update_time TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_product_language ON product_i18n (product_id, language) WHERE is_deleted = false;
```

## 12. product_category

如果未来一个产品允许多个分类，使用：

```sql
CREATE TABLE product_category (
    product_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (product_id, category_id)
);
```

Phase 2 如果产品只有一个分类，可以先使用 product.category_id。

## 13. article

```sql
CREATE TABLE article (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    category_id BIGINT,
    slug VARCHAR(200) NOT NULL,
    cover VARCHAR(1000),
    author VARCHAR(200),
    publish_time TIMESTAMP,
    status SMALLINT NOT NULL DEFAULT 1,
    sort INT NOT NULL DEFAULT 0,
    create_by BIGINT,
    create_time TIMESTAMP NOT NULL,
    update_by BIGINT,
    update_time TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_article_slug ON article (tenant_id, slug) WHERE is_deleted = false;
```

## 14. article_i18n

```sql
CREATE TABLE article_i18n (
    id BIGINT PRIMARY KEY,
    article_id BIGINT NOT NULL,
    language VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL,
    summary TEXT,
    content TEXT,
    seo_title VARCHAR(500),
    seo_description VARCHAR(1000),
    create_by BIGINT,
    create_time TIMESTAMP NOT NULL,
    update_by BIGINT,
    update_time TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_article_language ON article_i18n (article_id, language) WHERE is_deleted = false;
```

## 15. media

```sql
CREATE TABLE media (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    object_key VARCHAR(1000),
    url VARCHAR(2000) NOT NULL,
    filename VARCHAR(500),
    mime_type VARCHAR(200),
    size BIGINT,
    alt_text VARCHAR(1000),
    title VARCHAR(500),
    create_by BIGINT,
    create_time TIMESTAMP NOT NULL,
    update_by BIGINT,
    update_time TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_media_tenant ON media (tenant_id);
```

## 16. seo_config

```sql
CREATE TABLE seo_config (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    page_type VARCHAR(50) NOT NULL,
    page_id BIGINT,
    language VARCHAR(20) NOT NULL,
    title VARCHAR(500),
    description VARCHAR(2000),
    keywords VARCHAR(2000),
    canonical VARCHAR(2000),
    robots VARCHAR(100),
    og_title VARCHAR(500),
    og_description VARCHAR(2000),
    og_image VARCHAR(2000),
    create_by BIGINT,
    create_time TIMESTAMP NOT NULL,
    update_by BIGINT,
    update_time TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_seo_tenant ON seo_config (tenant_id);
```

## 17. admin_user

```sql
CREATE TABLE admin_user (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(500) NOT NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    create_by BIGINT,
    create_time TIMESTAMP NOT NULL,
    update_by BIGINT,
    update_time TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_tenant_username ON admin_user (tenant_id, username) WHERE is_deleted = false;
```

Phase 2 不实现复杂 RBAC。

## 18. publish_record

```sql
CREATE TABLE publish_record (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    version VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    error_message TEXT,
    create_by BIGINT,
    create_time TIMESTAMP NOT NULL,
    update_by BIGINT,
    update_time TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_tenant_version ON publish_record (tenant_id, version) WHERE is_deleted = false;
```

## 19. 租户隔离原则

所有业务查询必须带租户范围。

正确：

```sql
SELECT *
FROM product
WHERE tenant_id = ?
  AND id = ?
  AND is_deleted = false;
```

错误：

```sql
SELECT *
FROM product
WHERE id = ?;
```

Service 层不得信任请求中的 tenant_id。

Tenant 必须来自当前请求上下文 / 当前管理员上下文。

## 20. 删除策略

默认使用逻辑删除（`is_deleted = true`），框架 `deleteById` 自动执行逻辑删除，单表 select 自动过滤 `is_deleted = false`。

特别是：

- product
- article
- category
- media
- page

避免误删除导致已发布版本无法重新生成。

唯一约束以部分唯一索引（`WHERE is_deleted = false`）实现，逻辑删除后可重新注册同名 slug / domain / code。

## 21. 数据库原则

- 数据库使用 PostgreSQL
- 数据访问使用 `sharp-database`（`com.rick.db:sharp-database:0.0.1-SNAPSHOT`），不要另起 ORM 实现
- 字典相关操作使用 `sharp-meta`（`com.rick.meta:sharp-meta:0.0.1-SNAPSHOT`）
- 所有业务表包含 `create_by / create_time / update_by / update_time / is_deleted`（框架自动填充，逻辑删除）
- 所有租户业务表必须可追溯到 tenant
- slug 在租户范围内唯一
- i18n 在实体 + language 范围内唯一
- domain 全局唯一
- 不为了省表而把所有内容塞入一个 JSONB 字段
