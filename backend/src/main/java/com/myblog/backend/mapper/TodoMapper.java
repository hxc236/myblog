package com.myblog.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myblog.backend.pojo.Todo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TodoMapper extends BaseMapper<Todo> {
}
