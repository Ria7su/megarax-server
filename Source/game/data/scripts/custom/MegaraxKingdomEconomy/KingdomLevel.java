package custom.MegaraxKingdomEconomy;

public class KingdomLevel
{
	public final int level;
	public final long cost;
	public final String title;
	public final int dropBonusPercent;
	public final int xpBonusPercent;
	public final int adenaBonusPercent;
	
	public KingdomLevel(int level, long cost, String title, int dropBonusPercent, int xpBonusPercent, int adenaBonusPercent)
	{
		this.level = level;
		this.cost = cost;
		this.title = title;
		this.dropBonusPercent = dropBonusPercent;
		this.xpBonusPercent = xpBonusPercent;
		this.adenaBonusPercent = adenaBonusPercent;
	}
}
