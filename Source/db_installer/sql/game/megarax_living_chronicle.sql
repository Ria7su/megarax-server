CREATE TABLE IF NOT EXISTS `megarax_chronicle_players` (
  `player_id` INT NOT NULL,
  `account_name` VARCHAR(45) NOT NULL,
  `player_name` VARCHAR(35) NOT NULL,
  `first_seen` BIGINT NOT NULL,
  `last_seen` BIGINT NOT NULL,
  `legacy_points` INT NOT NULL DEFAULT 0,
  `milestone_count` INT NOT NULL DEFAULT 0,
  `profession_count` INT NOT NULL DEFAULT 0,
  `raid_count` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`player_id`),
  KEY `idx_chronicle_player_name` (`player_name`),
  KEY `idx_chronicle_legacy` (`legacy_points`),
  KEY `idx_chronicle_last_seen` (`last_seen`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `megarax_chronicle_events` (
  `event_id` BIGINT NOT NULL AUTO_INCREMENT,
  `player_id` INT NOT NULL,
  `player_name` VARCHAR(35) NOT NULL,
  `event_type` VARCHAR(32) NOT NULL,
  `event_key` VARCHAR(128) NOT NULL,
  `title` VARCHAR(96) NOT NULL,
  `details` VARCHAR(255) NOT NULL DEFAULT '',
  `legacy_points` INT NOT NULL DEFAULT 0,
  `event_time` BIGINT NOT NULL,
  `is_public` TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uq_chronicle_player_event` (`player_id`,`event_type`,`event_key`),
  KEY `idx_chronicle_event_time` (`event_time`),
  KEY `idx_chronicle_event_type` (`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `megarax_chronicle_firsts` (
  `achievement_key` VARCHAR(160) NOT NULL,
  `event_type` VARCHAR(32) NOT NULL,
  `player_id` INT NOT NULL,
  `player_name` VARCHAR(35) NOT NULL,
  `title` VARCHAR(96) NOT NULL,
  `details` VARCHAR(255) NOT NULL DEFAULT '',
  `event_time` BIGINT NOT NULL,
  PRIMARY KEY (`achievement_key`),
  KEY `idx_chronicle_first_time` (`event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

