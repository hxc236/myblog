package com.myblog.backend.controller;

import com.myblog.backend.service.SiteIntroductionService;
import com.myblog.backend.service.SiteSettingsService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 正式领域公开 API（#14「正式 API 仅使用无版本的领域语义路径」）。
 *
 * <p>数据库驱动内容（#15 起为 Public Introduction）只从 PostgreSQL 读取；
 * 未配置数据源或数据库不可用时返回 503，不退回 MVP 文件读路径，也不新增
 * 任何 URL 版本路径。
 */
@RestController
@RequestMapping("/api/site")
public class SiteApiController {

    private static final CacheControl CONTENT_CACHE =
            CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic();

    private final SiteIntroductionService introductionService;
    private final SiteSettingsService settingsService;

    public SiteApiController(SiteIntroductionService introductionService,
                             SiteSettingsService settingsService) {
        this.introductionService = introductionService;
        this.settingsService = settingsService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("status", "ok"));
    }

    @GetMapping("/introduction")
    public ResponseEntity<?> introduction() {
        if (!introductionService.isAvailable()) {
            return unavailable();
        }
        try {
            return ResponseEntity.ok()
                    .cacheControl(CONTENT_CACHE)
                    .body(introductionService.getIntroduction());
        } catch (DataAccessException e) {
            return unavailable();
        }
    }

    /** 联系方式（#17）：公开邮箱、GitHub 链接、版权标识。 */
    @GetMapping("/contact")
    public ResponseEntity<?> contact() {
        if (!settingsService.isAvailable()) {
            return unavailable();
        }
        try {
            return ResponseEntity.ok()
                    .cacheControl(CONTENT_CACHE)
                    .body(settingsService.getSettings().contact);
        } catch (DataAccessException e) {
            return unavailable();
        }
    }

    /** 作品区设置（#17）：标题 + 可选副标题（空值前台不渲染）。 */
    @GetMapping("/work-section")
    public ResponseEntity<?> workSection() {
        if (!settingsService.isAvailable()) {
            return unavailable();
        }
        try {
            return ResponseEntity.ok()
                    .cacheControl(CONTENT_CACHE)
                    .body(settingsService.getSettings().workSection);
        } catch (DataAccessException e) {
            return unavailable();
        }
    }

    private static ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("error", "database_unavailable"));
    }
}