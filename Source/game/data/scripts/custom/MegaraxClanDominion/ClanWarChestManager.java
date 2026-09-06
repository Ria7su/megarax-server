package custom.MegaraxClanDominion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.util.Broadcast;

public final class ClanWarChestManager
{
	private static final Logger LOGGER = Logger.getLogger(ClanWarChestManager.class.getName());
	private static final ClanWarChestManager INSTANCE = new ClanWarChestManager();
	private static final int ADENA = 57;
	
	private ClanWarChestManager()
	{
	}
	
	public static ClanWarChestManager getInstance()
	{
		return INSTANCE;
	}
	
	public boolean deposit(Player player, int itemId, long amount)
	{
		if ((player == null) || (player.getClanId() <= 0) || (itemId <= 0) || (amount <= 0))
		{
			return false;
		}
		if (!ClanDominionConfig.WAR_CHEST_ALLOWED_ITEMS.contains(itemId))
		{
			player.sendMessage("This item cannot be deposited into the Clan War Chest.");
			return false;
		}
		if (!ClanDominionConfig.WAR_CHEST_ANY_MEMBER_DEPOSIT && !canManageWarChest(player))
		{
			player.sendMessage("Only clan leaders can deposit into the Clan War Chest.");
			return false;
		}
		if (!takeItem(player, itemId, amount))
		{
			player.sendMessage("Not enough items for this deposit.");
			return false;
		}
		
		final int clanId = player.getClanId();
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("INSERT INTO clan_war_chest (clan_id, item_id, amount, updated_at) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE amount=amount+VALUES(amount), updated_at=VALUES(updated_at)"))
		{
			ps.setInt(1, clanId);
			ps.setInt(2, itemId);
			ps.setLong(3, amount);
			ps.setLong(4, System.currentTimeMillis());
			ps.executeUpdate();
			log(clanId, player, itemId, amount, "DEPOSIT", "War Chest");
			ClanMissionManager.getInstance().addProgress(clanId, ClanMissionManager.TYPE_WAR_CHEST_DEPOSIT, (int) Math.min(Integer.MAX_VALUE, amount), player.getName());
			player.sendMessage("Clan War Chest deposit complete.");
			return true;
		}
		catch (SQLException e)
		{
			refundItem(player, itemId, amount);
			LOGGER.warning("ClanDominion: War Chest deposit failed: " + e.getMessage());
			player.sendMessage("War Chest deposit failed. Items refunded.");
		}
		return false;
	}
	
	public boolean activateRallyPoint(Player player)
	{
		if ((player == null) || (player.getClanId() <= 0))
		{
			return false;
		}
		if (!canManageWarChest(player))
		{
			player.sendMessage("Only the clan leader can activate a Rally Point.");
			return false;
		}
		if (player.isInCombat() || player.isInsideZone(ZoneId.PVP) || player.isInsideZone(ZoneId.SIEGE) || player.isInSiege() || player.isInDuel() || player.isAlikeDead())
		{
			player.sendMessage("You cannot activate a Rally Point from combat or restricted zones.");
			return false;
		}
		
		final RallyPoint existing = getRallyPoint(player.getClanId());
		if ((existing != null) && (existing.cooldownUntil > System.currentTimeMillis()))
		{
			player.sendMessage("Clan Rally Point is on cooldown.");
			return false;
		}
		if (!spend(player.getClanId(), player, ClanDominionConfig.RALLY_COST_ITEM_ID, ClanDominionConfig.RALLY_COST_AMOUNT, "RALLY_POINT"))
		{
			player.sendMessage("Not enough Clan War Chest resources for a Rally Point.");
			return false;
		}
		
		final long now = System.currentTimeMillis();
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("REPLACE INTO clan_rally_points (clan_id, x, y, z, heading, activated_by, created_at, expires_at, cooldown_until) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"))
		{
			ps.setInt(1, player.getClanId());
			ps.setInt(2, player.getX());
			ps.setInt(3, player.getY());
			ps.setInt(4, player.getZ());
			ps.setInt(5, player.getHeading());
			ps.setString(6, player.getName());
			ps.setLong(7, now);
			ps.setLong(8, now + ClanDominionConfig.RALLY_DURATION);
			ps.setLong(9, now + ClanDominionConfig.RALLY_COOLDOWN);
			ps.executeUpdate();
			ClanDominionData.logActivity(player.getClanId(), "RALLY_POINT", player.getName(), "Rally Point activated.");
			for (Player member : player.getClan().getOnlineMembers(0))
			{
				member.sendMessage("Clan Dominion: Rally Point activated by " + player.getName() + ".");
			}
			return true;
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not activate Rally Point: " + e.getMessage());
		}
		return false;
	}
	
	public boolean teleportToRally(Player player)
	{
		if ((player == null) || (player.getClanId() <= 0))
		{
			return false;
		}
		if (player.isInCombat() || player.isInsideZone(ZoneId.PVP) || player.isInsideZone(ZoneId.SIEGE) || player.isInSiege() || player.isInDuel() || player.isAlikeDead())
		{
			player.sendMessage("You cannot use the Rally Point during combat or restricted states.");
			return false;
		}
		final RallyPoint point = getRallyPoint(player.getClanId());
		if ((point == null) || (point.expiresAt <= System.currentTimeMillis()))
		{
			player.sendMessage("Your clan has no active Rally Point.");
			return false;
		}
		player.teleToLocation(point.x, point.y, point.z);
		return true;
	}
	
	public boolean announceChallenge(Player player)
	{
		if ((player == null) || (player.getClanId() <= 0))
		{
			return false;
		}
		if (!canManageWarChest(player))
		{
			player.sendMessage("Only the clan leader can spend War Chest resources.");
			return false;
		}
		if (!spend(player.getClanId(), player, ClanDominionConfig.CHALLENGE_COST_ITEM_ID, ClanDominionConfig.CHALLENGE_COST_AMOUNT, "CHALLENGE_ANNOUNCE"))
		{
			player.sendMessage("Not enough Clan War Chest resources.");
			return false;
		}
		final String message = "Clan Dominion: " + player.getClan().getName() + " calls every rival clan to war!";
		Broadcast.toAllOnlinePlayers(message);
		Broadcast.toAllOnlinePlayersOnScreen(message);
		ClanDominionData.logActivity(player.getClanId(), "CHALLENGE", player.getName(), message);
		return true;
	}
	
	public boolean spend(int clanId, Player actor, int itemId, long amount, String service)
	{
		if ((clanId <= 0) || (itemId <= 0) || (amount <= 0))
		{
			return false;
		}
		try (Connection con = DatabaseFactory.getConnection())
		{
			try (PreparedStatement update = con.prepareStatement("UPDATE clan_war_chest SET amount=amount-?, updated_at=? WHERE clan_id=? AND item_id=? AND amount>=?"))
			{
				update.setLong(1, amount);
				update.setLong(2, System.currentTimeMillis());
				update.setInt(3, clanId);
				update.setInt(4, itemId);
				update.setLong(5, amount);
				if (update.executeUpdate() <= 0)
				{
					return false;
				}
			}
			log(clanId, actor, itemId, amount, "SPEND", service);
			return true;
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: War Chest spend failed: " + e.getMessage());
		}
		return false;
	}
	
	public List<ChestBalance> getBalances(int clanId)
	{
		final List<ChestBalance> result = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT item_id, amount FROM clan_war_chest WHERE clan_id=? ORDER BY item_id"))
		{
			ps.setInt(1, clanId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					result.add(new ChestBalance(rs.getInt("item_id"), rs.getLong("amount")));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not load War Chest balances: " + e.getMessage());
		}
		return result;
	}
	
	public RallyPoint getRallyPoint(int clanId)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT x, y, z, heading, activated_by, created_at, expires_at, cooldown_until FROM clan_rally_points WHERE clan_id=?"))
		{
			ps.setInt(1, clanId);
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					return new RallyPoint(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), rs.getInt("heading"), rs.getString("activated_by"), rs.getLong("created_at"), rs.getLong("expires_at"), rs.getLong("cooldown_until"));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not load Rally Point: " + e.getMessage());
		}
		return null;
	}
	
	public boolean canManageWarChest(Player player)
	{
		return (player != null) && (player.getClan() != null) && player.isClanLeader();
	}
	
	private boolean takeItem(Player player, int itemId, long amount)
	{
		if (amount > Integer.MAX_VALUE)
		{
			player.sendMessage("Amount is too large for one War Chest transaction.");
			return false;
		}
		final int count = (int) amount;
		if (itemId == ADENA)
		{
			return player.reduceAdena(ItemProcessType.FEE, count, player, true);
		}
		return player.destroyItemByItemId(ItemProcessType.FEE, itemId, count, player, true);
	}
	
	private void refundItem(Player player, int itemId, long amount)
	{
		final int count = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
		if (itemId == ADENA)
		{
			player.addAdena(ItemProcessType.REFUND, count, player, true);
		}
		else
		{
			player.addItem(ItemProcessType.REFUND, itemId, count, player, true);
		}
	}
	
	private void log(int clanId, Player actor, int itemId, long amount, String action, String service)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("INSERT INTO clan_war_chest_log (clan_id, player_id, player_name, item_id, amount, action, service, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"))
		{
			ps.setInt(1, clanId);
			ps.setInt(2, actor == null ? 0 : actor.getObjectId());
			ps.setString(3, actor == null ? "" : actor.getName());
			ps.setInt(4, itemId);
			ps.setLong(5, amount);
			ps.setString(6, action);
			ps.setString(7, service == null ? "" : service);
			ps.setLong(8, System.currentTimeMillis());
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not write War Chest log: " + e.getMessage());
		}
	}
	
	public static final class ChestBalance
	{
		public final int itemId;
		public final long amount;
		
		private ChestBalance(int itemId, long amount)
		{
			this.itemId = itemId;
			this.amount = amount;
		}
	}
	
	public static final class RallyPoint
	{
		public final int x;
		public final int y;
		public final int z;
		public final int heading;
		public final String activatedBy;
		public final long createdAt;
		public final long expiresAt;
		public final long cooldownUntil;
		
		private RallyPoint(int x, int y, int z, int heading, String activatedBy, long createdAt, long expiresAt, long cooldownUntil)
		{
			this.x = x;
			this.y = y;
			this.z = z;
			this.heading = heading;
			this.activatedBy = activatedBy;
			this.createdAt = createdAt;
			this.expiresAt = expiresAt;
			this.cooldownUntil = cooldownUntil;
		}
	}
}
