package custom.MegaraxNormalRaidDrops;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.Config;

public final class NormalRaidRewardsConfig
{
	private static final Logger LOGGER = Logger.getLogger(NormalRaidRewardsConfig.class.getName());
	private static final String CONFIG_FILE = "config/Custom/MegaraxNormalRaidDrops.ini";
	private static final int[] TIER_KEYS =
	{
		20, 30, 40, 50, 60, 70, 80, 85
	};
	private static final int[] MIN_LEVELS =
	{
		20, 30, 40, 50, 60, 70, 80, 85
	};
	private static final int[] MAX_LEVELS =
	{
		29, 39, 49, 59, 69, 79, 84, 89
	};
	private static final int[] DEFAULT_RAX =
	{
		200, 250, 325, 425, 550, 700, 900, 1100
	};
	private static final int[] DEFAULT_ADENA_MIN =
	{
		300000, 600000, 1200000, 2500000, 5000000, 9000000, 16000000, 25000000
	};
	private static final int[] DEFAULT_ADENA_MAX =
	{
		500000, 900000, 1800000, 3500000, 7000000, 13000000, 22000000, 35000000
	};
	private static final int[] DEFAULT_LIFE_STONE_CHANCE =
	{
		20, 20, 30, 30, 40, 50, 60, 70
	};

	public static boolean ENABLED = true;
	private static final int[] RAX = DEFAULT_RAX.clone();
	private static final int[] ADENA_MIN = DEFAULT_ADENA_MIN.clone();
	private static final int[] ADENA_MAX = DEFAULT_ADENA_MAX.clone();
	private static final int[] LIFE_STONE_CHANCE = DEFAULT_LIFE_STONE_CHANCE.clone();

	private NormalRaidRewardsConfig()
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
				LOGGER.log(Level.WARNING, "NormalRaidRewards: Could not load " + file.getAbsolutePath() + ".", e);
			}
		}
		else
		{
			LOGGER.warning("NormalRaidRewards: Config file not found. Safe x10 defaults will be used.");
		}

		ENABLED = getBoolean(properties, "EnableNormalRaidRewards", ENABLED);
		for (int i = 0; i < TIER_KEYS.length; i++)
		{
			final String prefix = "Tier" + TIER_KEYS[i];
			RAX[i] = Math.max(0, getInt(properties, prefix + "Rax", DEFAULT_RAX[i]));
			ADENA_MIN[i] = Math.max(0, getInt(properties, prefix + "AdenaMin", DEFAULT_ADENA_MIN[i]));
			ADENA_MAX[i] = Math.max(ADENA_MIN[i], getInt(properties, prefix + "AdenaMax", DEFAULT_ADENA_MAX[i]));
			LIFE_STONE_CHANCE[i] = clamp(getInt(properties, prefix + "LifeStoneChance", DEFAULT_LIFE_STONE_CHANCE[i]), 0, 100);
		}
	}

	public static Tier getTier(int level)
	{
		for (int i = 0; i < MIN_LEVELS.length; i++)
		{
			if ((level >= MIN_LEVELS[i]) && (level <= MAX_LEVELS[i]))
			{
				return new Tier(RAX[i], ADENA_MIN[i], ADENA_MAX[i], LIFE_STONE_CHANCE[i]);
			}
		}
		return null;
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

	public static final class Tier
	{
		public final int rax;
		public final int adenaMin;
		public final int adenaMax;
		public final int lifeStoneChance;

		private Tier(int rax, int adenaMin, int adenaMax, int lifeStoneChance)
		{
			this.rax = rax;
			this.adenaMin = adenaMin;
			this.adenaMax = adenaMax;
			this.lifeStoneChance = lifeStoneChance;
		}
	}
}
