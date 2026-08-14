# hxc236 个人主页（myblog）

前后端分离的个人主页 MVP：**Vue 3 静态站 + Spring Boot 2.7 / Java 11 匿名只读 API**。

- 首页四个内容区：公开介绍、作品展示、博客、联系方式（设计规范见 `docs/Design.md`）
- 内容随仓库版本化（JSON + Markdown），无需数据库；规格见 `docs/mvp_spec.md`
- 旧应用（账户、JWT、聊天、在线编程、计划）保留在代码中，但**不进入生产运行时**
- 部署说明与发布检查：`docs/deploy.md`；Render 蓝图：`render.yaml`

## 目录结构

```
backend/   Spring Boot API（默认制品 = 隔离公开站点应用）
  src/main/java/com/myblog/publicsite/   公开站点切片（无数据库、无认证）
  src/main/java/com/myblog/backend/      旧应用（legacy 分类制品，不参与生产）
  src/main/resources/publicsite/content/ 公开内容（JSON + Markdown，构建时校验）
myblogweb/ Vue 3 前端（生产首页 /blog 列表与详情 / 站内 404）
scripts/   本地集成冒烟工具（SPA 静态服务器 + 浏览器自动化检查）
docs/      规格、设计、部署文档
```

## 本地开发

```bash
# 后端（Java 11）
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.main-class=com.myblog.publicsite.PublicSiteApplication
# 或打包后运行：java -jar target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev

# 前端（Vue 3，读取 .env.development 的 API 来源）
cd myblogweb
npm install
npm run serve
```

## 测试与构建

```bash
cd backend && mvn test && mvn package      # MockMvc 契约测试 16 个
cd myblogweb && npm run lint && npm run build
docker build -t myblog-api backend/        # Java 11 多阶段构建
```

上线前的完整发布检查清单与 Render 部署步骤见 **`docs/deploy.md`**。
