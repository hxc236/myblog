-- #15 切片初始值：当前页面已采用的新 Public Introduction 与五组能力展示。
--
-- #14 规格：数据库迁移初始值必须是“全栈与桌面、AI 应用、数据与接口、
-- 工程交付、软件工程”五组，而不是旧的 “Java · Spring Boot · Vue” 眉题
-- 或三组技能枚举；内容与当前 introduction.json（无眉题版本）保持一致。

INSERT INTO public_introduction (id, display_name, headline, introduction)
VALUES (1,
        'hxc236',
        '构建可靠的全栈应用',
        'AI时代，技术栈、语言已不是门槛。我是一名正在成长的全栈开发者，正在探索与 AI Agent 结合的应用开发路线，关注从需求分析、产品设计到工程交付的完整过程。');

INSERT INTO skill_groups (name, position)
VALUES ('全栈与桌面', 0),
       ('AI 应用', 1),
       ('数据与接口', 2),
       ('工程交付', 3),
       ('软件工程', 4);

INSERT INTO skill_group_items (group_id, name, position)
SELECT g.id, i.name, i.item_position
FROM (VALUES
      (0, 0, 'Java'), (0, 1, 'Spring Boot'), (0, 2, 'Vue 3'), (0, 3, 'TypeScript'), (0, 4, 'Electron'),
      (1, 0, 'AI Agent'), (1, 1, 'Agent Loop'), (1, 2, 'Tool Calling'), (1, 3, 'Pi Agent RPC'),
      (2, 0, 'REST API'), (2, 1, 'MyBatis-Plus'), (2, 2, 'MySQL'), (2, 3, 'SQLite'),
      (3, 0, 'Git / GitHub'), (3, 1, 'Maven'), (3, 2, 'Docker'), (3, 3, 'Render'),
      (4, 0, 'TDD'), (4, 1, 'DDD'), (4, 2, 'Issue-driven Development')
     ) AS i(group_position, item_position, name)
JOIN skill_groups g ON g.position = i.group_position;
