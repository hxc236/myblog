package com.myblog.backend.service.impl;
import com.myblog.backend.service.MediaStorage;

import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

/**
 * 本地开发媒体存储（#24）：显式开发目录（{@code site.media.local-root}，
 * 默认 {@code data/media}），不允许使用 Render 临时文件系统作为持久媒体库。
 * object key 按日期与内容哈希组织，URL 不可变。
 */
public class LocalMediaStorage implements MediaStorage {

    private final Path root;

    public LocalMediaStorage(@Value("${site.media.local-root:data/media}") String localRoot) {
        this.root = Paths.get(localRoot).toAbsolutePath().normalize();
    }

    @Override
    public StoredObject store(byte[] content, String contentType) throws IOException {
        Files.createDirectories(root);
        String objectKey = objectKeyFor(content);
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("非法的 object key 路径");
        }
        Files.createDirectories(target.getParent());
        if (!Files.exists(target)) {
            Files.write(target, content);
        }
        return new StoredObject(objectKey);
    }

    @Override
    public byte[] load(String objectKey) throws IOException {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("非法的 object key 路径");
        }
        return Files.readAllBytes(target);
    }

    @Override
    public void delete(String objectKey) throws IOException {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("非法的 object key 路径");
        }
        Files.deleteIfExists(target);
    }

    @Override
    public boolean exists(String objectKey) {
        Path target = root.resolve(objectKey).normalize();
        return target.startsWith(root) && Files.exists(target);
    }

    @Override
    public String publicUrl(String objectKey) {
        return "/api/media/" + objectKey;
    }

    /** key = yyyy/MM/<sha256 前 16 位>；内容不变则 URL 不变（不可变 + 长期缓存）。 */
    private String objectKeyFor(byte[] content) {
        String hash = com.myblog.backend.utils.TokenUtil.sha256Hex(content);
        LocalDate today = LocalDate.now();
        return String.format("%d/%02d/%s", today.getYear(), today.getMonthValue(),
                hash.substring(0, 16));
    }
}