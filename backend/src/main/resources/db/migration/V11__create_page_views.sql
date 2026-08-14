-- #25 切片：匿名 Page View 聚合。
--
-- 只按 Blog Post 与日期聚合：不保存逐次事件、原始 IP、完整 User-Agent 或
-- 设备指纹（#14 实现决策）。同一浏览器每日去重由浏览器本地标记完成，服务端
-- 不做身份推断。每日明细保留二十四个月（上报时惰性清理），累计值长期保留。

CREATE TABLE page_view_daily (
    post_id BIGINT NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    day     DATE NOT NULL,
    count   INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (post_id, day)
);

CREATE TABLE page_view_totals (
    post_id BIGINT PRIMARY KEY REFERENCES posts (id) ON DELETE CASCADE,
    total   BIGINT NOT NULL DEFAULT 0
);
