/*
 * Megarax custom system.
 */
package handlers.voicedcommandhandlers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.Config;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.cache.HtmCache;
import org.l2jmobius.gameserver.data.xml.EnchantItemData;
import org.l2jmobius.gameserver.data.xml.EnchantItemGroupsData;
import org.l2jmobius.gameserver.handler.IVoicedCommandHandler;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enchant.EnchantResultType;
import org.l2jmobius.gameserver.model.item.enchant.EnchantScroll;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.EnchantResult;
import org.l2jmobius.gameserver.network.serverpackets.InventoryUpdate;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;

/**
 * Professional auto-enchant service for Megarax.
 * <p>
 * The attempt itself uses Mobius enchant data through {@link EnchantScroll#calculateSuccess(Player, Item)}
 * and mirrors the vanilla RequestEnchantItem result handling for scroll consumption, blessed reset,
 * safe failure and normal crystallization.
 * </p>
 */
public class ItemEnchanterAI implements IVoicedCommandHandler
{
	private static final Logger LOGGER = Logger.getLogger(ItemEnchanterAI.class.getName());
	private static final String[] VOICED_COMMANDS =
	{
		"enchantai"
	};
	
	private static final Map<Integer, EnchantSession> SESSIONS = new ConcurrentHashMap<>();
	private static final int PAGE_LIMIT = 7;
	private static final int AUTO_ENCHANT_DELAY = 2000;
	private static final int UNLIMITED_SCROLL_MAX = 65535;
	private static final String MAIN_HTML = "data/html/mods/ItemEnchanterAI/Main.htm";
	private static final String LIST_HTML = "data/html/mods/ItemEnchanterAI/List.htm";
	
	private static volatile Settings SETTINGS = Settings.load();
	
	public ItemEnchanterAI()
	{
		LOGGER.info("ItemEnchanterAI loaded.");
	}
	
	@Override
	public boolean onCommand(String command, Player player, String params)
	{
		if (player == null)
		{
			return false;
		}
		
		if (!SETTINGS.enabled)
		{
			player.sendMessage("Item Enchanter AI is disabled.");
			return true;
		}
		
		final EnchantSession session = getSession(player);
		final String[] args = splitParams(params);
		final String action = args.length > 0 ? args[0] : "main";
		
		try
		{
			switch (action)
			{
				case "items":
				{
					showItemList(player, session, parseInt(args, 1, 1));
					break;
				}
				case "item":
				{
					selectItem(player, session, parseInt(args, 1, 0));
					showMain(player, session);
					break;
				}
				case "scrolls":
				{
					showScrollList(player, session, parseInt(args, 1, 1));
					break;
				}
				case "scroll":
				{
					selectScroll(player, session, parseInt(args, 1, 0));
					showMain(player, session);
					break;
				}
				case "enable":
				{
					enable(player, session);
					showMain(player, session);
					break;
				}
				case "disable":
				{
					stop(player, session, AiStatus.STOPPED, "Stopped by player.", true);
					showMain(player, session);
					break;
				}
				case "clearItem":
				{
					if (session.isRunning())
					{
						player.sendMessage("Stop Item Enchanter AI before changing the item.");
					}
					else
					{
						session.itemObjectId = 0;
						session.status = AiStatus.DISABLED;
						session.message = "Select an item to begin.";
					}
					showMain(player, session);
					break;
				}
				case "clearScroll":
				{
					if (session.isRunning())
					{
						player.sendMessage("Stop Item Enchanter AI before changing the scroll.");
					}
					else
					{
						session.scrollItemId = 0;
						session.status = AiStatus.DISABLED;
						session.message = "Select a valid enchant scroll.";
					}
					showMain(player, session);
					break;
				}
				case "reload":
				{
					if (player.isGM())
					{
						SETTINGS = Settings.load();
						player.sendMessage("Item Enchanter AI config reloaded.");
					}
					showMain(player, session);
					break;
				}
				default:
				{
					showMain(player, session);
					break;
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.log(Level.WARNING, "ItemEnchanterAI action failed for " + player.getName() + ".", e);
			player.sendMessage("Item Enchanter AI error. Please try again.");
			stop(player, session, AiStatus.FAILED, "Internal error. Please try again.", false);
			showMain(player, session);
		}
		
		return true;
	}
	
	@Override
	public String[] getCommandList()
	{
		return VOICED_COMMANDS;
	}
	
	private static EnchantSession getSession(Player player)
	{
		return SESSIONS.computeIfAbsent(player.getObjectId(), objectId -> new EnchantSession());
	}
	
	private static void selectItem(Player player, EnchantSession session, int objectId)
	{
		if (session.isRunning())
		{
			player.sendMessage("Stop Item Enchanter AI before changing the item.");
			return;
		}
		
		final Item item = player.getInventory().getItemByObjectId(objectId);
		final String reason = validateItem(player, item, null);
		if (reason != null)
		{
			session.status = AiStatus.INVALID_ITEM;
			session.message = reason;
			player.sendMessage(reason);
			return;
		}
		
		session.itemObjectId = objectId;
		session.status = AiStatus.DISABLED;
		session.message = "Select a valid enchant scroll.";
		
		if (session.scrollItemId > 0)
		{
			final Item scrollItem = player.getInventory().getItemByItemId(session.scrollItemId);
			final EnchantScroll scroll = getEnchantScroll(scrollItem);
			if ((scroll == null) || !isAllowedScroll(scroll) || !scroll.isValid(item))
			{
				session.scrollItemId = 0;
			}
		}
	}
	
	private static void selectScroll(Player player, EnchantSession session, int itemId)
	{
		if (session.isRunning())
		{
			player.sendMessage("Stop Item Enchanter AI before changing the scroll.");
			return;
		}
		
		final Item scrollItem = player.getInventory().getItemByItemId(itemId);
		final EnchantScroll scroll = getEnchantScroll(scrollItem);
		final String reason = validateScroll(player, scrollItem, scroll, getSelectedItem(player, session));
		if (reason != null)
		{
			session.status = AiStatus.INVALID_ITEM;
			session.message = reason;
			player.sendMessage(reason);
			return;
		}
		
		session.scrollItemId = itemId;
		session.status = AiStatus.DISABLED;
		session.message = session.itemObjectId > 0 ? "Ready to enchant." : "Select an item to begin.";
	}
	
	private static void enable(Player player, EnchantSession session)
	{
		if (session.isRunning())
		{
			player.sendMessage("Item Enchanter AI is already running.");
			return;
		}
		
		final Validation validation = validateReady(player, session, true);
		if (!validation.valid)
		{
			session.status = validation.status;
			session.message = validation.message;
			player.sendMessage(validation.message);
			return;
		}
		
		session.status = AiStatus.ENABLED;
		session.message = "Auto enchant started. Attempt every 2 seconds.";
		LOGGER.info("ItemEnchanterAI start: player=" + player.getName() + ", itemId=" + validation.item.getId() + ", objectId=" + validation.item.getObjectId() + ", current=+" + validation.item.getEnchantLevel() + ", max=+" + getMaxEnchant(validation.item, validation.scroll) + ", scrollId=" + session.scrollItemId + ", scrollCount=" + validation.scrollItem.getCount() + ".");
		scheduleNext(player, session, AUTO_ENCHANT_DELAY);
	}
	
	private static void scheduleNext(Player player, EnchantSession session, int delay)
	{
		session.cancelTask();
		session.task = ThreadPool.schedule(() -> runAttempt(player, session), delay);
	}
	
	private static void runAttempt(Player player, EnchantSession session)
	{
		synchronized (session)
		{
			if (!session.isRunning() && (session.status != AiStatus.ENABLED))
			{
				return;
			}
			
			final Validation validation = validateReady(player, session, false);
			if (!validation.valid)
			{
				stop(player, session, validation.status, validation.message, true);
				return;
			}
			
			final int maxEnchant = getMaxEnchant(validation.item, validation.scroll);
			if (validation.item.getEnchantLevel() >= maxEnchant)
			{
				stop(player, session, AiStatus.COMPLETED, "Maximum enchant reached successfully.", true);
				return;
			}
			
			session.status = AiStatus.ENCHANTING;
			session.message = "Enchanting in progress: +" + validation.item.getEnchantLevel() + " to +" + (validation.item.getEnchantLevel() + 1) + ".";
			player.setEnchanting(true);
			player.setActiveEnchantItemId(validation.scrollItem.getObjectId());
			
			final AttemptResult result;
			try
			{
				result = attemptEnchant(player, validation.item, validation.scrollItem, validation.scroll);
			}
			finally
			{
				player.setEnchanting(false);
				player.setActiveEnchantItemId(-1);
			}
			
			if (result == AttemptResult.SUCCESS)
			{
				if (validation.item.getEnchantLevel() >= maxEnchant)
				{
					stop(player, session, AiStatus.COMPLETED, "Maximum enchant reached successfully.", true);
				}
				else
				{
					session.status = AiStatus.ENABLED;
					session.message = "Enchant successful. Current level: +" + validation.item.getEnchantLevel() + ".";
					scheduleNext(player, session, AUTO_ENCHANT_DELAY);
				}
			}
			else if (result == AttemptResult.SAFE_FAILURE)
			{
				session.status = AiStatus.ENABLED;
				session.message = "Safe enchant failed. Current level retained: +" + validation.item.getEnchantLevel() + ".";
				scheduleNext(player, session, AUTO_ENCHANT_DELAY);
			}
			else if (result == AttemptResult.BLESSED_FAILURE)
			{
				if (SETTINGS.stopOnBlessedFail)
				{
					stop(player, session, AiStatus.FAILED, "Blessed enchant failed. Process stopped.", true);
				}
				else
				{
					session.status = AiStatus.ENABLED;
					session.message = "Blessed enchant failed. Continuing from +" + validation.item.getEnchantLevel() + ".";
					scheduleNext(player, session, AUTO_ENCHANT_DELAY);
				}
			}
			else if (result == AttemptResult.NO_SCROLLS)
			{
				stop(player, session, AiStatus.NO_SCROLLS, "Not enough scrolls to continue.", true);
			}
			else
			{
				stop(player, session, AiStatus.FAILED, "Enchant failed. The process has stopped.", true);
			}
		}
	}
	
	private static AttemptResult attemptEnchant(Player player, Item item, Item scrollItem, EnchantScroll scroll)
	{
		if ((scroll == null) || !scroll.isValid(item) || (Config.DISABLE_OVER_ENCHANTING && (item.getEnchantLevel() >= scroll.getMaxEnchantLevel())))
		{
			player.sendPacket(SystemMessageId.INAPPROPRIATE_ENCHANT_CONDITIONS);
			player.sendPacket(new EnchantResult(0));
			return AttemptResult.ERROR;
		}
		
		scrollItem = player.getInventory().destroyItem(ItemProcessType.FEE, scrollItem.getObjectId(), 1, player, item);
		if (scrollItem == null)
		{
			player.sendPacket(SystemMessageId.INCORRECT_ITEM_COUNT_2);
			player.sendPacket(new EnchantResult(0));
			return AttemptResult.NO_SCROLLS;
		}
		
		synchronized (item)
		{
			if ((item.getOwnerId() != player.getObjectId()) || !item.isEnchantable())
			{
				player.sendPacket(SystemMessageId.INAPPROPRIATE_ENCHANT_CONDITIONS);
				player.sendPacket(new EnchantResult(0));
				return AttemptResult.ERROR;
			}
			
			final EnchantResultType result = scroll.calculateSuccess(player, item);
			if (result == EnchantResultType.ERROR)
			{
				player.sendPacket(SystemMessageId.INAPPROPRIATE_ENCHANT_CONDITIONS);
				player.sendPacket(new EnchantResult(0));
				return AttemptResult.ERROR;
			}
			
			if (result == EnchantResultType.SUCCESS)
			{
				applySuccess(player, item, scroll);
				return AttemptResult.SUCCESS;
			}
			
			if (scroll.isSafe())
			{
				player.sendMessage("Enchant failed. The enchant level for the corresponding item will be exactly retained.");
				player.sendPacket(new EnchantResult(0));
				player.sendItemList(true);
				player.broadcastUserInfo();
				return AttemptResult.SAFE_FAILURE;
			}
			
			if (item.isEquipped())
			{
				unEquipForFailure(player, item);
			}
			
			if (scroll.isBlessed())
			{
				player.sendPacket(SystemMessageId.FAILED_IN_BLESSED_ENCHANT_THE_ENCHANT_VALUE_OF_THE_ITEM_BECAME_0);
				item.setEnchantLevel(0);
				item.updateDatabase();
				player.sendPacket(new EnchantResult(0));
				player.sendItemList(true);
				player.broadcastUserInfo();
				return AttemptResult.BLESSED_FAILURE;
			}
			
			applyNormalFailure(player, item);
			return AttemptResult.NORMAL_FAILURE;
		}
	}
	
	private static void applySuccess(Player player, Item item, EnchantScroll scroll)
	{
		final int oldEnchant = item.getEnchantLevel();
		final SystemMessage message;
		if (oldEnchant == 0)
		{
			message = new SystemMessage(SystemMessageId.YOUR_S1_HAS_BEEN_SUCCESSFULLY_ENCHANTED);
			message.addItemName(item.getId());
		}
		else
		{
			message = new SystemMessage(SystemMessageId.YOUR_S1_S2_HAS_BEEN_SUCCESSFULLY_ENCHANTED);
			message.addInt(oldEnchant);
			message.addItemName(item.getId());
		}
		player.sendPacket(message);
		
		if (scroll.getChance(player, item) > 0)
		{
			item.setEnchantLevel(oldEnchant + 1);
			item.updateDatabase();
		}
		
		player.sendPacket(new EnchantResult(item.getEnchantLevel()));
		
		if (item.isArmor() && (item.getEnchantLevel() == 4) && item.isEquipped())
		{
			final Skill enchant4Skill = item.getTemplate().getEnchant4Skill();
			if (enchant4Skill != null)
			{
				player.addSkill(enchant4Skill, false);
				player.sendSkillList();
			}
		}
		
		player.sendItemList(false);
		player.broadcastUserInfo();
		player.sendMessage("Enchant successful. Current level: +" + item.getEnchantLevel() + ".");
	}
	
	private static void unEquipForFailure(Player player, Item item)
	{
		if (item.getEnchantLevel() > 0)
		{
			final SystemMessage message = new SystemMessage(SystemMessageId.THE_EQUIPMENT_S1_S2_HAS_BEEN_REMOVED);
			message.addInt(item.getEnchantLevel());
			message.addItemName(item);
			player.sendPacket(message);
		}
		else
		{
			final SystemMessage message = new SystemMessage(SystemMessageId.S1_HAS_BEEN_DISARMED);
			message.addItemName(item);
			player.sendPacket(message);
		}
		
		final InventoryUpdate inventoryUpdate = new InventoryUpdate();
		for (Item modifiedItem : player.getInventory().unEquipItemInSlotAndRecord(item.getLocationSlot()))
		{
			inventoryUpdate.addModifiedItem(modifiedItem);
		}
		player.sendInventoryUpdate(inventoryUpdate);
		player.broadcastUserInfo();
	}
	
	private static void applyNormalFailure(Player player, Item item)
	{
		if (item.getEnchantLevel() > 0)
		{
			final SystemMessage message = new SystemMessage(SystemMessageId.THE_ENCHANTMENT_HAS_FAILED_YOUR_S1_S2_HAS_BEEN_CRYSTALLIZED);
			message.addInt(item.getEnchantLevel());
			message.addItemName(item.getId());
			player.sendPacket(message);
		}
		else
		{
			final SystemMessage message = new SystemMessage(SystemMessageId.THE_ENCHANTMENT_HAS_FAILED_YOUR_S1_HAS_BEEN_CRYSTALLIZED);
			message.addItemName(item.getId());
			player.sendPacket(message);
		}
		
		if (player.getInventory().destroyItem(ItemProcessType.DESTROY, item, player, null) == null)
		{
			player.sendPacket(new EnchantResult(0));
			return;
		}
		
		World.getInstance().removeObject(item);
		final int crystalId = item.getTemplate().getCrystalItemId();
		if ((crystalId > 0) && item.getTemplate().isCrystallizable())
		{
			int crystalCount = item.getCrystalCount() - ((item.getTemplate().getCrystalCount() + 1) / 2);
			if (crystalCount < 1)
			{
				crystalCount = 1;
			}
			
			player.getInventory().addItem(ItemProcessType.COMPENSATE, crystalId, crystalCount, player, item);
			final SystemMessage earned = new SystemMessage(SystemMessageId.YOU_HAVE_EARNED_S2_S1_S);
			earned.addItemName(crystalId);
			earned.addInt(crystalCount);
			player.sendPacket(earned);
		}
		
		player.sendPacket(new EnchantResult(0));
		player.sendItemList(true);
		player.broadcastUserInfo();
	}
	
	private static Validation validateReady(Player player, EnchantSession session, boolean opening)
	{
		final String restriction = validatePlayer(player);
		if (restriction != null)
		{
			return Validation.fail(AiStatus.STOPPED, restriction);
		}
		
		if (player.isEnchanting())
		{
			return Validation.fail(AiStatus.STOPPED, "Another enchantment is in progress.");
		}
		
		final Item item = getSelectedItem(player, session);
		String reason = validateItem(player, item, null);
		if (reason != null)
		{
			return Validation.fail(AiStatus.INVALID_ITEM, reason);
		}
		
		final Item scrollItem = player.getInventory().getItemByItemId(session.scrollItemId);
		final EnchantScroll scroll = getEnchantScroll(scrollItem);
		reason = validateScroll(player, scrollItem, scroll, item);
		if (reason != null)
		{
			return Validation.fail(AiStatus.NO_SCROLLS, reason);
		}
		
		final int maxEnchant = getMaxEnchant(item, scroll);
		if (item.getEnchantLevel() >= maxEnchant)
		{
			return Validation.fail(opening ? AiStatus.INVALID_ITEM : AiStatus.COMPLETED, opening ? "Item is already at max enchant for this scroll." : "Maximum enchant reached successfully.");
		}
		
		return Validation.success(item, scrollItem, scroll);
	}
	
	private static String validatePlayer(Player player)
	{
		if ((player == null) || !player.isOnline())
		{
			return "Player is offline.";
		}
		if (player.isAlikeDead())
		{
			return "You cannot use Item Enchanter AI while dead.";
		}
		if (player.isJailed())
		{
			return "You cannot use Item Enchanter AI while jailed.";
		}
		if (player.isMounted())
		{
			return "You cannot enchant while mounted.";
		}
		if (player.isTeleporting() || player.inObserverMode())
		{
			return "You cannot use Item Enchanter AI right now.";
		}
		if (player.isInOlympiadMode() || player.isOlympiadStart())
		{
			return "You cannot use Item Enchanter AI in Olympiad.";
		}
		if (player.isOnEvent() || player.isRegisteredOnEvent())
		{
			return "You cannot use Item Enchanter AI during events.";
		}
		if (player.isInsideZone(ZoneId.PVP) || player.isInsideZone(ZoneId.SIEGE) || player.isInSiege() || player.isInDuel())
		{
			return "You cannot use Item Enchanter AI in PvP, duel or siege areas.";
		}
		if (!SETTINGS.allowInTown && player.isInsideZone(ZoneId.TOWN))
		{
			return "Item Enchanter AI is not allowed in town.";
		}
		if (player.isProcessingTransaction() || (player.getActiveTradeList() != null))
		{
			return "You cannot enchant while trading.";
		}
		if (player.isInStoreMode() || (player.getPrivateStoreType() != PrivateStoreType.NONE))
		{
			player.sendPacket(SystemMessageId.YOU_CANNOT_ENCHANT_WHILE_OPERATING_A_PRIVATE_STORE_OR_PRIVATE_WORKSHOP);
			return "You cannot enchant while using private store or workshop.";
		}
		if (player.getActiveWarehouse() != null)
		{
			return "Close the warehouse before using Item Enchanter AI.";
		}
		if (player.getMultiSell() != null)
		{
			return "Close multisell before using Item Enchanter AI.";
		}
		if (player.isInventoryDisabled())
		{
			return "Inventory is locked.";
		}
		if (player.isCastingNow())
		{
			return "You cannot enchant while casting.";
		}
		return null;
	}
	
	private static String validateItem(Player player, Item item, EnchantScroll scroll)
	{
		if (item == null)
		{
			return "Select an item to begin.";
		}
		if (item.getOwnerId() != player.getObjectId())
		{
			return "Invalid item owner.";
		}
		if (!player.validateItemManipulation(item.getObjectId(), ItemProcessType.NONE))
		{
			return "This item cannot be used right now.";
		}
		if (!item.isEnchantable() || (!item.isWeapon() && !item.isArmor()))
		{
			return "This item is not enchantable.";
		}
		if (item.isStackable() || item.isQuestItem())
		{
			return "Invalid item type.";
		}
		if (item.isHeroItem() || item.isShadowItem() || item.isTimeLimitedItem() || item.isCommonItem() || item.isOlyRestrictedItem())
		{
			return "Restricted items cannot be enchanted here.";
		}
		if (item.isAugmented() && !SETTINGS.allowAugmentedItems)
		{
			return "Augmented items are blocked by config.";
		}
		if (item.isEquipped() && !SETTINGS.allowEquippedItems)
		{
			return "Unequip the item before using Item Enchanter AI.";
		}
		if (item.getEnchantLevel() >= getMaxEnchant(item, scroll))
		{
			return "Item is already at max enchant.";
		}
		return null;
	}
	
	private static String validateScroll(Player player, Item scrollItem, EnchantScroll scroll, Item selectedItem)
	{
		if ((scrollItem == null) || (scrollItem.getCount() < 1))
		{
			return "Not enough scrolls to continue.";
		}
		if (scroll == null)
		{
			return "Select a valid enchant scroll.";
		}
		if (!player.validateItemManipulation(scrollItem.getObjectId(), ItemProcessType.FEE))
		{
			return "This scroll cannot be used right now.";
		}
		if (!isAllowedScroll(scroll))
		{
			return "This scroll type is disabled.";
		}
		if ((selectedItem != null) && !scroll.isValid(selectedItem))
		{
			if (selectedItem.getEnchantLevel() >= getMaxEnchant(selectedItem, scroll))
			{
				return "Item is already at max enchant for this scroll.";
			}
			return "Scroll does not match the selected item.";
		}
		return null;
	}
	
	private static boolean isAllowedScroll(EnchantScroll scroll)
	{
		if (scroll.isBlessed())
		{
			return SETTINGS.allowBlessedScrolls;
		}
		if (isCrystalScroll(scroll))
		{
			return SETTINGS.allowCrystalScrolls;
		}
		return SETTINGS.allowNormalScrolls;
	}
	
	private static boolean isCrystalScroll(EnchantScroll scroll)
	{
		return scroll.isSafe() || (scroll.getBonusRate() > 0);
	}
	
	private static EnchantScroll getEnchantScroll(Item scrollItem)
	{
		return scrollItem == null ? null : EnchantItemData.getInstance().getEnchantScroll(scrollItem);
	}
	
	private static Item getSelectedItem(Player player, EnchantSession session)
	{
		return session.itemObjectId > 0 ? player.getInventory().getItemByObjectId(session.itemObjectId) : null;
	}
	
	private static int getMaxEnchant(Item item, EnchantScroll scroll)
	{
		if (item == null)
		{
			return 0;
		}
		
		int max = item.isWeapon() ? EnchantItemGroupsData.getInstance().getMaxWeaponEnchant() : ((item.getTemplate().getType2() == ItemTemplate.TYPE2_ACCESSORY) ? EnchantItemGroupsData.getInstance().getMaxAccessoryEnchant() : EnchantItemGroupsData.getInstance().getMaxArmorEnchant());
		if ((scroll != null) && (scroll.getMaxEnchantLevel() > 0) && (scroll.getMaxEnchantLevel() < UNLIMITED_SCROLL_MAX))
		{
			max = Math.min(max, scroll.getMaxEnchantLevel());
		}
		if (SETTINGS.maxTargetEnchant > 0)
		{
			max = Math.min(max, SETTINGS.maxTargetEnchant);
		}
		return max;
	}
	
	private static void stop(Player player, EnchantSession session, AiStatus status, String reason, boolean notify)
	{
		session.cancelTask();
		session.status = status;
		session.message = reason;
		if (player != null)
		{
			player.setEnchanting(false);
			player.setActiveEnchantItemId(-1);
			if (notify)
			{
				player.sendMessage(reason);
			}
			LOGGER.info("ItemEnchanterAI finish: player=" + player.getName() + ", reason=" + reason + ".");
		}
	}
	
	private static void showMain(Player player, EnchantSession session)
	{
		final Item item = getSelectedItem(player, session);
		final Item scrollItem = player.getInventory().getItemByItemId(session.scrollItemId);
		final EnchantScroll scroll = getEnchantScroll(scrollItem);
		final int maxEnchant = getMaxEnchant(item, scroll);
		final String itemName = item == null ? "No item selected" : shortName(item.getName(), 28);
		final String scrollName = scrollItem == null ? "No scroll selected" : shortName(scrollItem.getName(), 28);
		final String grade = item == null ? "-" : item.getTemplate().getCrystalTypePlus().name();
		final String itemType = item == null ? "-" : item.isWeapon() ? "Weapon" : ((item.getTemplate().getType2() == ItemTemplate.TYPE2_ACCESSORY) ? "Jewelry" : "Armor");
		final String status = getDisplayStatus(session, item, scrollItem, scroll);
		
		String content = getHtml(player, MAIN_HTML, defaultMainHtml());
		content = content.replace("%status_color%", getStatusColor(status));
		content = content.replace("%status%", status);
		content = content.replace("%message%", html(session.message));
		content = content.replace("%item_icon%", buildIcon(item));
		content = content.replace("%item_name%", html(itemName));
		content = content.replace("%item_type%", itemType);
		content = content.replace("%item_grade%", grade);
		content = content.replace("%current_enchant%", item == null ? "-" : "+" + item.getEnchantLevel());
		content = content.replace("%max_enchant%", item == null ? "-" : "+" + maxEnchant);
		content = content.replace("%scroll_icon%", buildIcon(scrollItem));
		content = content.replace("%scroll_name%", html(scrollName));
		content = content.replace("%scroll_type%", getScrollType(scroll));
		content = content.replace("%scroll_count%", scrollItem == null ? "-" : String.valueOf(scrollItem.getCount()));
		content = content.replace("%toggle_button%", session.isRunning() || (session.status == AiStatus.ENABLED) || (session.status == AiStatus.ENCHANTING) ? premiumButton("STOP", "bypass -h voice .enchantai disable", 90) : premiumButton("START", "bypass -h voice .enchantai enable", 90));
		
		final NpcHtmlMessage html = new NpcHtmlMessage();
		html.setHtml(content);
		player.sendPacket(html);
	}
	
	private static void showItemList(Player player, EnchantSession session, int page)
	{
		final List<Item> items = new ArrayList<>();
		for (Item item : player.getInventory().getItems())
		{
			if (validateItem(player, item, null) == null)
			{
				items.add(item);
			}
		}
		items.sort(Comparator.comparing(Item::getName).thenComparingInt(Item::getEnchantLevel));
		showList(player, "Select Item", buildItemRows(items, page), page, getMaxPage(items.size()), "items");
	}
	
	private static void showScrollList(Player player, EnchantSession session, int page)
	{
		final Item selectedItem = getSelectedItem(player, session);
		final List<Item> scrolls = new ArrayList<>();
		for (Item item : player.getInventory().getItems())
		{
			final EnchantScroll scroll = getEnchantScroll(item);
			if (validateScroll(player, item, scroll, selectedItem) == null)
			{
				scrolls.add(item);
			}
		}
		scrolls.sort(Comparator.comparing(Item::getName));
		showList(player, "Select Scroll", buildScrollRows(scrolls, page), page, getMaxPage(scrolls.size()), "scrolls");
	}
	
	private static void showList(Player player, String title, String rows, int page, int maxPage, String action)
	{
		String content = getHtml(player, LIST_HTML, defaultListHtml());
		content = content.replace("%title%", title);
		content = content.replace("%subtitle%", "items".equals(action) ? "Choose the equipment you want to enchant." : "Choose a compatible enchant scroll.");
		content = content.replace("%rows%", rows);
		content = content.replace("%page%", String.valueOf(page));
		content = content.replace("%max_page%", String.valueOf(maxPage));
		content = content.replace("%prev%", premiumButton("Prev", "bypass -h voice .enchantai " + action + " " + (page > 1 ? page - 1 : page), 74));
		content = content.replace("%next%", premiumButton("Next", "bypass -h voice .enchantai " + action + " " + (page < maxPage ? page + 1 : page), 74));
		
		final NpcHtmlMessage html = new NpcHtmlMessage();
		html.setHtml(content);
		player.sendPacket(html);
	}
	
	private static String buildItemRows(List<Item> items, int page)
	{
		if (items.isEmpty())
		{
			return "<center><br><font color=\"B09878\">No enchantable items found.</font><br></center>";
		}
		
		final StringBuilder rows = new StringBuilder();
		for (Item item : slice(items, page))
		{
			rows.append("<table width=294 bgcolor=000000><tr>");
			rows.append("<td width=36 align=center bgcolor=000000>").append(buildIcon(item)).append("</td>");
			rows.append("<td width=174 bgcolor=000000><font color=\"LEVEL\">+").append(item.getEnchantLevel()).append(" ").append(html(shortName(item.getName(), 24))).append("</font><br1><font color=\"D8B66A\">").append(item.isWeapon() ? "Weapon" : "Armor/Jewel").append(" / ").append(item.getTemplate().getCrystalTypePlus()).append("</font></td>");
			rows.append("<td width=84 align=center bgcolor=000000>").append(premiumButton("Select", "bypass -h voice .enchantai item " + item.getObjectId(), 74)).append("</td>");
			rows.append("</tr></table>");
		}
		return rows.toString();
	}
	
	private static String buildScrollRows(List<Item> scrolls, int page)
	{
		if (scrolls.isEmpty())
		{
			return "<center><br><font color=\"B09878\">No valid scrolls found.</font><br></center>";
		}
		
		final StringBuilder rows = new StringBuilder();
		for (Item item : slice(scrolls, page))
		{
			final EnchantScroll scroll = getEnchantScroll(item);
			rows.append("<table width=294 bgcolor=000000><tr>");
			rows.append("<td width=36 align=center bgcolor=000000>").append(buildIcon(item)).append("</td>");
			rows.append("<td width=174 bgcolor=000000><font color=\"LEVEL\">").append(html(shortName(item.getName(), 25))).append("</font><br1><font color=\"D8B66A\">").append(getScrollType(scroll)).append(" / x").append(item.getCount()).append("</font></td>");
			rows.append("<td width=84 align=center bgcolor=000000>").append(premiumButton("Select", "bypass -h voice .enchantai scroll " + item.getId(), 74)).append("</td>");
			rows.append("</tr></table>");
		}
		return rows.toString();
	}
	
	private static <T> List<T> slice(List<T> list, int page)
	{
		final int maxPage = getMaxPage(list.size());
		page = clamp(page, 1, maxPage);
		final int from = Math.max(0, (page - 1) * PAGE_LIMIT);
		final int to = Math.min(list.size(), from + PAGE_LIMIT);
		return list.subList(from, to);
	}
	
	private static int getMaxPage(int size)
	{
		return Math.max(1, (int) Math.ceil(size / (double) PAGE_LIMIT));
	}
	
	private static String getScrollType(EnchantScroll scroll)
	{
		if (scroll == null)
		{
			return "-";
		}
		if (scroll.isBlessed())
		{
			return scroll.isWeapon() ? "Blessed Weapon" : "Blessed Armor";
		}
		if (isCrystalScroll(scroll))
		{
			return scroll.isWeapon() ? "Crystal Weapon" : "Crystal Armor";
		}
		return scroll.isWeapon() ? "Normal Weapon" : "Normal Armor";
	}
	
	private static String premiumButton(String value, String action, int width)
	{
		return "<button value=\"" + value + "\" action=\"" + action + "\" width=" + width + " height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">";
	}
	
	private static String buildIcon(Item item)
	{
		if ((item == null) || (item.getTemplate() == null) || (item.getTemplate().getIcon() == null) || item.getTemplate().getIcon().isEmpty())
		{
			return "<img src=\"L2UI.SquareBlank\" width=32 height=32>";
		}
		return "<img src=\"" + item.getTemplate().getIcon() + "\" width=32 height=32>";
	}
	
	private static String shortName(String text, int maxLength)
	{
		if (text == null)
		{
			return "";
		}
		return text.length() <= maxLength ? text : text.substring(0, Math.max(0, maxLength - 3)) + "...";
	}
	
	private static String getDisplayStatus(EnchantSession session, Item item, Item scrollItem, EnchantScroll scroll)
	{
		switch (session.status)
		{
			case ENABLED:
			case ENCHANTING:
				return "ENCHANTING";
			case COMPLETED:
				return "COMPLETED";
			case FAILED:
				return "FAILED";
			case NO_SCROLLS:
				return "NO SCROLLS";
			case INVALID_ITEM:
				return "INVALID ITEM";
			case STOPPED:
				return "DISABLED";
			default:
				return ((item != null) && (scrollItem != null) && (scroll != null) && scroll.isValid(item)) ? "READY" : "DISABLED";
		}
	}
	
	private static String getStatusColor(String status)
	{
		switch (status)
		{
			case "READY":
				return "82D870";
			case "ENCHANTING":
				return "F08A38";
			case "COMPLETED":
				return "D8B66A";
			case "FAILED":
			case "NO SCROLLS":
			case "INVALID ITEM":
				return "E35B45";
			case "DISABLED":
				return "B09878";
			default:
				return "7B6A58";
		}
	}
	
	private static String[] splitParams(String params)
	{
		return ((params == null) || params.trim().isEmpty()) ? new String[0] : params.trim().split("\\s+");
	}
	
	private static int parseInt(String[] args, int index, int fallback)
	{
		if (args.length <= index)
		{
			return fallback;
		}
		try
		{
			return Integer.parseInt(args[index]);
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}
	
	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}
	
	private static String html(String text)
	{
		return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "'");
	}
	
	private static String getHtml(Player player, String path, String fallback)
	{
		final String content = HtmCache.getInstance().getHtm(player, path);
		return content == null ? fallback : content;
	}
	
	private static String defaultMainHtml()
	{
		return "<html><body bgcolor=000000><center><table width=300 bgcolor=000000><tr><td align=center bgcolor=000000><button value=\"Item Enchanter AI\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\"><font color=\"D8B66A\">%message%</font></td></tr></table></center></body></html>";
	}
	
	private static String defaultListHtml()
	{
		return "<html><body bgcolor=000000><center><table width=300 bgcolor=000000><tr><td align=center bgcolor=000000><button value=\"%title%\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">%rows%<br1><button value=\"Back\" action=\"bypass -h voice .enchantai\" width=90 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td></tr></table></center></body></html>";
	}
	
	private enum AiStatus
	{
		DISABLED,
		ENABLED,
		ENCHANTING,
		STOPPED,
		COMPLETED,
		FAILED,
		NO_SCROLLS,
		INVALID_ITEM
	}
	
	private enum AttemptResult
	{
		SUCCESS,
		SAFE_FAILURE,
		BLESSED_FAILURE,
		NORMAL_FAILURE,
		NO_SCROLLS,
		ERROR
	}
	
	private static final class EnchantSession
	{
		private int itemObjectId;
		private int scrollItemId;
		private AiStatus status = AiStatus.DISABLED;
		private String message = "Select an item to begin.";
		private ScheduledFuture<?> task;
		
		private boolean isRunning()
		{
			return (task != null) && !task.isDone() && !task.isCancelled();
		}
		
		private void cancelTask()
		{
			if (task != null)
			{
				task.cancel(false);
				task = null;
			}
		}
	}
	
	private static final class Validation
	{
		private final boolean valid;
		private final AiStatus status;
		private final String message;
		private final Item item;
		private final Item scrollItem;
		private final EnchantScroll scroll;
		
		private Validation(boolean valid, AiStatus status, String message, Item item, Item scrollItem, EnchantScroll scroll)
		{
			this.valid = valid;
			this.status = status;
			this.message = message;
			this.item = item;
			this.scrollItem = scrollItem;
			this.scroll = scroll;
		}
		
		private static Validation success(Item item, Item scrollItem, EnchantScroll scroll)
		{
			return new Validation(true, AiStatus.ENABLED, "Ready to enchant.", item, scrollItem, scroll);
		}
		
		private static Validation fail(AiStatus status, String message)
		{
			return new Validation(false, status, message, null, null, null);
		}
	}
	
	private static final class Settings
	{
		private final boolean enabled;
		private final boolean allowInTown;
		private final boolean allowEquippedItems;
		private final boolean allowAugmentedItems;
		private final int maxTargetEnchant;
		private final boolean stopOnBlessedFail;
		private final boolean allowNormalScrolls;
		private final boolean allowBlessedScrolls;
		private final boolean allowCrystalScrolls;
		
		private Settings(Properties properties)
		{
			enabled = getBoolean(properties, "EnableItemEnchanterAI", true);
			allowInTown = getBoolean(properties, "AllowInTown", true);
			allowEquippedItems = getBoolean(properties, "AllowEquippedItems", true);
			allowAugmentedItems = getBoolean(properties, "AllowAugmentedItems", false);
			maxTargetEnchant = Math.max(0, getInt(properties, "MaxTargetEnchant", 0));
			stopOnBlessedFail = getBoolean(properties, "StopOnBlessedFail", true);
			allowNormalScrolls = getBoolean(properties, "AllowNormalScrolls", true);
			allowBlessedScrolls = getBoolean(properties, "AllowBlessedScrolls", true);
			allowCrystalScrolls = getBoolean(properties, "AllowCrystalScrolls", true);
		}
		
		private static Settings load()
		{
			final Properties properties = new Properties();
			final File configFile = resolveConfigFile();
			if ((configFile != null) && configFile.isFile())
			{
				try (FileInputStream input = new FileInputStream(configFile))
				{
					properties.load(input);
				}
				catch (IOException e)
				{
					LOGGER.log(Level.WARNING, "Could not load ItemEnchanterAI config from " + configFile.getAbsolutePath(), e);
				}
			}
			return new Settings(properties);
		}
		
		private static File resolveConfigFile()
		{
			final List<File> candidates = new ArrayList<>();
			candidates.add(new File("config/Custom/ItemEnchanterAI.ini"));
			candidates.add(new File("game/config/Custom/ItemEnchanterAI.ini"));
			if (Config.DATAPACK_ROOT != null)
			{
				final File parent = Config.DATAPACK_ROOT.getParentFile();
				if (parent != null)
				{
					candidates.add(new File(parent, "config/Custom/ItemEnchanterAI.ini"));
				}
			}
			
			for (File file : candidates)
			{
				if (file.isFile())
				{
					return file;
				}
			}
			return candidates.get(0);
		}
		
		private static boolean getBoolean(Properties properties, String key, boolean fallback)
		{
			return Boolean.parseBoolean(properties.getProperty(key, String.valueOf(fallback)).trim());
		}
		
		private static int getInt(Properties properties, String key, int fallback)
		{
			try
			{
				return Integer.parseInt(properties.getProperty(key, String.valueOf(fallback)).trim());
			}
			catch (NumberFormatException e)
			{
				return fallback;
			}
		}
	}
}
