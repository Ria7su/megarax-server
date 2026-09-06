/*
 * Custom Megarax city reputation.
 */
package custom.MegaraxCityReputation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.ListenerRegisterType;
import org.l2jmobius.gameserver.model.events.annotations.RegisterEvent;
import org.l2jmobius.gameserver.model.events.annotations.RegisterType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDeath;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.network.serverpackets.ExShowScreenMessage;

import ai.AbstractNpcAI;

public class MegaraxCityReputation extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxCityReputation.class.getName());
	
	private static final int RAX_COIN = 9142;
	private static final int CRAFT_COIN = 4356;
	private static final int MILESTONE_50_REWARD = 15;
	private static final int MILESTONE_200_REWARD = 75;
	
	private static final String TABLE_SQL = "CREATE TABLE IF NOT EXISTS megarax_city_reputation (player_id INT NOT NULL, week_key VARCHAR(10) NOT NULL, city VARCHAR(24) NOT NULL, points INT NOT NULL DEFAULT 0, claimed_50 TINYINT NOT NULL DEFAULT 0, claimed_200 TINYINT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL, PRIMARY KEY (player_id, week_key, city))";
	private static final String SELECT_SQL = "SELECT points, claimed_50, claimed_200 FROM megarax_city_reputation WHERE player_id=? AND week_key=? AND city=?";
	private static final String INSERT_SQL = "INSERT INTO megarax_city_reputation (player_id, week_key, city, updated_at) VALUES (?, ?, ?, ?)";
	private static final String UPDATE_SQL = "UPDATE megarax_city_reputation SET points=points+1, updated_at=? WHERE player_id=? AND week_key=? AND city=?";
	private static final String CLAIM_50_SQL = "UPDATE megarax_city_reputation SET claimed_50=1, updated_at=? WHERE player_id=? AND week_key=? AND city=? AND claimed_50=0";
	private static final String CLAIM_200_SQL = "UPDATE megarax_city_reputation SET claimed_200=1, updated_at=? WHERE player_id=? AND week_key=? AND city=? AND claimed_200=0";
	
	private static final CityArea[] AREAS =
	{
		new CityArea("Talking Island", -125000, -70000, 200000, 265000),
		new CityArea("Gludio", -90000, -5000, 80000, 210000),
		new CityArea("Dion", 5000, 80000, 90000, 180000),
		new CityArea("Giran", 75000, 140000, 70000, 170000),
		new CityArea("Oren", 60000, 125000, -35000, 60000),
		new CityArea("Aden", 130000, 195000, 0, 80000),
		new CityArea("Rune", 30000, 115000, -120000, -25000),
		new CityArea("Goddard", 120000, 190000, -125000, -25000),
		new CityArea("Schuttgart", 55000, 125000, -180000, -95000),
	};
	
	private MegaraxCityReputation()
	{
		initTable();
		LOGGER.info("MegaraxCityReputation: Script loaded.");
	}
	
	@RegisterEvent(EventType.ON_CREATURE_DEATH)
	@RegisterType(ListenerRegisterType.GLOBAL)
	public void onCreatureDeath(OnCreatureDeath event)
	{
		if ((event == null) || (event.getAttacker() == null) || (event.getTarget() == null) || !event.getAttacker().isPlayer() || !event.getTarget().isMonster())
		{
			return;
		}
		
		final Player player = event.getAttacker().asPlayer();
		final Npc npc = event.getTarget().asNpc();
		if ((player == null) || player.isGM() || (npc == null) || npc.isRaid() || npc.isRaidMinion())
		{
			return;
		}
		if ((player.getLevel() - npc.getLevel()) > 9)
		{
			return;
		}
		
		final String city = getCity(npc.getX(), npc.getY());
		if (city == null)
		{
			return;
		}
		
		final int points = addPoint(player, city);
		if (points == 50)
		{
			claimMilestone(player, city, 50);
		}
		else if (points == 200)
		{
			claimMilestone(player, city, 200);
		}
	}
	
	private int addPoint(Player player, String city)
	{
		final String week = getWeekKey();
		ensureRow(player.getObjectId(), week, city);
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(UPDATE_SQL))
		{
			ps.setLong(1, System.currentTimeMillis());
			ps.setInt(2, player.getObjectId());
			ps.setString(3, week);
			ps.setString(4, city);
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxCityReputation: Could not update city reputation: " + e.getMessage());
			return 0;
		}
		return getPoints(player.getObjectId(), week, city);
	}
	
	private void claimMilestone(Player player, String city, int milestone)
	{
		final String week = getWeekKey();
		final String sql = milestone == 200 ? CLAIM_200_SQL : CLAIM_50_SQL;
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setLong(1, System.currentTimeMillis());
			ps.setInt(2, player.getObjectId());
			ps.setString(3, week);
			ps.setString(4, city);
			if (ps.executeUpdate() > 0)
			{
				if (milestone == 200)
				{
					player.addItem(ItemProcessType.REWARD, RAX_COIN, MILESTONE_200_REWARD, player, true);
					player.addItem(ItemProcessType.REWARD, CRAFT_COIN, MILESTONE_200_REWARD, player, true);
				}
				else
				{
					player.addItem(ItemProcessType.REWARD, RAX_COIN, MILESTONE_50_REWARD, player, true);
					player.addItem(ItemProcessType.REWARD, CRAFT_COIN, MILESTONE_50_REWARD, player, true);
				}
				player.sendPacket(new ExShowScreenMessage(city + " Reputation: " + milestone + " points reached", 3000));
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxCityReputation: Could not claim milestone: " + e.getMessage());
		}
	}
	
	private int getPoints(int playerId, String week, String city)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(SELECT_SQL))
		{
			ps.setInt(1, playerId);
			ps.setString(2, week);
			ps.setString(3, city);
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					return rs.getInt("points");
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxCityReputation: Could not read points: " + e.getMessage());
		}
		return 0;
	}
	
	private void ensureRow(int playerId, String week, String city)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement select = con.prepareStatement(SELECT_SQL))
		{
			select.setInt(1, playerId);
			select.setString(2, week);
			select.setString(3, city);
			try (ResultSet rs = select.executeQuery())
			{
				if (rs.next())
				{
					return;
				}
			}
			
			try (PreparedStatement insert = con.prepareStatement(INSERT_SQL))
			{
				insert.setInt(1, playerId);
				insert.setString(2, week);
				insert.setString(3, city);
				insert.setLong(4, System.currentTimeMillis());
				insert.executeUpdate();
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxCityReputation: Could not ensure row: " + e.getMessage());
		}
	}
	
	private String getCity(int x, int y)
	{
		for (CityArea area : AREAS)
		{
			if (area.contains(x, y))
			{
				return area.name;
			}
		}
		return null;
	}
	
	private void initTable()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(TABLE_SQL))
		{
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("MegaraxCityReputation: Could not create table: " + e.getMessage());
		}
	}
	
	private String getWeekKey()
	{
		final LocalDate now = LocalDate.now();
		final int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
		return now.getYear() + "-W" + week;
	}
	
	private static class CityArea
	{
		private final String name;
		private final int minX;
		private final int maxX;
		private final int minY;
		private final int maxY;
		
		private CityArea(String name, int minX, int maxX, int minY, int maxY)
		{
			this.name = name;
			this.minX = minX;
			this.maxX = maxX;
			this.minY = minY;
			this.maxY = maxY;
		}
		
		private boolean contains(int x, int y)
		{
			return (x >= minX) && (x <= maxX) && (y >= minY) && (y <= maxY);
		}
	}
	
	public static void main(String[] args)
	{
		new MegaraxCityReputation();
	}
}
