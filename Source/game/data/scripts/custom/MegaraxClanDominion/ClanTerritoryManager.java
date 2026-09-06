package custom.MegaraxClanDominion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.util.Broadcast;

public final class ClanTerritoryManager
{
	private static final Logger LOGGER = Logger.getLogger(ClanTerritoryManager.class.getName());
	private static final ClanTerritoryManager INSTANCE = new ClanTerritoryManager();
	private final Map<String, Integer> _knownOwners = new ConcurrentHashMap<>();
	
	private ClanTerritoryManager()
	{
	}
	
	public static ClanTerritoryManager getInstance()
	{
		return INSTANCE;
	}
	
	public void recordCapture(String territoryId, String territoryName, int clanId, String actorName)
	{
		if ((territoryId == null) || territoryId.isEmpty() || (clanId <= 0))
		{
			return;
		}
		final long now = System.currentTimeMillis();
		final long expires = now + (Math.max(1, ClanDominionConfig.TERRITORY_OWNERSHIP_HOURS) * 3600000L);
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("REPLACE INTO clan_territory_ownership (territory_id, territory_name, owner_clan_id, captured_at, expires_at, attack_score) VALUES (?, ?, ?, ?, ?, COALESCE((SELECT t.attack_score FROM (SELECT attack_score FROM clan_territory_ownership WHERE territory_id=?) t), 0) + 1)"))
		{
			ps.setString(1, territoryId);
			ps.setString(2, territoryName == null ? territoryId : territoryName);
			ps.setInt(3, clanId);
			ps.setLong(4, now);
			ps.setLong(5, expires);
			ps.setString(6, territoryId);
			ps.executeUpdate();
			ClanDominionEngine.addDominionPoints(clanId, 0, ClanDominionConfig.POINTS_TERRITORY_CAPTURE, "TERRITORY_CAPTURE", actorName, "", territoryName);
			ClanDominionData.addScoreColumn(clanId, "territory_score", ClanDominionConfig.POINTS_TERRITORY_CAPTURE);
			ClanMissionManager.getInstance().addProgress(clanId, ClanMissionManager.TYPE_TERRITORY_CAPTURE, 1, actorName);
			ClanAchievementManager.getInstance().addProgress(clanId, ClanAchievementManager.TERRITORY_KING, 1, actorName);
			if (ClanDominionConfig.ANNOUNCEMENTS_ENABLED && ClanDominionConfig.ANNOUNCE_TERRITORY_CAPTURES)
			{
				final Clan clan = ClanTable.getInstance().getClan(clanId);
				final String clanName = clan == null ? ("Clan " + clanId) : clan.getName();
				Broadcast.toAllOnlinePlayers("Clan Dominion: " + clanName + " captured " + territoryName + ".");
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not record territory capture: " + e.getMessage());
		}
	}
	
	public void syncLegacyTerritoryConquest()
	{
		if (!ClanDominionConfig.TERRITORY_CONQUEST_ENABLED)
		{
			return;
		}
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT territory_name, owner_clan_id FROM territory_conquest WHERE owner_clan_id > 0");
			ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
			{
				final String name = rs.getString("territory_name");
				final int owner = rs.getInt("owner_clan_id");
				final Integer previous = _knownOwners.put(name, owner);
				if ((previous == null) || (previous.intValue() != owner))
				{
					recordCapture(name.toLowerCase().replace(' ', '_'), name, owner, "Territory Conquest");
				}
			}
		}
		catch (SQLException ignored)
		{
			// Older databases may not have the legacy table yet.
		}
	}
	
	public List<TerritoryView> getTerritories()
	{
		final List<TerritoryView> result = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT territory_id, territory_name, owner_clan_id, captured_at, expires_at, defense_score, attack_score FROM clan_territory_ownership ORDER BY captured_at DESC LIMIT 30");
			ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
			{
				result.add(new TerritoryView(rs.getString("territory_id"), rs.getString("territory_name"), rs.getInt("owner_clan_id"), rs.getLong("captured_at"), rs.getLong("expires_at"), rs.getInt("defense_score"), rs.getInt("attack_score")));
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("ClanDominion: Could not load territories: " + e.getMessage());
		}
		return result;
	}
	
	public static final class TerritoryView
	{
		public final String id;
		public final String name;
		public final int ownerClanId;
		public final long capturedAt;
		public final long expiresAt;
		public final int defenseScore;
		public final int attackScore;
		
		private TerritoryView(String id, String name, int ownerClanId, long capturedAt, long expiresAt, int defenseScore, int attackScore)
		{
			this.id = id;
			this.name = name;
			this.ownerClanId = ownerClanId;
			this.capturedAt = capturedAt;
			this.expiresAt = expiresAt;
			this.defenseScore = defenseScore;
			this.attackScore = attackScore;
		}
	}
}
