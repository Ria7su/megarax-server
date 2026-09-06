/*
 * Megarax Monster Card Tiat fixed ground drops.
 */
package custom.MonsterCards;

import java.util.logging.Logger;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.instance.Item;

import ai.AbstractNpcAI;

public class MonsterCardTiatDrops extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MonsterCardTiatDrops.class.getName());
	private static final int TIAT = 9925;
	private static final int[][] REWARDS =
	{
		{ 57, 100000000 }, // Adena
		{ 5575, 50000000 }, // Ancient Adena
		{ 9142, 1000 }, // Rax Coin
		{ 731, 2 }, // Crystal Scroll: Enchant Weapon (Grade A)
		{ 961, 2 }, // Crystal Scroll: Enchant Weapon (Grade S)
	};
	
	private MonsterCardTiatDrops()
	{
		addKillId(TIAT);
		LOGGER.info("MonsterCardTiatDrops: Fixed party-protected ground drops loaded for Tiat.");
	}
	
	@Override
	public void onKill(Npc npc, Player killer, boolean isSummon)
	{
		if ((npc == null) || (npc.getId() != TIAT) || (killer == null))
		{
			return;
		}
		
		final int ownerId = npc.getScriptValue();
		final Player owner = ownerId > 0 ? World.getInstance().getPlayer(ownerId) : null;
		final Player looter = owner != null ? owner : ((ownerId > 0) ? null : killer);
		if (looter == null)
		{
			LOGGER.info("MonsterCardTiatDrops: Tiat killed but card owner is offline. No rewards dropped.");
			scheduleDelete(npc);
			return;
		}
		
		for (int[] reward : REWARDS)
		{
			dropProtected(npc, looter, reward[0], reward[1]);
		}
		
		looter.sendMessage("Tiat rewards dropped on the ground. Only your party can pick them up.");
		if (looter != killer)
		{
			killer.sendMessage("Tiat rewards belong to the Monster Card owner party.");
		}
		LOGGER.info("MonsterCardTiatDrops: Dropped fixed rewards for Tiat owner: " + looter.getName() + ".");
		scheduleDelete(npc);
	}
	
	private void dropProtected(Npc npc, Player owner, int itemId, int count)
	{
		final Item item = npc.dropItem(owner, itemId, count);
		if (item == null)
		{
			LOGGER.warning("MonsterCardTiatDrops: Could not drop item " + itemId + " x" + count + ".");
			return;
		}
		
		item.resetOwnerTimer();
		item.setOwnerId(owner.getObjectId());
		item.getDropProtection().protect(owner);
	}
	
	private void scheduleDelete(Npc npc)
	{
		ThreadPool.schedule(() ->
		{
			if ((npc != null) && !npc.isDecayed())
			{
				npc.deleteMe();
			}
		}, 1000);
	}
	
	public static void main(String[] args)
	{
		new MonsterCardTiatDrops();
	}
}
