package custom.MegaraxKingdomEconomy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.managers.CastleManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.siege.Castle;

public class Kingdom
{
	public final String id;
	public final int castleId;
	public final String name;
	public final int prosperity;
	public final int neutralTaxPercent;
	public final int allyTaxPercent;
	public final int enemyTaxPercent;
	public final int rulerAdenaBonusPercent;
	public final int allyAdenaBonusPercent;
	private final List<KingdomTerritory> _territories = new ArrayList<>();
	
	public Kingdom(String id, int castleId, String name, int prosperity, int neutralTaxPercent, int allyTaxPercent, int enemyTaxPercent, int rulerAdenaBonusPercent, int allyAdenaBonusPercent)
	{
		this.id = id;
		this.castleId = castleId;
		this.name = name;
		this.prosperity = prosperity;
		this.neutralTaxPercent = neutralTaxPercent;
		this.allyTaxPercent = allyTaxPercent;
		this.enemyTaxPercent = enemyTaxPercent;
		this.rulerAdenaBonusPercent = rulerAdenaBonusPercent;
		this.allyAdenaBonusPercent = allyAdenaBonusPercent;
	}
	
	public void addTerritory(KingdomTerritory territory)
	{
		_territories.add(territory);
	}
	
	public List<KingdomTerritory> getTerritories()
	{
		return Collections.unmodifiableList(_territories);
	}
	
	public Castle getCastle()
	{
		return CastleManager.getInstance().getCastleById(castleId);
	}
	
	public int getOwnerClanId()
	{
		final Castle castle = getCastle();
		return castle == null ? 0 : castle.getOwnerId();
	}
	
	public Clan getOwnerClan()
	{
		final int ownerId = getOwnerClanId();
		return ownerId <= 0 ? null : ClanTable.getInstance().getClan(ownerId);
	}
	
	public String getOwnerName()
	{
		final Clan clan = getOwnerClan();
		return clan == null ? "No ruler" : clan.getName();
	}
	
	public KingdomRelation getRelation(Player player)
	{
		if (player == null)
		{
			return KingdomRelation.NEUTRAL;
		}
		
		final int ownerClanId = getOwnerClanId();
		if ((ownerClanId <= 0) || (player.getClanId() <= 0))
		{
			return KingdomRelation.NEUTRAL;
		}
		if (player.getClanId() == ownerClanId)
		{
			return KingdomRelation.RULER;
		}
		
		final Clan owner = getOwnerClan();
		final Clan playerClan = player.getClan();
		if ((owner != null) && (playerClan != null))
		{
			if ((owner.getAllyId() > 0) && (owner.getAllyId() == playerClan.getAllyId()))
			{
				return KingdomRelation.ALLY;
			}
			if (owner.isAtWarWith(playerClan) || playerClan.isAtWarWith(owner))
			{
				return KingdomRelation.ENEMY;
			}
		}
		return KingdomRelation.NEUTRAL;
	}
	
	public int getTaxPercent(KingdomRelation relation)
	{
		switch (relation)
		{
			case RULER:
			{
				return 0;
			}
			case ALLY:
			{
				return allyTaxPercent;
			}
			case ENEMY:
			{
				return enemyTaxPercent;
			}
			default:
			{
				return neutralTaxPercent;
			}
		}
	}
	
	public int getAdenaBonusPercent(KingdomRelation relation)
	{
		switch (relation)
		{
			case RULER:
			{
				return rulerAdenaBonusPercent;
			}
			case ALLY:
			{
				return allyAdenaBonusPercent;
			}
			default:
			{
				return 0;
			}
		}
	}
}
