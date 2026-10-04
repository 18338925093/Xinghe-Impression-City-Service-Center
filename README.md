# 星河印象城交易服务中心

基于 Spring Boot 3、MyBatis Plus、Redis、MySQL、RabbitMQ 和 Elasticsearch 的统一交易后台。

## 第一阶段：客服基础流程

已实现客服后端基础闭环，当前采用“固定流程优先”的路由方式，为后续接入 Agent Tool Calling 预留统一服务接口：

- 客服会话和消息写入 MySQL
- `X-User-Id` 请求头校验用户身份和会话归属
- 订单查询只允许访问当前用户自己的订单
- FAQ 固定问答：退款、配送、支付、人工客服
- Redis 保存每个会话最近 20 条上下文，30 分钟自动过期
- SSE 格式客服消息接口（当前返回单条完整回复）
- 商品查询接口和物流查询接口
- 商品和客服规则后续可迁移到 Elasticsearch 知识索引

## 第二阶段：受控 Agent 客服

- 可选接入 OpenAI-compatible Chat Completions；默认关闭，未启用或请求失败时继续走固定 FAQ/规则路由
- Agent 仅能调用订单状态、订单物流和在售商品搜索三个只读工具
- 订单和物流工具在服务端使用当前 `X-User-Id` 校验归属，不接受模型传入用户身份
- 限制每轮工具调用数量；未知工具、超时或模型错误不会执行写操作，并会降级为现有客服流程
- 模型 API 调用不占用数据库写事务
- 启用后，最近会话消息以及必要的查询结果会发送给所配置的模型服务商；默认关闭时不会发送对话内容

可通过环境变量配置（不要把 API Key 写进版本库）：

```powershell
$env:XINGHE_AGENT_ENABLED = "true"
$env:XINGHE_AGENT_API_KEY = "你的模型服务 API Key"
$env:XINGHE_AGENT_BASE_URL = "https://api.openai.com/v1"
$env:XINGHE_AGENT_MODEL = "gpt-4o-mini"
```

`XINGHE_AGENT_BASE_URL` 和 `XINGHE_AGENT_MODEL` 可按所用的 OpenAI-compatible 服务商调整。停用时将 `XINGHE_AGENT_ENABLED` 设为 `false` 或删除该变量。

## 初始化数据库

应用启动时会自动执行 `hd/src/main/resources/db/schema.sql`，创建客服、商品、订单和物流表。请先创建 MySQL 数据库 `xinghe_trade`，再启动后端。

开发环境默认可通过根目录 `.env` 中的 `XINGHE_SEED_ENABLED=true` 写入演示数据：

- 演示用户：`user-1001`
- 演示订单：`XH2026100300000001`
- 演示商品：`10001`
- 演示物流单号：`XH-DEMO-TRACK-001`

关闭演示数据时，将 `XINGHE_SEED_ENABLED` 设置为 `false`。

## 启动依赖

本地启动 MySQL（创建 `xinghe_trade`）、Redis、RabbitMQ 和 Elasticsearch 后运行：

```bash
cd hd
mvn -s maven-settings.xml spring-boot:run
```

本地未启动 RabbitMQ 时，应用默认不会启动订单超时消费者，因此不会反复打印连接失败日志。需要启用 RabbitMQ 订单超时取消功能时，先启动 RabbitMQ，再设置：

```powershell
$env:XINGHE_MESSAGING_ENABLED = "true"
```

## 客服接口

创建会话：

```http
POST /api/customer-service/sessions
X-User-Id: user-1001
```

发送消息（SSE）：

```http
POST /api/customer-service/sessions/{sessionId}/messages
X-User-Id: user-1001
Content-Type: application/json

{"content":"请帮我查询订单 XH202610021234567890 的状态"}
```

查询订单和物流：

```http
GET /api/customer-service/orders/{orderNo}
GET /api/customer-service/orders/{orderNo}/logistics
X-User-Id: user-1001
```

阶段 A 可直接查询演示订单：

```text
XH2026100300000001
```

商品搜索：

```http
GET /api/customer-service/products?keyword=鞋
```

## 前端联调

前端位于 `qd`，后端位于 `hd`。前端需要 Node.js 18 或更高版本。先启动后端及所需依赖，再在 `qd` 目录运行：

```bash
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。Vite 会将 `/api` 请求代理到 `http://localhost:8081`，无需额外配置跨域。页面默认使用联调用户 `user-1001`；客服会话记录列表暂存在当前浏览器中，后端目前没有会话列表接口。

