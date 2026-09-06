package handlers.communityboard;

import org.l2jmobius.gameserver.cache.HtmCache;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.handler.IParseBoardHandler;
import org.l2jmobius.gameserver.model.actor.Player;

import custom.MegaraxHuntContracts.MegaraxHuntContracts;
import custom.MegaraxWantedMonsters.MegaraxWantedMonsters;
import custom.MegaraxWantedMonsters.MegaraxWantedMonsters.EncounterView;

/** Black HOME-style interface for Wanted Hunts. */
public class WantedHuntsBoard implements IParseBoardHandler
{
	private static final String[] COMMANDS={"_bbswantedhunts","_bbswantedhunts_teleport","_bbswantedhunts_claim","_bbswantedhunts_rewards"};
	private static final String PAGE="data/html/CommunityBoard/Custom/wantedhunts/main.html";
	private static final String NAV="data/html/CommunityBoard/Custom/navigation.html";
	public WantedHuntsBoard(){MegaraxWantedMonsters.getInstance();}
	@Override public String[] getCommandList(){return COMMANDS;}
	@Override public boolean onCommand(String command,Player player)
	{
		final MegaraxWantedMonsters service=MegaraxWantedMonsters.getInstance();
		if(command.equals("_bbswantedhunts_teleport"))service.teleport(player);else if(command.equals("_bbswantedhunts_claim"))service.claimPending(player);
		show(player);return true;
	}
	private void show(Player p)
	{
		final MegaraxWantedMonsters s=MegaraxWantedMonsters.getInstance();final EncounterView e=s.getActiveView(p);String html=HtmCache.getInstance().getHtm(p,PAGE);if(html==null)return;html=html.replace("%navigation%",HtmCache.getInstance().getHtm(p,NAV));final long seals=MegaraxHuntContracts.getInstance().getWallet(p.getAccountName()).wantedSeals;final int pending=s.getPendingCount(p.getAccountName());final StringBuilder c=new StringBuilder(2500);
		c.append("<table width=500 bgcolor=\"000000\"><tr><td width=20></td><td width=230><font color=CDB67F>Character: </font><font color=LEVEL>").append(p.getName()).append("</font><br1><font color=CDB67F>Wanted Seals: </font><font color=F0D58A>").append(seals).append("</font></td><td width=230 align=right><font color=CDB67F>Pending Rewards: </font><font color=E0E0E0>").append(pending).append("</font><br1><font color=CDB67F>Last: </font><font color=AFAFAF>").append(s.getLastResult()).append("</font></td><td width=20></td></tr></table><img src=\"L2UI.SquareGray\" width=500 height=1>");
		if(e==null)c.append("<table width=500 height=230 bgcolor=\"000000\"><tr><td align=center><font color=C0C0C0>No Wanted Monster is currently active.</font><br1><font color=AFAFAF>The next hunt will appear automatically.</font></td></tr></table>");else{long sec=Math.max(0,(e.expiresAt-System.currentTimeMillis())/1000);c.append("<table width=500 bgcolor=\"000000\"><tr><td height=8></td></tr><tr><td width=20></td><td width=64 align=center><img src=\"").append(e.icon).append("\" width=48 height=48></td><td width=396><font color=F0D58A>").append(e.name).append("</font><br1><font color=CDB67F>").append(e.category).append("</font><br1><font color=C0C0C0>").append(e.zone).append("</font></td><td width=20></td></tr></table><table width=500 bgcolor=\"000000\"><tr><td width=20></td><td width=230><font color=CDB67F>Time: </font><font color=E0E0E0>").append(sec/60).append("m ").append(sec%60).append("s</font><br1><font color=CDB67F>HP: </font><font color=E0E0E0>").append(e.hpPercent).append("%</font><br1><font color=CDB67F>Participants: </font><font color=E0E0E0>").append(e.participants).append("</font></td><td width=230><font color=CDB67F>Your Damage: </font><font color=E0E0E0>").append(e.damage).append("</font><br1><font color=CDB67F>Current Tier: </font><font color=F0D58A>").append(e.tier).append("</font><br1><font color=AFAFAF>Rewards are fixed when the Wanted is defeated.</font></td><td width=20></td></tr><tr><td height=10></td></tr></table>");}
		html=html.replace("%content%",c.toString());String buttons=(e==null?"":"<button value=\"TELEPORT\" action=\"bypass _bbswantedhunts_teleport\" width=90 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"> ")+"<button value=\"CLAIM\" action=\"bypass _bbswantedhunts_claim\" width=90 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"> <button value=\"BACK\" action=\"bypass _bbstop;home.html\" width=90 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">";html=html.replace("%buttons%",buttons);CommunityBoardHandler.separateAndSend(html,p);
	}
}
