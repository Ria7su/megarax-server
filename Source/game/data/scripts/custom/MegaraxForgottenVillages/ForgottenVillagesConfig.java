package custom.MegaraxForgottenVillages;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.Config;

public final class ForgottenVillagesConfig
{
	private static final Logger LOGGER = Logger.getLogger(ForgottenVillagesConfig.class.getName());
	private static final String CONFIG_FILE = "config/Custom/MegaraxForgottenVillages.ini";
	
	public static boolean ENABLED = true;
	public static int MIN_LEVEL = 1;
	public static int MAX_LEVEL = 25;
	public static int HUNTER_GOAL = 12;
	public static int GUARDIAN_GOAL = 8;
	public static int GUARDIAN_RANGE = 1200;
	public static int REWARD_ADENA = 25000;
	public static long REWARD_EXP = 10000;
	public static long REWARD_SP = 1000;
	public static int REWARD_POTIONS = 20;
	public static int REWARD_SCROLLS = 3;
	public static int LEGACY_POINTS = 10;
	
	private ForgottenVillagesConfig()
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
				LOGGER.log(Level.WARNING, "ForgottenVillages: Could not load " + file.getAbsolutePath() + ".", e);
			}
		}
		else
		{
			LOGGER.warning("ForgottenVillages: Config file not found. Defaults will be used.");
		}
		
		ENABLED = getBoolean(properties, "EnableForgottenVillages", ENABLED);
		MIN_LEVEL = clamp(getInt(properties, "ForgottenVillagesMinLevel", MIN_LEVEL), 1, 100);
		MAX_LEVEL = clamp(getInt(properties, "ForgottenVillagesMaxLevel", MAX_LEVEL), MIN_LEVEL, 100);
		HUNTER_GOAL = clamp(getInt(properties, "ForgottenVillagesHunterGoal", HUNTER_GOAL), 1, 1000);
		GUARDIAN_GOAL = clamp(getInt(properties, "ForgottenVillagesGuardianGoal", GUARDIAN_GOAL), 1, 1000);
		GUARDIAN_RANGE = clamp(getInt(properties, "ForgottenVillagesGuardianRange", GUARDIAN_RANGE), 300, 3000);
		REWARD_ADENA = Math.max(0, getInt(properties, "ForgottenVillagesRewardAdena", REWARD_ADENA));
		REWARD_EXP = Math.max(0, getLong(properties, "ForgottenVillagesRewardExp", REWARD_EXP));
		REWARD_SP = Math.max(0, getLong(properties, "ForgottenVillagesRewardSp", REWARD_SP));
		REWARD_POTIONS = Math.max(0, getInt(properties, "ForgottenVillagesRewardPotionCount", REWARD_POTIONS));
		REWARD_SCROLLS = Math.max(0, getInt(properties, "ForgottenVillagesRewardScrollOfEscapeCount", REWARD_SCROLLS));
		LEGACY_POINTS = Math.max(0, getInt(properties, "ForgottenVillagesLegacyPoints", LEGACY_POINTS));
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
	
	private static long getLong(Properties properties, String key, long fallback)
	{
		try
		{
			return Long.parseLong(properties.getProperty(key, Long.toString(fallback)).trim());
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
