package com.myblog.backend.controller;

import com.myblog.backend.service.MediaAssetService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Admin Media Asset API（#24）：上传与删除只经认证管理能力；列表标记
 * 引用状态（未引用资源由 Site Owner 手动清理）。
 */
@RestController
@RequestMapping("/api/admin/media")
public class AdminMediaController {

    private static final CacheControl NO_STORE = CacheControl.noStore();

    private final MediaAssetService mediaService;

    public AdminMediaController(MediaAssetService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        if (!mediaService.isAvailable()) {
            return unavailable();
        }
        return ResponseEntity.ok().cacheControl(NO_STORE).body(mediaService.list());
    }

    @PostMapping
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "altText", required = false) String altText) {
        if (!mediaService.isAvailable()) {
            return unavailable();
        }
        if (file == null || file.isEmpty()) {
            return validationFailed(new IllegalArgumentException("请选择要上传的图片"));
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .cacheControl(NO_STORE)
                    .body(mediaService.upload(file.getOriginalFilename(),
                            file.getBytes(), altText));
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .cacheControl(NO_STORE)
                    .body(Map.of("error", "validation_failed", "message", "无法读取上传内容"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable long id) {
        if (!mediaService.isAvailable()) {
            return unavailable();
        }
        try {
            if (!mediaService.delete(id)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .cacheControl(NO_STORE)
                        .body(Map.of("error", "not_found"));
            }
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        }
    }

    private static ResponseEntity<Map<String, String>> validationFailed(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .cacheControl(NO_STORE)
                .body(Map.of("error", "validation_failed", "message", e.getMessage()));
    }

    private static ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(NO_STORE)
                .body(Map.of("error", "database_unavailable"));
    }
}