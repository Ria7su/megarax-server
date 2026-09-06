package custom.MegaraxClanDominion;

import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.util.Broadcast;

public final class ClanDominionEngine
{
	private static volatile boolean _initialized;
	
	private ClanDominionEngine()
	{
	}
	
	public static synchronized void ensureInitialized()
	{
		if (!_initialized)
		{
			reload();
		}
	}
	
	public static synchronized void reload()
	{
		ClanDominionConfig.load();
		ClanDominionData.initSchema();
		ClanSeasonManager.getInstance().init();
		ClanMissionManager.getInstance().init();
		ClanAchievementManager.getInstance().init();
		_initialized = true;
	}
	
	public static long addDominionPoints(int clanId, int targetClanId, int amount, String reason, String actorName, String targetName, String metadata)
	{
		ensureInitialized();
		final int seasonId = ClanSeasonManager.getInstance().getCurrentSeasonId();
		final long total = ClanDominionData.addPoints(clanId, targetClanId, amount, reason, actorName, targetName, metadata, seasonId);
		checkTier(clanId, actorName);
		if (amount >= ClanDominionConfig.ANNOUNCE_MIN_POINTS)
		{
			ClanDominionData.logActivity(clanId, reason, actorName, metadata + " (+" + amount + ")");
		}
		return total;
	}
	
	public static void notifyDungeonComplete(Player player)
	{
		if ((player == null) || (player.getClanId() <= 0))
		{
			return;
		}
		ensureInitialized();
		ClanMissionManager.getInstance().addProgress(player.getClanId(), ClanMissionManager.TYPE_DUNGEON_COMPLETE, 1, player.getName());
	}
	
	public static void notifyTerritoryCapture(String territoryId, String territoryName, int clanId, String actorName)
	{
		ensureInitialized();
		ClanTerritoryManager.getInstance().recordCapture(territoryId, territoryName, clanId, actorName);
	}
	
	private static void checkTier(int clanId, String actorName)
	{
		final Clan clan = ClanTable.getInstance().getClan(clanId);
		if (clan == null)
		{
			return;
		}
		final ClanDominionStats stats = ClanDominionData.getStats(clanId);
		int bestTier = stats.tier;
		for (ClanDominionConfig.TierRequirement requirement : ClanDominionConfig.TIERS)
		{
			if ((stats.totalPoints >= requirement.requiredPoints) && (clan.getMembersCount() >= requirement.minMembers) && (stats.weeklyActivity >= requirement.weeklyActivity))
			{
				bestTier = Math.max(bestTier, requirement.tier);
			}
		}
		if (bestTier > stats.tier)
		{
			ClanDominionData.setTier(clanId, bestTier);
			ClanAchievementManager.getInstance().setProgressAtLeast(clanId, ClanAchievementManager.RISING_POWER, bestTier, actorName);
			ClanAchievementManager.getInstance().setProgressAtLeast(clanId, ClanAchievementManager.MEGARAX_DYNASTY, bestTier, actorName);
			final String tierName = ClanDominionUtil.tierName(bestTier);
			ClanDominionData.logActivity(clanId, "TIER_UP", actorName, clan.getName() + " reached " + tierName + ".");
			if (ClanDominionConfig.ANNOUNCEMENTS_ENABLED && ClanDominionConfig.ANNOUNCE_TIER_UPS)
			{
				Broadcast.toAllOnlinePlayers("Clan Dominion: " + clan.getName() + " is now " + tierName + ".");
			}
		}
	}
}
