/*
 * Custom Megarax Monthly Events base.
 */
package custom.MegaraxMonthlyEvents;

import java.time.LocalDate;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.util.Broadcast;

import ai.AbstractNpcAI;

/**
 * Lightweight monthly event scaffold.
 * Keep MONTHLY_EVENTS_ENABLED false until a specific month has final mobs, drops and NPC content.
 */
public class MegaraxMonthlyEvents extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxMonthlyEvents.class.getName());
	
	private static final boolean MONTHLY_EVENTS_ENABLED = false;
	private static final long ANNOUNCEMENT_INTERVAL = 2 * 60 * 60 * 1000L;
	
	// Reserved future content. These IDs are placeholders until the event NPC/mobs are created.
	private static final int EVENT_NPC = 1004300;
	private static final int[] NO_MOBS = {};
	private static final int[] NO_DROPS = {};
	
	private static final MonthlyEvent[] EVENTS =
	{
		new MonthlyEvent(1, "New Year", "Megarax Monthly Event: New Year season is active.", EVENT_NPC, NO_MOBS, NO_DROPS, true),
		new MonthlyEvent(2, "Valentine", "Megarax Monthly Event: Valentine season is active.", EVENT_NPC, NO_MOBS, NO_DROPS, true),
		new MonthlyEvent(4, "Spring Hunt", "Megarax Monthly Event: Spring Hunt season is active.", EVENT_NPC, NO_MOBS, NO_DROPS, true),
		new MonthlyEvent(10, "Halloween", "Megarax Monthly Event: Halloween season is active.", EVENT_NPC, NO_MOBS, NO_DROPS, true),
		new MonthlyEvent(11, "Harvest", "Megarax Monthly Event: Harvest season is active.", EVENT_NPC, NO_MOBS, NO_DROPS, true),
		new MonthlyEvent(12, "Christmas", "Megarax Monthly Event: Christmas season is active.", EVENT_NPC, NO_MOBS, NO_DROPS, true)
	};
	
	private MegaraxMonthlyEvents()
	{
		if (!MONTHLY_EVENTS_ENABLED)
		{
			LOGGER.info("MegaraxMonthlyEvents: disabled. No timers or monthly content started.");
			return;
		}
		
		startQuestTimer("MONTHLY_ANNOUNCE", 60000, null, null, false);
		LOGGER.info("MegaraxMonthlyEvents: enabled.");
	}
	
	@Override
	public String onEvent(String event, org.l2jmobius.gameserver.model.actor.Npc npc, org.l2jmobius.gameserver.model.actor.Player player)
	{
		if (event.equals("MONTHLY_ANNOUNCE"))
		{
			announceCurrentEvent();
			startQuestTimer("MONTHLY_ANNOUNCE", ANNOUNCEMENT_INTERVAL, null, null, false);
			return null;
		}
		
		return super.onEvent(event, npc, player);
	}
	
	private void announceCurrentEvent()
	{
		final MonthlyEvent event = getCurrentEvent();
		if ((event == null) || !event.enabled)
		{
			return;
		}
		
		Broadcast.toAllOnlinePlayers(event.announcement + " Event NPC: " + event.npcId + ".");
	}
	
	private MonthlyEvent getCurrentEvent()
	{
		final int month = LocalDate.now().getMonthValue();
		for (MonthlyEvent event : EVENTS)
		{
			if (event.month == month)
			{
				return event;
			}
		}
		
		return null;
	}
	
	private static class MonthlyEvent
	{
		private final int month;
		private final String name;
		private final String announcement;
		private final int npcId;
		private final int[] mobIds;
		private final int[] dropItemIds;
		private final boolean enabled;
		
		private MonthlyEvent(int month, String name, String announcement, int npcId, int[] mobIds, int[] dropItemIds, boolean enabled)
		{
			this.month = month;
			this.name = name;
			this.announcement = announcement;
			this.npcId = npcId;
			this.mobIds = mobIds;
			this.dropItemIds = dropItemIds;
			this.enabled = enabled;
		}
	}
	
	public static void main(String[] args)
	{
		new MegaraxMonthlyEvents();
	}
}
