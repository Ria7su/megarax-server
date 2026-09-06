package custom.MegaraxMarketplace;

public class MarketplaceFilter
{
	public static final int PAGE_SIZE = 8;
	
	public int page = 1;
	public MarketplaceCategory category = MarketplaceCategory.ALL;
	public MarketplaceSort sort = MarketplaceSort.RECENT;
	public String search = "";
	public String seller = "";
	public long minPrice = 0;
	public long maxPrice = 0;
	public int minEnchant = Integer.MIN_VALUE;
	public int maxEnchant = Integer.MAX_VALUE;
	
	public int getOffset()
	{
		return (Math.max(1, page) - 1) * PAGE_SIZE;
	}
}
