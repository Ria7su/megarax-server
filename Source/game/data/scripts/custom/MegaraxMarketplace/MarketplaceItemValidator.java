package custom.MegaraxMarketplace;

import java.util.Calendar;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.zone.ZoneId;

public final class MarketplaceItemValidator
{
	private MarketplaceItemValidator()
	{
	}
	
	public static String validatePlayer(Player player)
	{
		if (player == null)
		{
			return "Jugador invalido.";
		}
		if (!MarketplaceConfig.ENABLED)
		{
			return "El Marketplace esta deshabilitado.";
		}
		if (!MarketplaceConfig.ALLOWED_IN_COMBAT && player.isInCombat())
		{
			return "No puedes usar el Marketplace en combate.";
		}
		if (!MarketplaceConfig.ALLOWED_WHILE_DEAD && player.isAlikeDead())
		{
			return "No puedes usar el Marketplace estando muerto.";
		}
		if (!MarketplaceConfig.ALLOWED_IN_OLYMPIAD && (player.isInOlympiadMode() || player.isOlympiadStart()))
		{
			return "No puedes usar el Marketplace durante Olympiad.";
		}
		if (!MarketplaceConfig.ALLOWED_IN_TOURNAMENT && player.isOnEvent())
		{
			return "No puedes usar el Marketplace durante eventos o torneos.";
		}
		if (!MarketplaceConfig.ALLOWED_IN_JAIL && (player.isJailed() || player.isInsideZone(ZoneId.JAIL)))
		{
			return "No puedes usar el Marketplace en jail.";
		}
		if (!MarketplaceConfig.ALLOWED_IN_INSTANCE && (player.getInstanceId() > 0))
		{
			return "No puedes usar el Marketplace dentro de una instancia.";
		}
		if (!MarketplaceConfig.ALLOWED_DURING_TRADE && (player.isProcessingTransaction() || (player.getActiveTradeList() != null)))
		{
			return "No puedes usar el Marketplace durante un trade.";
		}
		if (!MarketplaceConfig.ALLOWED_DURING_PRIVATE_STORE && player.isInStoreMode())
		{
			return "No puedes usar el Marketplace mientras tienes tienda privada.";
		}
		if (!MarketplaceConfig.ALLOWED_FOR_KARMA_PLAYERS && (player.getKarma() > 0))
		{
			return "Los jugadores con karma no pueden usar el Marketplace.";
		}
		if (player.getLevel() < MarketplaceConfig.MINIMUM_LEVEL)
		{
			return "Necesitas nivel " + MarketplaceConfig.MINIMUM_LEVEL + " para usar el Marketplace.";
		}
		if (!MarketplaceConfig.ALLOWED_FOR_NEW_CHARACTERS && (MarketplaceConfig.MINIMUM_ACCOUNT_AGE_DAYS > 0))
		{
			final Calendar createDate = player.getCreateDate();
			if ((createDate != null) && ((System.currentTimeMillis() - createDate.getTimeInMillis()) < (MarketplaceConfig.MINIMUM_ACCOUNT_AGE_DAYS * 24L * 60L * 60L * 1000L)))
			{
				return "Tu personaje necesita " + MarketplaceConfig.MINIMUM_ACCOUNT_AGE_DAYS + " dias de antiguedad para usar el Marketplace.";
			}
		}
		return null;
	}
	
	public static String validateItemForListing(Player player, Item item, long quantity)
	{
		final String playerReason = validatePlayer(player);
		if (playerReason != null)
		{
			return playerReason;
		}
		if (item == null)
		{
			return "El objeto seleccionado ya no existe.";
		}
		if (quantity <= 0)
		{
			return "La cantidad debe ser mayor a cero.";
		}
		if (quantity > Integer.MAX_VALUE)
		{
			return "La cantidad es demasiado grande.";
		}
		if (quantity > item.getCount())
		{
			return "No tienes esa cantidad disponible.";
		}
		if (item.getOwnerId() != player.getObjectId())
		{
			return "Este objeto no pertenece a tu inventario.";
		}
		if (!player.validateItemManipulation(item.getObjectId(), ItemProcessType.SELL))
		{
			return "El objeto esta bloqueado o no puede manipularse ahora.";
		}
		if (!player.getInventory().canManipulateWithItemId(item.getId()))
		{
			return "El objeto esta bloqueado por el inventario.";
		}
		if (!MarketplaceConfig.ALLOW_EQUIPPED_ITEMS && item.isEquipped())
		{
			return "No puedes vender objetos equipados.";
		}
		if (!MarketplaceConfig.ALLOW_STACKABLE_ITEMS && item.isStackable())
		{
			return "Los objetos stackeables no estan permitidos.";
		}
		if (!item.isStackable() && (quantity != 1))
		{
			return "Los objetos no stackeables deben publicarse individualmente.";
		}
		if (!MarketplaceConfig.ALLOW_AUGMENTED_ITEMS && item.isAugmented())
		{
			return "No puedes vender objetos augmentados.";
		}
		if (!MarketplaceConfig.ALLOW_ENCHANTED_ITEMS && (item.getEnchantLevel() > 0))
		{
			return "No puedes vender objetos encantados.";
		}
		if (!MarketplaceConfig.ALLOW_CUSTOM_ITEMS && ((item.getCustomType1() > 0) || (item.getCustomType2() > 0) || (item.getTransmogId() > 0)))
		{
			return "No puedes vender objetos custom en el Marketplace.";
		}
		if (!MarketplaceConfig.ALLOW_QUEST_ITEMS && item.isQuestItem())
		{
			return "No puedes vender quest items.";
		}
		if (!MarketplaceConfig.ALLOW_SHADOW_ITEMS && item.isShadowItem())
		{
			return "No puedes vender shadow items.";
		}
		if (!MarketplaceConfig.ALLOW_TIME_LIMITED_ITEMS && item.isTimeLimitedItem())
		{
			return "No puedes vender objetos con tiempo limitado.";
		}
		if (!MarketplaceConfig.ALLOW_HERO_ITEMS && item.isHeroItem())
		{
			return "No puedes vender hero items.";
		}
		if (MarketplaceConfig.ALLOW_TRADABLE_ONLY && !item.isTradeable())
		{
			return "Este objeto no es tradeable.";
		}
		if (MarketplaceConfig.isRestricted(item.getId()))
		{
			return "Este objeto esta restringido en el Marketplace.";
		}
		if (player.getActiveWarehouse() != null)
		{
			return "Cierra el warehouse antes de usar el Marketplace.";
		}
		if (player.getMultiSell() != null)
		{
			return "Cierra la ventana de multisell antes de usar el Marketplace.";
		}
		if (player.isEnchanting() || player.isInCrystallize())
		{
			return "No puedes publicar objetos mientras procesas enchant o crystallize.";
		}
		return null;
	}
	
	public static String validatePrice(long price)
	{
		if (price < MarketplaceConfig.MIN_PRICE)
		{
			return "El precio minimo es " + MarketplaceConfig.MIN_PRICE + " Rax Coin.";
		}
		if (price > MarketplaceConfig.MAX_PRICE)
		{
			return "El precio maximo es " + MarketplaceConfig.MAX_PRICE + " Rax Coin.";
		}
		if (price > Integer.MAX_VALUE)
		{
			return "El precio excede el limite seguro del servidor.";
		}
		return null;
	}
	
	public static boolean canReceiveItem(Player player, MarketplaceItemSnapshot item)
	{
		if ((player == null) || (item == null) || (item.quantity <= 0))
		{
			return false;
		}
		return player.getInventory().validateCapacityByItemId(item.itemId, item.quantity) && player.getInventory().validateWeightByItemId(item.itemId, item.quantity);
	}
}
