package com.myblog.publicsite.web;

import com.myblog.publicsite.analytics.PageViewService;
import com.myblog.publicsite.posts.PublicPostService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公开 Page View 上报（#25）：POST /api/posts/{slug}/view。
 *
 * <p>匿名聚合：服务端只累加 (post_id, day)，不读取任何请求身份信息；
 * 同一浏览器每日去重由浏览器本地标记决定。未发布/不存在文章返回 404。
 */
@RestController
public class PageViewController {

    private static final CacheControl NO_STORE = CacheControl.noStore();

    private final PublicPostService postService;
    private final PageViewService pageViewService;

    public PageViewController(PublicPostService postService, PageViewService pageViewService) {
        this.postService = postService;
        this.pageViewService = pageViewService;
    }

    @PostMapping("/api/posts/{slug}/view")
    public ResponseEntity<?> report(@PathVariable String slug) {
        if (!pageViewService.isAvailable()) {
            return unavailable();
        }
        com.myblog.publicsite.posts.PublicPostDetail post = postService.getPublishedBySlug(slug);
        if (post == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .cacheControl(NO_STORE)
                    .body(Map.of("error", "not_found"));
        }
        pageViewService.reportView(post.id);
        return ResponseEntity.ok().cacheControl(NO_STORE).body(Map.of("ok", true));
    }

    private static ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(NO_STORE)
                .body(Map.of("error", "database_unavailable"));
    }
}
