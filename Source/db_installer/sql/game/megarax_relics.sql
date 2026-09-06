CREATE TABLE IF NOT EXISTS `megarax_relics` (
  `relic_id` BIGINT NOT NULL AUTO_INCREMENT,
  `object_id` INT NOT NULL,
  `item_id` INT NOT NULL,
  `grade` CHAR(1) NOT NULL,
  `first_owner_id` INT NOT NULL,
  `first_owner_name` VARCHAR(35) NOT NULL,
  `first_clan_name` VARCHAR(45) NOT NULL DEFAULT '',
  `first_enchant_level` INT NOT NULL,
  `highest_enchant_level` INT NOT NULL,
  `highest_achieved_at` BIGINT NOT NULL,
  `registered_at` BIGINT NOT NULL,
  `last_owner_id` INT NOT NULL,
  `last_owner_name` VARCHAR(35) NOT NULL,
  `last_clan_name` VARCHAR(45) NOT NULL DEFAULT '',
  `last_registered_at` BIGINT NOT NULL,
  PRIMARY KEY (`relic_id`),
  UNIQUE KEY `uq_megarax_relic_object` (`object_id`),
  KEY `idx_megarax_relic_grade_enchant` (`grade`, `highest_enchant_level`),
  KEY `idx_megarax_relic_owner` (`last_owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `megarax_relic_firsts` (
  `grade` CHAR(1) NOT NULL,
  `enchant_level` INT NOT NULL,
  `relic_id` BIGINT NOT NULL,
  `object_id` INT NOT NULL,
  `item_id` INT NOT NULL,
  `player_id` INT NOT NULL,
  `player_name` VARCHAR(35) NOT NULL,
  `clan_name` VARCHAR(45) NOT NULL DEFAULT '',
  `achieved_at` BIGINT NOT NULL,
  PRIMARY KEY (`grade`, `enchant_level`),
  KEY `idx_megarax_relic_first_time` (`achieved_at`),
  KEY `idx_megarax_relic_first_player` (`player_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
