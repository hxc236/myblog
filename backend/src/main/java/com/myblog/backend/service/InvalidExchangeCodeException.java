package com.myblog.backend.service;

/** 一次性交换码无效（不存在、已使用或已过期）。 */
public class InvalidExchangeCodeException extends RuntimeException {

    public InvalidExchangeCodeException() {
        super("交换码无效、已使用或已过期");
    }
}