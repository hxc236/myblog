package com.myblog.publicsite.web;

import com.myblog.publicsite.site.SiteSettings;
import com.myblog.publicsite.site.SiteSettingsService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin 站点设置 API（#17）：读取与“保存并发布”整组站点设置。
 *
 * <p>只接受固定字段契约（{@link SiteSettings}）；未知字段（含隐私字段）
 * 由全局 strict JSON 反序列化拒绝为 400。保存在同一事务中原子生效。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminSiteSettingsController {

    private static final CacheControl NO_STORE = CacheControl.noStore();

    private final SiteSettingsService settingsService;

    public AdminSiteSettingsController(SiteSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** 当前站点设置（编辑表单初始值）。 */
    @GetMapping("/site-settings")
    public ResponseEntity<?> getSettings() {
        if (!settingsService.isAvailable()) {
            return unavailable();
        }
        return ResponseEntity.ok().cacheControl(NO_STORE).body(settingsService.getSettings());
    }

    /** 保存并发布：同一事务原子更新 Public Introduction、技能分组、作品区与联系方式。 */
    @PutMapping("/site-settings")
    public ResponseEntity<?> saveSettings(@RequestBody(required = false) SiteSettings settings) {
        if (!settingsService.isAvailable()) {
            return unavailable();
        }
        try {
            settingsService.saveSettings(settings);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .cacheControl(NO_STORE)
                    .body(Map.of("error", "validation_failed", "message", e.getMessage()));
        }
        return ResponseEntity.ok()
                .cacheControl(NO_STORE)
                .body(settingsService.getSettings());
    }

    private static ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(NO_STORE)
                .body(Map.of("error", "database_unavailable"));
    }
}
