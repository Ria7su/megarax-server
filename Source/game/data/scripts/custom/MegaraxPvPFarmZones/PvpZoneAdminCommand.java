package custom.MegaraxPvPFarmZones;

import java.util.StringTokenizer;
import org.l2jmobius.gameserver.handler.IAdminCommandHandler;
import org.l2jmobius.gameserver.model.actor.Player;

public class PvpZoneAdminCommand implements IAdminCommandHandler
{
	private static final String[] COMMANDS = { "admin_pvpzone_status", "admin_pvpzone_next", "admin_pvpzone_start", "admin_pvpzone_stop", "admin_pvpzone_warning", "admin_pvpzone_reload" };
	private final MegaraxPvPFarmZones _manager;
	PvpZoneAdminCommand(MegaraxPvPFarmZones manager) { _manager = manager; }
	@Override public boolean onCommand(String command, Player player)
	{
		try
		{
			final StringTokenizer st = new StringTokenizer(command);
			final String cmd = st.nextToken();
			if (cmd.equals("admin_pvpzone_next")) _manager.moveToNextZone();
			else if (cmd.equals("admin_pvpzone_start")) { if (!st.hasMoreTokens()) throw new IllegalArgumentException("Usage: //pvpzone_start ZONE_KEY"); _manager.adminStart(st.nextToken()); }
			else if (cmd.equals("admin_pvpzone_stop")) _manager.adminStop();
			else if (cmd.equals("admin_pvpzone_warning")) _manager.adminWarning();
			else if (cmd.equals("admin_pvpzone_reload")) _manager.adminReload();
			player.sendMessage(_manager.status());
		}
		catch (Exception e) { player.sendMessage("PvP zone command error: " + e.getMessage()); }
		return true;
	}
	@Override public String[] getCommandList() { return COMMANDS; }
}
