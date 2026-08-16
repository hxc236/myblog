package com.myblog.backend.controller;

import com.myblog.backend.service.FeedService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * 公开 RSS / Sitemap（#26）：请求时从数据库生成，只含当前公开内容；
 * ETag + no-cache 重新验证，归档文章立即从两种索引消失。
 */
@RestController
public class FeedController {

    private static final CacheControl NO_CACHE = CacheControl.noCache();

    private final FeedService feedService;
    private final String siteTitle;

    public FeedController(FeedService feedService,
                          @Value("${site.title:hxc236 的个人站}") String siteTitle) {
        this.feedService = feedService;
        this.siteTitle = siteTitle;
    }

    @GetMapping(value = "/api/site/rss.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<?> rss(HttpServletRequest request) {
        if (!feedService.isAvailable()) {
            return unavailable();
        }
        try {
            return withEtag(feedService.rss(siteTitle, baseUrl(request)), request);
        } catch (DataAccessException e) {
            return unavailable();
        }
    }

    @GetMapping(value = "/api/site/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<?> sitemap(HttpServletRequest request) {
        if (!feedService.isAvailable()) {
            return unavailable();
        }
        try {
            return withEtag(feedService.sitemap(baseUrl(request)), request);
        } catch (DataAccessException e) {
            return unavailable();
        }
    }

    /** 站点基础 URL：优先 site.origin（生产），否则取当前请求的协议+主机。 */
    private String baseUrl(HttpServletRequest request) {
        String origin = request.getHeader("X-Forwarded-Proto");
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        String scheme = origin != null && !origin.isBlank() ? origin : request.getScheme();
        return scheme + "://" + host;
    }

    private ResponseEntity<?> withEtag(String xml, HttpServletRequest request) {
        try {
            byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
            String etag = "\"" + sha256Hex(bytes) + "\"";
            HttpHeaders headers = new HttpHeaders();
            headers.setETag(etag);
            headers.setCacheControl(NO_CACHE);
            headers.setContentType(
                    new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8));
            String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
            if (ifNoneMatch != null && ifNoneMatch.trim().equals(etag)) {
                return new ResponseEntity<>(headers, HttpStatus.NOT_MODIFIED);
            }
            return new ResponseEntity<>(xml, headers, HttpStatus.OK);
        } catch (Exception e) {
            throw new IllegalStateException("无法生成公开 Feed", e);
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