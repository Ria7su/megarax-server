/*
 * Custom Megarax Farm Titles - L2JMobius Interlude
 */
package custom.MegaraxFarmTitles;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Attackable;
import org.l2jmobius.gameserver.model.actor.Npc;

import ai.AbstractNpcAI;

public class MegaraxFarmTitles extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxFarmTitles.class.getName());
	private static final String FARM_TITLE = "[Megarax Farm]";
	
	private static final int[] FARM_MOBS =
	{
		18001, 18343,
		20134, 20137, 20146,
		20235, 20236, 20237, 20238, 20239, 20240, 20241, 20242, 20243, 20244, 20245, 20246, 20265,
		20676, 20677, 20758, 20773,
		21108, 21109, 21110, 21111, 21112, 21113, 21114, 21115, 21116,
		21207, 21294, 21295, 21296, 21297, 21298, 21299,
		21300, 21301, 21302, 21303, 21304, 21305, 21306, 21307, 21308, 21309, 21310, 21311, 21312,
		21396, 21397, 21398, 21399,
		21400, 21401, 21402, 21403, 21404, 21405, 21406, 21407, 21408, 21409,
		21410, 21411, 21412, 21413, 21414, 21415, 21416, 21417, 21418,
		21420, 21421, 21422, 21423, 21424, 21425, 21426, 21427, 21428, 21429, 21430, 21431, 21432, 21434,
		21798, 21799, 21800, 21823,
		27099, 27114, 27184
	};
	
	private static final FarmArea[] FARM_AREAS =
	{
		new FarmArea("Abandoned Camp", -60000, -40000, 135000, 155000, -4500, -1500),
		new FarmArea("Cruma Tower 1", 14000, 21000, 108000, 116500, -12500, -8500),
		new FarmArea("Dragon Valley", 65000, 126000, 98000, 132000, -5200, -1800),
		new FarmArea("Blazing Swamp", 138000, 166000, -33000, -4000, -4500, -500),
		new FarmArea("Wall of Argos", 154000, 190000, -68000, -33000, -5000, -800),
		new FarmArea("Imperial Tomb", 174000, 191000, -87000, -69000, -4200, -1800),
		new FarmArea("Giant Cave", 168000, 197000, 38000, 67000, -6500, -2000),
		new FarmArea("Monastery", 103000, 126000, -98000, -72000, -4500, -1500),
		new FarmArea("Mithril Mines", 166000, 192000, -186000, -164000, 1800, 5200),
		new FarmArea("Execution Grounds Custom", 42000, 51500, 142000, 155000, -4300, -2300)
	};
	
	private MegaraxFarmTitles()
	{
		addSpawnId(FARM_MOBS);
		applyToAlreadySpawned();
		LOGGER.info("MegaraxFarmTitles: Farm mob titles enabled.");
	}
	
	@Override
	public void onSpawn(Npc npc)
	{
		applyFarmTitle(npc);
	}
	
	private void applyToAlreadySpawned()
	{
		int count = 0;
		for (WorldObject object : World.getInstance().getVisibleObjects())
		{
			if (object instanceof Npc)
			{
				if (applyFarmTitle((Npc) object))
				{
					count++;
				}
			}
		}
		LOGGER.info("MegaraxFarmTitles: Updated " + count + " already spawned farm mobs.");
	}
	
	private boolean applyFarmTitle(Npc npc)
	{
		if (!(npc instanceof Attackable))
		{
			return false;
		}
		
		final Attackable attackable = (Attackable) npc;
		if (attackable.isRaid() || attackable.isRaidMinion())
		{
			return false;
		}
		
		if (!isFarmMob(npc.getId()) || !isInsideFarmArea(npc))
		{
			return false;
		}
		
		if (!FARM_TITLE.equals(npc.getTitle()))
		{
			npc.setTitle(FARM_TITLE);
			npc.broadcastInfo();
			return true;
		}
		return false;
	}
	
	private boolean isFarmMob(int npcId)
	{
		for (int id : FARM_MOBS)
		{
			if (id == npcId)
			{
				return true;
			}
		}
		return false;
	}
	
	private boolean isInsideFarmArea(Npc npc)
	{
		for (FarmArea area : FARM_AREAS)
		{
			if (area.contains(npc))
			{
				return true;
			}
		}
		return false;
	}
	
	private static class FarmArea
	{
		private final String name;
		private final int minX;
		private final int maxX;
		private final int minY;
		private final int maxY;
		private final int minZ;
		private final int maxZ;
		
		private FarmArea(String name, int minX, int maxX, int minY, int maxY, int minZ, int maxZ)
		{
			this.name = name;
			this.minX = minX;
			this.maxX = maxX;
			this.minY = minY;
			this.maxY = maxY;
			this.minZ = minZ;
			this.maxZ = maxZ;
		}
		
		private boolean contains(Npc npc)
		{
			return (npc.getX() >= minX) && (npc.getX() <= maxX) &&
				(npc.getY() >= minY) && (npc.getY() <= maxY) &&
				(npc.getZ() >= minZ) && (npc.getZ() <= maxZ);
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
	
	public static void main(String[] args)
	{
		new MegaraxFarmTitles();
	}
}
