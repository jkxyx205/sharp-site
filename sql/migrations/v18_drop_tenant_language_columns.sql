-- Phase 18: 语言配置移出数据库,语种来源改为主题 theme.json(见 REQUIREMENTS.md §26.1/§26.6)。
-- 移除 tenant 上的语言列;可重复执行(DROP IF EXISTS)。
-- 存量语言信息由租户 theme_id 对应的 theme.json 决定,原列值不再使用。

ALTER TABLE tenant DROP COLUMN IF EXISTS default_language;
ALTER TABLE tenant DROP COLUMN IF EXISTS languages;
