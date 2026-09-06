package custom.MegaraxPvPFarmZones;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.handler.AdminCommandHandler;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.Spawn;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Attackable;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.zone.ZoneType;
import org.l2jmobius.gameserver.network.serverpackets.ExShowScreenMessage;
import org.l2jmobius.gameserver.util.Broadcast;

import ai.AbstractNpcAI;

/** Persistent, configurable Megarax PvP farm rotation. */
public class MegaraxPvPFarmZones extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxPvPFarmZones.class.getName());
	private static final File CONFIG = new File("config/Custom/MegaraxPvPFarmZones.ini");
	private static final File STATE = new File("data/MegaraxPvPFarmZones.state");
	private static final int[][] OFFSETS =
	{
		{ -1200, -900 }, { -600, -900 }, { 0, -900 }, { 600, -900 }, { 1200, -900 },
		{ -1200, 0 }, { -600, 0 }, { 0, 0 }, { 600, 0 }, { 1200, 0 },
		{ -1200, 900 }, { -600, 900 }, { 0, 900 }, { 600, 900 }, { 1200, 900 }
		,{ -1800, -1350 }, { -900, -1350 }, { 0, -1350 }, { 900, -1350 }, { 1800, -1350 }
	};
	private static MegaraxPvPFarmZones INSTANCE;

	private final Object _lock = new Object();
	private final Map<String, ZoneDef> _definitions = new LinkedHashMap<>();
	private final List<ZoneDef> _rotation = new ArrayList<>();
	private final Set<Integer> _excludedIds = new HashSet<>();
	private final Set<Spawn> _suspendedSpawns = new HashSet<>();
	private final List<Spawn> _borakSpawns = new ArrayList<>();
	private ScheduledFuture<?> _endTask;
	private ScheduledFuture<?> _warningTask;
	private ScheduledFuture<?> _sweeperTask;
	private ZoneDef _active;
	private int _activeIndex = -1;
	private long _startedAt;
	private long _endsAt;
	private boolean _warningSent;
	private boolean _enabled;
	private boolean _removeNormal;
	private boolean _forceRespawn;
	private boolean _showScreen;
	private boolean _announce;
	private int _durationMinutes;
	private int _warningMinutes;
	private int _borakId;
	private int _borakRespawn;
	private int _borakCount;
	private String _warningText;
	private String _activationText;
	private String _endText;

	private MegaraxPvPFarmZones()
	{
		INSTANCE = this;
		loadConfig();
		AdminCommandHandler.getInstance().registerHandler(new PvpZoneAdminCommand(this));
		ThreadPool.schedule(this::startRotation, 5000L);
	}

	public static MegaraxPvPFarmZones getInstance()
	{
		return INSTANCE;
	}

	private void loadConfig()
	{
		final Properties p = new Properties();
		try (FileInputStream in = new FileInputStream(CONFIG))
		{
			p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
		}
		catch (Exception e)
		{
			throw new IllegalStateException("Cannot load " + CONFIG, e);
		}
		_enabled = Boolean.parseBoolean(p.getProperty("PvPZoneRotationEnabled", "true"));
		_durationMinutes = Math.max(1, Integer.parseInt(p.getProperty("PvPZoneDurationMinutes", "180")));
		_warningMinutes = Math.max(0, Integer.parseInt(p.getProperty("PvPZoneWarningMinutes", "5")));
		_borakId = Integer.parseInt(p.getProperty("PvPZoneBorakNpcId", "9918"));
		_borakRespawn = Math.max(10, Integer.parseInt(p.getProperty("PvPZoneBorakRespawnSeconds", "60")));
		_borakCount = Math.max(0, Math.min(OFFSETS.length, Integer.parseInt(p.getProperty("PvPZoneBorakCount", "20"))));
		_removeNormal = Boolean.parseBoolean(p.getProperty("PvPZoneRemoveNormalMonsters", "true"));
		_forceRespawn = Boolean.parseBoolean(p.getProperty("PvPZoneForceNormalMobRespawn", "true"));
		_showScreen = Boolean.parseBoolean(p.getProperty("PvPZoneShowScreenMessages", "true"));
		_announce = Boolean.parseBoolean(p.getProperty("PvPZoneAnnounceChanges", "true"));
		_warningText = p.getProperty("PvPZoneFiveMinuteMessage", "¡ATENCIÓN! La zona PvP %currentZone% finalizará en %remainingMinutes% minutos. La próxima zona PvP será %nextZone%.");
		_activationText = p.getProperty("PvPZoneActivationMessage", "¡La zona PvP %zoneName% está activa! Los Borak han invadido el área durante las próximas tres horas.");
		_endText = p.getProperty("PvPZoneEndMessage", "La zona PvP %zoneName% ha finalizado. Los monstruos originales han regresado.");
		_definitions.clear();
		_rotation.clear();
		for (String key : p.getProperty("PvPZoneRotation", "").split(","))
		{
			key = key.trim();
			if (key.isEmpty()) continue;
			final String raw = p.getProperty("Zone." + key);
			if (raw == null) { LOGGER.severe("[PvPZoneRotation] Missing Zone." + key); continue; }
			try
			{
				final String[] v = raw.split("\\|");
				final ZoneDef d = new ZoneDef(key, v[0], v[1], Integer.parseInt(v[2]), Integer.parseInt(v[3]), Integer.parseInt(v[4]), Boolean.parseBoolean(v[5]));
				_definitions.put(key, d);
				if (d.enabled) _rotation.add(d);
			}
			catch (Exception e) { LOGGER.log(Level.SEVERE, "[PvPZoneRotation] Invalid Zone." + key, e); }
		}
		_excludedIds.clear();
		for (String id : p.getProperty("PvPZoneExcludedNpcIds", "").split(","))
		{
			try { if (!id.trim().isEmpty()) _excludedIds.add(Integer.parseInt(id.trim())); } catch (NumberFormatException ignored) { }
		}
		LOGGER.info("[PvPZoneRotation] Config loaded: zones=" + _rotation.size() + ", duration=" + _durationMinutes + "m, Borak=" + _borakId + ".");
	}

	private void startRotation()
	{
		synchronized (_lock)
		{
			if (!_enabled || _rotation.isEmpty()) { LOGGER.warning("[PvPZoneRotation] Rotation disabled or empty."); return; }
			if (!restoreState()) activateZone(0, false);
		}
	}

	private boolean restoreState()
	{
		if (!STATE.isFile()) return false;
		final Properties p = new Properties();
		try (FileInputStream in = new FileInputStream(STATE))
		{
			p.load(in);
			final String key = p.getProperty("activeZone", "");
			final ZoneDef zone = _definitions.get(key);
			if ((zone == null) || !zone.enabled) return false;
			_activeIndex = _rotation.indexOf(zone);
			_startedAt = Long.parseLong(p.getProperty("startedAt"));
			_endsAt = Long.parseLong(p.getProperty("endsAt"));
			_warningSent = Boolean.parseBoolean(p.getProperty("warningSent", "false"));
			if (_endsAt <= System.currentTimeMillis())
			{
				activateZone((_activeIndex + 1) % _rotation.size(), false);
			}
			else
			{
				activateZoneInternal(zone, true);
				LOGGER.info("[PvPZoneRotation] Restored " + zone.displayName + "; remaining=" + ((_endsAt - System.currentTimeMillis()) / 60000) + "m.");
			}
			return true;
		}
		catch (Exception e) { LOGGER.log(Level.WARNING, "[PvPZoneRotation] State restore failed.", e); return false; }
	}

	private void activateZone(int index, boolean admin)
	{
		if (_rotation.isEmpty()) return;
		index = Math.floorMod(index, _rotation.size());
		deactivateZone(_active != null);
		_activeIndex = index;
		_startedAt = System.currentTimeMillis();
		_endsAt = _startedAt + (_durationMinutes * 60000L);
		_warningSent = false;
		activateZoneInternal(_rotation.get(index), false);
	}

	private void activateZoneInternal(ZoneDef definition, boolean restoring)
	{
		final ZoneType zone = ZoneManager.getInstance().getZoneByName(definition.zoneName);
		if (zone == null) { LOGGER.severe("[PvPZoneRotation] Zone not found: " + definition.zoneName); moveToNextZone(); return; }
		// Disable every configured zone, including zones that were removed from rotation.
		// This prevents a previously active legacy zone from remaining enabled.
		for (ZoneDef d : _definitions.values())
		{
			final ZoneType z = ZoneManager.getInstance().getZoneByName(d.zoneName);
			if (z != null) z.setEnabled(d == definition);
		}
		_active = definition;
		int despawned = suspendNormalSpawns(zone);
		spawnBoraks(definition);
		scheduleTasks();
		persistState();
		if (!restoring) announce(_activationText.replace("%zoneName%", definition.displayName), false);
		LOGGER.info("[PvPZoneRotation] " + definition.displayName + " activated. Suspended spawns=" + _suspendedSpawns.size() + ", despawned=" + despawned + ", Boraks=" + _borakSpawns.size() + ".");
	}

	private int suspendNormalSpawns(ZoneType zone)
	{
		if (!_removeNormal) return 0;
		int count = 0;
		for (WorldObject object : new ArrayList<>(World.getInstance().getVisibleObjects()))
		{
			if (!(object instanceof Attackable)) continue;
			final Npc npc = (Npc) object;
			final Spawn spawn = npc.getSpawn();
			if ((npc.getId() == _borakId) || _excludedIds.contains(npc.getId()) || npc.isRaid() || npc.isRaidMinion() || (spawn == null) || !zone.isInsideZone(spawn.getX(), spawn.getY(), spawn.getZ())) continue;
			if (_suspendedSpawns.add(spawn)) spawn.stopRespawn();
			npc.deleteMe();
			count++;
		}
		return count;
	}

	private void spawnBoraks(ZoneDef zone)
	{
		despawnBoraks();
		for (int i = 0; i < _borakCount; i++)
		{
			try
			{
				final Spawn spawn = new Spawn(_borakId);
				spawn.setXYZ(zone.x + OFFSETS[i][0], zone.y + OFFSETS[i][1], zone.z);
				spawn.setRespawnDelay(_borakRespawn);
				spawn.startRespawn();
				spawn.doSpawn(true);
				_borakSpawns.add(spawn);
			}
			catch (Exception e) { LOGGER.log(Level.SEVERE, "[PvPZoneRotation] Cannot spawn Borak.", e); }
		}
	}

	private void despawnBoraks()
	{
		for (Spawn spawn : new ArrayList<>(_borakSpawns))
		{
			spawn.stopRespawn();
			for (Npc npc : new ArrayList<>(spawn.getSpawnedNpcs())) npc.deleteMe();
		}
		_borakSpawns.clear();
	}

	private void restoreNormalSpawns()
	{
		for (Spawn spawn : new HashSet<>(_suspendedSpawns))
		{
			spawn.startRespawn();
			if (_forceRespawn && spawn.getSpawnedNpcs().isEmpty()) spawn.doSpawn(true);
		}
		LOGGER.info("[PvPZoneRotation] Restored normal spawns=" + _suspendedSpawns.size() + ".");
		_suspendedSpawns.clear();
	}

	private void scheduleTasks()
	{
		cancelTasks();
		final long remaining = Math.max(1L, _endsAt - System.currentTimeMillis());
		_endTask = ThreadPool.schedule(this::moveToNextZone, remaining);
		final long warningDelay = remaining - (_warningMinutes * 60000L);
		if (!_warningSent && (_warningMinutes > 0))
		{
			if (warningDelay > 0) _warningTask = ThreadPool.schedule(this::sendWarning, warningDelay);
			else sendWarning();
		}
		_sweeperTask = ThreadPool.scheduleAtFixedRate(this::sweepActiveZone, 10000L, 10000L);
	}

	private void sweepActiveZone()
	{
		synchronized (_lock)
		{
			if (_active == null) return;
			final ZoneType zone = ZoneManager.getInstance().getZoneByName(_active.zoneName);
			if (zone != null) suspendNormalSpawns(zone);
		}
	}

	private void sendWarning()
	{
		synchronized (_lock)
		{
			if ((_active == null) || _warningSent) return;
			_warningSent = true;
			final ZoneDef next = _rotation.get((_activeIndex + 1) % _rotation.size());
			final String msg = _warningText.replace("%currentZone%", _active.displayName).replace("%nextZone%", next.displayName).replace("%remainingMinutes%", String.valueOf(_warningMinutes));
			announce(msg, true);
			persistState();
			LOGGER.info("[PvPZoneRotation] Five-minute warning sent.");
		}
	}

	public void moveToNextZone()
	{
		synchronized (_lock) { activateZone(_activeIndex + 1, true); }
	}

	private void deactivateZone(boolean announceEnd)
	{
		cancelTasks();
		if (_active == null) return;
		final String name = _active.displayName;
		despawnBoraks();
		restoreNormalSpawns();
		_active = null;
		if (announceEnd) announce(_endText.replace("%zoneName%", name), false);
	}

	private void cancelTasks()
	{
		if (_endTask != null) _endTask.cancel(false);
		if (_warningTask != null) _warningTask.cancel(false);
		if (_sweeperTask != null) _sweeperTask.cancel(false);
		_endTask = _warningTask = _sweeperTask = null;
	}

	private void announce(String message, boolean screen)
	{
		if (_announce) Broadcast.toAllOnlinePlayers(message);
		if (_showScreen && screen) Broadcast.toAllOnlinePlayers(new ExShowScreenMessage(message, ExShowScreenMessage.TOP_CENTER, 10000, 0, true, true));
	}

	private void persistState()
	{
		if (_active == null) return;
		final Properties p = new Properties();
		p.setProperty("activeZone", _active.key);
		p.setProperty("startedAt", String.valueOf(_startedAt));
		p.setProperty("endsAt", String.valueOf(_endsAt));
		p.setProperty("nextIndex", String.valueOf((_activeIndex + 1) % _rotation.size()));
		p.setProperty("warningSent", String.valueOf(_warningSent));
		try
		{
			Files.createDirectories(STATE.toPath().getParent());
			try (FileOutputStream out = new FileOutputStream(STATE)) { p.store(out, "Megarax PvP Farm rotation state"); }
		}
		catch (Exception e) { LOGGER.log(Level.WARNING, "[PvPZoneRotation] Cannot persist state.", e); }
	}

	public String status()
	{
		synchronized (_lock)
		{
			if (_active == null) return "PvP rotation is stopped.";
			final String next = _rotation.get((_activeIndex + 1) % _rotation.size()).displayName;
			return "Current=" + _active.displayName + ", next=" + next + ", remaining=" + Math.max(0, (_endsAt - System.currentTimeMillis()) / 60000) + "m, Boraks=" + _borakSpawns.size() + ", suspended=" + _suspendedSpawns.size() + ", warning=" + _warningSent;
		}
	}

	public void adminStart(String key)
	{
		synchronized (_lock)
		{
			final ZoneDef zone = _definitions.get(key.toUpperCase());
			if ((zone == null) || !_rotation.contains(zone)) throw new IllegalArgumentException("Unknown/disabled zone: " + key);
			activateZone(_rotation.indexOf(zone), true);
		}
	}

	public void adminStop()
	{
		synchronized (_lock) { deactivateZone(true); }
	}

	public void adminWarning() { sendWarning(); }

	public void adminReload()
	{
		synchronized (_lock)
		{
			final String current = _active != null ? _active.key : null;
			deactivateZone(false);
			loadConfig();
			if (current != null && _definitions.containsKey(current)) activateZone(_rotation.indexOf(_definitions.get(current)), true); else startRotation();
		}
	}

	public static void main(String[] args) { new MegaraxPvPFarmZones(); }

	private static class ZoneDef
	{
		final String key, zoneName, displayName;
		final int x, y, z;
		final boolean enabled;
		ZoneDef(String k, String zn, String dn, int px, int py, int pz, boolean en) { key = k; zoneName = zn; displayName = dn; x = px; y = py; z = pz; enabled = en; }
	}
}
