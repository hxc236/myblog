package com.myblog.backend.service.impl.user.account;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.myblog.backend.mapper.UserMapper;
import com.myblog.backend.pojo.User;
import com.myblog.backend.service.user.account.RegisterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RegisterServiceImpl implements RegisterService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Override
    public Map<String, String> register(String username, String password, String confirmedPassword) {
        Map<String, String> res = new HashMap<>();
        if (username == null) {
            res.put("error_message", "用户名不能为空!");
            return res;
        }
        if (password == null || confirmedPassword == null) {
            res.put("error_message", "密码不能为空!");
            return res;
        }
        username = username.trim();
        if (username.length() == 0) {
            res.put("error_message", "用户名不能为空!");
            return res;
        }
        if (password.length() == 0 || confirmedPassword.length() == 0) {
            res.put("error_message", "密码不能为空!");
            return res;
        }
        if (username.length() > 100) {
            res.put("error_message", "用户名不能超过100!");
            return res;
        }
        if(password.length() > 100 || confirmedPassword.length() > 100) {
            res.put("error_message", "密码长度太长了!");
            return res;
        }
        if(!password.equals(confirmedPassword)) {
            res.put("error_message", "两次输入的密码不一致!");
            return res;
        }

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        List<User> users = userMapper.selectList(queryWrapper);

        if(!users.isEmpty()) {
            res.put("error_message", "用户名已存在!");
            return res;
        }

        String encodedPassword = passwordEncoder.encode(password);

        String photo = "https://pic.imgdb.cn/item/640481eef144a01007a2fab3.jpg";
        String nickname = username;
        User user = new User(null, username, encodedPassword, photo, nickname);
        userMapper.insert(user);
        res.put("error_message", "success");
        return res;
    }
}
