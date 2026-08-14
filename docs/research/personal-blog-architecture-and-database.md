# 个人主页与个人博客架构、数据库使用调研

> 检索日期：2026-08-14（Asia/Shanghai）  
> 研究范围：个人主页、开发者博客、博客 CMS 的公开架构与内容存储方式  
> 证据优先级：官方文档、项目作者公开源码、固定 commit 的 GitHub 文件

## 结论先行

现在的个人主页和个人博客既有使用数据库的，也有完全不使用内容数据库的。更准确的判断不是“博客要不要数据库”，而是下面三个问题：

1. 文章的**权威来源**在哪里：Git 中的 Markdown/MDX/JSON，还是 MySQL/PostgreSQL/Notion 等数据库或 CMS？
2. 页面是构建时生成静态文件，还是每次请求时由服务器生成？
3. 是否需要后台编辑、草稿即时发布、多作者、评论、会员、搜索、浏览记录等动态能力？

本次代表性样本显示了两条都很成熟的路线：

- 技术型个人站常见“Git 管内容 + Markdown/MDX + SSG/预渲染”。内容更新靠提交代码触发构建，不需要持久内容数据库。
- CMS 型或功能型博客常见“运行时应用 + 关系型数据库”。数据库保存文章、用户、评论、草稿、修订、标签等，并配套管理后台。

两者之间还有大量混合方案：文章仍是 Markdown，但数据库只做搜索索引、阅读记录、登录、评论、重定向或构建缓存。看到项目里有 `sqlite`、`postgres` 依赖，并不能直接推断“文章存在数据库”。

**对本项目的建议不变：一天 MVP 继续用 Git 管理 JSON + Markdown，不为文章引入运行时数据库。** 这与大量成熟个人站的内容模型一致，也最符合当前只有一个站主、内容量很小、没有管理后台、目标是一天上线的约束。第二阶段确认博客编辑流程后，再决定引入 Git-backed CMS，还是 Spring Boot + 关系型数据库 CRUD。

## 研究边界与方法

这是一组为架构比较而选取的**代表性样本**，不是随机抽样，也不是市场份额统计。因此本文可以说明“哪些架构正在被真实项目采用、各自怎样工作”，不能声称“多少比例的博客使用数据库”。

样本刻意覆盖：

- Vue/Vite SSG + Markdown
- Jekyll/Hugo 一类传统静态生成思路
- Next.js + MDX
- Next.js + 外部内容数据库（Notion）
- 文件内容 + 附属数据库
- Django + PostgreSQL 原生内容数据库
- Spring Boot + Vue + MySQL 前后端分离
- Java CMS（Halo）、WordPress、Ghost 等数据库 CMS

开源站点样本均按本次检索时的 commit 固定链接，避免默认分支后续变化导致证据漂移。

## 先定义“用了数据库”

### 1. 内容权威数据库

文章正文、标题、发布日期、草稿状态等只在数据库或外部 CMS 中保存。应用必须查询数据库/API 才能得到内容。

例子：WordPress 的 MySQL/MariaDB、Ghost 的 MySQL、Simon Willison 博客的 PostgreSQL、Notion-backed 站点。

### 2. 内容文件的查询索引或缓存

Markdown/JSON 才是权威来源，但框架把文件解析后写入 SQLite、搜索索引或缓存，以便筛选、分页和全文检索。这个数据库通常可以从源文件重建。

例子：Nuxt Content v3 会把内容放入 SQLite 查询层；TinaCMS 明确把数据库描述成可重建的缓存，而 Markdown/JSON 仍是单一事实来源。

### 3. 附属功能数据库

文章仍在 Git 文件中，但登录、阅读进度、收藏、评论、搜索、分析、重定向等功能有自己的数据库。

例子：Kent C. Dodds 的文章是 MDX，D1/SQLite 用于用户、会话、阅读记录、收藏和搜索；Lee Robinson 的模板中 Postgres 仅为可选的重定向存储。

### 4. 无运行时数据库

构建程序读取 Markdown/MDX/JSON，输出 HTML/CSS/JS，由静态主机/CDN 提供。评论和分析如果存在，通常由第三方脚本或外部服务提供。

例子：Anthony Fu 的个人站、Jekyll/Hugo 站点、纯静态 Next.js/Astro 站点。

## 当前常见架构族

### A. Git 内容 + 静态站点生成（SSG）

```text
Markdown / MDX / JSON（Git）
          ↓ 构建
Astro / Hugo / Jekyll / Vite SSG / Next 静态导出
          ↓
HTML + CSS + JS
          ↓
CDN / 静态托管
```

特点：无内容数据库、部署简单、攻击面小、页面快；发布一篇文章通常要提交并重新构建。适合一个技术作者、内容更新不频繁、希望版本可追溯的站点。

### B. Git 内容 + SSR/ISR 混合渲染

```text
Markdown / MDX（Git）──┐
外部 API / 可选数据库 ├─→ Next.js / Nuxt / Astro SSR 或 ISR
缓存 / 搜索索引 ──────┘               ↓
                                  CDN + 运行时
```

特点：文章可以继续存在 Git，同时使用服务器渲染、增量重建、搜索或个性化能力。是否“用了数据库”要逐项判断。

### C. Headless CMS / 外部内容服务

```text
Notion / Ghost / WordPress / Headless CMS
              ↓ API
       Next.js / Nuxt / Vue
              ↓
        静态、ISR 或 SSR
```

特点：编辑体验好，前端与内容后台分离；代价是外部服务、密钥、同步、缓存和供应商运维复杂度。

### D. 全栈 CMS 或自建 CRUD

```text
管理后台 → 应用服务 → MySQL / PostgreSQL
访客页面 → 应用服务 → 内容查询 / SSR 或 JSON API
```

WordPress、Ghost、Halo，以及 Spring Boot + Vue 自建博客都属于这一族。适合草稿、修订、权限、多作者、评论和立即发布等运行时工作流。

## 框架和 CMS 的官方行为

| 方案 | 内容可以放在哪里 | 默认/典型运行方式 | 是否强制外部内容数据库 |
| --- | --- | --- | --- |
| Astro | 本地 Markdown/MDX，也可由 loader 读取远端源 | 默认构建时预渲染；可给部分路由加适配器做按需 SSR | 否 |
| Hugo | `content/` 中通常为 Markdown，`data/` 可为 JSON/TOML/YAML/XML | 构建为 `public/` 静态文件 | 否 |
| Jekyll | `_posts/`、页面 Markdown 和 front matter | 构建时把 Markdown/Liquid 转成静态站点 | 否 |
| Next.js | 本地 MDX、文件、API、CMS、数据库均可 | 静态导出、SSG、ISR、SSR 均支持 | 否 |
| Nuxt Content v3 | 权威源通常是 `content/` 中的 Markdown/YAML/JSON/CSV | 构建时解析；查询层使用 SQLite；既可 SSG 也可 Node 服务器 | 不强制外部内容 DB，但内部确实有 SQLite 查询库 |
| WordPress | 文章、页面、配置等存 MySQL/MariaDB | PHP 运行时 + 数据库，可用 REST API 做 headless | 是 |
| Ghost | 文章等由 Ghost 管理并通过主题或 Content API 输出 | Node.js + MySQL + Nginx；也可作为 headless CMS | 生产环境是 |
| Halo | 文章、站点模型和管理数据由 Java CMS 管理 | Java 应用 + PostgreSQL/MySQL/MariaDB/H2 + Console | 是（生产建议 PostgreSQL 等外部 DB） |

证据：

- Astro 官方说明页面默认在构建时预渲染，需要服务器适配器才按请求渲染；内容集合可从本地 Markdown/MDX 加载：[On-demand rendering](https://docs.astro.build/en/guides/on-demand-rendering/)、[Content collections](https://docs.astro.build/en/guides/content-collections/)。
- Hugo 官方目录说明 `content/` 是通常为 Markdown 的内容，`hugo build` 输出 `public/`；部署只需发布这些静态文件：[Directory structure](https://gohugo.io/getting-started/directory-structure/)、[Basic usage](https://gohugo.io/getting-started/usage/)。
- Jekyll 官方说明 Markdown 页面会在构建时转成 HTML，渲染流程最后写文件到磁盘：[Pages](https://jekyllrb.com/docs/pages/)、[Rendering process](https://jekyllrb.com/docs/rendering-process/)。
- Next.js 官方同时展示静态导出、服务器组件直接查询数据库两种路径，说明框架不绑定某一种内容存储：[Static Exports](https://nextjs.org/docs/app/guides/static-exports)、[Fetching Data](https://nextjs.org/docs/app/getting-started/fetching-data)、[MDX](https://nextjs.org/docs/app/guides/mdx)。
- Nuxt Content 官方说明构建时解析内容，开发时自动生成 `.data/content/contents.sqlite`；静态托管时以 WASM SQLite 在浏览器查询，Node 托管时服务器和浏览器都会加载 SQLite：[Installation](https://content.nuxt.com/docs/getting-started/installation)、[Configuration](https://content.nuxt.com/docs/getting-started/configuration)、[Debugging tools](https://content.nuxt.com/docs/advanced/tools)、[Static Hosting](https://content.nuxt.com/docs/deploy/static)、[Server Hosting](https://content.nuxt.com/docs/deploy/server)。这应归类为“文件权威源 + 可重建查询数据库”，不是传统 CMS 内容数据库。
- WordPress 官方运行要求明确 MariaDB 10.11+ 或 MySQL 8.0+ 用于保存内容与设置；REST API 可让独立前端读取和管理内容：[Requirements](https://wordpress.org/about/requirements/)、[REST API Handbook](https://developer.wordpress.org/rest-api/)。
- Ghost 官方说明生产只支持 MySQL 8；推荐栈为 Ubuntu、Node.js、MySQL、Nginx，Content API 对外提供已发布内容：[Supported databases](https://docs.ghost.org/faq/supported-databases/)、[Ubuntu install](https://docs.ghost.org/install/ubuntu/)、[Content API](https://docs.ghost.org/content-api/)。
- Halo 官方仓库把它定义为可用于个人博客的建站/CMS 工具；官方部署文档支持 PostgreSQL、MySQL、MariaDB、H2，并推荐 PostgreSQL、反对生产默认 H2：[Halo repository](https://github.com/halo-dev/halo)、[Docker Compose deployment](https://docs.halo.run/getting-started/install/docker-compose)、[准备说明](https://docs.halo.run/getting-started/prepare)。

## 可核验的开源个人站样本

### 样本一：Anthony Fu — Vue + Vite SSG + Markdown，无内容数据库

- 仓库：[antfu/antfu.me](https://github.com/antfu/antfu.me/tree/cd06ba10abd08a9dcb1418f14d4a9c9fe3080229)
- 文章直接位于 [`pages/posts/`](https://github.com/antfu/antfu.me/tree/cd06ba10abd08a9dcb1418f14d4a9c9fe3080229/pages/posts)，是大量 `.md` 文件。
- [`package.json`](https://github.com/antfu/antfu.me/blob/cd06ba10abd08a9dcb1418f14d4a9c9fe3080229/package.json) 的生产构建使用 `vite-ssg build`，内容工具包括 `gray-matter`、`markdown-it`、`unplugin-vue-markdown`；没有关系型数据库客户端或 ORM。

判断：文章权威源是 Git 中的 Markdown，部署产物为静态站点；不需要内容数据库。这是与当前 Vue 技术背景最接近的纯文件路线之一。

### 样本二：Hux Blog — Jekyll + Markdown，评论/分析是附属服务

- 仓库：[Huxpro/huxpro.github.io](https://github.com/Huxpro/huxpro.github.io/tree/a2ab0900be50d5f8695a18e22c5d61ebcc82afa7)
- [Jekyll 配置](https://github.com/Huxpro/huxpro.github.io/blob/a2ab0900be50d5f8695a18e22c5d61ebcc82afa7/_config.yml) 使用 Kramdown、Jekyll pagination，并配置了 Disqus 与 Google Analytics 标识。
- 文章以仓库文件管理，由 Jekyll 构建。

判断：文章本身没有内容数据库。即使评论和分析背后有外部持久化，它们也是附属系统，不应把本站归类为“数据库存文章”。

### 样本三：Lee Robinson 的 Next.js 模板 — MDX 内容，可选 Postgres 只存重定向

- 仓库：[leerob/next-mdx-blog](https://github.com/leerob/next-mdx-blog/tree/fd03371e3c90481a8447904e1b548e4c0327b7db)
- 页面内容是 [`app/page.mdx` 和 `app/n/*/page.mdx`](https://github.com/leerob/next-mdx-blog/tree/fd03371e3c90481a8447904e1b548e4c0327b7db/app)。
- [README](https://github.com/leerob/next-mdx-blog/blob/fd03371e3c90481a8447904e1b548e4c0327b7db/README.md) 明确把 Postgres 标为可选，并说明它只用于保存 redirects；[`package.json`](https://github.com/leerob/next-mdx-blog/blob/fd03371e3c90481a8447904e1b548e4c0327b7db/package.json) 有 `postgres` 依赖。

判断：这是“文件内容 + 可选附属数据库”。仅看依赖会误判，必须继续看 schema 和用途。

### 样本四：Bartosz Jarocki — Next.js + Notion 内容数据库 + ISR

- 仓库：[BartoszJarocki/jarocki.me](https://github.com/BartoszJarocki/jarocki.me/tree/8a99921d775910637d14e6ad49f494adc6419cf8)
- [README](https://github.com/BartoszJarocki/jarocki.me/blob/8a99921d775910637d14e6ad49f494adc6419cf8/README.md) 明确是 Notion-backed notes，并要求 `NOTION_TOKEN` 与 `NOTION_DATABASE_ID`。
- [`notesApi.ts`](https://github.com/BartoszJarocki/jarocki.me/blob/8a99921d775910637d14e6ad49f494adc6419cf8/src/lib/notesApi.ts) 通过 Notion SDK 查询 database/page/block。
- [Notes 列表页](https://github.com/BartoszJarocki/jarocki.me/blob/8a99921d775910637d14e6ad49f494adc6419cf8/src/pages/notes/index.tsx) 用 `getStaticProps` 拉取内容并设置 `revalidate: 10`。

判断：Notion 是文章内容数据库/CMS，Next.js 用静态生成加增量再验证提供页面。这说明“用了数据库”和“页面静态预渲染”并不冲突。

### 样本五：Kent C. Dodds — 文章是 MDX，D1/SQLite 服务动态功能与搜索

- 仓库：[kentcdodds/kentcdodds.com](https://github.com/kentcdodds/kentcdodds.com/tree/5efdefa313ddeec2e0027dd645b0e1f1eef662a8)
- 大量文章在 [`services/site/content/blog/`](https://github.com/kentcdodds/kentcdodds.com/tree/5efdefa313ddeec2e0027dd645b0e1f1eef662a8/services/site/content/blog)，正文是 MDX。
- [应用数据库 schema](https://github.com/kentcdodds/kentcdodds.com/blob/5efdefa313ddeec2e0027dd645b0e1f1eef662a8/services/site/app/utils/db/schema.server.ts) 包括用户、密码、会话、阅读记录、收藏、作业完成等，而不是博客正文表。
- [搜索迁移](https://github.com/kentcdodds/kentcdodds.com/blob/5efdefa313ddeec2e0027dd645b0e1f1eef662a8/services/search-worker/migrations/0001_lexical_search_schema.sql) 创建文档元数据、chunks 与 FTS5 表。

判断：这是功能丰富的运行时站点，但文章的权威源依然是 Git/MDX。数据库负责互动状态和可重建搜索索引。

### 样本六：Anand Chowdhary — Next.js 运行时读取 GitHub 内容文件/API

- 仓库：[AnandChowdhary/anandchowdhary.com](https://github.com/AnandChowdhary/anandchowdhary.com/tree/e7205250d3ac84f24ad20379f532e0ceaa70c764)
- [`app/api.ts`](https://github.com/AnandChowdhary/anandchowdhary.com/blob/e7205250d3ac84f24ad20379f532e0ceaa70c764/app/api.ts) 从 GitHub Pages JSON API 和 `raw.githubusercontent.com` 读取博客、项目、笔记等 Markdown，并使用 Next.js `revalidate` 缓存策略。
- [`package.json`](https://github.com/AnandChowdhary/anandchowdhary.com/blob/e7205250d3ac84f24ad20379f532e0ceaa70c764/package.json) 是 Next.js/Marked 技术栈，没有应用数据库客户端。

判断：内容仍是 Git 文件，但不一定与前端放在同一仓库，也不一定必须在构建时全部打包；服务器可以按需读取并缓存远端 Git 内容。

### 样本七：Simon Willison — Django + PostgreSQL，数据库原生内容与搜索

- 仓库：[simonw/simonwillisonblog](https://github.com/simonw/simonwillisonblog/tree/3138a8b480ba1c7b14ac96cd4ea463d808a97a63)
- [Django settings](https://github.com/simonw/simonwillisonblog/blob/3138a8b480ba1c7b14ac96cd4ea463d808a97a63/config/settings.py) 使用 `django.db.backends.postgresql`，并启用 Django Admin、auth、sessions 和 PostgreSQL 扩展。
- [`blog/models.py`](https://github.com/simonw/simonwillisonblog/blob/3138a8b480ba1c7b14ac96cd4ea463d808a97a63/blog/models.py) 定义 Tag、Entry、Blogmark、Quotation、Note 等内容模型及 PostgreSQL 搜索字段/索引；[migrations](https://github.com/simonw/simonwillisonblog/tree/3138a8b480ba1c7b14ac96cd4ea463d808a97a63/blog/migrations) 持续演进 schema。
- [README](https://github.com/simonw/simonwillisonblog/blob/3138a8b480ba1c7b14ac96cd4ea463d808a97a63/README.md) 说明内建全文检索和管理工具。

判断：这是长期运行的数据库原生个人博客，说明关系型数据库路线同样成熟，尤其适合复杂内容类型、后台工具和全文搜索。

### 样本八：Spring Boot + Vue + MySQL 前后端分离博客

- 仓库：[BFD2018/xjt-springboot-vue-blog](https://github.com/BFD2018/xjt-springboot-vue-blog/tree/f7d5597506fc9030dda61d7466f424f0075106c2)
- [README](https://github.com/BFD2018/xjt-springboot-vue-blog/blob/f7d5597506fc9030dda61d7466f424f0075106c2/README.md) 描述三个组件：Spring Boot API、Vue 管理后台、Vue 访客前台，并包含文章管理、Markdown 编辑、评论、留言、用户等功能。
- [`pom.xml`](https://github.com/BFD2018/xjt-springboot-vue-blog/blob/f7d5597506fc9030dda61d7466f424f0075106c2/springboot-server/pom.xml) 使用 Spring Boot Web、MySQL driver、MyBatis-Plus、Shiro，另有可选 Redis。
- [SQL schema](https://github.com/BFD2018/xjt-springboot-vue-blog/blob/f7d5597506fc9030dda61d7466f424f0075106c2/myFiles/xiong-blog.sql) 中 `t_blog` 直接保存正文 `content`，并有标签、评论、留言、用户/角色等表。

判断：这是典型的 Java CRUD 博客：数据库是文章的权威来源，前台、后台和 API 都是运行时系统。它证明当前项目技术栈完全能走数据库路线，但也展示了后台、鉴权、上传、评论、权限和运维会同时扩大工作量。此样本是架构证据，不代表推荐直接复用其较旧依赖或安全配置。

## 从样本能得出什么，不能得出什么

可以得出：

- “博客必须用数据库”是错误命题；成熟、持续更新的个人站可以只用 Git 文件。
- “静态站就不能有动态能力”也是错误命题；搜索、评论、分析、登录可由独立服务或附属数据库提供。
- “有数据库依赖就说明文章在数据库”不成立；必须区分正文、索引、缓存和互动状态。
- 框架通常不决定内容存储。Next.js、Nuxt、Astro 都可接文件、CMS 或数据库；工作流才是主导因素。
- 数据库 CMS 的优势主要在编辑与运行时能力，不在于“博客内容天然需要 SQL”。

不能得出：

- 本样本不能估算所有个人博客中数据库方案的占比。
- GitHub 开源样本天然偏向开发者和 Git 工作流，不能代表非技术作者使用 WordPress/Ghost 的总体情况。
- 仓库出现数据库或第三方服务，只能说明其代码架构，不能推断实际生产环境当前启用了所有可选功能。

## 对本项目的具体建议

### 一天 MVP：不要把内容迁入数据库

继续采用现有最终规格中的边界：

```text
Git 中的 JSON + Markdown（权威源）
              ↓
Spring Boot 2.7 只读 API
              ↓
Vue 3 个人主页与博客阅读界面
```

理由：

1. 首页只有公开介绍、作品、博客、联系方式四区，内容规模极小。
2. 当前只有一个站主，不需要多人权限、审稿、草稿协作或即时发布。
3. 一天上线的最大风险是旧项目依赖、公开 API、安全配置、CORS 和双服务部署，不是内容查询性能。
4. 引入数据库会额外带来 schema/migration、连接和密钥、种子数据、备份恢复、部署状态、管理后台鉴权等工作。
5. Markdown/JSON 能审查隐私字段、保留历史、回滚，并与已确认的“先看 MVP，再开发后台 CRUD”一致。
6. Spring Boot 只读 API 虽然比纯 SSG 多一个运行时服务，但它保留了用户要求的 Java + Vue 前后端分离技术栈，并为第二阶段留下稳定 API 边界。

这不是否定数据库，而是避免在还没有编辑工作流时过早选择数据模型。

### 第二阶段：按编辑工作流二选一

#### 选项 A：仍以 Git 文件为权威源，加网页编辑器

适用条件：仍然只有站主编辑；希望网页上写文章，但重视 Git 历史、PR/回滚；不需要评论、会员、复杂即时查询。

可研究 Git-backed CMS。TinaCMS 官方说明内容仍以 Markdown/MDX/JSON 存在 Git，`/admin` 编辑会提交回仓库；它内部数据库只是查询/编辑缓存，文件仍是单一事实来源：[What is TinaCMS?](https://tina.io/docs)、[Data Layer](https://tina.io/docs/reference/content-api/data-layer)。

此路线不等于零组件：仍要解决编辑身份、GitHub 授权、预览和部署触发，但不必维护文章关系型 schema。

#### 选项 B：Spring Boot + 关系型数据库 + 管理后台 CRUD

适用条件：需要登录后台即时发布、草稿/发布状态、文章修订、标签/搜索、多设备编辑、评论或后续多作者。

建议优先 PostgreSQL 或 MySQL，不建议把 H2 文件库作为生产主库。第一版表至少分开：

- `posts`：slug、标题、摘要、正文、状态、发布时间、更新时间
- `projects`：项目展示数据与排序
- `tags` / `post_tags`：只有确认需要标签后再加
- 管理员账户、会话/令牌：与公开访客完全隔离

评论、浏览量、分析、媒体上传不应因为“已经有数据库”就自动进入范围；每项都应独立评估隐私、反垃圾、安全和运维成本。

### 第二阶段的选择门槛

| 需求 | Git 文件 / Git-backed CMS | 关系型数据库 CRUD |
| --- | --- | --- |
| 单人、低频写作 | 优先 | 通常过重 |
| Git 历史和 PR 审核 | 原生适合 | 需额外审计机制 |
| 网页可视化编辑 | 需接 Git-backed CMS | 原生管理后台适合 |
| 保存即刻发布且不触发构建 | 较弱，需 SSR/ISR 或 webhook | 强 |
| 草稿、修订、多作者权限 | 可做但集成复杂 | 强 |
| 评论、会员、阅读记录 | 通常另接服务 | 强，但运维和安全成本高 |
| 全文搜索 | 构建索引/第三方即可 | 数据库搜索或独立引擎 |
| 一天上线 | 最适合 | 不适合当前范围 |

## 对票据拆分的影响

本调研不要求修改已拟定的 MVP 卡片顺序。T2“无数据库公共站点运行基线”和 T3–T5 的 JSON/Markdown tracer bullet 有充分的现实架构依据。

在 MVP 验收以后，再新增一个独立决策票据更合适：

> 明确站主写作、预览、发布、回滚、标签、搜索和多设备编辑流程，并在 Git-backed CMS 与 Spring Boot 数据库 CRUD 之间做选择。

在这个决策之前直接建文章表和管理后台，容易把尚未确认的博客工作流固化进 schema。

## 最终判断

- 如果问题是“现在个人博客有没有用数据库”：**有，而且 WordPress、Ghost、Halo、自建 Django/Spring Boot 博客都以数据库为核心。**
- 如果问题是“个人博客是否通常必须用数据库”：**不是。Git + Markdown/MDX + SSG/预渲染同样是活跃、成熟且特别适合技术个人站的架构。**
- 如果问题是“本项目 MVP 要不要数据库”：**现在不要。先以 Git 内容完成 A+C 原型对应的公开阅读体验；MVP 验收后，根据真实写作工作流决定第二阶段的数据层。**

