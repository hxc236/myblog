package com.myblog.backend.service.impl;

import com.myblog.backend.mapper.TaxonomyMapper;
import com.myblog.backend.pojo.CategoryItem;
import com.myblog.backend.pojo.TagItem;
import com.myblog.backend.service.CategoryTagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Category / Tag 服务实现（#19）。
 *
 * <p>领域语义：Category 是文章所属的单一栏目（一篇文章只有一个主分类），
 * Tag 是可复用主题标签（多对多），两者不混淆。删除正在使用的 Category 时
 * 在同一事务中把关联文章迁移到内置 Uncategorized；删除 Tag 时由外键级联
 * 自动解除全部关联（#14 实现决策）。数据访问见 {@link TaxonomyMapper}。
 */
@Service
public class CategoryTagServiceImpl implements CategoryTagService {

    private final TaxonomyMapper mapper;

    public CategoryTagServiceImpl(TaxonomyMapper mapper) {
        this.mapper = mapper;
    }

    public boolean isAvailable() {
        return mapper.isAvailable();
    }

    // ---- 管理端 ----

    public List<CategoryItem> listCategories() {
        return mapper.listCategories();
    }

    @Transactional
    public CategoryItem createCategory(String name) {
        requireText(name, 64, "分类名称");
        CategoryItem item = new CategoryItem();
        item.name = name.trim();
        item.uncategorized = false;
        item.id = mapper.insertCategory(item.name);
        return item;
    }

    @Transactional
    public CategoryItem renameCategory(long id, String name) {
        requireText(name, 64, "分类名称");
        Boolean isUncategorized = mapper.isUncategorized(id);
        if (isUncategorized == null) {
            return null;
        }
        if (isUncategorized) {
            throw new IllegalArgumentException("Uncategorized Category 不可改名");
        }
        mapper.updateCategoryName(id, name.trim());
        CategoryItem item = new CategoryItem();
        item.id = id;
        item.name = name.trim();
        item.uncategorized = false;
        return item;
    }

    /**
     * 删除 Category：同一事务内把关联 Blog Post 迁移到 Uncategorized 再删除；
     * 内置 Uncategorized 不可删除。
     */
    @Transactional
    public boolean deleteCategory(long id) {
        Boolean isUncategorized = mapper.isUncategorized(id);
        if (isUncategorized == null) {
            return false;
        }
        if (isUncategorized) {
            throw new IllegalArgumentException("Uncategorized Category 不可删除");
        }
        Long uncategorizedId = mapper.uncategorizedId();
        mapper.movePostsToCategory(uncategorizedId, id);
        mapper.deleteCategory(id);
        return true;
    }

    public List<TagItem> listTags() {
        return mapper.listTags();
    }

    @Transactional
    public TagItem createTag(String name) {
        requireText(name, 64, "标签名称");
        TagItem item = new TagItem();
        item.name = name.trim();
        item.slug = uniqueTagSlug(item.name);
        item.id = mapper.insertTag(item.slug, item.name);
        return item;
    }

    @Transactional
    public TagItem renameTag(long id, String name) {
        requireText(name, 64, "标签名称");
        if (!mapper.tagExists(id)) {
            return null;
        }
        String slug = uniqueTagSlug(name.trim());
        mapper.updateTag(name.trim(), slug, id);
        TagItem item = new TagItem();
        item.id = id;
        item.name = name.trim();
        item.slug = slug;
        return item;
    }

    /** 删除 Tag：外键 ON DELETE CASCADE 自动解除全部 Blog Post 关联。 */
    @Transactional
    public boolean deleteTag(long id) {
        return mapper.deleteTag(id);
    }

    // ---- 公开端：只暴露可供 Visitor 过滤 Published Revision 的内容 ----

    /** 至少有一篇已发布文章的 Category（Uncategorized 若被使用也会出现）。 */
    public List<CategoryItem> listPublishedCategories() {
        return mapper.listPublishedCategories();
    }

    /** 至少被一篇已发布文章使用的 Tag。 */
    public List<TagItem> listPublishedTags() {
        return mapper.listPublishedTags();
    }

    // ---- 内部 ----

    /** 自动 slug：ASCII 名称转小写连字符；冲突时追加随机后缀（slug 唯一）。 */
    private String uniqueTagSlug(String name) {
        String base = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isEmpty()) {
            base = "tag";
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            String slug = attempt == 0 ? base
                    : base + "-" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x10000));
            if (!mapper.tagSlugExists(slug)) {
                return slug;
            }
        }
        throw new IllegalArgumentException("标签 slug 生成失败，请更换名称");
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
