/*
 * SV Megarax x10 - persistent, idempotent community projects.
 */
package custom.MegaraxCommunityProjects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.Containers;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogin;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.network.serverpackets.ExShowScreenMessage;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.util.Broadcast;

import ai.AbstractNpcAI;
import custom.MegaraxLivingChronicle.MegaraxLivingChronicle;

public class MegaraxCommunityProjects extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxCommunityProjects.class.getName());
	private static final String OWNER_CLASS = MegaraxCommunityProjects.class.getName();
	
	private static final String CREATE_PROJECTS = "CREATE TABLE IF NOT EXISTS megarax_world_projects (project_key VARCHAR(64) NOT NULL, title VARCHAR(96) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', total_points INT NOT NULL DEFAULT 0, goal_points INT NOT NULL, started_at BIGINT NOT NULL, completed_at BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (project_key), KEY idx_world_project_status (status)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String CREATE_CONTRIBUTIONS = "CREATE TABLE IF NOT EXISTS megarax_world_project_contributions (project_key VARCHAR(64) NOT NULL, account_name VARCHAR(45) NOT NULL, player_id INT NOT NULL, player_name VARCHAR(35) NOT NULL, category VARCHAR(32) NOT NULL, amount INT NOT NULL DEFAULT 0, recognized TINYINT NOT NULL DEFAULT 0, contributed_at BIGINT NOT NULL, PRIMARY KEY (project_key,account_name), KEY idx_world_contribution_player (player_id), KEY idx_world_contribution_recognized (project_key,recognized)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String CREATE_EVENTS = "CREATE TABLE IF NOT EXISTS megarax_world_project_events (project_key VARCHAR(64) NOT NULL, account_name VARCHAR(45) NOT NULL, event_key VARCHAR(96) NOT NULL, created_at BIGINT NOT NULL, PRIMARY KEY (project_key,account_name,event_key)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String UPSERT_PROJECT = "INSERT INTO megarax_world_projects (project_key,title,status,total_points,goal_points,started_at,completed_at) VALUES (?,?,'ACTIVE',0,?,?,0) ON DUPLICATE KEY UPDATE title=VALUES(title),goal_points=GREATEST(total_points,VALUES(goal_points))";
	private static final String LOCK_PROJECT = "SELECT title,status,total_points,goal_points FROM megarax_world_projects WHERE project_key=? FOR UPDATE";
	private static final String INSERT_EVENT = "INSERT IGNORE INTO megarax_world_project_events (project_key,account_name,event_key,created_at) VALUES (?,?,?,?)";
	private static final String UPSERT_CONTRIBUTION = "INSERT INTO megarax_world_project_contributions (project_key,account_name,player_id,player_name,category,amount,recognized,contributed_at) VALUES (?,?,?,?,?,?,0,?) ON DUPLICATE KEY UPDATE player_id=VALUES(player_id),player_name=VALUES(player_name),category=VALUES(category),amount=amount+VALUES(amount),recognized=0,contributed_at=VALUES(contributed_at)";
	private static final String UPDATE_PROJECT = "UPDATE megarax_world_projects SET total_points=?,status=?,completed_at=? WHERE project_key=?";
	private static final String SELECT_PROJECT = "SELECT title,status,total_points,goal_points,started_at,completed_at FROM megarax_world_projects WHERE project_key=?";
	private static final String SELECT_RECENT = "SELECT player_name,category,amount FROM megarax_world_project_contributions WHERE project_key=? ORDER BY contributed_at DESC LIMIT ?";
	private static final String SELECT_RECOGNITIONS = "SELECT c.project_key,p.title,c.category,c.amount FROM megarax_world_project_contributions c JOIN megarax_world_projects p ON p.project_key=c.project_key WHERE c.player_id=? AND c.recognized=0 AND p.status='COMPLETED'";
	private static final String MARK_RECOGNIZED = "UPDATE megarax_world_project_contributions SET recognized=1 WHERE project_key=? AND account_name=? AND recognized=0";
	
	private static MegaraxCommunityProjects INSTANCE;
	
	private MegaraxCommunityProjects()
	{
		CommunityProjectsConfig.load();
		if (!CommunityProjectsConfig.ENABLED)
		{
			LOGGER.info("CommunityProjects: Disabled by configuration.");
			return;
		}
		INSTANCE = this;
		initTables();
		ensureConfiguredProjects();
		Containers.Players().removeListenerIf(listener -> (listener.getOwner() != null) && OWNER_CLASS.equals(listener.getOwner().getClass().getName()));
		Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_LOGIN, (OnPlayerLogin event) -> onPlayerLogin(event), this));
		addStartNpc(CommunityProjectsConfig.NPC_ID);
		addTalkId(CommunityProjectsConfig.NPC_ID);
		addFirstTalkId(CommunityProjectsConfig.NPC_ID);
		LOGGER.info("CommunityProjects: Loaded project " + CommunityProjectsConfig.FORGOTTEN_ROADS_KEY + ".");
	}
	
	private void onPlayerLogin(OnPlayerLogin event)
	{
		if ((event != null) && (event.getPlayer() != null) && !event.getPlayer().isGM())
		{
			recognizeCompletedProjects(event.getPlayer());
		}
	}
	
	public static boolean contributeForgottenOath(Player player, String path)
	{
		return (INSTANCE != null) && INSTANCE.contributeOnce(player, CommunityProjectsConfig.FORGOTTEN_ROADS_KEY, "CHAPTER1_OATH", path, CommunityProjectsConfig.FORGOTTEN_ROADS_OATH_POINTS);
	}
	
	public static boolean contribute(Player player, String projectKey, String eventKey, String category, int amount)
	{
		return (INSTANCE != null) && INSTANCE.contributeOnce(player, projectKey, eventKey, category, amount);
	}
	
	private boolean contributeOnce(Player player, String projectKey, String eventKey, String category, int amount)
	{
		if ((player == null) || player.isGM() || (amount <= 0))
		{
			return false;
		}
		final String safeProject = cleanKey(projectKey, 64);
		final String safeEvent = cleanKey(eventKey, 96);
		final String safeCategory = cleanKey(category, 32);
		if (safeProject.isEmpty() || safeEvent.isEmpty() || safeCategory.isEmpty())
		{
			return false;
		}
		
		String projectTitle = safeProject;
		int total = 0;
		int goal = 0;
		boolean completedNow = false;
		final long now = System.currentTimeMillis();
		try (Connection con = DatabaseFactory.getConnection())
		{
			con.setAutoCommit(false);
			try
			{
				String status;
				try (PreparedStatement ps = con.prepareStatement(LOCK_PROJECT))
				{
					ps.setString(1, safeProject);
					try (ResultSet rs = ps.executeQuery())
					{
						if (!rs.next())
						{
							con.rollback();
							return false;
						}
						projectTitle = rs.getString("title");
						status = rs.getString("status");
						total = rs.getInt("total_points");
						goal = rs.getInt("goal_points");
					}
				}
				if (!"ACTIVE".equals(status))
				{
					con.rollback();
					player.sendMessage("Proyecto comunitario: " + projectTitle + " ya fue completado.");
					return false;
				}
				try (PreparedStatement ps = con.prepareStatement(INSERT_EVENT))
				{
					ps.setString(1, safeProject);
					ps.setString(2, player.getAccountName());
					ps.setString(3, safeEvent);
					ps.setLong(4, now);
					if (ps.executeUpdate() == 0)
					{
						con.rollback();
						return false;
					}
				}
				try (PreparedStatement ps = con.prepareStatement(UPSERT_CONTRIBUTION))
				{
					ps.setString(1, safeProject);
					ps.setString(2, player.getAccountName());
					ps.setInt(3, player.getObjectId());
					ps.setString(4, player.getName());
					ps.setString(5, safeCategory);
					ps.setInt(6, amount);
					ps.setLong(7, now);
					ps.executeUpdate();
				}
				total = Math.min(goal, total + amount);
				completedNow = total >= goal;
				try (PreparedStatement ps = con.prepareStatement(UPDATE_PROJECT))
				{
					ps.setInt(1, total);
					ps.setString(2, completedNow ? "COMPLETED" : "ACTIVE");
					ps.setLong(3, completedNow ? now : 0);
					ps.setString(4, safeProject);
					ps.executeUpdate();
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
			LOGGER.log(Level.WARNING, "CommunityProjects: Could not record contribution for " + player.getName() + ".", e);
			return false;
		}
		
		player.sendMessage("Proyecto comunitario: +" + amount + " en " + projectTitle + " (" + total + "/" + goal + ").");
		if (completedNow)
		{
			Broadcast.toAllOnlinePlayers("Megarax recuerda: la comunidad completo " + projectTitle + ".");
			Broadcast.toAllOnlinePlayersOnScreen("Proyecto completado: " + projectTitle);
			recognizeCompletedProjects(player);
		}
		return true;
	}
	
	private void recognizeCompletedProjects(Player player)
	{
		final List<Recognition> recognitions = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(SELECT_RECOGNITIONS))
		{
			ps.setInt(1, player.getObjectId());
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					recognitions.add(new Recognition(rs.getString("project_key"), rs.getString("title"), rs.getString("category"), rs.getInt("amount")));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "CommunityProjects: Could not load recognitions for " + player.getName() + ".", e);
			return;
		}
		for (Recognition recognition : recognitions)
		{
			final boolean recorded = MegaraxLivingChronicle.recordEvent(player, "WORLD_PROJECT", recognition.projectKey, "Ayudo a completar " + recognition.title, "Aporto " + recognition.amount + " puntos como " + friendlyName(recognition.category) + ".", CommunityProjectsConfig.FORGOTTEN_ROADS_LEGACY_POINTS, false);
			if (recorded)
			{
				markRecognized(player.getAccountName(), recognition.projectKey);
			}
		}
	}
	
	private void markRecognized(String accountName, String projectKey)
	{
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(MARK_RECOGNIZED))
		{
			ps.setString(1, projectKey);
			ps.setString(2, accountName);
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "CommunityProjects: Could not mark recognition for " + accountName + ".", e);
		}
	}
	
	@Override
	public String onFirstTalk(Npc npc, Player player)
	{
		showProject(npc, player);
		return null;
	}
	
	@Override
	public String onTalk(Npc npc, Player player)
	{
		showProject(npc, player);
		return null;
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		showProject(npc, player);
		return null;
	}
	
	private void showProject(Npc npc, Player player)
	{
		final ProjectView project = loadProject(CommunityProjectsConfig.FORGOTTEN_ROADS_KEY);
		final StringBuilder html = new StringBuilder("<html><title>Proyectos de Megarax</title><body bgcolor=000000><center><table width=300 background=\"L2UI_CT1.Windows_DF_TooltipBG\"><tr><td align=center>");
		html.append("<table width=278><tr><td height=8></td></tr><tr><td align=center><font color=F0D58A>MEMORIAL DE LOS CAMINOS</font></td></tr><tr><td align=center><img src=\"L2UI.SquareGray\" width=272 height=1></td></tr><tr><td height=5></td></tr></table>");
		if (project == null)
		{
			html.append("<font color=FF7777>El proyecto no esta disponible.</font>");
		}
		else
		{
			final int percent = project.goal <= 0 ? 0 : Math.min(100, (project.total * 100) / project.goal);
			html.append("<font color=C94A4A>PROYECTO COMUNITARIO</font><br1>");
			html.append("<font color=LEVEL>").append(escape(project.title)).append("</font><br1>");
			html.append("<font color=B09878>Los juramentos de las aldeas restauran los caminos antiguos.</font><br><br>");
			html.append("<table width=278 cellpadding=3 cellspacing=0 bgcolor=\"000000\"><tr><td><font color=B09878>Estado</font></td><td align=right><font color=LEVEL>").append("COMPLETED".equals(project.status) ? "COMPLETADO" : "ACTIVO").append("</font></td></tr>");
			html.append("<tr><td><font color=B09878>Progreso</font></td><td align=right><font color=LEVEL>").append(project.total).append("/").append(project.goal).append(" (").append(percent).append("%)</font></td></tr></table><br>");
			html.append("<font color=F0D58A>CONTRIBUYENTES RECIENTES</font><br1>");
			appendContributors(html, project.key);
			html.append("<br><font color=777777>Cada nombre permanece guardado aunque no aparezca en esta vista.</font><br>");
			html.append("<button value=\"ACTUALIZAR MEMORIAL\" action=\"bypass -h Quest MegaraxCommunityProjects main\" width=260 height=24 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">");
		}
		html.append("</td></tr></table></center></body></html>");
		final NpcHtmlMessage message = new NpcHtmlMessage(npc == null ? 0 : npc.getObjectId());
		message.setHtml(html.toString());
		player.sendPacket(message);
	}
	
	private void appendContributors(StringBuilder html, String projectKey)
	{
		int count = 0;
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(SELECT_RECENT))
		{
			ps.setString(1, projectKey);
			ps.setInt(2, CommunityProjectsConfig.RECENT_CONTRIBUTORS);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					count++;
					html.append("<table width=278 cellpadding=2 cellspacing=0 bgcolor=\"000000\"><tr><td><font color=LEVEL>").append(escape(rs.getString("player_name"))).append("</font></td><td align=right><font color=B09878>").append(friendlyName(rs.getString("category"))).append(" +").append(rs.getInt("amount")).append("</font></td></tr></table>");
					html.append("<img src=\"L2UI.SquareGray\" width=278 height=1>");
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "CommunityProjects: Could not load contributors.", e);
		}
		if (count == 0)
		{
			html.append("<font color=777777>Aun no hay contribuciones.</font>");
		}
	}
	
	private ProjectView loadProject(String projectKey)
	{
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(SELECT_PROJECT))
		{
			ps.setString(1, projectKey);
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					return new ProjectView(projectKey, rs.getString("title"), rs.getString("status"), rs.getInt("total_points"), rs.getInt("goal_points"));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "CommunityProjects: Could not load project " + projectKey + ".", e);
		}
		return null;
	}
	
	private void initTables()
	{
		try (Connection con = DatabaseFactory.getConnection())
		{
			executeDdl(con, CREATE_PROJECTS);
			executeDdl(con, CREATE_CONTRIBUTIONS);
			executeDdl(con, CREATE_EVENTS);
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.SEVERE, "CommunityProjects: Could not initialize tables.", e);
		}
	}
	
	private void ensureConfiguredProjects()
	{
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(UPSERT_PROJECT))
		{
			ps.setString(1, CommunityProjectsConfig.FORGOTTEN_ROADS_KEY);
			ps.setString(2, CommunityProjectsConfig.FORGOTTEN_ROADS_TITLE);
			ps.setInt(3, CommunityProjectsConfig.FORGOTTEN_ROADS_GOAL);
			ps.setLong(4, System.currentTimeMillis());
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.SEVERE, "CommunityProjects: Could not configure Forgotten Roads.", e);
		}
	}
	
	private void executeDdl(Connection con, String sql) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.executeUpdate();
		}
	}
	
	private static String cleanKey(String value, int maximum)
	{
		if (value == null)
		{
			return "";
		}
		final String clean = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9:_\\-]", "_");
		return clean.substring(0, Math.min(maximum, clean.length()));
	}
	
	private static String friendlyName(String value)
	{
		if ((value == null) || value.isEmpty())
		{
			return "Sin categoria";
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
		return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
	
	private static final class ProjectView
	{
		private final String key;
		private final String title;
		private final String status;
		private final int total;
		private final int goal;
		
		private ProjectView(String key, String title, String status, int total, int goal)
		{
			this.key = key;
			this.title = title;
			this.status = status;
			this.total = total;
			this.goal = goal;
		}
	}
	
	private static final class Recognition
	{
		private final String projectKey;
		private final String title;
		private final String category;
		private final int amount;
		
		private Recognition(String projectKey, String title, String category, int amount)
		{
			this.projectKey = projectKey;
			this.title = title;
			this.category = category;
			this.amount = amount;
		}
	}
	
	public static void main(String[] args)
	{
		new MegaraxCommunityProjects();
	}
}
