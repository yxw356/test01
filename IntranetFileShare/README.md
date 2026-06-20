# IntranetFileShare

内网角色分权文件系统 — 集中控制面 + 分布式 PC 共享 + WPF 管理端/客户端。

**仓库地址：** https://github.com/qifenghe/auth--folder-admin

## 下载安装（Windows x64）

无需安装 .NET Runtime，解压即用：

| 组件 | Zip 包 | 可执行文件 |
|------|--------|-----------|
| 完整包 | [release/IntranetFileShare-Full-win-x64.zip](release/IntranetFileShare-Full-win-x64.zip) | 含 install.bat |
| Server | [release/IntranetFileShare-Server-win-x64.zip](release/IntranetFileShare-Server-win-x64.zip) | `IntranetFileShare.Server.exe` |
| Admin | [release/IntranetFileShare-Admin-win-x64.zip](release/IntranetFileShare-Admin-win-x64.zip) | `IntranetFileShare.Admin.exe` |
| Client | [release/IntranetFileShare-Client-win-x64.zip](release/IntranetFileShare-Client-win-x64.zip) | `IntranetFileShare.Client.exe` |
| Agent | [release/IntranetFileShare-Agent-win-x64.zip](release/IntranetFileShare-Agent-win-x64.zip) | `IntranetFileShare.Agent.exe` |

> 克隆仓库后需安装 [Git LFS](https://git-lfs.com/) 才能拉取 exe/zip：`git lfs install && git clone ...`

**快速安装：** 解压完整包，以管理员运行 `release/install.bat`。

默认管理员：`admin` / `admin123`

## 组件

| 项目 | 说明 |
|------|------|
| `IntranetFileShare.Server` | ASP.NET Core 控制面 API + SQLite |
| `IntranetFileShare.Agent` | Windows Service，代理本机共享目录 |
| `IntranetFileShare.Admin` | WPF 管理端 |
| `IntranetFileShare.Client` | WPF 客户端 |
| `IntranetFileShare.Shared` | 共享 DTO 与枚举 |

## 快速开始（Windows）

```powershell
# 构建
dotnet restore
dotnet build -c Release

# 启动 Server
dotnet run --project src/IntranetFileShare.Server

# 启动管理端 / 客户端（需 Windows + WPF）
dotnet run --project src/IntranetFileShare.Admin
dotnet run --project src/IntranetFileShare.Client
```

默认管理员：`admin` / `admin123`

## 文档

- [部署指南](docs/deploy.md)
- [使用说明](docs/user-guide.md)

## 权限模型

```
DENY 优先 → ALLOW（User/Group/Role）→ 角色能力上限 → 超级管理员 bypass
```

## 端口

- Server: `8443`
- Agent: `5001`
