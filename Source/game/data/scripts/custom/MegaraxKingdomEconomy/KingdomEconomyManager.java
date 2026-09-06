package custom.MegaraxKingdomEconomy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Attackable;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;

public final class KingdomEconomyManager
{
	private static final Logger LOGGER = Logger.getLogger(KingdomEconomyManager.class.getName());
	private static final KingdomEconomyManager INSTANCE = new KingdomEconomyManager();
	private static final String LOG_PREFIX = "[KES] ";
	private static final long MESSAGE_COOLDOWN_MS = 5000;
	
	private static final String[] DDL =
	{
		"CREATE TABLE IF NOT EXISTS kingdom_treasury (castle_id INT NOT NULL, clan_id INT NOT NULL DEFAULT 0, balance BIGINT NOT NULL DEFAULT 0, total_income BIGINT NOT NULL DEFAULT 0, today_income BIGINT NOT NULL DEFAULT 0, weekly_income BIGINT NOT NULL DEFAULT 0, monthly_income BIGINT NOT NULL DEFAULT 0, total_spent BIGINT NOT NULL DEFAULT 0, level INT NOT NULL DEFAULT 1, payroll_percent INT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (castle_id), KEY idx_kingdom_treasury_clan (clan_id), KEY idx_kingdom_treasury_balance (balance))",
		"CREATE TABLE IF NOT EXISTS kingdom_contribution (char_id INT NOT NULL, castle_id INT NOT NULL, clan_id INT NOT NULL DEFAULT 0, points BIGINT NOT NULL DEFAULT 0, lifetime_points BIGINT NOT NULL DEFAULT 0, daily_rax INT NOT NULL DEFAULT 0, weekly_rax INT NOT NULL DEFAULT 0, day_key INT NOT NULL DEFAULT 0, week_key INT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (char_id, castle_id), KEY idx_kingdom_contribution_castle (castle_id), KEY idx_kingdom_contribution_clan (clan_id), KEY idx_kingdom_contribution_points (points))",
		"CREATE TABLE IF NOT EXISTS kingdom_contribution_history (id BIGINT NOT NULL AUTO_INCREMENT, char_id INT NOT NULL, char_name VARCHAR(45) NOT NULL DEFAULT '', clan_id INT NOT NULL DEFAULT 0, castle_id INT NOT NULL, territory_id VARCHAR(64) NOT NULL DEFAULT '', activity_type VARCHAR(48) NOT NULL, points INT NOT NULL DEFAULT 0, adena_tax BIGINT NOT NULL DEFAULT 0, rax_reward INT NOT NULL DEFAULT 0, target_name VARCHAR(45) NOT NULL DEFAULT '', metadata VARCHAR(255) NOT NULL DEFAULT '', created_at BIGINT NOT NULL, PRIMARY KEY (id), KEY idx_kch_char (char_id), KEY idx_kch_castle (castle_id), KEY idx_kch_clan (clan_id), KEY idx_kch_created (created_at))",
		"CREATE TABLE IF NOT EXISTS kingdom_payroll (id BIGINT NOT NULL AUTO_INCREMENT, castle_id INT NOT NULL, clan_id INT NOT NULL, char_id INT NOT NULL, char_name VARCHAR(45) NOT NULL DEFAULT '', contribution_points BIGINT NOT NULL DEFAULT 0, amount BIGINT NOT NULL DEFAULT 0, cycle_key VARCHAR(24) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PAID', created_at BIGINT NOT NULL, PRIMARY KEY (id), KEY idx_kingdom_payroll_castle (castle_id), KEY idx_kingdom_payroll_clan (clan_id), KEY idx_kingdom_payroll_cycle (cycle_key))",
		"CREATE TABLE IF NOT EXISTS kingdom_levels (castle_id INT NOT NULL, level INT NOT NULL DEFAULT 1, upgraded_by INT NOT NULL DEFAULT 0, upgraded_at BIGINT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (castle_id))",
		"CREATE TABLE IF NOT EXISTS kingdom_statistics (stat_key VARCHAR(64) NOT NULL, stat_value BIGINT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (stat_key))",
		"CREATE TABLE IF NOT EXISTS kingdom_territory_state (territory_id VARCHAR(64) NOT NULL, castle_id INT NOT NULL, activity_score BIGINT NOT NULL DEFAULT 0, last_activity BIGINT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (territory_id), KEY idx_kingdom_territory_castle (castle_id))",
		"CREATE TABLE IF NOT EXISTS kingdom_event_log (id BIGINT NOT NULL AUTO_INCREMENT, castle_id INT NOT NULL, clan_id INT NOT NULL DEFAULT 0, event_type VARCHAR(48) NOT NULL, actor_name VARCHAR(45) NOT NULL DEFAULT '', details VARCHAR(255) NOT NULL DEFAULT '', created_at BIGINT NOT NULL, PRIMARY KEY (id), KEY idx_kingdom_event_castle (castle_id), KEY idx_kingdom_event_clan (clan_id), KEY idx_kingdom_event_created (created_at))",
		"CREATE TABLE IF NOT EXISTS kingdom_citizens (castle_id INT NOT NULL, char_id INT NOT NULL, clan_id INT NOT NULL DEFAULT 0, relation VARCHAR(16) NOT NULL DEFAULT 'NEUTRAL', first_seen BIGINT NOT NULL DEFAULT 0, last_seen BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (castle_id, char_id), KEY idx_kingdom_citizens_clan (clan_id), KEY idx_kingdom_citizens_seen (last_seen))"
	};
	
	private final Map<Integer, KingdomTreasury> _treasuries = new ConcurrentHashMap<>();
	private final Map<Long, KingdomContribution> _contributions = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> _adenaBalances = new ConcurrentHashMap<>();
	private final Map<Integer, String> _playerTerritory = new ConcurrentHashMap<>();
	private final Map<Integer, Long> _lastMessage = new ConcurrentHashMap<>();
	private final Map<String, Long> _pvpCooldowns = new ConcurrentHashMap<>();
	private final Map<String, Long> _territoryActivity = new ConcurrentHashMap<>();
	private final Set<Integer> _guardedPlayers = ConcurrentHashMap.newKeySet();
	private final Queue<HistoryEvent> _historyQueue = new ConcurrentLinkedQueue<>();
	private final ArrayDeque<String[]> _recentEvents = new ArrayDeque<>();
	private volatile boolean _initialized;
	
	private KingdomEconomyManager()
	{
	}
	
	public static KingdomEconomyManager getInstance()
	{
		return INSTANCE;
	}
	
	public synchronized void init()
	{
		if (_initialized)
		{
			return;
		}
		
		KingdomEconomyConfig.load();
		initSchema();
		loadTreasuries();
		for (Kingdom kingdom : KingdomEconomyConfig.getKingdoms())
		{
			_treasuries.computeIfAbsent(kingdom.castleId, KingdomTreasury::new);
		}
		ThreadPool.scheduleAtFixedRate(this::safeFlush, KingdomEconomyConfig.SAVE_INTERVAL_MINUTES * 60000L, KingdomEconomyConfig.SAVE_INTERVAL_MINUTES * 60000L);
		ThreadPool.scheduleAtFixedRate(this::safeTerritoryCheck, 10000L, KingdomEconomyConfig.TERRITORY_CHECK_SECONDS * 1000L);
		_initialized = true;
		LOGGER.info("KingdomEconomy: System initialized.");
	}
	
	public void onLogin(Player player)
	{
		if (player == null)
		{
			return;
		}
		_adenaBalances.put(player.getObjectId(), player.getAdena());
		checkPlayerTerritory(player, true);
	}
	
	public void onLogout(Player player)
	{
		if (player == null)
		{
			return;
		}
		_adenaBalances.remove(player.getObjectId());
		_playerTerritory.remove(player.getObjectId());
		_lastMessage.remove(player.getObjectId());
	}
	
	public void onAdenaAdd(Player player, Item item)
	{
		if (!KingdomEconomyConfig.ENABLED || (player == null) || (item == null) || (item.getId() != KingdomEconomyConfig.ADENA_ITEM_ID))
		{
			return;
		}
		
		final int objectId = player.getObjectId();
		final int currentAdena = player.getAdena();
		if (_guardedPlayers.contains(objectId))
		{
			_adenaBalances.put(objectId, currentAdena);
			return;
		}
		
		final Integer previous = _adenaBalances.putIfAbsent(objectId, currentAdena);
		if (previous == null)
		{
			return;
		}
		
		final int delta = currentAdena - previous.intValue();
		if (delta < KingdomEconomyConfig.MIN_ADENA_DELTA)
		{
			_adenaBalances.put(objectId, currentAdena);
			return;
		}
		
		if (!isEligiblePlayer(player))
		{
			_adenaBalances.put(objectId, currentAdena);
			return;
		}
		
		final KingdomTerritory territory = findTerritory(player);
		if (territory == null)
		{
			_adenaBalances.put(objectId, currentAdena);
			return;
		}
		
		final Kingdom kingdom = KingdomEconomyConfig.getKingdom(territory.castleId);
		if (kingdom == null)
		{
			_adenaBalances.put(objectId, currentAdena);
			return;
		}
		
		final KingdomRelation relation = kingdom.getRelation(player);
		final int taxPercent = kingdom.getTaxPercent(relation);
		final int bonusPercent = kingdom.getAdenaBonusPercent(relation);
		final int tax = safePercent(delta, taxPercent);
		final int bonus = safePercent(delta, bonusPercent);
		
		_guardedPlayers.add(objectId);
		try
		{
			if (tax > 0)
			{
				if (player.reduceAdena(ItemProcessType.FEE, tax, player, true))
				{
					final KingdomTreasury treasury = getTreasury(kingdom.castleId);
					treasury.addIncome(tax, kingdom.getOwnerClanId());
					queueHistory(player, kingdom, territory, "ADENA_TAX", 0, tax, 0, "", relation.name());
					increaseTerritoryActivity(territory, tax);
					if (canNotify(player))
					{
						player.sendMessage(formatMessage(KingdomEconomyConfig.MESSAGE_TAX, kingdom, relation, tax, 0, 0));
					}
				}
			}
			if (bonus > 0)
			{
				player.addAdena(ItemProcessType.REWARD, bonus, player, true);
				queueHistory(player, kingdom, territory, "ADENA_BONUS", 0, 0, 0, "", relation.name() + " bonus " + bonus);
			}
		}
		finally
		{
			_guardedPlayers.remove(objectId);
			_adenaBalances.put(objectId, player.getAdena());
		}
	}
	
	public void onCreatureDeath(Creature attacker, Creature target)
	{
		if (!KingdomEconomyConfig.ENABLED || (attacker == null) || (target == null) || !target.isAttackable())
		{
			return;
		}
		
		final Player player = getActingPlayer(attacker);
		if ((player == null) || !isEligiblePlayer(player))
		{
			return;
		}
		
		if ((player.getLevel() - target.getLevel()) > KingdomEconomyConfig.MAX_MONSTER_LEVEL_GAP)
		{
			queueSuspicious(player, "LOW_LEVEL_MONSTER", "Monster level " + target.getLevel());
			return;
		}
		
		final KingdomTerritory territory = findTerritory(target);
		if ((territory == null) || !territory.contains(player))
		{
			return;
		}
		
		final Kingdom kingdom = KingdomEconomyConfig.getKingdom(territory.castleId);
		if (kingdom == null)
		{
			return;
		}
		
		final Attackable attackable = target.asAttackable();
		final int points = attackable.isRaid() ? KingdomEconomyConfig.RAID_POINTS : attackable.isChampion() ? KingdomEconomyConfig.CHAMPION_POINTS : KingdomEconomyConfig.MONSTER_POINTS;
		if (points <= 0)
		{
			return;
		}
		
		addContribution(player, kingdom, territory, points, attackable.isRaid() ? "RAID" : attackable.isChampion() ? "CHAMPION" : "MONSTER", "", "npcId=" + target.getId());
	}
	
	public void onPvpKill(Player killer, Player target)
	{
		if (!KingdomEconomyConfig.ENABLED || (killer == null) || (target == null) || (killer == target) || !isEligiblePlayer(killer))
		{
			return;
		}
		
		final KingdomTerritory territory = findTerritory(target);
		if ((territory == null) || !territory.contains(killer))
		{
			return;
		}
		
		final Kingdom kingdom = KingdomEconomyConfig.getKingdom(territory.castleId);
		if (kingdom == null)
		{
			return;
		}
		
		final String cooldownKey = killer.getObjectId() + ":" + target.getObjectId();
		final long now = System.currentTimeMillis();
		final Long blockedUntil = _pvpCooldowns.get(cooldownKey);
		if ((blockedUntil != null) && (blockedUntil.longValue() > now))
		{
			queueSuspicious(killer, "REPEATED_PVP_TARGET", target.getName());
			return;
		}
		
		if (KingdomEconomyConfig.BLOCK_SAME_IP_PVP && safeIp(killer).equals(safeIp(target)) && !safeIp(killer).isEmpty())
		{
			queueSuspicious(killer, "SAME_IP_PVP", target.getName());
			return;
		}
		if (KingdomEconomyConfig.BLOCK_SAME_HWID_PVP && safeHwid(killer).equals(safeHwid(target)) && !safeHwid(killer).isEmpty())
		{
			queueSuspicious(killer, "SAME_HWID_PVP", target.getName());
			return;
		}
		
		_pvpCooldowns.put(cooldownKey, now + (KingdomEconomyConfig.PVP_SAME_TARGET_COOLDOWN_MINUTES * 60000L));
		final KingdomRelation relation = kingdom.getRelation(killer);
		final int points = (relation == KingdomRelation.RULER) && killer.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.SIEGE) ? KingdomEconomyConfig.CASTLE_DEFENSE_POINTS : KingdomEconomyConfig.ENEMY_PVP_POINTS;
		addContribution(killer, kingdom, territory, points, "PVP", target.getName(), "relation=" + relation.name());
	}
	
	public void checkOnlineTerritories()
	{
		for (Player player : World.getInstance().getPlayers())
		{
			checkPlayerTerritory(player, false);
		}
	}
	
	public void checkPlayerTerritory(Player player, boolean force)
	{
		if ((player == null) || !player.isOnline() || player.isInOfflineMode())
		{
			return;
		}
		
		final KingdomTerritory territory = findTerritory(player);
		final String newId = territory == null ? "" : territory.id;
		final String previousId = _playerTerritory.put(player.getObjectId(), newId);
		if (!force && ((previousId == null) || previousId.equals(newId)))
		{
			return;
		}
		if (territory == null)
		{
			return;
		}
		
		final Kingdom kingdom = KingdomEconomyConfig.getKingdom(territory.castleId);
		if (kingdom == null)
		{
			return;
		}
		
		final KingdomRelation relation = kingdom.getRelation(player);
		final String message = relation == KingdomRelation.RULER ? KingdomEconomyConfig.MESSAGE_ENTER_RULER : KingdomEconomyConfig.MESSAGE_ENTER_NEUTRAL;
		player.sendMessage(formatMessage(message, kingdom, relation, 0, 0, 0));
		queueCitizen(player, kingdom, relation);
	}
	
	public void addContribution(Player player, Kingdom kingdom, KingdomTerritory territory, int points, String reason, String targetName, String metadata)
	{
		if ((player == null) || (kingdom == null) || (territory == null) || (points <= 0))
		{
			return;
		}
		
		final int dayKey = KingdomEconomyUtil.dayKey();
		final int weekKey = KingdomEconomyUtil.weekKey();
		final KingdomContribution contribution = getContribution(player.getObjectId(), kingdom.castleId);
		contribution.addPoints(points, player.getClanId(), dayKey, weekKey);
		queueHistory(player, kingdom, territory, reason, points, 0, 0, targetName, metadata);
		increaseTerritoryActivity(territory, points);
		if (canNotify(player))
		{
			player.sendMessage(formatMessage(KingdomEconomyConfig.MESSAGE_CONTRIBUTION, kingdom, kingdom.getRelation(player), 0, points, 0));
		}
		processRaxRewards(player, kingdom, territory, contribution);
	}
	
	public void processRaxRewards(Player player, Kingdom kingdom, KingdomTerritory territory, KingdomContribution contribution)
	{
		final int dayKey = KingdomEconomyUtil.dayKey();
		final int weekKey = KingdomEconomyUtil.weekKey();
		contribution.roll(dayKey, weekKey);
		
		int rewards = (int) Math.min(Integer.MAX_VALUE, contribution.points / KingdomEconomyConfig.POINTS_PER_RAX_COIN);
		if (KingdomEconomyConfig.DAILY_RAX_LIMIT > 0)
		{
			rewards = Math.min(rewards, Math.max(0, KingdomEconomyConfig.DAILY_RAX_LIMIT - contribution.dailyRax));
		}
		if (KingdomEconomyConfig.WEEKLY_RAX_LIMIT > 0)
		{
			rewards = Math.min(rewards, Math.max(0, KingdomEconomyConfig.WEEKLY_RAX_LIMIT - contribution.weeklyRax));
		}
		if (rewards <= 0)
		{
			return;
		}
		
		contribution.spendPoints((long) rewards * KingdomEconomyConfig.POINTS_PER_RAX_COIN);
		contribution.addRaxReward(rewards, dayKey, weekKey);
		player.addItem(ItemProcessType.REWARD, KingdomEconomyConfig.RAX_COIN_ITEM_ID, rewards, player, true);
		queueHistory(player, kingdom, territory, "RAX_REWARD", 0, 0, rewards, "", "threshold=" + KingdomEconomyConfig.POINTS_PER_RAX_COIN);
		player.sendMessage(formatMessage(KingdomEconomyConfig.MESSAGE_RAX_REWARD, kingdom, kingdom.getRelation(player), 0, 0, rewards));
	}
	
	public KingdomTerritory findTerritory(Creature creature)
	{
		if (creature == null)
		{
			return null;
		}
		for (KingdomTerritory territory : KingdomEconomyConfig.getTerritories())
		{
			if (territory.contains(creature))
			{
				return territory;
			}
		}
		return null;
	}
	
	public KingdomTreasury getTreasury(int castleId)
	{
		return _treasuries.computeIfAbsent(castleId, KingdomTreasury::new);
	}
	
	public KingdomContribution getContribution(int charId, int castleId)
	{
		final long key = contributionKey(charId, castleId);
		KingdomContribution contribution = _contributions.get(key);
		if (contribution != null)
		{
			return contribution;
		}
		contribution = loadContribution(charId, castleId);
		final KingdomContribution existing = _contributions.putIfAbsent(key, contribution);
		return existing == null ? contribution : existing;
	}
	
	public Collection<KingdomTreasury> getTreasuries()
	{
		return Collections.unmodifiableCollection(_treasuries.values());
	}
	
	public List<KingdomTreasury> getTopTreasuries(int limit)
	{
		final List<KingdomTreasury> result = new ArrayList<>(_treasuries.values());
		result.sort(Comparator.comparingLong((KingdomTreasury treasury) -> treasury.balance).reversed());
		return result.subList(0, Math.min(Math.max(0, limit), result.size()));
	}
	
	public List<ContributionView> getTopContributors(int castleId, int limit)
	{
		final Map<Long, ContributionView> rows = new HashMap<>();
		final String sql = castleId > 0 ? "SELECT char_id, clan_id, castle_id, points, lifetime_points, daily_rax, weekly_rax FROM kingdom_contribution WHERE castle_id=? ORDER BY lifetime_points DESC LIMIT ?" : "SELECT char_id, clan_id, castle_id, points, lifetime_points, daily_rax, weekly_rax FROM kingdom_contribution ORDER BY lifetime_points DESC LIMIT ?";
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(sql))
		{
			if (castleId > 0)
			{
				ps.setInt(1, castleId);
				ps.setInt(2, Math.max(1, limit));
			}
			else
			{
				ps.setInt(1, Math.max(1, limit));
			}
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					final ContributionView view = new ContributionView(rs.getInt("char_id"), rs.getInt("clan_id"), rs.getInt("castle_id"), rs.getLong("points"), rs.getLong("lifetime_points"), rs.getInt("daily_rax"), rs.getInt("weekly_rax"));
					rows.put(contributionKey(view.charId, view.castleId), view);
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "KingdomEconomy: Could not load top contributors.", e);
		}
		
		for (KingdomContribution contribution : _contributions.values())
		{
			if ((castleId <= 0) || (contribution.castleId == castleId))
			{
				rows.put(contributionKey(contribution.charId, contribution.castleId), new ContributionView(contribution.charId, contribution.clanId, contribution.castleId, contribution.points, contribution.lifetimePoints, contribution.dailyRax, contribution.weeklyRax));
			}
		}
		final List<ContributionView> result = new ArrayList<>(rows.values());
		result.sort(Comparator.comparingLong((ContributionView view) -> view.lifetimePoints).reversed());
		if (result.size() > limit)
		{
			return new ArrayList<>(result.subList(0, limit));
		}
		return result;
	}
	
	public List<String[]> getRecentEvents()
	{
		synchronized (_recentEvents)
		{
			return new ArrayList<>(_recentEvents);
		}
	}
	
	public String getTerritoryActivity(String territoryId)
	{
		return KingdomEconomyUtil.format(_territoryActivity.getOrDefault(territoryId, 0L));
	}
	
	public void flush()
	{
		flushTreasuries();
		flushContributions();
		flushTerritories();
		flushHistory();
	}
	
	private void initSchema()
	{
		try (Connection con = DatabaseFactory.getConnection())
		{
			for (String sql : DDL)
			{
				try (PreparedStatement ps = con.prepareStatement(sql))
				{
					ps.executeUpdate();
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "KingdomEconomy: Could not initialize schema.", e);
		}
	}
	
	private void loadTreasuries()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT castle_id, clan_id, balance, total_income, today_income, weekly_income, monthly_income, total_spent, level, payroll_percent FROM kingdom_treasury"))
		{
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					final KingdomTreasury treasury = new KingdomTreasury(rs.getInt("castle_id"));
					treasury.clanId = rs.getInt("clan_id");
					treasury.balance = rs.getLong("balance");
					treasury.totalIncome = rs.getLong("total_income");
					treasury.todayIncome = rs.getLong("today_income");
					treasury.weeklyIncome = rs.getLong("weekly_income");
					treasury.monthlyIncome = rs.getLong("monthly_income");
					treasury.totalSpent = rs.getLong("total_spent");
					treasury.level = rs.getInt("level");
					treasury.payrollPercent = rs.getInt("payroll_percent");
					_treasuries.put(treasury.castleId, treasury);
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "KingdomEconomy: Could not load treasury data.", e);
		}
	}
	
	private KingdomContribution loadContribution(int charId, int castleId)
	{
		final KingdomContribution contribution = new KingdomContribution(charId, castleId);
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT clan_id, points, lifetime_points, daily_rax, weekly_rax, day_key, week_key FROM kingdom_contribution WHERE char_id=? AND castle_id=?"))
		{
			ps.setInt(1, charId);
			ps.setInt(2, castleId);
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					contribution.clanId = rs.getInt("clan_id");
					contribution.points = rs.getLong("points");
					contribution.lifetimePoints = rs.getLong("lifetime_points");
					contribution.dailyRax = rs.getInt("daily_rax");
					contribution.weeklyRax = rs.getInt("weekly_rax");
					contribution.dayKey = rs.getInt("day_key");
					contribution.weekKey = rs.getInt("week_key");
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "KingdomEconomy: Could not load contribution " + charId + "/" + castleId + ".", e);
		}
		return contribution;
	}
	
	private void flushTreasuries()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("INSERT INTO kingdom_treasury (castle_id, clan_id, balance, total_income, today_income, weekly_income, monthly_income, total_spent, level, payroll_percent, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE clan_id=VALUES(clan_id), balance=VALUES(balance), total_income=VALUES(total_income), today_income=VALUES(today_income), weekly_income=VALUES(weekly_income), monthly_income=VALUES(monthly_income), total_spent=VALUES(total_spent), level=VALUES(level), payroll_percent=VALUES(payroll_percent), updated_at=VALUES(updated_at)"))
		{
			final long now = System.currentTimeMillis();
			for (KingdomTreasury treasury : _treasuries.values())
			{
				if (!treasury.dirty)
				{
					continue;
				}
				ps.setInt(1, treasury.castleId);
				ps.setInt(2, treasury.clanId);
				ps.setLong(3, treasury.balance);
				ps.setLong(4, treasury.totalIncome);
				ps.setLong(5, treasury.todayIncome);
				ps.setLong(6, treasury.weeklyIncome);
				ps.setLong(7, treasury.monthlyIncome);
				ps.setLong(8, treasury.totalSpent);
				ps.setInt(9, treasury.level);
				ps.setInt(10, treasury.payrollPercent);
				ps.setLong(11, now);
				ps.addBatch();
				treasury.dirty = false;
			}
			ps.executeBatch();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "KingdomEconomy: Could not flush treasuries.", e);
		}
	}
	
	private void flushContributions()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("INSERT INTO kingdom_contribution (char_id, castle_id, clan_id, points, lifetime_points, daily_rax, weekly_rax, day_key, week_key, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE clan_id=VALUES(clan_id), points=VALUES(points), lifetime_points=VALUES(lifetime_points), daily_rax=VALUES(daily_rax), weekly_rax=VALUES(weekly_rax), day_key=VALUES(day_key), week_key=VALUES(week_key), updated_at=VALUES(updated_at)"))
		{
			final long now = System.currentTimeMillis();
			for (KingdomContribution contribution : _contributions.values())
			{
				if (!contribution.dirty)
				{
					continue;
				}
				ps.setInt(1, contribution.charId);
				ps.setInt(2, contribution.castleId);
				ps.setInt(3, contribution.clanId);
				ps.setLong(4, contribution.points);
				ps.setLong(5, contribution.lifetimePoints);
				ps.setInt(6, contribution.dailyRax);
				ps.setInt(7, contribution.weeklyRax);
				ps.setInt(8, contribution.dayKey);
				ps.setInt(9, contribution.weekKey);
				ps.setLong(10, now);
				ps.addBatch();
				contribution.dirty = false;
			}
			ps.executeBatch();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "KingdomEconomy: Could not flush contributions.", e);
		}
	}
	
	private void flushTerritories()
	{
		if (_territoryActivity.isEmpty())
		{
			return;
		}
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("INSERT INTO kingdom_territory_state (territory_id, castle_id, activity_score, last_activity, updated_at) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE activity_score=activity_score+VALUES(activity_score), last_activity=VALUES(last_activity), updated_at=VALUES(updated_at)"))
		{
			final long now = System.currentTimeMillis();
			final Map<String, Long> snapshot = new HashMap<>(_territoryActivity);
			_territoryActivity.keySet().removeAll(snapshot.keySet());
			for (Map.Entry<String, Long> entry : snapshot.entrySet())
			{
				final KingdomTerritory territory = findTerritoryById(entry.getKey());
				if (territory == null)
				{
					continue;
				}
				ps.setString(1, territory.id);
				ps.setInt(2, territory.castleId);
				ps.setLong(3, entry.getValue());
				ps.setLong(4, now);
				ps.setLong(5, now);
				ps.addBatch();
			}
			ps.executeBatch();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "KingdomEconomy: Could not flush territory state.", e);
		}
	}
	
	private void flushHistory()
	{
		final List<HistoryEvent> events = new ArrayList<>();
		HistoryEvent event;
		while ((event = _historyQueue.poll()) != null)
		{
			events.add(event);
			if (events.size() >= 500)
			{
				break;
			}
		}
		if (events.isEmpty())
		{
			return;
		}
		
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement history = con.prepareStatement("INSERT INTO kingdom_contribution_history (char_id, char_name, clan_id, castle_id, territory_id, activity_type, points, adena_tax, rax_reward, target_name, metadata, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
			PreparedStatement log = con.prepareStatement("INSERT INTO kingdom_event_log (castle_id, clan_id, event_type, actor_name, details, created_at) VALUES (?, ?, ?, ?, ?, ?)"))
		{
			for (HistoryEvent row : events)
			{
				history.setInt(1, row.charId);
				history.setString(2, row.charName);
				history.setInt(3, row.clanId);
				history.setInt(4, row.castleId);
				history.setString(5, row.territoryId);
				history.setString(6, row.type);
				history.setInt(7, row.points);
				history.setLong(8, row.adenaTax);
				history.setInt(9, row.raxReward);
				history.setString(10, row.targetName);
				history.setString(11, row.metadata);
				history.setLong(12, row.createdAt);
				history.addBatch();
				
				log.setInt(1, row.castleId);
				log.setInt(2, row.clanId);
				log.setString(3, row.type);
				log.setString(4, row.charName);
				log.setString(5, row.metadata);
				log.setLong(6, row.createdAt);
				log.addBatch();
			}
			history.executeBatch();
			log.executeBatch();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "KingdomEconomy: Could not flush history.", e);
			_historyQueue.addAll(events);
		}
	}
	
	private void queueHistory(Player player, Kingdom kingdom, KingdomTerritory territory, String type, int points, long adenaTax, int raxReward, String targetName, String metadata)
	{
		final HistoryEvent event = new HistoryEvent();
		event.charId = player.getObjectId();
		event.charName = KingdomEconomyUtil.safe(player.getName(), 45);
		event.clanId = player.getClanId();
		event.castleId = kingdom.castleId;
		event.territoryId = territory == null ? "" : KingdomEconomyUtil.safe(territory.id, 64);
		event.type = KingdomEconomyUtil.safe(type, 48);
		event.points = Math.max(0, points);
		event.adenaTax = Math.max(0, adenaTax);
		event.raxReward = Math.max(0, raxReward);
		event.targetName = KingdomEconomyUtil.safe(targetName, 45);
		event.metadata = KingdomEconomyUtil.safe(metadata, 255);
		event.createdAt = System.currentTimeMillis();
		_historyQueue.add(event);
		addRecent(event);
	}
	
	private void queueSuspicious(Player player, String type, String details)
	{
		if (player == null)
		{
			return;
		}
		final KingdomTerritory territory = findTerritory(player);
		final Kingdom kingdom = territory == null ? null : KingdomEconomyConfig.getKingdom(territory.castleId);
		if ((territory != null) && (kingdom != null))
		{
			queueHistory(player, kingdom, territory, "SUSPICIOUS_" + type, 0, 0, 0, "", details);
		}
		LOGGER.info(LOG_PREFIX + "SUSPICIOUS_ACTIVITY Character: " + player.getName() + " Type: " + type + " Details: " + details);
	}
	
	private void queueCitizen(Player player, Kingdom kingdom, KingdomRelation relation)
	{
		ThreadPool.execute(() ->
		{
			final long now = System.currentTimeMillis();
			try (Connection con = DatabaseFactory.getConnection();
				PreparedStatement ps = con.prepareStatement("INSERT INTO kingdom_citizens (castle_id, char_id, clan_id, relation, first_seen, last_seen) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE clan_id=VALUES(clan_id), relation=VALUES(relation), last_seen=VALUES(last_seen)"))
			{
				ps.setInt(1, kingdom.castleId);
				ps.setInt(2, player.getObjectId());
				ps.setInt(3, player.getClanId());
				ps.setString(4, relation.name());
				ps.setLong(5, now);
				ps.setLong(6, now);
				ps.executeUpdate();
			}
			catch (SQLException e)
			{
				LOGGER.log(Level.WARNING, "KingdomEconomy: Could not update citizen row.", e);
			}
		});
	}
	
	private boolean isEligiblePlayer(Player player)
	{
		return (player != null) && player.isOnline() && !player.isInOfflineMode() && !player.isInStoreMode() && !player.isDead() && !player.isAlikeDead() && !player.isAutoPlaying() && (player.getLevel() >= KingdomEconomyConfig.MIN_PLAYER_LEVEL);
	}
	
	private Player getActingPlayer(Creature attacker)
	{
		if (attacker == null)
		{
			return null;
		}
		if (attacker.isPlayer())
		{
			return attacker.asPlayer();
		}
		if (attacker.isSummon())
		{
			return attacker.asSummon().getOwner();
		}
		return null;
	}
	
	private int safePercent(int amount, int percent)
	{
		if ((amount <= 0) || (percent <= 0))
		{
			return 0;
		}
		return (int) Math.min(Integer.MAX_VALUE, ((long) amount * percent) / 100L);
	}
	
	private String formatMessage(String template, Kingdom kingdom, KingdomRelation relation, long amount, int points, int rax)
	{
		final KingdomTreasury treasury = getTreasury(kingdom.castleId);
		return template.replace("%kingdom%", kingdom.name).replace("%clan%", kingdom.getOwnerName()).replace("%tax%", Integer.toString(kingdom.getTaxPercent(relation))).replace("%bonus%", Integer.toString(kingdom.getAdenaBonusPercent(relation))).replace("%level%", Integer.toString(treasury.level)).replace("%amount%", KingdomEconomyUtil.format(amount)).replace("%points%", Integer.toString(points)).replace("%rax%", Integer.toString(rax)).replace("%prosperity%", Integer.toString(kingdom.prosperity));
	}
	
	private boolean canNotify(Player player)
	{
		final long now = System.currentTimeMillis();
		final Long last = _lastMessage.get(player.getObjectId());
		if ((last != null) && ((now - last.longValue()) < MESSAGE_COOLDOWN_MS))
		{
			return false;
		}
		_lastMessage.put(player.getObjectId(), now);
		return true;
	}
	
	private void increaseTerritoryActivity(KingdomTerritory territory, long amount)
	{
		if ((territory != null) && (amount > 0))
		{
			_territoryActivity.merge(territory.id, amount, Long::sum);
		}
	}
	
	private KingdomTerritory findTerritoryById(String id)
	{
		for (KingdomTerritory territory : KingdomEconomyConfig.getTerritories())
		{
			if (territory.id.equals(id))
			{
				return territory;
			}
		}
		return null;
	}
	
	private void addRecent(HistoryEvent event)
	{
		synchronized (_recentEvents)
		{
			_recentEvents.addFirst(new String[]
			{
				event.type,
				event.charName,
				event.metadata
			});
			while (_recentEvents.size() > 20)
			{
				_recentEvents.removeLast();
			}
		}
	}
	
	private void safeFlush()
	{
		try
		{
			flush();
		}
		catch (Exception e)
		{
			LOGGER.log(Level.WARNING, "KingdomEconomy: Scheduled flush failed.", e);
		}
	}
	
	private void safeTerritoryCheck()
	{
		try
		{
			checkOnlineTerritories();
		}
		catch (Exception e)
		{
			LOGGER.log(Level.WARNING, "KingdomEconomy: Territory check failed.", e);
		}
	}
	
	private long contributionKey(int charId, int castleId)
	{
		return (((long) charId) << 32) ^ (castleId & 0xFFFFFFFFL);
	}
	
	private String safeIp(Player player)
	{
		return (player == null) || (player.getIPAddress() == null) ? "" : player.getIPAddress();
	}
	
	private String safeHwid(Player player)
	{
		if ((player == null) || (player.getClient() == null) || (player.getClient().getHardwareInfo() == null) || (player.getClient().getHardwareInfo().getMacAddress() == null))
		{
			return "";
		}
		return player.getClient().getHardwareInfo().getMacAddress();
	}
	
	public String getClanName(int clanId)
	{
		if (clanId <= 0)
		{
			return "None";
		}
		final Clan clan = ClanTable.getInstance().getClan(clanId);
		return clan == null ? "Unknown" : clan.getName();
	}
	
	public static final class ContributionView
	{
		public final int charId;
		public final int clanId;
		public final int castleId;
		public final long points;
		public final long lifetimePoints;
		public final int dailyRax;
		public final int weeklyRax;
		
		private ContributionView(int charId, int clanId, int castleId, long points, long lifetimePoints, int dailyRax, int weeklyRax)
		{
			this.charId = charId;
			this.clanId = clanId;
			this.castleId = castleId;
			this.points = points;
			this.lifetimePoints = lifetimePoints;
			this.dailyRax = dailyRax;
			this.weeklyRax = weeklyRax;
		}
	}
	
	private static final class HistoryEvent
	{
		private int charId;
		private String charName;
		private int clanId;
		private int castleId;
		private String territoryId;
		private String type;
		private int points;
		private long adenaTax;
		private int raxReward;
		private String targetName;
		private String metadata;
		private long createdAt;
	}
}
