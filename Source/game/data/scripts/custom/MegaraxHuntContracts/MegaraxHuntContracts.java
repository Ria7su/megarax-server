package custom.MegaraxHuntContracts;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.zone.ZoneType;
import org.l2jmobius.gameserver.util.LocationUtil;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import ai.AbstractNpcAI;
import custom.MegaraxLivingChronicle.MegaraxLivingChronicle;

/** Account-wide Daily Hunt Contracts and reusable activity wallet service. */
public class MegaraxHuntContracts extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger("megarax_hunt_contracts");
	private static final File XML_FILE = new File("data/MegaraxHuntContracts.xml");
	private static final File INI_FILE = new File("config/Custom/MegaraxHuntContracts.ini");
	private static final Map<String, ReentrantLock> ACCOUNT_LOCKS = new ConcurrentHashMap<>();
	private static final Set<String> KILL_GUARD = ConcurrentHashMap.newKeySet();
	private static volatile MegaraxHuntContracts INSTANCE;
	private static volatile Settings SETTINGS = new Settings();
	private static volatile Map<String, Template> TEMPLATES = Collections.emptyMap();
	private static volatile boolean RUNTIME_ENABLED = true;

	private static final String INSERT_WALLET = "INSERT IGNORE INTO account_activity_wallet (account_name) VALUES (?)";
	private static final String SELECT_WALLET = "SELECT hunt_marks,wanted_seals,war_contribution FROM account_activity_wallet WHERE account_name=?";
	private static final String SELECT_WALLET_LOCK = SELECT_WALLET + " FOR UPDATE";
	private static final String SELECT_TODAY = "SELECT id,offer_slot,contract_template_id,contract_state,progress,target_count,premium_slot,accepted_at,completed_at FROM account_hunt_contracts WHERE account_name=? AND reset_date=? ORDER BY offer_slot";
	private static final String SELECT_TODAY_LOCK = SELECT_TODAY + " FOR UPDATE";

	private MegaraxHuntContracts()
	{
		INSTANCE = this;
		initTables();
		reloadConfig();
		LOGGER.info("Megarax Daily Hunt Contracts loaded.");
	}

	public static MegaraxHuntContracts getInstance()
	{
		if (INSTANCE == null)
		{
			synchronized (MegaraxHuntContracts.class)
			{
				if (INSTANCE == null)
				{
					new MegaraxHuntContracts();
				}
			}
		}
		return INSTANCE;
	}

	public static synchronized boolean reloadConfig()
	{
		try
		{
			final Properties ini = new Properties();
			try (FileInputStream input = new FileInputStream(INI_FILE))
			{
				ini.load(input);
			}
			final Settings settings = new Settings();
			final Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(XML_FILE).getDocumentElement();
			settings.enabled = Boolean.parseBoolean(ini.getProperty("EnableHuntContracts", root.getAttribute("enabled")));
			settings.debug = Boolean.parseBoolean(ini.getProperty("HuntContractsDebug", "false"));
			settings.offersPerDay = positive(root, "offersPerDay", 3);
			settings.resetHour = Math.max(0, Math.min(23, integer(root, "resetHour", 0)));
			settings.zoneId = ZoneId.of(text(root, "timeZone", "America/New_York"));
			settings.partyRange = positive(root, "partyRange", 1500);
			final Map<String, Template> templates = new HashMap<>();
			final NodeList nodes = root.getElementsByTagName("contract");
			for (int i = 0; i < nodes.getLength(); i++)
			{
				final Template template = parseTemplate((Element) nodes.item(i), settings);
				if (templates.put(template.id, template) != null)
				{
					throw new IllegalArgumentException("Duplicate contract template: " + template.id);
				}
				validateTemplate(template);
			}
			SETTINGS = settings;
			TEMPLATES = Collections.unmodifiableMap(templates);
			RUNTIME_ENABLED = settings.enabled;
			if (INSTANCE != null)
			{
				INSTANCE.registerKillListeners();
			}
			LOGGER.info("Loaded " + templates.size() + " hunt contract templates.");
			return true;
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "Could not load " + XML_FILE + ". Previous configuration remains active.", e);
			return false;
		}
	}

	private void registerKillListeners()
	{
		final Set<Integer> ids = new HashSet<>();
		for (Template template : TEMPLATES.values())
		{
			if (template.enabled && ((template.type == ContractType.KILL_NPC) || (template.type == ContractType.KILL_NPC_GROUP) || (template.type == ContractType.KILL_IN_ZONE) || (template.type == ContractType.KILL_RAID)))
			{
				ids.addAll(template.npcIds);
			}
		}
		addKillId(ids.stream().mapToInt(Integer::intValue).toArray());
	}

	@Override
	public void onKill(Npc npc, Player killer, boolean isSummon)
	{
		if (!isEnabled() || (npc == null) || (killer == null) || (npc.getSpawn() == null))
		{
			return;
		}
		final List<Player> eligible = new ArrayList<>();
		eligible.add(killer);
		if (killer.isInParty())
		{
			for (Player member : killer.getParty().getMembers())
			{
				if ((member != null) && (member != killer) && member.isOnline() && !member.isDead() && !member.isInStoreMode() && (member.getInstanceId() == npc.getInstanceId()) && LocationUtil.checkIfInRange(maxPartyRangeFor(npc.getId()), member, npc, true))
				{
					eligible.add(member);
				}
			}
		}
		for (Player player : eligible)
		{
			notifyKill(player, npc, player != killer);
		}
	}

	private void notifyKill(Player player, Npc npc, boolean shared)
	{
		final String guard = npc.getObjectId() + ":" + player.getAccountName().toLowerCase();
		if (!KILL_GUARD.add(guard))
		{
			return;
		}
		ThreadPool.schedule(() -> KILL_GUARD.remove(guard), 60000);
		progress(player, template -> template.matchesKill(player, npc) && (!shared || (template.partySharing && LocationUtil.checkIfInRange(template.partyRange, player, npc, true))), 1, "NPC:" + npc.getId());
	}

	public static void notifyDungeonComplete(Player player, String dungeonId)
	{
		if ((player != null) && (dungeonId != null))
		{
			getInstance().progress(player, template -> (template.type == ContractType.COMPLETE_DUNGEON) && template.dungeonIds.contains(dungeonId.toUpperCase()), 1, "DUNGEON:" + dungeonId);
		}
	}

	public static void notifyWantedParticipation(Player player, String wantedInstanceId)
	{
		if ((player != null) && (wantedInstanceId != null))
		{
			getInstance().progress(player, template -> template.type == ContractType.PARTICIPATE_WANTED, 1, "WANTED:" + wantedInstanceId);
		}
	}

	private void progress(Player player, TemplateMatcher matcher, int amount, String source)
	{
		if (!isEnabled() || (amount <= 0) || player.isDead() || !player.isOnline() || player.isInStoreMode())
		{
			return;
		}
		final String account = player.getAccountName();
		final ReentrantLock lock = lock(account);
		lock.lock();
		try (Connection con = DatabaseFactory.getConnection())
		{
			con.setAutoCommit(false);
			ensureOffers(con, player, true);
			final List<Contract> contracts = loadToday(con, account, true);
			for (Contract contract : contracts)
			{
				final Template template = TEMPLATES.get(contract.templateId);
				if ((contract.state != ContractState.ACTIVE) || (template == null) || !matcher.matches(template) || !template.levelValid(player.getLevel()))
				{
					continue;
				}
				final int next = Math.min(contract.target, contract.progress + amount);
				final boolean completed = next >= contract.target;
				try (PreparedStatement ps = con.prepareStatement("UPDATE account_hunt_contracts SET progress=?,contract_state=?,completed_at=IF(?='COMPLETED',CURRENT_TIMESTAMP,completed_at),last_character_id=? WHERE id=? AND contract_state='ACTIVE'"))
				{
					ps.setInt(1, next); ps.setString(2, completed ? "COMPLETED" : "ACTIVE"); ps.setString(3, completed ? "COMPLETED" : "ACTIVE"); ps.setInt(4, player.getObjectId()); ps.setLong(5, contract.id); ps.executeUpdate();
				}
				con.commit();
				if (completed)
				{
					player.sendMessage("Contract completed! Return to Hunt Contracts to claim your reward.");
				}
				else if ((next % 25 == 0) || (contract.target <= 10))
				{
					player.sendMessage("Contract progress: " + next + "/" + contract.target + ".");
				}
				if (SETTINGS.debug) LOGGER.info("account=" + account + " contract=" + contract.templateId + " progress=" + next + "/" + contract.target + " source=" + source);
				return;
			}
			con.rollback();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Progress failed for account=" + account + " source=" + source, e);
		}
		finally
		{
			lock.unlock();
		}
	}

	public List<Contract> getContracts(Player player)
	{
		final ReentrantLock lock = lock(player.getAccountName());
		lock.lock();
		try (Connection con = DatabaseFactory.getConnection())
		{
			con.setAutoCommit(false);
			ensureOffers(con, player, true);
			final List<Contract> result = loadToday(con, player.getAccountName(), false);
			con.commit();
			return result;
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Could not load contracts for " + player.getAccountName(), e);
			return Collections.emptyList();
		}
		finally
		{
			lock.unlock();
		}
	}

	public void accept(Player player, int offerSlot)
	{
		if (!isEnabled() || (offerSlot < 1) || (offerSlot > SETTINGS.offersPerDay))
		{
			player.sendMessage("Invalid contract request.");
			return;
		}
		final String account = player.getAccountName();
		final ReentrantLock lock = lock(account);
		if (!lock.tryLock())
		{
			player.sendMessage("Another request for this account is being processed.");
			return;
		}
		try (Connection con = DatabaseFactory.getConnection())
		{
			con.setAutoCommit(false);
			ensureOffers(con, player, true);
			final List<Contract> contracts = loadToday(con, account, true);
			final Contract selected = contracts.stream().filter(c -> c.offerSlot == offerSlot).findFirst().orElse(null);
			final long accepted = contracts.stream().filter(c -> c.state != ContractState.OFFERED && c.state != ContractState.EXPIRED && c.state != ContractState.ABANDONED).count();
			if ((selected == null) || (selected.state != ContractState.OFFERED))
			{
				player.sendMessage("This contract is no longer available."); con.rollback(); return;
			}
			if (accepted >= 2)
			{
				player.sendMessage("You have already selected your daily contracts."); con.rollback(); return;
			}
			if ((accepted == 1) && !player.hasPremiumStatus())
			{
				player.sendMessage("You need an active Premium status to accept a second contract."); con.rollback(); return;
			}
			final Template template = TEMPLATES.get(selected.templateId);
			if ((template == null) || !template.enabled || !template.levelValid(player.getLevel()) || (template.premiumOnly && !player.hasPremiumStatus()))
			{
				player.sendMessage("This contract is not compatible with your character."); con.rollback(); return;
			}
			try (PreparedStatement ps = con.prepareStatement("UPDATE account_hunt_contracts SET contract_state='ACTIVE',accepted=1,premium_slot=?,accepted_at=CURRENT_TIMESTAMP,last_character_id=? WHERE id=? AND contract_state='OFFERED'"))
			{
				ps.setBoolean(1, accepted == 1); ps.setInt(2, player.getObjectId()); ps.setLong(3, selected.id);
				if (ps.executeUpdate() != 1) { con.rollback(); player.sendMessage("The contract could not be accepted."); return; }
			}
			con.commit();
			player.sendMessage("Contract accepted: " + template.name + ".");
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Accept failed for account=" + account, e);
			player.sendMessage("The contract service is temporarily unavailable.");
		}
		finally { lock.unlock(); }
	}

	public void claim(Player player, int offerSlot)
	{
		if ((offerSlot < 1) || (offerSlot > SETTINGS.offersPerDay)) { player.sendMessage("Invalid contract request."); return; }
		final String account = player.getAccountName();
		final ReentrantLock lock = lock(account);
		if (!lock.tryLock()) { player.sendMessage("Another request for this account is being processed."); return; }
		final List<RewardItem> delivered = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection())
		{
			con.setAutoCommit(false);
			ensureWallet(con, account);
			final List<Contract> contracts = loadToday(con, account, true);
			final Contract contract = contracts.stream().filter(c -> c.offerSlot == offerSlot).findFirst().orElse(null);
			if ((contract == null) || (contract.state != ContractState.COMPLETED)) { player.sendMessage("This contract is not ready to claim."); con.rollback(); return; }
			final Template template = TEMPLATES.get(contract.templateId);
			if ((template == null) || !canReceive(player, template.rewards)) { con.rollback(); return; }
			final long before = wallet(con, account, true).huntMarks;
			final long wantedBefore = wallet(con, account, false).wantedSeals;
			if ((template.huntMarks < 0) || (before > (Long.MAX_VALUE - template.huntMarks))) { con.rollback(); return; }
			for (RewardItem reward : template.rewards)
			{
				final Item item = player.addItem(ItemProcessType.REWARD, reward.itemId, reward.count, player, true);
				if (item == null) throw new SQLException("Item delivery failed: " + reward.itemId);
				delivered.add(reward);
			}
			try (PreparedStatement ps = con.prepareStatement("UPDATE account_activity_wallet SET hunt_marks=hunt_marks+? WHERE account_name=?")) { ps.setLong(1, template.huntMarks); ps.setString(2, account); ps.executeUpdate(); }
			insertWalletHistory(con, account, player, template.huntMarks, before, before + template.huntMarks, "HUNT_CONTRACT", template.id);
			if (template.wantedSeals > 0)
			{
				try (PreparedStatement ps = con.prepareStatement("UPDATE account_activity_wallet SET wanted_seals=wanted_seals+? WHERE account_name=?")) { ps.setLong(1, template.wantedSeals); ps.setString(2, account); ps.executeUpdate(); }
				insertCurrencyHistory(con, account, player, "WANTED_SEALS", template.wantedSeals, wantedBefore, wantedBefore + template.wantedSeals, "HUNT_CONTRACT", template.id);
			}
			try (PreparedStatement ps = con.prepareStatement("UPDATE account_hunt_contracts SET contract_state='CLAIMED',claimed_at=CURRENT_TIMESTAMP,last_character_id=? WHERE id=? AND contract_state='COMPLETED'")) { ps.setInt(1, player.getObjectId()); ps.setLong(2, contract.id); if (ps.executeUpdate() != 1) throw new SQLException("Concurrent claim rejected"); }
			insertContractHistory(con, player, contract, template, "DELIVERED");
			con.commit();
			MegaraxLivingChronicle.recordEvent(player, "CONTRACT", rewardDate() + ":" + offerSlot + ":" + template.id, "Contrato cumplido: " + template.name, "Recompensa reclamada con " + template.huntMarks + " Hunt Marks.", 2, false);
			player.sendMessage("Contract reward successfully claimed.");
			LOGGER.info("account=" + account + " character=" + player.getName() + " contract=" + template.id + " marks=" + template.huntMarks + " result=SUCCESS");
		}
		catch (Exception e)
		{
			for (RewardItem reward : delivered) player.destroyItemByItemId(ItemProcessType.DESTROY, reward.itemId, reward.count, player, false);
			LOGGER.log(Level.WARNING, "Claim failed for account=" + account, e);
			player.sendMessage("The reward could not be delivered. Nothing was claimed.");
		}
		finally { lock.unlock(); }
	}

	public Wallet getWallet(String account)
	{
		try (Connection con = DatabaseFactory.getConnection()) { ensureWallet(con, account); return wallet(con, account, false); }
		catch (SQLException e) { return new Wallet(); }
	}

	public static boolean changeHuntMarks(Player actor, String account, long amount, String source)
	{
		final ReentrantLock lock = lock(account); lock.lock();
		try (Connection con = DatabaseFactory.getConnection())
		{
			con.setAutoCommit(false); ensureWallet(con, account); final Wallet current = wallet(con, account, true);
			if ((amount < 0) && (current.huntMarks < -amount)) { con.rollback(); return false; }
			final long after = current.huntMarks + amount;
			try (PreparedStatement ps = con.prepareStatement("UPDATE account_activity_wallet SET hunt_marks=? WHERE account_name=?")) { ps.setLong(1, after); ps.setString(2, account); ps.executeUpdate(); }
			insertWalletHistory(con, account, actor, amount, current.huntMarks, after, source, "ADMIN"); con.commit(); return true;
		}
		catch (SQLException e) { LOGGER.log(Level.WARNING, "Wallet update failed", e); return false; }
		finally { lock.unlock(); }
	}

	public static boolean changeWantedSeals(Player actor, String account, long amount, String source, String reference)
	{
		final ReentrantLock lock = lock(account); lock.lock();
		try (Connection con = DatabaseFactory.getConnection())
		{
			con.setAutoCommit(false); ensureWallet(con, account); final Wallet current = wallet(con, account, true);
			if ((amount < 0) && (current.wantedSeals < -amount)) { con.rollback(); return false; }
			final long after = current.wantedSeals + amount;
			try (PreparedStatement ps = con.prepareStatement("UPDATE account_activity_wallet SET wanted_seals=? WHERE account_name=?")) { ps.setLong(1, after); ps.setString(2, account); ps.executeUpdate(); }
			insertCurrencyHistory(con, account, actor, "WANTED_SEALS", amount, current.wantedSeals, after, source, reference); con.commit(); return true;
		}
		catch (SQLException e) { LOGGER.log(Level.WARNING, "Wanted Seals update failed", e); return false; }
		finally { lock.unlock(); }
	}

	public static long addWantedSeals(Connection con, Player actor, String account, long amount, String source, String reference) throws SQLException
	{
		if (amount < 0) throw new SQLException("Negative Wanted Seals award");
		ensureWallet(con, account);
		final Wallet current = wallet(con, account, true);
		if (current.wantedSeals > (Long.MAX_VALUE - amount)) throw new SQLException("Wanted Seals overflow");
		final long after = current.wantedSeals + amount;
		try (PreparedStatement ps = con.prepareStatement("UPDATE account_activity_wallet SET wanted_seals=? WHERE account_name=?")) { ps.setLong(1, after); ps.setString(2, account); ps.executeUpdate(); }
		insertCurrencyHistory(con, account, actor, "WANTED_SEALS", amount, current.wantedSeals, after, source, reference);
		return after;
	}

	public static void setEnabled(boolean enabled) { RUNTIME_ENABLED = enabled; }
	public static boolean isEnabled() { return RUNTIME_ENABLED && SETTINGS.enabled; }
	public static LocalDate rewardDate()
	{
		return ZonedDateTime.now(SETTINGS.zoneId).minusHours(SETTINGS.resetHour).toLocalDate();
	}
	public static String nextResetText()
	{
		final ZonedDateTime now = ZonedDateTime.now(SETTINGS.zoneId);
		ZonedDateTime next = now.toLocalDate().atTime(SETTINGS.resetHour, 0).atZone(SETTINGS.zoneId);
		if (!next.isAfter(now)) next = next.plusDays(1);
		final long minutes = java.time.Duration.between(now, next).toMinutes();
		return (minutes / 60) + "h " + (minutes % 60) + "m";
	}

	public Template getTemplate(String id) { return TEMPLATES.get(id); }
	public Collection<Template> getTemplates() { return TEMPLATES.values(); }

	private static void ensureOffers(Connection con, Player player, boolean lock) throws SQLException
	{
		final String account = player.getAccountName(); final LocalDate today = rewardDate();
		try (PreparedStatement ps = con.prepareStatement("INSERT INTO account_hunt_contract_history (account_name,character_id,contract_template_id,reset_date,final_progress,final_state,hunt_marks,items_delivered,premium_slot,accepted_at,completed_at,claimed_at,result) SELECT account_name,last_character_id,contract_template_id,reset_date,progress,'EXPIRED',0,'',premium_slot,accepted_at,completed_at,NULL,'DAILY_RESET' FROM account_hunt_contracts WHERE account_name=? AND reset_date<? AND contract_state IN ('OFFERED','ACTIVE','COMPLETED')"))
		{
			ps.setString(1, account);
			ps.setDate(2, Date.valueOf(today));
			ps.executeUpdate();
		}
		try (PreparedStatement ps = con.prepareStatement("UPDATE account_hunt_contracts SET contract_state='EXPIRED' WHERE account_name=? AND reset_date<? AND contract_state IN ('OFFERED','ACTIVE','COMPLETED')")) { ps.setString(1, account); ps.setDate(2, Date.valueOf(today)); ps.executeUpdate(); }
		if (!loadToday(con, account, lock).isEmpty()) return;
		final List<Template> pool = new ArrayList<>();
		for (Template t : TEMPLATES.values()) if (t.enabled && (!t.premiumOnly || player.hasPremiumStatus()) && t.levelValid(player.getLevel()) && (t.type != ContractType.PARTICIPATE_WANTED)) pool.add(t);
		if (pool.size() < SETTINGS.offersPerDay) throw new SQLException("Not enough compatible contract templates");
		for (int slot = 1; slot <= SETTINGS.offersPerDay; slot++)
		{
			final Template selected = weightedRemove(pool);
			try (PreparedStatement ps = con.prepareStatement("INSERT INTO account_hunt_contracts (account_name,reset_date,offer_slot,contract_template_id,target_count,last_character_id) VALUES (?,?,?,?,?,?)")) { ps.setString(1, account); ps.setDate(2, Date.valueOf(today)); ps.setInt(3, slot); ps.setString(4, selected.id); ps.setInt(5, selected.targetCount); ps.setInt(6, player.getObjectId()); ps.executeUpdate(); }
		}
	}

	private static Template weightedRemove(List<Template> pool)
	{
		int total = 0; for (Template t : pool) total += t.weight;
		int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
		for (int i = 0; i < pool.size(); i++) { roll -= pool.get(i).weight; if (roll < 0) return pool.remove(i); }
		return pool.remove(pool.size() - 1);
	}

	private static List<Contract> loadToday(Connection con, String account, boolean lock) throws SQLException
	{
		final List<Contract> result = new ArrayList<>();
		try (PreparedStatement ps = con.prepareStatement(lock ? SELECT_TODAY_LOCK : SELECT_TODAY))
		{
			ps.setString(1, account); ps.setDate(2, Date.valueOf(rewardDate()));
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					result.add(new Contract(rs));
				}
			}
		}
		return result;
	}

	private static void ensureWallet(Connection con, String account) throws SQLException { try (PreparedStatement ps = con.prepareStatement(INSERT_WALLET)) { ps.setString(1, account); ps.executeUpdate(); } }
	private static Wallet wallet(Connection con, String account, boolean lock) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(lock ? SELECT_WALLET_LOCK : SELECT_WALLET)) { ps.setString(1, account); try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return new Wallet(rs.getLong(1), rs.getLong(2), rs.getLong(3)); } }
		return new Wallet();
	}

	private static void insertWalletHistory(Connection con, String account, Player player, long amount, long before, long after, String source, String reference) throws SQLException
	{
		insertCurrencyHistory(con, account, player, "HUNT_MARKS", amount, before, after, source, reference);
	}

	private static void insertCurrencyHistory(Connection con, String account, Player player, String currency, long amount, long before, long after, String source, String reference) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement("INSERT INTO account_activity_wallet_history (account_name,character_id,currency_type,amount,balance_before,balance_after,source,reference_id) VALUES (?,?,?,?,?,?,?,?)")) { ps.setString(1, account); ps.setInt(2, player.getObjectId()); ps.setString(3, currency); ps.setLong(4, amount); ps.setLong(5, before); ps.setLong(6, after); ps.setString(7, source); ps.setString(8, reference); ps.executeUpdate(); }
	}

	private static void insertContractHistory(Connection con, Player player, Contract contract, Template template, String result) throws SQLException
	{
		final StringBuilder items = new StringBuilder(); for (RewardItem r : template.rewards) { if (items.length() > 0) items.append(','); items.append(r.itemId).append('x').append(r.count); }
		try (PreparedStatement ps = con.prepareStatement("INSERT INTO account_hunt_contract_history (account_name,character_id,contract_template_id,reset_date,final_progress,final_state,hunt_marks,items_delivered,premium_slot,accepted_at,completed_at,claimed_at,result) VALUES (?,?,?,?,?,'CLAIMED',?,?,?,?,?,?,?)")) { ps.setString(1, player.getAccountName()); ps.setInt(2, player.getObjectId()); ps.setString(3, template.id); ps.setDate(4, Date.valueOf(rewardDate())); ps.setInt(5, contract.target); ps.setLong(6, template.huntMarks); ps.setString(7, items.toString()); ps.setBoolean(8, contract.premiumSlot); ps.setTimestamp(9, contract.acceptedAt); ps.setTimestamp(10, contract.completedAt); ps.setTimestamp(11, new java.sql.Timestamp(System.currentTimeMillis())); ps.setString(12, result); ps.executeUpdate(); }
	}

	private static boolean canReceive(Player player, List<RewardItem> rewards)
	{
		int slots = 0; long weight = 0;
		for (RewardItem reward : rewards)
		{
			final ItemTemplate item = ItemData.getInstance().getTemplate(reward.itemId);
			if (item == null || reward.count <= 0) { player.sendMessage("A contract reward is configured incorrectly."); return false; }
			if (!item.isStackable() || (player.getInventory().getItemByItemId(reward.itemId) == null)) slots += item.isStackable() ? 1 : reward.count;
			weight += item.getWeight() * (long) reward.count;
		}
		if (!player.getInventory().validateCapacity(slots) || !player.getInventory().validateWeight(weight)) { player.sendMessage("Free inventory space before claiming this reward."); return false; }
		return true;
	}

	private static void initTables()
	{
		final String[] sql = {
			"CREATE TABLE IF NOT EXISTS account_activity_wallet (account_name VARCHAR(45) NOT NULL,hunt_marks BIGINT UNSIGNED NOT NULL DEFAULT 0,wanted_seals BIGINT UNSIGNED NOT NULL DEFAULT 0,war_contribution BIGINT UNSIGNED NOT NULL DEFAULT 0,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,PRIMARY KEY(account_name)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci",
			"CREATE TABLE IF NOT EXISTS account_activity_wallet_history (id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,account_name VARCHAR(45) NOT NULL,character_id INT NOT NULL,currency_type VARCHAR(24) NOT NULL,amount BIGINT NOT NULL,balance_before BIGINT UNSIGNED NOT NULL,balance_after BIGINT UNSIGNED NOT NULL,source VARCHAR(48) NOT NULL,reference_id VARCHAR(64) NOT NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(id),KEY account_created(account_name,created_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci",
			"CREATE TABLE IF NOT EXISTS account_hunt_contracts (id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,account_name VARCHAR(45) NOT NULL,reset_date DATE NOT NULL,offer_slot TINYINT UNSIGNED NOT NULL,contract_template_id VARCHAR(64) NOT NULL,contract_state ENUM('OFFERED','ACTIVE','COMPLETED','CLAIMED','EXPIRED','ABANDONED') NOT NULL DEFAULT 'OFFERED',progress INT UNSIGNED NOT NULL DEFAULT 0,target_count INT UNSIGNED NOT NULL,accepted TINYINT(1) NOT NULL DEFAULT 0,premium_slot TINYINT(1) NOT NULL DEFAULT 0,generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,accepted_at TIMESTAMP NULL,completed_at TIMESTAMP NULL,claimed_at TIMESTAMP NULL,last_character_id INT NOT NULL DEFAULT 0,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,PRIMARY KEY(id),UNIQUE KEY account_day_slot(account_name,reset_date,offer_slot),KEY account_state(account_name,contract_state)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci",
			"CREATE TABLE IF NOT EXISTS account_hunt_contract_history (id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,account_name VARCHAR(45) NOT NULL,character_id INT NOT NULL,contract_template_id VARCHAR(64) NOT NULL,reset_date DATE NOT NULL,final_progress INT UNSIGNED NOT NULL,final_state VARCHAR(16) NOT NULL,hunt_marks BIGINT UNSIGNED NOT NULL DEFAULT 0,items_delivered VARCHAR(512) NOT NULL DEFAULT '',premium_slot TINYINT(1) NOT NULL DEFAULT 0,accepted_at TIMESTAMP NULL,completed_at TIMESTAMP NULL,claimed_at TIMESTAMP NULL,result VARCHAR(32) NOT NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(id),KEY account_created(account_name,created_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci" };
		try (Connection con = DatabaseFactory.getConnection()) { for (String query : sql) try (PreparedStatement ps = con.prepareStatement(query)) { ps.execute(); } }
		catch (SQLException e) { LOGGER.log(Level.SEVERE, "Could not initialize hunt contract tables.", e); }
	}

	private static Template parseTemplate(Element e, Settings settings)
	{
		final Template t = new Template(); t.id = text(e, "id", ""); t.enabled = bool(e, "enabled", true); t.weight = positive(e, "weight", 1); t.type = ContractType.valueOf(text(e, "type", "KILL_NPC")); t.targetCount = positive(e, "targetCount", 1); t.minimumLevel = positive(e, "minimumLevel", 1); t.maximumLevel = positive(e, "maximumLevel", 85); t.premiumOnly = bool(e, "premiumOnly", false); t.partySharing = bool(e, "partySharing", false); t.partyRange = positive(e, "partyRange", settings.partyRange); t.huntMarks = positiveOrZero(e, "huntMarks", 0); t.wantedSeals = positiveOrZero(e, "wantedSeals", 0); t.shortName = text(e, "shortName", t.id); t.name = text(e, "name", t.id); t.description = text(e, "description", "Daily hunt contract"); t.icon = text(e, "icon", "icon.etc_letter_red_i00"); t.announceCompletion = bool(e, "announceCompletion", true);
		for (String tag : new String[] { "npcs", "raids" }) { final NodeList list = e.getElementsByTagName(tag); if (list.getLength() > 0) t.npcIds.addAll(intSet(((Element) list.item(0)).getAttribute("ids"))); }
		final NodeList zones = e.getElementsByTagName("zones"); if (zones.getLength() > 0) t.zoneIds.addAll(intSet(((Element) zones.item(0)).getAttribute("ids")));
		final NodeList dungeons = e.getElementsByTagName("dungeons"); if (dungeons.getLength() > 0) for (String id : ((Element) dungeons.item(0)).getAttribute("ids").split(",")) if (!id.trim().isEmpty()) t.dungeonIds.add(id.trim().toUpperCase());
		final NodeList rewards = e.getElementsByTagName("reward"); for (int i = 0; i < rewards.getLength(); i++) { final Element reward = (Element) rewards.item(i); t.rewards.add(new RewardItem(integer(reward, "itemId", 0), positive(reward, "count", 1))); }
		return t;
	}

	private static void validateTemplate(Template t)
	{
		if (t.id.isEmpty() || (t.minimumLevel > t.maximumLevel)) throw new IllegalArgumentException("Invalid template " + t.id);
		for (int npcId : t.npcIds) if (NpcData.getInstance().getTemplate(npcId) == null) throw new IllegalArgumentException("Template " + t.id + " has missing NPC " + npcId);
		for (int zoneId : t.zoneIds) if (ZoneManager.getInstance().getZoneById(zoneId) == null) throw new IllegalArgumentException("Template " + t.id + " has missing zone " + zoneId);
		for (RewardItem reward : t.rewards) if ((reward.itemId <= 0) || (ItemData.getInstance().getTemplate(reward.itemId) == null)) throw new IllegalArgumentException("Template " + t.id + " has missing reward item " + reward.itemId);
		if (t.enabled && (t.weight <= 0)) throw new IllegalArgumentException("Enabled template has zero weight: " + t.id);
	}

	private int maxPartyRangeFor(int npcId) { int range = SETTINGS.partyRange; for (Template t : TEMPLATES.values()) if (t.npcIds.contains(npcId)) range = Math.max(range, t.partyRange); return range; }
	private static ReentrantLock lock(String account) { return ACCOUNT_LOCKS.computeIfAbsent(account.toLowerCase(), key -> new ReentrantLock()); }
	private static Set<Integer> intSet(String value) { final Set<Integer> result = new HashSet<>(); for (String token : value.split(",")) if (!token.trim().isEmpty()) result.add(Integer.parseInt(token.trim())); return result; }
	private static String text(Element e, String name, String fallback) { final String value = e.getAttribute(name); return value == null || value.isEmpty() ? fallback : value; }
	private static int integer(Element e, String name, int fallback) { try { return Integer.parseInt(text(e, name, Integer.toString(fallback))); } catch (NumberFormatException ex) { return fallback; } }
	private static int positive(Element e, String name, int fallback) { return Math.max(1, integer(e, name, fallback)); }
	private static int positiveOrZero(Element e, String name, int fallback) { return Math.max(0, integer(e, name, fallback)); }
	private static boolean bool(Element e, String name, boolean fallback) { return Boolean.parseBoolean(text(e, name, Boolean.toString(fallback))); }

	public static void main(String[] args) { getInstance(); }

	private interface TemplateMatcher { boolean matches(Template template); }
	private static class Settings { boolean enabled = true; boolean debug; int offersPerDay = 3; int resetHour; int partyRange = 1500; ZoneId zoneId = ZoneId.of("America/New_York"); }
	public enum ContractType { KILL_NPC, KILL_NPC_GROUP, KILL_IN_ZONE, COMPLETE_DUNGEON, KILL_RAID, PARTICIPATE_WANTED }
	public enum ContractState { OFFERED, ACTIVE, COMPLETED, CLAIMED, EXPIRED, ABANDONED }

	public static class Template
	{
		public String id, shortName, name, description, icon; public boolean enabled, premiumOnly, partySharing, announceCompletion; public int weight, targetCount, minimumLevel, maximumLevel, partyRange; public long huntMarks, wantedSeals; public ContractType type; public final Set<Integer> npcIds = new HashSet<>(); public final Set<Integer> zoneIds = new HashSet<>(); public final Set<String> dungeonIds = new HashSet<>(); public final List<RewardItem> rewards = new ArrayList<>();
		boolean levelValid(int level) { return (level >= minimumLevel) && (level <= maximumLevel); }
		boolean matchesKill(Player player, Npc npc) { if (!npcIds.contains(npc.getId())) return false; if (!zoneIds.isEmpty()) { for (int id : zoneIds) { final ZoneType zone = ZoneManager.getInstance().getZoneById(id); if ((zone != null) && zone.isInsideZone(player)) return true; } return false; } return true; }
	}

	public static class RewardItem { public final int itemId, count; RewardItem(int itemId, int count) { this.itemId = itemId; this.count = count; } }
	public static class Wallet { public long huntMarks, wantedSeals, warContribution; Wallet() {} Wallet(long h, long w, long c) { huntMarks = h; wantedSeals = w; warContribution = c; } }
	public static class Contract
	{
		public long id; public int offerSlot, progress, target; public String templateId; public ContractState state; public boolean premiumSlot; public java.sql.Timestamp acceptedAt, completedAt;
		Contract(ResultSet rs) throws SQLException { id = rs.getLong("id"); offerSlot = rs.getInt("offer_slot"); templateId = rs.getString("contract_template_id"); state = ContractState.valueOf(rs.getString("contract_state")); progress = rs.getInt("progress"); target = rs.getInt("target_count"); premiumSlot = rs.getBoolean("premium_slot"); acceptedAt = rs.getTimestamp("accepted_at"); completedAt = rs.getTimestamp("completed_at"); }
	}
}
