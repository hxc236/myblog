# PostgreSQL 作为动态个人站的内容权威库

第二阶段把 Public Introduction、Project、联系方式、Markdown 正文、Draft、Published Revision、Category、Tag 和匿名 Page View 聚合存入 PostgreSQL，并以数据库内容为唯一权威源；选择 PostgreSQL 而不是延续 Git 文件或 legacy MySQL，是因为网页管理和立即发布需要事务化更新公开内容、发布修订和搜索投影，`pg_trgm` 能覆盖第一版标题与摘要搜索，而且当前不存在需要迁移的生产内容数据库。生产首先使用标准 JDBC 连接 Neon，托管平台可替换而不改变领域模型或 Flyway 迁移。
