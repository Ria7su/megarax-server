/*
 * Megarax Skin Coin Handler
 * Item ID: 6673 (Mega Coin)
 * 
 * Ubicación: game/data/scripts/handlers/itemhandlers/SkinCoinHandler.java
 */
package handlers.itemhandlers;

import org.l2jmobius.gameserver.handler.IItemHandler;
import org.l2jmobius.gameserver.model.actor.Playable;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.util.HtmlUtil;

public class SkinCoinHandler implements IItemHandler
{
	private static final int MEGA_COIN_ID = 6673;
	private static final int SKIN_COST = 10;
	
	@Override
	public boolean onItemUse(Playable playable, Item item, boolean forceUse)
	{
		if (!playable.isPlayer())
		{
			return false;
		}
		
		if (item.getId() != MEGA_COIN_ID)
		{
			return false;
		}
		
		final Player player = playable.asPlayer();
		final long coinCount = player.getInventory().getInventoryItemCount(MEGA_COIN_ID, -1);
		
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Skin Shop</title><body><center>");
		html.append("<button value=\"Megarax Skin Shop\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\"><tr>");
		html.append("<td width=36 align=center><img src=\"RaccoonCustom04.valkyrie_chest_change_0\" width=32 height=32></td>");
		html.append("<td width=240><font color=\"0099FF\">Visual Skin Collection</font><br1>");
		html.append("<font color=\"ae9977\">Mega Coin: <font color=\"LEVEL\">" + coinCount + "</font></font></td>");
		html.append("</tr></table><br>");
		
		html.append("<table width=292 cellspacing=2><tr>");
		html.append("<td align=center><button value=\"Armor Skins\" action=\"bypass -h Quest SkinCoin armor\" width=140 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td>");
		html.append("<td align=center><button value=\"Accessories\" action=\"bypass -h Quest SkinCoin accessories\" width=140 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td>");
		html.append("</tr></table><br>");
		
		html.append("<button value=\"How it works\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\"><tr><td align=center>");
		html.append("<font color=\"ae9977\">Spend " + SKIN_COST + " Mega Coin to unlock a skin permanently.</font><br1>");
		html.append("<font color=\"ae9977\">Apply it anytime from the Transmog NPC.</font>");
		html.append("</td></tr></table>");
		html.append("</center></body></html>");
		
		HtmlUtil.sendHtml(player, html.toString());
		
		return true;
	}
}
