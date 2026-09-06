package custom.MegaraxPvPFarmTeleport;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.taskmanagers.DynamicPvPZoneManager;
import org.l2jmobius.gameserver.model.zone.ZoneType;

import ai.AbstractNpcAI;

/** Dedicated NPC 9901 teleport to the active Megarax PvP farm zone. */
public class MegaraxPvPFarmTeleport extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxPvPFarmTeleport.class.getName());
	private static final int TELEPORTER = 9901;
	private static final Map<String, Location> DESTINATIONS = new HashMap<>();

	static
	{
		DESTINATIONS.put("Forest of the Dead", new Location(52107, -54328, -3158));
		DESTINATIONS.put("Imperial Tomb", new Location(181380, -78685, -2755));
		DESTINATIONS.put("Ketra Orc", new Location(146175, -68856, -3757));
		DESTINATIONS.put("Pagan Temple", new Location(-16368, -38912, -10720));
	}

	private MegaraxPvPFarmTeleport()
	{
		addStartNpc(TELEPORTER);
		addTalkId(TELEPORTER);
	}

	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		if (!"enter".equals(event) || (player == null))
		{
			return null;
		}
		if (player.isDead() || player.inObserverMode() || player.isInOlympiadMode() || player.isOnEvent() || (player.getInstanceId() > 0) || player.isInCombat() || player.isInStoreMode() || player.isCastingNow() || player.isTeleporting())
		{
			player.sendMessage("PvP farm teleport is unavailable in your current state.");
			return null;
		}

		final String activeZone = DynamicPvPZoneManager.getInstance().getActiveZoneName();
		final Location destination = DESTINATIONS.get(activeZone);
		if (destination == null)
		{
			LOGGER.warning("Rejected PvP farm teleport for " + player.getName() + ": unsupported active zone " + activeZone + ".");
			player.sendMessage("The PvP farm zone is changing. Please try again in a moment.");
			return null;
		}

		final Location safeDestination = findSafeDestination(destination, activeZone);
		LOGGER.info("PvP farm teleport: player=" + player.getName() + " zone=" + activeZone + " x=" + safeDestination.getX() + " y=" + safeDestination.getY() + " z=" + safeDestination.getZ());
		player.teleToLocation(safeDestination);
		player.setSpawnProtection(true);
		player.sendMessage("Active PvP farm zone: " + activeZone + ".");
		return null;
	}

	private Location findSafeDestination(Location center, String activeZone)
	{
		final String zoneName = "Forest of the Dead".equals(activeZone) ? "megarax_forest_dead_pvp" : "Imperial Tomb".equals(activeZone) ? "megarax_imperial_tomb_pvp" : "Ketra Orc".equals(activeZone) ? "megarax_ketra_orc_pvp" : "megarax_pagan_temple_pvp";
		final ZoneType zone = ZoneManager.getInstance().getZoneByName(zoneName);
		for (int attempt = 0; attempt < 12; attempt++)
		{
			final double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
			final int distance = ThreadLocalRandom.current().nextInt(50, 151);
			final int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
			final int y = center.getY() + (int) Math.round(Math.sin(angle) * distance);
			final int z = GeoEngine.getInstance().getSpawnHeight(x, y, center.getZ());
			if (((zone == null) || zone.isInsideZone(x, y, z)) && GeoEngine.getInstance().canMoveToTarget(center.getX(), center.getY(), center.getZ(), x, y, z, 0))
			{
				return new Location(x, y, z);
			}
		}
		return new Location(center.getX(), center.getY(), GeoEngine.getInstance().getSpawnHeight(center));
	}

	public static void main(String[] args)
	{
		new MegaraxPvPFarmTeleport();
	}
}
