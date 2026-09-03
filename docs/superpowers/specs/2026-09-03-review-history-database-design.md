# 评审历史数据库持久化设计

## 目标

为 CodeOps Copilot 增加 MySQL 持久化能力。大模型继续由 Python LangChain 服务调用，Java 不负责大模型推理；Java 负责本机 Git 仓库扫描、评审结果保存和历史记录查询。前端不再使用 `localStorage` 保存评审历史。

## 架构

```text
浏览器
  ├─ POST /api/repositories/scan ───────> Java 8080 ──> 本机 Git
  ├─ POST /api/ai/review ───────────────> LLM 8090 ──> 模型服务
  └─ POST /api/reviews ────────────────> Java 8080 ──> MySQL
     GET  /api/reviews ────────────────> Java 8080 ──> MySQL
```

Git 评审流程为：前端扫描变更、选择文件、直接调用 LLM、将 LLM 返回的结构化结果提交给 Java 保存。Java 的持久化接口不触发第二次大模型评审，避免重复调用和职责混淆。

## 技术选型

- MySQL 8.0，作为评审历史的持久化数据库。
- Spring Data JPA，负责实体映射和查询。
- Flyway，负责版本化数据库迁移。
- MySQL 通过 Docker Compose 暴露到宿主机 `3306`，本机运行的 Java 连接 `127.0.0.1:3306`。
- 评审补丁保存到数据库，完整源文件不单独保存；补丁中可能包含敏感代码，部署文档必须提示访问控制和数据清理策略。

## 数据模型

### `projects`

保存扫描过的本机 Git 仓库，一个绝对路径对应一个项目。

| 字段 | 类型 | 约束与用途 |
|---|---|---|
| `id` | BIGINT | 主键，自增 |
| `name` | VARCHAR(255) | 项目展示名称 |
| `repository_path` | VARCHAR(1000) | 本机绝对路径，唯一 |
| `last_branch` | VARCHAR(255) | 最近扫描分支 |
| `last_head_commit` | CHAR(40) | 最近扫描的 HEAD |
| `created_at` | DATETIME(6) | 创建时间 |
| `updated_at` | DATETIME(6) | 更新时间 |

### `reviews`

保存一次完整评审的元数据和状态。

| 字段 | 类型 | 约束与用途 |
|---|---|---|
| `id` | CHAR(36) | UUID 主键 |
| `request_id` | CHAR(36) | 客户端请求 UUID，唯一，用于幂等保存 |
| `project_id` | BIGINT | 外键关联 `projects.id` |
| `title` | VARCHAR(500) | 评审标题 |
| `source_type` | VARCHAR(30) | `GIT` 或 `MANUAL` |
| `scope` | VARCHAR(30) | `WORKTREE`、`BASE_COMMIT` 或 `NULL` |
| `base_ref` | VARCHAR(255) | 基准分支或提交 |
| `branch` | VARCHAR(255) | 评审时分支 |
| `head_commit` | CHAR(40) | 评审时 HEAD |
| `status` | VARCHAR(30) | `COMPLETED` 或 `FAILED` |
| `model_name` | VARCHAR(100) | LLM 模型名称 |
| `risk_score` | INT | 0 到 100 的风险评分 |
| `finding_count` | INT | 问题数量 |
| `error_message` | TEXT | 失败原因 |
| `created_at` | DATETIME(6) | 创建时间 |
| `completed_at` | DATETIME(6) | 完成时间 |

### `review_files`

保存本次评审涉及的变更文件及补丁元数据。

| 字段 | 类型 | 约束与用途 |
|---|---|---|
| `id` | BIGINT | 主键，自增 |
| `review_id` | CHAR(36) | 外键关联 `reviews.id` |
| `path` | VARCHAR(1000) | 仓库内相对路径 |
| `git_status` | VARCHAR(30) | `MODIFIED`、`ADDED`、`DELETED` 等 |
| `additions` | INT | 新增行数 |
| `deletions` | INT | 删除行数 |
| `patch` | MEDIUMTEXT | Git 补丁内容 |
| `content_hash` | CHAR(64) | 补丁或内容 SHA-256 |
| `created_at` | DATETIME(6) | 创建时间 |

### `review_findings`

保存 LLM 返回的结构化问题。

| 字段 | 类型 | 约束与用途 |
|---|---|---|
| `id` | BIGINT | 主键，自增 |
| `review_id` | CHAR(36) | 外键关联 `reviews.id` |
| `file_id` | BIGINT | 外键关联 `review_files.id` |
| `category` | VARCHAR(64) | 问题类别 |
| `severity` | VARCHAR(20) | `CRITICAL`、`HIGH`、`MEDIUM`、`LOW` |
| `line_number` | INT | 代码行号 |
| `message` | VARCHAR(2000) | 问题描述 |
| `suggestion` | TEXT | 修复建议 |
| `evidence` | TEXT | 代码证据 |
| `confidence` | DECIMAL(5,4) | 0 到 1 的置信度 |
| `created_at` | DATETIME(6) | 创建时间 |

## 关系和索引

```text
projects 1 ─── N reviews 1 ─── N review_files
                         └──── N review_findings
```

- `projects.repository_path` 建唯一索引。
- `reviews.project_id, reviews.created_at` 建组合索引，支持项目历史分页。
- `reviews.status, reviews.created_at` 建组合索引，支持状态筛选。
- `review_files.review_id`、`review_findings.review_id`、`review_findings.file_id` 建普通索引。
- 删除项目时级联删除评审、文件和问题；删除评审时级联删除文件和问题。

## API 契约

### 保存评审

`POST /api/reviews`

请求体包含 `requestId`、项目路径、Git 元数据、文件补丁和 LLM findings。接口同步完成数据库保存，返回完整评审记录和数据库 UUID，不触发 LLM。

### 查询历史

`GET /api/reviews?limit=20&offset=0&projectId=...`

返回历史摘要，默认按 `created_at DESC` 排序，不返回完整补丁和长文本证据。

### 查询详情

`GET /api/reviews/{id}`

返回评审元数据、变更文件和问题列表，用于历史详情页和报告导出。

### 删除历史

`DELETE /api/reviews/{id}`

删除一条评审及其关联文件、问题。

## 前端行为

- 删除 `loadReviewHistory`、`saveReviewHistory` 以及所有 `localStorage` 读写。
- 应用启动时调用 `GET /api/reviews` 加载历史摘要。
- Git 评审成功后，先调用 LLM，再调用 Java 的保存接口；保存失败时仍展示本次报告，并显示“评审结果未保存”的错误提示。
- 历史记录页点击记录时调用详情接口，不依赖当前页面内存。
- 手动评审也统一调用 LLM，再使用相同保存接口。

## 失败处理

- MySQL 不可用：扫描和 LLM 健康检查仍可工作，保存接口返回 503，前端明确提示历史记录未保存。
- LLM 不可用：不写入 `COMPLETED` 记录；前端展示评审失败状态。
- 保存请求重复提交：使用客户端生成的 `requestId`，数据库唯一约束保证幂等。
- 数据库重启后：所有已完成历史记录仍可查询，当前评审任务不依赖 Java 内存状态。

## 验收标准

1. Docker Compose 能启动 MySQL，并通过健康检查后再启动依赖服务。
2. Java 启动时执行 Flyway 迁移，四张表和索引创建成功。
3. Git 评审不调用 `/api/reviews/from-git`，LLM 结果可以通过 `POST /api/reviews` 保存。
4. 重启 Java、前端和 MySQL 后，历史记录仍可通过 API 查询。
5. 前端代码中不存在 `localStorage` 历史记录读写。
6. 测试覆盖迁移映射、保存、分页查询、详情查询、删除、LLM 成功但保存失败等场景。
