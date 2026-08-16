# hxc236 个人主页（myblog）

前后端分离的个人主页 MVP：**Vue 3 静态站 + Spring Boot 2.7 / Java 11 匿名只读 API**。

- 首页四个内容区：公开介绍、作品展示、博客、联系方式（设计规范见 `docs/Design.md`）
- PostgreSQL 是唯一运行时内容权威源（ADR-0001）；空库由 Flyway 建表，MVP 内容可经一次性导入写入（#27/#30）
- 已废弃的 JWT / MySQL / MyBatis-Plus 账户、Plan、Todo 应用已删除（#31），构建不再包含 legacy 分类制品
- 部署说明与发布检查：`docs/deploy.md`；Render 蓝图：`render.yaml`

## 目录结构

```
backend/   Spring Boot API（生产后端，统一包根 com.myblog.backend，入口 BackendApplication）
  src/main/java/com/myblog/backend/
    config/     Spring 配置、安全与过滤器、CORS、数据源、环境处理
    controller/ 全部 HTTP 控制器与异常→响应处理
    service/    业务接口（service/impl 为 Spring 实现与存储适配器）
    mapper/     PostgreSQL 数据访问（JDBC/SQL）
    pojo/       元数据与传输 DTO
    utils/      无状态工具与 MVP 快照加载助手
  src/main/resources/legacy-mvp-snapshot/content/  MVP 一次性导入快照（JSON + Markdown）
myblogweb/ Vue 3 前端（生产首页 /blog 列表与详情 / 站内 404）
scripts/   本地集成冒烟工具（SPA 静态服务器 + 浏览器自动化检查）
docs/      规格、设计、部署文档
```

## 本地开发

环境变量方式跨 Git Bash / PowerShell 通用（PowerShell 7.3+ 会拆分含 `=` 的 `-D` 参数，故不用 `-D`）：

```bash
# 后端（Java 11，生产后端，端口 1816）
export JAVA_HOME="C:\Program Files\Java\jdk-11.0.12"   # PowerShell: $env:JAVA_HOME='C:\Program Files\Java\jdk-11.0.12'
export SPRING_PROFILES_ACTIVE=dev                       # PowerShell: $env:SPRING_PROFILES_ACTIVE='dev'
cd backend
mvn spring-boot:run
# 或打包后运行：java -jar target/backend-0.0.1-SNAPSHOT.jar

# 前端（Vue 3，端口 8080，读取 .env.development 的 API 来源）
cd myblogweb
npm install
npm run serve
```

## 测试与构建

```bash
cd backend && mvn test && mvn package      # 120 个测试（Testcontainers 需 Docker + HTTP 契约 + 包根完整性）
cd myblogweb && npm run lint && npm run build
docker build -t myblog-api backend/        # Java 11 多阶段构建
```

上线前的完整发布检查清单与 Render 部署步骤见 **`docs/deploy.md`**。
