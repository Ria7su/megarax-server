package custom.MegaraxClanDominion;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.events.Containers;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.ListenerRegisterType;
import org.l2jmobius.gameserver.model.events.annotations.RegisterEvent;
import org.l2jmobius.gameserver.model.events.annotations.RegisterType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageReceived;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDeath;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerPvPKill;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.model.zone.ZoneType;
import org.l2jmobius.gameserver.network.serverpackets.ExShowScreenMessage;
import org.l2jmobius.gameserver.util.Broadcast;

import ai.AbstractNpcAI;

public class ClanDominionManager extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(ClanDominionManager.class.getName());
	private static ClanDominionManager INSTANCE;
	
	private final Map<String, Long> _sameTargetCooldown = new ConcurrentHashMap<>();
	
	private ClanDominionManager()
	{
		INSTANCE = this;
		ClanDominionEngine.reload();
		Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_PVP_KILL, (OnPlayerPvPKill event) -> onPlayerPvPKill(event), this));
		ThreadPool.scheduleAtFixedRate(() -> runPeriodicTasks(), 60000, 60000);
		LOGGER.info("Megarax Clan Dominion: System loaded.");
	}
	
	public static ClanDominionManager getInstance()
	{
		return INSTANCE;
	}
	
	public void reload()
	{
		ClanDominionEngine.reload();
	}
	
	private void runPeriodicTasks()
	{
		if (!ClanDominionConfig.ENABLED)
		{
			return;
		}
		ClanSeasonManager.getInstance().ensureCurrentSeason();
		ClanRivalryManager.getInstance().finishExpiredRivalries();
		ClanTerritoryManager.getInstance().syncLegacyTerritoryConquest();
		cleanCooldowns();
	}
	
	private void onPlayerPvPKill(OnPlayerPvPKill event)
	{
		if (!ClanDominionConfig.ENABLED)
		{
			return;
		}
		final Player killer = event.getPlayer();
		final Player target = event.getTarget();
		final Validation validation = validatePvPKill(killer, target);
		if (!validation.valid)
		{
			if ((killer != null) && validation.notifyKiller)
			{
				killer.sendMessage(validation.message);
			}
			return;
		}
		
		final int killerClanId = killer.getClanId();
		final int targetClanId = target.getClanId();
		int points = validation.points;
		if (ClanRivalryManager.getInstance().isActiveRivalry(killerClanId, targetClanId))
		{
			points *= Math.max(1, ClanDominionConfig.RIVALRY_KILL_MULTIPLIER);
		}
		
		ClanDominionData.addPvpKill(killerClanId, targetClanId);
		ClanDominionEngine.addDominionPoints(killerClanId, targetClanId, points, validation.reason, killer.getName(), target.getName(), validation.context);
		ClanRivalryManager.getInstance().recordKill(killerClanId, targetClanId);
		ClanRaidObjectiveManager.getInstance().recordPvpNearRaid(killer);
		ClanAchievementManager.getInstance().addProgress(killerClanId, ClanAchievementManager.FIRST_BLOOD, 1, killer.getName());
		ClanAchievementManager.getInstance().addProgress(killerClanId, ClanAchievementManager.WAR_MACHINE, 1, killer.getName());
		
		if ("PVP_ZONE_KILL".equals(validation.reason))
		{
			ClanMissionManager.getInstance().addProgress(killer, ClanMissionManager.TYPE_PVP_ZONE_KILL, 1);
		}
		else if ("CLAN_WAR_KILL".equals(validation.reason) || "RIVALRY_KILL".equals(validation.reason))
		{
			ClanMissionManager.getInstance().addProgress(killer, ClanMissionManager.TYPE_CLAN_WAR_KILL, 1);
		}
		else if ("SIEGE_KILL".equals(validation.reason))
		{
			ClanMissionManager.getInstance().addProgress(killer, ClanMissionManager.TYPE_SIEGE_KILL, 1);
			ClanDominionData.addScoreColumn(killerClanId, "siege_score", points);
		}
		
		killer.sendPacket(new ExShowScreenMessage("Clan Dominion +" + points + " points", 2500));
	}
	
	@RegisterEvent(EventType.ON_CREATURE_DAMAGE_RECEIVED)
	@RegisterType(ListenerRegisterType.GLOBAL)
	public void onCreatureDamageReceived(OnCreatureDamageReceived event)
	{
		if (!ClanDominionConfig.ENABLED || (event == null) || (event.getTarget() == null) || (event.getAttacker() == null) || !event.getTarget().isNpc())
		{
			return;
		}
		Player attacker = null;
		if (event.getAttacker().isPlayer())
		{
			attacker = event.getAttacker().asPlayer();
		}
		else if (event.getAttacker().isSummon() && (event.getAttacker().asSummon().getOwner() != null))
		{
			attacker = event.getAttacker().asSummon().getOwner();
		}
		if (attacker == null)
		{
			return;
		}
		final Npc npc = event.getTarget().asNpc();
		ClanRaidObjectiveManager.getInstance().rememberRaidPosition(npc);
		ClanRaidObjectiveManager.getInstance().recordDamage(npc, attacker, event.getDamage());
	}
	
	@RegisterEvent(EventType.ON_CREATURE_DEATH)
	@RegisterType(ListenerRegisterType.GLOBAL)
	public void onCreatureDeath(OnCreatureDeath event)
	{
		if (!ClanDominionConfig.ENABLED || (event == null) || (event.getTarget() == null) || !event.getTarget().isNpc())
		{
			return;
		}
		Player killer = null;
		if ((event.getAttacker() != null) && event.getAttacker().isPlayer())
		{
			killer = event.getAttacker().asPlayer();
		}
		else if ((event.getAttacker() != null) && event.getAttacker().isSummon() && (event.getAttacker().asSummon().getOwner() != null))
		{
			killer = event.getAttacker().asSummon().getOwner();
		}
		ClanRaidObjectiveManager.getInstance().recordRaidDeath(event.getTarget().asNpc(), killer);
	}
	
	public long addDominionPoints(int clanId, int targetClanId, int amount, String reason, String actorName, String targetName, String metadata)
	{
		return ClanDominionEngine.addDominionPoints(clanId, targetClanId, amount, reason, actorName, targetName, metadata);
	}
	
	public void notifyDungeonComplete(Player player)
	{
		ClanDominionEngine.notifyDungeonComplete(player);
	}
	
	public void notifyTerritoryCapture(String territoryId, String territoryName, int clanId, String actorName)
	{
		ClanDominionEngine.notifyTerritoryCapture(territoryId, territoryName, clanId, actorName);
	}
	
	private Validation validatePvPKill(Player killer, Player target)
	{
		if ((killer == null) || (target == null) || killer.isGM() || target.isGM() || (killer.getObjectId() == target.getObjectId()))
		{
			return Validation.reject(false, "");
		}
		if ((killer.getClanId() <= 0) || (target.getClanId() <= 0))
		{
			return Validation.reject(false, "");
		}
		if (killer.getClanId() == target.getClanId())
		{
			return Validation.reject(true, "Clan Dominion: same clan kills do not count.");
		}
		if ((killer.getAllyId() > 0) && (killer.getAllyId() == target.getAllyId()))
		{
			return Validation.reject(true, "Clan Dominion: allied clan kills do not count.");
		}
		if ((killer.getLevel() < ClanDominionConfig.MIN_LEVEL_FOR_PVP) || (target.getLevel() < ClanDominionConfig.MIN_LEVEL_FOR_PVP))
		{
			return Validation.reject(false, "");
		}
		if (isSameRealPlayer(killer, target))
		{
			return Validation.reject(true, "Clan Dominion: anti-feed protection blocked this kill.");
		}
		final String cooldownKey = killer.getClanId() + ":" + target.getObjectId();
		final long now = System.currentTimeMillis();
		final Long cooldown = _sameTargetCooldown.get(cooldownKey);
		if ((cooldown != null) && (cooldown > now))
		{
			return Validation.reject(true, "Clan Dominion: same target cooldown is active.");
		}
		
		final boolean rivalry = ClanRivalryManager.getInstance().isActiveRivalry(killer.getClanId(), target.getClanId());
		if (rivalry)
		{
			_sameTargetCooldown.put(cooldownKey, now + ClanDominionConfig.SAME_TARGET_COOLDOWN);
			return Validation.accept("RIVALRY_KILL", ClanDominionConfig.POINTS_CLAN_WAR_KILL, "Official Rivalry Contract");
		}
		if (ClanDominionConfig.ALLOW_SIEGES && (killer.isInSiege() || target.isInSiege() || killer.isInsideZone(ZoneId.SIEGE) || target.isInsideZone(ZoneId.SIEGE)))
		{
			_sameTargetCooldown.put(cooldownKey, now + ClanDominionConfig.SAME_TARGET_COOLDOWN);
			return Validation.accept("SIEGE_KILL", ClanDominionConfig.POINTS_SIEGE_KILL, "Siege combat");
		}
		if (ClanDominionConfig.ALLOW_CLAN_WAR && killer.isAtWarWith(target))
		{
			_sameTargetCooldown.put(cooldownKey, now + ClanDominionConfig.SAME_TARGET_COOLDOWN);
			return Validation.accept("CLAN_WAR_KILL", ClanDominionConfig.POINTS_CLAN_WAR_KILL, "Clan war");
		}
		if (ClanDominionConfig.ALLOW_PVP_ZONES && (killer.isInsideZone(ZoneId.PVP) || target.isInsideZone(ZoneId.PVP) || isInsideConfiguredPvpZone(killer) || isInsideConfiguredPvpZone(target)))
		{
			_sameTargetCooldown.put(cooldownKey, now + ClanDominionConfig.SAME_TARGET_COOLDOWN);
			return Validation.accept("PVP_ZONE_KILL", ClanDominionConfig.POINTS_PVP_ZONE_KILL, "PvP zone");
		}
		if (ClanDominionConfig.ALLOW_NORMAL_WORLD)
		{
			_sameTargetCooldown.put(cooldownKey, now + ClanDominionConfig.SAME_TARGET_COOLDOWN);
			return Validation.accept("WORLD_KILL", ClanDominionConfig.POINTS_PVP_ZONE_KILL, "Open world");
		}
		return Validation.reject(false, "");
	}
	
	private boolean isInsideConfiguredPvpZone(Player player)
	{
		for (String zoneName : ClanDominionConfig.PVP_ZONE_NAMES)
		{
			final ZoneType zone = ZoneManager.getInstance().getZoneByName(zoneName);
			if ((zone != null) && zone.isEnabled() && zone.isInsideZone(player))
			{
				return true;
			}
		}
		return false;
	}
	
	private boolean isSameRealPlayer(Player killer, Player target)
	{
		if (ClanDominionConfig.BLOCK_SAME_IP)
		{
			final String killerIp = killer.getIPAddress();
			final String targetIp = target.getIPAddress();
			if ((killerIp != null) && !killerIp.isEmpty() && killerIp.equals(targetIp))
			{
				return true;
			}
		}
		if (ClanDominionConfig.BLOCK_SAME_HWID)
		{
			final String killerHwid = ClanDominionUtil.hardwareId(killer);
			final String targetHwid = ClanDominionUtil.hardwareId(target);
			return (killerHwid != null) && !killerHwid.isEmpty() && killerHwid.equals(targetHwid);
		}
		return false;
	}
	
	private void cleanCooldowns()
	{
		final long now = System.currentTimeMillis();
		_sameTargetCooldown.entrySet().removeIf(entry -> entry.getValue() <= now);
	}
	
	private static final class Validation
	{
		private final boolean valid;
		private final boolean notifyKiller;
		private final String message;
		private final String reason;
		private final String context;
		private final int points;
		
		private Validation(boolean valid, boolean notifyKiller, String message, String reason, int points, String context)
		{
			this.valid = valid;
			this.notifyKiller = notifyKiller;
			this.message = message;
			this.reason = reason;
			this.points = points;
			this.context = context;
		}
		
		private static Validation accept(String reason, int points, String context)
		{
			return new Validation(true, false, "", reason, points, context);
		}
		
		private static Validation reject(boolean notifyKiller, String message)
		{
			return new Validation(false, notifyKiller, message, "", 0, "");
		}
	}
	
	public static void main(String[] args)
	{
		if (INSTANCE == null)
		{
			new ClanDominionManager();
		}
	}
}
