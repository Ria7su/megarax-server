-- MegaRax VIVO foundation schema.
-- Additive only: no existing table is altered or dropped.

CREATE TABLE IF NOT EXISTS `megarax_vivo_cycles` (
  `cycle_key` VARCHAR(64) NOT NULL,
  `cycle_type` VARCHAR(24) NOT NULL,
  `title` VARCHAR(96) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PREPARATION',
  `starts_at` BIGINT NOT NULL,
  `ends_at` BIGINT NOT NULL DEFAULT 0,
  `version` INT NOT NULL DEFAULT 1,
  `created_at` BIGINT NOT NULL,
  `updated_at` BIGINT NOT NULL,
  PRIMARY KEY (`cycle_key`),
  KEY `idx_vivo_cycle_status` (`status`,`starts_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `megarax_vivo_world_states` (
  `region_key` VARCHAR(32) NOT NULL,
  `region_name` VARCHAR(64) NOT NULL,
  `security_value` INT NOT NULL DEFAULT 50,
  `supply_value` INT NOT NULL DEFAULT 50,
  `spirit_value` INT NOT NULL DEFAULT 50,
  `state_version` INT NOT NULL DEFAULT 1,
  `updated_at` BIGINT NOT NULL,
  PRIMARY KEY (`region_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `megarax_vivo_needs` (
  `need_key` VARCHAR(64) NOT NULL,
  `cycle_key` VARCHAR(64) NOT NULL,
  `region_key` VARCHAR(32) NOT NULL,
  `title` VARCHAR(96) NOT NULL,
  `description` VARCHAR(255) NOT NULL DEFAULT '',
  `axis` VARCHAR(16) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PREPARATION',
  `progress` BIGINT NOT NULL DEFAULT 0,
  `goal` BIGINT NOT NULL,
  `personal_daily_cap` INT NOT NULL,
  `success_delta` INT NOT NULL DEFAULT 0,
  `success_effect` VARCHAR(255) NOT NULL DEFAULT '',
  `failure_effect` VARCHAR(255) NOT NULL DEFAULT '',
  `starts_at` BIGINT NOT NULL,
  `ends_at` BIGINT NOT NULL DEFAULT 0,
  `completed_at` BIGINT NOT NULL DEFAULT 0,
  `version` INT NOT NULL DEFAULT 1,
  `created_at` BIGINT NOT NULL,
  `updated_at` BIGINT NOT NULL,
  PRIMARY KEY (`need_key`),
  KEY `idx_vivo_need_cycle` (`cycle_key`,`status`),
  KEY `idx_vivo_need_region` (`region_key`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `megarax_vivo_need_categories` (
  `need_key` VARCHAR(64) NOT NULL,
  `category` VARCHAR(24) NOT NULL,
  `progress` BIGINT NOT NULL DEFAULT 0,
  `goal` BIGINT NOT NULL,
  `updated_at` BIGINT NOT NULL,
  PRIMARY KEY (`need_key`,`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `megarax_vivo_daily_contributions` (
  `need_key` VARCHAR(64) NOT NULL,
  `account_name` VARCHAR(45) NOT NULL,
  `day_key` CHAR(10) NOT NULL,
  `total_amount` INT NOT NULL DEFAULT 0,
  `updated_at` BIGINT NOT NULL,
  PRIMARY KEY (`need_key`,`account_name`,`day_key`),
  KEY `idx_vivo_daily_account` (`account_name`,`day_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `megarax_vivo_contribution_ledger` (
  `ledger_id` BIGINT NOT NULL AUTO_INCREMENT,
  `unique_event_key` VARCHAR(128) NOT NULL,
  `need_key` VARCHAR(64) NOT NULL,
  `cycle_key` VARCHAR(64) NOT NULL,
  `region_key` VARCHAR(32) NOT NULL,
  `account_name` VARCHAR(45) NOT NULL,
  `player_id` INT NOT NULL,
  `player_name` VARCHAR(35) NOT NULL,
  `source_type` VARCHAR(32) NOT NULL,
  `source_key` VARCHAR(96) NOT NULL,
  `category` VARCHAR(24) NOT NULL,
  `base_amount` INT NOT NULL,
  `public_multiplier` DECIMAL(7,3) NOT NULL DEFAULT 1.000,
  `accepted_amount` INT NOT NULL,
  `decision_code` VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED',
  `day_key` CHAR(10) NOT NULL,
  `created_at` BIGINT NOT NULL,
  PRIMARY KEY (`ledger_id`),
  UNIQUE KEY `uq_vivo_contribution_event` (`need_key`,`account_name`,`unique_event_key`),
  KEY `idx_vivo_ledger_player` (`player_id`,`created_at`),
  KEY `idx_vivo_ledger_need` (`need_key`,`created_at`),
  KEY `idx_vivo_ledger_region` (`region_key`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `megarax_vivo_player_preferences` (
  `account_name` VARCHAR(45) NOT NULL,
  `intention` VARCHAR(24) NOT NULL DEFAULT 'CONTINUE_STORY',
  `updated_at` BIGINT NOT NULL,
  PRIMARY KEY (`account_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `megarax_vivo_change_log` (
  `change_key` VARCHAR(64) NOT NULL,
  `version_label` VARCHAR(32) NOT NULL,
  `change_type` VARCHAR(24) NOT NULL,
  `title` VARCHAR(96) NOT NULL,
  `before_value` VARCHAR(255) NOT NULL DEFAULT '',
  `after_value` VARCHAR(255) NOT NULL DEFAULT '',
  `reason` VARCHAR(255) NOT NULL DEFAULT '',
  `effective_at` BIGINT NOT NULL,
  `review_at` BIGINT NOT NULL DEFAULT 0,
  `created_at` BIGINT NOT NULL,
  PRIMARY KEY (`change_key`),
  KEY `idx_vivo_change_effective` (`effective_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

