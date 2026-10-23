-- forum_user：账号、资料、隐私
-- 对应 user-service；只允许该服务访问本 schema。

USE forum_user;

CREATE TABLE IF NOT EXISTS db_account_jwt (
  id INT NOT NULL AUTO_INCREMENT,
  username VARCHAR(255) DEFAULT NULL,
  password VARCHAR(255) DEFAULT NULL,
  email VARCHAR(255) DEFAULT NULL,
  role VARCHAR(255) DEFAULT NULL,
  avatar VARCHAR(255) DEFAULT NULL,
  register_time DATETIME DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY unique_name (username),
  UNIQUE KEY unique_email (email)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS db_account_details (
  id INT NOT NULL,
  -- 原 study 库里是 tinyblob，这里修正为标准类型
  gender TINYINT NOT NULL DEFAULT 0,
  phone VARCHAR(255) DEFAULT NULL,
  qq VARCHAR(255) DEFAULT NULL,
  wx VARCHAR(255) DEFAULT NULL,
  `desc` VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_details_account
    FOREIGN KEY (id) REFERENCES db_account_jwt (id)
    ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS db_account_privacy (
  id INT NOT NULL,
  phone TINYINT(1) NOT NULL DEFAULT 1,
  email TINYINT(1) NOT NULL DEFAULT 1,
  wx TINYINT(1) NOT NULL DEFAULT 1,
  qq TINYINT(1) NOT NULL DEFAULT 1,
  gender TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  CONSTRAINT fk_privacy_account
    FOREIGN KEY (id) REFERENCES db_account_jwt (id)
    ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
