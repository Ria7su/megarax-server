/*
 * SV Megarax x10 - Living Chronicle.
 * Additive history layer. Existing systems can publish milestones through the
 * public recordEvent helpers without depending on rewards or rates.
 */
package custom.MegaraxLivingChronicle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.Containers;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDeath;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLevelChanged;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogin;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerProfessionChange;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.util.Broadcast;

import ai.AbstractNpcAI;

public class MegaraxLivingChronicle extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxLivingChronicle.class.getName());
	private static final String OWNER_CLASS = MegaraxLivingChronicle.class.getName();
	
	private static final String CREATE_PLAYERS = "CREATE TABLE IF NOT EXISTS megarax_chronicle_players (player_id INT NOT NULL, account_name VARCHAR(45) NOT NULL, player_name VARCHAR(35) NOT NULL, first_seen BIGINT NOT NULL, last_seen BIGINT NOT NULL, legacy_points INT NOT NULL DEFAULT 0, milestone_count INT NOT NULL DEFAULT 0, profession_count INT NOT NULL DEFAULT 0, raid_count INT NOT NULL DEFAULT 0, PRIMARY KEY (player_id), KEY idx_chronicle_player_name (player_name), KEY idx_chronicle_legacy (legacy_points), KEY idx_chronicle_last_seen (last_seen)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String CREATE_EVENTS = "CREATE TABLE IF NOT EXISTS megarax_chronicle_events (event_id BIGINT NOT NULL AUTO_INCREMENT, player_id INT NOT NULL, player_name VARCHAR(35) NOT NULL, event_type VARCHAR(32) NOT NULL, event_key VARCHAR(128) NOT NULL, title VARCHAR(96) NOT NULL, details VARCHAR(255) NOT NULL DEFAULT '', legacy_points INT NOT NULL DEFAULT 0, event_time BIGINT NOT NULL, is_public TINYINT NOT NULL DEFAULT 1, PRIMARY KEY (event_id), UNIQUE KEY uq_chronicle_player_event (player_id,event_type,event_key), KEY idx_chronicle_event_time (event_time), KEY idx_chronicle_event_type (event_type)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String CREATE_FIRSTS = "CREATE TABLE IF NOT EXISTS megarax_chronicle_firsts (achievement_key VARCHAR(160) NOT NULL, event_type VARCHAR(32) NOT NULL, player_id INT NOT NULL, player_name VARCHAR(35) NOT NULL, title VARCHAR(96) NOT NULL, details VARCHAR(255) NOT NULL DEFAULT '', event_time BIGINT NOT NULL, PRIMARY KEY (achievement_key), KEY idx_chronicle_first_time (event_time)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	
	private static final String UPSERT_PLAYER = "INSERT INTO megarax_chronicle_players (player_id,account_name,player_name,first_seen,last_seen) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE account_name=VALUES(account_name),player_name=VALUES(player_name),last_seen=VALUES(last_seen)";
	private static final String INSERT_EVENT = "INSERT IGNORE INTO megarax_chronicle_events (player_id,player_name,event_type,event_key,title,details,legacy_points,event_time,is_public) VALUES (?,?,?,?,?,?,?,?,?)";
	private static final String INSERT_FIRST = "INSERT IGNORE INTO megarax_chronicle_firsts (achievement_key,event_type,player_id,player_name,title,details,event_time) VALUES (?,?,?,?,?,?,?)";
	private static final String UPDATE_SUMMARY = "UPDATE megarax_chronicle_players SET legacy_points=legacy_points+?,milestone_count=milestone_count+?,profession_count=profession_count+?,raid_count=raid_count+?,last_seen=? WHERE player_id=?";
	private static final String SELECT_SUMMARY = "SELECT first_seen,last_seen,legacy_points,milestone_count,profession_count,raid_count FROM megarax_chronicle_players WHERE player_id=?";
	private static final String SELECT_EVENTS = "SELECT title,details,legacy_points,event_time FROM megarax_chronicle_events WHERE player_id=? ORDER BY event_time DESC,event_id DESC LIMIT ?";
	private static final String SELECT_FIRSTS = "SELECT player_name,title,details,event_time FROM megarax_chronicle_firsts ORDER BY event_time DESC LIMIT ?";
	
	private static MegaraxLivingChronicle INSTANCE;
	
	private MegaraxLivingChronicle()
	{
		LivingChronicleConfig.load();
		if (!LivingChronicleConfig.ENABLED)
		{
			LOGGER.info("LivingChronicle: Disabled by configuration.");
			return;
		}
		
		INSTANCE = this;
		initTables();
		removePreviousListeners();
		registerListeners();
		addStartNpc(LivingChronicleConfig.NPC_ID);
		addTalkId(LivingChronicleConfig.NPC_ID);
		addFirstTalkId(LivingChronicleConfig.NPC_ID);
		LOGGER.info("LivingChronicle: Loaded for NPC " + LivingChronicleConfig.NPC_ID + ".");
	}
	
	private void removePreviousListeners()
	{
		Containers.Players().removeListenerIf(listener -> isPreviousOwner(listener.getOwner()));
		Containers.Global().removeListenerIf(listener -> isPreviousOwner(listener.getOwner()));
	}
	
	private boolean isPreviousOwner(Object owner)
	{
		return (owner != null) && OWNER_CLASS.equals(owner.getClass().getName());
	}
	
	private void registerListeners()
	{
		Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_LOGIN, (OnPlayerLogin event) -> onPlayerLogin(event), this));
		Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_LEVEL_CHANGED, (OnPlayerLevelChanged event) -> onPlayerLevelChanged(event), this));
		Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_PROFESSION_CHANGE, (OnPlayerProfessionChange event) -> onPlayerProfessionChange(event), this));
		Containers.Global().addListener(new ConsumerEventListener(Containers.Global(), EventType.ON_CREATURE_DEATH, (OnCreatureDeath event) -> onCreatureDeath(event), this));
	}
	
	private void onPlayerLogin(OnPlayerLogin event)
	{
		final Player player = event == null ? null : event.getPlayer();
		if ((player == null) || player.isGM())
		{
			return;
		}
		ensurePlayer(player);
		if (LivingChronicleConfig.RECORD_ARRIVAL)
		{
			recordUniqueEvent(player, "ARRIVAL", "arrival", "Llego a Megarax", "Su nombre entro en la Cronica Viva.", 1, false, true);
		}
		if (LivingChronicleConfig.RECORD_LEVEL_MILESTONES)
		{
			for (int milestone : LivingChronicleConfig.LEVEL_MILESTONES)
			{
				if (player.getLevel() >= milestone)
				{
					recordLevelMilestone(player, milestone, false);
				}
			}
		}
	}
	
	private void onPlayerLevelChanged(OnPlayerLevelChanged event)
	{
		if (!LivingChronicleConfig.RECORD_LEVEL_MILESTONES || (event == null) || (event.getPlayer() == null) || event.getPlayer().isGM())
		{
			return;
		}
		for (int milestone : LivingChronicleConfig.LEVEL_MILESTONES)
		{
			if ((event.getOldLevel() < milestone) && (event.getNewLevel() >= milestone))
			{
				recordLevelMilestone(event.getPlayer(), milestone, true);
			}
		}
	}
	
	private void recordLevelMilestone(Player player, int milestone, boolean eligibleForFirst)
	{
		recordUniqueEvent(player, "LEVEL", "level:" + milestone, "Alcanzo nivel " + milestone, "Un nuevo capitulo de su viaje comenzo.", levelPoints(milestone), eligibleForFirst, true);
	}
	
	private void onPlayerProfessionChange(OnPlayerProfessionChange event)
	{
		if (!LivingChronicleConfig.RECORD_PROFESSION_CHANGES || (event == null) || (event.getPlayer() == null) || event.getPlayer().isGM() || (event.getTemplate() == null) || (event.getTemplate().getPlayerClass() == null))
		{
			return;
		}
		final Player player = event.getPlayer();
		final int classId = event.getTemplate().getPlayerClass().getId();
		final String className = friendlyName(event.getTemplate().getPlayerClass().name());
		final String prefix = event.isSubClass() ? "subclass:" : "class:";
		final String title = event.isSubClass() ? "Adopto la subclase " + className : "Se convirtio en " + className;
		recordUniqueEvent(player, "PROFESSION", prefix + classId, title, "Su profesion quedo inscrita en Megarax.", event.isSubClass() ? 2 : 3, true, true);
	}
	
	private void onCreatureDeath(OnCreatureDeath event)
	{
		if (!LivingChronicleConfig.RECORD_RAID_VICTORIES || (event == null) || (event.getAttacker() == null) || (event.getTarget() == null) || !event.getTarget().isNpc())
		{
			return;
		}
		final Npc raid = event.getTarget().asNpc();
		if (!raid.isRaid() || raid.isRaidMinion())
		{
			return;
		}
		
		final Player killer = getActingPlayer(event.getAttacker());
		if ((killer == null) || killer.isGM())
		{
			return;
		}
		
		final Set<Player> participants = new LinkedHashSet<>();
		participants.add(killer);
		if (killer.getParty() != null)
		{
			for (Player member : killer.getParty().getMembers())
			{
				if ((member != null) && !member.isGM() && member.isInsideRadius2D(raid, LivingChronicleConfig.RAID_PARTICIPANT_RANGE))
				{
					participants.add(member);
				}
			}
		}
		
		final String raidName = cleanText(raid.getName(), 60);
		for (Player participant : participants)
		{
			recordUniqueEvent(participant, "RAID", "raid:" + raid.getId(), "Vencio a " + raidName, "Participo en una expedicion registrada.", 5, participant.getObjectId() == killer.getObjectId(), true);
		}
	}
	
	private Player getActingPlayer(Creature creature)
	{
		if (creature.isPlayer())
		{
			return creature.asPlayer();
		}
		final Creature summoner = creature.getSummoner();
		return ((summoner != null) && summoner.isPlayer()) ? summoner.asPlayer() : null;
	}
	
	public static boolean recordEvent(Player player, String eventType, String eventKey, String title, String details, int legacyPoints, boolean eligibleForServerFirst)
	{
		return (INSTANCE != null) && INSTANCE.recordUniqueEvent(player, eventType, eventKey, title, details, legacyPoints, eligibleForServerFirst, true);
	}
	
	public static boolean recordPrivateEvent(Player player, String eventType, String eventKey, String title, String details, int legacyPoints)
	{
		return (INSTANCE != null) && INSTANCE.recordUniqueEvent(player, eventType, eventKey, title, details, legacyPoints, false, false);
	}
	
	private boolean recordUniqueEvent(Player player, String eventType, String eventKey, String title, String details, int legacyPoints, boolean eligibleForServerFirst, boolean isPublic)
	{
		if ((player == null) || player.isGM())
		{
			return false;
		}
		final String safeType = cleanKey(eventType, 32);
		final String safeKey = cleanKey(eventKey, 128);
		final String safeTitle = cleanText(title, 96);
		final String safeDetails = cleanText(details, 255);
		if (safeType.isEmpty() || safeKey.isEmpty() || safeTitle.isEmpty())
		{
			return false;
		}
		
		ensurePlayer(player);
		final int points = Math.max(0, legacyPoints);
		final long now = System.currentTimeMillis();
		boolean firstClaimed = false;
		try (Connection con = DatabaseFactory.getConnection())
		{
			con.setAutoCommit(false);
			try
			{
				final int inserted;
				try (PreparedStatement ps = con.prepareStatement(INSERT_EVENT))
				{
					ps.setInt(1, player.getObjectId());
					ps.setString(2, player.getName());
					ps.setString(3, safeType);
					ps.setString(4, safeKey);
					ps.setString(5, safeTitle);
					ps.setString(6, safeDetails);
					ps.setInt(7, points);
					ps.setLong(8, now);
					ps.setInt(9, isPublic ? 1 : 0);
					inserted = ps.executeUpdate();
				}
				if (inserted == 0)
				{
					con.rollback();
					return false;
				}
				
				try (PreparedStatement ps = con.prepareStatement(UPDATE_SUMMARY))
				{
					ps.setInt(1, points);
					ps.setInt(2, "LEVEL".equals(safeType) ? 1 : 0);
					ps.setInt(3, "PROFESSION".equals(safeType) ? 1 : 0);
					ps.setInt(4, "RAID".equals(safeType) ? 1 : 0);
					ps.setLong(5, now);
					ps.setInt(6, player.getObjectId());
					ps.executeUpdate();
				}
				
				if (eligibleForServerFirst)
				{
					try (PreparedStatement ps = con.prepareStatement(INSERT_FIRST))
					{
						ps.setString(1, safeType + ":" + safeKey);
						ps.setString(2, safeType);
						ps.setInt(3, player.getObjectId());
						ps.setString(4, player.getName());
						ps.setString(5, safeTitle);
						ps.setString(6, safeDetails);
						ps.setLong(7, now);
						firstClaimed = ps.executeUpdate() > 0;
					}
				}
				con.commit();
			}
			catch (Exception e)
			{
				con.rollback();
				throw e;
			}
			finally
			{
				con.setAutoCommit(true);
			}
		}
		catch (Exception e)
		{
			LOGGER.log(Level.WARNING, "LivingChronicle: Could not record " + safeType + "/" + safeKey + " for " + player.getName() + ".", e);
			return false;
		}
		
		player.sendMessage("Cronica Viva: " + safeTitle + " (" + points + " legado).");
		if (firstClaimed && LivingChronicleConfig.ANNOUNCE_SERVER_FIRSTS)
		{
			Broadcast.toAllOnlinePlayers("Cronica Viva: " + player.getName() + " logro un primer hito: " + safeTitle + ".");
		}
		return true;
	}
	
	private void ensurePlayer(Player player)
	{
		final long now = System.currentTimeMillis();
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(UPSERT_PLAYER))
		{
			ps.setInt(1, player.getObjectId());
			ps.setString(2, cleanText(player.getAccountName(), 45));
			ps.setString(3, cleanText(player.getName(), 35));
			ps.setLong(4, now);
			ps.setLong(5, now);
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "LivingChronicle: Could not update player " + player.getName() + ".", e);
		}
	}
	
	@Override
	public String onFirstTalk(Npc npc, Player player)
	{
		showMain(npc, player);
		return null;
	}
	
	@Override
	public String onTalk(Npc npc, Player player)
	{
		showMain(npc, player);
		return null;
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		if (player == null)
		{
			return null;
		}
		if ("history".equals(event))
		{
			showHistory(npc, player);
		}
		else if ("firsts".equals(event))
		{
			showFirsts(npc, player);
		}
		else
		{
			showMain(npc, player);
		}
		return null;
	}
	
	private void showMain(Npc npc, Player player)
	{
		ensurePlayer(player);
		final Summary summary = loadSummary(player.getObjectId());
		final StringBuilder html = pageStart("Cronica Viva");
		header(html, "LA CRONICA VIVA");
		html.append("<font color=B09878>Megarax recuerda a quienes construyen su historia.</font><br>");
		html.append("<font color=777777>Cada hito deja una huella que no desaparece.</font><br><br>");
		html.append("<table width=278 cellpadding=3 cellspacing=0 bgcolor=\"000000\">");
		html.append("<tr><td colspan=2 align=center><font color=C94A4A>REGISTRO DEL VIAJERO</font></td></tr>");
		html.append("<tr><td colspan=2><img src=\"L2UI.SquareGray\" width=272 height=1></td></tr>");
		row(html, "Nombre", escape(player.getName()));
		row(html, "Primera llegada", formatDate(summary.firstSeen));
		row(html, "Puntos de legado", Integer.toString(summary.legacyPoints));
		row(html, "Hitos de nivel", Integer.toString(summary.milestones));
		row(html, "Profesiones", Integer.toString(summary.professions));
		row(html, "Raids distintas", Integer.toString(summary.raids));
		html.append("</table><br>");
		longButton(html, "MI HISTORIA", "bypass -h Quest MegaraxLivingChronicle history");
		html.append("<br1>");
		longButton(html, "PRIMEROS HITOS", "bypass -h Quest MegaraxLivingChronicle firsts");
		send(npc, player, pageEnd(html));
	}
	
	private void showHistory(Npc npc, Player player)
	{
		final StringBuilder html = pageStart("Mi Historia");
		header(html, "MI HISTORIA");
		int count = 0;
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(SELECT_EVENTS))
		{
			ps.setInt(1, player.getObjectId());
			ps.setInt(2, LivingChronicleConfig.RECENT_EVENT_LIMIT);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					count++;
					html.append("<table width=278 cellpadding=3 cellspacing=0 bgcolor=\"000000\"><tr><td width=178>");
					html.append("<font color=LEVEL>").append(escape(rs.getString("title"))).append("</font></td>");
					html.append("<td width=100 align=right><font color=777777>").append(formatDate(rs.getLong("event_time"))).append("</font></td></tr>");
					html.append("<tr><td colspan=2><font color=B09878>").append(escape(rs.getString("details"))).append("</font>");
					if (rs.getInt("legacy_points") > 0)
					{
						html.append(" <font color=F0D58A>+").append(rs.getInt("legacy_points")).append(" legado</font>");
					}
					html.append("</td></tr></table><img src=\"L2UI.SquareGray\" width=278 height=1><br1>");
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "LivingChronicle: Could not load history for " + player.getName() + ".", e);
		}
		if (count == 0)
		{
			html.append("<font color=777777>Tu historia comenzara con el primer hito registrado.</font><br>");
		}
		backButton(html);
		send(npc, player, pageEnd(html));
	}
	
	private void showFirsts(Npc npc, Player player)
	{
		final StringBuilder html = pageStart("Primeros Hitos");
		header(html, "PRIMEROS HITOS");
		html.append("<font color=AE9977>Los primeros nombres conservados por el mundo.</font><br><br>");
		int count = 0;
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(SELECT_FIRSTS))
		{
			ps.setInt(1, LivingChronicleConfig.SERVER_FIRST_LIMIT);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					count++;
					html.append("<table width=278 cellpadding=3 cellspacing=0 bgcolor=\"000000\"><tr><td width=178>");
					html.append("<font color=F0D58A>").append(escape(rs.getString("player_name"))).append("</font><br1>");
					html.append("<font color=LEVEL>").append(escape(rs.getString("title"))).append("</font></td>");
					html.append("<td width=100 align=right><font color=777777>").append(formatDate(rs.getLong("event_time"))).append("</font></td></tr></table>");
					html.append("<img src=\"L2UI.SquareGray\" width=278 height=1><br1>");
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "LivingChronicle: Could not load server firsts.", e);
		}
		if (count == 0)
		{
			html.append("<font color=777777>Aun no hay primeros hitos registrados.</font><br>");
		}
		backButton(html);
		send(npc, player, pageEnd(html));
	}
	
	private Summary loadSummary(int playerId)
	{
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(SELECT_SUMMARY))
		{
			ps.setInt(1, playerId);
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					return new Summary(rs.getLong("first_seen"), rs.getLong("last_seen"), rs.getInt("legacy_points"), rs.getInt("milestone_count"), rs.getInt("profession_count"), rs.getInt("raid_count"));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "LivingChronicle: Could not load summary for player " + playerId + ".", e);
		}
		return new Summary(0, 0, 0, 0, 0, 0);
	}
	
	private void initTables()
	{
		try (Connection con = DatabaseFactory.getConnection())
		{
			executeDdl(con, CREATE_PLAYERS);
			executeDdl(con, CREATE_EVENTS);
			executeDdl(con, CREATE_FIRSTS);
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.SEVERE, "LivingChronicle: Could not initialize tables.", e);
		}
	}
	
	private void executeDdl(Connection con, String sql) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.executeUpdate();
		}
	}
	
	private static int levelPoints(int level)
	{
		if (level >= 80)
		{
			return 10;
		}
		if (level >= 76)
		{
			return 8;
		}
		if (level >= 61)
		{
			return 6;
		}
		if (level >= 40)
		{
			return 4;
		}
		return 2;
	}
	
	private static String cleanKey(String value, int maxLength)
	{
		if (value == null)
		{
			return "";
		}
		String clean = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9:_\\-.]", "_");
		return clean.length() > maxLength ? clean.substring(0, maxLength) : clean;
	}
	
	private static String cleanText(String value, int maxLength)
	{
		if (value == null)
		{
			return "";
		}
		String clean = value.replace('\r', ' ').replace('\n', ' ').trim();
		return clean.length() > maxLength ? clean.substring(0, maxLength) : clean;
	}
	
	private static String friendlyName(String value)
	{
		if ((value == null) || value.isEmpty())
		{
			return "Unknown";
		}
		final StringBuilder result = new StringBuilder();
		for (String part : value.toLowerCase(Locale.ROOT).split("_"))
		{
			if (part.isEmpty())
			{
				continue;
			}
			if (result.length() > 0)
			{
				result.append(' ');
			}
			result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return result.toString();
	}
	
	private static String escape(String value)
	{
		return cleanText(value, 255).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
	
	private static String formatDate(long timestamp)
	{
		return timestamp <= 0 ? "-" : new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(timestamp));
	}
	
	private StringBuilder pageStart(String title)
	{
		return new StringBuilder("<html><title>").append(title).append("</title><body bgcolor=000000><center><table width=300 background=\"L2UI_CT1.Windows_DF_TooltipBG\"><tr><td align=center>");
	}

	private String pageEnd(StringBuilder html)
	{
		return html.append("</td></tr></table></center></body></html>").toString();
	}
	
	private void header(StringBuilder html, String title)
	{
		html.append("<table width=278><tr><td height=8></td></tr><tr><td align=center><font color=F0D58A>").append(title).append("</font></td></tr>");
		html.append("<tr><td align=center><img src=\"L2UI.SquareGray\" width=272 height=1></td></tr><tr><td height=5></td></tr></table>");
	}
	
	private void row(StringBuilder html, String label, String value)
	{
		html.append("<tr><td width=150><font color=B09878>").append(label).append("</font></td><td width=128 align=right><font color=LEVEL>").append(value).append("</font></td></tr>");
	}

	private void longButton(StringBuilder html, String value, String action)
	{
		html.append("<button value=\"").append(value).append("\" action=\"").append(action).append("\" width=260 height=24 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">");
	}

	private void backButton(StringBuilder html)
	{
		html.append("<br>");
		longButton(html, "VOLVER A LA CRONICA", "bypass -h Quest MegaraxLivingChronicle main");
	}
	
	private void send(Npc npc, Player player, String html)
	{
		final NpcHtmlMessage message = new NpcHtmlMessage(npc == null ? 0 : npc.getObjectId());
		message.setHtml(html);
		player.sendPacket(message);
	}
	
	private static final class Summary
	{
		private final long firstSeen;
		@SuppressWarnings("unused")
		private final long lastSeen;
		private final int legacyPoints;
		private final int milestones;
		private final int professions;
		private final int raids;
		
		private Summary(long firstSeen, long lastSeen, int legacyPoints, int milestones, int professions, int raids)
		{
			this.firstSeen = firstSeen;
			this.lastSeen = lastSeen;
			this.legacyPoints = legacyPoints;
			this.milestones = milestones;
			this.professions = professions;
			this.raids = raids;
		}
	}
	
	public static void main(String[] args)
	{
		new MegaraxLivingChronicle();
	}
}
