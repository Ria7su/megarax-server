package custom.MegaraxCommunityProjects;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.Config;

public final class CommunityProjectsConfig
{
	private static final Logger LOGGER = Logger.getLogger(CommunityProjectsConfig.class.getName());
	private static final String CONFIG_FILE = "config/Custom/MegaraxCommunityProjects.ini";
	
	public static boolean ENABLED = true;
	public static int NPC_ID = 1005010;
	public static int RECENT_CONTRIBUTORS = 10;
	public static String FORGOTTEN_ROADS_KEY = "FORGOTTEN_ROADS_1";
	public static String FORGOTTEN_ROADS_TITLE = "Los Caminos Olvidados";
	public static int FORGOTTEN_ROADS_GOAL = 100;
	public static int FORGOTTEN_ROADS_OATH_POINTS = 10;
	public static int FORGOTTEN_ROADS_LEGACY_POINTS = 15;
	
	private CommunityProjectsConfig()
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
				LOGGER.log(Level.WARNING, "CommunityProjects: Could not load " + file.getAbsolutePath() + ".", e);
			}
		}
		else
		{
			LOGGER.warning("CommunityProjects: Config file not found. Defaults will be used.");
		}
		
		ENABLED = getBoolean(properties, "EnableCommunityProjects", ENABLED);
		NPC_ID = Math.max(1, getInt(properties, "CommunityProjectsNpcId", NPC_ID));
		RECENT_CONTRIBUTORS = clamp(getInt(properties, "CommunityProjectsRecentContributors", RECENT_CONTRIBUTORS), 1, 20);
		FORGOTTEN_ROADS_KEY = cleanKey(properties.getProperty("ForgottenRoadsProjectKey", FORGOTTEN_ROADS_KEY), FORGOTTEN_ROADS_KEY);
		FORGOTTEN_ROADS_TITLE = cleanText(properties.getProperty("ForgottenRoadsProjectTitle", FORGOTTEN_ROADS_TITLE), FORGOTTEN_ROADS_TITLE);
		FORGOTTEN_ROADS_GOAL = Math.max(1, getInt(properties, "ForgottenRoadsProjectGoal", FORGOTTEN_ROADS_GOAL));
		FORGOTTEN_ROADS_OATH_POINTS = Math.max(1, getInt(properties, "ForgottenRoadsContributionPerOath", FORGOTTEN_ROADS_OATH_POINTS));
		FORGOTTEN_ROADS_LEGACY_POINTS = Math.max(0, getInt(properties, "ForgottenRoadsLegacyPoints", FORGOTTEN_ROADS_LEGACY_POINTS));
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
	
	private static String cleanKey(String value, String fallback)
	{
		final String clean = value == null ? "" : value.trim().toUpperCase().replaceAll("[^A-Z0-9_\\-]", "_");
		return clean.isEmpty() ? fallback : clean.substring(0, Math.min(64, clean.length()));
	}
	
	private static String cleanText(String value, String fallback)
	{
		final String clean = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
		return clean.isEmpty() ? fallback : clean.substring(0, Math.min(96, clean.length()));
	}
}
