#!/usr/bin/env bash
# 备份保留策略（#28）：每日备份保留 14 份，每周备份保留 8 份。
# 依赖环境变量：R2_BACKUP_ENDPOINT / R2_BACKUP_ACCESS_KEY_ID /
# R2_BACKUP_SECRET_ACCESS_KEY / R2_BACKUP_BUCKET（由 GitHub Actions Secrets 提供）。
set -euo pipefail

: "${R2_BACKUP_ENDPOINT:?missing}"
: "${R2_BACKUP_ACCESS_KEY_ID:?missing}"
: "${R2_BACKUP_SECRET_ACCESS_KEY:?missing}"
: "${R2_BACKUP_BUCKET:?missing}"

export AWS_ACCESS_KEY_ID="$R2_BACKUP_ACCESS_KEY_ID"
export AWS_SECRET_ACCESS_KEY="$R2_BACKUP_SECRET_ACCESS_KEY"
export AWS_EC2_METADATA_DISABLED=true

now_epoch=$(date +%s)

delete_older_than() {
  local prefix="$1"   # 对象名前缀，如 db/daily-
  local days="$2"     # 保留天数
  aws --endpoint-url "$R2_BACKUP_ENDPOINT" s3 ls "s3://$R2_BACKUP_BUCKET/$prefix" \
    --query 'Contents[].{Key:Key,LastModified:LastModified}' --output json | jq -r '.[]? | [.Key, .LastModified] | @tsv' |
    while IFS=$'\t' read -r key last_modified; do
      [ -z "$key" ] && continue
      modified_epoch=$(date -d "$last_modified" +%s)
      age_days=$(( (now_epoch - modified_epoch) / 86400 ))
      if [ "$age_days" -gt "$days" ]; then
        echo "delete: $key (age ${age_days}d > ${days}d)"
        aws --endpoint-url "$R2_BACKUP_ENDPOINT" s3 rm "s3://$R2_BACKUP_BUCKET/$key"
      fi
    done
}

delete_older_than "db/daily-" 14
delete_older_than "db/weekly-" 56
echo "retention done"
