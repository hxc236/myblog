package com.myblog.backend.controller;

import com.myblog.backend.service.ProjectService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 正式领域公开 API：首页精选 Project（#14 路径契约 {@code /api/projects}）。
 *
 * <p>只返回零至三个按 featured_order 排序的精选 Project；无精选时返回空
 * 列表（#14 用户故事 3）。第二阶段不新增公开 Project 列表页。
 */
@RestController
public class ProjectsApiController {

    private static final CacheControl CONTENT_CACHE =
            CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic();

    private final ProjectService projectService;

    public ProjectsApiController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/api/projects")
    public ResponseEntity<?> featuredProjects() {
        if (!projectService.isAvailable()) {
            return unavailable();
        }
        try {
            return ResponseEntity.ok()
                    .cacheControl(CONTENT_CACHE)
                    .body(projectService.listFeatured());
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