# 测试环境公网 HTTPS 部署

> 面向 **单台服务器 + 仅测试**（暂不对全员开放）。  
> - **有域名**：`https://你的域名` + Let's Encrypt（§5）  
> - **无域名**：Cloudflare 临时隧道（§2，推荐）或自签名 + 公网 IP（§2.3）

---

## 2. 无自有域名时（当前常见情况）

没有真实域名时，**无法**用 Let's Encrypt / Certbot 申请受浏览器信任的证书。可选三条路：

| 方案 | 浏览器是否信任 | 适合场景 | 飞书回调 |
| --- | --- | --- | --- |
| **A. Cloudflare Tunnel** | 是（`*.trycloudflare.com`） | 快速公网 HTTPS 测试 | 可用该 HTTPS 地址（URL 会变，需固定隧道时再注册域名） |
| **B. 自签名 + 公网 IP** | 否（需手动信任或点「继续访问」） | 固定 IP、少数人测 | 飞书通常不接受自签证书 |
| **C. 购买便宜域名** | 是 | 长期测试 / 飞书正式对接 | 推荐 |

### 2.1 方案 A：Cloudflare Tunnel（推荐，零域名）

隧道在公网侧提供 **HTTPS**，本机只需 HTTP 反代，无需 Certbot。

**1）本机 Nginx（仅 127.0.0.1:8080）**

```bash
cd /home/lhagent/test01/frontend && pnpm build
sudo mkdir -p /var/www/enterprise-kb
sudo cp -r dist/* /var/www/enterprise-kb/

sudo cp /home/lhagent/test01/docs/nginx-http-tunnel.conf \
  /etc/nginx/sites-available/enterprise-kb-tunnel.conf
sudo ln -sf /etc/nginx/sites-available/enterprise-kb-tunnel.conf /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

模板见 [nginx-http-tunnel.conf](./nginx-http-tunnel.conf)。

**2）安装并启动隧道**

```bash
# cloudflared 已安装可跳过；aarch64 可从 GitHub releases 下载 cloudflared-linux-arm64

# 无 sudo 时：用 Docker 跑 Nginx（DaoCloud 镜像示例）
docker rm -f enterprise-kb-nginx 2>/dev/null
docker run -d --name enterprise-kb-nginx --restart unless-stopped --network host \
  -v /home/lhagent/test01/frontend/dist:/var/www/enterprise-kb:ro \
  -v /home/lhagent/test01/docs/nginx-docker-tunnel.conf:/etc/nginx/conf.d/default.conf:ro \
  docker.m.daocloud.io/library/nginx:alpine

# 快速临时隧道（每次启动 URL 可能不同；建议 nohup 或 systemd 保活）
cloudflared tunnel --url http://127.0.0.1:8080
# 日志中查找：https://xxxx.trycloudflare.com
```

终端会打印类似：

```text
https://xxxx-yyyy-zzzz.trycloudflare.com
```

用该地址在浏览器打开即可登录、问答；WebSocket 为 `wss://xxxx...trycloudflare.com/proxy-ws/...`。

**3）注意**

- **公网页面与 `localhost:9527` 不同步**：隧道走 `pnpm build` 的 `dist`，改前端后需重新 `pnpm build`（Nginx 容器挂载 `dist` 目录，构建后即生效）；浏览器可强制刷新 `Ctrl+Shift+R`。
- 临时隧道 **URL 不固定**，重启 `cloudflared` 后可能变；适合内测，不适合写进飞书长期配置。  
- 需保持 `cloudflared` 进程运行；生产化可注册 Cloudflare 账号做 **Named Tunnel**（仍可无自有域名，用 Cloudflare 分配的二级域）。  
- 后端、vLLM、Docker 仍只监听本机，**不要**对公网直接开放 8081/8000。

**4）验证**

```bash
curl -sI "https://你的隧道域名/" | head -5
# 本机
curl -s http://127.0.0.1:8080/ | head -3
```

**隧道 URL 保存在** `.run/public-tunnel-url.txt`。重启隧道：

```bash
./scripts/restart-public-tunnel.sh
```

### 2.1.1 浏览器「意外终止了连接」

| 原因 | 说明 |
| --- | --- |
| 旧 URL 已失效 | 重启 `cloudflared` 后 `*.trycloudflare.com` 会变，勿继续用旧链接 |
| QUIC 被掐断 | 日志常见 `timeout: no recent network activity`；国内部分运营商/WiFi 对 Cloudflare QUIC 不稳定 |
| 隧道进程退出 | 关闭终端或 `pkill cloudflared` 后公网立即不可用 |

**处理顺序：**

1. 服务器执行 `./scripts/restart-public-tunnel.sh`，用日志里 **新的** `https://xxxx.trycloudflare.com`。
2. 本机先测：`curl -sI https://新域名/` 应看到 `HTTP/2 200`（在服务器上测通不代表你手机网络也能通）。
3. 浏览器仍失败时：**换手机 4G/5G 热点** 或 **VPN** 再打开；或同一 WiFi 下用局域网 `http://服务器局域网IP:9527`（开发模式，见手工启动指南）。
4. 可选优化（服务器）：`sudo sysctl -w net.core.rmem_max=7500000 net.core.wmem_max=7500000`（`restart-public-tunnel.sh` 会尝试设置）。

> 长期稳定公网请用 **§5 自有域名 + Nginx 443**，勿依赖临时 trycloudflare 链接。

### 2.1.2 隧道反复失败时的替代（推荐内测）

Cloudflare 临时隧道依赖 **QUIC**，在部分网络下即使用新 URL 也会「意外终止连接」；服务器日志可见 `timeout: no recent network activity` / 间歇 **502**。

**优先用下面方式，不依赖 trycloudflare：**

| 场景 | 地址 | 命令 |
| --- | --- | --- |
| 同一 WiFi / 内网 | `http://<服务器局域网IP>:8080` 或 `:9527` | `./scripts/start-lan-http.sh` |
| 公网 IP 直连 | `http://<公网IP>:8080` | 同上 + **安全组放行 TCP 8080** |

```bash
cd /home/lhagent/test01
./scripts/start-lan-http.sh
# 示例：http://192.168.31.43:8080（局域网）
# 公网需放行 8080 后：http://120.229.12.105:8080（以本机 ifconfig.me 为准）
```

- `:9527` 为 `pnpm dev`，仅适合内网调试；`:8080` 为 `pnpm build` + Nginx，与公网隧道同源前端。
- 本机若运行 **mihomo/系统代理**，重启隧道前请用 `./scripts/restart-public-tunnel.sh`（已尝试绕过代理）；仍失败则不要用隧道，改用本节 HTTP。

### 2.2 方案 B：自签名证书 + 公网 IP（无隧道）

仅适合 **少数人** 用 IP 访问；首次访问浏览器会警告「不安全」。

```bash
sudo mkdir -p /etc/nginx/ssl
sudo openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout /etc/nginx/ssl/enterprise-kb.key \
  -out /etc/nginx/ssl/enterprise-kb.crt \
  -subj "/CN=enterprise-kb-test"
```

复制 [nginx-https.conf](./nginx-https.conf)，将 `server_name` 改为 `_` 或你的公网 IP，并改证书路径：

```nginx
ssl_certificate     /etc/nginx/ssl/enterprise-kb.crt;
ssl_certificate_key /etc/nginx/ssl/enterprise-kb.key;
```

安全组放行 443 后访问 `https://公网IP/`（接受风险提示）。**飞书事件订阅一般要求受信任证书**，此方案不宜对接飞书。

### 2.3 方案 C：后续补域名

购买域名（几元～几十元/年）后，将 `A` 记录指向服务器，按 **§5** 走 Certbot 即可；与隧道方案可并存，最终建议固定域名。

---

## 3. 架构示意（有域名或隧道统一理解）

```text
互联网用户浏览器
        ↓ HTTPS :443
   Nginx / Caddy（本机）
        ├─ /              → 前端静态 dist（或开发期反代 9527，不推荐公网直连 Vite）
        ├─ /api/          → Spring Boot 127.0.0.1:8081
        └─ /proxy-ws/     → WebSocket 升级 → 127.0.0.1:8081/chat/...
        
本机仅监听（勿暴露公网）：
        127.0.0.1:8000  vLLM 对话
        127.0.0.1:8001  vLLM 向量（可选）
        MySQL / Redis / ES / Kafka / MinIO（Docker）
```

**原则：** 只把 **443（和证书申请用的 80）** 对公网开放；模型与中间件保持在 `127.0.0.1`。

---

## 4. 前置条件（有自有域名时）

| 项 | 说明 |
| --- | --- |
| 域名 | 已备案（若在中国大陆公网接入）；`A` 记录指向服务器公网 IP |
| 防火墙 / 安全组 | 放行 **80、443**；**不要**对公网开放 8081、9527、8000、8001、3307、9200 等 |
| 服务已启动 | Docker 中间件、Spring Boot **8081**、vLLM **8000**（问答测试）、前端 **生产构建** 或反代 |
| 安全 | 修改默认 `admin/admin123`；测试期建议 **IP 白名单** 限制 443 |

---

## 5. 有域名：Nginx + Let's Encrypt（Certbot）

### 5.1 安装

```bash
sudo apt update
sudo apt install -y nginx certbot python3-certbot-nginx
```

### 5.2 构建前端（生产模式）

前端 WebSocket 固定走 `/proxy-ws`，与 `docs/nginx-https.conf` 一致。

```bash
cd /home/lhagent/test01/frontend
pnpm install
pnpm build    # 使用 .env.prod：API 为 /api/v1
```

产物目录：`frontend/dist/`。

```bash
sudo mkdir -p /var/www/enterprise-kb
sudo cp -r /home/lhagent/test01/frontend/dist/* /var/www/enterprise-kb/
sudo chown -R www-data:www-data /var/www/enterprise-kb
```

### 5.3 部署 Nginx 配置

将仓库内模板复制并 **替换域名**：

```bash
sudo cp /home/lhagent/test01/docs/nginx-https.conf /etc/nginx/sites-available/enterprise-kb.conf
sudo sed -i 's/kb.example.com/你的真实域名/g' /etc/nginx/sites-available/enterprise-kb.conf
sudo ln -sf /etc/nginx/sites-available/enterprise-kb.conf /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

### 5.4 申请 HTTPS 证书

```bash
sudo certbot --nginx -d 你的真实域名
```

按提示选择自动重定向 HTTP→HTTPS。证书自动续期由 certbot 定时任务处理。

### 5.5 验证

```bash
curl -sI https://你的真实域名/ | head -5
curl -s https://你的真实域名/api/v1/   # 或根健康检查经反代
```

浏览器访问：

- `https://你的真实域名` → 登录页 / 问答工作台  
- 运行监控：`https://你的真实域名/#/admin-monitoring`  
- 问答前确认 **8000 对话模型** 已启动；输入框下方 WebSocket 为 **绿色**

---

## 6. 备选：Caddy（自动 HTTPS，配置更短）

`/etc/caddy/Caddyfile` 示例：

```caddyfile
你的真实域名 {
    root * /var/www/enterprise-kb
    file_server
    try_files {path} /index.html

    handle /api/* {
        reverse_proxy 127.0.0.1:8081
    }

    handle /proxy-ws/* {
        uri strip_prefix /proxy-ws
        reverse_proxy 127.0.0.1:8081 {
            header_up Host {host}
            header_up X-Real-IP {remote_addr}
            header_up X-Forwarded-For {remote_addr}
            header_up X-Forwarded-Proto {scheme}
        }
    }
}
```

```bash
sudo systemctl reload caddy
```

> Caddy 默认自动申请与续期 Let's Encrypt 证书。WebSocket 在 Caddy 2 下通常可工作；若异常，改回 Nginx 模板。

---

## 7. WebSocket 说明

前端生产环境连接路径（代码固定）：

```text
wss://你的域名/proxy-ws/chat/{JWT}
```

Nginx 将 `/proxy-ws` 前缀去掉后转发到后端 `/chat/...`，须配置：

- `proxy_http_version 1.1`
- `Upgrade` / `Connection: upgrade`

详见 `docs/nginx-https.conf` 中 `location /proxy-ws/` 段。

后端 `WebSocketConfig` 当前为 `setAllowedOrigins("*")`，测试可先用；**正式上线前**改为 `https://你的真实域名`（见 `需求文档.md` NFR-PROD-03）。

---

## 8. 测试期安全加固（强烈建议）

### 8.1 IP 白名单（Nginx）

在 `server { listen 443 ssl; ... }` 内、`location` 之前增加：

```nginx
# 仅示例：改成你的办公网/家庭公网 IP
allow 203.0.113.50;
deny all;
```

### 8.2 修改默认管理员

登录后进入 **个人中心** 修改密码（`PUT /api/v1/users/password`）；首次建库前可改 `application.yml` 中 `admin.password`。

### 8.3 勿暴露模型端口

- 8000 / 8001 保持 `127.0.0.1` 监听  
- 安全组不要放行 8000、8001  

### 8.4 JWT 与密钥

生产/公网测试应通过环境变量注入 `jwt.secret-key`、数据库与 MinIO 密码，勿使用仓库内默认明文。

---

## 9. 开发模式（9527）与公网的关系

| 方式 | 是否适合公网 HTTPS |
| --- | --- |
| `pnpm dev`（9527） | **不推荐** 直接暴露；Vite 开发服非生产用途 |
| `pnpm build` + Nginx | **推荐** |
| 临时演示 | **无域名**时用 **§2.1 Cloudflare Tunnel**；有域名时用 **§5 构建 + Nginx** |

公网测试：**无域名 → §2.1**；**有域名 → §5**。

---

## 10. 与飞书回调的关系

飞书事件订阅要求：

- 公网 **HTTPS** URL（不支持纯 HTTP 回调）
- 固定路径，如：`https://你的真实域名/api/feishu/event`（实现飞书 MVP 时再挂）

- **无域名 + 临时隧道**：可先用 `trycloudflare.com` 做联调，URL 变动需同步改飞书配置。  
- **固定飞书回调**：建议 **§2.3 购买域名** 后走 §5，证书受信任且 URL 稳定。

---

## 11. 常见问题

| 现象 | 处理 |
| --- | --- |
| 没有域名 | 用 **§2.1 Cloudflare Tunnel**，不要强行 certbot |
| 证书申请失败 | 须有真实域名且 `A` 记录指向本机；80 可从公网访问 |
| 隧道 URL 每次变 | 临时隧道正常；要固定 URL 需买域名或 Cloudflare Named Tunnel |
| 页面能开、登录 404 | 检查 `location /api/` 是否反代到 `8081`，且后端已启动 |
| 问答 WebSocket 红色 | 检查 `/proxy-ws/` 反代与 8000 对话模型；浏览器 F12 看 `wss://域名/proxy-ws/...` |
| 混合内容错误 | 确保全站 HTTPS，勿在 HTTPS 页中写死 `http://` API |
| 仅向量 UP、要问答 | 启动 8000，见 [手工启动指南.md](./手工启动指南.md) |

---

## 12. 相关文档

| 文档 | 说明 |
| --- | --- |
| [nginx-http-tunnel.conf](./nginx-http-tunnel.conf) | 无域名：本机 8080 HTTP，配合 cloudflared |
| [nginx-https.conf](./nginx-https.conf) | 有域名：Nginx 完整模板（HTTPS + API + WebSocket） |
| [nginx.conf](./nginx.conf) | 旧版 HTTP 8080 片段（本地参考） |
| [手工启动指南.md](./手工启动指南.md) | 后端、模型、Docker 启动顺序 |
| [操作手册.md](./操作手册.md) | 完整安装与排障 |
| [需求文档.md](./需求文档.md) §11 | 生产安全与验收要求 |
