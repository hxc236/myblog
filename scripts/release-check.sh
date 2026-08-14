#!/usr/bin/env bash
# 本地发布检查（docs/deploy.md §1）：重启电脑（Docker 引擎）后执行本脚本即可完成 #12 全部本地验收。
# 用法：bash scripts/release-check.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND="$ROOT/backend"
FRONTEND="$ROOT/myblogweb"

PASS=0
FAIL=0
step() { printf '\n===== %s =====\n' "$1"; }
ok()   { PASS=$((PASS + 1)); echo "  [PASS] $1"; }
bad()  { FAIL=$((FAIL + 1)); echo "  [FAIL] $1"; }

# Java 11（优先使用本机已知 JDK 11 路径，其次取 PATH 上的 java）
if [ -d "C:/Program Files/Java/jdk-11.0.12" ]; then
  export JAVA_HOME="C:/Program Files/Java/jdk-11.0.12"
fi
JAVA_VER="$(java -version 2>&1 | head -1)"
echo "$JAVA_VER" | grep -q '"11' || { echo "需要 Java 11，当前：$JAVA_VER"; exit 1; }

# ---------- 1. 后端：测试 + 打包 ----------
step "1/6 后端 Maven 测试与打包（Java 11）"
(
  cd "$BACKEND"
  mvn -q clean package
) && ok "mvn clean package 通过（16 个 MockMvc 测试）" || bad "mvn clean package 失败"
[ -f "$BACKEND/target/backend-0.0.1-SNAPSHOT.jar" ] && ok "默认制品=公开站点应用 jar 已生成" || bad "缺公开站点 jar"

# ---------- 2. 容器：构建 + 启动 + 健康检查 ----------
step "2/6 Docker 容器构建、启动与健康检查"
if docker info > /dev/null 2>&1; then
  docker build -t myblog-api "$BACKEND" > /tmp/myblog-docker-build.log 2>&1 \
    && ok "docker build 成功" || bad "docker build 失败（见 /tmp/myblog-docker-build.log）"
  docker rm -f myblog-api-test > /dev/null 2>&1 || true
  docker run -d --name myblog-api-test -p 1816:1816 \
    -e PORT=1816 -e SITE_ORIGIN=http://localhost:8080 myblog-api > /dev/null
  READY=""
  for i in $(seq 1 30); do
    if curl -sf -m 2 http://localhost:1816/api/v1/health > /dev/null 2>&1; then READY=yes; break; fi
    sleep 2
  done
  if [ "$READY" = yes ]; then
    BODY="$(curl -s http://localhost:1816/api/v1/health)"
    echo "$BODY" | grep -q '"status":"ok"' && ok "容器 /api/v1/health → $BODY" || bad "健康检查响应异常：$BODY"
    curl -s http://localhost:1816/api/v1/introduction | grep -q 'houxc2249' \
      && ok "容器内提供已确认内容" || bad "容器内容异常"
  else
    bad "容器 60 秒内未就绪（docker logs myblog-api-test 查看）"
  fi
  docker rm -f myblog-api-test > /dev/null 2>&1 || true
else
  bad "Docker 引擎不可用：请先重启电脑并启动 Docker Desktop"
fi

# ---------- 3. 前端：lockfile 安装 + lint + 生产构建 ----------
step "3/6 前端 npm ci + lint + 生产构建"
(
  cd "$FRONTEND"
  npm ci > /dev/null 2>&1 && npm run lint > /dev/null 2>&1 \
    && VUE_APP_API_BASE_URL=http://localhost:1816 npm run build > /dev/null 2>&1
) && ok "npm ci / lint / build 通过" || bad "前端安装或构建失败"

# ---------- 4. 前后端集成冒烟 ----------
step "4/6 浏览器集成冒烟（桌面 + 390px 移动端）"
pkill -f "serve-dist.js" 2>/dev/null || true
export SITE_ORIGIN=http://localhost:8080
java -jar "$BACKEND/target/backend-0.0.1-SNAPSHOT.jar" --server.port=1816 > /tmp/myblog-api-smoke.log 2>&1 &
PID_API=$!
node "$ROOT/scripts/serve-dist.js" > /tmp/myblog-static.log 2>&1 &
PID_STATIC=$!
trap 'kill $PID_STATIC $PID_API 2>/dev/null || true' EXIT
# 等待 API 就绪（避免浏览器首屏请求早于 Tomcat 绑定端口）
READY=""
for i in $(seq 1 20); do
  if curl -sf -m 2 http://localhost:1816/api/v1/health > /dev/null 2>&1; then READY=yes; break; fi
  sleep 2
done
[ "$READY" = yes ] && ok "API 就绪（http://localhost:1816）" || bad "API 40 秒内未就绪"
if [ "$READY" = yes ]; then
  if NODE_PATH="$FRONTEND/node_modules" node "$ROOT/scripts/browser-smoke.js" > /tmp/myblog-smoke.log 2>&1 \
    && tail -1 /tmp/myblog-smoke.log | grep -q "24/24"; then
    ok "浏览器冒烟 24/24 通过"
  else
    bad "浏览器冒烟未通过（见 /tmp/myblog-smoke.log）"
    tail -5 /tmp/myblog-smoke.log || true
  fi
fi

# ---------- 5/6. 报告 ----------
step "发布检查结果"
echo "  通过 $PASS 项，失败 $FAIL 项"
[ "$FAIL" -eq 0 ] && echo "  ✅ 全部本地发布检查通过，可以按 docs/deploy.md §2 部署 Render"
exit "$FAIL"
