package custom.MegaraxMarketplace;

import java.sql.ResultSet;
import java.sql.SQLException;

public class MarketplaceHistoryEntry
{
	public int historyId;
	public int listingId;
	public int sellerCharId;
	public int buyerCharId;
	public int itemId;
	public String itemName;
	public long quantity;
	public long price;
	public long commission;
	public long sellerRevenue;
	public String actionType;
	public long createdAt;
	
	public static MarketplaceHistoryEntry fromResultSet(ResultSet rs) throws SQLException
	{
		final MarketplaceHistoryEntry entry = new MarketplaceHistoryEntry();
		entry.historyId = rs.getInt("history_id");
		entry.listingId = rs.getInt("listing_id");
		entry.sellerCharId = rs.getInt("seller_char_id");
		entry.buyerCharId = rs.getInt("buyer_char_id");
		entry.itemId = rs.getInt("item_id");
		entry.itemName = rs.getString("item_name");
		entry.quantity = rs.getLong("quantity");
		entry.price = rs.getLong("price");
		entry.commission = rs.getLong("commission");
		entry.sellerRevenue = rs.getLong("seller_revenue");
		entry.actionType = rs.getString("action_type");
		entry.createdAt = rs.getLong("created_at");
		return entry;
	}
}
