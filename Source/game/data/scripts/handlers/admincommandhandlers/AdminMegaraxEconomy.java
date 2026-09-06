/*
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package handlers.admincommandhandlers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.handler.IAdminCommandHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * Megarax economy dashboard.
 */
public class AdminMegaraxEconomy implements IAdminCommandHandler
{
	private static final Logger LOGGER = Logger.getLogger(AdminMegaraxEconomy.class.getName());
	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
	private static final int TOP_LIMIT = 8;
	
	private static final CoinInfo[] COINS =
	{
		new CoinInfo("Adena", 57),
		new CoinInfo("Rax Coin", 9142),
		new CoinInfo("Mega Coin", 6673),
		new CoinInfo("Dungeon Coin", 9143),
		new CoinInfo("Craft Coin", 4356),
		new CoinInfo("PvP Coin", 8875),
	};
	
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_economy",
		"admin_megarax_economy"
	};
	
	@Override
	public boolean onCommand(String command, Player activeChar)
	{
		showDashboard(activeChar);
		return true;
	}
	
	private void showDashboard(Player activeChar)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage();
		final StringBuilder sb = new StringBuilder();
		sb.append("<html><title>Megarax Economy</title><body><center>");
		sb.append("<table width=270 bgcolor=\"444444\"><tr>");
		sb.append("<td><button value=\"Main\" action=\"bypass admin_admin\" width=65 height=21 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\"></td>");
		sb.append("<td><button value=\"Server\" action=\"bypass admin_admin4\" width=65 height=21 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\"></td>");
		sb.append("<td><button value=\"Refresh\" action=\"bypass -h admin_economy\" width=65 height=21 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\"></td>");
		sb.append("</tr></table><br>");
		sb.append("<font color=\"LEVEL\">Megarax Economy Dashboard</font><br1>");
		sb.append("<font color=\"B09878\">Currency circulation snapshot</font><br>");
		
		try (Connection con = DatabaseFactory.getConnection())
		{
			appendTotals(sb, con);
			appendTopHolders(sb, con, "Top Rax Coin", 9142);
			appendTopHolders(sb, con, "Top Mega Coin", 6673);
			appendTopHolders(sb, con, "Top Dungeon Coin", 9143);
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Could not load Megarax economy dashboard.", e);
			sb.append("<br><font color=\"FF6666\">Could not load economy data.</font><br1>");
			sb.append("<font color=\"B09878\">Check gameserver error log for details.</font>");
		}
		
		sb.append("<br><font color=\"777777\">Use this panel to watch inflation and coin sinks.</font>");
		sb.append("</center></body></html>");
		html.setHtml(sb.toString());
		activeChar.sendPacket(html);
	}
	
	private void appendTotals(StringBuilder sb, Connection con) throws SQLException
	{
		sb.append("<table width=270 bgcolor=\"111111\">");
		sb.append("<tr><td width=150><font color=\"LEVEL\">Currency</font></td><td width=90 align=right><font color=\"LEVEL\">Total</font></td></tr>");
		for (CoinInfo coin : COINS)
		{
			sb.append("<tr><td>").append(coin.name).append(" <font color=\"777777\">(").append(coin.itemId).append(")</font></td>");
			sb.append("<td align=right>").append(format(getItemTotal(con, coin.itemId))).append("</td></tr>");
		}
		sb.append("</table><br>");
	}
	
	private void appendTopHolders(StringBuilder sb, Connection con, String title, int itemId) throws SQLException
	{
		final List<HolderInfo> holders = getTopHolders(con, itemId);
		sb.append("<table width=270 bgcolor=\"111111\">");
		sb.append("<tr><td colspan=2><font color=\"LEVEL\">").append(title).append("</font></td></tr>");
		if (holders.isEmpty())
		{
			sb.append("<tr><td colspan=2><font color=\"777777\">No holders found.</font></td></tr>");
		}
		else
		{
			for (HolderInfo holder : holders)
			{
				sb.append("<tr><td width=150>").append(holder.name).append("</td><td width=90 align=right>").append(format(holder.total)).append("</td></tr>");
			}
		}
		sb.append("</table><br>");
	}
	
	private long getItemTotal(Connection con, int itemId) throws SQLException
	{
		try (PreparedStatement statement = con.prepareStatement("SELECT COALESCE(SUM(`count`), 0) FROM items WHERE item_id = ?"))
		{
			statement.setInt(1, itemId);
			try (ResultSet result = statement.executeQuery())
			{
				return result.next() ? result.getLong(1) : 0;
			}
		}
	}
	
	private List<HolderInfo> getTopHolders(Connection con, int itemId) throws SQLException
	{
		final List<HolderInfo> holders = new ArrayList<>();
		try (PreparedStatement statement = con.prepareStatement("SELECT i.owner_id, c.char_name, SUM(i.`count`) AS total FROM items i LEFT JOIN characters c ON c.charId = i.owner_id WHERE i.item_id = ? GROUP BY i.owner_id, c.char_name ORDER BY total DESC LIMIT ?"))
		{
			statement.setInt(1, itemId);
			statement.setInt(2, TOP_LIMIT);
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					final String charName = result.getString("char_name");
					final String holderName = (charName == null) || charName.isEmpty() ? ("Owner " + result.getInt("owner_id")) : charName;
					holders.add(new HolderInfo(holderName, result.getLong("total")));
				}
			}
		}
		return holders;
	}
	
	private static String format(long value)
	{
		return NUMBER_FORMAT.format(value);
	}
	
	@Override
	public String[] getCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	private static class CoinInfo
	{
		private final String name;
		private final int itemId;
		
		private CoinInfo(String name, int itemId)
		{
			this.name = name;
			this.itemId = itemId;
		}
	}
	
	private static class HolderInfo
	{
		private final String name;
		private final long total;
		
		private HolderInfo(String name, long total)
		{
			this.name = name;
			this.total = total;
		}
	}
}
