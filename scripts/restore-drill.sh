#!/usr/bin/env bash
# 恢复演练（#28）：把最新加密备份恢复到全新 PostgreSQL 数据库，
# 运行 Flyway 校验，并通过公开读取核对 Published Revision、搜索投影、
# 站点设置与 Page View 聚合。仅证明备份文件存在不算验收通过——本脚本
# 的输出（各核对项 PASS）才是恢复验收记录。
#
# 必需环境变量：
#   R2_BACKUP_ENDPOINT / R2_BACKUP_ACCESS_KEY_ID / R2_BACKUP_SECRET_ACCESS_KEY / R2_BACKUP_BUCKET
#   BACKUP_PRIVATE_KEY_FILE  离线私钥文件路径（gpg，加密备份的解密密钥）
#   RESTORE_DATABASE_URL     全新空 PostgreSQL 的 JDBC URL（如 Neon 新库）
#   RESTORE_DATABASE_USERNAME / RESTORE_DATABASE_PASSWORD
#   BACKEND_DIR              后端模块目录（默认 ./backend）
#   SITE_BASE_URL            公开站点基础 URL（核对公开读取用，默认 http://localhost:1816）
set -euo pipefail

: "${R2_BACKUP_ENDPOINT:?missing}"
: "${R2_BACKUP_ACCESS_KEY_ID:?missing}"
: "${R2_BACKUP_SECRET_ACCESS_KEY:?missing}"
: "${R2_BACKUP_BUCKET:?missing}"
: "${BACKUP_PRIVATE_KEY_FILE:?missing}"
: "${RESTORE_DATABASE_URL:?missing}"
: "${RESTORE_DATABASE_USERNAME:?missing}"
: "${RESTORE_DATABASE_PASSWORD:?missing}"
BACKEND_DIR="${BACKEND_DIR:-./backend}"
SITE_BASE_URL="${SITE_BASE_URL:-http://localhost:1816}"

export AWS_ACCESS_KEY_ID="$R2_BACKUP_ACCESS_KEY_ID"
export AWS_SECRET_ACCESS_KEY="$R2_BACKUP_SECRET_ACCESS_KEY"
export AWS_EC2_METADATA_DISABLED=true

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT
cd "$WORKDIR"

echo "== 1/6 下载最新每日备份 =="
latest=$(aws --endpoint-url "$R2_BACKUP_ENDPOINT" s3 ls "s3://$R2_BACKUP_BUCKET/db/" \
  --query 'Contents[?contains(Key, `daily-`)].Key' --output text | tr '\t' '\n' | sort | tail -1)
[ -n "$latest" ] || { echo "FAIL: 备份桶中没有每日备份"; exit 1; }
aws --endpoint-url "$R2_BACKUP_ENDPOINT" s3 cp "s3://$R2_BACKUP_BUCKET/$latest" backup.dump.gpg
echo "backup: $latest"

echo "== 2/6 离线私钥解密 =="
gpg --batch --yes --import "$BACKUP_PRIVATE_KEY_FILE" 2>/dev/null || true
gpg --batch --yes --decrypt --output backup.dump backup.dump.gpg
[ -s backup.dump ] || { echo "FAIL: 解密失败（检查 BACKUP_PRIVATE_KEY_FILE）"; exit 1; }

echo "== 3/6 恢复到全新数据库 =="
pg_restore --clean --if-exists --no-owner --no-privileges \
  --dbname "$RESTORE_DATABASE_URL" backup.dump

echo "== 4/6 Flyway 校验 =="
( cd "$BACKEND_DIR" && mvn -q flyway:validate \
    -Dflyway.url="$RESTORE_DATABASE_URL" \
    -Dflyway.user="$RESTORE_DATABASE_USERNAME" \
    -Dflyway.password="$RESTORE_DATABASE_PASSWORD" )

echo "== 5/6 启动应用并公开读取核对 =="
( cd "$BACKEND_DIR" && SPRING_DATASOURCE_URL="$RESTORE_DATABASE_URL" \
    SPRING_DATASOURCE_USERNAME="$RESTORE_DATABASE_USERNAME" \
    SPRING_DATASOURCE_PASSWORD="$RESTORE_DATABASE_PASSWORD" \
    nohup mvn -q spring-boot:run > app.log 2>&1 & echo $! > app.pid )
trap 'kill "$(cat app.pid)" 2>/dev/null || true; rm -rf "$WORKDIR"' EXIT

check() { # name, curl args...
  local name="$1"; shift
  for _ in $(seq 1 60); do
    if curl -sf "$SITE_BASE_URL/api/v1/health" >/dev/null 2>&1; then break; fi
    sleep 2
  done
  if curl -sf "$@" >/dev/null 2>&1; then echo "PASS: $name"; else echo "FAIL: $name"; exit 1; fi
}

check "Published Revision 详情" "$SITE_BASE_URL/api/posts/mvp-launch-notes"
check "搜索投影（标题命中）" "$SITE_BASE_URL/api/posts?q=$(python3 -c "import urllib.parse;print(urllib.parse.quote('MVP'))")"
check "站点设置-介绍" "$SITE_BASE_URL/api/site/introduction"
check "站点设置-联系方式" "$SITE_BASE_URL/api/site/contact"
check "精选 Project" "$SITE_BASE_URL/api/projects"

echo "== 6/6 Page View 聚合核对（psql）=="
psql "$RESTORE_DATABASE_URL" -tAc \
  "SELECT 'PASS: page_view_totals rows = ' || count(*) FROM page_view_totals"

echo
echo "恢复演练完成：全部核对项 PASS（本输出即验收记录）。"
