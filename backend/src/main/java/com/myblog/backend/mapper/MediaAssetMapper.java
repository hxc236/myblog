package com.myblog.backend.mapper;

import com.myblog.backend.pojo.MediaAsset;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Media Asset 数据访问（#24）：media_assets 元数据与引用判定。
 *
 * <p>引用保护查询落在本 mapper（所有修订正文的引用判定属于媒体数据域）；
 * 存储对象读写由 {@code MediaStorage} 接缝负责，不经本 mapper。
 */
@Component
public class MediaAssetMapper {

    private static final String ASSET_COLUMNS =
            "SELECT id, object_key, file_name, mime_type, size_bytes, width, height,"
                    + " checksum_sha256, alt_text, public_url, created_at FROM media_assets";

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;

    public MediaAssetMapper(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isAvailable() {
        return jdbcTemplate.getIfAvailable() != null;
    }

    /** 按 object key 查询元数据（无则 null）。 */
    public MediaAsset findByObjectKey(String objectKey) {
        return requireJdbc().query(
                ASSET_COLUMNS + " WHERE object_key = ?",
                rs -> rs.next() ? readRow(rs) : null,
                objectKey);
    }

    /** 按 id 查询元数据（删除前置检查用）；不存在时返回 null。 */
    public MediaAsset findById(long id) {
        return requireJdbc().query(
                "SELECT id, object_key FROM media_assets WHERE id = ?",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    MediaAsset a = new MediaAsset();
                    a.id = rs.getLong("id");
                    a.objectKey = rs.getString("object_key");
                    return a;
                },
                id);
    }

    /** 写入元数据，返回 id。 */
    public Long insertAsset(String objectKey, String fileName, String mimeType, long sizeBytes,
                            int width, int height, String checksumSha256, String altText,
                            String publicUrl) {
        return requireJdbc().queryForObject(
                "INSERT INTO media_assets (object_key, file_name, mime_type, size_bytes,"
                        + " width, height, checksum_sha256, alt_text, public_url)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, objectKey, fileName, mimeType, sizeBytes,
                width, height, checksumSha256, altText, publicUrl);
    }

    /** 列表：按创建时间倒序。 */
    public List<MediaAsset> listAssets() {
        List<MediaAsset> assets = new ArrayList<>();
        requireJdbc().query(
                ASSET_COLUMNS + " ORDER BY created_at DESC, id DESC",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    MediaAsset a = readRow(rs);
                    assets.add(a);
                });
        return assets;
    }

    /** 删除元数据；不存在时返回 false。 */
    public boolean deleteAsset(long id) {
        return requireJdbc().update("DELETE FROM media_assets WHERE id = ?", id) > 0;
    }

    /** 引用判定：任一修订的 Markdown 正文出现 public_url 或 object_key。 */
    public boolean isReferenced(String publicUrl, String objectKey) {
        Integer hits = requireJdbc().query(
                "SELECT 1 FROM post_revisions"
                        + " WHERE body_markdown LIKE ? OR body_markdown LIKE ? LIMIT 1",
                rs -> rs.next() ? 1 : null,
                "%" + publicUrl + "%", "%" + objectKey + "%");
        return hits != null;
    }

    private MediaAsset readRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        MediaAsset a = new MediaAsset();
        a.id = rs.getLong("id");
        a.objectKey = rs.getString("object_key");
        a.fileName = rs.getString("file_name");
        a.mimeType = rs.getString("mime_type");
        a.sizeBytes = rs.getLong("size_bytes");
        a.width = rs.getInt("width");
        a.height = rs.getInt("height");
        a.checksumSha256 = rs.getString("checksum_sha256");
        a.altText = rs.getString("alt_text");
        a.publicUrl = rs.getString("public_url");
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        a.createdAt = createdAt == null ? null : createdAt.toString();
        return a;
    }

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("数据库未配置：媒体服务不可用");
        }
        return jdbc;
    }
}
