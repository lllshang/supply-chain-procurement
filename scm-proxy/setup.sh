#!/usr/bin/env bash
# ============================================================
# scm.16u.cc 本地反代 shim —— 一键部署（macOS / Apple Silicon）
# 修复「直接打开/刷新子路由（如 /product）返回 404」
# 需要：macOS + Homebrew + 管理员密码（sudo）
# ============================================================
set -euo pipefail

export PATH="/opt/homebrew/bin:$PATH"

PROJECT_DIR="/Users/xiaobinli/Desktop/dzgylxt/scm-proxy"
CONF_SRC="$PROJECT_DIR/nginx-scm.conf"
SERVERS_DIR="/opt/homebrew/etc/nginx/servers"
CONF_DST="$SERVERS_DIR/scm-spa-proxy.conf"
NGINX_CONF="/opt/homebrew/etc/nginx/nginx.conf"
SSL_DIR="/opt/homebrew/etc/nginx/ssl"
INCLUDE_LINE="    include /opt/homebrew/etc/nginx/servers/*.conf;"
INCLUDE_MARKER="# >>> scm.16u.cc proxy include >>>"
HOSTS_LINE="127.0.0.1 scm.16u.cc"
HOSTS_MARKER="# >>> scm.16u.cc SPA proxy >>>"

echo "==> [1/7] 检查 Homebrew..."
if ! command -v brew >/dev/null 2>&1; then
  echo "未检测到 Homebrew，正在安装（约 1-2 分钟）..."
  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
  export PATH="/opt/homebrew/bin:$PATH"
else
  echo "Homebrew 已存在：$(brew --version | head -1)"
fi

echo "==> [2/7] 安装依赖 (nginx, mkcert)..."
brew install nginx mkcert 2>&1 | tail -3 || true

echo "==> [3/7] 生成本地可信证书 (mkcert)..."
sudo mkdir -p "$SSL_DIR"
sudo chown "$(whoami)" "$SSL_DIR"
cd "$SSL_DIR"
mkcert -install
mkcert scm.16u.cc
ls -l "$SSL_DIR/scm.16u.cc.pem" "$SSL_DIR/scm.16u.cc-key.pem"

echo "==> [4/7] 部署 nginx 配置到 $CONF_DST ..."
mkdir -p "$SERVERS_DIR"
cp "$CONF_SRC" "$CONF_DST"

echo "==> [5/7] 确保主配置加载 servers/ 目录..."
if ! grep -qF "servers/*.conf" "$NGINX_CONF"; then
  echo "主配置未包含 servers/，自动插入 include..."
  sudo python3 - "$NGINX_CONF" "$INCLUDE_MARKER" "$INCLUDE_LINE" <<'PY'
import sys
p, marker, line = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
if marker not in s:
    idx = s.rstrip().rfind('}')
    s = s[:idx] + "\n" + marker + "\n" + line + "\n" + s[idx:]
    open(p, 'w').write(s)
    print("已插入 include 到主配置")
else:
    print("已存在，跳过")
PY
else
  echo "主配置已包含 servers/，跳过"
fi

echo "==> [6/7] 更新 /etc/hosts (需 sudo)..."
if ! grep -qF "$HOSTS_LINE" /etc/hosts; then
  echo "$HOSTS_MARKER" | sudo tee -a /etc/hosts >/dev/null
  echo "$HOSTS_LINE" | sudo tee -a /etc/hosts >/dev/null
  echo "已追加: $HOSTS_LINE"
else
  echo "/etc/hosts 已存在该条目，跳过"
fi

echo "==> [7/7] 校验并启动 nginx..."
sudo nginx -t
if sudo nginx -s reload 2>/dev/null; then
  echo "nginx 已重载"
else
  echo "nginx 未运行，启动中..."
  sudo nginx
fi

echo ""
echo "============================================================"
echo "完成。请在浏览器打开（注意是 https）："
echo "  https://scm.16u.cc/product"
echo "  https://scm.16u.cc/   (根地址不变，站内导航照常)"
echo ""
echo "命令行快速验证（忽略证书）："
echo "  curl -kI https://scm.16u.cc/product"
echo "  应看到 HTTP/2 200"
echo ""
echo "停止并还原：bash $PROJECT_DIR/teardown.sh"
echo "============================================================"
