package handlers.communityboard;

import java.util.List;

import org.l2jmobius.gameserver.cache.HtmCache;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.handler.IParseBoardHandler;
import org.l2jmobius.gameserver.model.actor.Player;

import custom.MegaraxHuntContracts.MegaraxHuntContracts;
import custom.MegaraxHuntContracts.MegaraxHuntContracts.Contract;
import custom.MegaraxHuntContracts.MegaraxHuntContracts.ContractState;
import custom.MegaraxHuntContracts.MegaraxHuntContracts.RewardItem;
import custom.MegaraxHuntContracts.MegaraxHuntContracts.Template;
import custom.MegaraxHuntContracts.MegaraxHuntContracts.Wallet;

/** Dark Community Board interface for account hunt contracts. */
public class HuntContractsBoard implements IParseBoardHandler
{
	private static final String[] COMMANDS = { "_bbshuntcontracts", "_bbshuntcontracts_accept", "_bbshuntcontracts_claim" };
	private static final String PAGE = "data/html/CommunityBoard/Custom/huntcontracts/main.html";
	private static final String NAVIGATION = "data/html/CommunityBoard/Custom/navigation.html";

	public HuntContractsBoard()
	{
		MegaraxHuntContracts.getInstance();
	}

	@Override
	public String[] getCommandList() { return COMMANDS; }

	@Override
	public boolean onCommand(String command, Player player)
	{
		if (command.startsWith("_bbshuntcontracts_accept"))
		{
			final int slot = parseSlot(command, "_bbshuntcontracts_accept");
			MegaraxHuntContracts.getInstance().accept(player, slot);
		}
		else if (command.startsWith("_bbshuntcontracts_claim"))
		{
			final int slot = parseSlot(command, "_bbshuntcontracts_claim");
			MegaraxHuntContracts.getInstance().claim(player, slot);
		}
		show(player);
		return true;
	}

	private int parseSlot(String command, String prefix)
	{
		try
		{
			final String value = command.substring(prefix.length()).trim();
			return Integer.parseInt(value);
		}
		catch (Exception e) { return -1; }
	}

	private void show(Player player)
	{
		final MegaraxHuntContracts service = MegaraxHuntContracts.getInstance();
		final List<Contract> contracts = service.getContracts(player);
		final Wallet wallet = service.getWallet(player.getAccountName());
		final long used = contracts.stream().filter(c -> (c.state != ContractState.OFFERED) && (c.state != ContractState.EXPIRED) && (c.state != ContractState.ABANDONED)).count();
		String html = HtmCache.getInstance().getHtm(player, PAGE);
		if (html == null) { player.sendMessage("Hunt Contracts page is missing."); return; }
		html = html.replace("%navigation%", HtmCache.getInstance().getHtm(player, NAVIGATION));
		final String premium = player.hasPremiumStatus() ? "<font color=66CC88>ACTIVE</font>" : "<font color=DD6666>INACTIVE</font>";
		final StringBuilder summary = new StringBuilder(900);
		summary.append("<table width=500 bgcolor=\"050505\"><tr><td width=15></td><td width=235><font color=LEVEL>").append(player.getName()).append("</font><br1><font color=CDB67F>Premium: </font>").append(premium).append("<br1><font color=CDB67F>Hunt Marks: </font><font color=F0D58A>").append(wallet.huntMarks).append("</font></td><td width=235 align=right><font color=CDB67F>Contracts Used: </font><font color=E0E0E0>").append(used).append(player.hasPremiumStatus() ? "/2" : "/1").append("</font><br1><font color=CDB67F>Reset In: </font><font color=E0E0E0>").append(MegaraxHuntContracts.nextResetText()).append("</font><br1><font color=CDB67F>Second Contract: </font><font color=").append(player.hasPremiumStatus() || used >= 2 ? "66CC88>AVAILABLE" : "E0B85C>PREMIUM REQUIRED").append("</font></td><td width=15></td></tr></table><br1>");
		html = html.replace("%summary%", summary.toString());
		final StringBuilder cards = new StringBuilder(4500);
		cards.append("<table width=500 bgcolor=\"000000\"><tr><td align=center><font color=F0D58A>TODAY'S OFFERS</font></td></tr></table>");
		for (Contract contract : contracts)
		{
			final Template template = service.getTemplate(contract.templateId);
			if (template == null) continue;
			cards.append("<table width=500 bgcolor=\"0B0E13\"><tr><td width=12></td><td width=48 align=center><img src=\"").append(template.icon).append("\" width=32 height=32></td><td width=285><font color=F0D58A>").append(template.shortName).append("</font><br1><font color=C0C0C0>").append(template.name).append("</font><br1><font color=AFAFAF>Objective: ").append(contract.target).append(" | ").append(rewardText(template)).append("</font></td><td width=140 align=center>");
			if (contract.state == ContractState.OFFERED) cards.append("<button value=\"ACCEPT\" action=\"bypass _bbshuntcontracts_accept ").append(contract.offerSlot).append("\" width=90 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
			else if (contract.state == ContractState.COMPLETED) cards.append("<button value=\"CLAIM\" action=\"bypass _bbshuntcontracts_claim ").append(contract.offerSlot).append("\" width=90 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
			else cards.append("<font color=").append(stateColor(contract.state)).append(">").append(contract.state).append("</font>");
			cards.append("</td><td width=15></td></tr></table><img src=\"L2UI.SquareGray\" width=500 height=1>");
		}
		html = html.replace("%contracts%", cards.toString());
		final StringBuilder active = new StringBuilder(2800);
		for (Contract contract : contracts)
		{
			if ((contract.state != ContractState.ACTIVE) && (contract.state != ContractState.COMPLETED)) continue;
			final Template template = service.getTemplate(contract.templateId); if (template == null) continue;
			active.append("<br1><table width=500 bgcolor=\"000000\"><tr><td align=center><font color=F0D58A>").append(contract.premiumSlot ? "SECOND CONTRACT" : "ACTIVE CONTRACT").append("</font></td></tr></table><table width=500 bgcolor=\"050505\"><tr><td width=20></td><td width=460><font color=LEVEL>").append(template.name).append("</font><br1><font color=C0C0C0>").append(template.description).append("</font><br1><font color=CDB67F>Progress: </font><font color=E0E0E0>").append(contract.progress).append("/").append(contract.target).append("</font><br1><font color=CDB67F>Reward: </font><font color=F0D58A>").append(rewardText(template)).append("</font><br1><font color=CDB67F>Status: </font><font color=").append(stateColor(contract.state)).append(">").append(contract.state).append("</font></td><td width=20></td></tr></table>");
		}
		if (!player.hasPremiumStatus() && (used < 2)) active.append("<br1><table width=500 bgcolor=\"120D06\"><tr><td align=center><font color=F0D58A>SECOND CONTRACT</font><br1><font color=E0B85C>PREMIUM REQUIRED</font><br1><button value=\"PREMIUM\" action=\"bypass _bbstop;premium/main.html\" width=90 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td></tr></table>");
		html = html.replace("%active%", active.toString());
		CommunityBoardHandler.separateAndSend(html, player);
	}

	private String rewardText(Template template)
	{
		final StringBuilder text = new StringBuilder(template.huntMarks + " Hunt Marks");
		if (template.wantedSeals > 0)
		{
			text.append(" + ").append(template.wantedSeals).append(" Wanted Seals");
		}
		for (RewardItem item : template.rewards)
		{
			final org.l2jmobius.gameserver.model.item.ItemTemplate itemTemplate = ItemData.getInstance().getTemplate(item.itemId);
			text.append(" + ").append(item.count).append(" ").append(itemTemplate == null ? "Item " + item.itemId : itemTemplate.getName());
		}
		return text.toString();
	}

	private String stateColor(ContractState state) { return state == ContractState.COMPLETED || state == ContractState.CLAIMED ? "66CC88" : state == ContractState.ACTIVE ? "E8C77A" : "A8ADB5"; }
}
