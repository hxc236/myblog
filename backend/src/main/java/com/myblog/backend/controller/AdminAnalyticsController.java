package com.myblog.backend.controller;

import com.myblog.backend.service.PageViewService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin 内容分析 API（#25）：全站累计、最近三十天趋势、访问量最高十篇与
 * 单篇 30/90 天趋势。只展示匿名聚合，不含 Visitor 身份、独立访客数或画像。
 */
@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {

    private static final CacheControl NO_STORE = CacheControl.noStore();

    private final PageViewService pageViewService;

    public AdminAnalyticsController(PageViewService pageViewService) {
        this.pageViewService = pageViewService;
    }

    @GetMapping
    public ResponseEntity<?> overview() {
        if (!pageViewService.isAvailable()) {
            return unavailable();
        }
        return ResponseEntity.ok().cacheControl(NO_STORE).body(Map.of(
                "siteTotal", pageViewService.siteTotal(),
                "last30Days", pageViewService.siteTrend(30),
                "topPosts", pageViewService.topPosts(10)));
    }

    /** 单篇趋势：days 仅允许 30 或 90。 */
    @GetMapping("/posts/{id}")
    public ResponseEntity<?> postTrend(@PathVariable long id,
                                       @RequestParam(defaultValue = "30") int days) {
        if (!pageViewService.isAvailable()) {
            return unavailable();
        }
        if (days != 30 && days != 90) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .cacheControl(NO_STORE)
                    .body(Map.of("error", "validation_failed", "message", "days 仅允许 30 或 90"));
        }
        return ResponseEntity.ok().cacheControl(NO_STORE)
                .body(pageViewService.postTrend(id, days));
    }

    private static ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(NO_STORE)
                .body(Map.of("error", "database_unavailable"));
    }
}