/*
 * Custom Megarax Daily Missions - L2JMobius Interlude
 */
package custom.MegaraxDailyMissions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.Containers;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.ListenerRegisterType;
import org.l2jmobius.gameserver.model.events.annotations.RegisterEvent;
import org.l2jmobius.gameserver.model.events.annotations.RegisterType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageReceived;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDeath;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureSkillUse;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerPvPKill;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.network.serverpackets.ExShowScreenMessage;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

import ai.AbstractNpcAI;
import custom.MegaraxLivingChronicle.MegaraxLivingChronicle;
import custom.MegaraxHuntContracts.MegaraxHuntContracts;
import handlers.communityboard.MercenaryBoard;

public class MegaraxDailyMissions extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxDailyMissions.class.getName());
	private static MegaraxDailyMissions INSTANCE;
	
	private static final int NPC = 9914;
	private static final int RAX_COIN = 9142;
	private static final int DUNGEON_COIN = 9143;
	private static final int CRAFT_COIN = 4356;
	private static final int PVP_BADGE = 8875;
	private static final int CONQUEST_CHEST = 6500;
	private static final int FINAL_MISSIONS_REQUIRED = 3;
	
	private static final int HUNTER_GOAL = 300;
	private static final int PVP_GOAL = 10;
	private static final int DUNGEON_GOAL = 1;
	private static final int SUPPORT_GOAL = 40;
	private static final int HEALER_GOAL = 30;
	private static final int TANK_GOAL = 50000;
	private static final int SPOILER_GOAL = 40;
	
	private static final int HUNTER_RAX = 30;
	private static final int HUNTER_DUNGEON = 15;
	private static final int PVP_RAX = 50;
	private static final int PVP_BADGES = 10;
	private static final int DUNGEON_RAX = 30;
	private static final int DUNGEON_COINS = 40;
	private static final int ROLE_RAX = 40;
	private static final int ROLE_CRAFT = 20;
	private static final int ROLE_DUNGEON = 20;
	
	private static final String TABLE_SQL = "CREATE TABLE IF NOT EXISTS megarax_daily_missions (account_name VARCHAR(45) NOT NULL, day_key VARCHAR(10) NOT NULL, hunter_kills INT NOT NULL DEFAULT 0, pvp_kills INT NOT NULL DEFAULT 0, dungeon_runs INT NOT NULL DEFAULT 0, support_actions INT NOT NULL DEFAULT 0, healer_actions INT NOT NULL DEFAULT 0, tank_damage INT NOT NULL DEFAULT 0, spoiler_actions INT NOT NULL DEFAULT 0, hunter_claimed TINYINT NOT NULL DEFAULT 0, pvp_claimed TINYINT NOT NULL DEFAULT 0, dungeon_claimed TINYINT NOT NULL DEFAULT 0, support_claimed TINYINT NOT NULL DEFAULT 0, healer_claimed TINYINT NOT NULL DEFAULT 0, tank_claimed TINYINT NOT NULL DEFAULT 0, spoiler_claimed TINYINT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL, PRIMARY KEY (account_name, day_key))";
	private static final String SELECT_SQL = "SELECT hunter_kills, pvp_kills, dungeon_runs, support_actions, healer_actions, tank_damage, spoiler_actions, hunter_claimed, pvp_claimed, dungeon_claimed, support_claimed, healer_claimed, tank_claimed, spoiler_claimed FROM megarax_daily_missions WHERE account_name=? AND day_key=?";
	private static final String INSERT_SQL = "INSERT INTO megarax_daily_missions (account_name, day_key, updated_at) VALUES (?, ?, ?)";
	private static final String UPDATE_PROGRESS_SQL = "UPDATE megarax_daily_missions SET %s=LEAST(%s + ?, ?), updated_at=? WHERE account_name=? AND day_key=?";
	private static final String UPDATE_CLAIM_SQL = "UPDATE megarax_daily_missions SET %s=1, updated_at=? WHERE account_name=? AND day_key=?";
	private static final String FINAL_STATUS_SQL = "SELECT final_claimed FROM megarax_daily_missions WHERE account_name=? AND day_key=?";
	private static final String FINAL_CLAIM_SQL = "UPDATE megarax_daily_missions SET final_claimed=1, updated_at=? WHERE account_name=? AND day_key=? AND final_claimed=0";
	
	private MegaraxDailyMissions()
	{
		INSTANCE = this;
		addStartNpc(NPC);
		addTalkId(NPC);
		addFirstTalkId(NPC);
		initTable();
		Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_PVP_KILL, (OnPlayerPvPKill event) -> onPlayerPvPKill(event), this));
		LOGGER.info("MegaraxDailyMissions: Script loaded for NPC " + NPC + ".");
	}
	
	public static void notifyDungeonComplete(Player player)
	{
		if (INSTANCE != null)
		{
			INSTANCE.addProgress(player, Mission.DUNGEON, 1);
		}
		MercenaryBoard.notifyDungeonComplete(player);
		MegaraxHuntContracts.notifyDungeonComplete(player, "MEGARAX_DUNGEON");
	}
	
	@Override
	public String onFirstTalk(Npc npc, Player player)
	{
		showMain(player);
		return null;
	}
	
	@Override
	public String onTalk(Npc npc, Player player)
	{
		showMain(player);
		return null;
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		if (player == null)
		{
			return null;
		}
		
		if (event.equals("main"))
		{
			showMain(player);
			return null;
		}
		if (event.equals("rules"))
		{
			showRules(player);
			return null;
		}
		if (event.startsWith("claim_"))
		{
			if (event.equals("claim_final"))
			{
				claimFinalReward(player);
			}
			else
			{
				claim(player, event.substring(6));
			}
			showMain(player);
			return null;
		}
		
		showMain(player);
		return null;
	}
	
	@RegisterEvent(EventType.ON_CREATURE_DEATH)
	@RegisterType(ListenerRegisterType.GLOBAL)
	public void onCreatureDeath(OnCreatureDeath event)
	{
		if ((event == null) || (event.getAttacker() == null) || (event.getTarget() == null))
		{
			return;
		}
		if (!event.getAttacker().isPlayer() || !event.getTarget().isMonster())
		{
			return;
		}
		
		final Player killer = event.getAttacker().asPlayer();
		final Npc killed = event.getTarget().asNpc();
		if ((killer == null) || killer.isGM() || killed.isRaid() || killed.isRaidMinion())
		{
			return;
		}
		if ((killer.getLevel() - killed.getLevel()) > 9)
		{
			return;
		}
		
		addProgress(killer, Mission.HUNTER, 1);
	}
	
	private void onPlayerPvPKill(OnPlayerPvPKill event)
	{
		final Player killer = event.getPlayer();
		final Player target = event.getTarget();
		if ((killer == null) || (target == null) || killer.isGM() || target.isGM())
		{
			return;
		}
		if (killer.getObjectId() == target.getObjectId())
		{
			return;
		}
		if (isSameRealPlayer(killer, target) || isSameClan(killer, target))
		{
			killer.sendMessage("Daily PvP progress skipped: anti-feed protection is active.");
			return;
		}
		
		addProgress(killer, Mission.PVP, 1);
		MercenaryBoard.notifyPvPKill(killer);
	}
	
	@RegisterEvent(EventType.ON_CREATURE_SKILL_USE)
	@RegisterType(ListenerRegisterType.GLOBAL)
	public void onCreatureSkillUse(OnCreatureSkillUse event)
	{
		if ((event == null) || (event.getCaster() == null) || !event.getCaster().isPlayer() || (event.getSkill() == null))
		{
			return;
		}
		
		final Player caster = event.getCaster().asPlayer();
		if ((caster == null) || caster.isGM())
		{
			return;
		}
		
		final Skill skill = event.getSkill();
		if (isSpoilSkill(skill) && (event.getTarget() != null) && event.getTarget().isMonster())
		{
			addProgress(caster, Mission.SPOILER, 1);
			MercenaryBoard.notifySpoilAction(caster);
			return;
		}
		
		if ((event.getTarget() == null) || !event.getTarget().isPlayer() || (event.getTarget().getObjectId() == caster.getObjectId()))
		{
			return;
		}
		
		if (isHealerSkill(skill))
		{
			addProgress(caster, Mission.HEALER, 1);
		}
		else if (isSupportSkill(skill))
		{
			addProgress(caster, Mission.SUPPORT, 1);
		}
	}
	
	@RegisterEvent(EventType.ON_CREATURE_DAMAGE_RECEIVED)
	@RegisterType(ListenerRegisterType.GLOBAL)
	public void onCreatureDamageReceived(OnCreatureDamageReceived event)
	{
		if ((event == null) || (event.getTarget() == null) || !event.getTarget().isPlayer() || (event.getAttacker() == null) || (event.getDamage() <= 0))
		{
			return;
		}
		
		final Player tank = event.getTarget().asPlayer();
		if ((tank == null) || tank.isGM() || !isTankClass(tank))
		{
			return;
		}
		
		if (!event.getAttacker().isMonster() && !event.getAttacker().isPlayer())
		{
			return;
		}
		
		addProgress(tank, Mission.TANK, Math.min(5000, (int) event.getDamage()));
	}
	
	private boolean isSpoilSkill(Skill skill)
	{
		return (skill.getId() == 254) || (skill.getId() == 42);
	}
	
	private boolean isHealerSkill(Skill skill)
	{
		final String name = skill.getName().toLowerCase();
		return name.contains("heal") || name.contains("recharge") || name.contains("resurrection") || name.contains("restore life");
	}
	
	private boolean isSupportSkill(Skill skill)
	{
		if (skill.isDebuff() || skill.isDamage() || !skill.isContinuous())
		{
			return false;
		}
		
		final String name = skill.getName().toLowerCase();
		return name.contains("song") || name.contains("dance") || name.contains("chant") || name.contains("prophecy") || name.contains("blessing") || name.contains("might") || name.contains("shield") || name.contains("empower") || name.contains("haste") || name.contains("focus") || name.contains("death whisper") || name.contains("guidance") || name.contains("acumen") || name.contains("concentration") || name.contains("wind walk") || name.contains("vampiric") || name.contains("berserker");
	}
	
	private boolean isTankClass(Player player)
	{
		switch (player.getPlayerClass())
		{
			case PALADIN:
			case DARK_AVENGER:
			case TEMPLE_KNIGHT:
			case SHILLIEN_KNIGHT:
			case PHOENIX_KNIGHT:
			case HELL_KNIGHT:
			case EVA_TEMPLAR:
			case SHILLIEN_TEMPLAR:
				return true;
			default:
				return false;
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
	
	private void addProgress(Player player, Mission mission, int amount)
	{
		if ((player == null) || (amount <= 0))
		{
			return;
		}
		
		final String account = player.getAccountName();
		final String day = getDayKey();
		ensureRow(account, day);
		final Progress previousProgress = getProgress(player);
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(String.format(UPDATE_PROGRESS_SQL, mission.progressColumn, mission.progressColumn)))
		{
			ps.setInt(1, amount);
			ps.setInt(2, mission.goal);
			ps.setLong(3, System.currentTimeMillis());
			ps.setString(4, account);
			ps.setString(5, day);
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxDailyMissions: Could not update progress: " + e.getMessage());
			return;
		}
		
		final Progress progress = getProgress(player);
		if ((progress != null) && (previousProgress != null) && !progress.isClaimed(mission) && (previousProgress.getValue(mission) < mission.goal) && (progress.getValue(mission) >= mission.goal))
		{
			player.sendPacket(new ExShowScreenMessage("Daily Mission Complete: " + mission.title, 3000));
		}
	}
	
	private void claim(Player player, String id)
	{
		final Mission mission = Mission.find(id);
		if (mission == null)
		{
			return;
		}
		
		final Progress progress = getProgress(player);
		if (progress == null)
		{
			player.sendMessage("Daily mission progress is not available.");
			return;
		}
		if (progress.isClaimed(mission))
		{
			player.sendMessage("This daily mission reward has already been claimed.");
			return;
		}
		if (progress.getValue(mission) < mission.goal)
		{
			player.sendMessage("This daily mission is not complete yet.");
			return;
		}
		
		if (!setClaimed(player, mission))
		{
			player.sendMessage("Could not claim the daily mission reward.");
			return;
		}
		
		for (Reward reward : mission.rewards)
		{
			player.addItem(ItemProcessType.REWARD, reward.itemId, reward.count, player, true);
		}
		player.sendMessage("Daily Mission reward claimed: " + mission.title + ".");
	}
	
	private boolean setClaimed(Player player, Mission mission)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(String.format(UPDATE_CLAIM_SQL, mission.claimColumn)))
		{
			ps.setLong(1, System.currentTimeMillis());
			ps.setString(2, player.getAccountName());
			ps.setString(3, getDayKey());
			return ps.executeUpdate() > 0;
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxDailyMissions: Could not set claimed: " + e.getMessage());
		}
		return false;
	}

	private void claimFinalReward(Player player)
	{
		final Progress progress = getProgress(player);
		if ((progress == null) || !hasRequiredMissionsComplete(progress))
		{
			player.sendMessage("Complete any " + FINAL_MISSIONS_REQUIRED + " daily missions before claiming the Conquest Chest.");
			return;
		}
		if (isFinalClaimed(player))
		{
			player.sendMessage("Today's Megarax Conquest Chest has already been claimed.");
			return;
		}
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(FINAL_CLAIM_SQL))
		{
			ps.setLong(1, System.currentTimeMillis());
			ps.setString(2, player.getAccountName());
			ps.setString(3, getDayKey());
			if (ps.executeUpdate() > 0)
			{
				player.addItem(ItemProcessType.REWARD, CONQUEST_CHEST, 1, player, true);
				MegaraxLivingChronicle.recordEvent(player, "VOCATION", "first_daily_paths", "Completo tres caminos diarios", "Demostro que cada vocacion puede sostener a Megarax.", 2, false);
				player.sendPacket(new ExShowScreenMessage("Three Daily Paths Complete: Megarax Conquest Chest!", 5000));
				player.sendMessage("You received 1 Megarax Conquest Chest.");
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxDailyMissions: Could not claim final reward: " + e.getMessage());
		}
	}

	private boolean hasRequiredMissionsComplete(Progress progress)
	{
		int completed = 0;
		for (Mission mission : Mission.values())
		{
			if (progress.getValue(mission) >= mission.goal)
			{
				completed++;
			}
		}
		return completed >= FINAL_MISSIONS_REQUIRED;
	}

	private int getCompletedMissionCount(Progress progress)
	{
		if (progress == null)
		{
			return 0;
		}
		int completed = 0;
		for (Mission mission : Mission.values())
		{
			if (progress.getValue(mission) >= mission.goal)
			{
				completed++;
			}
		}
		return completed;
	}

	private boolean isFinalClaimed(Player player)
	{
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(FINAL_STATUS_SQL))
		{
			ps.setString(1, player.getAccountName());
			ps.setString(2, getDayKey());
			try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getBoolean(1); }
		}
		catch (SQLException e) { LOGGER.warning("MegaraxDailyMissions: Could not read final reward: " + e.getMessage()); }
		return false;
	}
	
	private Progress getProgress(Player player)
	{
		final String account = player.getAccountName();
		final String day = getDayKey();
		ensureRow(account, day);
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(SELECT_SQL))
		{
			ps.setString(1, account);
			ps.setString(2, day);
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					return new Progress(rs.getInt("hunter_kills"), rs.getInt("pvp_kills"), rs.getInt("dungeon_runs"), rs.getInt("support_actions"), rs.getInt("healer_actions"), rs.getInt("tank_damage"), rs.getInt("spoiler_actions"), rs.getBoolean("hunter_claimed"), rs.getBoolean("pvp_claimed"), rs.getBoolean("dungeon_claimed"), rs.getBoolean("support_claimed"), rs.getBoolean("healer_claimed"), rs.getBoolean("tank_claimed"), rs.getBoolean("spoiler_claimed"));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxDailyMissions: Could not read progress: " + e.getMessage());
		}
		return null;
	}
	
	private void ensureRow(String account, String day)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement select = con.prepareStatement(SELECT_SQL))
		{
			select.setString(1, account);
			select.setString(2, day);
			try (ResultSet rs = select.executeQuery())
			{
				if (rs.next())
				{
					return;
				}
			}
			
			try (PreparedStatement insert = con.prepareStatement(INSERT_SQL))
			{
				insert.setString(1, account);
				insert.setString(2, day);
				insert.setLong(3, System.currentTimeMillis());
				insert.executeUpdate();
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxDailyMissions: Could not ensure daily row: " + e.getMessage());
		}
	}
	
	private void initTable()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(TABLE_SQL))
		{
			ps.executeUpdate();
			addColumnIfMissing(con, "support_actions", "INT NOT NULL DEFAULT 0");
			addColumnIfMissing(con, "healer_actions", "INT NOT NULL DEFAULT 0");
			addColumnIfMissing(con, "tank_damage", "INT NOT NULL DEFAULT 0");
			addColumnIfMissing(con, "spoiler_actions", "INT NOT NULL DEFAULT 0");
			addColumnIfMissing(con, "support_claimed", "TINYINT NOT NULL DEFAULT 0");
			addColumnIfMissing(con, "healer_claimed", "TINYINT NOT NULL DEFAULT 0");
			addColumnIfMissing(con, "tank_claimed", "TINYINT NOT NULL DEFAULT 0");
			addColumnIfMissing(con, "spoiler_claimed", "TINYINT NOT NULL DEFAULT 0");
			addColumnIfMissing(con, "final_claimed", "TINYINT NOT NULL DEFAULT 0");
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxDailyMissions: Could not create table: " + e.getMessage());
		}
	}
	
	private void addColumnIfMissing(Connection con, String column, String definition)
	{
		try (PreparedStatement ps = con.prepareStatement("ALTER TABLE megarax_daily_missions ADD COLUMN " + column + " " + definition))
		{
			ps.executeUpdate();
		}
		catch (SQLException ignored)
		{
			// Column already exists on updated databases.
		}
	}
	
	private void showMain(Player player)
	{
		final Progress progress = getProgress(player);
		final NpcHtmlMessage html = new NpcHtmlMessage();
		final StringBuilder sb = new StringBuilder();
		sb.append("<html><body><center>");
		sb.append("<img src=\"L2UI_CH3.herotower_deco\" width=256 height=32>");
		sb.append("<font color=\"LEVEL\">Megarax Daily Missions</font><br1>");
		sb.append("<font color=\"ae9977\">Reset diario por cuenta: ").append(getDayKey()).append("</font><br>");
		sb.append("<table width=292 cellpadding=3 cellspacing=0>");
		appendMission(sb, Mission.HUNTER, progress);
		appendMission(sb, Mission.PVP, progress);
		appendMission(sb, Mission.DUNGEON, progress);
		appendMission(sb, Mission.SUPPORT, progress);
		appendMission(sb, Mission.HEALER, progress);
		appendMission(sb, Mission.TANK, progress);
		appendMission(sb, Mission.SPOILER, progress);
		sb.append("</table><br>");
		final boolean finalReady = (progress != null) && hasRequiredMissionsComplete(progress);
		final boolean finalClaimed = isFinalClaimed(player);
		sb.append("<font color=\"LEVEL\">Daily Completion Reward</font><br1>");
		sb.append("<font color=\"ae9977\">Complete any ").append(FINAL_MISSIONS_REQUIRED).append(" missions to receive 1 Megarax Conquest Chest. Progress: ").append(getCompletedMissionCount(progress)).append("/").append(FINAL_MISSIONS_REQUIRED).append(".</font><br1>");
		sb.append("<button value=\"").append(finalClaimed ? "Claimed" : finalReady ? "Claim Chest" : "Locked").append("\" action=\"").append(finalReady && !finalClaimed ? "bypass -h Quest MegaraxDailyMissions claim_final" : "bypass -h Quest MegaraxDailyMissions main").append("\" width=134 height=21 back=\"L2UI_ch3.BigButton3_down\" fore=\"L2UI_ch3.BigButton3\"><br>");
		sb.append("<button value=\"Rules\" action=\"bypass -h Quest MegaraxDailyMissions rules\" width=134 height=21 back=\"L2UI_ch3.BigButton3_down\" fore=\"L2UI_ch3.BigButton3\">");
		sb.append("</center></body></html>");
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}
	
	private void appendMission(StringBuilder sb, Mission mission, Progress progress)
	{
		final int value = progress == null ? 0 : progress.getValue(mission);
		final boolean claimed = progress != null && progress.isClaimed(mission);
		final boolean complete = value >= mission.goal;
		final String status = claimed ? "CLAIMED" : complete ? "READY" : (value + "/" + mission.goal);
		final String button = claimed ? "Claimed" : complete ? "Claim" : "Locked";
		final String action = complete && !claimed ? "bypass -h Quest MegaraxDailyMissions claim_" + mission.id : "bypass -h Quest MegaraxDailyMissions main";
		
		sb.append("<tr><td width=194>");
		sb.append("<font color=\"LEVEL\">").append(mission.title).append("</font><br1>");
		sb.append("<font color=\"b09979\">").append(mission.description).append("</font><br1>");
		sb.append("<font color=\"70ffca\">").append(status).append("</font>");
		sb.append("</td><td width=88 align=center>");
		sb.append("<button value=\"").append(button).append("\" action=\"").append(action).append("\" width=80 height=21 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\">");
		sb.append("</td></tr>");
	}
	
	private void showRules(Player player)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage();
		final StringBuilder sb = new StringBuilder();
		sb.append("<html><body><center>");
		sb.append("<img src=\"L2UI_CH3.herotower_deco\" width=256 height=32>");
		sb.append("<font color=\"LEVEL\">Daily Mission Rules</font><br>");
		sb.append("<table width=292><tr><td>");
		sb.append("<font color=\"ae9977\">Progress is shared by account and resets each real day. Complete any three paths; no character is expected to perform all seven professions. Hunter does not count raid bosses, raid minions, GM kills, or mobs more than 9 levels below you. Role missions reward useful party actions: buffs, heals, tanking and spoil/sweeper.</font>");
		sb.append("</td></tr></table><br>");
		sb.append("<button value=\"Back\" action=\"bypass -h Quest MegaraxDailyMissions main\" width=134 height=21 back=\"L2UI_ch3.BigButton3_down\" fore=\"L2UI_ch3.BigButton3\">");
		sb.append("</center></body></html>");
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}
	
	private String getDayKey()
	{
		return LocalDate.now().toString();
	}
	
	private enum Mission
	{
		HUNTER("hunter", "Daily Hunter", "Kill 300 monsters.", HUNTER_GOAL, "hunter_kills", "hunter_claimed", new Reward(RAX_COIN, HUNTER_RAX), new Reward(DUNGEON_COIN, HUNTER_DUNGEON)),
		PVP("pvp", "Daily PvP", "Win 10 PvP kills.", PVP_GOAL, "pvp_kills", "pvp_claimed", new Reward(PVP_BADGE, PVP_BADGES), new Reward(RAX_COIN, PVP_RAX)),
		DUNGEON("dungeon", "Daily Dungeon", "Complete 1 dungeon.", DUNGEON_GOAL, "dungeon_runs", "dungeon_claimed", new Reward(DUNGEON_COIN, DUNGEON_COINS), new Reward(RAX_COIN, DUNGEON_RAX)),
		SUPPORT("support", "Daily Support", "Buff 40 allies.", SUPPORT_GOAL, "support_actions", "support_claimed", new Reward(RAX_COIN, ROLE_RAX), new Reward(DUNGEON_COIN, ROLE_DUNGEON)),
		HEALER("healer", "Daily Healer", "Heal or recharge 30 allies.", HEALER_GOAL, "healer_actions", "healer_claimed", new Reward(RAX_COIN, ROLE_RAX), new Reward(DUNGEON_COIN, ROLE_DUNGEON)),
		TANK("tank", "Daily Tank", "Receive 50,000 damage.", TANK_GOAL, "tank_damage", "tank_claimed", new Reward(RAX_COIN, ROLE_RAX), new Reward(DUNGEON_COIN, ROLE_DUNGEON)),
		SPOILER("spoiler", "Daily Spoiler", "Use Spoil/Sweeper 40 times.", SPOILER_GOAL, "spoiler_actions", "spoiler_claimed", new Reward(CRAFT_COIN, ROLE_CRAFT), new Reward(RAX_COIN, ROLE_RAX));
		
		private final String id;
		private final String title;
		private final String description;
		private final int goal;
		private final String progressColumn;
		private final String claimColumn;
		private final Reward[] rewards;
		
		Mission(String id, String title, String description, int goal, String progressColumn, String claimColumn, Reward... rewards)
		{
			this.id = id;
			this.title = title;
			this.description = description;
			this.goal = goal;
			this.progressColumn = progressColumn;
			this.claimColumn = claimColumn;
			this.rewards = rewards;
		}
		
		private static Mission find(String id)
		{
			for (Mission mission : values())
			{
				if (mission.id.equals(id))
				{
					return mission;
				}
			}
			return null;
		}
	}
	
	private static class Reward
	{
		private final int itemId;
		private final int count;
		
		private Reward(int itemId, int count)
		{
			this.itemId = itemId;
			this.count = count;
		}
	}
	
	private static class Progress
	{
		private final int hunterKills;
		private final int pvpKills;
		private final int dungeonRuns;
		private final int supportActions;
		private final int healerActions;
		private final int tankDamage;
		private final int spoilerActions;
		private final boolean hunterClaimed;
		private final boolean pvpClaimed;
		private final boolean dungeonClaimed;
		private final boolean supportClaimed;
		private final boolean healerClaimed;
		private final boolean tankClaimed;
		private final boolean spoilerClaimed;
		
		private Progress(int hunterKills, int pvpKills, int dungeonRuns, int supportActions, int healerActions, int tankDamage, int spoilerActions, boolean hunterClaimed, boolean pvpClaimed, boolean dungeonClaimed, boolean supportClaimed, boolean healerClaimed, boolean tankClaimed, boolean spoilerClaimed)
		{
			this.hunterKills = hunterKills;
			this.pvpKills = pvpKills;
			this.dungeonRuns = dungeonRuns;
			this.supportActions = supportActions;
			this.healerActions = healerActions;
			this.tankDamage = tankDamage;
			this.spoilerActions = spoilerActions;
			this.hunterClaimed = hunterClaimed;
			this.pvpClaimed = pvpClaimed;
			this.dungeonClaimed = dungeonClaimed;
			this.supportClaimed = supportClaimed;
			this.healerClaimed = healerClaimed;
			this.tankClaimed = tankClaimed;
			this.spoilerClaimed = spoilerClaimed;
		}
		
		private int getValue(Mission mission)
		{
			switch (mission)
			{
				case HUNTER:
					return hunterKills;
				case PVP:
					return pvpKills;
				case DUNGEON:
					return dungeonRuns;
				case SUPPORT:
					return supportActions;
				case HEALER:
					return healerActions;
				case TANK:
					return tankDamage;
				case SPOILER:
					return spoilerActions;
			}
			return 0;
		}
		
		private boolean isClaimed(Mission mission)
		{
			switch (mission)
			{
				case HUNTER:
					return hunterClaimed;
				case PVP:
					return pvpClaimed;
				case DUNGEON:
					return dungeonClaimed;
				case SUPPORT:
					return supportClaimed;
				case HEALER:
					return healerClaimed;
				case TANK:
					return tankClaimed;
				case SPOILER:
					return spoilerClaimed;
			}
			return false;
		}
	}
	
	public static void main(String[] args)
	{
		new MegaraxDailyMissions();
	}
}
