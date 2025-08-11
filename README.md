### steven-ai（后端服务）

基于 Spring Boot 3 与 Spring AI 的后端服务，提供聊天、PDF 问答、客服与旅游向导 Agent 能力，并内置会话历史与向量化检索（RAG）。

- **框架/依赖**：Spring Boot 3.4.x、Spring AI 1.0.0-M6、MyBatis-Plus、MySQL 8、Reactor
- **模型后端**：
  - 本地：Ollama（如 deepseek-r1:7b）
  - 云端：OpenAI 兼容（阿里云 DashScope）


### 运行要求
- Java 17
- Maven 3.9+
- MySQL 8（或兼容版本）
- 可选：Ollama（默认端口 11434）


### 快速开始
1) 初始化数据库（首次使用）：
```
CREATE DATABASE steven_ai DEFAULT CHARACTER SET utf8mb4;
```

2) 环境变量（至少需要云端模型的 Key，或在本地启用 Ollama）：
```
export OPENAI_API_KEY="<your_dashscope_api_key>"
export AMAP_API_KEY="<your_amap_key>"   # 旅游向导 Agent 可选
```

3) 启动服务（默认端口 8080）：
```
mvn spring-boot:run
```

4) 健康检查：
```
curl -I http://localhost:8080
```


### 配置说明（`src/main/resources/application.yaml`）
- 数据库：默认 `jdbc:mysql://localhost:3307/steven_ai`，用户名 `steven`，口令 `steven123`
- 模型：
  - Ollama：`http://localhost:11434`，对话模型 `deepseek-r1:7b`
  - OpenAI 兼容（DashScope）：`base-url: https://dashscope.aliyuncs.com/compatible-mode`，`api-key: ${OPENAI_API_KEY}`，对话模型 `qwen-max-latest`，向量模型 `text-embedding-v3(1024)`
- 旅游向导工具：高德 Key 来自 `AMAP_API_KEY`（或 `AMAP_MCP_KEY`）
- 日志级别：`org.springframework.ai` 与 `com.steven.ai` 已设为 `debug`

跨域：已在 `MvcConfiguration` 全局放开（允许所有来源/方法/头，并暴露 `Content-Disposition`）。


### API 文档（基础路径：`http://localhost:8080`）
- 聊天（通用/多模态）
  - `POST /ai/chat`（流式）
    - 参数：`prompt`（表单字段）必填，`chatId` 必填，`files` 可选（`multipart/form-data`，支持多文件，多模态）
    - 纯文本时可用 `application/x-www-form-urlencoded`

- 智能客服
  - `GET /ai/service?prompt&chatId`（流式）

- 旅游向导 Agent（含高德地图工具调用）
  - `GET /ai/travel?prompt&chatId`（流式）

- PDF 问答
  - `POST /ai/pdf/upload/{chatId}`（上传 PDF，表单字段名 `file`，仅 `application/pdf`）
  - `GET  /ai/pdf/chat?prompt&chatId`（流式，基于该 `chatId` 已上传的 PDF 与向量检索进行回答）
  - `GET  /ai/pdf/file/{chatId}`（下载此前上传的 PDF）

- 会话历史管理
  - `GET    /ai/history/{type}`：获取指定类型的会话 ID 列表（`type` ∈ `chat` | `pdf` | `service` | `travel`）
  - `GET    /ai/history/{type}/{chatId}`：获取指定会话消息（基于内存记忆）
  - `DELETE /ai/history/{type}/{chatId}`：删除某会话（同时清空内存消息）
  - `DELETE /ai/history/{type}`：清空某类型全部会话


#### 示例
```bash
# 1) 纯文本聊天（流式输出）
curl -N -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "prompt=你好&chatId=demo" \
  http://localhost:8080/ai/chat

# 2) 多模态聊天（携带附件）
curl -N -X POST \
  -F "prompt=请描述图片内容" -F "chatId=multi1" \
  -F "files=@/path/to/image.png" \
  http://localhost:8080/ai/chat

# 3) 上传 PDF（仅 application/pdf）
curl -X POST -F "file=@/path/to/file.pdf" \
  http://localhost:8080/ai/pdf/upload/pdf1

# 4) PDF 问答（流式输出）
curl -N "http://localhost:8080/ai/pdf/chat?prompt=这份文档的主题是什么&chatId=pdf1"

# 5) 下载 PDF
curl -OJL http://localhost:8080/ai/pdf/file/pdf1

# 6) 历史会话
curl http://localhost:8080/ai/history/chat
curl http://localhost:8080/ai/history/chat/demo
curl -X DELETE http://localhost:8080/ai/history/chat/demo
```


### 数据与持久化
- 会话历史与记忆：
  - 历史列表：`chat-history.json`
  - 内存消息：`chat-memory.json`
  - 两者在应用关闭时持久化（`@PreDestroy`），启动时自动加载（`@PostConstruct`）
- PDF 与向量库：
  - 上传文件保存在当前工作目录，文件名取源文件名（存在同名覆盖风险，生产建议替换为安全存储）
  - PDF 向量库：`chat-pdf.json`（`SimpleVectorStore` 保存/加载）
  - `chat-pdf.properties`：维护 `chatId -> 文件名` 的映射

注意：工作目录由运行方式决定（IDE/命令行/容器），为避免文件找不到或冲突，建议在生产环境将上述文件与上传目录配置为固定外部挂载路径。


### 构建与部署
```
mvn -DskipTests clean package
java -jar target/steven-ai-0.0.1-SNAPSHOT.jar
```

生产建议：
- 通过环境变量覆盖数据库与模型 Key；禁用默认弱口令
- 使用反向代理（Nginx）处理 TLS、限流与静态资源
- 将存储文件（会话/向量/PDF）挂载至持久卷
- 细化 CORS 策略，仅允许可信来源


### 常见问题
- 连接数据库失败：检查端口（示例为 3307）、库名、账号口令与网络访问
- 模型不可用：
  - 本地模型：确认 Ollama 运行、模型已拉取、端口 11434 可达
  - 云端模型：确认 `OPENAI_API_KEY` 生效、DashScope 账号有配额
- PDF 问答无结果：确认已上传对应 `chatId` 的 PDF；向量库已写入并加载
- CORS：开发阶段已放开，生产请按域名收敛


### 许可证
本模块未单独附带许可证文件。如仓库根目录后续添加 `LICENSE`，以其为准。


