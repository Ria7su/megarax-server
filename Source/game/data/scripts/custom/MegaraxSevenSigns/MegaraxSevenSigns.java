/*
 * Megarax Seven Signs overlay.
 */
package custom.MegaraxSevenSigns;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.managers.territory.Territory;
import org.l2jmobius.gameserver.managers.territory.TerritoryManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.Containers;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerPvPKill;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.zone.ZoneType;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.network.serverpackets.ExShowScreenMessage;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.util.Broadcast;

import ai.AbstractNpcAI;

public class MegaraxSevenSigns extends AbstractNpcAI
{
	private static final Logger LOG = Logger.getLogger(MegaraxSevenSigns.class.getName());
	
	private static final int RAX_COIN = 9142;
	private static final int CRAFT_COIN = 4356;
	private static final int PVP_COIN = 8875;
	
	private static final String SEAL_STRIFE = "Strife";
	private static final String SEAL_AVARICE = "Avarice";
	private static final String SEAL_GNOSIS = "Gnosis";
	private static final String TERRITORY_NAME = "Talking Island";
	
	private static final long REPEAT_PVP_REWARD_DELAY = 5 * 60 * 1000L;
	private static final long CONQUEST_CHECK_DELAY = 60 * 1000L;
	private static final long CONQUEST_WAVE_INTERVAL = 30 * 60 * 1000L;
	private static final long CONQUEST_DESPAWN_TIME = 20 * 60 * 1000L;
	
	private static final String CREATE_TERRITORY_TABLE = "CREATE TABLE IF NOT EXISTS territory_conquest (territory_name VARCHAR(64) NOT NULL PRIMARY KEY, owner_clan_id INT NOT NULL DEFAULT 0, tier INT NOT NULL DEFAULT 0, capture_time BIGINT NOT NULL DEFAULT 0, last_reward_time BIGINT NOT NULL DEFAULT 0, total_rewards_given BIGINT NOT NULL DEFAULT 0)";
	private static final String CREATE_SCORE_TABLE = "CREATE TABLE IF NOT EXISTS megarax_seven_signs_scores (cycle VARCHAR(32) NOT NULL, seal_name VARCHAR(16) NOT NULL, score BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (cycle, seal_name))";
	private static final String SAVE_SCORE = "REPLACE INTO megarax_seven_signs_scores (cycle, seal_name, score) VALUES (?, ?, ?)";
	
	private static final int[] CONQUEST_MOBS =
	{
		900020,
		900021,
		900022
	};
	private static final Location[] CONQUEST_SPAWNS =
	{
		new Location(-101445, 235140, -3560, 0),
		new Location(-100264, 236650, -3560, 0),
		new Location(-99055, 238002, -3560, 0),
		new Location(-101020, 238210, -3560, 0),
		new Location(-99120, 235332, -3560, 0),
		new Location(-100298, 236747, -3560, 0)
	};
	private static final String[] DYNAMIC_ZONE_NAMES =
	{
		"megarax_forest_dead_pvp",
		"megarax_imperial_tomb_pvp",
		"megarax_toi_13_pvp",
		"megarax_ketra_orc_pvp",
		"megarax_pagan_temple_pvp",
		"megarax_talking_island_conquest"
	};
	
	private static final Map<String, Long> PVP_REWARD_DELAY = new ConcurrentHashMap<>();
	private static final Map<String, Long> SEAL_POINTS = new ConcurrentHashMap<>();
	private static final Set<Npc> CONQUEST_SPAWNED_MOBS = Collections.newSetFromMap(new ConcurrentHashMap<Npc, Boolean>());
	
	private static volatile int _conquestOwnerClanId;
	private static volatile long _lastConquestWave;
	
	private MegaraxSevenSigns()
	{
		addKillId(CONQUEST_MOBS);
		Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_PVP_KILL, (OnPlayerPvPKill event) -> onPlayerPvPKill(event), this));
		initTables();
		ThreadPool.scheduleAtFixedRate(() -> checkConquestTerritory(), 30000, CONQUEST_CHECK_DELAY);
		LOG.info("MegaraxSevenSigns: Script loaded.");
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		if ((player != null) && event.equals("status"))
		{
			showStatus(player);
		}
		return null;
	}
	
	@Override
	public void onKill(Npc npc, Player killer, boolean isSummon)
	{
		if ((killer == null) || (killer.getClan() == null))
		{
			return;
		}
		
		final Territory territory = TerritoryManager.getInstance().getTerritory(TERRITORY_NAME);
		if ((territory == null) || !territory.hasOwner() || (territory.getOwnerClanId() != killer.getClanId()))
		{
			killer.sendMessage("Only the clan that controls this territory can claim Seal Guardian rewards.");
			return;
		}
		
		final int[] reward = getConquestReward(npc.getId());
		final int raxCoins = reward[0];
		final int craftCoins = reward[1];
		final int pvpCoins = reward[2];
		final int raxBonus = Math.max(1, (int) Math.ceil(raxCoins * 0.05));
		final int sealBonus = SEAL_AVARICE.equals(getDominantSeal()) ? raxBonus : 0;
		
		killer.addItem(ItemProcessType.REWARD, RAX_COIN, raxCoins + raxBonus + sealBonus, killer, true);
		if (craftCoins > 0)
		{
			killer.addItem(ItemProcessType.REWARD, CRAFT_COIN, craftCoins, killer, true);
		}
		if (pvpCoins > 0)
		{
			killer.addItem(ItemProcessType.REWARD, PVP_COIN, pvpCoins, killer, true);
		}
		
		addSealPoints(SEAL_AVARICE, Math.max(1, raxCoins / 10));
		killer.sendMessage("Conquest reward: " + raxCoins + " Rax Coin + " + raxBonus + " territory bonus" + (sealBonus > 0 ? " + " + sealBonus + " Avarice bonus." : "."));
		CONQUEST_SPAWNED_MOBS.remove(npc);
	}
	
	private void onPlayerPvPKill(OnPlayerPvPKill event)
	{
		final Player killer = event.getPlayer();
		final Player target = event.getTarget();
		if ((killer == null) || (target == null) || killer.isGM() || target.isGM())
		{
			return;
		}
		if (!isInsideActiveMegaraxZone(killer) || !isInsideActiveMegaraxZone(target))
		{
			return;
		}
		if (isSameRealPlayer(killer, target) || isSameClan(killer, target))
		{
			killer.sendMessage("PvP Coin reward skipped: anti-feed protection is active.");
			return;
		}
		
		final String rewardKey = killer.getObjectId() + ":" + target.getObjectId();
		final long now = System.currentTimeMillis();
		final Long reuse = PVP_REWARD_DELAY.get(rewardKey);
		if ((reuse != null) && (reuse > now))
		{
			killer.sendMessage("PvP Coin reward skipped: same target protection is active.");
			return;
		}
		
		PVP_REWARD_DELAY.put(rewardKey, now + REPEAT_PVP_REWARD_DELAY);
		killer.addItem(ItemProcessType.REWARD, PVP_COIN, 1, killer, true);
		if (SEAL_STRIFE.equals(getDominantSeal()))
		{
			killer.addItem(ItemProcessType.REWARD, PVP_COIN, 1, killer, true);
			killer.sendMessage("Seven Signs Strife bonus: +1 PvP Coin.");
		}
		addSealPoints(SEAL_STRIFE, 1);
		killer.sendPacket(new ExShowScreenMessage("Seven Signs: +1 Strife point", 3000));
	}
	
	private boolean isInsideActiveMegaraxZone(Player player)
	{
		for (String zoneName : DYNAMIC_ZONE_NAMES)
		{
			final ZoneType zone = ZoneManager.getInstance().getZoneByName(zoneName);
			if ((zone != null) && zone.isEnabled() && zone.isInsideZone(player))
			{
				return true;
			}
		}
		return false;
	}
	
	private void checkConquestTerritory()
	{
		try
		{
			cleanDeadConquestMobs();
			final Territory territory = TerritoryManager.getInstance().getTerritory(TERRITORY_NAME);
			final int ownerClanId = ((territory != null) && territory.hasOwner()) ? territory.getOwnerClanId() : 0;
			if (ownerClanId <= 0)
			{
				despawnConquestMobs();
				_conquestOwnerClanId = 0;
				return;
			}
			
			final long now = System.currentTimeMillis();
			if ((_conquestOwnerClanId != ownerClanId) || CONQUEST_SPAWNED_MOBS.isEmpty() || ((now - _lastConquestWave) >= CONQUEST_WAVE_INTERVAL))
			{
				_conquestOwnerClanId = ownerClanId;
				spawnConquestWave(ownerClanId);
			}
		}
		catch (Exception e)
		{
			LOG.warning("MegaraxSevenSigns conquest check failed: " + e.getMessage());
		}
	}
	
	private void spawnConquestWave(int ownerClanId)
	{
		despawnConquestMobs();
		for (int i = 0; i < CONQUEST_SPAWNS.length; i++)
		{
			final int npcId = (i == (CONQUEST_SPAWNS.length - 1)) ? 900022 : ((i % 2) == 0 ? 900020 : 900021);
			final Npc npc = addSpawn(npcId, CONQUEST_SPAWNS[i], false, CONQUEST_DESPAWN_TIME);
			if (npc != null)
			{
				CONQUEST_SPAWNED_MOBS.add(npc);
			}
		}
		_lastConquestWave = System.currentTimeMillis();
		Broadcast.toAllOnlinePlayers("Megarax Conquest: Seal Guardians have appeared in Talking Island territory.", false);
		LOG.info("MegaraxSevenSigns: Spawned conquest wave for clan " + ownerClanId + ".");
	}
	
	private void cleanDeadConquestMobs()
	{
		CONQUEST_SPAWNED_MOBS.removeIf(npc -> (npc == null) || npc.isDead() || npc.isDecayed());
	}
	
	private void despawnConquestMobs()
	{
		for (Npc npc : CONQUEST_SPAWNED_MOBS)
		{
			if ((npc != null) && !npc.isDecayed())
			{
				npc.deleteMe();
			}
		}
		CONQUEST_SPAWNED_MOBS.clear();
	}
	
	private int[] getConquestReward(int npcId)
	{
		switch (npcId)
		{
			case 900021:
			{
				return new int[]
				{
					150,
					8,
					0
				};
			}
			case 900022:
			{
				return new int[]
				{
					350,
					20,
					1
				};
			}
			case 900020:
			default:
			{
				return new int[]
				{
					75,
					3,
					0
				};
			}
		}
	}
	
	private boolean isSameClan(Player killer, Player target)
	{
		return (killer.getClanId() > 0) && (killer.getClanId() == target.getClanId());
	}
	
	private boolean isSameRealPlayer(Player killer, Player target)
	{
		final String killerIp = killer.getIPAddress();
		final String targetIp = target.getIPAddress();
		if ((killerIp != null) && !killerIp.isEmpty() && killerIp.equals(targetIp))
		{
			return true;
		}
		
		final String killerHwid = getHardwareId(killer);
		final String targetHwid = getHardwareId(target);
		return (killerHwid != null) && !killerHwid.isEmpty() && killerHwid.equals(targetHwid);
	}
	
	private String getHardwareId(Player player)
	{
		final GameClient client = player.getClient();
		if ((client == null) || (client.getHardwareInfo() == null))
		{
			return null;
		}
		return client.getHardwareInfo().getMacAddress();
	}
	
	public static void rewardDungeonSealBonus(Player player, int raxCoins, int craftCoins)
	{
		if (player == null)
		{
			return;
		}
		
		addSealPoints(SEAL_GNOSIS, Math.max(1, (raxCoins / 100) + (craftCoins / 10)));
		if (SEAL_GNOSIS.equals(getDominantSeal()))
		{
			final int bonus = Math.max(1, (int) Math.ceil(raxCoins * 0.05));
			player.addItem(ItemProcessType.REWARD, RAX_COIN, bonus, player, true);
			player.sendMessage("Seven Signs Gnosis bonus: +" + bonus + " Rax Coin.");
		}
	}
	
	public static void addSealPoints(String sealName, long points)
	{
		if ((sealName == null) || (points <= 0))
		{
			return;
		}
		
		final String key = getCycleKey() + ":" + sealName;
		final long total = SEAL_POINTS.merge(key, points, Long::sum);
		saveScore(getCycleKey(), sealName, total);
	}
	
	private static String getDominantSeal()
	{
		final Map<String, Long> scores = getCurrentScores();
		String winner = "None";
		long highest = 0;
		boolean tie = false;
		for (Map.Entry<String, Long> entry : scores.entrySet())
		{
			if (entry.getValue() > highest)
			{
				winner = entry.getKey();
				highest = entry.getValue();
				tie = false;
			}
			else if ((entry.getValue() == highest) && (highest > 0))
			{
				tie = true;
			}
		}
		return tie ? "None" : winner;
	}
	
	private static Map<String, Long> getCurrentScores()
	{
		final String cycle = getCycleKey();
		final Map<String, Long> scores = new HashMap<>();
		scores.put(SEAL_STRIFE, SEAL_POINTS.getOrDefault(cycle + ":" + SEAL_STRIFE, 0L));
		scores.put(SEAL_AVARICE, SEAL_POINTS.getOrDefault(cycle + ":" + SEAL_AVARICE, 0L));
		scores.put(SEAL_GNOSIS, SEAL_POINTS.getOrDefault(cycle + ":" + SEAL_GNOSIS, 0L));
		return scores;
	}
	
	private void showStatus(Player player)
	{
		final Map<String, Long> scores = getCurrentScores();
		final String dominantSeal = getDominantSeal();
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Megarax Seven Signs</title><body><center>");
		html.append("<button value=\"Seven Signs War\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\"><tr><td align=center>");
		html.append("<font color=\"ae9977\">Weekly Megarax seal control: " + getCycleKey() + "</font><br1>");
		html.append("<font color=\"LEVEL\">Dominant Seal: " + dominantSeal + "</font>");
		html.append("</td></tr></table><br>");
		html.append("<table width=292 bgcolor=\"000000\">");
		html.append("<tr><td width=160><font color=\"0099FF\">Strife</font></td><td><font color=\"LEVEL\">" + scores.get(SEAL_STRIFE) + "</font></td></tr>");
		html.append("<tr><td><font color=\"0099FF\">Avarice</font></td><td><font color=\"LEVEL\">" + scores.get(SEAL_AVARICE) + "</font></td></tr>");
		html.append("<tr><td><font color=\"0099FF\">Gnosis</font></td><td><font color=\"LEVEL\">" + scores.get(SEAL_GNOSIS) + "</font></td></tr>");
		html.append("</table><br>");
		html.append("<button value=\"How to score\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\"><tr><td>");
		html.append("<font color=\"ae9977\">Strife: PvP kills in Megarax zones.</font><br1>");
		html.append("<font color=\"ae9977\">Avarice: Seal Guardian kills in conquest.</font><br1>");
		html.append("<font color=\"ae9977\">Gnosis: Dungeon clears and craft rewards.</font>");
		html.append("</td></tr></table><br>");
		html.append("<button value=\"Active Bonuses\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\"><tr><td>");
		html.append("<font color=\"ae9977\">Strife wins: +1 extra PvP Coin in Megarax PvP zones.</font><br1>");
		html.append("<font color=\"ae9977\">Avarice wins: +5% extra Rax Coin from conquest guardians.</font><br1>");
		html.append("<font color=\"ae9977\">Gnosis wins: +5% Rax Coin from Megarax dungeon rewards.</font>");
		html.append("</td></tr></table>");
		html.append("</center></body></html>");
		
		final NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html.toString());
		player.sendPacket(msg);
	}
	
	private void initTables()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement territory = con.prepareStatement(CREATE_TERRITORY_TABLE);
			PreparedStatement ps = con.prepareStatement(CREATE_SCORE_TABLE))
		{
			territory.executeUpdate();
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOG.warning("MegaraxSevenSigns DB table init failed: " + e.getMessage());
		}
	}
	
	private static void saveScore(String cycle, String sealName, long score)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(SAVE_SCORE))
		{
			ps.setString(1, cycle);
			ps.setString(2, sealName);
			ps.setLong(3, score);
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOG.warning("MegaraxSevenSigns DB score save failed: " + e.getMessage());
		}
	}
	
	private static String getCycleKey()
	{
		return new SimpleDateFormat("yyyy-'W'ww", Locale.US).format(new Date());
	}
	
	public static void main(String[] args)
	{
		new MegaraxSevenSigns();
	}
}
