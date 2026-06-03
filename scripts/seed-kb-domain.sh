#!/usr/bin/env bash
# 写入四类业务知识库组织标签（幂等）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SQL="$ROOT/docs/databases/seed-kb-org-tags.sql"
if [[ ! -f "$SQL" ]]; then
  echo "缺少 $SQL"
  exit 1
fi
docker exec -i mysql mysql -uroot -pPaiSmart2025 PaiSmart <"$SQL"
echo "已执行 seed-kb-org-tags.sql"
docker exec mysql mysql -uroot -pPaiSmart2025 PaiSmart -e \
  "SELECT tag_id, name FROM organization_tags WHERE tag_id LIKE 'KB_%' ORDER BY tag_id;"
