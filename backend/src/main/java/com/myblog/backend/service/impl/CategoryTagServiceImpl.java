package com.myblog.backend.service.impl;
import com.myblog.backend.service.CategoryTagService;
import com.myblog.backend.pojo.CategoryItem;
import com.myblog.backend.pojo.TagItem;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Category / Tag 服务（#19）。
 *
 * <p>领域语义：Category 是文章所属的单一栏目（一篇文章只有一个主分类），
 * Tag 是可复用主题标签（多对多），两者不混淆。删除正在使用的 Category 时
 * 在同一事务中把关联文章迁移到内置 Uncategorized；删除 Tag 时由外键级联
 * 自动解除全部关联（#14 实现决策）。
 */
@Service
public class CategoryTagServiceImpl implements CategoryTagService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;

    public CategoryTagServiceImpl(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isAvailable() {
        return jdbcTemplate.getIfAvailable() != null;
    }

    // ---- 管理端 ----

    public List<CategoryItem> listCategories() {
        JdbcTemplate jdbc = requireJdbc();
        List<CategoryItem> result = new ArrayList<>();
        jdbc.query(
                "SELECT id, name, is_uncategorized FROM categories ORDER BY name, id",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    CategoryItem c = new CategoryItem();
                    c.id = rs.getLong("id");
                    c.name = rs.getString("name");
                    c.uncategorized = rs.getBoolean("is_uncategorized");
                    result.add(c);
                });
        return result;
    }

    @Transactional
    public CategoryItem createCategory(String name) {
        requireText(name, 64, "分类名称");
        CategoryItem item = new CategoryItem();
        item.name = name.trim();
        item.uncategorized = false;
        Long id = jdbcTemplate.getIfAvailable().queryForObject(
                "INSERT INTO categories (name) VALUES (?) RETURNING id", Long.class, item.name);
        item.id = id;
        return item;
    }

    @Transactional
    public CategoryItem renameCategory(long id, String name) {
        requireText(name, 64, "分类名称");
        JdbcTemplate jdbc = requireJdbc();
        Boolean isUncategorized = jdbc.query(
                "SELECT is_uncategorized FROM categories WHERE id = ?",
                rs -> rs.next() ? rs.getBoolean("is_uncategorized") : null, id);
        if (isUncategorized == null) {
            return null;
        }
        if (isUncategorized) {
            throw new IllegalArgumentException("Uncategorized Category 不可改名");
        }
        jdbc.update("UPDATE categories SET name = ? WHERE id = ?", name.trim(), id);
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
        JdbcTemplate jdbc = requireJdbc();
        Boolean isUncategorized = jdbc.query(
                "SELECT is_uncategorized FROM categories WHERE id = ?",
                rs -> rs.next() ? rs.getBoolean("is_uncategorized") : null, id);
        if (isUncategorized == null) {
            return false;
        }
        if (isUncategorized) {
            throw new IllegalArgumentException("Uncategorized Category 不可删除");
        }
        Long uncategorizedId = jdbc.queryForObject(
                "SELECT id FROM categories WHERE is_uncategorized", Long.class);
        jdbc.update(
                "UPDATE posts SET category_id = ?, updated_at = now() WHERE category_id = ?",
                uncategorizedId, id);
        jdbc.update("DELETE FROM categories WHERE id = ?", id);
        return true;
    }

    public List<TagItem> listTags() {
        JdbcTemplate jdbc = requireJdbc();
        List<TagItem> result = new ArrayList<>();
        jdbc.query(
                "SELECT id, slug, name FROM tags ORDER BY name, id",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    TagItem t = new TagItem();
                    t.id = rs.getLong("id");
                    t.slug = rs.getString("slug");
                    t.name = rs.getString("name");
                    result.add(t);
                });
        return result;
    }

    @Transactional
    public TagItem createTag(String name) {
        requireText(name, 64, "标签名称");
        TagItem item = new TagItem();
        item.name = name.trim();
        item.slug = uniqueTagSlug(item.name);
        Long id = jdbcTemplate.getIfAvailable().queryForObject(
                "INSERT INTO tags (slug, name) VALUES (?, ?) RETURNING id",
                Long.class, item.slug, item.name);
        item.id = id;
        return item;
    }

    @Transactional
    public TagItem renameTag(long id, String name) {
        requireText(name, 64, "标签名称");
        JdbcTemplate jdbc = requireJdbc();
        Integer exists = jdbc.query(
                "SELECT 1 FROM tags WHERE id = ?",
                rs -> rs.next() ? 1 : null, id);
        if (exists == null) {
            return null;
        }
        String slug = uniqueTagSlug(name.trim());
        jdbc.update("UPDATE tags SET name = ?, slug = ? WHERE id = ?", name.trim(), slug, id);
        TagItem item = new TagItem();
        item.id = id;
        item.name = name.trim();
        item.slug = slug;
        return item;
    }

    /** 删除 Tag：外键 ON DELETE CASCADE 自动解除全部 Blog Post 关联。 */
    @Transactional
    public boolean deleteTag(long id) {
        JdbcTemplate jdbc = requireJdbc();
        return jdbc.update("DELETE FROM tags WHERE id = ?", id) > 0;
    }

    // ---- 公开端：只暴露可供 Visitor 过滤 Published Revision 的内容 ----

    /** 至少有一篇已发布文章的 Category（Uncategorized 若被使用也会出现）。 */
    public List<CategoryItem> listPublishedCategories() {
        JdbcTemplate jdbc = requireJdbc();
        List<CategoryItem> result = new ArrayList<>();
        jdbc.query(
                "SELECT DISTINCT c.id, c.name, c.is_uncategorized"
                        + "  FROM categories c"
                        + "  JOIN posts p ON p.category_id = c.id"
                        + " WHERE p.published_revision_id IS NOT NULL"
                        + " ORDER BY c.name, c.id",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    CategoryItem c = new CategoryItem();
                    c.id = rs.getLong("id");
                    c.name = rs.getString("name");
                    c.uncategorized = rs.getBoolean("is_uncategorized");
                    result.add(c);
                });
        return result;
    }

    /** 至少被一篇已发布文章使用的 Tag。 */
    public List<TagItem> listPublishedTags() {
        JdbcTemplate jdbc = requireJdbc();
        List<TagItem> result = new ArrayList<>();
        jdbc.query(
                "SELECT DISTINCT t.id, t.slug, t.name"
                        + "  FROM tags t"
                        + "  JOIN post_tags pt ON pt.tag_id = t.id"
                        + "  JOIN posts p ON p.id = pt.post_id"
                        + " WHERE p.published_revision_id IS NOT NULL"
                        + " ORDER BY t.name, t.id",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    TagItem t = new TagItem();
                    t.id = rs.getLong("id");
                    t.slug = rs.getString("slug");
                    t.name = rs.getString("name");
                    result.add(t);
                });
        return result;
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
        JdbcTemplate jdbc = requireJdbc();
        for (int attempt = 0; attempt < 5; attempt++) {
            String slug = attempt == 0 ? base
                    : base + "-" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x10000));
            Integer exists = jdbc.query(
                    "SELECT 1 FROM tags WHERE slug = ?",
                    rs -> rs.next() ? 1 : null, slug);
            if (exists == null) {
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

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("数据库未配置：分类标签服务不可用");
        }
        return jdbc;
    }
}