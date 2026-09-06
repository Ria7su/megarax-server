/*
 * Custom Megarax Dungeon - L2JMobius Interlude
 */
package custom.MegaraxDungeon;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.instancezone.InstanceWorld;
import org.l2jmobius.gameserver.managers.InstanceManager;
import org.l2jmobius.gameserver.network.serverpackets.ExShowScreenMessage;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.util.Broadcast;

import ai.AbstractNpcAI;
import custom.DelevelManager.DelevelManager;
import custom.MegaraxDailyMissions.MegaraxDailyMissions;
import custom.MegaraxLivingChronicle.MegaraxLivingChronicle;
import custom.MegaraxSevenSigns.MegaraxSevenSigns;

public class MegaraxDungeon extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxDungeon.class.getName());
	private static final Random RANDOM = new Random();
	
	private static final int DUNGEON_MANAGER = 1004000;
	private static final int PRIVATE_INSTANCE_TEMPLATE = 3050;
	
	// ---------------------------------------------------------------
	// DAILY SOLO DUNGEON - IDs
	// ---------------------------------------------------------------
	private static final int ANGEL        = 1004001;
	private static final int ANGEL_KILLER = 1004002;
	private static final int KARIK        = 1004003;
	
	// ---------------------------------------------------------------
	// FARM DUNGEON - IDs
	// ---------------------------------------------------------------
	private static final int FARM_MOB_1 = 21186; // Oleadas 1-3
	private static final int FARM_MOB_2 = 21331; // Oleadas 4-5
	private static final int FARM_MOB_3 = 21657; // Magma Drake, used by Party Dungeon
	private static final int FARM_BORAK_MOB = 9918; // Replaces Magma Drake in Farm waves 6-8
	private static final int FARM_MOB_4 = 29032; // Oleada 9
	private static final int FARM_BOSS  = 9910;  // Oleada 10 - Sandramanaia
	
	// ---------------------------------------------------------------
	// ELITE DUNGEON - IDs
	// ---------------------------------------------------------------
	// Custom level 80+ mobs using retail appearances.
	private static final int ELITE_GREEN_MOB_1 = 1004010; // Megarax Stakato Guard
	private static final int ELITE_GREEN_MOB_2 = 1004011; // Megarax Stakato Soldier
	private static final int ELITE_GREEN_MOB_3 = 1004018; // Megarax Stakato Crusher
	private static final int ELITE_BLUE_MOB_1  = 1004012; // Megarax Argos Watchman
	private static final int ELITE_BLUE_MOB_2  = 1004013; // Megarax Argos Scout
	private static final int ELITE_BLUE_MOB_3  = 1004014; // Megarax Argos Supervisor
	private static final int ELITE_RED_MOB_1   = 1004015; // Megarax Grail Protector
	private static final int ELITE_RED_MOB_2   = 1004016; // Megarax Grail Destroyer
	private static final int ELITE_RED_MOB_3   = 1004017; // Megarax Grail Messenger
	private static final int ELITE_RED_BOSS    = 9918; // Borak
	// Bosses aleatorios
	private static final int BOSS_LILITH = 25283;
	private static final int BOSS_ANAKIM = 25286;
	
	// ---------------------------------------------------------------
	// ITEMS
	// ---------------------------------------------------------------
	private static final int DUNGEON_COIN = 9143;
	private static final int RAX_COIN     = 9142;
	private static final int CRAFT_COIN   = 4356;
	private static final int DAILY_ANGEL_COIN = 2;
	private static final int DAILY_ANGEL_KILLER_COIN = 4;
	private static final int DAILY_GUARDIAN_COIN = 30;
	private static final int BLESSED_ARMOR_S = 6578;
	private static final int BLESSED_WEAPON_S = 6577;
	private static final int SECRET_BOOK_OF_GIANTS = 6622;
	
	// ---------------------------------------------------------------
	// CONFIGURACION GENERAL
	// ---------------------------------------------------------------
	private static final int  MIN_LEVEL    = 76;
	private static final int  DUNGEON_TIME = 20 * 60 * 1000;       // 20 min
	private static final long COOLDOWN_24H = 24 * 60 * 60 * 1000L; // 24h
	private static final long COOLDOWN_12H = 12 * 60 * 60 * 1000L; // 12h
	private static final long COOLDOWN_6H  = 6 * 60 * 60 * 1000L;  // 6h
	private static final int  FARM_TIME    = 60 * 60 * 1000;       // 60 min
	private static final int  ELITE_TIME   = 30 * 60 * 1000;       // 30 min
	private static final int  PARTY_TIME   = 45 * 60 * 1000;       // 45 min
	private static final int  COMPLETION_TELEPORT_DELAY = 15 * 1000;
	private static final String RANKING_TABLE_SQL = "CREATE TABLE IF NOT EXISTS megarax_dungeon_ranking (week_key VARCHAR(10) NOT NULL, dungeon VARCHAR(16) NOT NULL, route VARCHAR(16) NOT NULL, player_id INT NOT NULL, player_name VARCHAR(35) NOT NULL, time_seconds INT NOT NULL, completed_at BIGINT NOT NULL, PRIMARY KEY (week_key, dungeon, route, player_id))";
	private static final String RANKING_SELECT_PLAYER_SQL = "SELECT time_seconds FROM megarax_dungeon_ranking WHERE week_key=? AND dungeon=? AND route=? AND player_id=?";
	private static final String RANKING_INSERT_SQL = "INSERT INTO megarax_dungeon_ranking (week_key, dungeon, route, player_id, player_name, time_seconds, completed_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
	private static final String RANKING_UPDATE_SQL = "UPDATE megarax_dungeon_ranking SET player_name=?, time_seconds=?, completed_at=? WHERE week_key=? AND dungeon=? AND route=? AND player_id=?";
	private static final String RANKING_TOP_SQL = "SELECT player_name, dungeon, route, time_seconds FROM megarax_dungeon_ranking WHERE week_key=? ORDER BY time_seconds ASC LIMIT 10";
	private static final String RANKING_REWARD_TABLE_SQL = "CREATE TABLE IF NOT EXISTS megarax_dungeon_ranking_rewards (week_key VARCHAR(10) NOT NULL, dungeon VARCHAR(16) NOT NULL, route VARCHAR(16) NOT NULL, player_id INT NOT NULL, claimed_at BIGINT NOT NULL, PRIMARY KEY (week_key, dungeon, route, player_id))";
	private static final String RANKING_TOP_CATEGORY_SQL = "SELECT player_id, player_name, time_seconds FROM megarax_dungeon_ranking WHERE week_key=? AND dungeon=? AND route=? ORDER BY time_seconds ASC LIMIT 10";
	private static final String RANKING_REWARD_CHECK_SQL = "SELECT 1 FROM megarax_dungeon_ranking_rewards WHERE week_key=? AND dungeon=? AND route=? AND player_id=?";
	private static final String RANKING_REWARD_INSERT_SQL = "INSERT INTO megarax_dungeon_ranking_rewards (week_key, dungeon, route, player_id, claimed_at) VALUES (?, ?, ?, ?, ?)";
	private static final String COOLDOWN_TABLE_SQL = "CREATE TABLE IF NOT EXISTS megarax_dungeon_cooldowns (account_name VARCHAR(45) NOT NULL, dungeon_key VARCHAR(24) NOT NULL, available_at BIGINT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (account_name,dungeon_key), KEY available_at (available_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String COOLDOWN_SELECT_SQL = "SELECT available_at FROM megarax_dungeon_cooldowns WHERE account_name=? AND dungeon_key=? FOR UPDATE";
	private static final String COOLDOWN_INSERT_SQL = "INSERT INTO megarax_dungeon_cooldowns (account_name,dungeon_key,available_at) VALUES (?,?,?) ON DUPLICATE KEY UPDATE available_at=VALUES(available_at)";
	private static final String[][] RANKING_CATEGORIES =
	{
		{ "Daily", "Solo" },
		{ "Farm", "Waves" },
		{ "Party", "Group" },
		{ "Elite", "Green" },
		{ "Elite", "Blue" },
		{ "Elite", "Red" }
	};
	
	private static final int BLUE_WAVE_1_COUNT = 10;
	private static final int BLUE_WAVE_2_COUNT = 15;
	private static final int BLUE_WAVE_3_COUNT = 15;
	private static final int RED_WAVE_1_COUNT = 10;
	private static final int RED_WAVE_2_COUNT = 12;
	private static final int RED_WAVE_3_COUNT = 8;
	
	// ---------------------------------------------------------------
	// UBICACIONES
	// ---------------------------------------------------------------
	private static final Location ENTER_LOC      = new Location(189400, -85600, -7219); // Imperial Tomb - large sepulcher chamber
	private static final Location EXIT_LOC        = new Location( 83400, 147943, -3404);
	private static final Location FARM_ENTER_LOC  = new Location(174230,  -76168, -5134);
	
	// Entradas Elite
	private static final Location ELITE_GREEN_LOC = new Location(87473, -43306, -4159);   // Stakato Nest
	private static final Location ELITE_BLUE_LOC  = new Location(165054, -47861, -3560);  // Wall of Argos
	private static final Location ELITE_RED_LOC   = new Location(112451, -76760, 85);     // Monastery Holy Grail room, side entry
	
	// ---------------------------------------------------------------
	// DAILY - SPAWNS
	// ---------------------------------------------------------------
	private static final Location[] ANGEL_SPAWNS =
	{
		new Location(188850, -85600, -7170),
		new Location(188950, -85250, -7170),
		new Location(189250, -85080, -7170),
		new Location(189650, -85080, -7170),
		new Location(189950, -85250, -7170),
		new Location(190050, -85600, -7170),
		new Location(189950, -85950, -7170),
		new Location(189650, -86120, -7170),
		new Location(189250, -86120, -7170),
		new Location(188950, -85950, -7170),
	};
	
	private static final Location[] ANGEL_KILLER_SPAWNS =
	{
		new Location(189000, -85600, -7170),
		new Location(189200, -85280, -7170),
		new Location(189600, -85280, -7170),
		new Location(189800, -85600, -7170),
		new Location(189400, -85920, -7170),
	};
	
	private static final Location KARIK_SPAWN = new Location(189400, -85600, -7219);
	
	// ---------------------------------------------------------------
	// FARM DUNGEON - SPAWNS
	// ---------------------------------------------------------------
	private static final Location[] FARM_SPAWN_POOL =
	{
		new Location(174230, -75868, -5134),
		new Location(174230, -76468, -5134),
		new Location(173930, -76168, -5134),
		new Location(174530, -76168, -5134),
		new Location(173930, -75868, -5134),
		new Location(174530, -75868, -5134),
		new Location(173930, -76468, -5134),
		new Location(174530, -76468, -5134),
		new Location(173630, -76168, -5134),
		new Location(174830, -76168, -5134), // 10
		new Location(174230, -75568, -5134),
		new Location(174230, -76768, -5134),
		new Location(173630, -75568, -5134),
		new Location(174830, -75568, -5134),
		new Location(173630, -76768, -5134), // 15
		new Location(174830, -76768, -5134),
		new Location(173330, -75868, -5134),
		new Location(175130, -75868, -5134),
		new Location(173330, -76468, -5134),
		new Location(175130, -76468, -5134), // 20
		new Location(174230, -75268, -5134),
		new Location(174230, -77068, -5134),
		new Location(173330, -75568, -5134),
		new Location(175130, -76768, -5134),
		new Location(173330, -76768, -5134), // 25
	};
	
	private static final int[] FARM_WAVE_COUNT = { 10, 15, 20, 20, 20, 20, 20, 20, 20, 1 };
	private static final int[] PARTY_WAVE_COUNT = { 15, 20, 25, 25, 1 };
	
	private static final int[] FARM_WAVE_MOB =
	{
		FARM_MOB_1, // Oleada 1
		FARM_MOB_1, // Oleada 2
		FARM_MOB_1, // Oleada 3
		FARM_MOB_2, // Oleada 4
		FARM_MOB_2, // Oleada 5
		FARM_BORAK_MOB, // Oleada 6
		FARM_BORAK_MOB, // Oleada 7
		FARM_BORAK_MOB, // Oleada 8
		FARM_MOB_4, // Oleada 9
		FARM_BOSS,  // Oleada 10
	};
	private static final int[] PARTY_WAVE_MOB =
	{
		FARM_MOB_2,
		FARM_MOB_3,
		ELITE_GREEN_MOB_2,
		ELITE_BLUE_MOB_3,
		FARM_BOSS,
	};
	
	private static final Location FARM_BOSS_SPAWN = new Location(174230, -76168, -5134);
	
	// Progression economy:
	// Daily grants controlled Dungeon Coin, Farm scales mostly through Rax Coin,
	// Elite adds route-based difficulty rewards, and weekly ranking adds the large bonus layer.
	// This keeps casual progress steady while the highest payouts remain tied to performance.
	private static final long[][] FARM_WAVE_REWARDS =
	{
		{   0,   25,   0 }, // Oleada 1
		{   0,   35,   0 }, // Oleada 2
		{   0,   45,   0 }, // Oleada 3
		{   0,   55,   0 }, // Oleada 4
		{   0,   65,   5 }, // Oleada 5
		{   0,   75,  10 }, // Oleada 6
		{   0,   85,  15 }, // Oleada 7
		{   0,   95,  20 }, // Oleada 8
		{   0,  105,  30 }, // Oleada 9
		{   0,  120,  40 }, // Oleada 10
	};
	
	// ---------------------------------------------------------------
	// ELITE DUNGEON - SPAWNS
	// ---------------------------------------------------------------
	// Ruta Verde - 15 mobs spawn directo en Stakato Nest, sin aparecer encima del jugador.
	private static final Location[] ELITE_GREEN_SPAWNS =
	{
		new Location(87073, -43306, -4159),
		new Location(87873, -43306, -4159),
		new Location(87473, -42906, -4159),
		new Location(87473, -43706, -4159),
		new Location(86873, -42956, -4159),
		new Location(88073, -43656, -4159),
		new Location(86873, -43656, -4159),
		new Location(88073, -42956, -4159),
		new Location(86673, -43306, -4159),
		new Location(88273, -43306, -4159),
		new Location(87473, -42556, -4159),
		new Location(87473, -44056, -4159),
		new Location(86473, -42756, -4159),
		new Location(88473, -43856, -4159),
		new Location(88473, -42756, -4159),
	};
	private static final int[] ELITE_GREEN_MOB_PATTERN =
	{
		ELITE_GREEN_MOB_1,
		ELITE_GREEN_MOB_2,
		ELITE_GREEN_MOB_1,
		ELITE_GREEN_MOB_2,
		ELITE_GREEN_MOB_3,
		ELITE_GREEN_MOB_1,
		ELITE_GREEN_MOB_2,
		ELITE_GREEN_MOB_3,
		ELITE_GREEN_MOB_1,
		ELITE_GREEN_MOB_2,
		ELITE_GREEN_MOB_1,
		ELITE_GREEN_MOB_3,
		ELITE_GREEN_MOB_2,
		ELITE_GREEN_MOB_1,
		ELITE_GREEN_MOB_3,
	};
	
	// Ruta Azul - Oleadas: 1=10 Watchman, 2=15 Scout, 3=15 Supervisor
	private static final Location[] ELITE_BLUE_SPAWNS =
	{
		new Location(165054, -47861, -3560),
		new Location(164454, -47861, -3560),
		new Location(165654, -47861, -3560),
		new Location(165054, -47261, -3560),
		new Location(165054, -48461, -3560),
		new Location(164454, -47261, -3560),
		new Location(165654, -48461, -3560),
		new Location(164454, -48461, -3560),
		new Location(165654, -47261, -3560),
		new Location(164154, -47861, -3560), // 10
		new Location(165954, -47861, -3560),
		new Location(165054, -46961, -3560),
		new Location(165054, -48761, -3560),
		new Location(164154, -47261, -3560),
		new Location(165954, -48461, -3560), // 15
	};
	
	// Ruta Roja - Holy Grail room: 1=10 Protector, 2=12 Destroyer, 3=8 Messenger
	private static final Location[] ELITE_RED_SPAWNS =
	{
		new Location(112801, -76510, 85),
		new Location(112401, -76510, 85),
		new Location(113201, -76510, 85),
		new Location(112801, -76110, 85),
		new Location(112801, -76910, 85),
		new Location(112451, -76160, 85),
		new Location(113151, -76860, 85),
		new Location(112451, -76860, 85),
		new Location(113151, -76160, 85),
		new Location(112001, -76510, 85), // 10
		new Location(113601, -76510, 85),
		new Location(112801, -75710, 85), // 12
		new Location(112801, -77310, 85),
		new Location(112151, -75910, 85),
		new Location(113451, -77110, 85), // 15
	};
	
	// Boss spawns (centro de cada zona)
	private static final Location ELITE_GREEN_BOSS_SPAWN = new Location(87473, -43306, -4159);
	private static final Location ELITE_BLUE_BOSS_SPAWN  = new Location(165054, -47861, -3560);
	private static final Location ELITE_RED_BOSS_SPAWN   = new Location(112801, -76510, 85);
	
	// Recompensas Elite por ruta: { dungeonCoin, raxCoin, craftCoin }
	private static final int[] ELITE_GREEN_REWARD = {   0, 100,  0 };
	private static final int[] ELITE_BLUE_REWARD  = {   0, 250, 30 };
	private static final int[] ELITE_RED_REWARD   = {   0, 500, 60 };
	
	// Bonus por boss
	private static final int[] BOSS_LILITH_BONUS  = {   0, 100, 20 };
	private static final int[] BOSS_ANAKIM_BONUS   = {   0, 150, 25 };
	private static final int[] BOSS_RED_BONUS     = {   0, 200, 30 };
	
	// ---------------------------------------------------------------
	// MAPAS DE ESTADO
	// ---------------------------------------------------------------
	// Daily
	private static final Map<Integer, Integer> PLAYER_KILLS     = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> PLAYER_STAGE     = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> DAILY_EXPECTED_KILLS = new ConcurrentHashMap<>();
	private static final Map<Integer, Long>    PLAYER_COOLDOWNS = new ConcurrentHashMap<>();
	private static final Map<Integer, Long>    DAILY_START_TIME = new ConcurrentHashMap<>();
	
	// Farm
	private static final Map<Integer, Integer> FARM_WAVE      = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> FARM_KILLS     = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> FARM_EXPECTED_KILLS = new ConcurrentHashMap<>();
	private static final Map<Integer, Long>    FARM_COOLDOWNS = new ConcurrentHashMap<>();
	private static final Map<Integer, Long>    FARM_START_TIME = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> PARTY_WAVE = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> PARTY_KILLS = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> PARTY_EXPECTED_KILLS = new ConcurrentHashMap<>();
	private static final Map<Integer, Long>    PARTY_START_TIME = new ConcurrentHashMap<>();
	
	// Elite
	// Ruta: 1=Verde, 2=Azul, 3=Roja
	private static final Map<Integer, Integer> ELITE_ROUTE      = new ConcurrentHashMap<>();
	// Stage: 1=mobs oleada1, 2=mobs oleada2, 3=mobs oleada3, 4=boss
	private static final Map<Integer, Integer> ELITE_STAGE      = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> ELITE_KILLS      = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> ELITE_EXPECTED_KILLS = new ConcurrentHashMap<>();
	private static final Map<Integer, Long>    ELITE_START_TIME = new ConcurrentHashMap<>(); // Speed Run
	private static final Map<Integer, Integer> ELITE_BOSS_ID    = new ConcurrentHashMap<>(); // Boss aleatorio asignado
	private static final Map<Integer, Long>    ELITE_GREEN_CD   = new ConcurrentHashMap<>(); // Sin cooldown fijo
	private static final Map<Integer, Long>    ELITE_BLUE_CD    = new ConcurrentHashMap<>(); // 12h
	private static final Map<Integer, Long>    ELITE_RED_CD     = new ConcurrentHashMap<>(); // 24h
	private static final Map<Integer, Map<Integer, Npc>> DUNGEON_ACTIVE_MOBS = new ConcurrentHashMap<>();
	private static final Map<Integer, Map<Integer, Integer>> DUNGEON_ACTIVE_MOB_STAGES = new ConcurrentHashMap<>();
	private static final Map<Integer, Object> DUNGEON_LOCKS = new ConcurrentHashMap<>();
	private static final Map<String, Object> ACCOUNT_COOLDOWN_LOCKS = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> PARTY_MEMBER_TO_LEADER = new ConcurrentHashMap<>();
	private static final Map<Integer, Long> DUNGEON_PANEL_LAST_UPDATE = new ConcurrentHashMap<>();
	private static final Map<Integer, InstanceWorld> DUNGEON_INSTANCES = new ConcurrentHashMap<>();
	
	// ---------------------------------------------------------------
	private MegaraxDungeon()
	{
		addStartNpc(DUNGEON_MANAGER);
		addTalkId(DUNGEON_MANAGER);
		addFirstTalkId(DUNGEON_MANAGER);
		
		// Daily
		addKillId(ANGEL, ANGEL_KILLER, KARIK);
		// Farm
		addKillId(FARM_MOB_1, FARM_MOB_2, FARM_MOB_3, FARM_BORAK_MOB, FARM_MOB_4, FARM_BOSS);
		// Elite mobs
		addKillId(ELITE_GREEN_MOB_1, ELITE_GREEN_MOB_2, ELITE_GREEN_MOB_3);
		addKillId(ELITE_BLUE_MOB_1, ELITE_BLUE_MOB_2, ELITE_BLUE_MOB_3);
		addKillId(ELITE_RED_MOB_1, ELITE_RED_MOB_2, ELITE_RED_MOB_3);
		// Elite bosses
		addKillId(BOSS_LILITH, BOSS_ANAKIM, ELITE_RED_BOSS);
		// Death detection removed: this script only tracks NPC kills.
		initRankingTable();
		initCooldownTable();
	}
	
	@Override
	public String onFirstTalk(Npc npc, Player player)
	{
		return "1004000.htm";
	}
	
	@Override
	public String onTalk(Npc npc, Player player)
	{
		return "1004000.htm";
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		if (player == null)
		{
			return null;
		}
		
		// ---- DAILY ----
		if (event.equals("enter_daily"))       return enterDailyDungeon(player);
		if (event.equals("leave_daily"))
		{
			cancelQuestTimer("finish_daily_" + player.getObjectId(), null, player);
			cleanDailyPlayer(player.getObjectId());
			player.sendMessage("You have left the Daily Dungeon.");
			player.teleToLocation(EXIT_LOC);
			return null;
		}
		
		// ---- FARM ----
		if (event.equals("enter_farm"))        return enterFarmDungeon(player);
		if (event.equals("leave_farm"))
		{
			cancelQuestTimer("finish_farm_" + player.getObjectId(), null, player);
			cleanFarmPlayer(player.getObjectId());
			player.sendMessage("You have left the Farm Dungeon.");
			player.teleToLocation(EXIT_LOC);
			return null;
		}
		if (event.equals("party_info"))       { showPartyDungeon(player); return null; }
		if (event.equals("enter_party"))      return enterPartyDungeon(player);
		if (event.equals("leave_party"))
		{
			final int leaderId = PARTY_MEMBER_TO_LEADER.getOrDefault(player.getObjectId(), player.getObjectId());
			final Player leader = getPlayerByObjectId(leaderId);
			cancelQuestTimer("finish_party_" + leaderId, null, leader != null ? leader : player);
			teleportPartyOut(leaderId, "You have left the Party Dungeon.");
			if (leader == null)
			{
				player.teleToLocation(EXIT_LOC);
			}
			cleanPartyPlayer(leaderId);
			return null;
		}
		
		// ---- ELITE ----
		if (event.equals("enter_elite_green")) return enterEliteDungeon(player, 1);
		if (event.equals("enter_elite_blue"))  return enterEliteDungeon(player, 2);
		if (event.equals("enter_elite_red"))   return enterEliteDungeon(player, 3);
		if (event.equals("leave_elite"))
		{
			cancelQuestTimer("finish_elite_" + player.getObjectId(), null, player);
			cleanElitePlayer(player.getObjectId());
			player.sendMessage("You have left the Elite Dungeon.");
			player.teleToLocation(EXIT_LOC);
			return null;
		}
		
		// ---- MENUS PENDIENTES ----
		if (event.equals("elite_info"))      return "1004000-elite.htm";
		if (event.equals("back"))            return "1004000.htm";
		if (event.equals("nightmare_info"))  { player.sendMessage("Nightmare Dungeon is coming soon."); return "1004000.htm"; }
		if (event.equals("ranking_info"))    { showRanking(player); return null; }
		if (event.equals("claim_ranking_rewards")) { claimWeeklyRankingRewards(player); return null; }
		
		// ---- TIMERS ----
		if (event.startsWith("finish_daily_"))
		{
			final int objectId = Integer.parseInt(event.substring(13));
			cleanDailyPlayer(objectId);
			final Player p = getPlayerByObjectId(objectId);
			if (p != null) { p.sendMessage("Daily Dungeon time is over."); p.teleToLocation(EXIT_LOC); }
			return null;
		}
		if (event.startsWith("finish_farm_"))
		{
			final int objectId = Integer.parseInt(event.substring(12));
			cleanFarmPlayer(objectId);
			final Player p = getPlayerByObjectId(objectId);
			if (p != null) { p.sendMessage("Farm Dungeon time is over."); p.teleToLocation(EXIT_LOC); }
			return null;
		}
		if (event.startsWith("finish_party_"))
		{
			final int objectId = Integer.parseInt(event.substring(13));
			teleportPartyOut(objectId, "Party Dungeon time is over.");
			cleanPartyPlayer(objectId);
			return null;
		}
		if (event.startsWith("finish_elite_"))
		{
			final int objectId = Integer.parseInt(event.substring(13));
			cleanElitePlayer(objectId);
			final Player p = getPlayerByObjectId(objectId);
			if (p != null) { p.sendMessage("Elite Dungeon time is over."); p.teleToLocation(EXIT_LOC); }
			return null;
		}
		if (event.startsWith("exit_after_clear_"))
		{
			final int objectId = Integer.parseInt(event.substring("exit_after_clear_".length()));
			final Player p = getPlayerByObjectId(objectId);
			if (p != null)
			{
				p.teleToLocation(EXIT_LOC);
			}
			return null;
		}
		if (event.startsWith("party_exit_after_clear_"))
		{
			final int objectId = Integer.parseInt(event.substring("party_exit_after_clear_".length()));
			teleportPartyOut(objectId, "Party Dungeon Complete!");
			return null;
		}
		return "1004000.htm";
	}
	
	// ================================================================
	// DAILY DUNGEON
	// ================================================================
	private String enterDailyDungeon(Player player)
	{
		if (player.getLevel() < MIN_LEVEL)
		{
			player.sendMessage("You need level " + MIN_LEVEL + " to enter.");
			return "1004000.htm";
		}
		final long now = System.currentTimeMillis();
		final long reuse = acquireAccountCooldown(player, "DAILY", COOLDOWN_24H);
		if (reuse > 0)
		{
			final long minutes = Math.max(1, reuse / 60000);
			player.sendMessage("Daily Dungeon available in " + minutes + " minute(s).");
			return "1004000.htm";
		}
		final int instanceId = createPrivateInstance(player.getObjectId(), Collections.singleton(player));
		if (instanceId == 0)
		{
			player.sendMessage("The private dungeon could not be created. Please try again.");
			return "1004000.htm";
		}
		player.teleToLocation(ENTER_LOC, instanceId, 30);
		player.sendMessage("=== Megarax Daily Dungeon ===");
		PLAYER_STAGE.put(player.getObjectId(), 1);
		PLAYER_KILLS.put(player.getObjectId(), 0);
		DAILY_START_TIME.put(player.getObjectId(), now);
		final int spawned = spawnDailyMobs(player.getObjectId(), ANGEL, ANGEL_SPAWNS);
		DAILY_EXPECTED_KILLS.put(player.getObjectId(), spawned);
		player.sendMessage("Stage 1 Started! Defeat all Angels. (0/" + spawned + ")");
		startQuestTimer("finish_daily_" + player.getObjectId(), DUNGEON_TIME, null, player);
		showDailyPanel(player, true, "In Progress");
		return null;
	}
	
	private int spawnDailyMobs(int playerId, int npcId, Location[] locations)
	{
		resetActiveMobs(playerId);
		int spawned = 0;
		for (Location loc : locations)
		{
			final Npc mob = spawnOwnedMob(playerId, npcId, loc, DUNGEON_TIME);
			if (mob != null)
			{
				spawned++;
			}
		}
		return spawned;
	}
	
	private int spawnDailyMob(int playerId, int npcId, Location location)
	{
		resetActiveMobs(playerId);
		return spawnOwnedMob(playerId, npcId, location, DUNGEON_TIME) != null ? 1 : 0;
	}
	
	// ================================================================
	// FARM DUNGEON
	// ================================================================
	private String enterFarmDungeon(Player player)
	{
		if (player.getLevel() < MIN_LEVEL)
		{
			player.sendMessage("You need level " + MIN_LEVEL + " to enter.");
			return "1004000.htm";
		}
		final long now = System.currentTimeMillis();
		final long reuse = acquireAccountCooldown(player, "FARM", COOLDOWN_24H);
		if (reuse > 0)
		{
			final long minutes = Math.max(1, reuse / 60000);
			player.sendMessage("Farm Dungeon available in " + minutes + " minute(s).");
			return "1004000.htm";
		}
		final int instanceId = createPrivateInstance(player.getObjectId(), Collections.singleton(player));
		if (instanceId == 0)
		{
			player.sendMessage("The private dungeon could not be created. Please try again.");
			return "1004000.htm";
		}
		player.teleToLocation(FARM_ENTER_LOC, instanceId, 30);
		FARM_WAVE.put(player.getObjectId(), 0);
		FARM_KILLS.put(player.getObjectId(), 0);
		FARM_START_TIME.put(player.getObjectId(), now);
		startQuestTimer("finish_farm_" + player.getObjectId(), FARM_TIME, null, player);
		spawnFarmWave(player, 0);
		return null;
	}
	
	private void spawnFarmWave(Player player, int waveIndex)
	{
		final int waveNumber = waveIndex + 1;
		final int mobId = FARM_WAVE_MOB[waveIndex];
		final int count = FARM_WAVE_COUNT[waveIndex];
		FARM_KILLS.put(player.getObjectId(), 0);
		resetActiveMobs(player.getObjectId());
		int spawned = 0;
		if (waveIndex == 9)
		{
			player.sendPacket(new ExShowScreenMessage("You have proven your strength... Now face the true challenge. Sandramanaia awaits!", 3000));
			player.sendMessage("=== Wave 10/10 - BOSS WAVE! ===");
			final Npc boss = spawnOwnedMob(player.getObjectId(), mobId, FARM_BOSS_SPAWN, FARM_TIME);
			if (boss != null)
			{
				spawned++;
			}
		}
		else
		{
			player.sendPacket(new ExShowScreenMessage("=== Wave " + waveNumber + "/10 Started! Defeat " + count + " enemies! ===", 3000));
			player.sendMessage("Wave " + waveNumber + "/10 | Defeat " + count + " enemies.");
			for (int i = 0; (i < count) && (i < FARM_SPAWN_POOL.length); i++)
			{
				final Npc mob = spawnOwnedMob(player.getObjectId(), mobId, FARM_SPAWN_POOL[i], FARM_TIME);
				if (mob != null)
				{
					spawned++;
				}
			}
		}
		FARM_EXPECTED_KILLS.put(player.getObjectId(), spawned);
		if (spawned != count)
		{
			player.sendMessage("Wave adjusted: " + spawned + " enemy spawn(s) detected.");
		}
		showFarmPanel(player, true, (waveIndex == 9) ? "Boss Stage" : "In Progress");
	}
	
	private void giveFarmWaveReward(Player player, int waveIndex)
	{
		final long[] reward    = FARM_WAVE_REWARDS[waveIndex];
		final int dungeonCoins = (int) reward[0];
		final int raxCoins     = (int) reward[1];
		final int craftCoins   = (int) reward[2];
		if (dungeonCoins > 0) rewardDungeonCoin(player, dungeonCoins);
		giveItems(player, RAX_COIN,     raxCoins);
		if (craftCoins > 0) giveItems(player, CRAFT_COIN, craftCoins);
		MegaraxSevenSigns.rewardDungeonSealBonus(player, raxCoins, craftCoins);
		final int waveNumber = waveIndex + 1;
		player.sendMessage("--- Wave " + waveNumber + " Rewards ---");
		if (dungeonCoins > 0) player.sendMessage("Dungeon Coins: +" + dungeonCoins);
		player.sendMessage("Rax Coins: +" + raxCoins);
		if (craftCoins > 0) player.sendMessage("Craft Coins: +" + craftCoins);
	}
	
	// ================================================================
	// PARTY DUNGEON
	// ================================================================
	private void showPartyDungeon(Player player)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Megarax Party Dungeon</title><body><center>");
		html.append("<button value=\"Party Dungeon\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\"><tr><td align=center>");
		html.append("<font color=\"0099FF\">5 wave party challenge</font><br1>");
		html.append("<font color=\"ae9977\">Recommended: Tank, Healer, Support and DPS.</font><br1>");
		html.append("<font color=\"LEVEL\">Minimum: 2 party members / Level 76+</font>");
		html.append("</td></tr></table><br>");
		html.append("<table width=292 bgcolor=\"000000\"><tr><td>");
		html.append("<font color=\"ae9977\">Rewards: Dungeon Coin per mob, Rax Coin and Craft Coin per wave. Completing this dungeon counts for Daily Dungeon and Mercenary Dungeon contracts.</font>");
		html.append("</td></tr></table><br>");
		html.append("<button value=\"Enter\" action=\"bypass -h Quest MegaraxDungeon enter_party\" width=92 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"><br1>");
		html.append("<button value=\"Back\" action=\"bypass -h Quest MegaraxDungeon back\" width=92 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		html.append("</center></body></html>");
		
		final NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html.toString());
		player.sendPacket(msg);
	}
	
	private String enterPartyDungeon(Player player)
	{
		if (player.getLevel() < MIN_LEVEL)
		{
			player.sendMessage("You need level " + MIN_LEVEL + " to enter.");
			return null;
		}
		if (!player.isInParty())
		{
			player.sendMessage("You need a party to enter Party Dungeon.");
			return null;
		}
		
		final Party party = player.getParty();
		if ((party == null) || (party.getLeader() == null) || (party.getLeader().getObjectId() != player.getObjectId()))
		{
			player.sendMessage("Only the party leader can start Party Dungeon.");
			return null;
		}
		if (party.getMembers().size() < 2)
		{
			player.sendMessage("Party Dungeon requires at least 2 party members.");
			return null;
		}
		final Set<String> partyAccounts = new HashSet<>();
		for (Player member : party.getMembers())
		{
			if ((member == null) || (member.getLevel() < MIN_LEVEL))
			{
				player.sendMessage("Every party member must be online and level " + MIN_LEVEL + "+.");
				return null;
			}
			if (!partyAccounts.add(member.getAccountName().toLowerCase(Locale.ROOT)))
			{
				player.sendMessage("Party Dungeon allows only one character per account.");
				return null;
			}
			final long reuse = getAccountCooldown(member, "PARTY");
			if (reuse > 0)
			{
				player.sendMessage(member.getName() + " has Party Dungeon cooldown for " + Math.max(1, reuse / 60000) + " minute(s).");
				return null;
			}
		}
		for (Player member : party.getMembers())
		{
			final long reuse = acquireAccountCooldown(member, "PARTY", COOLDOWN_24H);
			if (reuse > 0)
			{
				player.sendMessage("Party entry could not reserve all account cooldowns. Try again.");
				return null;
			}
		}
		
		final long now = System.currentTimeMillis();
		final Set<Player> instanceMembers = new HashSet<>();
		for (Player member : party.getMembers())
		{
			if (member != null)
			{
				instanceMembers.add(member);
			}
		}
		final int instanceId = createPrivateInstance(player.getObjectId(), instanceMembers);
		if (instanceId == 0)
		{
			player.sendMessage("The private party dungeon could not be created. Please try again.");
			return null;
		}
		PARTY_WAVE.put(player.getObjectId(), 0);
		PARTY_KILLS.put(player.getObjectId(), 0);
		PARTY_START_TIME.put(player.getObjectId(), now);
		PARTY_MEMBER_TO_LEADER.put(player.getObjectId(), player.getObjectId());
		for (Player member : party.getMembers())
		{
			if ((member != null) && (member.getLevel() >= MIN_LEVEL))
			{
				PARTY_MEMBER_TO_LEADER.put(member.getObjectId(), player.getObjectId());
				member.teleToLocation(FARM_ENTER_LOC, instanceId, 30);
				member.sendMessage("=== Megarax Party Dungeon Started ===");
			}
		}
		startQuestTimer("finish_party_" + player.getObjectId(), PARTY_TIME, null, player);
		spawnPartyWave(player, 0);
		return null;
	}
	
	private void spawnPartyWave(Player leader, int waveIndex)
	{
		final int waveNumber = waveIndex + 1;
		final int mobId = PARTY_WAVE_MOB[waveIndex];
		final int count = PARTY_WAVE_COUNT[waveIndex];
		PARTY_KILLS.put(leader.getObjectId(), 0);
		resetActiveMobs(leader.getObjectId());
		int spawned = 0;
		if (waveIndex == (PARTY_WAVE_MOB.length - 1))
		{
			announceParty(leader, "Party Dungeon: Final boss wave!");
			final Npc boss = spawnOwnedMob(leader.getObjectId(), mobId, FARM_BOSS_SPAWN, PARTY_TIME);
			if (boss != null)
			{
				spawned++;
			}
		}
		else
		{
			announceParty(leader, "Party Dungeon: Wave " + waveNumber + "/5 started.");
			for (int i = 0; (i < count) && (i < FARM_SPAWN_POOL.length); i++)
			{
				final Npc mob = spawnOwnedMob(leader.getObjectId(), mobId, FARM_SPAWN_POOL[i], PARTY_TIME);
				if (mob != null)
				{
					spawned++;
				}
			}
		}
		PARTY_EXPECTED_KILLS.put(leader.getObjectId(), spawned);
		if (spawned != count)
		{
			announceParty(leader, "Party wave adjusted: " + spawned + " enemy spawn(s) detected.");
		}
		showPartyPanel(leader, true, (waveIndex == (PARTY_WAVE_MOB.length - 1)) ? "Boss Stage" : "In Progress");
	}
	
	private void handlePartyKill(Npc npc, Player killer, int leaderId)
	{
		final int waveIndex = PARTY_WAVE.get(leaderId);
		if (npc.getId() != PARTY_WAVE_MOB[waveIndex])
		{
			return;
		}
		if (!consumeDungeonKill(npc, leaderId))
		{
			return;
		}
		
		rewardDungeonCoin(killer, waveIndex >= 3 ? 2 : 1);
		final int kills = PARTY_KILLS.getOrDefault(leaderId, 0) + 1;
		PARTY_KILLS.put(leaderId, kills);
		final int totalInWave = PARTY_EXPECTED_KILLS.getOrDefault(leaderId, PARTY_WAVE_COUNT[waveIndex]);
		final Player leader = getPlayerByObjectId(leaderId);
		if (leader != null)
		{
			announceParty(leader, "Party Dungeon wave " + (waveIndex + 1) + ": " + kills + "/" + totalInWave);
			showPartyPanel(leader, false, "In Progress");
		}
		
		if (kills < totalInWave)
		{
			return;
		}
		
		if (leader != null)
		{
			rewardPartyWave(leader, waveIndex);
			final int nextWave = waveIndex + 1;
			if (nextWave >= PARTY_WAVE_MOB.length)
			{
				recordDungeonRanking(leader, "Party", "Group", PARTY_START_TIME.getOrDefault(leaderId, System.currentTimeMillis()));
				rewardPartyCompletion(leader);
				cancelQuestTimer("finish_party_" + leaderId, null, leader);
				showPartyPanel(leader, true, "Completed");
				cleanPartyPlayer(leaderId);
				schedulePartyCompletionExit(leader, leaderId);
			}
			else
			{
				PARTY_WAVE.put(leaderId, nextWave);
				spawnPartyWave(leader, nextWave);
			}
		}
	}
	
	private void rewardPartyWave(Player leader, int waveIndex)
	{
		for (Player member : leader.getParty().getMembers())
		{
			if (member == null)
			{
				continue;
			}
			giveItems(member, RAX_COIN, 30 + (waveIndex * 10));
			giveItems(member, CRAFT_COIN, 5 + (waveIndex * 5));
			member.sendMessage("Party Dungeon wave reward received.");
		}
	}
	
	private void rewardPartyCompletion(Player leader)
	{
		for (Player member : leader.getParty().getMembers())
		{
			if (member == null)
			{
				continue;
			}
			rewardDungeonCoin(member, 80);
			giveItems(member, RAX_COIN, 100);
			MegaraxDailyMissions.notifyDungeonComplete(member);
			DelevelManager.notifyDungeonComplete(member);
			recordDungeonChronicle(member, "Party", "Group");
		}
	}
	
	private void announceParty(Player leader, String message)
	{
		if ((leader == null) || !leader.isInParty())
		{
			return;
		}
		for (Player member : leader.getParty().getMembers())
		{
			if (member != null)
			{
				member.sendMessage(message);
			}
		}
	}
	
	private void schedulePartyCompletionExit(Player leader, int leaderId)
	{
		announceParty(leader, "Party Dungeon Complete! You have 15 seconds to collect drops before exit.");
		startQuestTimer("party_exit_after_clear_" + leaderId, COMPLETION_TELEPORT_DELAY, null, leader);
	}
	
	private void teleportPartyOut(int leaderId, String message)
	{
		final Player leader = getPlayerByObjectId(leaderId);
		if ((leader != null) && leader.isInParty())
		{
			for (Player member : leader.getParty().getMembers())
			{
				if (member != null)
				{
					member.sendMessage(message);
					member.teleToLocation(EXIT_LOC);
				}
			}
		}
		else if (leader != null)
		{
			leader.sendMessage(message);
			leader.teleToLocation(EXIT_LOC);
		}
	}
	
	private void cleanPartyPlayer(int objectId)
	{
		final Player leader = getPlayerByObjectId(objectId);
		if ((leader != null) && leader.isInParty())
		{
			for (Player member : leader.getParty().getMembers())
			{
				if (member != null)
				{
					PARTY_MEMBER_TO_LEADER.remove(member.getObjectId());
					DUNGEON_PANEL_LAST_UPDATE.remove(member.getObjectId());
				}
			}
		}
		PARTY_MEMBER_TO_LEADER.entrySet().removeIf(entry -> entry.getValue() == objectId);
		PARTY_WAVE.remove(objectId);
		PARTY_KILLS.remove(objectId);
		PARTY_EXPECTED_KILLS.remove(objectId);
		PARTY_START_TIME.remove(objectId);
		clearDungeonTracking(objectId);
	}
	
	private void cleanDailyPlayer(int objectId)
	{
		PLAYER_KILLS.remove(objectId);
		PLAYER_STAGE.remove(objectId);
		DAILY_EXPECTED_KILLS.remove(objectId);
		DAILY_START_TIME.remove(objectId);
		clearDungeonTracking(objectId);
	}
	
	private void cleanFarmPlayer(int objectId)
	{
		FARM_WAVE.remove(objectId);
		FARM_KILLS.remove(objectId);
		FARM_EXPECTED_KILLS.remove(objectId);
		FARM_START_TIME.remove(objectId);
		clearDungeonTracking(objectId);
	}
	
	// ================================================================
	// ELITE DUNGEON
	// ================================================================
	private String enterEliteDungeon(Player player, int route)
	{
		if (player.getLevel() < MIN_LEVEL)
		{
			player.sendMessage("You need level " + MIN_LEVEL + " to enter.");
			return "1004000-elite.htm";
		}
		
		final long now = System.currentTimeMillis();
		
		final String routeKey = (route == 1) ? "ELITE_GREEN" : (route == 2) ? "ELITE_BLUE" : "ELITE_RED";
		final long routeCooldown = (route == 1) ? COOLDOWN_6H : (route == 2) ? COOLDOWN_12H : COOLDOWN_24H;
		final long reuse = acquireAccountCooldown(player, routeKey, routeCooldown);
		if (reuse > 0)
		{
			player.sendMessage("This Elite route is available in " + Math.max(1, reuse / 60000) + " minute(s).");
			return "1004000-elite.htm";
		}
		
		// Green/Blue keep the classic random raid boss. Red now ends with Borak.
		final int bossId = (route == 3) ? ELITE_RED_BOSS : (RANDOM.nextBoolean() ? BOSS_LILITH : BOSS_ANAKIM);
		ELITE_BOSS_ID.put(player.getObjectId(), bossId);
		
		// Guardar estado
		ELITE_ROUTE.put(player.getObjectId(), route);
		ELITE_STAGE.put(player.getObjectId(), 1);
		ELITE_KILLS.put(player.getObjectId(), 0);
		ELITE_START_TIME.put(player.getObjectId(), now);
		final int instanceId = createPrivateInstance(player.getObjectId(), Collections.singleton(player));
		if (instanceId == 0)
		{
			player.sendMessage("The private Elite Dungeon could not be created. Please try again.");
			cleanElitePlayer(player.getObjectId());
			return "1004000-elite.htm";
		}
		resetActiveMobs(player.getObjectId());
		
		// Timer de dungeon
		startQuestTimer("finish_elite_" + player.getObjectId(), ELITE_TIME, null, player);
		
		// Teletransportar y arrancar
		switch (route)
		{
			case 1: // Verde
			{
				player.teleToLocation(ELITE_GREEN_LOC, instanceId, 30);
				player.sendPacket(new ExShowScreenMessage("Green Route - Stakato Nest! Defeat all enemies!", 3000));
				player.sendMessage("=== Elite Dungeon - Green Route ===");
				player.sendMessage("Defeat all " + ELITE_GREEN_SPAWNS.length + " enemies. No resurrections allowed!");
				int spawned = 0;
				for (int i = 0; i < ELITE_GREEN_SPAWNS.length; i++)
				{
					final int mobId = ELITE_GREEN_MOB_PATTERN[i];
					if (spawnOwnedMob(player.getObjectId(), mobId, ELITE_GREEN_SPAWNS[i], ELITE_TIME) != null)
					{
						spawned++;
					}
				}
				ELITE_EXPECTED_KILLS.put(player.getObjectId(), spawned);
				break;
			}
			case 2: // Azul
			{
				player.teleToLocation(ELITE_BLUE_LOC, instanceId, 30);
				player.sendPacket(new ExShowScreenMessage("Blue Route - Wall of Argos! Wave 1/3 Begins!", 3000));
				player.sendMessage("=== Elite Dungeon - Blue Route ===");
				player.sendMessage("Wave 1/3 - Defeat " + BLUE_WAVE_1_COUNT + " Argos Watchmen!");
				int spawned = 0;
				for (int i = 0; i < BLUE_WAVE_1_COUNT; i++)
				{
					if (spawnOwnedMob(player.getObjectId(), ELITE_BLUE_MOB_1, ELITE_BLUE_SPAWNS[i], ELITE_TIME) != null)
					{
						spawned++;
					}
				}
				ELITE_EXPECTED_KILLS.put(player.getObjectId(), spawned);
				break;
			}
			case 3: // Roja
			{
				player.teleToLocation(ELITE_RED_LOC, instanceId, 30);
				player.sendPacket(new ExShowScreenMessage("Red Route - Monastery Secret Hall! Defeat all enemies!", 3000));
				player.sendMessage("=== Elite Dungeon - Red Route ===");
				player.sendMessage("Wave 1/3 - Defeat " + RED_WAVE_1_COUNT + " Grail Protectors!");
				int spawned = 0;
				for (int i = 0; i < RED_WAVE_1_COUNT; i++)
				{
					if (spawnOwnedMob(player.getObjectId(), ELITE_RED_MOB_1, ELITE_RED_SPAWNS[i], ELITE_TIME) != null)
					{
						spawned++;
					}
				}
				ELITE_EXPECTED_KILLS.put(player.getObjectId(), spawned);
				break;
			}
		}
		
		showElitePanel(player, true, "In Progress");
		return null;
	}
	
	private void giveEliteReward(Player player, int route)
	{
		final int[] baseReward = (route == 1) ? ELITE_GREEN_REWARD : (route == 2) ? ELITE_BLUE_REWARD : ELITE_RED_REWARD;
		final int bossId = ELITE_BOSS_ID.getOrDefault(player.getObjectId(), BOSS_LILITH);
		final int[] bossBonus = (bossId == ELITE_RED_BOSS) ? BOSS_RED_BONUS : (bossId == BOSS_LILITH) ? BOSS_LILITH_BONUS : BOSS_ANAKIM_BONUS;
		
		final int dungeonCoins = baseReward[0] + bossBonus[0];
		final int raxCoins     = baseReward[1] + bossBonus[1];
		final int craftCoins   = baseReward[2] + bossBonus[2];
		
		if (dungeonCoins > 0) rewardDungeonCoin(player, dungeonCoins);
		giveItems(player, RAX_COIN,     raxCoins);
		if (craftCoins > 0) giveItems(player, CRAFT_COIN, craftCoins);
		MegaraxSevenSigns.rewardDungeonSealBonus(player, raxCoins, craftCoins);
		
		// Mostrar tiempo de speed run
		final long startTime = ELITE_START_TIME.getOrDefault(player.getObjectId(), System.currentTimeMillis());
		final long elapsed   = (System.currentTimeMillis() - startTime) / 1000;
		final long minutes   = elapsed / 60;
		final long seconds   = elapsed % 60;
		final String bossName = (bossId == ELITE_RED_BOSS) ? "Borak" : (bossId == BOSS_LILITH) ? "Lilith" : "Anakim";
		recordDungeonRanking(player, "Elite", getEliteRouteName(route), startTime);
		
		player.sendPacket(new ExShowScreenMessage("Elite Dungeon Complete! Time: " + minutes + "m " + seconds + "s", 4000));
		player.sendMessage("=== Elite Dungeon Complete! ===");
		player.sendMessage("Boss Defeated: " + bossName);
		player.sendMessage("Your Time: " + minutes + "m " + seconds + "s");
		if (dungeonCoins > 0) player.sendMessage("Dungeon Coins: +" + dungeonCoins);
		player.sendMessage("Rax Coins: +" + raxCoins);
		if (craftCoins > 0) player.sendMessage("Craft Coins: +" + craftCoins);
	}
	
	private void cleanElitePlayer(int objectId)
	{
		ELITE_ROUTE.remove(objectId);
		ELITE_STAGE.remove(objectId);
		ELITE_KILLS.remove(objectId);
		ELITE_EXPECTED_KILLS.remove(objectId);
		ELITE_START_TIME.remove(objectId);
		ELITE_BOSS_ID.remove(objectId);
		clearDungeonTracking(objectId);
	}
	
	// ================================================================
	// ON KILL
	// ================================================================
	@Override
	public void onKill(Npc npc, Player killer, boolean isSummon)
	{
		if (npc == null)
		{
			return;
		}
		
		final int ownerId = npc.getScriptValue();
		if (ownerId > 0)
		{
			synchronized (getDungeonLock(ownerId))
			{
				if (PARTY_WAVE.containsKey(ownerId))
				{
					handlePartyKill(npc, killer, ownerId);
					return;
				}
				
				final Player owner = getPlayerByObjectId(ownerId);
				if (owner == null)
				{
					return;
				}
				if (PLAYER_STAGE.containsKey(ownerId))
				{
					handleDailyKill(npc, owner);
					return;
				}
				if (FARM_WAVE.containsKey(ownerId))
				{
					handleFarmKill(npc, owner);
					return;
				}
				if (ELITE_ROUTE.containsKey(ownerId))
				{
					handleEliteKill(npc, owner);
					return;
				}
			}
		}
		
		if (killer == null)
		{
			return;
		}
		final int killerId = killer.getObjectId();
		synchronized (getDungeonLock(killerId))
		{
			if (PLAYER_STAGE.containsKey(killerId))
			{
				handleDailyKill(npc, killer);
			}
			if (FARM_WAVE.containsKey(killerId))
			{
				handleFarmKill(npc, killer);
			}
			if (ELITE_ROUTE.containsKey(killerId))
			{
				handleEliteKill(npc, killer);
			}
		}
	}
	
	private void handleDailyKill(Npc npc, Player killer)
	{
		final int playerId = killer.getObjectId();
		final int stage = PLAYER_STAGE.get(playerId);
		
		if ((stage == 1) && (npc.getId() == ANGEL))
		{
			if (!consumeDungeonKill(npc, playerId))
			{
				return;
			}
			rewardDungeonCoin(killer, DAILY_ANGEL_COIN);
			final int kills = PLAYER_KILLS.getOrDefault(playerId, 0) + 1;
			PLAYER_KILLS.put(playerId, kills);
			final int totalKills = DAILY_EXPECTED_KILLS.getOrDefault(playerId, ANGEL_SPAWNS.length);
			killer.sendMessage("Angels: " + kills + "/" + totalKills);
			if (kills >= totalKills)
			{
				killer.sendMessage("Stage 1 Cleared!");
				PLAYER_STAGE.put(playerId, 2);
				PLAYER_KILLS.put(playerId, 0);
				final int spawned = spawnDailyMobs(playerId, ANGEL_KILLER, ANGEL_KILLER_SPAWNS);
				DAILY_EXPECTED_KILLS.put(playerId, spawned);
				killer.sendMessage("Stage 2 Started! Angel Killers have appeared. (0/" + spawned + ")");
				showDailyPanel(killer, true, "Stage Cleared");
			}
			else
			{
				showDailyPanel(killer, false, "In Progress");
			}
			return;
		}
		if ((stage == 2) && (npc.getId() == ANGEL_KILLER))
		{
			if (!consumeDungeonKill(npc, playerId))
			{
				return;
			}
			rewardDungeonCoin(killer, DAILY_ANGEL_KILLER_COIN);
			final int kills = PLAYER_KILLS.getOrDefault(playerId, 0) + 1;
			PLAYER_KILLS.put(playerId, kills);
			final int totalKills = DAILY_EXPECTED_KILLS.getOrDefault(playerId, ANGEL_KILLER_SPAWNS.length);
			killer.sendMessage("Angel Killers: " + kills + "/" + totalKills);
			if (kills >= totalKills)
			{
				killer.sendMessage("Stage 2 Cleared!");
				killer.sendMessage("The Karik has awakened!");
				PLAYER_STAGE.put(playerId, 3);
				PLAYER_KILLS.put(playerId, 0);
				DAILY_EXPECTED_KILLS.put(playerId, spawnDailyMob(playerId, KARIK, KARIK_SPAWN));
				showDailyPanel(killer, true, "Boss Stage");
			}
			else
			{
				showDailyPanel(killer, false, "In Progress");
			}
			return;
		}
		if ((stage == 3) && (npc.getId() == KARIK))
		{
			if (!consumeDungeonKill(npc, playerId))
			{
				return;
			}
			rewardDungeonCoin(killer, DAILY_GUARDIAN_COIN);
			recordDungeonRanking(killer, "Daily", "Solo", DAILY_START_TIME.getOrDefault(playerId, System.currentTimeMillis()));
			MegaraxDailyMissions.notifyDungeonComplete(killer);
			DelevelManager.notifyDungeonComplete(killer);
			killer.sendMessage("=== Daily Dungeon Complete! ===");
			killer.sendMessage("Daily reward complete. Total possible reward: 70 Dungeon Coin.");
			showDailyPanel(killer, true, "Completed");
			cleanDailyPlayer(playerId);
			cancelQuestTimer("finish_daily_" + playerId, null, killer);
			scheduleCompletionExit(killer, "Daily Dungeon Complete!");
		}
	}
	
	private void handleFarmKill(Npc npc, Player killer)
	{
		final int playerId = killer.getObjectId();
		final int waveIndex  = FARM_WAVE.get(playerId);
		final int expectedId = FARM_WAVE_MOB[waveIndex];
		if (npc.getId() != expectedId) return;
		if (!consumeDungeonKill(npc, playerId))
		{
			return;
		}
		
		final int totalInWave = FARM_EXPECTED_KILLS.getOrDefault(playerId, FARM_WAVE_COUNT[waveIndex]);
		rewardDungeonCoin(killer, getFarmDungeonCoinReward(waveIndex));
		int kills = FARM_KILLS.getOrDefault(playerId, 0) + 1;
		FARM_KILLS.put(playerId, kills);
		
		if (waveIndex < 9) killer.sendMessage("Wave " + (waveIndex + 1) + ": " + kills + "/" + totalInWave);
		
		if (kills >= totalInWave)
		{
			giveFarmWaveReward(killer, waveIndex);
			final int nextWave = waveIndex + 1;
			if (nextWave >= FARM_WAVE_MOB.length)
			{
				recordDungeonRanking(killer, "Farm", "Waves", FARM_START_TIME.getOrDefault(playerId, System.currentTimeMillis()));
				MegaraxDailyMissions.notifyDungeonComplete(killer);
				DelevelManager.notifyDungeonComplete(killer);
				killer.sendPacket(new ExShowScreenMessage("Farm Dungeon Complete! All 10 waves cleared!", 3000));
				killer.sendMessage("=== Farm Dungeon Complete! ===");
				// TODO: giveItems(killer, WEAPON_TICKET_S, 1);
				showFarmPanel(killer, true, "Completed");
				cancelQuestTimer("finish_farm_" + playerId, null, killer);
				cleanFarmPlayer(playerId);
				scheduleCompletionExit(killer, "Farm Dungeon Complete!");
			}
			else
			{
				killer.sendPacket(new ExShowScreenMessage("Wave " + (waveIndex + 1) + " Complete! The next wave begins!", 3000));
				FARM_WAVE.put(playerId, nextWave);
				spawnFarmWave(killer, nextWave);
			}
		}
		else
		{
			showFarmPanel(killer, false, "In Progress");
		}
	}
	
	private void handleEliteKill(Npc npc, Player killer)
	{
		final int route = ELITE_ROUTE.get(killer.getObjectId());
		final int stage = ELITE_STAGE.get(killer.getObjectId());
		
		switch (route)
		{
			case 1: handleEliteGreenKill(npc, killer, stage); break;
			case 2: handleEliteBlueKill(npc, killer, stage);  break;
			case 3: handleEliteRedKill(npc, killer, stage);   break;
		}
	}
	
	// ---- VERDE ----
	private void handleEliteGreenKill(Npc npc, Player killer, int stage)
	{
		final int npcId = npc.getId();
		final int playerId = killer.getObjectId();
		if (stage == 1 && (npcId == ELITE_GREEN_MOB_1 || npcId == ELITE_GREEN_MOB_2 || npcId == ELITE_GREEN_MOB_3))
		{
			if (!consumeDungeonKill(npc, playerId))
			{
				return;
			}
			rewardDungeonCoin(killer, 3);
			int kills = ELITE_KILLS.getOrDefault(playerId, 0) + 1;
			ELITE_KILLS.put(playerId, kills);
			final int total = ELITE_EXPECTED_KILLS.getOrDefault(playerId, ELITE_GREEN_SPAWNS.length);
			killer.sendMessage("Stakato: " + kills + "/" + total);
			if (kills >= total)
			{
				killer.sendPacket(new ExShowScreenMessage("All enemies defeated! The Boss appears!", 3000));
				killer.sendMessage("Enemies cleared! Boss incoming!");
				ELITE_STAGE.put(playerId, 4);
				ELITE_KILLS.put(playerId, 0);
				resetActiveMobs(playerId);
				final int bossId = ELITE_BOSS_ID.getOrDefault(playerId, BOSS_LILITH);
				ELITE_EXPECTED_KILLS.put(playerId, spawnOwnedMob(playerId, bossId, ELITE_GREEN_BOSS_SPAWN, ELITE_TIME) != null ? 1 : 0);
				showElitePanel(killer, true, "Boss Stage");
			}
			else
			{
				showElitePanel(killer, false, "In Progress");
			}
		}
		else if (stage == 4 && npcId == ELITE_BOSS_ID.getOrDefault(playerId, BOSS_LILITH))
		{
			if (!consumeDungeonKill(npc, playerId))
			{
				return;
			}
			rewardDungeonCoin(killer, 50);
			giveEliteReward(killer, 1);
			MegaraxDailyMissions.notifyDungeonComplete(killer);
			DelevelManager.notifyDungeonComplete(killer);
			showElitePanel(killer, true, "Completed");
			cancelQuestTimer("finish_elite_" + playerId, null, killer);
			cleanElitePlayer(playerId);
			scheduleCompletionExit(killer, "Elite Dungeon Complete!");
		}
	}
	
	// ---- AZUL ----
	private void handleEliteBlueKill(Npc npc, Player killer, int stage)
	{
		final int npcId = npc.getId();
		final int playerId = killer.getObjectId();
		
		if (stage == 1 && npcId == ELITE_BLUE_MOB_1)
		{
			if (!consumeDungeonKill(npc, playerId))
			{
				return;
			}
			rewardDungeonCoin(killer, 5);
			int kills = ELITE_KILLS.getOrDefault(playerId, 0) + 1;
			ELITE_KILLS.put(playerId, kills);
			final int total = ELITE_EXPECTED_KILLS.getOrDefault(playerId, BLUE_WAVE_1_COUNT);
			killer.sendMessage("Watchmen: " + kills + "/" + total);
			if (kills >= total)
			{
				killer.sendPacket(new ExShowScreenMessage("Wave 1 Complete! Wave 2 Begins!", 3000));
				killer.sendMessage("Wave 2/3 - Defeat " + BLUE_WAVE_2_COUNT + " Argos Scouts!");
				ELITE_STAGE.put(playerId, 2);
				ELITE_KILLS.put(playerId, 0);
				resetActiveMobs(playerId);
				int spawned = 0;
				for (int i = 0; i < BLUE_WAVE_2_COUNT; i++)
				{
					if (spawnOwnedMob(playerId, ELITE_BLUE_MOB_2, ELITE_BLUE_SPAWNS[i], ELITE_TIME) != null)
					{
						spawned++;
					}
				}
				ELITE_EXPECTED_KILLS.put(playerId, spawned);
				showElitePanel(killer, true, "Stage Cleared");
			}
			else
			{
				showElitePanel(killer, false, "In Progress");
			}
		}
		else if (stage == 2 && npcId == ELITE_BLUE_MOB_2)
		{
			if (!consumeDungeonKill(npc, playerId))
			{
				return;
			}
			rewardDungeonCoin(killer, 5);
			int kills = ELITE_KILLS.getOrDefault(playerId, 0) + 1;
			ELITE_KILLS.put(playerId, kills);
			final int total = ELITE_EXPECTED_KILLS.getOrDefault(playerId, BLUE_WAVE_2_COUNT);
			killer.sendMessage("Scouts: " + kills + "/" + total);
			if (kills >= total)
			{
				killer.sendPacket(new ExShowScreenMessage("Wave 2 Complete! Wave 3 Begins!", 3000));
				killer.sendMessage("Wave 3/3 - Defeat " + BLUE_WAVE_3_COUNT + " Argos Supervisors!");
				ELITE_STAGE.put(playerId, 3);
				ELITE_KILLS.put(playerId, 0);
				resetActiveMobs(playerId);
				int spawned = 0;
				for (int i = 0; i < BLUE_WAVE_3_COUNT; i++)
				{
					if (spawnOwnedMob(playerId, ELITE_BLUE_MOB_3, ELITE_BLUE_SPAWNS[i], ELITE_TIME) != null)
					{
						spawned++;
					}
				}
				ELITE_EXPECTED_KILLS.put(playerId, spawned);
				showElitePanel(killer, true, "Stage Cleared");
			}
			else
			{
				showElitePanel(killer, false, "In Progress");
			}
		}
		else if (stage == 3 && npcId == ELITE_BLUE_MOB_3)
		{
			if (!consumeDungeonKill(npc, playerId))
			{
				return;
			}
			rewardDungeonCoin(killer, 5);
			int kills = ELITE_KILLS.getOrDefault(playerId, 0) + 1;
			ELITE_KILLS.put(playerId, kills);
			final int total = ELITE_EXPECTED_KILLS.getOrDefault(playerId, BLUE_WAVE_3_COUNT);
			killer.sendMessage("Supervisors: " + kills + "/" + total);
			if (kills >= total)
			{
				killer.sendPacket(new ExShowScreenMessage("All waves cleared! The Boss appears!", 3000));
				killer.sendMessage("All waves done! Boss incoming!");
				ELITE_STAGE.put(playerId, 4);
				ELITE_KILLS.put(playerId, 0);
				resetActiveMobs(playerId);
				final int bossId = ELITE_BOSS_ID.getOrDefault(playerId, BOSS_LILITH);
				ELITE_EXPECTED_KILLS.put(playerId, spawnOwnedMob(playerId, bossId, ELITE_BLUE_BOSS_SPAWN, ELITE_TIME) != null ? 1 : 0);
				showElitePanel(killer, true, "Boss Stage");
			}
			else
			{
				showElitePanel(killer, false, "In Progress");
			}
		}
		else if (stage == 4 && npcId == ELITE_BOSS_ID.getOrDefault(playerId, BOSS_LILITH))
		{
			if (!consumeDungeonKill(npc, playerId))
			{
				return;
			}
			rewardDungeonCoin(killer, 100);
			giveEliteReward(killer, 2);
			MegaraxDailyMissions.notifyDungeonComplete(killer);
			DelevelManager.notifyDungeonComplete(killer);
			showElitePanel(killer, true, "Completed");
			cancelQuestTimer("finish_elite_" + playerId, null, killer);
			cleanElitePlayer(playerId);
			scheduleCompletionExit(killer, "Elite Dungeon Complete!");
		}
	}
	
	// ---- ROJA ----
	private void handleEliteRedKill(Npc npc, Player killer, int stage)
	{
		final int npcId = npc.getId();
		final int playerId = killer.getObjectId();
		
		if (stage == 1 && npcId == ELITE_RED_MOB_1)
		{
			if (!consumeDungeonKill(npc, playerId))
			{
				return;
			}
			rewardDungeonCoin(killer, 8);
			int kills = ELITE_KILLS.getOrDefault(playerId, 0) + 1;
			ELITE_KILLS.put(playerId, kills);
			final int total = ELITE_EXPECTED_KILLS.getOrDefault(playerId, RED_WAVE_1_COUNT);
			killer.sendMessage("Grail Protectors: " + kills + "/" + total);
			if (kills >= total)
			{
				killer.sendPacket(new ExShowScreenMessage("Wave 1 Complete! Wave 2 Begins!", 3000));
				killer.sendMessage("Wave 2/3 - Defeat " + RED_WAVE_2_COUNT + " Grail Destroyers!");
				ELITE_STAGE.put(playerId, 2);
				ELITE_KILLS.put(playerId, 0);
				resetActiveMobs(playerId);
				int spawned = 0;
				for (int i = 0; i < RED_WAVE_2_COUNT; i++)
				{
					if (spawnOwnedMob(playerId, ELITE_RED_MOB_2, ELITE_RED_SPAWNS[i], ELITE_TIME) != null)
					{
						spawned++;
					}
				}
				ELITE_EXPECTED_KILLS.put(playerId, spawned);
				showElitePanel(killer, true, "Stage Cleared");
			}
			else
			{
				showElitePanel(killer, false, "In Progress");
			}
		}
		else if (stage == 2 && npcId == ELITE_RED_MOB_2)
		{
			if (!consumeDungeonKill(npc, playerId))
			{
				return;
			}
			rewardDungeonCoin(killer, 8);
			int kills = ELITE_KILLS.getOrDefault(playerId, 0) + 1;
			ELITE_KILLS.put(playerId, kills);
			final int total = ELITE_EXPECTED_KILLS.getOrDefault(playerId, RED_WAVE_2_COUNT);
			killer.sendMessage("Grail Destroyers: " + kills + "/" + total);
			if (kills >= total)
			{
				killer.sendPacket(new ExShowScreenMessage("Wave 2 Complete! Wave 3 Begins!", 3000));
				killer.sendMessage("Wave 3/3 - Defeat " + RED_WAVE_3_COUNT + " Grail Messengers!");
				ELITE_STAGE.put(playerId, 3);
				ELITE_KILLS.put(playerId, 0);
				resetActiveMobs(playerId);
				int spawned = 0;
				for (int i = 0; i < RED_WAVE_3_COUNT; i++)
				{
					if (spawnOwnedMob(playerId, ELITE_RED_MOB_3, ELITE_RED_SPAWNS[i], ELITE_TIME) != null)
					{
						spawned++;
					}
				}
				ELITE_EXPECTED_KILLS.put(playerId, spawned);
				showElitePanel(killer, true, "Stage Cleared");
			}
			else
			{
				showElitePanel(killer, false, "In Progress");
			}
		}
		else if (stage == 3 && npcId == ELITE_RED_MOB_3)
		{
			if (!consumeDungeonKill(npc, playerId))
			{
				return;
			}
			rewardDungeonCoin(killer, 8);
			int kills = ELITE_KILLS.getOrDefault(playerId, 0) + 1;
			ELITE_KILLS.put(playerId, kills);
			final int total = ELITE_EXPECTED_KILLS.getOrDefault(playerId, RED_WAVE_3_COUNT);
			killer.sendMessage("Grail Messengers: " + kills + "/" + total);
			if (kills >= total)
			{
				killer.sendPacket(new ExShowScreenMessage("All waves cleared! The Boss appears!", 3000));
				killer.sendMessage("All waves done! Borak incoming!");
				ELITE_STAGE.put(playerId, 4);
				ELITE_KILLS.put(playerId, 0);
				resetActiveMobs(playerId);
				ELITE_EXPECTED_KILLS.put(playerId, spawnOwnedMob(playerId, ELITE_RED_BOSS, ELITE_RED_BOSS_SPAWN, ELITE_TIME) != null ? 1 : 0);
				showElitePanel(killer, true, "Boss Stage");
			}
			else
			{
				showElitePanel(killer, false, "In Progress");
			}
		}
		else if (stage == 4 && npcId == ELITE_RED_BOSS)
		{
			if (!consumeDungeonKill(npc, playerId))
			{
				return;
			}
			rewardDungeonCoin(killer, 150);
			giveEliteReward(killer, 3);
			MegaraxDailyMissions.notifyDungeonComplete(killer);
			DelevelManager.notifyDungeonComplete(killer);
			showElitePanel(killer, true, "Completed");
			cancelQuestTimer("finish_elite_" + playerId, null, killer);
			cleanElitePlayer(playerId);
			scheduleCompletionExit(killer, "Elite Dungeon Complete!");
		}
	}
	
	// ================================================================
	// UTIL
	// ================================================================
	private Npc spawnOwnedMob(int ownerId, int npcId, Location location, int duration)
	{
		final InstanceWorld world = DUNGEON_INSTANCES.get(ownerId);
		if (world == null)
		{
			return null;
		}
		final Npc mob = addSpawn(npcId, location, false, duration, false, world.getInstanceId());
		if (mob != null)
		{
			mob.setScriptValue(ownerId);
			registerActiveMob(ownerId, mob);
		}
		return mob;
	}
	
	private boolean consumeDungeonKill(Npc npc, int ownerId)
	{
		if ((npc == null) || (ownerId <= 0) || (npc.getScriptValue() != ownerId))
		{
			return false;
		}
		
		final Map<Integer, Npc> activeMobs = DUNGEON_ACTIVE_MOBS.get(ownerId);
		final Map<Integer, Integer> activeStages = DUNGEON_ACTIVE_MOB_STAGES.get(ownerId);
		if ((activeMobs == null) || (activeStages == null))
		{
			return false;
		}
		
		final Integer activeStage = activeStages.remove(npc.getObjectId());
		final Npc activeMob = activeMobs.remove(npc.getObjectId());
		if ((activeMob == null) || (activeStage == null) || (activeStage != getCurrentDungeonStageId(ownerId)))
		{
			return false;
		}
		
		npc.setScriptValue(0);
		return true;
	}
	
	private void resetActiveMobs(int ownerId)
	{
		deleteActiveMobs(ownerId);
		DUNGEON_ACTIVE_MOBS.put(ownerId, new ConcurrentHashMap<>());
		DUNGEON_ACTIVE_MOB_STAGES.put(ownerId, new ConcurrentHashMap<>());
	}
	
	private void registerActiveMob(int ownerId, Npc npc)
	{
		if ((ownerId > 0) && (npc != null))
		{
			DUNGEON_ACTIVE_MOBS.computeIfAbsent(ownerId, id -> new ConcurrentHashMap<>()).put(npc.getObjectId(), npc);
			DUNGEON_ACTIVE_MOB_STAGES.computeIfAbsent(ownerId, id -> new ConcurrentHashMap<>()).put(npc.getObjectId(), getCurrentDungeonStageId(ownerId));
		}
	}
	
	private void clearDungeonTracking(int ownerId)
	{
		deleteActiveMobs(ownerId);
		DUNGEON_ACTIVE_MOBS.remove(ownerId);
		DUNGEON_ACTIVE_MOB_STAGES.remove(ownerId);
		DUNGEON_LOCKS.remove(ownerId);
		DUNGEON_PANEL_LAST_UPDATE.remove(ownerId);
		final InstanceWorld world = DUNGEON_INSTANCES.remove(ownerId);
		if (world != null)
		{
			InstanceManager.getInstance().destroyInstance(world.getInstanceId());
		}
	}

	private int createPrivateInstance(int ownerId, Set<Player> players)
	{
		if (DUNGEON_INSTANCES.containsKey(ownerId))
		{
			return 0;
		}
		final InstanceWorld world = new InstanceWorld();
		world.setInstance(InstanceManager.getInstance().createDynamicInstance(PRIVATE_INSTANCE_TEMPLATE));
		if (world.getInstanceId() <= 0)
		{
			return 0;
		}
		InstanceManager.getInstance().addWorld(world);
		for (Player player : players)
		{
			if (player != null)
			{
				world.addAllowed(player);
			}
		}
		DUNGEON_INSTANCES.put(ownerId, world);
		return world.getInstanceId();
	}
	
	private void deleteActiveMobs(int ownerId)
	{
		final Map<Integer, Npc> activeMobs = DUNGEON_ACTIVE_MOBS.get(ownerId);
		if (activeMobs != null)
		{
			for (Npc mob : activeMobs.values())
			{
				if ((mob != null) && !mob.isDecayed())
				{
					mob.deleteMe();
				}
			}
			activeMobs.clear();
		}
		
		final Map<Integer, Integer> activeStages = DUNGEON_ACTIVE_MOB_STAGES.get(ownerId);
		if (activeStages != null)
		{
			activeStages.clear();
		}
	}
	
	private int getActiveMobCount(int ownerId)
	{
		final Map<Integer, Npc> activeMobs = DUNGEON_ACTIVE_MOBS.get(ownerId);
		return activeMobs == null ? 0 : activeMobs.size();
	}
	
	private int getCurrentDungeonStageId(int ownerId)
	{
		if (PLAYER_STAGE.containsKey(ownerId))
		{
			return PLAYER_STAGE.get(ownerId);
		}
		if (FARM_WAVE.containsKey(ownerId))
		{
			return 100 + FARM_WAVE.get(ownerId);
		}
		if (PARTY_WAVE.containsKey(ownerId))
		{
			return 200 + PARTY_WAVE.get(ownerId);
		}
		if (ELITE_STAGE.containsKey(ownerId))
		{
			return 300 + ELITE_STAGE.get(ownerId);
		}
		return 0;
	}
	
	private Object getDungeonLock(int ownerId)
	{
		return DUNGEON_LOCKS.computeIfAbsent(ownerId, id -> new Object());
	}
	
	private void showDailyPanel(Player player, boolean force, String status)
	{
		if (player == null)
		{
			return;
		}
		
		final int playerId = player.getObjectId();
		final int stage = PLAYER_STAGE.getOrDefault(playerId, 1);
		final int kills = PLAYER_KILLS.getOrDefault(playerId, 0);
		final int total = DAILY_EXPECTED_KILLS.getOrDefault(playerId, stage == 1 ? ANGEL_SPAWNS.length : stage == 2 ? ANGEL_KILLER_SPAWNS.length : 1);
		final String objective = (stage == 3) ? "Defeat the Karik" : "Kill " + kills + "/" + total + " " + getDailyTargetName(stage);
		showDungeonPanel(player, playerId, "Daily Dungeon", "Normal", "Stage " + stage + " / 3", formatElapsed(DAILY_START_TIME.get(playerId)), objective, getActiveMobCount(playerId), "Dungeon Coin", "Use Leave to exit safely.", status, "leave_daily", force);
	}
	
	private void showFarmPanel(Player player, boolean force, String status)
	{
		if (player == null)
		{
			return;
		}
		
		final int playerId = player.getObjectId();
		final int waveIndex = FARM_WAVE.getOrDefault(playerId, 0);
		final int waveNumber = waveIndex + 1;
		final int kills = FARM_KILLS.getOrDefault(playerId, 0);
		final int total = FARM_EXPECTED_KILLS.getOrDefault(playerId, FARM_WAVE_COUNT[waveIndex]);
		final String objective = (waveIndex == 9) ? "Defeat Sandramanaia" : "Kill " + kills + "/" + total + " " + getFarmTargetName(waveIndex);
		showDungeonPanel(player, playerId, "Farm Dungeon", "Farm", "Wave " + waveNumber + " / 10", formatElapsed(FARM_START_TIME.get(playerId)), objective, getActiveMobCount(playerId), "Rax Coin + Craft Coin", "Clear each wave to unlock the next one.", status, "leave_farm", force);
	}
	
	private void showPartyPanel(Player leader, boolean force, String status)
	{
		if (leader == null)
		{
			return;
		}
		
		if (leader.isInParty())
		{
			for (Player member : leader.getParty().getMembers())
			{
				if (member != null)
				{
					showPartyPanelForMember(member, leader.getObjectId(), force, status);
				}
			}
		}
		else
		{
			showPartyPanelForMember(leader, leader.getObjectId(), force, status);
		}
	}
	
	private void showPartyPanelForMember(Player player, int leaderId, boolean force, String status)
	{
		final int waveIndex = PARTY_WAVE.getOrDefault(leaderId, 0);
		final int waveNumber = waveIndex + 1;
		final int kills = PARTY_KILLS.getOrDefault(leaderId, 0);
		final int total = PARTY_EXPECTED_KILLS.getOrDefault(leaderId, PARTY_WAVE_COUNT[waveIndex]);
		final String objective = (waveIndex == (PARTY_WAVE_MOB.length - 1)) ? "Defeat the party boss" : "Kill " + kills + "/" + total + " party enemies";
		showDungeonPanel(player, leaderId, "Party Dungeon", "Party", "Wave " + waveNumber + " / 5", formatElapsed(PARTY_START_TIME.get(leaderId)), objective, getActiveMobCount(leaderId), "Dungeon Coin + Rax Coin", "Stay with the party. Leave exits the run safely.", status, "leave_party", force);
	}
	
	private void showElitePanel(Player player, boolean force, String status)
	{
		if (player == null)
		{
			return;
		}
		
		final int playerId = player.getObjectId();
		final int route = ELITE_ROUTE.getOrDefault(playerId, 1);
		final int stage = ELITE_STAGE.getOrDefault(playerId, 1);
		final int maxStage = route == 1 ? 2 : 4;
		final int displayStage = stage == 4 ? maxStage : stage;
		final int kills = ELITE_KILLS.getOrDefault(playerId, 0);
		final int total = ELITE_EXPECTED_KILLS.getOrDefault(playerId, getEliteDefaultTotal(route, stage));
		final String objective = (stage == 4) ? "Defeat " + getEliteBossName(playerId) : "Kill " + kills + "/" + total + " " + getEliteTargetName(route, stage);
		showDungeonPanel(player, playerId, "Elite Dungeon", "Elite", getEliteRouteName(route) + " Route - Stage " + displayStage + " / " + maxStage, formatElapsed(ELITE_START_TIME.get(playerId)), objective, getActiveMobCount(playerId), "Rax Coin + Craft Coin", "General limit: 30 minutes.", status, "leave_elite", force);
	}
	
	private void showDungeonPanel(Player player, int runId, String dungeonName, String difficulty, String stageText, String elapsed, String objective, int remaining, String reward, String rules, String status, String leaveEvent, boolean force)
	{
		if (player == null)
		{
			return;
		}
		
		final long now = System.currentTimeMillis();
		final int panelKey = player.getObjectId();
		if (!force && ((now - DUNGEON_PANEL_LAST_UPDATE.getOrDefault(panelKey, 0L)) < 1500))
		{
			return;
		}
		DUNGEON_PANEL_LAST_UPDATE.put(panelKey, now);
		
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Megarax Dungeon</title><body><center>");
		html.append("<button value=\"").append(dungeonName).append("\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\">");
		html.append("<tr><td width=92><font color=\"0099FF\">Difficulty</font></td><td><font color=\"LEVEL\">").append(difficulty).append("</font></td></tr>");
		html.append("<tr><td><font color=\"0099FF\">Stage</font></td><td><font color=\"ae9977\">").append(stageText).append("</font></td></tr>");
		html.append("<tr><td><font color=\"0099FF\">Elapsed</font></td><td><font color=\"LEVEL\">").append(elapsed).append("</font></td></tr>");
		html.append("<tr><td><font color=\"0099FF\">Status</font></td><td><font color=\"00FF99\">").append(status).append("</font></td></tr>");
		html.append("</table><br1>");
		html.append("<table width=292 bgcolor=\"000000\">");
		html.append("<tr><td><font color=\"ae9977\">Objective:</font> <font color=\"LEVEL\">").append(objective).append("</font></td></tr>");
		html.append("<tr><td><font color=\"ae9977\">Mobs remaining:</font> <font color=\"LEVEL\">").append(Math.max(0, remaining)).append("</font></td></tr>");
		html.append("<tr><td><font color=\"ae9977\">Rewards:</font> <font color=\"B09878\">").append(reward).append("</font></td></tr>");
		html.append("<tr><td><font color=\"ae9977\">Rules:</font> <font color=\"B09878\">").append(rules).append("</font></td></tr>");
		html.append("</table><br>");
		html.append("<button value=\"Leave\" action=\"bypass -h Quest MegaraxDungeon ").append(leaveEvent).append("\" width=90 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("</center></body></html>");
		
		final NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html.toString());
		player.sendPacket(msg);
	}
	
	private String getDailyTargetName(int stage)
	{
		return stage == 2 ? "Angel Killers" : "Angels";
	}
	
	private String getFarmTargetName(int waveIndex)
	{
		switch (FARM_WAVE_MOB[waveIndex])
		{
			case FARM_MOB_1:
			{
				return "Dungeon Warriors";
			}
			case FARM_MOB_2:
			{
				return "Dungeon Raiders";
			}
			case FARM_MOB_3:
			{
				return "Dungeon Champions";
			}
			case FARM_BORAK_MOB:
			{
				return "Borak";
			}
			case FARM_MOB_4:
			{
				return "Dungeon Guardians";
			}
			default:
			{
				return "Enemies";
			}
		}
	}
	
	private int getEliteDefaultTotal(int route, int stage)
	{
		if (stage == 4)
		{
			return 1;
		}
		if (route == 1)
		{
			return ELITE_GREEN_SPAWNS.length;
		}
		if (route == 2)
		{
			return stage == 1 ? BLUE_WAVE_1_COUNT : stage == 2 ? BLUE_WAVE_2_COUNT : BLUE_WAVE_3_COUNT;
		}
		return stage == 1 ? RED_WAVE_1_COUNT : stage == 2 ? RED_WAVE_2_COUNT : RED_WAVE_3_COUNT;
	}
	
	private String getEliteTargetName(int route, int stage)
	{
		if (route == 1)
		{
			return "Stakato enemies";
		}
		if (route == 2)
		{
			return stage == 1 ? "Argos Watchmen" : stage == 2 ? "Argos Scouts" : "Argos Supervisors";
		}
		return stage == 1 ? "Grail Protectors" : stage == 2 ? "Grail Destroyers" : "Grail Messengers";
	}
	
	private String getEliteBossName(int playerId)
	{
		final int bossId = ELITE_BOSS_ID.getOrDefault(playerId, BOSS_LILITH);
		return bossId == ELITE_RED_BOSS ? "Borak" : bossId == BOSS_LILITH ? "Lilith" : "Anakim";
	}
	
	private String formatElapsed(Long startTime)
	{
		if (startTime == null)
		{
			return "00:00:00";
		}
		
		final long totalSeconds = Math.max(0, (System.currentTimeMillis() - startTime) / 1000);
		final long hours = totalSeconds / 3600;
		final long minutes = (totalSeconds % 3600) / 60;
		final long seconds = totalSeconds % 60;
		return String.format("%02d:%02d:%02d", hours, minutes, seconds);
	}
	
	private void scheduleCompletionExit(Player player, String completeMessage)
	{
		player.sendMessage(completeMessage + " You have 15 seconds to collect drops before exit.");
		startQuestTimer("exit_after_clear_" + player.getObjectId(), COMPLETION_TELEPORT_DELAY, null, player);
	}
	
	private void rewardDungeonCoin(Player player, int amount)
	{
		if ((player != null) && (amount > 0))
		{
			final boolean boosted = player.getEffectList().getBuffInfoBySkillId(7073) != null;
			giveItems(player, DUNGEON_COIN, boosted ? (amount * 2) : amount);
		}
	}
	
	private int getFarmDungeonCoinReward(int waveIndex)
	{
		if (waveIndex >= 8)
		{
			return 5;
		}
		if (waveIndex >= 5)
		{
			return 3;
		}
		if (waveIndex >= 3)
		{
			return 2;
		}
		return 1;
	}
	
	private void initRankingTable()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(RANKING_TABLE_SQL);
			PreparedStatement rewards = con.prepareStatement(RANKING_REWARD_TABLE_SQL))
		{
			ps.executeUpdate();
			rewards.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxDungeon: Could not create ranking table: " + e.getMessage());
		}
	}

	private void initCooldownTable()
	{
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(COOLDOWN_TABLE_SQL))
		{
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxDungeon: Could not create cooldown table: " + e.getMessage());
		}
	}

	/** Returns zero when reserved, otherwise the remaining cooldown in milliseconds. */
	private long acquireAccountCooldown(Player player, String dungeonKey, long duration)
	{
		if ((player == null) || (player.getAccountName() == null))
		{
			return Long.MAX_VALUE;
		}
		final String account = player.getAccountName().toLowerCase(Locale.ROOT);
		final Object lock = ACCOUNT_COOLDOWN_LOCKS.computeIfAbsent(account, key -> new Object());
		synchronized (lock)
		{
			final long now = System.currentTimeMillis();
			try (Connection con = DatabaseFactory.getConnection())
			{
				con.setAutoCommit(false);
				long availableAt = 0;
				try (PreparedStatement select = con.prepareStatement(COOLDOWN_SELECT_SQL))
				{
					select.setString(1, account);
					select.setString(2, dungeonKey);
					try (ResultSet rs = select.executeQuery())
					{
						if (rs.next())
						{
							availableAt = rs.getLong(1);
						}
					}
				}
				if (availableAt > now)
				{
					con.rollback();
					return availableAt - now;
				}
				try (PreparedStatement update = con.prepareStatement(COOLDOWN_INSERT_SQL))
				{
					update.setString(1, account);
					update.setString(2, dungeonKey);
					update.setLong(3, now + duration);
					update.executeUpdate();
				}
				con.commit();
				return 0;
			}
			catch (SQLException e)
			{
				LOGGER.warning("MegaraxDungeon: Cooldown reservation failed for " + account + "/" + dungeonKey + ": " + e.getMessage());
				return Long.MAX_VALUE;
			}
		}
	}

	private long getAccountCooldown(Player player, String dungeonKey)
	{
		if ((player == null) || (player.getAccountName() == null))
		{
			return Long.MAX_VALUE;
		}
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT available_at FROM megarax_dungeon_cooldowns WHERE account_name=? AND dungeon_key=?"))
		{
			ps.setString(1, player.getAccountName().toLowerCase(Locale.ROOT));
			ps.setString(2, dungeonKey);
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					return Math.max(0, rs.getLong(1) - System.currentTimeMillis());
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxDungeon: Cooldown lookup failed: " + e.getMessage());
			return Long.MAX_VALUE;
		}
		return 0;
	}
	
	private void recordDungeonRanking(Player player, String dungeon, String route, long startTime)
	{
		if (player == null)
		{
			return;
		}

		recordDungeonChronicle(player, dungeon, route);
		
		final int timeSeconds = Math.max(1, (int) ((System.currentTimeMillis() - startTime) / 1000));
		final String weekKey = getWeekKey();
		boolean shouldSave = true;
		
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement select = con.prepareStatement(RANKING_SELECT_PLAYER_SQL))
		{
			select.setString(1, weekKey);
			select.setString(2, dungeon);
			select.setString(3, route);
			select.setInt(4, player.getObjectId());
			
			try (ResultSet rs = select.executeQuery())
			{
				if (rs.next())
				{
					final int currentBest = rs.getInt("time_seconds");
					shouldSave = timeSeconds < currentBest;
					if (shouldSave)
					{
						try (PreparedStatement update = con.prepareStatement(RANKING_UPDATE_SQL))
						{
							update.setString(1, player.getName());
							update.setInt(2, timeSeconds);
							update.setLong(3, System.currentTimeMillis());
							update.setString(4, weekKey);
							update.setString(5, dungeon);
							update.setString(6, route);
							update.setInt(7, player.getObjectId());
							update.executeUpdate();
						}
					}
				}
				else
				{
					try (PreparedStatement insert = con.prepareStatement(RANKING_INSERT_SQL))
					{
						insert.setString(1, weekKey);
						insert.setString(2, dungeon);
						insert.setString(3, route);
						insert.setInt(4, player.getObjectId());
						insert.setString(5, player.getName());
						insert.setInt(6, timeSeconds);
						insert.setLong(7, System.currentTimeMillis());
						insert.executeUpdate();
					}
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxDungeon: Could not save ranking entry: " + e.getMessage());
			return;
		}
		
		if (shouldSave)
		{
			player.sendMessage("Dungeon Ranking: new weekly best time saved (" + formatTime(timeSeconds) + ").");
		}
	}

	private void recordDungeonChronicle(Player player, String dungeon, String route)
	{
		if (player == null)
		{
			return;
		}
		final String safeDungeon = dungeon == null ? "Dungeon" : dungeon;
		final String safeRoute = route == null ? "Ruta" : route;
		final String key = (safeDungeon + "_" + safeRoute).toUpperCase(Locale.ROOT).replace(' ', '_');
		final int legacyPoints = "Elite".equalsIgnoreCase(safeDungeon) ? 5 : "Party".equalsIgnoreCase(safeDungeon) ? 4 : "Farm".equalsIgnoreCase(safeDungeon) ? 3 : 2;
		MegaraxLivingChronicle.recordEvent(player, "EXPEDITION", key, "Primera expedicion: " + safeDungeon + " - " + safeRoute, "Completo esta ruta de Megarax Dungeon.", legacyPoints, true);
	}
	
	private void showRanking(Player player)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Megarax Dungeon Ranking</title><body><center>");
		html.append("<button value=\"Dungeon Ranking\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\"><tr><td align=center>");
		html.append("<font color=\"0099FF\">Weekly Best Times</font><br1>");
		html.append("<font color=\"ae9977\">Daily, Farm and Elite dungeon runs.</font>");
		html.append("</td></tr></table><br>");
		html.append("<table width=292 bgcolor=\"000000\"><tr>");
		html.append("<td width=28><font color=\"LEVEL\">#</font></td>");
		html.append("<td width=102><font color=\"LEVEL\">Player</font></td>");
		html.append("<td width=92><font color=\"LEVEL\">Dungeon</font></td>");
		html.append("<td width=70><font color=\"LEVEL\">Time</font></td>");
		html.append("</tr>");
		
		int rank = 1;
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(RANKING_TOP_SQL))
		{
			ps.setString(1, getWeekKey());
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					html.append("<tr>");
					html.append("<td>").append(rank++).append("</td>");
					html.append("<td><font color=\"FFFFFF\">").append(rs.getString("player_name")).append("</font></td>");
					html.append("<td>").append(rs.getString("dungeon")).append(" ").append(rs.getString("route")).append("</td>");
					html.append("<td><font color=\"FF6600\">").append(formatTime(rs.getInt("time_seconds"))).append("</font></td>");
					html.append("</tr>");
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxDungeon: Could not load ranking: " + e.getMessage());
			html.append("<tr><td align=center colspan=4>Ranking temporarily unavailable.</td></tr>");
		}
		
		if (rank == 1)
		{
			html.append("<tr><td align=center colspan=4>No runs registered this week.</td></tr>");
		}
		html.append("</table><br>");
		html.append("<table width=292 bgcolor=\"000000\"><tr><td align=center>");
		html.append("<font color=\"ae9977\">Rewards are claimed from the previous week.</font><br1>");
		html.append("<button value=\"Claim Weekly Rewards\" action=\"bypass -h Quest MegaraxDungeon claim_ranking_rewards\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("</td></tr></table><br>");
		html.append("<button value=\"Back\" action=\"bypass -h Quest MegaraxDungeon back\" width=92 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		html.append("</center></body></html>");
		
		final NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html.toString());
		player.sendPacket(msg);
	}
	
	private void claimWeeklyRankingRewards(Player player)
	{
		final String rewardWeek = getPreviousWeekKey();
		int rewardsClaimed = 0;
		
		for (String[] category : RANKING_CATEGORIES)
		{
			rewardsClaimed += claimCategoryReward(player, rewardWeek, category[0], category[1]);
		}
		
		if (rewardsClaimed > 0)
		{
			player.sendMessage("Dungeon Ranking: claimed " + rewardsClaimed + " weekly reward(s) for week " + rewardWeek + ".");
		}
		else
		{
			player.sendMessage("Dungeon Ranking: no unclaimed rewards found for previous week (" + rewardWeek + ").");
		}
		showRanking(player);
	}
	
	private int claimCategoryReward(Player player, String weekKey, String dungeon, String route)
	{
		int rank = 1;
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement top = con.prepareStatement(RANKING_TOP_CATEGORY_SQL))
		{
			top.setString(1, weekKey);
			top.setString(2, dungeon);
			top.setString(3, route);
			try (ResultSet rs = top.executeQuery())
			{
				while (rs.next())
				{
					if (rs.getInt("player_id") == player.getObjectId())
					{
						if (isRankingRewardClaimed(con, weekKey, dungeon, route, player.getObjectId()))
						{
							return 0;
						}
						
						markRankingRewardClaimed(con, weekKey, dungeon, route, player.getObjectId());
						giveRankingReward(player, dungeon, route, rank);
						return 1;
					}
					rank++;
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxDungeon: Could not claim ranking reward: " + e.getMessage());
		}
		return 0;
	}
	
	private boolean isRankingRewardClaimed(Connection con, String weekKey, String dungeon, String route, int playerId) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(RANKING_REWARD_CHECK_SQL))
		{
			ps.setString(1, weekKey);
			ps.setString(2, dungeon);
			ps.setString(3, route);
			ps.setInt(4, playerId);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next();
			}
		}
	}
	
	private void markRankingRewardClaimed(Connection con, String weekKey, String dungeon, String route, int playerId) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(RANKING_REWARD_INSERT_SQL))
		{
			ps.setString(1, weekKey);
			ps.setString(2, dungeon);
			ps.setString(3, route);
			ps.setInt(4, playerId);
			ps.setLong(5, System.currentTimeMillis());
			ps.executeUpdate();
		}
	}
	
	private void giveRankingReward(Player player, String dungeon, String route, int rank)
	{
		final int[] reward = getRankingReward(rank);
		if (reward[0] > 0) rewardDungeonCoin(player, reward[0]);
		if (reward[1] > 0) giveItems(player, RAX_COIN, reward[1]);
		if (reward[2] > 0) giveItems(player, BLESSED_ARMOR_S, reward[2]);
		if (reward[3] > 0) giveItems(player, BLESSED_WEAPON_S, reward[3]);
		if (reward[4] > 0) giveItems(player, SECRET_BOOK_OF_GIANTS, reward[4]);
		
		player.sendMessage("Dungeon Ranking Reward: Top " + rank + " " + dungeon + " " + route + ".");
		if (rank <= 3)
		{
			Broadcast.toAllOnlinePlayers("Dungeon Ranking: " + player.getName() + " claimed Top " + rank + " reward for " + dungeon + " " + route + ".");
		}
	}
	
	private int[] getRankingReward(int rank)
	{
		// Ranking rewards are intentionally weekly and claim-once per category to avoid repeat farming.
		if (rank == 1)
		{
			return new int[] { 500, 500, 2, 1, 0 };
		}
		if (rank == 2)
		{
			return new int[] { 300, 300, 1, 0, 0 };
		}
		if (rank == 3)
		{
			return new int[] { 200, 200, 1, 0, 0 };
		}
		return new int[] { 100, 100, 0, 0, 0 };
	}
	
	private String getWeekKey()
	{
		return getWeekKey(LocalDate.now());
	}
	
	private String getPreviousWeekKey()
	{
		return getWeekKey(LocalDate.now().minusWeeks(1));
	}
	
	private String getWeekKey(LocalDate date)
	{
		final WeekFields weekFields = WeekFields.of(Locale.US);
		return date.get(weekFields.weekBasedYear()) + "-" + String.format("%02d", date.get(weekFields.weekOfWeekBasedYear()));
	}
	
	private String getEliteRouteName(int route)
	{
		switch (route)
		{
			case 1:
				return "Green";
			case 2:
				return "Blue";
			case 3:
				return "Red";
			default:
				return "Unknown";
		}
	}
	
	private String formatTime(int totalSeconds)
	{
		final int minutes = totalSeconds / 60;
		final int seconds = totalSeconds % 60;
		return minutes + "m " + seconds + "s";
	}
	
	private Player getPlayerByObjectId(int objectId)
	{
		for (Player player : org.l2jmobius.gameserver.model.World.getInstance().getPlayers())
		{
			if (player.getObjectId() == objectId) return player;
		}
		return null;
	}
	
	public static void main(String[] args)
	{
		LOGGER.info("MegaraxDungeon: Script loaded.");
		new MegaraxDungeon();
	}
}
