package custom.MegaraxMarketplace;

public enum MarketplaceSort
{
	RECENT("recent", "Most recent", "created_at DESC"),
	PRICE_ASC("price_asc", "Lowest price", "price ASC"),
	PRICE_DESC("price_desc", "Highest price", "price DESC"),
	ENCHANT_DESC("enchant_desc", "Highest enchant", "enchant_level DESC"),
	EXPIRING("expiring", "Expiring soon", "expires_at ASC");
	
	private final String _id;
	private final String _label;
	private final String _orderBy;
	
	MarketplaceSort(String id, String label, String orderBy)
	{
		_id = id;
		_label = label;
		_orderBy = orderBy;
	}
	
	public String getId()
	{
		return _id;
	}
	
	public String getLabel()
	{
		return _label;
	}
	
	public String getOrderBy()
	{
		return _orderBy;
	}
	
	public static MarketplaceSort fromId(String id)
	{
		for (MarketplaceSort sort : values())
		{
			if (sort._id.equalsIgnoreCase(id))
			{
				return sort;
			}
		}
		return RECENT;
	}
}
