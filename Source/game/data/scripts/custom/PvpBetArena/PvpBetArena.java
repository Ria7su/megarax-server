/*
 * Custom Megarax PvP Bet Arena - L2JMobius Interlude
 */
package custom.PvpBetArena;

import java.text.NumberFormat;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.Summon;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.annotations.RegisterEvent;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDeath;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogout;
import org.l2jmobius.gameserver.model.events.listeners.AbstractEventListener;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.olympiad.Olympiad;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.network.serverpackets.ExShowScreenMessage;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

import ai.AbstractNpcAI;

/**
 * 1v1 Rax Coin challenge system.
 */
public class PvpBetArena extends AbstractNpcAI
{
	private static final int GLADIATOR = 900010;
	private static final int RAX_COIN = 9142;
	private static final int ROUNDS_TO_WIN = 2;
	private static final int ROUND_DELAY = 10000;
	private static final int START_DELAY = 10000;
	private static final int MAX_OPEN_CHALLENGES = 20;
	private static final String RAX_COIN_ICON = "favero_l2_icons01.coin_11th_token";
	private static final int[] ALLOWED_BETS =
	{
		2000,
		5000,
		10000,
		20000,
		50000,
		100000
	};
	
	private static final Location EXIT_LOC = new Location(83400, 147943, -3404);
	private static final Location PLAYER_ONE_LOC = new Location(147447, 46722, -3416, 49000);
	private static final Location PLAYER_TWO_LOC = new Location(151536, 46722, -3416, 16000);
	
	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
	private static final AtomicInteger NEXT_CHALLENGE_ID = new AtomicInteger(1);
	private static final Map<Integer, Challenge> OPEN_CHALLENGES = new ConcurrentHashMap<>();
	private static final Map<Integer, Match> ACTIVE_MATCHES = new ConcurrentHashMap<>();
	private static volatile Match CURRENT_MATCH;
	
	private PvpBetArena()
	{
		addStartNpc(GLADIATOR);
		addTalkId(GLADIATOR);
		addFirstTalkId(GLADIATOR);
	}
	
	@Override
	public String onFirstTalk(Npc npc, Player player)
	{
		showMainPage(player, npc);
		return null;
	}
	
	@Override
	public String onTalk(Npc npc, Player player)
	{
		showMainPage(player, npc);
		return null;
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		if (event.startsWith("StartMatch_"))
		{
			startMatch(Integer.parseInt(event.substring(11)));
			return null;
		}
		else if (event.startsWith("NextRound_"))
		{
			startNextRound(Integer.parseInt(event.substring(10)));
			return null;
		}
		
		if (player == null)
		{
			return null;
		}
		
		if (event.equals("main"))
		{
			showMainPage(player, npc);
		}
		else if (event.equals("create"))
		{
			showCreatePage(player, npc);
		}
		else if (event.startsWith("createBet_"))
		{
			createChallenge(player, npc, Integer.parseInt(event.substring(10)));
		}
		else if (event.startsWith("accept_"))
		{
			acceptChallenge(player, npc, Integer.parseInt(event.substring(7)));
		}
		else if (event.startsWith("cancel_"))
		{
			cancelChallenge(player, npc, Integer.parseInt(event.substring(7)));
		}
		else if (event.equals("rules"))
		{
			showRulesPage(player, npc);
		}
		return null;
	}
	
	private void showMainPage(Player player, Npc npc)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Megarax Gladiator</title><body><center>");
		appendHeader(html, "MEGARAX GLADIATOR", "1v1 Rax Coin Challenge");
		html.append("<button value=\"Gladiator Arena\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\">");
		html.append("<tr><td width=36 align=center><button action=\"bypass Quest PvpBetArena create\" width=32 height=32 back=\"icon.skill0344\" fore=\"icon.skill0344\"></td>");
		html.append("<td width=178><font color=\"0099FF\">Create PvP Bet</font><br1><font color=\"ae9977\">Stake Rax Coins for a 1v1 fight.</font></td>");
		html.append("<td width=64 align=center><button value=\"Open\" action=\"bypass Quest PvpBetArena create\" width=60 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td></tr>");
		html.append("<tr><td height=3></td></tr>");
		html.append("<tr><td width=36 align=center><button action=\"bypass Quest PvpBetArena rules\" width=32 height=32 back=\"icon.skill1374\" fore=\"icon.skill1374\"></td>");
		html.append("<td width=178><font color=\"0099FF\">Rules</font><br1><font color=\"ae9977\">Best of 3. Winner takes all.</font></td>");
		html.append("<td width=64 align=center><button value=\"Open\" action=\"bypass Quest PvpBetArena rules\" width=60 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td></tr>");
		html.append("</table><br>");
		html.append("<button value=\"Open Challenges\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		
		if (OPEN_CHALLENGES.isEmpty())
		{
			html.append("<table width=292 bgcolor=\"000000\"><tr><td align=center><font color=\"808080\">No active bets.</font></td></tr></table>");
		}
		else
		{
			for (Challenge challenge : OPEN_CHALLENGES.values())
			{
				html.append("<table width=292 bgcolor=\"111111\"><tr>");
				html.append("<td width=36 align=center><button action=\"bypass Quest PvpBetArena main\" width=32 height=32 back=\"").append(RAX_COIN_ICON).append("\" fore=\"").append(RAX_COIN_ICON).append("\"></td>");
				html.append("<td width=178><font color=\"0099FF\">").append(challenge.creatorName).append("</font><br1><font color=\"B09878\">").append(format(challenge.betAmount)).append(" Rax Coin</font></td>");
				html.append("<td width=64 align=center>");
				if (challenge.creatorObjectId == player.getObjectId())
				{
					html.append("<button value=\"Cancel\" action=\"bypass Quest PvpBetArena cancel_").append(challenge.id).append("\" width=60 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
				}
				else
				{
					html.append("<button value=\"Accept\" action=\"bypass Quest PvpBetArena accept_").append(challenge.id).append("\" width=60 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
				}
				html.append("</td></tr></table>");
				html.append("<img src=\"L2UI.SquareGray\" width=292 height=1>");
			}
		}
		
		appendFooter(html, "Currency: Rax Coin (9142)");
		sendHtml(player, npc, html.toString());
	}
	
	private void showCreatePage(Player player, Npc npc)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Create Gladiator Bet</title><body><center>");
		appendHeader(html, "CREATE PVP BET", "Stake Rax Coins");
		html.append("<button value=\"Choose Bet Amount\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\">");
		for (int amount : ALLOWED_BETS)
		{
			html.append("<tr><td width=36 align=center><button action=\"bypass Quest PvpBetArena createBet_").append(amount).append("\" width=32 height=32 back=\"").append(RAX_COIN_ICON).append("\" fore=\"").append(RAX_COIN_ICON).append("\"></td>");
			html.append("<td width=178><font color=\"0099FF\">").append(format(amount)).append(" Rax Coin</font><br1><font color=\"ae9977\">Best of 3 Coliseum match.</font></td>");
			html.append("<td width=64 align=center><button value=\"Create\" action=\"bypass Quest PvpBetArena createBet_").append(amount).append("\" width=60 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\"></td></tr>");
			html.append("<tr><td height=3></td></tr>");
		}
		html.append("</table><br>");
		html.append("<button value=\"Back\" action=\"bypass Quest PvpBetArena main\" width=60 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		appendFooter(html, "MEGARAX INTERLUDE");
		sendHtml(player, npc, html.toString());
	}
	
	private void showRulesPage(Player player, Npc npc)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html><title>Gladiator Rules</title><body><center>");
		appendHeader(html, "GLADIATOR RULES", "Megarax PvP Bet");
		html.append("<button value=\"Match Rules\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
		html.append("<table width=292 bgcolor=\"000000\"><tr><td>");
		html.append("<font color=\"0099FF\">1v1 Coliseum</font><br1><font color=\"ae9977\">Both players fight in the Coliseum.</font><br>");
		html.append("<font color=\"0099FF\">Best of 3</font><br1><font color=\"ae9977\">First player to win 2 rounds wins.</font><br>");
		html.append("<font color=\"0099FF\">Buffs Allowed</font><br1><font color=\"ae9977\">Current buffs are not removed.</font><br>");
		html.append("<font color=\"0099FF\">Safe Refund</font><br1><font color=\"ae9977\">Disconnect or abandon refunds both players.</font><br>");
		html.append("<font color=\"0099FF\">Anti Feed</font><br1><font color=\"ae9977\">Same IP or same HWID cannot fight each other.</font><br>");
		html.append("<font color=\"0099FF\">Allowed Bets</font><br1><font color=\"ae9977\">2,000 / 5,000 / 10,000 / 20,000 / 50,000 / 100,000 Rax Coin.</font>");
		html.append("</td></tr></table><br>");
		html.append("<button value=\"Back\" action=\"bypass Quest PvpBetArena main\" width=60 height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
		appendFooter(html, "MEGARAX INTERLUDE");
		sendHtml(player, npc, html.toString());
	}
	
	private void createChallenge(Player player, Npc npc, int betAmount)
	{
		if (!isAllowedBet(betAmount))
		{
			player.sendMessage("Invalid bet amount.");
			showCreatePage(player, npc);
			return;
		}
		
		if (OPEN_CHALLENGES.size() >= MAX_OPEN_CHALLENGES)
		{
			player.sendMessage("There are too many open challenges right now.");
			showMainPage(player, npc);
			return;
		}
		
		if (!canUseSystem(player))
		{
			showMainPage(player, npc);
			return;
		}
		
		if (hasOpenChallenge(player) || isInActiveMatch(player))
		{
			player.sendMessage("You already have an active Gladiator bet.");
			showMainPage(player, npc);
			return;
		}
		
		if (!takeCoins(player, betAmount))
		{
			player.sendMessage("You do not have enough Rax Coins.");
			showCreatePage(player, npc);
			return;
		}
		
		final int challengeId = NEXT_CHALLENGE_ID.getAndIncrement();
		final Challenge challenge = new Challenge(challengeId, player.getObjectId(), player.getName(), betAmount);
		OPEN_CHALLENGES.put(challengeId, challenge);
		player.setRegisteredOnEvent(true);
		addLogoutListener(player);
		player.sendMessage("Your Gladiator bet has been created for " + format(betAmount) + " Rax Coins.");
		showMainPage(player, npc);
	}
	
	private void acceptChallenge(Player player, Npc npc, int challengeId)
	{
		final Challenge challenge = OPEN_CHALLENGES.get(challengeId);
		if (challenge == null)
		{
			player.sendMessage("This challenge is no longer available.");
			showMainPage(player, npc);
			return;
		}
		
		if (challenge.creatorObjectId == player.getObjectId())
		{
			player.sendMessage("You cannot accept your own challenge.");
			showMainPage(player, npc);
			return;
		}
		
		if (CURRENT_MATCH != null)
		{
			player.sendMessage("A Gladiator match is already in progress. Please wait.");
			showMainPage(player, npc);
			return;
		}
		
		final Player creator = getPlayerByObjectId(challenge.creatorObjectId);
		if ((creator == null) || (creator.isOnlineInt() != 1))
		{
			refundChallenge(challenge);
			OPEN_CHALLENGES.remove(challengeId);
			player.sendMessage("The creator is offline. The challenge was canceled.");
			showMainPage(player, npc);
			return;
		}
		
		if (!canUseSystem(player) || !canUseSystem(creator, true))
		{
			showMainPage(player, npc);
			return;
		}
		if (isSameRealPlayer(player, creator))
		{
			player.sendMessage("You cannot accept a Gladiator bet from the same IP or HWID.");
			creator.sendMessage("Your Gladiator bet was protected from same IP/HWID abuse.");
			showMainPage(player, npc);
			return;
		}
		
		if (hasOpenChallenge(player) || isInActiveMatch(player))
		{
			player.sendMessage("You already have an active Gladiator bet.");
			showMainPage(player, npc);
			return;
		}
		
		if (!takeCoins(player, challenge.betAmount))
		{
			player.sendMessage("You do not have enough Rax Coins.");
			showMainPage(player, npc);
			return;
		}
		
		OPEN_CHALLENGES.remove(challengeId);
		removeListeners(creator);
		final Match match = new Match(challengeId, creator, player, challenge.betAmount);
		CURRENT_MATCH = match;
		ACTIVE_MATCHES.put(creator.getObjectId(), match);
		ACTIVE_MATCHES.put(player.getObjectId(), match);
		creator.setRegisteredOnEvent(false);
		creator.setOnEvent(true);
		creator.setOnSoloEvent(true);
		player.setOnEvent(true);
		player.setOnSoloEvent(true);
		addLogoutListener(creator);
		addLogoutListener(player);
		addDeathListener(creator);
		addDeathListener(player);
		prepareFighter(creator, PLAYER_ONE_LOC);
		prepareFighter(player, PLAYER_TWO_LOC);
		message(match, "Gladiator match accepted. Round 1 starts in 10 seconds.");
		startQuestTimer("StartMatch_" + match.id, START_DELAY, null, null);
	}
	
	private void cancelChallenge(Player player, Npc npc, int challengeId)
	{
		final Challenge challenge = OPEN_CHALLENGES.get(challengeId);
		if (challenge == null)
		{
			player.sendMessage("This challenge is no longer available.");
			showMainPage(player, npc);
			return;
		}
		
		if (challenge.creatorObjectId != player.getObjectId())
		{
			player.sendMessage("Only the creator can cancel this challenge.");
			showMainPage(player, npc);
			return;
		}
		
		OPEN_CHALLENGES.remove(challengeId);
		refund(player, challenge.betAmount);
		player.setRegisteredOnEvent(false);
		removeListeners(player);
		player.sendMessage("Your Gladiator bet was canceled and refunded.");
		showMainPage(player, npc);
	}
	
	private void startMatch(int matchId)
	{
		final Match match = CURRENT_MATCH;
		if ((match == null) || (match.id != matchId) || match.finished)
		{
			return;
		}
		
		startRound(match);
	}
	
	private void startNextRound(int matchId)
	{
		final Match match = CURRENT_MATCH;
		if ((match == null) || (match.id != matchId) || match.finished)
		{
			return;
		}
		
		prepareFighter(match.playerOne, PLAYER_ONE_LOC);
		prepareFighter(match.playerTwo, PLAYER_TWO_LOC);
		message(match, "Next round starts in 10 seconds.");
		startQuestTimer("StartMatch_" + match.id, START_DELAY, null, null);
	}
	
	private void startRound(Match match)
	{
		match.roundActive = true;
		enableFighter(match.playerOne);
		enableFighter(match.playerTwo);
		message(match, "Round " + match.round + " has started!");
		screen(match.playerOne, "Fight!", 3);
		screen(match.playerTwo, "Fight!", 3);
	}
	
	@RegisterEvent(EventType.ON_CREATURE_DEATH)
	public void onPlayerDeath(OnCreatureDeath event)
	{
		if (!event.getTarget().isPlayer())
		{
			return;
		}
		
		final Player killed = event.getTarget().asPlayer();
		final Match match = ACTIVE_MATCHES.get(killed.getObjectId());
		if ((match == null) || match.finished || !match.roundActive)
		{
			return;
		}
		
		final Player winner = (killed == match.playerOne) ? match.playerTwo : match.playerOne;
		match.roundActive = false;
		if (winner == match.playerOne)
		{
			match.playerOneWins++;
		}
		else
		{
			match.playerTwoWins++;
		}
		
		message(match, winner.getName() + " wins round " + match.round + ". Score: " + match.playerOneWins + " - " + match.playerTwoWins);
		if ((match.playerOneWins >= ROUNDS_TO_WIN) || (match.playerTwoWins >= ROUNDS_TO_WIN))
		{
			finishMatch(match, winner);
			return;
		}
		
		match.round++;
		disableFighter(match.playerOne);
		disableFighter(match.playerTwo);
		startQuestTimer("NextRound_" + match.id, ROUND_DELAY, null, null);
	}
	
	@RegisterEvent(EventType.ON_PLAYER_LOGOUT)
	private void onPlayerLogout(OnPlayerLogout event)
	{
		final Player player = event.getPlayer();
		final Match match = ACTIVE_MATCHES.get(player.getObjectId());
		if (match != null)
		{
			cancelMatch(match, "A player disconnected. Both bets were refunded.");
			return;
		}
		
		for (Challenge challenge : OPEN_CHALLENGES.values())
		{
			if (challenge.creatorObjectId == player.getObjectId())
			{
				OPEN_CHALLENGES.remove(challenge.id);
				refund(player, challenge.betAmount);
				player.setRegisteredOnEvent(false);
				removeListeners(player);
				break;
			}
		}
	}
	
	private void finishMatch(Match match, Player winner)
	{
		match.finished = true;
		final int reward = match.betAmount * 2;
		winner.addItem(ItemProcessType.REWARD, RAX_COIN, reward, winner, true);
		message(match, winner.getName() + " won the Gladiator bet and received " + format(reward) + " Rax Coins.");
		cleanupMatch(match);
	}
	
	private void cancelMatch(Match match, String reason)
	{
		if (match.finished)
		{
			return;
		}
		
		match.finished = true;
		refund(match.playerOne, match.betAmount);
		refund(match.playerTwo, match.betAmount);
		message(match, reason);
		cleanupMatch(match);
	}
	
	private void cleanupMatch(Match match)
	{
		cancelQuestTimer("StartMatch_" + match.id, null, null);
		cancelQuestTimer("NextRound_" + match.id, null, null);
		for (Player player : new Player[]
		{
			match.playerOne,
			match.playerTwo
		})
		{
			if (player != null)
			{
				removeListeners(player);
				player.setRegisteredOnEvent(false);
				player.setOnEvent(false);
				player.setOnSoloEvent(false);
				enableFighter(player);
				if (player.isDead())
				{
					player.doRevive();
				}
				if (player.isOnlineInt() == 1)
				{
					player.teleToLocation(EXIT_LOC);
				}
			}
		}
		
		ACTIVE_MATCHES.remove(match.playerOne.getObjectId());
		ACTIVE_MATCHES.remove(match.playerTwo.getObjectId());
		CURRENT_MATCH = null;
	}
	
	private void refundChallenge(Challenge challenge)
	{
		final Player creator = getPlayerByObjectId(challenge.creatorObjectId);
		if (creator != null)
		{
			refund(creator, challenge.betAmount);
			creator.setRegisteredOnEvent(false);
			removeListeners(creator);
		}
	}
	
	private void prepareFighter(Player player, Location location)
	{
		player.leaveParty();
		if (player.isDead())
		{
			player.doRevive();
		}
		player.fullRestore();
		disableFighter(player);
		player.teleToLocation(location);
	}
	
	private void disableFighter(Player player)
	{
		player.setInvul(true);
		player.setImmobilized(true);
		player.disableAllSkills();
		final Summon summon = player.getSummon();
		if (summon != null)
		{
			summon.setInvul(true);
			summon.setImmobilized(true);
			summon.disableAllSkills();
		}
	}
	
	private void enableFighter(Player player)
	{
		player.setInvul(false);
		player.setImmobilized(false);
		player.enableAllSkills();
		final Summon summon = player.getSummon();
		if (summon != null)
		{
			summon.setInvul(false);
			summon.setImmobilized(false);
			summon.enableAllSkills();
		}
	}
	
	private boolean canUseSystem(Player player)
	{
		return canUseSystem(player, false);
	}
	
	private boolean canUseSystem(Player player, boolean allowRegistered)
	{
		if ((!allowRegistered && player.isRegisteredOnEvent()) || player.isOnEvent())
		{
			player.sendMessage("You are already registered in another event.");
			return false;
		}
		
		if (player.isInDuel())
		{
			player.sendMessage("You cannot use Gladiator while in a duel.");
			return false;
		}
		
		if (player.isInOlympiadMode() || Olympiad.getInstance().isRegistered(player))
		{
			player.sendMessage("You cannot use Gladiator while registered in Olympiad.");
			return false;
		}
		
		if (player.getInstanceId() > 0)
		{
			player.sendMessage("You cannot use Gladiator while inside an instance.");
			return false;
		}
		
		if (player.isInSiege() || player.isInsideZone(ZoneId.SIEGE))
		{
			player.sendMessage("You cannot use Gladiator during a siege.");
			return false;
		}
		
		if (player.isFishing())
		{
			player.sendMessage("You cannot use Gladiator while fishing.");
			return false;
		}
		
		if (player.isCursedWeaponEquipped() || (player.getKarma() > 0))
		{
			player.sendMessage("You cannot use Gladiator with karma or a cursed weapon.");
			return false;
		}
		
		return true;
	}
	
	private boolean isSameRealPlayer(Player player, Player other)
	{
		if ((player == null) || (other == null))
		{
			return false;
		}
		
		final String playerIp = player.getIPAddress();
		final String otherIp = other.getIPAddress();
		if ((playerIp != null) && !playerIp.isEmpty() && playerIp.equals(otherIp))
		{
			return true;
		}
		
		final String playerHwid = getHardwareId(player);
		final String otherHwid = getHardwareId(other);
		return (playerHwid != null) && !playerHwid.isEmpty() && playerHwid.equals(otherHwid);
	}
	
	private String getHardwareId(Player player)
	{
		final GameClient client = player.getClient();
		if ((client == null) || (client.getHardwareInfo() == null))
		{
			return null;
		}
		return client.getHardwareInfo().getMacAddress();
	}
	
	private boolean hasOpenChallenge(Player player)
	{
		for (Challenge challenge : OPEN_CHALLENGES.values())
		{
			if (challenge.creatorObjectId == player.getObjectId())
			{
				return true;
			}
		}
		
		return false;
	}
	
	private boolean isInActiveMatch(Player player)
	{
		return ACTIVE_MATCHES.containsKey(player.getObjectId());
	}
	
	private boolean isAllowedBet(int amount)
	{
		for (int allowed : ALLOWED_BETS)
		{
			if (allowed == amount)
			{
				return true;
			}
		}
		
		return false;
	}
	
	private boolean takeCoins(Player player, int amount)
	{
		if (amount <= 0)
		{
			return false;
		}
		
		if (player.getInventory().getInventoryItemCount(RAX_COIN, 0) < amount)
		{
			return false;
		}
		
		return player.destroyItemByItemId(ItemProcessType.FEE, RAX_COIN, amount, player, true);
	}
	
	private void refund(Player player, int amount)
	{
		if ((player != null) && (amount > 0))
		{
			player.addItem(ItemProcessType.REFUND, RAX_COIN, amount, player, true);
		}
	}
	
	private Player getPlayerByObjectId(int objectId)
	{
		for (Player player : org.l2jmobius.gameserver.model.World.getInstance().getPlayers())
		{
			if (player.getObjectId() == objectId)
			{
				return player;
			}
		}
		
		return null;
	}
	
	private void addLogoutListener(Player player)
	{
		player.addListener(new ConsumerEventListener(player, EventType.ON_PLAYER_LOGOUT, (OnPlayerLogout event) -> onPlayerLogout(event), this));
	}
	
	private void addDeathListener(Player player)
	{
		player.addListener(new ConsumerEventListener(player, EventType.ON_CREATURE_DEATH, (OnCreatureDeath event) -> onPlayerDeath(event), this));
	}
	
	private void removeListeners(Player player)
	{
		final Collection<AbstractEventListener> logoutListeners = player.getListeners(EventType.ON_PLAYER_LOGOUT);
		for (AbstractEventListener listener : logoutListeners)
		{
			if (listener.getOwner() == this)
			{
				listener.unregisterMe();
			}
		}
		
		final Collection<AbstractEventListener> deathListeners = player.getListeners(EventType.ON_CREATURE_DEATH);
		for (AbstractEventListener listener : deathListeners)
		{
			if (listener.getOwner() == this)
			{
				listener.unregisterMe();
			}
		}
	}
	
	private void message(Match match, String text)
	{
		match.playerOne.sendMessage(text);
		match.playerTwo.sendMessage(text);
	}
	
	private void screen(Player player, String text, int seconds)
	{
		player.sendPacket(new ExShowScreenMessage(text, ExShowScreenMessage.TOP_CENTER, seconds * 1000, 0, true, true));
	}
	
	private void appendHeader(StringBuilder html, String title, String subtitle)
	{
		html.append("<table width=300 background=\"L2UI_CT1.Windows_DF_TooltipBG\"><tr><td align=center>");
		html.append("<font color=\"LEVEL\">").append(title).append("</font><br1>");
		html.append("<font color=\"B09878\">").append(subtitle).append("</font>");
		html.append("</td></tr></table><br>");
	}
	
	private void appendFooter(StringBuilder html, String text)
	{
		html.append("<br><table width=300 bgcolor=\"000000\"><tr><td align=center>");
		html.append("<font color=\"808080\">").append(text).append("</font>");
		html.append("</td></tr></table>");
		html.append("</center></body></html>");
	}
	
	private void sendHtml(Player player, Npc npc, String html)
	{
		final NpcHtmlMessage packet = new NpcHtmlMessage(npc == null ? 0 : npc.getObjectId());
		packet.setHtml(html);
		player.sendPacket(packet);
	}
	
	private String format(int amount)
	{
		return NUMBER_FORMAT.format(amount);
	}
	
	private static class Challenge
	{
		private final int id;
		private final int creatorObjectId;
		private final String creatorName;
		private final int betAmount;
		
		private Challenge(int id, int creatorObjectId, String creatorName, int betAmount)
		{
			this.id = id;
			this.creatorObjectId = creatorObjectId;
			this.creatorName = creatorName;
			this.betAmount = betAmount;
		}
	}
	
	private static class Match
	{
		private final int id;
		private final Player playerOne;
		private final Player playerTwo;
		private final int betAmount;
		private int round = 1;
		private int playerOneWins = 0;
		private int playerTwoWins = 0;
		private boolean roundActive = false;
		private boolean finished = false;
		
		private Match(int id, Player playerOne, Player playerTwo, int betAmount)
		{
			this.id = id;
			this.playerOne = playerOne;
			this.playerTwo = playerTwo;
			this.betAmount = betAmount;
		}
	}
	
	public static void main(String[] args)
	{
		new PvpBetArena();
	}
}
