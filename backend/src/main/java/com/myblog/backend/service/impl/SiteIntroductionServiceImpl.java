package com.myblog.backend.service.impl;
import com.myblog.backend.service.SiteIntroductionService;

import com.myblog.backend.pojo.SkillGroup;
import com.myblog.backend.pojo.SiteIntroduction;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 PostgreSQL 读取 Public Introduction 的只读服务（#15）。
 *
 * <p>PostgreSQL 是 Public Introduction 的唯一运行时权威源；本服务只做只读
 * 查询。数据库不可用（未配置数据源或连接失败）时由控制器返回 503，绝不
 * 退化为读取 MVP 文件。
 */
@Service
public class SiteIntroductionServiceImpl implements SiteIntroductionService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;

    public SiteIntroductionServiceImpl(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 数据库读路径是否可用（存在数据源才会注册 JdbcTemplate）。 */
    public boolean isAvailable() {
        return jdbcTemplate.getIfAvailable() != null;
    }

    /**
     * 读取公开称呼、Hero 主标题、个人介绍与按 position 排序的技能分组。
     *
     * @throws IllegalStateException 数据库未配置时
     * @throws org.springframework.dao.DataAccessException 数据库不可用时
     */
    public SiteIntroduction getIntroduction() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("数据库未配置：无法读取 Public Introduction");
        }

        SiteIntroduction intro = jdbc.queryForObject(
                "SELECT display_name, headline, introduction"
                        + "  FROM public_introduction"
                        + " WHERE id = 1",
                (rs, rowNum) -> {
                    SiteIntroduction result = new SiteIntroduction();
                    result.displayName = rs.getString("display_name");
                    result.headline = rs.getString("headline");
                    result.introduction = rs.getString("introduction");
                    result.skillGroups = new ArrayList<>();
                    return result;
                });

        Map<Long, SkillGroup> groupsById = new LinkedHashMap<>();
        List<Long> groupOrder = new ArrayList<>();
        jdbc.query(
                "SELECT id, name FROM skill_groups ORDER BY position, id",
                (RowCallbackHandler) rs -> {
                    SkillGroup group = new SkillGroup();
                    group.name = rs.getString("name");
                    group.skills = new ArrayList<>();
                    groupsById.put(rs.getLong("id"), group);
                    groupOrder.add(rs.getLong("id"));
                });
        jdbc.query(
                "SELECT group_id, name FROM skill_group_items ORDER BY group_id, position, id",
                (RowCallbackHandler) rs ->
                        groupsById.get(rs.getLong("group_id")).skills.add(rs.getString("name")));

        for (Long id : groupOrder) {
            intro.skillGroups.add(groupsById.get(id));
        }
        return intro;
    }
}