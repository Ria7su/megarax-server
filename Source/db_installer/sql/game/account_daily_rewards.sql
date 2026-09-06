-- Megarax Premium Daily Rewards
CREATE TABLE IF NOT EXISTS `account_daily_rewards` (
  `account_name` varchar(45) NOT NULL,
  `free_cycle_day` tinyint unsigned NOT NULL DEFAULT 1,
  `premium_cycle_day` tinyint unsigned NOT NULL DEFAULT 1,
  `last_free_claim_date` date DEFAULT NULL,
  `last_premium_claim_date` date DEFAULT NULL,
  `online_minutes_date` date DEFAULT NULL,
  `current_online_minutes` int unsigned NOT NULL DEFAULT 0,
  `total_free_claims` int unsigned NOT NULL DEFAULT 0,
  `total_premium_claims` int unsigned NOT NULL DEFAULT 0,
  `reward_season` varchar(32) NOT NULL DEFAULT 'season1',
  `last_character_id` int NOT NULL DEFAULT 0,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`account_name`)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `account_daily_reward_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `account_name` varchar(45) NOT NULL,
  `character_id` int NOT NULL,
  `reward_track` varchar(8) NOT NULL,
  `reward_day` tinyint unsigned NOT NULL,
  `reward_id` varchar(64) NOT NULL,
  `item_id` int NOT NULL,
  `item_count` bigint NOT NULL,
  `claim_date` date NOT NULL,
  `claim_timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `ip_address` varchar(45) DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `account_date` (`account_name`,`claim_date`)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

