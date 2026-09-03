# CodeOps LLM Backend

这是 CodeOps Copilot 的 Python LangChain 评审服务。Java 后端负责本地 Git 扫描、任务状态和确定性规则，本服务负责调用 OpenAI 兼容的大模型并返回结构化评审意见。

## 环境要求

需要 Python 3.10 或更高版本。当前代码使用了 Python 3.10 类型语法，Python 3.8 环境无法安装或运行本服务。

推荐单独创建 Conda 环境，避免修改已有的 `langchain` 环境：

```powershell
conda create -n codeops-ai python=3.10 -y
conda activate codeops-ai
```

## 配置

复制 `.env.example` 为 `.env`，填写模型服务配置：

```text
AI_ENABLED=true
AI_API_KEY=你的模型服务密钥
AI_BASE_URL=https://api.openai.com/v1
AI_MODEL=gpt-4o-mini
```

也可以直接使用环境变量。`.env` 不会提交到 Git。

## 启动

```powershell
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --host 127.0.0.1 --port 8090
```

也可以从项目根目录使用 Docker Compose 一次启动三个服务。先复制根目录 `.env.example` 为 `.env` 并填写 `AI_API_KEY`，然后执行：

```powershell
docker compose up --build
```

容器辅助部署时，前端地址为 `http://localhost:5173`，Python 服务运行在 Docker 中并暴露到宿主机 `8090`；Java 在宿主机运行并通过 `http://127.0.0.1:8090` 调用它。

健康检查：`http://127.0.0.1:8090/api/ai/health`。通过 Vite 前端开发服务器访问时，对应地址是 `http://localhost:5173/api/ai/health`，该路径会被转发到 8090，不经过 Java。

启动顺序建议为：配置并启动本服务，启动 Java 后端并设置 `AI_BACKEND_ENABLED=true`，最后启动前端。Java 后端默认通过 `http://127.0.0.1:8090` 调用本服务。

当 `AI_ENABLED=false` 或 Java 的 `AI_BACKEND_ENABLED=false` 时，评审不会调用大模型。启用 Java 调用但 Python 服务不可用时，评审任务会进入 `FAILED`，不会伪造 AI 结果。
