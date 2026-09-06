/*
 * Megarax Monster Card handler.
 */
package handlers.itemhandlers;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.handler.IItemHandler;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.Spawn;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Playable;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.olympiad.Olympiad;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.util.LocationUtil;

public class MonsterCard implements IItemHandler
{
	private static final Logger LOGGER = Logger.getLogger(MonsterCard.class.getName());
	private static final int MONSTER_CARD_LV1 = 2300;
	private static final int MONSTER_CARD_NPC_ID = 9925; // Tiat
	private static final int MONSTER_CARD_NPC_DESPAWN = 120000;
	private static final int SUMMON_DISTANCE = 260;
	private static final int GIRAN_HARBOR_MIN_X = 45400;
	private static final int GIRAN_HARBOR_MAX_X = 52300;
	private static final int GIRAN_HARBOR_MIN_Y = 183400;
	private static final int GIRAN_HARBOR_MAX_Y = 190800;
	private static final int GIRAN_HARBOR_MIN_Z = -3900;
	private static final int GIRAN_HARBOR_MAX_Z = -3100;
	
	public MonsterCard()
	{
		LOGGER.info("MonsterCardHandler loaded.");
	}
	
	@Override
	public boolean onItemUse(Playable playable, Item item, boolean forceUse)
	{
		LOGGER.info("MonsterCardHandler useItem called.");
		if (!playable.isPlayer())
		{
			return false;
		}
		
		final Player player = playable.asPlayer();
		if (item.getId() != MONSTER_CARD_LV1)
		{
			player.sendMessage("This Monster Card is not configured.");
			return false;
		}
		
		player.sendMessage("Monster Card handler is working.");
		final String validationError = validateUse(player);
		if (validationError != null)
		{
			player.sendMessage(validationError);
			return false;
		}
		
		if (NpcData.getInstance().getTemplate(MONSTER_CARD_NPC_ID) == null)
		{
			player.sendMessage("Monster Card monster is missing.");
			LOGGER.warning("MonsterCardHandler missing NPC template: " + MONSTER_CARD_NPC_ID);
			return false;
		}
		
		if (!player.destroyItem(ItemProcessType.FEE, item.getObjectId(), 1, player, true))
		{
			player.sendMessage("Monster Card could not be consumed.");
			return false;
		}
		
		LOGGER.info("MonsterCardHandler consumed 1 card for player: " + player.getName() + " itemId: " + item.getId());
		if (!spawnMonsterCardNpc(player))
		{
			player.addItem(ItemProcessType.REFUND, MONSTER_CARD_LV1, 1, player, true);
			return false;
		}
		return true;
	}
	
	private String validateUse(Player player)
	{
		if (player.isDead() || player.isAlikeDead())
		{
			return "You cannot use a Monster Card while dead.";
		}
		if (!isInsideGiranHarbor(player))
		{
			return "Monster Card Lv. 1 can only be used inside Giran Harbor.";
		}
		if (player.isInOlympiadMode() || Olympiad.getInstance().isRegistered(player))
		{
			return "You cannot use a Monster Card while registered in Olympiad.";
		}
		if (player.isOnEvent() || player.isRegisteredOnEvent())
		{
			return "You cannot use a Monster Card while registered in a PvP event.";
		}
		if (player.getInstanceId() > 0)
		{
			return "You cannot use a Monster Card while inside another instance.";
		}
		if (player.isInDuel())
		{
			return "You cannot use a Monster Card while in a duel.";
		}
		if (player.isInSiege() || player.isInsideZone(ZoneId.SIEGE))
		{
			return "You cannot use a Monster Card during a siege.";
		}
		if (player.isInsideZone(ZoneId.NO_SUMMON_FRIEND) || player.isInsideZone(ZoneId.JAIL))
		{
			return "You cannot use a Monster Card in this restricted zone.";
		}
		return null;
	}
	
	private boolean spawnMonsterCardNpc(Player player)
	{
		try
		{
			final double radians = Math.toRadians(LocationUtil.convertHeadingToDegree(player.getHeading()));
			final int targetX = (int) (player.getX() + (SUMMON_DISTANCE * Math.cos(radians)));
			final int targetY = (int) (player.getY() + (SUMMON_DISTANCE * Math.sin(radians)));
			final int targetZ = player.getZ();
			final Location summonLoc = GeoEngine.getInstance().getValidLocation(player.getX(), player.getY(), player.getZ(), targetX, targetY, targetZ, player.getInstanceId());
			
			final Spawn spawn = new Spawn(MONSTER_CARD_NPC_ID);
			spawn.setXYZ(summonLoc.getX(), summonLoc.getY(), summonLoc.getZ());
			spawn.setHeading(player.getHeading());
			spawn.stopRespawn();
			
			final Npc npc = spawn.doSpawn(true);
			npc.setSummoner(player);
			npc.setScriptValue(player.getObjectId());
			npc.scheduleDespawn(MONSTER_CARD_NPC_DESPAWN);
			npc.broadcastInfo();
			
			player.sendMessage("Monster Card summoned Tiat nearby.");
			LOGGER.info("MonsterCardHandler spawned NPC " + MONSTER_CARD_NPC_ID + " for player: " + player.getName() + " near player at [" + summonLoc.getX() + ", " + summonLoc.getY() + ", " + summonLoc.getZ() + "].");
			return true;
		}
		catch (Exception e)
		{
			player.sendMessage("Monster Card summon failed. Check gameserver log.");
			LOGGER.warning("MonsterCardHandler failed to spawn NPC " + MONSTER_CARD_NPC_ID + " for player: " + player.getName() + " error: " + e.getMessage());
			return false;
		}
	}
	
	private boolean isInsideGiranHarbor(Player player)
	{
		return (player.getX() >= GIRAN_HARBOR_MIN_X) && (player.getX() <= GIRAN_HARBOR_MAX_X) && (player.getY() >= GIRAN_HARBOR_MIN_Y) && (player.getY() <= GIRAN_HARBOR_MAX_Y) && (player.getZ() >= GIRAN_HARBOR_MIN_Z) && (player.getZ() <= GIRAN_HARBOR_MAX_Z);
	}
}
