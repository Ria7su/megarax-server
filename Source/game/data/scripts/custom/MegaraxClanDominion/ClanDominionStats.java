package custom.MegaraxClanDominion;

public final class ClanDominionStats
{
	public final int clanId;
	public final int tier;
	public final long totalPoints;
	public final long seasonPoints;
	public final int weeklyActivity;
	public final int pvpKills;
	public final int pvpDeaths;
	public final int raidScore;
	public final int territoryScore;
	public final int siegeScore;
	public final int missionScore;
	public final long lastActivity;
	
	public ClanDominionStats(int clanId, int tier, long totalPoints, long seasonPoints, int weeklyActivity, int pvpKills, int pvpDeaths, int raidScore, int territoryScore, int siegeScore, int missionScore, long lastActivity)
	{
		this.clanId = clanId;
		this.tier = tier;
		this.totalPoints = totalPoints;
		this.seasonPoints = seasonPoints;
		this.weeklyActivity = weeklyActivity;
		this.pvpKills = pvpKills;
		this.pvpDeaths = pvpDeaths;
		this.raidScore = raidScore;
		this.territoryScore = territoryScore;
		this.siegeScore = siegeScore;
		this.missionScore = missionScore;
		this.lastActivity = lastActivity;
	}
}
