package custom.MegaraxStability;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.handler.IAdminCommandHandler;
import org.l2jmobius.gameserver.model.actor.Player;

public class StabilityAdminCommand implements IAdminCommandHandler
{
	private static final String[] COMMANDS =
	{
		"admin_serverhealth", "admin_economyreport", "admin_clanreport", "admin_customsystems", "admin_scheduledtasks"
	};
	private final MegaraxStability _monitor;

	StabilityAdminCommand(MegaraxStability monitor)
	{
		_monitor = monitor;
	}

	@Override
	public boolean onCommand(String command, Player admin)
	{
		try
		{
			final String operation = command.split("\\s+")[0];
			if (operation.equals("admin_serverhealth"))
			{
				_monitor.collectNow();
				admin.sendMessage(_monitor.healthSummary());
			}
			else if (operation.equals("admin_economyreport"))
			{
				economyReport(admin);
			}
			else if (operation.equals("admin_clanreport"))
			{
				clanReport(admin);
			}
			else if (operation.equals("admin_customsystems"))
			{
				customSystems(admin);
			}
			else
			{
				admin.sendMessage("Scheduled diagnostics: stability=1/" + configuredInterval() + "m; known custom periodic systems=10; JVM task queue size is not exposed by this core.");
			}
		}
		catch (Exception e)
		{
			admin.sendMessage("Diagnostic command failed: " + e.getMessage());
		}
		return true;
	}

	private void economyReport(Player admin) throws Exception
	{
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT (SELECT COALESCE(SUM(count),0) FROM items WHERE item_id=57) adena,(SELECT COALESCE(SUM(count),0) FROM items WHERE item_id=6673) rax,(SELECT COALESCE(SUM(balance),0) FROM kingdom_treasury) kingdom_balance,(SELECT COALESCE(SUM(adena_tax),0) FROM kingdom_contribution_history) total_tax,(SELECT COALESCE(SUM(rax_reward),0) FROM kingdom_contribution_history) rax_generated"); ResultSet rs = ps.executeQuery())
		{
			if (rs.next())
			{
				admin.sendMessage("Economy: stored Adena=" + rs.getLong(1) + ", stored Rax Coin=" + rs.getLong(2) + ", kingdom treasury=" + rs.getLong(3) + ", recorded tax=" + rs.getLong(4) + ", recorded Rax rewards=" + rs.getLong(5) + ".");
			}
		}
	}

	private void clanReport(Player admin) throws Exception
	{
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT c.clan_name,c.clan_level,c.reputation_score,COALESCE(d.tier,0),COALESCE(d.total_points,0),COALESCE(d.season_points,0),COALESCE(d.weekly_activity,0) FROM clan_data c LEFT JOIN clan_dominion d ON d.clan_id=c.clan_id ORDER BY COALESCE(d.total_points,0) DESC,c.clan_name LIMIT 10"); ResultSet rs = ps.executeQuery())
		{
			int count = 0;
			while (rs.next())
			{
				count++;
				admin.sendMessage("Clan " + rs.getString(1) + ": level=" + rs.getInt(2) + ", reputation=" + rs.getInt(3) + ", tier=" + rs.getInt(4) + ", points=" + rs.getLong(5) + ", season=" + rs.getLong(6) + ", weekly=" + rs.getInt(7) + ".");
			}
			if (count == 0)
			{
				admin.sendMessage("Clan report: no clans found.");
			}
		}
	}

	private void customSystems(Player admin)
	{
		admin.sendMessage("Custom systems: Stability=" + property("MegaraxStability.ini", "StabilityMonitorEnabled") + ", PvP Farm=" + property("MegaraxPvPFarmZones.ini", "PvPZoneRotationEnabled") + ", Clan Dominion=" + property("ClanDominion.ini", "EnableClanDominionSystem") + ", Marketplace=" + property("Marketplace.ini", "MarketplaceEnabled") + ", AutoPlay=" + property("AutoPlay.ini", "EnableAutoPlay") + ", Captcha=" + property("Captcha.ini", "EnableCaptcha") + ".");
	}

	private int configuredInterval()
	{
		try
		{
			return Integer.parseInt(property("MegaraxStability.ini", "StabilityMonitorIntervalMinutes"));
		}
		catch (Exception e)
		{
			return 5;
		}
	}

	private String property(String fileName, String key)
	{
		final Properties properties = new Properties();
		try (FileInputStream input = new FileInputStream(new File("config/Custom", fileName)))
		{
			properties.load(input);
			return properties.getProperty(key, "missing");
		}
		catch (Exception e)
		{
			return "unreadable";
		}
	}

	@Override
	public String[] getCommandList()
	{
		return COMMANDS;
	}
}
