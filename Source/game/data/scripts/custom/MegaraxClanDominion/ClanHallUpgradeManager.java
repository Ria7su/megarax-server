package custom.MegaraxClanDominion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.actor.Player;

public final class ClanHallUpgradeManager
{
	private static final Logger LOGGER = Logger.getLogger(ClanHallUpgradeManager.class.getName());
	private static final ClanHallUpgradeManager INSTANCE = new ClanHallUpgradeManager();
	private static final int ADENA = 57;
	
	private ClanHallUpgradeManager()
	{
	}
	
	public static ClanHallUpgradeManager getInstance()
	{
		return INSTANCE;
	}
	
	public int getUpgradeLevel(int clanId)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT upgrade_level FROM clan_hall_upgrades WHERE clan_id=?"))
		{
			ps.setInt(1, clanId);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? Math.max(1, rs.getInt("upgrade_level")) : 1;
			}
		}
		catch (SQLException e)
		{
			return 1;
		}
	}
	
	public boolean upgrade(Player player)
	{
		if ((player == null) || (player.getClan() == null))
		{
			return false;
		}
		if (!player.isClanLeader())
		{
			player.sendMessage("Only the clan leader can upgrade the Clan Hall.");
			return false;
		}
		if (player.getClan().getHideoutId() <= 0)
		{
			player.sendMessage("Your clan does not own a Clan Hall.");
			return false;
		}
		
		final int clanId = player.getClanId();
		final int current = getUpgradeLevel(clanId);
		final int next = current + 1;
		if (next > ClanDominionConfig.MAX_CLAN_HALL_UPGRADE_LEVEL)
		{
			player.sendMessage("Your Clan Hall is already at maximum Dominion upgrade level.");
			return false;
		}
		final ClanDominionConfig.HallUpgradeRequirement requirement = ClanDominionConfig.HALL_UPGRADE_REQUIREMENTS.get(next);
		if (requirement == null)
		{
			player.sendMessage("No Clan Hall upgrade requirement is configured for level " + next + ".");
			return false;
		}
		final ClanDominionStats stats = ClanDominionData.getStats(clanId);
		if (stats.totalPoints < requirement.requiredPoints)
		{
			player.sendMessage("Your clan needs more Dominion Points for this upgrade.");
			return false;
		}
		if (stats.weeklyActivity < requirement.minWeeklyActivity)
		{
			player.sendMessage("Your clan needs more weekly activity for this upgrade.");
			return false;
		}
		if ((requirement.adenaCost > 0) && !ClanWarChestManager.getInstance().spend(clanId, player, ADENA, requirement.adenaCost, "CLAN_HALL_UPGRADE_" + next))
		{
			player.sendMessage("Not enough Adena in the Clan War Chest.");
			return false;
		}
		
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("REPLACE INTO clan_hall_upgrades (clan_id, hall_id, upgrade_level, upgraded_at, updated_at) VALUES (?, ?, ?, ?, ?)"))
		{
			final long now = System.currentTimeMillis();
			ps.setInt(1, clanId);
			ps.setInt(2, player.getClan().getHideoutId());
			ps.setInt(3, next);
			ps.setLong(4, now);
			ps.setLong(5, now);
			ps.executeUpdate();
			ClanDominionData.logActivity(clanId, "HALL_UPGRADE", player.getName(), "Clan Hall upgraded to level " + next + ".");
			ClanAchievementManager.getInstance().setProgressAtLeast(clanId, ClanAchievementManager.RISING_POWER, stats.tier, player.getName());
			for (Player member : player.getClan().getOnlineMembers(0))
			{
				member.sendMessage("Clan Dominion: Clan Hall upgraded to level " + next + ".");
			}
			return true;
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not upgrade Clan Hall: " + e.getMessage());
		}
		return false;
	}
}
