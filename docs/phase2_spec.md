# 第二阶段规格：数据库驱动的个人站 CMS 与动态博客

状态：已确认，待拆分实施卡片  
基线：Vue 3 + Spring Boot 2.7.18 + Java 11，保持前后端分离

## 问题陈述（Problem Statement）

当前 Personal Site 的 Public Introduction、Project、联系方式和 Blog Post 由随仓库发布的 JSON 与 Markdown 文件提供。这个 MVP 适合快速上线，但 Site Owner 无法通过网页创建 Draft、立即发布、管理修订、分类、Tag、搜索、Media Asset 或匿名 Page View，也无法在不修改仓库和重新部署的情况下调整首页公开内容。

第二阶段需要把 Personal Site 扩展为单一 Site Owner 使用的小型 CMS：Visitor 继续匿名、稳定地浏览公开内容；Site Owner 通过受保护的 Admin Console 管理全站内容和 Markdown Blog Post。系统必须明确区分 Draft 与 Published Revision，保证后台未完成的修改不会污染公开页面，同时保持隐私边界、低成本部署和可恢复性。

## 解决方案（Solution）

新增 Vue 3 Admin Console 和 Spring Boot Admin API，并把 PostgreSQL 设为 Public Introduction、Project、联系方式、Blog Post、修订、Category、Tag、搜索投影和 Page View 聚合的唯一运行时权威源。Markdown 继续作为 Blog Post 正文格式，以文本保存；公开页面只读取 Published Revision。

Site Owner 通过 GitHub OAuth 登录，只有 allowlist 中唯一 GitHub 身份可以进入 Admin Console。生产 PostgreSQL 使用 Neon，数据库结构由 Flyway 管理；生产图片进入 Cloudflare R2，数据库只保存 Media Asset 元数据。现有 JSON 与 Markdown 通过一次性迁移导入数据库，在数据库读路径验收后退出运行时。

Visitor 可以分页阅读 Published Revision，按 Category、Tag 和标题/摘要自由文本搜索；服务端只保存匿名聚合 Page View，Reading History 仅保存在当前浏览器。系统同时生成 RSS Feed 和 Sitemap。

## 用户故事（User Stories）

1. 作为 Visitor，我希望无需登录即可访问 Personal Site，以便直接查看公开介绍、Project 和 Blog Post。
2. 作为 Visitor，我希望首页只显示 Site Owner 精选的最多三个 Project，以便快速了解最有代表性的工作。
3. 作为 Visitor，我希望首页在没有精选 Project 时仍能正常显示，以便内容尚未准备好时页面不会报错。
4. 作为 Visitor，我希望 Public Introduction 不包含位置、求职状态、简历、肖像、经历时间线等隐私信息，以便 Site Owner 只公开必要内容。
5. 作为 Visitor，我希望看到“构建可靠的全栈应用”Hero 标题和最新个人介绍，以便理解 Site Owner 当前的成长方向。
6. 作为 Visitor，我希望看到按能力方向组织的技能与技术栈，以便理解 Site Owner 的全栈、AI、数据、交付和软件工程能力。
7. 作为 Visitor，我希望技能分组名称和内容可以随 Site Owner 成长而变化，以便页面不会被旧的固定技术分类限制。
8. 作为 Visitor，我希望浏览按发布时间倒序排列的 Blog Post 列表，以便优先阅读最新内容。
9. 作为 Visitor，我希望打开稳定 slug 对应的 Blog Post 详情，以便保存和分享长期有效的链接。
10. 作为 Visitor，我希望旧 slug 永久重定向到当前 slug，以便文章改名后旧链接仍然可用。
11. 作为 Visitor，我希望只看到 Published Revision，以便不会读到未完成 Draft 或已撤回内容。
12. 作为 Visitor，我希望按 Category 精确过滤 Blog Post，以便浏览一个稳定栏目下的内容。
13. 作为 Visitor，我希望按 Tag 精确过滤 Blog Post，以便发现涉及同一主题的写作。
14. 作为 Visitor，我希望用最少一个字符搜索标题和摘要，以便快速找到相关 Blog Post。
15. 作为 Visitor，我希望搜索结果优先显示标题匹配，其次是摘要匹配和发布时间，以便结果更符合查询意图。
16. 作为 Visitor，我希望正文不参与第一版搜索，以便个人站在低数据量下保持简单、可预测的搜索行为。
17. 作为 Visitor，我希望当前设备记录最近阅读的最多十篇 Published Revision，以便继续阅读。
18. 作为 Visitor，我希望可以一键清除 Reading History，以便控制本地浏览记录。
19. 作为 Visitor，我希望 Reading History 不上传服务器也不跨设备同步，以便不需要 Visitor 账户并减少隐私风险。
20. 作为 Visitor，我希望文章访问不会保存原始 IP、完整 User-Agent 或设备指纹，以便匿名阅读不被长期追踪。
21. 作为订阅者，我希望通过 RSS Feed 获取最近二十篇 Blog Post 的标题、摘要和链接，以便使用订阅工具跟踪更新。
22. 作为搜索引擎，我希望通过 Sitemap 发现当前公开页面和 Published Revision，以便正确索引 Personal Site。
23. 作为 Site Owner，我希望使用 GitHub OAuth 登录 Admin Console，以便无需维护另一套密码。
24. 作为 Site Owner，我希望只有 allowlist 中的唯一 GitHub 身份可以获得管理权限，以便认证成功的其他 GitHub 用户仍无法进入后台。
25. 作为 Site Owner，我希望创建 Blog Post 时自动进入 Draft，以便内容在明确发布前保持私有。
26. 作为 Site Owner，我希望编辑标题、摘要、Markdown 正文、slug、Category 和 Tag，以便完成一篇结构化 Blog Post。
27. 作为 Site Owner，我希望点击保存后看到明确的成功或失败状态，以便知道 Draft 是否已经写入服务器。
28. 作为 Site Owner，我希望编辑过程定期暂存到当前浏览器，以便意外关闭页面后可以本地恢复尚未保存的内容。
29. 作为 Site Owner，我希望预览 Draft，以便在发布前检查 Markdown、图片和元数据。
30. 作为 Site Owner，我希望点击发布后立即公开当前 Draft，以便不依赖定时任务或重新部署。
31. 作为 Site Owner，我希望修改已发布 Blog Post 时公开版本保持不变，以便未完成修改不会影响 Visitor。
32. 作为 Site Owner，我希望每次正式发布都保留 Published Revision，以便查看历史并恢复旧内容。
33. 作为 Site Owner，我希望恢复旧修订时先复制为新的 Draft，以便预览确认后再发布，而不是直接覆盖线上内容。
34. 作为 Site Owner，我希望撤回已发布 Blog Post 并保留为 Archived Post，以便内容下线后仍可恢复。
35. 作为 Site Owner，我希望永久删除从未发布的 Draft，以便清理无价值草稿。
36. 作为 Site Owner，我希望已发布过的 Blog Post 不能从 Admin Console 硬删除，以便保留审计和恢复能力。
37. 作为 Site Owner，我希望管理 Category，并在删除正在使用的 Category 时把关联 Blog Post 迁移到受保护的 Uncategorized Category，以便文章关系始终有效。
38. 作为 Site Owner，我希望删除 Tag 时自动解除全部 Blog Post 关联，以便不残留无效关系。
39. 作为 Site Owner，我希望上传 JPEG、PNG 或 WebP Media Asset 并获得稳定公开 URL，以便在 Markdown 中插入图片。
40. 作为 Site Owner，我希望系统拒绝超过 5 MB、超过 4096×4096、SVG 或 GIF 文件，以便控制成本和攻击面。
41. 作为 Site Owner，我希望仍被 Draft 或 Published Revision 引用的 Media Asset 不能删除，以便公开文章不会出现坏图。
42. 作为 Site Owner，我希望未引用 Media Asset 只被标记并由我手动清理，以便系统不会误删仍需保留的图片。
43. 作为 Site Owner，我希望在 Admin Console 编辑公开称呼、Hero 主标题、个人介绍和技能分组，以便主页内容可以持续更新。
44. 作为 Site Owner，我希望新增、删除、改名、排序技能分组和技术项，以便技术能力不受固定枚举限制。
45. 作为 Site Owner，我希望编辑作品区标题并允许副标题为空，以便首页作品区文案可以保持简洁。
46. 作为 Site Owner，我希望新增、编辑、排序 Project，并管理标题、成果说明、职责、技术栈、年份和外链，以便作品展示保持准确。
47. 作为 Site Owner，我希望把任意数量的 Project 保存在数据库中，并选择零至三个首页精选，以便内容库和首页展示解耦。
48. 作为 Site Owner，我希望管理公开邮箱、GitHub 链接和版权标识，以便联系方式无需修改代码。
49. 作为 Site Owner，我希望 Public Introduction、Project 和联系方式只在点击“保存并发布”后原子更新，以便取消编辑时不会影响公开页面。
50. 作为 Site Owner，我希望查看全站累计 Page View、最近三十天趋势、访问量最高的十篇 Blog Post 和单篇三十/九十天趋势，以便了解内容表现。
51. 作为 Site Owner，我希望统计页面不展示 Visitor 身份或画像，以便分析能力保持在匿名聚合范围内。
52. 作为 Site Owner，我希望数据库和 Media Asset 有独立备份，以便托管服务故障后可以恢复 Personal Site。
53. 作为维护者，我希望从空 PostgreSQL 数据库运行 Flyway 即可得到完整结构，以便环境可重复创建。
54. 作为维护者，我希望现有 JSON 与 Markdown 可以一次性导入 PostgreSQL，以便第二阶段切换不丢失 MVP 内容。
55. 作为维护者，我希望搜索投影可以从 Published Revision 全量重建，以便投影损坏时无需恢复第二份内容源。
56. 作为维护者，我希望数据库切换可以回退，以便上线失败时仍能短期恢复旧的只读文件内容。

## 实现决策（Implementation Decisions）

- 保持 Vue 3、Spring Boot 2.7.18 和 Java 11，不在本阶段同时升级 Spring Boot 3 或重写前端框架。
- 公开站点和 Admin Console 都使用 Vue 3；Spring Boot 提供相互隔离的 Public API 与 Admin API。
- PostgreSQL 是 Public Introduction、Project、联系方式、Blog Post、修订、Category、Tag、搜索投影和 Page View 聚合的唯一运行时权威源。
- 生产 PostgreSQL 首选 Neon Free，并使用标准 JDBC，避免领域模型绑定托管厂商。
- 使用 Flyway 管理表、约束、索引和 `pg_trgm` 扩展；CRUD 延续 MyBatis-Plus，搜索与发布事务使用显式 SQL。
- Blog Post 身份与修订分离。Blog Post 保存当前 Draft 和 Published Revision 指针；公开性由 Published Revision 指针决定。
- Published Revision 不可变。恢复历史版本时复制为新的 Draft，Site Owner 预览后再次发布。
- 发布事务原子更新 Published Revision 指针、发布时间和搜索投影；撤回时同步移除公开搜索投影。
- Markdown 正文以 PostgreSQL `TEXT` 保存，不把渲染 HTML 或 Git 文件作为第二权威源。
- 搜索投影只包含 Published Revision 的标题、摘要和更新时间。标题权重高于摘要，正文不参与搜索。
- 搜索允许 1–50 个字符。1–2 字符使用有结果上限的 `ILIKE`，3 字符及以上使用 `pg_trgm` GIN 索引；Category 与 Tag 使用关系查询精确过滤。
- 只有 Category 或 Tag 过滤时按发布时间倒序；存在搜索词时依次按标题匹配度、摘要匹配度和发布时间排序。
- Public Introduction 只包含公开称呼、Hero 主标题、隐私安全个人介绍和可排序技能分组，不包含 Hero 眉题及任何被排除的个人资料字段。
- 技能分组不是固定枚举。第二阶段迁移初始值为“全栈与桌面、AI 应用、数据与接口、工程交付、软件工程”，之后可由 Admin Console 增删、改名和排序。
- 作品区设置包含标题和可选副标题；空副标题不渲染 DOM 元素，也不保留占位。
- 数据库可保存任意数量的 Project；首页精选数量为 0–3，`featured_order` 在精选范围内唯一。第二阶段不新增公开 Project 列表页。
- Public Introduction、Project、作品区设置和联系方式不建立 Draft 或修订历史；Admin Console 提供即时预览，点击“保存并发布”后原子更新。
- 每篇 Blog Post 只有一个主 Category，可有多个 Tag。Draft 可暂不分类，发布时必须有 Category。
- 系统内置不可删除的 Uncategorized Category。删除正在使用的 Category 时在同一事务迁移关联 Blog Post；删除 Tag 时解除全部关联。
- 服务端按 Blog Post 和日期聚合 Page View，不长期保存逐次事件、原始 IP、完整 User-Agent 或设备指纹。每日明细保留二十四个月，累计值长期保留。
- 同一浏览器对同一 Blog Post 每天最多上报一次 Page View；去重标记只保存在浏览器本地。
- Reading History 只保存在浏览器本地，最多十篇，支持一键清除，并在引用 Archived Post 或不存在内容时自动清理。
- 生产 Media Asset 存入私有 Cloudflare R2 bucket；PostgreSQL 只保存 object key、公开 URL、文件名、MIME、大小、宽高、校验值、替代文本和创建时间。
- 本地开发通过统一 `MediaStorage` 接口使用显式开发目录；生产使用 S3 兼容的 R2 实现。Render Free 的临时文件系统不得作为持久媒体库。
- 未使用独立域名时，使用 GET/HEAD-only Cloudflare Worker 和 `workers.dev` 子域名读取 R2 图片；上传和删除只能经过已认证 Admin API。
- GitHub OAuth 只用于认证唯一 Site Owner。认证后必须再次匹配 allowlist，不开放注册、角色管理、本地密码或 Visitor 账户。
- OAuth 回调返回一次性交换码，Vue 换取八小时有效的随机不透明令牌。令牌只存 `sessionStorage`，数据库只保存令牌哈希；不复用 legacy JWT。
- Markdown 保存时拒绝危险原始 HTML，渲染后继续使用 DOMPurify；生产 CORS 只允许精确前端来源。
- 正式数据库 API 使用无版本的领域语义路径：公开资源位于 `/api/site/...`、`/api/projects`、`/api/posts/...`、`/api/categories` 与 `/api/tags`，受保护的管理资源位于 `/api/admin/...`。不得新增或依赖 `/api/v2` 或其他 URL 版本路径；现有 `/api/v1` 仅是 MVP 文件读路径的短期回退，而非需要长期维护的公开契约。若未来需要不兼容 API 变更，必须单独评审并新增 ADR 后再决定版本策略。
- 修改公开 slug 时写入全局唯一的历史重定向；旧 slug 返回永久 `301`。Archived Post 的当前和历史 slug 返回站内 `404`。
- RSS Feed 只输出最近二十篇 Published Revision 的标题、摘要、发布日期、Category、Tag、永久链接和稳定 GUID，不输出 Markdown 全文。
- Sitemap、RSS Feed 和公开缓存只包含当前公开内容，绝不包含 Draft、Archived Post 或 Admin Console URL。
- 文章列表和详情使用 `ETag + Cache-Control: no-cache` 重新验证；Media Asset 使用不可变 URL 和长期缓存。
- 使用一次性导入器迁移现有 JSON 与 Markdown。正式数据库 API 与旧文件输出双读比对通过后，Vue 切换到无版本的领域语义路径；旧文件和 `/api/v1` 只作为短期只读回退快照。回退通过恢复旧前端和旧后端部署完成，而不是通过长期并存的 URL 版本。
- GitHub Actions 每日使用 `pg_dump --format=custom` 备份 Neon PostgreSQL，以离线公钥加密后写入独立私有 R2 backup bucket，保留十四份每日备份和八份每周备份。
- 上线前必须完成一次恢复演练，并核对 Published Revision、搜索投影、站点设置和 Page View 聚合。

## 测试决策（Testing Decisions）

- 主要验收接缝是“HTTP/API → Spring Boot 应用服务 → 真实 PostgreSQL”。测试外部可观察行为和事务结果，不断言控制器、Mapper 或内部方法的实现细节。
- 使用 Testcontainers 或等价的真实 PostgreSQL 环境验证 Flyway、约束、事务、`pg_trgm` 查询计划和 PostgreSQL 特有行为，不使用 H2 代替关键数据库测试。
- 延续现有 Spring Boot `MockMvc` 高层接缝验证公开 API 状态码、JSON 契约、缓存、CORS、隐私字段禁区以及 Draft/Archived Post 不可见性。
- 对发布、再次编辑、恢复修订、撤回、Category 删除迁移和 Tag 删除解绑编写事务级集成测试，验证失败时不会产生部分更新。
- 对唯一 slug、历史 slug、精选 Project 数量与顺序、技能分组名称唯一性、Media Asset 引用保护建立数据库约束及行为测试。
- 使用真实中文标题和摘要验证 1–2 字符 `ILIKE`、3 字符以上 trigram 检索、排序稳定性、结果上限和查询计划；不以内部 SQL 字符串作为断言目标。
- 前端以路由和页面行为作为接缝，验证 Visitor 浏览/过滤/搜索/Reading History，以及 Site Owner 登录、编辑、预览、保存、发布和错误反馈。
- 对关键全栈路径增加少量浏览器测试：GitHub OAuth 测试替身登录、创建 Draft、发布、Visitor 可见、修改 Draft 不影响 Published Revision、再次发布后更新。
- 安全测试覆盖 OAuth allowlist、一次性交换码、会话过期与撤销、Admin API 未授权访问、精确 CORS、Markdown XSS、媒体类型/大小限制和管理令牌不出现在日志或 URL 中。
- 备份验收必须从加密备份恢复到新数据库，运行 Flyway 校验并通过公开 API 核对内容；仅验证备份文件存在不算通过。
- 迁移验收比较旧文件与正式数据库 API 的公开语义，包括 Public Introduction、最多三个精选 Project、Blog Post 内容、slug 和发布时间，并验证生产路径中不存在 `/api/v2` 依赖。
- 好测试只验证 Visitor 或 Site Owner 能观察到的结果、持久化状态和安全边界；避免为 DTO、简单 getter 或框架配置编写重复的细粒度测试。

## 不在范围内（Out of Scope）

- 多设备同时编辑、实时协作、自动合并和乐观锁冲突 UI。
- 定时发布；第一版只支持 Site Owner 主动点击后的立即发布。
- Visitor 账户、注册、登录、评论、点赞、收藏和跨设备 Reading History。
- 联系表单；联系方式仍为公开邮箱和 GitHub 链接。
- 正文全文搜索、中文语言学分词、Meilisearch、Elasticsearch 或 OpenSearch。
- 接收或导入其他站点 RSS、WebSub 主动推送、微信公众号或其他平台同步发布。
- 自动删除未引用 Media Asset、在线裁剪、格式转换、SVG 和 GIF 上传。
- 独立 Project 列表页、个人资料页、位置、求职状态、简历、肖像和经历时间线。
- 独立域名与备案；继续使用平台 HTTPS 子域名，独立域名属于后续阶段。
- Spring Boot 3、Java 大版本升级、前端框架重写或重新启用 legacy 账户系统。
- 在同一次上线中同时删除全部文件回退资产；旧 JSON 与 Markdown 在稳定期内保留为只读迁移快照。

## 补充说明（Further Notes）

- 本规格遵循 PostgreSQL 作为动态个人站内容权威库、GitHub OAuth 只认证唯一 Site Owner 两项既有架构决策。
- 第二阶段的目标是可信、可恢复的小型个人站 CMS，不是多租户博客平台。
- 当前页面已经采用新的 Public Introduction 和五组能力展示；数据库迁移时应把它们作为初始值，而不是恢复旧的“Java · Spring Boot · Vue”眉题或三组技能枚举。
- Render API 免费实例可能休眠，公开前端继续保留启动提示和有限重试；数据库与媒体持久性不能依赖 Render 本地文件系统。
- 规格发布后应使用 `to-tickets` 按可独立验收的纵向能力拆分实施卡片；每张卡片实施时必须遵守仓库的独立 git worktree、测试通过后合入 main、合入后清理 worktree规则。
