/*
 * Custom Megarax PvP Streak Announcer - L2JMobius Interlude
 */
package custom.MegaraxPvPStreak;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.Containers;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.ListenerRegisterType;
import org.l2jmobius.gameserver.model.events.annotations.RegisterEvent;
import org.l2jmobius.gameserver.model.events.annotations.RegisterType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDeath;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogout;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerPvPKill;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.util.Broadcast;

import ai.AbstractNpcAI;

public class MegaraxPvPStreak extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxPvPStreak.class.getName());
	private static final int ANNOUNCE_START = 20;
	private static final int ANNOUNCE_STEP = 5;
	
	private final Map<Integer, Integer> _streaks = new ConcurrentHashMap<>();
	
	private MegaraxPvPStreak()
	{
		Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_PVP_KILL, (OnPlayerPvPKill event) -> onPlayerPvPKill(event), this));
		Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_LOGOUT, (OnPlayerLogout event) -> onPlayerLogout(event), this));
		LOGGER.info("MegaraxPvPStreak: PvP streak announcer loaded.");
	}
	
	private void onPlayerPvPKill(OnPlayerPvPKill event)
	{
		final Player killer = event.getPlayer();
		final Player target = event.getTarget();
		if ((killer == null) || (target == null) || killer.isGM() || target.isGM() || (killer.getObjectId() == target.getObjectId()))
		{
			return;
		}
		if (isSameRealPlayer(killer, target) || isSameClan(killer, target))
		{
			return;
		}
		
		_streaks.remove(target.getObjectId());
		final int streak = _streaks.merge(killer.getObjectId(), 1, Integer::sum);
		if ((streak >= ANNOUNCE_START) && ((streak == ANNOUNCE_START) || ((streak % ANNOUNCE_STEP) == 0)))
		{
			final String message = "Megarax PvP: " + killer.getName() + " is unstoppable with " + streak + " kills without dying!";
			Broadcast.toAllOnlinePlayers(message);
			Broadcast.toAllOnlinePlayersOnScreen(message);
		}
	}
	
	@RegisterEvent(EventType.ON_CREATURE_DEATH)
	@RegisterType(ListenerRegisterType.GLOBAL)
	public void onCreatureDeath(OnCreatureDeath event)
	{
		if ((event == null) || (event.getTarget() == null) || !event.getTarget().isPlayer())
		{
			return;
		}
		
		_streaks.remove(event.getTarget().asPlayer().getObjectId());
	}
	
	private void onPlayerLogout(OnPlayerLogout event)
	{
		if ((event != null) && (event.getPlayer() != null))
		{
			_streaks.remove(event.getPlayer().getObjectId());
		}
	}
	
	private boolean isSameClan(Player killer, Player target)
	{
		return (killer.getClanId() > 0) && (killer.getClanId() == target.getClanId());
	}
	
	private boolean isSameRealPlayer(Player killer, Player target)
	{
		final String killerIp = killer.getIPAddress();
		final String targetIp = target.getIPAddress();
		if ((killerIp != null) && !killerIp.isEmpty() && killerIp.equals(targetIp))
		{
			return true;
		}
		
		final String killerHwid = getHardwareId(killer);
		final String targetHwid = getHardwareId(target);
		return (killerHwid != null) && !killerHwid.isEmpty() && killerHwid.equals(targetHwid);
	}
	
	private String getHardwareId(Player player)
	{
		final GameClient client = player.getClient();
		if ((client == null) || (client.getHardwareInfo() == null))
		{
			return null;
		}
		return client.getHardwareInfo().getMacAddress();
	}
	
	public static void main(String[] args)
	{
		new MegaraxPvPStreak();
	}
}
