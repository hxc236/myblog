package com.myblog.backend.service;

import com.myblog.backend.pojo.MediaAsset;

import java.util.List;

/**
 * Media Asset 服务契约（#24）：上传校验、元数据、引用保护与删除。
 *
 * <p>实现见 {@link com.myblog.backend.service.impl.MediaAssetServiceImpl}。
 * 引用保护：Draft 或 Published Revision 的 Markdown 正文（所有修订）引用
 * （出现 public_url 或 object_key）的资源不可删除；未引用资源在列表中标记，
 * 由 Site Owner 手动清理。
 */
public interface MediaAssetService {

    /** 数据库读路径是否可用。 */
    boolean isAvailable();

    /** 上传并保存元数据（类型/大小/尺寸/校验值/替代文本）；内容相同则复用已有资源。 */
    MediaAsset upload(String fileName, byte[] content, String altText);

    /** 列表：按创建时间倒序，附 referenced（被任一修订正文引用）标记。 */
    List<MediaAsset> list();

    /**
     * 删除：被 Draft 或 Published Revision 引用的资源拒绝删除（#14 用户
     * 故事 41）；未引用资源删除元数据与存储对象。
     */
    boolean delete(long id);

    /** 公开读取：按 object key 返回元数据（无则 null）；忽略捕获的前导斜杠。 */
    MediaAsset findByObjectKey(String objectKey);

    /** 读取存储对象内容（本地回源）。 */
    byte[] loadContent(String objectKey);
}
