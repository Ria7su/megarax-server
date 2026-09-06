/*
 * Custom Megarax Hero Service - L2JMobius Interlude
 */
package custom.MegaraxHeroService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.Containers;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogin;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

import ai.AbstractNpcAI;

public class MegaraxHeroService extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxHeroService.class.getName());
	
	private static final int SERVICES_NPC = 9911;
	private static final int MEGA_COIN = 6673;
	private static final int HERO_COST = 10;
	private static final int HERO_DAYS = 30;
	private static final long DAY = 24L * 60 * 60 * 1000;
	
	private static final int HERO_CIRCLET = 6842;
	private static final int[] HERO_SKILLS =
	{
		395, // Heroic Miracle
		396, // Heroic Berserker
		1374, // Heroic Valor
		1375, // Heroic Grandeur
		1376, // Heroic Dread
	};
	private static final int[] HERO_WEAPONS =
	{
		6611, // Infinity Blade
		6612, // Infinity Cleaver
		6613, // Infinity Axe
		6614, // Infinity Rod
		6615, // Infinity Crusher
		6616, // Infinity Scepter
		6617, // Infinity Stinger
		6618, // Infinity Fang
		6619, // Infinity Bow
		6620, // Infinity Wing
		6621, // Infinity Spear
	};
	
	private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS megarax_hero_service (char_id INT NOT NULL PRIMARY KEY, expires_at BIGINT NOT NULL)";
	private static final String SELECT_SQL = "SELECT expires_at FROM megarax_hero_service WHERE char_id=?";
	private static final String UPSERT_SQL = "REPLACE INTO megarax_hero_service (char_id, expires_at) VALUES (?, ?)";
	private static final String DELETE_SQL = "DELETE FROM megarax_hero_service WHERE char_id=?";
	private static final String EXPIRED_SQL = "SELECT char_id FROM megarax_hero_service WHERE expires_at<=?";
	
	private MegaraxHeroService()
	{
		initTable();
		cleanupExpiredHeroes();
		Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_LOGIN, (OnPlayerLogin event) -> onPlayerLogin(event), this));
		ThreadPool.scheduleAtFixedRate(this::cleanupExpiredHeroes, 10 * 60 * 1000, 10 * 60 * 1000);
		LOGGER.info("MegaraxHeroService: Script loaded for Services NPC " + SERVICES_NPC + ".");
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
			{
				checkPlayerHero(player);
				showMain(player, npc);
				break;
			}
			case "become":
			{
				activateHero(player, npc);
				showMain(player, npc);
				break;
			}
			case "weapons":
			{
				checkPlayerHero(player);
				showWeapons(player);
				break;
			}
			case "circlet":
			{
				checkPlayerHero(player);
				claimCirclet(player, npc);
				showMain(player, npc);
				break;
			}
			default:
			{
				if (event.startsWith("weapon_"))
				{
					checkPlayerHero(player);
					claimWeapon(player, npc, event.substring(7));
					showWeapons(player);
				}
				else
				{
					showMain(player, npc);
				}
				break;
			}
		}
		
		return null;
	}
	
	private void activateHero(Player player, Npc npc)
	{
		final long activeUntil = getHeroExpiration(player.getObjectId());
		if (activeUntil > System.currentTimeMillis())
		{
			player.sendMessage("You already have active Megarax Hero status.");
			applyHero(player);
			return;
		}
		
		if (player.getInventory().getInventoryItemCount(MEGA_COIN, -1) < HERO_COST)
		{
			player.sendMessage("You need " + HERO_COST + " Mega Coin to become Hero.");
			return;
		}
		
		if (!player.destroyItemByItemId(ItemProcessType.FEE, MEGA_COIN, HERO_COST, npc == null ? player : npc, true))
		{
			return;
		}
		
		final long expiresAt = System.currentTimeMillis() + (HERO_DAYS * DAY);
		saveHero(player.getObjectId(), expiresAt);
		applyHero(player);
		giveItems(player, HERO_CIRCLET, 1);
		scheduleExpiration(player.getObjectId(), expiresAt);
		player.sendMessage("Megarax Hero activated for " + HERO_DAYS + " days.");
	}
	
	private void applyHero(Player player)
	{
		if (!player.isHero())
		{
			player.setHero(true);
		}
		
		for (int skillId : HERO_SKILLS)
		{
			final Skill skill = SkillData.getInstance().getSkill(skillId, 1);
			if (skill != null)
			{
				player.addSkill(skill, true);
			}
		}
		
		player.sendSkillList();
		player.broadcastUserInfo();
	}
	
	private void removeHero(Player player)
	{
		if (player.isHero())
		{
			player.setHero(false);
		}
		
		for (int skillId : HERO_SKILLS)
		{
			final Skill skill = SkillData.getInstance().getSkill(skillId, Math.max(1, player.getSkillLevel(skillId)));
			if (skill != null)
			{
				player.removeSkill(skill);
			}
		}
		
		removeHeroItems(player);
		player.sendSkillList();
		player.broadcastUserInfo();
		player.sendMessage("Your Megarax Hero status has expired.");
	}
	
	private void claimCirclet(Player player, Npc npc)
	{
		if (!isActiveMegaraxHero(player))
		{
			player.sendMessage("You must be an active Megarax Hero.");
			return;
		}
		if (getQuestItemsCount(player, HERO_CIRCLET) > 0)
		{
			player.sendMessage("You already have the Hero circlet.");
			return;
		}
		giveItems(player, HERO_CIRCLET, 1);
	}
	
	private void claimWeapon(Player player, Npc npc, String weaponText)
	{
		if (!isActiveMegaraxHero(player))
		{
			player.sendMessage("You must be an active Megarax Hero.");
			return;
		}
		
		int weaponId;
		try
		{
			weaponId = Integer.parseInt(weaponText);
		}
		catch (NumberFormatException e)
		{
			player.sendMessage("Invalid Hero weapon.");
			return;
		}
		
		if (!isHeroWeapon(weaponId))
		{
			player.sendMessage("Invalid Hero weapon.");
			return;
		}
		
		if (hasHeroWeapon(player))
		{
			player.sendMessage("You can only have one Hero weapon at a time.");
			return;
		}
		
		giveItems(player, weaponId, 1);
	}
	
	private boolean isActiveMegaraxHero(Player player)
	{
		return getHeroExpiration(player.getObjectId()) > System.currentTimeMillis();
	}
	
	private void checkPlayerHero(Player player)
	{
		final long expiresAt = getHeroExpiration(player.getObjectId());
		if (expiresAt <= 0)
		{
			return;
		}
		
		if (expiresAt <= System.currentTimeMillis())
		{
			deleteHero(player.getObjectId());
			removeHero(player);
			return;
		}
		
		applyHero(player);
		scheduleExpiration(player.getObjectId(), expiresAt);
	}
	
	private void showMain(Player player, Npc npc)
	{
		final long megaCoins = player.getInventory().getInventoryItemCount(MEGA_COIN, -1);
		final long expiresAt = getHeroExpiration(player.getObjectId());
		final boolean active = expiresAt > System.currentTimeMillis();
		
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Megarax Hero Service</title><body><center>");
		html.append("<button value=\"Hero Service\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\"><tr>");
		html.append("<td width=36 align=center><img src=\"favero_l2_icons03.skill_clan_22\" width=32 height=32></td>");
		html.append("<td width=240><font color=\"0099FF\">Megarax Hero Status</font><br1>");
		html.append("<font color=\"ae9977\">Cost: ").append(HERO_COST).append(" Mega Coin / Duration: ").append(HERO_DAYS).append(" days</font></td>");
		html.append("</tr></table><br>");
		
		html.append("<button value=\"Hero Status\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\">");
		html.append("<tr><td align=center><font color=\"ae9977\">Your Mega Coin:</font> <font color=\"LEVEL\">").append(megaCoins).append("</font></td></tr>");
		html.append("<tr><td align=center><font color=\"ae9977\">Status:</font> <font color=\"LEVEL\">").append(active ? "Active" : "Inactive").append("</font></td></tr>");
		if (active)
		{
			html.append("<tr><td align=center><font color=\"ae9977\">Remaining:</font> <font color=\"LEVEL\">").append(formatRemaining(expiresAt)).append("</font></td></tr>");
		}
		html.append("</table><br>");
		
		html.append("<button value=\"Hero Options\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\">");
		appendButtonRow(html, "Become Hero", HERO_COST + " Mega Coin", "favero_l2_icons03.skill_clan_15", "Buy", "bypass -h Quest MegaraxHeroService become");
		appendButtonRow(html, "Hero Weapons", "Infinity weapons", "icon.weapon_the_sword_of_hero_i00", "Open", "bypass -h Quest MegaraxHeroService weapons");
		appendButtonRow(html, "Hero Circlet", "Wings of Destiny", "icon.accessory_wings_of_destiny_circlet_i00", "Get", "bypass -h Quest MegaraxHeroService circlet");
		html.append("</table><br>");
		if (npc != null)
		{
			html.append("<button value=\"Back\" action=\"bypass -h npc_").append(npc.getObjectId()).append("_Chat 0\" width=90 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		}
		else
		{
			html.append("<button value=\"Back\" action=\"bypass -h Quest MegaraxHeroService main\" width=90 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		}
		html.append("</center></body></html>");
		sendHtml(player, npc, html.toString());
	}
	
	private void showWeapons(Player player)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Megarax Hero Weapons</title><body><center>");
		html.append("<button value=\"Hero Weapons\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\">");
		appendWeaponRow(html, 6611, "Infinity Blade");
		appendWeaponRow(html, 6612, "Infinity Cleaver");
		appendWeaponRow(html, 6613, "Infinity Axe");
		appendWeaponRow(html, 6614, "Infinity Rod");
		appendWeaponRow(html, 6615, "Infinity Crusher");
		appendWeaponRow(html, 6616, "Infinity Scepter");
		appendWeaponRow(html, 6617, "Infinity Stinger");
		appendWeaponRow(html, 6618, "Infinity Fang");
		appendWeaponRow(html, 6619, "Infinity Bow");
		appendWeaponRow(html, 6620, "Infinity Wing");
		appendWeaponRow(html, 6621, "Infinity Spear");
		html.append("</table><br>");
		html.append("<button value=\"Back\" action=\"bypass -h Quest MegaraxHeroService main\" width=90 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		html.append("</center></body></html>");
		sendHtml(player, null, html.toString());
	}
	
	private void appendButtonRow(StringBuilder html, String title, String desc, String icon, String button, String bypass)
	{
		html.append("<tr><td width=36 align=center><button action=\"").append(bypass).append("\" width=32 height=32 back=\"").append(icon).append("\" fore=\"").append(icon).append("\"></td>");
		html.append("<td width=178><font color=\"0099FF\">").append(title).append("</font><br1><font color=\"ae9977\">").append(desc).append("</font></td>");
		html.append("<td width=64 align=center><button value=\"").append(button).append("\" action=\"").append(bypass).append("\" width=60 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td></tr>");
		html.append("<tr><td height=3></td></tr>");
	}
	
	private void appendWeaponRow(StringBuilder html, int itemId, String name)
	{
		html.append("<tr><td width=210><font color=\"0099FF\">").append(name).append("</font></td>");
		html.append("<td width=72 align=center><button value=\"Get\" action=\"bypass -h Quest MegaraxHeroService weapon_").append(itemId).append("\" width=60 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td></tr>");
		html.append("<tr><td height=3></td></tr>");
	}
	
	private String formatRemaining(long expiresAt)
	{
		long millis = Math.max(0, expiresAt - System.currentTimeMillis());
		final long days = millis / DAY;
		millis %= DAY;
		final long hours = millis / (60L * 60 * 1000);
		return days + "d " + hours + "h";
	}
	
	private void sendHtml(Player player, Npc npc, String html)
	{
		final NpcHtmlMessage msg = new NpcHtmlMessage(npc == null ? 0 : npc.getObjectId());
		msg.setHtml(html);
		player.sendPacket(msg);
	}
	
	private void onPlayerLogin(OnPlayerLogin event)
	{
		checkPlayerHero(event.getPlayer());
	}
	
	private void initTable()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(CREATE_TABLE))
		{
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxHeroService: Could not create table: " + e.getMessage());
		}
	}
	
	private long getHeroExpiration(int charId)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(SELECT_SQL))
		{
			ps.setInt(1, charId);
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					return rs.getLong("expires_at");
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxHeroService: Could not read hero status: " + e.getMessage());
		}
		return 0;
	}
	
	private void saveHero(int charId, long expiresAt)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(UPSERT_SQL))
		{
			ps.setInt(1, charId);
			ps.setLong(2, expiresAt);
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxHeroService: Could not save hero status: " + e.getMessage());
		}
	}
	
	private void deleteHero(int charId)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(DELETE_SQL))
		{
			ps.setInt(1, charId);
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxHeroService: Could not delete hero status: " + e.getMessage());
		}
	}
	
	private void cleanupExpiredHeroes()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(EXPIRED_SQL))
		{
			ps.setLong(1, System.currentTimeMillis());
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					final int charId = rs.getInt("char_id");
					final Player player = getPlayerByObjectId(charId);
					deleteHero(charId);
					if (player != null)
					{
						removeHero(player);
					}
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxHeroService: Could not cleanup expired heroes: " + e.getMessage());
		}
	}
	
	private void scheduleExpiration(int charId, long expiresAt)
	{
		final long delay = expiresAt - System.currentTimeMillis();
		if (delay <= 0)
		{
			ThreadPool.execute(() -> expireHero(charId));
		}
		else
		{
			ThreadPool.schedule(() -> expireHero(charId), delay);
		}
	}
	
	private void expireHero(int charId)
	{
		final long expiresAt = getHeroExpiration(charId);
		if ((expiresAt <= 0) || (expiresAt > System.currentTimeMillis()))
		{
			return;
		}
		deleteHero(charId);
		final Player player = getPlayerByObjectId(charId);
		if (player != null)
		{
			removeHero(player);
		}
	}
	
	private boolean hasHeroWeapon(Player player)
	{
		for (int weaponId : HERO_WEAPONS)
		{
			if (getQuestItemsCount(player, weaponId) > 0)
			{
				return true;
			}
		}
		return false;
	}
	
	private boolean isHeroWeapon(int itemId)
	{
		for (int weaponId : HERO_WEAPONS)
		{
			if (weaponId == itemId)
			{
				return true;
			}
		}
		return false;
	}
	
	private void removeHeroItems(Player player)
	{
		destroyAll(player, HERO_CIRCLET);
		for (int weaponId : HERO_WEAPONS)
		{
			destroyAll(player, weaponId);
		}
	}
	
	private void destroyAll(Player player, int itemId)
	{
		long count = getQuestItemsCount(player, itemId);
		while (count > 0)
		{
			player.destroyItemByItemId(ItemProcessType.DESTROY, itemId, 1, player, false);
			count--;
		}
	}
	
	private Player getPlayerByObjectId(int objectId)
	{
		for (Player player : World.getInstance().getPlayers())
		{
			if (player.getObjectId() == objectId)
			{
				return player;
			}
		}
		return null;
	}
	
	public static void main(String[] args)
	{
		new MegaraxHeroService();
	}
}
