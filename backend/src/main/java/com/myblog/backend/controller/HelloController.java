package com.myblog.backend.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/hello/")
    public Map<String, String> hello(){
        Map<String, String> res = new HashMap<>();
        res.put("error_message", "success");
        return res;
    }
}
