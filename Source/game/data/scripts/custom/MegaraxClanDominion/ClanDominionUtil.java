package custom.MegaraxClanDominion;

import java.text.NumberFormat;
import java.util.Locale;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.GameClient;

public final class ClanDominionUtil
{
	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
	
	private ClanDominionUtil()
	{
	}
	
	public static String format(long value)
	{
		return NUMBER_FORMAT.format(value);
	}
	
	public static String html(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "'");
	}
	
	public static String hardwareId(Player player)
	{
		final GameClient client = player.getClient();
		if ((client == null) || (client.getHardwareInfo() == null))
		{
			return null;
		}
		return client.getHardwareInfo().getMacAddress();
	}
	
	public static String tierName(int tier)
	{
		String result = "Unknown Clan";
		for (ClanDominionConfig.TierRequirement requirement : ClanDominionConfig.TIERS)
		{
			if (requirement.tier == tier)
			{
				return requirement.name;
			}
			if (requirement.tier <= tier)
			{
				result = requirement.name;
			}
		}
		return result;
	}
}
