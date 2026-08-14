package com.myblog.publicsite.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myblog.publicsite.posts.PublicPostService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * 正式领域公开 API：Blog Post 列表与详情（#14 路径契约 {@code /api/posts}）。
 *
 * <p>只返回 Published Revision；Draft、旧修订与归档文章不可读。列表与详情
 * 使用 ETag + {@code Cache-Control: no-cache} 重新验证语义（#14 实现决策）：
 * 客户端带 {@code If-None-Match} 时返回 304，内容变化时 ETag 变化。
 */
@RestController
public class PublicPostsController {

    private static final CacheControl NO_CACHE = CacheControl.noCache();

    private final PublicPostService postService;
    private final ObjectMapper objectMapper;

    public PublicPostsController(PublicPostService postService, ObjectMapper objectMapper) {
        this.postService = postService;
        this.objectMapper = objectMapper;
    }

    /** 分页列表 / 搜索：q 存在时进入搜索模式（标题+摘要、标题优先）。 */
    @GetMapping("/api/posts")
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) Long tag,
            @RequestParam(required = false) String q,
            javax.servlet.http.HttpServletRequest request) {
        if (!postService.isAvailable()) {
            return unavailable();
        }
        try {
            Object payload = (q == null || q.trim().isEmpty())
                    ? postService.listPublished(page, pageSize, category, tag)
                    : postService.searchPublished(q, page, pageSize, category, tag);
            return withEtag(payload, request);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("error", "validation_failed", "message", e.getMessage()));
        } catch (DataAccessException e) {
            return unavailable();
        }
    }

    /** 稳定 slug 对应的 Published Revision 详情；历史 slug 返回永久 301（#22）。 */
    @GetMapping("/api/posts/{slug}")
    public ResponseEntity<?> detail(@PathVariable String slug,
                                    javax.servlet.http.HttpServletRequest request) {
        if (!postService.isAvailable()) {
            return unavailable();
        }
        try {
            PublicPostService.ResolvedSlug resolved = postService.resolvePublishedSlug(slug);
            if (resolved == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .cacheControl(CacheControl.noStore())
                        .body(Map.of("error", "not_found"));
            }
            if (resolved.redirectToSlug != null) {
                // 旧 slug 永久 301 到当前 slug（归档后目标不再已发布 → 上面 404）
                return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                        .location(URI.create("/api/posts/" + resolved.redirectToSlug))
                        .cacheControl(CacheControl.noCache())
                        .build();
            }
            return withEtag(resolved.detail, request);
        } catch (DataAccessException e) {
            return unavailable();
        }
    }

    /** ETag + no-cache：If-None-Match 命中时返回 304。 */
    private ResponseEntity<?> withEtag(Object payload, javax.servlet.http.HttpServletRequest request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(payload);
            String etag = "\"" + sha256Hex(json) + "\"";
            HttpHeaders headers = new HttpHeaders();
            headers.setETag(etag);
            headers.setCacheControl(NO_CACHE);
            headers.setContentType(MediaType.APPLICATION_JSON);
            String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
            if (ifNoneMatch != null && ifNoneMatch.trim().equals(etag)) {
                return new ResponseEntity<>(headers, HttpStatus.NOT_MODIFIED);
            }
            return new ResponseEntity<>(new String(json, StandardCharsets.UTF_8), headers, HttpStatus.OK);
        } catch (Exception e) {
            throw new IllegalStateException("无法序列化公开响应", e);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("error", "database_unavailable"));
    }
}
