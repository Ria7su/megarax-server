CREATE TABLE IF NOT EXISTS `megarax_world_projects` (
  `project_key` VARCHAR(64) NOT NULL,
  `title` VARCHAR(96) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  `total_points` INT NOT NULL DEFAULT 0,
  `goal_points` INT NOT NULL,
  `started_at` BIGINT NOT NULL,
  `completed_at` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`project_key`),
  KEY `idx_world_project_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `megarax_world_project_contributions` (
  `project_key` VARCHAR(64) NOT NULL,
  `account_name` VARCHAR(45) NOT NULL,
  `player_id` INT NOT NULL,
  `player_name` VARCHAR(35) NOT NULL,
  `category` VARCHAR(32) NOT NULL,
  `amount` INT NOT NULL DEFAULT 0,
  `recognized` TINYINT NOT NULL DEFAULT 0,
  `contributed_at` BIGINT NOT NULL,
  PRIMARY KEY (`project_key`,`account_name`),
  KEY `idx_world_contribution_player` (`player_id`),
  KEY `idx_world_contribution_recognized` (`project_key`,`recognized`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `megarax_world_project_events` (
  `project_key` VARCHAR(64) NOT NULL,
  `account_name` VARCHAR(45) NOT NULL,
  `event_key` VARCHAR(96) NOT NULL,
  `created_at` BIGINT NOT NULL,
  PRIMARY KEY (`project_key`,`account_name`,`event_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

