# CodeOps 可观测性设计

## 目标

为 CodeOps 的异步代码评审建立一套可查询、可统计、可追踪的可观测性能力。系统需要回答三个问题：任务执行到哪一步、LLM 和 Agent 工具调用发生了什么、整体运行质量和成本如何变化。

## 范围

本阶段覆盖 Java API、RabbitMQ Worker、Python LLM Backend、MySQL、Vue 前端和 Docker 配置。覆盖任务、分组、LLM 调用、工具调用、队列和错误指标；不实现自动误报判定、自动修复代码和完整分布式 Trace。

## 架构

ReviewTaskWorker 在任务、分组和 Java->Python 调用边界生成执行事件。Python AiReviewer 在 PLAN、REVIEW 和工具调用边界生成调用明细，随 LLM 响应返回给 Java。Java 将事件写入 MySQL，并通过 Micrometer 暴露实时 Counter、Timer 和 Gauge。前端通过业务 API 读取聚合统计和任务执行时间线；Prometheus 通过可选 Docker Profile 抓取 Actuator 指标。

```text
Git Hook -> ReviewTask -> RabbitMQ -> ReviewTaskWorker
                                  |-> execution_events (MySQL)
                                  |-> Micrometer metrics
                                  |-> LLM Backend
                                         |-> PLAN / REVIEW
                                         |-> file_read / file_find / code_search
```

## 事件模型

新增 `review_execution_events` 表，每一行表示一个不可变执行事件：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | varchar(36) | UUID 主键 |
| task_id | varchar(36) | 评审任务，可为空 |
| project_id | bigint | 项目 ID |
| group_number | int | 分组号，可为空 |
| event_type | varchar(30) | TASK、GROUP、LLM、TOOL、QUEUE |
| operation | varchar(60) | CREATE、START、COMPLETE、FAIL、RETRY、PLAN、REVIEW、FILE_READ 等 |
| status | varchar(20) | STARTED、SUCCESS、FAILED、CANCELLED |
| started_at | datetime(6) | 开始时间 |
| completed_at | datetime(6) | 完成时间 |
| duration_ms | bigint | 耗时 |
| model_name | varchar(120) | LLM 模型，可为空 |
| input_tokens | bigint | 输入 Token，可为空 |
| output_tokens | bigint | 输出 Token，可为空 |
| total_tokens | bigint | 总 Token，可为空 |
| estimated_cost | decimal(18,8) | 估算费用，可为空 |
| error_code | varchar(80) | 稳定错误码 |
| error_message | varchar(1000) | 脱敏后的错误信息 |
| metadata_json | json/text | 低敏摘要，如文件数、工具参数摘要 |
| created_at | datetime(6) | 创建时间 |

事件只保存路径、行号、参数数量等摘要，不保存完整 Prompt、完整 diff、工具返回代码或密钥。事件写入失败不能阻断评审主流程，但必须记录结构化日志。

## Micrometer 指标

### Counter

```text
codeops_review_tasks_total{status}
codeops_review_groups_total{status}
codeops_review_retries_total{reason}
codeops_llm_requests_total{phase,model,status}
codeops_tool_calls_total{tool,status}
codeops_queue_publish_total{status}
```

### Timer

```text
codeops_review_task_duration_seconds
codeops_review_group_duration_seconds
codeops_llm_duration_seconds{phase,model}
codeops_tool_duration_seconds{tool}
```

### Distribution and Gauge

```text
codeops_llm_tokens_total{direction,model}
codeops_llm_cost_total{model}
codeops_review_queue_depth{queue}
codeops_review_active_groups
codeops_outbox_pending_events
```

所有标签使用有限枚举或配置中的模型名，不使用 taskId、文件路径和用户输入，避免指标基数失控。

## LLM Backend 契约

`POST /api/ai/review` 在现有 `findings` 外返回 `metrics`：

```json
{
  "findings": [],
  "metrics": {
    "model": "gpt-4o-mini",
    "durationMs": 4200,
    "inputTokens": 3200,
    "outputTokens": 580,
    "totalTokens": 3780,
    "estimatedCost": 0.0012,
    "toolCalls": [
      {"name": "file_read", "durationMs": 120, "status": "SUCCESS"}
    ]
  }
}
```

Token 从 LangChain 响应的 `usage_metadata` 或 `response_metadata` 读取，供应商未提供时使用 null；费用依据模型价格配置计算，未知模型费用为 null。每次调用必须记录成功、失败、耗时和异常类型。

## Java API

新增：

```text
GET /api/observability/overview?from=&to=&projectId=
GET /api/observability/timeseries?from=&to=&projectId=&metric=
GET /api/observability/queue
GET /api/review-tasks/{taskId}/execution
```

`overview` 返回任务数、成功率、失败率、平均耗时、LLM 成功率、LLM 平均耗时、Token、估算费用、重试数、工具调用数和队列深度。`timeseries` 返回按小时聚合的任务、耗时、Token 和费用。`execution` 返回任务事件时间线、分组事件、LLM 调用和工具调用。

管理员可以查询全部项目；普通用户只能查询其具备评审权限的项目。未授权的 taskId 返回 403，不泄露任务是否存在。

## 前端

新增“运行监控”页面：

- 时间范围和项目筛选
- 任务总数、完成率、失败率、平均耗时
- LLM 成功率、平均响应时间、Token 和费用
- RabbitMQ 队列积压、Outbox 待发布数、重试次数
- 按小时趋势和失败任务列表
- LLM 阶段和工具调用统计

异步任务详情增加“执行明细”区域，以时间线展示任务、分组、PLAN、REVIEW 和工具调用。执行中的任务继续使用现有轮询机制，终态任务停止轮询。错误信息展示稳定错误码和脱敏摘要，不展示 Prompt 或完整代码。

## Docker 与运维

增加 Spring Boot Actuator，并暴露 `/actuator/health` 和 `/actuator/prometheus`。默认 Compose 启动不增加 Prometheus/Grafana；新增可选 `observability` profile 启动 Prometheus 抓取 Java 和 Python 指标。事件表按 `created_at` 和 `project_id` 建索引，默认保留 90 天，保留周期通过环境变量配置并由定时清理任务执行。

## 错误处理

- 观测事件写入失败只记录日志，不改变评审任务状态。
- LLM 超时、HTTP 错误、JSON 解析错误统一映射为稳定错误码。
- 工具失败记录后继续遵循现有 Agent 工具循环策略。
- 队列发布失败保留 Outbox 重试语义，并增加发布失败计数。
- 所有事件携带 taskId 和 groupNumber 的日志 MDC；指标标签不携带这些高基数字段。

## 测试与验收

- Python：验证 Token 元数据提取、工具调用成功/失败、异常和费用计算。
- Java：验证事件保存、聚合查询、权限过滤、任务执行详情和队列 Gauge。
- 前端：验证监控卡片、筛选、趋势数据、任务时间线和空/错误状态。
- 集成：创建异步任务，确认每个分组、LLM 调用和工具调用都有对应事件，任务失败/重试后统计正确。
- Docker：确认默认 Compose 健康检查通过，Prometheus Profile 能抓取指标。

验收标准：前端可以在不查看容器日志的情况下解释一个任务的完整执行过程；管理员可以看到项目级耗时、成功率、Token 和费用；普通用户无法读取无权限项目数据；观测组件故障不会阻断评审任务。
