-- #18 切片：Project 内容库与首页精选。
--
-- 数据库可保存任意数量的 Project；featured_order 非空表示精选，且只在
-- 精选范围内唯一（部分唯一索引），首页精选数量天然不超过三个（#14 用户
-- 故事 2/3、实现决策）。不建立服务端 Draft 或修订历史：保存即发布。

CREATE TABLE projects (
    id             BIGSERIAL PRIMARY KEY,
    title          TEXT NOT NULL,
    summary        TEXT NOT NULL,
    role           TEXT NOT NULL,
    year           TEXT NOT NULL,
    repository_url TEXT,
    demo_url       TEXT,
    -- 内容库显示顺序（管理端排序；不需要唯一约束，排序按 (display_order, id)）
    display_order  INTEGER NOT NULL,
    -- 首页精选顺序：NULL = 不精选；精选范围内唯一（部分唯一索引保证 0-3 个）
    featured_order INTEGER,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_projects_external_target CHECK (
        (repository_url IS NOT NULL AND repository_url <> '')
        OR (demo_url IS NOT NULL AND demo_url <> '')
    ),
    CONSTRAINT ck_projects_display_order CHECK (display_order >= 0),
    CONSTRAINT ck_projects_featured_order CHECK (
        featured_order IS NULL OR featured_order BETWEEN 1 AND 3
    )
);

CREATE UNIQUE INDEX uq_projects_featured_order
    ON projects (featured_order) WHERE featured_order IS NOT NULL;

CREATE INDEX idx_projects_display_order ON projects (display_order);

-- 技术栈：有顺序的字符串列表。
CREATE TABLE project_stack_items (
    id         BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    name       TEXT NOT NULL,
    position   INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_stack_items_project_position UNIQUE (project_id, position),
    CONSTRAINT ck_project_stack_items_position CHECK (position >= 0)
);

CREATE INDEX idx_project_stack_items_project ON project_stack_items (project_id, position);

-- 初始值：与当前 projects.json 三个精选作品保持一致。
INSERT INTO projects (title, summary, role, year, repository_url, demo_url, display_order, featured_order)
VALUES
  ('个人主页与博客',
   '从零搭建前后端分离的个人主页与博客系统：Spring Boot 提供匿名只读 API，Vue 3 渲染响应式页面，支持云端部署、冷启动恢复与隐私安全的公开内容展示。',
   '独立开发（全栈）', '2026', 'https://github.com/hxc236/myblog', NULL, 0, 1),
  ('AI 求职助手',
   '围绕岗位匹配度构建本地求职闭环，整合职位采集、规则评分、简历优化、学习规划与模拟面试；通过结构化校验、状态机和用户确认约束 Agent 输出，保护本地求职隐私数据。',
   '独立开发（AI 应用全栈）', '2026', 'https://github.com/hxc236/jobhunt-ai-helper', NULL, 1, 2),
  ('Personal Workbench',
   '构建本机优先的 AI 软件开发工作台，以 GitHub Issue DAG 驱动任务执行，通过独立 Worktree、检查点恢复、自动化验收和人工审批管理从需求规划到安全合并的完整开发流程。',
   '独立开发（Agent 工程与全栈）', '2026', 'https://github.com/hxc236/personal-workbench', NULL, 2, 3);

INSERT INTO project_stack_items (project_id, name, position)
SELECT p.id, i.name, i.position
FROM (VALUES
      (0, 0, 'Spring Boot'), (0, 1, 'Vue 3'), (0, 2, 'Java'), (0, 3, 'JavaScript'), (0, 4, 'Docker'), (0, 5, 'Render'),
      (1, 0, 'Electron'), (1, 1, 'Vue 3'), (1, 2, 'TypeScript'), (1, 3, 'SQLite'), (1, 4, 'Agent SDK'), (1, 5, 'Vitest'),
      (2, 0, 'TypeScript'), (2, 1, 'Vue 3'), (2, 2, 'Node.js'), (2, 3, 'SQLite'), (2, 4, 'Pi Agent'), (2, 5, 'GitHub')
     ) AS i(display_order, position, name)
JOIN projects p ON p.display_order = i.display_order;
