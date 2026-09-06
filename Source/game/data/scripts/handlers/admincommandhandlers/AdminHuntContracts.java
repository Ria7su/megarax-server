package handlers.admincommandhandlers;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.handler.IAdminCommandHandler;
import org.l2jmobius.gameserver.model.actor.Player;

import custom.MegaraxHuntContracts.MegaraxHuntContracts;

/** Administrative maintenance commands for Daily Hunt Contracts. */
public class AdminHuntContracts implements IAdminCommandHandler
{
	private static final String[] COMMANDS = { "admin_huntcontracts_info", "admin_huntcontracts_regenerate", "admin_huntcontracts_reset", "admin_huntcontracts_complete", "admin_huntcontracts_progress", "admin_huntcontracts_marks", "admin_huntcontracts_reload", "admin_huntcontracts_enable", "admin_huntcontracts_disable" };

	@Override
	public String[] getCommandList() { return COMMANDS; }

	@Override
	public boolean onCommand(String command, Player admin)
	{
		try
		{
			final String[] args = command.trim().split("\\s+");
			switch (args[0])
			{
				case "admin_huntcontracts_reload": admin.sendMessage(MegaraxHuntContracts.reloadConfig() ? "Hunt Contracts XML reloaded." : "Reload failed; check gameserver log."); break;
				case "admin_huntcontracts_enable": MegaraxHuntContracts.setEnabled(true); admin.sendMessage("Hunt Contracts enabled until restart/reload."); break;
				case "admin_huntcontracts_disable": MegaraxHuntContracts.setEnabled(false); admin.sendMessage("Hunt Contracts disabled until restart/reload."); break;
				case "admin_huntcontracts_info": require(args, 2); show(admin, args[1]); break;
				case "admin_huntcontracts_reset": require(args, 2); deleteAll(admin, args[1]); break;
				case "admin_huntcontracts_regenerate": require(args, 2); deleteToday(admin, args[1]); break;
				case "admin_huntcontracts_complete": require(args, 3); updateProgress(admin, args[1], Integer.parseInt(args[2]), Integer.MAX_VALUE, true); break;
				case "admin_huntcontracts_progress": require(args, 4); updateProgress(admin, args[1], Integer.parseInt(args[2]), Integer.parseInt(args[3]), false); break;
				case "admin_huntcontracts_marks": require(args, 3); admin.sendMessage(MegaraxHuntContracts.changeHuntMarks(admin, args[1], Long.parseLong(args[2]), "ADMIN_HUNT_CONTRACTS") ? "Hunt Marks updated." : "Hunt Marks update rejected."); break;
				default: usage(admin); break;
			}
		}
		catch (Exception e) { admin.sendMessage("Hunt Contracts admin error: " + e.getMessage()); usage(admin); }
		return true;
	}

	private void show(Player admin, String account) throws Exception
	{
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT reset_date,offer_slot,contract_template_id,contract_state,progress,target_count,premium_slot FROM account_hunt_contracts WHERE account_name=? ORDER BY reset_date DESC,offer_slot LIMIT 12"))
		{
			ps.setString(1, account); try (ResultSet rs = ps.executeQuery()) { admin.sendMessage("Hunt Contracts for " + account + ":"); while (rs.next()) admin.sendMessage(rs.getDate(1) + " slot=" + rs.getInt(2) + " " + rs.getString(3) + " " + rs.getString(4) + " " + rs.getInt(5) + "/" + rs.getInt(6) + (rs.getBoolean(7) ? " PREMIUM" : "")); }
		}
	}

	private void deleteAll(Player admin, String account) throws Exception { executeDelete(account, "DELETE FROM account_hunt_contracts WHERE account_name=?", false); admin.sendMessage("All Hunt Contract days reset for " + account + "."); }
	private void deleteToday(Player admin, String account) throws Exception { executeDelete(account, "DELETE FROM account_hunt_contracts WHERE account_name=? AND reset_date=?", true); admin.sendMessage("Today's offers removed for " + account + "; three new offers generate on next login/board open."); }
	private void executeDelete(String account, String sql, boolean date) throws Exception { try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) { ps.setString(1, account); if (date) ps.setDate(2, Date.valueOf(MegaraxHuntContracts.rewardDate())); ps.executeUpdate(); } }

	private void updateProgress(Player admin, String account, int slot, int amount, boolean complete) throws Exception
	{
		if ((slot < 1) || (slot > 3)) throw new IllegalArgumentException("Slot must be 1-3.");
		final String sql = complete ? "UPDATE account_hunt_contracts SET progress=target_count,contract_state='COMPLETED',completed_at=CURRENT_TIMESTAMP WHERE account_name=? AND reset_date=? AND offer_slot=? AND contract_state='ACTIVE'" : "UPDATE account_hunt_contracts SET progress=LEAST(target_count,GREATEST(0,progress+?)),contract_state=IF(LEAST(target_count,GREATEST(0,progress+?))>=target_count,'COMPLETED','ACTIVE'),completed_at=IF(LEAST(target_count,GREATEST(0,progress+?))>=target_count,CURRENT_TIMESTAMP,NULL) WHERE account_name=? AND reset_date=? AND offer_slot=? AND contract_state IN ('ACTIVE','COMPLETED')";
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(sql))
		{
			int p = 1; if (!complete) { ps.setInt(p++, amount); ps.setInt(p++, amount); ps.setInt(p++, amount); } ps.setString(p++, account); ps.setDate(p++, Date.valueOf(MegaraxHuntContracts.rewardDate())); ps.setInt(p, slot); admin.sendMessage("Updated rows: " + ps.executeUpdate());
		}
	}

	private void require(String[] args, int count) { if (args.length < count) throw new IllegalArgumentException("Missing arguments."); }
	private void usage(Player admin) { admin.sendMessage("//huntcontracts_info account | regenerate account | reset account | complete account slot | progress account slot amount | marks account amount | reload | enable | disable"); }
}
