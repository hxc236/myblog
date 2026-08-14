package com.myblog.publicsite.web;

import com.myblog.publicsite.media.MediaAsset;
import com.myblog.publicsite.media.MediaAssetService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公开 Media Asset 读取（#24）：object key 基于内容哈希，URL 不可变，
 * 使用一年长期缓存（immutable）；生产环境由 GET/HEAD-only Worker 直读
 * R2，本入口服务于本地开发存储。
 */
@RestController
public class MediaReadController {

    private final MediaAssetService mediaService;

    public MediaReadController(MediaAssetService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/api/media/{*objectKey}")
    public ResponseEntity<?> read(@PathVariable("objectKey") String objectKey) {
        if (!mediaService.isAvailable()) {
            return unavailable();
        }
        try {
            MediaAsset asset = mediaService.findByObjectKey(objectKey);
            if (asset == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .cacheControl(CacheControl.noStore())
                        .body(Map.of("error", "not_found"));
            }
            byte[] content = mediaService.loadContent(asset.objectKey);
            // 不可变 URL + 长期缓存（object key 基于内容哈希）
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                    .header(HttpHeaders.CONTENT_TYPE, asset.mimeType)
                    .body(content);
        } catch (DataAccessException | IllegalStateException e) {
            return unavailable();
        }
    }

    private static ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("error", "database_unavailable"));
    }
}
