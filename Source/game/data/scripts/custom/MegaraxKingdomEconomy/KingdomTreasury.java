package custom.MegaraxKingdomEconomy;

public class KingdomTreasury
{
	public final int castleId;
	public int clanId;
	public long balance;
	public long totalIncome;
	public long todayIncome;
	public long weeklyIncome;
	public long monthlyIncome;
	public long totalSpent;
	public int level = 1;
	public int payrollPercent;
	public volatile boolean dirty;
	
	public KingdomTreasury(int castleId)
	{
		this.castleId = castleId;
	}
	
	public synchronized void addIncome(long amount, int ownerClanId)
	{
		if (amount <= 0)
		{
			return;
		}
		clanId = Math.max(0, ownerClanId);
		balance += amount;
		totalIncome += amount;
		todayIncome += amount;
		weeklyIncome += amount;
		monthlyIncome += amount;
		dirty = true;
	}
}
