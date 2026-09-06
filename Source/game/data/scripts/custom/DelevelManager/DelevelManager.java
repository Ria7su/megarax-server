/*
 * Copyright (c) 2013 L2jMobius
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package custom.DelevelManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.xml.ExperienceData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

import ai.AbstractNpcAI;

/**
 * @author Mobius
 */
public class DelevelManager extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(DelevelManager.class.getName());
	
	private static final int NPC = 1002000;
	private static final int RAX_COIN = 9142;
	private static final int REBIRTH_COST = 10000;
	private static final int REQUIRED_LEVEL = 80;
	private static final int TARGET_LEVEL = 20;
	private static final int REQUIRED_DUNGEONS = 100;
	private static final int REQUIRED_PVP = 1000;
	private static final int REBIRTH_SKILL = 90000;
	private static final int MAX_REBIRTHS = 10;
	
	private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS megarax_rebirth_progress (char_id INT NOT NULL PRIMARY KEY, dungeon_runs INT NOT NULL DEFAULT 0, rebirths INT NOT NULL DEFAULT 0, was_clan_leader TINYINT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL)";
	private static final String SELECT_SQL = "SELECT dungeon_runs, rebirths, was_clan_leader FROM megarax_rebirth_progress WHERE char_id=?";
	private static final String INSERT_SQL = "INSERT INTO megarax_rebirth_progress (char_id, updated_at) VALUES (?, ?)";
	private static final String UPDATE_DUNGEON_SQL = "UPDATE megarax_rebirth_progress SET dungeon_runs=dungeon_runs+1, updated_at=? WHERE char_id=?";
	private static final String UPDATE_REBIRTH_SQL = "UPDATE megarax_rebirth_progress SET rebirths=?, was_clan_leader=?, updated_at=? WHERE char_id=?";
	private static final String UPDATE_CLAN_LEADER_SQL = "UPDATE megarax_rebirth_progress SET was_clan_leader=1, updated_at=? WHERE char_id=?";
	
	private DelevelManager()
	{
		initTable();
		addStartNpc(NPC);
		addTalkId(NPC);
		addFirstTalkId(NPC);
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		switch (event)
		{
			case "rebirth":
			{
				processRebirth(player, npc);
				showMain(player, npc);
				break;
			}
		}
		
		return null;
	}
	
	@Override
	public String onFirstTalk(Npc npc, Player player)
	{
		showMain(player, npc);
		return null;
	}
	
	public static void notifyDungeonComplete(Player player)
	{
		if (player == null)
		{
			return;
		}
		
		initTable();
		ensureProgress(player.getObjectId());
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(UPDATE_DUNGEON_SQL))
		{
			ps.setLong(1, System.currentTimeMillis());
			ps.setInt(2, player.getObjectId());
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("DelevelManager: Could not update dungeon progress: " + e.getMessage());
		}
	}
	
	private void processRebirth(Player player, Npc npc)
	{
		final Progress progress = getProgress(player);
		if (!canRebirth(player, progress, true))
		{
			return;
		}
		
		if (!player.destroyItemByItemId(ItemProcessType.FEE, RAX_COIN, REBIRTH_COST, npc == null ? player : npc, true))
		{
			return;
		}
		
		final int newRebirths = Math.min(MAX_REBIRTHS, progress.rebirths + 1);
		player.getStat().removeExpAndSp(player.getExp() - ExperienceData.getInstance().getExpForLevel(TARGET_LEVEL), 0);
		applyRebirthSkill(player, newRebirths);
		saveRebirth(player, newRebirths, progress.wasClanLeader || player.isClanLeader());
		player.setCurrentHpMp(player.getMaxHp(), player.getMaxMp());
		player.broadcastUserInfo();
		player.sendMessage("Megarax Rebirth complete. You returned to level " + TARGET_LEVEL + " and gained +500 HP.");
	}
	
	private boolean canRebirth(Player player, Progress progress, boolean notify)
	{
		if (player.getLevel() < REQUIRED_LEVEL)
		{
			if (notify) player.sendMessage("You must be level " + REQUIRED_LEVEL + " to rebirth.");
			return false;
		}
		if (progress.rebirths >= MAX_REBIRTHS)
		{
			if (notify) player.sendMessage("You have reached the maximum rebirth level.");
			return false;
		}
		if (progress.dungeonRuns < REQUIRED_DUNGEONS)
		{
			if (notify) player.sendMessage("You need " + REQUIRED_DUNGEONS + " completed dungeons.");
			return false;
		}
		if (player.getPvpKills() < REQUIRED_PVP)
		{
			if (notify) player.sendMessage("You need " + REQUIRED_PVP + " PvP wins.");
			return false;
		}
		if (!progress.wasClanLeader && !player.isClanLeader())
		{
			if (notify) player.sendMessage("You must be or have been a clan leader.");
			return false;
		}
		if (getQuestItemsCount(player, RAX_COIN) < REBIRTH_COST)
		{
			if (notify) player.sendMessage("You need " + REBIRTH_COST + " Rax Coin.");
			return false;
		}
		return true;
	}
	
	private void applyRebirthSkill(Player player, int rebirths)
	{
		final Skill skill = SkillData.getInstance().getSkill(REBIRTH_SKILL, rebirths);
		if (skill != null)
		{
			player.addSkill(skill, true);
			player.sendSkillList();
		}
	}
	
	private void showMain(Player player, Npc npc)
	{
		if (player.isClanLeader())
		{
			markClanLeader(player);
		}
		
		final Progress progress = getProgress(player);
		final long raxCoins = getQuestItemsCount(player, RAX_COIN);
		final boolean ready = canRebirth(player, progress, false);
		final String clanStatus = (progress.wasClanLeader || player.isClanLeader()) ? "OK" : "NO";
		
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Megarax Rebirth</title><body><center>");
		html.append("<button value=\"Megarax Rebirth\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\"><tr>");
		html.append("<td width=36 align=center><img src=\"icon.skill4416_human\" width=32 height=32></td>");
		html.append("<td width=240><font color=\"0099FF\">Jeadin Rebirth</font><br1>");
		html.append("<font color=\"ae9977\">Return to level 20 and gain permanent HP.</font></td>");
		html.append("</tr></table><br>");
		
		html.append("<button value=\"Requirements\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\">");
		appendStatus(html, "Level", player.getLevel() + "/" + REQUIRED_LEVEL);
		appendStatus(html, "Dungeons", progress.dungeonRuns + "/" + REQUIRED_DUNGEONS);
		appendStatus(html, "PvP Wins", player.getPvpKills() + "/" + REQUIRED_PVP);
		appendStatus(html, "Clan Leader", clanStatus);
		appendStatus(html, "Rax Coin", raxCoins + "/" + REBIRTH_COST);
		appendStatus(html, "Rebirths", progress.rebirths + "/" + MAX_REBIRTHS);
		html.append("</table><br>");
		
		html.append("<button value=\"Reward\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\">");
		html.append("<tr><td align=center><font color=\"LEVEL\">+500 Max HP per rebirth</font></td></tr>");
		html.append("<tr><td align=center><font color=\"ae9977\">Class and skills are preserved.</font></td></tr>");
		html.append("</table><br>");
		html.append("<button value=\"").append(ready ? "Rebirth" : "Locked").append("\" action=\"bypass -h Quest DelevelManager rebirth\" width=120 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		html.append("</center></body></html>");
		
		final NpcHtmlMessage msg = new NpcHtmlMessage(npc == null ? 0 : npc.getObjectId());
		msg.setHtml(html.toString());
		player.sendPacket(msg);
	}
	
	private void appendStatus(StringBuilder html, String title, String value)
	{
		html.append("<tr><td width=150><font color=\"ae9977\">").append(title).append("</font></td>");
		html.append("<td width=130 align=right><font color=\"LEVEL\">").append(value).append("</font></td></tr>");
	}
	
	private static void initTable()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(CREATE_TABLE))
		{
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("DelevelManager: Could not create rebirth table: " + e.getMessage());
		}
	}
	
	private static void ensureProgress(int charId)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement insert = con.prepareStatement(INSERT_SQL))
		{
			insert.setInt(1, charId);
			insert.setLong(2, System.currentTimeMillis());
			insert.executeUpdate();
		}
		catch (SQLException e)
		{
			// Duplicate primary key means the row already exists.
		}
	}
	
	private Progress getProgress(Player player)
	{
		ensureProgress(player.getObjectId());
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(SELECT_SQL))
		{
			ps.setInt(1, player.getObjectId());
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					return new Progress(rs.getInt("dungeon_runs"), rs.getInt("rebirths"), rs.getBoolean("was_clan_leader"));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("DelevelManager: Could not load rebirth progress: " + e.getMessage());
		}
		return new Progress(0, 0, false);
	}
	
	private void saveRebirth(Player player, int rebirths, boolean wasClanLeader)
	{
		ensureProgress(player.getObjectId());
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(UPDATE_REBIRTH_SQL))
		{
			ps.setInt(1, rebirths);
			ps.setBoolean(2, wasClanLeader);
			ps.setLong(3, System.currentTimeMillis());
			ps.setInt(4, player.getObjectId());
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("DelevelManager: Could not save rebirth: " + e.getMessage());
		}
	}
	
	private void markClanLeader(Player player)
	{
		ensureProgress(player.getObjectId());
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(UPDATE_CLAN_LEADER_SQL))
		{
			ps.setLong(1, System.currentTimeMillis());
			ps.setInt(2, player.getObjectId());
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("DelevelManager: Could not mark clan leader: " + e.getMessage());
		}
	}
	
	private static class Progress
	{
		private final int dungeonRuns;
		private final int rebirths;
		private final boolean wasClanLeader;
		
		private Progress(int dungeonRuns, int rebirths, boolean wasClanLeader)
		{
			this.dungeonRuns = dungeonRuns;
			this.rebirths = rebirths;
			this.wasClanLeader = wasClanLeader;
		}
	}
	
	public static void main(String[] args)
	{
		new DelevelManager();
	}
}
