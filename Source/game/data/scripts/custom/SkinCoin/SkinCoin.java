/*
 * Megarax Skin Coin System
 * Ubicación: game/data/scripts/custom/SkinCoin/SkinCoin.java
 */
package custom.SkinCoin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.network.enums.HtmlActionScope;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.util.HtmlUtil;

import ai.AbstractNpcAI;
import custom.Transmog.Transmog;

public class SkinCoin extends AbstractNpcAI
{
	private static final int SERVICES_NPC = 9911;
	private static final int MEGA_COIN_ID = 6673;
	private static final int SKIN_COST = 10;
	private static final int PAGE_SIZE = 6;
	
	private static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS character_transmogs (owner VARCHAR(45) NOT NULL, itemId INT UNSIGNED NOT NULL DEFAULT 0, PRIMARY KEY (owner, itemId)) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String SAVE_SQL  = "REPLACE INTO character_transmogs (owner, itemId) VALUES (?, ?)";
	private static final String CHECK_SQL = "SELECT itemId FROM character_transmogs WHERE owner=? AND itemId=?";
	
	private static final int[][] ARMOR_SKINS =
	{
		{ 9804,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9810,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9812,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9813,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9814,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9815,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9816,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9822,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9830,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9833,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9834,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9851,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9852,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9854,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9856,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9858,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9860,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9862,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9863,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9864,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9865,  ItemTemplate.SLOT_FULL_ARMOR },
		{ 9866,  ItemTemplate.SLOT_FULL_ARMOR },
	};
	
	private static final int[][] ACCESSORY_SKINS =
	{
		{ 9831, ItemTemplate.SLOT_HAIR },
		{ 9832, ItemTemplate.SLOT_HAIR },
		{ 9835, ItemTemplate.SLOT_HAIR },
		{ 9817, ItemTemplate.SLOT_HAIR },
		{ 9818, ItemTemplate.SLOT_HAIR },
		{ 9819, ItemTemplate.SLOT_HAIR },
		{ 9820, ItemTemplate.SLOT_HAIR },
		{ 9821, ItemTemplate.SLOT_HAIR },
		{ 9823, ItemTemplate.SLOT_HAIR },
		{ 9853, ItemTemplate.SLOT_HAIR },
		{ 9855, ItemTemplate.SLOT_HAIR },
		{ 9857, ItemTemplate.SLOT_HAIRALL },
		{ 9859, ItemTemplate.SLOT_HAIRALL },
		{ 9861, ItemTemplate.SLOT_HAIRALL },
	};
	
	private SkinCoin()
	{
		initTable();
		addStartNpc(SERVICES_NPC);
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		if (player == null)
		{
			return null;
		}
		
		switch (event)
		{
			case "main":
			{
				showMainMenu(player, npc);
				break;
			}
			case "armor":
			{
				showArmorMenu(player, npc);
				break;
			}
			case "accessories":
			{
				showAccessoryMenu(player, npc);
				break;
			}
			default:
			{
				if (event.startsWith("armor_page_"))
				{
					showArmorMenu(player, npc, parsePage(event.substring(11)));
				}
				else if (event.startsWith("accessories_page_"))
				{
					showAccessoryMenu(player, npc, parsePage(event.substring(17)));
				}
				else if (event.startsWith("buy!"))
				{
					try
					{
						final int itemId = Integer.parseInt(event.split("!")[1]);
						unlockSkin(player, itemId);
						if (isArmorSkin(itemId))
						{
							showArmorMenu(player);
						}
						else
						{
							showAccessoryMenu(player);
						}
					}
					catch (RuntimeException e)
					{
						logInvalidEvent(player, event, e);
						player.sendMessage("Invalid skin selection.");
						showMainMenu(player);
					}
				}
				else if (event.startsWith("buy_"))
				{
					try
					{
						final String[] parts = event.split("_");
						final int itemId = Integer.parseInt(parts[1]);
						unlockSkin(player, itemId);
						showSkinPage(player, npc, itemId, parts);
					}
					catch (RuntimeException e)
					{
						logInvalidEvent(player, event, e);
						player.sendMessage("Invalid skin selection.");
						showMainMenu(player, npc);
					}
				}
				else if (event.startsWith("preview!"))
				{
					try
					{
						final int itemId = Integer.parseInt(event.split("!")[1]);
						previewSkin(player, itemId);
						if (isArmorSkin(itemId))
						{
							showArmorMenu(player);
						}
						else
						{
							showAccessoryMenu(player);
						}
					}
					catch (RuntimeException e)
					{
						logInvalidEvent(player, event, e);
						player.sendMessage("Invalid skin selection.");
						showMainMenu(player);
					}
				}
				else if (event.startsWith("preview_"))
				{
					try
					{
						final String[] parts = event.split("_");
						final int itemId = Integer.parseInt(parts[1]);
						previewSkin(player, itemId);
						showSkinPage(player, npc, itemId, parts);
					}
					catch (RuntimeException e)
					{
						logInvalidEvent(player, event, e);
						player.sendMessage("Invalid skin selection.");
						showMainMenu(player, npc);
					}
				}
				else if (event.startsWith("apply!"))
				{
					try
					{
						final int itemId = Integer.parseInt(event.split("!")[1]);
						applySkin(player, itemId);
						if (isArmorSkin(itemId))
						{
							showArmorMenu(player);
						}
						else
						{
							showAccessoryMenu(player);
						}
					}
					catch (RuntimeException e)
					{
						logInvalidEvent(player, event, e);
						player.sendMessage("Invalid skin selection.");
						showMainMenu(player);
					}
				}
				else if (event.startsWith("apply_"))
				{
					try
					{
						final String[] parts = event.split("_");
						final int itemId = Integer.parseInt(parts[1]);
						applySkin(player, itemId);
						showSkinPage(player, npc, itemId, parts);
					}
					catch (RuntimeException e)
					{
						logInvalidEvent(player, event, e);
						player.sendMessage("Invalid skin selection.");
						showMainMenu(player, npc);
					}
				}
				else if (event.equals("remove_preview"))
				{
					Transmog.removePreview(player);
					showMainMenu(player, npc);
				}
				else if (event.equals("remove_skin"))
				{
					if (Transmog.removeBodySkin(player))
					{
						player.sendMessage("Visual skin removed.");
					}
					else
					{
						player.sendMessage("You do not have an active visual skin.");
					}
					showArmorMenu(player, npc);
				}
				else if (event.equals("remove_hat"))
				{
					if (Transmog.removeHatSkin(player))
					{
						player.sendMessage("Visual hat removed.");
					}
					else
					{
						player.sendMessage("You do not have an active visual hat.");
					}
					showAccessoryMenu(player, npc);
				}
				break;
			}
		}
		
		return null;
	}
	
	// Envía HTML registrando el bypass cache en scope NPC_HTML con npcObjId=0
	private void sendHtml(Player player, Npc npc, String html)
	{
		final int objectId = npc == null ? 0 : npc.getObjectId();
		player.clearHtmlActions(HtmlActionScope.NPC_HTML);
		HtmlUtil.buildHtmlActionCache(player, HtmlActionScope.NPC_HTML, objectId, html);
		final NpcHtmlMessage msg = new NpcHtmlMessage(objectId);
		msg.setHtml(html);
		player.sendPacket(msg);
	}
	
	private void showMainMenu(Player player)
	{
		showMainMenu(player, null);
	}
	
	private void showMainMenu(Player player, Npc npc)
	{
		final int coinCount = (int) player.getInventory().getInventoryItemCount(MEGA_COIN_ID, -1);
		
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Skin Shop</title><body><center>");
		html.append("<button value=\"Megarax Skin Shop\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\"><tr>");
		html.append("<td width=36 align=center><img src=\"RaccoonCustom04.valkyrie_chest_change_0\" width=32 height=32></td>");
		html.append("<td width=240><font color=\"0099FF\">Visual Skin Collection</font><br1>");
		html.append("<font color=\"ae9977\">Mega Coin: <font color=\"LEVEL\">" + coinCount + "</font></font></td>");
		html.append("</tr></table><br>");
		
		html.append("<table width=292 cellspacing=2><tr>");
		html.append("<td align=center><button value=\"Skins\" action=\"bypass -h Quest SkinCoin armor\" width=60 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td>");
		html.append("<td align=center><button value=\"Hat\" action=\"bypass -h Quest SkinCoin accessories\" width=60 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td>");
		html.append("</tr></table><br>");
		
		html.append("<button value=\"How it works\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\"><tr><td align=center>");
		html.append("<font color=\"ae9977\">Preview skins for free before buying.</font><br1>");
		html.append("<font color=\"ae9977\">Each permanent unlock costs 10 Mega Coin.</font>");
		html.append("</td></tr></table>");
		html.append("<br><button value=\"Remove\" action=\"bypass -h Quest SkinCoin remove_preview\" width=60 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		html.append("</center></body></html>");
		
		sendHtml(player, npc, html.toString());
	}
	
	private void showArmorMenu(Player player)
	{
		showArmorMenu(player, null);
	}
	
	private void showArmorMenu(Player player, Npc npc)
	{
		showArmorMenu(player, npc, 0);
	}
	
	private void showArmorMenu(Player player, Npc npc, int page)
	{
		final int coinCount = (int) player.getInventory().getInventoryItemCount(MEGA_COIN_ID, -1);
		final int currentPage = normalizePage(page, ARMOR_SKINS.length);
		final int start = currentPage * PAGE_SIZE;
		final int end = Math.min(start + PAGE_SIZE, ARMOR_SKINS.length);
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Skins</title><body><center>");
		html.append("<button value=\"Skins\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\"><tr><td align=center>");
		html.append("<font color=\"ae9977\">Mega Coin: <font color=\"LEVEL\">" + coinCount + "</font> &nbsp;|&nbsp; Cost: 10 per skin</font>");
		html.append("</td></tr></table><br>");
		
		for (int i = start; i < end; i++)
		{
			appendSkinRow(html, player, ARMOR_SKINS[i][0], coinCount, "armor", currentPage);
		}
		
		appendPageControls(html, "armor", currentPage, ARMOR_SKINS.length);
		html.append("<br><table width=292><tr>");
		html.append("<td align=center><button value=\"Remove Skin\" action=\"bypass -h Quest SkinCoin remove_skin\" width=90 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td>");
		html.append("<td align=center><button value=\"Back\" action=\"bypass -h Quest SkinCoin main\" width=60 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td>");
		html.append("</tr></table>");
		html.append("</center></body></html>");
		
		sendHtml(player, npc, html.toString());
	}
	
	private void showAccessoryMenu(Player player)
	{
		showAccessoryMenu(player, null);
	}
	
	private void showAccessoryMenu(Player player, Npc npc)
	{
		showAccessoryMenu(player, npc, 0);
	}
	
	private void showAccessoryMenu(Player player, Npc npc, int page)
	{
		final int coinCount = (int) player.getInventory().getInventoryItemCount(MEGA_COIN_ID, -1);
		final int currentPage = normalizePage(page, ACCESSORY_SKINS.length);
		final int start = currentPage * PAGE_SIZE;
		final int end = Math.min(start + PAGE_SIZE, ACCESSORY_SKINS.length);
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Hat</title><body><center>");
		html.append("<button value=\"Hat\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\"><tr><td align=center>");
		html.append("<font color=\"ae9977\">Mega Coin: <font color=\"LEVEL\">" + coinCount + "</font> &nbsp;|&nbsp; Cost: 10 per skin</font>");
		html.append("</td></tr></table><br>");
		
		for (int i = start; i < end; i++)
		{
			appendSkinRow(html, player, ACCESSORY_SKINS[i][0], coinCount, "accessories", currentPage);
		}
		
		appendPageControls(html, "accessories", currentPage, ACCESSORY_SKINS.length);
		html.append("<br><table width=292><tr>");
		html.append("<td align=center><button value=\"Remove Hat\" action=\"bypass -h Quest SkinCoin remove_hat\" width=90 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td>");
		html.append("<td align=center><button value=\"Back\" action=\"bypass -h Quest SkinCoin main\" width=60 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td>");
		html.append("</tr></table>");
		html.append("</center></body></html>");
		
		sendHtml(player, npc, html.toString());
	}
	
	private void appendSkinRow(StringBuilder html, Player player, int itemId, int coinCount, String category, int page)
	{
		final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
		if (template == null)
		{
			return;
		}
		
		final boolean owned = isSkinOwned(player, itemId);
		final String icon = template.getIcon() != null ? template.getIcon() : "icon.etc_adena_i00";
		
		html.append("<table width=292 bgcolor=\"000000\"><tr>");
		html.append("<td width=36 align=center><img src=\"" + icon + "\" width=32 height=32></td>");
		html.append("<td width=120><font color=\"LEVEL\">" + getSkinDisplayName(itemId, template) + "</font><br1>");
		if (owned)
		{
			html.append("<font color=\"00FF00\">Unlocked</font>");
		}
		else
		{
			html.append("<font color=\"B09878\">Cost: 10 Mega Coin</font>");
		}
		html.append("</td>");
		html.append("<td width=58 align=center><button value=\"Preview\" action=\"bypass -h Quest SkinCoin preview_" + itemId + "_" + category + "_" + page + "\" width=56 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td>");
		html.append("<td width=72 align=center>");
		
		if (owned)
		{
			html.append("<button value=\"Apply\" action=\"bypass -h Quest SkinCoin apply_" + itemId + "_" + category + "_" + page + "\" width=68 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		}
		else
		{
			html.append("<button value=\"Buy\" action=\"bypass -h Quest SkinCoin buy_" + itemId + "_" + category + "_" + page + "\" width=68 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		}
		html.append("</td></tr></table><br1>");
	}
	
	private void appendPageControls(StringBuilder html, String category, int page, int totalItems)
	{
		final int maxPage = Math.max(0, (totalItems - 1) / PAGE_SIZE);
		if (maxPage <= 0)
		{
			return;
		}
		
		final String eventPrefix = "armor".equals(category) ? "armor_page_" : "accessories_page_";
		html.append("<table width=292><tr>");
		if (page > 0)
		{
			html.append("<td align=center><button value=\"Prev\" action=\"bypass -h Quest SkinCoin " + eventPrefix + (page - 1) + "\" width=60 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td>");
		}
		else
		{
			html.append("<td align=center></td>");
		}
		html.append("<td align=center><font color=\"ae9977\">Page " + (page + 1) + "/" + (maxPage + 1) + "</font></td>");
		if (page < maxPage)
		{
			html.append("<td align=center><button value=\"Next\" action=\"bypass -h Quest SkinCoin " + eventPrefix + (page + 1) + "\" width=60 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td>");
		}
		else
		{
			html.append("<td align=center></td>");
		}
		html.append("</tr></table>");
	}
	
	private int normalizePage(int page, int totalItems)
	{
		final int maxPage = Math.max(0, (totalItems - 1) / PAGE_SIZE);
		return Math.max(0, Math.min(page, maxPage));
	}
	
	private int parsePage(String value)
	{
		try
		{
			return Math.max(0, Integer.parseInt(value));
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}
	
	private void showSkinPage(Player player, Npc npc, int itemId, String[] eventParts)
	{
		final String category = eventParts.length > 2 ? eventParts[2] : isArmorSkin(itemId) ? "armor" : "accessories";
		final int page = eventParts.length > 3 ? parsePage(eventParts[3]) : 0;
		if ("armor".equals(category))
		{
			showArmorMenu(player, npc, page);
		}
		else
		{
			showAccessoryMenu(player, npc, page);
		}
	}
	
	private void unlockSkin(Player player, int itemId)
	{
		if (!isShopSkin(itemId))
		{
			logVisualAction(player, "BUY", itemId, null, false, "Item is not configured as a shop skin");
			LOGGER.warning("SkinCoin invalid buy attempt from " + player.getName() + ": item " + itemId + " is not configured as a shop skin.");
			player.sendMessage("Invalid skin.");
			return;
		}
		
		if (player.getInventory().getInventoryItemCount(MEGA_COIN_ID, -1) < SKIN_COST)
		{
			logVisualAction(player, "BUY", itemId, ItemData.getInstance().getTemplate(itemId), false, "Not enough Mega Coin");
			player.sendMessage("You need 10 Mega Coin to buy this skin.");
			return;
		}
		
		final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
		if (template == null)
		{
			logVisualAction(player, "BUY", itemId, null, false, "ItemData not found");
			LOGGER.warning("SkinCoin invalid buy attempt from " + player.getName() + ": missing ItemTemplate for " + itemId + ".");
			player.sendMessage("Invalid skin.");
			return;
		}
		
		final int visualId = getVisualId(template);
		if (isSkinOwned(player, itemId))
		{
			logVisualAction(player, "BUY", itemId, template, false, "Skin already unlocked");
			player.sendMessage("You already own this skin.");
			return;
		}
		
		if (!player.destroyItemByItemId(ItemProcessType.FEE, MEGA_COIN_ID, SKIN_COST, player, true))
		{
			logVisualAction(player, "BUY", itemId, template, false, "Payment destroyItem failed");
			LOGGER.warning("SkinCoin payment failed for " + player.getName() + " buying skin " + itemId + ".");
			player.sendMessage("The payment could not be completed.");
			return;
		}
		
		if (!saveOwnedSkin(player, visualId))
		{
			player.addItem(ItemProcessType.REFUND, MEGA_COIN_ID, SKIN_COST, player, true);
			logVisualAction(player, "BUY", itemId, template, false, "Database save failed");
			player.sendMessage("Error saving skin. Contact an admin.");
			return;
		}
		
		Transmog.addToCollection(player, template);
		logVisualAction(player, "BUY", itemId, template, true, "Unlocked");
		
		if (Transmog.equipSkin(player, itemId))
		{
			player.sendMessage("Skin unlocked and equipped: " + template.getName() + ".");
		}
		else
		{
			player.sendMessage("Skin unlocked, but the visual could not be equipped. Check the server log.");
		}
	}
	
	private boolean isSkinOwned(Player player, int itemId)
	{
		final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
		if (template == null)
		{
			return false;
		}
		
		final int visualId = getVisualId(template);
		final String owner = getOwner(player);
		if (hasSavedSkin(owner, visualId) || hasSavedSkin(owner, itemId))
		{
			Transmog.addToCollection(player, template);
			if (itemId != visualId)
			{
				saveOwnedSkin(player, visualId);
			}
			return true;
		}
		
		final String legacyOwner = getLegacyOwner(player);
		if (!legacyOwner.equals(owner) && (hasSavedSkin(legacyOwner, visualId) || hasSavedSkin(legacyOwner, itemId)))
		{
			Transmog.addToCollection(player, template);
			saveOwnedSkin(player, visualId);
			return true;
		}
		
		final String legacyNameOwner = getLegacyNameOwner(player);
		if (!legacyNameOwner.equals(owner) && !legacyNameOwner.equals(legacyOwner) && (hasSavedSkin(legacyNameOwner, visualId) || hasSavedSkin(legacyNameOwner, itemId)))
		{
			Transmog.addToCollection(player, template);
			saveOwnedSkin(player, visualId);
			return true;
		}
		
		return false;
	}
	
	private boolean hasSavedSkin(String owner, int skinId)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(CHECK_SQL))
		{
			ps.setString(1, owner);
			ps.setInt(2, skinId);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next();
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("SkinCoin DB check error for owner " + owner + ", skin " + skinId + ": " + e.getMessage());
			return false;
		}
	}
	
	private boolean saveOwnedSkin(Player player, int visualId)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(SAVE_SQL))
		{
			ps.setString(1, getOwner(player));
			ps.setInt(2, visualId);
			ps.executeUpdate();
			return true;
		}
		catch (SQLException e)
		{
			LOGGER.warning("SkinCoin DB error: " + e.getMessage());
			return false;
		}
	}
	
	private void initTable()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(CREATE_TABLE_SQL))
		{
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("SkinCoin table init error: " + e.getMessage());
		}
	}
	
	private void previewSkin(Player player, int itemId)
	{
		if (!isShopSkin(itemId))
		{
			player.sendMessage("Invalid skin.");
			return;
		}
		
		final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
		if (template == null)
		{
			LOGGER.warning("SkinCoin invalid preview attempt from " + player.getName() + ": missing ItemTemplate for " + itemId + ".");
			player.sendMessage("Invalid skin.");
			return;
		}
		
		Transmog.previewSkin(player, itemId);
	}
	
	private void applySkin(Player player, int itemId)
	{
		if (!isShopSkin(itemId))
		{
			player.sendMessage("Invalid skin.");
			return;
		}
		
		if (!isSkinOwned(player, itemId))
		{
			player.sendMessage("You have not purchased this skin yet.");
			return;
		}
		
		final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
		if (template == null)
		{
			LOGGER.warning("SkinCoin invalid apply attempt from " + player.getName() + ": missing ItemTemplate for " + itemId + ".");
			player.sendMessage("Invalid skin.");
			return;
		}
		
		Transmog.addToCollection(player, template);
		if (Transmog.equipSkin(player, itemId))
		{
			player.sendMessage("Skin equipped: " + template.getName() + ".");
		}
	}
	
	private int getVisualId(ItemTemplate template)
	{
		final int displayId = template.getDisplayId();
		return displayId > 0 ? displayId : template.getId();
	}
	
	private void logInvalidEvent(Player player, String event, Exception e)
	{
		LOGGER.warning("SkinCoin invalid bypass from " + player.getName() + " [" + event + "]: " + e.getMessage());
	}
	
	private String getOwner(Player player)
	{
		return String.valueOf(player.getObjectId());
	}
	
	private String getLegacyOwner(Player player)
	{
		return player.getAccountName();
	}
	
	private String getLegacyNameOwner(Player player)
	{
		return player.getName();
	}
	
	private boolean isShopSkin(int itemId)
	{
		return isArmorSkin(itemId) || isAccessorySkin(itemId);
	}
	
	private boolean isArmorSkin(int itemId)
	{
		for (int[] skin : ARMOR_SKINS)
		{
			if (skin[0] == itemId)
			{
				return true;
			}
		}
		return false;
	}
	
	private boolean isAccessorySkin(int itemId)
	{
		for (int[] skin : ACCESSORY_SKINS)
		{
			if (skin[0] == itemId)
			{
				return true;
			}
		}
		return false;
	}
	
	private String getSkinDisplayName(int itemId, ItemTemplate template)
	{
		switch (itemId)
		{
			case 9830:
			{
				return "Costume Cowboy";
			}
			case 9831:
			{
				return "Helmet Cowboy";
			}
			case 9832:
			{
				return "Dark Helmet";
			}
			case 9833:
			{
				return "Dark Suit";
			}
			case 9834:
			{
				return "Costume Dragon Claw";
			}
			case 9835:
			{
				return "Helmet Dragon Claw";
			}
			case 9851:
			{
				return "Japan General";
			}
			case 9852:
			{
				return "Costume Kiborg";
			}
			case 9853:
			{
				return "Helmet Kiborg";
			}
			case 9854:
			{
				return "White Knight";
			}
			case 9855:
			{
				return "White Knight Suit";
			}
			default:
			{
				return template.getName();
			}
		}
	}
	
	private void logVisualAction(Player player, String action, int itemId, ItemTemplate template, boolean success, String reason)
	{
		final boolean hat = isAccessorySkin(itemId);
		final String tag = hat ? "[VISUAL_HAT]" : "[VISUAL_SKIN]";
		final String idLabel = hat ? "hatId" : "skinId";
		final StringBuilder sb = new StringBuilder();
		sb.append(tag);
		sb.append(" Player=").append(player == null ? "null" : player.getName());
		sb.append(" action=").append(action);
		sb.append(' ').append(idLabel).append('=').append(itemId);
		sb.append(" visualItemId=").append(getVisualId(template));
		sb.append(" result=").append(success ? "SUCCESS" : "FAILED");
		if ((reason != null) && !reason.isEmpty())
		{
			sb.append(" reason=").append(reason);
		}
		
		if (success)
		{
			LOGGER.info(sb.toString());
		}
		else
		{
			LOGGER.warning(sb.toString());
		}
	}
	
	public static void main(String[] args)
	{
		new SkinCoin();
	}
}
