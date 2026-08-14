-- #15 切片：数据库驱动的 Public Introduction 基线
--
-- Public Introduction 只允许保存规格允许的公开字段（公开称呼、Hero 主标题、
-- 隐私安全个人介绍、可排序技能分组）；被隐私边界排除的字段（位置、求职状态、
-- 简历、肖像、经历时间线、Hero 眉题等）不在任何表中建模，从结构上保证
-- 公开读取不可能暴露它们（#14 用户故事 4）。

CREATE TABLE public_introduction (
    id           BIGINT PRIMARY KEY CHECK (id = 1),
    display_name TEXT NOT NULL,
    headline     TEXT NOT NULL,
    introduction TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 技能分组：可排序、可增删改名，名称唯一（#14 用户故事 7、测试决策）。
CREATE TABLE skill_groups (
    id         BIGSERIAL PRIMARY KEY,
    name       TEXT NOT NULL,
    position   INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_skill_groups_name UNIQUE (name),
    CONSTRAINT uq_skill_groups_position UNIQUE (position),
    CONSTRAINT ck_skill_groups_position_non_negative CHECK (position >= 0)
);

-- 组内技术项：按分组与位置排序，组内位置唯一；删除分组时级联删除。
CREATE TABLE skill_group_items (
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT NOT NULL REFERENCES skill_groups (id) ON DELETE CASCADE,
    name       TEXT NOT NULL,
    position   INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_skill_group_items_group_position UNIQUE (group_id, position),
    CONSTRAINT ck_skill_group_items_position_non_negative CHECK (position >= 0)
);

CREATE INDEX idx_skill_group_items_group_position
    ON skill_group_items (group_id, position);
