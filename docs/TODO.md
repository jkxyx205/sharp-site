# TODO.md

# 建站平台待办路线图

> 本文档列出 sharp-site 在 Phase 2 MVP 之上、作为一个可商用的建站平台仍需实现或补全的功能。
> 与 `REQUIREMENTS.md` §4「明确不做」对应：那些是 Phase 2 范围外的能力，大多在此作为 Phase 3+ 候选项重新评估。
> 优先级：**P0** 商用前应有 / **P1** 增强竞争力 / **P2** 远期 / 可选。

---

## 0. 现状基线（已具备）

Phase 2 MVP 已实现并可用：多租户 + 域名解析、Theme 解耦（modern / xhope / xhope-cn / lyhotec）、按租户语种 i18n、产品 / 新闻 / 视频分类与内容、媒体管理（sharp-fileupload）、SEO + sitemap + robots、后台登录与租户隔离、预览、静态化发布（版本目录 + 原子切换 + 失败回滚）。

以下 TODO 基于此基线，不重复已实现能力。

---

## 1. 询盘与客户转化（P0）

外贸独立站的核心目标是收询盘。当前 `themes/modern/contact.html` 存在 `POST /contact` 表单，但**后端无任何处理**（无 Controller 映射、无存储），表单提交即 404/405。这是商用前最高优先级缺口。

- [ ] `inquiry` 表 + Service：租户隔离，记录 name / email / phone / message / source_page / language / ip / user_agent / created_at / status(NEW/READ/REPLIED/ARCHIVED) / is_deleted。
- [ ] `SiteInquiryController` 处理 `POST /{locale}/contact`（及无前缀路径）：参数校验（Bean Validation）、Honeypot 反垃圾、限频、成功后回显 + 失败兜底。
- [ ] 询盘列表 / 详情 / 标记已读 / 导出 CSV / 邮件通知到 `tenant_config.email`（可复用 Spring `JavaMailSender`）。
- [ ] 询盘在静态发布版本中可工作（静态站无法直接 POST，需 Nginx 反代该路径到 Spring Boot，或在静态站用 JS 提交到平台 API）——明确架构并写入部署文档。
- [ ] WhatsApp / 表单跳转外链的统一管理（`tenant_config` 已有 whatsapp 字段，需模板联动）。
- [ ] 反垃圾：reCAPTCHA / Turnstile 可选接入点预留。

> 说明：询盘是「网站 → 线索」的唯一通道，属架构必需，非可选增强。建议优先于第 2 节以下所有项。

---

## 2. 部署与运维（P0）

当前仅有本地开发说明，缺少生产部署文档与自动化。

- [ ] 生产 `application.yml` profile（`prod`）：数据库连接、`sharp.site.www-root`、上传根目录、日志路径。
- [ ] Nginx 配置范例：静态站 `root /data/www/{tenant}/current;` + `/admin/*`、`/preview/*`、`POST /contact`（询盘）反代到 Spring Boot；域名 → tenant 目录映射；**域名不可访问其它 tenant 目录**（task §TASK-1403）。
- [ ] SSL：Phase 2 明确不做自动 SSL，但商用需明确方案（certbot / Caddy / 反代层托管），写入部署文档。
- [ ] 应用作为 systemd / 容器运行说明；健康检查端点（`/actuator/health`，是否引入 actuator 需评估依赖）。
- [ ] 发布目录清理策略：保留最近 N 个 release，旧版本可回滚。
- [ ] 数据库备份与恢复流程文档；`/data/www` 静态站备份。

---

## 3. 多管理员与权限（P0/P1）

当前 `admin_user` 仅支持单租户内「登录即全权」。

- [ ] 后台管理员 CRUD（平台级 / 租户级）：新增、停用、重置密码。当前无新增管理员入口，仅能靠种子数据。
- [ ] 密码修改 / 忘记密码（邮箱重置链接，有效期 token）。
- [ ] 登录失败限频、账户锁定、登录日志。
- [ ] （P1）简单角色：超级管理员 / 内容编辑（只读内容、不能发布），Phase 2 明确不做复杂 RBAC，此处为最小实用权限。
- [ ] （P1）操作审计日志 `audit_log`：谁在何时改了什么（租户级）。

---

## 4. 域名与 SSL 自动化（P1）

Phase 2 只做域名绑定 + Host 解析，不做 DNS 验证与自动 SSL。

- [ ] 域名归属验证（TXT 记录 / 文件验证）后再激活。
- [ ] 自动 SSL 签发与续期（Let's Encrypt ACME，HTTP-01 或 DNS-01）。
- [ ] `www` ↔ 裸域重定向规则统一管理。
- [ ] 域名状态机：PENDING_VERIFICATION → ACTIVE → DISABLED。

---

## 5. 计费与套餐（P1，明确 Phase 2 不做）

REQUIREMENTS §4 明确不做 SaaS 套餐计费与在线支付。商用前需重新评估。

- [ ] 套餐模型（页面数 / 产品数 / 语种数 / 域名数 / 流量上限）。
- [ ] 试用 / 到期 / 降级 → 站点只读 / 下线策略。
- [ ] 在线支付接入（Phase 2 禁止，需平台级批准后再做）。
- [ ] 发票 / 账单记录。

---

## 6. 模板生态（P1/P2）

- [ ] Theme 安装/切换机制：当前 theme 由开发人员维护在 resources 内，无运行时安装。评估是否需要打包式 theme（jar / 目录扫描）。
- [ ] 模板市场（Phase 2 明确不做，远期）。
- [ ] 在线模板设计器 / 拖拽建站（Phase 2 明确不做，远期，且与「结构化字段 + 富文本」原则冲突，需重新立项）。
- [ ] Theme 版本与升级影响说明（theme 变更如何影响已发布静态版本）。

---

## 7. SEO 与分析增强（P1）

当前已具备 title/description/canonical/robots/OG + sitemap + robots.txt。

- [ ] 结构化数据 JSON-LD（Organization / Product / Article / BreadcrumbList）。
- [ ] `hreflang` 标签（多语言镜像互相指向）。
- [ ] Google Analytics 在线配置（Phase 2 明确不做，但 GA4 ID 后台录入 + 模板注入是最小可用形态）。
- [ ] Google Search Console 自动提交（远期；最小可用可仅输出 sitemap URL 供手动提交）。
- [ ] 301 重定向规则管理（域名/路径变更后旧 URL → 新 URL），避免静态站 404。
- [ ] 图片 alt 校验提示（发布前 lint：缺失 alt 的图片告警）。

---

## 8. 性能与缓存（P1）

当前 ARCHITECTURE §11 缓存非强制，仅本地缓存。

- [ ] 引入 Redis（如已部署）缓存 Tenant / TenantConfig / Theme 解析结果。
- [ ] 静态资源缓存策略（Nginx `Cache-Control`、带 hash 的静态资源 URL）。
- [ ] 图片处理：上传后生成 WEBP + 缩略图（产品列表用缩略图、详情用大图），减少静态站首屏体积。
- [ ] CDN 接入点（Phase 2 明确不做 CDN 管理，但静态资源 URL 可配置 CDN 域名是最小形态）。
- [ ] 发布渲染性能：大批量产品 / 新闻静态化耗时监控与进度提示。

---

## 9. 媒体能力增强（P1）

- [ ] 媒体库搜索 / 按类型筛选 / 分组（当前仅列表删除）。
- [ ] 图片裁剪 / 尺寸预设（产品封面比例统一）。
- [ ] 视频处理（当前已有 `video` 模块与模板，评估是否需转码 / 封面自动截取 / 外链 embed 优先）。
- [ ] OSS 对接确认：`sharp-fileupload` 是否已对接 OSS，生产环境上传落点与 URL 生成规则。
- [ ] 批量上传 + 拖拽上传（后台媒体页）。
- [ ] 未引用媒体清理提示（被产品/新闻引用的媒体不可删）。

---

## 10. 安全增强（P0/P1）

§21 已覆盖 XSS/CSRF/SQLi/上传/路径穿越/越权/租户隔离/Session/密码。以下为补全：

- [ ] 询盘 / 登录路径限频（rate limit，防爆破与垃圾提交）。
- [ ] CSRF：确认 Spring Security CSRF 对后台生效且对询盘公开表单有策略（询盘表单属未登录公开端点，需单独 token 或放行并辅以 Honeypot）。
- [ ] 两步验证（TOTP）可选。
- [ ] 敏感配置（DB 密码、OSS 密钥）走环境变量 / secret，不入库不入 git（当前 `.env.postgres.properties` 仅本地）。
- [ ] 安全响应头（CSP / X-Frame-Options / HSTS）统一配置。
- [ ] 定期依赖漏洞扫描。

---

## 11. 内容与编辑体验（P1）

- [ ] 富文本编辑器增强：当前接入了富文本，评估图片插入是否直接走媒体库（避免用户贴外链）。
- [ ] 草稿 / 发布分离：当前预览读最新数据，评估是否需要显式 `draft` 状态与「定时发布」。
- [ ] 产品规格 `specification_json` 的结构化编辑器（键值表单而非手写 JSON）。
- [ ] 内容复制：产品 / 新闻跨语种复制（en 已编辑 → 复制到 zh-cn 草稿）。
- [ ] 批量操作：产品 / 新闻批量发布 / 隐藏 / 排序 / 删除。
- [ ] （P2，明确不做）AI 文章生成 / AI 建站。

---

## 12. 国际化扩展（P1）

架构已允许 `de-DE / fr-FR / es-ES / it-IT / ja-JP / ko-KR`。

- [ ] 主题 `messages_*.properties` 按语种补齐（界面文案）。
- [ ] 货币 / 单位展示（外贸场景多币价）。
- [ ] 语种切换链接与 `hreflang` 联动（见第 7 节）。
- [ ] RTL 语言预留（如后续接入 ar-SA）。

---

## 13. 数据与迁移（P1）

- [ ] 租户导入 / 导出：整套租户内容（产品 / 新闻 / 媒体引用 / SEO）导出为可迁移包，便于备份与跨环境。
- [ ] 租户级数据统计：内容数量、发布次数、询盘数（Dashboard）。
- [ ] 平台级运营视图：租户数、活跃租户、发布失败率。
- [ ] 历史发布版本回滚 UI（当前有版本目录与失败回滚，补「主动回滚到某历史版本」入口）。

---

## 14. 测试与 CI（P0/P1）

§23 列出关键测试范围，需确认覆盖率。

- [ ] 询盘提交端到端测试（第 1 节落地后）。
- [ ] CI 流水线：`./gradlew clean build`（JDK 17）全绿门槛。
- [ ] 多语种静态发布回归测试集（`/zh-cn/` 与 `/en-us/` 镜像齐全 + `/` 重定向）。
- [ ] 发布回滚测试纳入 CI（模拟 render 失败 → current 不变）。
- [ ] 越权回归：tenant A 无法读写 tenant B 数据 / 静态文件。

---

## 15. 文档（P0）

- [ ] 部署文档（Nginx + SSL + systemd / 容器，对应第 2 节）。
- [ ] 询盘架构说明（静态站如何回传到平台，对应第 1 节）。
- [ ] Theme 开发指南：`theme.json` / `messages.json` 规范、区块清单、可用变量。
- [ ] 平台运维手册：新增租户全流程（建租户 → 绑域名 → 选 theme → 配置 → 发布）。
- [ ] 将本 TODO 项按版本拆分进 `TASKS.md` 的 Phase 3+ 段落。

---

# 优先级小结

| 级别 | 范围 |
|---|---|
| **P0（商用前必须）** | 询盘落地（§1）、部署/运维/Nginx 文档（§2、§15）、管理员 CRUD 与密码重置（§3）、限频与公开表单安全（§10）、关键回归测试与 CI（§14） |
| **P1（增强）** | SSL 自动化（§4）、SEO JSON-LD/hreflang/GA（§7）、Redis 缓存与图片 WEBP（§8）、媒体搜索与裁剪（§9）、富文本/草稿/规格编辑器（§11）、更多语种（§12）、数据导出与统计（§13）、审计日志与最小角色（§3） |
| **P2（远期/明确 Phase 2 不做）** | 计费支付（§5）、模板市场与拖拽建站（§6）、AI 内容（§11）、CDN 管理（§8） |

---

# 原则

- 任一 TODO 落地前先对照 `REQUIREMENTS.md` §4 与 `CLAUDE.md` §22，确认是否曾属「明确不做」；若属，需先取得架构变更确认，不得直接实现。
- 新增数据表遵循 `DATABASE.md` §0 通用约定（审计列 + `tenant_id` + 部分唯一索引 + 逻辑删除）。
- 新增功能不得破坏 `Tenant → Domain → Theme → Content → i18n → SEO → Preview → Static Publish` 主链。
- 询盘等「静态站 → 平台」回传能力，需在 ARCHITECTURE.md 补一节说明 Nginx 反代规则，避免与「正式站优先静态」原则冲突。
