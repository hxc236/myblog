package com.myblog.publicsite.web;

import com.myblog.publicsite.site.ProjectItem;
import com.myblog.publicsite.site.ProjectService;
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
 * Admin Project API（#18）：内容库 CRUD、排序与首页精选。
 *
 * <p>保存即发布（无 Draft/修订历史）；校验失败返回 400 validation_failed，
 * 未找到返回 404。
 */
@RestController
@RequestMapping("/api/admin/projects")
public class AdminProjectController {

    private static final CacheControl NO_STORE = CacheControl.noStore();

    private final ProjectService projectService;

    public AdminProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        if (!projectService.isAvailable()) {
            return unavailable();
        }
        return ResponseEntity.ok().cacheControl(NO_STORE).body(projectService.listProjects());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody(required = false) ProjectItem project) {
        if (!projectService.isAvailable()) {
            return unavailable();
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .cacheControl(NO_STORE)
                    .body(projectService.createProject(project));
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable long id,
                                    @RequestBody(required = false) ProjectItem project) {
        if (!projectService.isAvailable()) {
            return unavailable();
        }
        try {
            ProjectItem updated = projectService.updateProject(id, project);
            if (updated == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .cacheControl(NO_STORE)
                        .body(Map.of("error", "not_found"));
            }
            return ResponseEntity.ok().cacheControl(NO_STORE).body(updated);
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable long id) {
        if (!projectService.isAvailable()) {
            return unavailable();
        }
        if (!projectService.deleteProject(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .cacheControl(NO_STORE)
                    .body(Map.of("error", "not_found"));
        }
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<Map<String, String>> validationFailed(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .cacheControl(NO_STORE)
                .body(Map.of("error", "validation_failed", "message", e.getMessage()));
    }

    private static ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(NO_STORE)
                .body(Map.of("error", "database_unavailable"));
    }
}
