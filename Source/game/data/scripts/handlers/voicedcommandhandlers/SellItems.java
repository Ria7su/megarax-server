/*
 * Megarax custom system.
 */
package handlers.voicedcommandhandlers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.Config;
import org.l2jmobius.gameserver.handler.IVoicedCommandHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.network.serverpackets.ActionFailed;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.network.serverpackets.SellList;
import org.l2jmobius.gameserver.network.serverpackets.StatusUpdate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Secure inventory sell service for Megarax.
 * <p>
 * This handler is intentionally server-authoritative: the client only sends the selected object id
 * and quantity, while the server validates ownership, sellability, count and final Adena reward.
 * </p>
 */
public class SellItems implements IVoicedCommandHandler
{
	private static final Logger LOGGER = Logger.getLogger(SellItems.class.getName());
	private static final String[] VOICED_COMMANDS =
	{
		"sellitems",
		"sellitem",
		"sell",
		"venta"
	};
	
	private static final int ADENA_ID = 57;
	private static final int PAGE_SIZE = 7;
	private static final String PRICE_FILE = "data/custom/sell_prices.xml";
	private static final String BUTTON_BACK = "L2UI_ch3.smallbutton2_over";
	private static final String BUTTON_FORE = "L2UI_ch3.smallbutton2";
	
	private static final Map<Integer, SellSession> SESSIONS = new ConcurrentHashMap<>();
	private static volatile SellPrices SELL_PRICES = SellPrices.load();
	
	public SellItems()
	{
		LOGGER.info("SellItems loaded with " + SELL_PRICES.size() + " custom sell prices.");
	}
	
	@Override
	public boolean onCommand(String command, Player player, String params)
	{
		if (player == null)
		{
			return false;
		}
		
		final SellSession session = SESSIONS.computeIfAbsent(player.getObjectId(), objectId -> new SellSession());
		final String[] args = splitParams(params);
		final String action = args.length > 0 ? args[0] : "native";
		
		try
		{
			switch (action)
			{
				case "list":
				{
					showList(player, session, parseInt(args, 1, 1));
					break;
				}
				case "main":
				case "custom":
				{
					showMain(player, session);
					break;
				}
				case "select":
				{
					selectItem(player, session, parseInt(args, 1, 0));
					showMain(player, session);
					break;
				}
				case "qty":
				{
					setQuantity(player, session, parseInt(args, 1, 1));
					showMain(player, session);
					break;
				}
				case "all":
				{
					setAll(player, session);
					showMain(player, session);
					break;
				}
				case "confirm":
				{
					confirmSale(player, session);
					showMain(player, session);
					break;
				}
				case "native":
				{
					openNativeSellWindow(player);
					break;
				}
				case "reload":
				{
					if (player.isGM())
					{
						SELL_PRICES = SellPrices.load();
						session.message = "Sell prices reloaded. Custom entries: " + SELL_PRICES.size() + ".";
					}
					showMain(player, session);
					break;
				}
				default:
				{
					openNativeSellWindow(player);
					break;
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.log(Level.WARNING, "SellItems action failed for " + player.getName() + ". Params: " + params, e);
			session.message = "Sell Items error. Please try again.";
			showMain(player, session);
		}
		
		return true;
	}
	
	@Override
	public String[] getCommandList()
	{
		return VOICED_COMMANDS;
	}
	
	private static void selectItem(Player player, SellSession session, int objectId)
	{
		final Item item = player.getInventory().getItemByObjectId(objectId);
		final String reason = validateSellable(player, item, 1);
		if (reason != null)
		{
			session.clear();
			session.message = reason;
			return;
		}
		
		session.objectId = objectId;
		session.quantity = 1;
		session.message = "Item selected. Choose quantity and confirm.";
	}
	
	private static void setQuantity(Player player, SellSession session, int quantity)
	{
		final Item item = getSelectedItem(player, session);
		if (item == null)
		{
			session.clear();
			session.message = "Select an item first.";
			return;
		}
		
		final int safeQuantity = Math.max(1, Math.min(quantity, item.getCount()));
		final String reason = validateSellable(player, item, safeQuantity);
		if (reason != null)
		{
			session.message = reason;
			return;
		}
		
		session.quantity = safeQuantity;
		session.message = "Quantity updated.";
	}
	
	private static void setAll(Player player, SellSession session)
	{
		final Item item = getSelectedItem(player, session);
		if (item == null)
		{
			session.clear();
			session.message = "Select an item first.";
			return;
		}
		
		setQuantity(player, session, item.getCount());
	}
	
	private static void confirmSale(Player player, SellSession session)
	{
		final String state = validatePlayerState(player);
		if (state != null)
		{
			session.message = state;
			return;
		}
		
		final Item item = getSelectedItem(player, session);
		if (item == null)
		{
			session.clear();
			session.message = "Selected item is no longer available.";
			return;
		}
		
		final int quantity = Math.max(1, Math.min(session.quantity, item.getCount()));
		final String reason = validateSellable(player, item, quantity);
		if (reason != null)
		{
			session.message = reason;
			return;
		}
		
		final long unitPrice = getUnitPrice(item);
		final long total = unitPrice * quantity;
		if (total <= 0)
		{
			session.message = "This item has no sell price.";
			return;
		}
		
		if ((player.getAdena() + total) > Inventory.MAX_ADENA)
		{
			session.message = "You would exceed the Adena limit.";
			return;
		}
		
		final Item checkedItem = player.checkItemManipulation(item.getObjectId(), quantity, "sell");
		if (checkedItem == null)
		{
			session.message = "Item validation failed.";
			return;
		}
		
		if (!player.destroyItem(ItemProcessType.SELL, checkedItem, quantity, player, true))
		{
			session.message = "Could not remove the selected item.";
			return;
		}
		
		player.addAdena(ItemProcessType.SELL, (int) total, player, true);
		player.sendItemList(true);
		
		final StatusUpdate statusUpdate = new StatusUpdate(player);
		statusUpdate.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
		player.sendPacket(statusUpdate);
		
		LOGGER.info("[SELL_ITEMS] Player=" + player.getName() + " itemId=" + item.getId() + " objectId=" + item.getObjectId() + " count=" + quantity + " unitPrice=" + unitPrice + " total=" + total);
		session.clear();
		session.message = "Sale complete. You received " + format(total) + " Adena.";
	}
	
	private static void openNativeSellWindow(Player player)
	{
		final String state = validatePlayerState(player);
		if (state != null)
		{
			player.sendMessage(state);
			return;
		}
		
		player.sendPacket(new SellList(player));
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	private static String validatePlayerState(Player player)
	{
		if (player.isInventoryDisabled())
		{
			return "Inventory is currently locked.";
		}
		if (player.isInStoreMode() || player.isCrafting() || player.isProcessingTransaction())
		{
			return "Finish your current transaction first.";
		}
		if (player.isEnchanting())
		{
			return "Finish enchanting first.";
		}
		if (player.isInOlympiadMode() || player.isInDuel() || player.isJailed())
		{
			return "You cannot use Sell Items right now.";
		}
		return null;
	}
	
	private static String validateSellable(Player player, Item item, int quantity)
	{
		if (item == null)
		{
			return "Item not found.";
		}
		if (quantity <= 0)
		{
			return "Invalid quantity.";
		}
		if (item.getId() == ADENA_ID)
		{
			return "Adena cannot be sold.";
		}
		if (item.isEquipped())
		{
			return "Unequip the item before selling it.";
		}
		if (!item.isSellable())
		{
			return "This item cannot be sold.";
		}
		if (quantity > item.getCount())
		{
			return "You do not have enough items.";
		}
		if ((player.getSummon() != null) && (item.getObjectId() == player.getSummon().getControlObjectId()))
		{
			return "Pet control items cannot be sold while the pet is summoned.";
		}
		if (getUnitPrice(item) <= 0)
		{
			return "This item has no sell price.";
		}
		return null;
	}
	
	private static Item getSelectedItem(Player player, SellSession session)
	{
		return session.objectId > 0 ? player.getInventory().getItemByObjectId(session.objectId) : null;
	}
	
	private static long getUnitPrice(Item item)
	{
		final Long customPrice = SELL_PRICES.get(item.getId());
		if (customPrice != null)
		{
			return Math.max(0, customPrice);
		}
		
		if (Config.MERCHANT_ZERO_SELL_PRICE)
		{
			return 0;
		}
		
		return Math.max(0, item.getReferencePrice() / 2L);
	}
	
	private static List<Item> getSellableItems(Player player)
	{
		final List<Item> items = new ArrayList<>();
		for (Item item : player.getInventory().getItems())
		{
			if (validateSellable(player, item, 1) == null)
			{
				items.add(item);
			}
		}
		
		Collections.sort(items, Comparator.comparing(SellItems::displayName, String.CASE_INSENSITIVE_ORDER).thenComparingInt(Item::getObjectId));
		return items;
	}
	
	private static void showMain(Player player, SellSession session)
	{
		final Item item = getSelectedItem(player, session);
		final String selected;
		if (item == null)
		{
			selected = "<table width=292 bgcolor=111111><tr><td align=center><font color=ae9977>Select an item from your inventory.</font></td></tr></table>";
		}
		else
		{
			final long unitPrice = getUnitPrice(item);
			final int quantity = Math.max(1, Math.min(session.quantity, item.getCount()));
			final long total = unitPrice * quantity;
			selected = "<table width=292 bgcolor=111111>" +
				"<tr><td width=34><img src=\"" + icon(item) + "\" width=32 height=32></td><td width=258><font color=LEVEL>" + html(displayName(item)) + "</font><br1><font color=ae9977>Available: " + format(item.getCount()) + "</font></td></tr>" +
				"<tr><td></td><td><font color=ae9977>Unit:</font> <font color=FFCC66>" + format(unitPrice) + "</font> Adena</td></tr>" +
				"<tr><td></td><td><font color=ae9977>Total:</font> <font color=99FF99>" + format(total) + "</font> Adena</td></tr>" +
				"<tr><td></td><td><edit var=\"qty\" width=70 height=15 type=number> <button value=\"Calc\" action=\"bypass -h voice .sellitems qty $qty\" width=45 height=18 back=\"" + BUTTON_BACK + "\" fore=\"" + BUTTON_FORE + "\"></td></tr>" +
				"<tr><td></td><td><button value=\"x1\" action=\"bypass -h voice .sellitems qty 1\" width=45 height=18 back=\"" + BUTTON_BACK + "\" fore=\"" + BUTTON_FORE + "\"> <button value=\"All\" action=\"bypass -h voice .sellitems all\" width=45 height=18 back=\"" + BUTTON_BACK + "\" fore=\"" + BUTTON_FORE + "\"> <button value=\"Confirm\" action=\"bypass -h voice .sellitems confirm\" width=70 height=18 back=\"" + BUTTON_BACK + "\" fore=\"" + BUTTON_FORE + "\"></td></tr>" +
				"</table>";
		}
		
		final StringBuilder html = new StringBuilder(4096);
		html.append("<html><body bgcolor=000000><center>");
		html.append("<table width=300 bgcolor=000000>");
		html.append("<tr><td align=center><font color=D8B66A>MEGARAX SELL ITEMS</font></td></tr>");
		html.append("<tr><td align=center><font color=ae9977>").append(html(session.message)).append("</font></td></tr>");
		html.append("<tr><td height=4></td></tr>");
		html.append("<tr><td>").append(selected).append("</td></tr>");
		html.append("<tr><td height=6></td></tr>");
		html.append("<tr><td align=center>");
		html.append("<button value=\"Select Item\" action=\"bypass -h voice .sellitems list 1\" width=90 height=21 back=\"bigbutton_over\" fore=\"bigbutton\">");
		html.append("<button value=\"Native Sell\" action=\"bypass -h voice .sellitems native\" width=90 height=21 back=\"bigbutton_over\" fore=\"bigbutton\">");
		if (player.isGM())
		{
			html.append("<button value=\"Reload\" action=\"bypass -h voice .sellitems reload\" width=70 height=21 back=\"bigbutton_over\" fore=\"bigbutton\">");
		}
		html.append("</td></tr>");
		html.append("<tr><td align=center><font color=666666>Prices are calculated by the server.</font></td></tr>");
		html.append("</table>");
		html.append("</center></body></html>");
		
		final NpcHtmlMessage msg = new NpcHtmlMessage();
		msg.setHtml(html.toString());
		player.sendPacket(msg);
	}
	
	private static void showList(Player player, SellSession session, int page)
	{
		final List<Item> items = getSellableItems(player);
		final int maxPage = Math.max(1, (int) Math.ceil(items.size() / (double) PAGE_SIZE));
		final int currentPage = Math.max(1, Math.min(page, maxPage));
		final int start = (currentPage - 1) * PAGE_SIZE;
		final int end = Math.min(items.size(), start + PAGE_SIZE);
		
		final StringBuilder rows = new StringBuilder(4096);
		if (items.isEmpty())
		{
			rows.append("<tr><td align=center><font color=ae9977>No sellable items found.</font></td></tr>");
		}
		else
		{
			for (int i = start; i < end; i++)
			{
				final Item item = items.get(i);
				rows.append("<tr>");
				rows.append("<td width=34><img src=\"").append(icon(item)).append("\" width=32 height=32></td>");
				rows.append("<td width=176><font color=LEVEL>").append(html(displayName(item))).append("</font><br1><font color=ae9977>x").append(format(item.getCount())).append(" | ").append(format(getUnitPrice(item))).append(" Adena</font></td>");
				rows.append("<td width=74 align=center><button value=\"Select\" action=\"bypass -h voice .sellitems select ").append(item.getObjectId()).append("\" width=65 height=20 back=\"").append(BUTTON_BACK).append("\" fore=\"").append(BUTTON_FORE).append("\"></td>");
				rows.append("</tr>");
			}
		}
		
		final StringBuilder html = new StringBuilder(4096);
		html.append("<html><body bgcolor=000000><center>");
		html.append("<table width=300 bgcolor=000000>");
		html.append("<tr><td align=center><font color=D8B66A>SELLABLE ITEMS</font></td></tr>");
		html.append("<tr><td><table width=292 bgcolor=111111>").append(rows).append("</table></td></tr>");
		html.append("<tr><td align=center><font color=ae9977>Page ").append(currentPage).append(" / ").append(maxPage).append("</font></td></tr>");
		html.append("<tr><td align=center>");
		html.append("<button value=\"Prev\" action=\"bypass -h voice .sellitems list ").append(Math.max(1, currentPage - 1)).append("\" width=60 height=20 back=\"").append(BUTTON_BACK).append("\" fore=\"").append(BUTTON_FORE).append("\">");
		html.append("<button value=\"Back\" action=\"bypass -h voice .sellitems custom\" width=60 height=20 back=\"").append(BUTTON_BACK).append("\" fore=\"").append(BUTTON_FORE).append("\">");
		html.append("<button value=\"Next\" action=\"bypass -h voice .sellitems list ").append(Math.min(maxPage, currentPage + 1)).append("\" width=60 height=20 back=\"").append(BUTTON_BACK).append("\" fore=\"").append(BUTTON_FORE).append("\">");
		html.append("</td></tr>");
		html.append("</table>");
		html.append("</center></body></html>");
		
		final NpcHtmlMessage msg = new NpcHtmlMessage();
		msg.setHtml(html.toString());
		player.sendPacket(msg);
	}
	
	private static String icon(Item item)
	{
		final String icon = item.getTemplate().getIcon();
		return ((icon == null) || icon.isEmpty()) ? "icon.etc_question_mark_i00" : icon;
	}
	
	private static String displayName(Item item)
	{
		final String name = item.getItemName();
		if ((item.getEnchantLevel() > 0) && (item.isWeapon() || item.isArmor()))
		{
			return "+" + item.getEnchantLevel() + " " + name;
		}
		return name;
	}
	
	private static String[] splitParams(String params)
	{
		return ((params == null) || params.trim().isEmpty()) ? new String[0] : params.trim().split("\\s+");
	}
	
	private static int parseInt(String[] args, int index, int defaultValue)
	{
		if (index >= args.length)
		{
			return defaultValue;
		}
		
		try
		{
			return Integer.parseInt(args[index]);
		}
		catch (NumberFormatException e)
		{
			return defaultValue;
		}
	}
	
	private static String html(String text)
	{
		return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "'");
	}
	
	private static String format(long value)
	{
		return String.format("%,d", value);
	}
	
	private static final class SellSession
	{
		private int objectId;
		private int quantity = 1;
		private String message = "Select an item to sell.";
		
		private void clear()
		{
			objectId = 0;
			quantity = 1;
		}
	}
	
	private static final class SellPrices
	{
		private final Map<Integer, Long> prices;
		
		private SellPrices(Map<Integer, Long> prices)
		{
			this.prices = prices;
		}
		
		private Long get(int itemId)
		{
			return prices.get(itemId);
		}
		
		private int size()
		{
			return prices.size();
		}
		
		private static SellPrices load()
		{
			final Map<Integer, Long> prices = new HashMap<>();
			final File file = resolvePriceFile();
			if ((file == null) || !file.isFile())
			{
				LOGGER.warning("SellItems price file not found: " + PRICE_FILE + ". Falling back to item reference prices.");
				return new SellPrices(prices);
			}
			
			try
			{
				final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
				factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
				factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
				factory.setXIncludeAware(false);
				factory.setExpandEntityReferences(false);
				
				final DocumentBuilder builder = factory.newDocumentBuilder();
				final Document document = builder.parse(file);
				final NodeList nodes = document.getDocumentElement().getElementsByTagName("item");
				for (int i = 0; i < nodes.getLength(); i++)
				{
					final Element element = (Element) nodes.item(i);
					final int itemId = Integer.parseInt(element.getAttribute("id"));
					final long sellPrice = Long.parseLong(element.getAttribute("sellPrice"));
					if (itemId <= 0)
					{
						LOGGER.warning("SellItems skipped invalid item id in " + file.getAbsolutePath() + ".");
						continue;
					}
					
					prices.put(itemId, Math.max(0, sellPrice));
				}
			}
			catch (Exception e)
			{
				LOGGER.log(Level.WARNING, "Could not load SellItems prices from " + file.getAbsolutePath() + ".", e);
			}
			
			return new SellPrices(prices);
		}
		
		private static File resolvePriceFile()
		{
			final List<File> candidates = new ArrayList<>();
			candidates.add(new File(PRICE_FILE));
			candidates.add(new File("game/" + PRICE_FILE));
			if (Config.DATAPACK_ROOT != null)
			{
				candidates.add(new File(Config.DATAPACK_ROOT, PRICE_FILE));
			}
			
			for (File file : candidates)
			{
				if (file.isFile())
				{
					return file;
				}
			}
			return candidates.get(0);
		}
	}
}
