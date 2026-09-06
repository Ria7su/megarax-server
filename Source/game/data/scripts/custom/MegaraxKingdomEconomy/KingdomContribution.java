package custom.MegaraxKingdomEconomy;

public class KingdomContribution
{
	public final int charId;
	public final int castleId;
	public int clanId;
	public long points;
	public long lifetimePoints;
	public int dailyRax;
	public int weeklyRax;
	public int dayKey;
	public int weekKey;
	public volatile boolean dirty;
	
	public KingdomContribution(int charId, int castleId)
	{
		this.charId = charId;
		this.castleId = castleId;
	}
	
	public synchronized void addPoints(int amount, int clanId, int dayKey, int weekKey)
	{
		if (amount <= 0)
		{
			return;
		}
		this.clanId = Math.max(0, clanId);
		roll(dayKey, weekKey);
		points += amount;
		lifetimePoints += amount;
		dirty = true;
	}
	
	public synchronized void addRaxReward(int amount, int dayKey, int weekKey)
	{
		if (amount <= 0)
		{
			return;
		}
		roll(dayKey, weekKey);
		dailyRax += amount;
		weeklyRax += amount;
		dirty = true;
	}
	
	public synchronized void spendPoints(long amount)
	{
		if (amount <= 0)
		{
			return;
		}
		points = Math.max(0, points - amount);
		dirty = true;
	}
	
	public synchronized void roll(int currentDayKey, int currentWeekKey)
	{
		if (dayKey != currentDayKey)
		{
			dayKey = currentDayKey;
			dailyRax = 0;
			dirty = true;
		}
		if (weekKey != currentWeekKey)
		{
			weekKey = currentWeekKey;
			weeklyRax = 0;
			dirty = true;
		}
	}
}
