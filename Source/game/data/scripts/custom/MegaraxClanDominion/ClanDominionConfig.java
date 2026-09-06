package custom.MegaraxClanDominion;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.Config;

public final class ClanDominionConfig
{
	private static final Logger LOGGER = Logger.getLogger(ClanDominionConfig.class.getName());
	private static final String CONFIG_FILE = "config/Custom/ClanDominion.ini";
	
	public static boolean ENABLED = true;
	public static int MIN_LEVEL_FOR_PVP = 76;
	public static long SAME_TARGET_COOLDOWN = 30L * 60 * 1000;
	public static boolean ALLOW_NORMAL_WORLD = false;
	public static boolean ALLOW_PVP_ZONES = true;
	public static boolean ALLOW_SIEGES = true;
	public static boolean ALLOW_CLAN_WAR = true;
	public static boolean BLOCK_SAME_IP = true;
	public static boolean BLOCK_SAME_HWID = true;
	public static final Set<String> PVP_ZONE_NAMES = new HashSet<>();
	
	public static int POINTS_PVP_ZONE_KILL = 3;
	public static int POINTS_CLAN_WAR_KILL = 4;
	public static int POINTS_SIEGE_KILL = 5;
	public static int POINTS_RAID_OBJECTIVE = 60;
	public static int POINTS_TERRITORY_CAPTURE = 80;
	public static int POINTS_TERRITORY_DEFENSE = 25;
	public static int POINTS_MISSION_COMPLETE = 40;
	public static int POINTS_RIVALRY_WIN = 120;
	public static int RIVALRY_KILL_MULTIPLIER = 2;
	
	public static int MISSION_PVP_ZONE_GOAL = 50;
	public static int MISSION_CLAN_WAR_GOAL = 40;
	public static int MISSION_SIEGE_GOAL = 20;
	public static int MISSION_RAID_GOAL = 3;
	public static int MISSION_WAR_CHEST_DEPOSIT_GOAL = 10000000;
	public static int MISSION_TERRITORY_CAPTURE_GOAL = 2;
	public static int MISSION_REWARD_POINTS = 40;
	
	public static final Set<Integer> WAR_CHEST_ALLOWED_ITEMS = new HashSet<>();
	public static boolean WAR_CHEST_ANY_MEMBER_DEPOSIT = true;
	public static int RALLY_COST_ITEM_ID = 57;
	public static long RALLY_COST_AMOUNT = 1000000;
	public static long RALLY_DURATION = 20L * 60 * 1000;
	public static long RALLY_COOLDOWN = 60L * 60 * 1000;
	public static int CHALLENGE_COST_ITEM_ID = 8875;
	public static long CHALLENGE_COST_AMOUNT = 10;
	
	public static int MAX_CLAN_HALL_UPGRADE_LEVEL = 5;
	public static final Map<Integer, HallUpgradeRequirement> HALL_UPGRADE_REQUIREMENTS = new HashMap<>();
	public static final List<TierRequirement> TIERS = new ArrayList<>();
	
	public static boolean TERRITORY_CONQUEST_ENABLED = true;
	public static int TERRITORY_CAPTURE_MINUTES = 5;
	public static int TERRITORY_OWNERSHIP_HOURS = 3;
	public static int MAX_TERRITORIES_PER_CLAN = 2;
	public static boolean ALLOW_ALLIANCE_ASSIST = true;
	public static boolean REWARD_ONLY_ACTIVE_MEMBERS = true;
	
	public static boolean RIVALRIES_ENABLED = true;
	public static int RIVALRY_MINIMUM_TIER = 2;
	public static int RIVALRY_DURATION_HOURS = 48;
	public static boolean RIVALRY_AUTO_ACCEPT = true;
	
	public static boolean RAID_OBJECTIVES_ENABLED = true;
	public static final Set<Integer> RAID_OBJECTIVE_BOSS_IDS = new HashSet<>();
	public static int RAID_OBJECTIVE_RADIUS = 1500;
	public static int RAID_DAMAGE_WEIGHT = 50;
	public static int RAID_PVP_WEIGHT = 25;
	public static int RAID_ALIVE_WEIGHT = 15;
	public static int RAID_LAST_HIT_WEIGHT = 10;
	
	public static boolean SEASONS_ENABLED = true;
	public static int SEASON_DURATION_DAYS = 7;
	public static int SEASON_REWARD_TOP_LIMIT = 10;
	
	public static boolean ANNOUNCEMENTS_ENABLED = true;
	public static boolean ANNOUNCE_TIER_UPS = true;
	public static boolean ANNOUNCE_TERRITORY_CAPTURES = true;
	public static boolean ANNOUNCE_RAID_OBJECTIVES = true;
	public static boolean ANNOUNCE_RIVALRY_RESULTS = true;
	public static int ANNOUNCE_MIN_POINTS = 50;
	public static int WAR_NEWS_LIMIT = 20;
	
	private ClanDominionConfig()
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
				LOGGER.log(Level.WARNING, "ClanDominion: Could not load " + file.getAbsolutePath() + ".", e);
			}
		}
		else
		{
			LOGGER.warning("ClanDominion: Config file not found. Defaults will be used.");
		}
		
		ENABLED = getBoolean(properties, "EnableClanDominionSystem", ENABLED);
		MIN_LEVEL_FOR_PVP = getInt(properties, "MinLevelForDominionPvP", MIN_LEVEL_FOR_PVP);
		SAME_TARGET_COOLDOWN = getInt(properties, "SameTargetKillCooldownMinutes", 30) * 60L * 1000;
		ALLOW_NORMAL_WORLD = getBoolean(properties, "AllowDominionPointsInNormalWorld", ALLOW_NORMAL_WORLD);
		ALLOW_PVP_ZONES = getBoolean(properties, "AllowDominionPointsInPvPZones", ALLOW_PVP_ZONES);
		ALLOW_SIEGES = getBoolean(properties, "AllowDominionPointsInSieges", ALLOW_SIEGES);
		ALLOW_CLAN_WAR = getBoolean(properties, "AllowDominionPointsInClanWar", ALLOW_CLAN_WAR);
		BLOCK_SAME_IP = getBoolean(properties, "BlockSameIpDominionKills", BLOCK_SAME_IP);
		BLOCK_SAME_HWID = getBoolean(properties, "BlockSameHwidDominionKills", BLOCK_SAME_HWID);
		PVP_ZONE_NAMES.clear();
		for (String value : getList(properties, "DominionPvPZoneNames"))
		{
			PVP_ZONE_NAMES.add(value.toLowerCase(Locale.ROOT));
		}
		
		POINTS_PVP_ZONE_KILL = getInt(properties, "PointsPvPZoneKill", POINTS_PVP_ZONE_KILL);
		POINTS_CLAN_WAR_KILL = getInt(properties, "PointsClanWarKill", POINTS_CLAN_WAR_KILL);
		POINTS_SIEGE_KILL = getInt(properties, "PointsSiegeKill", POINTS_SIEGE_KILL);
		POINTS_RAID_OBJECTIVE = getInt(properties, "PointsRaidObjective", POINTS_RAID_OBJECTIVE);
		POINTS_TERRITORY_CAPTURE = getInt(properties, "PointsTerritoryCapture", POINTS_TERRITORY_CAPTURE);
		POINTS_TERRITORY_DEFENSE = getInt(properties, "PointsTerritoryDefense", POINTS_TERRITORY_DEFENSE);
		POINTS_MISSION_COMPLETE = getInt(properties, "PointsMissionComplete", POINTS_MISSION_COMPLETE);
		POINTS_RIVALRY_WIN = getInt(properties, "PointsRivalryWin", POINTS_RIVALRY_WIN);
		RIVALRY_KILL_MULTIPLIER = getInt(properties, "RivalryKillMultiplier", RIVALRY_KILL_MULTIPLIER);
		
		MISSION_PVP_ZONE_GOAL = getInt(properties, "MissionKillPvPZoneGoal", MISSION_PVP_ZONE_GOAL);
		MISSION_CLAN_WAR_GOAL = getInt(properties, "MissionKillClanWarGoal", MISSION_CLAN_WAR_GOAL);
		MISSION_SIEGE_GOAL = getInt(properties, "MissionKillSiegeGoal", MISSION_SIEGE_GOAL);
		MISSION_RAID_GOAL = getInt(properties, "MissionRaidGoal", MISSION_RAID_GOAL);
		MISSION_WAR_CHEST_DEPOSIT_GOAL = getInt(properties, "MissionWarChestDepositGoal", MISSION_WAR_CHEST_DEPOSIT_GOAL);
		MISSION_TERRITORY_CAPTURE_GOAL = getInt(properties, "MissionTerritoryCaptureGoal", MISSION_TERRITORY_CAPTURE_GOAL);
		MISSION_REWARD_POINTS = getInt(properties, "MissionRewardPoints", MISSION_REWARD_POINTS);
		
		WAR_CHEST_ALLOWED_ITEMS.clear();
		for (String value : getList(properties, "WarChestAllowedItemIds"))
		{
			addInt(WAR_CHEST_ALLOWED_ITEMS, value);
		}
		WAR_CHEST_ANY_MEMBER_DEPOSIT = getBoolean(properties, "WarChestAllowAnyMemberDeposit", WAR_CHEST_ANY_MEMBER_DEPOSIT);
		RALLY_COST_ITEM_ID = getInt(properties, "RallyPointCostItemId", RALLY_COST_ITEM_ID);
		RALLY_COST_AMOUNT = getLong(properties, "RallyPointCostAmount", RALLY_COST_AMOUNT);
		RALLY_DURATION = getInt(properties, "RallyPointDurationMinutes", 20) * 60L * 1000;
		RALLY_COOLDOWN = getInt(properties, "RallyPointCooldownMinutes", 60) * 60L * 1000;
		CHALLENGE_COST_ITEM_ID = getInt(properties, "ChallengeAnnouncementCostItemId", CHALLENGE_COST_ITEM_ID);
		CHALLENGE_COST_AMOUNT = getLong(properties, "ChallengeAnnouncementCostAmount", CHALLENGE_COST_AMOUNT);
		
		MAX_CLAN_HALL_UPGRADE_LEVEL = getInt(properties, "MaxClanHallUpgradeLevel", MAX_CLAN_HALL_UPGRADE_LEVEL);
		parseHallRequirements(properties.getProperty("ClanHallUpgradeRequirements", ""));
		parseTiers(properties.getProperty("ClanDominionTiers", ""));
		
		TERRITORY_CONQUEST_ENABLED = getBoolean(properties, "EnableTerritoryConquest", TERRITORY_CONQUEST_ENABLED);
		TERRITORY_CAPTURE_MINUTES = getInt(properties, "TerritoryCaptureTimeMinutes", TERRITORY_CAPTURE_MINUTES);
		TERRITORY_OWNERSHIP_HOURS = getInt(properties, "TerritoryOwnershipDurationHours", TERRITORY_OWNERSHIP_HOURS);
		MAX_TERRITORIES_PER_CLAN = getInt(properties, "MaxTerritoriesPerClan", MAX_TERRITORIES_PER_CLAN);
		ALLOW_ALLIANCE_ASSIST = getBoolean(properties, "AllowAllianceAssist", ALLOW_ALLIANCE_ASSIST);
		REWARD_ONLY_ACTIVE_MEMBERS = getBoolean(properties, "RewardOnlyActiveMembers", REWARD_ONLY_ACTIVE_MEMBERS);
		
		RIVALRIES_ENABLED = getBoolean(properties, "EnableClanRivalries", RIVALRIES_ENABLED);
		RIVALRY_MINIMUM_TIER = getInt(properties, "RivalryMinimumTier", RIVALRY_MINIMUM_TIER);
		RIVALRY_DURATION_HOURS = getInt(properties, "RivalryDurationHours", RIVALRY_DURATION_HOURS);
		RIVALRY_AUTO_ACCEPT = getBoolean(properties, "RivalryAutoAccept", RIVALRY_AUTO_ACCEPT);
		
		RAID_OBJECTIVES_ENABLED = getBoolean(properties, "EnableClanRaidObjectives", RAID_OBJECTIVES_ENABLED);
		RAID_OBJECTIVE_BOSS_IDS.clear();
		for (String value : getList(properties, "RaidObjectiveBossIds"))
		{
			addInt(RAID_OBJECTIVE_BOSS_IDS, value);
		}
		RAID_OBJECTIVE_RADIUS = getInt(properties, "RaidObjectiveRadius", RAID_OBJECTIVE_RADIUS);
		RAID_DAMAGE_WEIGHT = getInt(properties, "RaidObjectiveDamageWeight", RAID_DAMAGE_WEIGHT);
		RAID_PVP_WEIGHT = getInt(properties, "RaidObjectivePvpWeight", RAID_PVP_WEIGHT);
		RAID_ALIVE_WEIGHT = getInt(properties, "RaidObjectiveAliveWeight", RAID_ALIVE_WEIGHT);
		RAID_LAST_HIT_WEIGHT = getInt(properties, "RaidObjectiveLastHitWeight", RAID_LAST_HIT_WEIGHT);
		
		SEASONS_ENABLED = getBoolean(properties, "EnableClanSeasons", SEASONS_ENABLED);
		SEASON_DURATION_DAYS = getInt(properties, "SeasonDurationDays", SEASON_DURATION_DAYS);
		SEASON_REWARD_TOP_LIMIT = getInt(properties, "SeasonRewardTopLimit", SEASON_REWARD_TOP_LIMIT);
		
		ANNOUNCEMENTS_ENABLED = getBoolean(properties, "EnableClanDominionAnnouncements", ANNOUNCEMENTS_ENABLED);
		ANNOUNCE_TIER_UPS = getBoolean(properties, "AnnounceTierUps", ANNOUNCE_TIER_UPS);
		ANNOUNCE_TERRITORY_CAPTURES = getBoolean(properties, "AnnounceTerritoryCaptures", ANNOUNCE_TERRITORY_CAPTURES);
		ANNOUNCE_RAID_OBJECTIVES = getBoolean(properties, "AnnounceRaidObjectives", ANNOUNCE_RAID_OBJECTIVES);
		ANNOUNCE_RIVALRY_RESULTS = getBoolean(properties, "AnnounceRivalryResults", ANNOUNCE_RIVALRY_RESULTS);
		ANNOUNCE_MIN_POINTS = getInt(properties, "AnnounceMinPoints", ANNOUNCE_MIN_POINTS);
		WAR_NEWS_LIMIT = getInt(properties, "WarNewsLimit", WAR_NEWS_LIMIT);
		
		ensureDefaults();
	}
	
	private static void ensureDefaults()
	{
		if (PVP_ZONE_NAMES.isEmpty())
		{
			PVP_ZONE_NAMES.add("megarax_forest_dead_pvp");
			PVP_ZONE_NAMES.add("megarax_imperial_tomb_pvp");
			PVP_ZONE_NAMES.add("megarax_toi_13_pvp");
			PVP_ZONE_NAMES.add("megarax_ketra_orc_pvp");
			PVP_ZONE_NAMES.add("megarax_pagan_temple_pvp");
		}
		if (WAR_CHEST_ALLOWED_ITEMS.isEmpty())
		{
			WAR_CHEST_ALLOWED_ITEMS.add(57);
			WAR_CHEST_ALLOWED_ITEMS.add(9142);
			WAR_CHEST_ALLOWED_ITEMS.add(9143);
			WAR_CHEST_ALLOWED_ITEMS.add(4356);
			WAR_CHEST_ALLOWED_ITEMS.add(8875);
		}
		if (RAID_OBJECTIVE_BOSS_IDS.isEmpty())
		{
			RAID_OBJECTIVE_BOSS_IDS.add(25283);
			RAID_OBJECTIVE_BOSS_IDS.add(25286);
			RAID_OBJECTIVE_BOSS_IDS.add(29001);
			RAID_OBJECTIVE_BOSS_IDS.add(29006);
			RAID_OBJECTIVE_BOSS_IDS.add(29014);
		}
		if (TIERS.isEmpty())
		{
			TIERS.add(new TierRequirement(0, 0, 0, 0, "Unknown Clan"));
			TIERS.add(new TierRequirement(1, 250, 3, 20, "Rising Clan"));
			TIERS.add(new TierRequirement(2, 1000, 5, 60, "War Clan"));
			TIERS.add(new TierRequirement(3, 3000, 8, 120, "Dominant Clan"));
			TIERS.add(new TierRequirement(4, 7500, 12, 220, "Elite Clan"));
			TIERS.add(new TierRequirement(5, 15000, 18, 350, "Legendary Clan"));
			TIERS.add(new TierRequirement(6, 30000, 24, 500, "Megarax Dynasty"));
		}
		if (HALL_UPGRADE_REQUIREMENTS.isEmpty())
		{
			HALL_UPGRADE_REQUIREMENTS.put(1, new HallUpgradeRequirement(1, 0, 0, 0));
			HALL_UPGRADE_REQUIREMENTS.put(2, new HallUpgradeRequirement(2, 1000, 5000000, 40));
			HALL_UPGRADE_REQUIREMENTS.put(3, new HallUpgradeRequirement(3, 3000, 15000000, 80));
			HALL_UPGRADE_REQUIREMENTS.put(4, new HallUpgradeRequirement(4, 7500, 50000000, 150));
			HALL_UPGRADE_REQUIREMENTS.put(5, new HallUpgradeRequirement(5, 15000, 100000000, 250));
		}
	}
	
	private static void parseTiers(String value)
	{
		TIERS.clear();
		for (String entry : value.split(";"))
		{
			final String[] parts = entry.trim().split(":");
			if (parts.length < 5)
			{
				continue;
			}
			try
			{
				TIERS.add(new TierRequirement(Integer.parseInt(parts[0].trim()), Long.parseLong(parts[1].trim()), Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim()), parts[4].trim()));
			}
			catch (NumberFormatException e)
			{
				LOGGER.warning("ClanDominion: Invalid tier requirement: " + entry);
			}
		}
	}
	
	private static void parseHallRequirements(String value)
	{
		HALL_UPGRADE_REQUIREMENTS.clear();
		for (String entry : value.split(";"))
		{
			final String[] parts = entry.trim().split(":");
			if (parts.length < 4)
			{
				continue;
			}
			try
			{
				final int level = Integer.parseInt(parts[0].trim());
				HALL_UPGRADE_REQUIREMENTS.put(level, new HallUpgradeRequirement(level, Long.parseLong(parts[1].trim()), Long.parseLong(parts[2].trim()), Integer.parseInt(parts[3].trim())));
			}
			catch (NumberFormatException e)
			{
				LOGGER.warning("ClanDominion: Invalid clan hall requirement: " + entry);
			}
		}
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
	
	private static List<String> getList(Properties properties, String key)
	{
		final List<String> values = new ArrayList<>();
		for (String value : properties.getProperty(key, "").split(","))
		{
			final String clean = value.trim();
			if (!clean.isEmpty())
			{
				values.add(clean);
			}
		}
		return values;
	}
	
	private static void addInt(Set<Integer> values, String value)
	{
		try
		{
			values.add(Integer.parseInt(value.trim()));
		}
		catch (NumberFormatException ignored)
		{
		}
	}
	
	public static final class TierRequirement
	{
		public final int tier;
		public final long requiredPoints;
		public final int minMembers;
		public final int weeklyActivity;
		public final String name;
		
		private TierRequirement(int tier, long requiredPoints, int minMembers, int weeklyActivity, String name)
		{
			this.tier = tier;
			this.requiredPoints = requiredPoints;
			this.minMembers = minMembers;
			this.weeklyActivity = weeklyActivity;
			this.name = name;
		}
	}
	
	public static final class HallUpgradeRequirement
	{
		public final int level;
		public final long requiredPoints;
		public final long adenaCost;
		public final int minWeeklyActivity;
		
		private HallUpgradeRequirement(int level, long requiredPoints, long adenaCost, int minWeeklyActivity)
		{
			this.level = level;
			this.requiredPoints = requiredPoints;
			this.adenaCost = adenaCost;
			this.minWeeklyActivity = minWeeklyActivity;
		}
	}
}
