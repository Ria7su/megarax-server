/*
 * Custom Megarax Announcer
 */
package custom.MegaraxAnnouncer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.enums.ChatType;

import ai.AbstractNpcAI;

/**
 * Walking local announcers for major towns.
 */
public class MegaraxAnnouncer extends AbstractNpcAI
{
	private static final int ANNOUNCER = 31356;
	private static final int LEGACY_ANNOUNCER = 1004200;
	private static final int MOVE_DELAY = 7000;
	private static final int MOVE_DELAY_RANDOM = 5000;
	private static final int ANNOUNCE_DELAY = 30 * 1000;
	private static final int ANNOUNCE_DELAY_RANDOM = 15 * 1000;
	private static final int SAY_RANGE = 2200;
	private static final String EVENT_MOVE = "MOVE";
	private static final String EVENT_ANNOUNCE = "ANNOUNCE";
	
	private static final Map<Integer, PatrolRoute> ROUTES_BY_NPC = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> NEXT_POINT_BY_NPC = new ConcurrentHashMap<>();
	
	private static final PatrolRoute[] ROUTES =
	{
		new PatrolRoute(82696, 148396, -3495, // Giran
			new Location(82696, 148396, -3495),
			new Location(82080, 148980, -3495),
			new Location(82980, 149680, -3495),
			new Location(83980, 149240, -3431),
			new Location(84240, 148260, -3431),
			new Location(83580, 147560, -3431),
			new Location(82780, 147820, -3495)),
		new PatrolRoute(-81920, 243892, -3714, // Talking Island
			new Location(-81920, 243892, -3714),
			new Location(-82160, 244030, -3714),
			new Location(-81910, 244220, -3714),
			new Location(-81630, 244040, -3714),
			new Location(-81740, 243720, -3714)),
		new PatrolRoute(147908, -55221, -2728, // Goddard
			new Location(147908, -55221, -2728),
			new Location(147650, -55040, -2728),
			new Location(147880, -54840, -2728),
			new Location(148150, -55020, -2728),
			new Location(148080, -55480, -2728)),
		new PatrolRoute(-13920, 121977, -2984, // Gludio
			new Location(-13920, 121977, -2984),
			new Location(-14180, 122120, -2984),
			new Location(-13920, 122330, -2984),
			new Location(-13640, 122130, -2984),
			new Location(-13730, 121760, -2984)),
		new PatrolRoute(15693, 142886, -2696, // Dion
			new Location(15693, 142886, -2696),
			new Location(15440, 143070, -2696),
			new Location(15710, 143260, -2696),
			new Location(15980, 143020, -2696),
			new Location(15850, 142660, -2696)),
		new PatrolRoute(-80785, 149735, -3040, // Gludin
			new Location(-80785, 149735, -3040),
			new Location(-81050, 149900, -3040),
			new Location(-80740, 150120, -3040),
			new Location(-80490, 149860, -3040),
			new Location(-80580, 149520, -3040)),
		new PatrolRoute(111417, 219422, -3544, // Heine
			new Location(111417, 219422, -3544),
			new Location(111160, 219600, -3544),
			new Location(111420, 219820, -3544),
			new Location(111720, 219610, -3544),
			new Location(111640, 219250, -3544)),
		new PatrolRoute(116036, 76319, -2703, // Hunter Village
			new Location(116036, 76319, -2703),
			new Location(115780, 76480, -2703),
			new Location(116030, 76670, -2703),
			new Location(116290, 76480, -2703),
			new Location(116250, 76150, -2703)),
		new PatrolRoute(147456, 27488, -2204, // Aden
			new Location(147456, 27488, -2204),
			new Location(147210, 27670, -2204),
			new Location(147520, 27870, -2204),
			new Location(147760, 27620, -2204),
			new Location(147690, 27270, -2204))
	};
	
	private static final String[] MESSAGES =
	{
		"Noticias Megarax: revisa HOME en Alt+B para ver calendario, eventos y sistemas de temporada.",
		"Noticias Megarax: 6:00 PM Sandramanaia, 7:00 PM PvP Zone War y 8:00 PM Monster Card Rush.",
		"Noticias Megarax: a las 9:00 PM School of Dark Arts puede iniciar su ritual oscuro.",
		"Noticias Megarax: a las 10:00 PM Hidden Box Hunt invita a explorar Aden sin depender del teleport.",
		"Noticias Megarax: las zonas farm pueden entrar en Overcharged. Mas peligro, mejores recompensas.",
		"Noticias Megarax: Monster Cards invocan raids especiales en Giran Harbor. Prepara tu party.",
		"Noticias Megarax: el Monster Card Album crecera por seasons con titulos, skins y recompensas.",
		"Noticias Megarax: los clanes tendran misiones semanales para demostrar dominio real.",
		"Noticias Megarax: los dungeons avanzan hacia Reward Tracks para que cada clear cuente.",
		"Noticias Megarax: PvP War y ranking visible daran prestigio a los combatientes constantes.",
		"Noticias Megarax: Hidden Events no siempre aparecen en menus. Explora, escucha pistas y llega primero.",
		"Noticias Megarax: School of Dark Arts esta cambiando. Algo antiguo respira bajo sus piedras.",
		"Noticias Megarax: Sandramanaia vigila las sombras. Cuando despierte, solo los atentos responderan.",
		"Noticias Megarax: los raids especiales recompensaran participacion real, no presencia vacia.",
		"Noticias Megarax: Season 1 prepara el ascenso de Megarax con cartas, eventos y objetivos vivos.",
		"Noticias Megarax: no basta con hablar de poder. Farm, PvP, raids y clanes escriben la historia."
	};
	
	private MegaraxAnnouncer()
	{
		addSpawnId(ANNOUNCER);
		addSpawnId(LEGACY_ANNOUNCER);
	}
	
	@Override
	public void onSpawn(Npc npc)
	{
		if (isAnnouncer(npc))
		{
			npc.setRandomWalking(false);
			npc.setRunning();
			final PatrolRoute route = getNearestRoute(npc);
			ROUTES_BY_NPC.put(npc.getObjectId(), route);
			NEXT_POINT_BY_NPC.put(npc.getObjectId(), getRandom(route.points.length));
			startQuestTimer(EVENT_MOVE, 1000 + getRandom(2000), npc, null, false);
			startQuestTimer(EVENT_ANNOUNCE, getNextDelay(), npc, null, false);
		}
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		if ((npc != null) && npc.isSpawned() && isAnnouncer(npc))
		{
			if (event.equals(EVENT_MOVE))
			{
				moveAnnouncer(npc);
				startQuestTimer(EVENT_MOVE, MOVE_DELAY + getRandom(MOVE_DELAY_RANDOM), npc, null, false);
				return null;
			}
			if (event.equals(EVENT_ANNOUNCE))
			{
				npc.broadcastSay(ChatType.NPC_GENERAL, getRandomEntry(MESSAGES), SAY_RANGE);
				startQuestTimer(EVENT_ANNOUNCE, getNextDelay(), npc, null, false);
				return null;
			}
		}
		
		return super.onEvent(event, npc, player);
	}
	
	private void moveAnnouncer(Npc npc)
	{
		if ((npc.getWorldRegion() == null) || !npc.getWorldRegion().isActive())
		{
			return;
		}
		
		final int objectId = npc.getObjectId();
		final PatrolRoute route = ROUTES_BY_NPC.computeIfAbsent(objectId, id -> getNearestRoute(npc));
		final int point = NEXT_POINT_BY_NPC.getOrDefault(objectId, 0);
		final Location destination = route.points[point];
		NEXT_POINT_BY_NPC.put(objectId, (point + 1) % route.points.length);
		
		final Location moveLocation = GeoEngine.getInstance().getValidLocation(npc.getX(), npc.getY(), npc.getZ(), destination.getX(), destination.getY(), destination.getZ(), 0);
		if (npc.calculateDistance3D(moveLocation) > 30)
		{
			npc.setRunning();
			npc.getAI().setIntention(Intention.MOVE_TO, moveLocation);
		}
	}
	
	private PatrolRoute getNearestRoute(Npc npc)
	{
		PatrolRoute closest = ROUTES[0];
		long closestDistance = getDistanceSquared(npc, closest);
		for (int i = 1; i < ROUTES.length; i++)
		{
			final PatrolRoute route = ROUTES[i];
			final long distance = getDistanceSquared(npc, route);
			if (distance < closestDistance)
			{
				closest = route;
				closestDistance = distance;
			}
		}
		return closest;
	}
	
	private boolean isAnnouncer(Npc npc)
	{
		return (npc != null) && ((npc.getId() == ANNOUNCER) || (npc.getId() == LEGACY_ANNOUNCER));
	}
	
	private long getDistanceSquared(Npc npc, PatrolRoute route)
	{
		final long dx = npc.getX() - route.x;
		final long dy = npc.getY() - route.y;
		return (dx * dx) + (dy * dy);
	}
	
	private int getNextDelay()
	{
		return ANNOUNCE_DELAY + getRandom(ANNOUNCE_DELAY_RANDOM);
	}
	
	private static class PatrolRoute
	{
		private final int x;
		private final int y;
		private final Location[] points;
		
		private PatrolRoute(int x, int y, int z, Location... points)
		{
			this.x = x;
			this.y = y;
			this.points = points;
		}
	}
	
	public static void main(String[] args)
	{
		new MegaraxAnnouncer();
	}
}
