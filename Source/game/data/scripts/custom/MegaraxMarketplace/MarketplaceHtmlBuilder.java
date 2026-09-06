package custom.MegaraxMarketplace;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.instance.Item;

public final class MarketplaceHtmlBuilder
{
	private static final int BOARD_WIDTH = 610;
	private static final int MENU_WIDTH = 124;
	private static final int CONTENT_WIDTH = 470;
	private static final String BUTTON_BACK = "Galaxy.105_OVER";
	private static final String BUTTON_FORE = "Galaxy.105";
	private static final String SMALL_BACK = "Galaxy.105_OVER";
	private static final String SMALL_FORE = "Galaxy.105";
	private static final String CATEGORY_BACK = "Galaxy.2955";
	private static final String CATEGORY_FORE = "Galaxy.2955";
	private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.US);
	private static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
	
	private final MarketplaceManager _manager = MarketplaceManager.getInstance();
	private final MarketplaceDAO _dao = MarketplaceDAO.getInstance();
	
	public void showHome(Player player)
	{
		final StringBuilder html = startPage("Megarax Marketplace", "Comercio global entre jugadores con Rax Coin.");
		final MarketplaceStats stats = _dao.getStats();
		html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=000000><tr>");
		stat(html, "Activos", stats.activeListings);
		stat(html, "Ventas", stats.soldListings);
		stat(html, "Rax Movidas", stats.totalMoved);
		stat(html, "Comision", stats.totalCommission);
		html.append("</tr></table><br1>");
		
		html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=000000><tr>");
		html.append("<td width=250><font color=LEVEL>Explorar mercado</font><br1><font color=ae9977>Armas, armor, joyas y custom items.</font></td>");
		html.append("<td width=110 align=center><button value=\"Explorar\" action=\"bypass _bbsmarket;browse;1;all;recent\" width=104 height=24 back=\"").append(BUTTON_BACK).append("\" fore=\"").append(BUTTON_FORE).append("\"></td>");
		html.append("<td width=110 align=center><button value=\"Vender\" action=\"bypass _bbsmarket;sell;1\" width=104 height=24 back=\"").append(BUTTON_BACK).append("\" fore=\"").append(BUTTON_FORE).append("\"></td>");
		html.append("</tr></table><br1>");
		
		appendSearch(html);
		appendCategoryButtons(html);
		appendRecentListings(html);
		appendRecentSales(html);
		endPage(html);
		send(player, html);
	}
	
	public void showBrowse(Player player, MarketplaceFilter filter)
	{
		final int total = _dao.countActiveListings(filter);
		final int pages = Math.max(1, (int) Math.ceil(total / (double) MarketplaceFilter.PAGE_SIZE));
		filter.page = Math.max(1, Math.min(filter.page, pages));
		final List<MarketplaceListing> listings = _dao.getActiveListings(filter);
		
		final StringBuilder html = startPage("Explorar mercado", "Resultados globales con filtros seguros del servidor.");
		appendSearch(html);
		appendCategoryButtons(html);
		html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=000000><tr>");
		html.append("<td width=110><font color=ae9977>Categoria:</font><br1><font color=LEVEL>").append(filter.category.getLabel()).append("</font></td>");
		html.append("<td width=120><font color=ae9977>Orden:</font><br1><font color=LEVEL>").append(filter.sort.getLabel()).append("</font></td>");
		html.append("<td width=240 align=right>");
		sortButton(html, filter, MarketplaceSort.RECENT);
		sortButton(html, filter, MarketplaceSort.PRICE_ASC);
		sortButton(html, filter, MarketplaceSort.PRICE_DESC);
		html.append("</td></tr></table>");
		sortRow(html, filter);
		appendListingRows(html, listings, player);
		appendPages(html, "_bbsmarket;browse;", filter.page, pages, filter.category.getId(), filter.sort.getId());
		endPage(html);
		send(player, html);
	}
	
	public void showSellList(Player player, int page)
	{
		final List<Item> candidates = getSellCandidates(player);
		final int pages = Math.max(1, (int) Math.ceil(candidates.size() / (double) MarketplaceFilter.PAGE_SIZE));
		page = Math.max(1, Math.min(page, pages));
		final int from = (page - 1) * MarketplaceFilter.PAGE_SIZE;
		final int to = Math.min(candidates.size(), from + MarketplaceFilter.PAGE_SIZE);
		
		final StringBuilder html = startPage("Vender objeto", "Selecciona un objeto valido de tu inventario.");
		html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=000000><tr>");
		html.append("<td width=36></td><td width=190><font color=ae9977>Objeto</font></td><td width=58 align=center><font color=ae9977>Cant.</font></td><td width=52 align=center><font color=ae9977>Enc</font></td><td width=48 align=center><font color=ae9977>Aug</font></td><td width=86 align=center><font color=ae9977>Accion</font></td>");
		html.append("</tr></table>");
		if (candidates.isEmpty())
		{
			empty(html, "No tienes objetos publicables con la configuracion actual.");
		}
		else
		{
			for (int i = from; i < to; i++)
			{
				final Item item = candidates.get(i);
				rowStart(html, i);
				html.append("<td width=36 align=center><img src=\"").append(icon(item.getTemplate())).append("\" width=32 height=32></td>");
				html.append("<td width=190><font color=LEVEL>").append(escape(item.getName())).append("</font><br1><font color=777777>ID ").append(item.getId()).append(" / ").append(MarketplaceCategory.fromItem(item).getLabel()).append("</font></td>");
				html.append("<td width=58 align=center>").append(format(item.getCount())).append("</td>");
				html.append("<td width=52 align=center><font color=D8B66A>+").append(item.getEnchantLevel()).append("</font></td>");
				html.append("<td width=48 align=center>").append(item.isAugmented() ? "<font color=5CA8FF>Si</font>" : "<font color=777777>No</font>").append("</td>");
				html.append("<td width=86 align=center><button value=\"Elegir\" action=\"bypass _bbsmarket;select;").append(item.getObjectId()).append("\" width=78 height=20 back=\"").append(SMALL_BACK).append("\" fore=\"").append(SMALL_FORE).append("\"></td>");
				rowEnd(html);
			}
		}
		appendSimplePages(html, "_bbsmarket;sell;", page, pages);
		endPage(html);
		send(player, html);
	}
	
	public void showSellReview(Player player, MarketplaceManager.SellSession session)
	{
		final Item item = (session == null) ? null : player.getInventory().getItemByObjectId(session.objectId);
		if (item == null)
		{
			player.sendMessage("La sesion de venta expiro o el objeto ya no existe.");
			showSellList(player, 1);
			return;
		}
		final long commission = _manager.calculateCommission(session.price, MarketplaceConfig.COMMISSION_PERCENT);
		final long revenue = session.price - commission;
		final StringBuilder html = startPage("Confirmar publicacion", "Revisa precio, comision y duracion antes de publicar.");
		html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=000000><tr>");
		html.append("<td width=42 align=center><img src=\"").append(icon(item.getTemplate())).append("\" width=32 height=32></td>");
		html.append("<td width=240><font color=LEVEL>").append(escape(item.getName())).append("</font><br1><font color=777777>ID ").append(item.getId()).append(" / Object ").append(item.getObjectId()).append("</font></td>");
		html.append("<td width=70 align=center><font color=D8B66A>+").append(item.getEnchantLevel()).append("</font><br1><font color=777777>Enchant</font></td>");
		html.append("<td width=118 align=center><font color=").append(item.isAugmented() ? "5CA8FF" : "777777").append(">").append(item.isAugmented() ? "Augmented" : "No augment").append("</font></td>");
		html.append("</tr></table><br1>");
		
		html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=000000>");
		html.append("<tr><td width=130>Cantidad</td><td width=145><edit var=\"market_qty\" width=110 height=12></td><td width=195><font color=777777>Actual: ").append(format(session.quantity)).append(" / Disponible: ").append(format(item.getCount())).append("</font></td></tr>");
		html.append("<tr><td width=130>Precio total Rax</td><td width=145><edit var=\"market_price\" width=110 height=12></td><td width=195><font color=777777>Actual: ").append(format(session.price)).append("</font></td></tr>");
		html.append("<tr><td></td><td><button value=\"Calcular\" action=\"bypass _bbsmarket;quote;").append(session.token).append(";$market_qty;$market_price\" width=110 height=22 back=\"").append(BUTTON_BACK).append("\" fore=\"").append(BUTTON_FORE).append("\"></td><td></td></tr>");
		html.append("</table><br1>");
		
		html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=050505>");
		html.append("<tr><td width=160><font color=ae9977>Precio de venta</font></td><td width=310><font color=LEVEL>").append(format(session.price)).append(" Rax Coin</font></td></tr>");
		html.append("<tr><td><font color=ae9977>Comision Megarax</font></td><td><font color=CC6666>").append(format(commission)).append(" Rax Coin</font></td></tr>");
		html.append("<tr><td><font color=ae9977>Recibiras</font></td><td><font color=9ACD72>").append(format(revenue)).append(" Rax Coin</font></td></tr>");
		html.append("<tr><td><font color=ae9977>Duracion</font></td><td>").append(MarketplaceConfig.LISTING_DURATION_HOURS).append(" horas</td></tr>");
		html.append("</table><br>");
		html.append("<table width=").append(CONTENT_WIDTH).append("><tr>");
		html.append("<td width=235 align=right><button value=\"Confirmar\" action=\"bypass _bbsmarket;confirmSell;").append(session.token).append("\" width=128 height=24 back=\"").append(BUTTON_BACK).append("\" fore=\"").append(BUTTON_FORE).append("\"></td>");
		html.append("<td width=235 align=left><button value=\"Volver\" action=\"bypass _bbsmarket;sell;1\" width=128 height=24 back=\"").append(BUTTON_BACK).append("\" fore=\"").append(BUTTON_FORE).append("\"></td>");
		html.append("</tr></table>");
		endPage(html);
		send(player, html);
	}
	
	public void showListing(Player player, int listingId)
	{
		final MarketplaceListing listing = _dao.getListing(listingId);
		final StringBuilder html = startPage("Detalle de publicacion", "Confirma desde datos guardados en el servidor.");
		if ((listing == null) || (listing.status != MarketplaceStatus.ACTIVE))
		{
			empty(html, "Esta publicacion ya no esta disponible.");
		}
		else
		{
			final long balance = _manager.getCurrencyCount(player);
			html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=000000><tr>");
			html.append("<td width=42 align=center><img src=\"").append(icon(listing.item.itemId)).append("\" width=32 height=32></td>");
			html.append("<td width=240><font color=LEVEL>").append(escape(listing.item.itemName)).append("</font><br1><font color=777777>ID ").append(listing.item.itemId).append(" / ").append(listing.item.category.getLabel()).append("</font></td>");
			html.append("<td width=70 align=center><font color=D8B66A>+").append(listing.item.enchantLevel).append("</font><br1><font color=777777>Enchant</font></td>");
			html.append("<td width=118 align=center>").append(format(listing.price)).append("<br1><font color=777777>Rax Coin</font></td>");
			html.append("</tr></table><br1>");
			html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=000000>");
			html.append("<tr><td width=180>Vendedor</td><td>").append(escape(listing.sellerName)).append("</td></tr>");
			html.append("<tr><td>Cantidad</td><td>").append(format(listing.item.quantity)).append("</td></tr>");
			html.append("<tr><td>Comision incluida</td><td>").append(format(_manager.calculateCommission(listing.price, listing.commissionPercent))).append(" Rax Coin</td></tr>");
			html.append("<tr><td>Tu balance</td><td>").append(format(balance)).append(" Rax Coin</td></tr>");
			html.append("<tr><td>Balance restante</td><td>").append(format(Math.max(0, balance - listing.price))).append(" Rax Coin</td></tr>");
			html.append("<tr><td>Tiempo restante</td><td>").append(timeLeft(listing.expiresAt)).append("</td></tr>");
			html.append("</table><br>");
			html.append("<table width=").append(CONTENT_WIDTH).append("><tr>");
			html.append("<td width=235 align=right><button value=\"Comprar\" action=\"bypass _bbsmarket;buy;").append(listing.listingId).append("\" width=128 height=24 back=\"").append(BUTTON_BACK).append("\" fore=\"").append(BUTTON_FORE).append("\"></td>");
			html.append("<td width=235 align=left><button value=\"Volver\" action=\"bypass _bbsmarket;browse;1;all;recent\" width=128 height=24 back=\"").append(BUTTON_BACK).append("\" fore=\"").append(BUTTON_FORE).append("\"></td>");
			html.append("</tr></table>");
		}
		endPage(html);
		send(player, html);
	}
	
	public void showMyListings(Player player)
	{
		final StringBuilder html = startPage("Mis publicaciones", "Cancela solo publicaciones que sigan activas.");
		final List<MarketplaceListing> listings = _dao.getPlayerListings(player.getObjectId());
		if (listings.isEmpty())
		{
			empty(html, "No tienes publicaciones registradas.");
		}
		else
		{
			headerListings(html, "Accion");
			int row = 0;
			for (MarketplaceListing listing : listings)
			{
				rowStart(html, row++);
				html.append("<td width=36 align=center><img src=\"").append(icon(listing.item.itemId)).append("\" width=32 height=32></td>");
				html.append("<td width=155><font color=LEVEL>").append(escape(listing.item.itemName)).append("</font><br1><font color=777777>ID ").append(listing.listingId).append(" / ").append(timeLeft(listing.expiresAt)).append("</font></td>");
				html.append("<td width=42 align=center>").append(format(listing.item.quantity)).append("</td>");
				html.append("<td width=42 align=center><font color=D8B66A>+").append(listing.item.enchantLevel).append("</font></td>");
				html.append("<td width=70 align=center>").append(format(listing.price)).append("</td>");
				html.append("<td width=55 align=center>").append(statusColor(listing.status)).append("</td>");
				if (listing.status == MarketplaceStatus.ACTIVE)
				{
					html.append("<td width=70 align=center><button value=\"Quitar\" action=\"bypass _bbsmarket;cancel;").append(listing.listingId).append("\" width=68 height=20 back=\"").append(SMALL_BACK).append("\" fore=\"").append(SMALL_FORE).append("\"></td>");
				}
				else
				{
					html.append("<td width=70 align=center><font color=777777>-</font></td>");
				}
				rowEnd(html);
			}
		}
		endPage(html);
		send(player, html);
	}
	
	public void showGains(Player player)
	{
		final StringBuilder html = startPage("Mis ganancias", "Reclama las Rax Coin de ventas completadas.");
		final List<MarketplaceClaim> claims = _dao.getClaims(player.getObjectId());
		boolean empty = true;
		int row = 0;
		for (MarketplaceClaim claim : claims)
		{
			if (claim.claimType != MarketplaceClaimType.CURRENCY)
			{
				continue;
			}
			empty = false;
			rowStart(html, row++);
			html.append("<td width=260><font color=LEVEL>").append(escape(claim.item.itemName)).append("</font><br1><font color=777777>Venta #").append(claim.listingId).append(" / ").append(date(claim.createdAt)).append("</font></td>");
			html.append("<td width=110 align=center>").append(format(claim.currencyAmount)).append(" Rax</td>");
			html.append("<td width=100 align=center><button value=\"Cobrar\" action=\"bypass _bbsmarket;claimGain;").append(claim.claimId).append("\" width=88 height=20 back=\"").append(SMALL_BACK).append("\" fore=\"").append(SMALL_FORE).append("\"></td>");
			rowEnd(html);
		}
		if (empty)
		{
			empty(html, "No tienes ganancias pendientes.");
		}
		endPage(html);
		send(player, html);
	}
	
	public void showClaims(Player player)
	{
		final StringBuilder html = startPage("Objetos por reclamar", "Cancelados, vencidos, compras sin espacio y recuperaciones admin.");
		html.append("<table width=").append(CONTENT_WIDTH).append("><tr><td align=right><button value=\"Reclamar todo\" action=\"bypass _bbsmarket;claimAll\" width=128 height=22 back=\"").append(BUTTON_BACK).append("\" fore=\"").append(BUTTON_FORE).append("\"></td></tr></table>");
		final List<MarketplaceClaim> claims = _dao.getClaims(player.getObjectId());
		boolean empty = true;
		int row = 0;
		for (MarketplaceClaim claim : claims)
		{
			if (claim.claimType == MarketplaceClaimType.CURRENCY)
			{
				continue;
			}
			empty = false;
			rowStart(html, row++);
			html.append("<td width=42 align=center><img src=\"").append(icon(claim.item.itemId)).append("\" width=32 height=32></td>");
			html.append("<td width=205><font color=LEVEL>").append(escape(claim.item.itemName)).append("</font><br1><font color=777777>").append(claim.claimType.name()).append(" / Listing ").append(claim.listingId).append("</font></td>");
			html.append("<td width=60 align=center>").append(format(claim.item.quantity)).append("</td>");
			html.append("<td width=55 align=center>+").append(claim.item.enchantLevel).append("</td>");
			html.append("<td width=108 align=center><button value=\"Reclamar\" action=\"bypass _bbsmarket;claim;").append(claim.claimId).append("\" width=96 height=20 back=\"").append(SMALL_BACK).append("\" fore=\"").append(SMALL_FORE).append("\"></td>");
			rowEnd(html);
		}
		if (empty)
		{
			empty(html, "No tienes objetos pendientes por reclamar.");
		}
		endPage(html);
		send(player, html);
	}
	
	public void showHistory(Player player)
	{
		final StringBuilder html = startPage("Historial", "Compras, ventas, cancelaciones y expiraciones.");
		final List<MarketplaceHistoryEntry> entries = _dao.getHistory(player.getObjectId());
		if (entries.isEmpty())
		{
			empty(html, "No hay historial para mostrar.");
		}
		else
		{
			int row = 0;
			for (MarketplaceHistoryEntry entry : entries)
			{
				rowStart(html, row++);
				html.append("<td width=70><font color=ae9977>").append(entry.actionType).append("</font><br1><font color=777777>#").append(entry.listingId).append("</font></td>");
				html.append("<td width=190><font color=LEVEL>").append(escape(entry.itemName)).append("</font><br1><font color=777777>").append(date(entry.createdAt)).append("</font></td>");
				html.append("<td width=70 align=center>").append(format(entry.quantity)).append("</td>");
				html.append("<td width=85 align=center>").append(format(entry.price)).append(" Rax</td>");
				html.append("<td width=55 align=center>").append(format(entry.commission)).append("</td>");
				rowEnd(html);
			}
		}
		endPage(html);
		send(player, html);
	}
	
	public void showRules(Player player)
	{
		final StringBuilder html = startPage("Informacion y reglas", "El Marketplace convive con Private Store y Trade normal.");
		html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=000000>");
		rule(html, "Private Store", "Venta local, sin comision del Marketplace, con presencia fisica u offline si el servidor lo permite.");
		rule(html, "Marketplace", "Venta global desde Community Board, precio fijo en Rax Coin y comision al vender.");
		rule(html, "Seguridad", "El objeto publicado se retira del inventario y queda retenido por el sistema hasta venta, cancelacion o vencimiento.");
		rule(html, "Comision", "Se descuenta solo al completarse la venta. La ganancia se reclama manualmente en Mis ganancias.");
		rule(html, "Reclamaciones", "Objetos cancelados, vencidos o no entregados por inventario lleno quedan en Objetos por reclamar.");
		rule(html, "Protecciones", "No se confia en precio, itemId ni cantidad enviados por el cliente. El servidor consulta todo desde base de datos.");
		html.append("</table><br1>");
		html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=000000>");
		rule(html, "Moneda", "Rax Coin itemId " + MarketplaceConfig.CURRENCY_ITEM_ID + ".");
		rule(html, "Comision actual", MarketplaceConfig.COMMISSION_PERCENT + "%.");
		rule(html, "Duracion", MarketplaceConfig.LISTING_DURATION_HOURS + " horas.");
		rule(html, "Max publicaciones", String.valueOf(MarketplaceConfig.MAX_LISTINGS_PER_PLAYER));
		html.append("</table>");
		endPage(html);
		send(player, html);
	}
	
	private StringBuilder startPage(String title, String subtitle)
	{
		final StringBuilder html = new StringBuilder(8192);
		html.append("<html><body bgcolor=000000><br><center>");
		html.append("<table width=").append(BOARD_WIDTH).append(" height=448 bgcolor=000000><tr><td width=").append(MENU_WIDTH).append(" valign=top bgcolor=000000>");
		appendMenu(html);
		html.append("</td><td width=8 bgcolor=000000></td><td width=").append(CONTENT_WIDTH).append(" valign=top bgcolor=000000>");
		html.append("<table width=").append(CONTENT_WIDTH).append(" height=432 bgcolor=000000><tr><td valign=top>");
		html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=000000><tr>");
		html.append("<td width=250><font color=CDB67F>").append(title).append("</font><br1><font color=ae9977>").append(subtitle).append("</font></td>");
		html.append("<td width=80 align=center><font color=").append(MarketplaceConfig.ENABLED ? "9ACD72" : "CC6666").append(">").append(MarketplaceConfig.ENABLED ? "Enabled" : "Disabled").append("</font></td>");
		html.append("<td width=140 align=center><button value=\"Inicio\" action=\"bypass _bbsmarket\" width=112 height=22 back=\"").append(BUTTON_BACK).append("\" fore=\"").append(BUTTON_FORE).append("\"></td>");
		html.append("</tr></table><img src=\"L2UI.SquareGray\" width=").append(CONTENT_WIDTH).append(" height=1><br1>");
		return html;
	}
	
	private void endPage(StringBuilder html)
	{
		html.append("</td></tr></table></td></tr></table></center></body></html>");
	}
	
	private void appendMenu(StringBuilder html)
	{
		html.append("<table width=").append(MENU_WIDTH).append(" bgcolor=000000>");
		html.append("<tr><td height=4></td></tr>");
		html.append("<tr><td align=center><font color=CDB67F>Marketplace</font></td></tr>");
		html.append("<tr><td height=4></td></tr>");
		menuButton(html, "Inicio", "_bbsmarket");
		menuButton(html, "Explorar", "_bbsmarket;browse;1;all;recent");
		menuButton(html, "Vender", "_bbsmarket;sell;1");
		menuButton(html, "Mis ventas", "_bbsmarket;myListings");
		menuButton(html, "Ganancias", "_bbsmarket;gains");
		menuButton(html, "Reclamar", "_bbsmarket;claims");
		menuButton(html, "Historial", "_bbsmarket;history");
		menuButton(html, "Reglas", "_bbsmarket;rules");
		menuButton(html, "Community", "_bbstop");
		html.append("<tr><td height=18></td></tr></table>");
	}
	
	private void menuButton(StringBuilder html, String label, String bypass)
	{
		html.append("<tr><td align=center><button value=\"").append(label).append("\" action=\"bypass ").append(bypass).append("\" width=118 height=24 back=\"").append(BUTTON_BACK).append("\" fore=\"").append(BUTTON_FORE).append("\"></td></tr>");
	}
	
	private void appendSearch(StringBuilder html)
	{
		html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=000000>");
		html.append("<tr><td width=58><font color=ae9977>Nombre</font></td><td width=162><edit var=\"market_q\" width=150 height=12></td>");
		html.append("<td width=70><font color=ae9977>Vendedor</font></td><td width=180><edit var=\"market_seller\" width=168 height=12></td></tr>");
		html.append("</table>");
		html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=000000>");
		html.append("<tr><td width=58><font color=ae9977>Precio</font></td><td width=132><edit var=\"market_min\" width=54 height=12> - <edit var=\"market_max\" width=54 height=12></td>");
		html.append("<td width=70><font color=ae9977>Enchant</font></td><td width=112><edit var=\"market_emin\" width=45 height=12> - <edit var=\"market_emax\" width=45 height=12></td>");
		html.append("<td width=98 align=center><button value=\"Buscar\" action=\"bypass _bbsmarket;search;$market_q;$market_min;$market_max;$market_seller;$market_emin;$market_emax\" width=90 height=20 back=\"").append(SMALL_BACK).append("\" fore=\"").append(SMALL_FORE).append("\"></td></tr>");
		html.append("</table><br1>");
	}
	
	private void appendCategoryButtons(StringBuilder html)
	{
		html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=000000>");
		MarketplaceCategory[] categories = MarketplaceCategory.values();
		for (int i = 1; i < categories.length; i += 2)
		{
			html.append("<tr>");
			for (int j = 0; j < 2; j++)
			{
				if ((i + j) < categories.length)
				{
					final MarketplaceCategory category = categories[i + j];
					html.append("<td width=235 align=center><button value=\"").append(shortLabel(category.getLabel())).append("\" action=\"bypass _bbsmarket;browse;1;").append(category.getId()).append(";recent\" width=220 height=24 back=\"").append(CATEGORY_BACK).append("\" fore=\"").append(CATEGORY_FORE).append("\"></td>");
				}
				else
				{
					html.append("<td width=235></td>");
				}
			}
			html.append("</tr>");
		}
		html.append("</table><br1>");
	}
	
	private void appendRecentListings(StringBuilder html)
	{
		final MarketplaceFilter filter = new MarketplaceFilter();
		filter.page = 1;
		final List<MarketplaceListing> listings = _dao.getActiveListings(filter);
		html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=000000><tr><td><font color=LEVEL>Publicaciones recientes</font></td></tr></table>");
		appendListingRows(html, listings.size() > 5 ? listings.subList(0, 5) : listings, null);
	}
	
	private void appendRecentSales(StringBuilder html)
	{
		final List<MarketplaceHistoryEntry> sales = _dao.getRecentSales(4);
		html.append("<br1><table width=").append(CONTENT_WIDTH).append(" bgcolor=000000><tr><td><font color=LEVEL>Ventas recientes</font></td></tr></table>");
		if (sales.isEmpty())
		{
			empty(html, "Aun no hay ventas recientes.");
			return;
		}
		int row = 0;
		for (MarketplaceHistoryEntry sale : sales)
		{
			rowStart(html, row++);
			html.append("<td width=250>").append(escape(sale.itemName)).append("<br1><font color=777777>").append(date(sale.createdAt)).append("</font></td>");
			html.append("<td width=80 align=center>").append(format(sale.quantity)).append("</td>");
			html.append("<td width=140 align=center><font color=D8B66A>").append(format(sale.price)).append(" Rax</font></td>");
			rowEnd(html);
		}
	}
	
	private void appendListingRows(StringBuilder html, List<MarketplaceListing> listings, Player viewer)
	{
		headerListings(html, "Accion");
		if (listings.isEmpty())
		{
			empty(html, "No hay publicaciones para mostrar.");
			return;
		}
		int row = 0;
		for (MarketplaceListing listing : listings)
		{
			rowStart(html, row++);
			html.append("<td width=36 align=center><img src=\"").append(icon(listing.item.itemId)).append("\" width=32 height=32></td>");
			html.append("<td width=155><font color=LEVEL>").append(escape(listing.item.itemName)).append("</font><br1><font color=777777>").append(escape(listing.sellerName)).append(" / ").append(timeLeft(listing.expiresAt)).append("</font></td>");
			html.append("<td width=42 align=center>").append(format(listing.item.quantity)).append("</td>");
			html.append("<td width=42 align=center><font color=D8B66A>+").append(listing.item.enchantLevel).append("</font></td>");
			html.append("<td width=70 align=center>").append(format(listing.price)).append("</td>");
			html.append("<td width=55 align=center>").append(statusColor(listing.status)).append("</td>");
			html.append("<td width=70 align=center><button value=\"Ver\" action=\"bypass _bbsmarket;view;").append(listing.listingId).append("\" width=68 height=20 back=\"").append(SMALL_BACK).append("\" fore=\"").append(SMALL_FORE).append("\"></td>");
			rowEnd(html);
		}
	}
	
	private void headerListings(StringBuilder html, String actionLabel)
	{
		html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=000000><tr>");
		html.append("<td width=36></td><td width=155><font color=ae9977>Objeto</font></td><td width=42 align=center><font color=ae9977>Cant</font></td><td width=42 align=center><font color=ae9977>Enc</font></td><td width=70 align=center><font color=ae9977>Precio</font></td><td width=55 align=center><font color=ae9977>Estado</font></td><td width=70 align=center><font color=ae9977>").append(actionLabel).append("</font></td>");
		html.append("</tr></table>");
	}
	
	private void appendPages(StringBuilder html, String prefix, int page, int pages, String category, String sort)
	{
		html.append("<br1><table width=").append(CONTENT_WIDTH).append("><tr>");
		html.append("<td width=145 align=right>");
		if (page > 1)
		{
			html.append("<button value=\"Anterior\" action=\"bypass ").append(prefix).append(page - 1).append(";").append(category).append(";").append(sort).append("\" width=100 height=20 back=\"").append(SMALL_BACK).append("\" fore=\"").append(SMALL_FORE).append("\">");
		}
		html.append("</td><td width=180 align=center>Pagina ").append(page).append(" de ").append(pages).append("</td><td width=145 align=left>");
		if (page < pages)
		{
			html.append("<button value=\"Siguiente\" action=\"bypass ").append(prefix).append(page + 1).append(";").append(category).append(";").append(sort).append("\" width=100 height=20 back=\"").append(SMALL_BACK).append("\" fore=\"").append(SMALL_FORE).append("\">");
		}
		html.append("</td></tr></table>");
	}
	
	private void appendSimplePages(StringBuilder html, String prefix, int page, int pages)
	{
		html.append("<br1><table width=").append(CONTENT_WIDTH).append("><tr><td width=145 align=right>");
		if (page > 1)
		{
			html.append("<button value=\"Anterior\" action=\"bypass ").append(prefix).append(page - 1).append("\" width=100 height=20 back=\"").append(SMALL_BACK).append("\" fore=\"").append(SMALL_FORE).append("\">");
		}
		html.append("</td><td width=180 align=center>Pagina ").append(page).append(" de ").append(pages).append("</td><td width=145 align=left>");
		if (page < pages)
		{
			html.append("<button value=\"Siguiente\" action=\"bypass ").append(prefix).append(page + 1).append("\" width=100 height=20 back=\"").append(SMALL_BACK).append("\" fore=\"").append(SMALL_FORE).append("\">");
		}
		html.append("</td></tr></table>");
	}
	
	private void sortButton(StringBuilder html, MarketplaceFilter filter, MarketplaceSort sort)
	{
		html.append("<button value=\"").append(sort == filter.sort ? "*" : "").append(sort.getLabel().split(" ")[0]).append("\" action=\"bypass _bbsmarket;browse;1;").append(filter.category.getId()).append(";").append(sort.getId()).append("\" width=78 height=20 back=\"").append(SMALL_BACK).append("\" fore=\"").append(SMALL_FORE).append("\">");
	}
	
	private void sortRow(StringBuilder html, MarketplaceFilter filter)
	{
		html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=050505><tr><td align=center>");
		sortButton(html, filter, MarketplaceSort.ENCHANT_DESC);
		sortButton(html, filter, MarketplaceSort.EXPIRING);
		html.append("</td></tr></table>");
	}
	
	private List<Item> getSellCandidates(Player player)
	{
		final List<Item> items = new ArrayList<>();
		final Collection<Item> inventoryItems = player.getInventory().getItems();
		for (Item item : inventoryItems)
		{
			final long quantity = item.isStackable() ? item.getCount() : 1;
			if (MarketplaceItemValidator.validateItemForListing(player, item, quantity) == null)
			{
				items.add(item);
			}
		}
		items.sort(Comparator.comparing(Item::getName, String.CASE_INSENSITIVE_ORDER));
		return items;
	}
	
	private void stat(StringBuilder html, String label, long value)
	{
		html.append("<td width=117 align=center><font color=D8B66A>").append(format(value)).append("</font><br1><font color=777777>").append(label).append("</font></td>");
	}
	
	private void rule(StringBuilder html, String title, String text)
	{
		html.append("<tr><td width=120><font color=LEVEL>").append(title).append("</font></td><td width=350><font color=ae9977>").append(text).append("</font></td></tr>");
	}
	
	private void empty(StringBuilder html, String text)
	{
		html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=000000><tr><td height=58 align=center><font color=ae9977>").append(text).append("</font></td></tr></table>");
	}
	
	private void rowStart(StringBuilder html, int row)
	{
		html.append("<table width=").append(CONTENT_WIDTH).append(" bgcolor=").append((row % 2) == 0 ? "000000" : "090909").append("><tr>");
	}
	
	private void rowEnd(StringBuilder html)
	{
		html.append("</tr></table><img src=\"L2UI.SquareGray\" width=").append(CONTENT_WIDTH).append(" height=1>");
	}
	
	private String statusColor(MarketplaceStatus status)
	{
		switch (status)
		{
			case ACTIVE:
				return "<font color=9ACD72>Activo</font>";
			case SOLD:
				return "<font color=D8B66A>Vendido</font>";
			case CANCELLED:
				return "<font color=CC6666>Cancelado</font>";
			case EXPIRED:
				return "<font color=999999>Vencido</font>";
			case CLAIMED:
				return "<font color=5CA8FF>Reclamado</font>";
			case ADMIN_REMOVED:
				return "<font color=5CA8FF>Admin</font>";
			default:
				return "<font color=777777>" + status.name() + "</font>";
		}
	}
	
	private String icon(int itemId)
	{
		return icon(ItemData.getInstance().getTemplate(itemId));
	}
	
	private String icon(ItemTemplate template)
	{
		if (template == null)
		{
			return "icon.NOIMAGE";
		}
		final String icon = template.getIcon();
		return ((icon == null) || icon.isEmpty()) ? "icon.NOIMAGE" : icon;
	}
	
	private String timeLeft(long expiresAt)
	{
		final long millis = expiresAt - System.currentTimeMillis();
		if (millis <= 0)
		{
			return "vencido";
		}
		final long hours = millis / (60L * 60L * 1000L);
		final long days = hours / 24L;
		final long leftHours = hours % 24L;
		if (days > 0)
		{
			return days + "d " + leftHours + "h";
		}
		return Math.max(1, leftHours) + "h";
	}
	
	private String date(long millis)
	{
		return millis <= 0 ? "-" : DATE.format(new Date(millis));
	}
	
	private String format(long value)
	{
		return NUMBER.format(value);
	}
	
	private String shortLabel(String value)
	{
		return value.length() > 16 ? value.substring(0, 16) : value;
	}
	
	private String escape(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
	
	private void send(Player player, StringBuilder html)
	{
		CommunityBoardHandler.separateAndSend(html.toString(), player);
	}
}
