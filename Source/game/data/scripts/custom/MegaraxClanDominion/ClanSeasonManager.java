package custom.MegaraxClanDominion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;

public final class ClanSeasonManager
{
	private static final Logger LOGGER = Logger.getLogger(ClanSeasonManager.class.getName());
	private static final ClanSeasonManager INSTANCE = new ClanSeasonManager();
	
	private volatile int _currentSeasonId;
	private volatile long _currentSeasonEnd;
	private volatile String _currentSeasonKey = "inactive";
	
	private ClanSeasonManager()
	{
	}
	
	public static ClanSeasonManager getInstance()
	{
		return INSTANCE;
	}
	
	public void init()
	{
		ensureCurrentSeason();
	}
	
	public synchronized void ensureCurrentSeason()
	{
		if (!ClanDominionConfig.SEASONS_ENABLED)
		{
			_currentSeasonId = 0;
			_currentSeasonEnd = 0;
			_currentSeasonKey = "inactive";
			return;
		}
		
		final long now = System.currentTimeMillis();
		try (Connection con = DatabaseFactory.getConnection())
		{
			try (PreparedStatement ps = con.prepareStatement("SELECT id, season_key, end_at FROM clan_seasons WHERE status='ACTIVE' ORDER BY id DESC LIMIT 1");
				ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					final int id = rs.getInt("id");
					final long end = rs.getLong("end_at");
					if (end > now)
					{
						_currentSeasonId = id;
						_currentSeasonKey = rs.getString("season_key");
						_currentSeasonEnd = end;
						return;
					}
					closeSeason(con, id);
				}
			}
			startSeason(con, now);
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not ensure current season: " + e.getMessage());
		}
	}
	
	public synchronized void resetSeasonNow()
	{
		try (Connection con = DatabaseFactory.getConnection())
		{
			if (_currentSeasonId > 0)
			{
				closeSeason(con, _currentSeasonId);
			}
			startSeason(con, System.currentTimeMillis());
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not reset season: " + e.getMessage());
		}
	}
	
	public int getCurrentSeasonId()
	{
		ensureCurrentSeason();
		return _currentSeasonId;
	}
	
	public String getCurrentSeasonKey()
	{
		ensureCurrentSeason();
		return _currentSeasonKey;
	}
	
	public long getCurrentSeasonEnd()
	{
		ensureCurrentSeason();
		return _currentSeasonEnd;
	}
	
	private void startSeason(Connection con, long now) throws SQLException
	{
		final long duration = Math.max(1, ClanDominionConfig.SEASON_DURATION_DAYS) * 86400000L;
		final String key = "S-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date(now));
		try (PreparedStatement ps = con.prepareStatement("INSERT INTO clan_seasons (season_key, start_at, end_at, status) VALUES (?, ?, ?, 'ACTIVE')", Statement.RETURN_GENERATED_KEYS))
		{
			ps.setString(1, key);
			ps.setLong(2, now);
			ps.setLong(3, now + duration);
			ps.executeUpdate();
			try (ResultSet rs = ps.getGeneratedKeys())
			{
				if (rs.next())
				{
					_currentSeasonId = rs.getInt(1);
				}
			}
		}
		_currentSeasonKey = key;
		_currentSeasonEnd = now + duration;
		ClanDominionData.logActivity(0, "SEASON_START", "System", key);
	}
	
	private void closeSeason(Connection con, int seasonId) throws SQLException
	{
		snapshotCategory(con, seasonId, "DOMINION", "season_points");
		snapshotCategory(con, seasonId, "PVP", "pvp_kills");
		snapshotCategory(con, seasonId, "RAID", "raid_score");
		snapshotCategory(con, seasonId, "TERRITORY", "territory_score");
		try (PreparedStatement ps = con.prepareStatement("UPDATE clan_seasons SET status='ENDED' WHERE id=?"))
		{
			ps.setInt(1, seasonId);
			ps.executeUpdate();
		}
		ClanDominionData.logActivity(0, "SEASON_END", "System", "Season " + seasonId + " closed.");
		ClanDominionData.resetWeeklyStats();
	}
	
	private void snapshotCategory(Connection con, int seasonId, String category, String column) throws SQLException
	{
		if (!isSafeColumn(column))
		{
			return;
		}
		try (PreparedStatement select = con.prepareStatement("SELECT clan_id, " + column + " AS score FROM clan_dominion WHERE " + column + " > 0 ORDER BY " + column + " DESC LIMIT ?"))
		{
			select.setInt(1, ClanDominionConfig.SEASON_REWARD_TOP_LIMIT);
			try (ResultSet rs = select.executeQuery())
			{
				int rank = 1;
				while (rs.next())
				{
					try (PreparedStatement insert = con.prepareStatement("REPLACE INTO clan_season_results (season_id, clan_id, category, rank_position, score, reward_claimed) VALUES (?, ?, ?, ?, ?, 0)"))
					{
						insert.setInt(1, seasonId);
						insert.setInt(2, rs.getInt("clan_id"));
						insert.setString(3, category);
						insert.setInt(4, rank++);
						insert.setLong(5, rs.getLong("score"));
						insert.executeUpdate();
					}
				}
			}
		}
	}
	
	private boolean isSafeColumn(String column)
	{
		return "season_points".equals(column) || "pvp_kills".equals(column) || "raid_score".equals(column) || "territory_score".equals(column);
	}
}
