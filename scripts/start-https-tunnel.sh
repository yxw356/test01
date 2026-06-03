#!/usr/bin/env bash
# 无域名公网 HTTPS：Docker Nginx (127.0.0.1:8080) + Cloudflare 临时隧道
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/frontend/dist"
NGINX_CONF="$ROOT/docs/nginx-docker-tunnel.conf"
IMAGE="${NGINX_IMAGE:-docker.m.daocloud.io/library/nginx:alpine}"
CLOUDFLARED="${CLOUDFLARED:-$HOME/.local/bin/cloudflared}"
LOG="$ROOT/.run/cloudflared.log"

if [[ ! -f "$DIST/index.html" ]]; then
  echo "缺少前端构建产物，执行: cd $ROOT/frontend && pnpm build"
  exit 1
fi

# 公网隧道使用 dist 静态资源，与 pnpm dev(9527) 不同步；改前端后请先 build
if [[ "${REBUILD_FRONTEND:-}" == "1" ]]; then
  echo "REBUILD_FRONTEND=1，正在 pnpm build ..."
  (cd "$ROOT/frontend" && pnpm build)
fi

docker rm -f enterprise-kb-nginx 2>/dev/null || true
docker run -d --name enterprise-kb-nginx --restart unless-stopped --network host \
  -v "$DIST:/var/www/enterprise-kb:ro" \
  -v "$NGINX_CONF:/etc/nginx/conf.d/default.conf:ro" \
  "$IMAGE"

if ! curl -sf -o /dev/null http://127.0.0.1:8080/; then
  echo "本机 http://127.0.0.1:8080 不可用，请检查 Docker 与 8081 后端"
  exit 1
fi

mkdir -p "$ROOT/.run"
pkill -f 'cloudflared tunnel --url http://127.0.0.1:8080' 2>/dev/null || true
sleep 1
nohup "$CLOUDFLARED" tunnel --url http://127.0.0.1:8080 >>"$LOG" 2>&1 &
echo "cloudflared pid=$! 日志: $LOG"
echo "等待隧道 URL..."
for _ in $(seq 1 30); do
  url=$(grep -oE 'https://[a-zA-Z0-9-]+\.trycloudflare\.com' "$LOG" 2>/dev/null | head -1 || true)
  if [[ -n "$url" ]]; then
    echo ""
    echo "公网访问地址: $url"
    echo "登录后请修改默认 admin 密码。"
    exit 0
  fi
  sleep 1
done
echo "未在日志中找到 trycloudflare.com，请查看: $LOG"
exit 1
