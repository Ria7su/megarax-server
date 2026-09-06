package custom.MegaraxMarketplace;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.Config;

public final class MarketplaceConfig
{
	private static final Logger LOGGER = Logger.getLogger(MarketplaceConfig.class.getName());
	private static final String MARKETPLACE_CONFIG_FILE = "config/Custom/Marketplace.ini";
	private static final String RESTRICTED_CONFIG_FILE = "config/Custom/MarketplaceRestrictedItems.ini";
	
	public static boolean ENABLED = true;
	public static int CURRENCY_ITEM_ID = 9142;
	public static int COMMISSION_PERCENT = 5;
	public static int LISTING_DURATION_HOURS = 72;
	public static int MAX_LISTINGS_PER_PLAYER = 15;
	public static long MIN_PRICE = 1;
	public static long MAX_PRICE = 1000000;
	public static int LISTING_COOLDOWN_SECONDS = 3;
	public static boolean ALLOW_STACKABLE_ITEMS = true;
	public static boolean ALLOW_EQUIPPED_ITEMS = false;
	public static boolean ALLOW_AUGMENTED_ITEMS = false;
	public static boolean ALLOW_ENCHANTED_ITEMS = true;
	public static boolean ALLOW_CUSTOM_ITEMS = true;
	public static boolean ALLOW_QUEST_ITEMS = false;
	public static boolean ALLOW_SHADOW_ITEMS = false;
	public static boolean ALLOW_TIME_LIMITED_ITEMS = false;
	public static boolean ALLOW_HERO_ITEMS = false;
	public static boolean ALLOW_TRADABLE_ONLY = true;
	public static boolean LOG_TRANSACTIONS = true;
	
	public static boolean ALLOW_CURRENCY_ITEM_TO_BE_SOLD = false;
	public static boolean ALLOW_ADENA = false;
	
	public static boolean ALLOWED_IN_COMBAT = false;
	public static boolean ALLOWED_WHILE_DEAD = false;
	public static boolean ALLOWED_IN_OLYMPIAD = false;
	public static boolean ALLOWED_IN_TOURNAMENT = false;
	public static boolean ALLOWED_IN_JAIL = false;
	public static boolean ALLOWED_IN_INSTANCE = false;
	public static boolean ALLOWED_DURING_TRADE = false;
	public static boolean ALLOWED_DURING_PRIVATE_STORE = false;
	public static boolean ALLOWED_FOR_KARMA_PLAYERS = false;
	public static boolean ALLOWED_FOR_NEW_CHARACTERS = false;
	public static int MINIMUM_LEVEL = 40;
	public static int MINIMUM_ACCOUNT_AGE_DAYS = 3;
	
	public static boolean ANNOUNCE_RARE_SALES = true;
	public static long RARE_SALE_MIN_PRICE = 500;
	public static int ANNOUNCE_ENCHANT_LEVEL = 16;
	
	public static final Set<Integer> RESTRICTED_ITEM_IDS = new HashSet<>();
	
	private MarketplaceConfig()
	{
	}
	
	public static void load()
	{
		final Properties properties = loadProperties(MARKETPLACE_CONFIG_FILE);
		ENABLED = getBoolean(properties, "MarketplaceEnabled", ENABLED);
		CURRENCY_ITEM_ID = getInt(properties, "MarketplaceCurrencyItemId", CURRENCY_ITEM_ID);
		COMMISSION_PERCENT = getInt(properties, "MarketplaceCommissionPercent", COMMISSION_PERCENT);
		LISTING_DURATION_HOURS = getInt(properties, "MarketplaceListingDurationHours", LISTING_DURATION_HOURS);
		MAX_LISTINGS_PER_PLAYER = getInt(properties, "MarketplaceMaxListingsPerPlayer", MAX_LISTINGS_PER_PLAYER);
		MIN_PRICE = getLong(properties, "MarketplaceMinPrice", MIN_PRICE);
		MAX_PRICE = getLong(properties, "MarketplaceMaxPrice", MAX_PRICE);
		LISTING_COOLDOWN_SECONDS = getInt(properties, "MarketplaceListingCooldownSeconds", LISTING_COOLDOWN_SECONDS);
		ALLOW_STACKABLE_ITEMS = getBoolean(properties, "MarketplaceAllowStackableItems", ALLOW_STACKABLE_ITEMS);
		ALLOW_EQUIPPED_ITEMS = getBoolean(properties, "MarketplaceAllowEquippedItems", ALLOW_EQUIPPED_ITEMS);
		ALLOW_AUGMENTED_ITEMS = getBoolean(properties, "MarketplaceAllowAugmentedItems", ALLOW_AUGMENTED_ITEMS);
		ALLOW_ENCHANTED_ITEMS = getBoolean(properties, "MarketplaceAllowEnchantedItems", ALLOW_ENCHANTED_ITEMS);
		ALLOW_CUSTOM_ITEMS = getBoolean(properties, "MarketplaceAllowCustomItems", ALLOW_CUSTOM_ITEMS);
		ALLOW_QUEST_ITEMS = getBoolean(properties, "MarketplaceAllowQuestItems", ALLOW_QUEST_ITEMS);
		ALLOW_SHADOW_ITEMS = getBoolean(properties, "MarketplaceAllowShadowItems", ALLOW_SHADOW_ITEMS);
		ALLOW_TIME_LIMITED_ITEMS = getBoolean(properties, "MarketplaceAllowTimeLimitedItems", ALLOW_TIME_LIMITED_ITEMS);
		ALLOW_HERO_ITEMS = getBoolean(properties, "MarketplaceAllowHeroItems", ALLOW_HERO_ITEMS);
		ALLOW_TRADABLE_ONLY = getBoolean(properties, "MarketplaceAllowTradableOnly", ALLOW_TRADABLE_ONLY);
		LOG_TRANSACTIONS = getBoolean(properties, "MarketplaceLogTransactions", LOG_TRANSACTIONS);
		ALLOW_CURRENCY_ITEM_TO_BE_SOLD = getBoolean(properties, "MarketplaceAllowCurrencyItemToBeSold", ALLOW_CURRENCY_ITEM_TO_BE_SOLD);
		ALLOW_ADENA = getBoolean(properties, "MarketplaceAllowAdena", ALLOW_ADENA);
		ALLOWED_IN_COMBAT = getBoolean(properties, "MarketplaceAllowedInCombat", ALLOWED_IN_COMBAT);
		ALLOWED_WHILE_DEAD = getBoolean(properties, "MarketplaceAllowedWhileDead", ALLOWED_WHILE_DEAD);
		ALLOWED_IN_OLYMPIAD = getBoolean(properties, "MarketplaceAllowedInOlympiad", ALLOWED_IN_OLYMPIAD);
		ALLOWED_IN_TOURNAMENT = getBoolean(properties, "MarketplaceAllowedInTournament", ALLOWED_IN_TOURNAMENT);
		ALLOWED_IN_JAIL = getBoolean(properties, "MarketplaceAllowedInJail", ALLOWED_IN_JAIL);
		ALLOWED_IN_INSTANCE = getBoolean(properties, "MarketplaceAllowedInInstance", ALLOWED_IN_INSTANCE);
		ALLOWED_DURING_TRADE = getBoolean(properties, "MarketplaceAllowedDuringTrade", ALLOWED_DURING_TRADE);
		ALLOWED_DURING_PRIVATE_STORE = getBoolean(properties, "MarketplaceAllowedDuringPrivateStore", ALLOWED_DURING_PRIVATE_STORE);
		ALLOWED_FOR_KARMA_PLAYERS = getBoolean(properties, "MarketplaceAllowedForKarmaPlayers", ALLOWED_FOR_KARMA_PLAYERS);
		ALLOWED_FOR_NEW_CHARACTERS = getBoolean(properties, "MarketplaceAllowedForNewCharacters", ALLOWED_FOR_NEW_CHARACTERS);
		MINIMUM_LEVEL = getInt(properties, "MarketplaceMinimumLevel", MINIMUM_LEVEL);
		MINIMUM_ACCOUNT_AGE_DAYS = getInt(properties, "MarketplaceMinimumAccountAgeDays", MINIMUM_ACCOUNT_AGE_DAYS);
		ANNOUNCE_RARE_SALES = getBoolean(properties, "MarketplaceAnnounceRareSales", ANNOUNCE_RARE_SALES);
		RARE_SALE_MIN_PRICE = getLong(properties, "MarketplaceRareSaleMinPrice", RARE_SALE_MIN_PRICE);
		ANNOUNCE_ENCHANT_LEVEL = getInt(properties, "MarketplaceAnnounceEnchantLevel", ANNOUNCE_ENCHANT_LEVEL);
		
		COMMISSION_PERCENT = Math.max(0, Math.min(99, COMMISSION_PERCENT));
		LISTING_DURATION_HOURS = Math.max(1, LISTING_DURATION_HOURS);
		MAX_LISTINGS_PER_PLAYER = Math.max(1, MAX_LISTINGS_PER_PLAYER);
		MIN_PRICE = Math.max(1, MIN_PRICE);
		MAX_PRICE = Math.max(MIN_PRICE, Math.min(Integer.MAX_VALUE, MAX_PRICE));
		LISTING_COOLDOWN_SECONDS = Math.max(0, LISTING_COOLDOWN_SECONDS);
		MINIMUM_LEVEL = Math.max(1, MINIMUM_LEVEL);
		MINIMUM_ACCOUNT_AGE_DAYS = Math.max(0, MINIMUM_ACCOUNT_AGE_DAYS);
		
		RESTRICTED_ITEM_IDS.clear();
		final Properties restricted = loadProperties(RESTRICTED_CONFIG_FILE);
		for (String value : getList(restricted, "RestrictedItemIds"))
		{
			try
			{
				RESTRICTED_ITEM_IDS.add(Integer.parseInt(value));
			}
			catch (NumberFormatException ignored)
			{
			}
		}
	}
	
	public static boolean isRestricted(int itemId)
	{
		return RESTRICTED_ITEM_IDS.contains(itemId) || (!ALLOW_CURRENCY_ITEM_TO_BE_SOLD && (itemId == CURRENCY_ITEM_ID)) || (!ALLOW_ADENA && (itemId == 57));
	}
	
	private static Properties loadProperties(String path)
	{
		final Properties properties = new Properties();
		final File file = resolveConfigFile(path);
		if ((file != null) && file.isFile())
		{
			try (FileInputStream input = new FileInputStream(file))
			{
				properties.load(input);
			}
			catch (Exception e)
			{
				LOGGER.log(Level.WARNING, "Marketplace: Could not load " + file.getAbsolutePath() + ".", e);
			}
		}
		else
		{
			LOGGER.warning("Marketplace: Config file " + path + " not found. Defaults will be used.");
		}
		return properties;
	}
	
	private static File resolveConfigFile(String path)
	{
		final List<File> candidates = new ArrayList<>();
		candidates.add(new File(path));
		candidates.add(new File("game/" + path));
		if (Config.DATAPACK_ROOT != null)
		{
			candidates.add(new File(Config.DATAPACK_ROOT, path));
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
	
	private static List<String> getList(Properties properties, String key)
	{
		final String raw = properties.getProperty(key, "");
		if (raw.trim().isEmpty())
		{
			return Collections.emptyList();
		}
		final List<String> values = new ArrayList<>();
		for (String value : raw.split(","))
		{
			final String clean = value.trim();
			if (!clean.isEmpty())
			{
				values.add(clean);
			}
		}
		return values;
	}
}
