/*
 * This file is part of the L2J Mobius project.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package custom.RaidbossInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.l2jmobius.commons.util.StringUtil;
import org.l2jmobius.gameserver.data.SpawnTable;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.Spawn;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;

import ai.AbstractNpcAI;

/**
 * Raidboss Info AI.<br>
 * Original Jython script Kerberos.
 * @author Nyaran
 */
public class RaidbossInfo extends AbstractNpcAI
{
	// @formatter:off
	private static final int[] NPC =
	{
		31729, 31730, 31731, 31732, 31733, 31734, 31735, 31736, 31737, 31738,
		31739, 31740, 31741, 31742, 31743, 31744, 31745, 31746, 31747, 31748,
		31750, 31751, 31752, 31755, 31756, 31757, 31758,
		31759, 31760, 31761, 31762, 31763, 31764, 31765, 31766, 31767, 31768,
		31769, 31770, 31771, 31772, 31773, 31774, 31775, 31776, 31777, 31778,
		31779, 31780, 31781, 31782, 31783, 31784, 31785, 31786, 31787, 31788,
		31789, 31790, 31791, 31792, 31793, 31794, 31795, 31796, 31797, 31798,
		31799, 31800, 31801, 31802, 31803, 31804, 31805, 31806, 31807, 31808,
		31809, 31810, 31811, 31812, 31813, 31814, 31815, 31816, 31817, 31818,
		31819, 31820, 31821, 31822, 31823, 31824, 31825, 31826, 31827, 31828,
		31829, 31830, 31831, 31832, 31833, 31834, 31835, 31836, 31837, 31838,
		31839, 31840, 31841
	};
	// @formatter:on
	private static final List<Integer> RAIDS = new ArrayList<>();
	private static final int TELEPORT_FEE = 1000;
	private static final Set<Integer> NORMAL_RAIDS = new HashSet<>();
	private static final int[] NORMAL_RAID_IDS =
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
	
	private RaidbossInfo()
	{
		addStartNpc(NPC);
		addTalkId(NPC);
		
		// Add all Raid Bosses to RAIDS list
		for (NpcTemplate raid : NpcData.getInstance().getAllNpcOfClassType("RaidBoss"))
		{
			RAIDS.add(raid.getId());
		}
		for (int raidId : NORMAL_RAID_IDS)
		{
			NORMAL_RAIDS.add(raidId);
		}
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		String htmltext = event;
		if (StringUtil.isNumeric(event))
		{
			htmltext = null;
			final int bossId = Integer.parseInt(event);
			if (RAIDS.contains(bossId))
			{
				final Spawn spawn = SpawnTable.getInstance().getAnySpawn(bossId);
				if (spawn != null)
				{
					final Location loc = spawn;
					player.getRadar().addMarker(loc.getX(), loc.getY(), loc.getZ());
					if (NORMAL_RAIDS.contains(bossId))
					{
						if (!player.destroyItemByItemId(ItemProcessType.FEE, 57, TELEPORT_FEE, npc, true))
						{
							return null;
						}
						player.teleToLocation(findSafeLocation(loc));
						player.setSpawnProtection(true);
						player.sendMessage("You have arrived near " + NpcData.getInstance().getTemplate(bossId).getName() + ".");
					}
				}
			}
		}
		
		return htmltext;
	}

	private Location findSafeLocation(Location raidLocation)
	{
		for (int attempt = 0; attempt < 24; attempt++)
		{
			final double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
			final int distance = ThreadLocalRandom.current().nextInt(250, 451);
			final int x = raidLocation.getX() + (int) Math.round(Math.cos(angle) * distance);
			final int y = raidLocation.getY() + (int) Math.round(Math.sin(angle) * distance);
			final int z = GeoEngine.getInstance().getSpawnHeight(x, y, raidLocation.getZ());
			if ((Math.abs(z - raidLocation.getZ()) <= 250) && GeoEngine.getInstance().canMoveToTarget(raidLocation.getX(), raidLocation.getY(), raidLocation.getZ(), x, y, z, 0))
			{
				return new Location(x, y, z);
			}
		}
		return new Location(raidLocation.getX(), raidLocation.getY(), GeoEngine.getInstance().getSpawnHeight(raidLocation));
	}
	
	@Override
	public String onTalk(Npc npc, Player player)
	{
		return "info.htm";
	}
	
	public static void main(String[] args)
	{
		new RaidbossInfo();
	}
}
