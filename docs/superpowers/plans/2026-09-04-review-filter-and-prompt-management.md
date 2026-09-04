# Prompt 管理与二次审查 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 LLM 评审提示词移到独立模板文件，并在每个评审组主评审后增加保守的事实过滤阶段。

**Architecture:** `app/prompts.py` 使用文件模板注册表生成 LangChain messages，`AiReviewer.review` 在 findings 规范化后调用无工具过滤器。过滤器只能按索引删除明确错误的非保护 finding，任何过滤失败都返回原结果。

**Tech Stack:** Python 3.10、LangChain Core、Pydantic、pytest。

**Spec:** `docs/superpowers/specs/2026-09-04-review-filter-and-prompt-management-design.md`

## Global Constraints

- 保持现有 `/api/ai/review` 输入和 `ReviewResponse` 输出兼容。
- 所有模板文件使用 UTF-8 编码；自然语言输出保持中文。
- 过滤器失败、超时、响应格式异常或索引异常时必须保留全部主评审 finding。
- 过滤器不可以删除 SECURITY、CONCURRENCY、MEMORY_SAFETY、COMPATIBILITY、UNUSED_PARAMETER 类别的 finding。

---

### Task 1: 模板注册表与主评审模板迁移

**Files:**
- Create: `llm-backend/app/prompt_templates/main_review_system.md`
- Create: `llm-backend/app/prompt_templates/main_review_user.md`
- Create: `llm-backend/app/prompt_templates/plan_system.md`
- Create: `llm-backend/app/prompt_templates/plan_user.md`
- Modify: `llm-backend/app/prompts.py`
- Test: `llm-backend/tests/test_prompts.py`

**Interfaces:**
- Produces: `TemplateRegistry.render(name: str, **values: str) -> str`。
- Produces: `build_messages(...)` 和 `build_plan_messages(...)` 现有签名不变。

- [ ] **Step 1: 写入模板渲染失败测试**

```python
def test_prompt_builders_render_external_templates():
    messages = build_messages("repo", "title", [])
    content = "\n".join(message.content for message in messages)
    assert "仓库：repo" in content
    assert "只返回一个 JSON 对象" in content
```

- [ ] **Step 2: 运行测试确认失败**

Run: `pytest llm-backend/tests/test_prompts.py -v`
Expected: FAIL，因为外部模板注册表尚不存在。

- [ ] **Step 3: 实现注册表和模板文件**

```python
class TemplateRegistry:
    def render(self, name: str, **values: str) -> str:
        return self._load(name).format(**values)
```

将现有主评审和 Plan 的 system/human 字符串逐字迁移到 Markdown 文件；使用具名占位符并通过 `ChatPromptTemplate.from_messages` 构造消息。

- [ ] **Step 4: 运行模板测试**

Run: `pytest llm-backend/tests/test_prompts.py -v`
Expected: PASS。

### Task 2: 二次审查 Prompt 与解析边界

**Files:**
- Create: `llm-backend/app/prompt_templates/review_filter_system.md`
- Create: `llm-backend/app/prompt_templates/review_filter_user.md`
- Modify: `llm-backend/app/prompts.py`
- Test: `llm-backend/tests/test_reviewer.py`

**Interfaces:**
- Produces: `build_filter_messages(repository: str, title: str, files: list[dict], findings: list[ReviewFinding]) -> list`。
- Produces: `AiReviewer._parse_filter_response(content: Any, finding_count: int) -> set[int]`。

- [ ] **Step 1: 写入过滤器 JSON 解析失败测试**

```python
def test_filter_response_ignores_invalid_indices_and_returns_valid_ones():
    assert AiReviewer._parse_filter_response('{"remove_indices":[0, 4, -1]}', 2) == {0}
```

- [ ] **Step 2: 运行定向测试确认失败**

Run: `pytest llm-backend/tests/test_reviewer.py::test_filter_response_ignores_invalid_indices_and_returns_valid_ones -v`
Expected: FAIL，因为过滤器解析器尚不存在。

- [ ] **Step 3: 创建过滤 Prompt 并实现解析器**

过滤 Prompt 仅允许 JSON `remove_indices`，并明确默认保留、仅允许直接证据删除。解析器去除代码围栏，只接受整数索引，忽略越界与非法项。

- [ ] **Step 4: 运行定向测试**

Run: `pytest llm-backend/tests/test_reviewer.py::test_filter_response_ignores_invalid_indices_and_returns_valid_ones -v`
Expected: PASS。

### Task 3: 将二次审查接入评审组执行流程

**Files:**
- Modify: `llm-backend/app/reviewer.py`
- Test: `llm-backend/tests/test_reviewer.py`

**Interfaces:**
- Produces: `AiReviewer._filter_findings(repository, title, files, findings) -> list[ReviewFinding]`。
- Consumes: `build_filter_messages`，且不使用 `bind_tools` 或 `_invoke_with_tools`。

- [ ] **Step 1: 写入主评审后删除低风险 finding 的测试**

```python
def test_reviewer_filters_only_requested_low_risk_findings():
    model = SequenceModel([main_json, '{"remove_indices":[0]}'])
    findings = reviewer.review("D:/repo", "title", files)
    assert [finding.message for finding in findings] == ["第二条"]
```

- [ ] **Step 2: 运行定向测试确认失败**

Run: `pytest llm-backend/tests/test_reviewer.py::test_reviewer_filters_only_requested_low_risk_findings -v`
Expected: FAIL，因为 review 尚未调用过滤器。

- [ ] **Step 3: 实现保守过滤调用**

在 `normalize_findings` 后调用 `_filter_findings`。仅在 findings 非空时发请求；模型异常、内容非 JSON 或空响应时直接返回原 findings。定义保护类别集合，在删除前检查 finding.category。

- [ ] **Step 4: 写入保护与失败回退测试**

```python
def test_filter_keeps_protected_category_when_model_requests_removal():
    assert reviewer.review(...)[0].category == "SECURITY"

def test_filter_failure_keeps_all_main_review_findings():
    assert reviewer.review(...) == expected_findings
```

- [ ] **Step 5: 运行评审器测试**

Run: `pytest llm-backend/tests/test_reviewer.py -v`
Expected: PASS。

### Task 4: 回归验证和文档

**Files:**
- Modify: `llm-backend/README.md`
- Test: `llm-backend/tests/test_api.py`
- Test: `llm-backend/tests/test_context.py`
- Test: `llm-backend/tests/test_findings.py`
- Test: `llm-backend/tests/test_rules.py`
- Test: `llm-backend/tests/test_tools.py`

**Interfaces:**
- Consumes: 所有既有评审 API 和工具契约。
- Produces: README 对 Prompt 管理和二次审查的简短行为说明。

- [ ] **Step 1: 更新 README 的能力说明**

加入说明：Prompt 模板位于 `app/prompt_templates`；主评审结果会经过保守事实过滤；过滤异常不会阻断或删除主评审结果。

- [ ] **Step 2: 运行全部后端测试**

Run: `pytest llm-backend/tests -v`
Expected: PASS。

- [ ] **Step 3: 检查工作区改动范围**

Run: `git diff --check` and `git status --short`
Expected: 无空白错误；仅本任务涉及的后端、测试、文档文件由本次改动产生，已有前端改动保持不变。
