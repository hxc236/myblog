# 部署与发布检查（腾讯云 Lighthouse，中国内地）

> 部署决策（#13）：**腾讯云中国内地 Lighthouse（Docker + Nginx）为唯一部署路径**，
> 使用自有域名 **hxc236.cn**、ICP 备案与同源 `/api/` 反向代理。
> 原 Render 手册（新加坡区域、平台子域名、免费实例冷启动）已废止，见 §8。

## 0. 部署架构

```
浏览器 ── https://hxc236.cn ──► Nginx（web 容器，公网 80/443）
                                  ├─ /                      → 前端静态文件（SPA 回退 index.html）
                                  ├─ /api/                  → 反代到后端容器
                                  ├─ /oauth2/、/login/oauth2/ → 反代到后端容器（GitHub OAuth）
                                  └─ /.well-known/…         → ACME 证书校验（certbot）

后端 API（api 容器，8080，仅内部网络）──► PostgreSQL（db 容器，5432，仅内部网络）
```

三件事理解这个架构：

1. **浏览器只和 Nginx 说话**。前端页面、图片、`/api/...` 请求全都打到 `https://hxc236.cn`，
   `/api/` 由 Nginx 在内部转给后端。浏览器视角里前后端是"同一个网站"，所以**没有跨域问题**。
2. **后端和数据库不暴露公网**。它们只在 Docker 内部网络里互联，公网只开 Nginx 的 80/443。
3. **数据库是唯一内容来源**（ADR-0001）。后端启动时 Flyway 自动建表，首次上线后执行一次
   MVP 内容导入（§4.8），之后博客内容都在 PostgreSQL 里。

## 1. 基础知识速览（第一次部署的人必读）

### 1.1 服务器与 SSH

**Lighthouse（轻量应用服务器）** 是一台云上的 Linux 虚拟机，运行在你电脑之外的腾讯云机房。
你通过 **SSH**（Secure Shell，加密远程登录协议）像操作本地电脑一样操作它，只是只有命令行。

- 本机是 Windows：PowerShell 自带 `ssh` 命令，无需装额外软件。
- 登录命令：`ssh root@<公网IP>`，密码是购买实例时设置的 root 密码。

### 1.2 Docker 是什么

**Docker** 是"容器"技术的实现。容器可以理解为一个**打包好的、自带运行环境的小型进程隔离箱**：

- 你的后端需要 Java 11、前端需要 Node 构建、数据库需要 PostgreSQL——每样东西的安装配置
  都很麻烦，而且换台机器就要重来一遍。
- **镜像（Image）** 是"打包好的模板"：比如 `backend/Dockerfile` 把「Java 11 + Maven + 你的
  代码」打包成一个镜像。
- **容器（Container）** 是镜像的**运行实例**：同一个镜像可以启动多个容器，互不干扰。
- **docker compose** 是"一键编排多个容器"的工具：一个 `docker-compose.yml` 声明
  db/api/web 三个容器、它们的网络关系和环境变量，一条 `docker compose up -d` 全部启动。

不用 Docker 也能部署（直接在服务器上装 Java、装 PostgreSQL、装 Nginx），但 Docker 的好处是：
**所有环境配置都写死在仓库里，可复现、可回退、换机器不重来**。

### 1.3 Nginx 是什么

**Nginx** 是一个 Web 服务器软件，本项目里它同时扮演两个角色：

1. **静态文件服务器**：把前端构建出的 `dist/` 目录（HTML/JS/CSS/图片）直接发给浏览器；
   并用 `try_files $uri $uri/ /index.html` 实现 **SPA 回退**——`/blog` 这种前端路由不存在
   对应文件时，返回 `index.html` 让 Vue Router 接管。
2. **反向代理（Reverse Proxy）**：收到 `/api/...` 请求时，在**服务器内部**转发给后端容器。
   反向代理的意义：浏览器只认一个域名，后端藏在 Nginx 后面，不暴露公网端口。

### 1.4 域名与 DNS

- **域名**（hxc236.cn）是网站的门牌号；**DNS**（域名系统）是把门牌号翻译成服务器 IP 的系统。
- **A 记录**：把 `hxc236.cn` 指向 Lighthouse 的公网 IP。配置在腾讯云「云解析 DNS」控制台。
- 域名要能"实名"，即域名所有者的身份证信息与备案主体一致。

### 1.5 ICP 备案（中国大陆服务器的法律要求）

- 中国大陆机房的服务器**必须完成 ICP 备案**才能用域名对外服务；腾讯云会拦截未备案域名
  解析到其大陆服务器的访问。
- 个人主页走**个人备案**：用身份证 + Lighthouse 实例（须中国内地、购买 ≥3 个月）作为
  备案资源，通过腾讯云备案小程序办理。周期：初审 1–2 个工作日 + 短信核验 24 小时内 +
  管局审核最长 20 个工作日。
- 备案通过后，网站底部要放备案号并链接到工信部备案系统；开通后 30 日内还要做**公安备案**。
- **部署工作不依赖备案**：备案期间可以在服务器上把整套环境跑起来，通过公网 IP 访问验收，
  只是域名不能解析到服务器。

### 1.6 端口与防火墙

- **80**：HTTP；**443**：HTTPS；**22**：SSH。
- Lighthouse 控制台有防火墙规则：80/443 开放给所有人，22 建议只对你的家庭 IP 开放。

## 2. 部署工件（本仓库为部署新增的文件）

| 文件 | 作用 |
| --- | --- |
| `docker-compose.yml` | 编排 db / api / web 三容器 + certbot 工具（根目录 `.env` 提供配置） |
| `deploy/nginx.conf` | **HTTPS 阶段** Nginx 配置：80→443 跳转 + SPA + `/api/`、OAuth 反代 |
| `deploy/nginx.http.conf` | **证书签发前**的 HTTP 阶段配置（无 443、无跳转） |
| `deploy/.env.example` | `.env` 模板：数据库口令、`SITE_ORIGIN`、OAuth、JVM 参数 |
| `myblogweb/Dockerfile` | 前端多阶段镜像：Node 构建 `dist/` → Nginx 托管 |
| `backend/Dockerfile` | 后端多阶段镜像：Maven 打包（测试在宿主发布检查中运行） |

## 3. 本地发布检查（上线前必须全部通过）

### 3.1 一键检查

重启电脑并启动 Docker Desktop 后：

```bash
bash scripts/release-check.sh
```

脚本依次完成后端测试+打包、容器启动+健康检查、前端 lint+构建、浏览器冒烟并汇总结果。

### 3.2 本地整套编排冒烟（可选，推荐第一次部署前跑）

等价于在服务器上会做的事，在本机先跑一遍：

```bash
cd <仓库根目录>
cp deploy/.env.example .env
# 编辑 .env：POSTGRES_PASSWORD 设一个本地测试密码；SITE_ORIGIN=http://localhost:80
docker compose up -d --build
curl -s http://localhost/api/site/health          # → {"status":"ok"}
curl -s http://localhost/ | head -5               # → index.html
curl -s -o /dev/null -w "%{http_code}" http://localhost/blog   # → 200（SPA 回退）
docker compose down
```

> 注意：`docker compose up --build` 会拉取/构建约 1.5GB 镜像与依赖缓存，注意磁盘空间
> （本机 Docker 数据在 C 盘）。验证完用 `docker system prune -a` 清理。

## 4. 腾讯云人工操作手册（按顺序执行，别跳步）

> 每节开头标注操作地点：**腾讯云控制台**（网页）或 **服务器 SSH**（命令行）。

### 4.1 购买 Lighthouse 实例（腾讯云控制台）

1. 登录腾讯云控制台 → 搜索「轻量应用服务器 Lighthouse」→ **新建实例**。
2. 关键选项：
   - **地域**：中国内地，选离你近的（如广州/上海）。
   - **镜像**：应用镜像 → **Docker CE**（自带 Docker 环境）。
   - **套餐**：**2 核 2G**（本个人站足够：Java 后端 + PostgreSQL + Nginx 实测约 1GB 内存）。
   - **时长**：**≥ 3 个月**（ICP 备案对备案资源的最低要求）。
   - **登录方式**：设置 root 密码（记好），或使用密钥对。
3. 购买后在实例详情页看到**公网 IP**（记为 `IP`）。
4. **防火墙**：实例 → 防火墙 → 确保 TCP **80**、**443** 已放行（模板通常默认）；
   把 **22**（SSH）的来源限制为你当前家庭网络的公网 IP。

### 4.2 域名实名与 ICP 备案（腾讯云控制台）

1. **确认域名已实名**：控制台 → 域名注册 → 找到 hxc236.cn → 域名状态为"已实名"。
   未实名先完成实名（通常 1 个工作日），备案时域名所有者须与备案主体（你本人）一致。
2. **办理备案**：控制台搜索「ICP 备案」→ 开始备案：
   - 备案资源选择刚买的 Lighthouse 实例；
   - 主体信息填本人（个人备案）；网站信息填 hxc236.cn；
   - 小程序/网页完成人脸核验与材料上传；
   - 腾讯云初审通过后，**24 小时内**按短信完成工信部短信核验；
   - 等待省通信管理局审核（最长 20 个工作日，通常 1–2 周）。
3. 备案期间**不要**在云解析里把域名指向服务器（会被拦截）；服务器可以照常部署（§4.3 起）。
4. 备案通过后你会收到备案号（如 `粤ICP备XXXXXXXX号`）——保留备用（§4.7 验收时放网站底部）。

### 4.3 SSH 登录服务器（本机 PowerShell）

```powershell
ssh root@<IP>
```

首次登录会提示确认指纹（输入 `yes`），然后输入 root 密码。

登录后先做基础检查：

```bash
docker --version          # 应显示 Docker 版本（模板自带）
docker compose version    # compose 插件；若提示不存在，执行：
                          #   apt-get update && apt-get install -y docker-compose-plugin
free -h                   # 内存（2G 实例：used 应远小于 total）
df -h /                   # 磁盘（构建镜像前确认有 >5GB 空闲）
```

### 4.4 克隆仓库并配置 .env（服务器 SSH）

```bash
cd /opt
git clone https://github.com/hxc236/myblog.git
cd myblog
cp deploy/.env.example .env
nano .env        # 没有 nano 就 vi .env
```

`.env` 里必须改的三处：

```bash
POSTGRES_PASSWORD=一个足够长的随机密码        # 生成：openssl rand -hex 24
SITE_ORIGIN=https://hxc236.cn
NGINX_CONF=./deploy/nginx.http.conf            # 证书签发前：HTTP 阶段配置
```

> `.env` 含数据库口令，仓库的 `.gitignore` 已排除，绝不提交进 Git。

### 4.5 构建并启动（HTTP 阶段，服务器 SSH）

```bash
cd /opt/myblog
docker compose up -d --build
 docker compose ps          # 三个容器都应为 Up；db 显示 healthy
```

首次构建约 5–15 分钟（下载 Maven/Node 依赖并编译）。查看启动日志：

```bash
docker compose logs -f api    # 看到 "Started BackendApplication" 即启动成功
```

> 国内加速说明（仓库已内置，无需手动配置）：
> - **Maven 依赖**：`backend/settings.xml` 已配阿里云镜像（Docker 构建时自动生效）；
> - **npm 依赖**：`myblogweb/.npmrc` 已指向 npmmirror 国内镜像；
> - **Docker Hub 镜像**：Lighthouse 的 Docker CE 模板通常自带腾讯云内网加速
>   （`mirror.ccs.tencentyun.com`）。若拉取慢/失败，可在
>   `/etc/docker/daemon.json` 的 `registry-mirrors` 添加可用加速器后
>   `systemctl restart docker`（本机 Windows 版 Docker Desktop 的加速器
>   配置方法相同：Settings → Docker Engine → registry-mirrors）。

> **先停掉再解释**：`up -d --build` = 构建镜像（--build）并以守护模式启动（-d）。
> 三个容器：`db`（PostgreSQL）→ `api`（后端，等 db 健康后才启动）→ `web`（Nginx）。

### 4.6 HTTP 阶段验收（备案期间就能做）

浏览器直接访问 **http://<公网IP>**（备案只拦域名，不拦 IP）：

- 首页四区内容正常（公开介绍、作品、博客、联系方式）；
- `/blog` 直接刷新正常（SPA 回退生效）；
- 命令行验证 API 反代：

```bash
curl -s http://<IP>/api/site/health          # → {"status":"ok"}
curl -s http://<IP>/api/site/introduction    # → 数据库中的公开介绍 JSON
```

如果内容为空（introduction 未导入），先做 §4.8 的 OAuth 配置与内容导入，再回来验收。

### 4.7 备案通过 → 域名解析 → HTTPS（腾讯云控制台 + 服务器 SSH）

**第 1 步：域名解析（腾讯云控制台）**

控制台 → 云解析 DNS → hxc236.cn → 添加记录：

| 主机记录 | 类型 | 记录值 |
| --- | --- | --- |
| `@` | A | `<IP>`（Lighthouse 公网 IP） |
| `www` | A | `<IP>` |

等 1–2 分钟，用命令确认生效（本机 PowerShell）：

```powershell
nslookup hxc236.cn
```

**第 2 步：签发 HTTPS 证书（服务器 SSH）**

```bash
cd /opt/myblog
docker compose --profile certbot run --rm certbot certonly \
  --webroot -w /var/www/certbot -d hxc236.cn -d www.hxc236.cn \
  --email 你的邮箱 --agree-tos --no-eff-email
```

看到 `Successfully received certificate` 即成功。证书存于容器卷 `certbot-certs`，
Nginx 容器以只读方式挂载同一卷（compose 已配置好）。

**第 3 步：切换到 HTTPS 配置（服务器 SSH）**

```bash
nano .env     # 把 NGINX_CONF 改为 ./deploy/nginx.conf
docker compose up -d web    # 重新创建 web 容器，挂载 HTTPS 配置
docker compose exec web nginx -t   # 配置检查（应输出 syntax is ok）
docker compose exec web nginx -s reload
```

**第 4 步：配置证书自动续期（服务器 SSH）**

Let's Encrypt 证书有效期 90 天，用 cron 每两周检查续期：

```bash
crontab -e    # 首次会询问编辑器，选 nano 即可
```

添加一行：

```
0 3 */14 * * cd /opt/myblog && docker compose --profile certbot run --rm certbot renew --webroot -w /var/www/certbot --quiet && docker compose exec web nginx -s reload
```

> 含义：每 14 天的凌晨 3 点尝试续期（只有到期前 30 天内才会真续），成功则重载 Nginx。

### 4.8 GitHub OAuth 与 MVP 内容导入（网站所有者操作）

后端 Admin Console 用 GitHub OAuth 登录（#16），也是执行 MVP 内容导入（#27）的前提。

**第 1 步：创建 GitHub OAuth App（github.com 网页）**

GitHub → Settings → Developer settings → OAuth Apps → **New OAuth App**：

- Homepage URL：`https://hxc236.cn`
- Authorization callback URL：`https://hxc236.cn/login/oauth2/code/github`

创建后得到 **Client ID** 和 **Client Secret**。

**第 2 步：写入服务器 .env（服务器 SSH）**

```bash
nano /opt/myblog/.env
```

```bash
GITHUB_OAUTH_CLIENT_ID=刚才的ClientID
GITHUB_OAUTH_CLIENT_SECRET=刚才的ClientSecret
GITHUB_ADMIN_ALLOWLIST=你的GitHub登录名
```

**第 3 步：重启后端并导入 MVP 内容**

```bash
cd /opt/myblog
docker compose up -d api    # 应用新环境变量
```

浏览器打开 `https://hxc236.cn/admin` → 「使用 GitHub 登录」→ 授权后进入 Admin Console，
然后执行一次性内容导入（开发者接口，登录后调用）：

```bash
# 已登录的浏览器里访问（或开发者工具里执行）：
curl -X POST https://hxc236.cn/api/admin/import/mvp -H "Authorization: Bearer <会话令牌>"
```

返回 `{"introductionImported":true, "projectsImported":N, "postsImported":N}` 即导入完成，
回 §4.6 验收内容。导入可重复执行（已有数据的领域自动跳过）。

### 4.9 最终验收清单（#13）

- [ ] 浏览器打开 `https://hxc236.cn`，HTTP 自动跳转 HTTPS（地址栏有锁）。
- [ ] 首页四区内容完整（来自 PostgreSQL 的 JSON 渲染）。
- [ ] `/blog`、真实博客 URL、未知路由直接打开/刷新均正常（SPA 重写生效）。
- [ ] `curl https://hxc236.cn/api/site/health` → `{"status":"ok"}`。
- [ ] 浏览器控制台无混合内容、CORS、JavaScript 错误。
- [ ] 网站底部标注备案号并链接 `https://beian.miit.gov.cn`（工信部要求）。
- [ ] 公安备案完成（网站开通后 30 日内，全国互联网安全管理服务平台）。
- [ ] 桌面端与 390px 移动端无横向溢出。
- [ ] 所有可见内容与外链经站点所有者确认（#6）。
- [ ] 备份恢复演练通过（见 `docs/backup.md`，含 pg_dump 备份与 restore-drill.sh）。

## 5. 环境变量表

| 变量 | 服务 | 说明 |
| --- | --- | --- |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | db | PostgreSQL 库名/账号/口令（仅容器内部使用） |
| `DATABASE_URL` | api | compose 自动拼成 `postgres://…@db:5432/…`，应用自动转换 JDBC |
| `SITE_ORIGIN` | api | 生产 HTTPS 来源（CORS 白名单，fail closed）；同源反代下浏览器不触发跨域 |
| `GITHUB_OAUTH_CLIENT_ID` / `GITHUB_OAUTH_CLIENT_SECRET` | api | Admin 登录凭据；不配置则后台与内容导入不可用 |
| `GITHUB_ADMIN_ALLOWLIST` | api | 唯一 Site Owner 的 GitHub 登录名（授权边界） |
| `JAVA_OPTS` | api | JVM 参数（默认 `-Xmx512m`，2C2G 实例的保守值） |
| `NGINX_CONF` | web | 挂载哪份 Nginx 配置：`./deploy/nginx.http.conf`（证书前）/ `./deploy/nginx.conf`（HTTPS） |
| `VUE_APP_API_BASE_URL` | 前端构建 | **保持为空**（同源部署）；构建时注入非空值即改为跨域模式 |

## 6. 日常运维（服务器 SSH）

```bash
# 查看状态与日志
docker compose ps
docker compose logs -f web api db

# 重启某个服务（改配置后常用）
docker compose up -d api

# 升级到新版本代码（git pull 后重建）
cd /opt/myblog
git pull
docker compose up -d --build

# 数据库备份（详细演练见 docs/backup.md）
docker compose exec db pg_dump -U myblog -d myblog > backup.sql

# 停整套 / 起整套
docker compose down        # 停；数据卷 pgdata 保留
docker compose up -d       # 起
```

## 7. 回退

1. 回退代码：`git log --oneline -5` 找到上一个部署提交 →
   `git checkout <提交>` → `docker compose up -d --build`。
2. 回退数据：从备份恢复（`docs/backup.md` 的 restore-drill.sh）。
3. 回退域名/证书不影响数据，重新解析或重签即可。

> 规则（#14/#30）：回退通过恢复旧版本部署完成，**不得**以长期并存的 URL 版本实现。

## 8. 历史说明（已废止）

- 2026-08-16 前：本手册为 **Render 部署**（新加坡区域、Blueprint、免费计划冷启动、平台子域名），
  对应 `render.yaml` 与 `#12` 发布制品。
- 部署决策更新（#13）：以**腾讯云中国内地 Lighthouse（Docker + Nginx）**为唯一部署路径，
  使用自有域名 hxc236.cn、ICP 备案与同源 `/api/` 反代；本手册已按新路径重写。
- `render.yaml` 保留仅供历史参考，不再用于生产部署。
