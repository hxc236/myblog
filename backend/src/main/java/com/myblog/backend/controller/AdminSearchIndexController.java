package com.myblog.backend.controller;

import com.myblog.backend.service.PostService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin 搜索索引 API（#23）：从 Published Revision 全量重建搜索投影。
 */
@RestController
@RequestMapping("/api/admin/search-index")
public class AdminSearchIndexController {

    private static final CacheControl NO_STORE = CacheControl.noStore();

    private final PostService postService;

    public AdminSearchIndexController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping("/rebuild")
    public ResponseEntity<?> rebuild() {
        if (!postService.isAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .cacheControl(NO_STORE)
                    .body(Map.of("error", "database_unavailable"));
        }
        int count = postService.rebuildSearchIndex();
        return ResponseEntity.ok()
                .cacheControl(NO_STORE)
                .body(Map.of("rebuilt", count));
    }
}