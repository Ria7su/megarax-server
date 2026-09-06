package custom.MegaraxClanDominion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.util.Broadcast;

public final class ClanRivalryManager
{
	private static final Logger LOGGER = Logger.getLogger(ClanRivalryManager.class.getName());
	private static final ClanRivalryManager INSTANCE = new ClanRivalryManager();
	
	private ClanRivalryManager()
	{
	}
	
	public static ClanRivalryManager getInstance()
	{
		return INSTANCE;
	}
	
	public boolean declareRivalry(Player player, int rivalClanId)
	{
		if ((player == null) || (player.getClan() == null) || (rivalClanId <= 0))
		{
			return false;
		}
		if (!ClanDominionConfig.RIVALRIES_ENABLED)
		{
			player.sendMessage("Clan Rivalries are disabled.");
			return false;
		}
		if (!player.isClanLeader())
		{
			player.sendMessage("Only the clan leader can declare a Rivalry Contract.");
			return false;
		}
		final int clanId = player.getClanId();
		if (clanId == rivalClanId)
		{
			player.sendMessage("You cannot declare rivalry against your own clan.");
			return false;
		}
		final Clan rival = ClanTable.getInstance().getClan(rivalClanId);
		if (rival == null)
		{
			player.sendMessage("Rival clan not found.");
			return false;
		}
		if ((player.getClan().getAllyId() > 0) && (player.getClan().getAllyId() == rival.getAllyId()))
		{
			player.sendMessage("You cannot declare rivalry against an allied clan.");
			return false;
		}
		if ((ClanDominionData.getStats(clanId).tier < ClanDominionConfig.RIVALRY_MINIMUM_TIER) || (ClanDominionData.getStats(rivalClanId).tier < ClanDominionConfig.RIVALRY_MINIMUM_TIER))
		{
			player.sendMessage("Both clans need a higher Dominion tier for Rivalry Contracts.");
			return false;
		}
		if (getActiveRivalryId(clanId, rivalClanId) > 0)
		{
			player.sendMessage("There is already an active rivalry with that clan.");
			return false;
		}
		
		final long now = System.currentTimeMillis();
		final long end = now + (Math.max(1, ClanDominionConfig.RIVALRY_DURATION_HOURS) * 3600000L);
		final String status = ClanDominionConfig.RIVALRY_AUTO_ACCEPT ? "ACTIVE" : "PENDING";
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("INSERT INTO clan_rivalries (clan_id, rival_clan_id, status, start_at, end_at, created_at) VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS))
		{
			ps.setInt(1, clanId);
			ps.setInt(2, rivalClanId);
			ps.setString(3, status);
			ps.setLong(4, ClanDominionConfig.RIVALRY_AUTO_ACCEPT ? now : 0);
			ps.setLong(5, ClanDominionConfig.RIVALRY_AUTO_ACCEPT ? end : 0);
			ps.setLong(6, now);
			ps.executeUpdate();
			long id = 0;
			try (ResultSet rs = ps.getGeneratedKeys())
			{
				if (rs.next())
				{
					id = rs.getLong(1);
				}
			}
			if (id > 0)
			{
				ensureScore(con, id, clanId);
				ensureScore(con, id, rivalClanId);
			}
			final String message = "Clan Dominion: " + player.getClan().getName() + " declared a Rivalry Contract against " + rival.getName() + ".";
			ClanDominionData.logActivity(clanId, "RIVALRY", player.getName(), message);
			Broadcast.toAllOnlinePlayers(message);
			return true;
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not declare rivalry: " + e.getMessage());
		}
		return false;
	}
	
	public int getActiveRivalryId(int clanId, int rivalClanId)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT id FROM clan_rivalries WHERE status='ACTIVE' AND ((clan_id=? AND rival_clan_id=?) OR (clan_id=? AND rival_clan_id=?)) AND end_at>? ORDER BY id DESC LIMIT 1"))
		{
			ps.setInt(1, clanId);
			ps.setInt(2, rivalClanId);
			ps.setInt(3, rivalClanId);
			ps.setInt(4, clanId);
			ps.setLong(5, System.currentTimeMillis());
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? rs.getInt("id") : 0;
			}
		}
		catch (SQLException e)
		{
			return 0;
		}
	}
	
	public boolean isActiveRivalry(int clanId, int rivalClanId)
	{
		return getActiveRivalryId(clanId, rivalClanId) > 0;
	}
	
	public void recordKill(int killerClanId, int victimClanId)
	{
		final int rivalryId = getActiveRivalryId(killerClanId, victimClanId);
		if (rivalryId <= 0)
		{
			return;
		}
		try (Connection con = DatabaseFactory.getConnection())
		{
			ensureScore(con, rivalryId, killerClanId);
			ensureScore(con, rivalryId, victimClanId);
			try (PreparedStatement killer = con.prepareStatement("UPDATE clan_rivalry_score SET kills=kills+1, updated_at=? WHERE rivalry_id=? AND clan_id=?"))
			{
				killer.setLong(1, System.currentTimeMillis());
				killer.setInt(2, rivalryId);
				killer.setInt(3, killerClanId);
				killer.executeUpdate();
			}
			try (PreparedStatement victim = con.prepareStatement("UPDATE clan_rivalry_score SET deaths=deaths+1, updated_at=? WHERE rivalry_id=? AND clan_id=?"))
			{
				victim.setLong(1, System.currentTimeMillis());
				victim.setInt(2, rivalryId);
				victim.setInt(3, victimClanId);
				victim.executeUpdate();
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not record rivalry kill: " + e.getMessage());
		}
	}
	
	public void finishExpiredRivalries()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT id, clan_id, rival_clan_id FROM clan_rivalries WHERE status='ACTIVE' AND end_at<=?"))
		{
			ps.setLong(1, System.currentTimeMillis());
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					finishRivalry(con, rs.getInt("id"), rs.getInt("clan_id"), rs.getInt("rival_clan_id"));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not finish rivalries: " + e.getMessage());
		}
	}
	
	public List<RivalryView> getRivalries(int clanId)
	{
		final List<RivalryView> result = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT r.id, r.clan_id, r.rival_clan_id, r.status, r.start_at, r.end_at, COALESCE(s1.kills, 0) AS clan_kills, COALESCE(s2.kills, 0) AS rival_kills FROM clan_rivalries r LEFT JOIN clan_rivalry_score s1 ON s1.rivalry_id=r.id AND s1.clan_id=r.clan_id LEFT JOIN clan_rivalry_score s2 ON s2.rivalry_id=r.id AND s2.clan_id=r.rival_clan_id WHERE r.status IN ('ACTIVE','PENDING') AND (r.clan_id=? OR r.rival_clan_id=?) ORDER BY r.id DESC LIMIT 20"))
		{
			ps.setInt(1, clanId);
			ps.setInt(2, clanId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					result.add(new RivalryView(rs.getInt("id"), rs.getInt("clan_id"), rs.getInt("rival_clan_id"), rs.getString("status"), rs.getLong("start_at"), rs.getLong("end_at"), rs.getInt("clan_kills"), rs.getInt("rival_kills")));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not load rivalries: " + e.getMessage());
		}
		return result;
	}
	
	private void finishRivalry(Connection con, int rivalryId, int clanId, int rivalClanId) throws SQLException
	{
		final int clanScore = getRivalryScore(con, rivalryId, clanId);
		final int rivalScore = getRivalryScore(con, rivalryId, rivalClanId);
		final int winner = clanScore == rivalScore ? 0 : (clanScore > rivalScore ? clanId : rivalClanId);
		try (PreparedStatement ps = con.prepareStatement("UPDATE clan_rivalries SET status='ENDED', winner_clan_id=? WHERE id=?"))
		{
			ps.setInt(1, winner);
			ps.setInt(2, rivalryId);
			ps.executeUpdate();
		}
		if (winner > 0)
		{
			ClanDominionEngine.addDominionPoints(winner, winner == clanId ? rivalClanId : clanId, ClanDominionConfig.POINTS_RIVALRY_WIN, "RIVALRY_WIN", "System", "", "Rivalry " + rivalryId);
		}
		if (ClanDominionConfig.ANNOUNCEMENTS_ENABLED && ClanDominionConfig.ANNOUNCE_RIVALRY_RESULTS)
		{
			final Clan winnerClan = ClanTable.getInstance().getClan(winner);
			final String winnerName = winnerClan == null ? "No winner" : winnerClan.getName();
			Broadcast.toAllOnlinePlayers("Clan Dominion: Rivalry Contract #" + rivalryId + " ended. Winner: " + winnerName + ".");
		}
	}
	
	private int getRivalryScore(Connection con, int rivalryId, int clanId) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement("SELECT (kills * 3) + raid_score + (territory_captures * 5) + siege_score - deaths AS score FROM clan_rivalry_score WHERE rivalry_id=? AND clan_id=?"))
		{
			ps.setInt(1, rivalryId);
			ps.setInt(2, clanId);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? rs.getInt("score") : 0;
			}
		}
	}
	
	private void ensureScore(Connection con, long rivalryId, int clanId) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement("INSERT IGNORE INTO clan_rivalry_score (rivalry_id, clan_id, updated_at) VALUES (?, ?, ?)"))
		{
			ps.setLong(1, rivalryId);
			ps.setInt(2, clanId);
			ps.setLong(3, System.currentTimeMillis());
			ps.executeUpdate();
		}
	}
	
	public static final class RivalryView
	{
		public final int id;
		public final int clanId;
		public final int rivalClanId;
		public final String status;
		public final long startAt;
		public final long endAt;
		public final int clanKills;
		public final int rivalKills;
		
		private RivalryView(int id, int clanId, int rivalClanId, String status, long startAt, long endAt, int clanKills, int rivalKills)
		{
			this.id = id;
			this.clanId = clanId;
			this.rivalClanId = rivalClanId;
			this.status = status;
			this.startAt = startAt;
			this.endAt = endAt;
			this.clanKills = clanKills;
			this.rivalKills = rivalKills;
		}
	}
}
