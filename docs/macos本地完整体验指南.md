# macOS 本地完整体验指南

> 适用于当前本机 `/Users/lhrzp/test01` 的开发体验环境。  
> 目标是让前端、后端、上传、Kafka 异步索引、Embedding、检索和运行监控都能完整跑通。

---

## 当前运行方式

这套环境没有依赖 Docker Desktop。中间件和本地兜底服务通过 Homebrew 或 LaunchAgent 运行：

| 组件 | 端口 | 运行方式 | 说明 |
| --- | --- | --- | --- |
| 前端 Vite | 9527 | `pnpm dev` | 浏览器访问入口 |
| Spring Boot | 8081 | `com.test01.backend` | local profile，JDK 17 |
| Redis | 6379 | Homebrew service | 密码 `PaiSmart2025` |
| MinIO API | 19000 | `com.test01.minio` | 桶名 `uploads` |
| MinIO Console | 19001 | `com.test01.minio` | `admin / PaiSmart2025` |
| Kafka | 9092 | Homebrew service | topic `file-processing` |
| 本地 ES 兼容服务 | 9200 | `com.test01.local-es` | 开发兜底，不替代生产 ES |
| 本地 Embedding 兼容服务 | 8001 | `com.test01.embedding` | OpenAI-compatible `/v1/embeddings` |
| 对话模型 | 8000 | oMLX | OpenAI-compatible `/v1/models`、聊天接口 |

---

## 一键状态检查

```bash
cd /Users/lhrzp/test01
chmod +x scripts/local-dev-status.sh
scripts/local-dev-status.sh
```

期望重点结果：

| 项 | 期望 |
| --- | --- |
| `frontend vite` | `UP` |
| `spring backend` | `UP` |
| `redis`、`minio`、`kafka` | `UP` |
| `local es`、`embedding`、`chat model` | `UP` |
| `preflight` | `上传服务已就绪` |
| `kafka` | `UP lag=0` |

---

## 访问入口

| 用途 | 地址 |
| --- | --- |
| 系统前端 | http://127.0.0.1:9527 |
| 运行监控 | http://127.0.0.1:9527/#/admin-monitoring |
| 后端 API | http://127.0.0.1:8081 |
| MinIO 控制台 | http://127.0.0.1:19001 |
| 本地 ES 兼容接口 | http://127.0.0.1:9200 |

默认登录账号：

```text
admin / admin123
```

---

## 重启服务

```bash
# 后端
launchctl kickstart -k gui/$(id -u)/com.test01.backend

# MinIO
launchctl kickstart -k gui/$(id -u)/com.test01.minio

# 本地 ES 兼容服务
launchctl kickstart -k gui/$(id -u)/com.test01.local-es

# 本地 Embedding 兼容服务
launchctl kickstart -k gui/$(id -u)/com.test01.embedding

# Kafka / Redis
brew services restart kafka
brew services restart redis
```

前端如果没有运行：

```bash
cd /Users/lhrzp/test01/frontend
pnpm dev --host 127.0.0.1 --port 9527
```

---

## 上传链路验收

在页面上传一个 `.txt` 文件后，运行监控页应看到：

| 位置 | 期望 |
| --- | --- |
| 知识资产列表 | 上传状态为「已完成」，索引状态最终为「可检索」 |
| 运行监控 | Elasticsearch `knowledgeBaseCount` 增加 |
| Kafka | `totalLag` 回到 `0` |

命令行可直接检查：

```bash
curl -s http://127.0.0.1:9200/knowledge_base/_count | python3 -m json.tool
```

---

## 本地兜底服务说明

`scripts/local_es_compat_server.py` 和 `scripts/local_embedding_server.py` 是为了本地体验准备的轻量兼容服务：

| 服务 | 作用 | 注意 |
| --- | --- | --- |
| `local_es_compat_server.py` | 兼容项目用到的 ES health、mapping、count、bulk、search 接口 | 只用于开发烟测，不提供真实 ES 8 全能力 |
| `local_embedding_server.py` | 提供 OpenAI-compatible `/v1/embeddings` | 返回确定性的 1024 维向量，方便本地索引链路跑通 |

生产或准生产环境仍建议使用真实 Elasticsearch 8.10.x 和真实 Embedding 模型。

---

## 常见问题

| 现象 | 处理 |
| --- | --- |
| 上传预检不是 `ready` | 先跑 `scripts/local-dev-status.sh`，看 Redis、MinIO、Kafka 哪个 DOWN |
| 上传合并成功但不可检索 | 等待 Kafka 消费；若 `lag` 长时间不为 0，重启后端和 Kafka |
| ES count 不增加 | 重启 `com.test01.local-es`，确认 `scripts/local_es_compat_server.py` 支持 chunked bulk |
| 运行监控 Kafka lag 偶尔短暂跳高 | 上传后消费者需要几秒处理，稍等刷新；长期不归零再排障 |
| 后端打包失败 `TypeTag UNKNOWN` | 使用 JDK 17：`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn package` |

---

## 下一步替换方向

当 Docker Desktop、Colima 或真实中间件环境可用后，建议逐步替换：

1. 用真实 Elasticsearch 8.10.x 替换本地 ES 兼容服务。
2. 用真实 bge-m3/vLLM Embedding 替换本地 Embedding 兼容服务。
3. 将 LaunchAgent 配置整理为统一安装脚本，减少人工操作。
