package com.myblog.backend.service;

import com.myblog.backend.pojo.AdminPostDetail;
import com.myblog.backend.pojo.AdminPostSummary;

import java.util.List;

/**
 * Blog Post 服务契约（#20）：Draft 生命周期与立即发布。
 *
 * <p>实现见 {@link com.myblog.backend.service.impl.PostServiceImpl}。修订模型
 * （#14 4.1/4.2）：{@code posts} 保存身份与发布指针；草稿修订可编辑，
 * Published Revision 不可变。保存 Draft 不触碰公开指针；发布在单一事务内
 * 原子切换 published_revision_id、发布时间并替换搜索投影。Markdown 正文以
 * TEXT 保存在 post_revisions，不产生第二权威内容源。
 */
public interface PostService {

    /** 数据库读路径是否可用。 */
    boolean isAvailable();

    /** 新建 Blog Post：自动进入 Draft（空修订），slug 留待保存时设置。 */
    AdminPostDetail createPost();

    /** 管理端列表：标题取 Draft（有则）否则取 Published Revision，归档后取最新修订。 */
    List<AdminPostSummary> listPosts();

    /** 管理端详情（仅 Site Owner）：优先返回当前 Draft，否则返回 Published Revision。 */
    AdminPostDetail getPostDetail(long id);

    /**
     * 保存 Draft：首次编辑已发布文章时先创建新的 Draft 修订（Published
     * Revision 保持不可变）；保存不触碰公开指针、发布时间与搜索投影。
     * 修改已发布文章的 slug 时写入全局唯一的历史重定向（#14 实现决策）。
     */
    AdminPostDetail saveDraft(long id, DraftPayload payload);

    /** 立即发布：事务内原子切换 Published Revision 指针、首次/最近发布时间，并替换搜索投影。 */
    AdminPostDetail publish(long id);

    /** 修订历史（#22）：全部不可变修订，标记当前已发布的那个。 */
    List<RevisionItem> listRevisions(long id);

    /** 恢复历史修订（#22）：把目标修订复制为新的 Draft，预览确认后再次发布。 */
    AdminPostDetail restoreRevision(long id, long revisionId);

    /** 撤回已发布 Blog Post 并保留为 Archived Post（#22）：公开指针置空并移除搜索投影。 */
    AdminPostDetail archive(long id);

    /** 永久删除：只允许从未发布的 Draft（#14 用户故事 35）。 */
    boolean deletePost(long id);

    /** 从 Published Revision 全量重建搜索投影（#23）：投影不是第二权威源。 */
    int rebuildSearchIndex();

    /** 草稿保存载荷（#20 编辑表单）。 */
    class DraftPayload {

        public String title;
        public String summary;
        public String bodyMarkdown;
        public String slug;
        public Long categoryId;
        public List<Long> tagIds;
    }

    /** 修订历史条目。 */
    class RevisionItem {

        public Long revisionId;
        public Integer revisionNo;
        public String title;
        public String summary;
        public String createdAt;
        public Boolean published;
    }
}
