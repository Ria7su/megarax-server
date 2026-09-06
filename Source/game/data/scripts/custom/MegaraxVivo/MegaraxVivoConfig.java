package custom.MegaraxVivo;

import java.io.File;
import java.io.FileInputStream;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.Config;

/** Configuration for the additive MegaRax VIVO world layer. */
public final class MegaraxVivoConfig
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxVivoConfig.class.getName());
	private static final String CONFIG_FILE = "config/Custom/MegaraxVivo.ini";

	public static boolean ENABLED = true;
	public static boolean BOARD_ENABLED = true;
	public static boolean AUTO_CREATE_TABLES = true;
	public static ZoneId TIME_ZONE = ZoneId.of("America/New_York");
	public static int RECENT_RECEIPT_LIMIT = 12;
	public static int RECENT_CONTRIBUTOR_LIMIT = 8;
	public static int CHANGE_LOG_LIMIT = 10;
	public static boolean SHOW_CONTRIBUTION_MESSAGES = true;
	public static int CONTRIBUTION_MESSAGE_THRESHOLD = 3;
	public static int MAXIMUM_SINGLE_CONTRIBUTION = 100;
	public static int DEFAULT_PERSONAL_DAILY_CAP = 40;
	public static int DEFAULT_SECURITY = 50;
	public static int DEFAULT_SUPPLY = 50;
	public static int DEFAULT_SPIRIT = 50;

	public static String PILOT_CYCLE_KEY = "VIVO_GODDARD_EMBER_CARAVAN_1";
	public static String PILOT_CYCLE_TITLE = "La Caravana de las Brasas";
	public static String PILOT_NEED_KEY = "GODDARD_EMBER_CARAVAN_PREP";
	public static String PILOT_NEED_TITLE = "Goddard prepara la Caravana de las Brasas";
	public static String PILOT_NEED_DESCRIPTION = "Reunir suministros, trabajo artesanal, piezas recuperadas, servicios y escoltas.";
	public static String PILOT_REGION = "GODDARD";
	public static String PILOT_STATUS = "PREPARATION";
	public static long PILOT_GOAL = 1000;
	public static int PILOT_PERSONAL_DAILY_CAP = 40;
	public static int PILOT_SUCCESS_SUPPLY_DELTA = 10;
	public static String PILOT_SUCCESS_EFFECT = "Abre una ruta y un mercader temporal durante 24 horas.";
	public static String PILOT_FAILURE_EFFECT = "La caravana pequena parte con una emboscada adicional; los aportes quedan registrados.";
	public static Map<String, Long> PILOT_CATEGORIES = defaultPilotCategories();
	public static boolean GODDARD_ADAPTER_ENABLED = false;

	private MegaraxVivoConfig()
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
				LOGGER.log(Level.WARNING, "MegaraxVivo: Could not load " + file.getAbsolutePath() + ".", e);
			}
		}
		else
		{
			LOGGER.warning("MegaraxVivo: Config file not found. Safe defaults will be used.");
		}

		ENABLED = getBoolean(properties, "EnableMegaraxVivo", ENABLED);
		BOARD_ENABLED = getBoolean(properties, "EnableMegaraxVivoBoard", BOARD_ENABLED);
		AUTO_CREATE_TABLES = getBoolean(properties, "MegaraxVivoAutoCreateTables", AUTO_CREATE_TABLES);
		TIME_ZONE = getZone(properties.getProperty("MegaraxVivoTimeZone"), TIME_ZONE);
		RECENT_RECEIPT_LIMIT = clamp(getInt(properties, "MegaraxVivoRecentReceiptLimit", RECENT_RECEIPT_LIMIT), 1, 50);
		RECENT_CONTRIBUTOR_LIMIT = clamp(getInt(properties, "MegaraxVivoRecentContributorLimit", RECENT_CONTRIBUTOR_LIMIT), 1, 25);
		CHANGE_LOG_LIMIT = clamp(getInt(properties, "MegaraxVivoChangeLogLimit", CHANGE_LOG_LIMIT), 1, 25);
		SHOW_CONTRIBUTION_MESSAGES = getBoolean(properties, "MegaraxVivoShowContributionMessages", SHOW_CONTRIBUTION_MESSAGES);
		CONTRIBUTION_MESSAGE_THRESHOLD = clamp(getInt(properties, "MegaraxVivoContributionMessageThreshold", CONTRIBUTION_MESSAGE_THRESHOLD), 1, 1000000);
		MAXIMUM_SINGLE_CONTRIBUTION = clamp(getInt(properties, "MegaraxVivoMaximumSingleContribution", MAXIMUM_SINGLE_CONTRIBUTION), 1, 1000000);
		DEFAULT_PERSONAL_DAILY_CAP = clamp(getInt(properties, "MegaraxVivoDefaultPersonalDailyCap", DEFAULT_PERSONAL_DAILY_CAP), 1, 1000000);
		DEFAULT_SECURITY = clamp(getInt(properties, "MegaraxVivoDefaultSecurity", DEFAULT_SECURITY), 0, 100);
		DEFAULT_SUPPLY = clamp(getInt(properties, "MegaraxVivoDefaultSupply", DEFAULT_SUPPLY), 0, 100);
		DEFAULT_SPIRIT = clamp(getInt(properties, "MegaraxVivoDefaultSpirit", DEFAULT_SPIRIT), 0, 100);

		PILOT_CYCLE_KEY = cleanKey(properties.getProperty("MegaraxVivoPilotCycleKey", PILOT_CYCLE_KEY), PILOT_CYCLE_KEY, 64);
		PILOT_CYCLE_TITLE = cleanText(properties.getProperty("MegaraxVivoPilotCycleTitle", PILOT_CYCLE_TITLE), PILOT_CYCLE_TITLE, 96);
		PILOT_NEED_KEY = cleanKey(properties.getProperty("MegaraxVivoPilotNeedKey", PILOT_NEED_KEY), PILOT_NEED_KEY, 64);
		PILOT_NEED_TITLE = cleanText(properties.getProperty("MegaraxVivoPilotNeedTitle", PILOT_NEED_TITLE), PILOT_NEED_TITLE, 96);
		PILOT_NEED_DESCRIPTION = cleanText(properties.getProperty("MegaraxVivoPilotNeedDescription", PILOT_NEED_DESCRIPTION), PILOT_NEED_DESCRIPTION, 255);
		PILOT_REGION = cleanKey(properties.getProperty("MegaraxVivoPilotRegion", PILOT_REGION), PILOT_REGION, 32);
		PILOT_STATUS = cleanStatus(properties.getProperty("MegaraxVivoPilotStatus", PILOT_STATUS));
		PILOT_GOAL = Math.max(1L, getLong(properties, "MegaraxVivoPilotGoal", PILOT_GOAL));
		PILOT_PERSONAL_DAILY_CAP = clamp(getInt(properties, "MegaraxVivoPilotPersonalDailyCap", DEFAULT_PERSONAL_DAILY_CAP), 1, 1000000);
		PILOT_SUCCESS_SUPPLY_DELTA = clamp(getInt(properties, "MegaraxVivoPilotSuccessSupplyDelta", PILOT_SUCCESS_SUPPLY_DELTA), 0, 100);
		PILOT_SUCCESS_EFFECT = cleanText(properties.getProperty("MegaraxVivoPilotSuccessEffect", PILOT_SUCCESS_EFFECT), PILOT_SUCCESS_EFFECT, 255);
		PILOT_FAILURE_EFFECT = cleanText(properties.getProperty("MegaraxVivoPilotFailureEffect", PILOT_FAILURE_EFFECT), PILOT_FAILURE_EFFECT, 255);
		PILOT_CATEGORIES = parseCategories(properties.getProperty("MegaraxVivoPilotCategories"), PILOT_CATEGORIES);
		GODDARD_ADAPTER_ENABLED = getBoolean(properties, "EnableMegaraxVivoGoddardAdapter", GODDARD_ADAPTER_ENABLED);

		final long categoryGoal = PILOT_CATEGORIES.values().stream().mapToLong(Long::longValue).sum();
		if (categoryGoal != PILOT_GOAL)
		{
			LOGGER.warning("MegaraxVivo: Pilot category goals total " + categoryGoal + " but pilot goal is " + PILOT_GOAL + ". The overall goal will remain authoritative.");
		}
	}

	private static Map<String, Long> defaultPilotCategories()
	{
		final Map<String, Long> categories = new LinkedHashMap<>();
		categories.put("SUPPLY", 400L);
		categories.put("CRAFT", 200L);
		categories.put("SPOIL", 200L);
		categories.put("SERVICE", 100L);
		categories.put("ESCORT", 100L);
		return Collections.unmodifiableMap(categories);
	}

	private static Map<String, Long> parseCategories(String value, Map<String, Long> fallback)
	{
		if ((value == null) || value.trim().isEmpty())
		{
			return fallback;
		}
		final Map<String, Long> categories = new LinkedHashMap<>();
		for (String token : value.split(";"))
		{
			final String[] parts = token.trim().split(":", 2);
			if (parts.length != 2)
			{
				continue;
			}
			final String key = cleanKey(parts[0], "", 24);
			try
			{
				final long goal = Long.parseLong(parts[1].trim());
				if (!key.isEmpty() && (goal > 0))
				{
					categories.put(key, goal);
				}
			}
			catch (NumberFormatException e)
			{
				LOGGER.warning("MegaraxVivo: Ignoring invalid pilot category token " + token + ".");
			}
		}
		return categories.isEmpty() ? fallback : Collections.unmodifiableMap(categories);
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

	private static ZoneId getZone(String value, ZoneId fallback)
	{
		if ((value == null) || value.trim().isEmpty())
		{
			return fallback;
		}
		try
		{
			return ZoneId.of(value.trim());
		}
		catch (DateTimeException e)
		{
			LOGGER.warning("MegaraxVivo: Unknown time zone " + value + ". Using " + fallback + ".");
			return fallback;
		}
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static String cleanStatus(String value)
	{
		final String status = cleanKey(value, "PREPARATION", 16);
		switch (status)
		{
			case "ACTIVE":
			case "COMPLETED":
			case "FAILED":
			case "PAUSED":
			case "PREPARATION":
				return status;
			default:
				return "PREPARATION";
		}
	}

	private static String cleanKey(String value, String fallback, int maxLength)
	{
		final String clean = value == null ? "" : value.trim().toUpperCase().replaceAll("[^A-Z0-9_\\-]", "_");
		final String result = clean.isEmpty() ? fallback : clean;
		return result.substring(0, Math.min(maxLength, result.length()));
	}

	private static String cleanText(String value, String fallback, int maxLength)
	{
		final String clean = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
		final String result = clean.isEmpty() ? fallback : clean;
		return result.substring(0, Math.min(maxLength, result.length()));
	}
}
