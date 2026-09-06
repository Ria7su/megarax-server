/*
 * Copyright (c) 2013 L2jMobius
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package custom.Transmog;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.l2jmobius.Config;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.cache.HtmCache;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.model.actor.appearance.VisualArmorSkinData;
import org.l2jmobius.gameserver.model.actor.appearance.VisualArmorSkinData.VisualSet;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.Containers;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerItemEquip;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogin;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogout;
import org.l2jmobius.gameserver.model.events.holders.actor.player.inventory.OnPlayerItemAdd;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.item.EtcItem;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.network.enums.HtmlActionScope;
import org.l2jmobius.gameserver.network.serverpackets.CreatureSay;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.util.HtmlUtil;

import ai.AbstractNpcAI;

/**
 * @author Mobius
 */
public class Transmog extends AbstractNpcAI
{
	private static final int NPC = 900009;
	
	private static final int MEGA_COIN_ID = 6673;
	private static final int DRESS_ME_COST = 10;
	private static final int PREVIEW_DURATION = 7000;
	private static final int AGATHION_ANGER_ID = 9824;
	private static final int AGATHION_SORROW_ID = 9825;
	private static final int VISUAL_BODY_ANCHOR_SLOT = Inventory.PAPERDOLL_HAIR;
	private static final int[] VISUAL_HAT_SLOTS =
	{
		Inventory.PAPERDOLL_HAIR,
		Inventory.PAPERDOLL_HAIR2,
		Inventory.PAPERDOLL_FACE
	};
	private static final int[] VISUAL_BODY_CLEANUP_SLOTS =
	{
		VISUAL_BODY_ANCHOR_SLOT,
		Inventory.PAPERDOLL_HAIR2,
		Inventory.PAPERDOLL_FACE,
		Inventory.PAPERDOLL_HEAD,
		Inventory.PAPERDOLL_CHEST,
		Inventory.PAPERDOLL_LEGS,
		Inventory.PAPERDOLL_GLOVES,
		Inventory.PAPERDOLL_FEET
	};
	private static final int[] BODY_SKIN_TARGET_SLOTS =
	{
		Inventory.PAPERDOLL_HEAD,
		Inventory.PAPERDOLL_CHEST,
		Inventory.PAPERDOLL_LEGS,
		Inventory.PAPERDOLL_GLOVES,
		Inventory.PAPERDOLL_FEET
	};
	
	private static final Map<Integer, Map<Integer, Set<Integer>>> PLAYER_TRANSMOGS = new ConcurrentHashMap<>();
	private static final Map<Integer, Map<Integer, Integer>> DRESS_ME_PREVIEW_BACKUP = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> DRESS_ME_PREVIEW_HAT_BACKUP = new ConcurrentHashMap<>();
	private static final Map<Integer, BodySkinBackup> DRESS_ME_PREVIEW_BODY_BACKUP = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> DRESS_ME_PREVIEW_TOKEN = new ConcurrentHashMap<>();
	private static final String ACTIVE_HAT_SKIN_VARIABLE = "MegaraxActiveHatSkin";
	private static final String ACTIVE_BODY_SKIN_VARIABLE = "MegaraxActiveBodySkin";
	private static final DressMeSkin[] DRESS_ME_SKINS =
	{
		new DressMeSkin(9804, "Megarax DualTone Suit"),
		new DressMeSkin(9810, "Megarax Valkyrie Suit"),
		new DressMeSkin(9812, "Megarax Red Arrow"),
		new DressMeSkin(9813, "Megarax Ninja Suit"),
		new DressMeSkin(9814, "White Assassin"),
		new DressMeSkin(9815, "Dark Assassin"),
		new DressMeSkin(9816, "Golden Armour"),
		new DressMeSkin(9822, "Barbarian Costume"),
		new DressMeSkin(9830, "Costume Cowboy"),
		new DressMeSkin(9833, "Dark Suit"),
		new DressMeSkin(9834, "Costume Dragon Claw"),
		new DressMeSkin(9851, "Japan General"),
		new DressMeSkin(9852, "Costume Kiborg"),
		new DressMeSkin(9854, "White Knight"),
		new DressMeSkin(9856, "Death Knight Fire Skin"),
		new DressMeSkin(9858, "Death Knight Purple Skin"),
		new DressMeSkin(9860, "Death Knight Blue Skin"),
		new DressMeSkin(9862, "Megarax Ancient Antharas"),
		new DressMeSkin(9863, "Megarax Antharas Dragon"),
		new DressMeSkin(9864, "Megarax Fafurion Dragon"),
		new DressMeSkin(9865, "Megarax Classic Valakas"),
		new DressMeSkin(9866, "Megarax Crimson Dragon"),
		new DressMeSkin(9857, "Death Knight Fire Hat"),
		new DressMeSkin(9859, "Death Knight Purple Hat"),
		new DressMeSkin(9861, "Death Knight Blue Hat")
	};
	private static final int[] MEGARAX_SKIN_ITEM_IDS =
	{
		9804, 9810, 9812, 9813, 9814, 9815, 9816, 9817, 9818, 9819, 9820, 9821, 9822, 9823,
		9830, 9831, 9832, 9833, 9834, 9835, 9836, 9837, 9838, 9839, 9840, 9841, 9842, 9843, 9844, 9845, 9846, 9847, 9848, 9849, 9850, 9851, 9852, 9853, 9854, 9855,
		9856, 9857, 9858, 9859, 9860, 9861, 9862, 9863, 9864, 9865, 9866
	};
	private static final Set<Integer> DRESS_ME_SKIN_IDS = new HashSet<>();
	private static final Set<Integer> DRESS_ME_BODY_SKIN_IDS = new HashSet<>();
	private static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS character_transmogs (owner VARCHAR(45) NOT NULL, itemId INT UNSIGNED NOT NULL DEFAULT 0, PRIMARY KEY (owner, itemId)) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String LOAD_SQL = "SELECT itemId FROM character_transmogs WHERE owner=? ORDER BY itemId ASC;";
	private static final String SAVE_SQL = "REPLACE INTO character_transmogs (owner,itemId) VALUES (?,?)";
	
	private Transmog()
	{
		for (int skinId : MEGARAX_SKIN_ITEM_IDS)
		{
			final ItemTemplate template = ItemData.getInstance().getTemplate(skinId);
			if (template != null)
			{
				DRESS_ME_SKIN_IDS.add(template.getId());
				DRESS_ME_SKIN_IDS.add(getVisualId(template));
				if (!isHatBodyPart(template.getBodyPart()))
				{
					DRESS_ME_BODY_SKIN_IDS.add(template.getId());
					DRESS_ME_BODY_SKIN_IDS.add(getVisualId(template));
				}
			}
		}
		installMegaraxVisualSets();
		
		addStartNpc(NPC);
		addTalkId(NPC);
		addFirstTalkId(NPC);
		initTable();
		
		if (Config.ENABLE_TRANSMOG)
		{
			Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_ITEM_ADD, (OnPlayerItemAdd event) -> onPlayerItemAdd(event), this));
			Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_ITEM_EQUIP, (OnPlayerItemEquip event) -> onPlayerItemEquip(event), this));
			Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_LOGIN, (OnPlayerLogin event) -> onPlayerLogin(event), this));
			Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_LOGOUT, (OnPlayerLogout event) -> onPlayerLogout(event), this));
		}
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		if (event.equals("menu"))
		{
			return "900009-01.html";
		}
		else if (event.equals("dressme"))
		{
			showDressMe(player, npc);
		}
		else if (event.startsWith("dressPreview"))
		{
			final String[] split = event.split("!");
			if (split.length >= 2)
			{
				previewDressMe(player, Integer.parseInt(split[1]));
			}
			showDressMe(player, npc);
		}
		else if (event.startsWith("dressBuy"))
		{
			final String[] split = event.split("!");
			if (split.length >= 2)
			{
				buyDressMe(player, Integer.parseInt(split[1]));
			}
			showDressMe(player, npc);
		}
		else if (event.startsWith("dressEquip"))
		{
			final String[] split = event.split("!");
			if (split.length >= 2)
			{
				equipDressMe(player, Integer.parseInt(split[1]), false);
			}
			showDressMe(player, npc);
		}
		else if (event.equals("dressRemovePreview"))
		{
			removeDressMePreview(player);
			showDressMe(player, npc);
		}
		else if (event.startsWith("rem"))
		{
			final String[] split = event.split("!");
			final String slot = split[1];
			final String page = split[2];
			if (isValidSlot(slot))
			{
				final Item item = player.getInventory().unEquipItemInBodySlot(getBodypart(slot));
				if (item != null)
				{
					final int transmogId = item.getTransmogId();
					if (transmogId > 0)
					{
						if (Config.TRANSMOG_REMOVE_COST > 0)
						{
							if (player.getAdena() < Config.TRANSMOG_REMOVE_COST)
							{
								player.getInventory().equipItem(item);
								player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_ADENA);
								onEvent(slot + "!" + page, npc, player);
								return null;
							}
							
							player.reduceAdena(ItemProcessType.FEE, Config.TRANSMOG_REMOVE_COST, player, true);
						}
						
						item.removeTransmog();
						player.sendMessage("You have removed your " + ItemData.getInstance().getTemplate(transmogId).getName() + " transmog.");
					}
					
					player.getInventory().equipItem(item);
					refreshVisual(player);
				}
				else
				{
					player.sendMessage("No item equipped in this slot.");
				}
			}
			
			onEvent(slot + "!" + page, npc, player);
		}
		else if (event.startsWith("set"))
		{
			final String[] split = event.split("!");
			final String slot = split[1];
			final String page = split[2];
			final int transmogId = Integer.parseInt(split[3]);
			if (isValidSlot(slot))
			{
				final Integer bodypart = getBodypart(slot);
				final Item item = player.getInventory().unEquipItemInBodySlot(bodypart);
				if (item != null)
				{
					final ItemTemplate itemTemplate = ItemData.getInstance().getTemplate(transmogId);
					if (itemTemplate != null)
					{
						final Map<Integer, Set<Integer>> playerTransmogs = PLAYER_TRANSMOGS.getOrDefault(player.getObjectId(), Collections.emptyMap());
						final Set<Integer> itemIds = playerTransmogs.getOrDefault(bodypart, Collections.emptySet());
						if (itemIds.contains(transmogId))
						{
							if (Config.TRANSMOG_APPLY_COST > 0)
							{
								if (player.getAdena() < Config.TRANSMOG_APPLY_COST)
								{
									player.getInventory().equipItem(item);
									player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_ADENA);
									onEvent(slot + "!" + page, npc, player);
									return null;
								}
								
								player.reduceAdena(ItemProcessType.FEE, Config.TRANSMOG_APPLY_COST, player, true);
							}
							
							item.setTransmogId(transmogId);
							player.sendMessage("You have set your item transmog to " + itemTemplate.getName() + ".");
						}
					}
					
					player.getInventory().equipItem(item);
					refreshVisual(player);
				}
				else
				{
					player.sendMessage("No item equipped in this slot.");
				}
			}
			
			onEvent(slot + "!" + page, npc, player);
		}
		else
		{
			final String[] split = event.split("!");
			final String slot = split[0];
			final int page = split.length > 1 ? Integer.parseInt(split[1]) : 1;
			
			final Map<Integer, Set<Integer>> playerTransmogs = PLAYER_TRANSMOGS.getOrDefault(player.getObjectId(), Collections.emptyMap());
			final Set<Integer> itemIds = playerTransmogs.getOrDefault(getBodypart(slot), Collections.emptySet());
			if (itemIds.isEmpty())
			{
				return "900009-02.html";
			}
			
			int total = 0;
			int counter = 0;
			final int pages = (int) Math.ceil((double) itemIds.size() / 8);
			final int maxItem = page * 8;
			final int minItem = maxItem - 8;
			String content = HtmCache.getInstance().getHtm(player, "data/scripts/custom/Transmog/900009-03.html");
			for (Integer itemId : itemIds)
			{
				final ItemTemplate itemTemplate = ItemData.getInstance().getTemplate(itemId);
				if (itemTemplate != null)
				{
					total++;
					if ((total < minItem) || (total > maxItem))
					{
						continue;
					}
					
					counter++;
					final String itemName = itemTemplate.getName();
					content = content.replace("%" + counter + "%", "<center><img src=\"" + itemTemplate.getIcon() + "\" width=32 height=32><br><a action=\"bypass Quest Transmog set!" + slot + "!" + page + "!" + itemTemplate.getDisplayId() + "\">" + itemName + "</a></center>");
				}
			}
			
			for (int i = 1; i < 9; i++)
			{
				content = content.replace("%" + i + "%", "");
			}
			
			content = content.replace("%title%", "Transmog (" + page + " of " + pages + ")");
			content = content.replace("%previous%", "<a action=\"bypass Quest Transmog " + slot + "!" + Math.max(1, (page - 1)) + "\">Previous</a>");
			content = content.replace("%next%", "<a action=\"bypass Quest Transmog " + slot + "!" + Math.min(pages, (page + 1)) + "\">Next</a>");
			content = content.replace("%remove%", "<a action=\"bypass Quest Transmog rem!" + slot + "!" + page + "\">Remove Transmog</a>");
			
			HtmlUtil.sendCBHtml(player, content);
		}
		
		return super.onEvent(event, npc, player);
	}
	
	private void showDressMe(Player player, Npc npc)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Megarax Dress Me</title><body><center>");
		html.append("<button value=\"Megarax Dress Me\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\"><tr><td align=center>");
		html.append("<font color=\"ae9977\">Preview skins for free. Buy permanent skins with Mega Coin.</font><br1>");
		html.append("<font color=\"LEVEL\">Cost: 10 Mega Coin per skin</font>");
		html.append("</td></tr></table><br>");
		
		for (DressMeSkin skin : DRESS_ME_SKINS)
		{
			final ItemTemplate template = ItemData.getInstance().getTemplate(skin.displayItemId);
			if (template == null)
			{
				continue;
			}
			
			final int visualId = getVisualId(template);
			final boolean owned = ownsDressMeSkin(player, template);
			final String name = (skin.name != null) ? skin.name : template.getName();
			html.append("<table width=292 bgcolor=\"000000\"><tr>");
			html.append("<td width=36 align=center><img src=\"").append(template.getIcon()).append("\" width=32 height=32></td>");
			html.append("<td width=118><font color=\"LEVEL\">").append(name).append("</font><br1>");
			html.append(owned ? "<font color=\"00FF99\">Owned</font>" : "<font color=\"B09878\">10 Mega Coin</font>");
			html.append("</td>");
			html.append("<td width=62 align=center><button value=\"Preview\" action=\"bypass -h Quest Transmog dressPreview!").append(visualId).append("\" width=58 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td>");
			if (owned)
			{
				html.append("<td width=62 align=center><button value=\"Equip\" action=\"bypass -h Quest Transmog dressEquip!").append(visualId).append("\" width=58 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td>");
			}
			else
			{
				html.append("<td width=62 align=center><button value=\"Buy\" action=\"bypass -h Quest Transmog dressBuy!").append(visualId).append("\" width=58 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td>");
			}
			html.append("</tr></table><br>");
		}
		
		html.append("<table width=292><tr>");
		html.append("<td align=center><button value=\"Remove\" action=\"bypass -h Quest Transmog dressRemovePreview\" width=60 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td>");
		html.append("<td align=center><button value=\"Back\" action=\"bypass -h Quest Transmog menu\" width=60 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td>");
		html.append("</tr></table>");
		html.append("</center></body></html>");
		sendNpcHtml(player, npc, html.toString());
	}
	
	private void sendNpcHtml(Player player, Npc npc, String html)
	{
		final int objectId = npc == null ? 0 : npc.getObjectId();
		player.clearHtmlActions(HtmlActionScope.NPC_HTML);
		HtmlUtil.buildHtmlActionCache(player, HtmlActionScope.NPC_HTML, objectId, html);
		final NpcHtmlMessage msg = new NpcHtmlMessage(objectId);
		msg.setHtml(html);
		player.sendPacket(msg);
	}
	
	private static void previewDressMe(Player player, int skinId)
	{
		final DressMeSkin skin = resolveDressMeSkin(skinId);
		final ItemTemplate template = skin == null ? resolveMegaraxSkinTemplate(skinId) : ItemData.getInstance().getTemplate(skin.displayItemId);
		if (template == null)
		{
			logVisualAction(player, "PREVIEW", skinId, null, -1, false, "ItemData not found");
			player.sendMessage("Invalid skin.");
			return;
		}
		
		if (equipDressMe(player, skinId, true))
		{
			startPreviewTimer(player);
			player.sendMessage("Preview applied for 7 seconds. Buy the skin to keep it permanently.");
		}
	}
	
	private void buyDressMe(Player player, int skinId)
	{
		final DressMeSkin skin = resolveDressMeSkin(skinId);
		final ItemTemplate template = skin == null ? resolveMegaraxSkinTemplate(skinId) : ItemData.getInstance().getTemplate(skin.displayItemId);
		if (template == null)
		{
			logVisualAction(player, "BUY", skinId, null, -1, false, "ItemData not found");
			player.sendMessage("Invalid skin.");
			return;
		}
		
		if (ownsDressMeSkin(player, template))
		{
			equipDressMe(player, skinId, false);
			return;
		}
		
		final Item paymentItem = player.getInventory().getItemByItemId(MEGA_COIN_ID);
		if ((paymentItem == null) || (paymentItem.getCount() < DRESS_ME_COST))
		{
			logVisualAction(player, "BUY", skinId, template, -1, false, "Not enough Mega Coin");
			player.sendMessage("You need 10 Mega Coin to buy this skin.");
			return;
		}
		
		if (!player.destroyItem(ItemProcessType.FEE, paymentItem, DRESS_ME_COST, player, true))
		{
			logVisualAction(player, "BUY", skinId, template, -1, false, "Payment destroyItem failed");
			player.sendMessage("The payment could not be completed.");
			return;
		}
		
		if (!saveDressMeSkin(player, getVisualId(template)))
		{
			player.addItem(ItemProcessType.REFUND, MEGA_COIN_ID, DRESS_ME_COST, player, true);
			logVisualAction(player, "BUY", skinId, template, -1, false, "Database save failed");
			player.sendMessage("The skin could not be saved. Your Mega Coin has been refunded.");
			return;
		}
		
		addToCollection(player, template);
		logVisualAction(player, "BUY", skinId, template, -1, true, "Unlocked");
		if (equipDressMe(player, skinId, false))
		{
			player.sendMessage(template.getName() + " has been purchased and equipped.");
		}
		else
		{
			player.sendMessage(template.getName() + " has been purchased, but the visual could not be equipped. Check the server log.");
		}
	}
	
	private static boolean equipDressMe(Player player, int skinId, boolean preview)
	{
		final String action = preview ? "PREVIEW" : "APPLY";
		final ItemTemplate template = resolveMegaraxSkinTemplate(skinId);
		if (template == null)
		{
			logVisualAction(player, action, skinId, null, -1, false, "ItemData not found");
			player.sendMessage("Invalid skin.");
			return false;
		}
		
		if (!preview && !ownsDressMeSkin(player, template))
		{
			logVisualAction(player, action, skinId, template, -1, false, "Skin is not unlocked");
			player.sendMessage("You have not purchased this skin yet.");
			return false;
		}
		
		if (preview)
		{
			removeDressMePreview(player, false);
		}
		
		final boolean applied;
		logVisualDebug(player, action + "_REQUEST", template, -1);
		if (isHatBodyPart(template.getBodyPart()))
		{
			if (preview)
			{
				DRESS_ME_PREVIEW_HAT_BACKUP.putIfAbsent(player.getObjectId(), getActiveHatSkin(player));
			}
			else
			{
				DRESS_ME_PREVIEW_HAT_BACKUP.remove(player.getObjectId());
			}
			
			applied = applyVirtualHatSkin(player, template, !preview);
			if (!applied)
			{
				logVisualAction(player, action, skinId, template, -1, false, "No free visual hat slot");
				player.sendMessage("No free visual hat slot. Remove another hat or agathion first.");
			}
		}
		else
		{
			if (preview)
			{
				backupBodySkin(player);
			}
			else
			{
				DRESS_ME_PREVIEW_BODY_BACKUP.remove(player.getObjectId());
			}
			
			if (preview)
			{
				// Preview must also work for a new or naked character. Prefer the
				// player's real armor, then use a temporary chest item that is
				// removed by the existing preview rollback after seven seconds.
				applied = applyBodySkin(player, template, false, true) || applyVirtualBodySkinToArmorSlot(player, template, false) || applyVirtualBodySkin(player, template, false);
			}
			else
			{
				applied = applyBodySkin(player, template, true, false) || applyVirtualBodySkin(player, template, true);
			}
			if (!applied)
			{
				logVisualAction(player, action, skinId, template, -1, false, "No compatible visual body slot");
				player.sendMessage("No compatible visual slot for this skin. Check the server log.");
			}
		}
		
		if (applied)
		{
			logVisualAction(player, action, skinId, template, findAppliedVisualSlot(player, template), true, null);
			logVisualDebug(player, action + "_PACKET", template, findAppliedVisualSlot(player, template));
			refreshVisual(player);
		}
		return applied;
	}
	
	private static void removeDressMePreview(Player player)
	{
		removeDressMePreview(player, true);
	}
	
	private static void removeDressMePreview(Player player, boolean notify)
	{
		DRESS_ME_PREVIEW_TOKEN.merge(player.getObjectId(), 1, Integer::sum);
		final Map<Integer, Integer> backups = DRESS_ME_PREVIEW_BACKUP.remove(player.getObjectId());
		final Integer hatBackup = DRESS_ME_PREVIEW_HAT_BACKUP.remove(player.getObjectId());
		final BodySkinBackup bodyBackup = DRESS_ME_PREVIEW_BODY_BACKUP.remove(player.getObjectId());
		if (((backups == null) || backups.isEmpty()) && (hatBackup == null) && (bodyBackup == null))
		{
			if (cleanupUnownedEquippedTransmogs(player))
			{
				if (notify)
				{
					player.sendMessage("Temporary skin previews were removed.");
				}
				return;
			}
			if (!notify)
			{
				return;
			}
			player.sendMessage("You do not have an active preview.");
			return;
		}
		
		if (hatBackup != null)
		{
			if (hatBackup > 0)
			{
				setActiveHatSkin(player, hatBackup);
				restoreActiveHatSkin(player);
			}
			else
			{
				removeVirtualHatSkin(player, true);
			}
		}
		
		if (backups != null)
		{
			for (Map.Entry<Integer, Integer> entry : backups.entrySet())
			{
				final Item item = player.getInventory().unEquipItemInBodySlot(entry.getKey());
				if (item != null)
				{
					if (entry.getValue() > 0)
					{
						item.setTransmogId(entry.getValue());
					}
					else
					{
						item.removeTransmog();
					}
					player.getInventory().equipItem(item);
				}
			}
		}
		
		if (bodyBackup != null)
		{
			restoreBodySkinBackup(player, bodyBackup);
		}
		
		refreshVisual(player);
		if (notify)
		{
			player.sendMessage("Preview removed.");
		}
	}
	
	private static void startPreviewTimer(Player player)
	{
		final int objectId = player.getObjectId();
		final int token = DRESS_ME_PREVIEW_TOKEN.merge(objectId, 1, Integer::sum);
		ThreadPool.schedule(() ->
		{
			final Integer currentToken = DRESS_ME_PREVIEW_TOKEN.get(objectId);
			if ((currentToken != null) && (currentToken == token))
			{
				removeDressMePreview(player, false);
				player.sendMessage("Skin preview expired.");
			}
		}, PREVIEW_DURATION);
	}
	
	private static boolean ownsDressMeSkin(Player player, ItemTemplate template)
	{
		final Map<Integer, Set<Integer>> playerTransmogs = PLAYER_TRANSMOGS.getOrDefault(player.getObjectId(), Collections.emptyMap());
		final Set<Integer> itemIds = playerTransmogs.getOrDefault(template.getBodyPart(), Collections.emptySet());
		final int visualId = getVisualId(template);
		if (itemIds.contains(visualId) || itemIds.contains(template.getId()))
		{
			return true;
		}
		
		if (hasSavedDressMeSkin(player, visualId) || hasSavedDressMeSkin(player, template.getId()))
		{
			addToCollection(player, template);
			return true;
		}
		
		return false;
	}
	
	private static DressMeSkin resolveDressMeSkin(int skinId)
	{
		for (DressMeSkin skin : DRESS_ME_SKINS)
		{
			final ItemTemplate displayTemplate = ItemData.getInstance().getTemplate(skin.displayItemId);
			if ((displayTemplate != null) && ((displayTemplate.getId() == skinId) || (getVisualId(displayTemplate) == skinId)))
			{
				return skin;
			}
			
			for (int itemId : skin.itemIds)
			{
				final ItemTemplate pieceTemplate = ItemData.getInstance().getTemplate(itemId);
				if ((pieceTemplate != null) && ((pieceTemplate.getId() == skinId) || (getVisualId(pieceTemplate) == skinId)))
				{
					return skin;
				}
			}
		}
		return null;
	}
	
	private static ItemTemplate resolveMegaraxSkinTemplate(int skinId)
	{
		for (int itemId : MEGARAX_SKIN_ITEM_IDS)
		{
			final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
			if ((template != null) && ((template.getId() == skinId) || (getVisualId(template) == skinId)))
			{
				return template;
			}
		}
		
		return null;
	}
	
	private static int getVisualId(ItemTemplate template)
	{
		if (template == null)
		{
			return 0;
		}
		
		final int displayId = template.getDisplayId();
		return displayId > 0 ? displayId : template.getId();
	}
	
	private static ItemTemplate resolveTransmogTemplate(int transmogId)
	{
		final ItemTemplate skinTemplate = resolveMegaraxSkinTemplate(transmogId);
		if (skinTemplate != null)
		{
			return skinTemplate;
		}
		
		return ItemData.getInstance().getTemplate(transmogId);
	}
	
	private static String getOwner(Player player)
	{
		return String.valueOf(player.getObjectId());
	}
	
	private void initTable()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement statement = con.prepareStatement(CREATE_TABLE_SQL))
		{
			statement.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("Problem creating Transmog table: " + e.getMessage());
		}
	}
	
	private boolean saveDressMeSkin(Player player, int itemId)
	{
		final String owner = getOwner(player);
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement statement = con.prepareStatement(SAVE_SQL))
		{
			statement.setString(1, owner);
			statement.setInt(2, itemId);
			statement.execute();
			return true;
		}
		catch (SQLException e)
		{
			LOGGER.warning("Problem saving Dress Me skin: " + e.getMessage());
		}
		return false;
	}
	
	private static boolean hasSavedDressMeSkin(Player player, int itemId)
	{
		return hasSavedDressMeSkin(getOwner(player), itemId) || hasSavedDressMeSkin(player.getAccountName(), itemId) || hasSavedDressMeSkin(player.getName(), itemId);
	}
	
	private static boolean hasSavedDressMeSkin(String owner, int itemId)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement statement = con.prepareStatement("SELECT itemId FROM character_transmogs WHERE owner=? AND itemId=?"))
		{
			statement.setString(1, owner);
			statement.setInt(2, itemId);
			try (ResultSet result = statement.executeQuery())
			{
				return result.next();
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("Problem checking Dress Me skin: " + e.getMessage());
		}
		return false;
	}
	
	public void onPlayerItemAdd(OnPlayerItemAdd event)
	{
		final Player player = event.getPlayer();
		final Item eventItem = event.getItem();
		if ((eventItem == null) || cleanupLeakedVirtualSkinItem(player, eventItem))
		{
			return;
		}
		
		final ItemTemplate itemTemplate = eventItem.getTemplate();
		if ((itemTemplate instanceof EtcItem) || itemTemplate.isForNpc())
		{
			return;
		}
		
		final Integer bodypart = itemTemplate.getBodyPart();
		if ((bodypart < ItemTemplate.SLOT_R_HAND) || (bodypart > ItemTemplate.SLOT_HAIRALL))
		{
			return;
		}
		
		if (Config.TRANSMOG_BANNED_ITEM_IDS.contains(itemTemplate.getId()))
		{
			return;
		}
		
		final Integer playerObjectId = player.getObjectId();
		final Map<Integer, Set<Integer>> playerTransmogs = PLAYER_TRANSMOGS.getOrDefault(playerObjectId, new HashMap<>());
		final Set<Integer> itemIds = playerTransmogs.getOrDefault(bodypart, new HashSet<>());
		if (itemIds.add(getVisualId(itemTemplate)))
		{
			playerTransmogs.putIfAbsent(bodypart, itemIds);
			PLAYER_TRANSMOGS.putIfAbsent(playerObjectId, playerTransmogs);
			player.sendPacket(new CreatureSay(null, ChatType.WHISPER, "[Transmog]", itemTemplate.getName() + " has been added to your appearance collection."));
		}
	}
	
	private void onPlayerItemEquip(OnPlayerItemEquip event)
	{
		final Player player = event.getPlayer();
		final Item item = event.getItem();
		cleanupLeakedVirtualSkinItems(player);
		if ((item == null) || isVirtualDressMeItem(item))
		{
			return;
		}
		
		ThreadPool.schedule(() ->
		{
			cleanupLeakedVirtualSkinItems(player);
			normalizeAgathionSlot(player);
			restoreActiveBodySkin(player);
			restoreActiveHatSkin(player);
		}, 200);
	}
	
	private void onPlayerLogin(OnPlayerLogin event)
	{
		final Player player = event.getPlayer();
		final String owner = getOwner(player);
		final Map<Integer, Set<Integer>> playerTransmogs = new HashMap<>();
		final Set<Integer> loadedItems = new HashSet<>();
		
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement statement = con.prepareStatement(LOAD_SQL))
		{
			for (String ownerKey : new String[] { owner, player.getAccountName(), player.getName() })
			{
				if ((ownerKey == null) || ownerKey.isEmpty())
				{
					continue;
				}
				
				statement.setString(1, ownerKey);
				try (ResultSet result = statement.executeQuery())
				{
					while (result.next())
					{
						final int itemId = result.getInt(1);
						if (!loadedItems.add(itemId))
						{
							continue;
						}
						
						final DressMeSkin skin = resolveDressMeSkin(itemId);
						if (skin != null)
						{
							addSkinToCollection(playerTransmogs, skin);
							continue;
						}
						
						final ItemTemplate itemTemplate = resolveTransmogTemplate(itemId);
						if (itemTemplate != null)
						{
							final Integer bodypart = itemTemplate.getBodyPart();
							final Set<Integer> itemIds = playerTransmogs.getOrDefault(bodypart, new HashSet<>());
							itemIds.add(itemTemplate.getId());
							itemIds.add(getVisualId(itemTemplate));
							playerTransmogs.putIfAbsent(bodypart, itemIds);
						}
					}
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("Problem with Transmog: " + e.getMessage());
		}
		
		if (!playerTransmogs.isEmpty())
		{
			PLAYER_TRANSMOGS.put(player.getObjectId(), playerTransmogs);
		}
		
		cleanupUnownedEquippedTransmogs(player);
		cleanupLeakedVirtualSkinItems(player);
		normalizeAgathionSlot(player);
		restoreActiveBodySkin(player);
		restoreActiveHatSkin(player);
	}
	
	private void onPlayerLogout(OnPlayerLogout event)
	{
		final Player player = event.getPlayer();
		final Integer playerObjectId = player.getObjectId();
		final String owner = getOwner(player);
		if (DRESS_ME_PREVIEW_BACKUP.containsKey(playerObjectId) || DRESS_ME_PREVIEW_HAT_BACKUP.containsKey(playerObjectId) || DRESS_ME_PREVIEW_BODY_BACKUP.containsKey(playerObjectId))
		{
			removeDressMePreview(player);
		}
		removeVirtualHatSkin(player, false);
		removeVirtualBodySkin(player, false);
		cleanupLeakedVirtualSkinItems(player);
		
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement statement = con.prepareStatement(SAVE_SQL))
		{
			for (Set<Integer> itemIds : PLAYER_TRANSMOGS.getOrDefault(playerObjectId, Collections.emptyMap()).values())
			{
				for (int itemId : itemIds)
				{
					statement.setString(1, owner);
					statement.setInt(2, itemId);
					statement.addBatch();
				}
			}
			
			statement.executeBatch();
		}
		catch (SQLException e)
		{
			LOGGER.warning("Problem with Transmog: " + e.getMessage());
		}
		
		PLAYER_TRANSMOGS.remove(playerObjectId);
	}
	
	private static boolean cleanupUnownedEquippedTransmogs(Player player)
	{
		boolean changed = false;
		final Map<Integer, Set<Integer>> playerTransmogs = PLAYER_TRANSMOGS.getOrDefault(player.getObjectId(), Collections.emptyMap());
		for (Item item : player.getInventory().getPaperdollItems())
		{
			if (item == null)
			{
				continue;
			}
			
			final int transmogId = item.getTransmogId();
			if (transmogId <= 0)
			{
				continue;
			}
			
			final ItemTemplate template = resolveTransmogTemplate(transmogId);
			final Set<Integer> ownedSkins = template == null ? Collections.emptySet() : playerTransmogs.getOrDefault(template.getBodyPart(), Collections.emptySet());
			if (DRESS_ME_SKIN_IDS.contains(transmogId) && (template != null) && !ownedSkins.contains(transmogId) && !ownedSkins.contains(template.getId()) && !ownedSkins.contains(getVisualId(template)))
			{
				item.removeTransmog();
				item.updateDatabase();
				changed = true;
			}
		}
		
		if (changed)
		{
			refreshVisual(player);
		}
		if (!isOwnedActiveHatSkin(player))
		{
			changed |= removeVirtualHatSkin(player, true);
		}
		if (!isOwnedActiveBodySkin(player))
		{
			changed |= removeVirtualBodySkin(player, true);
		}
		
		return changed;
	}
	
	private static boolean cleanupLeakedVirtualSkinItem(Player player, Item item)
	{
		if (!isVirtualDressMeItem(item))
		{
			return false;
		}
		
		final boolean removed = player.getInventory().destroyItem(ItemProcessType.DESTROY, item, player, null) != null;
		if (removed)
		{
			player.sendItemList(false);
			refreshVisual(player);
		}
		return removed;
	}
	
	private static boolean cleanupLeakedVirtualSkinItems(Player player)
	{
		boolean removed = false;
		for (Item item : new ArrayList<>(player.getInventory().getItems()))
		{
			if (isVirtualDressMeItem(item) && (player.getInventory().destroyItem(ItemProcessType.DESTROY, item, player, null) != null))
			{
				removed = true;
			}
		}
		
		if (removed)
		{
			player.sendItemList(false);
			refreshVisual(player);
		}
		return removed;
	}
	
	private static boolean removeEquippedDressMeSkin(Player player)
	{
		boolean changed = removeVirtualHatSkin(player, true);
		changed |= removeVirtualBodySkin(player, true);
		for (Item item : player.getInventory().getPaperdollItems())
		{
			if (item == null)
			{
				continue;
			}
			
			final int transmogId = item.getTransmogId();
			if (DRESS_ME_SKIN_IDS.contains(transmogId))
			{
				item.removeTransmog();
				item.updateDatabase();
				changed = true;
			}
		}
		
		if (changed)
		{
			refreshVisual(player);
		}
		
		return changed;
	}
	
	private Integer getBodypart(String event)
	{
		switch (event)
		{
			case "R_HAND":
			{
				return ItemTemplate.SLOT_R_HAND;
			}
			case "L_HAND":
			{
				return ItemTemplate.SLOT_L_HAND;
			}
			case "GLOVES":
			{
				return ItemTemplate.SLOT_GLOVES;
			}
			case "CHEST":
			{
				return ItemTemplate.SLOT_CHEST;
			}
			case "LEGS":
			{
				return ItemTemplate.SLOT_LEGS;
			}
			case "FEET":
			{
				return ItemTemplate.SLOT_FEET;
			}
			case "BACK":
			{
				return ItemTemplate.SLOT_BACK;
			}
			case "LR_HAND":
			{
				return ItemTemplate.SLOT_LR_HAND;
			}
			case "FULL_ARMOR":
			{
				return ItemTemplate.SLOT_FULL_ARMOR;
			}
			case "HAIRALL":
			{
				return ItemTemplate.SLOT_HAIRALL;
			}
			case "HAIR2":
			{
				return ItemTemplate.SLOT_HAIR2;
			}
			case "HAIR":
			{
				return ItemTemplate.SLOT_HAIR;
			}
			default:
			{
				return -1;
			}
		}
	}
	
	private boolean isValidSlot(String slot)
	{
		switch (slot)
		{
			case "R_HAND":
			case "L_HAND":
			case "GLOVES":
			case "CHEST":
			case "LEGS":
			case "FEET":
			case "BACK":
			case "LR_HAND":
			case "FULL_ARMOR":
			case "HAIRALL":
			case "HAIR2":
			case "HAIR":
			{
				return true;
			}
			default:
			{
				return false;
			}
		}
	}
	
	/**
	 * Permite desbloquear un skin via Skin Coin e inyectarlo en memoria para que el jugador pueda aplicarlo sin necesidad de relog.
	 */
	public static void addToCollection(Player player, ItemTemplate template)
	{
		final DressMeSkin skin = resolveDressMeSkin(getVisualId(template));
		if (skin != null)
		{
			final Map<Integer, Set<Integer>> playerTransmogs = PLAYER_TRANSMOGS.computeIfAbsent(player.getObjectId(), k -> new HashMap<>());
			addSkinToCollection(playerTransmogs, skin);
			return;
		}
		
		final Integer playerObjectId = player.getObjectId();
		final Integer bodypart = template.getBodyPart();
		final Map<Integer, Set<Integer>> playerTransmogs = PLAYER_TRANSMOGS.computeIfAbsent(playerObjectId, k -> new HashMap<>());
		final Set<Integer> itemIds = playerTransmogs.computeIfAbsent(bodypart, k -> new HashSet<>());
		itemIds.add(template.getId());
		itemIds.add(getVisualId(template));
	}
	
	private static void addSkinToCollection(Map<Integer, Set<Integer>> playerTransmogs, DressMeSkin skin)
	{
		for (int itemId : skin.itemIds)
		{
			final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
			if (template == null)
			{
				continue;
			}
			
			final Set<Integer> itemIds = playerTransmogs.computeIfAbsent(template.getBodyPart(), k -> new HashSet<>());
			itemIds.add(template.getId());
			itemIds.add(getVisualId(template));
		}
	}
	
	public static void previewSkin(Player player, int skinId)
	{
		previewDressMe(player, skinId);
	}
	
	public static boolean equipSkin(Player player, int skinId)
	{
		return equipDressMe(player, skinId, false);
	}
	
	public static void removePreview(Player player)
	{
		removeDressMePreview(player);
	}
	
	public static boolean removeBodySkin(Player player)
	{
		boolean changed = removeVirtualBodySkin(player, true);
		for (Item item : player.getInventory().getPaperdollItems())
		{
			if ((item != null) && DRESS_ME_BODY_SKIN_IDS.contains(item.getTransmogId()))
			{
				item.removeTransmog();
				item.updateDatabase();
				changed = true;
			}
		}
		
		if (changed)
		{
			normalizeAgathionSlot(player);
			refreshVisual(player);
		}
		return changed;
	}
	
	public static boolean removeHatSkin(Player player)
	{
		return removeVirtualHatSkin(player, true);
	}
	
	@SuppressWarnings("unchecked")
	private static void installMegaraxVisualSets()
	{
		try
		{
			final Field field = VisualArmorSkinData.class.getDeclaredField("HAT_TO_SET");
			field.setAccessible(true);
			final Map<Integer, VisualSet> visualSets = (Map<Integer, VisualSet>) field.get(null);
			for (DressMeSkin skin : DRESS_ME_SKINS)
			{
				final VisualSet set = buildVisualSet(skin);
				if (set == null)
				{
					continue;
				}
				
				visualSets.put(skin.displayItemId, set);
				for (int itemId : skin.itemIds)
				{
					final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
					if (template != null)
					{
						visualSets.put(template.getId(), set);
						visualSets.put(getVisualId(template), set);
					}
				}
			}
		}
		catch (ReflectiveOperationException | RuntimeException e)
		{
			LOGGER.warning("Problem registering Megarax visual skins: " + e.getMessage());
		}
	}
	
	private static VisualSet buildVisualSet(DressMeSkin skin)
	{
		int head = -1;
		int chest = -1;
		int legs = -1;
		int gloves = -1;
		int feet = -1;
		for (int itemId : skin.itemIds)
		{
			final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
			if ((template == null) || isHatBodyPart(template.getBodyPart()))
			{
				continue;
			}
			
			final int displayId = getVisualId(template);
			switch (template.getBodyPart())
			{
				case ItemTemplate.SLOT_HEAD:
				{
					head = displayId;
					break;
				}
				case ItemTemplate.SLOT_GLOVES:
				{
					gloves = displayId;
					break;
				}
				case ItemTemplate.SLOT_CHEST:
				case ItemTemplate.SLOT_FULL_ARMOR:
				{
					chest = displayId;
					break;
				}
				case ItemTemplate.SLOT_LEGS:
				{
					legs = displayId;
					break;
				}
				case ItemTemplate.SLOT_FEET:
				{
					feet = displayId;
					break;
				}
			}
		}
		
		if ((head <= 0) && (chest <= 0) && (legs <= 0) && (gloves <= 0) && (feet <= 0))
		{
			return null;
		}
		return new VisualSet(head, chest, legs, gloves, feet);
	}
	
	private static void backupBodySkin(Player player)
	{
		DRESS_ME_PREVIEW_BODY_BACKUP.computeIfAbsent(player.getObjectId(), objectId -> new BodySkinBackup(player));
	}
	
	private static void restoreBodySkinBackup(Player player, BodySkinBackup backup)
	{
		removeVirtualBodySkin(player, false);
		player.getInventory().setPaperdollItem(VISUAL_BODY_ANCHOR_SLOT, backup.hair);
		player.getInventory().setPaperdollItem(Inventory.PAPERDOLL_HAIR2, backup.hair2);
		player.getInventory().setPaperdollItem(Inventory.PAPERDOLL_FACE, backup.face);
		if (backup.activeSkin > 0)
		{
			setActiveBodySkin(player, backup.activeSkin);
			restoreActiveBodySkin(player);
		}
		else
		{
			player.getVariables().remove(ACTIVE_BODY_SKIN_VARIABLE);
			player.getVariables().storeMe();
		}
	}
	
	private static boolean isHatBodyPart(int bodypart)
	{
		return (bodypart == ItemTemplate.SLOT_HAIR) || (bodypart == ItemTemplate.SLOT_HAIR2) || (bodypart == ItemTemplate.SLOT_HAIRALL);
	}
	
	private static boolean isVirtualHatItem(Item item)
	{
		if ((item == null) || (item.getOwnerId() != 0))
		{
			return false;
		}
		
		final ItemTemplate template = resolveMegaraxSkinTemplate(item.getId());
		return (template != null) && isHatBodyPart(template.getBodyPart());
	}
	
	private static boolean applyVirtualHatSkin(Player player, ItemTemplate template, boolean permanent)
	{
		removeVirtualHatSkin(player, false);
		final int slot = findVirtualHatSlot(player, template);
		if (slot < 0)
		{
			return false;
		}
		
		final Item currentItem = player.getInventory().getPaperdollItem(slot);
		if ((currentItem != null) && !isVirtualHatItem(currentItem))
		{
			return false;
		}
		
		if (currentItem != null)
		{
			player.getInventory().setPaperdollItem(slot, null);
		}
		
		final Item visualItem = new Item(template.getId());
		player.getInventory().setPaperdollItem(slot, visualItem);
		if (permanent)
		{
			setActiveHatSkin(player, getVisualId(template));
		}
		refreshVisual(player);
		return true;
	}
	
	private static int findVirtualHatSlot(Player player, ItemTemplate template)
	{
		for (int slot : getVirtualHatSlots(template))
		{
			if (isAvailableVirtualHatSlot(player, slot))
			{
				return slot;
			}
		}
		return -1;
	}
	
	private static int[] getVirtualHatSlots(ItemTemplate template)
	{
		if ((template != null) && (template.getBodyPart() == ItemTemplate.SLOT_HAIR2))
		{
			return new int[] { Inventory.PAPERDOLL_HAIR2 };
		}
		return new int[] { Inventory.PAPERDOLL_HAIR, Inventory.PAPERDOLL_HAIR2 };
	}
	
	private static boolean isAvailableVirtualHatSlot(Player player, int slot)
	{
		if (slot < 0)
		{
			return false;
		}
		
		final Item item = player.getInventory().getPaperdollItem(slot);
		if (item == null)
		{
			return true;
		}
		
		return isVirtualHatItem(item) && !isVirtualBodySkinItem(item);
	}
	
	private static boolean applyBodySkin(Player player, ItemTemplate template, boolean permanent, boolean preview)
	{
		if (!isBodySkinTemplate(template))
		{
			return false;
		}
		
		cleanupLegacyVirtualBodySkin(player);
		final Item item = getBodySkinTargetItem(player, template);
		if (item == null)
		{
			logVisualAction(player, preview ? "PREVIEW" : "APPLY", getVisualId(template), template, Inventory.PAPERDOLL_CHEST, false, "Equip armor first, then apply the visual skin");
			return false;
		}
		
		if (preview)
		{
			backupBodyTransmogs(player);
		}
		
		clearBodySkinTransmogs(player, !preview);
		final int bodyPart = item.getTemplate().getBodyPart();
		final Item unequipped = player.getInventory().unEquipItemInBodySlot(bodyPart);
		final Item target = unequipped != null ? unequipped : item;
		target.setTransmogId(getVisualId(template));
		if (!preview)
		{
			target.updateDatabase();
		}
		if (unequipped != null)
		{
			player.getInventory().equipItem(target);
		}
		if (permanent)
		{
			setActiveBodySkin(player, getVisualId(template));
		}
		refreshVisual(player);
		if (!isBodySkinPacketApplied(player, template))
		{
			final int packetSlot = getVirtualBodySlot(template);
			logVisualAction(player, preview ? "PREVIEW_PACKET_VALIDATE" : "APPLY_PACKET_VALIDATE", getVisualId(template), template, packetSlot, false, "Packet display did not change expected=" + getVisualId(template) + " actual=" + player.getInventory().getPaperdollItemDisplayId(packetSlot));
			return false;
		}
		return true;
	}
	
	private static void backupBodyTransmogs(Player player)
	{
		final Map<Integer, Integer> backups = DRESS_ME_PREVIEW_BACKUP.computeIfAbsent(player.getObjectId(), k -> new HashMap<>());
		for (int slot : BODY_SKIN_TARGET_SLOTS)
		{
			final Item item = player.getInventory().getPaperdollItem(slot);
			if ((item != null) && !isVirtualDressMeItem(item))
			{
				backups.putIfAbsent(item.getTemplate().getBodyPart(), item.getTransmogId());
			}
		}
	}
	
	private static boolean clearBodySkinTransmogs(Player player, boolean updateDatabase)
	{
		boolean changed = false;
		for (int slot : BODY_SKIN_TARGET_SLOTS)
		{
			final Item item = player.getInventory().getPaperdollItem(slot);
			if ((item != null) && DRESS_ME_BODY_SKIN_IDS.contains(item.getTransmogId()))
			{
				item.removeTransmog();
				if (updateDatabase)
				{
					item.updateDatabase();
				}
				changed = true;
			}
		}
		return changed;
	}
	
	private static boolean cleanupLegacyVirtualBodySkin(Player player)
	{
		boolean changed = false;
		for (int slot : VISUAL_BODY_CLEANUP_SLOTS)
		{
			final Item item = player.getInventory().getPaperdollItem(slot);
			if (isVirtualBodySkinItem(item))
			{
				player.getInventory().setPaperdollItem(slot, null);
				changed = true;
			}
		}
		return changed;
	}
	
	private static boolean applyVirtualBodySkinToArmorSlot(Player player, ItemTemplate template, boolean permanent)
	{
		final int slot = getVirtualBodySlot(template);
		final Item currentItem = player.getInventory().getPaperdollItem(slot);
		if ((currentItem != null) && !isVirtualBodySkinItem(currentItem))
		{
			final ItemTemplate currentTemplate = currentItem.getTemplate();
			final String currentBodyPart = currentTemplate != null ? getBodyPartName(currentTemplate.getBodyPart()) : "unknown";
			logVisualAction(player, "SERVER_VALIDATE", getVisualId(template), template, slot, false, "Virtual body slot blocked by equipped itemId=" + currentItem.getId() + " bodypart=" + currentBodyPart);
			return false;
		}
		
		removeVirtualBodySkin(player, false);
		final Item visualItem = new Item(template.getId());
		player.getInventory().setPaperdollItem(slot, visualItem);
		if (permanent)
		{
			setActiveBodySkin(player, getVisualId(template));
		}
		refreshVisual(player);
		if (!isBodySkinPacketApplied(player, template))
		{
			final int packetSlot = getVirtualBodySlot(template);
			logVisualAction(player, "VIRTUAL_PACKET_VALIDATE", getVisualId(template), template, packetSlot, false, "Packet display did not change expected=" + getVisualId(template) + " actual=" + player.getInventory().getPaperdollItemDisplayId(packetSlot));
			return false;
		}
		return true;
	}
	
	private static int getVirtualBodySlot(ItemTemplate template)
	{
		switch (template.getBodyPart())
		{
			case ItemTemplate.SLOT_HEAD:
			{
				return Inventory.PAPERDOLL_HEAD;
			}
			case ItemTemplate.SLOT_GLOVES:
			{
				return Inventory.PAPERDOLL_GLOVES;
			}
			case ItemTemplate.SLOT_LEGS:
			{
				return Inventory.PAPERDOLL_LEGS;
			}
			case ItemTemplate.SLOT_FEET:
			{
				return Inventory.PAPERDOLL_FEET;
			}
			case ItemTemplate.SLOT_FULL_ARMOR:
			case ItemTemplate.SLOT_CHEST:
			default:
			{
				return Inventory.PAPERDOLL_CHEST;
			}
		}
	}
	
	private static boolean isBodySkinPacketApplied(Player player, ItemTemplate template)
	{
		final int packetSlot = getVirtualBodySlot(template);
		return player.getInventory().getPaperdollItemDisplayId(packetSlot) == getVisualId(template);
	}
	
	private static boolean applyVirtualBodySkin(Player player, ItemTemplate template, boolean permanent)
	{
		if (!isBodySkinTemplate(template))
		{
			return false;
		}
		
		if (!hasRegisteredBodyVisualSet(template))
		{
			logVisualAction(player, "SERVER_VALIDATE", getVisualId(template), template, VISUAL_BODY_ANCHOR_SLOT, false, "VisualArmorSkinData set not registered");
			return false;
		}
		
		if (!prepareVirtualBodySkinSlot(player))
		{
			return false;
		}
		
		final Item visualItem = new Item(template.getId());
		player.getInventory().setPaperdollItem(VISUAL_BODY_ANCHOR_SLOT, visualItem);
		if (permanent)
		{
			setActiveBodySkin(player, getVisualId(template));
		}
		refreshVisual(player);
		if (!isBodySkinPacketApplied(player, template))
		{
			final int packetSlot = getVirtualBodySlot(template);
			logVisualAction(player, "VIRTUAL_PACKET_VALIDATE", getVisualId(template), template, packetSlot, false, "Packet display did not change expected=" + getVisualId(template) + " actual=" + player.getInventory().getPaperdollItemDisplayId(packetSlot));
			return false;
		}
		return true;
	}
	
	private static boolean prepareVirtualBodySkinSlot(Player player)
	{
		final Item hairItem = player.getInventory().getPaperdollItem(VISUAL_BODY_ANCHOR_SLOT);
		if (hairItem == null)
		{
			return true;
		}
		
		if (isVirtualBodySkinItem(hairItem))
		{
			player.getInventory().setPaperdollItem(VISUAL_BODY_ANCHOR_SLOT, null);
			return true;
		}
		
		if (isMegaraxAgathion(hairItem) && moveHairItemToSlot(player, hairItem, Inventory.PAPERDOLL_HAIR2))
		{
			LOGGER.info("[VISUAL_HAT] Player=" + player.getName() + " action=SLOT_NORMALIZE hatId=" + hairItem.getId() + " visualItemId=" + hairItem.getDisplayId() + " from=" + getPaperdollSlotName(VISUAL_BODY_ANCHOR_SLOT) + " to=" + getPaperdollSlotName(Inventory.PAPERDOLL_HAIR2) + " result=SUCCESS reason=Reserved Hair slot for body skin");
			return true;
		}
		
		if (isVirtualHatItem(hairItem) && moveHairItemToSecondaryVisualSlot(player, hairItem))
		{
			return true;
		}
		
		LOGGER.warning("DressMe: Unable to apply body skin for " + player.getName() + ". Hair slot is occupied by item " + hairItem.getId() + " and no secondary visual slot is available.");
		return false;
	}
	
	private static boolean moveHairItemToSlot(Player player, Item hairItem, int targetSlot)
	{
		final Item targetItem = player.getInventory().getPaperdollItem(targetSlot);
		if (targetItem == null)
		{
			player.getInventory().setPaperdollItem(VISUAL_BODY_ANCHOR_SLOT, null);
			player.getInventory().setPaperdollItem(targetSlot, hairItem);
			return true;
		}
		
		if (isVirtualHatItem(targetItem))
		{
			final Item faceItem = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_FACE);
			if ((targetSlot != Inventory.PAPERDOLL_FACE) && (faceItem == null))
			{
				player.getInventory().setPaperdollItem(targetSlot, null);
				player.getInventory().setPaperdollItem(Inventory.PAPERDOLL_FACE, targetItem);
				player.getInventory().setPaperdollItem(VISUAL_BODY_ANCHOR_SLOT, null);
				player.getInventory().setPaperdollItem(targetSlot, hairItem);
				LOGGER.info("[VISUAL_HAT] Player=" + player.getName() + " action=SLOT_NORMALIZE hatId=" + targetItem.getId() + " visualItemId=" + targetItem.getDisplayId() + " from=" + getPaperdollSlotName(targetSlot) + " to=" + getPaperdollSlotName(Inventory.PAPERDOLL_FACE) + " result=SUCCESS reason=Freed Hair2 for agathion");
				return true;
			}
		}
		
		if (isVirtualBodySkinItem(targetItem))
		{
			player.getInventory().setPaperdollItem(targetSlot, null);
			player.getInventory().setPaperdollItem(VISUAL_BODY_ANCHOR_SLOT, null);
			player.getInventory().setPaperdollItem(targetSlot, hairItem);
			return true;
		}
		return false;
	}
	
	private static boolean moveHairItemToSecondaryVisualSlot(Player player, Item hairItem)
	{
		if (hairItem == null)
		{
			return true;
		}
		
		for (int slot : new int[] { Inventory.PAPERDOLL_HAIR2, Inventory.PAPERDOLL_FACE })
		{
			final Item slotItem = player.getInventory().getPaperdollItem(slot);
			if (slotItem == null)
			{
				player.getInventory().setPaperdollItem(VISUAL_BODY_ANCHOR_SLOT, null);
				player.getInventory().setPaperdollItem(slot, hairItem);
				LOGGER.info("[VISUAL_SKIN] Player=" + player.getName() + " action=SLOT_NORMALIZE movedItemId=" + hairItem.getId() + " from=" + getPaperdollSlotName(VISUAL_BODY_ANCHOR_SLOT) + " to=" + getPaperdollSlotName(slot) + " result=SUCCESS");
				return true;
			}
		}
		
		for (int slot : new int[] { Inventory.PAPERDOLL_HAIR2, Inventory.PAPERDOLL_FACE })
		{
			final Item slotItem = player.getInventory().getPaperdollItem(slot);
			if (isVirtualBodySkinItem(slotItem))
			{
				player.getInventory().setPaperdollItem(slot, null);
				player.getInventory().setPaperdollItem(VISUAL_BODY_ANCHOR_SLOT, null);
				player.getInventory().setPaperdollItem(slot, hairItem);
				LOGGER.info("[VISUAL_SKIN] Player=" + player.getName() + " action=SLOT_NORMALIZE movedItemId=" + hairItem.getId() + " from=" + getPaperdollSlotName(VISUAL_BODY_ANCHOR_SLOT) + " to=" + getPaperdollSlotName(slot) + " result=SUCCESS reason=Replaced stale body anchor");
				return true;
			}
		}
		
		LOGGER.warning("[VISUAL_SKIN] Player=" + player.getName() + " action=SLOT_NORMALIZE movedItemId=" + hairItem.getId() + " result=FAILED reason=Hair2 and Face are occupied by real or active visual items");
		return false;
	}
	
	private static Item getBodySkinTargetItem(Player player, ItemTemplate template)
	{
		final int[] preferredSlots = getBodySkinTargetSlots(template);
		for (int slot : preferredSlots)
		{
			final Item item = player.getInventory().getPaperdollItem(slot);
			if (isBodySkinTargetItem(item, slot))
			{
				return item;
			}
		}
		
		for (int slot : BODY_SKIN_TARGET_SLOTS)
		{
			final Item item = player.getInventory().getPaperdollItem(slot);
			if (isBodySkinTargetItem(item, slot))
			{
				return item;
			}
		}
		return null;
	}
	
	private static boolean isBodySkinTargetItem(Item item, int slot)
	{
		return (item != null) && !isVirtualDressMeItem(item) && isBodyPaperdollSlot(slot);
	}
	
	private static boolean isBodyPaperdollSlot(int slot)
	{
		return (slot == Inventory.PAPERDOLL_HEAD) || (slot == Inventory.PAPERDOLL_CHEST) || (slot == Inventory.PAPERDOLL_LEGS) || (slot == Inventory.PAPERDOLL_GLOVES) || (slot == Inventory.PAPERDOLL_FEET);
	}
	
	private static int[] getBodySkinTargetSlots(ItemTemplate template)
	{
		switch (template.getBodyPart())
		{
			case ItemTemplate.SLOT_HEAD:
			{
				return new int[] { Inventory.PAPERDOLL_HEAD };
			}
			case ItemTemplate.SLOT_GLOVES:
			{
				return new int[] { Inventory.PAPERDOLL_GLOVES };
			}
			case ItemTemplate.SLOT_LEGS:
			{
				return new int[] { Inventory.PAPERDOLL_LEGS };
			}
			case ItemTemplate.SLOT_FEET:
			{
				return new int[] { Inventory.PAPERDOLL_FEET };
			}
			case ItemTemplate.SLOT_FULL_ARMOR:
			case ItemTemplate.SLOT_CHEST:
			default:
			{
				return new int[] { Inventory.PAPERDOLL_CHEST };
			}
		}
	}
	
	private static boolean removeVirtualHatSkin(Player player, boolean clearPermanent)
	{
		boolean changed = false;
		for (int slot : new int[] { Inventory.PAPERDOLL_HAIR2, Inventory.PAPERDOLL_FACE, Inventory.PAPERDOLL_HAIR })
		{
			final Item item = player.getInventory().getPaperdollItem(slot);
			if (isVirtualHatItem(item) && !isVirtualBodySkinItem(item))
			{
				player.getInventory().setPaperdollItem(slot, null);
				changed = true;
			}
		}
		
		if (clearPermanent && (getActiveHatSkin(player) > 0))
		{
			player.getVariables().remove(ACTIVE_HAT_SKIN_VARIABLE);
			player.getVariables().storeMe();
			changed = true;
		}
		
		if (changed)
		{
			refreshVisual(player);
		}
		return changed;
	}
	
	private static boolean removeVirtualBodySkin(Player player, boolean clearPermanent)
	{
		boolean changed = false;
		for (int slot : VISUAL_BODY_CLEANUP_SLOTS)
		{
			final Item item = player.getInventory().getPaperdollItem(slot);
			if (isVirtualBodySkinItem(item))
			{
				player.getInventory().setPaperdollItem(slot, null);
				changed = true;
			}
		}
		
		if (clearPermanent && (getActiveBodySkin(player) > 0))
		{
			player.getVariables().remove(ACTIVE_BODY_SKIN_VARIABLE);
			player.getVariables().storeMe();
			changed = true;
		}
		
		if (changed)
		{
			refreshVisual(player);
		}
		return changed;
	}
	
	private static void restoreActiveHatSkin(Player player)
	{
		final int activeHatSkin = getActiveHatSkin(player);
		if (activeHatSkin <= 0)
		{
			return;
		}
		
		final ItemTemplate template = resolveMegaraxSkinTemplate(activeHatSkin);
		if ((template == null) || !isHatBodyPart(template.getBodyPart()) || !ownsDressMeSkin(player, template))
		{
			player.getVariables().remove(ACTIVE_HAT_SKIN_VARIABLE);
			player.getVariables().storeMe();
			return;
		}
		
		applyVirtualHatSkin(player, template, false);
	}
	
	private static boolean normalizeAgathionSlot(Player player)
	{
		final Item hairItem = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_HAIR);
		if (!isMegaraxAgathion(hairItem))
		{
			return false;
		}
		
		final Item hair2Item = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_HAIR2);
		if (hair2Item != null)
		{
			LOGGER.warning("[VISUAL_HAT] Player=" + player.getName() + " action=SLOT_NORMALIZE agathionId=" + hairItem.getId() + " from=Hair to=Hair2 result=FAILED reason=Hair2 occupied by itemId=" + hair2Item.getId());
			return false;
		}
		
		player.getInventory().setPaperdollItem(Inventory.PAPERDOLL_HAIR, null);
		player.getInventory().setPaperdollItem(Inventory.PAPERDOLL_HAIR2, hairItem);
		LOGGER.info("[VISUAL_HAT] Player=" + player.getName() + " action=SLOT_NORMALIZE agathionId=" + hairItem.getId() + " from=Hair to=Hair2 result=SUCCESS reason=Reserved Hair for body visual skins");
		refreshVisual(player);
		return true;
	}
	
	private static void restoreActiveBodySkin(Player player)
	{
		final int activeBodySkin = getActiveBodySkin(player);
		if (activeBodySkin <= 0)
		{
			return;
		}
		
		final ItemTemplate template = resolveMegaraxSkinTemplate(activeBodySkin);
		if ((template == null) || isHatBodyPart(template.getBodyPart()) || !ownsDressMeSkin(player, template))
		{
			player.getVariables().remove(ACTIVE_BODY_SKIN_VARIABLE);
			player.getVariables().storeMe();
			return;
		}
		
		applyBodySkin(player, template, false, false);
	}
	
	private static void refreshVisual(Player player)
	{
		player.broadcastUserInfo();
		player.broadcastInfo();
	}
	
	private static boolean isOwnedActiveHatSkin(Player player)
	{
		final int activeHatSkin = getActiveHatSkin(player);
		if (activeHatSkin <= 0)
		{
			return true;
		}
		
		final ItemTemplate template = resolveMegaraxSkinTemplate(activeHatSkin);
		return (template != null) && ownsDressMeSkin(player, template);
	}
	
	private static boolean isOwnedActiveBodySkin(Player player)
	{
		final int activeBodySkin = getActiveBodySkin(player);
		if (activeBodySkin <= 0)
		{
			return true;
		}
		
		final ItemTemplate template = resolveMegaraxSkinTemplate(activeBodySkin);
		return (template != null) && ownsDressMeSkin(player, template);
	}
	
	private static int getActiveHatSkin(Player player)
	{
		return player.getVariables().getInt(ACTIVE_HAT_SKIN_VARIABLE, 0);
	}
	
	private static void setActiveHatSkin(Player player, int displayId)
	{
		player.getVariables().set(ACTIVE_HAT_SKIN_VARIABLE, displayId);
		player.getVariables().storeMe();
	}
	
	private static int getActiveBodySkin(Player player)
	{
		return player.getVariables().getInt(ACTIVE_BODY_SKIN_VARIABLE, 0);
	}
	
	private static void setActiveBodySkin(Player player, int displayId)
	{
		player.getVariables().set(ACTIVE_BODY_SKIN_VARIABLE, displayId);
		player.getVariables().storeMe();
	}
	
	private static boolean isVirtualBodySkinItem(Item item)
	{
		if ((item == null) || (item.getOwnerId() != 0))
		{
			return false;
		}
		
		final ItemTemplate template = resolveMegaraxSkinTemplate(item.getId());
		return (template != null) && !isHatBodyPart(template.getBodyPart());
	}
	
	private static boolean isBodySkinTemplate(ItemTemplate template)
	{
		if ((template == null) || isHatBodyPart(template.getBodyPart()))
		{
			return false;
		}
		
		final int visualId = getVisualId(template);
		return DRESS_ME_BODY_SKIN_IDS.contains(template.getId()) || DRESS_ME_BODY_SKIN_IDS.contains(visualId) || isMegaraxSkinId(template.getId()) || isMegaraxSkinId(visualId);
	}
	
	private static boolean isMegaraxSkinId(int itemId)
	{
		for (int skinId : MEGARAX_SKIN_ITEM_IDS)
		{
			if (skinId == itemId)
			{
				return true;
			}
		}
		return false;
	}
	
	private static boolean isBodySkinAnchorItem(Item item)
	{
		return isVirtualBodySkinItem(item) || ((item != null) && DRESS_ME_BODY_SKIN_IDS.contains(item.getTransmogId()));
	}
	
	private static boolean isVirtualDressMeItem(Item item)
	{
		return (item != null) && (item.getOwnerId() == 0) && ((resolveMegaraxSkinTemplate(item.getId()) != null) || (resolveMegaraxSkinTemplate(item.getDisplayId()) != null));
	}
	
	private static boolean isMegaraxAgathion(Item item)
	{
		return (item != null) && ((item.getId() == AGATHION_ANGER_ID) || (item.getId() == AGATHION_SORROW_ID));
	}
	
	private static boolean hasRegisteredBodyVisualSet(ItemTemplate template)
	{
		if (template == null)
		{
			return false;
		}
		
		return (VisualArmorSkinData.getSet(template.getId()) != null) || (VisualArmorSkinData.getSet(getVisualId(template)) != null);
	}
	
	private static int findAppliedVisualSlot(Player player, ItemTemplate template)
	{
		if ((player == null) || (template == null))
		{
			return -1;
		}
		
		if (isHatBodyPart(template.getBodyPart()))
		{
			for (int slot : VISUAL_HAT_SLOTS)
			{
				final Item item = player.getInventory().getPaperdollItem(slot);
				if (isVirtualHatItem(item) && (item.getId() == template.getId()))
				{
					return slot;
				}
			}
			return -1;
		}
		
		final Item anchor = player.getInventory().getPaperdollItem(VISUAL_BODY_ANCHOR_SLOT);
		if (isVirtualBodySkinItem(anchor) && (anchor.getId() == template.getId()))
		{
			return VISUAL_BODY_ANCHOR_SLOT;
		}
		
		final int visualId = getVisualId(template);
		for (int slot : new int[] { Inventory.PAPERDOLL_HEAD, Inventory.PAPERDOLL_CHEST, Inventory.PAPERDOLL_LEGS, Inventory.PAPERDOLL_GLOVES, Inventory.PAPERDOLL_FEET })
		{
			final Item item = player.getInventory().getPaperdollItem(slot);
			if ((item != null) && (item.getTransmogId() == visualId))
			{
				return slot;
			}
		}
		return -1;
	}
	
	private static void logVisualAction(Player player, String action, int requestedId, ItemTemplate template, int slot, boolean success, String reason)
	{
		final boolean isHat = (template != null) && isHatBodyPart(template.getBodyPart());
		final String tag = isHat ? "[VISUAL_HAT]" : "[VISUAL_SKIN]";
		final String idLabel = isHat ? "hatId" : "skinId";
		final int itemId = template != null ? template.getId() : requestedId;
		final int visualId = getVisualId(template);
		final StringBuilder sb = new StringBuilder();
		sb.append(tag);
		sb.append(" Player=").append(player == null ? "null" : player.getName());
		sb.append(" action=").append(action);
		sb.append(' ').append(idLabel).append('=').append(itemId);
		sb.append(" visualItemId=").append(visualId);
		if (template != null)
		{
			sb.append(" bodypart=").append(getBodyPartName(template.getBodyPart()));
		}
		if (slot >= 0)
		{
			sb.append(" slot=").append(getPaperdollSlotName(slot));
		}
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
	
	private static String getBodyPartName(int bodypart)
	{
		switch (bodypart)
		{
			case ItemTemplate.SLOT_HEAD:
				return "Head";
			case ItemTemplate.SLOT_CHEST:
				return "Chest";
			case ItemTemplate.SLOT_LEGS:
				return "Legs";
			case ItemTemplate.SLOT_GLOVES:
				return "Gloves";
			case ItemTemplate.SLOT_FEET:
				return "Feet";
			case ItemTemplate.SLOT_FULL_ARMOR:
				return "FullArmor";
			case ItemTemplate.SLOT_HAIR:
				return "Hair";
			case ItemTemplate.SLOT_HAIR2:
				return "Hair2";
			case ItemTemplate.SLOT_HAIRALL:
				return "HairAll";
			default:
				return String.valueOf(bodypart);
		}
	}
	
	private static String getPaperdollSlotName(int slot)
	{
		switch (slot)
		{
			case Inventory.PAPERDOLL_FACE:
				return "Face";
			case Inventory.PAPERDOLL_HAIR:
				return "Hair";
			case Inventory.PAPERDOLL_HAIR2:
				return "Hair2";
			case Inventory.PAPERDOLL_HEAD:
				return "Head";
			case Inventory.PAPERDOLL_CHEST:
				return "Chest";
			case Inventory.PAPERDOLL_LEGS:
				return "Legs";
			case Inventory.PAPERDOLL_GLOVES:
				return "Gloves";
			case Inventory.PAPERDOLL_FEET:
				return "Feet";
			default:
				return String.valueOf(slot);
		}
	}
	
	private static VisualSlot resolveVisualSlot(ItemTemplate template)
	{
		if (template == null)
		{
			return VisualSlot.NONE;
		}
		
		switch (template.getBodyPart())
		{
			case ItemTemplate.SLOT_HEAD:
			{
				return VisualSlot.HEAD;
			}
			case ItemTemplate.SLOT_CHEST:
			case ItemTemplate.SLOT_FULL_ARMOR:
			{
				return VisualSlot.CHEST;
			}
			case ItemTemplate.SLOT_LEGS:
			{
				return VisualSlot.LEGS;
			}
			case ItemTemplate.SLOT_GLOVES:
			{
				return VisualSlot.GLOVES;
			}
			case ItemTemplate.SLOT_FEET:
			{
				return VisualSlot.FEET;
			}
			case ItemTemplate.SLOT_HAIR:
			{
				return VisualSlot.HAIR;
			}
			case ItemTemplate.SLOT_HAIR2:
			{
				return VisualSlot.HAIR2;
			}
			case ItemTemplate.SLOT_HAIRALL:
			{
				return VisualSlot.HAIRALL;
			}
			default:
			{
				return VisualSlot.NONE;
			}
		}
	}
	
	private static void logVisualDebug(Player player, String stage, ItemTemplate template, int actualPaperdollSlot)
	{
		if (template == null)
		{
			LOGGER.info("[VISUAL_DEBUG] stage=" + stage + " itemId=0 name=null bodypartRaw=0 bodypartName=null type=null armorType=null slotMask=0 resolvedVisualSlot=NONE paperdollSlot=none");
			return;
		}
		
		final VisualSlot resolvedSlot = resolveVisualSlot(template);
		final int packetSlot = actualPaperdollSlot >= 0 ? actualPaperdollSlot : resolvedSlot.paperdollSlot;
		LOGGER.info("[VISUAL_DEBUG] stage=" + stage + " itemId=" + template.getId() + " name=\"" + template.getName() + "\" bodypartRaw=" + template.getBodyPart() + " bodypartName=" + getBodyPartName(template.getBodyPart()) + " type=" + template.getClass().getSimpleName() + " armorType=" + template.getItemType() + " slotMask=" + template.getItemMask() + " resolvedVisualSlot=" + resolvedSlot + " paperdollSlot=" + getPaperdollSlotName(packetSlot));
		
		if (player == null)
		{
			return;
		}
		
		final Inventory inventory = player.getInventory();
		LOGGER.info("[VISUAL_DEBUG] stage=" + stage + " equippedChest=" + describePaperdollItem(inventory, Inventory.PAPERDOLL_CHEST) + " equippedLegs=" + describePaperdollItem(inventory, Inventory.PAPERDOLL_LEGS) + " equippedFullArmor=" + describeFullArmor(inventory) + " equippedHair=" + describePaperdollItem(inventory, Inventory.PAPERDOLL_HAIR) + " equippedHair2=" + describePaperdollItem(inventory, Inventory.PAPERDOLL_HAIR2) + " equippedFace=" + describePaperdollItem(inventory, Inventory.PAPERDOLL_FACE));
		LOGGER.info("[VISUAL_DEBUG] stage=" + stage + " packetUserInfoVisualChest=" + inventory.getPaperdollItemDisplayId(Inventory.PAPERDOLL_CHEST) + " visualLegs=" + inventory.getPaperdollItemDisplayId(Inventory.PAPERDOLL_LEGS) + " visualHair=" + inventory.getPaperdollItemDisplayId(Inventory.PAPERDOLL_HAIR) + " visualHair2=" + inventory.getPaperdollItemDisplayId(Inventory.PAPERDOLL_HAIR2) + " visualFace=" + inventory.getPaperdollItemDisplayId(Inventory.PAPERDOLL_FACE));
	}
	
	private static String describeFullArmor(Inventory inventory)
	{
		final Item chest = inventory.getPaperdollItem(Inventory.PAPERDOLL_CHEST);
		if ((chest == null) || (chest.getTemplate() == null) || (chest.getTemplate().getBodyPart() != ItemTemplate.SLOT_FULL_ARMOR))
		{
			return "none";
		}
		return describePaperdollItem(inventory, Inventory.PAPERDOLL_CHEST);
	}
	
	private static String describePaperdollItem(Inventory inventory, int slot)
	{
		final Item item = inventory.getPaperdollItem(slot);
		if (item == null)
		{
			return "none";
		}
		
		final ItemTemplate template = item.getTemplate();
		return "{slot=" + getPaperdollSlotName(slot) + ",id=" + item.getId() + ",displayId=" + item.getDisplayId() + ",transmogId=" + item.getTransmogId() + ",ownerId=" + item.getOwnerId() + ",locSlot=" + item.getLocationSlot() + ",bodypart=" + (template == null ? "unknown" : getBodyPartName(template.getBodyPart())) + "}";
	}
	
	private enum VisualSlot
	{
		NONE(-1),
		HEAD(Inventory.PAPERDOLL_HEAD),
		CHEST(Inventory.PAPERDOLL_CHEST),
		LEGS(Inventory.PAPERDOLL_LEGS),
		GLOVES(Inventory.PAPERDOLL_GLOVES),
		FEET(Inventory.PAPERDOLL_FEET),
		HAIR(Inventory.PAPERDOLL_HAIR),
		HAIR2(Inventory.PAPERDOLL_HAIR2),
		HAIRALL(Inventory.PAPERDOLL_HAIR);
		
		private final int paperdollSlot;
		
		private VisualSlot(int paperdollSlot)
		{
			this.paperdollSlot = paperdollSlot;
		}
	}
	
	private static class BodySkinBackup
	{
		private final int activeSkin;
		private final Item hair;
		private final Item hair2;
		private final Item face;
		
		private BodySkinBackup(Player player)
		{
			activeSkin = getActiveBodySkin(player);
			hair = player.getInventory().getPaperdollItem(VISUAL_BODY_ANCHOR_SLOT);
			hair2 = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_HAIR2);
			face = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_FACE);
		}
	}
	
	private static class DressMeSkin
	{
		private final int displayItemId;
		private final String name;
		private final int[] itemIds;
		
		private DressMeSkin(int itemId, String name)
		{
			this(itemId, name, itemId);
		}
		
		private DressMeSkin(int displayItemId, String name, int... itemIds)
		{
			this.displayItemId = displayItemId;
			this.name = name;
			this.itemIds = itemIds;
		}
	}
	
	public static void main(String[] args)
	{
		new Transmog();
	}
}
