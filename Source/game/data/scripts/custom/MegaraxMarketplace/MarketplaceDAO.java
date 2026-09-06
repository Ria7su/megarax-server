package custom.MegaraxMarketplace;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.actor.Player;

public final class MarketplaceDAO
{
	private static final Logger LOGGER = Logger.getLogger(MarketplaceDAO.class.getName());
	private static final MarketplaceDAO INSTANCE = new MarketplaceDAO();
	
	private static final String CREATE_LISTINGS = "CREATE TABLE IF NOT EXISTS marketplace_listings (" +
		"listing_id INT NOT NULL AUTO_INCREMENT," +
		"seller_char_id INT NOT NULL," +
		"seller_name VARCHAR(35) NOT NULL," +
		"item_object_id INT NOT NULL," +
		"item_id INT NOT NULL," +
		"item_name VARCHAR(120) NOT NULL," +
		"category VARCHAR(32) NOT NULL," +
		"quantity BIGINT NOT NULL," +
		"enchant_level INT NOT NULL DEFAULT 0," +
		"augmentation_id INT NOT NULL DEFAULT -1," +
		"custom_type1 INT NOT NULL DEFAULT 0," +
		"custom_type2 INT NOT NULL DEFAULT 0," +
		"transmog_id INT NOT NULL DEFAULT 0," +
		"mana_left INT NOT NULL DEFAULT -1," +
		"remaining_time_millis BIGINT NOT NULL DEFAULT 0," +
		"price BIGINT NOT NULL," +
		"currency_item_id INT NOT NULL," +
		"commission_percent INT NOT NULL," +
		"commission_amount BIGINT NOT NULL DEFAULT 0," +
		"seller_revenue BIGINT NOT NULL DEFAULT 0," +
		"status VARCHAR(16) NOT NULL," +
		"buyer_char_id INT NOT NULL DEFAULT 0," +
		"buyer_name VARCHAR(35) NOT NULL DEFAULT ''," +
		"created_at BIGINT NOT NULL," +
		"expires_at BIGINT NOT NULL," +
		"sold_at BIGINT NOT NULL DEFAULT 0," +
		"cancelled_at BIGINT NOT NULL DEFAULT 0," +
		"claimed_at BIGINT NOT NULL DEFAULT 0," +
		"last_updated BIGINT NOT NULL," +
		"PRIMARY KEY (listing_id)," +
		"KEY idx_market_status (status)," +
		"KEY idx_market_seller (seller_char_id)," +
		"KEY idx_market_buyer (buyer_char_id)," +
		"KEY idx_market_item (item_id)," +
		"KEY idx_market_price (price)," +
		"KEY idx_market_created (created_at)," +
		"KEY idx_market_expires (expires_at)," +
		"KEY idx_market_category (category)" +
		") DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	
	private static final String CREATE_CLAIMS = "CREATE TABLE IF NOT EXISTS marketplace_claims (" +
		"claim_id INT NOT NULL AUTO_INCREMENT," +
		"owner_char_id INT NOT NULL," +
		"owner_name VARCHAR(35) NOT NULL," +
		"claim_type VARCHAR(20) NOT NULL," +
		"listing_id INT NOT NULL DEFAULT 0," +
		"item_object_id INT NOT NULL DEFAULT 0," +
		"item_id INT NOT NULL DEFAULT 0," +
		"item_name VARCHAR(120) NOT NULL DEFAULT ''," +
		"category VARCHAR(32) NOT NULL DEFAULT 'misc'," +
		"quantity BIGINT NOT NULL DEFAULT 0," +
		"enchant_level INT NOT NULL DEFAULT 0," +
		"augmentation_id INT NOT NULL DEFAULT -1," +
		"custom_type1 INT NOT NULL DEFAULT 0," +
		"custom_type2 INT NOT NULL DEFAULT 0," +
		"transmog_id INT NOT NULL DEFAULT 0," +
		"mana_left INT NOT NULL DEFAULT -1," +
		"remaining_time_millis BIGINT NOT NULL DEFAULT 0," +
		"currency_amount BIGINT NOT NULL DEFAULT 0," +
		"currency_item_id INT NOT NULL DEFAULT 0," +
		"status VARCHAR(16) NOT NULL," +
		"created_at BIGINT NOT NULL," +
		"claimed_at BIGINT NOT NULL DEFAULT 0," +
		"PRIMARY KEY (claim_id)," +
		"KEY idx_market_claim_owner (owner_char_id)," +
		"KEY idx_market_claim_listing (listing_id)," +
		"KEY idx_market_claim_status (status)," +
		"KEY idx_market_claim_type (claim_type)" +
		") DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	
	private static final String CREATE_HISTORY = "CREATE TABLE IF NOT EXISTS marketplace_history (" +
		"history_id INT NOT NULL AUTO_INCREMENT," +
		"listing_id INT NOT NULL," +
		"seller_char_id INT NOT NULL," +
		"buyer_char_id INT NOT NULL DEFAULT 0," +
		"item_id INT NOT NULL," +
		"item_name VARCHAR(120) NOT NULL," +
		"quantity BIGINT NOT NULL," +
		"price BIGINT NOT NULL DEFAULT 0," +
		"commission BIGINT NOT NULL DEFAULT 0," +
		"seller_revenue BIGINT NOT NULL DEFAULT 0," +
		"action_type VARCHAR(32) NOT NULL," +
		"created_at BIGINT NOT NULL," +
		"ip_address VARCHAR(45) NOT NULL DEFAULT ''," +
		"account_name VARCHAR(64) NOT NULL DEFAULT ''," +
		"PRIMARY KEY (history_id)," +
		"KEY idx_market_history_listing (listing_id)," +
		"KEY idx_market_history_seller (seller_char_id)," +
		"KEY idx_market_history_buyer (buyer_char_id)," +
		"KEY idx_market_history_item (item_id)," +
		"KEY idx_market_history_created (created_at)" +
		") DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	
	private MarketplaceDAO()
	{
	}
	
	public static MarketplaceDAO getInstance()
	{
		return INSTANCE;
	}
	
	public void init()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement listings = con.prepareStatement(CREATE_LISTINGS);
			PreparedStatement claims = con.prepareStatement(CREATE_CLAIMS);
			PreparedStatement history = con.prepareStatement(CREATE_HISTORY))
		{
			listings.executeUpdate();
			claims.executeUpdate();
			history.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace: Could not initialize tables.", e);
		}
	}
	
	public int insertProcessingListing(Player seller, MarketplaceItemSnapshot item, long price, long commission, long revenue, long expiresAt)
	{
		final long now = System.currentTimeMillis();
		final String sql = "INSERT INTO marketplace_listings (seller_char_id, seller_name, item_object_id, item_id, item_name, category, quantity, enchant_level, augmentation_id, custom_type1, custom_type2, transmog_id, mana_left, remaining_time_millis, price, currency_item_id, commission_percent, commission_amount, seller_revenue, status, created_at, expires_at, last_updated) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS))
		{
			int i = 1;
			ps.setInt(i++, seller.getObjectId());
			ps.setString(i++, seller.getName());
			ps.setInt(i++, item.itemObjectId);
			ps.setInt(i++, item.itemId);
			ps.setString(i++, item.itemName);
			ps.setString(i++, item.category.getId());
			ps.setLong(i++, item.quantity);
			ps.setInt(i++, item.enchantLevel);
			ps.setInt(i++, item.augmentationId);
			ps.setInt(i++, item.customType1);
			ps.setInt(i++, item.customType2);
			ps.setInt(i++, item.transmogId);
			ps.setInt(i++, item.manaLeft);
			ps.setLong(i++, item.remainingTimeMillis);
			ps.setLong(i++, price);
			ps.setInt(i++, MarketplaceConfig.CURRENCY_ITEM_ID);
			ps.setInt(i++, MarketplaceConfig.COMMISSION_PERCENT);
			ps.setLong(i++, commission);
			ps.setLong(i++, revenue);
			ps.setString(i++, MarketplaceStatus.PROCESSING.name());
			ps.setLong(i++, now);
			ps.setLong(i++, expiresAt);
			ps.setLong(i++, now);
			ps.executeUpdate();
			try (ResultSet rs = ps.getGeneratedKeys())
			{
				return rs.next() ? rs.getInt(1) : 0;
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace: Could not insert listing.", e);
		}
		return 0;
	}
	
	public boolean activateListing(int listingId, int sellerCharId)
	{
		return updateStatus(listingId, sellerCharId, MarketplaceStatus.PROCESSING, MarketplaceStatus.ACTIVE);
	}
	
	public boolean markError(int listingId)
	{
		final String sql = "UPDATE marketplace_listings SET status=?, last_updated=? WHERE listing_id=? AND status=?";
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setString(1, MarketplaceStatus.ERROR.name());
			ps.setLong(2, System.currentTimeMillis());
			ps.setInt(3, listingId);
			ps.setString(4, MarketplaceStatus.PROCESSING.name());
			return ps.executeUpdate() > 0;
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace: Could not mark listing as error.", e);
		}
		return false;
	}
	
	public int countActiveListings(int sellerCharId)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM marketplace_listings WHERE seller_char_id=? AND status=?"))
		{
			ps.setInt(1, sellerCharId);
			ps.setString(2, MarketplaceStatus.ACTIVE.name());
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? rs.getInt(1) : 0;
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace: Could not count active listings.", e);
		}
		return 0;
	}
	
	public int countActiveListings()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM marketplace_listings WHERE status=? AND expires_at>?"))
		{
			ps.setString(1, MarketplaceStatus.ACTIVE.name());
			ps.setLong(2, System.currentTimeMillis());
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? rs.getInt(1) : 0;
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace: Could not count active listings.", e);
		}
		return 0;
	}
	
	public List<MarketplaceListing> getActiveListings(MarketplaceFilter filter)
	{
		final List<Object> params = new ArrayList<>();
		final StringBuilder sql = new StringBuilder("SELECT * FROM marketplace_listings WHERE status=? AND expires_at>?");
		params.add(MarketplaceStatus.ACTIVE.name());
		params.add(System.currentTimeMillis());
		appendFilters(sql, params, filter);
		sql.append(" ORDER BY ").append(filter.sort.getOrderBy()).append(" LIMIT ? OFFSET ?");
		params.add(MarketplaceFilter.PAGE_SIZE);
		params.add(filter.getOffset());
		
		final List<MarketplaceListing> listings = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(sql.toString()))
		{
			fill(ps, params);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					listings.add(MarketplaceListing.fromResultSet(rs));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace: Could not load active listings.", e);
		}
		return listings;
	}
	
	public int countActiveListings(MarketplaceFilter filter)
	{
		final List<Object> params = new ArrayList<>();
		final StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM marketplace_listings WHERE status=? AND expires_at>?");
		params.add(MarketplaceStatus.ACTIVE.name());
		params.add(System.currentTimeMillis());
		appendFilters(sql, params, filter);
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(sql.toString()))
		{
			fill(ps, params);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? rs.getInt(1) : 0;
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace: Could not count filtered listings.", e);
		}
		return 0;
	}
	
	public MarketplaceListing getListing(int listingId)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT * FROM marketplace_listings WHERE listing_id=?"))
		{
			ps.setInt(1, listingId);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? MarketplaceListing.fromResultSet(rs) : null;
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace: Could not load listing.", e);
		}
		return null;
	}
	
	public MarketplaceListing getListingForUpdate(Connection con, int listingId) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement("SELECT * FROM marketplace_listings WHERE listing_id=? FOR UPDATE"))
		{
			ps.setInt(1, listingId);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? MarketplaceListing.fromResultSet(rs) : null;
			}
		}
	}
	
	public void markSold(Connection con, MarketplaceListing listing, Player buyer, long commission, long revenue, long now) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement("UPDATE marketplace_listings SET status=?, buyer_char_id=?, buyer_name=?, commission_amount=?, seller_revenue=?, sold_at=?, last_updated=? WHERE listing_id=? AND status=?"))
		{
			ps.setString(1, MarketplaceStatus.SOLD.name());
			ps.setInt(2, buyer.getObjectId());
			ps.setString(3, buyer.getName());
			ps.setLong(4, commission);
			ps.setLong(5, revenue);
			ps.setLong(6, now);
			ps.setLong(7, now);
			ps.setInt(8, listing.listingId);
			ps.setString(9, MarketplaceStatus.ACTIVE.name());
			if (ps.executeUpdate() <= 0)
			{
				throw new SQLException("Listing was not active while marking SOLD.");
			}
		}
	}
	
	public void markCancelled(Connection con, MarketplaceListing listing, MarketplaceStatus newStatus, long now) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement("UPDATE marketplace_listings SET status=?, cancelled_at=?, last_updated=? WHERE listing_id=? AND status=?"))
		{
			ps.setString(1, newStatus.name());
			ps.setLong(2, now);
			ps.setLong(3, now);
			ps.setInt(4, listing.listingId);
			ps.setString(5, MarketplaceStatus.ACTIVE.name());
			if (ps.executeUpdate() <= 0)
			{
				throw new SQLException("Listing was not active while marking " + newStatus.name() + ".");
			}
		}
	}
	
	public void insertClaim(Connection con, int ownerCharId, String ownerName, MarketplaceClaimType type, MarketplaceListing listing, long currencyAmount, int currencyItemId, long now) throws SQLException
	{
		final MarketplaceItemSnapshot item = listing.item;
		final String sql = "INSERT INTO marketplace_claims (owner_char_id, owner_name, claim_type, listing_id, item_object_id, item_id, item_name, category, quantity, enchant_level, augmentation_id, custom_type1, custom_type2, transmog_id, mana_left, remaining_time_millis, currency_amount, currency_item_id, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
		try (PreparedStatement ps = con.prepareStatement(sql))
		{
			int i = 1;
			ps.setInt(i++, ownerCharId);
			ps.setString(i++, ownerName);
			ps.setString(i++, type.name());
			ps.setInt(i++, listing.listingId);
			ps.setInt(i++, item.itemObjectId);
			ps.setInt(i++, item.itemId);
			ps.setString(i++, item.itemName);
			ps.setString(i++, item.category.getId());
			ps.setLong(i++, type == MarketplaceClaimType.CURRENCY ? 0 : item.quantity);
			ps.setInt(i++, item.enchantLevel);
			ps.setInt(i++, item.augmentationId);
			ps.setInt(i++, item.customType1);
			ps.setInt(i++, item.customType2);
			ps.setInt(i++, item.transmogId);
			ps.setInt(i++, item.manaLeft);
			ps.setLong(i++, item.remainingTimeMillis);
			ps.setLong(i++, currencyAmount);
			ps.setInt(i++, currencyItemId);
			ps.setString(i++, MarketplaceClaimStatus.PENDING.name());
			ps.setLong(i++, now);
			ps.executeUpdate();
		}
	}
	
	public void insertHistory(Connection con, MarketplaceListing listing, int buyerCharId, long price, long commission, long revenue, String action, Player actor, long now) throws SQLException
	{
		final String sql = "INSERT INTO marketplace_history (listing_id, seller_char_id, buyer_char_id, item_id, item_name, quantity, price, commission, seller_revenue, action_type, created_at, ip_address, account_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
		try (PreparedStatement ps = con.prepareStatement(sql))
		{
			int i = 1;
			ps.setInt(i++, listing.listingId);
			ps.setInt(i++, listing.sellerCharId);
			ps.setInt(i++, buyerCharId);
			ps.setInt(i++, listing.item.itemId);
			ps.setString(i++, listing.item.itemName);
			ps.setLong(i++, listing.item.quantity);
			ps.setLong(i++, price);
			ps.setLong(i++, commission);
			ps.setLong(i++, revenue);
			ps.setString(i++, action);
			ps.setLong(i++, now);
			ps.setString(i++, actor == null ? "" : actor.getIPAddress());
			ps.setString(i++, actor == null ? "" : actor.getAccountName());
			ps.executeUpdate();
		}
	}
	
	public List<MarketplaceListing> getPlayerListings(int sellerCharId)
	{
		final List<MarketplaceListing> listings = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT * FROM marketplace_listings WHERE seller_char_id=? ORDER BY listing_id DESC LIMIT 30"))
		{
			ps.setInt(1, sellerCharId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					listings.add(MarketplaceListing.fromResultSet(rs));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace: Could not load player listings.", e);
		}
		return listings;
	}
	
	public List<MarketplaceClaim> getClaims(int ownerCharId)
	{
		final List<MarketplaceClaim> claims = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT * FROM marketplace_claims WHERE owner_char_id=? AND status=? ORDER BY claim_id DESC LIMIT 40"))
		{
			ps.setInt(1, ownerCharId);
			ps.setString(2, MarketplaceClaimStatus.PENDING.name());
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					claims.add(MarketplaceClaim.fromResultSet(rs));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace: Could not load claims.", e);
		}
		return claims;
	}
	
	public MarketplaceClaim getClaimForUpdate(Connection con, int claimId) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement("SELECT * FROM marketplace_claims WHERE claim_id=? FOR UPDATE"))
		{
			ps.setInt(1, claimId);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? MarketplaceClaim.fromResultSet(rs) : null;
			}
		}
	}
	
	public void markClaimed(Connection con, int claimId, long now) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement("UPDATE marketplace_claims SET status=?, claimed_at=? WHERE claim_id=? AND status=?"))
		{
			ps.setString(1, MarketplaceClaimStatus.CLAIMED.name());
			ps.setLong(2, now);
			ps.setInt(3, claimId);
			ps.setString(4, MarketplaceClaimStatus.PENDING.name());
			if (ps.executeUpdate() <= 0)
			{
				throw new SQLException("Claim was already processed.");
			}
		}
	}
	
	public List<MarketplaceHistoryEntry> getHistory(int charId)
	{
		final List<MarketplaceHistoryEntry> history = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT * FROM marketplace_history WHERE seller_char_id=? OR buyer_char_id=? ORDER BY history_id DESC LIMIT 40"))
		{
			ps.setInt(1, charId);
			ps.setInt(2, charId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					history.add(MarketplaceHistoryEntry.fromResultSet(rs));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace: Could not load history.", e);
		}
		return history;
	}
	
	public List<MarketplaceHistoryEntry> getRecentSales(int limit)
	{
		final List<MarketplaceHistoryEntry> history = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT * FROM marketplace_history WHERE action_type=? ORDER BY history_id DESC LIMIT ?"))
		{
			ps.setString(1, "PURCHASED");
			ps.setInt(2, limit);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					history.add(MarketplaceHistoryEntry.fromResultSet(rs));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace: Could not load recent sales.", e);
		}
		return history;
	}
	
	public List<Integer> getExpiredListingIds(int limit)
	{
		final List<Integer> ids = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT listing_id FROM marketplace_listings WHERE status=? AND expires_at<=? ORDER BY expires_at ASC LIMIT ?"))
		{
			ps.setString(1, MarketplaceStatus.ACTIVE.name());
			ps.setLong(2, System.currentTimeMillis());
			ps.setInt(3, limit);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					ids.add(rs.getInt(1));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace: Could not load expired listings.", e);
		}
		return ids;
	}
	
	public MarketplaceStats getStats()
	{
		final MarketplaceStats stats = new MarketplaceStats();
		try (Connection con = DatabaseFactory.getConnection())
		{
			stats.activeListings = countByStatus(con, MarketplaceStatus.ACTIVE);
			stats.soldListings = countByStatus(con, MarketplaceStatus.SOLD);
			stats.expiredListings = countByStatus(con, MarketplaceStatus.EXPIRED);
			stats.totalMoved = sum(con, "SELECT COALESCE(SUM(price),0) FROM marketplace_history WHERE action_type='PURCHASED'");
			stats.totalCommission = sum(con, "SELECT COALESCE(SUM(commission),0) FROM marketplace_history WHERE action_type='PURCHASED'");
			stats.pendingRevenue = sum(con, "SELECT COALESCE(SUM(currency_amount),0) FROM marketplace_claims WHERE claim_type='CURRENCY' AND status='PENDING'");
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace: Could not load stats.", e);
		}
		return stats;
	}
	
	private int countByStatus(Connection con, MarketplaceStatus status) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM marketplace_listings WHERE status=?"))
		{
			ps.setString(1, status.name());
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? rs.getInt(1) : 0;
			}
		}
	}
	
	private long sum(Connection con, String sql) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(sql);
			ResultSet rs = ps.executeQuery())
		{
			return rs.next() ? rs.getLong(1) : 0;
		}
	}
	
	private boolean updateStatus(int listingId, int sellerCharId, MarketplaceStatus oldStatus, MarketplaceStatus newStatus)
	{
		final String sql = "UPDATE marketplace_listings SET status=?, last_updated=? WHERE listing_id=? AND seller_char_id=? AND status=?";
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setString(1, newStatus.name());
			ps.setLong(2, System.currentTimeMillis());
			ps.setInt(3, listingId);
			ps.setInt(4, sellerCharId);
			ps.setString(5, oldStatus.name());
			return ps.executeUpdate() > 0;
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace: Could not update listing status.", e);
		}
		return false;
	}
	
	private void appendFilters(StringBuilder sql, List<Object> params, MarketplaceFilter filter)
	{
		if ((filter.category != null) && (filter.category != MarketplaceCategory.ALL))
		{
			sql.append(" AND category=?");
			params.add(filter.category.getId());
		}
		if ((filter.search != null) && !filter.search.isEmpty())
		{
			sql.append(" AND item_name LIKE ? ESCAPE '/'");
			params.add("%" + escapeLike(filter.search) + "%");
		}
		if ((filter.seller != null) && !filter.seller.isEmpty())
		{
			sql.append(" AND seller_name LIKE ? ESCAPE '/'");
			params.add("%" + escapeLike(filter.seller) + "%");
		}
		if (filter.minPrice > 0)
		{
			sql.append(" AND price>=?");
			params.add(filter.minPrice);
		}
		if (filter.maxPrice > 0)
		{
			sql.append(" AND price<=?");
			params.add(filter.maxPrice);
		}
		if (filter.minEnchant != Integer.MIN_VALUE)
		{
			sql.append(" AND enchant_level>=?");
			params.add(filter.minEnchant);
		}
		if (filter.maxEnchant != Integer.MAX_VALUE)
		{
			sql.append(" AND enchant_level<=?");
			params.add(filter.maxEnchant);
		}
	}
	
	private void fill(PreparedStatement ps, List<Object> params) throws SQLException
	{
		for (int i = 0; i < params.size(); i++)
		{
			final Object value = params.get(i);
			final int index = i + 1;
			if (value instanceof Integer)
			{
				ps.setInt(index, ((Integer) value).intValue());
			}
			else if (value instanceof Long)
			{
				ps.setLong(index, ((Long) value).longValue());
			}
			else
			{
				ps.setString(index, String.valueOf(value));
			}
		}
	}
	
	private String escapeLike(String value)
	{
		return value.replace("/", "//").replace("%", "/%").replace("_", "/_");
	}
}
