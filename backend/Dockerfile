# 隔离公开站点 API 的 Render-ready 多阶段 Docker 构建（Java 11）
# 生产运行进程无需 MySQL / 嵌入式数据库，通过 /api/v1/health 健康检查。

# ---- 构建阶段：Java 11 + Maven，运行全部测试与打包 ----
FROM maven:3.8.6-eclipse-temurin-11 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package

# ---- 运行阶段：Java 11 JRE ----
FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=build /build/target/backend-0.0.1-SNAPSHOT.jar app.jar

# 默认制品即隔离公开站点应用（com.myblog.publicsite.PublicSiteApplication）
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
