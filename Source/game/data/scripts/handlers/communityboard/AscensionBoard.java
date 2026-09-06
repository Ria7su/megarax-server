/*
 * Megarax Ascension Community Board.
 */
package handlers.communityboard;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.handler.IParseBoardHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.instance.Item;

public class AscensionBoard implements IParseBoardHandler
{
	private static final Logger LOGGER = Logger.getLogger(AscensionBoard.class.getName());
	private static final int BOARD_WIDTH = 610;
	private static final String SMALL_BUTTON_BACK = "Galaxy.105_OVER";
	private static final String SMALL_BUTTON_FORE = "Galaxy.105";
	private static final String BIG_BUTTON_BACK = "Galaxy.105_OVER";
	private static final String BIG_BUTTON_FORE = "Galaxy.105";
	
	@Override
	public String[] getCommandList()
	{
		return new String[]
		{
			"_bbsascension"
		};
	}
	
	@Override
	public boolean onCommand(String command, Player player)
	{
		if ((player == null) || (command == null))
		{
			return false;
		}
		
		if (command.equals("_bbsascension") || command.equals("_bbsascension:list"))
		{
			showList(player, "all");
		}
		else if (command.startsWith("_bbsascension:list:"))
		{
			showList(player, command.substring("_bbsascension:list:".length()));
		}
		else if (command.startsWith("_bbsascension:select:"))
		{
			showSelected(player, parseObjectId(command, "_bbsascension:select:"));
		}
		else if (command.startsWith("_bbsascension:confirm:"))
		{
			confirm(player, parseObjectId(command, "_bbsascension:confirm:"));
		}
		else
		{
			showList(player, "all");
		}
		return false;
	}
	
	private void showList(Player player, String filter)
	{
		final String currentFilter = normalizeFilter(filter);
		final List<Item> allItems = getCandidates(player, "all");
		final List<Item> items = getCandidates(player, currentFilter);
		final StringBuilder html = startPage("Megarax Ascension", "Choose equipped gear and prepare upgrades.");
		
		html.append("<table width=").append(BOARD_WIDTH).append(" bgcolor=000000><tr>");
		html.append("<td width=250><font color=LEVEL>Equipped Ascension Items</font><br1><font color=9f8f70>Only equipped weapons, armor and jewels are listed.</font></td>");
		html.append("<td width=90 align=center><font color=D8B66A>").append(allItems.size()).append("</font><br1><font color=777777>Ready</font></td>");
		html.append("<td width=90 align=center><font color=D8B66A>").append(countWeapons(allItems)).append("</font><br1><font color=777777>Weapons</font></td>");
		html.append("<td width=90 align=center><font color=D8B66A>").append(countArmor(allItems)).append("</font><br1><font color=777777>Armor</font></td>");
		html.append("<td width=90 align=center><font color=D8B66A>").append(countJewels(allItems)).append("</font><br1><font color=777777>Jewels</font></td>");
		html.append("</tr></table>");
		html.append("<img src=\"L2UI.Squaregray\" width=").append(BOARD_WIDTH).append(" height=1><br1>");
		
		html.append("<table width=").append(BOARD_WIDTH).append(" bgcolor=000000><tr>");
		html.append("<td width=130><font color=ae9977>Filter: </font><font color=D8B66A>").append(filterLabel(currentFilter)).append("</font></td>");
		filterButton(html, currentFilter, "all", "All");
		filterButton(html, currentFilter, "weapon", "Weapons");
		filterButton(html, currentFilter, "armor", "Armor");
		filterButton(html, currentFilter, "jewel", "Jewels");
		html.append("</tr></table><br1>");
		
		html.append("<table width=").append(BOARD_WIDTH).append(" bgcolor=000000><tr>");
		html.append("<td width=44></td>");
		html.append("<td width=286><font color=ae9977>Item</font></td>");
		html.append("<td width=70 align=center><font color=ae9977>Enchant</font></td>");
		html.append("<td width=90 align=center><font color=ae9977>Status</font></td>");
		html.append("<td width=120 align=center><font color=ae9977>Action</font></td>");
		html.append("</tr></table>");
		
		if (items.isEmpty())
		{
			html.append("<br><table width=").append(BOARD_WIDTH).append(" bgcolor=000000><tr>");
			html.append("<td align=center height=72><font color=ae9977>").append(emptyListMessage(currentFilter)).append("</font></td>");
			html.append("</tr></table>");
		}
		else
		{
			for (Item item : items)
			{
				html.append("<table width=").append(BOARD_WIDTH).append(" bgcolor=000000><tr>");
				html.append("<td width=44 align=center><img src=\"").append(getIcon(item)).append("\" width=32 height=32></td>");
				html.append("<td width=286><font color=LEVEL>").append(html(item.getName())).append("</font><br1><font color=7f7566>").append(getTypeLabel(item)).append(" / Obj ").append(item.getObjectId()).append("</font></td>");
				html.append("<td width=70 align=center><font color=D8B66A>+").append(item.getEnchantLevel()).append("</font></td>");
				html.append("<td width=90 align=center><font color=9ACD72>Ready</font></td>");
				html.append("<td width=120 align=center><button value=\"Select\" action=\"bypass _bbsascension:select:").append(item.getObjectId()).append("\" width=96 height=24 back=\"").append(SMALL_BUTTON_BACK).append("\" fore=\"").append(SMALL_BUTTON_FORE).append("\"></td>");
				html.append("</tr></table><img src=\"L2UI.Squaregray\" width=").append(BOARD_WIDTH).append(" height=1>");
			}
		}
		
		html.append("<br1><table width=").append(BOARD_WIDTH).append(" bgcolor=000000><tr><td align=center><font color=696969>Upgrade rules are validated by the server before any item changes.</font></td></tr></table>");
		endPage(html);
		CommunityBoardHandler.separateAndSend(html.toString(), player);
	}
	
	private void showSelected(Player player, int objectId)
	{
		final Item item = getOwnedItem(player, objectId);
		if (!isCandidate(item))
		{
			player.sendMessage("Ascension: invalid item.");
			showList(player, "all");
			return;
		}
		
		final StringBuilder html = startPage("Upgrade Item", "Review the selected item before confirmation.");
		html.append("<table width=").append(BOARD_WIDTH).append(" bgcolor=000000><tr>");
		html.append("<td width=44 align=center><img src=\"").append(getIcon(item)).append("\" width=32 height=32></td>");
		html.append("<td width=306><font color=LEVEL>").append(html(item.getName())).append("</font><br1><font color=7f7566>").append(getTypeLabel(item)).append(" / Object ").append(item.getObjectId()).append("</font></td>");
		html.append("<td width=90 align=center><font color=D8B66A>+").append(item.getEnchantLevel()).append("</font><br1><font color=777777>Enchant</font></td>");
		html.append("<td width=170 align=center><font color=9ACD72>Ready</font><br1><font color=777777>Server checked</font></td>");
		html.append("</tr></table><img src=\"L2UI.Squaregray\" width=").append(BOARD_WIDTH).append(" height=1><br1>");
		
		html.append("<table width=").append(BOARD_WIDTH).append(" bgcolor=000000>");
		html.append("<tr><td width=160><font color=ae9977>Validation</font></td><td width=450><font color=9ACD72>Server side checks enabled</font></td></tr>");
		html.append("<tr><td width=160><font color=ae9977>Recipe</font></td><td width=450><font color=D8B66A>Waiting for ascension recipe configuration</font></td></tr>");
		html.append("<tr><td width=160><font color=ae9977>Safety</font></td><td width=450><font color=777777>No item changes happen until confirmation succeeds.</font></td></tr>");
		html.append("</table><br>");
		
		html.append("<table width=").append(BOARD_WIDTH).append("><tr>");
		html.append("<td width=300 align=right><button value=\"Confirm\" action=\"bypass _bbsascension:confirm:").append(item.getObjectId()).append("\" width=132 height=24 back=\"").append(BIG_BUTTON_BACK).append("\" fore=\"").append(BIG_BUTTON_FORE).append("\"></td>");
		html.append("<td width=10></td>");
		html.append("<td width=300 align=left><button value=\"Back\" action=\"bypass _bbsascension:list:all\" width=132 height=24 back=\"").append(BIG_BUTTON_BACK).append("\" fore=\"").append(BIG_BUTTON_FORE).append("\"></td>");
		html.append("</tr></table>");
		endPage(html);
		CommunityBoardHandler.separateAndSend(html.toString(), player);
	}
	
	private void confirm(Player player, int objectId)
	{
		final Item item = getOwnedItem(player, objectId);
		if (!isCandidate(item))
		{
			player.sendMessage("Ascension: invalid item.");
			showList(player, "all");
			return;
		}
		
		LOGGER.warning("Megarax Ascension requested by " + player.getName() + " for item " + item.getId() + " objectId " + item.getObjectId() + ", but no ascension recipe is configured.");
		player.sendMessage("Ascension: no upgrade recipe is configured for this item yet.");
		showSelected(player, objectId);
	}
	
	private Item getOwnedItem(Player player, int objectId)
	{
		return objectId > 0 ? player.getInventory().getItemByObjectId(objectId) : null;
	}
	
	private boolean isCandidate(Item item)
	{
		return (item != null) && (item.isWeapon() || item.isArmor());
	}
	
	private List<Item> getCandidates(Player player, String filter)
	{
		final String currentFilter = normalizeFilter(filter);
		final List<Item> items = new ArrayList<>();
		for (Item item : player.getInventory().getPaperdollItems())
		{
			if (isCandidate(item) && matchesFilter(item, currentFilter))
			{
				items.add(item);
			}
		}
		return items;
	}
	
	private boolean matchesFilter(Item item, String filter)
	{
		if ("weapon".equals(filter))
		{
			return item.isWeapon();
		}
		if ("armor".equals(filter))
		{
			return !item.isWeapon() && (item.getTemplate().getType2() == 1);
		}
		if ("jewel".equals(filter))
		{
			return !item.isWeapon() && (item.getTemplate().getType2() == 2);
		}
		return true;
	}
	
	private int countWeapons(List<Item> items)
	{
		int count = 0;
		for (Item item : items)
		{
			if (item.isWeapon())
			{
				count++;
			}
		}
		return count;
	}
	
	private int countArmor(List<Item> items)
	{
		int count = 0;
		for (Item item : items)
		{
			if (!item.isWeapon() && (item.getTemplate().getType2() == 1))
			{
				count++;
			}
		}
		return count;
	}
	
	private int countJewels(List<Item> items)
	{
		int count = 0;
		for (Item item : items)
		{
			if (!item.isWeapon() && (item.getTemplate().getType2() == 2))
			{
				count++;
			}
		}
		return count;
	}
	
	private String getTypeLabel(Item item)
	{
		if (item.isWeapon())
		{
			return "Weapon";
		}
		if (item.getTemplate().getType2() == 1)
		{
			return "Armor";
		}
		if (item.getTemplate().getType2() == 2)
		{
			return "Jewel";
		}
		return "Gear";
	}
	
	private String getIcon(Item item)
	{
		final String icon = item.getTemplate().getIcon();
		return ((icon == null) || icon.isEmpty()) ? "icon.NOIMAGE" : icon;
	}
	
	private String normalizeFilter(String filter)
	{
		if ("weapon".equals(filter) || "armor".equals(filter) || "jewel".equals(filter))
		{
			return filter;
		}
		return "all";
	}
	
	private String filterLabel(String filter)
	{
		if ("weapon".equals(filter))
		{
			return "Weapons";
		}
		if ("armor".equals(filter))
		{
			return "Armor";
		}
		if ("jewel".equals(filter))
		{
			return "Jewels";
		}
		return "All";
	}
	
	private String emptyListMessage(String filter)
	{
		if ("weapon".equals(filter))
		{
			return "No equipped weapon is available for ascension.";
		}
		if ("armor".equals(filter))
		{
			return "No equipped armor is available for ascension.";
		}
		if ("jewel".equals(filter))
		{
			return "No equipped jewel is available for ascension.";
		}
		return "No equipped weapon, armor or jewel is available for ascension.";
	}
	
	private void filterButton(StringBuilder html, String currentFilter, String filter, String label)
	{
		final String value = filter.equals(currentFilter) ? ("* " + label) : label;
		html.append("<td width=120 align=center><button value=\"").append(value).append("\" action=\"bypass _bbsascension:list:").append(filter).append("\" width=106 height=24 back=\"").append(SMALL_BUTTON_BACK).append("\" fore=\"").append(SMALL_BUTTON_FORE).append("\"></td>");
	}
	
	private String html(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
	
	private int parseObjectId(String command, String prefix)
	{
		try
		{
			return Integer.parseInt(command.substring(prefix.length()));
		}
		catch (RuntimeException e)
		{
			return 0;
		}
	}
	
	private StringBuilder startPage(String title, String subtitle)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html><body bgcolor=000000><br><center>");
		html.append("<table width=").append(BOARD_WIDTH).append(" height=448 bgcolor=000000><tr><td valign=top align=center>");
		html.append("<table width=").append(BOARD_WIDTH).append(" bgcolor=000000><tr>");
		html.append("<td width=390><font color=LEVEL>").append(title).append("</font><br1><font color=ae9977>").append(subtitle).append("</font></td>");
		html.append("<td width=110 align=center><button value=\"Item List\" action=\"bypass _bbsascension:list:all\" width=100 height=24 back=\"").append(SMALL_BUTTON_BACK).append("\" fore=\"").append(SMALL_BUTTON_FORE).append("\"></td>");
		html.append("<td width=110 align=center><button value=\"Home\" action=\"bypass _bbstop\" width=100 height=24 back=\"").append(SMALL_BUTTON_BACK).append("\" fore=\"").append(SMALL_BUTTON_FORE).append("\"></td>");
		html.append("</tr></table><img src=\"L2UI.Squaregray\" width=").append(BOARD_WIDTH).append(" height=1><br1>");
		return html;
	}
	
	private void endPage(StringBuilder html)
	{
		html.append("</td></tr></table></center></body></html>");
	}
}
