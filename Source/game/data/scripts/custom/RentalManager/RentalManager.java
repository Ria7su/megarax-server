/*
 * Custom Megarax Rental Manager - L2JMobius Interlude
 */
package custom.RentalManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.Containers;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogin;
import org.l2jmobius.gameserver.model.events.holders.actor.player.inventory.OnPlayerItemDrop;
import org.l2jmobius.gameserver.model.events.holders.actor.player.inventory.OnPlayerItemTransfer;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

import ai.AbstractNpcAI;

public class RentalManager extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(RentalManager.class.getName());
	
	private static final int NPC = 9913;
	private static final int RENTAL_MARKER = 991300;
	private static final int ADENA = 57;
	private static final int RAX_COIN = 9142;
	private static final int DUNGEON_COIN = 9143;
	private static final int CRAFT_COIN = 4356;
	private static final int ENCHANT_LEVEL = 10;
	private static final int MAX_ACTIVE_PACKAGES = 3;
	private static final long HOUR = 60L * 60 * 1000;
	
	private static final String TABLE_SQL = "CREATE TABLE IF NOT EXISTS megarax_rentals (object_id INT NOT NULL, owner_id INT NOT NULL, item_id INT NOT NULL, package_name VARCHAR(64) NOT NULL, expires_at BIGINT NOT NULL, PRIMARY KEY (object_id), KEY owner_id (owner_id), KEY expires_at (expires_at))";
	private static final String INSERT_SQL = "INSERT INTO megarax_rentals (object_id, owner_id, item_id, package_name, expires_at) VALUES (?, ?, ?, ?, ?)";
	private static final String DELETE_SQL = "DELETE FROM megarax_rentals WHERE object_id=?";
	private static final String SELECT_OWNER_SQL = "SELECT object_id, item_id, package_name, expires_at FROM megarax_rentals WHERE owner_id=?";
	private static final String SELECT_RENTAL_SQL = "SELECT owner_id, expires_at FROM megarax_rentals WHERE object_id=?";
	private static final String ACTIVE_PACKAGES_SQL = "SELECT COUNT(DISTINCT package_name) AS package_count FROM megarax_rentals WHERE owner_id=? AND expires_at>?";
	private static final String EXPIRED_SQL = "SELECT object_id FROM megarax_rentals WHERE expires_at<=?";
	private static final String DELETE_ITEM_SQL = "DELETE FROM items WHERE object_id=?";
	private static final String DELETE_GROUND_ITEM_SQL = "DELETE FROM itemsonground WHERE object_id=?";
	
	private static final Map<String, RentalOption> OPTIONS = new HashMap<>();
	
	static
	{
		// B grade weapons.
		add(new RentalOption("b_sword", "B Weapon: Keshanberk +10", new int[] { 9004 }, 5000000, 0, 50, 0));
		add(new RentalOption("b_bow", "B Weapon: Dark Elven Long Bow +10", new int[] { 9008 }, 5000000, 0, 50, 0));
		add(new RentalOption("b_pole", "B Weapon: Halberd +10", new int[] { 9012 }, 5000000, 0, 50, 0));
		add(new RentalOption("b_mage", "B Weapon: Sword of Valhalla +10", new int[] { 9005 }, 5000000, 0, 50, 0));
		
		// A grade weapons.
		add(new RentalOption("a_sword", "A Weapon: Tallum Blade +10", new int[] { 9011 }, 12000000, 20, 100, 0));
		add(new RentalOption("a_bow", "A Weapon: Soul Bow +10", new int[] { 9027 }, 12000000, 20, 100, 0));
		add(new RentalOption("a_pole", "A Weapon: Tallum Glaive +10", new int[] { 9028 }, 12000000, 20, 100, 0));
		add(new RentalOption("a_mage", "A Weapon: Branch of the Mother Tree +10", new int[] { 9024 }, 12000000, 20, 100, 0));
		
		// Armor sets.
		add(new RentalOption("b_heavy", "B Set: Zubei Heavy +10", new int[] { 9067, 9068, 9069, 9071, 9072 }, 7000000, 0, 80, 0));
		add(new RentalOption("b_light", "B Set: Zubei Light +10", new int[] { 9073, 9074, 9075, 9076 }, 7000000, 0, 80, 0));
		add(new RentalOption("b_robe", "B Set: Zubei Robe +10", new int[] { 9077, 9078, 9079, 9080 }, 7000000, 0, 80, 0));
		add(new RentalOption("a_heavy", "A Set: Majestic Heavy +10", new int[] { 9094, 9095, 9096, 9097 }, 15000000, 30, 150, 0));
		add(new RentalOption("a_light", "A Set: Majestic Light +10", new int[] { 9098, 9095, 9099, 9100 }, 15000000, 30, 150, 0));
		add(new RentalOption("a_robe", "A Set: Majestic Robe +10", new int[] { 9101, 9095, 9102, 9103 }, 15000000, 30, 150, 0));
		
		// Jewelry sets use normal templates because Interlude has no shadow jewelry templates here.
		add(new RentalOption("a_jewels", "A Jewels: Majestic Set +10", new int[] { 924, 862, 862, 893, 893 }, 10000000, 80, 0, 0));
		add(new RentalOption("s_jewels", "S Jewels: Tateossian Set +10", new int[] { 920, 858, 858, 889, 889 }, 20000000, 150, 0, 100));
	}
	
	private RentalManager()
	{
		addStartNpc(NPC);
		addTalkId(NPC);
		addFirstTalkId(NPC);
		initTable();
		cleanupExpiredRentals();
		Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_LOGIN, (OnPlayerLogin event) -> onPlayerLogin(event), this));
		Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_ITEM_DROP, (OnPlayerItemDrop event) -> onPlayerItemDrop(event), this));
		Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_ITEM_TRANSFER, (OnPlayerItemTransfer event) -> onPlayerItemTransfer(event), this));
		ThreadPool.scheduleAtFixedRate(this::cleanupExpiredRentals, 10 * 60 * 1000, 10 * 60 * 1000);
		LOGGER.info("RentalManager: Script loaded for NPC " + NPC + ".");
	}
	
	private static void add(RentalOption option)
	{
		OPTIONS.put(option.id, option);
	}
	
	@Override
	public String onFirstTalk(Npc npc, Player player)
	{
		cleanupPlayerRentals(player);
		showMain(player);
		return null;
	}
	
	@Override
	public String onTalk(Npc npc, Player player)
	{
		cleanupPlayerRentals(player);
		showMain(player);
		return null;
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		if (player == null)
		{
			return null;
		}
		
		cleanupPlayerRentals(player);
		if (event.equals("main"))
		{
			showMain(player);
			return null;
		}
		if (event.equals("rules"))
		{
			showRules(player);
			return null;
		}
		if (event.startsWith("rent_"))
		{
			final String[] parts = event.split("_");
			if (parts.length >= 3)
			{
				final String optionId = event.substring(5, event.lastIndexOf('_'));
				final int hours = Integer.parseInt(parts[parts.length - 1]);
				rent(player, npc, optionId, hours);
			}
			showMain(player);
			return null;
		}
		
		showMain(player);
		return null;
	}
	
	private void rent(Player player, Npc npc, String optionId, int hours)
	{
		final RentalOption option = OPTIONS.get(optionId);
		if ((option == null) || ((hours != 6) && (hours != 24)))
		{
			player.sendMessage("Invalid rental option.");
			return;
		}
		
		if (!canReceiveRental(player, option))
		{
			return;
		}
		
		if (getActivePackageCount(player) >= MAX_ACTIVE_PACKAGES)
		{
			player.sendMessage("You can only have " + MAX_ACTIVE_PACKAGES + " active rental packages.");
			return;
		}
		
		final long multiplier = (hours == 24) ? 4 : 1;
		final int adena = (int) (option.adena * multiplier);
		final int dungeonCoin = (int) (option.dungeonCoin * multiplier);
		final int craftCoin = (int) (option.craftCoin * multiplier);
		final int raxCoin = (int) (option.raxCoin * multiplier);
		
		if (!takePayment(player, npc, adena, dungeonCoin, craftCoin, raxCoin))
		{
			return;
		}
		
		final long expiresAt = System.currentTimeMillis() + (hours * HOUR);
		final List<Item> grantedItems = new ArrayList<>(option.itemIds.length);
		boolean rentalCreated = true;
		for (int itemId : option.itemIds)
		{
			final Item item = player.addItem(ItemProcessType.REWARD, itemId, 1, npc, true);
			if (item == null)
			{
				rentalCreated = false;
				break;
			}
			item.setEnchantLevel(ENCHANT_LEVEL);
			item.setCustomType1(RENTAL_MARKER);
			item.setCustomType2((int) (expiresAt / 1000));
			item.setProtected(true);
			item.updateDatabase();
			grantedItems.add(item);
			if (!saveRental(item, player, option.name, expiresAt))
			{
				rentalCreated = false;
				break;
			}
		}
		
		if (!rentalCreated || (grantedItems.size() != option.itemIds.length))
		{
			for (Item item : grantedItems)
			{
				player.destroyItem(ItemProcessType.DESTROY, item, player, false);
				deleteRentalItemFromDb(item.getObjectId());
				deleteRentalRow(item.getObjectId());
			}
			refundPayment(player, npc, adena, dungeonCoin, craftCoin, raxCoin);
			player.sendMessage("The rental could not be completed. Your payment was refunded.");
			return;
		}
		
		for (Item item : grantedItems)
		{
			scheduleExpiration(item.getObjectId(), expiresAt);
		}
		
		player.sendMessage("Rental complete: " + option.name + " for " + hours + " hour(s).");
	}
	
	private boolean canReceiveRental(Player player, RentalOption option)
	{
		if (!player.getInventory().validateCapacity(option.itemIds.length))
		{
			player.sendMessage("You need " + option.itemIds.length + " free inventory slot(s) to rent this equipment.");
			return false;
		}
		
		long weight = 0;
		for (int itemId : option.itemIds)
		{
			final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
			if (template == null)
			{
				player.sendMessage("This rental option is not available right now.");
				LOGGER.warning("RentalManager: Missing rental item template " + itemId + " for " + option.name + ".");
				return false;
			}
			weight += template.getWeight();
		}
		
		if (!player.getInventory().validateWeight(weight))
		{
			player.sendMessage("Reduce your weight before renting this equipment.");
			return false;
		}
		return true;
	}
	
	private void refundPayment(Player player, Npc npc, int adena, int dungeonCoin, int craftCoin, int raxCoin)
	{
		if (adena > 0)
		{
			player.addAdena(ItemProcessType.REFUND, adena, npc, true);
		}
		if (dungeonCoin > 0)
		{
			player.addItem(ItemProcessType.REFUND, DUNGEON_COIN, dungeonCoin, npc, true);
		}
		if (craftCoin > 0)
		{
			player.addItem(ItemProcessType.REFUND, CRAFT_COIN, craftCoin, npc, true);
		}
		if (raxCoin > 0)
		{
			player.addItem(ItemProcessType.REFUND, RAX_COIN, raxCoin, npc, true);
		}
	}
	
	private boolean takePayment(Player player, Npc npc, int adena, int dungeonCoin, int craftCoin, int raxCoin)
	{
		if ((player.getInventory().getAdena() < adena) || (player.getInventory().getInventoryItemCount(DUNGEON_COIN, -1) < dungeonCoin) || (player.getInventory().getInventoryItemCount(CRAFT_COIN, -1) < craftCoin) || (player.getInventory().getInventoryItemCount(RAX_COIN, -1) < raxCoin))
		{
			player.sendMessage("Not enough currency for this rental.");
			return false;
		}
		
		if ((adena > 0) && !player.reduceAdena(ItemProcessType.FEE, adena, npc, true))
		{
			return false;
		}
		if ((dungeonCoin > 0) && !player.destroyItemByItemId(ItemProcessType.FEE, DUNGEON_COIN, dungeonCoin, npc, true))
		{
			return false;
		}
		if ((craftCoin > 0) && !player.destroyItemByItemId(ItemProcessType.FEE, CRAFT_COIN, craftCoin, npc, true))
		{
			return false;
		}
		if ((raxCoin > 0) && !player.destroyItemByItemId(ItemProcessType.FEE, RAX_COIN, raxCoin, npc, true))
		{
			return false;
		}
		return true;
	}
	
	private void showMain(Player player)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Megarax Rental Manager</title><body><center>");
		html.append("<button value=\"Rental Manager\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\"><tr><td align=center>");
		html.append("<font color=\"0099FF\">Temporary +10 equipment</font><br1>");
		html.append("<font color=\"ae9977\">Choose 6h or 24h rentals.</font>");
		html.append("</td></tr></table><br>");
		appendSection(html, "B Grade Weapons", "b_sword", "b_bow", "b_pole", "b_mage");
		appendSection(html, "A Grade Weapons", "a_sword", "a_bow", "a_pole", "a_mage");
		appendSection(html, "Armor Sets", "b_heavy", "b_light", "b_robe", "a_heavy", "a_light", "a_robe");
		appendSection(html, "Jewelry Sets", "a_jewels", "s_jewels");
		html.append("<br><button value=\"Rules\" action=\"bypass -h Quest RentalManager rules\" width=92 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		html.append("</center></body></html>");
		sendHtml(player, html.toString());
	}
	
	private void appendSection(StringBuilder html, String title, String... ids)
	{
		html.append("<button value=\"").append(title).append("\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\">");
		for (String id : ids)
		{
			final RentalOption option = OPTIONS.get(id);
			if (option == null)
			{
				continue;
			}
			html.append("<tr><td width=146><font color=\"FFFFFF\">").append(option.name).append("</font><br1><font color=\"ae9977\">").append(formatCost(option, 6)).append("</font></td>");
			html.append("<td width=73><button value=\"6h\" action=\"bypass -h Quest RentalManager rent_").append(id).append("_6\" width=55 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td>");
			html.append("<td width=73><button value=\"24h\" action=\"bypass -h Quest RentalManager rent_").append(id).append("_24\" width=55 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td></tr>");
		}
		html.append("</table><br>");
	}
	
	private void showRules(Player player)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Rental Rules</title><body><center>");
		html.append("<button value=\"Rental Rules\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\">");
		html.append("<tr><td><font color=\"ae9977\">Rental items are temporary and expire automatically.</font></td></tr>");
		html.append("<tr><td><font color=\"ae9977\">Shadow weapons and armor cannot be traded, dropped, sold, or crystallized.</font></td></tr>");
		html.append("<tr><td><font color=\"ae9977\">Jewelry rentals are tracked by the Rental Manager and removed on expiration.</font></td></tr>");
		html.append("<tr><td><font color=\"ae9977\">Maximum active rental packages: ").append(MAX_ACTIVE_PACKAGES).append(".</font></td></tr>");
		html.append("</table><br>");
		html.append("<button value=\"Back\" action=\"bypass -h Quest RentalManager main\" width=92 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		html.append("</center></body></html>");
		sendHtml(player, html.toString());
	}
	
	private String formatCost(RentalOption option, int hours)
	{
		final long multiplier = (hours == 24) ? 4 : 1;
		final StringBuilder sb = new StringBuilder();
		sb.append((option.adena * multiplier) / 1000000).append("kk Adena");
		if (option.dungeonCoin > 0)
		{
			sb.append(", ").append(option.dungeonCoin * multiplier).append(" DC");
		}
		if (option.craftCoin > 0)
		{
			sb.append(", ").append(option.craftCoin * multiplier).append(" Craft");
		}
		if (option.raxCoin > 0)
		{
			sb.append(", ").append(option.raxCoin * multiplier).append(" Rax");
		}
		return sb.toString();
	}
	
	private void sendHtml(Player player, String html)
	{
		final NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html);
		player.sendPacket(msg);
	}
	
	private void initTable()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(TABLE_SQL))
		{
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("RentalManager: Could not create table: " + e.getMessage());
		}
	}
	
	private int getActivePackageCount(Player player)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(ACTIVE_PACKAGES_SQL))
		{
			ps.setInt(1, player.getObjectId());
			ps.setLong(2, System.currentTimeMillis());
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					return rs.getInt("package_count");
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("RentalManager: Could not count active rentals: " + e.getMessage());
		}
		return 0;
	}
	
	private boolean saveRental(Item item, Player player, String packageName, long expiresAt)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(INSERT_SQL))
		{
			ps.setInt(1, item.getObjectId());
			ps.setInt(2, player.getObjectId());
			ps.setInt(3, item.getId());
			ps.setString(4, packageName);
			ps.setLong(5, expiresAt);
			ps.executeUpdate();
			return true;
		}
		catch (SQLException e)
		{
			LOGGER.warning("RentalManager: Could not save rental: " + e.getMessage());
			return false;
		}
	}
	
	private void onPlayerLogin(OnPlayerLogin event)
	{
		cleanupPlayerRentals(event.getPlayer());
	}
	
	private void onPlayerItemDrop(OnPlayerItemDrop event)
	{
		final Item item = event.getItem();
		if ((item != null) && isRentalObject(item.getObjectId()))
		{
			event.getPlayer().sendMessage("Rental items cannot be dropped. The rental has been removed.");
			item.decayMe();
			expireRental(item.getObjectId());
		}
	}
	
	private void onPlayerItemTransfer(OnPlayerItemTransfer event)
	{
		final Item item = event.getItem();
		if ((item != null) && isRentalObject(item.getObjectId()))
		{
			event.getPlayer().sendMessage("Rental items cannot be transferred. The rental has been removed.");
			event.getContainer().destroyItem(ItemProcessType.DESTROY, item, event.getPlayer(), null);
			deleteRentalItemFromDb(item.getObjectId());
			deleteRentalRow(item.getObjectId());
		}
	}
	
	private void cleanupPlayerRentals(Player player)
	{
		if (player == null)
		{
			return;
		}
		
		final long now = System.currentTimeMillis();
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(SELECT_OWNER_SQL))
		{
			ps.setInt(1, player.getObjectId());
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					final int objectId = rs.getInt("object_id");
					final long expiresAt = rs.getLong("expires_at");
					if (expiresAt <= now)
					{
						expireRental(objectId);
					}
					else
					{
						scheduleExpiration(objectId, expiresAt);
					}
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("RentalManager: Could not cleanup player rentals: " + e.getMessage());
		}
	}
	
	private void cleanupExpiredRentals()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(EXPIRED_SQL))
		{
			ps.setLong(1, System.currentTimeMillis());
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					expireRental(rs.getInt("object_id"));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("RentalManager: Could not cleanup expired rentals: " + e.getMessage());
		}
	}
	
	private void scheduleExpiration(int objectId, long expiresAt)
	{
		final long delay = expiresAt - System.currentTimeMillis();
		if (delay <= 0)
		{
			ThreadPool.execute(() -> expireRental(objectId));
		}
		else
		{
			ThreadPool.schedule(() -> expireRental(objectId), delay);
		}
	}
	
	private void expireRental(int objectId)
	{
		int ownerId = 0;
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(SELECT_RENTAL_SQL))
		{
			ps.setInt(1, objectId);
			try (ResultSet rs = ps.executeQuery())
			{
				if (!rs.next())
				{
					return;
				}
				ownerId = rs.getInt("owner_id");
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("RentalManager: Could not read rental: " + e.getMessage());
			return;
		}
		
		final Player player = getPlayerByObjectId(ownerId);
		if (player != null)
		{
			final Item item = player.getInventory().getItemByObjectId(objectId);
			if (item != null)
			{
				final ItemTemplate template = ItemData.getInstance().getTemplate(item.getId());
				player.destroyItem(ItemProcessType.DESTROY, item, player, false);
				player.sendMessage("Rental expired: " + ((template == null) ? "item" : template.getName()) + ".");
			}
		}
		
		deleteRentalItemFromDb(objectId);
		deleteRentalRow(objectId);
	}
	
	private void deleteRentalItemFromDb(int objectId)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ground = con.prepareStatement(DELETE_GROUND_ITEM_SQL);
			PreparedStatement item = con.prepareStatement(DELETE_ITEM_SQL))
		{
			ground.setInt(1, objectId);
			ground.executeUpdate();
			item.setInt(1, objectId);
			item.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("RentalManager: Could not delete rental item from DB: " + e.getMessage());
		}
	}
	
	private void deleteRentalRow(int objectId)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(DELETE_SQL))
		{
			ps.setInt(1, objectId);
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("RentalManager: Could not delete rental row: " + e.getMessage());
		}
	}
	
	private boolean isRentalObject(int objectId)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(SELECT_RENTAL_SQL))
		{
			ps.setInt(1, objectId);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next();
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("RentalManager: Could not check rental object: " + e.getMessage());
		}
		return false;
	}
	
	private Player getPlayerByObjectId(int objectId)
	{
		for (Player player : World.getInstance().getPlayers())
		{
			if (player.getObjectId() == objectId)
			{
				return player;
			}
		}
		return null;
	}
	
	private static class RentalOption
	{
		private final String id;
		private final String name;
		private final int[] itemIds;
		private final int adena;
		private final int dungeonCoin;
		private final int craftCoin;
		private final int raxCoin;
		
		private RentalOption(String id, String name, int[] itemIds, int adena, int dungeonCoin, int craftCoin, int raxCoin)
		{
			this.id = id;
			this.name = name;
			this.itemIds = itemIds;
			this.adena = adena;
			this.dungeonCoin = dungeonCoin;
			this.craftCoin = craftCoin;
			this.raxCoin = raxCoin;
		}
	}
	
	public static void main(String[] args)
	{
		new RentalManager();
	}
}
