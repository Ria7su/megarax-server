package custom.MegaraxKingdomEconomy;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.Config;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class KingdomEconomyConfig
{
	private static final Logger LOGGER = Logger.getLogger(KingdomEconomyConfig.class.getName());
	private static final String XML_FILE = "data/xml/MegaraxKingdomEconomy.xml";
	
	public static boolean ENABLED = true;
	public static int ADENA_ITEM_ID = 57;
	public static int RAX_COIN_ITEM_ID = 9142;
	public static int SAVE_INTERVAL_MINUTES = 5;
	public static int TERRITORY_CHECK_SECONDS = 15;
	public static int POINTS_PER_RAX_COIN = 500;
	public static int DAILY_RAX_LIMIT = 5;
	public static int WEEKLY_RAX_LIMIT = 25;
	public static int MIN_PLAYER_LEVEL = 40;
	public static int MAX_MONSTER_LEVEL_GAP = 9;
	public static int MIN_ADENA_DELTA = 1;
	public static int PVP_SAME_TARGET_COOLDOWN_MINUTES = 30;
	public static boolean BLOCK_SAME_IP_PVP = true;
	public static boolean BLOCK_SAME_HWID_PVP = true;
	public static int MONSTER_POINTS = 1;
	public static int CHAMPION_POINTS = 10;
	public static int RAID_POINTS = 100;
	public static int TERRITORY_MISSION_POINTS = 50;
	public static int ENEMY_PVP_POINTS = 25;
	public static int CASTLE_DEFENSE_POINTS = 30;
	public static String MESSAGE_ENTER_NEUTRAL = "Welcome to the Kingdom of %kingdom%. Ruled by %clan%. Royal tax: %tax%%. Kingdom level: %level%.";
	public static String MESSAGE_ENTER_RULER = "Welcome home, defender of %kingdom%. Royal bonus active: +%bonus%% Adena. Royal Contribution enabled.";
	public static String MESSAGE_TAX = "Royal Tax: %amount% Adena contributed to the Kingdom of %kingdom%.";
	public static String MESSAGE_CONTRIBUTION = "Royal Contribution: +%points% points in the Kingdom of %kingdom%.";
	public static String MESSAGE_RAX_REWARD = "Royal Contribution reward: +%amount% Rax Coin.";
	
	private static final Map<Integer, Kingdom> KINGDOMS_BY_CASTLE = new LinkedHashMap<>();
	private static final Map<String, KingdomTerritory> TERRITORIES = new LinkedHashMap<>();
	private static final Map<Integer, KingdomLevel> LEVELS = new HashMap<>();
	
	private KingdomEconomyConfig()
	{
	}
	
	public static synchronized void load()
	{
		KINGDOMS_BY_CASTLE.clear();
		TERRITORIES.clear();
		LEVELS.clear();
		
		final File file = resolveFile(XML_FILE);
		if (!file.isFile())
		{
			LOGGER.warning("KingdomEconomy: XML file not found: " + file.getAbsolutePath());
			return;
		}
		
		try
		{
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setIgnoringComments(true);
			factory.setIgnoringElementContentWhitespace(true);
			final Document document = factory.newDocumentBuilder().parse(file);
			final Element root = document.getDocumentElement();
			
			ENABLED = getBoolean(root, "enabled", ENABLED);
			ADENA_ITEM_ID = getInt(root, "adenaItemId", ADENA_ITEM_ID);
			RAX_COIN_ITEM_ID = getInt(root, "raxCoinItemId", RAX_COIN_ITEM_ID);
			SAVE_INTERVAL_MINUTES = Math.max(1, getInt(root, "saveIntervalMinutes", SAVE_INTERVAL_MINUTES));
			TERRITORY_CHECK_SECONDS = Math.max(5, getInt(root, "territoryCheckSeconds", TERRITORY_CHECK_SECONDS));
			
			final Element messages = child(root, "messages");
			if (messages != null)
			{
				MESSAGE_ENTER_NEUTRAL = getString(messages, "enterNeutral", MESSAGE_ENTER_NEUTRAL);
				MESSAGE_ENTER_RULER = getString(messages, "enterRuler", MESSAGE_ENTER_RULER);
				MESSAGE_TAX = getString(messages, "tax", MESSAGE_TAX);
				MESSAGE_CONTRIBUTION = getString(messages, "contribution", MESSAGE_CONTRIBUTION);
				MESSAGE_RAX_REWARD = getString(messages, "raxReward", MESSAGE_RAX_REWARD);
			}
			
			final Element contribution = child(root, "contribution");
			if (contribution != null)
			{
				POINTS_PER_RAX_COIN = Math.max(1, getInt(contribution, "pointsPerRaxCoin", POINTS_PER_RAX_COIN));
				DAILY_RAX_LIMIT = Math.max(0, getInt(contribution, "dailyRaxLimit", DAILY_RAX_LIMIT));
				WEEKLY_RAX_LIMIT = Math.max(0, getInt(contribution, "weeklyRaxLimit", WEEKLY_RAX_LIMIT));
				MIN_PLAYER_LEVEL = Math.max(1, getInt(contribution, "minPlayerLevel", MIN_PLAYER_LEVEL));
				MAX_MONSTER_LEVEL_GAP = Math.max(0, getInt(contribution, "maxMonsterLevelGap", MAX_MONSTER_LEVEL_GAP));
				MIN_ADENA_DELTA = Math.max(1, getInt(contribution, "minAdenaDelta", MIN_ADENA_DELTA));
				PVP_SAME_TARGET_COOLDOWN_MINUTES = Math.max(1, getInt(contribution, "pvpSameTargetCooldownMinutes", PVP_SAME_TARGET_COOLDOWN_MINUTES));
				BLOCK_SAME_IP_PVP = getBoolean(contribution, "blockSameIpPvp", BLOCK_SAME_IP_PVP);
				BLOCK_SAME_HWID_PVP = getBoolean(contribution, "blockSameHwidPvp", BLOCK_SAME_HWID_PVP);
			}
			
			final Element activity = child(root, "activity");
			if (activity != null)
			{
				MONSTER_POINTS = Math.max(0, getInt(activity, "monsterPoints", MONSTER_POINTS));
				CHAMPION_POINTS = Math.max(0, getInt(activity, "championPoints", CHAMPION_POINTS));
				RAID_POINTS = Math.max(0, getInt(activity, "raidPoints", RAID_POINTS));
				TERRITORY_MISSION_POINTS = Math.max(0, getInt(activity, "territoryMissionPoints", TERRITORY_MISSION_POINTS));
				ENEMY_PVP_POINTS = Math.max(0, getInt(activity, "enemyPvpPoints", ENEMY_PVP_POINTS));
				CASTLE_DEFENSE_POINTS = Math.max(0, getInt(activity, "castleDefensePoints", CASTLE_DEFENSE_POINTS));
			}
			
			final Element levels = child(root, "levels");
			if (levels != null)
			{
				final NodeList nodes = levels.getElementsByTagName("level");
				for (int i = 0; i < nodes.getLength(); i++)
				{
					final Element level = (Element) nodes.item(i);
					final int value = getInt(level, "value", 1);
					LEVELS.put(value, new KingdomLevel(value, getLong(level, "cost", 0), getString(level, "title", "Kingdom Level " + value), getInt(level, "dropBonusPercent", 0), getInt(level, "xpBonusPercent", 0), getInt(level, "adenaBonusPercent", 0)));
				}
			}
			
			final Element kingdoms = child(root, "kingdoms");
			if (kingdoms != null)
			{
				final NodeList nodes = kingdoms.getElementsByTagName("kingdom");
				for (int i = 0; i < nodes.getLength(); i++)
				{
					final Element element = (Element) nodes.item(i);
					final int castleId = getInt(element, "castleId", 0);
					if (castleId <= 0)
					{
						continue;
					}
					final Kingdom kingdom = new Kingdom(getString(element, "id", "castle_" + castleId), castleId, getString(element, "name", "Castle " + castleId), getInt(element, "prosperity", 50), getInt(element, "neutralTaxPercent", 3), getInt(element, "allyTaxPercent", 1), getInt(element, "enemyTaxPercent", 5), getInt(element, "rulerAdenaBonusPercent", 5), getInt(element, "allyAdenaBonusPercent", 2));
					final NodeList territories = element.getElementsByTagName("territory");
					for (int j = 0; j < territories.getLength(); j++)
					{
						final Element territoryElement = (Element) territories.item(j);
						final KingdomTerritory territory = new KingdomTerritory(getString(territoryElement, "id", kingdom.id + "_" + j), getString(territoryElement, "name", "Territory"), castleId, getInt(territoryElement, "minX", 0), getInt(territoryElement, "maxX", 0), getInt(territoryElement, "minY", 0), getInt(territoryElement, "maxY", 0), getInt(territoryElement, "minZ", Integer.MIN_VALUE), getInt(territoryElement, "maxZ", Integer.MAX_VALUE));
						kingdom.addTerritory(territory);
						TERRITORIES.put(territory.id, territory);
					}
					KINGDOMS_BY_CASTLE.put(castleId, kingdom);
				}
			}
			
			LOGGER.info("KingdomEconomy: Loaded " + KINGDOMS_BY_CASTLE.size() + " kingdoms, " + TERRITORIES.size() + " territories and " + LEVELS.size() + " levels.");
		}
		catch (Exception e)
		{
			LOGGER.log(Level.WARNING, "KingdomEconomy: Could not load " + file.getAbsolutePath(), e);
		}
	}
	
	public static Collection<Kingdom> getKingdoms()
	{
		return Collections.unmodifiableCollection(KINGDOMS_BY_CASTLE.values());
	}
	
	public static List<KingdomTerritory> getTerritories()
	{
		return Collections.unmodifiableList(new ArrayList<>(TERRITORIES.values()));
	}
	
	public static Kingdom getKingdom(int castleId)
	{
		return KINGDOMS_BY_CASTLE.get(castleId);
	}
	
	public static KingdomLevel getLevel(int level)
	{
		return LEVELS.get(level);
	}
	
	public static Collection<KingdomLevel> getLevels()
	{
		return Collections.unmodifiableCollection(LEVELS.values());
	}
	
	private static File resolveFile(String path)
	{
		final File local = new File(path);
		if (local.isFile())
		{
			return local;
		}
		final File gameLocal = new File("game/" + path);
		if (gameLocal.isFile())
		{
			return gameLocal;
		}
		if (Config.DATAPACK_ROOT != null)
		{
			return new File(Config.DATAPACK_ROOT, path);
		}
		return local;
	}
	
	private static Element child(Element parent, String name)
	{
		final NodeList nodes = parent.getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++)
		{
			final Node node = nodes.item(i);
			if ((node instanceof Element) && name.equals(node.getNodeName()))
			{
				return (Element) node;
			}
		}
		return null;
	}
	
	private static String getString(Element element, String name, String fallback)
	{
		return element.hasAttribute(name) ? element.getAttribute(name) : fallback;
	}
	
	private static boolean getBoolean(Element element, String name, boolean fallback)
	{
		return element.hasAttribute(name) ? Boolean.parseBoolean(element.getAttribute(name)) : fallback;
	}
	
	private static int getInt(Element element, String name, int fallback)
	{
		try
		{
			return element.hasAttribute(name) ? Integer.parseInt(element.getAttribute(name)) : fallback;
		}
		catch (Exception e)
		{
			return fallback;
		}
	}
	
	private static long getLong(Element element, String name, long fallback)
	{
		try
		{
			return element.hasAttribute(name) ? Long.parseLong(element.getAttribute(name)) : fallback;
		}
		catch (Exception e)
		{
			return fallback;
		}
	}
}
