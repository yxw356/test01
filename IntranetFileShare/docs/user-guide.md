# 使用说明

## 管理端（Admin）

### 登录

1. 启动 `IntranetFileShare.Admin.exe`
2. 填写 Server 地址（如 `http://192.168.1.10:8443`）
3. 使用管理员账户登录（默认 `admin` / `admin123`）

### 用户管理

- 创建用户：填写用户名、密码、显示名、角色
- 角色说明：

| 角色 | 能力 |
|------|------|
| Viewer | 只读 |
| User | 读 + 写 |
| Manager | 读 + 写 + 删 + 管理共享 |
| Admin | 全部 + 管理端 |
| SuperAdmin | 绕过 ACL |

### 组管理

创建部门/项目组，ACL 中可用 `Group` 主体类型 + 组 ID 授权。

### 共享管理

1. 在目标 PC 安装 Agent 并启动
2. 管理端「共享管理」查看 Agent 列表（在线/离线）
3. 创建共享：填写 Agent ID、逻辑名（如 `finance`）、本地路径（如 `D:\Finance`）
4. 在 Agent 的 `agent.json` 中添加对应 ShareId 映射：

```json
"Shares": [{ "ShareId": 1, "LocalPath": "D:\\Finance" }]
```

5. 重启 Agent 服务

### ACL 配置

选中共享后添加 ACL 条目：

| 字段 | 示例 |
|------|------|
| 主体类型 | User / Group / Role |
| 主体 ID | 用户 ID `2`，或角色名 `Viewer`，或组 ID `1` |
| 动作 | Read / Write / Delete / Manage |
| 效果 | Allow / Deny（Deny 优先） |

示例：让组 1 的所有成员只读共享「finance」：

- Group / `1` / Read / Allow

### 审计日志

查看登录、列表、上传、下载、删除等操作记录。

---

## 客户端（Client）

### 登录

1. 启动 `IntranetFileShare.Client.exe`
2. 填写 Server 地址和账户
3. 登录后左侧显示有权访问的共享

### 浏览文件

1. 选择共享（Agent 需在线）
2. 右侧显示文件列表
3. 双击目录进入，「上级目录」返回

### 操作

| 按钮 | 条件 |
|------|------|
| 下载 | 选中文件 + 有 Read 权限 |
| 上传 | 有 Write 权限 |
| 删除 | 选中项 + 有 Delete 权限 |

Agent 离线时会提示「Agent 离线，无法访问该共享」。

---

## 典型部署流程

1. 部署 Server，登录管理端改密码
2. 创建用户和组
3. 在 PC-A 安装 Agent，注册共享目录
4. 管理端创建 Share，配置 ACL
5. PC-B 安装 Client，登录验证访问权限
6. 检查审计日志

---

## 故障排查

| 现象 | 排查 |
|------|------|
| 登录失败 | 检查 Server 地址、端口、防火墙 |
| 共享列表为空 | 检查 ACL 是否授予 Read |
| Agent 离线 | 检查 Agent 服务、心跳、Server 连通性 |
| 文件列表失败 | 检查 agent.json ShareId 映射、本地路径是否存在 |
| 403 无权限 | 检查 ACL 和角色上限（Viewer 不可写） |
