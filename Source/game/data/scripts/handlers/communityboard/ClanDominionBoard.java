package handlers.communityboard;

import java.util.List;

import org.l2jmobius.gameserver.data.sql.ClanHallTable;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.handler.IParseBoardHandler;
import org.l2jmobius.gameserver.managers.CastleManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;

import custom.MegaraxClanDominion.ClanAchievementManager;
import custom.MegaraxClanDominion.ClanDominionConfig;
import custom.MegaraxClanDominion.ClanDominionData;
import custom.MegaraxClanDominion.ClanDominionEngine;
import custom.MegaraxClanDominion.ClanDominionStats;
import custom.MegaraxClanDominion.ClanDominionUtil;
import custom.MegaraxClanDominion.ClanHallUpgradeManager;
import custom.MegaraxClanDominion.ClanMissionManager;
import custom.MegaraxClanDominion.ClanRivalryManager;
import custom.MegaraxClanDominion.ClanSeasonManager;
import custom.MegaraxClanDominion.ClanTerritoryManager;
import custom.MegaraxClanDominion.ClanWarChestManager;

public class ClanDominionBoard implements IParseBoardHandler
{
	private static final int BOARD_WIDTH = 720;
	private static final int MIN_TAB_WIDTH = 70;
	private static final int MAX_TAB_WIDTH = 120;
	private static final String SMALL_BUTTON_BACK = "Galaxy.105_OVER";
	private static final String SMALL_BUTTON_FORE = "Galaxy.105";
	
	private static final String[] COMMANDS =
	{
		"_bbsdominion"
	};
	
	@Override
	public String[] getCommandList()
	{
		return COMMANDS;
	}
	
	@Override
	public boolean onCommand(String command, Player player)
	{
		ClanDominionEngine.ensureInitialized();
		final String[] parts = command.split(";");
		final String page = parts.length > 1 ? parts[1] : "ranking";
		
		if ("profile".equals(page) && (parts.length > 2))
		{
			showProfile(player, parseInt(parts[2]));
		}
		else if ("my".equals(page))
		{
			showMyClan(player);
		}
		else if ("missions".equals(page))
		{
			showMissions(player);
		}
		else if ("claim".equals(page) && (parts.length > 2))
		{
			ClanMissionManager.getInstance().claim(player, parts[2]);
			showMissions(player);
		}
		else if ("chest".equals(page))
		{
			if ((parts.length > 4) && "deposit".equals(parts[2]))
			{
				ClanWarChestManager.getInstance().deposit(player, parseInt(parts[3]), parseLong(parts[4]));
			}
			else if ((parts.length > 3) && "service".equals(parts[2]))
			{
				if ("rally".equals(parts[3]))
				{
					ClanWarChestManager.getInstance().activateRallyPoint(player);
				}
				else if ("challenge".equals(parts[3]))
				{
					ClanWarChestManager.getInstance().announceChallenge(player);
				}
			}
			else if ((parts.length > 3) && "rally".equals(parts[2]) && "use".equals(parts[3]))
			{
				ClanWarChestManager.getInstance().teleportToRally(player);
			}
			showWarChest(player);
		}
		else if ("territories".equals(page))
		{
			showTerritories(player);
		}
		else if ("rivalries".equals(page))
		{
			if ((parts.length > 3) && "declare".equals(parts[2]))
			{
				ClanRivalryManager.getInstance().declareRivalry(player, parseInt(parts[3]));
			}
			showRivalries(player);
		}
		else if ("hall".equals(page))
		{
			if ((parts.length > 2) && "upgrade".equals(parts[2]))
			{
				ClanHallUpgradeManager.getInstance().upgrade(player);
			}
			showHall(player);
		}
		else if ("achievements".equals(page))
		{
			showAchievements(player);
		}
		else if ("seasons".equals(page))
		{
			showSeasons(player);
		}
		else if ("news".equals(page))
		{
			showNews(player);
		}
		else
		{
			showRanking(player);
		}
		return false;
	}
	
	private void showRanking(Player player)
	{
		final StringBuilder html = startPage("Megarax Clans", "Dominion ranking");
		html.append("<table width=720 bgcolor=000000><tr><td width=35>#</td><td width=190>Clan</td><td width=150>Tier</td><td width=110 align=right>Season</td><td width=110 align=right>Total</td><td width=80>Profile</td></tr></table>");
		int rank = 1;
		for (ClanDominionStats stats : ClanDominionData.getTopClans(15))
		{
			final Clan clan = ClanTable.getInstance().getClan(stats.clanId);
			if (clan == null)
			{
				continue;
			}
			html.append("<table width=720><tr>");
			html.append("<td width=35>").append(rank++).append("</td>");
			html.append("<td width=190>").append(ClanDominionUtil.html(clan.getName())).append("</td>");
			html.append("<td width=150>").append(ClanDominionUtil.html(ClanDominionUtil.tierName(stats.tier))).append("</td>");
			html.append("<td width=110 align=right>").append(ClanDominionUtil.format(stats.seasonPoints)).append("</td>");
			html.append("<td width=110 align=right>").append(ClanDominionUtil.format(stats.totalPoints)).append("</td>");
			html.append("<td width=80><button value=\"Open\" action=\"bypass _bbsdominion;profile;").append(stats.clanId).append("\" width=65 height=20 back=\"l2ui_ch3.smallbutton2_down\" fore=\"l2ui_ch3.smallbutton2\"></td>");
			html.append("</tr></table><img src=\"L2UI.Squaregray\" width=720 height=1>");
		}
		if (rank == 1)
		{
			html.append("<br><center>No Dominion data yet.</center>");
		}
		endPage(html, player);
	}
	
	private void showMyClan(Player player)
	{
		if (player.getClanId() <= 0)
		{
			sendMessagePage(player, "My Clan", "You need a clan to use Clan Dominion.");
			return;
		}
		showProfile(player, player.getClanId());
	}
	
	private void showProfile(Player player, int clanId)
	{
		final Clan clan = ClanTable.getInstance().getClan(clanId);
		if (clan == null)
		{
			sendMessagePage(player, "Clan Profile", "Clan not found.");
			return;
		}
		final ClanDominionStats stats = ClanDominionData.getStats(clanId);
		final StringBuilder html = startPage("Clan Profile", clan.getName());
		html.append("<table width=720 bgcolor=000000><tr><td width=180>Clan</td><td width=220>").append(ClanDominionUtil.html(clan.getName())).append("</td><td width=130>Tier</td><td width=190>").append(ClanDominionUtil.html(ClanDominionUtil.tierName(stats.tier))).append("</td></tr>");
		html.append("<tr><td>Leader</td><td>").append(ClanDominionUtil.html(clan.getLeaderName())).append("</td><td>Alliance</td><td>").append(ClanDominionUtil.html(clan.getAllyName() == null ? "None" : clan.getAllyName())).append("</td></tr>");
		html.append("<tr><td>Members</td><td>").append(clan.getMembersCount()).append("</td><td>Online</td><td>").append(clan.getOnlineMembers(0).size()).append("</td></tr>");
		html.append("<tr><td>Total Points</td><td>").append(ClanDominionUtil.format(stats.totalPoints)).append("</td><td>Season Points</td><td>").append(ClanDominionUtil.format(stats.seasonPoints)).append("</td></tr>");
		html.append("<tr><td>PvP K/D</td><td>").append(stats.pvpKills).append(" / ").append(stats.pvpDeaths).append("</td><td>Weekly Activity</td><td>").append(stats.weeklyActivity).append("</td></tr>");
		html.append("<tr><td>Castle</td><td>").append(getCastleName(clan)).append("</td><td>Clan Hall</td><td>").append(getHallName(clan)).append("</td></tr>");
		html.append("<tr><td>Raid Score</td><td>").append(stats.raidScore).append("</td><td>Territory Score</td><td>").append(stats.territoryScore).append("</td></tr></table><br>");
		if (player.getClanId() == clanId)
		{
			html.append("<table width=720><tr>");
			html.append("<td><button value=\"Missions\" action=\"bypass _bbsdominion;missions\" width=120 height=24 back=\"l2ui_ch3.bigbutton3_down\" fore=\"l2ui_ch3.bigbutton3\"></td>");
			html.append("<td><button value=\"War Chest\" action=\"bypass _bbsdominion;chest\" width=120 height=24 back=\"l2ui_ch3.bigbutton3_down\" fore=\"l2ui_ch3.bigbutton3\"></td>");
			html.append("<td><button value=\"Hall\" action=\"bypass _bbsdominion;hall\" width=120 height=24 back=\"l2ui_ch3.bigbutton3_down\" fore=\"l2ui_ch3.bigbutton3\"></td>");
			html.append("<td><button value=\"Achievements\" action=\"bypass _bbsdominion;achievements\" width=120 height=24 back=\"l2ui_ch3.bigbutton3_down\" fore=\"l2ui_ch3.bigbutton3\"></td>");
			html.append("</tr></table>");
		}
		endPage(html, player);
	}
	
	private void showMissions(Player player)
	{
		if (player.getClanId() <= 0)
		{
			sendMessagePage(player, "Clan Missions", "You need a clan to use Clan Missions.");
			return;
		}
		final StringBuilder html = startPage("Clan Missions", "Cycle " + ClanMissionManager.getInstance().getCycleKey());
		html.append("<table width=720 bgcolor=000000><tr><td width=210>Mission</td><td width=255>Goal</td><td width=110 align=right>Progress</td><td width=75>Reward</td><td width=70>Claim</td></tr></table>");
		for (ClanMissionManager.MissionView mission : ClanMissionManager.getInstance().getMissionViews(player.getClanId()))
		{
			final boolean complete = mission.progress >= mission.goal;
			final String button = mission.claimed ? "Done" : complete ? "Claim" : "Locked";
			final String action = complete && !mission.claimed ? ("bypass _bbsdominion;claim;" + mission.id) : "bypass _bbsdominion;missions";
			html.append("<table width=720><tr>");
			html.append("<td width=210><font color=LEVEL>").append(ClanDominionUtil.html(mission.name)).append("</font><br1><font color=ae9977>").append(ClanDominionUtil.html(mission.description)).append("</font></td>");
			html.append("<td width=255>").append(ClanDominionUtil.html(mission.type)).append("</td>");
			html.append("<td width=110 align=right>").append(ClanDominionUtil.format(mission.progress)).append(" / ").append(ClanDominionUtil.format(mission.goal)).append("</td>");
			html.append("<td width=75>").append(mission.rewardPoints).append(" DP</td>");
			html.append("<td width=70><button value=\"").append(button).append("\" action=\"").append(action).append("\" width=65 height=20 back=\"l2ui_ch3.smallbutton2_down\" fore=\"l2ui_ch3.smallbutton2\"></td>");
			html.append("</tr></table><img src=\"L2UI.Squaregray\" width=720 height=1>");
		}
		endPage(html, player);
	}
	
	private void showWarChest(Player player)
	{
		if (player.getClanId() <= 0)
		{
			sendMessagePage(player, "War Chest", "You need a clan to use the War Chest.");
			return;
		}
		final StringBuilder html = startPage("Clan War Chest", "Deposits, rally point and services");
		html.append("<table width=720 bgcolor=000000><tr><td width=120>Item ID</td><td width=180 align=right>Balance</td><td width=420>Quick deposit</td></tr></table>");
		for (ClanWarChestManager.ChestBalance balance : ClanWarChestManager.getInstance().getBalances(player.getClanId()))
		{
			html.append("<table width=720><tr><td width=120>").append(balance.itemId).append("</td><td width=180 align=right>").append(ClanDominionUtil.format(balance.amount)).append("</td><td width=420></td></tr></table>");
		}
		html.append("<br><table width=720 bgcolor=000000><tr><td>Deposit shortcuts</td></tr></table>");
		html.append("<table width=720><tr>");
		html.append("<td><button value=\"1m Adena\" action=\"bypass _bbsdominion;chest;deposit;57;1000000\" width=95 height=20 back=\"l2ui_ch3.smallbutton2_down\" fore=\"l2ui_ch3.smallbutton2\"></td>");
		html.append("<td><button value=\"10 PvP\" action=\"bypass _bbsdominion;chest;deposit;8875;10\" width=95 height=20 back=\"l2ui_ch3.smallbutton2_down\" fore=\"l2ui_ch3.smallbutton2\"></td>");
		html.append("<td><button value=\"50 Rax\" action=\"bypass _bbsdominion;chest;deposit;9142;50\" width=95 height=20 back=\"l2ui_ch3.smallbutton2_down\" fore=\"l2ui_ch3.smallbutton2\"></td>");
		html.append("</tr></table><br>");
		html.append("<table width=720><tr>");
		html.append("<td><button value=\"Activate Rally\" action=\"bypass _bbsdominion;chest;service;rally\" width=130 height=24 back=\"l2ui_ch3.bigbutton3_down\" fore=\"l2ui_ch3.bigbutton3\"></td>");
		html.append("<td><button value=\"Use Rally\" action=\"bypass _bbsdominion;chest;rally;use\" width=100 height=24 back=\"l2ui_ch3.bigbutton3_down\" fore=\"l2ui_ch3.bigbutton3\"></td>");
		html.append("<td><button value=\"Challenge\" action=\"bypass _bbsdominion;chest;service;challenge\" width=110 height=24 back=\"l2ui_ch3.bigbutton3_down\" fore=\"l2ui_ch3.bigbutton3\"></td>");
		html.append("</tr></table>");
		endPage(html, player);
	}
	
	private void showTerritories(Player player)
	{
		final StringBuilder html = startPage("Territory Map", "Clan territory ownership");
		html.append("<table width=720 bgcolor=000000><tr><td width=230>Territory</td><td width=200>Owner</td><td width=140>Expires</td><td width=75>Attack</td><td width=75>Defense</td></tr></table>");
		for (ClanTerritoryManager.TerritoryView territory : ClanTerritoryManager.getInstance().getTerritories())
		{
			html.append("<table width=720><tr>");
			html.append("<td width=230>").append(ClanDominionUtil.html(territory.name)).append("</td>");
			html.append("<td width=200>").append(ClanDominionUtil.html(getClanName(territory.ownerClanId))).append("</td>");
			html.append("<td width=140>").append(timeLeft(territory.expiresAt)).append("</td>");
			html.append("<td width=75>").append(territory.attackScore).append("</td>");
			html.append("<td width=75>").append(territory.defenseScore).append("</td>");
			html.append("</tr></table><img src=\"L2UI.Squaregray\" width=720 height=1>");
		}
		endPage(html, player);
	}
	
	private void showRivalries(Player player)
	{
		if (player.getClanId() <= 0)
		{
			sendMessagePage(player, "Rivalries", "You need a clan to use Rivalry Contracts.");
			return;
		}
		final StringBuilder html = startPage("Rivalries", "Official clan vs clan contracts");
		html.append("<table width=720 bgcolor=000000><tr><td width=55>ID</td><td width=170>Clan</td><td width=170>Rival</td><td width=100>Status</td><td width=115>Score</td><td width=110>Ends</td></tr></table>");
		for (ClanRivalryManager.RivalryView rivalry : ClanRivalryManager.getInstance().getRivalries(player.getClanId()))
		{
			html.append("<table width=720><tr>");
			html.append("<td width=55>").append(rivalry.id).append("</td>");
			html.append("<td width=170>").append(ClanDominionUtil.html(getClanName(rivalry.clanId))).append("</td>");
			html.append("<td width=170>").append(ClanDominionUtil.html(getClanName(rivalry.rivalClanId))).append("</td>");
			html.append("<td width=100>").append(ClanDominionUtil.html(rivalry.status)).append("</td>");
			html.append("<td width=115>").append(rivalry.clanKills).append(" - ").append(rivalry.rivalKills).append("</td>");
			html.append("<td width=110>").append(timeLeft(rivalry.endAt)).append("</td>");
			html.append("</tr></table><img src=\"L2UI.Squaregray\" width=720 height=1>");
		}
		html.append("<br><table width=720 bgcolor=000000><tr><td>Challenge a top clan</td></tr></table><table width=720><tr>");
		int shown = 0;
		for (ClanDominionStats stats : ClanDominionData.getTopClans(5))
		{
			if ((stats.clanId == player.getClanId()) || (ClanTable.getInstance().getClan(stats.clanId) == null))
			{
				continue;
			}
			html.append("<td><button value=\"").append(ClanDominionUtil.html(getClanName(stats.clanId))).append("\" action=\"bypass _bbsdominion;rivalries;declare;").append(stats.clanId).append("\" width=120 height=20 back=\"l2ui_ch3.smallbutton2_down\" fore=\"l2ui_ch3.smallbutton2\"></td>");
			if (++shown >= 4)
			{
				break;
			}
		}
		html.append("</tr></table>");
		endPage(html, player);
	}
	
	private void showHall(Player player)
	{
		if (player.getClanId() <= 0)
		{
			sendMessagePage(player, "Clan Hall", "You need a clan to use Clan Hall upgrades.");
			return;
		}
		final Clan clan = player.getClan();
		final int current = ClanHallUpgradeManager.getInstance().getUpgradeLevel(player.getClanId());
		final int next = current + 1;
		final ClanDominionConfig.HallUpgradeRequirement req = ClanDominionConfig.HALL_UPGRADE_REQUIREMENTS.get(next);
		final StringBuilder html = startPage("Clan Hall Upgrades", getHallName(clan));
		html.append("<table width=720 bgcolor=000000><tr><td width=200>Current Level</td><td>").append(current).append("</td></tr>");
		html.append("<tr><td>Next Level</td><td>").append(next > ClanDominionConfig.MAX_CLAN_HALL_UPGRADE_LEVEL ? "Max" : next).append("</td></tr>");
		if (req != null)
		{
			html.append("<tr><td>Required Points</td><td>").append(ClanDominionUtil.format(req.requiredPoints)).append("</td></tr>");
			html.append("<tr><td>Adena Cost</td><td>").append(ClanDominionUtil.format(req.adenaCost)).append(" from War Chest</td></tr>");
			html.append("<tr><td>Weekly Activity</td><td>").append(req.minWeeklyActivity).append("</td></tr>");
		}
		html.append("</table><br><button value=\"Upgrade\" action=\"bypass _bbsdominion;hall;upgrade\" width=120 height=24 back=\"l2ui_ch3.bigbutton3_down\" fore=\"l2ui_ch3.bigbutton3\">");
		endPage(html, player);
	}
	
	private void showAchievements(Player player)
	{
		if (player.getClanId() <= 0)
		{
			sendMessagePage(player, "Achievements", "You need a clan to view Clan Achievements.");
			return;
		}
		final StringBuilder html = startPage("Clan Achievements", "Permanent clan history");
		html.append("<table width=720 bgcolor=000000><tr><td width=230>Achievement</td><td width=300>Description</td><td width=110 align=right>Progress</td><td width=80>Reward</td></tr></table>");
		for (ClanAchievementManager.AchievementView view : ClanAchievementManager.getInstance().getViews(player.getClanId()))
		{
			html.append("<table width=720><tr>");
			html.append("<td width=230>").append(view.completed ? "<font color=LEVEL>" : "").append(ClanDominionUtil.html(view.name)).append(view.completed ? "</font>" : "").append("</td>");
			html.append("<td width=300>").append(ClanDominionUtil.html(view.description)).append("</td>");
			html.append("<td width=110 align=right>").append(view.progress).append(" / ").append(view.goal).append("</td>");
			html.append("<td width=80>").append(view.rewardPoints).append(" DP</td>");
			html.append("</tr></table><img src=\"L2UI.Squaregray\" width=720 height=1>");
		}
		endPage(html, player);
	}
	
	private void showSeasons(Player player)
	{
		final StringBuilder html = startPage("Season Rewards", ClanSeasonManager.getInstance().getCurrentSeasonKey());
		html.append("<table width=720 bgcolor=000000><tr><td width=180>Current season</td><td>").append(ClanDominionUtil.html(ClanSeasonManager.getInstance().getCurrentSeasonKey())).append("</td></tr>");
		html.append("<tr><td>Reset in</td><td>").append(timeLeft(ClanSeasonManager.getInstance().getCurrentSeasonEnd())).append("</td></tr>");
		html.append("<tr><td>Duration</td><td>").append(ClanDominionConfig.SEASON_DURATION_DAYS).append(" days</td></tr>");
		html.append("</table><br><font color=ae9977>Season results are snapshotted automatically when the season closes.</font>");
		endPage(html, player);
	}
	
	private void showNews(Player player)
	{
		final StringBuilder html = startPage("Megarax War News", "Recent clan drama");
		html.append("<table width=720 bgcolor=000000><tr><td width=140>Type</td><td width=130>Actor</td><td width=450>Details</td></tr></table>");
		for (String[] row : ClanDominionData.getWarNews(ClanDominionConfig.WAR_NEWS_LIMIT))
		{
			html.append("<table width=720><tr><td width=140>").append(ClanDominionUtil.html(row[0])).append("</td><td width=130>").append(ClanDominionUtil.html(row[1])).append("</td><td width=450>").append(ClanDominionUtil.html(row[2])).append("</td></tr></table><img src=\"L2UI.Squaregray\" width=720 height=1>");
		}
		endPage(html, player);
	}
	
	private StringBuilder startPage(String title, String subtitle)
	{
		final StringBuilder html = new StringBuilder(4096);
		html.append("<html><body><br><center>");
		html.append("<table width=").append(BOARD_WIDTH).append(" bgcolor=000000><tr><td align=center><font color=LEVEL>").append(ClanDominionUtil.html(title)).append("</font><br1><font color=ae9977>").append(ClanDominionUtil.html(subtitle)).append("</font></td></tr></table><br>");
		html.append("<table width=").append(BOARD_WIDTH).append("><tr>");
		tab(html, "Ranking", "ranking");
		tab(html, "My Clan", "my");
		tab(html, "Missions", "missions");
		tab(html, "War Chest", "chest");
		tab(html, "Territory", "territories");
		tab(html, "Rivalries", "rivalries");
		tab(html, "Hall", "hall");
		tab(html, "News", "news");
		html.append("</tr></table><br>");
		return html;
	}
	
	private void tab(StringBuilder html, String title, String page)
	{
		final int width = tabWidth(title);
		html.append("<td width=").append(width + 6).append(" align=center><button value=\"").append(title).append("\" action=\"bypass _bbsdominion;").append(page).append("\" width=").append(width).append(" height=24 back=\"").append(SMALL_BUTTON_BACK).append("\" fore=\"").append(SMALL_BUTTON_FORE).append("\"></td>");
	}
	
	private int tabWidth(String title)
	{
		return Math.max(MIN_TAB_WIDTH, Math.min(MAX_TAB_WIDTH, (title.length() * 7) + 30));
	}
	
	private void endPage(StringBuilder html, Player player)
	{
		html.append("</center></body></html>");
		CommunityBoardHandler.separateAndSend(html.toString(), player);
	}
	
	private void sendMessagePage(Player player, String title, String message)
	{
		final StringBuilder html = startPage(title, "Clan Dominion");
		html.append("<br><center>").append(ClanDominionUtil.html(message)).append("</center>");
		endPage(html, player);
	}
	
	private String getClanName(int clanId)
	{
		final Clan clan = ClanTable.getInstance().getClan(clanId);
		return clan == null ? "NPC" : clan.getName();
	}
	
	private String getCastleName(Clan clan)
	{
		if ((clan == null) || (clan.getCastleId() <= 0))
		{
			return "None";
		}
		return CastleManager.getInstance().getCastleById(clan.getCastleId()).getName();
	}
	
	private String getHallName(Clan clan)
	{
		if ((clan == null) || (clan.getHideoutId() <= 0))
		{
			return "None";
		}
		return ClanHallTable.getInstance().getClanHallById(clan.getHideoutId()).getName();
	}
	
	private String timeLeft(long timestamp)
	{
		if (timestamp <= 0)
		{
			return "Pending";
		}
		final long diff = Math.max(0, timestamp - System.currentTimeMillis()) / 1000;
		final long days = diff / 86400;
		final long hours = (diff % 86400) / 3600;
		final long minutes = (diff % 3600) / 60;
		if (days > 0)
		{
			return days + "d " + hours + "h";
		}
		if (hours > 0)
		{
			return hours + "h " + minutes + "m";
		}
		return minutes + "m";
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
	
	private long parseLong(String value)
	{
		try
		{
			return Long.parseLong(value);
		}
		catch (Exception e)
		{
			return 0;
		}
	}
}
