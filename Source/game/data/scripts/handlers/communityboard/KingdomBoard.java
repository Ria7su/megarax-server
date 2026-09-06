package handlers.communityboard;

import org.l2jmobius.gameserver.data.sql.CharInfoTable;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.handler.IParseBoardHandler;
import org.l2jmobius.gameserver.model.actor.Player;

import custom.MegaraxKingdomEconomy.Kingdom;
import custom.MegaraxKingdomEconomy.KingdomContribution;
import custom.MegaraxKingdomEconomy.KingdomEconomyConfig;
import custom.MegaraxKingdomEconomy.KingdomEconomyManager;
import custom.MegaraxKingdomEconomy.KingdomEconomyUtil;
import custom.MegaraxKingdomEconomy.KingdomLevel;
import custom.MegaraxKingdomEconomy.KingdomRelation;
import custom.MegaraxKingdomEconomy.KingdomTerritory;
import custom.MegaraxKingdomEconomy.KingdomTreasury;

public class KingdomBoard implements IParseBoardHandler
{
	private static final int WIDTH = 610;
	private static final String BUTTON_BACK = "Galaxy.105_OVER";
	private static final String BUTTON_FORE = "Galaxy.105";
	private static final String BIG_BACK = "Galaxy.105_OVER";
	private static final String BIG_FORE = "Galaxy.105";
	private static final String RAX_ICON = "favero_l2_icons01.coin_11th_token";
	private static final String ADENA_ICON = "icon.etc_adena_i00";
	private static final String[] COMMANDS =
	{
		"_bbskingdom"
	};
	
	private final KingdomEconomyManager _manager = KingdomEconomyManager.getInstance();
	
	@Override
	public String[] getCommandList()
	{
		return COMMANDS;
	}
	
	@Override
	public boolean onCommand(String command, Player player)
	{
		_manager.init();
		final String[] args = command.split(";", -1);
		final String page = args.length > 1 ? args[1] : "overview";
		if ("map".equals(page))
		{
			showMap(player);
		}
		else if ("kingdom".equals(page) && (args.length > 2))
		{
			showKingdom(player, parseInt(args[2]));
		}
		else if ("leader".equals(page))
		{
			showLeader(player);
		}
		else if ("ranking".equals(page))
		{
			showRanking(player);
		}
		else if ("contrib".equals(page))
		{
			showContribution(player);
		}
		else if ("events".equals(page))
		{
			showEvents(player);
		}
		else
		{
			showOverview(player);
		}
		return false;
	}
	
	private void showOverview(Player player)
	{
		final KingdomTerritory territory = _manager.findTerritory(player);
		final Kingdom current = territory == null ? null : KingdomEconomyConfig.getKingdom(territory.castleId);
		final StringBuilder html = startPage("Kingdom Economy", "The castles are living economies");
		html.append("<table width=").append(WIDTH).append(" bgcolor=000000><tr>");
		html.append("<td width=44 align=center><img src=\"").append(RAX_ICON).append("\" width=32 height=32></td>");
		html.append("<td width=566><font color=LEVEL>El mundo pertenece a quienes lo conquistan</font><br1><font color=ae9977>Farm, taxes, treasury and royal contribution.</font></td>");
		html.append("</tr></table><br>");
		if (current != null)
		{
			final KingdomRelation relation = current.getRelation(player);
			final KingdomTreasury treasury = _manager.getTreasury(current.castleId);
			html.append("<table width=").append(WIDTH).append(" bgcolor=000000><tr><td align=center><font color=LEVEL>You are in the Kingdom of ").append(h(current.name)).append("</font><br1>");
			html.append("<font color=ae9977>").append(h(territory.name)).append(" - ruled by ").append(h(current.getOwnerName())).append("</font></td></tr></table>");
			html.append("<table width=").append(WIDTH).append("><tr><td width=150>Relation</td><td width=155>").append(relation.name()).append("</td><td width=150>Royal Tax</td><td width=155>").append(current.getTaxPercent(relation)).append("%</td></tr>");
			html.append("<tr><td>Kingdom Level</td><td>").append(treasury.level).append("</td><td>Prosperity</td><td>").append(current.prosperity).append("%</td></tr></table><br>");
		}
		else
		{
			html.append("<table width=").append(WIDTH).append(" bgcolor=000000><tr><td align=center><font color=ae9977>You are outside a configured Kingdom territory.</font></td></tr></table><br>");
		}
		appendKingdomRows(html, false);
		endPage(html, player);
	}
	
	private void showMap(Player player)
	{
		final StringBuilder html = startPage("Kingdom Map", "Territories loaded from XML");
		for (Kingdom kingdom : KingdomEconomyConfig.getKingdoms())
		{
			final KingdomTreasury treasury = _manager.getTreasury(kingdom.castleId);
			html.append("<table width=").append(WIDTH).append(" bgcolor=000000><tr><td width=150><font color=LEVEL>").append(h(kingdom.name)).append("</font></td><td width=180>").append(h(kingdom.getOwnerName())).append("</td><td width=100>Level ").append(treasury.level).append("</td><td width=180 align=right>").append(KingdomEconomyUtil.format(treasury.balance)).append(" Adena</td></tr></table>");
			html.append("<table width=").append(WIDTH).append("><tr><td width=230>Territory</td><td width=110>Activity</td><td width=270>Bounds</td></tr>");
			for (KingdomTerritory territory : kingdom.getTerritories())
			{
				html.append("<tr><td>").append(h(territory.name)).append("</td><td>").append(_manager.getTerritoryActivity(territory.id)).append("</td><td><font color=777777>").append(territory.minX).append(",").append(territory.minY).append(" to ").append(territory.maxX).append(",").append(territory.maxY).append("</font></td></tr>");
			}
			html.append("</table><img src=\"L2UI.SquareGray\" width=").append(WIDTH).append(" height=1><br1>");
		}
		endPage(html, player);
	}
	
	private void showKingdom(Player player, int castleId)
	{
		final Kingdom kingdom = KingdomEconomyConfig.getKingdom(castleId);
		if (kingdom == null)
		{
			sendMessage(player, "Kingdom", "Kingdom not found.");
			return;
		}
		
		final KingdomTreasury treasury = _manager.getTreasury(castleId);
		final KingdomLevel level = KingdomEconomyConfig.getLevel(treasury.level);
		final StringBuilder html = startPage("Kingdom of " + kingdom.name, kingdom.getOwnerName());
		html.append("<table width=").append(WIDTH).append(" bgcolor=000000><tr><td width=44><img src=\"").append(ADENA_ICON).append("\" width=32 height=32></td><td width=566>");
		html.append("<font color=LEVEL>Treasury: ").append(KingdomEconomyUtil.format(treasury.balance)).append(" Adena</font><br1>");
		html.append("<font color=ae9977>Total income: ").append(KingdomEconomyUtil.format(treasury.totalIncome)).append(" - Today: ").append(KingdomEconomyUtil.format(treasury.todayIncome)).append("</font></td></tr></table><br>");
		html.append("<table width=").append(WIDTH).append("><tr><td width=150>Level</td><td width=155>").append(treasury.level).append("</td><td width=150>Prosperity</td><td width=155>").append(kingdom.prosperity).append("%</td></tr>");
		html.append("<tr><td>Benefit</td><td>").append(level == null ? "Configured" : h(level.title)).append("</td><td>Owner Bonus</td><td>").append(kingdom.rulerAdenaBonusPercent).append("% Adena</td></tr>");
		html.append("<tr><td>Neutral Tax</td><td>").append(kingdom.neutralTaxPercent).append("%</td><td>Enemy Tax</td><td>").append(kingdom.enemyTaxPercent).append("%</td></tr></table><br>");
		html.append("<table width=").append(WIDTH).append(" bgcolor=000000><tr><td>Territories</td></tr></table>");
		for (KingdomTerritory territory : kingdom.getTerritories())
		{
			html.append("<table width=").append(WIDTH).append("><tr><td width=300>").append(h(territory.name)).append("</td><td width=180>Activity ").append(_manager.getTerritoryActivity(territory.id)).append("</td><td width=130></td></tr></table>");
		}
		endPage(html, player);
	}
	
	private void showLeader(Player player)
	{
		final Kingdom kingdom = getOwnedKingdom(player);
		if (kingdom == null)
		{
			sendMessage(player, "Leader Panel", "Your clan does not rule a configured kingdom.");
			return;
		}
		final KingdomTreasury treasury = _manager.getTreasury(kingdom.castleId);
		final StringBuilder html = startPage("Leader Panel", "Royal administration for " + kingdom.name);
		html.append("<table width=").append(WIDTH).append(" bgcolor=000000><tr><td width=150>Treasury</td><td width=155 align=right>").append(KingdomEconomyUtil.format(treasury.balance)).append("</td><td width=150>Payroll Fund</td><td width=155>").append(treasury.payrollPercent).append("%</td></tr>");
		html.append("<tr><td>Total Income</td><td align=right>").append(KingdomEconomyUtil.format(treasury.totalIncome)).append("</td><td>Total Spent</td><td align=right>").append(KingdomEconomyUtil.format(treasury.totalSpent)).append("</td></tr>");
		html.append("<tr><td>Week Income</td><td align=right>").append(KingdomEconomyUtil.format(treasury.weeklyIncome)).append("</td><td>Month Income</td><td align=right>").append(KingdomEconomyUtil.format(treasury.monthlyIncome)).append("</td></tr></table><br>");
		html.append("<table width=").append(WIDTH).append(" bgcolor=000000><tr><td>Most active contributors</td></tr></table>");
		appendContributors(html, kingdom.castleId);
		endPage(html, player);
	}
	
	private void showRanking(Player player)
	{
		final StringBuilder html = startPage("Global Ranking", "Top kingdoms by treasury");
		html.append("<table width=").append(WIDTH).append(" bgcolor=000000><tr><td width=40>#</td><td width=140>Kingdom</td><td width=170>Ruler</td><td width=90>Level</td><td width=170 align=right>Treasury</td></tr></table>");
		int rank = 1;
		for (KingdomTreasury treasury : _manager.getTopTreasuries(20))
		{
			final Kingdom kingdom = KingdomEconomyConfig.getKingdom(treasury.castleId);
			if (kingdom == null)
			{
				continue;
			}
			html.append("<table width=").append(WIDTH).append("><tr><td width=40>").append(rank++).append("</td><td width=140>").append(h(kingdom.name)).append("</td><td width=170>").append(h(kingdom.getOwnerName())).append("</td><td width=90>").append(treasury.level).append("</td><td width=170 align=right>").append(KingdomEconomyUtil.format(treasury.balance)).append("</td></tr></table><img src=\"L2UI.SquareGray\" width=").append(WIDTH).append(" height=1>");
		}
		endPage(html, player);
	}
	
	private void showContribution(Player player)
	{
		final KingdomTerritory territory = _manager.findTerritory(player);
		final int castleId = territory == null ? 0 : territory.castleId;
		final StringBuilder html = startPage("Royal Contribution", castleId > 0 ? "Current kingdom contributors" : "Global active contributors");
		if (castleId > 0)
		{
			final KingdomContribution contribution = _manager.getContribution(player.getObjectId(), castleId);
			html.append("<table width=").append(WIDTH).append(" bgcolor=000000><tr><td width=150>Your Points</td><td width=155>").append(KingdomEconomyUtil.format(contribution.points)).append("</td><td width=150>Lifetime</td><td width=155>").append(KingdomEconomyUtil.format(contribution.lifetimePoints)).append("</td></tr>");
			html.append("<tr><td>Daily Rax</td><td>").append(contribution.dailyRax).append(" / ").append(KingdomEconomyConfig.DAILY_RAX_LIMIT).append("</td><td>Weekly Rax</td><td>").append(contribution.weeklyRax).append(" / ").append(KingdomEconomyConfig.WEEKLY_RAX_LIMIT).append("</td></tr></table><br>");
		}
		appendContributors(html, castleId);
		endPage(html, player);
	}
	
	private void showEvents(Player player)
	{
		final StringBuilder html = startPage("Kingdom Events", "Recent economic activity");
		html.append("<table width=").append(WIDTH).append(" bgcolor=000000><tr><td width=140>Type</td><td width=120>Actor</td><td width=350>Details</td></tr></table>");
		for (String[] event : _manager.getRecentEvents())
		{
			html.append("<table width=").append(WIDTH).append("><tr><td width=140>").append(h(event[0])).append("</td><td width=120>").append(h(event[1])).append("</td><td width=350>").append(h(event[2])).append("</td></tr></table><img src=\"L2UI.SquareGray\" width=").append(WIDTH).append(" height=1>");
		}
		endPage(html, player);
	}
	
	private void appendKingdomRows(StringBuilder html, boolean compact)
	{
		html.append("<table width=").append(WIDTH).append(" bgcolor=000000><tr><td width=130>Kingdom</td><td width=150>Ruler</td><td width=80>Level</td><td width=150 align=right>Treasury</td><td width=100>Open</td></tr></table>");
		for (Kingdom kingdom : KingdomEconomyConfig.getKingdoms())
		{
			final KingdomTreasury treasury = _manager.getTreasury(kingdom.castleId);
			html.append("<table width=").append(WIDTH).append("><tr><td width=130>").append(h(kingdom.name)).append("</td><td width=150>").append(h(kingdom.getOwnerName())).append("</td><td width=80>").append(treasury.level).append("</td><td width=150 align=right>").append(KingdomEconomyUtil.format(treasury.balance)).append("</td><td width=100><button value=\"Open\" action=\"bypass _bbskingdom;kingdom;").append(kingdom.castleId).append("\" width=84 height=20 back=\"").append(BUTTON_BACK).append("\" fore=\"").append(BUTTON_FORE).append("\"></td></tr></table>");
			if (!compact)
			{
				html.append("<img src=\"L2UI.SquareGray\" width=").append(WIDTH).append(" height=1>");
			}
		}
	}
	
	private void appendContributors(StringBuilder html, int castleId)
	{
		html.append("<table width=").append(WIDTH).append(" bgcolor=000000><tr><td width=40>#</td><td width=150>Player</td><td width=140>Clan</td><td width=100 align=right>Points</td><td width=110 align=right>Lifetime</td><td width=70>Rax</td></tr></table>");
		int rank = 1;
		for (KingdomEconomyManager.ContributionView view : _manager.getTopContributors(castleId, 15))
		{
			html.append("<table width=").append(WIDTH).append("><tr><td width=40>").append(rank++).append("</td><td width=150>").append(h(getPlayerName(view.charId))).append("</td><td width=140>").append(h(_manager.getClanName(view.clanId))).append("</td><td width=100 align=right>").append(KingdomEconomyUtil.format(view.points)).append("</td><td width=110 align=right>").append(KingdomEconomyUtil.format(view.lifetimePoints)).append("</td><td width=70>").append(view.dailyRax).append("/").append(view.weeklyRax).append("</td></tr></table><img src=\"L2UI.SquareGray\" width=").append(WIDTH).append(" height=1>");
		}
		if (rank == 1)
		{
			html.append("<center><font color=ae9977>No contribution data loaded yet.</font></center>");
		}
	}
	
	private StringBuilder startPage(String title, String subtitle)
	{
		final StringBuilder html = new StringBuilder(8192);
		html.append("<html><body bgcolor=000000><br><center>");
		html.append("<table width=").append(WIDTH).append(" height=448 bgcolor=000000><tr><td valign=top align=center>");
		html.append("<table width=").append(WIDTH).append(" bgcolor=000000><tr><td align=center><font color=LEVEL>").append(h(title)).append("</font><br1><font color=ae9977>").append(h(subtitle)).append("</font></td></tr></table><br1>");
		html.append("<table width=").append(WIDTH).append("><tr>");
		centerTab(html, "Overview", "overview", 112, 205, "right");
		centerTab(html, "Map", "map", 96, 200, "center");
		centerTab(html, "Ranking", "ranking", 112, 205, "left");
		html.append("</tr><tr>");
		centerTab(html, "Contribution", "contrib", 128, 205, "right");
		centerTab(html, "Leader", "leader", 112, 200, "center");
		centerTab(html, "Events", "events", 112, 205, "left");
		html.append("</tr></table><br1>");
		return html;
	}
	
	private void centerTab(StringBuilder html, String title, String page, int buttonWidth, int cellWidth, String align)
	{
		html.append("<td width=").append(cellWidth).append(" align=").append(align).append("><button value=\"").append(title).append("\" action=\"bypass _bbskingdom;").append(page).append("\" width=").append(buttonWidth).append(" height=24 back=\"").append(BIG_BACK).append("\" fore=\"").append(BIG_FORE).append("\"></td>");
	}
	
	private void endPage(StringBuilder html, Player player)
	{
		html.append("</td></tr></table></center></body></html>");
		CommunityBoardHandler.separateAndSend(html.toString(), player);
	}
	
	private void sendMessage(Player player, String title, String message)
	{
		final StringBuilder html = startPage(title, "Kingdom Economy");
		html.append("<br><center><font color=ae9977>").append(h(message)).append("</font></center>");
		endPage(html, player);
	}
	
	private Kingdom getOwnedKingdom(Player player)
	{
		if ((player == null) || (player.getClanId() <= 0))
		{
			return null;
		}
		for (Kingdom kingdom : KingdomEconomyConfig.getKingdoms())
		{
			if (kingdom.getOwnerClanId() == player.getClanId())
			{
				return kingdom;
			}
		}
		return null;
	}
	
	private String getPlayerName(int objectId)
	{
		final String name = CharInfoTable.getInstance().getNameById(objectId);
		return name == null ? Integer.toString(objectId) : name;
	}
	
	private int parseInt(String value)
	{
		try
		{
			return Integer.parseInt(value);
		}
		catch (Exception e)
		{
			return 0;
		}
	}
	
	private String h(String value)
	{
		return KingdomEconomyUtil.html(value);
	}
}
