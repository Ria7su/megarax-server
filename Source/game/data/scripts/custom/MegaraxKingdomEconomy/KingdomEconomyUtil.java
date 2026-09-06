package custom.MegaraxKingdomEconomy;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Locale;

public final class KingdomEconomyUtil
{
	private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.US);
	
	private KingdomEconomyUtil()
	{
	}
	
	public static String html(String value)
	{
		if (value == null)
		{
			return "";
		}
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
	
	public static String safe(String value, int maxLength)
	{
		if (value == null)
		{
			return "";
		}
		String clean = value.replace("\r", " ").replace("\n", " ").replace("\t", " ").trim();
		if (clean.length() > maxLength)
		{
			clean = clean.substring(0, maxLength);
		}
		return clean;
	}
	
	public static String format(long value)
	{
		return NUMBER.format(value);
	}
	
	public static int dayKey()
	{
		final LocalDate now = LocalDate.now();
		return (now.getYear() * 10000) + (now.getMonthValue() * 100) + now.getDayOfMonth();
	}
	
	public static int weekKey()
	{
		final LocalDate now = LocalDate.now();
		final int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
		final int year = now.get(WeekFields.ISO.weekBasedYear());
		return (year * 100) + week;
	}
}
