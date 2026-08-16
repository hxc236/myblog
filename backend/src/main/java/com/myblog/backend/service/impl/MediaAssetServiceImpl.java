package com.myblog.backend.service.impl;

import com.myblog.backend.mapper.MediaAssetMapper;
import com.myblog.backend.pojo.MediaAsset;
import com.myblog.backend.service.MediaAssetService;
import com.myblog.backend.service.MediaStorage;
import com.myblog.backend.utils.MediaContentValidator;
import com.myblog.backend.utils.TokenUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Media Asset 服务实现（#24）：上传校验、元数据、引用保护与删除。
 *
 * <p>引用保护：Draft 或 Published Revision 的 Markdown 正文（所有修订）
 * 引用（出现 public_url 或 object_key）的资源不可删除；未引用资源在列表中
 * 标记，由 Site Owner 手动清理。数据访问见 {@link MediaAssetMapper}，存储
 * 对象读写走 {@link MediaStorage} 接缝。
 */
@Service
public class MediaAssetServiceImpl implements MediaAssetService {

    private final MediaAssetMapper mapper;
    private final MediaStorage storage;

    public MediaAssetServiceImpl(MediaAssetMapper mapper, MediaStorage storage) {
        this.mapper = mapper;
        this.storage = storage;
    }

    public boolean isAvailable() {
        return mapper.isAvailable();
    }

    /** 上传并保存元数据（类型/大小/尺寸/校验值/替代文本）；内容相同则复用已有资源。 */
    @Transactional
    public MediaAsset upload(String fileName, byte[] content, String altText) {
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
        asset.checksumSha256 = TokenUtil.sha256Hex(content);
        asset.altText = altText == null ? "" : altText.trim();
        asset.publicUrl = storage.publicUrl(stored.objectKey);
        // 内容寻址去重：同一内容已有资源时复用（不可变 URL 语义）；
        // 唯一约束保留为并发兜底
        MediaAsset existing = mapper.findByObjectKey(asset.objectKey);
        if (existing != null) {
            return existing;
        }
        asset.id = mapper.insertAsset(
                asset.objectKey, asset.fileName, asset.mimeType, asset.sizeBytes,
                asset.width, asset.height, asset.checksumSha256, asset.altText, asset.publicUrl);
        return asset;
    }

    /** 列表：按创建时间倒序，附 referenced（被任一修订正文引用）标记。 */
    public List<MediaAsset> list() {
        List<MediaAsset> assets = mapper.listAssets();
        for (MediaAsset a : assets) {
            a.referenced = mapper.isReferenced(a.publicUrl, a.objectKey);
        }
        return assets;
    }

    /**
     * 删除：被 Draft 或 Published Revision 引用的资源拒绝删除（#14 用户
     * 故事 41）；未引用资源删除元数据与存储对象。
     */
    @Transactional
    public boolean delete(long id) {
        MediaAsset asset = mapper.findById(id);
        if (asset == null) {
            return false;
        }
        if (mapper.isReferenced(asset.publicUrl, asset.objectKey)) {
            throw new IllegalArgumentException("该 Media Asset 正被文章引用，不能删除");
        }
        mapper.deleteAsset(id);
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
        return mapper.findByObjectKey(key);
    }

    /** 读取存储对象内容（本地回源）。 */
    public byte[] loadContent(String objectKey) {
        try {
            return storage.load(objectKey);
        } catch (IOException e) {
            throw new IllegalStateException("媒体存储读取失败", e);
        }
    }
}
