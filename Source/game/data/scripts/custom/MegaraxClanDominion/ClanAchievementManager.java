package custom.MegaraxClanDominion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;

public final class ClanAchievementManager
{
	public static final String FIRST_BLOOD = "first_blood";
	public static final String WAR_MACHINE = "war_machine";
	public static final String RAID_HUNTERS = "raid_hunters";
	public static final String TERRITORY_KING = "territory_king";
	public static final String RISING_POWER = "rising_power";
	public static final String MEGARAX_DYNASTY = "megarax_dynasty";
	
	private static final Logger LOGGER = Logger.getLogger(ClanAchievementManager.class.getName());
	private static final ClanAchievementManager INSTANCE = new ClanAchievementManager();
	
	private ClanAchievementManager()
	{
	}
	
	public static ClanAchievementManager getInstance()
	{
		return INSTANCE;
	}
	
	public void init()
	{
		ensureDefaults();
	}
	
	public void addProgress(int clanId, String achievementId, int amount, String actorName)
	{
		if ((clanId <= 0) || (achievementId == null) || (amount <= 0))
		{
			return;
		}
		try (Connection con = DatabaseFactory.getConnection())
		{
			ensureProgress(con, clanId, achievementId);
			try (PreparedStatement ps = con.prepareStatement("SELECT a.name, a.goal, a.reward_points, p.progress, p.completed FROM clan_achievements a INNER JOIN clan_achievement_progress p ON p.achievement_id=a.achievement_id WHERE p.clan_id=? AND a.achievement_id=? AND a.active=1"))
			{
				ps.setInt(1, clanId);
				ps.setString(2, achievementId);
				try (ResultSet rs = ps.executeQuery())
				{
					if (!rs.next() || rs.getBoolean("completed"))
					{
						return;
					}
					final int goal = rs.getInt("goal");
					final int progress = Math.min(goal, rs.getInt("progress") + amount);
					final boolean completed = progress >= goal;
					try (PreparedStatement update = con.prepareStatement("UPDATE clan_achievement_progress SET progress=?, completed=?, completed_at=IF(?=1, ?, completed_at), updated_at=? WHERE clan_id=? AND achievement_id=?"))
					{
						final long now = System.currentTimeMillis();
						update.setInt(1, progress);
						update.setBoolean(2, completed);
						update.setBoolean(3, completed);
						update.setLong(4, now);
						update.setLong(5, now);
						update.setInt(6, clanId);
						update.setString(7, achievementId);
						update.executeUpdate();
					}
					if (completed)
					{
						final int reward = rs.getInt("reward_points");
						ClanDominionEngine.addDominionPoints(clanId, 0, reward, "ACHIEVEMENT", actorName, "", rs.getString("name"));
						ClanDominionData.logActivity(clanId, "ACHIEVEMENT", actorName, rs.getString("name"));
					}
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not update achievement: " + e.getMessage());
		}
	}
	
	public void setProgressAtLeast(int clanId, String achievementId, int value, String actorName)
	{
		final int current = getProgress(clanId, achievementId);
		if (value > current)
		{
			addProgress(clanId, achievementId, value - current, actorName);
		}
	}
	
	public List<AchievementView> getViews(int clanId)
	{
		final List<AchievementView> views = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT a.achievement_id, a.name, a.description, a.goal, a.reward_points, COALESCE(p.progress, 0) AS progress, COALESCE(p.completed, 0) AS completed FROM clan_achievements a LEFT JOIN clan_achievement_progress p ON p.achievement_id=a.achievement_id AND p.clan_id=? WHERE a.active=1 ORDER BY a.achievement_id"))
		{
			ps.setInt(1, clanId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					views.add(new AchievementView(rs.getString("achievement_id"), rs.getString("name"), rs.getString("description"), rs.getInt("progress"), rs.getInt("goal"), rs.getInt("reward_points"), rs.getBoolean("completed")));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not load achievements: " + e.getMessage());
		}
		return views;
	}
	
	private int getProgress(int clanId, String achievementId)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT progress FROM clan_achievement_progress WHERE clan_id=? AND achievement_id=?"))
		{
			ps.setInt(1, clanId);
			ps.setString(2, achievementId);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? rs.getInt("progress") : 0;
			}
		}
		catch (SQLException e)
		{
			return 0;
		}
	}
	
	private void ensureDefaults()
	{
		upsert(FIRST_BLOOD, "First Blood", "First valid PvP kill for the clan.", 1, 25);
		upsert(WAR_MACHINE, "War Machine", "Reach 500 valid PvP kills.", 500, 250);
		upsert(RAID_HUNTERS, "Raid Hunters", "Win 10 clan raid objectives.", 10, 250);
		upsert(TERRITORY_KING, "Territory King", "Capture 3 territories.", 3, 250);
		upsert(RISING_POWER, "Rising Power", "Reach Clan Tier 3.", 3, 200);
		upsert(MEGARAX_DYNASTY, "Megarax Dynasty", "Reach the final Clan Dominion tier.", 6, 500);
	}
	
	private void upsert(String id, String name, String description, int goal, int reward)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("REPLACE INTO clan_achievements (achievement_id, name, description, goal, reward_points, active) VALUES (?, ?, ?, ?, ?, 1)"))
		{
			ps.setString(1, id);
			ps.setString(2, name);
			ps.setString(3, description);
			ps.setInt(4, goal);
			ps.setInt(5, reward);
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not upsert achievement " + id + ": " + e.getMessage());
		}
	}
	
	private void ensureProgress(Connection con, int clanId, String achievementId) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement("INSERT IGNORE INTO clan_achievement_progress (clan_id, achievement_id, updated_at) VALUES (?, ?, ?)"))
		{
			ps.setInt(1, clanId);
			ps.setString(2, achievementId);
			ps.setLong(3, System.currentTimeMillis());
			ps.executeUpdate();
		}
	}
	
	public static final class AchievementView
	{
		public final String id;
		public final String name;
		public final String description;
		public final int progress;
		public final int goal;
		public final int rewardPoints;
		public final boolean completed;
		
		private AchievementView(String id, String name, String description, int progress, int goal, int rewardPoints, boolean completed)
		{
			this.id = id;
			this.name = name;
			this.description = description;
			this.progress = progress;
			this.goal = goal;
			this.rewardPoints = rewardPoints;
			this.completed = completed;
		}
	}
}
