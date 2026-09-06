package custom.MegaraxMarketplace;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.handler.IAdminCommandHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

public class MarketplaceAdminCommand implements IAdminCommandHandler
{
	private static final Logger LOGGER = Logger.getLogger(MarketplaceAdminCommand.class.getName());
	private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.US);
	private static final String[] COMMANDS =
	{
		"admin_marketplace"
	};
	
	private final MarketplaceManager _manager = MarketplaceManager.getInstance();
	
	@Override
	public boolean onCommand(String command, Player activeChar)
	{
		final StringTokenizer st = new StringTokenizer(command, " ");
		st.nextToken();
		final String action = st.hasMoreTokens() ? st.nextToken().toLowerCase(Locale.ROOT) : "dashboard";
		if ("reload".equals(action))
		{
			_manager.reload();
			activeChar.sendMessage("Marketplace config reloaded.");
			showDashboard(activeChar);
		}
		else if ("enable".equals(action))
		{
			_manager.setEnabled(true);
			activeChar.sendMessage("Marketplace enabled.");
			showDashboard(activeChar);
		}
		else if ("disable".equals(action))
		{
			_manager.setEnabled(false);
			activeChar.sendMessage("Marketplace disabled.");
			showDashboard(activeChar);
		}
		else if ("info".equals(action) && st.hasMoreTokens())
		{
			showListing(activeChar, parseInt(st.nextToken()));
		}
		else if (("remove".equals(action) || "return".equals(action)) && st.hasMoreTokens())
		{
			final int listingId = parseInt(st.nextToken());
			if (_manager.adminRemove(activeChar, listingId))
			{
				activeChar.sendMessage("Marketplace listing removed and sent to claims.");
			}
			showListing(activeChar, listingId);
		}
		else if ("seller".equals(action) && st.hasMoreTokens())
		{
			showByName(activeChar, "seller_name", st.nextToken());
		}
		else if ("buyer".equals(action) && st.hasMoreTokens())
		{
			showByName(activeChar, "buyer_name", st.nextToken());
		}
		else
		{
			showDashboard(activeChar);
		}
		return true;
	}
	
	@Override
	public String[] getCommandList()
	{
		return COMMANDS;
	}
	
	private void showDashboard(Player activeChar)
	{
		final MarketplaceStats stats = MarketplaceDAO.getInstance().getStats();
		final StringBuilder html = base("Marketplace Admin");
		html.append("<table width=270 bgcolor=\"111111\">");
		row(html, "Enabled", MarketplaceConfig.ENABLED ? "Yes" : "No");
		row(html, "Currency", String.valueOf(MarketplaceConfig.CURRENCY_ITEM_ID));
		row(html, "Commission", MarketplaceConfig.COMMISSION_PERCENT + "%");
		row(html, "Active", format(stats.activeListings));
		row(html, "Sold", format(stats.soldListings));
		row(html, "Expired", format(stats.expiredListings));
		row(html, "Moved", format(stats.totalMoved));
		row(html, "Commission burned", format(stats.totalCommission));
		row(html, "Pending revenue", format(stats.pendingRevenue));
		html.append("</table><br>");
		html.append("<table width=270><tr><td><button value=\"Reload\" action=\"bypass -h admin_marketplace reload\" width=80 height=21 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\"></td>");
		html.append("<td><button value=\"Enable\" action=\"bypass -h admin_marketplace enable\" width=80 height=21 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\"></td>");
		html.append("<td><button value=\"Disable\" action=\"bypass -h admin_marketplace disable\" width=80 height=21 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\"></td></tr></table>");
		html.append("<br><font color=\"777777\">Usage: //marketplace info/remove/return/seller/buyer</font>");
		end(activeChar, html);
	}
	
	private void showListing(Player activeChar, int listingId)
	{
		final MarketplaceListing listing = MarketplaceDAO.getInstance().getListing(listingId);
		final StringBuilder html = base("Marketplace Listing");
		if (listing == null)
		{
			html.append("<font color=\"FF6666\">Listing not found.</font>");
		}
		else
		{
			html.append("<table width=270 bgcolor=\"111111\">");
			row(html, "Listing", String.valueOf(listing.listingId));
			row(html, "Status", listing.status.name());
			row(html, "Seller", listing.sellerName + " (" + listing.sellerCharId + ")");
			row(html, "Buyer", listing.buyerName + " (" + listing.buyerCharId + ")");
			row(html, "Item", listing.item.itemName + " (" + listing.item.itemId + ")");
			row(html, "ObjectId", String.valueOf(listing.item.itemObjectId));
			row(html, "Count", format(listing.item.quantity));
			row(html, "Enchant", "+" + listing.item.enchantLevel);
			row(html, "Augment", String.valueOf(listing.item.augmentationId));
			row(html, "Price", format(listing.price));
			row(html, "Commission", format(listing.commissionAmount));
			row(html, "Revenue", format(listing.sellerRevenue));
			html.append("</table><br>");
			if (listing.status == MarketplaceStatus.ACTIVE)
			{
				html.append("<button value=\"Remove safely\" action=\"bypass -h admin_marketplace remove ").append(listing.listingId).append("\" width=120 height=21 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\">");
			}
		}
		end(activeChar, html);
	}
	
	private void showByName(Player activeChar, String column, String name)
	{
		final StringBuilder html = base("Marketplace Search");
		html.append("<table width=270 bgcolor=\"111111\"><tr><td>ID</td><td>Status</td><td>Item</td><td>Price</td></tr>");
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT listing_id,status,item_name,price FROM marketplace_listings WHERE " + column + " LIKE ? ORDER BY listing_id DESC LIMIT 20"))
		{
			ps.setString(1, "%" + name.replace("%", "").replace("_", "") + "%");
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					html.append("<tr><td><a action=\"bypass -h admin_marketplace info ").append(rs.getInt("listing_id")).append("\">").append(rs.getInt("listing_id")).append("</a></td>");
					html.append("<td>").append(rs.getString("status")).append("</td><td>").append(escape(rs.getString("item_name"))).append("</td><td>").append(format(rs.getLong("price"))).append("</td></tr>");
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace admin search failed.", e);
			html.append("<tr><td colspan=4><font color=\"FF6666\">Search failed.</font></td></tr>");
		}
		html.append("</table>");
		end(activeChar, html);
	}
	
	private StringBuilder base(String title)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html><body><center>");
		html.append("<table width=270 bgcolor=\"444444\"><tr>");
		html.append("<td><button value=\"Main\" action=\"bypass admin_admin\" width=65 height=21 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\"></td>");
		html.append("<td><button value=\"Market\" action=\"bypass -h admin_marketplace\" width=65 height=21 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\"></td>");
		html.append("</tr></table><br>");
		html.append("<font color=\"LEVEL\">").append(title).append("</font><br1>");
		return html;
	}
	
	private void end(Player activeChar, StringBuilder html)
	{
		html.append("</center></body></html>");
		final NpcHtmlMessage packet = new NpcHtmlMessage();
		packet.setHtml(html.toString());
		activeChar.sendPacket(packet);
	}
	
	private void row(StringBuilder html, String key, String value)
	{
		html.append("<tr><td width=110><font color=\"B09878\">").append(key).append("</font></td><td width=150>").append(escape(value)).append("</td></tr>");
	}
	
	private String escape(String value)
	{
		if (value == null)
		{
			return "";
		}
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
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
	
	private String format(long value)
	{
		return NUMBER.format(value);
	}
}
