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
package handlers.itemhandlers;

import org.l2jmobius.gameserver.model.actor.Playable;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.ExShowScreenMessage;

/**
 * Item skills not allowed on Olympiad.
 */
	public class ItemSkills extends ItemSkillsTemplate
{
	private static final int EXP_SP_X2_ITEM_ID = 5593;

	@Override
	public boolean onItemUse(Playable playable, Item item, boolean forceUse)
	{
		final Player player = playable.asPlayer();
		if ((player != null) && player.isInOlympiadMode())
		{
			player.sendPacket(SystemMessageId.YOU_CANNOT_USE_THAT_ITEM_IN_A_GRAND_OLYMPIAD_GAMES_MATCH);
			return false;
		}
		
		final boolean used = super.onItemUse(playable, item, forceUse);
		if (used && (player != null) && (item.getId() == EXP_SP_X2_ITEM_ID))
		{
			player.sendMessage("Exp/SP x2 activated for 3 hours.");
			player.sendPacket(new ExShowScreenMessage("Exp/SP x2 active for 3 hours", ExShowScreenMessage.TOP_CENTER, 5000, 0, true, true));
		}

		return used;
	}
}
