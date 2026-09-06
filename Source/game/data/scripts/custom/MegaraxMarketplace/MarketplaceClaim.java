package custom.MegaraxMarketplace;

import java.sql.ResultSet;
import java.sql.SQLException;

public class MarketplaceClaim
{
	public int claimId;
	public int ownerCharId;
	public String ownerName;
	public MarketplaceClaimType claimType;
	public int listingId;
	public MarketplaceItemSnapshot item;
	public long currencyAmount;
	public int currencyItemId;
	public MarketplaceClaimStatus status;
	public long createdAt;
	public long claimedAt;
	
	public static MarketplaceClaim fromResultSet(ResultSet rs) throws SQLException
	{
		final MarketplaceClaim claim = new MarketplaceClaim();
		claim.claimId = rs.getInt("claim_id");
		claim.ownerCharId = rs.getInt("owner_char_id");
		claim.ownerName = rs.getString("owner_name");
		claim.claimType = MarketplaceClaimType.valueOf(rs.getString("claim_type"));
		claim.listingId = rs.getInt("listing_id");
		claim.item = MarketplaceItemSnapshot.fromResultSet(rs);
		claim.currencyAmount = rs.getLong("currency_amount");
		claim.currencyItemId = rs.getInt("currency_item_id");
		claim.status = MarketplaceClaimStatus.valueOf(rs.getString("status"));
		claim.createdAt = rs.getLong("created_at");
		claim.claimedAt = rs.getLong("claimed_at");
		return claim;
	}
}
