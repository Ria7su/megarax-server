package handlers.itemhandlers;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.handler.IItemHandler;
import org.l2jmobius.gameserver.model.actor.Playable;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.skill.BuffInfo;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.enums.SkillFinishType;

public class MegaraxFarmBoost implements IItemHandler
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxFarmBoost.class.getName());
	public static final int ITEM_ID = 9144;
	public static final int SKILL_ID = 7073;
	private static final int DURATION = 3600;

	@Override
	public boolean onItemUse(Playable playable, Item item, boolean forceUse)
	{
		if (!playable.isPlayer())
		{
			playable.sendMessage("Only players can use this item.");
			return false;
		}

		final Player player = playable.asPlayer();
		synchronized (player)
		{
			if ((item.getId() != ITEM_ID) || (item.getOwnerId() != player.getObjectId()) || (item.getCount() < 1))
			{
				return false;
			}
			if (player.isDead() || player.isAlikeDead() || player.inObserverMode())
			{
				player.sendMessage("You cannot activate the farm boost in your current state.");
				return false;
			}
			if (player.isInOlympiadMode() || player.isOnEvent() || player.isRegisteredOnEvent())
			{
				player.sendMessage("You cannot activate the farm boost during Olympiad or an event.");
				return false;
			}

			final Skill skill = SkillData.getInstance().getSkill(SKILL_ID, 1);
			if (skill == null)
			{
				LOGGER.warning("Missing skill " + SKILL_ID + " for item " + ITEM_ID + ".");
				player.sendMessage("The farm boost is temporarily unavailable.");
				return false;
			}
			final BuffInfo current = player.getEffectList().getBuffInfoBySkillId(SKILL_ID);
			if ((current != null) && (current.getTime() > 0))
			{
				player.sendMessage("This farm boost is already active. Remaining time: " + ((current.getTime() + 59) / 60) + " minute(s).");
				return false;
			}

			skill.applyEffects(player, player);
			final BuffInfo applied = player.getEffectList().getBuffInfoBySkillId(SKILL_ID);
			if (applied == null)
			{
				player.sendMessage("The farm boost could not be activated; the item was not consumed.");
				return false;
			}
			applied.setAbnormalTime(DURATION);
			if (!player.destroyItem(ItemProcessType.NONE, item.getObjectId(), 1, player, false))
			{
				player.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, SKILL_ID);
				player.sendMessage("The item could not be consumed; the farm boost was removed.");
				return false;
			}

			player.sendMessage("Farm boost activated for 1 hour: +50% drop amount, x2 Dungeon Coin and +5% enchant success.");
			LOGGER.info("Activated item=" + ITEM_ID + " player=" + player.getName() + " account=" + player.getAccountName() + " duration=" + DURATION + ".");
			return true;
		}
	}
}
