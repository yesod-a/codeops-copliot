# 评审规则中心第一阶段设计

## 1. 目标与范围

第一阶段面向单机、本地部署的 CodeOps 实例，交付可配置、可预览、可追溯的规则中心。本机操作者可以在前端维护全局规则和项目规则，选择本机已导入项目与文件路径后看到该文件最终会被送给评审 Agent 的规则集。

规则的唯一权威来源是 Java 服务和 MySQL。前端不在浏览器内解析规则，Python LLM 服务不自行读取前端配置或仓库内规则文件。这样工作台、全局 Git Hook 和历史评审使用同一套解析结果。

本阶段不引入用户、管理员、组织、租户或角色权限。`GLOBAL` 表示“当前 CodeOps 安装实例内，对所有本机已导入项目生效”，不是跨用户或跨组织的中央规则。

第一阶段包含：规则 CRUD、全局/项目规则页面、规则预览、路径 Glob 校验、按文件规则解析和规则快照数据模型。暂不包含审批流、临时豁免、规则版本回滚、自然语言生成规则和集中式规则下发。

## 2. 规则层级

每个文件的有效规则固定按以下顺序叠加：

```text
平台硬约束
  -> 内置文件类型规则
  -> 匹配的全局规则
  -> 匹配的项目规则
  -> 当前任务的需求背景、Plan 与 Diff
```

平台硬约束存在于 Python 主评审和二次审查的 system Prompt 中，不能被前端规则关闭。它负责 JSON 输出契约、只评论输入变更、证据不足不报告、二次审查保守删除等 Agent 行为。

内置文件类型规则由后端维护，参考 OCR 的 `system_rules.json`：根据仓库相对路径按 Glob 选择 Java、MyBatis XML、pom.xml、Vue/TypeScript、YAML 等规则包。它不是简单的后缀判断，例如 `**/*{mapper,dao}*.xml` 必须先于普通 XML，`**/pom.xml` 必须先于通用文件规则。规则按声明顺序，首个命中的内置规则生效；无命中时使用通用规则。

全局和项目规则均为叠加规则：同一文件可命中多条。项目规则只能增强或补充全局规则，不能通过“覆盖模式”删除已经命中的全局规则。这个选择与 OCR 的默认覆盖策略不同，可避免本机配置出现难以理解的规则失效。

## 3. 数据模型

新增 `review_rules` 表。全局规则以 `scope=GLOBAL`、`project_id=NULL` 存储；项目规则以 `scope=PROJECT`、`project_id` 指向现有 `projects.id` 存储。

| 列 | 类型/约束 | 含义 |
|---|---|---|
| `id` | bigint PK | 规则标识 |
| `scope` | varchar(20) | `GLOBAL` 或 `PROJECT` |
| `project_id` | bigint nullable FK | 仅项目规则填写 |
| `name` | varchar(120) | 用户可读的规则名 |
| `category` | varchar(30) | `SECURITY`、`CORRECTNESS`、`CONCURRENCY`、`DATABASE`、`TEST`、`ARCHITECTURE`、`QUALITY` |
| `path_pattern` | varchar(255) | 仓库根目录相对 Glob；`**` 表示全部文件 |
| `content` | text | 发给 Agent 的具体检查要求 |
| `priority` | int | 同层匹配规则由小到大排序，默认 100 |
| `enabled` | boolean | 是否参与解析 |
| `version` | int | 每次内容或解析相关字段变更加一 |
| `created_at` / `updated_at` | datetime | 审计基础字段 |

服务层校验：`GLOBAL` 规则必须没有 `project_id`；`PROJECT` 规则必须有合法本机项目；名称非空且不超过 120 字符；正文非空；Glob 长度不超过 255，必须可被 doublestar 兼容语义解析。

规则正文是评审指令，不承载执行开关。`pre_push_enabled`、`fail_on_severity`、`fail_open` 等确定性策略继续保存在现有 `review_policies`，不混入 `review_rules`。

## 4. 解析算法

输入为项目 ID、仓库根路径和本次待评审的相对文件路径集合。Java `RuleResolutionService` 对每个文件独立生成 `EffectiveFileRule`：

1. 规范化路径为 `/` 分隔的仓库相对路径，拒绝绝对路径、空路径和 `..` 逃逸。
2. 用内置规则映射解析一个文件类型规则包。
3. 查询所有启用的全局规则，保留 path pattern 命中的规则。
4. 查询该项目的所有启用项目规则，保留 path pattern 命中的规则。
5. 各层均按 `priority ASC, id ASC` 排序；层之间固定为 `BUILTIN -> GLOBAL -> PROJECT`。
6. 生成稳定 JSON，并以 `SHA-256` 计算 `effectiveRuleHash`。哈希输入包含内置规则版本、每条规则 ID、版本、范围、路径模式、正文和排序位置。

规则不会被“全量塞入 Prompt”。所有规则可存在数据库和服务缓存中，但只对本次评审组文件解析命中的项。将相同有效规则列表的文件按 hash 合并，减少 Prompt 重复；多个不同列表必须以 `for="file paths"` 标记分别注入。

示例：`backend/src/UserService.java` 命中 Java 内置规则、全局事务规则和项目业务规则；`frontend/src/App.vue` 仅命中 Vue 内置规则和项目 Web 规则。两者进入同一评审组时，Agent 会收到两个显式绑定的规则块，而不是一个混合规则段。

## 5. 接口契约

规则管理接口由 Java `/api` 提供：

```text
GET    /api/rules/global
POST   /api/rules/global
PUT    /api/rules/global/{ruleId}
DELETE /api/rules/global/{ruleId}

GET    /api/projects/{projectId}/rules
POST   /api/projects/{projectId}/rules
PUT    /api/projects/{projectId}/rules/{ruleId}
DELETE /api/projects/{projectId}/rules/{ruleId}

POST   /api/projects/{projectId}/rules/preview
```

创建和修改使用同一个请求体：

```json
{
  "name": "订单状态流转",
  "category": "SECURITY",
  "pathPattern": "backend/src/**",
  "content": "订单状态变更必须校验允许的前置状态，并确保失败时不会写入部分数据。",
  "priority": 200,
  "enabled": true
}
```

预览接口请求：

```json
{ "paths": ["backend/src/UserService.java", "frontend/src/App.vue"] }
```

预览响应按文件返回解析详情：

```json
{
  "files": [
    {
      "path": "backend/src/UserService.java",
      "effectiveRuleHash": "...",
      "rules": [
        {"source":"BUILTIN","name":"Java","pattern":"**/*.java","version":"builtin-1","content":"..."},
        {"source":"GLOBAL","id":12,"name":"事务完整性","pattern":"**/*.java","version":3,"content":"..."},
        {"source":"PROJECT","id":28,"name":"订单状态流转","pattern":"backend/src/**","version":1,"content":"..."}
      ],
      "renderedPromptFragment": "<rules for=...>...</rules>"
    }
  ]
}
```

Python 的 `/api/ai/review` 在第二阶段接收 `projectId` 与 Java 已解析的 `effectiveRulesByFile`，并将其作为唯一的用户规则输入。正常调用链由前端和 Hook 先调用 Java 预览/解析接口再请求 Python；长期目标是由 Java 编排 scan、解析、Python 评审和历史保存，避免客户端转发。

## 6. 前端设计

新增 hash 路由 `#rules`，页面使用三个页签：`全局规则`、`项目规则`、`规则预览`。

### 全局规则

本机操作者可看到规则表格：名称、分类、适用路径、优先级、启用状态、版本、更新时间和操作。表格支持按分类、启用状态和路径搜索。新建、编辑在右侧抽屉中完成。

所有本机配置的全局规则均可在本机启停、编辑和删除；内置平台硬约束不出现在可编辑列表中。第一阶段不做身份校验或授权边界。

### 项目规则

先选择已导入项目。页面分上下两段：上段“继承规则”只读，列出该项目当前启用的全局规则；下段“项目专有规则”允许新建、编辑、停用与删除。项目规则表格不出现“覆盖全局”开关。

编辑器字段为名称、分类、路径 Glob、正文、优先级和启用状态。路径字段提供常用模板：`**`、`**/*.java`、`backend/**`、`frontend/**`；输入时仅做格式校验，不根据机器文件系统猜测路径。

### 规则预览

选择项目，输入一个或多个仓库相对路径，调用 preview 接口。每个文件展示命中链、来源、匹配模式、规则版本、正文摘要、最终 hash 与 Prompt Token 估算。此页是排查“规则未命中、规则过宽、规则冲突”的唯一真相来源。

## 7. 评审历史与审计

扩展 `reviews`，新增 `effective_rule_hash`；新增 `review_rule_snapshots`，记录一次评审内真正使用的规则快照：`review_id`、`file_path`、`source`、`rule_id nullable`、`rule_version`、`pattern`、`content_hash`、`content`、`order_index`。

历史详情页展示“本次评审使用的规则”折叠区，按文件显示，读取 snapshot 而非当前规则。规则更新不会改变既有历史任务的解释结果。

## 8. 验收标准

1. 可创建、更新、启停和删除本机全局规则与项目规则；项目规则不能覆盖已命中的全局规则。
2. 相同路径、同一项目下的 preview 响应顺序和 hash 稳定。
3. 规则预览只返回当前路径命中的内置、全局和项目规则，不返回无关规则。
4. 具体路径规则与通用路径规则可以同时命中，并按照固定层级和优先级显示。
5. 无效 Glob、项目不存在、路径越界和项目规则跨项目编辑均返回可理解的 4xx 错误。
6. 评审历史持久化后，能显示评审当时的规则快照，即使当前规则后来已经更新或删除。

## 9. 后续阶段

第二阶段将 Java 规则解析结果接入 Python 的主评审、Plan 和二次审查 Prompt，并让本机 Git Hook 使用同一解析接口。第三阶段再引入版本回滚、规则命中统计和低质量规则治理；只有产品演进为中心化团队服务时，才评估用户、角色权限、组织规则、审批、临时豁免和租户隔离。
