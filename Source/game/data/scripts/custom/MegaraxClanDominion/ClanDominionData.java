package custom.MegaraxClanDominion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;

public final class ClanDominionData
{
	private static final Logger LOGGER = Logger.getLogger(ClanDominionData.class.getName());
	
	private static final String[] DDL =
	{
		"CREATE TABLE IF NOT EXISTS clan_dominion (clan_id INT NOT NULL, tier INT NOT NULL DEFAULT 0, total_points BIGINT NOT NULL DEFAULT 0, season_points BIGINT NOT NULL DEFAULT 0, weekly_activity INT NOT NULL DEFAULT 0, pvp_kills INT NOT NULL DEFAULT 0, pvp_deaths INT NOT NULL DEFAULT 0, raid_score INT NOT NULL DEFAULT 0, territory_score INT NOT NULL DEFAULT 0, siege_score INT NOT NULL DEFAULT 0, mission_score INT NOT NULL DEFAULT 0, last_activity BIGINT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (clan_id), KEY idx_clan_dominion_tier (tier), KEY idx_clan_dominion_season_points (season_points), KEY idx_clan_dominion_last_activity (last_activity))",
		"CREATE TABLE IF NOT EXISTS clan_dominion_points_log (id BIGINT NOT NULL AUTO_INCREMENT, clan_id INT NOT NULL, target_clan_id INT NOT NULL DEFAULT 0, season_id INT NOT NULL DEFAULT 0, amount INT NOT NULL, reason VARCHAR(48) NOT NULL, actor_name VARCHAR(45) NOT NULL DEFAULT '', target_name VARCHAR(45) NOT NULL DEFAULT '', metadata VARCHAR(255) NOT NULL DEFAULT '', created_at BIGINT NOT NULL, PRIMARY KEY (id), KEY idx_cdp_log_clan (clan_id), KEY idx_cdp_log_target_clan (target_clan_id), KEY idx_cdp_log_season (season_id), KEY idx_cdp_log_created (created_at))",
		"CREATE TABLE IF NOT EXISTS clan_missions (mission_id VARCHAR(40) NOT NULL, name VARCHAR(80) NOT NULL, description VARCHAR(255) NOT NULL, type VARCHAR(32) NOT NULL, goal INT NOT NULL, reward_points INT NOT NULL, active TINYINT NOT NULL DEFAULT 1, sort_order INT NOT NULL DEFAULT 0, PRIMARY KEY (mission_id), KEY idx_clan_missions_type (type), KEY idx_clan_missions_active (active))",
		"CREATE TABLE IF NOT EXISTS clan_mission_progress (clan_id INT NOT NULL, mission_id VARCHAR(40) NOT NULL, cycle_key VARCHAR(24) NOT NULL, progress INT NOT NULL DEFAULT 0, claimed TINYINT NOT NULL DEFAULT 0, completed_at BIGINT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (clan_id, mission_id, cycle_key), KEY idx_clan_mission_progress_clan (clan_id), KEY idx_clan_mission_progress_cycle (cycle_key))",
		"CREATE TABLE IF NOT EXISTS clan_war_chest (clan_id INT NOT NULL, item_id INT NOT NULL, amount BIGINT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (clan_id, item_id), KEY idx_clan_war_chest_clan (clan_id))",
		"CREATE TABLE IF NOT EXISTS clan_war_chest_log (id BIGINT NOT NULL AUTO_INCREMENT, clan_id INT NOT NULL, player_id INT NOT NULL DEFAULT 0, player_name VARCHAR(45) NOT NULL DEFAULT '', item_id INT NOT NULL, amount BIGINT NOT NULL, action VARCHAR(16) NOT NULL, service VARCHAR(64) NOT NULL DEFAULT '', created_at BIGINT NOT NULL, PRIMARY KEY (id), KEY idx_clan_war_chest_log_clan (clan_id), KEY idx_clan_war_chest_log_created (created_at))",
		"CREATE TABLE IF NOT EXISTS clan_hall_upgrades (clan_id INT NOT NULL, hall_id INT NOT NULL DEFAULT 0, upgrade_level INT NOT NULL DEFAULT 1, upgraded_at BIGINT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (clan_id), KEY idx_clan_hall_upgrades_hall (hall_id))",
		"CREATE TABLE IF NOT EXISTS clan_territory_ownership (territory_id VARCHAR(64) NOT NULL, territory_name VARCHAR(80) NOT NULL, owner_clan_id INT NOT NULL DEFAULT 0, captured_at BIGINT NOT NULL DEFAULT 0, expires_at BIGINT NOT NULL DEFAULT 0, defense_score INT NOT NULL DEFAULT 0, attack_score INT NOT NULL DEFAULT 0, PRIMARY KEY (territory_id), KEY idx_clan_territory_owner (owner_clan_id), KEY idx_clan_territory_expires (expires_at))",
		"CREATE TABLE IF NOT EXISTS clan_rivalries (id BIGINT NOT NULL AUTO_INCREMENT, clan_id INT NOT NULL, rival_clan_id INT NOT NULL, status VARCHAR(16) NOT NULL, start_at BIGINT NOT NULL DEFAULT 0, end_at BIGINT NOT NULL DEFAULT 0, winner_clan_id INT NOT NULL DEFAULT 0, created_at BIGINT NOT NULL, PRIMARY KEY (id), KEY idx_clan_rivalries_clan (clan_id), KEY idx_clan_rivalries_rival (rival_clan_id), KEY idx_clan_rivalries_status_end (status, end_at))",
		"CREATE TABLE IF NOT EXISTS clan_rivalry_score (rivalry_id BIGINT NOT NULL, clan_id INT NOT NULL, kills INT NOT NULL DEFAULT 0, deaths INT NOT NULL DEFAULT 0, raid_score INT NOT NULL DEFAULT 0, territory_captures INT NOT NULL DEFAULT 0, siege_score INT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (rivalry_id, clan_id), KEY idx_clan_rivalry_score_clan (clan_id))",
		"CREATE TABLE IF NOT EXISTS clan_seasons (id INT NOT NULL AUTO_INCREMENT, season_key VARCHAR(24) NOT NULL, start_at BIGINT NOT NULL, end_at BIGINT NOT NULL, status VARCHAR(16) NOT NULL, PRIMARY KEY (id), UNIQUE KEY uk_clan_season_key (season_key), KEY idx_clan_seasons_status (status), KEY idx_clan_seasons_end (end_at))",
		"CREATE TABLE IF NOT EXISTS clan_season_results (season_id INT NOT NULL, clan_id INT NOT NULL, category VARCHAR(32) NOT NULL, rank_position INT NOT NULL, score BIGINT NOT NULL, reward_claimed TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (season_id, clan_id, category), KEY idx_clan_season_results_clan (clan_id), KEY idx_clan_season_results_category_rank (category, rank_position))",
		"CREATE TABLE IF NOT EXISTS clan_achievements (achievement_id VARCHAR(40) NOT NULL, name VARCHAR(80) NOT NULL, description VARCHAR(255) NOT NULL, goal INT NOT NULL, reward_points INT NOT NULL, active TINYINT NOT NULL DEFAULT 1, PRIMARY KEY (achievement_id), KEY idx_clan_achievements_active (active))",
		"CREATE TABLE IF NOT EXISTS clan_achievement_progress (clan_id INT NOT NULL, achievement_id VARCHAR(40) NOT NULL, progress INT NOT NULL DEFAULT 0, completed TINYINT NOT NULL DEFAULT 0, completed_at BIGINT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (clan_id, achievement_id), KEY idx_clan_achievement_progress_clan (clan_id))",
		"CREATE TABLE IF NOT EXISTS clan_raid_objectives (id BIGINT NOT NULL AUTO_INCREMENT, raid_id INT NOT NULL, raid_name VARCHAR(80) NOT NULL DEFAULT '', winning_clan_id INT NOT NULL, score BIGINT NOT NULL, killer_name VARCHAR(45) NOT NULL DEFAULT '', season_id INT NOT NULL DEFAULT 0, killed_at BIGINT NOT NULL, PRIMARY KEY (id), KEY idx_clan_raid_objectives_clan (winning_clan_id), KEY idx_clan_raid_objectives_raid (raid_id), KEY idx_clan_raid_objectives_season (season_id), KEY idx_clan_raid_objectives_killed (killed_at))",
		"CREATE TABLE IF NOT EXISTS clan_activity_log (id BIGINT NOT NULL AUTO_INCREMENT, clan_id INT NOT NULL, activity_type VARCHAR(48) NOT NULL, actor_name VARCHAR(45) NOT NULL DEFAULT '', details VARCHAR(255) NOT NULL DEFAULT '', created_at BIGINT NOT NULL, PRIMARY KEY (id), KEY idx_clan_activity_log_clan (clan_id), KEY idx_clan_activity_log_type (activity_type), KEY idx_clan_activity_log_created (created_at))",
		"CREATE TABLE IF NOT EXISTS clan_rally_points (clan_id INT NOT NULL, x INT NOT NULL, y INT NOT NULL, z INT NOT NULL, heading INT NOT NULL DEFAULT 0, activated_by VARCHAR(45) NOT NULL DEFAULT '', created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL, cooldown_until BIGINT NOT NULL, PRIMARY KEY (clan_id), KEY idx_clan_rally_points_expires (expires_at))",
	};
	
	private ClanDominionData()
	{
	}
	
	public static void initSchema()
	{
		try (Connection con = DatabaseFactory.getConnection())
		{
			for (String sql : DDL)
			{
				try (PreparedStatement ps = con.prepareStatement(sql))
				{
					ps.executeUpdate();
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not initialize schema: " + e.getMessage());
		}
	}
	
	public static void ensureClan(int clanId)
	{
		if (clanId < 0)
		{
			return;
		}
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("INSERT IGNORE INTO clan_dominion (clan_id, updated_at) VALUES (?, ?)"))
		{
			ps.setInt(1, clanId);
			ps.setLong(2, System.currentTimeMillis());
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not ensure clan row " + clanId + ": " + e.getMessage());
		}
	}
	
	public static ClanDominionStats getStats(int clanId)
	{
		ensureClan(clanId);
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT clan_id, tier, total_points, season_points, weekly_activity, pvp_kills, pvp_deaths, raid_score, territory_score, siege_score, mission_score, last_activity FROM clan_dominion WHERE clan_id=?"))
		{
			ps.setInt(1, clanId);
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					return new ClanDominionStats(rs.getInt("clan_id"), rs.getInt("tier"), rs.getLong("total_points"), rs.getLong("season_points"), rs.getInt("weekly_activity"), rs.getInt("pvp_kills"), rs.getInt("pvp_deaths"), rs.getInt("raid_score"), rs.getInt("territory_score"), rs.getInt("siege_score"), rs.getInt("mission_score"), rs.getLong("last_activity"));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not load stats for clan " + clanId + ": " + e.getMessage());
		}
		return new ClanDominionStats(clanId, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	}
	
	public static long addPoints(int clanId, int targetClanId, int amount, String reason, String actorName, String targetName, String metadata, int seasonId)
	{
		if ((clanId <= 0) || (amount == 0))
		{
			return getStats(clanId).totalPoints;
		}
		ensureClan(clanId);
		final long now = System.currentTimeMillis();
		try (Connection con = DatabaseFactory.getConnection())
		{
			try (PreparedStatement update = con.prepareStatement("UPDATE clan_dominion SET total_points=GREATEST(0, total_points + ?), season_points=GREATEST(0, season_points + ?), weekly_activity=weekly_activity + ?, last_activity=?, updated_at=? WHERE clan_id=?"))
			{
				update.setInt(1, amount);
				update.setInt(2, amount);
				update.setInt(3, Math.max(0, amount));
				update.setLong(4, now);
				update.setLong(5, now);
				update.setInt(6, clanId);
				update.executeUpdate();
			}
			try (PreparedStatement log = con.prepareStatement("INSERT INTO clan_dominion_points_log (clan_id, target_clan_id, season_id, amount, reason, actor_name, target_name, metadata, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"))
			{
				log.setInt(1, clanId);
				log.setInt(2, Math.max(0, targetClanId));
				log.setInt(3, Math.max(0, seasonId));
				log.setInt(4, amount);
				log.setString(5, safe(reason, 48));
				log.setString(6, safe(actorName, 45));
				log.setString(7, safe(targetName, 45));
				log.setString(8, safe(metadata, 255));
				log.setLong(9, now);
				log.executeUpdate();
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not add points to clan " + clanId + ": " + e.getMessage());
		}
		return getStats(clanId).totalPoints;
	}
	
	public static void setTier(int clanId, int tier)
	{
		ensureClan(clanId);
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("UPDATE clan_dominion SET tier=?, updated_at=? WHERE clan_id=?"))
		{
			ps.setInt(1, Math.max(0, tier));
			ps.setLong(2, System.currentTimeMillis());
			ps.setInt(3, clanId);
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not set tier: " + e.getMessage());
		}
	}
	
	public static void addPvpKill(int killerClanId, int victimClanId)
	{
		ensureClan(killerClanId);
		ensureClan(victimClanId);
		try (Connection con = DatabaseFactory.getConnection())
		{
			try (PreparedStatement ps = con.prepareStatement("UPDATE clan_dominion SET pvp_kills=pvp_kills+1, updated_at=? WHERE clan_id=?"))
			{
				ps.setLong(1, System.currentTimeMillis());
				ps.setInt(2, killerClanId);
				ps.executeUpdate();
			}
			try (PreparedStatement ps = con.prepareStatement("UPDATE clan_dominion SET pvp_deaths=pvp_deaths+1, updated_at=? WHERE clan_id=?"))
			{
				ps.setLong(1, System.currentTimeMillis());
				ps.setInt(2, victimClanId);
				ps.executeUpdate();
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not update PvP stats: " + e.getMessage());
		}
	}
	
	public static void addScoreColumn(int clanId, String column, int amount)
	{
		if ((clanId <= 0) || (amount <= 0) || !isSafeScoreColumn(column))
		{
			return;
		}
		ensureClan(clanId);
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("UPDATE clan_dominion SET " + column + "=" + column + " + ?, updated_at=? WHERE clan_id=?"))
		{
			ps.setInt(1, amount);
			ps.setLong(2, System.currentTimeMillis());
			ps.setInt(3, clanId);
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not update " + column + ": " + e.getMessage());
		}
	}
	
	public static void logActivity(int clanId, String type, String actorName, String details)
	{
		if (clanId <= 0)
		{
			return;
		}
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("INSERT INTO clan_activity_log (clan_id, activity_type, actor_name, details, created_at) VALUES (?, ?, ?, ?, ?)"))
		{
			ps.setInt(1, clanId);
			ps.setString(2, safe(type, 48));
			ps.setString(3, safe(actorName, 45));
			ps.setString(4, safe(details, 255));
			ps.setLong(5, System.currentTimeMillis());
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not log activity: " + e.getMessage());
		}
	}
	
	public static List<ClanDominionStats> getTopClans(int limit)
	{
		final List<ClanDominionStats> result = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT clan_id, tier, total_points, season_points, weekly_activity, pvp_kills, pvp_deaths, raid_score, territory_score, siege_score, mission_score, last_activity FROM clan_dominion ORDER BY season_points DESC, total_points DESC LIMIT ?"))
		{
			ps.setInt(1, Math.max(1, limit));
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					result.add(new ClanDominionStats(rs.getInt("clan_id"), rs.getInt("tier"), rs.getLong("total_points"), rs.getLong("season_points"), rs.getInt("weekly_activity"), rs.getInt("pvp_kills"), rs.getInt("pvp_deaths"), rs.getInt("raid_score"), rs.getInt("territory_score"), rs.getInt("siege_score"), rs.getInt("mission_score"), rs.getLong("last_activity")));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not load top clans: " + e.getMessage());
		}
		return result;
	}
	
	public static List<String[]> getWarNews(int limit)
	{
		final List<String[]> rows = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT activity_type, actor_name, details, created_at FROM clan_activity_log ORDER BY created_at DESC LIMIT ?"))
		{
			ps.setInt(1, Math.max(1, limit));
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					rows.add(new String[]
					{
						rs.getString("activity_type"),
						rs.getString("actor_name"),
						rs.getString("details"),
						Long.toString(rs.getLong("created_at"))
					});
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not load war news: " + e.getMessage());
		}
		return rows;
	}
	
	public static void resetWeeklyStats()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("UPDATE clan_dominion SET season_points=0, weekly_activity=0, pvp_kills=0, pvp_deaths=0, raid_score=0, territory_score=0, siege_score=0, mission_score=0, updated_at=?"))
		{
			ps.setLong(1, System.currentTimeMillis());
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not reset weekly stats: " + e.getMessage());
		}
	}
	
	private static boolean isSafeScoreColumn(String column)
	{
		return "raid_score".equals(column) || "territory_score".equals(column) || "siege_score".equals(column) || "mission_score".equals(column);
	}
	
	private static String safe(String value, int max)
	{
		if (value == null)
		{
			return "";
		}
		return value.length() > max ? value.substring(0, max) : value;
	}
}
