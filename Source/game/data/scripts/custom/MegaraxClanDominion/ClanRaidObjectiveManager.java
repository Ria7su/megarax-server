package custom.MegaraxClanDominion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.util.Broadcast;

public final class ClanRaidObjectiveManager
{
	private static final Logger LOGGER = Logger.getLogger(ClanRaidObjectiveManager.class.getName());
	private static final ClanRaidObjectiveManager INSTANCE = new ClanRaidObjectiveManager();
	
	private final Map<Integer, RaidTracker> _trackers = new ConcurrentHashMap<>();
	
	private ClanRaidObjectiveManager()
	{
	}
	
	public static ClanRaidObjectiveManager getInstance()
	{
		return INSTANCE;
	}
	
	public boolean isRaidObjective(Npc npc)
	{
		return ClanDominionConfig.RAID_OBJECTIVES_ENABLED && (npc != null) && (npc.isRaid() || ClanDominionConfig.RAID_OBJECTIVE_BOSS_IDS.contains(npc.getId()));
	}
	
	public void recordDamage(Npc npc, Player attacker, double damage)
	{
		if (!isRaidObjective(npc) || (attacker == null) || (attacker.getClanId() <= 0) || (damage <= 0))
		{
			return;
		}
		final RaidTracker tracker = _trackers.computeIfAbsent(npc.getObjectId(), id -> new RaidTracker(npc.getId(), npc.getName()));
		tracker.damageByClan.merge(attacker.getClanId(), Math.max(1L, (long) damage), Long::sum);
	}
	
	public void recordPvpNearRaid(Player killer)
	{
		if ((killer == null) || (killer.getClanId() <= 0))
		{
			return;
		}
		for (Map.Entry<Integer, RaidTracker> entry : _trackers.entrySet())
		{
			final RaidTracker tracker = entry.getValue();
			if ((tracker.lastKnownX == 0) || (distance(killer.getX(), killer.getY(), tracker.lastKnownX, tracker.lastKnownY) > ClanDominionConfig.RAID_OBJECTIVE_RADIUS))
			{
				continue;
			}
			tracker.pvpKillsByClan.merge(killer.getClanId(), 1, Integer::sum);
		}
	}
	
	public void recordRaidDeath(Npc npc, Player killer)
	{
		if (!isRaidObjective(npc))
		{
			return;
		}
		final RaidTracker tracker = _trackers.remove(npc.getObjectId());
		final Map<Integer, Long> scores = new HashMap<>();
		long totalDamage = 0;
		if (tracker != null)
		{
			for (long value : tracker.damageByClan.values())
			{
				totalDamage += value;
			}
			for (Map.Entry<Integer, Long> entry : tracker.damageByClan.entrySet())
			{
				final long damageScore = totalDamage > 0 ? ((entry.getValue() * ClanDominionConfig.RAID_DAMAGE_WEIGHT) / totalDamage) * 100 : 0;
				scores.merge(entry.getKey(), damageScore, Long::sum);
			}
			for (Map.Entry<Integer, Integer> entry : tracker.pvpKillsByClan.entrySet())
			{
				scores.merge(entry.getKey(), (long) entry.getValue() * ClanDominionConfig.RAID_PVP_WEIGHT * 100, Long::sum);
			}
		}
		
		for (Player player : World.getInstance().getVisibleObjects(npc, Player.class))
		{
			if ((player != null) && !player.isDead() && (player.getClanId() > 0))
			{
				scores.merge(player.getClanId(), (long) ClanDominionConfig.RAID_ALIVE_WEIGHT * 100, Long::sum);
			}
		}
		if ((killer != null) && (killer.getClanId() > 0))
		{
			scores.merge(killer.getClanId(), (long) ClanDominionConfig.RAID_LAST_HIT_WEIGHT * 1000, Long::sum);
		}
		if (scores.isEmpty())
		{
			return;
		}
		
		int winningClanId = 0;
		long bestScore = Long.MIN_VALUE;
		for (Map.Entry<Integer, Long> entry : scores.entrySet())
		{
			if (entry.getValue() > bestScore)
			{
				winningClanId = entry.getKey();
				bestScore = entry.getValue();
			}
		}
		if (winningClanId <= 0)
		{
			return;
		}
		
		final String killerName = killer == null ? "" : killer.getName();
		saveObjective(npc, winningClanId, bestScore, killerName);
		ClanDominionEngine.addDominionPoints(winningClanId, 0, ClanDominionConfig.POINTS_RAID_OBJECTIVE, "RAID_OBJECTIVE", killerName, "", npc.getName());
		ClanDominionData.addScoreColumn(winningClanId, "raid_score", ClanDominionConfig.POINTS_RAID_OBJECTIVE);
		ClanMissionManager.getInstance().addProgress(winningClanId, ClanMissionManager.TYPE_RAID_OBJECTIVE, 1, killerName);
		ClanAchievementManager.getInstance().addProgress(winningClanId, ClanAchievementManager.RAID_HUNTERS, 1, killerName);
		
		if (ClanDominionConfig.ANNOUNCEMENTS_ENABLED && ClanDominionConfig.ANNOUNCE_RAID_OBJECTIVES)
		{
			final Clan clan = ClanTable.getInstance().getClan(winningClanId);
			final String clanName = clan == null ? ("Clan " + winningClanId) : clan.getName();
			Broadcast.toAllOnlinePlayers("Clan Dominion: " + clanName + " secured raid objective " + npc.getName() + ".");
		}
	}
	
	private void saveObjective(Npc npc, int winningClanId, long score, String killerName)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("INSERT INTO clan_raid_objectives (raid_id, raid_name, winning_clan_id, score, killer_name, season_id, killed_at) VALUES (?, ?, ?, ?, ?, ?, ?)"))
		{
			ps.setInt(1, npc.getId());
			ps.setString(2, npc.getName());
			ps.setInt(3, winningClanId);
			ps.setLong(4, score);
			ps.setString(5, killerName == null ? "" : killerName);
			ps.setInt(6, ClanSeasonManager.getInstance().getCurrentSeasonId());
			ps.setLong(7, System.currentTimeMillis());
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not save raid objective: " + e.getMessage());
		}
	}
	
	public void rememberRaidPosition(Npc npc)
	{
		if (!isRaidObjective(npc))
		{
			return;
		}
		final RaidTracker tracker = _trackers.computeIfAbsent(npc.getObjectId(), id -> new RaidTracker(npc.getId(), npc.getName()));
		tracker.lastKnownX = npc.getX();
		tracker.lastKnownY = npc.getY();
	}
	
	private int distance(int x1, int y1, int x2, int y2)
	{
		final long dx = x1 - x2;
		final long dy = y1 - y2;
		return (int) Math.sqrt((dx * dx) + (dy * dy));
	}
	
	private static final class RaidTracker
	{
		private final int raidId;
		private final String raidName;
		private final Map<Integer, Long> damageByClan = new ConcurrentHashMap<>();
		private final Map<Integer, Integer> pvpKillsByClan = new ConcurrentHashMap<>();
		private volatile int lastKnownX;
		private volatile int lastKnownY;
		
		private RaidTracker(int raidId, String raidName)
		{
			this.raidId = raidId;
			this.raidName = raidName;
		}
	}
}
