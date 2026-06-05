#!/usr/bin/env bash
# 局域网/公网 IP 直连 HTTP（不经过 Cloudflare）。公网需在安全组放行 8080。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/frontend/dist"
NGINX_CONF="$ROOT/docs/nginx-docker-lan.conf"
IMAGE="${NGINX_IMAGE:-docker.m.daocloud.io/library/nginx:alpine}"
LAN_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
PUB_IP="$(curl -sf --max-time 5 ifconfig.me 2>/dev/null || curl -sf --max-time 5 ip.sb 2>/dev/null || true)"

if [[ ! -f "$DIST/index.html" ]]; then
  echo "缺少 dist，执行: cd $ROOT/frontend && pnpm build"
  exit 1
fi

docker rm -f enterprise-kb-nginx 2>/dev/null || true
docker run -d --name enterprise-kb-nginx --restart unless-stopped --network host \
  -v "$DIST:/var/www/enterprise-kb:ro" \
  -v "$NGINX_CONF:/etc/nginx/conf.d/default.conf:ro" \
  "$IMAGE"

sleep 2
if ! curl -sf -o /dev/null "http://127.0.0.1:8080/"; then
  echo "http://127.0.0.1:8080 不可用"
  exit 1
fi

echo ""
echo "局域网访问（同一 WiFi/内网）:"
echo "  http://${LAN_IP:-<本机IP>}:8080"
echo "  或开发模式: http://${LAN_IP:-<本机IP>}:9527"
if [[ -n "$PUB_IP" ]]; then
  echo ""
  echo "公网 IP 直连（须在云厂商安全组放行 TCP 8080）:"
  echo "  http://${PUB_IP}:8080"
fi
echo ""
echo "说明: Cloudflare trycloudflare 在部分网络会被掐断，可优先用上述地址测试。"
