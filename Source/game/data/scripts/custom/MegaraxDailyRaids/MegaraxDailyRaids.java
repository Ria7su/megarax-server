/*
 * Custom Megarax Daily Raids - L2JMobius Interlude
 */
package custom.MegaraxDailyRaids;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.logging.Logger;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.managers.GlobalVariablesManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.util.Broadcast;

import ai.AbstractNpcAI;
import handlers.communityboard.MercenaryBoard;

public class MegaraxDailyRaids extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxDailyRaids.class.getName());
	
	private static final int LILITH = 25283;
	private static final int ANAKIM = 25286;
	private static final int SAILREN = 29065;
	private static final int SANDRAMANAIA = 9910;
	private static final int SANDRAMANAIA_DAILY_MARK = -9910;
	
	private static final LocalTime DAILY_RAID_SPAWN_TIME = LocalTime.of(22, 0);
	private static final LocalTime SANDRAMANAIA_SPAWN_TIME = LocalTime.of(18, 0);
	private static final long ONE_DAY = 24L * 60 * 60 * 1000;
	private static final long SAILREN_RESPAWN = 12L * 60 * 60 * 1000;
	private static final String SANDRAMANAIA_LAST_SPAWN_DAY = "MegaraxSandramanaiaLastSpawnDay";
	private static final String SANDRAMANAIA_MESSAGE = "Sandramanaia ha aparecido en su nueva zona de raid. Gatekeeper 9901 puede llevarte al combate!";
	
	private static final Location LILITH_LOC = new Location(-47047, 49426, -5813);
	private static final Location ANAKIM_LOC = new Location(-114799, -180563, -6781);
	private static final Location SAILREN_LOC = new Location(47461, 152422, -2760);
	private static final Location SANDRAMANAIA_LOC = new Location(184509, 20300, -3200);
	
	private Npc _lilith;
	private Npc _anakim;
	private Npc _sailren;
	private Npc _sandramanaia;
	
	private MegaraxDailyRaids()
	{
		addKillId(LILITH, ANAKIM, SAILREN, SANDRAMANAIA);
		scheduleNextSpawn();
		scheduleNextSandramanaiaSpawn();
		ThreadPool.schedule(this::spawnSailren, 120000);
		LOGGER.info("MegaraxDailyRaids: Daily raid schedulers loaded.");
	}
	
	private void scheduleNextSpawn()
	{
		final LocalDateTime now = LocalDateTime.now();
		LocalDateTime next = now.withHour(DAILY_RAID_SPAWN_TIME.getHour()).withMinute(DAILY_RAID_SPAWN_TIME.getMinute()).withSecond(0).withNano(0);
		if (!next.isAfter(now))
		{
			next = next.plusDays(1);
		}
		
		final long delay = Math.max(1000, Duration.between(now, next).toMillis());
		ThreadPool.schedule(this::spawnDailyRaids, delay);
		LOGGER.info("MegaraxDailyRaids: Next Anakim/Lilith spawn scheduled for " + next + ".");
	}
	
	private void scheduleNextSandramanaiaSpawn()
	{
		final LocalDateTime now = LocalDateTime.now();
		if (!hasSpawnedSandramanaiaToday() && !now.toLocalTime().isBefore(SANDRAMANAIA_SPAWN_TIME))
		{
			ThreadPool.schedule(this::spawnSandramanaia, 10000);
			LOGGER.info("MegaraxDailyRaids: Sandramanaia daily spawn pending for today.");
			return;
		}
		
		LocalDateTime next = now.withHour(SANDRAMANAIA_SPAWN_TIME.getHour()).withMinute(SANDRAMANAIA_SPAWN_TIME.getMinute()).withSecond(0).withNano(0);
		if (!next.isAfter(now) || hasSpawnedSandramanaiaToday())
		{
			next = next.plusDays(1);
		}
		
		final long delay = Math.max(1000, Duration.between(now, next).toMillis());
		ThreadPool.schedule(this::spawnSandramanaia, delay);
		LOGGER.info("MegaraxDailyRaids: Next Sandramanaia spawn scheduled for " + next + ".");
	}
	
	private void spawnDailyRaids()
	{
		despawnExistingRaids();
		_lilith = addSpawn(LILITH, LILITH_LOC, false, 0);
		_anakim = addSpawn(ANAKIM, ANAKIM_LOC, false, 0);
		
		Broadcast.toAllOnlinePlayers("Megarax Raid: Lilith and Anakim have appeared. Gatekeeper 9901 can take you there.");
		Broadcast.toAllOnlinePlayersOnScreen("Daily Megarax Raids: Lilith and Anakim have spawned!");
		ThreadPool.schedule(this::spawnDailyRaids, ONE_DAY);
	}
	
	private void spawnSandramanaia()
	{
		if (hasSpawnedSandramanaiaToday())
		{
			scheduleNextSandramanaiaSpawn();
			return;
		}
		
		despawnExistingSandramanaia();
		_sandramanaia = addSpawn(SANDRAMANAIA, SANDRAMANAIA_LOC, false, 0);
		if (_sandramanaia != null)
		{
			_sandramanaia.setScriptValue(SANDRAMANAIA_DAILY_MARK);
			GlobalVariablesManager.getInstance().set(SANDRAMANAIA_LAST_SPAWN_DAY, LocalDate.now().toString());
			Broadcast.toAllOnlinePlayers("Megarax Raid: " + SANDRAMANAIA_MESSAGE);
			Broadcast.toAllOnlinePlayersOnScreen(SANDRAMANAIA_MESSAGE);
		}
		
		scheduleNextSandramanaiaSpawn();
	}
	
	private void spawnSailren()
	{
		if ((_sailren == null) || _sailren.isDecayed())
		{
			_sailren = addSpawn(SAILREN, SAILREN_LOC, false, 0);
			Broadcast.toAllOnlinePlayers("Megarax Raid: Sailren has appeared in the Farm x3 zone.");
			Broadcast.toAllOnlinePlayersOnScreen("Sailren has spawned in Farm x3!");
		}
	}
	
	private void despawnExistingRaids()
	{
		if ((_lilith != null) && !_lilith.isDecayed())
		{
			_lilith.deleteMe();
		}
		if ((_anakim != null) && !_anakim.isDecayed())
		{
			_anakim.deleteMe();
		}
		_lilith = null;
		_anakim = null;
	}
	
	private void despawnExistingSandramanaia()
	{
		if ((_sandramanaia != null) && !_sandramanaia.isDecayed())
		{
			_sandramanaia.deleteMe();
		}
		_sandramanaia = null;
	}
	
	private boolean hasSpawnedSandramanaiaToday()
	{
		final String lastSpawnDay = GlobalVariablesManager.getInstance().getString(SANDRAMANAIA_LAST_SPAWN_DAY, "");
		return LocalDate.now().toString().equals(lastSpawnDay);
	}
	
	@Override
	public void onKill(Npc npc, Player killer, boolean isSummon)
	{
		final boolean isDailySandramanaia = (npc.getId() == SANDRAMANAIA) && (npc.getScriptValue() == SANDRAMANAIA_DAILY_MARK);
		if ((npc.getId() != SANDRAMANAIA) || isDailySandramanaia)
		{
			MercenaryBoard.notifyRaidKill(killer);
		}
		
		if (npc.getId() == LILITH)
		{
			_lilith = null;
			Broadcast.toAllOnlinePlayers("Megarax Raid: Lilith has been defeated.");
		}
		else if (npc.getId() == ANAKIM)
		{
			_anakim = null;
			Broadcast.toAllOnlinePlayers("Megarax Raid: Anakim has been defeated.");
		}
		else if (npc.getId() == SAILREN)
		{
			_sailren = null;
			Broadcast.toAllOnlinePlayers("Megarax Raid: Sailren has been defeated. It will return in 12 hours.");
			ThreadPool.schedule(this::spawnSailren, SAILREN_RESPAWN);
		}
		else if (isDailySandramanaia)
		{
			_sandramanaia = null;
			Broadcast.toAllOnlinePlayers("Megarax Raid: Sandramanaia has been defeated.");
		}
	}
	
	public static void main(String[] args)
	{
		new MegaraxDailyRaids();
	}
}
