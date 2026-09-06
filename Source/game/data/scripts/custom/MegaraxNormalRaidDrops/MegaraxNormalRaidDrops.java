package custom.MegaraxNormalRaidDrops;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;

import ai.AbstractNpcAI;
import custom.MegaraxNormalRaidDrops.NormalRaidRewardsConfig.Tier;

/** Final, rate-independent rewards for normal world Raid Bosses. */
public class MegaraxNormalRaidDrops extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxNormalRaidDrops.class.getName());
	private static final int ADENA = 57;
	private static final int RAX_COIN = 9142;
	private static final int[] NORMAL_RAIDS =
	{
		25001, 25004, 25007, 25010, 25013, 25016, 25019, 25020, 25023, 25026, 25029, 25032, 25035, 25038, 25041, 25044,
		25047, 25050, 25051, 25054, 25057, 25060, 25063, 25064, 25067, 25070, 25073, 25076, 25079, 25082, 25085, 25088,
		25089, 25092, 25095, 25098, 25099, 25102, 25103, 25106, 25109, 25112, 25115, 25118, 25119, 25122, 25125, 25126,
		25127, 25128, 25131, 25134, 25137, 25140, 25143, 25146, 25149, 25152, 25155, 25158, 25159, 25162, 25163, 25166,
		25169, 25170, 25173, 25176, 25179, 25182, 25185, 25188, 25189, 25192, 25198, 25199, 25202, 25205, 25208, 25211,
		25214, 25217, 25220, 25223, 25226, 25229, 25230, 25233, 25234, 25235, 25238, 25241, 25244, 25245, 25248, 25249,
		25252, 25255, 25256, 25259, 25260, 25263, 25266, 25269, 25272, 25273, 25276, 25277, 25280, 25281, 25282, 25290,
		25293, 25296, 25299, 25302, 25305, 25306, 25309, 25312, 25315, 25316, 25319, 25322, 25325, 25328, 25352, 25354,
		25357, 25360, 25362, 25365, 25366, 25369, 25372, 25373, 25375, 25378, 25380, 25383, 25385, 25388, 25391, 25392,
		25394, 25395, 25398, 25401, 25404, 25407, 25410, 25412, 25415, 25418, 25420, 25423, 25426, 25429, 25431, 25434,
		25437, 25438, 25441, 25444, 25447, 25450, 25453, 25456, 25460, 25463, 25467, 25470, 25473, 25475, 25478, 25481,
		25484, 25487, 25490, 25493, 25496, 25498, 25501, 25504, 25506, 25509, 25514, 25517, 25523, 25524, 25527
	};

	private MegaraxNormalRaidDrops()
	{
		NormalRaidRewardsConfig.load();
		if (NormalRaidRewardsConfig.ENABLED)
		{
			addKillId(NORMAL_RAIDS);
			LOGGER.info("Megarax normal raid drops loaded for " + NORMAL_RAIDS.length + " Raid Bosses with configurable x10 rewards.");
		}
		else
		{
			LOGGER.info("Megarax normal raid drops are disabled by configuration.");
		}
	}

	@Override
	public void onKill(Npc npc, Player killer, boolean isSummon)
	{
		if ((npc == null) || (killer == null) || (npc.getInstanceId() != 0))
		{
			return;
		}

		final int level = npc.getLevel();
		final Tier reward = NormalRaidRewardsConfig.getTier(level);
		if (reward == null)
		{
			return;
		}

		npc.dropItem(killer, RAX_COIN, reward.rax);
		final int adena = getRandom(reward.adenaMin, reward.adenaMax);
		npc.dropItem(killer, ADENA, adena);
		if (getRandom(100) < reward.lifeStoneChance)
		{
			npc.dropItem(killer, getLifeStone(level), 1);
		}
		LOGGER.info("Normal raid reward: npcId=" + npc.getId() + ", name=" + npc.getName() + ", level=" + level + ", killer=" + killer.getName() + ", summonKill=" + isSummon + ", rax=" + reward.rax + ", adena=" + adena + ".");
	}

	private int getLifeStone(int level)
	{
		if (level <= 48) return 8753; // Top-Grade Life Stone: level 46 (lowest available)
		if (level <= 51) return 8754;
		if (level <= 54) return 8755;
		if (level <= 57) return 8756;
		if (level <= 60) return 8757;
		if (level <= 63) return 8758;
		if (level <= 66) return 8759;
		if (level <= 69) return 8760;
		if (level <= 75) return 8761;
		return 8762; // Top-Grade Life Stone: level 76 (maximum available)
	}

	public static void main(String[] args)
	{
		new MegaraxNormalRaidDrops();
	}
}
