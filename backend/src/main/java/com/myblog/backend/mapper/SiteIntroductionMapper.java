package com.myblog.backend.mapper;

import com.myblog.backend.pojo.SiteIntroduction;
import com.myblog.backend.pojo.SkillGroup;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public Introduction 数据访问（#15）：public_introduction 与技能分组。
 *
 * <p>PostgreSQL 是 Public Introduction 的唯一运行时权威源；本 mapper 只做
 * 该表组的读取与写入，业务规则由 service 层协调。
 */
@Component
public class SiteIntroductionMapper {

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;

    public SiteIntroductionMapper(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isAvailable() {
        return jdbcTemplate.getIfAvailable() != null;
    }

    /** 是否已有任何 Public Introduction 内容（一次性导入判空用）。 */
    public boolean hasAnyIntroduction() {
        JdbcTemplate jdbc = requireJdbc();
        Integer hasContent = jdbc.query(
                "SELECT 1 FROM public_introduction LIMIT 1",
                rs -> rs.next() ? 1 : null);
        return hasContent != null;
    }

    /**
     * 读取公开称呼、Hero 主标题、个人介绍与按 position 排序的技能分组。
     *
     * @throws org.springframework.dao.DataAccessException 数据库不可用时
     */
    public SiteIntroduction getIntroduction() {
        JdbcTemplate jdbc = requireJdbc();
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

    /** 写入初始 Public Introduction（一次性导入）。 */
    public void insertIntroduction(String displayName, String headline, String introduction) {
        requireJdbc().update(
                "INSERT INTO public_introduction (id, display_name, headline, introduction)"
                        + " VALUES (1, ?, ?, ?)",
                displayName, headline, introduction);
    }

    /** 更新 Public Introduction（保存并发布）。 */
    public void updateIntroduction(String displayName, String headline, String introduction) {
        requireJdbc().update(
                "UPDATE public_introduction"
                        + "   SET display_name = ?, headline = ?, introduction = ?, updated_at = now()"
                        + " WHERE id = 1",
                displayName, headline, introduction);
    }

    /** 清空全部技能分组（保存并发布时整组替换，先删 items 再删 groups）。 */
    public void deleteAllSkillGroups() {
        JdbcTemplate jdbc = requireJdbc();
        jdbc.update("DELETE FROM skill_group_items");
        jdbc.update("DELETE FROM skill_groups");
    }

    /** 写入技能分组（position 决定排序）。 */
    public void insertSkillGroup(String name, int position) {
        requireJdbc().update(
                "INSERT INTO skill_groups (name, position) VALUES (?, ?)",
                name, position);
    }

    /** 写入技能项：按分组 position 归属（保存/导入共用形状）。 */
    public void insertSkillGroupItemByGroupPosition(String name, int position, int groupPosition) {
        requireJdbc().update(
                "INSERT INTO skill_group_items (group_id, name, position)"
                        + " SELECT id, ?, ? FROM skill_groups WHERE position = ?",
                name, position, groupPosition);
    }

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("数据库未配置：无法读取 Public Introduction");
        }
        return jdbc;
    }
}
