#!/usr/bin/env bash
# 重启 Cloudflare 临时公网隧道（本机 Nginx 127.0.0.1:8080 须已可用）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG="$ROOT/.run/cloudflared-live.log"
URL_FILE="$ROOT/.run/public-tunnel-url.txt"
CF="${CLOUDFLARED:-$HOME/.local/bin/cloudflared}"

# 增大 UDP 缓冲，减轻 QUIC 断连（需 root 时自动跳过）
if command -v sysctl >/dev/null 2>&1; then
  sysctl -w net.core.rmem_max=7500000 net.core.wmem_max=7500000 2>/dev/null \
    || sudo sysctl -w net.core.rmem_max=7500000 net.core.wmem_max=7500000 2>/dev/null \
    || true
fi

pkill -f 'cloudflared tunnel --url http://127.0.0.1:8080' 2>/dev/null || true
sleep 2

# 避免本机 mihomo/HTTP_PROXY 干扰 cloudflared 连 Cloudflare
unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY all_proxy ALL_PROXY
export NO_PROXY='127.0.0.1,localhost,.trycloudflare.com,.cloudflare.com'

if ! curl -sf -o /dev/null http://127.0.0.1:8080/; then
  echo "本机 http://127.0.0.1:8080 不可用，请先执行: $ROOT/scripts/start-https-tunnel.sh（或确保 enterprise-kb-nginx 在跑）"
  exit 1
fi

: >"$LOG"
nohup env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY -u all_proxy -u ALL_PROXY \
  NO_PROXY='127.0.0.1,localhost,.trycloudflare.com,.cloudflare.com' \
  "$CF" tunnel --url http://127.0.0.1:8080 >>"$LOG" 2>&1 &
echo "cloudflared pid=$! 日志: $LOG"

for _ in $(seq 1 45); do
  url=$(grep -oE 'https://[a-zA-Z0-9-]+\.trycloudflare\.com' "$LOG" | head -1 || true)
  if [[ -n "$url" ]]; then
    echo "$url" >"$URL_FILE"
    echo ""
    echo "公网 HTTPS: $url"
    sleep 3
    if curl -sfI -m 20 "$url/" | head -1 | grep -q '200'; then
      echo "外网探测: HTTP 200（服务器侧正常）"
    else
      echo "外网探测: 服务器 curl 未返回 200，请查看 $LOG"
    fi
    echo ""
    echo "若浏览器仍「连接意外终止」，多为本地网络拦截 Cloudflare QUIC："
    echo "  - 换手机热点 / VPN 再试"
    echo "  - 或同局域网访问 http://$(hostname -I 2>/dev/null | awk '{print $1}'):9527"
    exit 0
  fi
  sleep 2
done

echo "未获取到隧道 URL，请查看: $LOG"
exit 1
