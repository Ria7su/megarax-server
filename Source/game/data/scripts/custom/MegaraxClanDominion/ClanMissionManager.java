package custom.MegaraxClanDominion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.serverpackets.ExShowScreenMessage;

public final class ClanMissionManager
{
	public static final String TYPE_PVP_ZONE_KILL = "PVP_ZONE_KILL";
	public static final String TYPE_CLAN_WAR_KILL = "CLAN_WAR_KILL";
	public static final String TYPE_SIEGE_KILL = "SIEGE_KILL";
	public static final String TYPE_RAID_OBJECTIVE = "RAID_OBJECTIVE";
	public static final String TYPE_WAR_CHEST_DEPOSIT = "WAR_CHEST_DEPOSIT";
	public static final String TYPE_TERRITORY_CAPTURE = "TERRITORY_CAPTURE";
	public static final String TYPE_DUNGEON_COMPLETE = "DUNGEON_COMPLETE";
	
	private static final Logger LOGGER = Logger.getLogger(ClanMissionManager.class.getName());
	private static final ClanMissionManager INSTANCE = new ClanMissionManager();
	
	private ClanMissionManager()
	{
	}
	
	public static ClanMissionManager getInstance()
	{
		return INSTANCE;
	}
	
	public void init()
	{
		ensureDefaultMissions();
	}
	
	public void reload()
	{
		ensureDefaultMissions();
	}
	
	public void addProgress(Player actor, String type, int amount)
	{
		if ((actor == null) || (actor.getClanId() <= 0))
		{
			return;
		}
		addProgress(actor.getClanId(), type, amount, actor.getName(), actor);
	}
	
	public void addProgress(int clanId, String type, int amount, String actorName)
	{
		addProgress(clanId, type, amount, actorName, null);
	}
	
	private void addProgress(int clanId, String type, int amount, String actorName, Player actor)
	{
		if ((clanId <= 0) || (amount <= 0) || (type == null))
		{
			return;
		}
		final String cycle = getCycleKey();
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement missions = con.prepareStatement("SELECT mission_id, name, goal FROM clan_missions WHERE active=1 AND type=?"))
		{
			missions.setString(1, type);
			try (ResultSet rs = missions.executeQuery())
			{
				while (rs.next())
				{
					final String missionId = rs.getString("mission_id");
					final int goal = rs.getInt("goal");
					ensureProgress(con, clanId, missionId, cycle);
					final int before = getProgress(con, clanId, missionId, cycle);
					if (before >= goal)
					{
						continue;
					}
					final int after = Math.min(goal, before + amount);
					try (PreparedStatement update = con.prepareStatement("UPDATE clan_mission_progress SET progress=?, completed_at=IF(? >= ?, IF(completed_at=0, ?, completed_at), completed_at), updated_at=? WHERE clan_id=? AND mission_id=? AND cycle_key=?"))
					{
						final long now = System.currentTimeMillis();
						update.setInt(1, after);
						update.setInt(2, after);
						update.setInt(3, goal);
						update.setLong(4, now);
						update.setLong(5, now);
						update.setInt(6, clanId);
						update.setString(7, missionId);
						update.setString(8, cycle);
						update.executeUpdate();
					}
					if ((before < goal) && (after >= goal))
					{
						ClanDominionData.logActivity(clanId, "MISSION_COMPLETE", actorName, rs.getString("name"));
						if (actor != null)
						{
							actor.sendPacket(new ExShowScreenMessage("Clan Mission Complete: " + rs.getString("name"), 3000));
						}
					}
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not update mission progress: " + e.getMessage());
		}
	}
	
	public boolean claim(Player player, String missionId)
	{
		if ((player == null) || (player.getClanId() <= 0) || (missionId == null))
		{
			return false;
		}
		final int clanId = player.getClanId();
		final String cycle = getCycleKey();
		try (Connection con = DatabaseFactory.getConnection())
		{
			ensureProgress(con, clanId, missionId, cycle);
			try (PreparedStatement ps = con.prepareStatement("SELECT m.name, m.goal, m.reward_points, p.progress, p.claimed FROM clan_missions m INNER JOIN clan_mission_progress p ON p.mission_id=m.mission_id WHERE p.clan_id=? AND p.cycle_key=? AND m.mission_id=?"))
			{
				ps.setInt(1, clanId);
				ps.setString(2, cycle);
				ps.setString(3, missionId);
				try (ResultSet rs = ps.executeQuery())
				{
					if (!rs.next())
					{
						player.sendMessage("Clan mission not found.");
						return false;
					}
					if (rs.getBoolean("claimed"))
					{
						player.sendMessage("This clan mission reward is already claimed.");
						return false;
					}
					if (rs.getInt("progress") < rs.getInt("goal"))
					{
						player.sendMessage("This clan mission is not complete yet.");
						return false;
					}
					try (PreparedStatement update = con.prepareStatement("UPDATE clan_mission_progress SET claimed=1, updated_at=? WHERE clan_id=? AND mission_id=? AND cycle_key=? AND claimed=0"))
					{
						update.setLong(1, System.currentTimeMillis());
						update.setInt(2, clanId);
						update.setString(3, missionId);
						update.setString(4, cycle);
						if (update.executeUpdate() > 0)
						{
							final int points = rs.getInt("reward_points");
							ClanDominionEngine.addDominionPoints(clanId, 0, points, "MISSION_REWARD", player.getName(), "", rs.getString("name"));
							ClanDominionData.addScoreColumn(clanId, "mission_score", points);
							player.sendMessage("Clan mission reward claimed: " + rs.getString("name") + ".");
							return true;
						}
					}
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not claim mission: " + e.getMessage());
		}
		return false;
	}
	
	public List<MissionView> getMissionViews(int clanId)
	{
		final List<MissionView> views = new ArrayList<>();
		final String cycle = getCycleKey();
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement missions = con.prepareStatement("SELECT mission_id, name, description, type, goal, reward_points FROM clan_missions WHERE active=1 ORDER BY sort_order, mission_id"))
		{
			try (ResultSet rs = missions.executeQuery())
			{
				while (rs.next())
				{
					final String missionId = rs.getString("mission_id");
					ensureProgress(con, clanId, missionId, cycle);
					try (PreparedStatement progress = con.prepareStatement("SELECT progress, claimed FROM clan_mission_progress WHERE clan_id=? AND mission_id=? AND cycle_key=?"))
					{
						progress.setInt(1, clanId);
						progress.setString(2, missionId);
						progress.setString(3, cycle);
						try (ResultSet prs = progress.executeQuery())
						{
							int current = 0;
							boolean claimed = false;
							if (prs.next())
							{
								current = prs.getInt("progress");
								claimed = prs.getBoolean("claimed");
							}
							views.add(new MissionView(missionId, rs.getString("name"), rs.getString("description"), rs.getString("type"), current, rs.getInt("goal"), rs.getInt("reward_points"), claimed));
						}
					}
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not load mission views: " + e.getMessage());
		}
		return views;
	}
	
	public String getCycleKey()
	{
		final LocalDate date = LocalDate.now();
		final WeekFields weekFields = WeekFields.of(Locale.US);
		return date.get(weekFields.weekBasedYear()) + "-W" + String.format("%02d", date.get(weekFields.weekOfWeekBasedYear()));
	}
	
	private void ensureDefaultMissions()
	{
		upsertMission("pvp_zone_kills", "PvP Zone Hunters", "Kill enemies in active Megarax PvP zones.", TYPE_PVP_ZONE_KILL, ClanDominionConfig.MISSION_PVP_ZONE_GOAL, 10);
		upsertMission("clan_war_kills", "War Pressure", "Kill enemies during real clan wars.", TYPE_CLAN_WAR_KILL, ClanDominionConfig.MISSION_CLAN_WAR_GOAL, 20);
		upsertMission("siege_kills", "Siege Bloodline", "Kill enemies during castle or siege combat.", TYPE_SIEGE_KILL, ClanDominionConfig.MISSION_SIEGE_GOAL, 30);
		upsertMission("raid_objectives", "Raid Control", "Win clan raid objectives.", TYPE_RAID_OBJECTIVE, ClanDominionConfig.MISSION_RAID_GOAL, 40);
		upsertMission("war_chest_deposit", "War Chest Supply", "Deposit resources into the Clan War Chest.", TYPE_WAR_CHEST_DEPOSIT, ClanDominionConfig.MISSION_WAR_CHEST_DEPOSIT_GOAL, 50);
		upsertMission("territory_capture", "Territory Banner", "Capture clan territories.", TYPE_TERRITORY_CAPTURE, ClanDominionConfig.MISSION_TERRITORY_CAPTURE_GOAL, 60);
	}
	
	private void upsertMission(String id, String name, String description, String type, int goal, int order)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("REPLACE INTO clan_missions (mission_id, name, description, type, goal, reward_points, active, sort_order) VALUES (?, ?, ?, ?, ?, ?, 1, ?)"))
		{
			ps.setString(1, id);
			ps.setString(2, name);
			ps.setString(3, description);
			ps.setString(4, type);
			ps.setInt(5, Math.max(1, goal));
			ps.setInt(6, ClanDominionConfig.MISSION_REWARD_POINTS);
			ps.setInt(7, order);
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not upsert mission " + id + ": " + e.getMessage());
		}
	}
	
	private void ensureProgress(Connection con, int clanId, String missionId, String cycle) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement("INSERT IGNORE INTO clan_mission_progress (clan_id, mission_id, cycle_key, updated_at) VALUES (?, ?, ?, ?)"))
		{
			ps.setInt(1, clanId);
			ps.setString(2, missionId);
			ps.setString(3, cycle);
			ps.setLong(4, System.currentTimeMillis());
			ps.executeUpdate();
		}
	}
	
	private int getProgress(Connection con, int clanId, String missionId, String cycle) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement("SELECT progress FROM clan_mission_progress WHERE clan_id=? AND mission_id=? AND cycle_key=?"))
		{
			ps.setInt(1, clanId);
			ps.setString(2, missionId);
			ps.setString(3, cycle);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? rs.getInt("progress") : 0;
			}
		}
	}
	
	public static final class MissionView
	{
		public final String id;
		public final String name;
		public final String description;
		public final String type;
		public final int progress;
		public final int goal;
		public final int rewardPoints;
		public final boolean claimed;
		
		private MissionView(String id, String name, String description, String type, int progress, int goal, int rewardPoints, boolean claimed)
		{
			this.id = id;
			this.name = name;
			this.description = description;
			this.type = type;
			this.progress = progress;
			this.goal = goal;
			this.rewardPoints = rewardPoints;
			this.claimed = claimed;
		}
	}
}
