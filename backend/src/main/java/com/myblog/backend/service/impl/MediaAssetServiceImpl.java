package com.myblog.backend.service.impl;
import com.myblog.backend.service.MediaStorage;
import com.myblog.backend.service.MediaAssetService;
import com.myblog.backend.utils.MediaContentValidator;
import com.myblog.backend.pojo.MediaAsset;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Media Asset 服务（#24）：上传校验、元数据、引用保护与删除。
 *
 * <p>引用保护：Draft 或 Published Revision 的 Markdown 正文（所有修订）
 * 引用（出现 public_url 或 object_key）的资源不可删除；未引用资源在列表中
 * 标记，由 Site Owner 手动清理。
 */
@Service
public class MediaAssetServiceImpl implements MediaAssetService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;
    private final MediaStorage storage;

    public MediaAssetServiceImpl(ObjectProvider<JdbcTemplate> jdbcTemplate, MediaStorage storage) {
        this.jdbcTemplate = jdbcTemplate;
        this.storage = storage;
    }

    public boolean isAvailable() {
        return jdbcTemplate.getIfAvailable() != null;
    }

    /** 上传并保存元数据（类型/大小/尺寸/校验值/替代文本）；内容相同则复用已有资源。 */
    @Transactional
    public MediaAsset upload(String fileName, byte[] content, String altText) {
        JdbcTemplate jdbc = requireJdbc();
        MediaContentValidator.Validated validated = MediaContentValidator.validate(content);
        MediaStorage.StoredObject stored;
        try {
            stored = storage.store(content, validated.mimeType);
        } catch (IOException e) {
            throw new IllegalStateException("媒体存储写入失败", e);
        }
        MediaAsset asset = new MediaAsset();
        asset.objectKey = stored.objectKey;
        asset.fileName = fileName == null || fileName.trim().isEmpty()
                ? stored.objectKey : fileName.trim();
        asset.mimeType = validated.mimeType;
        asset.sizeBytes = (long) content.length;
        asset.width = validated.width;
        asset.height = validated.height;
        asset.checksumSha256 = com.myblog.backend.utils.TokenUtil.sha256Hex(content);
        asset.altText = altText == null ? "" : altText.trim();
        asset.publicUrl = storage.publicUrl(stored.objectKey);
        // 内容寻址去重：同一内容已有资源时复用（不可变 URL 语义）；
        // 唯一约束保留为并发兜底
        MediaAsset existing = findByObjectKey(asset.objectKey);
        if (existing != null) {
            return existing;
        }
        Long id = jdbc.queryForObject(
                "INSERT INTO media_assets (object_key, file_name, mime_type, size_bytes,"
                        + " width, height, checksum_sha256, alt_text, public_url)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, asset.objectKey, asset.fileName, asset.mimeType, asset.sizeBytes,
                asset.width, asset.height, asset.checksumSha256, asset.altText, asset.publicUrl);
        asset.id = id;
        return asset;
    }

    /** 列表：按创建时间倒序，附 referenced（被任一修订正文引用）标记。 */
    public List<MediaAsset> list() {
        JdbcTemplate jdbc = requireJdbc();
        List<MediaAsset> assets = new ArrayList<>();
        jdbc.query(
                "SELECT id, object_key, file_name, mime_type, size_bytes, width, height,"
                        + " checksum_sha256, alt_text, public_url, created_at"
                        + "  FROM media_assets ORDER BY created_at DESC, id DESC",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
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
                    assets.add(a);
                });
        for (MediaAsset a : assets) {
            a.referenced = isReferenced(jdbc, a);
        }
        return assets;
    }

    /**
     * 删除：被 Draft 或 Published Revision 引用的资源拒绝删除（#14 用户
     * 故事 41）；未引用资源删除元数据与存储对象。
     */
    @Transactional
    public boolean delete(long id) {
        JdbcTemplate jdbc = requireJdbc();
        MediaAsset asset = jdbc.query(
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
        if (asset == null) {
            return false;
        }
        if (isReferenced(jdbc, asset)) {
            throw new IllegalArgumentException("该 Media Asset 正被文章引用，不能删除");
        }
        jdbc.update("DELETE FROM media_assets WHERE id = ?", id);
        try {
            storage.delete(asset.objectKey);
        } catch (IOException e) {
            throw new IllegalStateException("媒体存储删除失败", e);
        }
        return true;
    }

    /** 公开读取：按 object key 返回元数据（无则 null）；忽略捕获的前导斜杠。 */
    public MediaAsset findByObjectKey(String objectKey) {
        String key = objectKey == null ? null : objectKey.replaceAll("^/+", "");
        JdbcTemplate jdbc = requireJdbc();
        return jdbc.query(
                "SELECT id, object_key, file_name, mime_type, size_bytes, width, height,"
                        + " checksum_sha256, alt_text, public_url, created_at"
                        + "  FROM media_assets WHERE object_key = ?",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
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
                    return a;
                },
                key);
    }

    /** 读取存储对象内容（本地回源）。 */
    public byte[] loadContent(String objectKey) {
        try {
            return storage.load(objectKey);
        } catch (IOException e) {
            throw new IllegalStateException("媒体存储读取失败", e);
        }
    }

    /** 引用判定：所有修订的 Markdown 正文中出现 public_url 或 object_key。 */
    private boolean isReferenced(JdbcTemplate jdbc, MediaAsset asset) {
        Integer hits = jdbc.query(
                "SELECT 1 FROM post_revisions"
                        + " WHERE body_markdown LIKE ? OR body_markdown LIKE ? LIMIT 1",
                rs -> rs.next() ? 1 : null,
                "%" + asset.publicUrl + "%", "%" + asset.objectKey + "%");
        return hits != null;
    }

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("数据库未配置：媒体服务不可用");
        }
        return jdbc;
    }
}