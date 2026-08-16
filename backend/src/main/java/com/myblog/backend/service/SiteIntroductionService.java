package com.myblog.backend.service;

import com.myblog.backend.pojo.SiteIntroduction;

/**
 * Public Introduction 只读服务契约（#15）：从 PostgreSQL 读取。
 *
 * <p>实现见 {@link com.myblog.backend.service.impl.SiteIntroductionServiceImpl}。
 * PostgreSQL 是 Public Introduction 的唯一运行时权威源；数据库不可用（未
 * 配置数据源或连接失败）时由控制器返回 503，绝不退化为读取 MVP 文件。
 */
public interface SiteIntroductionService {

    /** 数据库读路径是否可用（存在数据源才会注册 JdbcTemplate）。 */
    boolean isAvailable();

    /**
     * 读取公开称呼、Hero 主标题、个人介绍与按 position 排序的技能分组。
     *
     * @throws IllegalStateException 数据库未配置时
     * @throws org.springframework.dao.DataAccessException 数据库不可用时
     */
    SiteIntroduction getIntroduction();
}
