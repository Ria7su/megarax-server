package custom.MegaraxKingdomEconomy;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.model.events.Containers;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDeath;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogin;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogout;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerPvPKill;
import org.l2jmobius.gameserver.model.events.holders.actor.player.inventory.OnPlayerItemAdd;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;

import ai.AbstractNpcAI;

public class KingdomEconomy extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(KingdomEconomy.class.getName());
	private final KingdomEconomyManager _manager = KingdomEconomyManager.getInstance();
	
	private KingdomEconomy()
	{
		_manager.init();
		if (KingdomEconomyConfig.ENABLED)
		{
			Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_ITEM_ADD, (OnPlayerItemAdd event) -> _manager.onAdenaAdd(event.getPlayer(), event.getItem()), this));
			Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_LOGIN, (OnPlayerLogin event) -> _manager.onLogin(event.getPlayer()), this));
			Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_LOGOUT, (OnPlayerLogout event) -> _manager.onLogout(event.getPlayer()), this));
			Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_PVP_KILL, (OnPlayerPvPKill event) -> _manager.onPvpKill(event.getPlayer(), event.getTarget()), this));
			Containers.Monsters().addListener(new ConsumerEventListener(Containers.Monsters(), EventType.ON_CREATURE_DEATH, (OnCreatureDeath event) -> _manager.onCreatureDeath(event.getAttacker(), event.getTarget()), this));
		}
		LOGGER.info("KingdomEconomy: Loaded Kingdom Economy System.");
	}
	
	public static void main(String[] args)
	{
		new KingdomEconomy();
	}
}
