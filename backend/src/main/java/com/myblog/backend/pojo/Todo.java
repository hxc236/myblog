package com.myblog.backend.pojo;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Todo {
    @TableId(type = IdType.AUTO)
    private Integer todoId;
    private Integer planId;
    private String title;
    private String description;
    private Date dueDate;
    private boolean isCompleted;
    private Date createdAt;
    private Date updatedAt;
}
