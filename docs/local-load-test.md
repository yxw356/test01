# 本地 20 人并发压测

这个脚本用于本机完整体验环境的轻量压测，不依赖 k6/Locust，只使用 Python 标准库。

## 默认压测

```bash
python3 scripts/local_load_test.py
```

默认参数：

- 20 个虚拟用户
- 60 秒持续时间
- 10 秒爬坡启动
- 混合请求：知识库列表 55%、上传预检 15%、小文件上传 15%、聊天 WebSocket 15%
- 聊天默认统计首包响应时间：`--chat-mode first-byte`

## 建议分阶段跑

先跑不含聊天的小压测，确认基础链路稳定：

```bash
python3 scripts/local_load_test.py \
  --users 10 \
  --duration 30 \
  --chat-weight 0 \
  --upload-weight 10 \
  --list-weight 80 \
  --preflight-weight 10
```

再跑完整 20 人混合压测：

```bash
python3 scripts/local_load_test.py \
  --users 20 \
  --duration 60 \
  --ramp-seconds 10
```

如果本地大模型响应慢，可以降低聊天权重：

```bash
python3 scripts/local_load_test.py \
  --users 20 \
  --duration 60 \
  --chat-weight 5 \
  --upload-weight 15 \
  --list-weight 65 \
  --preflight-weight 15
```

如果要测完整回答完成时间：

```bash
python3 scripts/local_load_test.py \
  --users 5 \
  --duration 60 \
  --chat-weight 100 \
  --list-weight 0 \
  --preflight-weight 0 \
  --upload-weight 0 \
  --chat-mode completion \
  --chat-timeout 180
```

## 结果说明

输出为 JSON，核心字段：

- `total`：请求总数
- `errors`：失败数
- `error_rate`：失败率
- `avg_ms`：平均耗时
- `p50_ms`、`p95_ms`、`p99_ms`：延迟分位数
- `by_scenario`：按登录、列表、上传、聊天分别统计

## 注意

上传场景会生成 `loadtest-*.md` 小文件并触发异步清洗/索引，因此完整压测后 Kafka 和 ES 计数会增加。

聊天场景会真实调用当前配置的大模型。当前项目建议用环境变量配置 DeepSeek：

```bash
export DEEPSEEK_API_URL=https://api.deepseek.com
export DEEPSEEK_API_MODEL=deepseek-v4-flash
export DEEPSEEK_API_KEY=<你的 DeepSeek API Key>
```
