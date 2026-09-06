package handlers.communityboard;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.handler.IParseBoardHandler;
import org.l2jmobius.gameserver.managers.PremiumManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.Containers;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogin;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogout;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Account-wide free and premium daily reward tracks. */
public class DailyRewardsBoard implements IParseBoardHandler
{
	private static final Logger LOGGER = Logger.getLogger("premium_rewards");
	private static final File CONFIG_FILE = new File("data/DailyRewards.xml");
	private static final String MASTER_CONFIG_FILE = "config/Custom/DailyRewards.ini";
	private static volatile boolean MASTER_ENABLED = false;
	private static final String NAVIGATION_PATH = "data/html/CommunityBoard/Custom/navigation.html";
	private static final String CREATE_PROGRESS = "CREATE TABLE IF NOT EXISTS account_daily_rewards (account_name VARCHAR(45) NOT NULL, free_cycle_day TINYINT UNSIGNED NOT NULL DEFAULT 1, premium_cycle_day TINYINT UNSIGNED NOT NULL DEFAULT 1, last_free_claim_date DATE NULL, last_premium_claim_date DATE NULL, online_minutes_date DATE NULL, current_online_minutes INT UNSIGNED NOT NULL DEFAULT 0, total_free_claims INT UNSIGNED NOT NULL DEFAULT 0, total_premium_claims INT UNSIGNED NOT NULL DEFAULT 0, reward_season VARCHAR(32) NOT NULL DEFAULT 'season1', last_character_id INT NOT NULL DEFAULT 0, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (account_name)) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String CREATE_HISTORY = "CREATE TABLE IF NOT EXISTS account_daily_reward_history (id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, account_name VARCHAR(45) NOT NULL, character_id INT NOT NULL, reward_track VARCHAR(8) NOT NULL, reward_day TINYINT UNSIGNED NOT NULL, reward_id VARCHAR(64) NOT NULL, item_id INT NOT NULL, item_count BIGINT NOT NULL, claim_date DATE NOT NULL, claim_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, ip_address VARCHAR(45) NULL, status VARCHAR(16) NOT NULL, PRIMARY KEY (id), KEY account_date (account_name, claim_date)) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String INSERT_ACCOUNT = "INSERT IGNORE INTO account_daily_rewards (account_name, online_minutes_date, reward_season) VALUES (?, ?, ?)";
	private static final String SELECT_PROGRESS = "SELECT free_cycle_day,premium_cycle_day,last_free_claim_date,last_premium_claim_date,online_minutes_date,current_online_minutes,total_free_claims,total_premium_claims FROM account_daily_rewards WHERE account_name=?";
	private static final String SELECT_PROGRESS_LOCK = SELECT_PROGRESS + " FOR UPDATE";
	private static final String[] COMMANDS = { "_bbsdailyrewards", "_bbsdailyrewards_free", "_bbsdailyrewards_premium", "_bbsdailyrewards_info", "_bbsdailyrewards_claim_free", "_bbsdailyrewards_claim_premium", "_bbsdailyrewards_claim_all" };
	private static final Map<String, ReentrantLock> ACCOUNT_LOCKS = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> SESSION_BASE_MINUTES = new ConcurrentHashMap<>();
	private static volatile Settings SETTINGS = new Settings();
	private static volatile Map<Track, Map<Integer, Reward>> REWARDS = new HashMap<>();

	public DailyRewardsBoard()
	{
		loadMasterSwitch();
		reload();
		if (MASTER_ENABLED)
		{
			initTables();
			Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_LOGIN, (OnPlayerLogin event) -> onLogin(event.getPlayer()), this));
			Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_LOGOUT, (OnPlayerLogout event) -> onLogout(event.getPlayer()), this));
		}
	}

	@Override
	public String[] getCommandList()
	{
		return COMMANDS;
	}

	@Override
	public boolean onCommand(String command, Player player)
	{
		if (!MASTER_ENABLED)
		{
			player.sendMessage("Daily Rewards are temporarily unavailable.");
			return true;
		}
		Track displayTrack = Track.FREE;
		int page = -1;
		int selectedDay = 0;
		if (command.equals("_bbsdailyrewards_claim_free"))
		{
			claim(player, Track.FREE);
		}
		else if (command.equals("_bbsdailyrewards_claim_premium"))
		{
			displayTrack = Track.PREMIUM;
			claim(player, Track.PREMIUM);
		}
		else if (command.equals("_bbsdailyrewards_claim_all"))
		{
			claim(player, Track.FREE);
			claim(player, Track.PREMIUM);
		}
		else if (command.startsWith("_bbsdailyrewards_free"))
		{
			displayTrack = Track.FREE;
			page = parsePage(command);
		}
		else if (command.startsWith("_bbsdailyrewards_premium"))
		{
			displayTrack = Track.PREMIUM;
			page = parsePage(command);
		}
		else if (command.equals("_bbsdailyrewards_info"))
		{
			showInformation(player);
			return true;
		}
		show(player, displayTrack, page, selectedDay);
		return true;
	}

	private static int parsePage(String command)
	{
		final int separator = command.indexOf(';');
		if (separator < 0) return 0;
		try { return Math.max(0, Math.min(5, Integer.parseInt(command.substring(separator + 1)))); }
		catch (NumberFormatException e) { return 0; }
	}

	public static synchronized boolean reload()
	{
		loadMasterSwitch();
		final Settings settings = new Settings();
		final Map<Track, Map<Integer, Reward>> rewards = new HashMap<>();
		rewards.put(Track.FREE, new HashMap<>());
		rewards.put(Track.PREMIUM, new HashMap<>());
		try
		{
			final Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(CONFIG_FILE).getDocumentElement();
			settings.enabled = bool(root, "enabled", true);
			settings.minimumLevel = integer(root, "minimumLevel", 80);
			settings.onlineMinutes = integer(root, "onlineMinutes", 20);
			settings.resetHour = integer(root, "resetHour", 0);
			settings.zone = ZoneId.of(root.getAttribute("timeZone"));
			settings.season = value(root, "season", "season1");
			settings.loyaltyItemId = integer(root, "loyaltyItemId", 0);
			final NodeList nodes = root.getElementsByTagName("reward");
			for (int i = 0; i < nodes.getLength(); i++)
			{
				final Element element = (Element) nodes.item(i);
				final Track track = Track.valueOf(element.getAttribute("track").toUpperCase());
				final int day = integer(element, "day", 0);
				if ((day < 1) || (day > 30) || rewards.get(track).containsKey(day))
				{
					throw new IllegalArgumentException("Duplicate or invalid " + track + " day " + day);
				}
				final Reward reward = new Reward(value(element, "id", track.name().toLowerCase() + day), day, value(element, "name", "Daily Reward"), value(element, "shortName", "REWARD"), value(element, "description", "Daily account reward"), value(element, "icon", "icon.etc_adena_i00"));
				final NodeList items = element.getElementsByTagName("item");
				for (int j = 0; j < items.getLength(); j++)
				{
					final Element item = (Element) items.item(j);
					if (item.getParentNode() != element) continue;
					final int configuredItemId = integer(item, "id", 0);
					final int itemId = configuredItemId == -1 ? settings.loyaltyItemId : configuredItemId;
					final long configuredCount = Long.parseLong(item.getAttribute("count"));
					if ((itemId > 0) && (configuredCount > 0) && (configuredCount <= Integer.MAX_VALUE))
					{
						reward.items.add(new RewardItem(itemId, (int) configuredCount));
					}
				}
				final NodeList randomGroups = element.getElementsByTagName("randomGroup");
				if (randomGroups.getLength() > 0)
				{
					final Element group = (Element) randomGroups.item(0);
					reward.randomCount = integer(group, "count", 1);
					final NodeList candidates = group.getElementsByTagName("item");
					for (int j = 0; j < candidates.getLength(); j++)
					{
						final Element candidate = (Element) candidates.item(j);
						final int itemId = integer(candidate, "id", 0);
						final int weight = integer(candidate, "weight", 0);
						if ((itemId > 0) && (weight > 0)) reward.randomItems.add(new WeightedItem(itemId, weight));
					}
				}
				if (reward.items.isEmpty() && reward.randomItems.isEmpty())
				{
					throw new IllegalArgumentException("Reward " + reward.id + " has no valid items");
				}
				rewards.get(track).put(day, reward);
			}
			for (Track track : Track.values())
			{
				if (rewards.get(track).size() != 30)
				{
					throw new IllegalArgumentException(track + " must define exactly 30 days");
				}
			}
			SETTINGS = settings;
			REWARDS = rewards;
			LOGGER.info("[PREMIUM_REWARDS] configuration reloaded");
			return true;
		}
		catch (Exception e)
		{
			LOGGER.warning("[PREMIUM_REWARDS] configuration error: " + e.getMessage());
			return false;
		}
	}

	public static void setEnabled(boolean enabled)
	{
		SETTINGS.enabled = enabled;
	}

	private void claim(Player player, Track track)
	{
		if (!SETTINGS.enabled)
		{
			player.sendMessage("Daily Rewards are currently disabled.");
			return;
		}
		final String account = player.getAccountName();
		final ReentrantLock lock = ACCOUNT_LOCKS.computeIfAbsent(account.toLowerCase(), k -> new ReentrantLock());
		if (!lock.tryLock())
		{
			player.sendMessage("A reward request for this account is already being processed.");
			return;
		}
		try (Connection con = DatabaseFactory.getConnection())
		{
			con.setAutoCommit(false);
			ensureAccount(con, account);
			final Progress progress = load(con, account, true);
			final LocalDate today = rewardDate();
			resetOnlineDate(progress, today);
			progress.onlineMinutes = currentOnlineMinutes(player, progress);
			if (player.getLevel() < SETTINGS.minimumLevel)
			{
				player.sendMessage("Level " + SETTINGS.minimumLevel + " is required to claim this reward.");
				con.rollback();
				return;
			}
			if (progress.onlineMinutes < SETTINGS.onlineMinutes)
			{
				player.sendMessage("You need " + SETTINGS.onlineMinutes + " online minutes today.");
				con.rollback();
				return;
			}
			if ((track == Track.PREMIUM) && !player.hasPremiumStatus())
			{
				player.sendMessage("An active Premium status is required to claim this reward.");
				con.rollback();
				return;
			}
			if (today.equals(track == Track.FREE ? progress.lastFree : progress.lastPremium))
			{
				player.sendMessage("This account already claimed that reward today.");
				con.rollback();
				return;
			}
			final int day = track == Track.FREE ? progress.freeDay : progress.premiumDay;
			final Reward configuredReward = REWARDS.get(track).get(day);
			final Reward reward = resolveReward(configuredReward);
			if ((reward == null) || !canReceive(player, reward))
			{
				con.rollback();
				return;
			}
			updateProgress(con, player, track, progress, today);
			insertHistory(con, player, track, reward, today, "DELIVERED");
			final List<RewardItem> delivered = new ArrayList<>();
			try
			{
				for (RewardItem entry : reward.items)
				{
					final Item item = player.addItem(ItemProcessType.REWARD, entry.itemId, entry.count, player, true);
					if (item == null)
					{
						throw new IllegalStateException("addItem returned null for " + entry.itemId);
					}
					delivered.add(entry);
				}
				con.commit();
				player.sendMessage("Daily reward successfully claimed.");
				LOGGER.info("[PREMIUM_REWARDS] account=" + account + " character=" + player.getName() + " track=" + track + " day=" + day + " result=SUCCESS");
			}
			catch (Exception e)
			{
				con.rollback();
				for (RewardItem entry : delivered)
				{
					player.destroyItemByItemId(ItemProcessType.DESTROY, entry.itemId, entry.count, player, false);
				}
				player.sendMessage("The reward could not be delivered. Nothing was claimed.");
				LOGGER.warning("[PREMIUM_REWARDS] account=" + account + " track=" + track + " result=FAILED reason=" + e.getMessage());
			}
		}
		catch (SQLException e)
		{
			player.sendMessage("The reward service is temporarily unavailable.");
			LOGGER.warning("[PREMIUM_REWARDS] account=" + account + " result=FAILED reason=" + e.getMessage());
		}
		finally
		{
			lock.unlock();
		}
	}

	private boolean canReceive(Player player, Reward reward)
	{
		int slots = 0;
		long weight = 0;
		for (RewardItem entry : reward.items)
		{
			final ItemTemplate template = ItemData.getInstance().getTemplate(entry.itemId);
			if (template == null)
			{
				player.sendMessage("This reward is not configured correctly.");
				return false;
			}
			if (!template.isStackable() || (player.getInventory().getItemByItemId(entry.itemId) == null))
			{
				slots += template.isStackable() ? 1 : (int) Math.min(Integer.MAX_VALUE, entry.count);
			}
			weight += template.getWeight() * entry.count;
		}
		if (!player.getInventory().validateCapacity(slots) || !player.getInventory().validateWeight(weight))
		{
			player.sendMessage("Free inventory space before claiming this reward.");
			return false;
		}
		return true;
	}

	private static Reward resolveReward(Reward configured)
	{
		if ((configured == null) || configured.randomItems.isEmpty()) return configured;
		final Reward resolved = new Reward(configured.id, configured.day, configured.name, configured.shortName, configured.description, configured.icon);
		resolved.items.addAll(configured.items);
		int totalWeight = 0;
		for (WeightedItem candidate : configured.randomItems) totalWeight += candidate.weight;
		int roll = ThreadLocalRandom.current().nextInt(totalWeight);
		for (WeightedItem candidate : configured.randomItems)
		{
			roll -= candidate.weight;
			if (roll < 0)
			{
				resolved.items.add(new RewardItem(candidate.itemId, configured.randomCount));
				break;
			}
		}
		return resolved;
	}

	private void show(Player player, Track track, int requestedPage, int requestedDay)
	{
		Progress progress = new Progress();
		try (Connection con = DatabaseFactory.getConnection())
		{
			ensureAccount(con, player.getAccountName());
			progress = load(con, player.getAccountName(), false);
			resetOnlineDate(progress, rewardDate());
			progress.onlineMinutes = currentOnlineMinutes(player, progress);
		}
		catch (SQLException e)
		{
			LOGGER.warning("[PREMIUM_REWARDS] display failed: " + e.getMessage());
		}
		final boolean premium = player.hasPremiumStatus();
		final long expiration = PremiumManager.getInstance().getPremiumExpiration(player.getAccountName());
		final long remainingMillis = Math.max(0, expiration - System.currentTimeMillis());
		final int currentDay = track == Track.FREE ? progress.freeDay : progress.premiumDay;
		final int page = requestedPage < 0 ? (currentDay - 1) / 5 : Math.max(0, Math.min(5, requestedPage));
		final LocalDate lastClaim = track == Track.FREE ? progress.lastFree : progress.lastPremium;
		final int firstDay = (page * 5) + 1;
		final int lastDay = Math.min(30, firstDay + 4);
		final int selectedDay = currentDay;
		final StringBuilder html = new StringBuilder(12000);
		html.append("<html><body><table width=20 bgcolor=\"000000\"><tr><td valign=top>");
		html.append(loadNavigation()).append("</td><td valign=top><center>");
		html.append("<table width=500 bgcolor=\"000000\"><tr><td height=10></td></tr><tr><td align=center><font color=F0D58A>MEGARAX DAILY REWARDS</font><br1><font color=C0C0C0>ENTER THE WORLD. BUILD YOUR LEGACY.</font></td></tr><tr><td height=10></td></tr></table>");
		html.append("<img src=\"L2UI.SquareGray\" width=500 height=1>");
		html.append("<table width=500 bgcolor=\"000000\"><tr><td width=20></td><td width=220><font color=LEVEL>").append(player.getName()).append("</font><br1><font color=CDB67F>Level: </font><font color=E0E0E0>").append(player.getLevel()).append("</font><br1><font color=CDB67F>Premium: </font><font color=").append(premium ? "66CC88>ACTIVE" : "DD6666>EXPIRED").append("</font><br1><font color=CDB67F>Remaining: </font><font color=E0E0E0>").append(formatRemaining(remainingMillis)).append("</font></td>");
		html.append("<td width=240 align=right><font color=CDB67F>Days Attended: </font><font color=LEVEL>").append(track == Track.FREE ? progress.totalFree : progress.totalPremium).append("</font><br1><font color=CDB67F>Online Today: </font><font color=E0E0E0>").append(progress.onlineMinutes).append("/").append(SETTINGS.onlineMinutes).append(" min</font><br1><font color=CDB67F>Next Reset: </font><font color=E0E0E0>").append(nextReset()).append("</font></td><td width=20></td></tr></table><br1>");
		html.append("<table width=500 bgcolor=\"000000\"><tr><td width=145></td><td align=center><button value=\"FREE\" action=\"bypass _bbsdailyrewards_free;").append(page).append("\" width=105 height=24 back=\"L2UI_CH3.Button.bigbutton2_down\" fore=\"").append(track == Track.FREE ? "L2UI_CH3.Button.bigbutton2_down" : "L2UI_CH3.Button.bigbutton2").append("\"></td>");
		html.append("<td align=center><button value=\"PREMIUM\" action=\"bypass _bbsdailyrewards_premium;").append(page).append("\" width=105 height=24 back=\"L2UI_CH3.Button.bigbutton2_down\" fore=\"").append(track == Track.PREMIUM ? "L2UI_CH3.Button.bigbutton2_down" : "L2UI_CH3.Button.bigbutton2").append("\"></td><td width=145></td></tr></table><br1>");
		if ((track == Track.PREMIUM) && !premium)
		{
			html.append("<table width=500 bgcolor=\"000000\"><tr><td height=5></td></tr><tr><td align=center><font color=F0D58A>PREMIUM REQUIRED</font><br1><font color=C0C0C0>Activate Premium to unlock this reward track.</font><br1><button value=\"ACTIVATE\" action=\"bypass _bbstop;premium/main.html\" width=100 height=24 back=\"L2UI_CH3.Button.bigbutton2_down\" fore=\"L2UI_CH3.Button.bigbutton2\"></td></tr><tr><td height=5></td></tr></table><br1>");
		}
		appendCards(html, player, progress, track, page, selectedDay, premium);
		appendSelectedReward(html, player, progress, track, page, selectedDay, premium, lastClaim);
		final int attended = Math.min(30, track == Track.FREE ? progress.totalFree : progress.totalPremium);
		final int filled = attended * 10;
		html.append("<br1><table width=500 bgcolor=\"000000\"><tr><td width=180><font color=CDB67F>Attendance Progress: </font><font color=LEVEL>").append(attended).append(" / 30</font></td><td width=300><table width=300 bgcolor=\"000000\"><tr><td width=").append(filled).append(" height=8 bgcolor=\"A88438\"></td><td width=").append(300 - filled).append(" height=8 bgcolor=\"000000\"></td></tr></table></td><td width=20></td></tr></table>");
		if (!premium)
		{
			html.append("<br1>");
		}
		html.append("<table width=500 bgcolor=\"000000\"><tr><td height=6></td></tr><tr><td align=center><font color=AFAFAF>One reward per account each day.<br1>Missing a day does not reset progress. Premium progress is preserved.</font></td></tr><tr><td align=center><table width=240><tr><td width=120 align=center><button value=\"INFO\" action=\"bypass _bbsdailyrewards_info\" width=90 height=24 back=\"L2UI_CH3.Button.bigbutton2_down\" fore=\"L2UI_CH3.Button.bigbutton2\"></td><td width=120 align=center><button value=\"BACK\" action=\"bypass _bbstop;home.html\" width=90 height=24 back=\"L2UI_CH3.Button.bigbutton2_down\" fore=\"L2UI_CH3.Button.bigbutton2\"></td></tr></table></td></tr><tr><td height=6></td></tr></table>");
		html.append("</center></td></tr></table></body></html>");
		CommunityBoardHandler.separateAndSend(html.toString(), player);
	}

	private void appendCards(StringBuilder html, Player player, Progress progress, Track track, int page, int selectedDay, boolean premium)
	{
		final int first = (page * 5) + 1;
		final int last = Math.min(30, first + 4);
		html.append("<table width=500 bgcolor=\"000000\"><tr><td width=5></td>");
		for (int day = first; day <= last; day++)
		{
			final Reward reward = REWARDS.get(track).get(day);
			final String state = visualState(player, progress, track, day, premium);
			final boolean special = !reward.randomItems.isEmpty() || (day == 7) || (day == 15) || (day == 21) || (day == 30);
			final String color = "000000";
			html.append("<td width=98 align=center bgcolor=\"").append(color).append("\"><font color=").append(special ? "F0D58A" : "D8E0EA").append(">DAY ").append(day).append(reward.randomItems.isEmpty() ? "" : " BONUS").append("</font><br1><img src=\"").append(reward.icon).append("\" width=32 height=32><br1><font color=CDB67F>").append(reward.shortName).append("</font><br1><font color=").append(stateColor(state)).append(">").append(shortState(state)).append("</font></td>");
		}
		for (int empty = last - first + 1; empty < 5; empty++) html.append("<td width=98 bgcolor=\"000000\"></td>");
		html.append("<td width=5></td></tr></table><table width=500 bgcolor=\"000000\"><tr><td width=120 align=center>");
		if (page > 0) html.append("<button value=\"PREVIOUS\" action=\"bypass _bbsdailyrewards_").append(track.name().toLowerCase()).append(";").append(page - 1).append("\" width=90 height=22 back=\"L2UI_CH3.Button.bigbutton2_down\" fore=\"L2UI_CH3.Button.bigbutton2\">");
		html.append("</td><td width=260 align=center><font color=C0C0C0>PAGE ").append(page + 1).append(" / 6</font></td><td width=120 align=center>");
		if (page < 5) html.append("<button value=\"NEXT\" action=\"bypass _bbsdailyrewards_").append(track.name().toLowerCase()).append(";").append(page + 1).append("\" width=90 height=22 back=\"L2UI_CH3.Button.bigbutton2_down\" fore=\"L2UI_CH3.Button.bigbutton2\">");
		html.append("</td></tr></table>");
	}

	private void appendSelectedReward(StringBuilder html, Player player, Progress progress, Track track, int page, int day, boolean premium, LocalDate lastClaim)
	{
		final Reward reward = REWARDS.get(track).get(day);
		final String state = visualState(player, progress, track, day, premium);
		html.append("<table width=500 bgcolor=\"000000\"><tr><td align=center><font color=F0D58A>CURRENT REWARD</font></td></tr></table><table width=500 bgcolor=\"000000\"><tr><td width=15></td><td width=64 align=center><img src=\"").append(reward.icon).append("\" width=48 height=48></td><td width=246><font color=F0D58A>DAY ").append(day).append(" ").append(track.name()).append(" REWARD</font><br1><font color=CDB67F>").append(reward.name).append("</font><br1>");
		for (RewardItem entry : reward.items)
		{
			final ItemTemplate template = ItemData.getInstance().getTemplate(entry.itemId);
			html.append("<font color=B9C7D8>").append(template == null ? "Item " + entry.itemId : template.getName()).append(" x").append(entry.count).append("</font><br1>");
		}
		if (!reward.randomItems.isEmpty()) html.append("<font color=B9C7D8>One random A-Grade item x").append(reward.randomCount).append("</font><br1>");
		html.append("<font color=C0C0C0>").append(requirementText(state, progress)).append("</font>");
		html.append("</td><td width=160 align=center>");
		final boolean actionable = state.equals("AVAILABLE");
		if (actionable) html.append("<button value=\"CLAIM\" action=\"bypass _bbsdailyrewards_claim_").append(track.name().toLowerCase()).append("\" width=90 height=24 back=\"L2UI_CH3.Button.bigbutton2_down\" fore=\"L2UI_CH3.Button.bigbutton2\">");
		else html.append("<button value=\"LOCKED\" width=90 height=24 back=\"L2UI_CH3.Button.bigbutton2_down\" fore=\"L2UI_CH3.Button.bigbutton2\">");
		html.append("</td><td width=15></td></tr></table>");
	}

	private String visualState(Player player, Progress progress, Track track, int day, boolean premium)
	{
		final int current = track == Track.FREE ? progress.freeDay : progress.premiumDay;
		final LocalDate last = track == Track.FREE ? progress.lastFree : progress.lastPremium;
		if ((track == Track.PREMIUM) && !premium) return "PREMIUM REQUIRED";
		if (day < current) return "CLAIMED";
		if (day != current) return "LOCKED";
		if (rewardDate().equals(last)) return "CLAIMED";
		if (player.getLevel() < SETTINGS.minimumLevel) return "LEVEL REQUIRED";
		if (progress.onlineMinutes < SETTINGS.onlineMinutes) return "ONLINE TIME REQUIRED";
		if (!hasInventoryCapacity(player, REWARDS.get(track).get(day))) return "INVENTORY FULL";
		return "AVAILABLE";
	}

	private boolean hasInventoryCapacity(Player player, Reward reward)
	{
		int slots = 0; long weight = 0;
		for (RewardItem entry : reward.items)
		{
			final ItemTemplate template = ItemData.getInstance().getTemplate(entry.itemId);
			if (template == null) return false;
			if (!template.isStackable() || (player.getInventory().getItemByItemId(entry.itemId) == null)) slots += template.isStackable() ? 1 : entry.count;
			weight += template.getWeight() * (long) entry.count;
		}
		if (!reward.randomItems.isEmpty())
		{
			long maximumWeight = 0;
			for (WeightedItem candidate : reward.randomItems)
			{
				final ItemTemplate template = ItemData.getInstance().getTemplate(candidate.itemId);
				if (template == null) return false;
				maximumWeight = Math.max(maximumWeight, template.getWeight() * (long) reward.randomCount);
			}
			slots += reward.randomCount;
			weight += maximumWeight;
		}
		return player.getInventory().validateCapacity(slots) && player.getInventory().validateWeight(weight);
	}

	private String requirementText(String state, Progress progress)
	{
		if (state.equals("AVAILABLE")) return "Online requirement completed.";
		if (state.equals("ONLINE TIME REQUIRED")) return "Play " + Math.max(1, SETTINGS.onlineMinutes - progress.onlineMinutes) + " more minutes.";
		if (state.equals("LEVEL REQUIRED")) return "Level " + SETTINGS.minimumLevel + " required.";
		if (state.equals("PREMIUM REQUIRED")) return "Active Premium required.";
		if (state.equals("INVENTORY FULL")) return "Free inventory space is required.";
		return state.equals("CLAIMED") ? "Already claimed." : "Complete previous attendance days.";
	}

	private static String shortState(String state)
	{
		if (state.equals("ONLINE TIME REQUIRED")) return "ONLINE";
		if (state.equals("LEVEL REQUIRED")) return "LEVEL";
		if (state.equals("PREMIUM REQUIRED")) return "PREMIUM";
		if (state.equals("INVENTORY FULL")) return "FULL";
		return state;
	}

	private static String stateColor(String state)
	{
		if (state.equals("AVAILABLE")) return "E8C77A";
		if (state.equals("CLAIMED")) return "82B394";
		if (state.equals("PREMIUM REQUIRED")) return "E0B85C";
		if (state.contains("REQUIRED") || state.equals("INVENTORY FULL")) return "E07A7A";
		return "A8ADB5";
	}

	private static String formatRemaining(long millis)
	{
		if (millis <= 0) return "Expired";
		final long minutes = millis / 60000;
		final long days = minutes / 1440;
		final long hours = (minutes % 1440) / 60;
		final long mins = minutes % 60;
		return days > 0 ? days + "d " + hours + "h" : hours > 0 ? hours + "h " + mins + "m" : mins + "m";
	}

	private void showInformation(Player player)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html><body><table width=20 bgcolor=\"000000\"><tr><td valign=top>").append(loadNavigation()).append("</td><td valign=top><center><table width=500 bgcolor=\"000000\"><tr><td height=10></td></tr><tr><td align=center><font color=F0D58A>REWARD INFORMATION</font></td></tr><tr><td height=10></td></tr></table><table width=500 bgcolor=\"000000\"><tr><td width=20></td><td width=460><font color=C0C0C0>One Free and one Premium reward may be claimed per account each reward day.<br1>Each track advances through a personal 30-day cycle.<br1>Missing a day does not reset either track.<br1>Premium progress remains saved while Premium is inactive.<br1>Level ").append(SETTINGS.minimumLevel).append(" and ").append(SETTINGS.onlineMinutes).append(" daily online minutes are required.<br1>The server validates account, track, reward, inventory space and weight.</font></td><td width=20></td></tr></table><br1><button value=\"BACK\" action=\"bypass _bbsdailyrewards\" width=90 height=24 back=\"L2UI_CH3.Button.bigbutton2_down\" fore=\"L2UI_CH3.Button.bigbutton2\"></center></td></tr></table></body></html>");
		CommunityBoardHandler.separateAndSend(html.toString(), player);
	}

	private static void ensureAccount(Connection con, String account) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(INSERT_ACCOUNT))
		{
			ps.setString(1, account);
			ps.setDate(2, Date.valueOf(rewardDate()));
			ps.setString(3, SETTINGS.season);
			ps.executeUpdate();
		}
	}

	private static Progress load(Connection con, String account, boolean lock) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(lock ? SELECT_PROGRESS_LOCK : SELECT_PROGRESS))
		{
			ps.setString(1, account);
			try (ResultSet rs = ps.executeQuery())
			{
				if (!rs.next()) return new Progress();
				final Progress p = new Progress();
				p.freeDay = rs.getInt(1); p.premiumDay = rs.getInt(2);
				p.lastFree = localDate(rs.getDate(3)); p.lastPremium = localDate(rs.getDate(4));
				p.onlineDate = localDate(rs.getDate(5)); p.onlineMinutes = rs.getInt(6);
				p.totalFree = rs.getInt(7); p.totalPremium = rs.getInt(8);
				return p;
			}
		}
	}

	private static void resetOnlineDate(Progress progress, LocalDate today)
	{
		if (!today.equals(progress.onlineDate))
		{
			progress.onlineDate = today;
			progress.onlineMinutes = 0;
		}
	}

	private static void updateProgress(Connection con, Player player, Track track, Progress p, LocalDate today) throws SQLException
	{
		final String sql = track == Track.FREE ? "UPDATE account_daily_rewards SET free_cycle_day=?,last_free_claim_date=?,total_free_claims=total_free_claims+1,online_minutes_date=?,current_online_minutes=?,last_character_id=?,updated_at=CURRENT_TIMESTAMP WHERE account_name=?" : "UPDATE account_daily_rewards SET premium_cycle_day=?,last_premium_claim_date=?,total_premium_claims=total_premium_claims+1,online_minutes_date=?,current_online_minutes=?,last_character_id=?,updated_at=CURRENT_TIMESTAMP WHERE account_name=?";
		final int current = track == Track.FREE ? p.freeDay : p.premiumDay;
		try (PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setInt(1, (current % 30) + 1); ps.setDate(2, Date.valueOf(today)); ps.setDate(3, Date.valueOf(today)); ps.setInt(4, p.onlineMinutes); ps.setInt(5, player.getObjectId()); ps.setString(6, player.getAccountName()); ps.executeUpdate();
		}
	}

	private static void insertHistory(Connection con, Player player, Track track, Reward reward, LocalDate today, String status) throws SQLException
	{
		final String sql = "INSERT INTO account_daily_reward_history (account_name,character_id,reward_track,reward_day,reward_id,item_id,item_count,claim_date,ip_address,status) VALUES (?,?,?,?,?,?,?,?,?,?)";
		for (RewardItem item : reward.items)
		{
			try (PreparedStatement ps = con.prepareStatement(sql))
			{
				ps.setString(1, player.getAccountName()); ps.setInt(2, player.getObjectId()); ps.setString(3, track.name()); ps.setInt(4, reward.day); ps.setString(5, reward.id); ps.setInt(6, item.itemId); ps.setLong(7, item.count); ps.setDate(8, Date.valueOf(today)); ps.setString(9, null); ps.setString(10, status); ps.executeUpdate();
			}
		}
	}

	private static void initTables()
	{
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement one = con.prepareStatement(CREATE_PROGRESS); PreparedStatement two = con.prepareStatement(CREATE_HISTORY))
		{
			one.executeUpdate(); two.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("[PREMIUM_REWARDS] table initialization failed: " + e.getMessage());
		}
	}

	private static void onLogin(Player player)
	{
		if ((player == null) || !MASTER_ENABLED || !SETTINGS.enabled) return;
		try (Connection con = DatabaseFactory.getConnection())
		{
			ensureAccount(con, player.getAccountName());
			final Progress progress = load(con, player.getAccountName(), false);
			resetOnlineDate(progress, rewardDate());
			SESSION_BASE_MINUTES.put(player.getObjectId(), progress.onlineMinutes);
			if (!rewardDate().equals(progress.lastFree) || (player.hasPremiumStatus() && !rewardDate().equals(progress.lastPremium)))
			{
				player.sendMessage("Your Daily Reward is waiting. Open the Community Board to claim it.");
			}
			final int remaining = Math.max(0, SETTINGS.onlineMinutes - progress.onlineMinutes);
			if (remaining > 0)
			{
				ThreadPool.schedule(() ->
				{
					if (player.isOnline())
					{
						player.sendMessage("Your Daily Reward is now available!");
						if (player.hasPremiumStatus()) player.sendMessage("Your Premium Daily Reward is now available!");
					}
				}, remaining * 60000L);
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("[PREMIUM_REWARDS] login load failed: " + e.getMessage());
		}
	}

	private static void onLogout(Player player)
	{
		if ((player == null) || !MASTER_ENABLED) return;
		final Integer base = SESSION_BASE_MINUTES.remove(player.getObjectId());
		if (base == null) return;
		final int minutes = Math.max(base, base + (int) (player.getUptime() / 60000L));
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement("UPDATE account_daily_rewards SET online_minutes_date=?,current_online_minutes=?,last_character_id=?,updated_at=CURRENT_TIMESTAMP WHERE account_name=?"))
		{
			ps.setDate(1, Date.valueOf(rewardDate())); ps.setInt(2, minutes); ps.setInt(3, player.getObjectId()); ps.setString(4, player.getAccountName()); ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.warning("[PREMIUM_REWARDS] logout save failed: " + e.getMessage());
		}
	}

	private static void loadMasterSwitch()
	{
		final Properties properties = new Properties();
		try (InputStream input = new FileInputStream(MASTER_CONFIG_FILE))
		{
			properties.load(input);
			MASTER_ENABLED = Boolean.parseBoolean(properties.getProperty("DailyRewardsEnabled", "false").trim());
		}
		catch (Exception e)
		{
			MASTER_ENABLED = false;
			LOGGER.warning("[PREMIUM_REWARDS] Could not load master switch; Daily Rewards remain disabled.");
		}
	}

	private static int currentOnlineMinutes(Player player, Progress progress)
	{
		final int base = SESSION_BASE_MINUTES.getOrDefault(player.getObjectId(), progress.onlineMinutes);
		return Math.max(progress.onlineMinutes, base + (int) (player.getUptime() / 60000L));
	}

	private static LocalDate rewardDate()
	{
		return ZonedDateTime.now(SETTINGS.zone).minusHours(SETTINGS.resetHour).toLocalDate();
	}

	private static String nextReset()
	{
		final ZonedDateTime now = ZonedDateTime.now(SETTINGS.zone);
		ZonedDateTime reset = now.toLocalDate().atTime(SETTINGS.resetHour, 0).atZone(SETTINGS.zone);
		if (!reset.isAfter(now)) reset = reset.plusDays(1);
		final Duration duration = Duration.between(now, reset);
		return duration.toHours() + "h " + (duration.toMinutes() % 60) + "m";
	}

	private String loadNavigation()
	{
		try { return new String(java.nio.file.Files.readAllBytes(new File(NAVIGATION_PATH).toPath()), java.nio.charset.StandardCharsets.UTF_8); }
		catch (Exception e) { return ""; }
	}

	private static LocalDate localDate(Date date) { return date == null ? null : date.toLocalDate(); }
	private static String value(Element e, String key, String fallback) { final String value = e.getAttribute(key); return value == null || value.isEmpty() ? fallback : value; }
	private static int integer(Element e, String key, int fallback) { final String value = e.getAttribute(key); return value == null || value.isEmpty() ? fallback : Integer.parseInt(value); }
	private static boolean bool(Element e, String key, boolean fallback) { final String value = e.getAttribute(key); return value == null || value.isEmpty() ? fallback : Boolean.parseBoolean(value); }

	private enum Track { FREE, PREMIUM }
	private static class Settings { boolean enabled = false; int minimumLevel = 80; int onlineMinutes = 20; int resetHour = 0; int loyaltyItemId = 0; ZoneId zone = ZoneId.systemDefault(); String season = "season1"; }
	private static class Reward { final String id; final int day; final String name; final String shortName; final String description; final String icon; final List<RewardItem> items = new ArrayList<>(); final List<WeightedItem> randomItems = new ArrayList<>(); int randomCount = 1; Reward(String id, int day, String name, String shortName, String description, String icon) { this.id=id; this.day=day; this.name=name; this.shortName=shortName; this.description=description; this.icon=icon; } }
	private static class RewardItem { final int itemId; final int count; RewardItem(int itemId, int count) { this.itemId=itemId; this.count=count; } }
	private static class WeightedItem { final int itemId; final int weight; WeightedItem(int itemId, int weight) { this.itemId=itemId; this.weight=weight; } }
	private static class Progress { int freeDay=1; int premiumDay=1; int onlineMinutes=20; int totalFree; int totalPremium; LocalDate lastFree; LocalDate lastPremium; LocalDate onlineDate=rewardDate(); }
}
