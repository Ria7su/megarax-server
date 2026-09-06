/*
 * Custom Megarax Hardcore Zone entry.
 */
package custom.MegaraxHardcoreZone;

import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.network.serverpackets.ExShowScreenMessage;

import ai.AbstractNpcAI;

public class MegaraxHardcoreZone extends AbstractNpcAI
{
	private static final int GATEKEEPER = 9901;
	private static final int MIN_LEVEL = 76;
	private static final int MIN_PARTY_MEMBERS = 4;
	private static final Location[] ENTRY_POINTS =
	{
		new Location(189964, -116820, -1624),
		new Location(190104, -116720, -1624),
		new Location(189824, -116720, -1624),
		new Location(189964, -116980, -1624),
		new Location(190124, -116940, -1624),
		new Location(189804, -116940, -1624),
	};
	
	private MegaraxHardcoreZone()
	{
		addStartNpc(GATEKEEPER);
		addTalkId(GATEKEEPER);
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		if (player == null)
		{
			return null;
		}
		
		if (event.equals("enter"))
		{
			enterHardcoreZone(player);
		}
		return null;
	}
	
	private void enterHardcoreZone(Player player)
	{
		if (!player.isInParty())
		{
			player.sendMessage("Hardcore Zone requires a party of at least 4 members.");
			return;
		}
		
		final Party party = player.getParty();
		if ((party == null) || (party.getLeader() == null) || (party.getLeader().getObjectId() != player.getObjectId()))
		{
			player.sendMessage("Only the party leader can enter Hardcore Zone.");
			return;
		}
		if (party.getMembers().size() < MIN_PARTY_MEMBERS)
		{
			player.sendMessage("Hardcore Zone requires at least 4 party members.");
			return;
		}
		
		for (Player member : party.getMembers())
		{
			if ((member != null) && (member.getLevel() < MIN_LEVEL))
			{
				player.sendMessage("All party members must be level " + MIN_LEVEL + "+ to enter Hardcore Zone.");
				return;
			}
		}
		
		int index = 0;
		for (Player member : party.getMembers())
		{
			if (member == null)
			{
				continue;
			}
			member.teleToLocation(ENTRY_POINTS[index % ENTRY_POINTS.length]);
			member.sendPacket(new ExShowScreenMessage("Hardcore Zone: Hall of Flames", 3000));
			member.sendMessage("Hardcore Zone opened. Stay together and clear Hall of Flames.");
			index++;
		}
	}
	
	public static void main(String[] args)
	{
		new MegaraxHardcoreZone();
	}
}
