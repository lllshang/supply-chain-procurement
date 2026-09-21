#!/usr/bin/env bash
# ============================================================
# scm.16u.cc 本地反代 shim —— 一键还原（macOS）
# 停止 nginx、移除配置与 /etc/hosts 条目、撤销主配置 include，恢复直连
# 需要：sudo
# ============================================================
set -euo pipefail

export PATH="/opt/homebrew/bin:$PATH"

PROJECT_DIR="/Users/xiaobinli/Desktop/dzgylxt/scm-proxy"
CONF_DST="/opt/homebrew/etc/nginx/servers/scm-spa-proxy.conf"
NGINX_CONF="/opt/homebrew/etc/nginx/nginx.conf"
INCLUDE_MARKER="# >>> scm.16u.cc proxy include >>>"
HOSTS_LINE="127.0.0.1 scm.16u.cc"
HOSTS_MARKER="# >>> scm.16u.cc SPA proxy >>>"

echo "==> 停止 nginx..."
sudo nginx -s stop 2>/dev/null || true

echo "==> 移除 nginx 站点配置..."
sudo rm -f "$CONF_DST"
echo "已移除 $CONF_DST"

echo "==> 撤销主配置的 include 行..."
if grep -qF "$INCLUDE_MARKER" "$NGINX_CONF"; then
  sudo sed -i '' "/$INCLUDE_MARKER/d; /include \/opt\/homebrew\/etc\/nginx\/servers\/\*.conf;/d" "$NGINX_CONF"
  echo "已移除 include 行"
else
  echo "主配置无相关 include，跳过"
fi

echo "==> 还原 /etc/hosts (需 sudo)..."
if grep -qF "$HOSTS_MARKER" /etc/hosts; then
  sudo sed -i '' "/$HOSTS_MARKER/d; /127.0.0.1 scm.16u.cc/d" /etc/hosts
  echo "已移除 hosts 条目"
else
  echo "hosts 无相关条目，跳过"
fi

echo ""
echo "============================================================"
echo "完成。scm.16u.cc 已恢复为直连真实服务器（深链 404 会再次出现）。"
echo ""
echo "如需彻底清理本地证书信任，可额外执行："
echo "  rm -rf /opt/homebrew/etc/nginx/ssl"
echo "  mkcert -uninstall   # 仅在你确定不再用 mkcert 时执行"
echo "============================================================"
