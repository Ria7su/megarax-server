package custom.MegaraxMidgameProgression;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;

import ai.AbstractNpcAI;

/** Controlled Rax Coin progression for level 51-63 Lilim and Nephilim. */
public final class MegaraxMidgameProgression extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxMidgameProgression.class.getName());
	private static final File CONFIG = new File("config/Custom/MegaraxMidgameProgression.ini");

	private boolean _enabled;
	private int[] _npcIds;
	private int _itemId;
	private double _chance;
	private int _minAmount;
	private int _maxAmount;
	private int _minPlayerLevel;
	private int _maxPlayerAboveNpc;

	private MegaraxMidgameProgression()
	{
		loadConfig();
		if (_enabled && (_npcIds.length > 0))
		{
			addKillId(_npcIds);
		}
		LOGGER.info("MegaraxMidgameProgression: enabled=" + _enabled + ", npcs=" + _npcIds.length + ", chance=" + _chance + "%.");
	}

	private void loadConfig()
	{
		final Properties properties = new Properties();
		try (InputStreamReader reader = new InputStreamReader(new FileInputStream(CONFIG), StandardCharsets.UTF_8))
		{
			properties.load(reader);
			_enabled = Boolean.parseBoolean(properties.getProperty("MidgameRaxEnabled", "false"));
			_npcIds = Arrays.stream(properties.getProperty("MidgameRaxNpcIds", "").split(",")).map(String::trim).filter(value -> !value.isEmpty()).mapToInt(Integer::parseInt).toArray();
			_itemId = Math.max(1, Integer.parseInt(properties.getProperty("MidgameRaxItemId", "9142")));
			_chance = Math.max(0, Math.min(100, Double.parseDouble(properties.getProperty("MidgameRaxChancePercent", "75"))));
			_minAmount = Math.max(1, Integer.parseInt(properties.getProperty("MidgameRaxMinAmount", "1")));
			_maxAmount = Math.max(_minAmount, Integer.parseInt(properties.getProperty("MidgameRaxMaxAmount", "1")));
			_minPlayerLevel = Math.max(1, Integer.parseInt(properties.getProperty("MidgameRaxMinPlayerLevel", "48")));
			_maxPlayerAboveNpc = Math.max(0, Integer.parseInt(properties.getProperty("MidgameRaxMaxPlayerAboveNpc", "8")));
		}
		catch (Exception e)
		{
			_enabled = false;
			_npcIds = new int[0];
			LOGGER.log(Level.SEVERE, "MegaraxMidgameProgression: Could not load configuration.", e);
		}
	}

	@Override
	public void onKill(Npc npc, Player killer, boolean isSummon)
	{
		if (!_enabled || (npc == null) || (killer == null) || (killer.getLevel() < _minPlayerLevel) || ((killer.getLevel() - npc.getLevel()) > _maxPlayerAboveNpc))
		{
			return;
		}
		if (ThreadLocalRandom.current().nextDouble(100) >= _chance)
		{
			return;
		}
		final int amount = ThreadLocalRandom.current().nextInt(_minAmount, _maxAmount + 1);
		killer.addItem(ItemProcessType.REWARD, _itemId, amount, npc, true);
	}

	public static void main(String[] args)
	{
		new MegaraxMidgameProgression();
	}
}
