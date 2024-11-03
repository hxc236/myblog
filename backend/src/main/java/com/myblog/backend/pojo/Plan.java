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
public class Plan {
    @TableId(type = IdType.AUTO)
    private Integer planId;
    private Integer userId;
    private String planName;
    private String planDescription;
    private Date createdAt;
    private Date updatedAt;
}
