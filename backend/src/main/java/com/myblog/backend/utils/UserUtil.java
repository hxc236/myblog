package com.myblog.backend.utils;

import com.myblog.backend.pojo.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

public class UserUtil {

    public static User getUser() {
        // 返回一个Spring Security中专门用于存储用户名和密码认证信息的对象；
        // 从Security上下文中获取当前的认证信息
        UsernamePasswordAuthenticationToken authentication =
                (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();

        UserDetailsImpl loginUser = (UserDetailsImpl) authentication.getPrincipal();
        // 这样可以获取当前token对应的User类
        return loginUser.getUser();
    }
}
