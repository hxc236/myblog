package com.myblog.publicsite.web;

import com.myblog.publicsite.site.CategoryItem;
import com.myblog.publicsite.site.CategoryTagService;
import com.myblog.publicsite.site.TagItem;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin Category / Tag API（#19）：分类与标签管理。
 *
 * <p>删除正在使用的 Category 时事务内迁移文章到 Uncategorized；删除 Tag
 * 自动解除全部关联。Uncategorized 不可删除/改名。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminTaxonomyController {

    private static final CacheControl NO_STORE = CacheControl.noStore();

    private final CategoryTagService taxonomyService;

    public AdminTaxonomyController(CategoryTagService taxonomyService) {
        this.taxonomyService = taxonomyService;
    }

    // ---- Category ----

    @GetMapping("/categories")
    public ResponseEntity<?> listCategories() {
        if (!taxonomyService.isAvailable()) {
            return unavailable();
        }
        return ResponseEntity.ok().cacheControl(NO_STORE).body(taxonomyService.listCategories());
    }

    @PostMapping("/categories")
    public ResponseEntity<?> createCategory(@RequestBody(required = false) Map<String, String> body) {
        if (!taxonomyService.isAvailable()) {
            return unavailable();
        }
        try {
            CategoryItem created = taxonomyService.createCategory(body == null ? null : body.get("name"));
            return ResponseEntity.status(HttpStatus.CREATED).cacheControl(NO_STORE).body(created);
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        } catch (DataIntegrityViolationException e) {
            return validationFailed(new IllegalArgumentException("分类名称已存在"));
        }
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<?> renameCategory(@PathVariable long id,
                                            @RequestBody(required = false) Map<String, String> body) {
        if (!taxonomyService.isAvailable()) {
            return unavailable();
        }
        try {
            CategoryItem renamed = taxonomyService.renameCategory(id, body == null ? null : body.get("name"));
            if (renamed == null) {
                return notFound();
            }
            return ResponseEntity.ok().cacheControl(NO_STORE).body(renamed);
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        } catch (DataIntegrityViolationException e) {
            return validationFailed(new IllegalArgumentException("分类名称已存在"));
        }
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable long id) {
        if (!taxonomyService.isAvailable()) {
            return unavailable();
        }
        try {
            if (!taxonomyService.deleteCategory(id)) {
                return notFound();
            }
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        }
    }

    // ---- Tag ----

    @GetMapping("/tags")
    public ResponseEntity<?> listTags() {
        if (!taxonomyService.isAvailable()) {
            return unavailable();
        }
        return ResponseEntity.ok().cacheControl(NO_STORE).body(taxonomyService.listTags());
    }

    @PostMapping("/tags")
    public ResponseEntity<?> createTag(@RequestBody(required = false) Map<String, String> body) {
        if (!taxonomyService.isAvailable()) {
            return unavailable();
        }
        try {
            TagItem created = taxonomyService.createTag(body == null ? null : body.get("name"));
            return ResponseEntity.status(HttpStatus.CREATED).cacheControl(NO_STORE).body(created);
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        } catch (DataIntegrityViolationException e) {
            return validationFailed(new IllegalArgumentException("标签 slug 冲突，请更换名称"));
        }
    }

    @PutMapping("/tags/{id}")
    public ResponseEntity<?> renameTag(@PathVariable long id,
                                       @RequestBody(required = false) Map<String, String> body) {
        if (!taxonomyService.isAvailable()) {
            return unavailable();
        }
        try {
            TagItem renamed = taxonomyService.renameTag(id, body == null ? null : body.get("name"));
            if (renamed == null) {
                return notFound();
            }
            return ResponseEntity.ok().cacheControl(NO_STORE).body(renamed);
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        } catch (DataIntegrityViolationException e) {
            return validationFailed(new IllegalArgumentException("标签 slug 冲突，请更换名称"));
        }
    }

    @DeleteMapping("/tags/{id}")
    public ResponseEntity<?> deleteTag(@PathVariable long id) {
        if (!taxonomyService.isAvailable()) {
            return unavailable();
        }
        if (!taxonomyService.deleteTag(id)) {
            return notFound();
        }
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<Map<String, String>> validationFailed(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .cacheControl(NO_STORE)
                .body(Map.of("error", "validation_failed", "message", e.getMessage()));
    }

    private static ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .cacheControl(NO_STORE)
                .body(Map.of("error", "not_found"));
    }

    private static ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(NO_STORE)
                .body(Map.of("error", "database_unavailable"));
    }
}
