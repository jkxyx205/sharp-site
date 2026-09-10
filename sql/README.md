# sql/

数据库 DDL 脚本(PostgreSQL),内容与 `docs/DATABASE.md` 一致,按任务逐步追加。

- 应用**不会**自动执行这些脚本(Spring sql init 未启用),需手工执行。
- 本地开发库连接信息在 `src/main/resources/.env.postgres.properties`(gitignored)。
- 脚本可重复执行:建表语句报错 "already exists" 时跳过即可。
