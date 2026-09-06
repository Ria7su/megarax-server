/*
 * SV Megarax x10 - Chapter I: The Forgotten Villages.
 */
package custom.MegaraxForgottenVillages;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.network.serverpackets.ExShowScreenMessage;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

import ai.AbstractNpcAI;
import custom.MegaraxCommunityProjects.MegaraxCommunityProjects;
import custom.MegaraxLivingChronicle.MegaraxLivingChronicle;

public class MegaraxForgottenVillages extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxForgottenVillages.class.getName());
	
	private static final int ADENA = 57;
	private static final int LESSER_HEALING_POTION = 1060;
	private static final int SCROLL_OF_ESCAPE = 736;
	private static final int ANIMAL_SKIN = 1867;
	private static final int IRON_ORE = 1869;
	private static final int STEM_ITEM = 1864;
	private static final int QUEST_GUIDE = 31805;
	
	private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS megarax_forgotten_villages (account_name VARCHAR(45) NOT NULL, player_id INT NOT NULL, player_name VARCHAR(35) NOT NULL, village VARCHAR(24) NOT NULL, path VARCHAR(16) NOT NULL, progress INT NOT NULL DEFAULT 0, goal INT NOT NULL, status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE', started_at BIGINT NOT NULL, completed_at BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (account_name), KEY idx_forgotten_player (player_id), KEY idx_forgotten_village_status (village,status)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String SELECT_PROGRESS = "SELECT player_id,player_name,village,path,progress,goal,status,started_at,completed_at FROM megarax_forgotten_villages WHERE account_name=?";
	private static final String INSERT_PROGRESS = "INSERT IGNORE INTO megarax_forgotten_villages (account_name,player_id,player_name,village,path,progress,goal,status,started_at) VALUES (?,?,?,?,?,0,?,'ACTIVE',?)";
	private static final String INCREMENT_PROGRESS = "UPDATE megarax_forgotten_villages SET status=IF(progress+1>=goal,'READY',status),progress=LEAST(goal,progress+1),player_id=?,player_name=? WHERE account_name=? AND village=? AND path=? AND status='ACTIVE'";
	private static final String READY_SUPPLIER = "UPDATE megarax_forgotten_villages SET progress=goal,status='READY',player_id=?,player_name=? WHERE account_name=? AND village=? AND path='SUPPLIER' AND status='ACTIVE'";
	private static final String COMPLETE = "UPDATE megarax_forgotten_villages SET status='COMPLETED',completed_at=?,player_id=?,player_name=? WHERE account_name=? AND village=? AND status='READY'";
	
	private MegaraxForgottenVillages()
	{
		ForgottenVillagesConfig.load();
		if (!ForgottenVillagesConfig.ENABLED)
		{
			LOGGER.info("ForgottenVillages: Disabled by configuration.");
			return;
		}
		initTable();
		final int[] keepers = Village.npcIds();
		addStartNpc(keepers);
		addTalkId(keepers);
		addFirstTalkId(keepers);
		addKillId(Village.allTargetIds());
		LOGGER.info("ForgottenVillages: Chapter I loaded with " + Village.values().length + " village oaths.");
	}
	
	@Override
	public String onFirstTalk(Npc npc, Player player)
	{
		show(npc, player);
		return null;
	}
	
	@Override
	public String onTalk(Npc npc, Player player)
	{
		show(npc, player);
		return null;
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		if ((player == null) || (npc == null))
		{
			return null;
		}
		if ((npc.getId() == QUEST_GUIDE) && event.startsWith("guide"))
		{
			handleGuideEvent(event, npc, player);
			return null;
		}
		final Village village = Village.fromNpcId(npc.getId());
		if (village == null)
		{
			return null;
		}
		if ("start_hunter".equals(event))
		{
			start(player, village, Path.HUNTER);
		}
		else if ("start_guardian".equals(event))
		{
			start(player, village, Path.GUARDIAN);
		}
		else if ("start_supplier".equals(event))
		{
			start(player, village, Path.SUPPLIER);
		}
		else if (event.startsWith("deliver_"))
		{
			deliver(player, village, event.substring("deliver_".length()));
		}
		else if ("claim".equals(event))
		{
			claim(player, village);
		}
		show(npc, player);
		return null;
	}

	private void handleGuideEvent(String event, Npc npc, Player player)
	{
		if ("guide".equals(event))
		{
			final Progress progress = loadProgress(player.getAccountName());
			final Village chosen = progress == null ? null : Village.fromKey(progress.village);
			if (chosen != null)
			{
				show(npc, player, chosen, true);
			}
			else
			{
				showGuideVillages(npc, player);
			}
			return;
		}
		if ("guide_villages".equals(event))
		{
			showGuideVillages(npc, player);
			return;
		}

		for (Village village : Village.values())
		{
			if (event.equals("guide_show_" + village.key))
			{
				show(npc, player, village, true);
				return;
			}
			if (event.equals("guide_start_hunter_" + village.key))
			{
				start(player, village, Path.HUNTER);
				show(npc, player, village, true);
				return;
			}
			if (event.equals("guide_start_guardian_" + village.key))
			{
				start(player, village, Path.GUARDIAN);
				show(npc, player, village, true);
				return;
			}
			if (event.equals("guide_start_supplier_" + village.key))
			{
				start(player, village, Path.SUPPLIER);
				show(npc, player, village, true);
				return;
			}
			if (event.equals("guide_claim_" + village.key))
			{
				claim(player, village);
				show(npc, player, village, true);
				return;
			}
			for (Delivery delivery : Delivery.values())
			{
				if (event.equals("guide_deliver_" + delivery.key + "_" + village.key))
				{
					deliver(player, village, delivery.key);
					show(npc, player, village, true);
					return;
				}
			}
		}

		showGuideVillages(npc, player);
	}
	
	@Override
	public void onKill(Npc npc, Player killer, boolean isSummon)
	{
		if ((npc == null) || (killer == null) || killer.isGM())
		{
			return;
		}
		final Progress killerProgress = loadProgress(killer.getAccountName());
		if ((killerProgress != null) && "ACTIVE".equals(killerProgress.status) && Path.HUNTER.name().equals(killerProgress.path))
		{
			final Village village = Village.fromKey(killerProgress.village);
			if ((village != null) && village.accepts(npc.getId()))
			{
				increment(killer, killerProgress, Path.HUNTER);
			}
		}
		
		if (killer.getParty() == null)
		{
			return;
		}
		for (Player member : killer.getParty().getMembers())
		{
			if ((member == null) || member.isGM() || !member.isInsideRadius2D(npc, ForgottenVillagesConfig.GUARDIAN_RANGE) || !hasEligibleCompanion(member, npc))
			{
				continue;
			}
			final Progress progress = loadProgress(member.getAccountName());
			if ((progress == null) || !"ACTIVE".equals(progress.status) || !Path.GUARDIAN.name().equals(progress.path))
			{
				continue;
			}
			final Village village = Village.fromKey(progress.village);
			if ((village != null) && village.accepts(npc.getId()))
			{
				increment(member, progress, Path.GUARDIAN);
			}
		}
	}
	
	private boolean hasEligibleCompanion(Player player, Npc target)
	{
		if (player.getParty() == null)
		{
			return false;
		}
		for (Player member : player.getParty().getMembers())
		{
			if ((member == null) || (member.getObjectId() == player.getObjectId()) || member.isGM())
			{
				continue;
			}
			if (!player.getAccountName().equalsIgnoreCase(member.getAccountName()) && (member.getLevel() <= ForgottenVillagesConfig.MAX_LEVEL) && member.isInsideRadius2D(target, ForgottenVillagesConfig.GUARDIAN_RANGE))
			{
				return true;
			}
		}
		return false;
	}
	
	private void start(Player player, Village village, Path path)
	{
		if ((player.getLevel() < ForgottenVillagesConfig.MIN_LEVEL) || (player.getLevel() > ForgottenVillagesConfig.MAX_LEVEL))
		{
			player.sendMessage("El Juramento de las Raices esta disponible entre niveles " + ForgottenVillagesConfig.MIN_LEVEL + " y " + ForgottenVillagesConfig.MAX_LEVEL + ".");
			return;
		}
		if (loadProgress(player.getAccountName()) != null)
		{
			player.sendMessage("Tu cuenta ya eligio una raiz en Megarax.");
			return;
		}
		final int goal = path == Path.HUNTER ? ForgottenVillagesConfig.HUNTER_GOAL : path == Path.GUARDIAN ? ForgottenVillagesConfig.GUARDIAN_GOAL : 1;
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(INSERT_PROGRESS))
		{
			ps.setString(1, player.getAccountName());
			ps.setInt(2, player.getObjectId());
			ps.setString(3, player.getName());
			ps.setString(4, village.key);
			ps.setString(5, path.name());
			ps.setInt(6, goal);
			ps.setLong(7, System.currentTimeMillis());
			if (ps.executeUpdate() > 0)
			{
				player.sendPacket(new ExShowScreenMessage("Juramento iniciado: " + path.displayName, 4000));
				player.sendMessage("Has elegido " + path.displayName + " para proteger " + village.displayName + ".");
			}
			else
			{
				player.sendMessage("Tu cuenta ya eligio una raiz en Megarax.");
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "ForgottenVillages: Could not start oath for " + player.getName() + ".", e);
			player.sendMessage("El custodio no pudo registrar tu juramento. Intenta de nuevo.");
		}
	}
	
	private void increment(Player player, Progress current, Path path)
	{
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(INCREMENT_PROGRESS))
		{
			ps.setInt(1, player.getObjectId());
			ps.setString(2, player.getName());
			ps.setString(3, player.getAccountName());
			ps.setString(4, current.village);
			ps.setString(5, path.name());
			if (ps.executeUpdate() == 0)
			{
				return;
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "ForgottenVillages: Could not update progress for " + player.getName() + ".", e);
			return;
		}
		final Progress updated = loadProgress(player.getAccountName());
		if (updated == null)
		{
			return;
		}
		if ("READY".equals(updated.status))
		{
			player.sendPacket(new ExShowScreenMessage("Juramento listo: regresa al custodio de " + Village.fromKey(updated.village).displayName + ".", 5000));
		}
		else
		{
			player.sendMessage("Juramento de las Raices: " + updated.progress + "/" + updated.goal + ".");
		}
	}
	
	private void deliver(Player player, Village village, String deliveryKey)
	{
		final Progress progress = loadProgress(player.getAccountName());
		if ((progress == null) || !"ACTIVE".equals(progress.status) || !Path.SUPPLIER.name().equals(progress.path) || !village.key.equals(progress.village))
		{
			player.sendMessage("No tienes una entrega pendiente para esta aldea.");
			return;
		}
		final Delivery delivery = Delivery.fromKey(deliveryKey);
		if (delivery == null)
		{
			return;
		}
		if (!player.destroyItemByItemId(ItemProcessType.QUEST, delivery.itemId, delivery.count, player, true))
		{
			player.sendMessage("Necesitas " + delivery.count + " " + delivery.displayName + ".");
			return;
		}
		boolean saved = false;
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(READY_SUPPLIER))
		{
			ps.setInt(1, player.getObjectId());
			ps.setString(2, player.getName());
			ps.setString(3, player.getAccountName());
			ps.setString(4, village.key);
			saved = ps.executeUpdate() > 0;
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "ForgottenVillages: Could not save delivery for " + player.getName() + ".", e);
		}
		if (!saved)
		{
			player.addItem(ItemProcessType.REWARD, delivery.itemId, delivery.count, player, true);
			player.sendMessage("La entrega no pudo registrarse; tus materiales fueron devueltos.");
			return;
		}
		player.sendPacket(new ExShowScreenMessage("Provisiones entregadas: regresa con el custodio.", 5000));
	}
	
	private void claim(Player player, Village village)
	{
		final Progress progress = loadProgress(player.getAccountName());
		if ((progress == null) || !"READY".equals(progress.status) || !village.key.equals(progress.village))
		{
			player.sendMessage("Tu juramento aun no esta listo para completarse aqui.");
			return;
		}
		boolean claimed = false;
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(COMPLETE))
		{
			ps.setLong(1, System.currentTimeMillis());
			ps.setInt(2, player.getObjectId());
			ps.setString(3, player.getName());
			ps.setString(4, player.getAccountName());
			ps.setString(5, village.key);
			claimed = ps.executeUpdate() > 0;
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "ForgottenVillages: Could not complete oath for " + player.getName() + ".", e);
		}
		if (!claimed)
		{
			player.sendMessage("El juramento ya fue reclamado o no pudo registrarse.");
			return;
		}
		
		if (ForgottenVillagesConfig.REWARD_ADENA > 0)
		{
			player.addItem(ItemProcessType.REWARD, ADENA, ForgottenVillagesConfig.REWARD_ADENA, player, true);
		}
		if (ForgottenVillagesConfig.REWARD_POTIONS > 0)
		{
			player.addItem(ItemProcessType.REWARD, LESSER_HEALING_POTION, ForgottenVillagesConfig.REWARD_POTIONS, player, true);
		}
		if (ForgottenVillagesConfig.REWARD_SCROLLS > 0)
		{
			player.addItem(ItemProcessType.REWARD, SCROLL_OF_ESCAPE, ForgottenVillagesConfig.REWARD_SCROLLS, player, true);
		}
		if ((ForgottenVillagesConfig.REWARD_EXP > 0) || (ForgottenVillagesConfig.REWARD_SP > 0))
		{
			player.addExpAndSp(ForgottenVillagesConfig.REWARD_EXP, ForgottenVillagesConfig.REWARD_SP);
		}
		
		final Path path = Path.valueOf(progress.path);
		MegaraxLivingChronicle.recordEvent(player, "STORY", "chapter1:" + village.key + ":" + path.name(), "Juro proteger " + village.displayName, "Completo El Juramento de las Raices por " + path.displayName + ".", ForgottenVillagesConfig.LEGACY_POINTS, true);
		MegaraxCommunityProjects.contributeForgottenOath(player, path.name());
		player.sendPacket(new ExShowScreenMessage("Las raices de " + village.displayName + " recordaran tu nombre.", 6000));
		player.sendMessage("Capitulo I completado: El Juramento de las Raices.");
	}
	
	private void show(Npc npc, Player player)
	{
		final Village npcVillage = Village.fromNpcId(npc.getId());
		if (npcVillage == null)
		{
			return;
		}
		show(npc, player, npcVillage, false);
	}

	private void show(Npc npc, Player player, Village npcVillage, boolean viaGuide)
	{
		final Progress progress = loadProgress(player.getAccountName());
		final StringBuilder html = new StringBuilder("<html><title>Las Aldeas Olvidadas</title><body><center>");
		header(html, "LAS ALDEAS OLVIDADAS");
		html.append("<font color=F0D58A>").append(npcVillage.displayName).append("</font><br1>");
		html.append("<font color=AE9977>Los caminos antiguos se borran cuando nadie recuerda quienes los defendieron.</font><br><br>");
		if (progress == null)
		{
			if ((player.getLevel() < ForgottenVillagesConfig.MIN_LEVEL) || (player.getLevel() > ForgottenVillagesConfig.MAX_LEVEL))
			{
				html.append("<font color=FF7777>Disponible para niveles ").append(ForgottenVillagesConfig.MIN_LEVEL).append("-").append(ForgottenVillagesConfig.MAX_LEVEL).append(".</font>");
			}
			else
			{
				html.append("<font color=LEVEL>Elige como dejaras tu primera huella:</font><br><br>");
				choice(html, "CAZADOR", "Derrota " + ForgottenVillagesConfig.HUNTER_GOAL + " amenazas locales.", "start_hunter", npcVillage, viaGuide);
				choice(html, "GUARDIAN", "Protege la aldea junto a otro jugador.", "start_guardian", npcVillage, viaGuide);
				choice(html, "PROVEEDOR", "Entrega materiales basicos para reconstruir.", "start_supplier", npcVillage, viaGuide);
			}
		}
		else if (!npcVillage.key.equals(progress.village))
		{
			final Village chosen = Village.fromKey(progress.village);
			html.append("<font color=AE9977>Tu juramento pertenece a </font><font color=LEVEL>").append(chosen == null ? progress.village : chosen.displayName).append("</font>.");
		}
		else if ("COMPLETED".equals(progress.status))
		{
			html.append("<font color=LEVEL>Esta aldea ya conserva tu nombre.</font><br1>");
			html.append("<font color=AE9977>Camino: ").append(Path.valueOf(progress.path).displayName).append(".</font>");
		}
		else
		{
			html.append("<table width=292 bgcolor=080808><tr><td><font color=AE9977>Camino</font></td><td align=right><font color=LEVEL>").append(Path.valueOf(progress.path).displayName).append("</font></td></tr>");
			html.append("<tr><td><font color=AE9977>Progreso</font></td><td align=right><font color=LEVEL>").append(progress.progress).append("/").append(progress.goal).append("</font></td></tr></table><br>");
			if (Path.SUPPLIER.name().equals(progress.path) && "ACTIVE".equals(progress.status))
			{
				html.append("<font color=AE9977>Entrega una de estas provisiones:</font><br1>");
				deliveryButton(html, Delivery.SKIN, npcVillage, viaGuide);
				deliveryButton(html, Delivery.IRON, npcVillage, viaGuide);
				deliveryButton(html, Delivery.STEM, npcVillage, viaGuide);
			}
			else if ("READY".equals(progress.status))
			{
				final String claimEvent = viaGuide ? "guide_claim_" + npcVillage.key : "claim";
				html.append("<button value=\"COMPLETAR JURAMENTO\" action=\"bypass -h Quest MegaraxForgottenVillages ").append(claimEvent).append("\" width=190 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
			}
			else if (Path.GUARDIAN.name().equals(progress.path))
			{
				html.append("<font color=AE9977>Forma party con otro jugador de nivel ").append(ForgottenVillagesConfig.MAX_LEVEL).append(" o menor y enfrenta amenazas locales.</font>");
			}
			else
			{
				html.append("<font color=AE9977>Enfrenta las amenazas de esta region y regresa.</font>");
			}
		}
		if (viaGuide)
		{
			html.append("<br><button value=\"ELEGIR OTRA ALDEA\" action=\"bypass -h Quest MegaraxForgottenVillages guide_villages\" width=190 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
			html.append("<br1><button value=\"VOLVER A AVENTURAS\" action=\"bypass -h npc_").append(npc.getObjectId()).append("_Chat 3\" width=190 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		}
		html.append("</center></body></html>");
		final NpcHtmlMessage message = new NpcHtmlMessage(npc.getObjectId());
		message.setHtml(html.toString());
		player.sendPacket(message);
	}

	private void showGuideVillages(Npc npc, Player player)
	{
		final Progress progress = loadProgress(player.getAccountName());
		final StringBuilder html = new StringBuilder("<html><title>Las Aldeas Olvidadas</title><body><center>");
		header(html, "ELIGE UNA ALDEA");
		if (progress == null)
		{
			html.append("<font color=AE9977>Tu juramento puede comenzar aqui, sin viajar al custodio original.</font><br><br>");
		}
		else
		{
			final Village chosen = Village.fromKey(progress.village);
			html.append("<font color=AE9977>Juramento actual: </font><font color=LEVEL>").append(chosen == null ? progress.village : chosen.displayName).append("</font><br><br>");
		}
		for (Village village : Village.values())
		{
			html.append("<button value=\"").append(village.displayName).append("\" action=\"bypass -h Quest MegaraxForgottenVillages guide_show_").append(village.key).append("\" width=220 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"><br1>");
		}
		html.append("<br><button value=\"VOLVER A AVENTURAS\" action=\"bypass -h npc_").append(npc.getObjectId()).append("_Chat 3\" width=220 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		html.append("</center></body></html>");
		final NpcHtmlMessage message = new NpcHtmlMessage(npc.getObjectId());
		message.setHtml(html.toString());
		player.sendPacket(message);
	}
	
	private Progress loadProgress(String accountName)
	{
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(SELECT_PROGRESS))
		{
			ps.setString(1, accountName);
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					return new Progress(rs.getString("village"), rs.getString("path"), rs.getInt("progress"), rs.getInt("goal"), rs.getString("status"));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "ForgottenVillages: Could not load account progress.", e);
		}
		return null;
	}
	
	private void initTable()
	{
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(CREATE_TABLE))
		{
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.SEVERE, "ForgottenVillages: Could not initialize table.", e);
		}
	}
	
	private void header(StringBuilder html, String title)
	{
		html.append("<table width=292><tr><td align=center><font color=F0D58A>").append(title).append("</font></td></tr><tr><td align=center><img src=\"L2UI.SquareGray\" width=286 height=1></td></tr></table><br>");
	}
	
	private void choice(StringBuilder html, String label, String description, String event, Village village, boolean viaGuide)
	{
		final String action = viaGuide ? "guide_" + event + "_" + village.key : event;
		html.append("<table width=292 bgcolor=080808><tr><td width=185><font color=LEVEL>").append(label).append("</font><br1><font color=AE9977>").append(description).append("</font></td><td align=right><button value=\"ELEGIR\" action=\"bypass -h Quest MegaraxForgottenVillages ").append(action).append("\" width=70 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td></tr></table><br1>");
	}

	private void deliveryButton(StringBuilder html, Delivery delivery, Village village, boolean viaGuide)
	{
		final String event = viaGuide ? "guide_deliver_" + delivery.key + "_" + village.key : "deliver_" + delivery.key;
		html.append("<button value=\"").append(delivery.count).append(" ").append(delivery.displayName).append("\" action=\"bypass -h Quest MegaraxForgottenVillages ").append(event).append("\" width=180 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"><br1>");
	}
	
	private enum Path
	{
		HUNTER("Camino del Cazador"),
		GUARDIAN("Camino del Guardian"),
		SUPPLIER("Camino del Proveedor");
		
		private final String displayName;
		
		Path(String displayName)
		{
			this.displayName = displayName;
		}
	}
	
	private enum Delivery
	{
		SKIN("skin", ANIMAL_SKIN, 10, "Animal Skin"),
		IRON("iron", IRON_ORE, 10, "Iron Ore"),
		STEM("stem", STEM_ITEM, 15, "Stem");
		
		private final String key;
		private final int itemId;
		private final int count;
		private final String displayName;
		
		Delivery(String key, int itemId, int count, String displayName)
		{
			this.key = key;
			this.itemId = itemId;
			this.count = count;
			this.displayName = displayName;
		}
		
		private static Delivery fromKey(String key)
		{
			for (Delivery delivery : values())
			{
				if (delivery.key.equalsIgnoreCase(key))
				{
					return delivery;
				}
			}
			return null;
		}
	}
	
	private enum Village
	{
		TALKING_ISLAND(1005001, "TALKING_ISLAND", "Talking Island", 20481, 20544, 20120, 20442, 20130, 20131),
		ELVEN_VILLAGE(1005002, "ELVEN_VILLAGE", "Elven Village", 20530, 20534, 20537, 20525),
		DARK_ELF_VILLAGE(1005003, "DARK_ELF_VILLAGE", "Dark Elf Village", 20529, 20532, 20536, 20456, 20003, 20004, 20005),
		ORC_VILLAGE(1005004, "ORC_VILLAGE", "Orc Village", 20531, 20535, 20538, 20475, 20477, 20319, 20311, 20312),
		DWARVEN_VILLAGE(1005005, "DWARVEN_VILLAGE", "Dwarven Village", 20533, 20539, 20317, 20318, 20327, 20446, 20448);
		
		private final int npcId;
		private final String key;
		private final String displayName;
		private final Set<Integer> targets;
		
		Village(int npcId, String key, String displayName, Integer... targets)
		{
			this.npcId = npcId;
			this.key = key;
			this.displayName = displayName;
			this.targets = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(targets)));
		}
		
		private boolean accepts(int npcId)
		{
			return targets.contains(npcId);
		}
		
		private static Village fromNpcId(int npcId)
		{
			for (Village village : values())
			{
				if (village.npcId == npcId)
				{
					return village;
				}
			}
			return null;
		}
		
		private static Village fromKey(String key)
		{
			for (Village village : values())
			{
				if (village.key.equalsIgnoreCase(key))
				{
					return village;
				}
			}
			return null;
		}
		
		private static int[] npcIds()
		{
			final Village[] values = values();
			final int[] ids = new int[values.length];
			for (int i = 0; i < values.length; i++)
			{
				ids[i] = values[i].npcId;
			}
			return ids;
		}
		
		private static int[] allTargetIds()
		{
			final Set<Integer> ids = new HashSet<>();
			for (Village village : values())
			{
				ids.addAll(village.targets);
			}
			final int[] result = new int[ids.size()];
			int index = 0;
			for (int id : ids)
			{
				result[index++] = id;
			}
			return result;
		}
	}
	
	private static final class Progress
	{
		private final String village;
		private final String path;
		private final int progress;
		private final int goal;
		private final String status;
		
		private Progress(String village, String path, int progress, int goal, String status)
		{
			this.village = village;
			this.path = path;
			this.progress = progress;
			this.goal = goal;
			this.status = status;
		}
	}
	
	public static void main(String[] args)
	{
		new MegaraxForgottenVillages();
	}
}
