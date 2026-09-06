package custom.MegaraxMarketplace;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.l2jmobius.gameserver.model.Augmentation;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;

public class MarketplaceItemSnapshot
{
	public int itemObjectId;
	public int itemId;
	public String itemName;
	public long quantity;
	public int enchantLevel;
	public int augmentationId;
	public int customType1;
	public int customType2;
	public int transmogId;
	public int manaLeft;
	public long remainingTimeMillis;
	public MarketplaceCategory category;
	
	public static MarketplaceItemSnapshot fromItem(Item item, long quantity)
	{
		final MarketplaceItemSnapshot snapshot = new MarketplaceItemSnapshot();
		snapshot.itemObjectId = item.getObjectId();
		snapshot.itemId = item.getId();
		snapshot.itemName = item.getName();
		snapshot.quantity = quantity;
		snapshot.enchantLevel = item.getEnchantLevel();
		snapshot.augmentationId = item.isAugmented() ? item.getAugmentation().getAugmentationId() : -1;
		snapshot.customType1 = item.getCustomType1();
		snapshot.customType2 = item.getCustomType2();
		snapshot.transmogId = item.getTransmogId();
		snapshot.manaLeft = item.getMana();
		snapshot.remainingTimeMillis = item.isTimeLimitedItem() ? Math.max(0, item.getRemainingTime()) : 0;
		snapshot.category = MarketplaceCategory.fromItem(item);
		return snapshot;
	}
	
	public static MarketplaceItemSnapshot fromResultSet(ResultSet rs) throws SQLException
	{
		final MarketplaceItemSnapshot snapshot = new MarketplaceItemSnapshot();
		snapshot.itemObjectId = rs.getInt("item_object_id");
		snapshot.itemId = rs.getInt("item_id");
		snapshot.itemName = rs.getString("item_name");
		snapshot.quantity = rs.getLong("quantity");
		snapshot.enchantLevel = rs.getInt("enchant_level");
		snapshot.augmentationId = rs.getInt("augmentation_id");
		snapshot.customType1 = rs.getInt("custom_type1");
		snapshot.customType2 = rs.getInt("custom_type2");
		snapshot.transmogId = rs.getInt("transmog_id");
		snapshot.manaLeft = rs.getInt("mana_left");
		snapshot.remainingTimeMillis = rs.getLong("remaining_time_millis");
		snapshot.category = MarketplaceCategory.fromId(rs.getString("category"));
		return snapshot;
	}
	
	public Item giveTo(Player player, WorldObject reference, boolean sendMessage)
	{
		if ((player == null) || (quantity <= 0) || (quantity > Integer.MAX_VALUE))
		{
			return null;
		}
		
		final Item item = player.addItem(ItemProcessType.REWARD, itemId, (int) quantity, reference == null ? player : reference, sendMessage);
		if ((item != null) && !item.isStackable())
		{
			applyTo(item);
		}
		return item;
	}
	
	public void applyTo(Item item)
	{
		if (item == null)
		{
			return;
		}
		item.setEnchantLevel(enchantLevel);
		item.setCustomType1(customType1);
		item.setCustomType2(customType2);
		if (transmogId > 0)
		{
			item.setTransmogId(transmogId);
		}
		if (augmentationId > 0)
		{
			item.setAugmentation(new Augmentation(augmentationId));
		}
		if (manaLeft >= 0)
		{
			setPrivateInt(item, "_mana", manaLeft);
		}
		if (remainingTimeMillis > 0)
		{
			setPrivateLong(item, "_time", System.currentTimeMillis() + remainingTimeMillis);
			item.scheduleLifeTimeTask();
		}
		item.updateDatabase();
	}
	
	private void setPrivateInt(Item item, String fieldName, int value)
	{
		try
		{
			final Field field = Item.class.getDeclaredField(fieldName);
			field.setAccessible(true);
			field.setInt(item, value);
		}
		catch (ReflectiveOperationException ignored)
		{
		}
	}
	
	private void setPrivateLong(Item item, String fieldName, long value)
	{
		try
		{
			final Field field = Item.class.getDeclaredField(fieldName);
			field.setAccessible(true);
			field.setLong(item, value);
		}
		catch (ReflectiveOperationException ignored)
		{
		}
	}
}
