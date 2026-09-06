CREATE TABLE IF NOT EXISTS `megarax_forgotten_villages` (
  `account_name` VARCHAR(45) NOT NULL,
  `player_id` INT NOT NULL,
  `player_name` VARCHAR(35) NOT NULL,
  `village` VARCHAR(24) NOT NULL,
  `path` VARCHAR(16) NOT NULL,
  `progress` INT NOT NULL DEFAULT 0,
  `goal` INT NOT NULL,
  `status` VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
  `started_at` BIGINT NOT NULL,
  `completed_at` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`account_name`),
  KEY `idx_forgotten_player` (`player_id`),
  KEY `idx_forgotten_village_status` (`village`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

