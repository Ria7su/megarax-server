package handlers.voicedcommandhandlers;

import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.cache.HtmCache;
import org.l2jmobius.gameserver.handler.IVoicedCommandHandler;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.Containers;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogin;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

/** Account-wide first-login guide for Megarax. */
public class WelcomeCenter implements IVoicedCommandHandler
{
	private static final Logger LOGGER = Logger.getLogger("megarax_welcome");
	private static final String CONFIG = "config/Custom/WelcomeCenter.ini";
	private static final String HTML = "data/html/mods/WelcomeCenter/";
	private static final String CREATE = "CREATE TABLE IF NOT EXISTS account_welcome_status (account_name VARCHAR(45) NOT NULL, welcome_shown TINYINT(1) NOT NULL DEFAULT 0, first_shown_at TIMESTAMP NULL, last_opened_at TIMESTAMP NULL, last_character_id INT NOT NULL DEFAULT 0, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY(account_name)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String[] COMMANDS = { "guide", "welcome" };
	private static boolean ENABLED = true;
	private static boolean SHOW_EXISTING = false;
	private static int DELAY_SECONDS = 2;

	public WelcomeCenter()
	{
		loadConfig();
		if (!ENABLED)
		{
			return;
		}
		initializeTable();
		final Consumer<OnPlayerLogin> listener = event -> scheduleWelcome(event.getPlayer());
		Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_LOGIN, listener, this));
	}

	@Override
	public boolean onCommand(String command, Player player, String params)
	{
		if (!ENABLED || (player == null))
		{
			return false;
		}
		final String page = sanitizePage(params);
		show(player, page, true);
		return true;
	}

	@Override
	public String[] getCommandList()
	{
		return COMMANDS;
	}

	private static void scheduleWelcome(Player player)
	{
		if ((player == null) || (!SHOW_EXISTING && hasStatus(player.getAccountName())))
		{
			return;
		}
		ThreadPool.schedule(() ->
		{
			if ((World.getInstance().getPlayer(player.getObjectId()) == player) && !player.isDead() && !player.inObserverMode() && (SHOW_EXISTING || !hasStatus(player.getAccountName())))
			{
				show(player, "main", false);
			}
		}, DELAY_SECONDS * 1000L);
	}

	private static void show(Player player, String page, boolean manual)
	{
		String html = HtmCache.getInstance().getHtm(player, HTML + page + ".html");
		if (html == null)
		{
			player.sendMessage("The Megarax guide is temporarily unavailable.");
			return;
		}
		if (page.equals("features"))
		{
			html = replaceFeatures(html);
		}
		final NpcHtmlMessage packet = new NpcHtmlMessage();
		packet.setHtml(html);
		player.sendPacket(packet);
		markOpened(player, !manual);
	}

	private static String sanitizePage(String params)
	{
		if (params == null || params.trim().isEmpty()) return "main";
		final String page = params.trim().split("\\s+")[0].toLowerCase();
		return page.equals("features") || page.equals("systems") || page.equals("clans") || page.equals("commands") ? page : "main";
	}

	private static String replaceFeatures(String html)
	{
		final Properties rates = load("config/Rates.ini");
		final Properties character = load("config/Character.ini");
		final Properties enchant = load("config/General.ini");
		html = html.replace("%exp%", rates.getProperty("RateXp", "?").trim());
		html = html.replace("%sp%", rates.getProperty("RateSp", "?").trim());
		html = html.replace("%adena%", itemMultiplier(rates.getProperty("DropAmountMultiplierByItemId", ""), 57, "?"));
		html = html.replace("%drop%", rates.getProperty("DeathDropAmountMultiplier", "?").trim());
		html = html.replace("%spoil%", rates.getProperty("SpoilDropAmountMultiplier", "?").trim());
		html = html.replace("%startlevel%", character.getProperty("StartingLevel", "1").trim());
		html = html.replace("%maxlevel%", character.getProperty("MaximumPlayerLevel", "?").trim());
		html = html.replace("%safe%", enchant.getProperty("SafeEnchant", "3").trim());
		html = html.replace("%maxenchant%", enchant.getProperty("MaxEnchant", "20").trim());
		return html;
	}

	private static String itemMultiplier(String values, int itemId, String fallback)
	{
		for (String entry : values.split(";"))
		{
			final String[] pair = entry.trim().split(",");
			if ((pair.length == 2) && pair[0].trim().equals(Integer.toString(itemId))) return pair[1].trim();
		}
		return fallback;
	}

	private static Properties load(String file)
	{
		final Properties p = new Properties();
		try (InputStream input = new FileInputStream(file)) { p.load(input); }
		catch (Exception e) { LOGGER.fine("Could not read display config " + file); }
		return p;
	}

	private static boolean hasStatus(String account)
	{
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT welcome_shown FROM account_welcome_status WHERE account_name=?"))
		{
			ps.setString(1, account);
			try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getBoolean(1); }
		}
		catch (Exception e) { LOGGER.log(Level.WARNING, "Welcome status lookup failed for " + account, e); return true; }
	}

	private static void markOpened(Player player, boolean first)
	{
		final String sql = "INSERT INTO account_welcome_status(account_name,welcome_shown,first_shown_at,last_opened_at,last_character_id) VALUES(?,1," + (first ? "CURRENT_TIMESTAMP" : "NULL") + ",CURRENT_TIMESTAMP,?) ON DUPLICATE KEY UPDATE welcome_shown=1,first_shown_at=COALESCE(first_shown_at," + (first ? "CURRENT_TIMESTAMP" : "first_shown_at") + "),last_opened_at=CURRENT_TIMESTAMP,last_character_id=VALUES(last_character_id)";
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) { ps.setString(1, player.getAccountName()); ps.setInt(2, player.getObjectId()); ps.executeUpdate(); }
		catch (Exception e) { LOGGER.log(Level.WARNING, "Welcome status update failed for " + player.getAccountName(), e); }
	}

	private static void initializeTable()
	{
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(CREATE)) { ps.execute(); }
		catch (Exception e) { LOGGER.log(Level.SEVERE, "Welcome table initialization failed.", e); }
	}

	private static void loadConfig()
	{
		final Properties p = load(CONFIG);
		ENABLED = Boolean.parseBoolean(p.getProperty("WelcomeCenterEnabled", "true").trim());
		SHOW_EXISTING = Boolean.parseBoolean(p.getProperty("WelcomeCenterShowToExistingAccounts", "false").trim());
		try { DELAY_SECONDS = Math.max(1, Integer.parseInt(p.getProperty("WelcomeCenterDelaySeconds", "2").trim())); } catch (Exception e) { DELAY_SECONDS = 2; }
	}
}
