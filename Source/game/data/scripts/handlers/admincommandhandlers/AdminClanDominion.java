package handlers.admincommandhandlers;

import java.util.StringTokenizer;

import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.handler.IAdminCommandHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

import custom.MegaraxClanDominion.ClanDominionData;
import custom.MegaraxClanDominion.ClanDominionEngine;
import custom.MegaraxClanDominion.ClanDominionStats;
import custom.MegaraxClanDominion.ClanDominionUtil;
import custom.MegaraxClanDominion.ClanSeasonManager;

public class AdminClanDominion implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_clandominion_addpoints",
		"admin_clandominion_removepoints",
		"admin_clandominion_info",
		"admin_clandominion_resetweekly",
		"admin_clandominion_reload",
		"admin_clandominion_settier",
		"admin_clandominion_debug"
	};
	
	@Override
	public boolean onCommand(String command, Player activeChar)
	{
		final StringTokenizer st = new StringTokenizer(command);
		final String cmd = st.hasMoreTokens() ? st.nextToken() : command;
		if ("admin_clandominion_reload".equals(cmd))
		{
			ClanDominionEngine.reload();
			activeChar.sendMessage("Clan Dominion config and defaults reloaded.");
			showInfo(activeChar, null);
			return true;
		}
		if ("admin_clandominion_resetweekly".equals(cmd))
		{
			ClanSeasonManager.getInstance().resetSeasonNow();
			activeChar.sendMessage("Clan Dominion season/weekly stats reset.");
			showInfo(activeChar, null);
			return true;
		}
		
		final String clanName = st.hasMoreTokens() ? st.nextToken() : "";
		final Clan clan = clanName.isEmpty() ? getTargetClan(activeChar) : ClanTable.getInstance().getClanByName(clanName);
		if (clan == null)
		{
			activeChar.sendMessage("Clan not found. Target a player or pass a clan name.");
			showUsage(activeChar);
			return true;
		}
		
		if ("admin_clandominion_addpoints".equals(cmd))
		{
			final int amount = st.hasMoreTokens() ? parseInt(st.nextToken()) : 0;
			ClanDominionEngine.addDominionPoints(clan.getId(), 0, amount, "ADMIN_ADD", activeChar.getName(), "", "Admin command");
			showInfo(activeChar, clan);
		}
		else if ("admin_clandominion_removepoints".equals(cmd))
		{
			final int amount = st.hasMoreTokens() ? parseInt(st.nextToken()) : 0;
			ClanDominionEngine.addDominionPoints(clan.getId(), 0, -Math.abs(amount), "ADMIN_REMOVE", activeChar.getName(), "", "Admin command");
			showInfo(activeChar, clan);
		}
		else if ("admin_clandominion_settier".equals(cmd))
		{
			final int tier = st.hasMoreTokens() ? parseInt(st.nextToken()) : 0;
			ClanDominionData.setTier(clan.getId(), tier);
			ClanDominionData.logActivity(clan.getId(), "ADMIN_SET_TIER", activeChar.getName(), "Tier set to " + tier);
			showInfo(activeChar, clan);
		}
		else if ("admin_clandominion_debug".equals(cmd) || "admin_clandominion_info".equals(cmd))
		{
			showInfo(activeChar, clan);
		}
		else
		{
			showUsage(activeChar);
		}
		return true;
	}
	
	private Clan getTargetClan(Player activeChar)
	{
		if ((activeChar.getTarget() != null) && activeChar.getTarget().isPlayer())
		{
			return activeChar.getTarget().asPlayer().getClan();
		}
		return activeChar.getClan();
	}
	
	private void showInfo(Player activeChar, Clan clan)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage();
		final StringBuilder sb = new StringBuilder();
		sb.append("<html><body><center>");
		sb.append("<font color=LEVEL>Clan Dominion Admin</font><br>");
		sb.append("<table width=280 bgcolor=000000>");
		if (clan != null)
		{
			final ClanDominionStats stats = ClanDominionData.getStats(clan.getId());
			sb.append("<tr><td>Clan</td><td>").append(ClanDominionUtil.html(clan.getName())).append("</td></tr>");
			sb.append("<tr><td>Tier</td><td>").append(stats.tier).append(" - ").append(ClanDominionUtil.html(ClanDominionUtil.tierName(stats.tier))).append("</td></tr>");
			sb.append("<tr><td>Total Points</td><td>").append(ClanDominionUtil.format(stats.totalPoints)).append("</td></tr>");
			sb.append("<tr><td>Season Points</td><td>").append(ClanDominionUtil.format(stats.seasonPoints)).append("</td></tr>");
			sb.append("<tr><td>Weekly Activity</td><td>").append(stats.weeklyActivity).append("</td></tr>");
			sb.append("<tr><td>PvP K/D</td><td>").append(stats.pvpKills).append(" / ").append(stats.pvpDeaths).append("</td></tr>");
			sb.append("<tr><td>Raid</td><td>").append(stats.raidScore).append("</td></tr>");
			sb.append("<tr><td>Territory</td><td>").append(stats.territoryScore).append("</td></tr>");
		}
		else
		{
			sb.append("<tr><td>No clan selected.</td></tr>");
		}
		sb.append("</table><br>");
		sb.append("<font color=ae9977>//clandominion_info clanName</font><br1>");
		sb.append("<font color=ae9977>//clandominion_addpoints clanName amount</font><br1>");
		sb.append("<font color=ae9977>//clandominion_removepoints clanName amount</font><br1>");
		sb.append("<font color=ae9977>//clandominion_settier clanName tier</font><br1>");
		sb.append("<font color=ae9977>//clandominion_resetweekly</font><br1>");
		sb.append("<font color=ae9977>//clandominion_reload</font>");
		sb.append("</center></body></html>");
		html.setHtml(sb.toString());
		activeChar.sendPacket(html);
	}
	
	private void showUsage(Player activeChar)
	{
		showInfo(activeChar, null);
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
	
	@Override
	public String[] getCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
