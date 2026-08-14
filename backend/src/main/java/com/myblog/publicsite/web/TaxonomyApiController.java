package com.myblog.publicsite.web;

import com.myblog.publicsite.site.CategoryTagService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 正式领域公开 API：Category 与 Tag（#14 路径契约 {@code /api/categories}、
 * {@code /api/tags}）。
 *
 * <p>只暴露可供 Visitor 过滤 Published Revision 的内容（#19 验收）：没有任何
 * 已发布文章的分类/标签不出现在列表中。
 */
@RestController
public class TaxonomyApiController {

    private static final CacheControl CONTENT_CACHE =
            CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic();

    private final CategoryTagService taxonomyService;

    public TaxonomyApiController(CategoryTagService taxonomyService) {
        this.taxonomyService = taxonomyService;
    }

    @GetMapping("/api/categories")
    public ResponseEntity<?> categories() {
        if (!taxonomyService.isAvailable()) {
            return unavailable();
        }
        try {
            return ResponseEntity.ok()
                    .cacheControl(CONTENT_CACHE)
                    .body(taxonomyService.listPublishedCategories());
        } catch (DataAccessException e) {
            return unavailable();
        }
    }

    @GetMapping("/api/tags")
    public ResponseEntity<?> tags() {
        if (!taxonomyService.isAvailable()) {
            return unavailable();
        }
        try {
            return ResponseEntity.ok()
                    .cacheControl(CONTENT_CACHE)
                    .body(taxonomyService.listPublishedTags());
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
