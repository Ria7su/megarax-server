package custom.MammonActivityExchange;

import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

import ai.AbstractNpcAI;
import custom.MegaraxHuntContracts.MegaraxHuntContracts;

/** Account-wallet sink hosted by the retail Blacksmith of Mammon (31126). */
public class MammonActivityExchange extends AbstractNpcAI
{
	private static final int BLACKSMITH_OF_MAMMON = 31126;
	private static final int RAX_COIN = 9142;
	private static final int DUNGEON_COIN = 9143;
	private static final int CRAFT_COIN = 4356;
	private static final int MONSTER_CARD = 2300;
	private static final int NOBLESSE_SCROLL = 9154;
	private static final int MALARIA_SCROLL = 9155;

	private MammonActivityExchange()
	{
		addStartNpc(BLACKSMITH_OF_MAMMON);
	}

	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		if ((player == null) || (npc == null) || (npc.getId() != BLACKSMITH_OF_MAMMON))
		{
			return null;
		}
		if (event.startsWith("buy_hunt_"))
		{
			exchange(player, true, event.substring(9));
		}
		else if (event.startsWith("buy_seal_"))
		{
			exchange(player, false, event.substring(9));
		}
		showMain(player, npc);
		return null;
	}

	private void exchange(Player player, boolean huntMarks, String offer)
	{
		int cost;
		int itemId;
		int count;
		if (huntMarks)
		{
			switch (offer)
			{
				case "dungeon": cost = 10; itemId = DUNGEON_COIN; count = 50; break;
				case "craft": cost = 10; itemId = CRAFT_COIN; count = 75; break;
				case "rax": cost = 20; itemId = RAX_COIN; count = 250; break;
				case "card": cost = 25; itemId = MONSTER_CARD; count = 1; break;
				default: return;
			}
		}
		else
		{
			switch (offer)
			{
				case "dungeon": cost = 5; itemId = DUNGEON_COIN; count = 75; break;
				case "craft": cost = 5; itemId = CRAFT_COIN; count = 100; break;
				case "rax": cost = 10; itemId = RAX_COIN; count = 300; break;
				case "noblesse": cost = 20; itemId = NOBLESSE_SCROLL; count = 1; break;
				case "malaria": cost = 20; itemId = MALARIA_SCROLL; count = 1; break;
				default: return;
			}
		}

		final boolean paid = huntMarks
			? MegaraxHuntContracts.changeHuntMarks(player, player.getAccountName(), -cost, "MAMMON_EXCHANGE")
			: MegaraxHuntContracts.changeWantedSeals(player, player.getAccountName(), -cost, "MAMMON_EXCHANGE", offer);
		if (!paid)
		{
			player.sendMessage("You do not have enough " + (huntMarks ? "Hunt Marks." : "Wanted Seals."));
			return;
		}
		if (player.addItem(ItemProcessType.REWARD, itemId, count, player, true) == null)
		{
			if (huntMarks)
			{
				MegaraxHuntContracts.changeHuntMarks(player, player.getAccountName(), cost, "MAMMON_REFUND");
			}
			else
			{
				MegaraxHuntContracts.changeWantedSeals(player, player.getAccountName(), cost, "MAMMON_REFUND", offer);
			}
			player.sendMessage("The exchange failed and your currency was refunded.");
		}
	}

	private void showMain(Player player, Npc npc)
	{
		final MegaraxHuntContracts.Wallet wallet = MegaraxHuntContracts.getInstance().getWallet(player.getAccountName());
		final StringBuilder html = new StringBuilder(3000);
		html.append("<html><title>Megarax Mammon Exchange</title><body bgcolor=000000><center>");
		html.append("<button value=\"MAMMON ACTIVITY EXCHANGE\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=000000><tr><td align=center><font color=LEVEL>Hunt Marks: ").append(wallet.huntMarks).append("</font><br1><font color=FF9966>Wanted Seals: ").append(wallet.wantedSeals).append("</font></td></tr></table><br1>");
		html.append("<table width=292 bgcolor=000000>");
		row(html, "50 Dungeon Coin", "10 Hunt Marks", "buy_hunt_dungeon");
		row(html, "75 Craft Coin", "10 Hunt Marks", "buy_hunt_craft");
		row(html, "250 Rax Coin", "20 Hunt Marks", "buy_hunt_rax");
		row(html, "Monster Card Lv.1", "25 Hunt Marks", "buy_hunt_card");
		row(html, "75 Dungeon Coin", "5 Wanted Seals", "buy_seal_dungeon");
		row(html, "100 Craft Coin", "5 Wanted Seals", "buy_seal_craft");
		row(html, "300 Rax Coin", "10 Wanted Seals", "buy_seal_rax");
		row(html, "Noblesse Scroll", "20 Wanted Seals", "buy_seal_noblesse");
		row(html, "Malaria Scroll", "20 Wanted Seals", "buy_seal_malaria");
		html.append("</table><br1><font color=777777>Account balances. Exchanges are final.</font><br1>");
		html.append("<button value=\"Back\" action=\"bypass -h npc_%objectId%_Link seven_signs/mammblack_1.htm\" width=90 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		html.append("</center></body></html>");
		final NpcHtmlMessage msg = new NpcHtmlMessage(npc.getObjectId());
		msg.setHtml(html.toString());
		player.sendPacket(msg);
	}

	private void row(StringBuilder html, String reward, String price, String event)
	{
		html.append("<tr><td width=170><font color=E0E0E0>").append(reward).append("</font><br1><font color=ae9977>").append(price).append("</font></td><td width=122 align=right><button value=\"Exchange\" action=\"bypass -h Quest MammonActivityExchange ").append(event).append("\" width=90 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td></tr>");
	}

	public static void main(String[] args)
	{
		new MammonActivityExchange();
	}
}
