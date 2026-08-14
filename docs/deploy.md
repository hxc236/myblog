# 部署与发布检查（Render，新加坡区域）

本仓库一次发布两个服务（#5「11. Render 部署」）：

| 服务 | 类型 | 根目录 | 说明 |
| --- | --- | --- | --- |
| `myblog-api` | Web Service（Docker，Java 11） | `backend/` | 隔离公开站点 API，无需数据库 |
| `myblog-frontend` | Static Site（Vue 生产构建） | `myblogweb/` | SPA，全部非静态路径重写到 `index.html` |

## 1. 本地发布检查（上线前必须全部通过）

```bash
# 后端：测试 + 打包（Java 11，默认制品即公开站点应用）
cd backend
mvn test
mvn package
ls target/backend-0.0.1-SNAPSHOT.jar          # 公开站点应用（生产入口）
ls target/backend-0.0.1-SNAPSHOT-legacy.jar   # 旧应用（不参与生产）

# 后端：容器启动 + 健康检查
docker build -t myblog-api backend/
docker run -d -p 1816:1816 -e PORT=1816 -e SITE_ORIGIN=http://localhost:8080 --name myblog-api-test myblog-api
curl -s http://localhost:1816/api/v1/health   # → {"status":"ok"}
docker rm -f myblog-api-test

# 前端：按 lockfile 安装依赖 + lint + 生产构建
cd myblogweb
npm ci
npm run lint
VUE_APP_API_BASE_URL=http://localhost:1816 npm run build   # 生产构建时由 Render 注入真实 API 来源

# 前后端集成冒烟（浏览器自动化，需本机 Chrome）
node ../scripts/serve-dist.js &               # SPA 静态服务器 :8080（含重写）
java -jar ../backend/target/backend-0.0.1-SNAPSHOT.jar &   # API :1816
NODE_PATH=myblogweb/node_modules node scripts/browser-smoke.js   # 24/24 通过
```

## 2. Render 部署步骤

### 方式 A：Blueprint（推荐）

在 Render Dashboard → New → Blueprint，选择本仓库，读取根目录 `render.yaml`。
一次创建两个服务并自动互连环境变量：

- `myblog-api`：Docker 构建（Java 11 多阶段），Free 计划，新加坡区域，
  健康路径 `/api/v1/health`，`SITE_ORIGIN` 自动指向前端主机；
- `myblog-frontend`：`npm ci && npm run build`，发布 `dist/`，
  `VUE_APP_API_BASE_URL` 自动指向 API 主机，SPA 重写已配置。

### 方式 B：手动创建

1. **先创建 API**：New → Web Service → 选仓库 → Root Directory `backend`、
   Runtime `Docker`、Region `Singapore`、Free 计划、Health Check Path `/api/v1/health`。
   环境变量：
   - `SITE_ORIGIN`：部署完成后的前端 HTTPS 来源（见第 3 步）
2. **再创建前端**：New → Static Site → Root Directory `myblogweb`、
   Build Command `npm ci && npm run build`、Publish Directory `dist`。
   环境变量：
   - `VUE_APP_API_BASE_URL`：API 的 HTTPS 来源，如 `https://myblog-api-xxxx.onrender.com`
3. **锁定 CORS**：把前端实际 URL 写入 API 的 `SITE_ORIGIN` 并重新部署 API。
4. **SPA 重写**：前端 Static Site → Redirects/Rewrites →
   Source `/*`、Destination `/index.html`、Action `Rewrite`
   （Render 会先命中真实静态资源，再把其余路径重写到应用入口）。
5. **验收**（见第 5 节）。

部署顺序不可裁剪：先 API，后前端，再把最终前端来源写入 API 并重新部署，
最后完成桌面／移动、热启动／冷启动冒烟测试。

## 3. 环境变量

| 变量 | 服务 | 说明 |
| --- | --- | --- |
| `PORT` | API | Render 自动提供；本地默认 1816 |
| `SITE_ORIGIN` | API | 生产前端准确来源，CORS 只允许该来源（未设置时 fail closed） |
| `VUE_APP_API_BASE_URL` | 前端（构建时） | 公开 API 的 HTTPS 来源，不得包含任何凭据 |
| `JWT_SECRET` | 旧应用 | 旧登录功能所需；生产公开站点不使用 |

本地开发：API 以 `dev` profile 启动（`application-dev.yml` 提供
`http://localhost:8080` 开发来源）；前端开发服务器读取 `.env.development`。

## 4. 秘密轮换（上线前必须完成）

仓库历史中曾提交以下凭据，已从活动配置移除：

- **MySQL 密码**（`application.properties` → `application.properties.hide`）：
  上线前重置 `myblog` 数据库密码（或改用最小权限账号），并确认旧应用不再使用该凭据。
- **JWT 签名密钥**（`JwtUtil.JWT_KEY` 硬编码值）：已改为仅从 `JWT_SECRET`
  环境变量读取；任何需要旧登录功能的部署必须先轮换密钥。

轮换完成后删除/保留 `.hide` 文件与 Git 历史清理按补充说明执行，不进行 Git 历史重写。

## 5. 线上验收清单

- 前后端地址均为 HTTPS，HTTP 自动跳转 HTTPS。
- API 使用 Render 端口启动并通过 `/api/v1/health`。
- 前端只请求 Render API 的 HTTPS 来源（`VUE_APP_API_BASE_URL`）。
- CORS 只接受准确前端来源（浏览器 Network 面板确认）。
- 浏览器控制台无混合内容、CORS、认证或 JavaScript 错误。
- API 闲置至少 15 分钟后：页面骨架立即出现，约 3 秒出现
  “免费内容服务正在启动，通常可能需要约一分钟”，随后自动恢复。
- 直接打开/刷新 `/blog`、真实博客 URL、未知路由均正确（SPA 重写生效）。
- 桌面端与 390 像素移动端无横向溢出。
- 所有可见内容与外链经站点所有者确认（#6）。

## 6. 冷启动与免费计划说明

Render Free API 闲置约 15 分钟后休眠，首次请求需要约一分钟唤醒。
页面会显示启动提示并自动重试（窗口 ≤75 秒），最终失败提供手动重试按钮。
当求职投递期的第一印象受到影响时，升级为持续在线实例。
