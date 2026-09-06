package custom.MegaraxMarketplace;

import java.util.Locale;

import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.EtcItemType;

public enum MarketplaceCategory
{
	ALL("all", "All"),
	WEAPONS("weapons", "Weapons"),
	ARMOR("armor", "Armor"),
	JEWELS("jewels", "Jewels"),
	ACCESSORIES("accessories", "Accessories"),
	CONSUMABLES("consumables", "Consumables"),
	ENCHANT_SCROLLS("enchant_scrolls", "Enchant Scrolls"),
	MATERIALS("materials", "Materials"),
	RECIPES("recipes", "Recipes"),
	DYES("dyes", "Dyes"),
	SOUL_CRYSTALS("soul_crystals", "Soul Crystals"),
	CUSTOM_ITEMS("custom_items", "Custom Items"),
	MISC("misc", "Miscellaneous");
	
	private final String _id;
	private final String _label;
	
	MarketplaceCategory(String id, String label)
	{
		_id = id;
		_label = label;
	}
	
	public String getId()
	{
		return _id;
	}
	
	public String getLabel()
	{
		return _label;
	}
	
	public static MarketplaceCategory fromId(String id)
	{
		for (MarketplaceCategory category : values())
		{
			if (category._id.equalsIgnoreCase(id))
			{
				return category;
			}
		}
		return ALL;
	}
	
	public static MarketplaceCategory fromItem(Item item)
	{
		if (item == null)
		{
			return MISC;
		}
		if ((item.getCustomType1() > 0) || (item.getCustomType2() > 0) || (item.getTransmogId() > 0))
		{
			return CUSTOM_ITEMS;
		}
		return fromTemplate(item.getTemplate());
	}
	
	public static MarketplaceCategory fromTemplate(ItemTemplate template)
	{
		if (template == null)
		{
			return MISC;
		}
		if (template.isWeapon())
		{
			return WEAPONS;
		}
		if (template.isArmor())
		{
			final int bodyPart = template.getBodyPart();
			if ((bodyPart == ItemTemplate.SLOT_R_EAR) || (bodyPart == ItemTemplate.SLOT_L_EAR) || (bodyPart == ItemTemplate.SLOT_LR_EAR) || (bodyPart == ItemTemplate.SLOT_NECK) || (bodyPart == ItemTemplate.SLOT_R_FINGER) || (bodyPart == ItemTemplate.SLOT_L_FINGER) || (bodyPart == ItemTemplate.SLOT_LR_FINGER))
			{
				return JEWELS;
			}
			if ((bodyPart == ItemTemplate.SLOT_HAIR) || (bodyPart == ItemTemplate.SLOT_HAIR2) || (bodyPart == ItemTemplate.SLOT_HAIRALL) || (bodyPart == ItemTemplate.SLOT_ALLDRESS))
			{
				return ACCESSORIES;
			}
			return ARMOR;
		}
		if (template.isEtcItem())
		{
			final EtcItemType type = template.getItemType() instanceof EtcItemType ? (EtcItemType) template.getItemType() : EtcItemType.NONE;
			if ((type == EtcItemType.SCRL_ENCHANT_WP) || (type == EtcItemType.SCRL_ENCHANT_AM) || (type == EtcItemType.BLESS_SCRL_ENCHANT_WP) || (type == EtcItemType.BLESS_SCRL_ENCHANT_AM) || (type == EtcItemType.ANCIENT_CRYSTAL_ENCHANT_WP) || (type == EtcItemType.ANCIENT_CRYSTAL_ENCHANT_AM))
			{
				return ENCHANT_SCROLLS;
			}
			if ((type == EtcItemType.POTION) || (type == EtcItemType.ELIXIR) || (type == EtcItemType.SCROLL) || (type == EtcItemType.SHOT) || (type == EtcItemType.ARROW))
			{
				return CONSUMABLES;
			}
			if (type == EtcItemType.MATERIAL)
			{
				return MATERIALS;
			}
			if (type == EtcItemType.RECIPE)
			{
				return RECIPES;
			}
			if (type == EtcItemType.DYE)
			{
				return DYES;
			}
			final String name = template.getName() == null ? "" : template.getName().toLowerCase(Locale.ROOT);
			if (name.contains("soul crystal"))
			{
				return SOUL_CRYSTALS;
			}
		}
		return MISC;
	}
}
