package com.myblog.backend.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.myblog.backend.mapper.PlanMapper;
import com.myblog.backend.mapper.TodoMapper;
import com.myblog.backend.pojo.Plan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class HelloController {

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private TodoMapper todoMapper;

    @GetMapping("/hello/")
    public Map<String, Object> hello(){
        Map<String, Object> res = new HashMap<>();
        res.put("error_message", "success");
        List<Plan> test_list;
        QueryWrapper<Plan> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", 2);
        test_list = planMapper.selectList(queryWrapper);
        res.put("test_list", test_list);
        return res;
    }
}
