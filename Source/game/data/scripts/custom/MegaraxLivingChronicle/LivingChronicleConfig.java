package custom.MegaraxLivingChronicle;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.Config;

public final class LivingChronicleConfig
{
	private static final Logger LOGGER = Logger.getLogger(LivingChronicleConfig.class.getName());
	private static final String CONFIG_FILE = "config/Custom/MegaraxLivingChronicle.ini";
	
	public static boolean ENABLED = true;
	public static int NPC_ID = 1005000;
	public static int RECENT_EVENT_LIMIT = 10;
	public static int SERVER_FIRST_LIMIT = 10;
	public static int RAID_PARTICIPANT_RANGE = 2000;
	public static boolean RECORD_ARRIVAL = true;
	public static boolean RECORD_LEVEL_MILESTONES = true;
	public static boolean RECORD_PROFESSION_CHANGES = true;
	public static boolean RECORD_RAID_VICTORIES = true;
	public static boolean ANNOUNCE_SERVER_FIRSTS = true;
	public static final List<Integer> LEVEL_MILESTONES = new ArrayList<>();
	
	private LivingChronicleConfig()
	{
	}
	
	public static void load()
	{
		final Properties properties = new Properties();
		final File file = resolveConfigFile();
		if ((file != null) && file.isFile())
		{
			try (FileInputStream input = new FileInputStream(file))
			{
				properties.load(input);
			}
			catch (Exception e)
			{
				LOGGER.log(Level.WARNING, "LivingChronicle: Could not load " + file.getAbsolutePath() + ".", e);
			}
		}
		else
		{
			LOGGER.warning("LivingChronicle: Config file not found. Defaults will be used.");
		}
		
		ENABLED = getBoolean(properties, "EnableLivingChronicle", ENABLED);
		NPC_ID = Math.max(1, getInt(properties, "ChronicleNpcId", NPC_ID));
		RECENT_EVENT_LIMIT = clamp(getInt(properties, "ChronicleRecentEventLimit", RECENT_EVENT_LIMIT), 1, 20);
		SERVER_FIRST_LIMIT = clamp(getInt(properties, "ChronicleServerFirstLimit", SERVER_FIRST_LIMIT), 1, 20);
		RAID_PARTICIPANT_RANGE = clamp(getInt(properties, "ChronicleRaidParticipantRange", RAID_PARTICIPANT_RANGE), 500, 5000);
		RECORD_ARRIVAL = getBoolean(properties, "ChronicleRecordArrival", RECORD_ARRIVAL);
		RECORD_LEVEL_MILESTONES = getBoolean(properties, "ChronicleRecordLevelMilestones", RECORD_LEVEL_MILESTONES);
		RECORD_PROFESSION_CHANGES = getBoolean(properties, "ChronicleRecordProfessionChanges", RECORD_PROFESSION_CHANGES);
		RECORD_RAID_VICTORIES = getBoolean(properties, "ChronicleRecordRaidVictories", RECORD_RAID_VICTORIES);
		ANNOUNCE_SERVER_FIRSTS = getBoolean(properties, "ChronicleAnnounceServerFirsts", ANNOUNCE_SERVER_FIRSTS);
		
		LEVEL_MILESTONES.clear();
		final String milestones = properties.getProperty("ChronicleLevelMilestones", "20,40,52,61,76,80");
		for (String value : milestones.split(","))
		{
			try
			{
				final int level = Integer.parseInt(value.trim());
				if ((level > 1) && (level <= 100) && !LEVEL_MILESTONES.contains(level))
				{
					LEVEL_MILESTONES.add(level);
				}
			}
			catch (NumberFormatException ignored)
			{
			}
		}
		if (LEVEL_MILESTONES.isEmpty())
		{
			Collections.addAll(LEVEL_MILESTONES, 20, 40, 52, 61, 76, 80);
		}
		Collections.sort(LEVEL_MILESTONES);
	}
	
	private static File resolveConfigFile()
	{
		final List<File> candidates = new ArrayList<>();
		candidates.add(new File(CONFIG_FILE));
		candidates.add(new File("game/" + CONFIG_FILE));
		if (Config.DATAPACK_ROOT != null)
		{
			candidates.add(new File(Config.DATAPACK_ROOT, CONFIG_FILE));
		}
		for (File candidate : candidates)
		{
			if (candidate.isFile())
			{
				return candidate;
			}
		}
		return candidates.get(0);
	}
	
	private static boolean getBoolean(Properties properties, String key, boolean fallback)
	{
		return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(fallback)).trim());
	}
	
	private static int getInt(Properties properties, String key, int fallback)
	{
		try
		{
			return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)).trim());
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}
	
	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
