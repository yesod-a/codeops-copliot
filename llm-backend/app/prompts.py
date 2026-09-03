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


def build_messages(repository: str, title: str, files: list[dict]):
    return REVIEW_PROMPT.format_messages(
        repository=repository,
        title=title,
        files_json=json.dumps(files, ensure_ascii=False, indent=2),
    )
