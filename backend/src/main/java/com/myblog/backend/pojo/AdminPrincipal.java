package com.myblog.backend.pojo;

/** 已认证的 Site Owner 身份（#16）。 */
public class AdminPrincipal {

    private final String login;

    public AdminPrincipal(String login) {
        this.login = login;
    }

    public String getLogin() {
        return login;
    }

    @Override
    public String toString() {
        return "AdminPrincipal{login=" + login + "}";
    }
}