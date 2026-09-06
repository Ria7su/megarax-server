package custom.MegaraxMarketplace;

import java.sql.ResultSet;
import java.sql.SQLException;

public class MarketplaceListing
{
	public int listingId;
	public int sellerCharId;
	public String sellerName;
	public MarketplaceItemSnapshot item;
	public long price;
	public int currencyItemId;
	public int commissionPercent;
	public long commissionAmount;
	public long sellerRevenue;
	public MarketplaceStatus status;
	public int buyerCharId;
	public String buyerName;
	public long createdAt;
	public long expiresAt;
	public long soldAt;
	public long cancelledAt;
	public long claimedAt;
	public long lastUpdated;
	
	public static MarketplaceListing fromResultSet(ResultSet rs) throws SQLException
	{
		final MarketplaceListing listing = new MarketplaceListing();
		listing.listingId = rs.getInt("listing_id");
		listing.sellerCharId = rs.getInt("seller_char_id");
		listing.sellerName = rs.getString("seller_name");
		listing.item = MarketplaceItemSnapshot.fromResultSet(rs);
		listing.price = rs.getLong("price");
		listing.currencyItemId = rs.getInt("currency_item_id");
		listing.commissionPercent = rs.getInt("commission_percent");
		listing.commissionAmount = rs.getLong("commission_amount");
		listing.sellerRevenue = rs.getLong("seller_revenue");
		listing.status = MarketplaceStatus.valueOf(rs.getString("status"));
		listing.buyerCharId = rs.getInt("buyer_char_id");
		listing.buyerName = rs.getString("buyer_name");
		listing.createdAt = rs.getLong("created_at");
		listing.expiresAt = rs.getLong("expires_at");
		listing.soldAt = rs.getLong("sold_at");
		listing.cancelledAt = rs.getLong("cancelled_at");
		listing.claimedAt = rs.getLong("claimed_at");
		listing.lastUpdated = rs.getLong("last_updated");
		return listing;
	}
}
