# 个人站生产后端的多阶段 Docker 构建（#13 腾讯云 Lighthouse 部署）
# 运行进程：PostgreSQL 数据源（DATABASE_URL）+ 公开内容 API + Admin（GitHub OAuth）。

# ---- 构建阶段：Java 11 + Maven，打包 ----
FROM maven:3.8.6-eclipse-temurin-11 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
# 测试不在此阶段运行：数据库集成测试依赖 Testcontainers（需要 Docker 守护进程），
# 容器构建环境没有；测试由宿主发布检查执行（docs/deploy.md 本地发布检查）。
RUN mvn -q package -DskipTests

# ---- 运行阶段：Java 11 JRE ----
FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=build /build/target/backend-0.0.1-SNAPSHOT.jar app.jar

# 默认制品即隔离公开站点应用（com.myblog.publicsite.PublicSiteApplication）
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
