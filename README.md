# Travelist — AI 旅游行程规划平台

前后端分离的旅游规划应用:AI 聊天助手 + 行程规划 + 热门景点浏览,LLM 能力全部由后端提供,支持 Docker 一键部署以固定运行环境。

## ✨ 项目特点

- **AI 聊天助手(后端驱动)**:后端调用 OpenAI 兼容 LLM 接口,SSE 流式逐字输出;前端保留打字机节奏播放、流式中可继续发送(排队)、一键停止、Markdown 渲染(含代码高亮、DOMPurify 防 XSS)。
- **景点上下文对话**:首页热门景点仅展示"简介 + 标签"(不显示价格),点击查看详情页;详情页提供「问 AI 这个景点」入口,一键将该景点作为 AI 聊天的上下文(可移除)。
- **行程规划**:起始城市 + 目的地、单程/往返、出发/返程日期(往返自动算天数)、预算、行程要求 → 后端 `TravelService` 调用 LLM 生成完整规划:每日上午/下午/晚间行程、预算构成、**简易交通流程(去程/市内/往返含返程路线)**、实用建议与注意事项;结果同页展示,可展开/收起、一键重新规划。
- **密钥安全**:API Key 不落仓库,从系统环境变量 `LLM_API_KEY` 读取(通用命名,不绑定厂商);Docker 部署时自动透传。
- **任意 OpenAI 兼容厂商**:`llm.base-url` / `llm.model` 可切换 DeepSeek、OpenAI、Kimi、智谱、本地 Ollama 等,支持 `responses` / `chat` 两种调用风格。
- **固定环境部署**:前后端各自容器化(JDK 26 JRE + Nginx stable),`docker compose` 一键起停。

## 🏗️ 技术栈

| 端   | 技术                                                                               |
|------|------------------------------------------------------------------------------------|
| 前端 | Vue 3 · Vant 4 · Vue Router · marked + highlight.js + dompurify · Vite             |
| 后端 | Spring Boot 4.1 · Java 26 · Lombok · Jakarta Validation · JDK HttpClient(LLM 调用) |
| 部署 | Docker + Docker Compose(eclipse-temurin:26-jre / nginx:stable-alpine)              |

## 📁 目录结构

```
Travelist/
├─ travelist-backend/
│  └─ travelist/                  # Spring Boot 工程
│     ├─ src/main/java/com/travelist/
│     │  ├─ controller/           # TravelController(规划) ChatController(SSE 聊天) SpotController(景点)
│     │  ├─ service/              # TravelService · ChatService · SpotService(静态数据,后续可换数据库)
│     │  ├─ Util/                 # LLMUtil(responses/chat 两种风格,流式+非流式)
│     │  ├─ entity/ · validation/ · common/ · config/
│     │  └─ resources/application.yaml
│     ├─ Dockerfile
│     └─ maven-proxy-settings.xml # 本机走 Clash(含 fake-ip)时 Maven 的代理设置(可选)
├─ travelist-frontend/            # Vue 3 工程
│  ├─ src/views/                  # HomeView · PlanView · AiChatView · SpotDetailView
│  ├─ src/utils/ai.js             # 后端 SSE 聊天客户端
│  ├─ Dockerfile · nginx.conf     # Nginx 静态托管 + /api 反向代理(SSE 透传)
├─ docker-compose.yml             # 前后端分离部署
└─ README.md
```

## 🔌 接口一览

统一响应格式:`{ "success": bool, "code": int, "message": string, "data": ... }`

| 接口                         | 说明                                                                                            |
|------------------------------|-------------------------------------------------------------------------------------------------|
| `GET /api/spot/list`         | 热门景点列表(名称/标签/简介)                                                                    |
| `GET /api/spot/{id}`         | 景点详情(无价格字段)                                                                            |
| `POST /api/travel/recommend` | 行程规划(`originCity/city/days/budget` 必填,`tripType/departDate/returnDate/requirements` 可选) |
| `POST /api/ai/chat`          | AI 聊天,SSE 流式(`{messages:[{role,content}], spotId?}`;帧:`{delta}`/`{error}`/`{done}`)        |

## 🚀 本地开发

### 环境要求

- JDK 26
- Node.js 20+
- Docker(仅部署需要)

### 1. 配置环境变量(API Key,必需)

```powershell
setx LLM_API_KEY sk-你的密钥   # 新开终端生效;当前终端需手动 set $env:LLM_API_KEY
```

默认deepseek，未配置时后端仍可启动,但 AI 聊天/规划会返回明确错误提示。

### 2. 启动后端(端口 5222)

```powershell
cd travelist-backend/travelist
$env:JAVA_HOME = '你的jdk路径'   # 指向 JDK 26
# 若本机通过 Clash/V2Ray 代理上网(application.yaml 已配置 llm.proxy-host/port),Maven 需带代理设置:
.\mvnw.cmd -s ..\maven-proxy-settings.xml spring-boot:run
# 无代理环境直接: .\mvnw.cmd spring-boot:run
```

### 3. 启动前端(端口 5215)

```powershell
cd travelist-frontend
npm install
npm run dev
```

Vite 已配置 `/api → http://127.0.0.1:5222` 代理,直接访问 `http://127.0.0.1:5215`。

## 🐳 Docker 部署(前后端分离,推荐)

### 1. 构建产物(宿主机执行)

```powershell
# 后端 jar(代理环境加 -s ..\maven-proxy-settings.xml)
cd travelist-backend/travelist
$env:JAVA_HOME = 'C:\Users\你的用户名\.jdks\openjdk-26.0.1'
.\mvnw.cmd -s ..\maven-proxy-settings.xml -DskipTests package

# 前端 dist
cd travelist-frontend
npm run build
```

### 2. 启动容器

```powershell
cd Travelist
docker compose up -d --build
```

| 服务                 | 访问地址                | 说明                                                      |
|----------------------|-------------------------|-----------------------------------------------------------|
| `travelist-frontend` | `http://127.0.0.1:5215` | Nginx 静态托管 + `/api` 反代后端(SSE 已关缓冲、超时 300s) |
| `travelist-backend`  | `http://127.0.0.1:5222` | Spring Boot 直连;`LLM_API_KEY` 从宿主机环境变量透传       |

### 3. 常用命令

```powershell
docker compose ps                # 状态
docker compose logs -f backend   # 后端日志
docker compose restart backend   # 重启后端
docker compose down -v           # 停止并移除容器
docker compose up -d --build     # 重新打包容器
```

### LLM 出网说明

容器内无法使用宿主机代理地址,compose 默认通过 `LLM_PROXYHOST=host.docker.internal` 走宿主机代理(默认端口 7892,可用 `LLM_PROXY_PORT` 调整),并给容器配了公共 DNS(223.5.5.5)避免宿主机代理 fake-ip 污染解析。若直连也可用:在 `docker-compose.yml` 中将 `LLM_PROXYHOST` 置空并保留公共 DNS 即可。

## ⚙️ 配置说明(application.yaml)

```yaml
llm:
  base-url: https://api.deepseek.com   # OpenAI 兼容接口地址(任意厂商可换)
  model: deepseek-v4-flash-vision-exp  # 模型名
  api-key: ${LLM_API_KEY:}             # 从系统环境变量 LLM_API_KEY 读取(通用命名)
  api-style: responses                 # responses | chat
  proxy-host:                          # 本机代理主机;容器部署时由 compose 覆盖
  proxy-port:                          # 本机代理端口;容器部署时由 compose 覆盖
```

## ⚠️ 已知限制

- 景点数据为后端内置静态数据(`SpotService`),后续可平滑替换为数据库实现;
- 无用户体系/鉴权,聊天记录不落库,Access Key 仅后端可见;
- AI 生成内容受模型输出影响,金额、时长等以实际为准。
