# CodeOps Copilot Java Backend

Java 服务负责本机 Git 仓库扫描，以及将 Python LangChain 服务返回的评审结果保存到 MySQL。大模型推理由 `llm-backend` 完成，Java 不再执行评审推理。

## API

```text
POST   /api/repositories/scan
POST   /api/reviews
GET    /api/reviews?limit=20&offset=0
GET    /api/reviews/{id}
DELETE /api/reviews/{id}
```

`POST /api/reviews` 是持久化接口：前端先调用 `POST /api/ai/review` 获取 findings，再把评审元数据、补丁和 findings 提交到 Java。该接口不会再次调用 LLM。

## 数据库

服务使用 MySQL 8 和 Flyway。启动时会自动执行 `V1__create_review_history.sql`，创建：

- `projects`
- `reviews`
- `review_files`
- `review_findings`

Docker 默认连接：`127.0.0.1:3307/codeops`（容器内部端口仍为 `3306`），可通过 `MYSQL_PORT` 或 `SPRING_DATASOURCE_URL` 覆盖。

## 启动

推荐从项目根目录启动 MySQL、LLM、前端和本机 Java：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local-stack.ps1
```

也可以单独运行 Java，但需要先启动 MySQL：

```powershell
mvn spring-boot:run
```

Java 直接运行在 Windows 宿主机，因此 Git 扫描支持本机任意现有仓库路径。

## 测试

```powershell
mvn test
```
