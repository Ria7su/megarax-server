package custom.MegaraxKingdomEconomy;

import org.l2jmobius.gameserver.model.WorldObject;

public class KingdomTerritory
{
	public final String id;
	public final String name;
	public final int castleId;
	public final int minX;
	public final int maxX;
	public final int minY;
	public final int maxY;
	public final int minZ;
	public final int maxZ;
	
	public KingdomTerritory(String id, String name, int castleId, int minX, int maxX, int minY, int maxY, int minZ, int maxZ)
	{
		this.id = id;
		this.name = name;
		this.castleId = castleId;
		this.minX = Math.min(minX, maxX);
		this.maxX = Math.max(minX, maxX);
		this.minY = Math.min(minY, maxY);
		this.maxY = Math.max(minY, maxY);
		this.minZ = Math.min(minZ, maxZ);
		this.maxZ = Math.max(minZ, maxZ);
	}
	
	public boolean contains(WorldObject object)
	{
		return (object != null) && contains(object.getX(), object.getY(), object.getZ());
	}
	
	public boolean contains(int x, int y, int z)
	{
		return (x >= minX) && (x <= maxX) && (y >= minY) && (y <= maxY) && (z >= minZ) && (z <= maxZ);
	}
}
