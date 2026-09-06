package custom.MegaraxVivo;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Additive persistence and transparency service for MegaRax VIVO.
 * Existing systems may publish through {@link #contribute(Player, String, String, String, String, String, int)}
 * after their adapter has been validated and explicitly enabled.
 */
public final class MegaraxVivoService
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxVivoService.class.getName());

	private static final String CREATE_CYCLES = "CREATE TABLE IF NOT EXISTS megarax_vivo_cycles (cycle_key VARCHAR(64) NOT NULL, cycle_type VARCHAR(24) NOT NULL, title VARCHAR(96) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PREPARATION', starts_at BIGINT NOT NULL, ends_at BIGINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 1, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, PRIMARY KEY (cycle_key), KEY idx_vivo_cycle_status (status,starts_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String CREATE_WORLD_STATES = "CREATE TABLE IF NOT EXISTS megarax_vivo_world_states (region_key VARCHAR(32) NOT NULL, region_name VARCHAR(64) NOT NULL, security_value INT NOT NULL DEFAULT 50, supply_value INT NOT NULL DEFAULT 50, spirit_value INT NOT NULL DEFAULT 50, state_version INT NOT NULL DEFAULT 1, updated_at BIGINT NOT NULL, PRIMARY KEY (region_key)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String CREATE_NEEDS = "CREATE TABLE IF NOT EXISTS megarax_vivo_needs (need_key VARCHAR(64) NOT NULL, cycle_key VARCHAR(64) NOT NULL, region_key VARCHAR(32) NOT NULL, title VARCHAR(96) NOT NULL, description VARCHAR(255) NOT NULL DEFAULT '', axis VARCHAR(16) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PREPARATION', progress BIGINT NOT NULL DEFAULT 0, goal BIGINT NOT NULL, personal_daily_cap INT NOT NULL, success_delta INT NOT NULL DEFAULT 0, success_effect VARCHAR(255) NOT NULL DEFAULT '', failure_effect VARCHAR(255) NOT NULL DEFAULT '', starts_at BIGINT NOT NULL, ends_at BIGINT NOT NULL DEFAULT 0, completed_at BIGINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 1, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, PRIMARY KEY (need_key), KEY idx_vivo_need_cycle (cycle_key,status), KEY idx_vivo_need_region (region_key,status)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String CREATE_NEED_CATEGORIES = "CREATE TABLE IF NOT EXISTS megarax_vivo_need_categories (need_key VARCHAR(64) NOT NULL, category VARCHAR(24) NOT NULL, progress BIGINT NOT NULL DEFAULT 0, goal BIGINT NOT NULL, updated_at BIGINT NOT NULL, PRIMARY KEY (need_key,category)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String CREATE_DAILY = "CREATE TABLE IF NOT EXISTS megarax_vivo_daily_contributions (need_key VARCHAR(64) NOT NULL, account_name VARCHAR(45) NOT NULL, day_key CHAR(10) NOT NULL, total_amount INT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL, PRIMARY KEY (need_key,account_name,day_key), KEY idx_vivo_daily_account (account_name,day_key)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String CREATE_LEDGER = "CREATE TABLE IF NOT EXISTS megarax_vivo_contribution_ledger (ledger_id BIGINT NOT NULL AUTO_INCREMENT, unique_event_key VARCHAR(128) NOT NULL, need_key VARCHAR(64) NOT NULL, cycle_key VARCHAR(64) NOT NULL, region_key VARCHAR(32) NOT NULL, account_name VARCHAR(45) NOT NULL, player_id INT NOT NULL, player_name VARCHAR(35) NOT NULL, source_type VARCHAR(32) NOT NULL, source_key VARCHAR(96) NOT NULL, category VARCHAR(24) NOT NULL, base_amount INT NOT NULL, public_multiplier DECIMAL(7,3) NOT NULL DEFAULT 1.000, accepted_amount INT NOT NULL, decision_code VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED', day_key CHAR(10) NOT NULL, created_at BIGINT NOT NULL, PRIMARY KEY (ledger_id), UNIQUE KEY uq_vivo_contribution_event (need_key,account_name,unique_event_key), KEY idx_vivo_ledger_player (player_id,created_at), KEY idx_vivo_ledger_need (need_key,created_at), KEY idx_vivo_ledger_region (region_key,created_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String CREATE_PREFERENCES = "CREATE TABLE IF NOT EXISTS megarax_vivo_player_preferences (account_name VARCHAR(45) NOT NULL, intention VARCHAR(24) NOT NULL DEFAULT 'CONTINUE_STORY', updated_at BIGINT NOT NULL, PRIMARY KEY (account_name)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String CREATE_CHANGE_LOG = "CREATE TABLE IF NOT EXISTS megarax_vivo_change_log (change_key VARCHAR(64) NOT NULL, version_label VARCHAR(32) NOT NULL, change_type VARCHAR(24) NOT NULL, title VARCHAR(96) NOT NULL, before_value VARCHAR(255) NOT NULL DEFAULT '', after_value VARCHAR(255) NOT NULL DEFAULT '', reason VARCHAR(255) NOT NULL DEFAULT '', effective_at BIGINT NOT NULL, review_at BIGINT NOT NULL DEFAULT 0, created_at BIGINT NOT NULL, PRIMARY KEY (change_key), KEY idx_vivo_change_effective (effective_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";

	private static final String UPSERT_CYCLE = "INSERT INTO megarax_vivo_cycles (cycle_key,cycle_type,title,status,starts_at,ends_at,version,created_at,updated_at) VALUES (?,?,?,?,?,0,1,?,?) ON DUPLICATE KEY UPDATE title=VALUES(title),version=GREATEST(version,VALUES(version)),updated_at=VALUES(updated_at)";
	private static final String UPSERT_WORLD_STATE = "INSERT INTO megarax_vivo_world_states (region_key,region_name,security_value,supply_value,spirit_value,state_version,updated_at) VALUES (?,?,?,?,?,1,?) ON DUPLICATE KEY UPDATE region_name=VALUES(region_name)";
	private static final String UPSERT_NEED = "INSERT INTO megarax_vivo_needs (need_key,cycle_key,region_key,title,description,axis,status,progress,goal,personal_daily_cap,success_delta,success_effect,failure_effect,starts_at,ends_at,completed_at,version,created_at,updated_at) VALUES (?,?,?,?,?,?,?,0,?,?,?,?,?,?,0,0,1,?,?) ON DUPLICATE KEY UPDATE title=VALUES(title),description=VALUES(description),goal=GREATEST(progress,VALUES(goal)),personal_daily_cap=VALUES(personal_daily_cap),success_delta=VALUES(success_delta),success_effect=VALUES(success_effect),failure_effect=VALUES(failure_effect),version=GREATEST(version,VALUES(version)),updated_at=VALUES(updated_at)";
	private static final String UPSERT_NEED_CATEGORY = "INSERT INTO megarax_vivo_need_categories (need_key,category,progress,goal,updated_at) VALUES (?,?,0,?,?) ON DUPLICATE KEY UPDATE goal=GREATEST(progress,VALUES(goal)),updated_at=VALUES(updated_at)";
	private static final String INSERT_FOUNDATION_CHANGE = "INSERT IGNORE INTO megarax_vivo_change_log (change_key,version_label,change_type,title,before_value,after_value,reason,effective_at,review_at,created_at) VALUES ('VIVO_FOUNDATION_1','VIVO 0.1','FOUNDATION','Fundacion auditable de MegaRax VIVO','Sistemas existentes separados','Pulso, Huella y Libro Abierto sobre tablas propias','Conectar sistemas sin reemplazarlos ni alterar su economia',?,0,?)";

	private static final String LOCK_NEED = "SELECT cycle_key,region_key,title,axis,status,progress,goal,personal_daily_cap,success_delta FROM megarax_vivo_needs WHERE need_key=? FOR UPDATE";
	private static final String LOCK_CATEGORY = "SELECT progress,goal FROM megarax_vivo_need_categories WHERE need_key=? AND category=? FOR UPDATE";
	private static final String ENSURE_DAILY = "INSERT IGNORE INTO megarax_vivo_daily_contributions (need_key,account_name,day_key,total_amount,updated_at) VALUES (?,?,?,0,?)";
	private static final String LOCK_DAILY = "SELECT total_amount FROM megarax_vivo_daily_contributions WHERE need_key=? AND account_name=? AND day_key=? FOR UPDATE";
	private static final String INSERT_LEDGER = "INSERT IGNORE INTO megarax_vivo_contribution_ledger (unique_event_key,need_key,cycle_key,region_key,account_name,player_id,player_name,source_type,source_key,category,base_amount,public_multiplier,accepted_amount,decision_code,day_key,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'ACCEPTED',?,?)";
	private static final String UPDATE_DAILY = "UPDATE megarax_vivo_daily_contributions SET total_amount=total_amount+?,updated_at=? WHERE need_key=? AND account_name=? AND day_key=?";
	private static final String UPDATE_CATEGORY = "UPDATE megarax_vivo_need_categories SET progress=?,updated_at=? WHERE need_key=? AND category=?";
	private static final String UPDATE_NEED = "UPDATE megarax_vivo_needs SET progress=?,status=?,completed_at=?,updated_at=? WHERE need_key=?";
	private static final String UPDATE_CYCLE_STATUS = "UPDATE megarax_vivo_cycles SET status=?,updated_at=? WHERE cycle_key=?";
	private static final String UPDATE_WORLD_SECURITY = "UPDATE megarax_vivo_world_states SET security_value=LEAST(100,security_value+?),state_version=state_version+1,updated_at=? WHERE region_key=?";
	private static final String UPDATE_WORLD_SUPPLY = "UPDATE megarax_vivo_world_states SET supply_value=LEAST(100,supply_value+?),state_version=state_version+1,updated_at=? WHERE region_key=?";
	private static final String UPDATE_WORLD_SPIRIT = "UPDATE megarax_vivo_world_states SET spirit_value=LEAST(100,spirit_value+?),state_version=state_version+1,updated_at=? WHERE region_key=?";

	private static final String SELECT_WORLD_STATES = "SELECT region_key,region_name,security_value,supply_value,spirit_value,state_version,updated_at FROM megarax_vivo_world_states ORDER BY CASE region_key WHEN 'ADEN' THEN 1 WHEN 'RUNE' THEN 2 WHEN 'GODDARD' THEN 3 WHEN 'OREN' THEN 4 ELSE 5 END,region_name";
	private static final String SELECT_NEEDS = "SELECT need_key,cycle_key,region_key,title,description,axis,status,progress,goal,personal_daily_cap,success_delta,success_effect,failure_effect,starts_at,ends_at,completed_at,version,updated_at FROM megarax_vivo_needs WHERE status IN ('PREPARATION','ACTIVE','PAUSED','COMPLETED','FAILED') ORDER BY CASE status WHEN 'ACTIVE' THEN 1 WHEN 'PREPARATION' THEN 2 WHEN 'PAUSED' THEN 3 ELSE 4 END,updated_at DESC";
	private static final String SELECT_CATEGORIES = "SELECT category,progress,goal FROM megarax_vivo_need_categories WHERE need_key=? ORDER BY goal DESC,category";
	private static final String SELECT_RECEIPTS = "SELECT ledger_id,need_key,region_key,player_name,source_type,source_key,category,base_amount,public_multiplier,accepted_amount,decision_code,created_at FROM megarax_vivo_contribution_ledger WHERE account_name=? ORDER BY created_at DESC,ledger_id DESC LIMIT ?";
	private static final String SELECT_RECENT_CONTRIBUTORS = "SELECT player_name,category,SUM(accepted_amount) total_amount,MAX(created_at) last_at FROM megarax_vivo_contribution_ledger WHERE need_key=? GROUP BY account_name,player_name,category ORDER BY last_at DESC LIMIT ?";
	private static final String SELECT_CHANGE_LOG = "SELECT change_key,version_label,change_type,title,before_value,after_value,reason,effective_at,review_at FROM megarax_vivo_change_log ORDER BY effective_at DESC LIMIT ?";
	private static final String SELECT_CHRONICLE = "SELECT player_name,title,details,event_time FROM megarax_chronicle_events WHERE is_public=1 ORDER BY event_time DESC,event_id DESC LIMIT ?";
	private static final String SELECT_INTENTION = "SELECT intention FROM megarax_vivo_player_preferences WHERE account_name=?";
	private static final String UPSERT_INTENTION = "INSERT INTO megarax_vivo_player_preferences (account_name,intention,updated_at) VALUES (?,?,?) ON DUPLICATE KEY UPDATE intention=VALUES(intention),updated_at=VALUES(updated_at)";

	private volatile boolean ready;
	private volatile String lastError = "";

	private MegaraxVivoService()
	{
		MegaraxVivoConfig.load();
		if (!MegaraxVivoConfig.ENABLED)
		{
			LOGGER.info("MegaraxVivo: Disabled by configuration. Existing systems remain unchanged.");
			return;
		}
		try
		{
			if (MegaraxVivoConfig.AUTO_CREATE_TABLES)
			{
				initializeTables();
			}
			ensureBaseline();
			ready = true;
			LOGGER.info("MegaraxVivo: Foundation loaded in " + MegaraxVivoConfig.PILOT_STATUS + " mode. No existing reward or economy table was changed.");
		}
		catch (Exception e)
		{
			lastError = safeMessage(e);
			LOGGER.log(Level.WARNING, "MegaraxVivo: Foundation could not initialize. The board will remain read-only with an error notice.", e);
		}
	}

	public static MegaraxVivoService getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	public boolean isEnabled()
	{
		return MegaraxVivoConfig.ENABLED;
	}

	public boolean isReady()
	{
		return ready;
	}

	public String getLastError()
	{
		return lastError;
	}

	private void initializeTables() throws SQLException
	{
		try (Connection con = DatabaseFactory.getConnection(); Statement statement = con.createStatement())
		{
			statement.executeUpdate(CREATE_CYCLES);
			statement.executeUpdate(CREATE_WORLD_STATES);
			statement.executeUpdate(CREATE_NEEDS);
			statement.executeUpdate(CREATE_NEED_CATEGORIES);
			statement.executeUpdate(CREATE_DAILY);
			statement.executeUpdate(CREATE_LEDGER);
			statement.executeUpdate(CREATE_PREFERENCES);
			statement.executeUpdate(CREATE_CHANGE_LOG);
		}
	}

	private void ensureBaseline() throws SQLException
	{
		final long now = System.currentTimeMillis();
		try (Connection con = DatabaseFactory.getConnection())
		{
			con.setAutoCommit(false);
			try
			{
				try (PreparedStatement ps = con.prepareStatement(UPSERT_CYCLE))
				{
					ps.setString(1, MegaraxVivoConfig.PILOT_CYCLE_KEY);
					ps.setString(2, "WEEKLY_STORY");
					ps.setString(3, MegaraxVivoConfig.PILOT_CYCLE_TITLE);
					ps.setString(4, MegaraxVivoConfig.PILOT_STATUS);
					ps.setLong(5, now);
					ps.setLong(6, now);
					ps.setLong(7, now);
					ps.executeUpdate();
				}
				seedRegion(con, "ADEN", "Aden", now);
				seedRegion(con, "RUNE", "Rune", now);
				seedRegion(con, "GODDARD", "Goddard", now);
				seedRegion(con, "OREN", "Oren", now);
				try (PreparedStatement ps = con.prepareStatement(UPSERT_NEED))
				{
					int i = 1;
					ps.setString(i++, MegaraxVivoConfig.PILOT_NEED_KEY);
					ps.setString(i++, MegaraxVivoConfig.PILOT_CYCLE_KEY);
					ps.setString(i++, MegaraxVivoConfig.PILOT_REGION);
					ps.setString(i++, MegaraxVivoConfig.PILOT_NEED_TITLE);
					ps.setString(i++, MegaraxVivoConfig.PILOT_NEED_DESCRIPTION);
					ps.setString(i++, "SUPPLY");
					ps.setString(i++, MegaraxVivoConfig.PILOT_STATUS);
					ps.setLong(i++, MegaraxVivoConfig.PILOT_GOAL);
					ps.setInt(i++, MegaraxVivoConfig.PILOT_PERSONAL_DAILY_CAP);
					ps.setInt(i++, MegaraxVivoConfig.PILOT_SUCCESS_SUPPLY_DELTA);
					ps.setString(i++, MegaraxVivoConfig.PILOT_SUCCESS_EFFECT);
					ps.setString(i++, MegaraxVivoConfig.PILOT_FAILURE_EFFECT);
					ps.setLong(i++, now);
					ps.setLong(i++, now);
					ps.setLong(i++, now);
					ps.executeUpdate();
				}
				for (Map.Entry<String, Long> category : MegaraxVivoConfig.PILOT_CATEGORIES.entrySet())
				{
					try (PreparedStatement ps = con.prepareStatement(UPSERT_NEED_CATEGORY))
					{
						ps.setString(1, MegaraxVivoConfig.PILOT_NEED_KEY);
						ps.setString(2, category.getKey());
						ps.setLong(3, category.getValue());
						ps.setLong(4, now);
						ps.executeUpdate();
					}
				}
				try (PreparedStatement ps = con.prepareStatement(INSERT_FOUNDATION_CHANGE))
				{
					ps.setLong(1, now);
					ps.setLong(2, now);
					ps.executeUpdate();
				}
				con.commit();
			}
			catch (Exception e)
			{
				con.rollback();
				throw e;
			}
			finally
			{
				con.setAutoCommit(true);
			}
		}
	}

	private void seedRegion(Connection con, String key, String name, long now) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(UPSERT_WORLD_STATE))
		{
			ps.setString(1, key);
			ps.setString(2, name);
			ps.setInt(3, MegaraxVivoConfig.DEFAULT_SECURITY);
			ps.setInt(4, MegaraxVivoConfig.DEFAULT_SUPPLY);
			ps.setInt(5, MegaraxVivoConfig.DEFAULT_SPIRIT);
			ps.setLong(6, now);
			ps.executeUpdate();
		}
	}

	/**
	 * Public adapter API. The pilot starts in PREPARATION, so calls are safely rejected until activation.
	 */
	public static ContributionResult contribute(Player player, String needKey, String uniqueEventKey, String sourceType, String sourceKey, String category, int baseAmount)
	{
		return getInstance().recordContribution(player, needKey, uniqueEventKey, sourceType, sourceKey, category, baseAmount);
	}

	private ContributionResult recordContribution(Player player, String needKey, String uniqueEventKey, String sourceType, String sourceKey, String category, int baseAmount)
	{
		if (!ready)
		{
			return ContributionResult.rejected("SERVICE_NOT_READY", "MegaRax VIVO is not ready.");
		}
		if ((player == null) || player.isGM())
		{
			return ContributionResult.rejected("INVALID_PLAYER", "This action is not eligible.");
		}
		if ((baseAmount <= 0) || (baseAmount > MegaraxVivoConfig.MAXIMUM_SINGLE_CONTRIBUTION))
		{
			return ContributionResult.rejected("INVALID_AMOUNT", "Contribution amount is outside the public limit.");
		}

		final String safeNeed = cleanKey(needKey, 64);
		final String safeEvent = cleanKey(uniqueEventKey, 128);
		final String safeSourceType = cleanKey(sourceType, 32);
		final String safeSourceKey = cleanKey(sourceKey, 96);
		final String safeCategory = cleanKey(category, 24);
		if (safeNeed.isEmpty() || safeEvent.isEmpty() || safeSourceType.isEmpty() || safeSourceKey.isEmpty() || safeCategory.isEmpty())
		{
			return ContributionResult.rejected("INVALID_KEY", "Contribution identifiers are invalid.");
		}

		final long now = System.currentTimeMillis();
		final String dayKey = LocalDate.now(MegaraxVivoConfig.TIME_ZONE).toString();
		NeedLock need = null;
		int accepted = 0;
		long newNeedProgress = 0;
		long needGoal = 0;
		boolean completedNow = false;
		try (Connection con = DatabaseFactory.getConnection())
		{
			con.setAutoCommit(false);
			try
			{
				need = lockNeed(con, safeNeed);
				if (need == null)
				{
					con.rollback();
					return ContributionResult.rejected("UNKNOWN_NEED", "This world need does not exist.");
				}
				if (!"ACTIVE".equals(need.status))
				{
					con.rollback();
					return ContributionResult.rejected("NEED_" + need.status, "This need is currently " + friendlyStatus(need.status) + ".");
				}
				final CategoryLock categoryLock = lockCategory(con, safeNeed, safeCategory);
				if (categoryLock == null)
				{
					con.rollback();
					return ContributionResult.rejected("CATEGORY_NOT_ACCEPTED", "This need does not accept that contribution category.");
				}
				if (categoryLock.progress >= categoryLock.goal)
				{
					con.rollback();
					return ContributionResult.rejected("CATEGORY_COMPLETE", "That contribution category is already complete.");
				}

				ensureDaily(con, safeNeed, player.getAccountName(), dayKey, now);
				final int dailyTotal = lockDaily(con, safeNeed, player.getAccountName(), dayKey);
				if (dailyTotal >= need.personalDailyCap)
				{
					con.rollback();
					return ContributionResult.rejected("DAILY_CAP_REACHED", "Your public daily contribution cap for this need is complete.");
				}

				final long needRemaining = Math.max(0L, need.goal - need.progress);
				final long categoryRemaining = Math.max(0L, categoryLock.goal - categoryLock.progress);
				final int capRemaining = Math.max(0, need.personalDailyCap - dailyTotal);
				accepted = (int) Math.min(baseAmount, Math.min(capRemaining, Math.min(needRemaining, categoryRemaining)));
				if (accepted <= 0)
				{
					con.rollback();
					return ContributionResult.rejected("NO_CAPACITY", "No contribution capacity remains for this action today.");
				}

				try (PreparedStatement ps = con.prepareStatement(INSERT_LEDGER))
				{
					int i = 1;
					ps.setString(i++, safeEvent);
					ps.setString(i++, safeNeed);
					ps.setString(i++, need.cycleKey);
					ps.setString(i++, need.regionKey);
					ps.setString(i++, player.getAccountName());
					ps.setInt(i++, player.getObjectId());
					ps.setString(i++, safePlayerName(player.getName()));
					ps.setString(i++, safeSourceType);
					ps.setString(i++, safeSourceKey);
					ps.setString(i++, safeCategory);
					ps.setInt(i++, baseAmount);
					ps.setBigDecimal(i++, BigDecimal.ONE.setScale(3));
					ps.setInt(i++, accepted);
					ps.setString(i++, dayKey);
					ps.setLong(i++, now);
					if (ps.executeUpdate() == 0)
					{
						con.rollback();
						return ContributionResult.rejected("DUPLICATE_EVENT", "This action was already recorded.");
					}
				}

				updateDaily(con, safeNeed, player.getAccountName(), dayKey, accepted, now);
				final long newCategoryProgress = Math.min(categoryLock.goal, categoryLock.progress + accepted);
				updateCategory(con, safeNeed, safeCategory, newCategoryProgress, now);
				newNeedProgress = Math.min(need.goal, need.progress + accepted);
				needGoal = need.goal;
				completedNow = newNeedProgress >= need.goal;
				updateNeed(con, safeNeed, newNeedProgress, completedNow ? "COMPLETED" : "ACTIVE", completedNow ? now : 0L, now);
				updateCycleStatus(con, need.cycleKey, completedNow ? "COMPLETED" : "ACTIVE", now);
				if (completedNow && (need.successDelta > 0))
				{
					updateWorldState(con, need.regionKey, need.axis, need.successDelta, now);
				}
				con.commit();
			}
			catch (Exception e)
			{
				con.rollback();
				throw e;
			}
			finally
			{
				con.setAutoCommit(true);
			}
		}
		catch (Exception e)
		{
			LOGGER.log(Level.WARNING, "MegaraxVivo: Could not record contribution for " + player.getName() + ".", e);
			return ContributionResult.rejected("DATABASE_ERROR", "The contribution could not be committed safely.");
		}

		final ContributionResult result = ContributionResult.accepted(accepted, newNeedProgress, needGoal, completedNow, need == null ? safeNeed : need.title, need == null ? "" : need.regionKey, safeCategory);
		if (MegaraxVivoConfig.SHOW_CONTRIBUTION_MESSAGES && ((accepted >= MegaraxVivoConfig.CONTRIBUTION_MESSAGE_THRESHOLD) || completedNow))
		{
			player.sendMessage("MegaRax VIVO: +" + accepted + " " + friendlyKey(safeCategory) + " for " + result.needTitle + " (" + newNeedProgress + "/" + needGoal + ").");
		}
		return result;
	}

	private NeedLock lockNeed(Connection con, String needKey) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(LOCK_NEED))
		{
			ps.setString(1, needKey);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? new NeedLock(rs.getString("cycle_key"), rs.getString("region_key"), rs.getString("title"), rs.getString("axis"), rs.getString("status"), rs.getLong("progress"), rs.getLong("goal"), rs.getInt("personal_daily_cap"), rs.getInt("success_delta")) : null;
			}
		}
	}

	private CategoryLock lockCategory(Connection con, String needKey, String category) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(LOCK_CATEGORY))
		{
			ps.setString(1, needKey);
			ps.setString(2, category);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? new CategoryLock(rs.getLong("progress"), rs.getLong("goal")) : null;
			}
		}
	}

	private void ensureDaily(Connection con, String needKey, String accountName, String dayKey, long now) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(ENSURE_DAILY))
		{
			ps.setString(1, needKey);
			ps.setString(2, accountName);
			ps.setString(3, dayKey);
			ps.setLong(4, now);
			ps.executeUpdate();
		}
	}

	private int lockDaily(Connection con, String needKey, String accountName, String dayKey) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(LOCK_DAILY))
		{
			ps.setString(1, needKey);
			ps.setString(2, accountName);
			ps.setString(3, dayKey);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? rs.getInt("total_amount") : 0;
			}
		}
	}

	private void updateDaily(Connection con, String needKey, String accountName, String dayKey, int amount, long now) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(UPDATE_DAILY))
		{
			ps.setInt(1, amount);
			ps.setLong(2, now);
			ps.setString(3, needKey);
			ps.setString(4, accountName);
			ps.setString(5, dayKey);
			ps.executeUpdate();
		}
	}

	private void updateCategory(Connection con, String needKey, String category, long progress, long now) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(UPDATE_CATEGORY))
		{
			ps.setLong(1, progress);
			ps.setLong(2, now);
			ps.setString(3, needKey);
			ps.setString(4, category);
			ps.executeUpdate();
		}
	}

	private void updateNeed(Connection con, String needKey, long progress, String status, long completedAt, long now) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(UPDATE_NEED))
		{
			ps.setLong(1, progress);
			ps.setString(2, status);
			ps.setLong(3, completedAt);
			ps.setLong(4, now);
			ps.setString(5, needKey);
			ps.executeUpdate();
		}
	}

	private void updateCycleStatus(Connection con, String cycleKey, String status, long now) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(UPDATE_CYCLE_STATUS))
		{
			ps.setString(1, status);
			ps.setLong(2, now);
			ps.setString(3, cycleKey);
			ps.executeUpdate();
		}
	}

	private void updateWorldState(Connection con, String regionKey, String axis, int delta, long now) throws SQLException
	{
		final String sql;
		switch (axis)
		{
			case "SECURITY":
				sql = UPDATE_WORLD_SECURITY;
				break;
			case "SPIRIT":
				sql = UPDATE_WORLD_SPIRIT;
				break;
			case "SUPPLY":
			default:
				sql = UPDATE_WORLD_SUPPLY;
				break;
		}
		try (PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setInt(1, delta);
			ps.setLong(2, now);
			ps.setString(3, regionKey);
			ps.executeUpdate();
		}
	}

	public List<WorldStateView> getWorldStates()
	{
		if (!ready)
		{
			return Collections.emptyList();
		}
		final List<WorldStateView> result = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(SELECT_WORLD_STATES); ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
			{
				result.add(new WorldStateView(rs.getString("region_key"), rs.getString("region_name"), rs.getInt("security_value"), rs.getInt("supply_value"), rs.getInt("spirit_value"), rs.getInt("state_version"), rs.getLong("updated_at")));
			}
		}
		catch (SQLException e)
		{
			logReadFailure("world states", e);
		}
		return result;
	}

	public List<NeedView> getNeeds()
	{
		if (!ready)
		{
			return Collections.emptyList();
		}
		final List<NeedView> result = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(SELECT_NEEDS); ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
			{
				final String needKey = rs.getString("need_key");
				result.add(new NeedView(needKey, rs.getString("cycle_key"), rs.getString("region_key"), rs.getString("title"), rs.getString("description"), rs.getString("axis"), rs.getString("status"), rs.getLong("progress"), rs.getLong("goal"), rs.getInt("personal_daily_cap"), rs.getInt("success_delta"), rs.getString("success_effect"), rs.getString("failure_effect"), rs.getLong("starts_at"), rs.getLong("ends_at"), rs.getLong("completed_at"), rs.getInt("version"), rs.getLong("updated_at"), getCategories(con, needKey)));
			}
		}
		catch (SQLException e)
		{
			logReadFailure("world needs", e);
		}
		return result;
	}

	private List<CategoryView> getCategories(Connection con, String needKey) throws SQLException
	{
		final List<CategoryView> categories = new ArrayList<>();
		try (PreparedStatement ps = con.prepareStatement(SELECT_CATEGORIES))
		{
			ps.setString(1, needKey);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					categories.add(new CategoryView(rs.getString("category"), rs.getLong("progress"), rs.getLong("goal")));
				}
			}
		}
		return categories;
	}

	public List<ReceiptView> getReceipts(Player player, int requestedLimit)
	{
		if (!ready || (player == null))
		{
			return Collections.emptyList();
		}
		final int limit = Math.max(1, Math.min(requestedLimit, 50));
		final List<ReceiptView> result = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(SELECT_RECEIPTS))
		{
			ps.setString(1, player.getAccountName());
			ps.setInt(2, limit);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					result.add(new ReceiptView(rs.getLong("ledger_id"), rs.getString("need_key"), rs.getString("region_key"), rs.getString("player_name"), rs.getString("source_type"), rs.getString("source_key"), rs.getString("category"), rs.getInt("base_amount"), rs.getBigDecimal("public_multiplier"), rs.getInt("accepted_amount"), rs.getString("decision_code"), rs.getLong("created_at")));
				}
			}
		}
		catch (SQLException e)
		{
			logReadFailure("player receipts", e);
		}
		return result;
	}

	public List<ContributorView> getRecentContributors(String needKey, int requestedLimit)
	{
		if (!ready)
		{
			return Collections.emptyList();
		}
		final int limit = Math.max(1, Math.min(requestedLimit, 25));
		final List<ContributorView> result = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(SELECT_RECENT_CONTRIBUTORS))
		{
			ps.setString(1, cleanKey(needKey, 64));
			ps.setInt(2, limit);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					result.add(new ContributorView(rs.getString("player_name"), rs.getString("category"), rs.getLong("total_amount"), rs.getLong("last_at")));
				}
			}
		}
		catch (SQLException e)
		{
			logReadFailure("recent contributors", e);
		}
		return result;
	}

	public List<ChangeView> getChangeLog()
	{
		if (!ready)
		{
			return Collections.emptyList();
		}
		final List<ChangeView> result = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(SELECT_CHANGE_LOG))
		{
			ps.setInt(1, MegaraxVivoConfig.CHANGE_LOG_LIMIT);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					result.add(new ChangeView(rs.getString("change_key"), rs.getString("version_label"), rs.getString("change_type"), rs.getString("title"), rs.getString("before_value"), rs.getString("after_value"), rs.getString("reason"), rs.getLong("effective_at"), rs.getLong("review_at")));
				}
			}
		}
		catch (SQLException e)
		{
			logReadFailure("change log", e);
		}
		return result;
	}

	public List<ChronicleView> getChronicleEntries(int requestedLimit)
	{
		if (!ready)
		{
			return Collections.emptyList();
		}
		final int limit = Math.max(1, Math.min(requestedLimit, 20));
		final List<ChronicleView> result = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(SELECT_CHRONICLE))
		{
			ps.setInt(1, limit);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					result.add(new ChronicleView(rs.getString("player_name"), rs.getString("title"), rs.getString("details"), rs.getLong("event_time")));
				}
			}
		}
		catch (SQLException e)
		{
			// Chronicle remains an independent optional system. Its absence must not break VIVO.
			LOGGER.log(Level.FINE, "MegaraxVivo: Chronicle feed is not available.", e);
		}
		return result;
	}

	public Intention getIntention(Player player)
	{
		if (!ready || (player == null))
		{
			return Intention.CONTINUE_STORY;
		}
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(SELECT_INTENTION))
		{
			ps.setString(1, player.getAccountName());
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? Intention.parse(rs.getString("intention")) : Intention.CONTINUE_STORY;
			}
		}
		catch (SQLException e)
		{
			logReadFailure("player intention", e);
			return Intention.CONTINUE_STORY;
		}
	}

	public boolean setIntention(Player player, String value)
	{
		if (!ready || (player == null))
		{
			return false;
		}
		final Intention intention = Intention.parse(value);
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(UPSERT_INTENTION))
		{
			ps.setString(1, player.getAccountName());
			ps.setString(2, intention.name());
			ps.setLong(3, System.currentTimeMillis());
			ps.executeUpdate();
			return true;
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "MegaraxVivo: Could not save intention for " + player.getName() + ".", e);
			return false;
		}
	}

	private void logReadFailure(String subject, SQLException e)
	{
		LOGGER.log(Level.WARNING, "MegaraxVivo: Could not read " + subject + ".", e);
	}

	public static String friendlyKey(String value)
	{
		if (value == null)
		{
			return "Unknown";
		}
		final String[] words = value.toLowerCase(Locale.ROOT).replace('_', ' ').split(" ");
		final StringBuilder result = new StringBuilder();
		for (String word : words)
		{
			if (word.isEmpty())
			{
				continue;
			}
			if (result.length() > 0)
			{
				result.append(' ');
			}
			result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return result.toString();
	}

	public static String friendlyStatus(String value)
	{
		if (value == null)
		{
			return "unknown";
		}
		switch (value)
		{
			case "PREPARATION":
				return "in private preparation";
			case "ACTIVE":
				return "active";
			case "PAUSED":
				return "paused";
			case "COMPLETED":
				return "completed";
			case "FAILED":
				return "closed with a recoverable consequence";
			default:
				return friendlyKey(value).toLowerCase(Locale.ROOT);
		}
	}

	private static String cleanKey(String value, int maxLength)
	{
		if (value == null)
		{
			return "";
		}
		final String clean = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_\\-:.]", "_");
		return clean.substring(0, Math.min(maxLength, clean.length()));
	}

	private static String safePlayerName(String value)
	{
		if (value == null)
		{
			return "Unknown";
		}
		return value.substring(0, Math.min(35, value.length()));
	}

	private static String safeMessage(Exception e)
	{
		final String value = e.getMessage();
		return (value == null) || value.trim().isEmpty() ? e.getClass().getSimpleName() : value.substring(0, Math.min(180, value.length()));
	}

	private static final class SingletonHolder
	{
		private static final MegaraxVivoService INSTANCE = new MegaraxVivoService();
	}

	private static final class NeedLock
	{
		private final String cycleKey;
		private final String regionKey;
		private final String title;
		private final String axis;
		private final String status;
		private final long progress;
		private final long goal;
		private final int personalDailyCap;
		private final int successDelta;

		private NeedLock(String cycleKey, String regionKey, String title, String axis, String status, long progress, long goal, int personalDailyCap, int successDelta)
		{
			this.cycleKey = cycleKey;
			this.regionKey = regionKey;
			this.title = title;
			this.axis = axis;
			this.status = status;
			this.progress = progress;
			this.goal = goal;
			this.personalDailyCap = personalDailyCap;
			this.successDelta = successDelta;
		}
	}

	private static final class CategoryLock
	{
		private final long progress;
		private final long goal;

		private CategoryLock(long progress, long goal)
		{
			this.progress = progress;
			this.goal = goal;
		}
	}

	public enum Intention
	{
		ADVENTURE("Aventurarme", "Caceria, amenazas, raids y expediciones."),
		SERVE_OTHERS("Servir a otros", "Buffs, curacion, tanking, rescate y mentoria."),
		PRODUCE_TRADE("Producir y comerciar", "Craft, spoil, encargos, suministro y mercado."),
		EXPLORE("Explorar y descubrir", "Huellas, rutas, secretos y primeras noticias."),
		COMPETE("Competir", "PvP consentido, dominio, rivalidades y objetivos."),
		CONTINUE_STORY("Continuar mi historia", "Cronica, proyectos y progreso personal.");

		public final String title;
		public final String description;

		Intention(String title, String description)
		{
			this.title = title;
			this.description = description;
		}

		public static Intention parse(String value)
		{
			if (value != null)
			{
				try
				{
					return valueOf(value.trim().toUpperCase(Locale.ROOT));
				}
				catch (IllegalArgumentException e)
				{
					// Safe default below.
				}
			}
			return CONTINUE_STORY;
		}
	}

	public static final class ContributionResult
	{
		public final boolean accepted;
		public final String code;
		public final String message;
		public final int acceptedAmount;
		public final long progress;
		public final long goal;
		public final boolean completedNow;
		public final String needTitle;
		public final String regionKey;
		public final String category;

		private ContributionResult(boolean accepted, String code, String message, int acceptedAmount, long progress, long goal, boolean completedNow, String needTitle, String regionKey, String category)
		{
			this.accepted = accepted;
			this.code = code;
			this.message = message;
			this.acceptedAmount = acceptedAmount;
			this.progress = progress;
			this.goal = goal;
			this.completedNow = completedNow;
			this.needTitle = needTitle;
			this.regionKey = regionKey;
			this.category = category;
		}

		private static ContributionResult accepted(int amount, long progress, long goal, boolean completedNow, String needTitle, String regionKey, String category)
		{
			return new ContributionResult(true, "ACCEPTED", "Contribution committed.", amount, progress, goal, completedNow, needTitle, regionKey, category);
		}

		public static ContributionResult rejected(String code, String message)
		{
			return new ContributionResult(false, code, message, 0, 0, 0, false, "", "", "");
		}
	}

	public static final class WorldStateView
	{
		public final String regionKey;
		public final String regionName;
		public final int security;
		public final int supply;
		public final int spirit;
		public final int version;
		public final long updatedAt;

		private WorldStateView(String regionKey, String regionName, int security, int supply, int spirit, int version, long updatedAt)
		{
			this.regionKey = regionKey;
			this.regionName = regionName;
			this.security = security;
			this.supply = supply;
			this.spirit = spirit;
			this.version = version;
			this.updatedAt = updatedAt;
		}
	}

	public static final class CategoryView
	{
		public final String category;
		public final long progress;
		public final long goal;

		private CategoryView(String category, long progress, long goal)
		{
			this.category = category;
			this.progress = progress;
			this.goal = goal;
		}
	}

	public static final class NeedView
	{
		public final String needKey;
		public final String cycleKey;
		public final String regionKey;
		public final String title;
		public final String description;
		public final String axis;
		public final String status;
		public final long progress;
		public final long goal;
		public final int personalDailyCap;
		public final int successDelta;
		public final String successEffect;
		public final String failureEffect;
		public final long startsAt;
		public final long endsAt;
		public final long completedAt;
		public final int version;
		public final long updatedAt;
		public final List<CategoryView> categories;

		private NeedView(String needKey, String cycleKey, String regionKey, String title, String description, String axis, String status, long progress, long goal, int personalDailyCap, int successDelta, String successEffect, String failureEffect, long startsAt, long endsAt, long completedAt, int version, long updatedAt, List<CategoryView> categories)
		{
			this.needKey = needKey;
			this.cycleKey = cycleKey;
			this.regionKey = regionKey;
			this.title = title;
			this.description = description;
			this.axis = axis;
			this.status = status;
			this.progress = progress;
			this.goal = goal;
			this.personalDailyCap = personalDailyCap;
			this.successDelta = successDelta;
			this.successEffect = successEffect;
			this.failureEffect = failureEffect;
			this.startsAt = startsAt;
			this.endsAt = endsAt;
			this.completedAt = completedAt;
			this.version = version;
			this.updatedAt = updatedAt;
			this.categories = Collections.unmodifiableList(new ArrayList<>(categories));
		}
	}

	public static final class ReceiptView
	{
		public final long ledgerId;
		public final String needKey;
		public final String regionKey;
		public final String playerName;
		public final String sourceType;
		public final String sourceKey;
		public final String category;
		public final int baseAmount;
		public final BigDecimal multiplier;
		public final int acceptedAmount;
		public final String decisionCode;
		public final long createdAt;

		private ReceiptView(long ledgerId, String needKey, String regionKey, String playerName, String sourceType, String sourceKey, String category, int baseAmount, BigDecimal multiplier, int acceptedAmount, String decisionCode, long createdAt)
		{
			this.ledgerId = ledgerId;
			this.needKey = needKey;
			this.regionKey = regionKey;
			this.playerName = playerName;
			this.sourceType = sourceType;
			this.sourceKey = sourceKey;
			this.category = category;
			this.baseAmount = baseAmount;
			this.multiplier = multiplier;
			this.acceptedAmount = acceptedAmount;
			this.decisionCode = decisionCode;
			this.createdAt = createdAt;
		}
	}

	public static final class ContributorView
	{
		public final String playerName;
		public final String category;
		public final long totalAmount;
		public final long lastAt;

		private ContributorView(String playerName, String category, long totalAmount, long lastAt)
		{
			this.playerName = playerName;
			this.category = category;
			this.totalAmount = totalAmount;
			this.lastAt = lastAt;
		}
	}

	public static final class ChangeView
	{
		public final String changeKey;
		public final String versionLabel;
		public final String changeType;
		public final String title;
		public final String beforeValue;
		public final String afterValue;
		public final String reason;
		public final long effectiveAt;
		public final long reviewAt;

		private ChangeView(String changeKey, String versionLabel, String changeType, String title, String beforeValue, String afterValue, String reason, long effectiveAt, long reviewAt)
		{
			this.changeKey = changeKey;
			this.versionLabel = versionLabel;
			this.changeType = changeType;
			this.title = title;
			this.beforeValue = beforeValue;
			this.afterValue = afterValue;
			this.reason = reason;
			this.effectiveAt = effectiveAt;
			this.reviewAt = reviewAt;
		}
	}

	public static final class ChronicleView
	{
		public final String playerName;
		public final String title;
		public final String details;
		public final long eventTime;

		private ChronicleView(String playerName, String title, String details, long eventTime)
		{
			this.playerName = playerName;
			this.title = title;
			this.details = details;
			this.eventTime = eventTime;
		}
	}
}
