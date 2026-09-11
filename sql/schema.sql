-- sharp-site 数据库 Schema(PostgreSQL)
-- 审计字段采用 sharp-database 框架标准:create_by/create_time/update_by/update_time/is_deleted
-- (逻辑删除)。与 docs/DATABASE.md 原 created_at/updated_at 设计有偏离,详见 TASK 报告。
-- UNIQUE 约束改为部分唯一索引(WHERE is_deleted = false),支持逻辑删除后重新注册。
-- 执行方式见 sql/README.md(手工执行,应用不自动跑 DDL)。

-- TASK-0101: tenant(DATABASE.md §1,改用框架审计列)
DROP TABLE IF EXISTS tenant;
CREATE TABLE tenant (
    id BIGINT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(100) NOT NULL,
    theme_id VARCHAR(100) NOT NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    create_by BIGINT,
    create_time TIMESTAMP NOT NULL,
    update_by BIGINT,
    update_time TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_tenant_code ON tenant (code) WHERE is_deleted = false;

-- TASK-0102: tenant_domain(DATABASE.md §2)
DROP TABLE IF EXISTS tenant_domain;
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

-- TASK-0103: tenant_config(DATABASE.md §3)
-- company_name/company_name_short/address/copyright 改由 tenant_config_i18n 按语种维护。
DROP TABLE IF EXISTS tenant_config;
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

-- TASK-0103: 企业固定信息多语言(company_name/company_name_short/address/copyright)。
DROP TABLE IF EXISTS tenant_config_i18n;
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

-- 站点静态页面(about/contact 等)与首页区块(hero/cta 等)均改由前端模板维护,
-- 清单见 themes/{themeId}/meta/theme.json 的 pages,文案见 messages.json;
-- 不再有 site_page / site_page_i18n / home_section / home_section_i18n 表。
-- TASK-0401 / TASK-0501 表已移除。

-- TASK-0601: category(DATABASE.md §8)— PRODUCT / NEWS 共用,支持 parent_id 层级
CREATE TABLE IF NOT EXISTS category (
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
CREATE UNIQUE INDEX IF NOT EXISTS uk_category_slug ON category (tenant_id, type, slug) WHERE is_deleted = false;

-- TASK-0601: category_i18n(DATABASE.md §9)
CREATE TABLE IF NOT EXISTS category_i18n (
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
CREATE UNIQUE INDEX IF NOT EXISTS uk_category_language ON category_i18n (category_id, language) WHERE is_deleted = false;

-- TASK-0602: product(DATABASE.md §10)
CREATE TABLE IF NOT EXISTS product (
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
CREATE UNIQUE INDEX IF NOT EXISTS uk_product_slug ON product (tenant_id, slug) WHERE is_deleted = false;

-- TASK-0603: product_i18n(DATABASE.md §11)
CREATE TABLE IF NOT EXISTS product_i18n (
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
CREATE UNIQUE INDEX IF NOT EXISTS uk_product_language ON product_i18n (product_id, language) WHERE is_deleted = false;

-- TASK-0702: article(DATABASE.md §13)
CREATE TABLE IF NOT EXISTS article (
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
CREATE UNIQUE INDEX IF NOT EXISTS uk_article_slug ON article (tenant_id, slug) WHERE is_deleted = false;

-- TASK-0703: article_i18n(DATABASE.md §14)
CREATE TABLE IF NOT EXISTS article_i18n (
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
CREATE UNIQUE INDEX IF NOT EXISTS uk_article_language ON article_i18n (article_id, language) WHERE is_deleted = false;

-- TASK-0801: media(DATABASE.md §15)
CREATE TABLE IF NOT EXISTS media (
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
CREATE INDEX IF NOT EXISTS idx_media_tenant ON media (tenant_id);

-- TASK-0901: admin_user(DATABASE.md §17)— 管理员账号,租户内 username 唯一
CREATE TABLE IF NOT EXISTS admin_user (
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
CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_username ON admin_user (tenant_id, username) WHERE is_deleted = false;

-- TASK-1001: seo_config(DATABASE.md §16)— 每页面/内容的 SEO 元数据,按 (租户,page_type,page_id,语言) 唯一
CREATE TABLE IF NOT EXISTS seo_config (
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
CREATE INDEX IF NOT EXISTS idx_seo_tenant ON seo_config (tenant_id);
-- page_id 可空(首页/列表页);COALESCE 保证 NULL page_id 也按唯一键去重
CREATE UNIQUE INDEX IF NOT EXISTS uk_seo_page ON seo_config (tenant_id, page_type, COALESCE(page_id, 0), language) WHERE is_deleted = false;

-- TASK-1301: publish_record(DATABASE.md §18)— 发布记录,按 (租户,version) 唯一
CREATE TABLE IF NOT EXISTS publish_record (
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
CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_version ON publish_record (tenant_id, version) WHERE is_deleted = false;

-- sharp-database 对 jsonb 列 UPDATE 未做 ::jsonb 转换(INSERT 内联字面量已 OK),
-- 导致 UPDATE ... SET specification_json = ?(varchar 绑定)报类型不匹配。
-- 补一个 varchar→jsonb 赋值转换,使 setString 绑定可写入 jsonb 列。可重复执行。
DROP CAST IF EXISTS (varchar AS jsonb);
CREATE CAST (varchar AS jsonb) WITH INOUT AS ASSIGNMENT;
