# 内网部署指南

## 架构

```
[Server]  :8443  ASP.NET Core + SQLite
[Admin]   WPF 管理端（任意管理员 PC）
[Client]  WPF 客户端（用户 PC）
[Agent]   Windows Service（共享本机目录的 PC）
```

## 1. 环境要求

- Windows 10/11 或 Windows Server 2019+
- [.NET 8 SDK](https://dotnet.microsoft.com/download/dotnet/8.0)（构建用）
- [.NET 8 Runtime](https://dotnet.microsoft.com/download/dotnet/8.0)（运行 Server/Agent 用）

## 2. 构建

在 Windows 开发机上：

```powershell
cd IntranetFileShare
dotnet restore
dotnet build -c Release
dotnet test tests/IntranetFileShare.Server.Tests
```

产物路径：

| 组件 | 路径 |
|------|------|
| Server | `src/IntranetFileShare.Server/bin/Release/net8.0/` |
| Agent | `src/IntranetFileShare.Agent/bin/Release/net8.0/` |
| Admin | `src/IntranetFileShare.Admin/bin/Release/net8.0-windows/` |
| Client | `src/IntranetFileShare.Client/bin/Release/net8.0-windows/` |

## 3. 部署 Server（控制面）

### 3.1 复制文件

将 Server 发布目录复制到内网服务器，例如 `D:\IntranetFileShare\Server\`。

```powershell
dotnet publish src/IntranetFileShare.Server -c Release -o D:\IntranetFileShare\Server
```

### 3.2 配置

编辑 `appsettings.json`：

```json
{
  "ConnectionStrings": {
    "Default": "Data Source=D:\\IntranetFileShare\\data\\intranet.db"
  },
  "Jwt": {
    "Secret": "请替换为至少32字符的随机密钥",
    "Issuer": "IntranetFileShare"
  },
  "Urls": "http://0.0.0.0:8443"
}
```

> v1 默认 HTTP。生产环境建议前置 IIS/nginx 做 HTTPS 终结，或配置 Kestrel 证书。

### 3.3 启动

```powershell
cd D:\IntranetFileShare\Server
dotnet IntranetFileShare.Server.dll
```

首次启动自动创建 SQLite 数据库，默认账户：

- 用户名：`admin`
- 密码：`admin123`（登录后立即修改）

### 3.4 注册为 Windows 服务（可选）

```powershell
sc create IntranetFileShareServer binPath= "dotnet D:\IntranetFileShare\Server\IntranetFileShare.Server.dll" start= auto
sc start IntranetFileShareServer
```

### 3.5 防火墙

```powershell
New-NetFirewallRule -DisplayName "IntranetFileShare Server" -Direction Inbound -Protocol TCP -LocalPort 8443 -Action Allow
```

## 4. 部署 Agent（各共享 PC）

### 4.1 发布

```powershell
dotnet publish src/IntranetFileShare.Agent -c Release -o C:\Program Files\IntranetFileShare\Agent
```

### 4.2 配置 agent.json

```json
{
  "ServerUrl": "http://192.168.1.10:8443",
  "ListenPort": 5001,
  "Shares": [
    { "ShareId": 1, "LocalPath": "D:\\Finance" }
  ]
}
```

> ShareId 在管理端创建共享后获得。

### 4.3 安装服务

以管理员运行：

```powershell
.\scripts\install-agent.ps1 -ServerUrl "http://192.168.1.10:8443"
```

### 4.4 URL ACL（HttpListener 需要）

```powershell
netsh http add urlacl url=http://+:5001/ user=Everyone
```

### 4.5 防火墙

Agent 端口 5001 仅对内网网段放行：

```powershell
New-NetFirewallRule -DisplayName "IntranetFileShare Agent" -Direction Inbound -Protocol TCP -LocalPort 5001 -Action Allow -RemoteAddress 192.168.1.0/24
```

## 5. HTTPS 证书（可选，推荐）

### 自签证书（内网）

```powershell
$cert = New-SelfSignedCertificate -DnsName "fileserver.local" -CertStoreLocation "Cert:\LocalMachine\My" -NotAfter (Get-Date).AddYears(5)
```

导出 PFX 后在 `appsettings.json` 配置 Kestrel HTTPS，或将 Server 放在 IIS 反向代理后。

客户端/Agent 连接自签 HTTPS 时，需将证书导入「受信任的根证书颁发机构」。

## 6. 网络拓扑

| 端口 | 组件 | 访问范围 |
|------|------|----------|
| 8443 | Server | 全内网 |
| 5001 | Agent | 内网各 PC 互访 |

跨 VLAN 时，IT 需放通 Server 8443 与各 Agent 5001。

## 7. 安全建议

1. 修改默认 admin 密码
2. 更换 Jwt.Secret 为随机 32+ 字符
3. Server/Agent 不映射到公网
4. 定期备份 `intranet.db`
5. 通过管理端审计日志监控异常访问
