# 第二阶段个人主页与博客技术方案

状态：**已接受，可进入规格与票据拆分；尚未实施**  
范围：在已上线的 Vue 3 + Spring Boot 2.7.18 / Java 11 个人主页 MVP 上增加数据库驱动的博客写作与阅读能力。

## 1. 结论摘要

采用以下基线：

```text
Vue 3 公开站点 + Vue 3 管理后台
                │ REST/JSON
                ▼
Spring Boot 2.7.18（Public Blog API + Admin Blog API）
                │
                ▼
PostgreSQL（内容权威库、修订历史、分类/标签、搜索投影、Page View 聚合）
```

- 保持现有 Java + Vue 前后端分离，不在第二阶段同时升级 Spring Boot 3。
- PostgreSQL 成为个人站公开内容的唯一权威来源；Markdown 仍是博客正文格式，以 `TEXT` 保存，不把渲染后的 HTML 或 Git 文件当成另一份权威内容。
- 文章身份与文章修订分离：公开页面只读取明确发布的修订，后台继续编辑草稿不会污染线上版本。
- 每次正式发布保留一个 Published Revision；恢复历史版本时先复制为 Draft，确认后再次发布。
- 第二阶段暂不支持多设备同时编辑，不建设冲突检测、自动合并或实时协同能力。
- 搜索第一版使用 PostgreSQL `pg_trgm` 与 GIN 索引，只对标题和摘要做中文子串/相似度检索；分类和标签使用关系查询精确筛选，正文不进入搜索索引。
- 数据库结构使用 Flyway 迁移；CRUD 延续项目已有的 MyBatis-Plus，并为搜索、发布事务等能力编写显式 SQL。
- 管理接口与公开接口彻底分开；公开接口只返回已发布站点内容和 Published Revision，后台不开放公众注册。
- Admin Console 同时管理 Public Introduction、Project、联系方式、Blog Post、Category、Tag、Media Asset 和 Page View 统计，形成小型个人站 CMS；既有隐私字段禁区继续生效。

## 2. 为什么推荐 PostgreSQL

当前 legacy 代码虽然使用 MySQL，但 MVP 的 `publicsite` 切片尚未建立博客数据库，因此第二阶段没有需要迁移的既有博客数据。此时选择 PostgreSQL 的主要理由是：

1. 草稿、发布指针、分类、标签和匿名浏览统计天然适合关系模型与事务。
2. 发布操作可以在一个事务中同时切换 Published Revision、更新时间并刷新搜索投影，避免“页面已发布但搜索仍看不到”的中间状态。
3. `pg_trgm` 提供 GIN/GiST 索引，可加速相似度以及 `LIKE` / `ILIKE` 查询；最终选择的 Neon PostgreSQL 支持该扩展。
4. 中文博客不应直接把 PostgreSQL 默认全文分词当作完整中文搜索方案。第一版选择不依赖专用中文分词扩展的 trigram 检索，数据量和需求增长后再评估 Meilisearch / OpenSearch 等独立搜索引擎。
5. 与 MySQL 相比，切库会增加一次驱动和 SQL 适配成本；但当前博客模块是新建模块，这个成本小于未来从已积累文章和搜索模型中迁移的成本。

不推荐在本阶段引入 Elasticsearch/OpenSearch：个人博客数据量小，独立搜索服务会增加同步、监控、备份和部署成本。也不推荐 H2 作为生产主库。

## 3. 模块边界

### 3.1 公开博客

- 匿名浏览已发布文章。
- 按发布时间分页。
- 按分类、标签过滤。
- 搜索标题和摘要，不搜索正文。
- 记录匿名 Page View，只用于聚合访问统计。
- 永远不能通过公开 API 得到 Draft 或未发布修订。

### 3.2 管理后台

- 仅 Site Owner 可登录。
- 创建文章时默认进入 Draft。
- 编辑 Markdown、标题、摘要、slug、分类和标签。
- 保存 Draft 并显示“已保存 / 保存失败”状态。
- 预览 Draft。
- 发布、更新已发布文章、撤回和查看修订历史。
- 编辑 Public Introduction 的公开称呼、Hero 主标题、个人介绍和技能分组，但不能新增位置、机会状态、简历、肖像、经历时间线等被隐私边界排除的字段。
- 编辑作品区标题和可选副标题；副标题为空时前台完全不显示该行。
- 新增、排序和编辑 Project 的标题、成果说明、职责、技术栈、年份和外链，以及管理公开邮箱、GitHub 和版权标识。
- 管理 Category、Tag、Media Asset，并查看匿名 Page View 聚合。

Public Introduction、Project 和联系方式不建立服务端 Draft 或修订历史。Admin Console 提供即时预览，只有点击“保存并发布”才原子更新公开数据。

### 3.3 内容服务

- `Public Blog API` 负责公开只读查询。
- `Admin Blog API` 负责鉴权后的命令和草稿查询。
- `Publishing Service` 在单一数据库事务内完成发布。
- `Search Projection` 只索引 Published Revision，可从内容库重建。
- 现有 `ContentLoader` 在迁移完成前继续服务 MVP；数据库读路径验收后再切换，不直接重新激活 legacy 账户应用。

## 4. 推荐数据模型

### 4.0 个人站公开内容

- `public_introduction`：保存公开称呼、Hero 主标题、不涉及隐私的个人介绍和技能分组。Admin Console 可分别编辑这些字段；技能分组支持编辑分组名称、组内技术项及显示顺序，不把“后端 / 前端 / 交付”等名称硬编码为固定枚举。第二阶段以“全栈与桌面、AI 应用、数据与接口、工程交付、软件工程”五组现有内容为迁移初始值。
- `project_section_settings`：保存作品区标题和可选副标题。标题默认使用“个人项目展示”；副标题允许保存为空字符串，空值时前台不渲染元素，也不保留空白占位。
- `projects`：保存作品标题、成果说明、职责、技术栈、年份、显示顺序与代码仓库/演示外链。上述字段均可在 Admin Console 编辑，其中年份是作品自身的可编辑展示字段，不由系统当前年份自动生成。
- `contact_settings`：保存公开邮箱、GitHub 和版权标识，不建设联系表单。

这些内容与 Blog Post 一样以 PostgreSQL 为运行时唯一权威源。Admin Console 只能编辑规格已允许的字段，不能通过自由 JSON 绕过隐私边界。

数据库可以保存任意数量的 Project。每个 Project 可被标记为首页精选，精选数量为 0–3 个且 `featured_order` 在已选项目中唯一；首页只展示这些精选作品，第二阶段不新增公开作品列表页。

### 4.1 `posts`：文章身份与发布指针

| 字段 | 含义 |
| --- | --- |
| `id` | 稳定文章标识 |
| `slug` | 公开 URL 标识，唯一 |
| `category_id` | 主分类；草稿阶段可空，发布时按最终规则校验 |
| `draft_revision_id` | 当前 Draft 指向的修订 |
| `published_revision_id` | 当前 Published Revision；未发布时为空 |
| `first_published_at` | 首次发布时间 |
| `last_published_at` | 最近一次发布时间 |
| `created_at` / `updated_at` | 审计时间 |

文章是否公开由 `published_revision_id` 决定，不让一个模糊的 `status` 同时承担草稿、发布内容和修订关系。

### 4.2 `post_revisions`：不可变内容修订

| 字段 | 含义 |
| --- | --- |
| `id` / `post_id` / `revision_no` | 修订身份与顺序 |
| `title` / `summary` | 该修订的公开文本 |
| `body_markdown` | Markdown 正文权威内容 |
| `created_at` | 修订时间 |

公开文章引用一个不可变修订。修改已发布文章时先产生新的 Draft；只有再次点击发布，公开指针才原子切换到新修订。这样满足草稿、立即发布以及后续可选的版本恢复，同时不会让未完成修改污染线上文章。

每次正式发布都保留 Published Revision。恢复旧版本时不直接覆盖线上内容，而是把目标修订复制为新的 Draft，待 Site Owner 预览并再次发布。编辑过程中的浏览器本地暂存不进入修订历史。

### 4.3 分类与标签

- `categories`：少量、稳定、层级扁平的栏目。
- `tags`：可复用主题标签，`slug` 唯一。
- `post_tags`：Blog Post 与 Tag 的多对多关系。

一篇文章只有一个主分类、可以有多个标签。Draft 可以暂不分类，发布时必须选择主分类。分类回答“这篇文章属于哪个栏目”，标签回答“它涉及哪些主题”，避免两套体系语义重叠。

系统内置不可删除的“未分类”Category。删除正在被 Blog Post 使用的 Category 时，在同一事务中把关联文章迁移到“未分类”再删除；删除 Tag 时直接解除全部文章关联再删除。

### 4.4 搜索投影

`post_search_documents` 只保存 Published Revision 的文章标识、标题、摘要和更新时间。发布事务同步替换该投影；删除或撤回时同步移除。投影可从 Published Revision 全量重建，因此不是第二份内容权威源。

第一版搜索规则：

- 标题权重高于摘要；正文不参与搜索。
- 分类和标签是精确过滤条件，不混入自由文本猜测。
- 对可搜索文本建立 `pg_trgm` GIN 索引。
- 允许最少 1 个字符、最多 50 个字符。1–2 字符查询使用有结果上限的 `ILIKE`；3 字符及以上使用 `pg_trgm` 索引。特别测试短查询的扫描范围和响应时间。
- 用真实中文文章做召回率与查询计划验收；不宣称已实现语言学意义上的中文分词。

### 4.5 浏览记录

服务端只记录匿名 Page View，用于计算文章访问次数与趋势；不存原始 IP、完整 User-Agent 或可长期追踪个人的标识。Visitor 的 Reading History 只保存在浏览器本地，不上传、不跨设备同步，也不需要 Visitor 账户。

推荐表 `post_daily_views` 按 `post_id + view_date` 聚合保存计数，而不是永久保存逐次访问事件。若需要基本防刷，可在短时内存窗口中做去重与速率限制，但不把设备指纹写入长期数据库。

同一浏览器对同一文章每天最多上报一次 Page View，去重标记只保存在本地。服务端保留 24 个月每日明细，文章累计访问量长期保存，不计算独立访客人数。

`/blog` 列表页展示当前设备最近阅读的最多 10 篇 Published Revision，并提供“一键清除”。Reading History 不上传服务器；本地记录指向已归档或不存在的文章时，在渲染时自动移除。

### 4.6 媒体资源

应用通过统一的 `MediaStorage` 接口隔离文件存储实现：

- 本地开发使用 `LocalMediaStorage`，把文件写入显式配置的开发目录。
- 生产使用 `R2MediaStorage`，通过 S3 兼容 API 把文件写入私有 Cloudflare R2 bucket。
- PostgreSQL 的 `media_assets` 只保存 object key、公开 URL、原始文件名、MIME 类型、字节数、宽高、校验值、替代文本和创建时间。
- Markdown 只保存媒体公开 URL，不保存二进制内容。
- 删除媒体前查询文章引用；仍被 Draft 或 Published Revision 引用时拒绝删除。
- 第一版只接受 JPEG、PNG、WebP，单张不超过 5 MB、尺寸不超过 4096×4096；拒绝 SVG 和 GIF，不提供在线裁剪或格式转换。

在尚未使用独立域名的阶段，使用一个 GET/HEAD-only Cloudflare Worker 绑定私有 R2 bucket，并通过 `workers.dev` 平台子域名读取图片。上传和删除仍只能由通过 GitHub OAuth 鉴权的 Spring Boot Admin API 执行。以后启用独立域名时，可以再为同一 Worker 增加 `assets.<domain>` 入口；原 `workers.dev` URL 保持可用，避免已有 Markdown 图片失效。

Render 免费 Web Service 的本地文件系统是临时的，休眠、重启或重新部署都会丢失上传文件，而且免费实例不能挂 Persistent Disk。因此生产环境不能把普通本地文件夹当作持久媒体库。升级为付费 Render 服务后虽然可以挂 Persistent Disk，但磁盘只能由一个服务实例访问并阻止横向扩容，更适合作为低成本单机备选，而不是本方案首选。

## 5. 核心工作流

### 5.1 新文章

1. 后台创建 Blog Post，生成首个 Draft。
2. Site Owner 点击“保存”才把 Draft 写入服务器；编辑过程每隔数秒暂存到当前浏览器，用于意外关闭后的本地恢复。
3. 预览接口只对 Site Owner 返回 Draft。
4. 点击发布后，服务在事务内更新 `published_revision_id`、发布时间和搜索投影。

### 5.2 修改已发布文章

1. Published Revision 保持公开不变。
2. 第一次修改创建新的 Draft 修订。
3. 保存 Draft 不改变访客看到的内容。
4. 再次发布后，公开指针原子切换；旧修订保留，可回滚。

### 5.3 撤回、归档与删除

- 从未发布的 Draft 可以永久删除。
- 曾经发布的 Blog Post 只能撤回为 Archived Post，Published Revision 不再公开，但历史修订继续保留。
- Archived Post 可以恢复成 Draft，预览后重新发布；Admin Console 不提供对曾发布文章的硬删除。

## 6. API 草案

第二阶段的正式 API 不使用 URL 版本号。公开资源使用领域语义路径，管理资源统一以 `/api/admin/...` 隔离。现有 `/api/v1` 仅在迁移稳定期作为 MVP 文件读路径的短期回退；不得新增或依赖 `/api/v2` 或其他 URL 版本路径。未来若出现不兼容 API 变更，必须单独评审并新增 ADR，再决定是否需要版本机制。

公开 API：

- `GET /api/site/introduction`
- `GET /api/site/project-section`
- `GET /api/projects`
- `GET /api/site/contact`
- `GET /api/posts?page=&size=&category=&tag=&q=`
- `GET /api/posts/{slug}`
- `GET /api/categories`
- `GET /api/tags`
- `POST /api/posts/{slug}/views`
- `GET /rss.xml`
- `GET /sitemap.xml`

管理 API：

- `GET /api/admin/site/introduction`
- `PUT /api/admin/site/introduction`
- `GET /api/admin/site/project-section`
- `PUT /api/admin/site/project-section`
- `GET /api/admin/site/contact`
- `PUT /api/admin/site/contact`
- `GET /api/admin/projects`
- `POST /api/admin/projects`
- `PUT /api/admin/projects/{id}`
- `DELETE /api/admin/projects/{id}`
- `POST /api/admin/posts`
- `GET /api/admin/posts?state=draft|published`
- `GET /api/admin/posts/{id}`
- `PUT /api/admin/posts/{id}/drafts/current`
- `POST /api/admin/posts/{id}/publish`
- `POST /api/admin/posts/{id}/unpublish`
- `GET /api/admin/posts/{id}/revisions`
- `POST /api/admin/posts/{id}/revisions/{revisionId}/restore`

公开 slug 修改时写入 `post_redirects`。任何当前或历史 slug 都全局唯一；访问旧 slug 时返回永久 `301` 到当前地址。

第一版不支持定时发布，只支持 Site Owner 主动点击后的立即发布。文章列表和详情使用 `ETag + Cache-Control: no-cache`；发布事务成功后，下一次公开请求重新验证并读取新 Published Revision。Media Asset 使用不可变 URL 和长期缓存。

`/rss.xml` 与 `/sitemap.xml` 只读取 Published Revision 和公开页面，绝不包含 Draft、Archived Post 或 Admin Console URL。它们在请求时根据数据库生成，并使用 ETag 重新验证。

RSS Feed 只输出最近 20 篇文章的标题、摘要、发布日期、Category、Tag、永久链接和稳定 GUID，不包含 Markdown 全文。RSS 是本站向外提供的内容聚合格式，订阅工具通常定期拉取；接收或导入其他站点的 RSS、WebSub 主动推送，以及向微信公众号等第三方平台同步发布都不属于第二阶段。

Archived Post 的当前 slug 和历史 slug 都返回站内 404；对应 URL 同时从 RSS Feed 和 Sitemap 移除。归档恢复并重新发布后，原当前 slug 恢复可访问。

## 7. 认证与安全基线

- 只有一个 Site Owner，不开放注册、找回密码、角色管理或 Visitor 账户。
- 不复用当前硬编码密钥、14 天长效 JWT 和 legacy 注册入口。
- 使用 Spring Security OAuth2 Client 接入 GitHub OAuth，并只允许配置中的唯一 GitHub 账号进入 Admin Console；认证成功不代表自动取得权限，必须再通过 allowlist 校验。
- OAuth 回调成功后，Spring Boot 向 Vue 返回一次性交换码；Vue 交换成 8 小时有效的随机不透明会话令牌。令牌只存 `sessionStorage`，数据库只存令牌哈希，关闭浏览器后重新登录。
- 管理 API 接受 `Authorization: Bearer <opaque-token>`，不使用旧 JWT 和刷新令牌；生产 CORS 只允许精确前端来源。
- Markdown 保存时禁止危险原始 HTML；渲染后继续使用 DOMPurify 兜底。
- 所有管理命令写审计时间；数据库凭据只从环境变量/秘密管理注入。

## 8. 数据迁移与发布

### 8.1 数据库托管候选

生产数据库使用 Neon PostgreSQL Free：它没有 Render Free PostgreSQL 的 30 天到期限制，支持标准 PostgreSQL 连接与 `pg_trgm`，空闲计算可自动缩容，当前免费额度适合低流量个人博客。代价是空闲唤醒延迟、每项目 0.5 GB 存储限制，以及免费计划只有短恢复窗口；必须另建逻辑备份。

不选择 Render Free PostgreSQL，因为数据库在创建 30 天后到期且没有备份。Supabase Free PostgreSQL 可以长期保存数据，但低活动项目可能在 7 天后暂停，且免费计划同样没有托管日备份，因此只作为备选。

### 8.2 迁移步骤

1. 用 Flyway 创建表、约束、索引与扩展。
2. 编写一次性导入器，把现有公开介绍、作品、联系方式、`posts.json` 和 Markdown 转成数据库内容、Blog Post、初始修订和 Published Revision。
3. 在数据库 API 后面完成公开列表/详情对照测试。
4. 双读比对通过后把 Vue 切到正式的无版本领域语义 API；保留旧文件内容和 `/api/v1` 作为短期可回退资产。回退通过恢复旧前端和旧后端部署完成，而不是长期并存 URL 版本。
5. 完成数据库备份与恢复演练后再删除运行时文件读路径。

导入后 PostgreSQL 成为唯一运行时权威源；原 JSON 和 Markdown 只保留为一段时间内的只读迁移快照，不再与数据库双向同步。

### 8.3 逻辑备份

GitHub Actions 每天通过 `pg_dump --format=custom` 导出 Neon PostgreSQL，用离线保存的公钥加密后上传到独立私有 R2 backup bucket。保留最近 14 份每日备份和 8 份每周备份；数据库连接凭据和 R2 写入凭据只存 GitHub Actions Secrets。

上线前至少完成一次恢复演练：下载并解密备份，在新建 Neon 数据库执行 `pg_restore`，运行 Flyway 校验并核对 Published Revision、搜索投影、站点设置和 Page View 聚合。媒体二进制已独立存在 R2 media bucket，不进入 PostgreSQL dump。

切换应是可回退的，不在一次部署里同时升级 Boot 3、重写前端框架、替换持久层和上线新博客能力。

## 9. 测试与验收重点

- Flyway 能从空库完整建库，并能在已有数据上升级。
- 使用真实 PostgreSQL 的集成测试覆盖发布事务、唯一 slug、标签筛选和 `pg_trgm` 查询。
- Draft、旧修订和撤回文章无法通过任何公开接口读取。
- 发布后公开详情、列表、分类、标签和搜索在目标时限内一致可见。
- Markdown XSS、管理令牌泄漏与撤销、OAuth 一次性交换码、CORS 和速率限制通过安全测试。
- 数据库备份可恢复，恢复后 Published Revision 与搜索投影一致。

## 10. 决策状态

设计树已经清空，没有未确认决策。本文记录的范围、领域边界、技术选型、数据模型、发布流程、安全基线、备份方式和验收重点已经形成共同理解；后续新增外部 RSS 聚合、多平台同步或其他动态能力时必须重新评审范围。

已确认：有搜索词时依次按标题匹配度、摘要匹配度和发布时间排序；只有分类或标签筛选时按发布时间倒序。Admin Console 展示全站累计、最近 30 天趋势、访问量最高的 10 篇文章，以及单篇文章 30/90 天趋势，不展示访客身份或画像。未引用 Media Asset 只在媒体库中标记并由 Site Owner 手动清理，删除前再次检查 Draft 与 Published Revision 引用。

评论、点赞、收藏、Visitor 账户和访客登录明确不进入第二阶段。

已确认：系统自动生成 RSS Feed 和 Sitemap；正在使用的 Category 删除后，关联文章迁移到受保护的“未分类”，Tag 删除时直接解除关系；数据库每天加密备份到独立私有 R2 bucket，并保留 14 份每日备份和 8 份每周备份。

这些问题确认后，再把本文状态改为“已接受”。

## 11. 事实依据

- PostgreSQL 当前内置全文检索解析器只有默认解析器，分词结果还要经过字典规范化；核心并不提供中文词法分词：[Text Search Parsers](https://www.postgresql.org/docs/current/textsearch-parsers.html)、[Controlling Text Search](https://www.postgresql.org/docs/current/textsearch-controls.html)。
- `pg_trgm` 按连续字符 trigram 做相似度，并提供 GIN/GiST 索引以加速相似度、`LIKE` 和 `ILIKE` 查询：[pg_trgm](https://www.postgresql.org/docs/current/pgtrgm.html)。
- Render 托管 PostgreSQL 的支持扩展清单明确包含 `pg_trgm`，但不包含需要服务器安装二进制和词典的 `pg_jieba`：[Supported Extensions for Render Postgres](https://render.com/docs/postgresql-extensions)。
- Neon 提供 `pg_trgm` 扩展并允许通过 `CREATE EXTENSION pg_trgm` 启用：[The pg_trgm extension](https://neon.com/docs/extensions/pg_trgm)。
- Cloudflare R2 Standard 每月包含免费存储和操作额度且直接出口免费；Workers Free 提供每日请求额度，可通过 R2 binding 在 `workers.dev` 上提供只读媒体入口：[R2 Pricing](https://developers.cloudflare.com/r2/pricing/)、[Workers Pricing](https://developers.cloudflare.com/workers/platform/pricing/)、[R2 Workers API](https://developers.cloudflare.com/r2/get-started/workers-api/)。
- Render Free PostgreSQL 固定 1 GB、30 天后到期且没有备份，不能作为第二阶段长期内容库：[Render Free PostgreSQL](https://render.com/docs/free)。
- Neon Free 当前每项目包含 0.5 GB 存储和 100 CU-hours，空闲 5 分钟后自动缩容，并提供最长 6 小时或 1 GB 数据变化的恢复窗口：[Neon Pricing](https://neon.com/pricing)、[Scale to Zero](https://neon.com/docs/introduction/scale-to-zero)。
- Supabase Free 当前包含 500 MB 数据库，但低活动项目可能在 7 天后暂停；免费项目需要自行做逻辑备份：[Supabase Pricing](https://supabase.com/pricing)、[Project Pausing](https://supabase.com/docs/guides/platform/free-project-pausing)、[Database Backups](https://supabase.com/docs/guides/platform/backups)。
