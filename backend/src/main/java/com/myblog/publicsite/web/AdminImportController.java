package com.myblog.publicsite.web;

import com.myblog.publicsite.imports.MvpContentImporter;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin 一次性导入（#27）：从随仓库发布的 MVP JSON/Markdown 导入 PostgreSQL。
 * 可重复执行（已有数据的领域跳过），不建立文件与数据库双向同步。
 */
@RestController
@RequestMapping("/api/admin/import")
public class AdminImportController {

    private static final CacheControl NO_STORE = CacheControl.noStore();

    private final MvpContentImporter importer;

    public AdminImportController(MvpContentImporter importer) {
        this.importer = importer;
    }

    @PostMapping("/mvp")
    public ResponseEntity<?> importMvp() {
        if (!importer.isAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .cacheControl(NO_STORE)
                    .body(Map.of("error", "database_unavailable"));
        }
        MvpContentImporter.ImportSummary summary = importer.importAll();
        return ResponseEntity.ok().cacheControl(NO_STORE).body(Map.of(
                "introductionImported", summary.introductionImported,
                "projectsImported", summary.projectsImported,
                "postsImported", summary.postsImported));
    }
}
