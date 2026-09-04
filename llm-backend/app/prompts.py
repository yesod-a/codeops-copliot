import json

from langchain_core.prompts import ChatPromptTemplate

REVIEW_PROMPT = ChatPromptTemplate.from_messages([
    (
        "system",
        """你是一名资深 Java 后端代码评审专家。请只分析用户提供的代码变更，不要臆测未提供的上下文。
你必须只返回一个 JSON 对象，格式为 {{\"findings\": [...]}}，不要使用 Markdown 代码块，不要添加解释文字。
每个 finding 必须包含 category、severity、file、line、message、suggestion、evidence、confidence。
severity 只能是 CRITICAL、HIGH、MEDIUM、LOW，confidence 必须是 0 到 1 之间的数字。
没有问题时返回 {{\"findings\": []}}。所有自然语言字段使用中文，file 必须使用输入中的相对路径。""",
    ),
    (
        "human",
        "仓库：{repository}\n评审标题：{title}\n代码变更：\n{files_json}",
    ),
])

PLAN_PROMPT = ChatPromptTemplate.from_messages([
    (
        "system",
        "你是代码评审规划器。根据变更文件列表和仓库上下文，输出简短的中文评审计划，"
        "列出优先级、跨文件关系和需要重点读取的文件。只输出计划文本。",
    ),
    ("human", "仓库：{repository}\n标题：{title}\n变更文件：{files_json}"),
])


def build_messages(
    repository: str,
    title: str,
    files: list[dict],
    rule: str = "",
    context: str = "",
    plan: str = "",
):
    review_context = context or "未读取到完整文件内容；请仅基于 diff 判断。"
    review_rule = rule or "只报告能够从变更中验证的问题，不要为了凑数量而猜测。"
    review_plan = plan or "小变更：逐行检查变更及其直接上下文。"
    return REVIEW_PROMPT.format_messages(
        repository=repository,
        title=title,
        files_json=json.dumps({
            "rule": review_rule,
            "评审计划": review_plan,
            "files": files,
            "context": review_context,
        }, ensure_ascii=False, indent=2),
    )


def build_plan_messages(repository: str, title: str, files: list[dict]):
    return PLAN_PROMPT.format_messages(
        repository=repository,
        title=title,
        files_json=json.dumps(
            [{"path": file.get("path"), "size": len(file.get("content", "") or "")} for file in files],
            ensure_ascii=False,
            indent=2,
        ),
    )
