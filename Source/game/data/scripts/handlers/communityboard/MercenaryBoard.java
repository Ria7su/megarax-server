/*
 * Custom Megarax Mercenary Contract Board - L2JMobius Interlude
 */
package handlers.communityboard;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.handler.IParseBoardHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;

public class MercenaryBoard implements IParseBoardHandler
{
	private static final Logger LOGGER = Logger.getLogger(MercenaryBoard.class.getName());
	
	private static final int ADENA = 57;
	private static final int RAX_COIN = 9142;
	private static final int DUNGEON_COIN = 9143;
	private static final int CRAFT_COIN = 4356;
	private static final int PVP_COIN = 8875;
	private static final long ACCEPT_TIMEOUT = 2L * 60 * 60 * 1000;
	private static final int PAGE_SIZE = 8;
	
	private static final String TABLE_SQL = "CREATE TABLE IF NOT EXISTS megarax_mercenary_contracts (id INT NOT NULL AUTO_INCREMENT, creator_id INT NOT NULL, creator_name VARCHAR(35) NOT NULL, mercenary_id INT NOT NULL DEFAULT 0, mercenary_name VARCHAR(35) NOT NULL DEFAULT '', type VARCHAR(24) NOT NULL, role VARCHAR(32) NOT NULL, title VARCHAR(80) NOT NULL, currency_id INT NOT NULL, amount INT NOT NULL, status VARCHAR(16) NOT NULL, created_at BIGINT NOT NULL, accepted_at BIGINT NOT NULL DEFAULT 0, expires_at BIGINT NOT NULL DEFAULT 0, completed_at BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (id), KEY status (status), KEY creator_id (creator_id), KEY mercenary_id (mercenary_id))";
	private static final String INSERT_SQL = "INSERT INTO megarax_mercenary_contracts (creator_id, creator_name, type, role, title, currency_id, amount, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?)";
	private static final String OPEN_SQL = "SELECT * FROM megarax_mercenary_contracts WHERE status='OPEN' ORDER BY created_at DESC LIMIT ?";
	private static final String MINE_SQL = "SELECT * FROM megarax_mercenary_contracts WHERE creator_id=? OR mercenary_id=? ORDER BY id DESC LIMIT 20";
	private static final String SELECT_SQL = "SELECT * FROM megarax_mercenary_contracts WHERE id=?";
	private static final String ACCEPT_SQL = "UPDATE megarax_mercenary_contracts SET status='ACCEPTED', mercenary_id=?, mercenary_name=?, accepted_at=?, expires_at=? WHERE id=? AND status='OPEN' AND creator_id<>?";
	private static final String COMPLETE_SQL = "UPDATE megarax_mercenary_contracts SET status='COMPLETED', completed_at=? WHERE id=? AND status='ACCEPTED' AND creator_id=?";
	private static final String READY_SQL = "UPDATE megarax_mercenary_contracts SET status='READY', completed_at=? WHERE id=? AND status='ACCEPTED' AND creator_id=?";
	private static final String CLAIM_SQL = "UPDATE megarax_mercenary_contracts SET status='COMPLETED', completed_at=? WHERE id=? AND status='READY' AND mercenary_id=?";
	private static final String CANCEL_SQL = "UPDATE megarax_mercenary_contracts SET status='CANCELLED', completed_at=? WHERE id=? AND status='OPEN' AND creator_id=?";
	private static final String EXPIRE_SQL = "UPDATE megarax_mercenary_contracts SET status='EXPIRED', completed_at=? WHERE id=? AND status='ACCEPTED' AND creator_id=?";
	private static final String AUTO_READY_SQL = "UPDATE megarax_mercenary_contracts SET status='READY', completed_at=? WHERE mercenary_id=? AND status='ACCEPTED' AND type=?";
	
	private static final Map<String, ContractTemplate> TEMPLATES = new LinkedHashMap<>();
	
	static
	{
		add(new ContractTemplate("d_bishop", "Dungeon", "Bishop", "Need Bishop for dungeon", DUNGEON_COIN, 120));
		add(new ContractTemplate("d_tank", "Dungeon", "Tank", "Need Tank for dungeon", DUNGEON_COIN, 120));
		add(new ContractTemplate("raid_bishop", "Raid", "Bishop", "Need Bishop for raid boss", RAX_COIN, 250));
		add(new ContractTemplate("raid_tank", "Raid", "Tank", "Need Tank for raid boss", RAX_COIN, 250));
		add(new ContractTemplate("pvp_support", "PvP Zone", "Support", "Need Support for PvP Zone", PVP_COIN, 30));
		add(new ContractTemplate("pvp_ol", "PvP Zone", "Overlord/Warcryer", "Need OL/WC for PvP Zone", PVP_COIN, 40));
		add(new ContractTemplate("conquest_support", "Conquest", "Support", "Need Support for Territory Conquest", RAX_COIN, 300));
		add(new ContractTemplate("farm_spoiler", "Spoil/Farm", "Spoiler", "Need Spoiler for farm party", CRAFT_COIN, 150));
	}
	
	public MercenaryBoard()
	{
		initTable();
	}
	
	public static void notifyDungeonComplete(Player player)
	{
		completeAcceptedByType(player, "Dungeon");
	}
	
	public static void notifyRaidKill(Player player)
	{
		completeAcceptedByType(player, "Raid");
	}
	
	public static void notifyPvPKill(Player player)
	{
		completeAcceptedByType(player, "PvP Zone");
	}
	
	public static void notifySpoilAction(Player player)
	{
		completeAcceptedByType(player, "Spoil/Farm");
	}
	
	private static void completeAcceptedByType(Player player, String type)
	{
		if ((player == null) || (type == null))
		{
			return;
		}
		
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(AUTO_READY_SQL))
		{
			ps.setLong(1, System.currentTimeMillis());
			ps.setInt(2, player.getObjectId());
			ps.setString(3, type);
			final int count = ps.executeUpdate();
			if (count > 0)
			{
				player.sendMessage("Mercenary Board: " + count + " " + type + " contract(s) are ready to claim.");
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MercenaryBoard: Could not auto-complete contract: " + e.getMessage());
		}
	}
	
	private static void add(ContractTemplate template)
	{
		TEMPLATES.put(template.id, template);
	}
	
	@Override
	public String[] getCommandList()
	{
		return new String[]
		{
			"_bbsmerc"
		};
	}
	
	@Override
	public boolean onCommand(String command, Player player)
	{
		if (command.equals("_bbsmerc"))
		{
			showOpen(player, 1);
		}
		else if (command.startsWith("_bbsmerc;create;"))
		{
			createContract(player, command.substring("_bbsmerc;create;".length()));
			showOpen(player, 1);
		}
		else if (command.startsWith("_bbsmerc;accept;"))
		{
			acceptContract(player, parseInt(command.substring("_bbsmerc;accept;".length())));
			showOpen(player, 1);
		}
		else if (command.startsWith("_bbsmerc;complete;"))
		{
			completeContract(player, parseInt(command.substring("_bbsmerc;complete;".length())));
			showMine(player);
		}
		else if (command.startsWith("_bbsmerc;claim;"))
		{
			claimContract(player, parseInt(command.substring("_bbsmerc;claim;".length())));
			showMine(player);
		}
		else if (command.startsWith("_bbsmerc;cancel;"))
		{
			cancelContract(player, parseInt(command.substring("_bbsmerc;cancel;".length())));
			showMine(player);
		}
		else if (command.startsWith("_bbsmerc;expire;"))
		{
			expireAcceptedContract(player, parseInt(command.substring("_bbsmerc;expire;".length())));
			showMine(player);
		}
		else if (command.equals("_bbsmerc;mine"))
		{
			showMine(player);
		}
		else if (command.equals("_bbsmerc;create"))
		{
			showCreate(player);
		}
		else
		{
			showOpen(player, 1);
		}
		return false;
	}
	
	private void createContract(Player player, String templateId)
	{
		final ContractTemplate template = TEMPLATES.get(templateId);
		if (template == null)
		{
			player.sendMessage("Invalid mercenary contract.");
			return;
		}
		if (!takePayment(player, template.currencyId, template.amount))
		{
			player.sendMessage("Not enough " + getCurrencyName(template.currencyId) + " to create this contract.");
			return;
		}
		
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(INSERT_SQL))
		{
			ps.setInt(1, player.getObjectId());
			ps.setString(2, player.getName());
			ps.setString(3, template.type);
			ps.setString(4, template.role);
			ps.setString(5, template.title);
			ps.setInt(6, template.currencyId);
			ps.setInt(7, template.amount);
			ps.setLong(8, System.currentTimeMillis());
			ps.executeUpdate();
			player.sendMessage("Mercenary contract created. Payment is now reserved by the board.");
		}
		catch (SQLException e)
		{
			refund(player, template.currencyId, template.amount);
			LOGGER.warning("MercenaryBoard: Could not create contract: " + e.getMessage());
			player.sendMessage("Could not create contract. Payment refunded.");
		}
	}
	
	private void acceptContract(Player player, int contractId)
	{
		if (contractId <= 0)
		{
			return;
		}
		
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(ACCEPT_SQL))
		{
			final long now = System.currentTimeMillis();
			ps.setInt(1, player.getObjectId());
			ps.setString(2, player.getName());
			ps.setLong(3, now);
			ps.setLong(4, now + ACCEPT_TIMEOUT);
			ps.setInt(5, contractId);
			ps.setInt(6, player.getObjectId());
			if (ps.executeUpdate() > 0)
			{
				player.sendMessage("Mercenary contract accepted. Contact the client to complete the task.");
			}
			else
			{
				player.sendMessage("Could not accept this contract.");
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MercenaryBoard: Could not accept contract: " + e.getMessage());
		}
	}
	
	private void completeContract(Player player, int contractId)
	{
		final Contract contract = getContract(contractId);
		if ((contract == null) || (contract.creatorId != player.getObjectId()) || !"ACCEPTED".equals(contract.status))
		{
			player.sendMessage("You cannot complete this contract.");
			return;
		}
		
		final Player mercenary = getOnlinePlayer(contract.mercenaryId);
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement((mercenary != null) ? COMPLETE_SQL : READY_SQL))
		{
			ps.setLong(1, System.currentTimeMillis());
			ps.setInt(2, contractId);
			ps.setInt(3, player.getObjectId());
			if (ps.executeUpdate() > 0)
			{
				if (mercenary != null)
				{
					refund(mercenary, contract.currencyId, contract.amount);
					mercenary.sendMessage("Mercenary contract completed. Payment received.");
				}
				else
				{
					player.sendMessage("Mercenary is offline. Contract marked as ready to claim.");
				}
				player.sendMessage("Contract completed for " + contract.mercenaryName + ".");
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MercenaryBoard: Could not complete contract: " + e.getMessage());
		}
	}
	
	private void claimContract(Player player, int contractId)
	{
		final Contract contract = getContract(contractId);
		if ((contract == null) || (contract.mercenaryId != player.getObjectId()) || !"READY".equals(contract.status))
		{
			player.sendMessage("You have no payment to claim from this contract.");
			return;
		}
		
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(CLAIM_SQL))
		{
			ps.setLong(1, System.currentTimeMillis());
			ps.setInt(2, contractId);
			ps.setInt(3, player.getObjectId());
			if (ps.executeUpdate() > 0)
			{
				refund(player, contract.currencyId, contract.amount);
				player.sendMessage("Mercenary payment claimed.");
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MercenaryBoard: Could not claim contract: " + e.getMessage());
		}
	}
	
	private void cancelContract(Player player, int contractId)
	{
		final Contract contract = getContract(contractId);
		if ((contract == null) || (contract.creatorId != player.getObjectId()) || !"OPEN".equals(contract.status))
		{
			player.sendMessage("Only open contracts can be cancelled.");
			return;
		}
		
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(CANCEL_SQL))
		{
			ps.setLong(1, System.currentTimeMillis());
			ps.setInt(2, contractId);
			ps.setInt(3, player.getObjectId());
			if (ps.executeUpdate() > 0)
			{
				refund(player, contract.currencyId, contract.amount);
				player.sendMessage("Contract cancelled. Payment refunded.");
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MercenaryBoard: Could not cancel contract: " + e.getMessage());
		}
	}
	
	private void expireAcceptedContract(Player player, int contractId)
	{
		final Contract contract = getContract(contractId);
		if ((contract == null) || (contract.creatorId != player.getObjectId()) || !"ACCEPTED".equals(contract.status))
		{
			player.sendMessage("Only accepted contracts can expire.");
			return;
		}
		if (contract.expiresAt > System.currentTimeMillis())
		{
			player.sendMessage("This contract has not reached its timeout yet.");
			return;
		}
		
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(EXPIRE_SQL))
		{
			ps.setLong(1, System.currentTimeMillis());
			ps.setInt(2, contractId);
			ps.setInt(3, player.getObjectId());
			if (ps.executeUpdate() > 0)
			{
				refund(player, contract.currencyId, contract.amount);
				player.sendMessage("Contract expired. Payment refunded.");
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MercenaryBoard: Could not expire contract: " + e.getMessage());
		}
	}
	
	private void showOpen(Player player, int page)
	{
		final StringBuilder html = startPage("Mercenary Board");
		appendTabs(html);
		html.append("<table width=720 bgcolor=000000><tr><td width=90>Type</td><td width=130>Role</td><td width=230>Contract</td><td width=120>Reward</td><td width=80>Client</td><td width=70>Action</td></tr></table>");
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(OPEN_SQL))
		{
			ps.setInt(1, PAGE_SIZE);
			try (ResultSet rs = ps.executeQuery())
			{
				boolean empty = true;
				while (rs.next())
				{
					empty = false;
					html.append("<table width=720><tr>");
					html.append("<td width=90>").append(rs.getString("type")).append("</td>");
					html.append("<td width=130>").append(rs.getString("role")).append("</td>");
					html.append("<td width=230>").append(rs.getString("title")).append("</td>");
					html.append("<td width=120>").append(rs.getInt("amount")).append(" ").append(getCurrencyName(rs.getInt("currency_id"))).append("</td>");
					html.append("<td width=80>").append(rs.getString("creator_name")).append("</td>");
					html.append("<td width=70><button value=\"Accept\" action=\"bypass _bbsmerc;accept;").append(rs.getInt("id")).append("\" width=65 height=20 back=\"l2ui_ch3.smallbutton2_down\" fore=\"l2ui_ch3.smallbutton2\"></td>");
					html.append("</tr></table><img src=\"L2UI.Squaregray\" width=720 height=1>");
				}
				if (empty)
				{
					html.append("<br><center>No open contracts right now.</center>");
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MercenaryBoard: Could not load open contracts: " + e.getMessage());
			html.append("<br><center>Could not load contracts.</center>");
		}
		endPage(html);
		CommunityBoardHandler.separateAndSend(html.toString(), player);
	}
	
	private void showMine(Player player)
	{
		final StringBuilder html = startPage("My Contracts");
		appendTabs(html);
		html.append("<table width=720 bgcolor=000000><tr><td width=45>ID</td><td width=90>Status</td><td width=190>Contract</td><td width=120>Reward</td><td width=95>Client</td><td width=95>Mercenary</td><td width=85>Action</td></tr></table>");
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(MINE_SQL))
		{
			ps.setInt(1, player.getObjectId());
			ps.setInt(2, player.getObjectId());
			try (ResultSet rs = ps.executeQuery())
			{
				boolean empty = true;
				while (rs.next())
				{
					empty = false;
					final int id = rs.getInt("id");
					final int creatorId = rs.getInt("creator_id");
					final String status = rs.getString("status");
					html.append("<table width=720><tr>");
					html.append("<td width=45>").append(id).append("</td>");
					html.append("<td width=90>").append(status).append("</td>");
					html.append("<td width=190>").append(rs.getString("title")).append("</td>");
					html.append("<td width=120>").append(rs.getInt("amount")).append(" ").append(getCurrencyName(rs.getInt("currency_id"))).append("</td>");
					html.append("<td width=95>").append(rs.getString("creator_name")).append("</td>");
					html.append("<td width=95>").append(rs.getString("mercenary_name")).append("</td>");
					html.append("<td width=85>");
					if ((creatorId == player.getObjectId()) && "OPEN".equals(status))
					{
						html.append("<button value=\"Cancel\" action=\"bypass _bbsmerc;cancel;").append(id).append("\" width=65 height=20 back=\"l2ui_ch3.smallbutton2_down\" fore=\"l2ui_ch3.smallbutton2\">");
					}
					else if ((creatorId == player.getObjectId()) && "ACCEPTED".equals(status))
					{
						html.append("<button value=\"Done\" action=\"bypass _bbsmerc;complete;").append(id).append("\" width=65 height=20 back=\"l2ui_ch3.smallbutton2_down\" fore=\"l2ui_ch3.smallbutton2\">");
						if (rs.getLong("expires_at") <= System.currentTimeMillis())
						{
							html.append("<br1><a action=\"bypass _bbsmerc;expire;").append(id).append("\">Expire</a>");
						}
					}
					else if ((rs.getInt("mercenary_id") == player.getObjectId()) && "READY".equals(status))
					{
						html.append("<button value=\"Claim\" action=\"bypass _bbsmerc;claim;").append(id).append("\" width=65 height=20 back=\"l2ui_ch3.smallbutton2_down\" fore=\"l2ui_ch3.smallbutton2\">");
					}
					html.append("</td></tr></table><img src=\"L2UI.Squaregray\" width=720 height=1>");
				}
				if (empty)
				{
					html.append("<br><center>You have no contracts.</center>");
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MercenaryBoard: Could not load player contracts: " + e.getMessage());
			html.append("<br><center>Could not load contracts.</center>");
		}
		endPage(html);
		CommunityBoardHandler.separateAndSend(html.toString(), player);
	}
	
	private void showCreate(Player player)
	{
		final StringBuilder html = startPage("Create Contract");
		appendTabs(html);
		html.append("<table width=720 bgcolor=000000><tr><td width=100>Type</td><td width=150>Role</td><td width=280>Contract</td><td width=120>Reward</td><td width=70>Create</td></tr></table>");
		for (ContractTemplate template : TEMPLATES.values())
		{
			html.append("<table width=720><tr>");
			html.append("<td width=100>").append(template.type).append("</td>");
			html.append("<td width=150>").append(template.role).append("</td>");
			html.append("<td width=280>").append(template.title).append("</td>");
			html.append("<td width=120>").append(template.amount).append(" ").append(getCurrencyName(template.currencyId)).append("</td>");
			html.append("<td width=70><button value=\"Post\" action=\"bypass _bbsmerc;create;").append(template.id).append("\" width=65 height=20 back=\"l2ui_ch3.smallbutton2_down\" fore=\"l2ui_ch3.smallbutton2\"></td>");
			html.append("</tr></table><img src=\"L2UI.Squaregray\" width=720 height=1>");
		}
		html.append("<br><center>Payment is reserved by the board until completion or cancellation.</center>");
		endPage(html);
		CommunityBoardHandler.separateAndSend(html.toString(), player);
	}
	
	private StringBuilder startPage(String title)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html><body><br><center>");
		html.append("<table width=720 bgcolor=000000><tr><td align=center><font color=LEVEL>").append(title).append("</font><br1>");
		html.append("<font color=ae9977>Megarax player contract market</font></td></tr></table><br>");
		return html;
	}
	
	private void appendTabs(StringBuilder html)
	{
		html.append("<table width=720><tr>");
		html.append("<td align=center><button value=\"Open Contracts\" action=\"bypass _bbsmerc\" width=140 height=24 back=\"l2ui_ch3.bigbutton3_down\" fore=\"l2ui_ch3.bigbutton3\"></td>");
		html.append("<td align=center><button value=\"Create Contract\" action=\"bypass _bbsmerc;create\" width=140 height=24 back=\"l2ui_ch3.bigbutton3_down\" fore=\"l2ui_ch3.bigbutton3\"></td>");
		html.append("<td align=center><button value=\"My Contracts\" action=\"bypass _bbsmerc;mine\" width=140 height=24 back=\"l2ui_ch3.bigbutton3_down\" fore=\"l2ui_ch3.bigbutton3\"></td>");
		html.append("</tr></table><br>");
	}
	
	private void endPage(StringBuilder html)
	{
		html.append("</center></body></html>");
	}
	
	private Contract getContract(int id)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(SELECT_SQL))
		{
			ps.setInt(1, id);
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					return new Contract(rs);
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MercenaryBoard: Could not load contract: " + e.getMessage());
		}
		return null;
	}
	
	private boolean takePayment(Player player, int itemId, int amount)
	{
		if (itemId == ADENA)
		{
			return player.reduceAdena(ItemProcessType.FEE, amount, player, true);
		}
		return player.destroyItemByItemId(ItemProcessType.FEE, itemId, amount, player, true);
	}
	
	private void refund(Player player, int itemId, int amount)
	{
		if (itemId == ADENA)
		{
			player.addAdena(ItemProcessType.REFUND, amount, player, true);
		}
		else
		{
			player.addItem(ItemProcessType.REWARD, itemId, amount, player, true);
		}
	}
	
	private Player getOnlinePlayer(int objectId)
	{
		for (Player player : org.l2jmobius.gameserver.model.World.getInstance().getPlayers())
		{
			if (player.getObjectId() == objectId)
			{
				return player;
			}
		}
		return null;
	}
	
	private String getCurrencyName(int itemId)
	{
		switch (itemId)
		{
			case ADENA:
				return "Adena";
			case RAX_COIN:
				return "Rax";
			case DUNGEON_COIN:
				return "Dungeon";
			case CRAFT_COIN:
				return "Craft";
			case PVP_COIN:
				return "PvP";
			default:
				return "Item " + itemId;
		}
	}
	
	private int parseInt(String value)
	{
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
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
			LOGGER.warning("MercenaryBoard: Could not create table: " + e.getMessage());
		}
	}
	
	private static class ContractTemplate
	{
		private final String id;
		private final String type;
		private final String role;
		private final String title;
		private final int currencyId;
		private final int amount;
		
		private ContractTemplate(String id, String type, String role, String title, int currencyId, int amount)
		{
			this.id = id;
			this.type = type;
			this.role = role;
			this.title = title;
			this.currencyId = currencyId;
			this.amount = amount;
		}
	}
	
	private static class Contract
	{
		private final int creatorId;
		private final int mercenaryId;
		private final String mercenaryName;
		private final int currencyId;
		private final int amount;
		private final String status;
		private final long expiresAt;
		
		private Contract(ResultSet rs) throws SQLException
		{
			creatorId = rs.getInt("creator_id");
			mercenaryId = rs.getInt("mercenary_id");
			mercenaryName = rs.getString("mercenary_name");
			currencyId = rs.getInt("currency_id");
			amount = rs.getInt("amount");
			status = rs.getString("status");
			expiresAt = rs.getLong("expires_at");
		}
	}
}
