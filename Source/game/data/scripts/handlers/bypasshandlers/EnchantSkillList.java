/*
 * Copyright (c) 2013 L2jMobius
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package handlers.bypasshandlers;

import org.l2jmobius.gameserver.data.sql.EnchantSkillTreesTable;
import org.l2jmobius.gameserver.handler.IBypassHandler;
import org.l2jmobius.gameserver.model.EnchantSkillLearn;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Folk;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.AcquireSkillDone;
import org.l2jmobius.gameserver.network.serverpackets.ActionFailed;
import org.l2jmobius.gameserver.network.serverpackets.ExEnchantSkillList;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;

/**
 * @author Mobius
 */
public class EnchantSkillList implements IBypassHandler
{
	private static final int MEGARAX_SHOP = 9900;
	
	private static final String[] COMMANDS =
	{
		"EnchantSkillList"
	};
	
	@Override
	public boolean onCommand(String command, Player player, Creature target)
	{
		if ((player == null) || (target == null) || !target.isNpc())
		{
			return false;
		}
		
		final Npc npc = target.asNpc();
		if (npc.getId() == MEGARAX_SHOP)
		{
			showMegaraxEnchantSkillList(player, npc);
			return true;
		}
		
		if (!(target instanceof Folk))
		{
			return false;
		}
		
		((Folk) target).showEnchantSkillList(player);
		return true;
	}
	
	private void showMegaraxEnchantSkillList(Player player, Npc npc)
	{
		player.setLastFolkNPC(npc);
		
		if (player.getPlayerClass().level() < 3)
		{
			final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
			html.setHtml("<html><body>You must have 3rd class change quest completed.</body></html>");
			player.sendPacket(html);
			return;
		}
		
		final ExEnchantSkillList list = new ExEnchantSkillList();
		int count = 0;
		for (EnchantSkillLearn skillLearn : EnchantSkillTreesTable.getInstance().getAvailableEnchantSkills(player))
		{
			count++;
			list.addSkill(skillLearn.getId(), skillLearn.getLevel(), skillLearn.getSpCost(), skillLearn.getExp());
		}
		
		if (count > 0)
		{
			player.sendPacket(list);
		}
		else
		{
			player.sendPacket(SystemMessageId.THERE_IS_NO_SKILL_THAT_ENABLES_ENCHANT);
			if (player.getLevel() < 74)
			{
				final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_DO_NOT_HAVE_ANY_FURTHER_SKILLS_TO_LEARN_COME_BACK_WHEN_YOU_HAVE_REACHED_LEVEL_S1);
				sm.addInt(74);
				player.sendPacket(sm);
			}
			else
			{
				player.sendPacket(SystemMessageId.THERE_ARE_NO_OTHER_SKILLS_TO_LEARN);
			}
			player.sendPacket(AcquireSkillDone.STATIC_PACKET);
		}
		
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public String[] getCommandList()
	{
		return COMMANDS;
	}
}
