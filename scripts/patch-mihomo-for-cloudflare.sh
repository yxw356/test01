#!/usr/bin/env bash
# 在 mihomo 规则最前插入 Cloudflare / cloudflared 直连，避免 trycloudflare 隧道 QUIC 被代理掐断
set -euo pipefail
CFG="${MIHOMO_CONFIG:-$HOME/.config/mihomo/config.yaml}"
MARKER='PROCESS-NAME,cloudflared,DIRECT'

if grep -q "$MARKER" "$CFG" 2>/dev/null; then
  echo "mihomo 已含 cloudflared 直连规则: $CFG"
else
  echo "请在 $CFG 的 rules: 段最前加入 Cloudflare DIRECT 规则（见 docs/测试环境公网HTTPS部署.md §2.1.3）"
  exit 1
fi

if curl -sf -X PUT "http://127.0.0.1:9090/configs?force=true" \
  -H 'Content-Type: application/json' \
  -d "{\"path\":\"$CFG\"}" >/dev/null 2>&1; then
  echo "mihomo 配置已热重载"
else
  echo "热重载失败，请重启 mihomo: pkill mihomo && mihomo -d $(dirname \"$CFG\") &"
fi
