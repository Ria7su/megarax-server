CREATE TABLE IF NOT EXISTS kingdom_treasury (
  castle_id INT NOT NULL,
  clan_id INT NOT NULL DEFAULT 0,
  balance BIGINT NOT NULL DEFAULT 0,
  total_income BIGINT NOT NULL DEFAULT 0,
  today_income BIGINT NOT NULL DEFAULT 0,
  weekly_income BIGINT NOT NULL DEFAULT 0,
  monthly_income BIGINT NOT NULL DEFAULT 0,
  total_spent BIGINT NOT NULL DEFAULT 0,
  level INT NOT NULL DEFAULT 1,
  payroll_percent INT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (castle_id),
  KEY idx_kingdom_treasury_clan (clan_id),
  KEY idx_kingdom_treasury_balance (balance)
);

CREATE TABLE IF NOT EXISTS kingdom_contribution (
  char_id INT NOT NULL,
  castle_id INT NOT NULL,
  clan_id INT NOT NULL DEFAULT 0,
  points BIGINT NOT NULL DEFAULT 0,
  lifetime_points BIGINT NOT NULL DEFAULT 0,
  daily_rax INT NOT NULL DEFAULT 0,
  weekly_rax INT NOT NULL DEFAULT 0,
  day_key INT NOT NULL DEFAULT 0,
  week_key INT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (char_id, castle_id),
  KEY idx_kingdom_contribution_castle (castle_id),
  KEY idx_kingdom_contribution_clan (clan_id),
  KEY idx_kingdom_contribution_points (points)
);

CREATE TABLE IF NOT EXISTS kingdom_contribution_history (
  id BIGINT NOT NULL AUTO_INCREMENT,
  char_id INT NOT NULL,
  char_name VARCHAR(45) NOT NULL DEFAULT '',
  clan_id INT NOT NULL DEFAULT 0,
  castle_id INT NOT NULL,
  territory_id VARCHAR(64) NOT NULL DEFAULT '',
  activity_type VARCHAR(48) NOT NULL,
  points INT NOT NULL DEFAULT 0,
  adena_tax BIGINT NOT NULL DEFAULT 0,
  rax_reward INT NOT NULL DEFAULT 0,
  target_name VARCHAR(45) NOT NULL DEFAULT '',
  metadata VARCHAR(255) NOT NULL DEFAULT '',
  created_at BIGINT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_kch_char (char_id),
  KEY idx_kch_castle (castle_id),
  KEY idx_kch_clan (clan_id),
  KEY idx_kch_created (created_at)
);

CREATE TABLE IF NOT EXISTS kingdom_payroll (
  id BIGINT NOT NULL AUTO_INCREMENT,
  castle_id INT NOT NULL,
  clan_id INT NOT NULL,
  char_id INT NOT NULL,
  char_name VARCHAR(45) NOT NULL DEFAULT '',
  contribution_points BIGINT NOT NULL DEFAULT 0,
  amount BIGINT NOT NULL DEFAULT 0,
  cycle_key VARCHAR(24) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PAID',
  created_at BIGINT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_kingdom_payroll_castle (castle_id),
  KEY idx_kingdom_payroll_clan (clan_id),
  KEY idx_kingdom_payroll_cycle (cycle_key)
);

CREATE TABLE IF NOT EXISTS kingdom_levels (
  castle_id INT NOT NULL,
  level INT NOT NULL DEFAULT 1,
  upgraded_by INT NOT NULL DEFAULT 0,
  upgraded_at BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (castle_id)
);

CREATE TABLE IF NOT EXISTS kingdom_statistics (
  stat_key VARCHAR(64) NOT NULL,
  stat_value BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (stat_key)
);

CREATE TABLE IF NOT EXISTS kingdom_territory_state (
  territory_id VARCHAR(64) NOT NULL,
  castle_id INT NOT NULL,
  activity_score BIGINT NOT NULL DEFAULT 0,
  last_activity BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (territory_id),
  KEY idx_kingdom_territory_castle (castle_id)
);

CREATE TABLE IF NOT EXISTS kingdom_event_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  castle_id INT NOT NULL,
  clan_id INT NOT NULL DEFAULT 0,
  event_type VARCHAR(48) NOT NULL,
  actor_name VARCHAR(45) NOT NULL DEFAULT '',
  details VARCHAR(255) NOT NULL DEFAULT '',
  created_at BIGINT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_kingdom_event_castle (castle_id),
  KEY idx_kingdom_event_clan (clan_id),
  KEY idx_kingdom_event_created (created_at)
);

CREATE TABLE IF NOT EXISTS kingdom_citizens (
  castle_id INT NOT NULL,
  char_id INT NOT NULL,
  clan_id INT NOT NULL DEFAULT 0,
  relation VARCHAR(16) NOT NULL DEFAULT 'NEUTRAL',
  first_seen BIGINT NOT NULL DEFAULT 0,
  last_seen BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (castle_id, char_id),
  KEY idx_kingdom_citizens_clan (clan_id),
  KEY idx_kingdom_citizens_seen (last_seen)
);
