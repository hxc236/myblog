package com.myblog.backend.service;

import java.io.IOException;

/**
 * 统一媒体存储能力（#24）：本地开发使用显式目录（非 Render 临时文件系统），
 * 生产使用私有 R2（S3 兼容）实现；上传与删除只经认证的 Admin API，
 * 公开读取走不可变 URL。
 */
public interface MediaStorage {

    /** 存储对象内容，返回不可变 object key（基于内容哈希）。 */
    StoredObject store(byte[] content, String contentType) throws IOException;

    /** 读取对象内容（本地回源；生产 R2 由公开读取入口直读）。 */
    byte[] load(String objectKey) throws IOException;

    /** 删除对象；对象不存在时静默成功。 */
    void delete(String objectKey) throws IOException;

    /** 对象是否存在。 */
    boolean exists(String objectKey);

    /** 公开读取 URL（不可变，长期缓存）。 */
    String publicUrl(String objectKey);

    /** 存储结果。 */
    class StoredObject {

        public final String objectKey;

        public StoredObject(String objectKey) {
            this.objectKey = objectKey;
        }
    }
}