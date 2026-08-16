package com.myblog.backend.controller;

import com.myblog.backend.pojo.AdminPostDetail;
import com.myblog.backend.pojo.DraftPayload;
import com.myblog.backend.service.PostService;
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
 * Admin Blog Post API（#20）：Draft 创建、保存、预览与立即发布。
 *
 * <p>列表/详情/预览只对 Site Owner 开放；发布在事务内原子更新 Published
 * Revision、发布时间与搜索投影。
 */
@RestController
@RequestMapping("/api/admin/posts")
public class AdminPostController {

    private static final CacheControl NO_STORE = CacheControl.noStore();

    private final PostService postService;

    public AdminPostController(PostService postService) {
        this.postService = postService;
    }

    /** 新建 Blog Post（自动进入 Draft）。 */
    @PostMapping
    public ResponseEntity<?> create() {
        if (!postService.isAvailable()) {
            return unavailable();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(NO_STORE)
                .body(postService.createPost());
    }

    /** 管理端列表（Draft 与已发布）。 */
    @GetMapping
    public ResponseEntity<?> list() {
        if (!postService.isAvailable()) {
            return unavailable();
        }
        return ResponseEntity.ok().cacheControl(NO_STORE).body(postService.listPosts());
    }

    /** 管理端详情 / 预览（仅 Site Owner）。 */
    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable long id) {
        if (!postService.isAvailable()) {
            return unavailable();
        }
        AdminPostDetail detail = postService.getPostDetail(id);
        if (detail == null) {
            return notFound();
        }
        return ResponseEntity.ok().cacheControl(NO_STORE).body(detail);
    }

    /** 保存 Draft（不改变公开内容）。 */
    @PutMapping("/{id}")
    public ResponseEntity<?> saveDraft(@PathVariable long id,
                                       @RequestBody(required = false) DraftPayload payload) {
        if (!postService.isAvailable()) {
            return unavailable();
        }
        try {
            AdminPostDetail detail = postService.saveDraft(id, payload);
            if (detail == null) {
                return notFound();
            }
            return ResponseEntity.ok().cacheControl(NO_STORE).body(detail);
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        }
    }

    /** 立即发布（事务原子更新发布指针、发布时间与搜索投影）。 */
    @PostMapping("/{id}/publish")
    public ResponseEntity<?> publish(@PathVariable long id) {
        if (!postService.isAvailable()) {
            return unavailable();
        }
        try {
            AdminPostDetail detail = postService.publish(id);
            if (detail == null) {
                return notFound();
            }
            return ResponseEntity.ok().cacheControl(NO_STORE).body(detail);
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        }
    }

    /** 修订历史（#22）：全部不可变修订，标记当前已发布版本。 */
    @GetMapping("/{id}/revisions")
    public ResponseEntity<?> revisions(@PathVariable long id) {
        if (!postService.isAvailable()) {
            return unavailable();
        }
        if (postService.getPostDetail(id) == null) {
            return notFound();
        }
        return ResponseEntity.ok().cacheControl(NO_STORE).body(postService.listRevisions(id));
    }

    /** 恢复历史修订为新的 Draft（#22）：预览确认后再发布。 */
    @PostMapping("/{id}/restore")
    public ResponseEntity<?> restore(@PathVariable long id,
                                     @RequestBody(required = false) Map<String, Long> body) {
        if (!postService.isAvailable()) {
            return unavailable();
        }
        Long revisionId = body == null ? null : body.get("revisionId");
        if (revisionId == null) {
            return validationFailed(new IllegalArgumentException("缺少 revisionId"));
        }
        AdminPostDetail detail = postService.restoreRevision(id, revisionId);
        if (detail == null) {
            return notFound();
        }
        return ResponseEntity.ok().cacheControl(NO_STORE).body(detail);
    }

    /** 撤回并归档（#22）：公开指针置空，历史修订保留。 */
    @PostMapping("/{id}/archive")
    public ResponseEntity<?> archive(@PathVariable long id) {
        if (!postService.isAvailable()) {
            return unavailable();
        }
        AdminPostDetail detail = postService.archive(id);
        if (detail == null) {
            return validationFailed(new IllegalArgumentException("只能归档当前已发布的文章"));
        }
        return ResponseEntity.ok().cacheControl(NO_STORE).body(detail);
    }

    /** 永久删除：只允许从未发布的 Draft（#22）。 */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable long id) {
        if (!postService.isAvailable()) {
            return unavailable();
        }
        try {
            if (!postService.deletePost(id)) {
                return notFound();
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