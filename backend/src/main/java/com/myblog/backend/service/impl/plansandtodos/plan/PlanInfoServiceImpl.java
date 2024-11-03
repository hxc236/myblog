package com.myblog.backend.service.impl.plansandtodos.plan;

import com.myblog.backend.mapper.PlanMapper;
import com.myblog.backend.service.plansandtodos.plan.PlanInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PlanInfoServiceImpl implements PlanInfoService {

    @Autowired
    private PlanMapper planMapper;

    @Override
    public Map<String, Object> getPlanInfo() {
        /* 返回一个形如这种的对象：
           {
            "error_message": "success",
             "plan_info": [
             {"plan_id": xxx, "plan_name": "xxx", "plan_description": "xxx", "create_at": "xxx",
              "update_at": "xxx", "todolist": [
              {"todo_id": xxx, "title": "xxx", "description": "xxx", "is_completed": xxx, "create_at": "xx", "update_at": "xx"},
              {"todo_id": xxx, "title": "xxx", "description": "xxx", "is_completed": xxx, "create_at": "xx", "update_at": "xx"},
              ]
             },
             {},
             {}]
            }


         */
        Map<String, Object> result = new HashMap<>();

        return result;
    }
}
