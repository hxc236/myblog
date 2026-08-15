# 数据库与 Media Asset 备份、恢复演练（#28）

## 备份

GitHub Actions 每日（`03:23 UTC`）执行 `.github/workflows/backup.yml`：

1. `pg_dump --format=custom --no-owner --no-privileges` 生成 Neon PostgreSQL 逻辑备份；
2. 用离线公钥（`BACKUP_PUBLIC_KEY`）gpg 加密；
3. 上传到独立私有 R2 backup bucket（`db/daily-YYYYMMDD.dump.gpg`；每周日另存
   `db/weekly-YYYYMMDD.dump.gpg`）；
4. Media Asset 二进制从媒体桶同步镜像到 backup bucket 的 `media/`（与数据库备份分离）；
5. `scripts/backup-retention.sh` 保留最近 14 份每日备份与 8 份每周备份。

数据库连接与备份写入凭据只由 GitHub Actions Secrets 提供，不进入仓库：

| Secret | 用途 |
| --- | --- |
| `DATABASE_URL` | Neon 连接串（`postgres://...`） |
| `BACKUP_PUBLIC_KEY` | 离线备份加密公钥（armored ASCII） |
| `R2_BACKUP_ENDPOINT` / `R2_BACKUP_ACCESS_KEY_ID` / `R2_BACKUP_SECRET_ACCESS_KEY` / `R2_BACKUP_BUCKET` | 独立私有备份桶（S3 兼容） |
| `R2_MEDIA_ENDPOINT` / `R2_MEDIA_BUCKET` | 生产媒体桶（与备份桶同账户时直接用备份凭据同步） |

## 恢复演练（上线前必须执行一次并保留输出）

`scripts/restore-drill.sh` 完成：

1. 下载最新每日备份；
2. 离线私钥解密（私钥不进入仓库与 CI）；
3. `pg_restore` 到**全新空库**（如新建的 Neon 数据库）；
4. `mvn flyway:validate` 校验恢复后结构与迁移一致；
5. 启动应用（指向恢复库），通过公开读取核对 Published Revision 详情、
   搜索投影（标题命中）、站点设置（介绍/联系方式）与精选 Project；
6. psql 核对 Page View 聚合。

只有全部核对项输出 `PASS` 才视为恢复验收通过；备份文件存在本身不算验收。
仓库内的自动化预演 `BackupRestoreDrillTest`（Testcontainers：pg_dump → 恢复
到新容器 → Flyway validate → 正式读路径核对）保证该流程在空库上可复现。
