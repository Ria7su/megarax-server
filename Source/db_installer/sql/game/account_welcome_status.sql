CREATE TABLE IF NOT EXISTS `account_welcome_status` (
  `account_name` varchar(45) NOT NULL,
  `welcome_shown` tinyint(1) NOT NULL DEFAULT 0,
  `first_shown_at` timestamp NULL DEFAULT NULL,
  `last_opened_at` timestamp NULL DEFAULT NULL,
  `last_character_id` int NOT NULL DEFAULT 0,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`account_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

