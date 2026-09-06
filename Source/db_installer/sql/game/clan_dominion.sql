CREATE TABLE IF NOT EXISTS clan_dominion (
  clan_id INT NOT NULL,
  tier INT NOT NULL DEFAULT 0,
  total_points BIGINT NOT NULL DEFAULT 0,
  season_points BIGINT NOT NULL DEFAULT 0,
  weekly_activity INT NOT NULL DEFAULT 0,
  pvp_kills INT NOT NULL DEFAULT 0,
  pvp_deaths INT NOT NULL DEFAULT 0,
  raid_score INT NOT NULL DEFAULT 0,
  territory_score INT NOT NULL DEFAULT 0,
  siege_score INT NOT NULL DEFAULT 0,
  mission_score INT NOT NULL DEFAULT 0,
  last_activity BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (clan_id),
  KEY idx_clan_dominion_tier (tier),
  KEY idx_clan_dominion_season_points (season_points),
  KEY idx_clan_dominion_last_activity (last_activity)
);

CREATE TABLE IF NOT EXISTS clan_dominion_points_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  clan_id INT NOT NULL,
  target_clan_id INT NOT NULL DEFAULT 0,
  season_id INT NOT NULL DEFAULT 0,
  amount INT NOT NULL,
  reason VARCHAR(48) NOT NULL,
  actor_name VARCHAR(45) NOT NULL DEFAULT '',
  target_name VARCHAR(45) NOT NULL DEFAULT '',
  metadata VARCHAR(255) NOT NULL DEFAULT '',
  created_at BIGINT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_cdp_log_clan (clan_id),
  KEY idx_cdp_log_target_clan (target_clan_id),
  KEY idx_cdp_log_season (season_id),
  KEY idx_cdp_log_created (created_at)
);

CREATE TABLE IF NOT EXISTS clan_missions (
  mission_id VARCHAR(40) NOT NULL,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(255) NOT NULL,
  type VARCHAR(32) NOT NULL,
  goal INT NOT NULL,
  reward_points INT NOT NULL,
  active TINYINT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (mission_id),
  KEY idx_clan_missions_type (type),
  KEY idx_clan_missions_active (active)
);

CREATE TABLE IF NOT EXISTS clan_mission_progress (
  clan_id INT NOT NULL,
  mission_id VARCHAR(40) NOT NULL,
  cycle_key VARCHAR(24) NOT NULL,
  progress INT NOT NULL DEFAULT 0,
  claimed TINYINT NOT NULL DEFAULT 0,
  completed_at BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (clan_id, mission_id, cycle_key),
  KEY idx_clan_mission_progress_clan (clan_id),
  KEY idx_clan_mission_progress_cycle (cycle_key)
);

CREATE TABLE IF NOT EXISTS clan_war_chest (
  clan_id INT NOT NULL,
  item_id INT NOT NULL,
  amount BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (clan_id, item_id),
  KEY idx_clan_war_chest_clan (clan_id)
);

CREATE TABLE IF NOT EXISTS clan_war_chest_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  clan_id INT NOT NULL,
  player_id INT NOT NULL DEFAULT 0,
  player_name VARCHAR(45) NOT NULL DEFAULT '',
  item_id INT NOT NULL,
  amount BIGINT NOT NULL,
  action VARCHAR(16) NOT NULL,
  service VARCHAR(64) NOT NULL DEFAULT '',
  created_at BIGINT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_clan_war_chest_log_clan (clan_id),
  KEY idx_clan_war_chest_log_created (created_at)
);

CREATE TABLE IF NOT EXISTS clan_hall_upgrades (
  clan_id INT NOT NULL,
  hall_id INT NOT NULL DEFAULT 0,
  upgrade_level INT NOT NULL DEFAULT 1,
  upgraded_at BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (clan_id),
  KEY idx_clan_hall_upgrades_hall (hall_id)
);

CREATE TABLE IF NOT EXISTS clan_territory_ownership (
  territory_id VARCHAR(64) NOT NULL,
  territory_name VARCHAR(80) NOT NULL,
  owner_clan_id INT NOT NULL DEFAULT 0,
  captured_at BIGINT NOT NULL DEFAULT 0,
  expires_at BIGINT NOT NULL DEFAULT 0,
  defense_score INT NOT NULL DEFAULT 0,
  attack_score INT NOT NULL DEFAULT 0,
  PRIMARY KEY (territory_id),
  KEY idx_clan_territory_owner (owner_clan_id),
  KEY idx_clan_territory_expires (expires_at)
);

CREATE TABLE IF NOT EXISTS clan_rivalries (
  id BIGINT NOT NULL AUTO_INCREMENT,
  clan_id INT NOT NULL,
  rival_clan_id INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  start_at BIGINT NOT NULL DEFAULT 0,
  end_at BIGINT NOT NULL DEFAULT 0,
  winner_clan_id INT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_clan_rivalries_clan (clan_id),
  KEY idx_clan_rivalries_rival (rival_clan_id),
  KEY idx_clan_rivalries_status_end (status, end_at)
);

CREATE TABLE IF NOT EXISTS clan_rivalry_score (
  rivalry_id BIGINT NOT NULL,
  clan_id INT NOT NULL,
  kills INT NOT NULL DEFAULT 0,
  deaths INT NOT NULL DEFAULT 0,
  raid_score INT NOT NULL DEFAULT 0,
  territory_captures INT NOT NULL DEFAULT 0,
  siege_score INT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (rivalry_id, clan_id),
  KEY idx_clan_rivalry_score_clan (clan_id)
);

CREATE TABLE IF NOT EXISTS clan_seasons (
  id INT NOT NULL AUTO_INCREMENT,
  season_key VARCHAR(24) NOT NULL,
  start_at BIGINT NOT NULL,
  end_at BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_clan_season_key (season_key),
  KEY idx_clan_seasons_status (status),
  KEY idx_clan_seasons_end (end_at)
);

CREATE TABLE IF NOT EXISTS clan_season_results (
  season_id INT NOT NULL,
  clan_id INT NOT NULL,
  category VARCHAR(32) NOT NULL,
  rank_position INT NOT NULL,
  score BIGINT NOT NULL,
  reward_claimed TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (season_id, clan_id, category),
  KEY idx_clan_season_results_clan (clan_id),
  KEY idx_clan_season_results_category_rank (category, rank_position)
);

CREATE TABLE IF NOT EXISTS clan_achievements (
  achievement_id VARCHAR(40) NOT NULL,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(255) NOT NULL,
  goal INT NOT NULL,
  reward_points INT NOT NULL,
  active TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (achievement_id),
  KEY idx_clan_achievements_active (active)
);

CREATE TABLE IF NOT EXISTS clan_achievement_progress (
  clan_id INT NOT NULL,
  achievement_id VARCHAR(40) NOT NULL,
  progress INT NOT NULL DEFAULT 0,
  completed TINYINT NOT NULL DEFAULT 0,
  completed_at BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (clan_id, achievement_id),
  KEY idx_clan_achievement_progress_clan (clan_id)
);

CREATE TABLE IF NOT EXISTS clan_raid_objectives (
  id BIGINT NOT NULL AUTO_INCREMENT,
  raid_id INT NOT NULL,
  raid_name VARCHAR(80) NOT NULL DEFAULT '',
  winning_clan_id INT NOT NULL,
  score BIGINT NOT NULL,
  killer_name VARCHAR(45) NOT NULL DEFAULT '',
  season_id INT NOT NULL DEFAULT 0,
  killed_at BIGINT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_clan_raid_objectives_clan (winning_clan_id),
  KEY idx_clan_raid_objectives_raid (raid_id),
  KEY idx_clan_raid_objectives_season (season_id),
  KEY idx_clan_raid_objectives_killed (killed_at)
);

CREATE TABLE IF NOT EXISTS clan_activity_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  clan_id INT NOT NULL,
  activity_type VARCHAR(48) NOT NULL,
  actor_name VARCHAR(45) NOT NULL DEFAULT '',
  details VARCHAR(255) NOT NULL DEFAULT '',
  created_at BIGINT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_clan_activity_log_clan (clan_id),
  KEY idx_clan_activity_log_type (activity_type),
  KEY idx_clan_activity_log_created (created_at)
);

CREATE TABLE IF NOT EXISTS clan_rally_points (
  clan_id INT NOT NULL,
  x INT NOT NULL,
  y INT NOT NULL,
  z INT NOT NULL,
  heading INT NOT NULL DEFAULT 0,
  activated_by VARCHAR(45) NOT NULL DEFAULT '',
  created_at BIGINT NOT NULL,
  expires_at BIGINT NOT NULL,
  cooldown_until BIGINT NOT NULL,
  PRIMARY KEY (clan_id),
  KEY idx_clan_rally_points_expires (expires_at)
);
