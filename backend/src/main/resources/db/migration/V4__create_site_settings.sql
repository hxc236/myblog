-- #17 切片：作品区设置与联系方式。
--
-- 与 public_introduction（#15）一起构成“站点设置组”：不建立 Draft 或修订
-- 历史，Admin Console 点击“保存并发布”后在同一事务中原子更新（#14 实现决策）。

-- 作品区设置：标题 + 可选副标题；副标题允许为空字符串，空值前台不渲染。
CREATE TABLE project_section_settings (
    id        BIGINT PRIMARY KEY CHECK (id = 1),
    title     TEXT NOT NULL,
    subtitle  TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 联系方式：公开邮箱、GitHub 链接、版权标识；不建设联系表单。
CREATE TABLE contact_settings (
    id         BIGINT PRIMARY KEY CHECK (id = 1),
    email      TEXT NOT NULL,
    github_url TEXT NOT NULL,
    copyright  TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 初始值：与当前 introduction.json 联系方式与首页作品区文案保持一致。
INSERT INTO project_section_settings (id, title, subtitle)
VALUES (1, '个人项目展示', '');

INSERT INTO contact_settings (id, email, github_url, copyright)
VALUES (1, 'houxc2249@gmail.com', 'https://github.com/hxc236', '© 2026 hxc236');
