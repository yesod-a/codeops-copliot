# Prompt 管理与二次审查设计

## 目标

为 LLM 后端增加 OCR 风格的提示词模板管理和保守二次审查，使主评审产生的结构化 findings 在返回前经过可追溯的事实核查，同时不因过滤器异常而丢失结论。

## 范围

修改范围限定在 `llm-backend`：评审 Prompt、计划 Prompt、二次审查 Prompt、模板加载器、`AiReviewer` 调用链及其单元测试。HTTP API、Java 后端、前端、Git Hook 的请求和响应结构保持不变。

## 架构

`app/prompt_templates/` 存放 UTF-8 Markdown 模板，按任务区分 system 和 user 消息。`app/prompts.py` 保留公开的 `build_messages`、`build_plan_messages`，新增 `build_filter_messages`，但不再持有大段提示词；它通过一个模板注册表加载、缓存和渲染模板。

一个评审组仍只执行一次主评审。主评审结果先经现有 `normalize_findings` 验证路径、行号和枚举值，再送给二次审查。过滤器只返回需要删除的输入索引，默认返回空列表。代码再额外保护敏感类别，过滤器无论如何都不能删除安全、并发、兼容性、未使用参数和内存安全问题。

## 数据流

```text
ReviewFile group
  -> Plan（仅大变更）
  -> MAIN_REVIEW Prompt / 工具循环
  -> JSON 解析与 normalize_findings
  -> REVIEW_FILTER Prompt（当前组 diff + 已规范化 findings）
  -> 删除明确错误且非保护类别的索引
  -> 组结果合并并返回
```

过滤器调用不绑定仓库读取工具。它只基于同组 Diff 和评论本身判断，避免将主评审已经获得但过滤器不可复核的上下文误判为错误。

## Prompt 约束

主评审模板保持当前 JSON finding 契约，并新增 OCR 风格约束：只评论输入文件、删除行仅作背景、证据不足不报告、上下文不足时可调用仓库工具。

过滤器模板要求输出 `{"remove_indices": []}`。仅在下列情况删除：评论指向的文件或代码不在输入 Diff，或 Diff 中存在直接反驳该评论中心结论的文本。不可根据“低价值”“无法验证”“不同意建议”删除；保护类别始终保留。

## 失败策略与超时

二次审查模型请求复用已有模型实例和工具循环的直接 `invoke` 路径，但不附加工具。过滤器响应为空、非 JSON、索引非法、模型异常时，记录为无删除并返回全部主评审 findings。过滤器不改变主评审的失败语义：主评审 JSON 非法仍然作为评审失败上抛。

## 测试

测试须覆盖模板加载和变量渲染、主评审后过滤器的调用顺序、合法索引删除、保护类别不删除、过滤器无效输出或异常时保留全部 findings。既有大变更 Plan、工具调用和分组测试需继续通过。
