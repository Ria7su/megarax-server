package custom.MegaraxMarketplace;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Logger;

import org.l2jmobius.Config;
import org.l2jmobius.gameserver.model.actor.Player;

public final class MarketplaceLogger
{
	private static final Logger LOGGER = Logger.getLogger(MarketplaceLogger.class.getName());
	private static final Object LOCK = new Object();
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
	
	private MarketplaceLogger()
	{
	}
	
	public static void log(String action, Player actor, MarketplaceListing listing, String reason)
	{
		if (!MarketplaceConfig.LOG_TRANSACTIONS)
		{
			return;
		}
		
		final StringBuilder line = new StringBuilder(256);
		line.append(DATE_FORMAT.format(new Date())).append(" [MARKETPLACE] ").append(action);
		if (actor != null)
		{
			line.append(" account=").append(safe(actor.getAccountName()));
			line.append(" actorId=").append(actor.getObjectId());
			line.append(" actorName=").append(safe(actor.getName()));
		}
		if (listing != null)
		{
			line.append(" listingId=").append(listing.listingId);
			line.append(" sellerId=").append(listing.sellerCharId);
			line.append(" seller=").append(safe(listing.sellerName));
			line.append(" buyerId=").append(listing.buyerCharId);
			line.append(" buyer=").append(safe(listing.buyerName));
			if (listing.item != null)
			{
				line.append(" itemId=").append(listing.item.itemId);
				line.append(" objectId=").append(listing.item.itemObjectId);
				line.append(" count=").append(listing.item.quantity);
				line.append(" enchant=").append(listing.item.enchantLevel);
				line.append(" augment=").append(listing.item.augmentationId);
			}
			line.append(" price=").append(listing.price);
			line.append(" commission=").append(listing.commissionAmount);
			line.append(" revenue=").append(listing.sellerRevenue);
			line.append(" status=").append(listing.status);
		}
		if ((reason != null) && !reason.isEmpty())
		{
			line.append(" reason=\"").append(safe(reason)).append("\"");
		}
		write(line.toString());
	}
	
	public static void logFailure(String action, Player actor, int listingId, String reason)
	{
		if (!MarketplaceConfig.LOG_TRANSACTIONS)
		{
			return;
		}
		final StringBuilder line = new StringBuilder(192);
		line.append(DATE_FORMAT.format(new Date())).append(" [MARKETPLACE] ").append(action);
		if (actor != null)
		{
			line.append(" account=").append(safe(actor.getAccountName()));
			line.append(" actorId=").append(actor.getObjectId());
			line.append(" actorName=").append(safe(actor.getName()));
		}
		line.append(" listingId=").append(listingId);
		line.append(" reason=\"").append(safe(reason)).append("\"");
		write(line.toString());
	}
	
	private static void write(String line)
	{
		synchronized (LOCK)
		{
			final File file = resolveLogFile();
			final File parent = file.getParentFile();
			if ((parent != null) && !parent.isDirectory())
			{
				parent.mkdirs();
			}
			try (FileWriter writer = new FileWriter(file, true))
			{
				writer.write(line);
				writer.write(System.lineSeparator());
			}
			catch (IOException e)
			{
				LOGGER.warning("Marketplace: Could not write marketplace log: " + e.getMessage());
			}
		}
	}
	
	private static File resolveLogFile()
	{
		if (Config.DATAPACK_ROOT != null)
		{
			return new File(Config.DATAPACK_ROOT, "log/marketplace.log");
		}
		return new File("game/log/marketplace.log");
	}
	
	private static String safe(String value)
	{
		return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').replace('"', '\'');
	}
}
