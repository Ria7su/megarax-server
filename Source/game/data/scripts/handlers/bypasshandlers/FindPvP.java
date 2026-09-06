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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.l2jmobius.Config;
import org.l2jmobius.commons.util.Rnd;
import org.l2jmobius.gameserver.handler.IBypassHandler;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.zone.ZoneType;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.network.serverpackets.CreatureSay;
import org.l2jmobius.gameserver.network.serverpackets.ExShowScreenMessage;
import org.l2jmobius.gameserver.taskmanagers.DynamicPvPZoneManager;

/**
 * Based on Tenkai pvpzone.
 * @author Mobius
 */
public class FindPvP implements IBypassHandler
{
	private static final String[] COMMANDS =
	{
		"FindPvP"
	};
	private static final DynamicZone[] MEGARAX_ZONES =
	{
		new DynamicZone("megarax_forest_dead_pvp", "Forest of the Dead", 52107, -54328, -3158),
		new DynamicZone("megarax_imperial_tomb_pvp", "Imperial Tomb", 181380, -78685, -2755),
		new DynamicZone("megarax_ketra_orc_pvp", "Ketra Orc", 146175, -68856, -3757),
		new DynamicZone("megarax_pagan_temple_pvp", "Pagan Temple", -16368, -38912, -10720)
	};
	
	@Override
	public boolean onCommand(String command, Player player, Creature target)
	{
		if (!Config.ENABLE_FIND_PVP || !target.isNpc())
		{
			return false;
		}
		
		if (teleportToActiveMegaraxZone(player, target))
		{
			return true;
		}
		
		Player mostPvP = null;
		int max = -1;
		for (Player plr : World.getInstance().getPlayers())
		{
			if ((plr == null) //
				|| (plr.getPvpFlag() == 0) //
				|| (plr.getInstanceId() != 0) //
				|| plr.isGM() //
				|| plr.isInsideZone(ZoneId.PEACE) //
				|| plr.isInsideZone(ZoneId.SIEGE) //
				|| plr.isInsideZone(ZoneId.NO_SUMMON_FRIEND))
			{
				continue;
			}
			
			int count = 0;
			for (Player pl : World.getInstance().getVisibleObjects(plr, Player.class))
			{
				if ((pl.getPvpFlag() > 0) && !pl.isInsideZone(ZoneId.PEACE))
				{
					count++;
				}
			}
			
			if (count > max)
			{
				max = count;
				mostPvP = plr;
			}
		}
		
		if (mostPvP != null)
		{
			// Check if the player's clan is already outnumbering the PvP
			if (player.getClan() != null)
			{
				final Map<Integer, Integer> clanNumbers = new HashMap<>();
				int allyId = player.getAllyId();
				if (allyId == 0)
				{
					allyId = player.getClanId();
				}
				
				clanNumbers.put(allyId, 1);
				for (Player known : World.getInstance().getVisibleObjects(mostPvP, Player.class))
				{
					int knownAllyId = known.getAllyId();
					if (knownAllyId == 0)
					{
						knownAllyId = known.getClanId();
					}
					
					if (knownAllyId != 0)
					{
						if (clanNumbers.containsKey(knownAllyId))
						{
							clanNumbers.put(knownAllyId, clanNumbers.get(knownAllyId) + 1);
						}
						else
						{
							clanNumbers.put(knownAllyId, 1);
						}
					}
				}
				
				int biggestAllyId = 0;
				int biggestAmount = 2;
				for (Entry<Integer, Integer> clanNumber : clanNumbers.entrySet())
				{
					if (clanNumber.getValue() > biggestAmount)
					{
						biggestAllyId = clanNumber.getKey();
						biggestAmount = clanNumber.getValue();
					}
				}
				
				if (biggestAllyId == allyId)
				{
					player.sendPacket(new CreatureSay(null, ChatType.WHISPER, target.getName(), "Sorry, your clan/ally is outnumbering the place already so you can't move there."));
					return true;
				}
			}
			
			player.teleToLocation((mostPvP.getX() + Rnd.get(300)) - 150, (mostPvP.getY() + Rnd.get(300)) - 150, mostPvP.getZ());
			player.setSpawnProtection(true);
			if (!player.isGM())
			{
				player.setPvpFlagLasts(System.currentTimeMillis() + Config.PVP_PVP_TIME);
				player.startPvPFlag();
			}
		}
		else
		{
			player.sendPacket(new CreatureSay(null, ChatType.WHISPER, target.getName(), "Sorry, I can't find anyone in flag status right now."));
		}
		
		return false;
	}
	
	@Override
	public String[] getCommandList()
	{
		return COMMANDS;
	}
	
	private boolean teleportToActiveMegaraxZone(Player player, Creature target)
	{
		final String activeZoneName = DynamicPvPZoneManager.getInstance().getActiveZoneName();
		for (DynamicZone dynamicZone : MEGARAX_ZONES)
		{
			if (!dynamicZone.name.equals(activeZoneName))
			{
				continue;
			}
			final ZoneType zone = ZoneManager.getInstance().getZoneByName(dynamicZone.zoneName);
			if ((zone == null) || !zone.isEnabled())
			{
				continue;
			}
			
			player.teleToLocation(dynamicZone.x, dynamicZone.y, dynamicZone.z);
			player.setSpawnProtection(true);
			if (!player.isGM())
			{
				player.setPvpFlagLasts(System.currentTimeMillis() + Config.PVP_PVP_TIME);
				player.startPvPFlag();
			}
			player.sendPacket(new CreatureSay(null, ChatType.WHISPER, target.getName(), "Active Megarax PvP zone: " + dynamicZone.name + "."));
			player.sendPacket(new ExShowScreenMessage("Bienvenido al combate.", 3000));
			return true;
		}
		return false;
	}
	
	private static class DynamicZone
	{
		private final String zoneName;
		private final String name;
		private final int x;
		private final int y;
		private final int z;
		
		private DynamicZone(String zoneName, String name, int x, int y, int z)
		{
			this.zoneName = zoneName;
			this.name = name;
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}
}
