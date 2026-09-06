package custom.MegaraxWantedMonsters;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.Spawn;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.ListenerRegisterType;
import org.l2jmobius.gameserver.model.events.annotations.RegisterEvent;
import org.l2jmobius.gameserver.model.events.annotations.RegisterType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageReceived;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDeath;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.util.Broadcast;
import org.l2jmobius.gameserver.util.LocationUtil;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import ai.AbstractNpcAI;
import custom.MegaraxHuntContracts.MegaraxHuntContracts;
import custom.MegaraxLivingChronicle.MegaraxLivingChronicle;

/** Dynamic account-safe Wanted Monster manager. Restart policy: CANCEL_ACTIVE. */
public class MegaraxWantedMonsters extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger("megarax_wanted_monsters");
	private static final File CONFIG = new File("data/MegaraxWantedMonsters.xml");
	private static final Map<String, Object> ACCOUNT_LOCKS = new ConcurrentHashMap<>();
	private static final Map<String, Long> TELEPORT_COOLDOWNS = new ConcurrentHashMap<>();
	private static volatile MegaraxWantedMonsters INSTANCE;
	private static volatile Settings SETTINGS = new Settings();
	private static volatile Map<String, WantedTemplate> TEMPLATES = Collections.emptyMap();
	private static volatile Map<String, WantedZone> ZONES = Collections.emptyMap();
	private static volatile boolean RUNTIME_ENABLED = true;
	private final Object _encounterLock = new Object();
	private volatile Encounter _active;
	private volatile String _lastTemplate = "";
	private volatile String _lastZone = "";
	private final Map<String, Long> _templateCooldowns = new ConcurrentHashMap<>();
	private final Map<String, Long> _zoneCooldowns = new ConcurrentHashMap<>();

	private MegaraxWantedMonsters()
	{
		INSTANCE = this;
		initTables();
		cancelRestartedInstances();
		reloadConfig();
		scheduleNext(60000);
	}

	public static MegaraxWantedMonsters getInstance()
	{
		if (INSTANCE == null) synchronized (MegaraxWantedMonsters.class) { if (INSTANCE == null) new MegaraxWantedMonsters(); }
		return INSTANCE;
	}

	public static synchronized boolean reloadConfig()
	{
		try
		{
			final Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(CONFIG).getDocumentElement();
			final Settings s = new Settings();
			s.enabled = bool(root, "enabled", true); s.minDelay = positive(root, "minSpawnDelayMinutes", 45); s.maxDelay = Math.max(s.minDelay, positive(root, "maxSpawnDelayMinutes", 90)); s.maxActive = positive(root, "maxActive", 1); s.rewardRange = positive(root, "rewardRange", 2000); s.teleportCooldown = positive(root, "teleportCooldownSeconds", 300); s.leashSeconds = positive(root, "leashCheckSeconds", 10); s.restartPolicy = text(root, "restartPolicy", "CANCEL_ACTIVE");
			final Map<String, WantedZone> zones = new HashMap<>();
			final NodeList zoneNodes = root.getElementsByTagName("zone");
			for (int i = 0; i < zoneNodes.getLength(); i++)
			{
				final Element e = (Element) zoneNodes.item(i); if (!e.getParentNode().getNodeName().equals("zones")) continue;
				final WantedZone z = new WantedZone(e); if (zones.put(z.id, z) != null) throw new IllegalArgumentException("Duplicate zone " + z.id); if ((z.zoneId > 0) && (ZoneManager.getInstance().getZoneById(z.zoneId) == null)) throw new IllegalArgumentException("Missing zoneId " + z.zoneId);
				final NodeList spawns = e.getElementsByTagName("spawn"); for (int j = 0; j < spawns.getLength(); j++) z.spawns.add(new Point((Element) spawns.item(j))); if (z.spawns.isEmpty()) throw new IllegalArgumentException("Zone without spawn " + z.id);
			}
			final Map<String, WantedTemplate> templates = new HashMap<>();
			final NodeList wantedNodes = root.getElementsByTagName("wanted");
			for (int i = 0; i < wantedNodes.getLength(); i++)
			{
				final WantedTemplate t = new WantedTemplate((Element) wantedNodes.item(i)); if (templates.put(t.id, t) != null) throw new IllegalArgumentException("Duplicate template " + t.id); validate(t, zones);
			}
			SETTINGS = s; ZONES = Collections.unmodifiableMap(zones); TEMPLATES = Collections.unmodifiableMap(templates); RUNTIME_ENABLED = s.enabled;
			LOGGER.info("Loaded " + templates.size() + " Wanted templates and " + zones.size() + " zones. Restart policy=" + s.restartPolicy);
			return true;
		}
		catch (Exception e) { LOGGER.log(Level.SEVERE, "Wanted configuration reload failed.", e); return false; }
	}

	private static void validate(WantedTemplate t, Map<String, WantedZone> zones)
	{
		if (NpcData.getInstance().getTemplate(t.npcId) == null) throw new IllegalArgumentException("Missing wanted NPC " + t.npcId);
		for (String zone : t.zones) if (!zones.containsKey(zone)) throw new IllegalArgumentException("Missing allowed zone " + zone);
		final Set<String> tiers = new HashSet<>(); for (Tier tier : t.tiers) { if (!tiers.add(tier.name)) throw new IllegalArgumentException("Duplicate tier " + tier.name); for (RewardItem item : tier.items) if (ItemData.getInstance().getTemplate(item.id) == null) throw new IllegalArgumentException("Missing reward item " + item.id); }
		for (SpecialSkill skill : t.skills) if (SkillData.getInstance().getSkill(skill.id, skill.level) == null) throw new IllegalArgumentException("Missing skill " + skill.id + "-" + skill.level);
	}

	@RegisterEvent(EventType.ON_CREATURE_DAMAGE_RECEIVED)
	@RegisterType(ListenerRegisterType.GLOBAL)
	public void onDamage(OnCreatureDamageReceived event)
	{
		final Encounter encounter = _active;
		if ((encounter == null) || encounter.finished.get() || (event == null) || (event.getTarget() != encounter.npc) || (event.getDamage() <= 0)) return;
		final Player player = owner(event.getAttacker()); if ((player == null) || !player.isOnline()) return;
		final long valid = Math.max(0, Math.min((long) event.getDamage(), (long) Math.ceil(encounter.npc.getCurrentHp()))); if (valid <= 0) return;
		final Contribution contribution = encounter.contributions.computeIfAbsent(player.getAccountName().toLowerCase(), key -> new Contribution(player)); contribution.player = player; contribution.damage = safeAdd(contribution.damage, valid); encounter.totalDamage = safeAdd(encounter.totalDamage, valid); persistContribution(encounter, contribution);
		if (!encounter.npc.isCastingNow()) for (SpecialSkill configured : encounter.template.skills) if (ThreadLocalRandom.current().nextInt(100) < configured.chance) { final Skill skill = SkillData.getInstance().getSkill(configured.id, configured.level); encounter.npc.setTarget(player); if ((skill != null) && encounter.npc.checkDoCastConditions(skill)) encounter.npc.doCast(skill); break; }
	}

	@RegisterEvent(EventType.ON_CREATURE_DEATH)
	@RegisterType(ListenerRegisterType.GLOBAL)
	public void onDeath(OnCreatureDeath event)
	{
		final Encounter encounter = _active; if ((encounter != null) && (event != null) && (event.getTarget() == encounter.npc)) finish(encounter, true, owner(event.getAttacker()), "DEFEATED");
	}

	public boolean forceSpawn(String templateId, String zoneId)
	{
		synchronized (_encounterLock)
		{
			if ((_active != null) || !isEnabled()) return false;
			WantedTemplate template = templateId == null || templateId.isEmpty() ? chooseTemplate() : TEMPLATES.get(templateId); if ((template == null) || !template.enabled) return false;
			WantedZone zone = zoneId == null || zoneId.isEmpty() ? chooseZone(template) : ZONES.get(zoneId); if ((zone == null) || !template.zones.contains(zone.id)) return false;
			return spawn(template, zone);
		}
	}

	private boolean spawn(WantedTemplate template, WantedZone zone)
	{
		try
		{
			final Point point = zone.spawns.get(ThreadLocalRandom.current().nextInt(zone.spawns.size())); final Spawn spawn = new Spawn(template.npcId); spawn.setXYZ(point.x, point.y, point.z); spawn.stopRespawn(); final Npc npc = spawn.doSpawn(true); npc.setLethalable(false); npc.asAttackable().setCanReturnToSpawnPoint(false);
			final long id = insertInstance(template, zone, point, npc); final Encounter encounter = new Encounter(id, template, zone, point, npc); _active = encounter; _lastTemplate = template.id; _lastZone = zone.id; _templateCooldowns.put(template.id, System.currentTimeMillis() + (template.cooldown * 60000L)); _zoneCooldowns.put(zone.id, System.currentTimeMillis() + (zone.cooldown * 60000L));
			if (template.announce) Broadcast.toAllOnlinePlayers("A Wanted Monster has appeared in " + zone.name + "!");
			ThreadPool.schedule(() -> warn(encounter), Math.max(1000, (template.lifetimeMinutes - 5L) * 60000)); ThreadPool.schedule(() -> finish(encounter, false, null, "ESCAPED"), template.lifetimeMinutes * 60000L); ThreadPool.schedule(() -> leash(encounter), SETTINGS.leashSeconds * 1000L); return true;
		}
		catch (Exception e) { LOGGER.log(Level.SEVERE, "Wanted spawn failed", e); return false; }
	}

	private void warn(Encounter e) { if ((_active == e) && !e.finished.get()) Broadcast.toAllOnlinePlayers(e.template.name + " will escape in 5 minutes!"); }
	private void leash(Encounter e)
	{
		if ((_active != e) || e.finished.get() || e.npc.isDead()) return;
		if (LocationUtil.calculateDistance(e.npc.getX(), e.npc.getY(), e.npc.getZ(), e.point.x, e.point.y, e.point.z, true, false) > e.template.leashRange) { e.npc.asAttackable().clearAggroList(); e.npc.setTarget(null); e.npc.teleToLocation(e.point.x, e.point.y, e.point.z); e.npc.setCurrentHp(Math.min(e.npc.getMaxHp(), e.npc.getCurrentHp() + (e.npc.getMaxHp() * e.template.leashHealPercent / 100.0))); }
		ThreadPool.schedule(() -> leash(e), SETTINGS.leashSeconds * 1000L);
	}

	private void finish(Encounter e, boolean defeated, Player killer, String reason)
	{
		if ((_active != e) || !e.finished.compareAndSet(false, true)) return;
		synchronized (_encounterLock)
		{
			try
			{
				if (defeated) createRewards(e); updateInstance(e, defeated ? "DEFEATED" : "ESCAPED", killer, reason); if (!defeated && !e.npc.isDead()) e.npc.deleteMe(); Broadcast.toAllOnlinePlayers(defeated ? e.template.name + " has been defeated!" : e.template.name + " has escaped from " + e.zone.name + ".");
			}
			finally { _active = null; scheduleNext(-1); }
		}
	}

	private void createRewards(Encounter e)
	{
		for (Contribution c : e.contributions.values())
		{
			final double percent = e.totalDamage <= 0 ? 0 : (c.damage * 100.0 / e.totalDamage); final Tier tier = e.template.tier(percent, c.damage); final Player p = c.player; final boolean eligible = (tier != null) && (c.damage >= e.template.minimumContribution) && (p != null) && p.isOnline() && !p.isDead() && !p.isInStoreMode() && !p.inObserverMode() && LocationUtil.checkIfInRange(SETTINGS.rewardRange, p, e.npc, true);
			persistFinalContribution(e, c, percent, tier, eligible);
			if (!eligible) continue;
			if (insertPending(e, c, tier))
			{
				MegaraxLivingChronicle.recordEvent(p, "WANTED", e.template.id, "Caceria compartida: " + e.template.name, "Participo desde " + e.zone.name + " con categoria " + tier.name + ".", 4, true);
				MegaraxHuntContracts.notifyWantedParticipation(p, Long.toString(e.id));
				claimPending(p);
			}
		}
	}

	public int claimPending(Player player)
	{
		final String account = player.getAccountName(); synchronized (ACCOUNT_LOCKS.computeIfAbsent(account.toLowerCase(), key -> new Object()))
		{
			int delivered = 0;
			try (Connection con = DatabaseFactory.getConnection())
			{
				con.setAutoCommit(false);
				try (PreparedStatement ps = con.prepareStatement("SELECT id,instance_id,template_id,tier,contribution,wanted_seals,item_rewards FROM wanted_monster_pending_rewards WHERE account_name=? AND status='PENDING' FOR UPDATE"))
				{
					ps.setString(1, account);
					try (ResultSet rs = ps.executeQuery())
					{
						while (rs.next())
						{
						final List<RewardItem> items = parseItems(rs.getString("item_rewards")); if (!canReceive(player, items)) continue; final List<RewardItem> added = new ArrayList<>();
						try { for (RewardItem r : items) { final Item item = player.addItem(ItemProcessType.REWARD, r.id, r.count, player, true); if (item == null) throw new IllegalStateException("Item delivery failed"); added.add(r); } MegaraxHuntContracts.addWantedSeals(con, player, account, rs.getLong("wanted_seals"), "WANTED_MONSTER", Long.toString(rs.getLong("instance_id"))); try (PreparedStatement up = con.prepareStatement("UPDATE wanted_monster_pending_rewards SET status='DELIVERED',delivered_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'")) { up.setLong(1, rs.getLong("id")); if (up.executeUpdate() != 1) throw new IllegalStateException("Double claim"); } insertRewardHistory(con, rs, account, player, "DELIVERED", ""); con.commit(); delivered++; }
						catch (Exception ex) { con.rollback(); for (RewardItem r : added) player.destroyItemByItemId(ItemProcessType.DESTROY, r.id, r.count, player, false); LOGGER.log(Level.WARNING, "Wanted delivery failed account=" + account, ex); }
						}
					}
				}
			}
			catch (Exception ex) { LOGGER.log(Level.WARNING, "Pending claim failed account=" + account, ex); }
			if (delivered > 0) player.sendMessage("Wanted reward successfully claimed."); else player.sendMessage("No deliverable Wanted reward. Free inventory space if a reward is pending."); return delivered;
		}
	}

	public boolean teleport(Player p)
	{
		final Encounter e = _active; if ((e == null) || !e.zone.teleport || p.isDead() || p.inObserverMode() || p.isInOlympiadMode() || p.isOnEvent() || p.getInstanceId() > 0 || p.isInsideZone(ZoneId.JAIL) || p.isInCombat() || (p.getPvpFlag() > 0)) { p.sendMessage("Wanted teleport is unavailable in your current state."); return false; }
		final long now = System.currentTimeMillis(); final String key = p.getObjectId() + ""; if (TELEPORT_COOLDOWNS.getOrDefault(key, 0L) > now) { p.sendMessage("Wanted teleport is on cooldown."); return false; } TELEPORT_COOLDOWNS.put(key, now + SETTINGS.teleportCooldown * 1000L); p.teleToLocation(e.zone.teleportX, e.zone.teleportY, e.zone.teleportZ); return true;
	}

	private void scheduleNext(long fixed) { if (!isEnabled()) return; final long delay = fixed >= 0 ? fixed : ThreadLocalRandom.current().nextLong(SETTINGS.minDelay, SETTINGS.maxDelay + 1L) * 60000L; ThreadPool.schedule(() -> { if ((_active == null) && !forceSpawn("", "")) scheduleNext(300000); }, delay); }
	private WantedTemplate chooseTemplate() { final long now=System.currentTimeMillis();final List<WantedTemplate> pool = new ArrayList<>(); for (WantedTemplate t : TEMPLATES.values()) if (t.enabled && !t.id.equals(_lastTemplate) && (_templateCooldowns.getOrDefault(t.id,0L)<=now) && hasAvailableZone(t,now)) pool.add(t); return weightedTemplate(pool); }
	private boolean hasAvailableZone(WantedTemplate t,long now){for(String id:t.zones)if(!id.equals(_lastZone)&&(_zoneCooldowns.getOrDefault(id,0L)<=now))return true;return false;}
	private WantedZone chooseZone(WantedTemplate t) { final long now=System.currentTimeMillis();final List<WantedZone> pool = new ArrayList<>(); for (String id : t.zones) if (!id.equals(_lastZone) && (_zoneCooldowns.getOrDefault(id,0L)<=now)) pool.add(ZONES.get(id)); return pool.isEmpty() ? null : pool.get(ThreadLocalRandom.current().nextInt(pool.size())); }
	private WantedTemplate weightedTemplate(List<WantedTemplate> pool) { if (pool.isEmpty()) return null; int total=0; for(WantedTemplate t:pool) total+=t.weight; int roll=ThreadLocalRandom.current().nextInt(Math.max(1,total)); for(WantedTemplate t:pool){roll-=t.weight;if(roll<0)return t;}return pool.get(0); }
	private Player owner(Creature c) { if (c == null) return null; if (c.isPlayer()) return c.asPlayer(); if (c.isSummon() && (c.asSummon().getOwner() != null)) return c.asSummon().getOwner(); return null; }
	private long safeAdd(long a,long b){return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;}

	public EncounterView getActiveView(Player p) { final Encounter e=_active; if(e==null)return null; final Contribution c=e.contributions.get(p.getAccountName().toLowerCase()); return new EncounterView(e,c==null?0:c.damage); }
	public String getLastResult() { try(Connection con=DatabaseFactory.getConnection();PreparedStatement ps=con.prepareStatement("SELECT template_id,state,zone_id FROM wanted_monster_instances WHERE state<>'ACTIVE' ORDER BY instance_id DESC LIMIT 1");ResultSet rs=ps.executeQuery()){return rs.next()?rs.getString(1)+" - "+rs.getString(2)+" in "+rs.getString(3):"No previous Wanted encounter.";}catch(Exception e){return "History unavailable.";} }
	public int getPendingCount(String account){try(Connection c=DatabaseFactory.getConnection();PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM wanted_monster_pending_rewards WHERE account_name=? AND status='PENDING'")){ps.setString(1,account);try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getInt(1):0;}}catch(Exception e){return 0;}}
	public String getStatusText(){Encounter e=_active;return e==null?"No active Wanted Monster.":"ACTIVE id="+e.id+" template="+e.template.id+" zone="+e.zone.id+" participants="+e.contributions.size()+" damage="+e.totalDamage;}
	public List<String> getContributionText(){Encounter e=_active;List<String> out=new ArrayList<>();if(e!=null)for(Map.Entry<String,Contribution>x:e.contributions.entrySet())out.add(x.getKey()+" damage="+x.getValue().damage);return out;}
	public static boolean isEnabled(){return RUNTIME_ENABLED&&SETTINGS.enabled;} public static void setEnabled(boolean value){RUNTIME_ENABLED=value;}
	public void escape(){final Encounter e=_active;if(e!=null)finish(e,false,null,"ADMIN_ESCAPE");} public void simulateDeath(Player admin){final Encounter e=_active;if(e!=null)finish(e,true,admin,"ADMIN_DEFEAT");}

	private long insertInstance(WantedTemplate t,WantedZone z,Point p,Npc n)throws Exception{try(Connection c=DatabaseFactory.getConnection();PreparedStatement ps=c.prepareStatement("INSERT INTO wanted_monster_instances(template_id,npc_object_id,npc_id,category,zone_id,spawn_x,spawn_y,spawn_z,state,spawned_at,expires_at) VALUES(?,?,?,?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP,?)",Statement.RETURN_GENERATED_KEYS)){ps.setString(1,t.id);ps.setInt(2,n.getObjectId());ps.setInt(3,t.npcId);ps.setString(4,t.category);ps.setString(5,z.id);ps.setInt(6,p.x);ps.setInt(7,p.y);ps.setInt(8,p.z);ps.setTimestamp(9,new Timestamp(System.currentTimeMillis()+t.lifetimeMinutes*60000L));ps.executeUpdate();try(ResultSet rs=ps.getGeneratedKeys()){if(rs.next())return rs.getLong(1);}}throw new IllegalStateException("No instance id");}
	private void updateInstance(Encounter e,String state,Player killer,String reason){try(Connection c=DatabaseFactory.getConnection();PreparedStatement ps=c.prepareStatement("UPDATE wanted_monster_instances SET state=?,killed_at=IF(?='DEFEATED',CURRENT_TIMESTAMP,killed_at),killed_by_character_id=?,total_damage=?,failure_reason=? WHERE instance_id=? AND state='ACTIVE'")){ps.setString(1,state);ps.setString(2,state);ps.setInt(3,killer==null?0:killer.getObjectId());ps.setLong(4,e.totalDamage);ps.setString(5,reason);ps.setLong(6,e.id);ps.executeUpdate();}catch(Exception ex){LOGGER.log(Level.WARNING,"Instance finalization SQL failed",ex);}}
	private void persistContribution(Encounter e,Contribution c){try(Connection con=DatabaseFactory.getConnection();PreparedStatement ps=con.prepareStatement("INSERT INTO wanted_monster_contributions(instance_id,account_name,character_id,damage) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE character_id=VALUES(character_id),damage=VALUES(damage)")){ps.setLong(1,e.id);ps.setString(2,c.player.getAccountName());ps.setInt(3,c.player.getObjectId());ps.setLong(4,c.damage);ps.executeUpdate();}catch(Exception ex){LOGGER.warning("Contribution SQL failed: "+ex.getMessage());}}
	private void persistFinalContribution(Encounter e,Contribution c,double percent,Tier tier,boolean eligible){try(Connection con=DatabaseFactory.getConnection();PreparedStatement ps=con.prepareStatement("UPDATE wanted_monster_contributions SET percentage=?,tier=?,eligible=?,reward_status=? WHERE instance_id=? AND account_name=?")){ps.setDouble(1,percent);ps.setString(2,tier==null?"":tier.name);ps.setBoolean(3,eligible);ps.setString(4,eligible?"PENDING":"INELIGIBLE");ps.setLong(5,e.id);ps.setString(6,c.player.getAccountName());ps.executeUpdate();}catch(Exception ex){LOGGER.warning("Final contribution SQL failed: "+ex.getMessage());}}
	private boolean insertPending(Encounter e,Contribution c,Tier tier){try(Connection con=DatabaseFactory.getConnection();PreparedStatement ps=con.prepareStatement("INSERT IGNORE INTO wanted_monster_pending_rewards(instance_id,template_id,account_name,character_id,tier,contribution,wanted_seals,item_rewards) VALUES(?,?,?,?,?,?,?,?)")){ps.setLong(1,e.id);ps.setString(2,e.template.id);ps.setString(3,c.player.getAccountName());ps.setInt(4,c.player.getObjectId());ps.setString(5,tier.name);ps.setLong(6,c.damage);ps.setInt(7,tier.seals);ps.setString(8,items(tier.items));return ps.executeUpdate()==1;}catch(Exception ex){LOGGER.warning("Pending reward SQL failed: "+ex.getMessage());return false;}}
	private void insertRewardHistory(Connection con,ResultSet rs,String account,Player p,String status,String reason)throws Exception{try(PreparedStatement ps=con.prepareStatement("INSERT INTO wanted_monster_reward_history(instance_id,template_id,account_name,character_id,contribution,tier,wanted_seals,item_rewards,status,failure_reason) VALUES(?,?,?,?,?,?,?,?,?,?)")){ps.setLong(1,rs.getLong("instance_id"));ps.setString(2,rs.getString("template_id"));ps.setString(3,account);ps.setInt(4,p.getObjectId());ps.setLong(5,rs.getLong("contribution"));ps.setString(6,rs.getString("tier"));ps.setInt(7,rs.getInt("wanted_seals"));ps.setString(8,rs.getString("item_rewards"));ps.setString(9,status);ps.setString(10,reason);ps.executeUpdate();}}
	private boolean canReceive(Player p,List<RewardItem> list){int slots=0;long weight=0;for(RewardItem r:list){ItemTemplate t=ItemData.getInstance().getTemplate(r.id);if(t==null)return false;if(!t.isStackable()||p.getInventory().getItemByItemId(r.id)==null)slots+=t.isStackable()?1:r.count;weight+=t.getWeight()*(long)r.count;}return p.getInventory().validateCapacity(slots)&&p.getInventory().validateWeight(weight);}
	private String items(List<RewardItem> list){StringBuilder s=new StringBuilder();for(RewardItem r:list){if(s.length()>0)s.append(',');s.append(r.id).append(':').append(r.count);}return s.toString();}
	private List<RewardItem> parseItems(String value){List<RewardItem> list=new ArrayList<>();if(value==null||value.isEmpty())return list;for(String token:value.split(",")){String[] p=token.split(":");list.add(new RewardItem(Integer.parseInt(p[0]),Integer.parseInt(p[1])));}return list;}
	private void cancelRestartedInstances(){try(Connection c=DatabaseFactory.getConnection();PreparedStatement ps=c.prepareStatement("UPDATE wanted_monster_instances SET state='CANCELLED',failure_reason='SERVER_RESTART' WHERE state='ACTIVE'")){ps.executeUpdate();}catch(Exception e){LOGGER.warning("Restart cleanup failed: "+e.getMessage());}}
	private void initTables(){String[] q={"CREATE TABLE IF NOT EXISTS wanted_monster_instances(instance_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,template_id VARCHAR(64) NOT NULL,npc_object_id INT NOT NULL DEFAULT 0,npc_id INT NOT NULL,category VARCHAR(24) NOT NULL,zone_id VARCHAR(32) NOT NULL,spawn_x INT NOT NULL,spawn_y INT NOT NULL,spawn_z INT NOT NULL,state VARCHAR(16) NOT NULL,spawned_at TIMESTAMP NULL,expires_at TIMESTAMP NULL,killed_at TIMESTAMP NULL,killed_by_character_id INT NOT NULL DEFAULT 0,total_damage BIGINT UNSIGNED NOT NULL DEFAULT 0,failure_reason VARCHAR(128) NOT NULL DEFAULT '',created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,PRIMARY KEY(instance_id)) ENGINE=InnoDB","CREATE TABLE IF NOT EXISTS wanted_monster_contributions(instance_id BIGINT UNSIGNED NOT NULL,account_name VARCHAR(45) NOT NULL,character_id INT NOT NULL,damage BIGINT UNSIGNED NOT NULL DEFAULT 0,percentage DECIMAL(8,4) NOT NULL DEFAULT 0,tier VARCHAR(16) NOT NULL DEFAULT '',eligible TINYINT(1) NOT NULL DEFAULT 0,reward_status VARCHAR(16) NOT NULL DEFAULT 'NONE',created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,PRIMARY KEY(instance_id,account_name)) ENGINE=InnoDB","CREATE TABLE IF NOT EXISTS wanted_monster_pending_rewards(id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,instance_id BIGINT UNSIGNED NOT NULL,template_id VARCHAR(64) NOT NULL,account_name VARCHAR(45) NOT NULL,character_id INT NOT NULL,tier VARCHAR(16) NOT NULL,contribution BIGINT UNSIGNED NOT NULL,wanted_seals INT UNSIGNED NOT NULL,item_rewards VARCHAR(512) NOT NULL DEFAULT '',status VARCHAR(16) NOT NULL DEFAULT 'PENDING',created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,delivered_at TIMESTAMP NULL,PRIMARY KEY(id),UNIQUE KEY instance_account(instance_id,account_name)) ENGINE=InnoDB","CREATE TABLE IF NOT EXISTS wanted_monster_reward_history(id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,instance_id BIGINT UNSIGNED NOT NULL,template_id VARCHAR(64) NOT NULL,account_name VARCHAR(45) NOT NULL,character_id INT NOT NULL,contribution BIGINT UNSIGNED NOT NULL,tier VARCHAR(16) NOT NULL,wanted_seals INT UNSIGNED NOT NULL,item_rewards VARCHAR(512) NOT NULL DEFAULT '',status VARCHAR(24) NOT NULL,failure_reason VARCHAR(128) NOT NULL DEFAULT '',created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(id),UNIQUE KEY instance_account_status(instance_id,account_name,status)) ENGINE=InnoDB"};try(Connection c=DatabaseFactory.getConnection()){for(String x:q)try(PreparedStatement ps=c.prepareStatement(x)){ps.execute();}}catch(Exception e){LOGGER.log(Level.SEVERE,"Wanted tables failed",e);}}

	private static String text(Element e,String n,String d){String v=e.getAttribute(n);return v==null||v.isEmpty()?d:v;} private static int integer(Element e,String n,int d){try{return Integer.parseInt(text(e,n,""+d));}catch(Exception x){return d;}} private static int positive(Element e,String n,int d){return Math.max(1,integer(e,n,d));} private static boolean bool(Element e,String n,boolean d){return Boolean.parseBoolean(text(e,n,""+d));} private static double decimal(Element e,String n,double d){try{return Double.parseDouble(text(e,n,""+d));}catch(Exception x){return d;}}
	public static void main(String[] args){getInstance();}

	private static class Settings{boolean enabled;int minDelay,maxDelay,maxActive,rewardRange,teleportCooldown,leashSeconds;String restartPolicy;}
	private static class Point{final int x,y,z,weight;Point(Element e){x=integer(e,"x",0);y=integer(e,"y",0);z=integer(e,"z",0);weight=positive(e,"weight",1);}}
	private static class WantedZone{final String id,name;final int zoneId,centerX,centerY,centerZ,radius,teleportX,teleportY,teleportZ,cooldown;final boolean teleport;final List<Point> spawns=new ArrayList<>();WantedZone(Element e){id=text(e,"id","");name=text(e,"name",id);zoneId=integer(e,"zoneId",0);centerX=integer(e,"centerX",0);centerY=integer(e,"centerY",0);centerZ=integer(e,"centerZ",0);radius=positive(e,"radius",1000);teleport=bool(e,"teleportEnabled",false);teleportX=integer(e,"teleportX",0);teleportY=integer(e,"teleportY",0);teleportZ=integer(e,"teleportZ",0);cooldown=positive(e,"cooldownMinutes",120);}}
	private static class SpecialSkill{final int id,level,chance;SpecialSkill(String s){String[]p=s.split(":");id=Integer.parseInt(p[0]);level=Integer.parseInt(p[1]);chance=Integer.parseInt(p[2]);}}
	private static class RewardItem{final int id,count;RewardItem(int i,int c){id=i;count=c;}}
	private static class Tier{final String name;final double percent;final long damage;final int seals;final List<RewardItem> items=new ArrayList<>();Tier(Element e){name=text(e,"name","");percent=decimal(e,"minPercent",0);damage=Long.parseLong(text(e,"minDamage","0"));seals=integer(e,"wantedSeals",0);NodeList n=e.getElementsByTagName("item");for(int i=0;i<n.getLength();i++){Element x=(Element)n.item(i);items.add(new RewardItem(integer(x,"id",0),positive(x,"count",1)));}}}
	private static class WantedTemplate{final String id,name,category,aiType,icon;final int npcId,weight,level,lifetimeMinutes,aggroRange,leashRange,leashHealPercent,minimumContribution,cooldown;final boolean enabled,announce;final double hp,pAtk,mAtk,pDef,mDef,atkSpd,castSpd;final Set<String> zones=new HashSet<>();final List<SpecialSkill> skills=new ArrayList<>();final List<Tier> tiers=new ArrayList<>();WantedTemplate(Element e){id=text(e,"id","");npcId=integer(e,"npcId",0);name=text(e,"name",id);category=text(e,"category","WANTED_ELITE");enabled=bool(e,"enabled",true);weight=positive(e,"weight",1);hp=decimal(e,"hpMultiplier",1);pAtk=decimal(e,"pAtkMultiplier",1);mAtk=decimal(e,"mAtkMultiplier",1);pDef=decimal(e,"pDefMultiplier",1);mDef=decimal(e,"mDefMultiplier",1);atkSpd=decimal(e,"attackSpeedMultiplier",1);castSpd=decimal(e,"castSpeedMultiplier",1);level=integer(e,"level",80);lifetimeMinutes=positive(e,"lifetimeMinutes",15);aggroRange=positive(e,"aggroRange",700);leashRange=positive(e,"leashRange",1800);leashHealPercent=positive(e,"leashHealPercent",10);aiType=text(e,"aiType","BALANCED");minimumContribution=positive(e,"minimumContribution",1);announce=bool(e,"announce",true);cooldown=positive(e,"cooldownMinutes",180);icon=text(e,"icon","icon.etc_letter_red_i00");for(String z:text(e,"zones","").split(","))if(!z.trim().isEmpty())zones.add(z.trim());NodeList sn=e.getElementsByTagName("skills");if(sn.getLength()>0)for(String s:text((Element)sn.item(0),"ids","").split(","))if(!s.trim().isEmpty())skills.add(new SpecialSkill(s.trim()));NodeList tn=e.getElementsByTagName("tier");for(int i=0;i<tn.getLength();i++)tiers.add(new Tier((Element)tn.item(i)));tiers.sort((a,b)->Double.compare(a.percent,b.percent));}Tier tier(double percent,long damage){Tier result=null;for(Tier t:tiers)if(percent>=t.percent&&damage>=t.damage)result=t;return result;}}
	private static class Contribution{volatile Player player;volatile long damage;Contribution(Player p){player=p;}}
	private static class Encounter{final long id;final WantedTemplate template;final WantedZone zone;final Point point;final Npc npc;final long expires=System.currentTimeMillis();final AtomicBoolean finished=new AtomicBoolean();final Map<String,Contribution> contributions=new ConcurrentHashMap<>();volatile long totalDamage;Encounter(long i,WantedTemplate t,WantedZone z,Point p,Npc n){id=i;template=t;zone=z;point=p;npc=n;}}
	public static class EncounterView{public final long id,damage,totalDamage,expiresAt;public final String name,category,zone,icon,tier;public final int hpPercent,participants;EncounterView(Encounter e,long d){id=e.id;damage=d;totalDamage=e.totalDamage;expiresAt=e.expires+e.template.lifetimeMinutes*60000L;name=e.template.name;category=e.template.category;zone=e.zone.name;icon=e.template.icon;hpPercent=e.npc.getCurrentHpPercent();participants=e.contributions.size();Tier t=e.template.tier(totalDamage<=0?0:d*100.0/totalDamage,d);tier=t==null?"NONE":t.name;}}
}
