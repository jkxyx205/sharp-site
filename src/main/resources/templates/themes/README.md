# 主题开发指南（Theme Authoring Guide）

> 面向大模型 / 开发者：照此规则可基于 `modern` 主题生成新主题。数据与模板分离——**后端只产出上下文变量，主题负责全部 HTML/CSS 与文案**。

## 1. 核心原则

- **数据与模板分离**：页面设计、布局、样式、文案键全部由主题模板维护；后端只渲染、不产出 HTML 内容（产品/新闻正文除外，来自 DB 富文本）。
- **三路渲染一致**：同一套主题模板被三条路径复用——前台 Controller（`/`、`/products`…）、预览（`/preview/…`）、静态发布（`StaticSiteGenerator`）。三者注入的上下文变量名**必须一致**，主题模板不要依赖某一路径才有的变量（分页变量除外，见 §7）。
- **语种唯一来源是 `theme.json`**：`locales` / `defaultLocale` 决定支持哪些语言；租户选用某主题即等于选定语种集合。
- **URL 约定**：默认语种无前缀（`/products`），其它语种带小写前缀（`/zh-cn/products`）。所有站内链接都要拼接 `localePrefix`（见 §5）。
- **主题可移植（不含主题名）**：主题目录内部不自持任何主题名——`theme.json` 的 `template` 用逻辑名（`index`/`products`…），片段引用用 `~{themes/__${themeId}__/fragments/...}` 预处理表达式。`themeId` 是唯一的"身份变量"，由租户配置决定并注入上下文（§6）；**复制主题目录后无需改动任何内部引用**，只把租户 `themeId` 指向新目录即可生效。

## 2. 主题目录结构

主题位于 `src/main/resources/templates/themes/{themeId}/`：

```
themes/{themeId}/
├── meta/
│   ├── theme.json        # 主题清单：语种 + 页面目录 + 每页 SEO 默认（§3）
│   └── messages.json     # 文案键 → {语种: 文案}（§4）
├── fragments/
│   ├── head.html         # <head>：charset/viewport + SEO meta + 内联 <style>
│   ├── seo.html          # SEO meta 标签片段（被 head 引用）
│   ├── header.html       # 顶部导航
│   ├── footer.html       # 页脚
│   └── language.html     # 语言切换
├── index.html            # 首页（path "/"）
├── products.html         # 产品列表（path "/products"）
├── product-detail.html   # 产品详情（/products/{slug}）
├── news.html             # 新闻列表（path "/news"）
├── news-detail.html      # 新闻详情（/news/{slug}）
└── about.html / contact.html / info.html / …  # 主题声明的任意静态页
```

模板路径在 `theme.json` 的 `pages[].template` 中声明（值为**逻辑名**，如 `index`/`products`/`about`，**不带 `themes/{themeId}/` 前缀、不带 `.html`**）；运行时由 `ThemeManifestResolver#template(tenant, page)` 拼成 `themes/{tenant.themeId}/{page}`。

## 3. `meta/theme.json`

```jsonc
{
  // 语种（规范代码），第一个不一定是默认；defaultLocale 指明默认
  "locales": ["en-US", "zh-CN", "ar-SA", "fr-FR", "ru-RU", "es-ES"],
  "defaultLocale": "en-US",
  "pages": [
    {
      "path": "/",                // 站点页路径；同时是 SEO 的 page_type
      "template": "index",        // 渲染模板逻辑名；运行时拼成 themes/{themeId}/index（无 .html）
      "label": "首页",             // 展示名；也是 SEO 兜底标题来源
      "seo_config": {                    // 可选：每页每语种 SEO 默认值（后台可覆盖）
        "en-US": {
          "title": "Welcome to Our Site",
          "description": "…",
          "keywords": "…",
          "canonical": "",               // 留空 → 回退到请求 URL
          "robots": "index, follow",
          "ogTitle": "Welcome to Our Site",
          "ogDescription": "…",
          "ogImage": ""                 // 留空 → 不输出 og:image
        },
        "zh-CN": { /* … */ }
      }
    },
    { "path": "/products", "template": "products", "label": "产品列表" },
    { "path": "/news",     "template": "news",     "label": "新闻列表" },
    { "path": "/about",    "template": "about",    "label": "关于我们" },
    { "path": "/contact",  "template": "contact",  "label": "联系我们" },
    { "path": "/info",     "template": "info",     "label": "信息" }
  ]
}
```

**规则**

- `pages` 列出所有"站点页"。`/`（首页）、`/products`、`/news` 是动态页（列表/首页由专用 Controller 处理），其余（`/about`、`/contact`、`/info`…）是静态页，由 `SitePageController` 按 `path` 路由到 `template`。
- 路径变量正则限定单段字母数字中划线，所以站点页 `path` 必须是单段（`/about` 可以，`/a/b` 不行）。
- `seo_config` 任一字段留空（`""` 或缺省）即跳过该层，继续回退。优先级链：**后台 DB `seo_config` > `theme.json` 的 `seo_config` > 内容回退**（标题回退页面 label / 产品名等，canonical 回退请求 URL，robots 兜底 `index, follow`）。
- 未声明 `seo_config` 的语种会回退到 `defaultLocale`。无需每页每语种都填，按需填常见语种即可。
- 产品/新闻详情页（`/products/{slug}`、`/news/{slug}`）**不**在 `pages` 中声明，其 SEO 由内容（产品名/封面）回退，后台可按 `page_type=product|article` + `page_id` 单独覆盖。

## 4. `meta/messages.json`

文案键 → `{ "语种": "文案" }` 的扁平 Map。模板用 `#{key}` 取当前语种文案。

```jsonc
{
  "nav.home":     { "zh-CN": "首页", "en-US": "Home", "ar-SA": "الرئيسية", "fr-FR": "Accueil", "ru-RU": "Главная", "es-ES": "Inicio" },
  "home.hero.title": { "zh-CN": "欢迎来到我们的网站", "en-US": "Welcome to Our Site" },
  "about.body":    { "zh-CN": "<p>…</p>", "en-US": "<p>…</p>" },
  "common.tel":    { "zh-CN": "电话", "en-US": "Tel" }
}
```

**规则**

- 每个键至少给 `defaultLocale` 文案（其它语种缺失会回退到默认语种）。
- 值可含 HTML（如 `about.body`），模板用 `th:utext` 输出；普通文案用 `th:text`。
- 新增语种只需在 `theme.json.locales` 加入并在 `messages.json` 补对应语种文案，无需改 Java。

## 5. URL 与 i18n 约定

| 变量 | 含义 | 示例 |
|---|---|---|
| `currentLanguage` | 当前请求语种代码 | `en-US` / `zh-CN` |
| `localePrefix` | 链接前缀；默认语种为 `""`，其余为 `/{locale小写}` | `""` / `/zh-cn` |
| `languages` | 语言切换项列表（见 §8），单语言主题为空 |

**所有站内链接都必须拼 `localePrefix`**：

```html
<a th:href="@{${localePrefix + '/products'}}" th:text="#{nav.products}">Products</a>
<a th:href="@{${localePrefix + '/products/' + p.slug}}">…</a>
```

根目录链接用 `localePrefix + '/'`。`<html>` 上设置方向与语言：

```html
<html th:lang="${currentLanguage}" th:dir="${currentLanguage == 'ar-SA' ? 'rtl' : 'ltr'}">
```

## 6. 全局上下文变量（所有页面都有）

由 `SiteCommonAttributes`（`@ControllerAdvice`）注入，所有前台页面可用：

| 变量 | 类型 | 说明 |
|---|---|---|
| `siteName` | String | 站点名（`config.companyName`，缺失回退租户名） |
| `config` | `TenantConfigView`? | 企业信息，**可能为 null**，见 §8 |
| `currentLanguage` | String | 当前语种 |
| `themeId` | String | 当前租户主题名（如 `xhope-cn`）；供片段引用 `${themeId}` 用（见 §10），由 `ThemeResolver` 解析 |
| `localePrefix` | String | 链接前缀 |
| `languages` | `List<LanguageOption>` | 语言切换项；单语言为空列表 |

## 7. 各页面上下文变量

> 类型详见 §8。除"全局变量"外，下表是各页**额外**注入的变量。预览（`/preview/…`）与静态发布注入同名变量；**分页变量仅在静态发布的列表页存在**，模板用 `th:if` 守卫。

### 首页 `/`（`SiteHomeController` → `index.html`）

| 变量 | 类型 | 说明 |
|---|---|---|
| `products` | `List<ProductView>` | 精选产品子集（前 8） |
| `news` | `List<ArticleView>` | 最新文章子集（前 5） |
| `allProducts` | `List<ProductView>` | 全部上架产品（供按分类过滤） |
| `allNews` | `List<ArticleView>` | 全部已发布文章 |
| `categories` | `List<Category>` | 全部分类（**原始实体，无 name 字段**，仅 `slug` 等可用） |

### 产品列表 `/products`（`ProductController.list` → `products.html`）

| 变量 | 类型 | 说明 |
|---|---|---|
| `allProducts` | `List<ProductView>` | 全部上架产品（三路恒为全量） |
| `products` | `List<ProductView>` | live=全量；预览/静态=当前页切片 |
| `categories` | `List<CategoryView>` | 产品分类（**含 `slug`+`name`**） |
| `page` `totalPages` `prevLink` `nextLink` | 分页 | 预览/静态有；**live 无**（模板用 `th:if` 守卫） |

### 产品详情 `/products/{slug}`（`ProductController.detail` → `product-detail.html`）

| 变量 | 类型 | 说明 |
|---|---|---|
| `product` | `ProductView` | 单个产品（含 i18n 字段） |

### 新闻列表 `/news`（`NewsController.list` → `news.html`）

| 变量 | 类型 | 说明 |
|---|---|---|
| `allNews` | `List<ArticleView>` | 全部已发布文章（三路恒为全量） |
| `news` | `List<ArticleView>` | live=全量；预览/静态=当前页切片 |
| `categories` | `List<CategoryView>` | 新闻分类（含 `slug`+`name`） |
| `page` `totalPages` `prevLink` `nextLink` | 分页 | 预览/静态有；**live 无**（模板用 `th:if` 守卫） |

### 新闻详情 `/news/{slug}`（`NewsController.detail` → `news-detail.html`）

| 变量 | 类型 | 说明 |
|---|---|---|
| `article` | `ArticleView` | 单篇文章 |

### 静态页 `/about`、`/contact`、`/info`…（`SitePageController.page` → `page.template()`）

| 变量 | 类型 | 说明 |
|---|---|---|
| `page` | `ThemePage` | 当前页（`path`/`template`/`label`），见 §8 |
| `products` | `List<ProductView>` | 全部上架产品 |
| `news` | `List<ArticleView>` | 全部已发布文章 |
| `categories` | `List<Category>` | 全部分类（原始实体，无 name） |

> `/contact` 是唯一带表单提交的静态页：表单 AJAX `POST /contact`（多语言无关）由 `SiteContactController` 返回 JSON，契约见 §12。

## 8. 变量类型（字段速查）

```java
record ProductView(String slug, String cover, String name, String subtitle,
                   String description, String content, String specificationJson,
                   String seoTitle, String seoDescription, String categorySlug, String categoryName)

record ArticleView(String slug, String cover, String author, LocalDateTime publishTime,
                   String title, String summary, String content,
                   String seoTitle, String seoDescription, String categorySlug, String categoryName)

record TenantConfigView(String logo, String companyName, String companyNameShort,
                        String address, String copyright,
                        String phone, String mobile, String email, String whatsapp,
                        String facebook, String linkedin, String youtube, String icp)

record LanguageOption(String code, String label, String path)   // 语言切换项

record CategoryView(String slug, String name)                    // 列表页的分类项

// Category 实体（首页/静态页注入的原始分类）：
class Category { String type; Long parentId; String slug; Integer sort; Short status; } // 注意：无 name

record ThemePage(String path, String template, String label)     // 当前页信息（静态页可用）；template 为逻辑名，运行时拼 themes/{themeId}/{template}
```

**要点**

- `ProductView`/`ArticleView` 已合并命中语种 i18n；缺失语种回退默认语种，再缺失 `name/title` 回退 `slug`。
- `content` 是富文本 HTML，用 `th:utext`；`cover` 是图片 URL。
- `categorySlug` / `categoryName` 用于在列表/首页按分类过滤出"图库/分组板块"。
- `config` 可能为 `null`，访问任何字段前先判空（`th:if="${config != null and config.email != null}"`）。
- `publishTime` 是 `LocalDateTime`，用 `#temporals.format(a.publishTime, 'yyyy-MM-dd')` 格式化。

## 9. SEO 上下文变量（所有页面都有）

由 `SeoConfigService.resolveView(...).applyTo(model)` 注入（详情页 `product-detail` 会用 `${product.name}` 覆盖 `pageTitle` 传入 head）：

| 变量 | 说明 |
|---|---|
| `pageTitle` | `<title>` 文案（片段会再拼 ` | siteName`） |
| `pageDescription` | meta description |
| `pageKeywords` | meta keywords |
| `canonical` | canonical 链接 |
| `robots` | robots 指令（默认 `index, follow`） |
| `ogTitle` `ogDescription` `ogImage` | Open Graph；`ogImage` 为空时不输出标签 |

## 10. 片段（fragments）约定

主题须提供这些片段（签名固定，新主题应保持一致以便复用）：

| 片段 | 签名 | 作用 |
|---|---|---|
| `head` | `head(pageTitle, pageDescription, pageKeywords, canonical, robots, siteName)` | `<head>`：charset/viewport + 内联 `<style>` + 调用 `seo` 片段 |
| `seo` | `seo(title, description, keywords, canonical, robots, siteName)` | 输出 `<title>`/meta/OG/canonical |
| `header` | `header(siteName, active)` | 顶部导航；`active` 标记当前页（如 `'products'`） |
| `footer` | `footer(siteName, config)` | 页脚企业信息 |
| `language` | `language(current, languages)` | 语言切换；`languages` 为空时整体不渲染 |

调用示例：

```html
<head th:replace="~{themes/__${themeId}__/fragments/head :: head(pageTitle=${pageTitle},
     pageDescription=${pageDescription}, pageKeywords=${pageKeywords},
     canonical=${canonical}, robots=${robots}, siteName=${siteName})}"></head>
<header th:replace="~{themes/__${themeId}__/fragments/header :: header(siteName=${siteName}, active='about')}"></header>
<footer th:replace="~{themes/__${themeId}__/fragments/footer :: footer(siteName=${siteName}, config=${config})}"></footer>
```

> 片段路径用 `themes/__${themeId}__/fragments/` 形式：`__${themeId}__` 是 Thymeleaf **预处理表达式**，解析前先把上下文变量 `themeId`（当前租户主题名，由 `ThemeResolver` 注入）求值文本替换进表达式，故片段路径与页面模板同目录、且不含硬编码主题名——**复制主题目录后片段引用零改动**。样式**内联**进 `head.html` 的 `<style>`，使静态发布产物自带样式、不依赖外部 CSS 路径。

## 11. 常用 Thymeleaf 技法（主题里频繁使用）

```html
<!-- 按分类过滤出某分类的产品图库（selection 表达式 .?[...]） -->
<th:block th:if="${products != null}" th:with="gallery=${products.?[categorySlug == 'test']}">
  <section th:if="${!gallery.isEmpty()}">
    <h2 th:text="${gallery[0].categoryName}">Gallery</h2>
    <a class="product-card" th:each="p : ${gallery}" th:href="@{${localePrefix + '/products/' + p.slug}}">
      <img th:if="${p.cover != null}" th:src="${p.cover}" th:alt="${p.name}" />
      <h3 th:text="${p.name}">Product</h3>
    </a>
  </section>
</th:block>

<!-- 投影取所有 categorySlug（.![...]），再用 #lists.contains 判断分类是否有内容 -->
<th:block th:if="${categories != null and allProducts != null}"
          th:with="slugs=${allProducts.![categorySlug]}">
  <th:block th:each="cat : ${categories}">
    <section th:if="${#lists.contains(slugs, cat.slug)}">
      <h2 th:text="${cat.name}">Category</h2>
      <a th:each="p : ${allProducts}" th:if="${p.categorySlug == cat.slug}" th:href="…">…</a>
    </section>
  </th:block>
</th:block>

<!-- 日期格式化 -->
<span th:if="${a.publishTime != null}" th:text="${#temporals.format(a.publishTime, 'yyyy-MM-dd')}"></span>

<!-- 富文本正文 -->
<div class="rich-text" th:utext="${product.content} ?: ''">…</div>

<!-- 文案键（带变量插值用 |...| 语法） -->
<p th:text="|#{common.tel}: ${config.phone}|">Tel</p>

<!-- 分页（仅静态有；务必 th:if 守卫 totalPages） -->
<nav class="pagination" th:if="${totalPages != null and totalPages > 1}">
  <a th:if="${prevLink != null}" th:href="${prevLink}" th:text="#{page.prev}">‹ Previous</a>
  <span th:text="${page + ' / ' + totalPages}">1 / 1</span>
  <a th:if="${nextLink != null}" th:href="${nextLink}" th:text="#{page.next}">Next ›</a>
</nav>
```

## 12. 联系表单（`contact.html` 专属）

联系页是唯一带表单提交的静态页。表单经 `SiteContactController` 以 **AJAX + JSON** 提交，**不走 PRG/flash**——静态发布站的 `GET /contact` 由 Nginx 静态服务，Spring 读不到 flash，只有客户端 JS 能给出"发送成功"反馈。

### 后端契约（`SiteContactController.submit`）

- 路由：`POST /contact` 与 `POST /{locale:[a-z]{2}-[a-z]{2}}/contact`，均返回 JSON。
- 请求体（`ContactForm` DTO，`@Valid` 校验）：`name`（必填）、`email`（必填，合法邮箱）、`message`（必填）、`topic`（可选）。
- 响应：`Content-Type: application/json`，`{"success": true}` 或 `{"success": false}`（校验失败 400、发信失败 200，体均 `success:false`）。**不返回视图、不重定向、不发 flash**。
- 发信：经 sharp-mail `MailHandler` 发往当前租户 `tenant_config.email`；发件人取 `spring.mail.username`（ISP 要求 from=认证账号）。
- CSRF：`POST /contact` 在 `SecurityConfig` 中用 `RegexRequestMatcher` 豁免（静态站无 session，无法注入 token；公开表单靠服务端校验，后续加限频/Honeypot，见 `docs/TODO.md §1/§10`）。

### 模板契约（`contact.html` 必须遵守）

```html
<!-- 1) 表单：action 固定 "/contact"（多语言无关——即页 /zh-cn/contact 也 POST /contact）。
        禁用 th:action（见下“要点”），用普通 action="/contact"。 -->
<form class="contact-form" method="post" action="/contact">
  <input type="text" name="name" required />
  <input type="email" name="email" required />
  <textarea name="message" rows="5" required></textarea>
  <!-- 可选：<select name="topic">…</select> -->

  <!-- 2) 成功/失败横幅：常驻 hidden，文案走 i18n 键 contact.sent_success / contact.sent_failed -->
  <div id="contactSuccess" hidden th:text="#{contact.sent_success}">发送成功</div>
  <div id="contactError" hidden th:text="#{contact.sent_failed}">发送失败</div>
  <button type="submit" th:text="#{contact.send}">Send</button>
</form>

<!-- 3) 提交脚本（页脚后、</body> 前）：拦截 submit → fetch POST → 据 success 显示横幅；
        成功清空表单、禁用按钮防重复、异常降级显示失败横幅 -->
<script>
(function () {
    var form = document.querySelector('.contact-form');
    if (!form) return;
    var ok = document.getElementById('contactSuccess');
    var no = document.getElementById('contactError');
    function show(el) { if (el) el.hidden = false; }
    function hide(el) { if (el) el.hidden = true; }
    form.addEventListener('submit', function (e) {
        e.preventDefault();
        hide(ok); hide(no);
        var btn = form.querySelector('button[type=submit]');
        if (btn) btn.disabled = true;
        fetch(form.action, { method: 'POST', body: new FormData(form), headers: { 'Accept': 'application/json' } })
            .then(function (r) { return r.json(); })
            .then(function (d) { if (d && d.success) { show(ok); form.reset(); } else { show(no); } })
            .catch(function () { show(no); })
            .finally(function () { if (btn) btn.disabled = false; });
    });
})();
</script>
```

**要点**

- `action="/contact"` 硬编码、**多语言无关**：访客在 `/zh-cn/contact` 页面也 POST `/contact`；Controller 同时映射 `/contact` 与 `/{locale}/contact`，响应是 JSON，locale 在 POST 上丢失无影响（横幅文案已随页面 locale 渲染进 DOM）。
- **禁用 `th:action`**：它触发 Spring Security 的 CSRF 隐藏域注入，需 `request.getSession()`；联系页较大，渲染到 `<form>` 时响应已 commit（缓冲区已 flush），`getSession()` 抛 `Cannot create a session after the response has been committed`，页面在表单处截断、页脚与脚本全丢。改用普通 `action="/contact"`（应用 context-path 为 `/`，与静态站路径一致）即可规避。
- 三路渲染一致：live、预览、静态发布产物中此表单与脚本同源（`StaticSiteGenerator` 把 `contact.html` 原样写出，脚本随之发布）。
- 文案键：新增 `contact.sent_success` / `contact.sent_failed`，复用既有 `contact.*` 系列。单语言主题（如 `xhope-cn`）可用内联文案（`th:text="'发送成功…'"`）代替 `#{}`。

### 静态发布站部署提醒

静态站须由 Nginx 把 `POST /contact`（及 `POST /{locale}/contact`）代理到后端 Spring Boot；`GET /contact` 仍静态服务（`location /` + `root $site_root`）。否则 POST 命中 Nginx 的目录跳转（301 `/contact`→`/contact/`），表单数据丢失。Nginx 只代理 `/admin`、`/preview`、`/themes-images` 时需为 `/contact` 补一条 POST 代理规则。

## 13. 生成新主题的检查清单

1. 新建 `themes/{themeId}/`（如复制 `themes/xhope-cn/` → `themes/xhope-cn-v2/`），按目标站点改 `meta/theme.json` 的 `locales`/`defaultLocale`/`pages`/文案与 `meta/messages.json`。
2. `theme.json` 的 `pages[].template` 写逻辑名（`index`/`products`/`about`…）；片段引用写 `~{themes/__${themeId}__/fragments/...}`——**两者都不含主题名，复制目录后无需改动任何内部引用**。租户 `themeId` 指向新目录是唯一的"身份"改动。
3. 提供 `fragments/` 下 `head`/`seo`/`header`/`footer`/`language` 五个片段，保持 §10 签名。
4. 为 `pages` 声明的每个 `path` 提供对应模板文件；`/`→`index`、`/products`→`products`、`/news`→`news`，详情页固定为 `product-detail`/`news-detail`（由 Controller 硬编码返回）。
5. 每个页面的 `<head>` 用 `head` 片段并传入 §9 的 SEO 变量；`<html>` 设 `th:lang`/`th:dir`。
6. 所有站内链接拼 `localePrefix`；所有字段访问判空（`config`、`*.cover`、`*.subtitle`…）。
7. 列表页分页块用 `th:if="${totalPages != null and totalPages > 1}"` 守卫（live 不分页、`totalPages` 为 null）。
8. 样式内联进 `head.html` 的 `<style>`，断点覆盖 mobile/tablet/desktop，响应式不溢出（`img{max-width:100%}`、网格 `auto-fill`）。
9. 文案一律走 `messages.json` 键（`#{...}`），不硬编码企业名/地址等可变内容。
10. 至少为 `defaultLocale` 提供所有用到的文案键；`seo_config` 至少为 `defaultLocale` 填首页与关键页。
11. 验证：启动站点，默认语种与 `/zh-cn/` 前缀分别访问首页/列表/详情/静态页；后台 `/admin` 改一条 SEO 看前台是否覆盖生效；执行 `./gradlew test`。
12. 联系页 `contact.html` 按 §12 契约实现：表单 `class="contact-form"` + `action="/contact"`（**不用 `th:action`**）、字段 `name`/`email`/`message`（可选 `topic`）、常驻 `hidden` 的 `#contactSuccess`/`#contactError` 横幅、页脚后内联提交脚本；补 `contact.sent_success`/`contact.sent_failed` 文案键。提交后应内联弹出成功横幅并发信到 `tenant_config.email`。
