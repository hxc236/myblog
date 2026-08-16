package com.myblog.backend.service;

import com.myblog.backend.pojo.CategoryItem;
import com.myblog.backend.pojo.TagItem;

import java.util.List;

/**
 * Category / Tag 服务契约（#19）。
 *
 * <p>实现见 {@link com.myblog.backend.service.impl.CategoryTagServiceImpl}。
 * 领域语义：Category 是文章所属的单一栏目（一篇文章只有一个主分类），Tag
 * 是可复用主题标签（多对多），两者不混淆。删除正在使用的 Category 时在同一
 * 事务中把关联文章迁移到内置 Uncategorized；删除 Tag 时由外键级联自动解除
 * 全部关联（#14 实现决策）。
 */
public interface CategoryTagService {

    /** 数据库读路径是否可用。 */
    boolean isAvailable();

    // ---- 管理端 ----

    /** 全部 Category（管理端）。 */
    List<CategoryItem> listCategories();

    /** 新建 Category。 */
    CategoryItem createCategory(String name);

    /** 重命名 Category；内置 Uncategorized 不可改名。 */
    CategoryItem renameCategory(long id, String name);

    /**
     * 删除 Category：同一事务内把关联 Blog Post 迁移到 Uncategorized 再删除；
     * 内置 Uncategorized 不可删除。
     */
    boolean deleteCategory(long id);

    /** 全部 Tag（管理端）。 */
    List<TagItem> listTags();

    /** 新建 Tag（自动生成唯一 slug）。 */
    TagItem createTag(String name);

    /** 重命名 Tag（同步更新 slug）。 */
    TagItem renameTag(long id, String name);

    /** 删除 Tag：外键 ON DELETE CASCADE 自动解除全部 Blog Post 关联。 */
    boolean deleteTag(long id);

    // ---- 公开端：只暴露可供 Visitor 过滤 Published Revision 的内容 ----

    /** 至少有一篇已发布文章的 Category（Uncategorized 若被使用也会出现）。 */
    List<CategoryItem> listPublishedCategories();

    /** 至少被一篇已发布文章使用的 Tag。 */
    List<TagItem> listPublishedTags();
}
