# 项目策略与 Git Hook 评审设计

## 目标

让用户在前端引入本机 Git 项目，并在数据库中配置代码评审触发时机。评审工作台可选择已引入项目；本机所有启用 CodeOps 全局 Hook 的仓库在执行 Git 操作时，根据数据库策略决定是否触发评审。

## 约束

- 项目路径必须由 Java 在 Windows 宿主机校验，支持任意本机 Git 仓库。
- 项目策略和项目元数据存储在 MySQL，不使用项目内配置文件或浏览器 `localStorage`。
- LLM 评审由 Python LangChain 服务 `8090` 完成。
- Java `8080` 负责项目管理、策略解析、Git 扫描、评审持久化和历史查询。
- 未引入项目或已禁用项目不得阻断 Git 操作。
- `pre-push` 可阻断推送；`pre-commit` 可阻止提交；`post-merge` 只能产生评审，不能阻止合并。

## 数据模型

在现有 `projects` 表基础上新增一对一 `review_policies` 表。策略包含总开关、`pre-commit`、`pre-push`、`post-merge` 开关、阻断严重级别和服务异常时是否放行。

项目通过规范化仓库根路径唯一识别。项目引入接口会调用 Git 根目录解析，写入或更新项目元数据，并创建默认策略。

## API

- `POST /api/projects/import`：校验并引入本机 Git 项目。
- `GET /api/projects`：返回项目及策略，供项目页和工作台选择。
- `GET /api/projects/{id}`：返回项目详情。
- `PUT /api/projects/{id}/policy`：更新策略。
- `DELETE /api/projects/{id}`：删除项目及策略和关联历史。
- `GET /api/projects/policy/resolve?repositoryPath=...`：供 Hook 根据仓库根路径查询策略；未找到时返回 `enabled=false`。

## 工作台

工作台显示已引入项目下拉框。选择项目后自动填充 Git 路径，扫描和保存评审都带上项目路径；未选择项目时保留手动路径能力，但不自动创建项目。项目页提供引入、选择和策略编辑。

## Hook 数据流

全局 Hook 只安装一次，脚本通过 `git rev-parse --show-toplevel` 获取仓库根路径，然后调用 Java 策略解析接口。策略允许后，脚本读取当前暂存区、待推送提交或合并后的变更，调用 8090 LLM 并保存历史。策略阈值以上的问题返回非零退出码。

默认策略为：启用项目、关闭 `pre-commit`、启用 `pre-push`、关闭 `post-merge`、`HIGH` 阻断、LLM 异常不放行。

## 错误处理

- Java 不可用时，Hook 默认 fail-closed；项目策略无法解析时按未启用处理，避免未引入项目被误阻断。
- LLM 不可用时依据已解析策略的 `failOpen` 决定退出码。
- Hook 评审结果保存失败时视为评审失败，并遵循 `failOpen`。
- Hook 不能阻止 `post-merge`，但必须输出错误并尽力保存结果。

## 验证

- Java 测试覆盖项目引入、列表、策略更新、策略解析和删除级联。
- 前端测试覆盖项目 API、工作台项目选择和策略表单。
- PowerShell 测试覆盖策略阈值和三种 Hook 的触发分支。
- 真实服务验证项目引入、前端选择、策略修改和 Hook 查询。
