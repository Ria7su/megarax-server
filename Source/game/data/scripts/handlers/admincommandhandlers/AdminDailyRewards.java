package handlers.admincommandhandlers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.handler.IAdminCommandHandler;
import org.l2jmobius.gameserver.model.actor.Player;

import handlers.communityboard.DailyRewardsBoard;

/** Administrative maintenance commands for account daily rewards. */
public class AdminDailyRewards implements IAdminCommandHandler
{
	private static final String[] COMMANDS =
	{
		"admin_dailyrewards_reload", "admin_dailyrewards_info", "admin_dailyrewards_reset",
		"admin_dailyrewards_setday", "admin_dailyrewards_undo", "admin_dailyrewards_enable", "admin_dailyrewards_disable"
	};

	@Override
	public boolean onCommand(String command, Player admin)
	{
		try
		{
			final String[] args = command.trim().split("\\s+");
			if (args[0].equals("admin_dailyrewards_reload"))
			{
				admin.sendMessage(DailyRewardsBoard.reload() ? "Daily Rewards configuration reloaded." : "Daily Rewards reload failed. Check the log.");
			}
			else if (args[0].equals("admin_dailyrewards_enable"))
			{
				DailyRewardsBoard.setEnabled(true); admin.sendMessage("Daily Rewards enabled until restart or reload.");
			}
			else if (args[0].equals("admin_dailyrewards_disable"))
			{
				DailyRewardsBoard.setEnabled(false); admin.sendMessage("Daily Rewards disabled until restart or reload.");
			}
			else if ((args.length >= 2) && args[0].equals("admin_dailyrewards_info"))
			{
				showInfo(admin, args[1]);
			}
			else if ((args.length >= 2) && args[0].equals("admin_dailyrewards_reset"))
			{
				execute(admin, "DELETE FROM account_daily_rewards WHERE account_name=?", args[1], "Daily Rewards progress reset for ");
			}
			else if ((args.length >= 4) && args[0].equals("admin_dailyrewards_setday"))
			{
				final boolean free = parseTrack(args[2]);
				final int day = Integer.parseInt(args[3]);
				if ((day < 1) || (day > 30)) throw new IllegalArgumentException("Day must be 1-30.");
				final String column = free ? "free_cycle_day" : "premium_cycle_day";
				try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement("UPDATE account_daily_rewards SET " + column + "=?,updated_at=CURRENT_TIMESTAMP WHERE account_name=?"))
				{
					ps.setInt(1, day); ps.setString(2, args[1]); ps.executeUpdate();
				}
				admin.sendMessage("Daily Rewards " + args[2] + " day set to " + day + " for " + args[1] + ".");
			}
			else if ((args.length >= 3) && args[0].equals("admin_dailyrewards_undo"))
			{
				final boolean free = parseTrack(args[2]);
				final String dayColumn = free ? "free_cycle_day" : "premium_cycle_day";
				final String dateColumn = free ? "last_free_claim_date" : "last_premium_claim_date";
				final String totalColumn = free ? "total_free_claims" : "total_premium_claims";
				final String sql = "UPDATE account_daily_rewards SET " + dayColumn + "=IF(" + dayColumn + "=1,30," + dayColumn + "-1)," + dateColumn + "=NULL," + totalColumn + "=GREATEST(0," + totalColumn + "-1),updated_at=CURRENT_TIMESTAMP WHERE account_name=?";
				execute(admin, sql, args[1], "Latest " + args[2] + " claim undone for ");
			}
			else
			{
				admin.sendMessage("Usage: //dailyrewards_info account | reset account | setday account free|premium 1-30 | undo account free|premium | reload | enable | disable");
			}
		}
		catch (Exception e)
		{
			admin.sendMessage("Daily Rewards admin error: " + e.getMessage());
		}
		return true;
	}

	private static boolean parseTrack(String value)
	{
		if (value.equalsIgnoreCase("free")) return true;
		if (value.equalsIgnoreCase("premium")) return false;
		throw new IllegalArgumentException("Track must be free or premium.");
	}

	private static void showInfo(Player admin, String account) throws Exception
	{
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT free_cycle_day,premium_cycle_day,last_free_claim_date,last_premium_claim_date,current_online_minutes,total_free_claims,total_premium_claims,reward_season FROM account_daily_rewards WHERE account_name=?"))
		{
			ps.setString(1, account);
			try (ResultSet rs = ps.executeQuery())
			{
				if (!rs.next()) { admin.sendMessage("No Daily Rewards progress for " + account + "."); return; }
				admin.sendMessage("Daily Rewards " + account + ": freeDay=" + rs.getInt(1) + " premiumDay=" + rs.getInt(2) + " lastFree=" + rs.getDate(3) + " lastPremium=" + rs.getDate(4) + " minutes=" + rs.getInt(5) + " totals=" + rs.getInt(6) + "/" + rs.getInt(7) + " season=" + rs.getString(8));
			}
		}
	}

	private static void execute(Player admin, String sql, String account, String message) throws Exception
	{
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setString(1, account); ps.executeUpdate();
		}
		admin.sendMessage(message + account + ".");
	}

	@Override
	public String[] getCommandList()
	{
		return COMMANDS;
	}
}
