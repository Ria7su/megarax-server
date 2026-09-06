/*
 * Custom Borak AI - L2JMobius Interlude
 */
package custom.Borak;

import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.skill.Skill;

import ai.AbstractNpcAI;

public class Borak extends AbstractNpcAI
{
	private static final int BORAK = 9918;
	private static final int WAR_CRY = 78;
	private static final int WAR_CRY_LEVEL = 2;
	private static final int SHOCK = 4072;
	private static final int SHOCK_LEVEL = 10;
	private static final int BORAK_FRENZY = 90090;
	private static final int BORAK_FRENZY_LEVEL = 1;
	private static final int FRENZY_HP_PERCENT = 30;
	private static final int SHOCK_CHANCE = 18;
	
	private static final String WAR_CRY_USED = "BORAK_WAR_CRY_USED";
	private static final String FRENZY_USED = "BORAK_FRENZY_USED";
	
	private Borak()
	{
		addSpawnId(BORAK);
		addAttackId(BORAK);
	}
	
	@Override
	public void onSpawn(Npc npc)
	{
		npc.getVariables().set(WAR_CRY_USED, 0);
		npc.getVariables().set(FRENZY_USED, 0);
	}
	
	@Override
	public void onAttack(Npc npc, Player attacker, int damage, boolean isSummon)
	{
		if ((attacker == null) || npc.isDead() || npc.isCastingNow())
		{
			return;
		}
		
		final double hpPercent = (npc.getCurrentHp() / npc.getMaxHp()) * 100;
		if ((hpPercent <= FRENZY_HP_PERCENT) && (npc.getVariables().getInt(FRENZY_USED, 0) == 0))
		{
			npc.getVariables().set(FRENZY_USED, 1);
			tryCast(npc, npc, BORAK_FRENZY, BORAK_FRENZY_LEVEL);
			return;
		}
		
		if (npc.getVariables().getInt(WAR_CRY_USED, 0) == 0)
		{
			npc.getVariables().set(WAR_CRY_USED, 1);
			tryCast(npc, npc, WAR_CRY, WAR_CRY_LEVEL);
			return;
		}
		
		if (getRandom(100) < SHOCK_CHANCE)
		{
			tryCast(npc, attacker, SHOCK, SHOCK_LEVEL);
		}
	}
	
	private boolean tryCast(Npc npc, Creature target, int skillId, int skillLevel)
	{
		final Skill skill = SkillData.getInstance().getSkill(skillId, skillLevel);
		if ((skill == null) || npc.isSkillDisabled(skill) || npc.isCastingNow())
		{
			return false;
		}
		
		npc.setTarget(target);
		if (!npc.checkDoCastConditions(skill))
		{
			return false;
		}
		
		npc.doCast(skill);
		return true;
	}
	
	public static void main(String[] args)
	{
		new Borak();
	}
}
