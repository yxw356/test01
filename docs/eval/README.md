# RAG 分库评测集规范

> 与 [知识库分域与边界定义.md](../知识库分域与边界定义.md)、[需求文档 FR-PROD-RAG-01/02](../需求文档.md) 对齐。  
> **勿将含敏感业务数据的题面提交 Git**；仓库内仅保留格式说明与示例结构。

---

## 1. 目录结构

```text
docs/eval/
├── README.md                 # 本文件
├── KB_POLICY/
│   └── questions.jsonl       # 制度流程库（业务方维护，可 .gitignore）
├── KB_PROJECT/
│   └── questions.jsonl
├── KB_PRESALES/
│   └── questions.jsonl
└── KB_OPS/
    └── questions.jsonl
```

各子目录已放置 `.gitkeep` 与 `questions.jsonl.example`（复制为 `questions.jsonl` 后填入真实题面，勿提交敏感内容）。

---

## 2. JSONL 字段说明

每行一条 JSON 对象：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 题号，如 `POLICY-001` |
| `question` | string | 是 | 用户问题原文 |
| `kb_id` | string | 是 | 业务库 `tag_id`：`KB_POLICY` 等 |
| `type` | string | 是 | `answerable` \| `unanswerable` \| `cross_kb_negative` |
| `expected_doc` | string | 否 | 期望命中文件名或关键词（answerable） |
| `expected_answer_contains` | string[] | 否 | 答案应包含的短语（勿写完整机密答案） |
| `must_cite` | boolean | 否 | 是否必须带引用来源，默认 true |
| `negative_cross_kb` | string | 否 | 越权题：其他库 `tag_id`，检索应为 0 命中 |
| `notes` | string | 否 | 评测备注 |

### 2.1 题型

| type | 用途 | 验收 |
| --- | --- | --- |
| `answerable` | 库内有材料的标准问 | Top5 召回、答案正确率、引用准确率 |
| `unanswerable` | 库内无依据或超范围 | 拒答率 ≥ 90%（「暂无相关信息」类） |
| `cross_kb_negative` | 他库私有文档，本题库用户不应看到 | 检索 0 命中、越权 0 次 |

### 2.2 示例行（可复制改 id）

```json
{"id":"POLICY-001","question":"年假有多少天？","kb_id":"KB_POLICY","type":"answerable","expected_doc":"请假制度","expected_answer_contains":["年假"],"must_cite":true}
{"id":"POLICY-002","question":"火星基地报销流程是什么？","kb_id":"KB_POLICY","type":"unanswerable","must_cite":false}
{"id":"POLICY-003","question":"某未授权售前报价单中的折扣是多少？","kb_id":"KB_POLICY","type":"cross_kb_negative","negative_cross_kb":"KB_PRESALES"}
```

---

## 3. 规模建议

| 库 | 题量（试点） | 构成建议 |
| --- | --- | --- |
| 每库 | 20～30 | 60% answerable、25% unanswerable、15% cross_kb_negative |
| 全项目 | 50～100（§11.4） | 发版前至少跑当前试点库全集 |

---

## 4. 执行方式（试点手工 / 未来自动）

**试点手工：**

1. 用试点账号（仅该库 `org_tags`）登录。
2. 混合检索 / 智能问答逐题记录：是否命中、`expected_doc`、是否拒答、是否越权。
3. 填表或脚本输出 CSV，对照 [需求文档 §11.4](../需求文档.md) 门槛。

**未来（阶段 2）：**

- `scripts/run-rag-eval.sh --kb KB_POLICY`：批量调 `/api/v1/search/hybrid` 与可选 LLM 判分。
- 管理端展示按库评测报告（FR-PROD-KB-04）。

---

## 5. 相关文档

- [知识库分域与边界定义.md](../知识库分域与边界定义.md)
- [企业能力落地说明.md §2](../企业能力落地说明.md) — 权限与 `DocumentAccessPolicyTest`
