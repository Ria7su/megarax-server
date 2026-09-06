/*
 * Megarax isolated one-hour buff scrolls.
 */
package handlers.itemhandlers;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.handler.IItemHandler;
import org.l2jmobius.gameserver.model.actor.Playable;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.skill.BuffInfo;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.enums.SkillFinishType;
import org.l2jmobius.gameserver.model.zone.ZoneId;

public class MegaraxBuffScrolls implements IItemHandler
{
	private static final Logger LOGGER = Logger.getLogger("megarax_buff_scrolls");
	private static final String CONFIG_FILE = "config/Custom/MegaraxBuffScrolls.ini";
	private static final int NOBLESSE_SCROLL = 9154;
	private static final int MALARIA_SCROLL = 9155;
	private static final int NOBLESSE_SKILL = 7065;
	private static final int MALARIA_SKILL = 7066;

	private boolean _enableNoblesse = true;
	private boolean _enableMalaria = true;
	private int _noblesseDuration = 3600;
	private int _malariaDuration = 3600;
	private int _malariaLevel = 4;
	private boolean _allowOlympiad = false;
	private boolean _allowEvents = false;
	private boolean _allowSiege = true;
	private boolean _allowDungeon = true;
	private boolean _debug = false;

	public MegaraxBuffScrolls()
	{
		loadConfig();
		LOGGER.info("MegaraxBuffScrolls loaded: items 9154/9155, skills 7065/7066.");
	}

	@Override
	public boolean onItemUse(Playable playable, Item item, boolean forceUse)
	{
		if (!playable.isPlayer())
		{
			playable.sendMessage("Only players can use this scroll.");
			return false;
		}

		final Player player = playable.asPlayer();
		synchronized (player)
		{
			return useScroll(player, item);
		}
	}

	private boolean useScroll(Player player, Item item)
	{
		final int itemId = item.getId();
		final boolean noblesse = itemId == NOBLESSE_SCROLL;
		if (!noblesse && (itemId != MALARIA_SCROLL))
		{
			return reject(player, itemId, "This scroll is not configured.");
		}
		if ((noblesse && !_enableNoblesse) || (!noblesse && !_enableMalaria))
		{
			return reject(player, itemId, "This scroll is temporarily disabled.");
		}
		if (player.isDead() || player.isAlikeDead())
		{
			return reject(player, itemId, "You cannot use this scroll while dead.");
		}
		if (player.inObserverMode())
		{
			return reject(player, itemId, "You cannot use this scroll in observer mode.");
		}
		if (!_allowOlympiad && player.isInOlympiadMode())
		{
			return reject(player, itemId, "You cannot use this scroll during Olympiad.");
		}
		if (!_allowEvents && (player.isOnEvent() || player.isRegisteredOnEvent()))
		{
			return reject(player, itemId, "You cannot use this scroll during an event.");
		}
		if (!_allowSiege && (player.isInSiege() || player.isInsideZone(ZoneId.SIEGE)))
		{
			return reject(player, itemId, "You cannot use this scroll during a siege.");
		}
		if (!_allowDungeon && (player.getInstanceId() > 0))
		{
			return reject(player, itemId, "You cannot use this scroll inside an instance.");
		}
		if ((item.getOwnerId() != player.getObjectId()) || (item.getCount() < 1))
		{
			return reject(player, itemId, "The scroll is no longer in your inventory.");
		}

		final int skillId = noblesse ? NOBLESSE_SKILL : MALARIA_SKILL;
		final int skillLevel = noblesse ? 1 : _malariaLevel;
		final int duration = noblesse ? _noblesseDuration : _malariaDuration;
		final Skill skill = SkillData.getInstance().getSkill(skillId, skillLevel);
		if (skill == null)
		{
			LOGGER.warning("Missing custom skill " + skillId + " level " + skillLevel + " for item " + itemId + ".");
			return reject(player, itemId, "This scroll cannot be used right now.");
		}

		final BuffInfo current = player.getEffectList().getBuffInfoByAbnormalType(skill.getAbnormalType());
		if (current != null)
		{
			if (noblesse && (current.getTime() > duration))
			{
				return reject(player, itemId, "A stronger effect is already active.");
			}
			if (!noblesse && ((current.getSkill().getAbnormalLevel() > skill.getAbnormalLevel()) || ((current.getSkill().getAbnormalLevel() == skill.getAbnormalLevel()) && (current.getTime() >= duration))))
			{
				return reject(player, itemId, "A stronger effect is already active.");
			}
		}

		try
		{
			skill.applyEffects(player, player);
			final BuffInfo applied = player.getEffectList().getBuffInfoBySkillId(skillId);
			if (applied == null)
			{
				return reject(player, itemId, "The effect could not be applied; the scroll was not consumed.");
			}
			applied.setAbnormalTime(duration);

			if (!player.destroyItem(ItemProcessType.NONE, item.getObjectId(), 1, player, false))
			{
				player.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, skill);
				return reject(player, itemId, "The scroll could not be consumed; its effect was removed.");
			}

			player.sendMessage(noblesse ? "Noblesse Blessing has been activated for 1 hour." : "Hot Springs Malaria has been activated for 1 hour.");
			LOGGER.info(audit(player, itemId) + " skillId=" + skillId + " skillLevel=" + skillLevel + " duration=" + duration + " result=SUCCESS consumed=1");
			return true;
		}
		catch (Exception e)
		{
			player.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, skill);
			LOGGER.log(Level.WARNING, audit(player, itemId) + " result=ERROR reason=application_exception", e);
			player.sendMessage("The effect could not be applied; the scroll was not consumed.");
			return false;
		}
	}

	private boolean reject(Player player, int itemId, String message)
	{
		player.sendMessage(message);
		if (_debug)
		{
			LOGGER.info(audit(player, itemId) + " result=REJECTED reason=" + message);
		}
		return false;
	}

	private String audit(Player player, int itemId)
	{
		return "character=" + player.getName() + " account=" + player.getAccountName() + " itemId=" + itemId + " instanceId=" + player.getInstanceId() + " zone=[" + player.getX() + "," + player.getY() + "," + player.getZ() + "]";
	}

	private void loadConfig()
	{
		final Properties properties = new Properties();
		try (InputStream input = new FileInputStream(CONFIG_FILE))
		{
			properties.load(input);
			_enableNoblesse = getBoolean(properties, "EnableCustomNoblesseScroll", true);
			_enableMalaria = getBoolean(properties, "EnableCustomMalariaScroll", true);
			_noblesseDuration = getPositiveInt(properties, "NoblesseScrollDuration", 3600);
			_malariaDuration = getPositiveInt(properties, "MalariaScrollDuration", 3600);
			_malariaLevel = Math.max(1, Math.min(10, getPositiveInt(properties, "MalariaScrollLevel", 4)));
			_allowOlympiad = getBoolean(properties, "AllowScrollsInOlympiad", false);
			_allowEvents = getBoolean(properties, "AllowScrollsInEvents", false);
			_allowSiege = getBoolean(properties, "AllowScrollsInSiege", true);
			_allowDungeon = getBoolean(properties, "AllowScrollsInDungeon", true);
			_debug = getBoolean(properties, "BuffScrollDebug", false);
		}
		catch (Exception e)
		{
			LOGGER.log(Level.WARNING, "Could not load " + CONFIG_FILE + "; using safe defaults.", e);
		}
	}

	private boolean getBoolean(Properties properties, String key, boolean defaultValue)
	{
		return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(defaultValue)).trim());
	}

	private int getPositiveInt(Properties properties, String key, int defaultValue)
	{
		try
		{
			return Math.max(1, Integer.parseInt(properties.getProperty(key, Integer.toString(defaultValue)).trim()));
		}
		catch (NumberFormatException e)
		{
			LOGGER.warning("Invalid " + key + " in " + CONFIG_FILE + "; using " + defaultValue + ".");
			return defaultValue;
		}
	}
}
