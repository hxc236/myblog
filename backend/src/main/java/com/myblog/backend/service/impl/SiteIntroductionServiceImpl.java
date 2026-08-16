package com.myblog.backend.service.impl;

import com.myblog.backend.mapper.SiteIntroductionMapper;
import com.myblog.backend.pojo.SiteIntroduction;
import com.myblog.backend.service.SiteIntroductionService;
import org.springframework.stereotype.Service;

/**
 * Public Introduction 只读服务实现（#15）：从 PostgreSQL 读取。
 *
 * <p>PostgreSQL 是 Public Introduction 的唯一运行时权威源；本服务只做只读
 * 查询，数据访问见 {@link SiteIntroductionMapper}。数据库不可用（未配置
 * 数据源或连接失败）时由控制器返回 503，绝不退化为读取 MVP 文件。
 */
@Service
public class SiteIntroductionServiceImpl implements SiteIntroductionService {

    private final SiteIntroductionMapper mapper;

    public SiteIntroductionServiceImpl(SiteIntroductionMapper mapper) {
        this.mapper = mapper;
    }

    /** 数据库读路径是否可用（存在数据源才会注册 JdbcTemplate）。 */
    public boolean isAvailable() {
        return mapper.isAvailable();
    }

    /**
     * 读取公开称呼、Hero 主标题、个人介绍与按 position 排序的技能分组。
     *
     * @throws IllegalStateException 数据库未配置时
     * @throws org.springframework.dao.DataAccessException 数据库不可用时
     */
    public SiteIntroduction getIntroduction() {
        return mapper.getIntroduction();
    }
}
