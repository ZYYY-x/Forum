-- Optional seed: five base topic types for a fresh forum_content schema.
-- Safe to re-run.
SET search_path TO forum_content;

INSERT INTO db_topic_type (id, name, "desc", color) VALUES
    (1, '日常闲聊', '在这里分享你的各种日常', '#1E90FF'),
    (2, '真诚交友', '在校园里寻找与自己志同道合的朋友', '#CE1EFF'),
    (3, '问题反馈', '反馈你在校园里遇到的问题', '#E07373'),
    (4, '恋爱官宣', '向大家展示你的恋爱成果', '#0CE73F'),
    (5, '踩坑记录', '将你遇到的坑分享给大家，防止其他人再次入坑', '#3BB62A')
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    "desc" = EXCLUDED."desc",
    color = EXCLUDED.color;

-- Explicit IDs above do not advance an identity sequence; move it past the max id.
SELECT setval(
    pg_get_serial_sequence('db_topic_type', 'id'),
    (SELECT COALESCE(MAX(id), 1) FROM db_topic_type),
    true
);
