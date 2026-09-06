package custom.MegaraxStability;

import java.io.File;
import java.io.FileInputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.handler.AdminCommandHandler;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Npc;

import ai.AbstractNpcAI;

/** Lightweight, reload-safe stability telemetry for private testing. */
public class MegaraxStability extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxStability.class.getName());
	private static final File CONFIG = new File("config/Custom/MegaraxStability.ini");
	private static final long STARTED_AT = System.currentTimeMillis();
	private static MegaraxStability INSTANCE;

	private final Properties _config = new Properties();
	private final StabilityAdminCommand _adminHandler;
	private final Logger _stabilityLog = Logger.getLogger("MegaraxStabilityLog");
	private ScheduledFuture<?> _task;
	private volatile Snapshot _lastSnapshot;
	private boolean _enabled;
	private boolean _databaseMetrics;
	private boolean _npcMetrics;
	private boolean _privateTestMode;
	private int _intervalMinutes;
	private int _warnHeapPercent;
	private int _warnDatabaseConnections;
	private String _lastBackup = "unknown";

	private MegaraxStability()
	{
		if (INSTANCE != null)
		{
			INSTANCE.shutdownForReload();
		}
		INSTANCE = this;
		loadConfig();
		configureFileLogger();
		_adminHandler = new StabilityAdminCommand(this);
		AdminCommandHandler.getInstance().registerHandler(_adminHandler);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> logShutdownHook(), "MegaraxStabilityShutdownHook"));
		if (_enabled)
		{
			_task = ThreadPool.scheduleAtFixedRate(this::safeCollect, 30000L, _intervalMinutes * 60000L);
			safeCollect();
		}
		LOGGER.info("MegaraxStability: enabled=" + _enabled + ", interval=" + _intervalMinutes + "m, privateTest=" + _privateTestMode + ".");
	}

	private void loadConfig()
	{
		try (FileInputStream input = new FileInputStream(CONFIG))
		{
			_config.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
		}
		catch (Exception e)
		{
			throw new IllegalStateException("Cannot load " + CONFIG, e);
		}
		_enabled = Boolean.parseBoolean(_config.getProperty("StabilityMonitorEnabled", "true"));
		_intervalMinutes = Math.max(1, Integer.parseInt(_config.getProperty("StabilityMonitorIntervalMinutes", "5")));
		_databaseMetrics = Boolean.parseBoolean(_config.getProperty("StabilityMonitorDatabaseMetrics", "true"));
		_npcMetrics = Boolean.parseBoolean(_config.getProperty("StabilityMonitorNpcMetrics", "true"));
		_privateTestMode = Boolean.parseBoolean(_config.getProperty("PrivateTestMode", "true"));
		_warnHeapPercent = Math.max(1, Math.min(100, Integer.parseInt(_config.getProperty("StabilityMonitorWarnHeapPercent", "85"))));
		_warnDatabaseConnections = Math.max(1, Integer.parseInt(_config.getProperty("StabilityMonitorWarnDatabaseConnections", "80")));
	}

	private void configureFileLogger()
	{
		try
		{
			final String fileName = _config.getProperty("StabilityMonitorLogFile", "log/megarax-stability.log");
			final Path path = new File(fileName).toPath();
			if (path.getParent() != null)
			{
				Files.createDirectories(path.getParent());
			}
			_stabilityLog.setUseParentHandlers(false);
			for (java.util.logging.Handler handler : _stabilityLog.getHandlers())
			{
				handler.close();
				_stabilityLog.removeHandler(handler);
			}
			final FileHandler handler = new FileHandler(fileName, true);
			handler.setEncoding(StandardCharsets.UTF_8.name());
			handler.setFormatter(new Formatter()
			{
				@Override
				public String format(LogRecord record)
				{
					return Instant.ofEpochMilli(record.getMillis()) + " [" + record.getLevel() + "] " + record.getMessage() + System.lineSeparator();
				}
			});
			_stabilityLog.addHandler(handler);
		}
		catch (Exception e)
		{
			throw new IllegalStateException("Cannot initialize stability log.", e);
		}
	}

	private void safeCollect()
	{
		try
		{
			_lastSnapshot = collect();
			_stabilityLog.info(_lastSnapshot.toLogLine());
			if (_lastSnapshot.heapPercent >= _warnHeapPercent)
			{
				_stabilityLog.warning("HEAP_THRESHOLD percent=" + _lastSnapshot.heapPercent + " threshold=" + _warnHeapPercent);
			}
			if ((_lastSnapshot.databaseConnections >= 0) && (_lastSnapshot.databaseConnections >= _warnDatabaseConnections))
			{
				_stabilityLog.warning("DB_CONNECTION_THRESHOLD connections=" + _lastSnapshot.databaseConnections + " threshold=" + _warnDatabaseConnections);
			}
		}
		catch (Exception e)
		{
			_stabilityLog.log(Level.SEVERE, "COLLECTION_FAILED " + e.getMessage(), e);
			LOGGER.log(Level.WARNING, "MegaraxStability collection failed.", e);
		}
	}

	private Snapshot collect()
	{
		final Runtime runtime = Runtime.getRuntime();
		final long used = runtime.totalMemory() - runtime.freeMemory();
		final long max = runtime.maxMemory();
		final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
		int npcs = -1;
		if (_npcMetrics)
		{
			npcs = 0;
			for (WorldObject object : World.getInstance().getVisibleObjects())
			{
				if (object instanceof Npc)
				{
					npcs++;
				}
			}
		}
		final int databaseConnections = _databaseMetrics ? getDatabaseConnections() : -1;
		_lastBackup = findLastBackupSuccess();
		return new Snapshot(System.currentTimeMillis() - STARTED_AT, used, max, threads.getThreadCount(), threads.getPeakThreadCount(), World.getInstance().getPlayers().size(), World.getInstance().getVisibleObjectsCount(), npcs, databaseConnections, _lastBackup, _privateTestMode);
	}

	private int getDatabaseConnections()
	{
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM information_schema.processlist"); ResultSet rs = ps.executeQuery())
		{
			return rs.next() ? rs.getInt(1) : -1;
		}
		catch (Exception e)
		{
			_stabilityLog.warning("DB_METRIC_FAILED " + e.getMessage());
			return -1;
		}
	}

	private String findLastBackupSuccess()
	{
		try
		{
			final Path log = new File("../backups/logs/megarax-backup.log").toPath();
			if (!Files.isRegularFile(log))
			{
				return "not-found";
			}
			String result = "none";
			for (String line : Files.readAllLines(log, StandardCharsets.UTF_8))
			{
				if (line.contains("[SUCCESS] Backup operation completed"))
				{
					result = line.length() >= 19 ? line.substring(0, 19) : line;
				}
			}
			return result;
		}
		catch (Exception e)
		{
			return "unreadable";
		}
	}

	private void logShutdownHook()
	{
		try
		{
			_stabilityLog.warning("SHUTDOWN_HOOK uptimeMs=" + (System.currentTimeMillis() - STARTED_AT) + " reason=JVM_SHUTDOWN_HOOK");
		}
		catch (Exception ignored)
		{
		}
	}

	private void shutdownForReload()
	{
		if (_task != null)
		{
			_task.cancel(false);
			_task = null;
		}
		if (_adminHandler != null)
		{
			AdminCommandHandler.getInstance().removeHandler(_adminHandler);
		}
		_stabilityLog.warning("MONITOR_RELOAD old periodic task cancelled");
	}

	public String healthSummary()
	{
		final Snapshot snapshot = _lastSnapshot != null ? _lastSnapshot : collect();
		return snapshot.toSummary();
	}

	public void collectNow()
	{
		safeCollect();
	}

	public static void main(String[] args)
	{
		new MegaraxStability();
	}

	private static class Snapshot
	{
		final long uptimeMs, usedMemory, maxMemory;
		final int threads, peakThreads, players, worldObjects, npcs, databaseConnections, heapPercent;
		final String lastBackup;
		final boolean privateTest;

		Snapshot(long up, long used, long max, int threadCount, int peak, int online, int objects, int npcCount, int dbConnections, String backup, boolean test)
		{
			uptimeMs = up; usedMemory = used; maxMemory = max; threads = threadCount; peakThreads = peak; players = online; worldObjects = objects; npcs = npcCount; databaseConnections = dbConnections; lastBackup = backup; privateTest = test;
			heapPercent = max > 0 ? (int) ((used * 100L) / max) : 0;
		}

		String toLogLine()
		{
			return "HEALTH uptimeSec=" + (uptimeMs / 1000L) + " heapUsedMB=" + (usedMemory / 1048576L) + " heapMaxMB=" + (maxMemory / 1048576L) + " heapPercent=" + heapPercent + " players=" + players + " threads=" + threads + " peakThreads=" + peakThreads + " worldObjects=" + worldObjects + " npcs=" + npcs + " dbConnections=" + databaseConnections + " lastBackup=\"" + lastBackup + "\" privateTest=" + privateTest;
		}

		String toSummary()
		{
			return "Health: uptime=" + formatDuration(uptimeMs) + ", heap=" + (usedMemory / 1048576L) + "/" + (maxMemory / 1048576L) + "MB (" + heapPercent + "%), players=" + players + ", threads=" + threads + " (peak " + peakThreads + "), objects=" + worldObjects + ", NPCs=" + npcs + ", DB connections=" + databaseConnections + ", last backup=" + lastBackup + ".";
		}

		private static String formatDuration(long millis)
		{
			final long seconds = millis / 1000L;
			return (seconds / 86400L) + "d " + ((seconds % 86400L) / 3600L) + "h " + ((seconds % 3600L) / 60L) + "m";
		}
	}
}
