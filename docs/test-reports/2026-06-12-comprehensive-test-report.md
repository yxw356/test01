# 龙汇QA全面测试报告

测试时间：2026-06-12  
测试分支：`LHRAG-run`  
测试方案：`docs/superpowers/plans/2026-06-12-comprehensive-test-plan.md`  
测试性质：本地自动化验收 + 核心接口冒烟 + 页面只读检查 + 短时并发压测

## 1. 结论摘要

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| 后端单元测试 | 通过 | 指定测试集 18 个用例全部通过 |
| 前端构建 | 通过 | `vite build --mode test` 构建成功，仅有 Node 本地存储实验特性提示 |
| 前端页面 | 通过 | `http://127.0.0.1:9527/#/chat` 可渲染，页面显示“龙汇QA 问答工作台”，控制台无 error |
| 登录链路 | 通过 | `admin` 登录成功并返回 token |
| 核心业务接口 | 通过 | 用户、知识资产、分区、拓扑、上传预检、清洗规则、FAQ、术语、案例、审计日志、监控接口均返回 200 |
| 运行监控 | 部分通过 | 监控接口可访问；Redis 显示 DOWN，其他关键组件显示 UP |
| Actuator 健康检查 | 未通过 | `/actuator/health` 返回 503，主要由 Redis 健康检查失败触发 |
| 短时并发压测 | 通过 | 10 并发 15 秒，1253 次请求，错误率 0，整体 P95 13.5ms |
| 50 人完整压测 | 未执行 | 本轮先做无大模型、无上传的轻量压测；50 人含聊天/上传压测建议单独窗口执行 |

总体判断：当前项目主业务链路可以体验，知识库列表、拓扑、上传预检、监控、问答页面等基础功能可用；上线前需要优先处理 Redis 健康检查与完整 50 人压测，随后补齐权限矩阵、AI 效果集和文件生命周期的人工验收。

## 2. 测试环境

| 项目 | 地址/配置 |
| --- | --- |
| 前端 | `http://127.0.0.1:9527` |
| 后端 | `http://127.0.0.1:8081` |
| 本地服务方式 | LaunchAgent：`com.test01.backend` |
| 前端进程 | Vite，端口 9527 |
| 后端进程 | Spring Boot，端口 8081 |
| 数据库 | H2 文件库：`./data/PaiSmart` |
| 大模型配置 | DeepSeek/OpenAI-compatible，本报告不记录密钥明文 |

## 3. 执行明细

### 3.1 自动化测试

| 测试项 | 命令 | 结果 |
| --- | --- | --- |
| 后端核心测试 | `mvn -Dtest=RetrievalCitationTest,DocumentLifecycleServiceTest,PolicyAuditAgentServiceTest,ChatHandlerKnowledgeSpaceTest test` | 通过，18/18 |
| 前端构建 | `node_modules/.bin/vite build --mode test` | 通过 |

覆盖重点：
- 来源文件名推断与展示模型。
- 文件生命周期过滤。
- 制度审计 Agent 基础规则。
- 聊天知识空间约束。

### 3.2 服务与接口冒烟

| 接口 | 状态码 | 耗时 | 结果 |
| --- | ---: | ---: | --- |
| `/api/v1/users/me` | 200 | 0.037s | 通过 |
| `/api/v1/documents/uploads` | 200 | 0.109s | 通过，返回 16 个文件 |
| `/api/v1/documents/knowledge-spaces` | 200 | 0.020s | 通过，返回 3 个知识库分区 |
| `/api/v1/documents/topology` | 200 | 0.023s | 通过，返回 16 个节点、24 条边 |
| `/api/v1/upload/preflight` | 200 | 0.038s | 通过，MinIO/Kafka UP，Redis DOWN |
| `/api/v1/upload/supported-types` | 200 | 0.008s | 通过 |
| `/api/v1/data-cleaning/rules/default` | 200 | 0.009s | 通过 |
| `/api/v1/knowledge-assistant/faqs` | 200 | 0.011s | 通过，当前 0 条 |
| `/api/v1/knowledge-assistant/terms` | 200 | 0.010s | 通过，当前 0 条 |
| `/api/v1/knowledge-assistant/cases` | 200 | 0.013s | 通过，当前 0 条 |
| `/api/v1/admin/audit-logs` | 200 | 0.026s | 通过，返回 20 条 |
| `/api/v1/admin/monitoring/status` | 200 | 0.727s | 通过 |

监控组件状态：

| 组件 | 状态 |
| --- | --- |
| Redis | DOWN |
| MinIO | UP |
| Elasticsearch | UP |
| Chat 模型 | UP |
| Embedding 模型 | UP |
| Kafka | UP |

### 3.3 上传能力检查

`/api/v1/upload/supported-types` 返回支持类型：

| 类型 |
| --- |
| Markdown 文档、文本文件、HTML 文档、XML 文档、JSON 文件、CSV 文件 |
| Word 文档、Excel 表格、PowerPoint 演示文稿、PDF 文档 |
| OpenDocument 文本/表格/演示文稿、富文本文档、EPUB 电子书 |
| Apple Pages、Numbers、Keynote |

上传预检结果显示 MinIO 与 Kafka 正常，Redis 为 DOWN。因为断点续传高度依赖缓存/状态能力，Redis 未连接时应重点复测上传中断后的续传体验。

### 3.4 页面检查

浏览器当前页面：`http://127.0.0.1:9527/#/chat`

| 检查项 | 结果 |
| --- | --- |
| 页面标题 | `聊天助手` |
| 品牌显示 | 已显示“龙汇QA 问答工作台” |
| 登录态 | 已登录，左侧菜单可见 |
| 控制台 error | 0 条 |
| 聊天历史渲染 | 可见 |

### 3.5 短时并发压测

压测命令：

```bash
/usr/bin/python3 scripts/local_load_test.py \
  --users 10 \
  --duration 15 \
  --ramp-seconds 3 \
  --think-time 0.2 \
  --list-weight 80 \
  --preflight-weight 20 \
  --upload-weight 0 \
  --chat-weight 0
```

结果：

| 指标 | 数值 |
| --- | ---: |
| 总请求数 | 1253 |
| 成功数 | 1253 |
| 错误数 | 0 |
| 错误率 | 0% |
| 平均响应 | 8.86ms |
| P50 | 7.68ms |
| P95 | 13.5ms |
| P99 | 35.2ms |
| 最大响应 | 117.26ms |

分场景：

| 场景 | 请求数 | 错误率 | P95 | 最大响应 |
| --- | ---: | ---: | ---: | ---: |
| 登录 | 10 | 0% | 117.26ms | 117.26ms |
| 知识列表 | 995 | 0% | 12.3ms | 49.87ms |
| 上传预检 | 248 | 0% | 13.92ms | 25.84ms |

说明：本轮压测刻意关闭了上传和聊天，避免大模型调用成本和文件写入影响判断；它只能证明基础接口在 10 并发下稳定，不能替代 50 人完整业务压测。

## 4. 问题与风险

| 等级 | 问题 | 影响 | 建议 |
| --- | --- | --- | --- |
| P1 | Redis 当前为 DOWN | 影响健康检查、续传状态、缓存能力；`/actuator/health` 返回 503 | 本地完整体验应启动 Redis，或在 local profile 中关闭/降级 Redis health indicator |
| P2 | `/actuator/health` 与业务可用性不一致 | 运维看到 503 会误判服务整体不可用 | 将 Redis 可选化，或将核心业务健康与依赖健康拆分为 readiness/liveness |
| P2 | 50 人完整压测未覆盖聊天与上传 | 不能证明大模型、WebSocket、文件分片在 50 人下稳定 | 单独执行 50 用户、含聊天/上传/预览的压测，并记录模型延迟与错误率 |
| P2 | FAQ、术语、案例当前为空 | 智能体增强入口已可用，但缺少真实治理数据 | 准备部门负责人维护样例，再评测标准问答、同义词和案例辅助回答 |
| P3 | 启停日志存在关闭阶段 `NoClassDefFoundError` | 当前不影响启动，但会污染日志并增加排障难度 | 检查打包依赖和 LaunchAgent 重启方式，确认 fat jar 关闭阶段依赖完整 |

## 5. 未执行项

| 模块 | 原因 | 建议执行方式 |
| --- | --- | --- |
| 权限矩阵全量测试 | 需要准备 `hr_lead/hr_user/sales_lead/sales_user/kb_admin` 等账号和部门数据 | 按测试方案第 3 节创建账号，逐条执行 PERM 用例 |
| 断点续传真实文件测试 | 本轮未执行真实大文件中断/续传 | 准备 20MB、50MB、100MB 文件，重点观察 Redis DOWN 时行为 |
| AI 效果评测集 | 需要固定制度样本和标准答案 | 建立 120 题评测集，记录命中率、来源准确率、拒答率 |
| PPT/题库导出人工验收 | 本轮只验证接口存在，未下载检查文件内容 | 用公共知识和部门知识分别生成 PPT、题库并人工审阅 |
| Obsidian 风格知识网络交互 | 本轮验证拓扑接口非空，未做图谱交互截图 | 用浏览器检查拖动、缩放、节点点击、部门颜色区分 |

## 6. 下一步执行计划

| 顺序 | 事项 | 目标 |
| --- | --- | --- |
| 1 | 修复 Redis 本地依赖或 local 健康检查配置 | 让 `/actuator/health` 不再误报 503 |
| 2 | 执行上传中断/续传专项测试 | 验证用户之前遇到的续传跳登录和中断不可恢复问题是否彻底解决 |
| 3 | 创建多角色测试账号和部门数据 | 跑完整权限矩阵，确认增删改查不越权 |
| 4 | 准备 AI 效果评测集 | 量化知识库回答准确率、来源点击率、拒答质量 |
| 5 | 执行 50 人完整压测 | 覆盖登录、知识列表、上传预检、文件预览、聊天、题库、监控 |
| 6 | 生成最终验收报告 | 汇总缺陷、风险、性能指标和上线建议 |

## 7. 2026-06-12 后续修复记录

| 项目 | 修复结果 |
| --- | --- |
| local 健康检查 | 已在 `application-local.yml` 关闭 Redis 与 Elasticsearch 的 Actuator 默认健康项，避免本地可选依赖导致 `/actuator/health` 误报 DOWN |
| 上传续传兜底 | 已新增 `knowledge.upload.redis.enabled` 配置；local 环境关闭 Redis 分片状态后，上传状态从数据库 `chunk_info` 记录恢复 |
| 上传预检 | Redis 关闭时显示 `SKIPPED`，不再阻断上传预检；MinIO 与 Kafka 正常时 `ready=true` |
| 单元测试 | 新增 `UploadServiceRedisFallbackTest`，覆盖 Redis 关闭时从数据库恢复已上传分片 |

复测结果：

| 检查项 | 结果 |
| --- | --- |
| `/actuator/health` | 200，`{"status":"UP"}` |
| `/api/v1/upload/preflight` | 200，`ready=true`，Redis=`SKIPPED`，MinIO/Kafka=`UP` |
| `/api/v1/admin/monitoring/status` | 200，Redis=`DOWN`，MinIO/Elasticsearch/模型/Kafka=`UP` |
| 后端测试 | 21 个指定用例通过 |
