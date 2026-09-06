/*
 * Megarax Monster Cards - premium solo raid cards.
 */
package custom.MonsterCards;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.commons.util.Rnd;
import org.l2jmobius.gameserver.managers.InstanceManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDeath;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogout;
import org.l2jmobius.gameserver.model.events.listeners.AbstractEventListener;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.instancezone.InstanceWorld;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.olympiad.Olympiad;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.network.serverpackets.ExShowScreenMessage;

import ai.AbstractNpcAI;

public class MonsterCardManager extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MonsterCardManager.class.getName());
	
	private static final int INSTANCE_TEMPLATE_ID = 3049; // Coliseum dynamic instance.
	private static final Location ENTRY_LOC = new Location(149495, 46719, -3413);
	private static final Location RAID_SPAWN_LOC = new Location(149495, 46719, -3413, 32768);
	private static final Location EXIT_LOC = new Location(83400, 147943, -3404);
	private static final int TELEPORT_OUT_DELAY = 15 * 1000;
	private static final int MONITOR_DELAY = 10 * 1000;
	private static final String DEBUG_VERSION = "MC-2300-BORAK-v3";
	
	private static final int MONSTER_CARD_LV1 = 2300;
	private static final int BORAK = 9918;
	private static final int ADENA = 57;
	private static final int RAX_COIN = 9142;
	private static final int DUNGEON_COIN = 9143;
	private static final int CRAFT_COIN = 4356;
	private static final int SECRET_BOOK_OF_GIANTS = 6622;
	private static final int BLESSED_SCROLL_ENCHANT_WEAPON_S = 6577;
	private static final int BLESSED_SCROLL_ENCHANT_ARMOR_S = 6578;
	
	private static final Map<Integer, CardConfig> CARDS = new ConcurrentHashMap<>();
	private static final Map<Integer, Encounter> ACTIVE_ENCOUNTERS = new ConcurrentHashMap<>();
	private static final Map<Integer, Boolean> USE_LOCKS = new ConcurrentHashMap<>();
	private static MonsterCardManager _instance;
	
	static
	{
		registerCard(new CardConfig(
			MONSTER_CARD_LV1,
			"Monster Card Lv. 1",
			1,
			BORAK,
			"Borak",
			10 * 60 * 1000,
			PartyMode.SOLO,
			new Reward(ADENA, 200000, 300000, 100000),
			new Reward(DUNGEON_COIN, 20, 30, 100000),
			new Reward(CRAFT_COIN, 8, 12, 100000),
			new Reward(RAX_COIN, 1, 2, 25000),
			new Reward(SECRET_BOOK_OF_GIANTS, 1, 1, 35000),
			new Reward(BLESSED_SCROLL_ENCHANT_ARMOR_S, 1, 1, 20000),
			new Reward(BLESSED_SCROLL_ENCHANT_WEAPON_S, 1, 1, 10000)));
	}
	
	public static MonsterCardManager getInstance()
	{
		return _instance;
	}
	
	private MonsterCardManager()
	{
		_instance = this;
		addKillId(BORAK);
		LOGGER.info("MonsterCardManager: Monster Cards system loaded. " + DEBUG_VERSION);
	}
	
	private static void registerCard(CardConfig config)
	{
		CARDS.put(config.itemId, config);
	}
	
	public boolean useCard(Player player, Item item)
	{
		if ((player == null) || (item == null))
		{
			return false;
		}
		player.sendMessage("Monster Card manager " + DEBUG_VERSION + " received item " + item.getId() + ".");
		
		final CardConfig config = CARDS.get(item.getId());
		if (config == null)
		{
			player.sendMessage("Monster Card config missing for item " + item.getId() + ". Supported item: " + MONSTER_CARD_LV1 + ".");
			return false;
		}
		
		final int playerId = player.getObjectId();
		if (USE_LOCKS.putIfAbsent(playerId, Boolean.TRUE) != null)
		{
			player.sendMessage("Please wait before using another Monster Card.");
			return false;
		}
		
		try
		{
			player.sendMessage("Monster Card Lv. 1 activated at " + player.getX() + ", " + player.getY() + ", " + player.getZ() + ". Checking location...");
			final String validationError = validateUse(player, config);
			if (validationError != null)
			{
				player.sendMessage(validationError);
				return false;
			}
			
			if (!player.destroyItem(ItemProcessType.FEE, item.getObjectId(), 1, player, true))
			{
				player.sendMessage("The Monster Card could not be consumed.");
				return false;
			}
			
			if (!startEncounter(player, config))
			{
				player.addItem(ItemProcessType.REFUND, config.itemId, 1, player, true);
				player.sendMessage("The Monster Card could not open its arena. Your card has been refunded.");
				return false;
			}
			
			return true;
		}
		finally
		{
			USE_LOCKS.remove(playerId);
		}
	}
	
	private String validateUse(Player player, CardConfig config)
	{
		if (player.isDead() || player.isAlikeDead())
		{
			return "You cannot use a Monster Card while dead.";
		}
		if (ACTIVE_ENCOUNTERS.containsKey(player.getObjectId()))
		{
			return "You already have an active Monster Card raid.";
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
		if (isInsideCustomDungeonArea(player))
		{
			return "You cannot use a Monster Card while inside another custom dungeon.";
		}
		if (player.isFishing())
		{
			return "You cannot use a Monster Card while fishing.";
		}
		if (player.isCursedWeaponEquipped() || (player.getKarma() > 0))
		{
			return "You cannot use a Monster Card with karma or a cursed weapon.";
		}
		if (!player.isInventoryUnder90(true) || (player.getWeightPenalty() >= 3))
		{
			return "Free some inventory space or weight before using a Monster Card.";
		}
		if ((config.mode == PartyMode.SOLO) && player.isInParty())
		{
			return config.name + " is solo content. Leave your party before using it.";
		}
		if (player.getInventory().getInventoryItemCount(config.itemId, -1) <= 0)
		{
			return "You do not have the required Monster Card.";
		}
		return null;
	}
	
	private boolean startEncounter(Player player, CardConfig config)
	{
		final InstanceWorld world = new InstanceWorld();
		world.setInstance(InstanceManager.getInstance().createDynamicInstance(INSTANCE_TEMPLATE_ID));
		InstanceManager.getInstance().addWorld(world);
		world.addAllowed(player);
		
		final int instanceId = world.getInstanceId();
		final Npc raid = addSpawn(config.raidId, RAID_SPAWN_LOC, false, config.timeLimitMs, false, instanceId);
		if (raid == null)
		{
			InstanceManager.getInstance().destroyInstance(instanceId);
			return false;
		}
		
		raid.setScriptValue(player.getObjectId());
		final Encounter encounter = new Encounter(player.getObjectId(), player.getName(), config, raid, instanceId);
		ACTIVE_ENCOUNTERS.put(player.getObjectId(), encounter);
		
		addLogoutListener(player);
		addDeathListener(player);
		player.teleToLocation(ENTRY_LOC, instanceId, 50);
		screen(player, config.name + " activated. Defeat the summoned raid!", 5);
		player.sendMessage(config.name + " has awakened " + config.raidName + ".");
		player.sendMessage("A sealed monster has been released. Defeat it before time runs out!");
		startQuestTimer("monster_card_timeout_" + player.getObjectId(), config.timeLimitMs, null, player);
		startQuestTimer("monster_card_monitor_" + player.getObjectId(), MONITOR_DELAY, null, player);
		return true;
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		if (event.startsWith("monster_card_timeout_"))
		{
			final int playerId = parseObjectId(event, "monster_card_timeout_");
			failEncounter(playerId, "The summoned monster vanished. The power of the card has been lost.", true);
			return null;
		}
		if (event.startsWith("monster_card_monitor_"))
		{
			final int playerId = parseObjectId(event, "monster_card_monitor_");
			monitorEncounter(playerId);
			return null;
		}
		if (event.startsWith("monster_card_exit_"))
		{
			final int playerId = parseObjectId(event, "monster_card_exit_");
			final Player activePlayer = getPlayerByObjectId(playerId);
			if (activePlayer != null)
			{
				activePlayer.teleToLocation(EXIT_LOC);
			}
			return null;
		}
		return null;
	}
	
	@Override
	public void onKill(Npc npc, Player killer, boolean isSummon)
	{
		if ((npc == null) || (npc.getId() != BORAK))
		{
			return;
		}
		
		final int ownerId = npc.getScriptValue();
		final Encounter encounter = ACTIVE_ENCOUNTERS.get(ownerId);
		if ((encounter == null) || (encounter.raidObjectId != npc.getObjectId()))
		{
			return;
		}
		
		if ((killer == null) || (killer.getObjectId() != ownerId) || (killer.getInstanceId() != encounter.instanceId))
		{
			return;
		}
		
		finishEncounter(encounter, killer);
	}
	
	private void finishEncounter(Encounter encounter, Player player)
	{
		if (!ACTIVE_ENCOUNTERS.remove(encounter.ownerId, encounter))
		{
			return;
		}
		
		cancelQuestTimer("monster_card_timeout_" + encounter.ownerId, null, player);
		cancelQuestTimer("monster_card_monitor_" + encounter.ownerId, null, player);
		removeListeners(player);
		giveRewards(player, encounter.config);
		screen(player, "You defeated the summoned monster and claimed the power of the card.", 5);
		player.sendMessage("You defeated the summoned monster and claimed the power of the card.");
		player.sendMessage("You will be returned to town in 15 seconds.");
		startQuestTimer("monster_card_exit_" + encounter.ownerId, TELEPORT_OUT_DELAY, null, player);
		ThreadPool.schedule(() -> destroyInstance(encounter.instanceId), TELEPORT_OUT_DELAY + 3000);
	}
	
	private void failEncounter(int playerId, String message, boolean teleportOut)
	{
		final Encounter encounter = ACTIVE_ENCOUNTERS.remove(playerId);
		if (encounter == null)
		{
			return;
		}
		
		final Player player = getPlayerByObjectId(playerId);
		if (player != null)
		{
			cancelQuestTimer("monster_card_timeout_" + playerId, null, player);
			cancelQuestTimer("monster_card_monitor_" + playerId, null, player);
			removeListeners(player);
			player.sendMessage(message);
			screen(player, message, 5);
			if (teleportOut)
			{
				player.teleToLocation(EXIT_LOC);
			}
		}
		
		if ((encounter.raid != null) && !encounter.raid.isDead() && !encounter.raid.isDecayed())
		{
			encounter.raid.deleteMe();
		}
		destroyInstance(encounter.instanceId);
	}
	
	private void monitorEncounter(int playerId)
	{
		final Encounter encounter = ACTIVE_ENCOUNTERS.get(playerId);
		if (encounter == null)
		{
			return;
		}
		
		final Player player = getPlayerByObjectId(playerId);
		if (player == null)
		{
			failEncounter(playerId, "Monster Card raid cleaned because the player disconnected.", false);
			return;
		}
		if (player.isDead() || player.isAlikeDead())
		{
			failEncounter(playerId, "The summoned monster vanished because you died. The power of the card has been lost.", true);
			return;
		}
		if (player.getInstanceId() != encounter.instanceId)
		{
			failEncounter(playerId, "The summoned monster vanished because you left the arena. The power of the card has been lost.", false);
			return;
		}
		
		startQuestTimer("monster_card_monitor_" + playerId, MONITOR_DELAY, null, player);
	}
	
	private void giveRewards(Player player, CardConfig config)
	{
		for (Reward reward : config.rewards)
		{
			if (Rnd.get(100000) > reward.chance)
			{
				continue;
			}
			
			final int amount = reward.min == reward.max ? reward.min : Rnd.get(reward.min, reward.max);
			if (amount > 0)
			{
				player.addItem(ItemProcessType.REWARD, reward.itemId, amount, player, true);
			}
		}
	}
	
	private void addLogoutListener(Player player)
	{
		player.addListener(new ConsumerEventListener(player, EventType.ON_PLAYER_LOGOUT, (OnPlayerLogout event) -> onPlayerLogout(event), this));
	}
	
	private void addDeathListener(Player player)
	{
		player.addListener(new ConsumerEventListener(player, EventType.ON_CREATURE_DEATH, (OnCreatureDeath event) -> onPlayerDeath(event), this));
	}
	
	private void onPlayerLogout(OnPlayerLogout event)
	{
		if ((event != null) && (event.getPlayer() != null))
		{
			failEncounter(event.getPlayer().getObjectId(), "Monster Card raid cleaned because the player disconnected.", false);
		}
	}
	
	private void onPlayerDeath(OnCreatureDeath event)
	{
		if ((event == null) || (event.getTarget() == null) || !event.getTarget().isPlayer())
		{
			return;
		}
		
		final Player player = event.getTarget().asPlayer();
		if (ACTIVE_ENCOUNTERS.containsKey(player.getObjectId()))
		{
			failEncounter(player.getObjectId(), "The summoned monster vanished because you died. The power of the card has been lost.", true);
		}
	}
	
	private void removeListeners(Player player)
	{
		final Collection<AbstractEventListener> logoutListeners = player.getListeners(EventType.ON_PLAYER_LOGOUT);
		for (AbstractEventListener listener : logoutListeners)
		{
			if (listener.getOwner() == this)
			{
				listener.unregisterMe();
			}
		}
		
		final Collection<AbstractEventListener> deathListeners = player.getListeners(EventType.ON_CREATURE_DEATH);
		for (AbstractEventListener listener : deathListeners)
		{
			if (listener.getOwner() == this)
			{
				listener.unregisterMe();
			}
		}
	}
	
	private boolean isInsideCustomDungeonArea(Player player)
	{
		return isNear(player, 189400, -85600, -7219, 2500) || // Daily Dungeon room
			isNear(player, 174230, -76168, -5134, 3500) || // Farm Dungeon room
			isNear(player, 87473, -43306, -4159, 2500) || // Elite Green
			isNear(player, 165054, -47861, -3560, 2500) || // Elite Blue
			isNear(player, 112451, -76760, 85, 2500); // Elite Red
	}
	
	private boolean isInsideGiranHarbor(Player player)
	{
		return (player.getX() >= 45400) && (player.getX() <= 52300) &&
			(player.getY() >= 183400) && (player.getY() <= 190800) &&
			(player.getZ() >= -3900) && (player.getZ() <= -3100);
	}
	
	private boolean isNear(Player player, int x, int y, int z, int range)
	{
		return (Math.abs(player.getX() - x) <= range) && (Math.abs(player.getY() - y) <= range) && (Math.abs(player.getZ() - z) <= 1200);
	}
	
	private Player getPlayerByObjectId(int objectId)
	{
		for (Player player : World.getInstance().getPlayers())
		{
			if (player.getObjectId() == objectId)
			{
				return player;
			}
		}
		return null;
	}
	
	private int parseObjectId(String event, String prefix)
	{
		try
		{
			return Integer.parseInt(event.substring(prefix.length()));
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}
	
	private void screen(Player player, String text, int seconds)
	{
		player.sendPacket(new ExShowScreenMessage(text, ExShowScreenMessage.TOP_CENTER, seconds * 1000, 0, true, true));
	}
	
	private void destroyInstance(int instanceId)
	{
		try
		{
			InstanceManager.getInstance().destroyInstance(instanceId);
		}
		catch (Exception e)
		{
			LOGGER.warning("MonsterCardManager: Could not destroy instance " + instanceId + ": " + e.getMessage());
		}
	}
	
	public static void main(String[] args)
	{
		new MonsterCardManager();
	}
	
	private enum PartyMode
	{
		SOLO,
		PARTY
	}
	
	private static class CardConfig
	{
		private final int itemId;
		private final String name;
		private final int level;
		private final int raidId;
		private final String raidName;
		private final int timeLimitMs;
		private final PartyMode mode;
		private final Reward[] rewards;
		
		private CardConfig(int itemId, String name, int level, int raidId, String raidName, int timeLimitMs, PartyMode mode, Reward... rewards)
		{
			this.itemId = itemId;
			this.name = name;
			this.level = level;
			this.raidId = raidId;
			this.raidName = raidName;
			this.timeLimitMs = timeLimitMs;
			this.mode = mode;
			this.rewards = rewards;
		}
	}
	
	private static class Reward
	{
		private final int itemId;
		private final int min;
		private final int max;
		private final int chance;
		
		private Reward(int itemId, int min, int max, int chance)
		{
			this.itemId = itemId;
			this.min = min;
			this.max = max;
			this.chance = chance;
		}
	}
	
	private static class Encounter
	{
		private final int ownerId;
		private final String ownerName;
		private final CardConfig config;
		private final Npc raid;
		private final int raidObjectId;
		private final int instanceId;
		private final long startedAt;
		
		private Encounter(int ownerId, String ownerName, CardConfig config, Npc raid, int instanceId)
		{
			this.ownerId = ownerId;
			this.ownerName = ownerName;
			this.config = config;
			this.raid = raid;
			this.raidObjectId = raid.getObjectId();
			this.instanceId = instanceId;
			this.startedAt = System.currentTimeMillis();
		}
	}
}
