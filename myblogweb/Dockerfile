# 前端生产镜像（#13 腾讯云部署）：Node 构建 dist → Nginx 托管。
# 站点配置（SPA 回退 / API 反代）由 docker-compose 挂载 deploy/nginx*.conf，
# 修改配置无需重建镜像。
#
# 同源部署：VUE_APP_API_BASE_URL 保持为空，浏览器直接请求 https://域名/api/...，
# 由 Nginx 反代到后端容器；需要时可用 --build-arg VUE_APP_API_BASE_URL=... 覆盖。

FROM node:18-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VUE_APP_API_BASE_URL=
ENV VUE_APP_API_BASE_URL=$VUE_APP_API_BASE_URL
RUN npm run build

FROM nginx:stable-alpine
COPY --from=build /build/dist /usr/share/nginx/html
