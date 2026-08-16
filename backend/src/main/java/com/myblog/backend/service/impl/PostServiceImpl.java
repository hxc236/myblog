package com.myblog.backend.service.impl;

import com.myblog.backend.mapper.PostMapper;
import com.myblog.backend.pojo.AdminPostDetail;
import com.myblog.backend.pojo.AdminPostSummary;
import com.myblog.backend.pojo.DraftPayload;
import com.myblog.backend.pojo.RevisionItem;
import com.myblog.backend.service.PostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Blog Post 服务实现（#20）：Draft 生命周期与立即发布。
 *
 * <p>修订模型（#14 4.1/4.2）：{@code posts} 保存身份与发布指针；草稿修订
 * 可编辑，Published Revision 不可变。保存 Draft 不触碰公开指针；发布在单一
 * 事务内原子切换 published_revision_id、发布时间并替换搜索投影。Markdown
 * 正文以 TEXT 保存在 post_revisions，不产生第二权威内容源。数据访问见
 * {@link PostMapper}。
 */
@Service
public class PostServiceImpl implements PostService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final int MAX_SLUG_LENGTH = 64;

    private final PostMapper mapper;

    public PostServiceImpl(PostMapper mapper) {
        this.mapper = mapper;
    }

    public boolean isAvailable() {
        return mapper.isAvailable();
    }

    /** 新建 Blog Post：自动进入 Draft（空修订），slug 留待保存时设置。 */
    @Transactional
    public AdminPostDetail createPost() {
        Long postId = mapper.insertPostWithNullSlug();
        Long revisionId = mapper.insertRevision(postId, 1, "", "", "");
        mapper.setDraftRevision(postId, revisionId);
        return mapper.getAdminDetail(postId);
    }

    /** 管理端列表：标题取 Draft（有则）否则取 Published Revision，归档后取最新修订。 */
    public List<AdminPostSummary> listPosts() {
        return mapper.listAdminSummaries();
    }

    /** 管理端详情（仅 Site Owner）：优先返回当前 Draft，否则返回 Published Revision。 */
    public AdminPostDetail getPostDetail(long id) {
        return mapper.getAdminDetail(id);
    }

    /**
     * 保存 Draft：首次编辑已发布文章时先创建新的 Draft 修订（Published
     * Revision 保持不可变）；保存不触碰公开指针、发布时间与搜索投影。
     * 修改已发布文章的 slug 时写入全局唯一的历史重定向（#14 实现决策）。
     */
    @Transactional
    public AdminPostDetail saveDraft(long id, DraftPayload payload) {
        PostMapper.PostRow post = mapper.getPostState(id);
        if (post == null) {
            return null;
        }
        validateDraft(payload, id);

        String newSlug = payload.slug == null || payload.slug.trim().isEmpty()
                ? null : payload.slug.trim();
        String currentSlug = post.slug;
        if (post.publishedRevisionId != null
                && currentSlug != null && !currentSlug.isEmpty()
                && !currentSlug.equals(newSlug)) {
            // 公开 slug 变更：旧 slug 永久 301 到当前 slug
            mapper.insertSlugRedirect(currentSlug, id);
        }

        Long draftRevisionId = post.draftRevisionId;
        if (draftRevisionId == null) {
            int maxNo = mapper.maxRevisionNo(id);
            draftRevisionId = mapper.insertRevision(
                    id, maxNo + 1,
                    payload.title.trim(),
                    payload.summary == null ? "" : payload.summary.trim(),
                    payload.bodyMarkdown == null ? "" : payload.bodyMarkdown);
        } else {
            mapper.updateRevision(
                    draftRevisionId,
                    payload.title.trim(),
                    payload.summary == null ? "" : payload.summary.trim(),
                    payload.bodyMarkdown == null ? "" : payload.bodyMarkdown);
        }

        mapper.updatePostForDraft(id, newSlug, payload.categoryId, draftRevisionId);
        mapper.replacePostTags(id, payload.tagIds);
        return mapper.getAdminDetail(id);
    }

    /**
     * 立即发布：事务内原子切换 Published Revision 指针、首次/最近发布时间，
     * 并替换搜索投影；发布后原修订不可变（#14 实现决策）。
     */
    @Transactional
    public AdminPostDetail publish(long id) {
        PostMapper.PostRow post = mapper.getPostState(id);
        if (post == null) {
            return null;
        }
        Long draftRevisionId = post.draftRevisionId;
        if (draftRevisionId == null) {
            throw new IllegalArgumentException("没有可发布的 Draft");
        }
        if (post.categoryId == null) {
            throw new IllegalArgumentException("发布必须选择分类");
        }
        String slug = post.slug;
        validateSlugForPublish(slug, id);

        OffsetDateTime now = OffsetDateTime.now();
        mapper.publishPost(id, draftRevisionId, now);

        AdminPostDetail published = mapper.getRevisionTitleSummary(draftRevisionId);
        mapper.deleteSearchDocument(id);
        mapper.insertSearchDocument(id, published.title, published.summary, now);
        return mapper.getAdminDetail(id);
    }

    /** 修订历史（#22）：全部不可变修订，标记当前已发布的那个。 */
    public List<RevisionItem> listRevisions(long id) {
        return mapper.listRevisions(id);
    }

    /**
     * 恢复历史修订（#22）：把目标修订复制为新的 Draft，预览确认后再次发布；
     * 不直接覆盖线上内容。
     */
    @Transactional
    public AdminPostDetail restoreRevision(long id, long revisionId) {
        if (!mapper.revisionBelongsToPost(revisionId, id)) {
            return null;
        }
        int maxNo = mapper.maxRevisionNo(id);
        Long newDraftId = mapper.insertRevisionCopy(id, maxNo + 1, revisionId);
        mapper.setDraftRevision(id, newDraftId);
        return mapper.getAdminDetail(id);
    }

    /** 撤回已发布 Blog Post 并保留为 Archived Post（#22）：公开指针置空并移除搜索投影。 */
    @Transactional
    public AdminPostDetail archive(long id) {
        if (!mapper.isPublished(id)) {
            return null;
        }
        mapper.clearPublished(id);
        mapper.deleteSearchDocument(id);
        return mapper.getAdminDetail(id);
    }

    /**
     * 永久删除：只允许从未发布的 Draft（#14 用户故事 35）；曾发布过的文章
     * 只能归档，保留审计与恢复能力（#14 用户故事 36）。
     */
    @Transactional
    public boolean deletePost(long id) {
        if (mapper.wasEverPublished(id)) {
            throw new IllegalArgumentException("曾发布的文章只能归档，不能删除");
        }
        return mapper.deletePost(id);
    }

    /**
     * 从 Published Revision 全量重建搜索投影（#23）：投影不是第二权威源，
     * 损坏时无需恢复第二份内容。
     */
    @Transactional
    public int rebuildSearchIndex() {
        return mapper.rebuildSearchIndex();
    }

    // ---- 内部 ----

    private void validateDraft(DraftPayload payload, long postId) {
        if (payload == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        requireText(payload.title, 200, "标题");
        if (payload.summary != null && payload.summary.length() > 500) {
            throw new IllegalArgumentException("摘要最多 500 字符");
        }
        if (payload.bodyMarkdown != null && payload.bodyMarkdown.length() > 200_000) {
            throw new IllegalArgumentException("Markdown 正文过长");
        }
        validateMarkdownSafety(payload.bodyMarkdown);
        if (payload.slug != null && !payload.slug.trim().isEmpty()) {
            validateSlugForPublish(payload.slug.trim(), postId);
        }
        if (payload.tagIds != null && payload.tagIds.size() > 10) {
            throw new IllegalArgumentException("每篇文章最多 10 个标签");
        }
    }

    /** Markdown 保存时拒绝危险原始 HTML（#24）；渲染后仍由 DOMPurify 兜底。 */
    private void validateMarkdownSafety(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return;
        }
        String lower = markdown.toLowerCase(Locale.ROOT);
        for (String forbidden : new String[]{
                "<script", "<iframe", "<object", "<embed", "<link", "<meta",
                "javascript:", "onerror=", "onload=", "onclick=", "onmouseover=",
                "<svg", "<math"}) {
            if (lower.contains(forbidden)) {
                throw new IllegalArgumentException(
                        "Markdown 包含被禁止的危险 HTML（" + forbidden + "）");
            }
        }
    }

    private void validateSlugForPublish(String slug, long postId) {
        if (slug == null || slug.isEmpty()) {
            throw new IllegalArgumentException("发布必须设置 slug");
        }
        if (slug.length() > MAX_SLUG_LENGTH
                || !SLUG_PATTERN.matcher(slug.toLowerCase(Locale.ROOT)).matches()
                || !slug.equals(slug.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "slug 必须是 64 字符以内的小写字母、数字与连字符组合");
        }
        if (mapper.slugConflict(slug, postId)) {
            throw new IllegalArgumentException("slug 已被其他文章使用：" + slug);
        }
        // 旧 slug 永久绑定其文章（#22）：不得被新 slug 复用
        if (mapper.redirectSlugConflict(slug)) {
            throw new IllegalArgumentException("slug 已被历史重定向占用：" + slug);
        }
    }

    private static void requireText(String value, int maxLength, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " 最多 " + maxLength + " 字符");
        }
    }
}
