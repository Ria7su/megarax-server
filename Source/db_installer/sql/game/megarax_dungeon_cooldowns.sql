CREATE TABLE IF NOT EXISTS megarax_dungeon_cooldowns (
  account_name VARCHAR(45) NOT NULL,
  dungeon_key VARCHAR(24) NOT NULL,
  available_at BIGINT NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (account_name, dungeon_key),
  KEY available_at (available_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
