package com.myblog.publicsite.web;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 公开站点 API 错误契约（#17）：请求体不可读（含严格 JSON 反序列化拒绝的
 * 未知/隐私字段）统一返回 400 {@code validation_failed}，不泄漏内部细节。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "error", "validation_failed",
                        "message", "请求体不是有效的 JSON 或包含不允许的字段"));
    }
}
