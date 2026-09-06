CREATE TABLE IF NOT EXISTS `account_activity_wallet` (
  `account_name` varchar(45) NOT NULL,
  `hunt_marks` bigint unsigned NOT NULL DEFAULT 0,
  `wanted_seals` bigint unsigned NOT NULL DEFAULT 0,
  `war_contribution` bigint unsigned NOT NULL DEFAULT 0,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`account_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `account_activity_wallet_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `account_name` varchar(45) NOT NULL,
  `character_id` int NOT NULL,
  `currency_type` varchar(24) NOT NULL,
  `amount` bigint NOT NULL,
  `balance_before` bigint unsigned NOT NULL,
  `balance_after` bigint unsigned NOT NULL,
  `source` varchar(48) NOT NULL,
  `reference_id` varchar(64) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `account_created` (`account_name`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `account_hunt_contracts` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `account_name` varchar(45) NOT NULL,
  `reset_date` date NOT NULL,
  `offer_slot` tinyint unsigned NOT NULL,
  `contract_template_id` varchar(64) NOT NULL,
  `contract_state` enum('OFFERED','ACTIVE','COMPLETED','CLAIMED','EXPIRED','ABANDONED') NOT NULL DEFAULT 'OFFERED',
  `progress` int unsigned NOT NULL DEFAULT 0,
  `target_count` int unsigned NOT NULL,
  `accepted` tinyint(1) NOT NULL DEFAULT 0,
  `premium_slot` tinyint(1) NOT NULL DEFAULT 0,
  `generated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `accepted_at` timestamp NULL DEFAULT NULL,
  `completed_at` timestamp NULL DEFAULT NULL,
  `claimed_at` timestamp NULL DEFAULT NULL,
  `last_character_id` int NOT NULL DEFAULT 0,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `account_day_slot` (`account_name`,`reset_date`,`offer_slot`), KEY `account_state` (`account_name`,`contract_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `account_hunt_contract_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `account_name` varchar(45) NOT NULL,
  `character_id` int NOT NULL,
  `contract_template_id` varchar(64) NOT NULL,
  `reset_date` date NOT NULL,
  `final_progress` int unsigned NOT NULL,
  `final_state` varchar(16) NOT NULL,
  `hunt_marks` bigint unsigned NOT NULL DEFAULT 0,
  `items_delivered` varchar(512) NOT NULL DEFAULT '',
  `premium_slot` tinyint(1) NOT NULL DEFAULT 0,
  `accepted_at` timestamp NULL DEFAULT NULL,
  `completed_at` timestamp NULL DEFAULT NULL,
  `claimed_at` timestamp NULL DEFAULT NULL,
  `result` varchar(32) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `account_created` (`account_name`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
