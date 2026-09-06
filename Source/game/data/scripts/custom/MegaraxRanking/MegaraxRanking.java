/*
 * Custom Megarax Ranking Manager - L2JMobius Interlude
 */
package custom.MegaraxRanking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.model.item.type.WeaponType;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

import ai.AbstractNpcAI;
import custom.MegaraxLivingChronicle.MegaraxLivingChronicle;

public class MegaraxRanking extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxRanking.class.getName());
	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
	
	private static final int NPC = 31365;
	private static final int LIMIT = 10;
	private static final int MIN_RELIC_ENCHANT = 15;
	private static final String CLAN_ICON = "L2UI_CH3.mainwndtabicon4";
	private static final String RELIC_ICON = "icon.weapon_the_sword_of_hero_i00";
	private static final String CREATE_RELICS = "CREATE TABLE IF NOT EXISTS megarax_relics (relic_id BIGINT NOT NULL AUTO_INCREMENT, object_id INT NOT NULL, item_id INT NOT NULL, grade CHAR(1) NOT NULL, first_owner_id INT NOT NULL, first_owner_name VARCHAR(35) NOT NULL, first_clan_name VARCHAR(45) NOT NULL DEFAULT '', first_enchant_level INT NOT NULL, highest_enchant_level INT NOT NULL, highest_achieved_at BIGINT NOT NULL, registered_at BIGINT NOT NULL, last_owner_id INT NOT NULL, last_owner_name VARCHAR(35) NOT NULL, last_clan_name VARCHAR(45) NOT NULL DEFAULT '', last_registered_at BIGINT NOT NULL, PRIMARY KEY (relic_id), UNIQUE KEY uq_megarax_relic_object (object_id), KEY idx_megarax_relic_grade_enchant (grade,highest_enchant_level), KEY idx_megarax_relic_owner (last_owner_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String CREATE_RELIC_FIRSTS = "CREATE TABLE IF NOT EXISTS megarax_relic_firsts (grade CHAR(1) NOT NULL, enchant_level INT NOT NULL, relic_id BIGINT NOT NULL, object_id INT NOT NULL, item_id INT NOT NULL, player_id INT NOT NULL, player_name VARCHAR(35) NOT NULL, clan_name VARCHAR(45) NOT NULL DEFAULT '', achieved_at BIGINT NOT NULL, PRIMARY KEY (grade,enchant_level), KEY idx_megarax_relic_first_time (achieved_at), KEY idx_megarax_relic_first_player (player_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	
	private MegaraxRanking()
	{
		initRelicTables();
		addStartNpc(NPC);
		addTalkId(NPC);
		addFirstTalkId(NPC);
		LOGGER.info("MegaraxRanking: Script loaded for NPC " + NPC + ".");
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
		
		switch (event)
		{
			case "main":
				showMain(player);
				break;
			case "pvp":
				showCharacterRanking(player, "PvP Champions", "PvP", "icon.skill1260", "SELECT char_name, pvpkills AS score FROM characters WHERE accesslevel=0 AND deletetime=0 ORDER BY pvpkills DESC, level DESC LIMIT " + LIMIT);
				break;
			case "pk":
				showCharacterRanking(player, "PK Hunters", "PK", "icon.skill1068", "SELECT char_name, pkkills AS score FROM characters WHERE accesslevel=0 AND deletetime=0 ORDER BY pkkills DESC, level DESC LIMIT " + LIMIT);
				break;
			case "clans":
				showClanRanking(player);
				break;
			case "relics":
				showRelicsMain(player);
				break;
			case "relics_a":
				showRelicRanking(player, "A");
				break;
			case "relics_s":
				showRelicRanking(player, "S");
				break;
			case "relic_firsts":
				showRelicFirsts(player);
				break;
			case "register_relics":
				registerPlayerRelics(player);
				break;
			default:
				showMain(player);
				break;
		}
		
		return null;
	}
	
	private void showMain(Player player)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Megarax Ranking</title><body><center>");
		header(html, "Megarax Ranking");
		html.append("<table width=292 bgcolor=\"000000\"><tr><td align=center>");
		html.append("<font color=\"0099FF\">Hall of Fame</font><br1>");
		html.append("<font color=\"ae9977\">PvP, PK and clan dominance.</font>");
		html.append("</td></tr></table><br>");
		
		html.append("<table width=292 bgcolor=\"000000\">");
		menuRow(html, "PvP", "Top PvP killers", "icon.skill1260", "Quest MegaraxRanking pvp");
		menuRow(html, "PK", "Most feared killers", "icon.skill1068", "Quest MegaraxRanking pk");
		menuRow(html, "Clan", "Reputation leaders", CLAN_ICON, "Quest MegaraxRanking clans");
		menuRow(html, "Reliquias Vivas", "Armas A/S que escriben la historia", RELIC_ICON, "Quest MegaraxRanking relics");
		html.append("</table>");
		html.append("</center></body></html>");
		send(player, html);
	}

	private void initRelicTables()
	{
		try (Connection con = DatabaseFactory.getConnection(); Statement st = con.createStatement())
		{
			st.executeUpdate(CREATE_RELICS);
			st.executeUpdate(CREATE_RELIC_FIRSTS);
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxRanking: Could not initialize relic tables: " + e.getMessage());
		}
	}

	private void showRelicsMain(Player player)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Reliquias Vivas</title><body><center>");
		header(html, "RELIQUIAS VIVAS");
		html.append("<table width=292 cellpadding=5 bgcolor=\"000000\"><tr><td align=center>");
		html.append("<font color=F0D58A>EL ARSENAL LEGENDARIO DE MEGARAX</font><br1>");
		html.append("<font color=AE9977>El poder cambia de manos. La historia permanece.</font><br>");
		html.append("<font color=777777>Se aceptan armas grado A y S desde +").append(MIN_RELIC_ENCHANT).append(".</font>");
		html.append("</td></tr></table><br>");
		relicButton(html, "RELIQUIAS GRADO S", "bypass -h Quest MegaraxRanking relics_s");
		html.append("<br1>");
		relicButton(html, "RELIQUIAS GRADO A", "bypass -h Quest MegaraxRanking relics_a");
		html.append("<br1>");
		relicButton(html, "PRIMEROS LEGENDARIOS", "bypass -h Quest MegaraxRanking relic_firsts");
		html.append("<br1>");
		relicButton(html, "INSCRIBIR MIS RELIQUIAS", "bypass -h Quest MegaraxRanking register_relics");
		html.append("<br><font color=777777>El NPC no retira, modifica ni bloquea tu arma.</font><br>");
		relicButton(html, "VOLVER AL RANKING", "bypass -h Quest MegaraxRanking main");
		html.append("</center></body></html>");
		send(player, html);
	}

	private void showRelicRanking(Player player, String grade)
	{
		final String safeGrade = "S".equalsIgnoreCase(grade) ? "S" : "A";
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Reliquias grado ").append(safeGrade).append("</title><body><center>");
		header(html, "RELIQUIAS GRADO " + safeGrade);
		html.append("<table width=292 cellpadding=3 bgcolor=\"000000\">");
		html.append("<tr><td width=24><font color=LEVEL>#</font></td><td width=216><font color=LEVEL>Portador y reliquia</font></td><td width=52 align=right><font color=LEVEL>Poder</font></td></tr>");
		int rank = 1;
		final String sql = "SELECT r.relic_id,i.item_id,i.enchant_level,c.char_name FROM megarax_relics r INNER JOIN items i ON i.object_id=r.object_id INNER JOIN characters c ON c.charId=i.owner_id WHERE r.grade=? AND i.enchant_level>=? AND c.accesslevel=0 AND c.deletetime=0 ORDER BY i.enchant_level DESC,r.highest_achieved_at ASC LIMIT 50";
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setString(1, safeGrade);
			ps.setInt(2, MIN_RELIC_ENCHANT);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next() && (rank <= LIMIT))
				{
					final ItemTemplate template = ItemData.getInstance().getTemplate(rs.getInt("item_id"));
					if (!isEligibleRelicTemplate(template, safeGrade))
					{
						continue;
					}
					relicRow(html, rank++, rs.getString("char_name"), serial(safeGrade, rs.getLong("relic_id")), template.getName(), rs.getInt("enchant_level"));
				}
			}
		}
		catch (SQLException e)
		{
			html.append("<tr><td colspan=3 align=center><font color=FF6666>El registro no esta disponible temporalmente.</font></td></tr>");
			LOGGER.warning("MegaraxRanking: Could not load grade " + safeGrade + " relics: " + e.getMessage());
		}
		if (rank == 1)
		{
			html.append("<tr><td colspan=3 align=center><font color=777777>Aun no existen reliquias grado ").append(safeGrade).append(" inscritas.</font></td></tr>");
		}
		html.append("</table><br>");
		relicButton(html, "ACTUALIZAR", "bypass -h Quest MegaraxRanking relics_" + safeGrade.toLowerCase(Locale.ROOT));
		html.append("<br1>");
		relicButton(html, "VOLVER A RELIQUIAS", "bypass -h Quest MegaraxRanking relics");
		html.append("</center></body></html>");
		send(player, html);
	}

	private void showRelicFirsts(Player player)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Primeros Legendarios</title><body><center>");
		header(html, "PRIMEROS LEGENDARIOS");
		html.append("<font color=AE9977>Estos nombres no pueden ser desplazados de la historia.</font><br><br>");
		int count = 0;
		final String sql = "SELECT grade,enchant_level,relic_id,item_id,player_name,clan_name,achieved_at FROM megarax_relic_firsts ORDER BY enchant_level DESC,grade DESC,achieved_at ASC LIMIT ?";
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setInt(1, LIMIT);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					count++;
					final ItemTemplate template = ItemData.getInstance().getTemplate(rs.getInt("item_id"));
					final String itemName = template == null ? "Arma desconocida" : template.getName();
					final String clan = rs.getString("clan_name");
					html.append("<table width=292 cellpadding=3 bgcolor=\"000000\"><tr><td width=210>");
					html.append("<font color=F0D58A>").append(escape(rs.getString("player_name"))).append("</font>");
					if ((clan != null) && !clan.isEmpty())
					{
						html.append(" <font color=777777>[").append(escape(clan)).append("]</font>");
					}
					html.append("<br1><font color=LEVEL>Primer grado ").append(rs.getString("grade")).append(" +").append(rs.getInt("enchant_level")).append("</font>");
					html.append("<br1><font color=AE9977>").append(escape(itemName)).append(" - ").append(serial(rs.getString("grade"), rs.getLong("relic_id"))).append("</font></td>");
					html.append("<td width=82 align=right><font color=777777>").append(formatRelicDate(rs.getLong("achieved_at"))).append("</font></td></tr></table>");
					html.append("<img src=\"L2UI.SquareGray\" width=292 height=1><br1>");
				}
			}
		}
		catch (SQLException e)
		{
			html.append("<font color=FF6666>La historia no esta disponible temporalmente.</font><br>");
			LOGGER.warning("MegaraxRanking: Could not load relic firsts: " + e.getMessage());
		}
		if (count == 0)
		{
			html.append("<font color=777777>Aun no se ha inscrito ningun primer hito.</font><br>");
		}
		relicButton(html, "VOLVER A RELIQUIAS", "bypass -h Quest MegaraxRanking relics");
		html.append("</center></body></html>");
		send(player, html);
	}

	private void registerPlayerRelics(Player player)
	{
		if (player.isGM())
		{
			player.sendMessage("Reliquias Vivas: los personajes GM no participan en el registro.");
			showRelicsMain(player);
			return;
		}

		final List<Item> relics = new ArrayList<>();
		for (Item item : player.getInventory().getItems())
		{
			if ((item.getOwnerId() == player.getObjectId()) && isEligibleRelic(item))
			{
				relics.add(item);
			}
		}
		relics.sort(Comparator.comparingInt(Item::getEnchantLevel).reversed().thenComparingInt(Item::getObjectId));

		if (relics.isEmpty())
		{
			player.sendMessage("Reliquias Vivas: no tienes armas grado A o S de +" + MIN_RELIC_ENCHANT + " o superior en tu inventario.");
			showRelicsMain(player);
			return;
		}

		int registered = 0;
		int improved = 0;
		int firsts = 0;
		for (Item item : relics)
		{
			final RelicRegistration result = registerRelic(player, item);
			if (result == null)
			{
				continue;
			}
			registered++;
			if (result.newRelic || result.improved)
			{
				improved++;
				MegaraxLivingChronicle.recordEvent(player, "RELIC", "weapon:" + item.getObjectId() + ":" + item.getEnchantLevel(), "Inscribio " + item.getItemName() + " +" + item.getEnchantLevel(), "La reliquia " + serial(result.grade, result.relicId) + " entro en el arsenal legendario.", result.newRelic ? 3 : 2, false);
			}
			if (!result.firstMilestones.isEmpty())
			{
				firsts += result.firstMilestones.size();
				final int highestFirst = result.firstMilestones.get(result.firstMilestones.size() - 1);
				MegaraxLivingChronicle.recordEvent(player, "RELIC_FIRST", "grade:" + result.grade + ":enchant:" + highestFirst, "Primera reliquia grado " + result.grade + " +" + highestFirst, item.getItemName() + " quedo inscrita como " + serial(result.grade, result.relicId) + ".", 5, true);
			}
		}

		if (registered == 0)
		{
			player.sendMessage("Reliquias Vivas: no fue posible verificar tus armas. Intentalo nuevamente.");
		}
		else
		{
			player.sendMessage("Reliquias Vivas: " + registered + " arma(s) verificadas, " + improved + " registro(s) nuevos o mejorados y " + firsts + " primer(os) hito(s) conservados.");
		}
		showRelicRanking(player, relics.get(0).getTemplate().getCrystalType() == CrystalType.S ? "S" : "A");
	}

	private RelicRegistration registerRelic(Player player, Item item)
	{
		final String grade = gradeOf(item.getTemplate());
		final String clanName = getClanName(player);
		final int enchant = item.getEnchantLevel();
		final long now = System.currentTimeMillis();
		try (Connection con = DatabaseFactory.getConnection())
		{
			con.setAutoCommit(false);
			try
			{
				long relicId = 0;
				int previousHighest = -1;
				try (PreparedStatement ps = con.prepareStatement("SELECT relic_id,highest_enchant_level FROM megarax_relics WHERE object_id=? FOR UPDATE"))
				{
					ps.setInt(1, item.getObjectId());
					try (ResultSet rs = ps.executeQuery())
					{
						if (rs.next())
						{
							relicId = rs.getLong("relic_id");
							previousHighest = rs.getInt("highest_enchant_level");
						}
					}
				}

				final boolean newRelic = relicId == 0;
				final boolean improved = !newRelic && (enchant > previousHighest);
				if (newRelic)
				{
					final String sql = "INSERT INTO megarax_relics (object_id,item_id,grade,first_owner_id,first_owner_name,first_clan_name,first_enchant_level,highest_enchant_level,highest_achieved_at,registered_at,last_owner_id,last_owner_name,last_clan_name,last_registered_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
					try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS))
					{
						ps.setInt(1, item.getObjectId());
						ps.setInt(2, item.getId());
						ps.setString(3, grade);
						ps.setInt(4, player.getObjectId());
						ps.setString(5, player.getName());
						ps.setString(6, clanName);
						ps.setInt(7, enchant);
						ps.setInt(8, enchant);
						ps.setLong(9, now);
						ps.setLong(10, now);
						ps.setInt(11, player.getObjectId());
						ps.setString(12, player.getName());
						ps.setString(13, clanName);
						ps.setLong(14, now);
						ps.executeUpdate();
						try (ResultSet keys = ps.getGeneratedKeys())
						{
							if (keys.next())
							{
								relicId = keys.getLong(1);
							}
						}
					}
				}
				else
				{
					final int highest = Math.max(previousHighest, enchant);
					final long highestAt = improved ? now : loadHighestAchievedAt(con, relicId);
					final String sql = "UPDATE megarax_relics SET item_id=?,grade=?,highest_enchant_level=?,highest_achieved_at=?,last_owner_id=?,last_owner_name=?,last_clan_name=?,last_registered_at=? WHERE relic_id=?";
					try (PreparedStatement ps = con.prepareStatement(sql))
					{
						ps.setInt(1, item.getId());
						ps.setString(2, grade);
						ps.setInt(3, highest);
						ps.setLong(4, highestAt);
						ps.setInt(5, player.getObjectId());
						ps.setString(6, player.getName());
						ps.setString(7, clanName);
						ps.setLong(8, now);
						ps.setLong(9, relicId);
						ps.executeUpdate();
					}
				}

				if (relicId <= 0)
				{
					throw new SQLException("No relic id returned for object " + item.getObjectId());
				}

				final List<Integer> firstMilestones = insertFirstMilestones(con, grade, relicId, item, player, clanName, now);
				con.commit();
				return new RelicRegistration(relicId, grade, newRelic, improved, firstMilestones);
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
			LOGGER.warning("MegaraxRanking: Could not register relic object " + item.getObjectId() + " for " + player.getName() + ": " + e.getMessage());
			return null;
		}
	}

	private long loadHighestAchievedAt(Connection con, long relicId) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement("SELECT highest_achieved_at FROM megarax_relics WHERE relic_id=?"))
		{
			ps.setLong(1, relicId);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? rs.getLong(1) : System.currentTimeMillis();
			}
		}
	}

	private List<Integer> insertFirstMilestones(Connection con, String grade, long relicId, Item item, Player player, String clanName, long now) throws SQLException
	{
		final List<Integer> inserted = new ArrayList<>();
		final int enchant = item.getEnchantLevel();
		final int sequentialEnd = Math.min(enchant, 30);
		final String sql = "INSERT IGNORE INTO megarax_relic_firsts (grade,enchant_level,relic_id,object_id,item_id,player_id,player_name,clan_name,achieved_at) VALUES (?,?,?,?,?,?,?,?,?)";
		try (PreparedStatement ps = con.prepareStatement(sql))
		{
			for (int level = MIN_RELIC_ENCHANT; level <= sequentialEnd; level++)
			{
				if (insertFirstMilestone(ps, grade, level, relicId, item, player, clanName, now))
				{
					inserted.add(level);
				}
			}
			if ((enchant > 30) && insertFirstMilestone(ps, grade, enchant, relicId, item, player, clanName, now))
			{
				inserted.add(enchant);
			}
		}
		return inserted;
	}

	private boolean insertFirstMilestone(PreparedStatement ps, String grade, int level, long relicId, Item item, Player player, String clanName, long now) throws SQLException
	{
		ps.setString(1, grade);
		ps.setInt(2, level);
		ps.setLong(3, relicId);
		ps.setInt(4, item.getObjectId());
		ps.setInt(5, item.getId());
		ps.setInt(6, player.getObjectId());
		ps.setString(7, player.getName());
		ps.setString(8, clanName);
		ps.setLong(9, now);
		return ps.executeUpdate() > 0;
	}

	private boolean isEligibleRelic(Item item)
	{
		if ((item == null) || (item.getOwnerId() <= 0) || (item.getEnchantLevel() < MIN_RELIC_ENCHANT) || item.isShadowItem() || item.isTimeLimitedItem())
		{
			return false;
		}
		return isEligibleRelicTemplate(item.getTemplate(), null);
	}

	private boolean isEligibleRelicTemplate(ItemTemplate template, String expectedGrade)
	{
		if ((template == null) || !template.isWeapon() || !template.isEnchantable() || !template.isTradeable() || template.isHeroItem() || template.isCommon())
		{
			return false;
		}
		final String grade = gradeOf(template);
		if (grade.isEmpty() || ((expectedGrade != null) && !expectedGrade.equals(grade)))
		{
			return false;
		}
		if (template.getItemType() instanceof WeaponType)
		{
			final WeaponType type = (WeaponType) template.getItemType();
			return (type != WeaponType.FLAG) && (type != WeaponType.FISHINGROD) && (type != WeaponType.NONE);
		}
		return false;
	}

	private String gradeOf(ItemTemplate template)
	{
		if (template == null)
		{
			return "";
		}
		if (template.getCrystalType() == CrystalType.S)
		{
			return "S";
		}
		return template.getCrystalType() == CrystalType.A ? "A" : "";
	}

	private String getClanName(Player player)
	{
		return player.getClan() == null ? "" : player.getClan().getName();
	}

	private void relicRow(StringBuilder html, int rank, String owner, String serial, String itemName, int enchant)
	{
		final String color = rank == 1 ? "FFCC00" : rank == 2 ? "C0C0C0" : rank == 3 ? "CD7F32" : "FFFFFF";
		html.append("<tr><td><font color=").append(color).append(">").append(rank).append("</font></td><td>");
		html.append("<font color=").append(color).append(">").append(escape(owner)).append("</font><br1>");
		html.append("<font color=777777>").append(escape(serial)).append("</font> <font color=AE9977>").append(escape(itemName)).append("</font></td>");
		html.append("<td align=right><font color=").append(color).append(">+").append(enchant).append("</font></td></tr>");
	}

	private void relicButton(StringBuilder html, String value, String action)
	{
		html.append("<button value=\"").append(escape(value)).append("\" action=\"").append(action).append("\" width=260 height=24 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">");
	}

	private String serial(String grade, long relicId)
	{
		return String.format(Locale.US, "MX-%s-%04d", grade, relicId);
	}

	private String formatRelicDate(long time)
	{
		return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(time));
	}
	
	private void showCharacterRanking(Player player, String title, String scoreName, String icon, String sql)
	{
		final StringBuilder html = rankingPage(title, icon, scoreName);
		int rank = 1;
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(sql);
			ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
			{
				final long score = rs.getLong("score");
				if (score <= 0)
				{
					continue;
				}
				row(html, rank++, rs.getString("char_name"), format(score));
			}
		}
		catch (SQLException e)
		{
			appendError(html, "Ranking temporarily unavailable.");
			LOGGER.warning("MegaraxRanking: Could not load " + title + ": " + e.getMessage());
		}
		finishRanking(player, html, rank);
	}
	
	private void showClanRanking(Player player)
	{
		final StringBuilder html = rankingPage("Clan Dominion", CLAN_ICON, "Rep");
		int rank = 1;
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT clan_name, clan_level, reputation_score FROM clan_data ORDER BY reputation_score DESC, clan_level DESC LIMIT " + LIMIT);
			ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
			{
				row(html, rank++, rs.getString("clan_name") + " Lv." + rs.getInt("clan_level"), format(rs.getLong("reputation_score")));
			}
		}
		catch (SQLException e)
		{
			appendError(html, "Clan ranking temporarily unavailable.");
			LOGGER.warning("MegaraxRanking: Could not load clans: " + e.getMessage());
		}
		finishRanking(player, html, rank);
	}
	
	private void showItemRanking(Player player, String title, String scoreName, String icon, int itemId)
	{
		final StringBuilder html = rankingPage(title, icon, scoreName);
		int rank = 1;
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT c.char_name, SUM(i.`count`) AS total FROM items i INNER JOIN characters c ON c.charId=i.owner_id WHERE i.item_id=? AND c.accesslevel=0 AND c.deletetime=0 GROUP BY c.charId, c.char_name ORDER BY total DESC LIMIT ?"))
		{
			ps.setInt(1, itemId);
			ps.setInt(2, LIMIT);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					row(html, rank++, rs.getString("char_name"), format(rs.getLong("total")));
				}
			}
		}
		catch (SQLException e)
		{
			appendError(html, "Currency ranking temporarily unavailable.");
			LOGGER.warning("MegaraxRanking: Could not load item ranking " + itemId + ": " + e.getMessage());
		}
		finishRanking(player, html, rank);
	}
	
	private void showHeroes(Player player)
	{
		final StringBuilder html = rankingPage("Active Heroes", "icon.weapon_the_sword_of_hero_i00", "Days");
		int rank = 1;
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT c.char_name, h.expires_at FROM megarax_hero_service h INNER JOIN characters c ON c.charId=h.char_id WHERE h.expires_at>? ORDER BY h.expires_at DESC LIMIT ?"))
		{
			ps.setLong(1, System.currentTimeMillis());
			ps.setInt(2, LIMIT);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					final long days = Math.max(1, (rs.getLong("expires_at") - System.currentTimeMillis()) / 86400000L);
					row(html, rank++, rs.getString("char_name"), days + "d");
				}
			}
		}
		catch (SQLException e)
		{
			appendError(html, "Hero ranking temporarily unavailable.");
			LOGGER.warning("MegaraxRanking: Could not load heroes: " + e.getMessage());
		}
		finishRanking(player, html, rank);
	}
	
	private void showDungeonRanking(Player player)
	{
		final StringBuilder html = rankingPage("Dungeon Speed", "icon.etc_treasure_box_i00", "Time");
		int rank = 1;
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT player_name, dungeon, route, time_seconds FROM megarax_dungeon_ranking WHERE week_key=? ORDER BY time_seconds ASC LIMIT ?"))
		{
			ps.setString(1, getWeekKey());
			ps.setInt(2, LIMIT);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					row(html, rank++, rs.getString("player_name") + " - " + rs.getString("dungeon") + " " + rs.getString("route"), formatTime(rs.getInt("time_seconds")));
				}
			}
		}
		catch (SQLException e)
		{
			appendError(html, "Dungeon ranking starts after the first completed run.");
			LOGGER.warning("MegaraxRanking: Could not load dungeon ranking: " + e.getMessage());
		}
		if (rank == 1)
		{
			html.append("<tr><td align=center colspan=3><font color=\"777777\">No dungeon runs this week.</font></td></tr>");
		}
		html.append("</table><br>");
		html.append("<button value=\"Claim Rewards\" action=\"bypass -h Quest MegaraxDungeon claim_ranking_rewards\" width=140 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		html.append("<br1><button value=\"Back\" action=\"bypass -h Quest MegaraxRanking main\" width=90 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		html.append("</center></body></html>");
		send(player, html);
	}
	
	private StringBuilder rankingPage(String title, String icon, String scoreName)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>").append(escape(title)).append("</title><body><center>");
		header(html, title);
		html.append("<table width=292 bgcolor=\"000000\"><tr>");
		html.append("<td width=36 align=center><button action=\"bypass -h Quest MegaraxRanking main\" width=32 height=32 back=\"").append(icon).append("\" fore=\"").append(icon).append("\"></td>");
		html.append("<td width=256><font color=\"0099FF\">").append(escape(title)).append("</font><br1>");
		html.append("<font color=\"ae9977\">Updated from live server data.</font></td>");
		html.append("</tr></table><br>");
		html.append("<table width=292 bgcolor=\"000000\">");
		html.append("<tr><td width=28><font color=\"LEVEL\">#</font></td><td width=184><font color=\"LEVEL\">Name</font></td><td width=80 align=right><font color=\"LEVEL\">").append(escape(scoreName)).append("</font></td></tr>");
		return html;
	}
	
	private void finishRanking(Player player, StringBuilder html, int rank)
	{
		if (rank == 1)
		{
			html.append("<tr><td align=center colspan=3><font color=\"777777\">No ranking data yet.</font></td></tr>");
		}
		html.append("</table><br>");
		html.append("<button value=\"Refresh\" action=\"bypass -h Quest MegaraxRanking main\" width=90 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		html.append(" <button value=\"Back\" action=\"bypass -h Quest MegaraxRanking main\" width=90 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		html.append("</center></body></html>");
		send(player, html);
	}
	
	private void row(StringBuilder html, int rank, String name, String score)
	{
		final String color = rank == 1 ? "FFCC00" : rank == 2 ? "C0C0C0" : rank == 3 ? "CD7F32" : "FFFFFF";
		html.append("<tr>");
		html.append("<td><font color=\"").append(color).append("\">").append(rank).append("</font></td>");
		html.append("<td><font color=\"").append(color).append("\">").append(escape(name)).append("</font></td>");
		html.append("<td align=right><font color=\"").append(color).append("\">").append(escape(score)).append("</font></td>");
		html.append("</tr>");
	}
	
	private void menuRow(StringBuilder html, String title, String desc, String icon, String bypass)
	{
		html.append("<tr><td width=36 align=center>");
		html.append("<button action=\"bypass -h ").append(bypass).append("\" width=32 height=32 back=\"").append(icon).append("\" fore=\"").append(icon).append("\">");
		html.append("</td><td width=178>");
		html.append("<font color=\"0099FF\">").append(escape(title)).append("</font><br1>");
		html.append("<font color=\"ae9977\">").append(escape(desc)).append("</font>");
		html.append("</td><td width=64 align=center>");
		html.append("<button value=\"Open\" action=\"bypass -h ").append(bypass).append("\" width=60 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		html.append("</td></tr><tr><td height=3></td></tr>");
	}
	
	private void header(StringBuilder html, String title)
	{
		html.append("<button value=\"").append(escape(title)).append("\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
	}
	
	private void appendError(StringBuilder html, String message)
	{
		html.append("<tr><td align=center colspan=3><font color=\"FF6666\">").append(escape(message)).append("</font></td></tr>");
	}
	
	private void send(Player player, StringBuilder html)
	{
		final NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html.toString());
		player.sendPacket(msg);
	}
	
	private String getWeekKey()
	{
		final LocalDate date = LocalDate.now();
		final WeekFields weekFields = WeekFields.of(Locale.US);
		return date.get(weekFields.weekBasedYear()) + "-" + String.format("%02d", date.get(weekFields.weekOfWeekBasedYear()));
	}
	
	private String formatTime(int totalSeconds)
	{
		final int minutes = totalSeconds / 60;
		final int seconds = totalSeconds % 60;
		return minutes + "m " + seconds + "s";
	}
	
	private static String format(long value)
	{
		return NUMBER_FORMAT.format(value);
	}
	
	private static String escape(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "'");
	}

	private static final class RelicRegistration
	{
		private final long relicId;
		private final String grade;
		private final boolean newRelic;
		private final boolean improved;
		private final List<Integer> firstMilestones;

		private RelicRegistration(long relicId, String grade, boolean newRelic, boolean improved, List<Integer> firstMilestones)
		{
			this.relicId = relicId;
			this.grade = grade;
			this.newRelic = newRelic;
			this.improved = improved;
			this.firstMilestones = firstMilestones;
		}
	}
	
	public static void main(String[] args)
	{
		new MegaraxRanking();
	}
}
