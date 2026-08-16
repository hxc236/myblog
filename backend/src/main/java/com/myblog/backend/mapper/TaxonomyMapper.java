package com.myblog.backend.mapper;

import com.myblog.backend.pojo.CategoryItem;
import com.myblog.backend.pojo.TagItem;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Category / Tag 数据访问（#19）：categories 与 tags。
 *
 * <p>删除正在使用的 Category 时在同一事务中把关联文章迁移到内置
 * Uncategorized；删除 Tag 时由外键级联自动解除全部关联（#14 实现决策）。
 */
@Component
public class TaxonomyMapper {

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;

    public TaxonomyMapper(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isAvailable() {
        return jdbcTemplate.getIfAvailable() != null;
    }

    // ---- 管理端 ----

    /** 全部 Category。 */
    public List<CategoryItem> listCategories() {
        List<CategoryItem> result = new ArrayList<>();
        requireJdbc().query(
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

    /** 新建 Category，返回 id。 */
    public Long insertCategory(String name) {
        return requireJdbc().queryForObject(
                "INSERT INTO categories (name) VALUES (?) RETURNING id", Long.class, name);
    }

    /** Category 是否为内置 Uncategorized；不存在时返回 null。 */
    public Boolean isUncategorized(long id) {
        return requireJdbc().query(
                "SELECT is_uncategorized FROM categories WHERE id = ?",
                rs -> rs.next() ? rs.getBoolean("is_uncategorized") : null, id);
    }

    /** 重命名 Category。 */
    public void updateCategoryName(long id, String name) {
        requireJdbc().update("UPDATE categories SET name = ? WHERE id = ?", name, id);
    }

    /** 内置 Uncategorized 的 id。 */
    public Long uncategorizedId() {
        return requireJdbc().queryForObject(
                "SELECT id FROM categories WHERE is_uncategorized", Long.class);
    }

    /** 把关联文章迁移到 Uncategorized（删除 Category 事务内）。 */
    public void movePostsToCategory(long uncategorizedId, long fromCategoryId) {
        requireJdbc().update(
                "UPDATE posts SET category_id = ?, updated_at = now() WHERE category_id = ?",
                uncategorizedId, fromCategoryId);
    }

    /** 删除 Category；不存在时返回 false。 */
    public boolean deleteCategory(long id) {
        return requireJdbc().update("DELETE FROM categories WHERE id = ?", id) > 0;
    }

    /** 全部 Tag。 */
    public List<TagItem> listTags() {
        List<TagItem> result = new ArrayList<>();
        requireJdbc().query(
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

    /** slug 是否已被占用。 */
    public boolean tagSlugExists(String slug) {
        Integer exists = requireJdbc().query(
                "SELECT 1 FROM tags WHERE slug = ?",
                rs -> rs.next() ? 1 : null, slug);
        return exists != null;
    }

    /** 新建 Tag，返回 id。 */
    public Long insertTag(String slug, String name) {
        return requireJdbc().queryForObject(
                "INSERT INTO tags (slug, name) VALUES (?, ?) RETURNING id",
                Long.class, slug, name);
    }

    /** Tag 是否存在。 */
    public boolean tagExists(long id) {
        Integer exists = requireJdbc().query(
                "SELECT 1 FROM tags WHERE id = ?",
                rs -> rs.next() ? 1 : null, id);
        return exists != null;
    }

    /** 重命名 Tag（同步更新 slug）。 */
    public void updateTag(String name, String slug, long id) {
        requireJdbc().update("UPDATE tags SET name = ?, slug = ? WHERE id = ?", name, slug, id);
    }

    /** 删除 Tag（外键级联解除关联）；不存在时返回 false。 */
    public boolean deleteTag(long id) {
        return requireJdbc().update("DELETE FROM tags WHERE id = ?", id) > 0;
    }

    // ---- 公开端：只暴露可供 Visitor 过滤 Published Revision 的内容 ----

    /** 至少有一篇已发布文章的 Category（Uncategorized 若被使用也会出现）。 */
    public List<CategoryItem> listPublishedCategories() {
        List<CategoryItem> result = new ArrayList<>();
        requireJdbc().query(
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
        List<TagItem> result = new ArrayList<>();
        requireJdbc().query(
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

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("数据库未配置：分类标签服务不可用");
        }
        return jdbc;
    }
}
