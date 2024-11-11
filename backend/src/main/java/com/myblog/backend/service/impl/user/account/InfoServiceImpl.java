package com.myblog.backend.service.impl.user.account;

import com.myblog.backend.pojo.User;
import com.myblog.backend.service.user.account.InfoService;
import com.myblog.backend.utils.UserDetailsImpl;
import com.myblog.backend.utils.UserUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class InfoServiceImpl implements InfoService {
    @Override
    public Map<String, String> getInfo() {

        User user = UserUtil.getUser();

        Map<String, String> res = new HashMap<>();
        res.put("error_message", "success");
        res.put("id", user.getUserId().toString());
        res.put("username", user.getUsername());
        res.put("photo", user.getPhoto());
        return res;
    }
}
