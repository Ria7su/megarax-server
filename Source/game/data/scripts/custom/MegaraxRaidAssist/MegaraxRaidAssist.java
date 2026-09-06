/*
 * Megarax custom raid assist.
 */
package custom.MegaraxRaidAssist;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;

import ai.AbstractNpcAI;

/**
 * Forces raid guards to assist their leader as soon as the raid is attacked.
 */
public class MegaraxRaidAssist extends AbstractNpcAI
{
	private static final int HATE = 99999;
	
	private static final int[] RAID_LEADERS =
	{
		25001, // Greyclaw Kutus
		25004, // Turek Mercenary Captain
		25007, // Retreat Spider Cletu
		25010, // Furious Thieles
		25013, // Ghost of Peasant Leader
		25016, // The 3rd Underwater Guardian
		25020, // Breka Warlock Pastu
		25023, // Stakato Queen Zyrnna
		25026, // Katu Van Leader Atui
		25029, // Atraiban
		25032, // Eva's Guardian Millenu
		25035, // Shilen's Messenger Cabrio
		25038, // Tirak
		25041, // Remmel
		25044, // Barion
		25047, // Karte
		25051, // Rahha
		25054, // Kernon
		25057, // Biconne of Blue Sky
		25060, // Unrequited Kael
		25064, // Wizard of Storm Teruk
		25067, // Captain of Red Flag Shaka
		25070, // Enchanted Forest Watcher Ruell
		25073, // Bloody Priest Rudelto
		25076, // Princess Molrang
		25079, // Cat's Eye Bandit
		25082, // Leader of Cat Gang
		25085, // Timak Orc Chief Ranger
		25089, // Soulless Wild Boar
		25092, // Korim
		25095, // Elf Renoa
		25099, // Rotten Tree Repiro
		25103, // Sorcerer Isirr
		25106, // Ghost of the Well Lidia
		25109, // Antharas Priest Cloe
		25112, // Agent of Beres, Meana
		25115, // Icarus Sample 1
		25119, // Messenger of Fairy Queen Berun
		25122, // Refugee Hopeful Leo
		25128, // Vuku Grand Seer Gharmash
		25131, // Carnage Lord Gato
		25134, // Leto Chief Talkin
		25137, // Beleth's Seer Sephia
		25140, // Hekaton Prime
		25143, // Fire of Wrath Shuriel
		25146, // Serpent Demon Bifrons
		25149, // Zombie Lord Crowl
		25152, // Flame Lord Shadar
		25155, // Shaman King Selu
		25159, // Paniel the Unicorn
		25166, // Ikuntai
		25170, // Lizardmen Leader Hellion
		25173, // Tiger King Karuta
		25176, // Black Lily
		25179, // Guardian of the Statue of Giant Karum
		25182, // Demon Kurikups
		25185, // Tasaba Patriarch Hellena
		25189, // Cronos's Servitor Mumu
		25192, // Earth Protector Panathen
		25199, // Water Dragon Seer Sheshark
		25202, // Krokian Padisha Sobekk
		25205, // Ocean Flame Ashakiel
		25208, // Water Couatle Ateka
		25211, // Sebek
		25214, // Fafurion's Page Sika
		25217, // Cursed Clara
		25220, // Death Lord Hallate
		25223, // Soul Collector Acheron
		25226, // Roaring Lord Kastor
		25230, // Timak Seer Ragoth
		25235, // Vanor Chief Kandra
		25238, // Abyss Brukunt
		25241, // Harit Hero Tamash
		25245, // Last Lesser Giant Glaki
		25249, // Palatanos of Horrific Power
		25252, // Palibati Queen Themis
		25256, // Taik High Prefect Arak
		25260, // Iron Giant Totem
		25263, // Kernon's Faithful Servant Kelone
		25266, // Bloody Empress Decarbia
		25269, // Beast Lord Behemoth
		25273, // Carnamakos
		25277, // Lilith's Witch Marilion
		25283, // Lilith
		25286, // Anakim
		25290, // Daimon the White-Eyed
		25293, // Hesti Guardian Deity of the Hot Springs
		25296, // Icicle Emperor Bumbalump
		25299, // Ketra's Hero Hekaton
		25302, // Ketra's Commander Tayr
		25306, // Soul of Fire Nastron
		25309, // Varka's Hero Shadith
		25312, // Varka's Commander Mos
		25316, // Soul of Water Ashutar
		25319, // Ember
		25322, // Demon's Agent Falston
		25325, // Flame of Splendor Barakiel
		25328, // Eilhalder von Hellmann
		25352, // Giant Wasteland Basilisk
		25354, // Gargoyle Lord Sirocco
		25357, // Sukar Wererat Chief
		25360, // Tiger Hornet
		25362, // Tracker Leader Sharuk
		25366, // Kuroboros' Priest
		25369, // Soul Scavenger
		25373, // Malex Herald of Dagoniel
		25375, // Zombie Lord Farakelsus
		25378, // Madness Beast
		25380, // Kaysha Herald of Icarus
		25383, // Revenant of Sir Calibus
		25385, // Evil Spirit Tempest
		25388, // Red Eye Captain Trakia
		25392, // Captain of Queen's Royal Guards
		25395, // Archon Suscepter
		25398, // Eye of Beleth
		25401, // Skyla
		25404, // Corsair Captain Kylon
		25407, // Lord Ishka
		25410, // Road Scavenger Leader
		25412, // Necrosentinel Royal Guard
		25415, // Nakondas
		25418, // Dread Avenger Kraven
		25420, // Orfen's Handmaiden
		25423, // Fairy Queen Timiniel
		25426, // Betrayer of Urutu Freki
		25429, // Mammon Collector Talos
		25431, // Flamestone Golem
		25434, // Bandit Leader Barda
		25438, // Thief Kelbar
		25441, // Evil Spirit Cyrion
		25444, // Enmity Ghost Ramdal
		25447, // Immortal Savior Mardil
		25450, // Cherub Galaxia
		25453, // Meanas Anor
		25456, // Mirror of Oblivion
		25460, // Deadman Ereve
		25463, // Harit Guardian Garangky
		25467, // Gorgolos
		25470, // Last Titan Utenus
		25473, // Grave Robber Kim
		25475, // Ghost Knight Kabed
		25478, // Shilen's Priest Hisilrome
		25481, // Magus Kenishee
		25484, // Zaken's Chief Mate Tillion
		25487, // Water Spirit Lian
		25490, // Gwindorr
		25493, // Eva's Spirit Niniel
		25496, // Fafurion's Envoy Pingolpin
		25498, // Fafurion's Henchman Istary
		25501, // Boss Akata
		25504, // Nellis' Vengeful Spirit
		25506, // Rayito the Looter
		25509, // Dark Shaman Varangka
		25514, // Queen Shyeed
		25517, // Master Anays
		25524, // Flamestone Giant
		29001, // Queen Ant
		29006, // Core
		29014, // Orfen
		29030, // Fenril Hound Kerinne
		29033, // Fenril Hound Freki
		29037, // Fenril Hound Kinaz
		29040, // Wings of Flame, Ixion
		29056, // Ice Fairy Sirra
		29062, // High Priestess van Halter
		29096, // Lord of Splendor Anays
	};
	
	private MegaraxRaidAssist()
	{
		addAttackId(RAID_LEADERS);
	}
	
	@Override
	public void onAttack(Npc npc, Player attacker, int damage, boolean isSummon)
	{
		if ((attacker == null) || !npc.isMonster())
		{
			return;
		}
		
		final Monster leader = npc.asMonster();
		if (!leader.hasMinions())
		{
			return;
		}
		
		for (Monster minion : leader.getMinionList().getSpawnedMinions())
		{
			if ((minion == null) || minion.isDead() || minion.isDecayed())
			{
				continue;
			}
			
			minion.setRunning();
			minion.addDamageHate(attacker, 0, HATE);
			minion.getAI().setIntention(Intention.ATTACK, attacker);
		}
	}
	
	public static void main(String[] args)
	{
		new MegaraxRaidAssist();
	}
}
