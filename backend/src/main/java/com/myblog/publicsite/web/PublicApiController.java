package com.myblog.publicsite.web;

import com.myblog.publicsite.content.ContentLoader;
import com.myblog.publicsite.content.Post;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 公开内容 API（#5 5. 公开 API 契约）：匿名、只读，仅 GET / HEAD / OPTIONS。
 */
@RestController
@RequestMapping("/api/v1")
public class PublicApiController {

    private static final CacheControl CONTENT_CACHE =
            CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic();
    private static final CacheControl NO_CACHE = CacheControl.noStore();

    private final ContentLoader contentLoader;

    public PublicApiController(ContentLoader contentLoader) {
        this.contentLoader = contentLoader;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok()
                .cacheControl(NO_CACHE)
                .body(Map.of("status", "ok"));
    }

    @GetMapping("/introduction")
    public ResponseEntity<Object> introduction() {
        return ResponseEntity.ok()
                .cacheControl(CONTENT_CACHE)
                .body(contentLoader.getIntroduction());
    }

    @GetMapping("/projects")
    public ResponseEntity<Object> projects() {
        return ResponseEntity.ok()
                .cacheControl(CONTENT_CACHE)
                .body(contentLoader.getProjects());
    }

    @GetMapping("/posts")
    public ResponseEntity<Object> posts() {
        return ResponseEntity.ok()
                .cacheControl(CONTENT_CACHE)
                .body(contentLoader.getPostsMeta());
    }

    @GetMapping("/posts/{slug}")
    public ResponseEntity<Object> post(@PathVariable String slug) {
        return contentLoader.findPost(slug)
                .<ResponseEntity<Object>>map(post -> ResponseEntity.ok()
                        .cacheControl(CONTENT_CACHE)
                        .body(post))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "not_found")));
    }
}
