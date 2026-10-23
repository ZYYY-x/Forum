-- 基础帖子类型（可选种子数据）
-- 仅用于新环境初始化；不会覆盖已有业务数据。
-- 如果线上已经维护了类型，请不要执行本脚本。

USE forum_content;

INSERT INTO db_topic_type (id, name, `desc`, color) VALUES
  (1, '日常闲聊', '在这里分享你的各种日常', '#1E90FF'),
  (2, '真诚交友', '在校园里寻找与自己志同道合的朋友', '#CE1EFF'),
  (3, '问题反馈', '反馈你在校园里遇到的问题', '#E07373'),
  (4, '恋爱官宣', '向大家展示你的恋爱成果', '#0CE73F'),
  (5, '踩坑记录', '将你遇到的坑分享给大家，防止其他人再次入坑', '#3BB62A')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  `desc` = VALUES(`desc`),
  color = VALUES(color);
