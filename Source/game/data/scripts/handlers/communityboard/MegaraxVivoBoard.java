package handlers.communityboard;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.l2jmobius.gameserver.cache.HtmCache;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.handler.IParseBoardHandler;
import org.l2jmobius.gameserver.model.actor.Player;

import custom.MegaraxVivo.MegaraxVivoConfig;
import custom.MegaraxVivo.MegaraxVivoService;
import custom.MegaraxVivo.MegaraxVivoService.CategoryView;
import custom.MegaraxVivo.MegaraxVivoService.ChangeView;
import custom.MegaraxVivo.MegaraxVivoService.ChronicleView;
import custom.MegaraxVivo.MegaraxVivoService.ContributorView;
import custom.MegaraxVivo.MegaraxVivoService.Intention;
import custom.MegaraxVivo.MegaraxVivoService.NeedView;
import custom.MegaraxVivo.MegaraxVivoService.ReceiptView;
import custom.MegaraxVivo.MegaraxVivoService.WorldStateView;

/** Community Board for El Pulso, Mi Huella, Libro Abierto and the Chronicle feed. */
public class MegaraxVivoBoard implements IParseBoardHandler
{
	private static final String[] COMMANDS =
	{
		"_bbsvivo",
		"_bbsvivo_pulse",
		"_bbsvivo_footprint",
		"_bbsvivo_openbook",
		"_bbsvivo_chronicle",
		"_bbsvivo_intention"
	};
	private static final String PAGE = "data/html/CommunityBoard/Custom/vivo/main.html";
	private static final String NAVIGATION = "data/html/CommunityBoard/Custom/navigation.html";
	private static final String BUTTON_BACK = "L2UI_CH3.Button.bigbutton2_down";
	private static final String BUTTON_FORE = "L2UI_CH3.Button.bigbutton2";
	private static final int WIDTH = 500;

	private final MegaraxVivoService service;

	public MegaraxVivoBoard()
	{
		service = MegaraxVivoService.getInstance();
	}

	@Override
	public String[] getCommandList()
	{
		return COMMANDS;
	}

	@Override
	public boolean onCommand(String command, Player player)
	{
		if ((player == null) || !MegaraxVivoConfig.BOARD_ENABLED)
		{
			return false;
		}
		CommunityBoardHandler.getInstance().addBypass(player, "MegaRax VIVO", command);
		if (command.startsWith("_bbsvivo_intention"))
		{
			final String value = command.substring("_bbsvivo_intention".length()).trim();
			if (service.setIntention(player, value))
			{
				player.sendMessage("MegaRax VIVO: session intention updated.");
			}
			showPulse(player);
		}
		else if (command.startsWith("_bbsvivo_footprint"))
		{
			showFootprint(player);
		}
		else if (command.startsWith("_bbsvivo_openbook"))
		{
			showOpenBook(player);
		}
		else if (command.startsWith("_bbsvivo_chronicle"))
		{
			showChronicle(player);
		}
		else
		{
			showPulse(player);
		}
		return true;
	}

	private void showPulse(Player player)
	{
		final StringBuilder html = pageStart("EL PULSO", "Que esta pasando, donde eres util y que cambiara.", "pulse");
		if (!appendServiceState(html))
		{
			send(player, html);
			return;
		}

		appendIntention(html, player);
		section(html, "ESTADO DE LOS REINOS");
		final List<WorldStateView> states = service.getWorldStates();
		if (states.isEmpty())
		{
			empty(html, "Los estados regionales aun no estan disponibles.");
		}
		else
		{
			for (WorldStateView state : states)
			{
				html.append("<table width=").append(WIDTH).append(" bgcolor=07090D><tr><td width=18></td><td width=105><font color=F0D58A>").append(h(state.regionName)).append("</font><br1><font color=696969>v").append(state.version).append("</font></td>");
				html.append("<td width=115><font color=AFAFAF>Seguridad</font><br1><font color=").append(valueColor(state.security)).append(">").append(state.security).append("/100</font></td>");
				html.append("<td width=115><font color=AFAFAF>Abastecimiento</font><br1><font color=").append(valueColor(state.supply)).append(">").append(state.supply).append("/100</font></td>");
				html.append("<td width=125><font color=AFAFAF>Animo</font><br1><font color=").append(valueColor(state.spirit)).append(">").append(state.spirit).append("/100</font></td><td width=22></td></tr></table><img src=L2UI.SquareGray width=500 height=1>");
			}
		}

		section(html, "NECESIDAD PRINCIPAL");
		final List<NeedView> needs = service.getNeeds();
		if (needs.isEmpty())
		{
			empty(html, "No hay necesidades publicadas.");
		}
		else
		{
			appendNeedCard(html, needs.get(0), false);
		}
		send(player, html);
	}

	private void appendIntention(StringBuilder html, Player player)
	{
		final Intention current = service.getIntention(player);
		section(html, "TU INTENCION DE SESION");
		html.append("<table width=").append(WIDTH).append(" bgcolor=050505><tr><td width=18></td><td width=464><font color=F0D58A>").append(h(current.title)).append("</font><br1><font color=AFAFAF>").append(h(current.description)).append("</font><br1><font color=696969>Personaliza El Pulso; no multiplica premios ni bloquea actividades.</font></td><td width=18></td></tr></table><br1>");
		final Intention[] intentions = Intention.values();
		html.append("<table width=480>");
		for (int i = 0; i < intentions.length; i++)
		{
			if ((i % 3) == 0)
			{
				html.append("<tr>");
			}
			final Intention intention = intentions[i];
			html.append("<td width=160 align=center><button value=\"").append(h(shortIntention(intention))).append("\" action=\"bypass _bbsvivo_intention ").append(intention.name()).append("\" width=150 height=22 back=\"").append(BUTTON_BACK).append("\" fore=\"").append(BUTTON_FORE).append("\"></td>");
			if ((i % 3) == 2)
			{
				html.append("</tr>");
			}
		}
		html.append("</table><br1>");
	}

	private void showFootprint(Player player)
	{
		final StringBuilder html = pageStart("MI HUELLA", "Comprobantes personales, no un ranking infinito.", "footprint");
		if (!appendServiceState(html))
		{
			send(player, html);
			return;
		}
		final List<ReceiptView> receipts = service.getReceipts(player, MegaraxVivoConfig.RECENT_RECEIPT_LIMIT);
		section(html, "COMPROBANTES RECIENTES");
		if (receipts.isEmpty())
		{
			empty(html, "Aun no tienes huellas VIVO. El piloto esta en preparacion y no altera tus sistemas actuales.");
		}
		else
		{
			for (ReceiptView receipt : receipts)
			{
				html.append("<table width=").append(WIDTH).append(" bgcolor=07090D><tr><td width=14></td><td width=330><font color=F0D58A>+").append(receipt.acceptedAmount).append(" ").append(h(categoryName(receipt.category))).append("</font><br1><font color=C0C0C0>").append(h(MegaraxVivoService.friendlyKey(receipt.regionKey))).append(" · ").append(h(MegaraxVivoService.friendlyKey(receipt.sourceType))).append("</font><br1><font color=696969>Base ").append(receipt.baseAmount).append(" x ").append(receipt.multiplier).append(" · recibo #").append(receipt.ledgerId).append("</font></td><td width=140 align=right><font color=AFAFAF>").append(formatDate(receipt.createdAt)).append("</font></td><td width=16></td></tr></table><img src=L2UI.SquareGray width=500 height=1>");
			}
		}

		section(html, "QUE SIGNIFICA UNA HUELLA");
		html.append("<table width=").append(WIDTH).append(" bgcolor=050505><tr><td width=20></td><td width=460><font color=C0C0C0>Cada fila conserva fuente, categoria, base, multiplicador publico, cantidad aceptada, region y hora.<br1>Una clave unica evita aplicar dos veces la misma accion. Premium no aumenta el aporte.</font></td><td width=20></td></tr></table>");
		send(player, html);
	}

	private void showOpenBook(Player player)
	{
		final StringBuilder html = pageStart("LIBRO ABIERTO", "Reglas, limites, consecuencias y cambios comprobables.", "openbook");
		if (!appendServiceState(html))
		{
			send(player, html);
			return;
		}

		section(html, "CONTRIBUCION PUBLICA");
		html.append("<table width=").append(WIDTH).append(" bgcolor=050505><tr><td width=20></td><td width=460><font color=F0D58A>Aporte = base x relevancia x diversidad x colaboracion</font><br1><font color=C0C0C0>Fundacion actual: multiplicador publico 1.000. Maximo por accion: ").append(MegaraxVivoConfig.MAXIMUM_SINGLE_CONTRIBUTION).append(". Cada necesidad publica su cap diario.<br1>No se crea Adena, Rax Coin, items ni recompensas en esta fase.</font></td><td width=20></td></tr></table>");

		for (NeedView need : service.getNeeds())
		{
			section(html, "REGLAS DE " + need.regionKey);
			appendNeedCard(html, need, true);
		}

		section(html, "REGISTRO DE CAMBIOS");
		final List<ChangeView> changes = service.getChangeLog();
		if (changes.isEmpty())
		{
			empty(html, "No hay cambios VIVO publicados.");
		}
		else
		{
			for (ChangeView change : changes)
			{
				html.append("<table width=").append(WIDTH).append(" bgcolor=07090D><tr><td width=16></td><td width=468><font color=F0D58A>").append(h(change.versionLabel)).append(" · ").append(h(change.title)).append("</font><br1><font color=AFAFAF>Antes: ").append(h(change.beforeValue)).append("</font><br1><font color=C0C0C0>Ahora: ").append(h(change.afterValue)).append("</font><br1><font color=696969>Motivo: ").append(h(change.reason)).append(" · ").append(formatDate(change.effectiveAt)).append("</font></td><td width=16></td></tr></table><img src=L2UI.SquareGray width=500 height=1>");
			}
		}
		send(player, html);
	}

	private void showChronicle(Player player)
	{
		final StringBuilder html = pageStart("SALON DE LA CRONICA", "Memoria publica leida sin modificar la Cronica existente.", "chronicle");
		if (!appendServiceState(html))
		{
			send(player, html);
			return;
		}
		section(html, "ACONTECIMIENTOS RECIENTES");
		final List<ChronicleView> entries = service.getChronicleEntries(10);
		if (entries.isEmpty())
		{
			empty(html, "La Cronica no tiene entradas publicas disponibles. VIVO no crea ni borra sus registros.");
		}
		else
		{
			for (ChronicleView entry : entries)
			{
				html.append("<table width=").append(WIDTH).append(" bgcolor=07090D><tr><td width=16></td><td width=350><font color=F0D58A>").append(h(entry.playerName)).append("</font><br1><font color=C0C0C0>").append(h(entry.title)).append("</font><br1><font color=696969>").append(h(entry.details)).append("</font></td><td width=118 align=right><font color=AFAFAF>").append(formatDate(entry.eventTime)).append("</font></td><td width=16></td></tr></table><img src=L2UI.SquareGray width=500 height=1>");
			}
		}
		section(html, "COMPROMISO");
		html.append("<table width=").append(WIDTH).append(" bgcolor=050505><tr><td width=20></td><td width=460><font color=C0C0C0>Esta vista es de solo lectura. La Crónica Viva conserva sus propias tablas, reglas, NPC y recompensas. VIVO solo presenta sus acontecimientos publicos dentro de un lenguaje comun.</font></td><td width=20></td></tr></table>");
		send(player, html);
	}

	private void appendNeedCard(StringBuilder html, NeedView need, boolean detailed)
	{
		final int percent = percent(need.progress, need.goal);
		html.append("<table width=").append(WIDTH).append(" bgcolor=07090D><tr><td width=18></td><td width=464><font color=F0D58A>").append(h(need.title)).append("</font><br1><font color=AFAFAF>").append(h(need.description)).append("</font><br1><font color=CDB67F>Estado: </font><font color=").append(statusColor(need.status)).append(">").append(h(statusName(need.status))).append("</font> · <font color=CDB67F>Region: </font>").append(h(MegaraxVivoService.friendlyKey(need.regionKey))).append("<br1><font color=CDB67F>Progreso: </font>").append(need.progress).append("/").append(need.goal).append(" (").append(percent).append("%)</td><td width=18></td></tr></table>");
		appendProgressBar(html, percent);
		for (CategoryView category : need.categories)
		{
			html.append("<table width=").append(WIDTH).append(" bgcolor=050505><tr><td width=24></td><td width=220><font color=C0C0C0>").append(h(categoryName(category.category))).append("</font></td><td width=230 align=right><font color=AFAFAF>").append(category.progress).append("/").append(category.goal).append(" · ").append(percent(category.progress, category.goal)).append("%</font></td><td width=26></td></tr></table>");
		}
		html.append("<table width=").append(WIDTH).append(" bgcolor=050505><tr><td width=20></td><td width=460><font color=66CC88>Si se completa:</font> <font color=C0C0C0>").append(h(need.successEffect)).append("</font><br1><font color=E0B85C>Si no se completa:</font> <font color=C0C0C0>").append(h(need.failureEffect)).append("</font>");
		if (detailed)
		{
			html.append("<br1><font color=696969>Cap diario por cuenta: ").append(need.personalDailyCap).append(" · Eje: ").append(h(axisName(need.axis))).append(" · Delta al exito: +").append(need.successDelta).append(" · Version ").append(need.version).append("</font>");
		}
		if ("PREPARATION".equals(need.status))
		{
			html.append("<br1><font color=DD8888>Preparacion privada: los adaptadores de gameplay no estan activos; toda contribucion es rechazada de forma segura.</font>");
		}
		html.append("</td><td width=20></td></tr></table>");

		final List<ContributorView> contributors = service.getRecentContributors(need.needKey, MegaraxVivoConfig.RECENT_CONTRIBUTOR_LIMIT);
		if (!contributors.isEmpty())
		{
			html.append("<br1><table width=").append(WIDTH).append(" bgcolor=000000><tr><td align=center><font color=CDB67F>HUELLAS RECIENTES</font></td></tr></table>");
			for (ContributorView contributor : contributors)
			{
				html.append("<table width=").append(WIDTH).append(" bgcolor=050505><tr><td width=24></td><td width=250><font color=C0C0C0>").append(h(contributor.playerName)).append("</font></td><td width=200 align=right><font color=AFAFAF>").append(h(categoryName(contributor.category))).append(" +").append(contributor.totalAmount).append("</font></td><td width=26></td></tr></table>");
			}
		}
	}

	private boolean appendServiceState(StringBuilder html)
	{
		if (!service.isEnabled())
		{
			empty(html, "MegaRax VIVO esta desactivado. Los sistemas existentes continúan sin cambios.");
			return false;
		}
		if (!service.isReady())
		{
			empty(html, "MegaRax VIVO no pudo inicializar su capa propia. Ningun sistema existente fue modificado. Diagnostico: " + service.getLastError());
			return false;
		}
		return true;
	}

	private StringBuilder pageStart(String title, String subtitle, String activeTab)
	{
		final StringBuilder html = new StringBuilder(10000);
		html.append("<table width=").append(WIDTH).append(" bgcolor=000000><tr><td height=8></td></tr><tr><td align=center><font color=F0D58A>").append(h(title)).append("</font><br1><font color=AFAFAF>").append(h(subtitle)).append("</font></td></tr><tr><td height=8></td></tr></table>");
		html.append("<table width=").append(WIDTH).append("><tr>");
		tab(html, "EL PULSO", "_bbsvivo_pulse", "pulse".equals(activeTab));
		tab(html, "MI HUELLA", "_bbsvivo_footprint", "footprint".equals(activeTab));
		tab(html, "LIBRO ABIERTO", "_bbsvivo_openbook", "openbook".equals(activeTab));
		tab(html, "CRONICA", "_bbsvivo_chronicle", "chronicle".equals(activeTab));
		html.append("</tr></table><img src=L2UI.SquareGray width=500 height=1><br1>");
		return html;
	}

	private void tab(StringBuilder html, String label, String command, boolean active)
	{
		html.append("<td width=125 align=center><button value=\"").append(label).append("\" action=\"bypass ").append(command).append("\" width=120 height=24 back=\"").append(active ? "L2UI_CH3.Button.bigbutton2_down" : BUTTON_BACK).append("\" fore=\"").append(BUTTON_FORE).append("\"></td>");
	}

	private void section(StringBuilder html, String title)
	{
		html.append("<br1><table width=").append(WIDTH).append(" bgcolor=000000><tr><td height=22 align=center><font color=CDB67F>").append(h(title)).append("</font></td></tr></table><img src=L2UI.SquareGray width=500 height=1>");
	}

	private void empty(StringBuilder html, String message)
	{
		html.append("<table width=").append(WIDTH).append(" bgcolor=050505><tr><td width=20></td><td width=460 align=center><font color=AFAFAF>").append(h(message)).append("</font></td><td width=20></td></tr></table>");
	}

	private void appendProgressBar(StringBuilder html, int percent)
	{
		html.append("<table width=").append(WIDTH).append(" bgcolor=050505><tr><td width=20></td>");
		if (percent <= 0)
		{
			html.append("<td width=460 height=8 bgcolor=202020></td>");
		}
		else if (percent >= 100)
		{
			html.append("<td width=460 height=8 bgcolor=7A5A1F></td>");
		}
		else
		{
			final int filled = (460 * percent) / 100;
			html.append("<td width=").append(filled).append(" height=8 bgcolor=5A451A></td><td width=").append(460 - filled).append(" height=8 bgcolor=202020></td>");
		}
		html.append("<td width=20></td></tr></table>");
	}

	private void send(Player player, StringBuilder content)
	{
		String page = HtmCache.getInstance().getHtm(player, PAGE);
		if (page == null)
		{
			page = "<html><body><center>%content%</center></body></html>";
		}
		String navigation = HtmCache.getInstance().getHtm(player, NAVIGATION);
		if (navigation == null)
		{
			navigation = "";
		}
		page = page.replace("%navigation%", navigation).replace("%content%", content.toString());
		CommunityBoardHandler.separateAndSend(page, player);
	}

	private static int percent(long progress, long goal)
	{
		if (goal <= 0)
		{
			return 0;
		}
		return (int) Math.max(0, Math.min(100, (progress * 100L) / goal));
	}

	private static String valueColor(int value)
	{
		return value >= 80 ? "F0D58A" : value >= 60 ? "66CC88" : value >= 40 ? "C0C0C0" : value >= 20 ? "E0B85C" : "DD6666";
	}

	private static String statusColor(String status)
	{
		return "ACTIVE".equals(status) ? "66CC88" : "COMPLETED".equals(status) ? "F0D58A" : "FAILED".equals(status) ? "DD6666" : "E0B85C";
	}

	private static String statusName(String status)
	{
		if ("PREPARATION".equals(status)) return "PREPARACION PRIVADA";
		if ("ACTIVE".equals(status)) return "ACTIVO";
		if ("PAUSED".equals(status)) return "PAUSADO";
		if ("COMPLETED".equals(status)) return "COMPLETADO";
		if ("FAILED".equals(status)) return "CONSECUENCIA DE RECUPERACION";
		return MegaraxVivoService.friendlyKey(status).toUpperCase();
	}

	private static String categoryName(String category)
	{
		if ("SUPPLY".equals(category)) return "Suministros";
		if ("CRAFT".equals(category)) return "Trabajo artesanal";
		if ("SPOIL".equals(category)) return "Piezas recuperadas";
		if ("SERVICE".equals(category)) return "Servicio";
		if ("ESCORT".equals(category)) return "Escolta";
		return MegaraxVivoService.friendlyKey(category);
	}

	private static String axisName(String axis)
	{
		if ("SECURITY".equals(axis)) return "Seguridad";
		if ("SUPPLY".equals(axis)) return "Abastecimiento";
		if ("SPIRIT".equals(axis)) return "Animo";
		return MegaraxVivoService.friendlyKey(axis);
	}

	private static String shortIntention(Intention intention)
	{
		switch (intention)
		{
			case SERVE_OTHERS:
				return "Servir";
			case PRODUCE_TRADE:
				return "Producir";
			case CONTINUE_STORY:
				return "Mi historia";
			default:
				return intention.title;
		}
	}

	private static String formatDate(long value)
	{
		return value <= 0 ? "Pendiente" : new SimpleDateFormat("MM-dd HH:mm").format(new Date(value));
	}

	private static String h(String value)
	{
		if (value == null)
		{
			return "";
		}
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
}
