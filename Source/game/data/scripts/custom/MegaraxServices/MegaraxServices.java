/*
 * Custom Megarax Services - L2JMobius Interlude
 */
package custom.MegaraxServices;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.l2jmobius.Config;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.util.StringUtil;
import org.l2jmobius.gameserver.data.sql.CharInfoTable;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.network.serverpackets.PartySmallWindowAll;
import org.l2jmobius.gameserver.network.serverpackets.PartySmallWindowDeleteAll;

import ai.AbstractNpcAI;

public class MegaraxServices extends AbstractNpcAI
{
	private static final Logger LOGGER = Logger.getLogger(MegaraxServices.class.getName());
	private static final int SERVICES_NPC = 9911;
	private static final int MAX_INTERACTION_DISTANCE = 250;
	private static final String CONFIG_FILE = "config/Custom/MegaraxServices.ini";
	private static final String NAME_COLOR_VARIABLE = "MEGARAX_NAME_COLOR";
	private static final String MEGA_COIN_ICON = "icon.etc_coin_of_fair_i00";
	private static final String CLAN_ICON = "favero_l2_icons03.skill_clan_15";
	private static final String COLOR_ICON = "icon.etc_reagent_red_i00";
	private static final String CLAN_NAME_EXISTS_SQL = "SELECT clan_id FROM clan_data WHERE LOWER(clan_name)=LOWER(?) AND clan_id<>?";
	private static final String UPDATE_CLAN_NAME_SQL = "UPDATE clan_data SET clan_name=? WHERE clan_id=?";
	private static final String LOG_PREFIX = "[MEGARAX_SERVICE] ";
	private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9]{2,15}");
	private static final Pattern CLAN_NAME_PATTERN = Pattern.compile("[A-Za-z0-9]{2,16}");
	private static final String[] RESERVED_NAMES =
	{
		"admin",
		"administrator",
		"gm",
		"staff",
		"support",
		"system",
		"server"
	};
	private static final ConcurrentHashMap<Integer, Long> SERVICE_COOLDOWNS = new ConcurrentHashMap<>();
	private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.US);
	private static final SimpleDateFormat LOG_DATE = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
	
	private static int MEGA_COIN_ID = 6673;
	private static int CHANGE_GENDER_COST = 50;
	private static int CHANGE_RACE_COST = 10;
	private static int CHANGE_NAME_COST = 10;
	private static int CLAN_NAME_COST = 50;
	private static int NAME_COLOR_COST = 50;
	private static int SERVICE_COOLDOWN_MS = 2000;
	
	private MegaraxServices()
	{
		loadConfig();
		addStartNpc(SERVICES_NPC);
		addTalkId(SERVICES_NPC);
		setOnEnterWorld(true);
	}
	
	@Override
	public void onEnterWorld(Player player)
	{
		applyStoredNameColor(player);
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		if ((player == null) || (event == null))
		{
			return null;
		}
		
		if (event.startsWith("name_confirm"))
		{
			final String newName = getTextParameter(event, "name_confirm");
			changeName(player, npc, newName);
			showName(player, npc);
			return null;
		}
		if (event.startsWith("clan_name_confirm"))
		{
			final String newName = getTextParameter(event, "clan_name_confirm");
			changeClanName(player, npc, newName);
			showClanName(player, npc);
			return null;
		}
		if (event.startsWith("name_color_page"))
		{
			showNameColorPage(player, npc, parseIntParameter(event, "name_color_page", 1));
			return null;
		}
		if (event.startsWith("name_color_preview"))
		{
			showNameColorConfirm(player, npc, parseIntParameter(event, "name_color_preview", 0));
			return null;
		}
		if (event.startsWith("name_color_confirm"))
		{
			changeNameColor(player, npc, parseIntParameter(event, "name_color_confirm", 0));
			showNameColorPage(player, npc, 1);
			return null;
		}
		
		switch (event)
		{
			case "name":
			{
				showName(player, npc);
				break;
			}
			case "clan_name":
			{
				showClanName(player, npc);
				break;
			}
			case "name_color":
			{
				showNameColorPage(player, npc, 1);
				break;
			}
			case "gender":
			{
				showGender(player, npc);
				break;
			}
			case "gender_confirm":
			{
				changeGender(player, npc);
				showGender(player, npc);
				break;
			}
			case "race":
			{
				showRaceInfo(player, npc);
				break;
			}
			default:
			{
				showGender(player, npc);
				break;
			}
		}
		
		return null;
	}
	
	private void changeName(Player player, Npc npc, String newName)
	{
		if (!validateServiceState(player, npc) || !tryStartService(player, "CHARACTER_NAME"))
		{
			return;
		}
		
		if ((newName == null) || newName.trim().isEmpty() || newName.equals("$new_name"))
		{
			player.sendMessage("Enter a new character name.");
			return;
		}
		
		newName = newName.trim();
		if (!PLAYER_NAME_PATTERN.matcher(newName).matches() || containsForbiddenName(newName))
		{
			player.sendMessage("Name must start with a letter and use 3-16 valid letters or numbers.");
			return;
		}
		if (newName.equalsIgnoreCase(player.getName()))
		{
			player.sendMessage("That is already your character name.");
			return;
		}
		if (CharInfoTable.getInstance().doesCharNameExist(newName))
		{
			player.sendMessage("That character name is already taken.");
			return;
		}
		if (!hasRequiredMegaCoin(player, CHANGE_NAME_COST))
		{
			player.sendMessage("You need " + CHANGE_NAME_COST + " Mega Coin to change name.");
			return;
		}
		if (!consumeMegaCoin(player, npc, CHANGE_NAME_COST))
		{
			return;
		}
		
		final String oldName = player.getName();
		try
		{
			player.setName(newName);
			player.storeMe();
			player.broadcastUserInfo();
			refreshPartyAndClan(player);
			logInfo("CHARACTER_NAME_CHANGED Account: " + player.getAccountName() + " Character: " + oldName + " Character ID: " + player.getObjectId() + " New Name: " + newName + " Mega Coin Cost: " + CHANGE_NAME_COST + " Date: " + now());
			player.sendMessage("Your name has been changed from " + oldName + " to " + newName + ".");
		}
		catch (Exception e)
		{
			refundMegaCoin(player, npc, CHANGE_NAME_COST);
			player.setName(oldName);
			player.sendMessage("Name change failed. Your Mega Coin has been refunded.");
			logFailure("CHARACTER_NAME_CHANGE_FAILED", player, e);
		}
	}
	
	private void changeClanName(Player player, Npc npc, String requestedName)
	{
		if (!validateServiceState(player, npc) || !tryStartService(player, "CLAN_NAME"))
		{
			return;
		}
		
		final Clan clan = player.getClan();
		if (clan == null)
		{
			player.sendMessage("You must belong to a clan.");
			return;
		}
		if (!player.isClanLeader() || (clan.getLeaderId() != player.getObjectId()))
		{
			player.sendMessage("Only the clan leader can use this service.");
			return;
		}
		
		final String newName = cleanName(requestedName);
		final String validation = validateClanName(clan, newName);
		if (validation != null)
		{
			player.sendMessage(validation);
			return;
		}
		if (!hasRequiredMegaCoin(player, CLAN_NAME_COST))
		{
			player.sendMessage("You need " + CLAN_NAME_COST + " Mega Coin to change your clan name.");
			return;
		}
		if (!consumeMegaCoin(player, npc, CLAN_NAME_COST))
		{
			return;
		}
		
		final String oldName = clan.getName();
		boolean databaseUpdated = false;
		try
		{
			if (isClanNameUsedInDatabase(newName, clan.getId()))
			{
				refundMegaCoin(player, npc, CLAN_NAME_COST);
				player.sendMessage("This clan name is already in use.");
				return;
			}
			
			updateClanNameInDatabase(clan.getId(), newName);
			databaseUpdated = true;
			clan.setName(newName);
			clan.broadcastClanStatus();
			for (Player member : clan.getOnlineMembers(0))
			{
				if (member != null)
				{
					member.broadcastUserInfo();
					member.updateUserInfo();
				}
			}
			
			logInfo("CLAN_NAME_CHANGED Account: " + player.getAccountName() + " Character: " + player.getName() + " Character ID: " + player.getObjectId() + " Clan ID: " + clan.getId() + " Old Clan Name: " + oldName + " New Clan Name: " + newName + " Mega Coin Cost: " + CLAN_NAME_COST + " Date: " + now());
			player.sendMessage("Your clan name has been changed successfully.");
			player.sendMessage("Your clan is now called: " + newName + ".");
		}
		catch (Exception e)
		{
			clan.setName(oldName);
			if (databaseUpdated)
			{
				try
				{
					updateClanNameInDatabase(clan.getId(), oldName);
				}
				catch (Exception restoreException)
				{
					LOGGER.log(Level.WARNING, LOG_PREFIX + "CLAN_NAME_RESTORE_FAILED Clan ID: " + clan.getId() + " Old Clan Name: " + oldName + " Date: " + now(), restoreException);
				}
			}
			refundMegaCoin(player, npc, CLAN_NAME_COST);
			player.sendMessage("No se pudo cambiar el nombre del clan.");
			logFailure("CLAN_NAME_CHANGE_FAILED", player, e);
		}
	}
	
	private void changeNameColor(Player player, Npc npc, int colorId)
	{
		if (!validateServiceState(player, npc) || !tryStartService(player, "NAME_COLOR"))
		{
			return;
		}
		
		final NameColorOption color = NameColorOption.find(colorId);
		if (color == null)
		{
			player.sendMessage("Invalid color selected.");
			logInfo("INVALID_BYPASS Account: " + player.getAccountName() + " Character: " + player.getName() + " Character ID: " + player.getObjectId() + " Event: name_color_confirm " + colorId + " Date: " + now());
			return;
		}
		if (player.getAppearance().getNameColor() == color.serverColor)
		{
			player.sendMessage("This is already your current name color.");
			return;
		}
		if (!hasRequiredMegaCoin(player, NAME_COLOR_COST))
		{
			player.sendMessage("You need " + NAME_COLOR_COST + " Mega Coin to change name color.");
			return;
		}
		if (!consumeMegaCoin(player, npc, NAME_COLOR_COST))
		{
			return;
		}
		
		final int oldColor = player.getAppearance().getNameColor();
		final int oldVariable = player.getVariables().getInt(NAME_COLOR_VARIABLE, 0);
		try
		{
			player.getVariables().set(NAME_COLOR_VARIABLE, color.id);
			if (!player.getVariables().storeMe())
			{
				throw new IllegalStateException("Could not store player variables.");
			}
			player.getAppearance().setNameColor(color.serverColor);
			player.broadcastUserInfo();
			player.updateUserInfo();
			logInfo("NAME_COLOR_CHANGED Account: " + player.getAccountName() + " Character: " + player.getName() + " Character ID: " + player.getObjectId() + " Old Color: " + serverColorToHtmlHex(oldColor) + " New Color: " + color.displayName + " Mega Coin Cost: " + NAME_COLOR_COST + " Date: " + now());
			player.sendMessage("Your name color has been changed to " + color.displayName + ".");
		}
		catch (Exception e)
		{
			player.getAppearance().setNameColor(oldColor);
			if (oldVariable > 0)
			{
				player.getVariables().set(NAME_COLOR_VARIABLE, oldVariable);
			}
			else
			{
				player.getVariables().remove(NAME_COLOR_VARIABLE);
			}
			player.getVariables().storeMe();
			refundMegaCoin(player, npc, NAME_COLOR_COST);
			player.sendMessage("Name color change failed. Your Mega Coin has been refunded.");
			logFailure("NAME_COLOR_CHANGE_FAILED", player, e);
		}
	}
	
	private void changeGender(Player player, Npc npc)
	{
		if (!validateServiceState(player, npc) || !tryStartService(player, "GENDER"))
		{
			return;
		}
		if (!hasRequiredMegaCoin(player, CHANGE_GENDER_COST))
		{
			player.sendMessage("You need " + CHANGE_GENDER_COST + " Mega Coin to change gender.");
			return;
		}
		if (!consumeMegaCoin(player, npc, CHANGE_GENDER_COST))
		{
			return;
		}
		
		final PlayerAppearance appearance = player.getAppearance();
		if (appearance.isFemale())
		{
			appearance.setMale();
		}
		else
		{
			appearance.setFemale();
		}
		
		player.broadcastUserInfo();
		player.sendMessage("Your gender has been changed.");
	}
	
	private void showName(Player player, Npc npc)
	{
		final long megaCoins = getMegaCoinCount(player);
		final StringBuilder html = new StringBuilder();
		startHtml(html, "Change Name");
		serviceCard(html, "Name Service", "Change your character name.", "icon.etc_letter_red_i00", CHANGE_NAME_COST);
		statusHeader(html, "Status");
		html.append("<table width=292 bgcolor=\"000000\">");
		html.append("<tr><td align=center><font color=\"ae9977\">Your Mega Coin:</font> <font color=\"LEVEL\">").append(format(megaCoins)).append("</font></td></tr>");
		html.append("<tr><td align=center><font color=\"ae9977\">Current name:</font> <font color=\"LEVEL\">").append(escape(player.getName())).append("</font></td></tr>");
		html.append("<tr><td align=center><font color=\"ae9977\">New name:</font> <edit var=\"new_name\" width=140 height=15></td></tr>");
		html.append("</table><br>");
		actionButton(html, "Change Name", "Quest MegaraxServices name_confirm $new_name", 180);
		html.append("<br1>");
		backButton(html, npc);
		endHtml(html);
		sendHtml(player, npc, html.toString());
	}
	
	private void showClanName(Player player, Npc npc)
	{
		final long megaCoins = getMegaCoinCount(player);
		final Clan clan = player.getClan();
		final String currentClanName = (clan == null) ? "No clan" : clan.getName();
		final StringBuilder html = new StringBuilder();
		startHtml(html, "Change Clan Name");
		serviceCard(html, "Clan Name Service", "Create a new identity for your community.", CLAN_ICON, CLAN_NAME_COST);
		statusHeader(html, "Requirements");
		html.append("<table width=292 bgcolor=\"000000\">");
		html.append("<tr><td><font color=\"ae9977\">You must be the clan leader.</font></td></tr>");
		html.append("<tr><td><font color=\"ae9977\">The new name must be available and valid.</font></td></tr>");
		html.append("</table><br>");
		statusHeader(html, "Clan Status");
		html.append("<table width=292 bgcolor=\"000000\">");
		html.append("<tr><td width=116><font color=\"ae9977\">Current Clan:</font></td><td width=176><font color=\"LEVEL\">").append(escape(currentClanName)).append("</font></td></tr>");
		html.append("<tr><td><font color=\"ae9977\">Mega Coin:</font></td><td><font color=\"LEVEL\">").append(format(megaCoins)).append("</font></td></tr>");
		html.append("<tr><td><font color=\"ae9977\">Price:</font></td><td><img src=\"").append(MEGA_COIN_ICON).append("\" width=16 height=16> <font color=\"LEVEL\">").append(CLAN_NAME_COST).append("</font></td></tr>");
		html.append("<tr><td><font color=\"ae9977\">New Name:</font></td><td><edit var=\"new_clan_name\" width=140 height=15></td></tr>");
		html.append("</table><br>");
		actionButton(html, "Confirm Change", "Quest MegaraxServices clan_name_confirm $new_clan_name", 180);
		html.append("<br1>");
		backButton(html, npc);
		endHtml(html);
		sendHtml(player, npc, html.toString());
	}
	
	private void showNameColorPage(Player player, Npc npc, int page)
	{
		page = Math.max(1, Math.min(page, 2));
		final int start = (page - 1) * 10;
		final int end = Math.min(NameColorOption.VALUES.length, start + 10);
		final long megaCoins = getMegaCoinCount(player);
		final String currentColor = serverColorToHtmlHex(player.getAppearance().getNameColor());
		final StringBuilder html = new StringBuilder();
		startHtml(html, "Character Name Color");
		serviceCard(html, "Color Name Service", "Choose a permanent color for your character name.", COLOR_ICON, NAME_COLOR_COST);
		statusHeader(html, "Current Status");
		html.append("<table width=292 bgcolor=\"000000\">");
		html.append("<tr><td width=116><font color=\"ae9977\">Character:</font></td><td width=176><font color=\"").append(currentColor).append("\">").append(escape(player.getName())).append("</font></td></tr>");
		html.append("<tr><td><font color=\"ae9977\">Current Color:</font></td><td><font color=\"").append(currentColor).append("\">").append(currentColor).append("</font></td></tr>");
		html.append("<tr><td><font color=\"ae9977\">Mega Coin:</font></td><td><font color=\"LEVEL\">").append(format(megaCoins)).append("</font></td></tr>");
		html.append("<tr><td><font color=\"ae9977\">Price:</font></td><td><img src=\"").append(MEGA_COIN_ICON).append("\" width=16 height=16> <font color=\"LEVEL\">").append(NAME_COLOR_COST).append("</font></td></tr>");
		html.append("</table><br>");
		statusHeader(html, "Colors Page " + page);
		html.append("<table width=292 bgcolor=\"000000\">");
		for (int i = start; i < end; i += 2)
		{
			html.append("<tr>");
			appendColorCell(html, player, NameColorOption.VALUES[i]);
			if ((i + 1) < end)
			{
				appendColorCell(html, player, NameColorOption.VALUES[i + 1]);
			}
			else
			{
				html.append("<td width=146></td>");
			}
			html.append("</tr>");
		}
		html.append("</table><br>");
		html.append("<table width=292><tr>");
		html.append("<td width=97 align=center>");
		if (page > 1)
		{
			inlineButton(html, "Previous", "Quest MegaraxServices name_color_page " + (page - 1), 80);
		}
		html.append("</td><td width=98 align=center>");
		backButton(html, npc, 80);
		html.append("</td><td width=97 align=center>");
		if (page < 2)
		{
			inlineButton(html, "Next", "Quest MegaraxServices name_color_page " + (page + 1), 80);
		}
		html.append("</td></tr></table>");
		endHtml(html);
		sendHtml(player, npc, html.toString());
	}
	
	private void showNameColorConfirm(Player player, Npc npc, int colorId)
	{
		final NameColorOption color = NameColorOption.find(colorId);
		if (color == null)
		{
			player.sendMessage("Invalid color selected.");
			showNameColorPage(player, npc, 1);
			return;
		}
		
		final long megaCoins = getMegaCoinCount(player);
		final long after = Math.max(0, megaCoins - NAME_COLOR_COST);
		final StringBuilder html = new StringBuilder();
		startHtml(html, "Confirm Name Color");
		serviceCard(html, "Selected Color: " + color.displayName, "Confirm before paying Mega Coin.", COLOR_ICON, NAME_COLOR_COST);
		statusHeader(html, "Preview");
		html.append("<table width=292 bgcolor=\"000000\">");
		html.append("<tr><td align=center><font color=\"ae9977\">Preview:</font> <font color=\"").append(color.htmlHex).append("\">").append(escape(player.getName())).append("</font></td></tr>");
		html.append("<tr><td align=center><font color=\"ae9977\">Price:</font> <img src=\"").append(MEGA_COIN_ICON).append("\" width=16 height=16> <font color=\"LEVEL\">").append(NAME_COLOR_COST).append("</font></td></tr>");
		html.append("<tr><td align=center><font color=\"ae9977\">Current Balance:</font> <font color=\"LEVEL\">").append(format(megaCoins)).append("</font></td></tr>");
		html.append("<tr><td align=center><font color=\"ae9977\">Balance After:</font> <font color=\"LEVEL\">").append(format(after)).append("</font></td></tr>");
		html.append("</table><br>");
		actionButton(html, "Confirm", "Quest MegaraxServices name_color_confirm " + color.id, 180);
		html.append("<br1>");
		actionButton(html, "Back to Colors", "Quest MegaraxServices name_color_page " + color.page(), 180);
		endHtml(html);
		sendHtml(player, npc, html.toString());
	}
	
	private void showGender(Player player, Npc npc)
	{
		final long megaCoins = getMegaCoinCount(player);
		final StringBuilder html = new StringBuilder();
		startHtml(html, "Change Gender");
		serviceCard(html, "Gender Service", "Change your character gender.", "favero_l2_icons03.skill_clan_21", CHANGE_GENDER_COST);
		statusHeader(html, "Status");
		html.append("<table width=292 bgcolor=\"000000\">");
		html.append("<tr><td align=center><font color=\"ae9977\">Your Mega Coin:</font> <font color=\"LEVEL\">").append(format(megaCoins)).append("</font></td></tr>");
		html.append("<tr><td align=center><font color=\"ae9977\">Current gender:</font> <font color=\"LEVEL\">").append(player.getAppearance().isFemale() ? "Female" : "Male").append("</font></td></tr>");
		html.append("</table><br>");
		actionButton(html, "Change Gender", "Quest MegaraxServices gender_confirm", 180);
		html.append("<br1>");
		backButton(html, npc);
		endHtml(html);
		sendHtml(player, npc, html.toString());
	}
	
	private void showRaceInfo(Player player, Npc npc)
	{
		final StringBuilder html = new StringBuilder();
		startHtml(html, "Change Skin");
		serviceCard(html, "Race Appearance", "Protected appearance service.", "favero_l2_icons02.talisman_x_allstat", CHANGE_RACE_COST);
		statusHeader(html, "Protected Service");
		html.append("<table width=292 bgcolor=\"000000\">");
		html.append("<tr><td align=center><font color=\"ae9977\">Race is linked to class template in this Interlude core.</font></td></tr>");
		html.append("<tr><td align=center><font color=\"LEVEL\">This service is disabled until visual-race support is added safely.</font></td></tr>");
		html.append("</table><br>");
		backButton(html, npc);
		endHtml(html);
		sendHtml(player, npc, html.toString());
	}
	
	private String validateClanName(Clan clan, String newName)
	{
		if ((newName == null) || newName.isEmpty() || newName.equals("$new_clan_name"))
		{
			return "Enter a new clan name.";
		}
		if (newName.equalsIgnoreCase(clan.getName()))
		{
			return "That is already your clan name.";
		}
		if (newName.length() < 2)
		{
			return "The clan name is too short.";
		}
		if (newName.length() > 16)
		{
			return "The clan name is too long.";
		}
		if (!CLAN_NAME_PATTERN.matcher(newName).matches() || !StringUtil.isAlphaNumeric(newName))
		{
			return "The clan name contains invalid characters.";
		}
		if (containsForbiddenName(newName) || isReservedName(newName))
		{
			return "The clan name contains a restricted word.";
		}
		
		final Clan existing = ClanTable.getInstance().getClanByName(newName);
		if ((existing != null) && (existing.getId() != clan.getId()))
		{
			return "This clan name is already in use.";
		}
		return null;
	}
	
	private boolean validateServiceState(Player player, Npc npc)
	{
		if (!validateNpc(player, npc))
		{
			return false;
		}
		if (!player.isOnline() || (player.getClient() == null))
		{
			player.sendMessage("This service is temporarily unavailable.");
			return false;
		}
		if (player.isDead() || player.isAlikeDead())
		{
			player.sendMessage("You cannot use this service while dead.");
			return false;
		}
		if (player.isInCombat())
		{
			player.sendMessage("You cannot use this service while in combat.");
			return false;
		}
		if (player.isInOlympiadMode() || player.isOlympiadStart())
		{
			player.sendMessage("You cannot use this service during Olympiad.");
			return false;
		}
		if (player.isJailed())
		{
			player.sendMessage("You cannot use this service while jailed.");
			return false;
		}
		if (player.isProcessingTransaction() || (player.getActiveTradeList() != null))
		{
			player.sendMessage("You cannot use this service while trading.");
			return false;
		}
		if (player.isInStoreMode() || player.isCrafting() || (player.getPrivateStoreType() != PrivateStoreType.NONE))
		{
			player.sendMessage("You cannot use this service while using private store.");
			return false;
		}
		return true;
	}
	
	private boolean validateNpc(Player player, Npc npc)
	{
		if ((npc == null) || (npc.getId() != SERVICES_NPC))
		{
			player.sendMessage("This service is temporarily unavailable.");
			logInfo("INVALID_BYPASS Account: " + player.getAccountName() + " Character: " + player.getName() + " Character ID: " + player.getObjectId() + " Reason: Invalid NPC Date: " + now());
			return false;
		}
		if (!player.isInsideRadius3D(npc, MAX_INTERACTION_DISTANCE))
		{
			player.sendMessage("You are too far from the service NPC.");
			return false;
		}
		return true;
	}
	
	private boolean tryStartService(Player player, String service)
	{
		final long now = System.currentTimeMillis();
		final AtomicBoolean allowed = new AtomicBoolean(false);
		SERVICE_COOLDOWNS.compute(player.getObjectId(), (objectId, current) ->
		{
			if ((current == null) || (current <= now))
			{
				allowed.set(true);
				return now + SERVICE_COOLDOWN_MS;
			}
			return current;
		});
		
		if (!allowed.get())
		{
			player.sendMessage("Please wait a moment before confirming again.");
			logInfo("DUPLICATE_REQUEST Account: " + player.getAccountName() + " Character: " + player.getName() + " Character ID: " + player.getObjectId() + " Service: " + service + " Date: " + now());
			return false;
		}
		return true;
	}
	
	private boolean hasRequiredMegaCoin(Player player, int amount)
	{
		return getMegaCoinCount(player) >= amount;
	}
	
	private boolean consumeMegaCoin(Player player, Npc npc, int amount)
	{
		return player.destroyItemByItemId(ItemProcessType.FEE, MEGA_COIN_ID, amount, npc == null ? player : npc, true);
	}
	
	private void refundMegaCoin(Player player, Npc npc, int amount)
	{
		player.addItem(ItemProcessType.REFUND, MEGA_COIN_ID, amount, npc == null ? player : npc, true);
	}
	
	private long getMegaCoinCount(Player player)
	{
		return player.getInventory().getInventoryItemCount(MEGA_COIN_ID, -1);
	}
	
	private void refreshPartyAndClan(Player player)
	{
		final Party party = player.getParty();
		if (party != null)
		{
			party.broadcastToPartyMembers(player, PartySmallWindowDeleteAll.STATIC_PACKET);
			for (Player member : party.getMembers())
			{
				if (member != player)
				{
					member.sendPacket(new PartySmallWindowAll(member, party));
				}
			}
		}
		
		final Clan clan = player.getClan();
		if (clan != null)
		{
			clan.broadcastClanStatus();
		}
	}
	
	private boolean isClanNameUsedInDatabase(String name, int ownClanId) throws SQLException
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(CLAN_NAME_EXISTS_SQL))
		{
			ps.setString(1, name);
			ps.setInt(2, ownClanId);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next();
			}
		}
	}
	
	private void updateClanNameInDatabase(int clanId, String newName) throws SQLException
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(UPDATE_CLAN_NAME_SQL))
		{
			ps.setString(1, newName);
			ps.setInt(2, clanId);
			if (ps.executeUpdate() != 1)
			{
				throw new SQLException("Clan row was not updated.");
			}
		}
	}
	
	private void applyStoredNameColor(Player player)
	{
		if (player == null)
		{
			return;
		}
		
		final int colorId = player.getVariables().getInt(NAME_COLOR_VARIABLE, 0);
		if (colorId <= 0)
		{
			return;
		}
		
		final NameColorOption color = NameColorOption.find(colorId);
		if (color == null)
		{
			player.getVariables().remove(NAME_COLOR_VARIABLE);
			player.getVariables().storeMe();
			return;
		}
		
		player.getAppearance().setNameColor(color.serverColor);
		player.broadcastUserInfo();
		player.updateUserInfo();
	}
	
	private String cleanName(String name)
	{
		return name == null ? "" : name.trim();
	}
	
	private boolean containsForbiddenName(String value)
	{
		if ((Config.FORBIDDEN_NAMES == null) || (value == null))
		{
			return false;
		}
		
		final String lower = value.toLowerCase(Locale.ROOT);
		for (String forbidden : Config.FORBIDDEN_NAMES)
		{
			if ((forbidden != null) && !forbidden.trim().isEmpty() && lower.contains(forbidden.trim().toLowerCase(Locale.ROOT)))
			{
				return true;
			}
		}
		return false;
	}
	
	private boolean isReservedName(String value)
	{
		for (String reserved : RESERVED_NAMES)
		{
			if (reserved.equalsIgnoreCase(value))
			{
				return true;
			}
		}
		return false;
	}
	
	private void appendColorCell(StringBuilder html, Player player, NameColorOption color)
	{
		html.append("<td width=146 align=center>");
		html.append("<font color=\"").append(color.htmlHex).append("\">").append(escape(player.getName())).append("</font><br1>");
		inlineButton(html, color.displayName, "Quest MegaraxServices name_color_preview " + color.id, 86);
		html.append("</td>");
	}
	
	private void startHtml(StringBuilder html, String title)
	{
		html.append("<html><title>Athena</title><body><center>");
		html.append("<button value=\"").append(escape(title)).append("\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
	}
	
	private void endHtml(StringBuilder html)
	{
		html.append("</center></body></html>");
	}
	
	private void serviceCard(StringBuilder html, String title, String description, String icon, int price)
	{
		html.append("<table width=292 bgcolor=\"000000\"><tr>");
		html.append("<td width=36 align=center><img src=\"").append(icon).append("\" width=32 height=32></td>");
		html.append("<td width=240><font color=\"0099FF\">").append(escape(title)).append("</font><br1>");
		html.append("<font color=\"ae9977\">").append(escape(description)).append("</font><br1>");
		html.append("<img src=\"").append(MEGA_COIN_ICON).append("\" width=16 height=16> <font color=\"LEVEL\">Cost: ").append(price).append(" Mega Coin</font></td>");
		html.append("</tr></table><br>");
	}
	
	private void statusHeader(StringBuilder html, String title)
	{
		html.append("<button value=\"").append(escape(title)).append("\" width=295 height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
	}
	
	private void actionButton(StringBuilder html, String label, String action, int width)
	{
		html.append("<button value=\"").append(escape(label)).append("\" action=\"bypass -h ").append(action).append("\" width=").append(width).append(" height=24 back=\"Galaxy.2955\" fore=\"Galaxy.2955\">");
	}
	
	private void inlineButton(StringBuilder html, String label, String action, int width)
	{
		html.append("<button value=\"").append(escape(label)).append("\" action=\"bypass -h ").append(action).append("\" width=").append(width).append(" height=24 back=\"Galaxy.105_OVER\" fore=\"Galaxy.105\">");
	}
	
	private void backButton(StringBuilder html, Npc npc)
	{
		backButton(html, npc, 180);
	}
	
	private void backButton(StringBuilder html, Npc npc, int width)
	{
		if (npc == null)
		{
			inlineButton(html, "Back", "Quest MegaraxServices gender", width);
			return;
		}
		inlineButton(html, "Back", "npc_" + npc.getObjectId() + "_Chat 0", width);
	}
	
	private String getTextParameter(String event, String prefix)
	{
		return event.length() > prefix.length() ? event.substring(prefix.length()).trim() : "";
	}
	
	private int parseIntParameter(String event, String prefix, int fallback)
	{
		final String value = getTextParameter(event, prefix);
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}
	
	private String serverColorToHtmlHex(int serverColor)
	{
		final int red = serverColor & 0xFF;
		final int green = (serverColor >> 8) & 0xFF;
		final int blue = (serverColor >> 16) & 0xFF;
		return String.format(Locale.US, "%02X%02X%02X", red, green, blue);
	}
	
	private String format(long value)
	{
		return NUMBER.format(value);
	}
	
	private String escape(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
	
	private String now()
	{
		return LOG_DATE.format(new Date());
	}
	
	private void logInfo(String message)
	{
		LOGGER.info(LOG_PREFIX + message);
	}
	
	private void logFailure(String event, Player player, Exception e)
	{
		LOGGER.log(Level.WARNING, LOG_PREFIX + event + " Account: " + (player == null ? "-" : player.getAccountName()) + " Character: " + (player == null ? "-" : player.getName()) + " Character ID: " + (player == null ? 0 : player.getObjectId()) + " Date: " + now(), e);
	}
	
	private void sendHtml(Player player, Npc npc, String html)
	{
		final NpcHtmlMessage msg = new NpcHtmlMessage(npc == null ? 0 : npc.getObjectId());
		msg.setHtml(html);
		player.sendPacket(msg);
	}
	
	private static int rgbToServerColor(String htmlHex)
	{
		final int rgb = Integer.parseInt(htmlHex, 16);
		final int red = (rgb >> 16) & 0xFF;
		final int green = (rgb >> 8) & 0xFF;
		final int blue = rgb & 0xFF;
		return red | (green << 8) | (blue << 16);
	}
	
	private static void loadConfig()
	{
		final Properties properties = new Properties();
		final File file = resolveConfigFile(CONFIG_FILE);
		if (file.isFile())
		{
			try (FileInputStream input = new FileInputStream(file))
			{
				properties.load(input);
			}
			catch (Exception e)
			{
				LOGGER.log(Level.WARNING, "Could not load " + file.getAbsolutePath() + ". Defaults will be used.", e);
			}
		}
		
		MEGA_COIN_ID = getInt(properties, "NpcServiceMegaCoinId", MEGA_COIN_ID);
		CHANGE_GENDER_COST = getInt(properties, "GenderChangePrice", CHANGE_GENDER_COST);
		CHANGE_RACE_COST = getInt(properties, "RaceChangePrice", CHANGE_RACE_COST);
		CHANGE_NAME_COST = getInt(properties, "CharacterNameChangePrice", CHANGE_NAME_COST);
		CLAN_NAME_COST = getInt(properties, "ClanNameChangePrice", CLAN_NAME_COST);
		NAME_COLOR_COST = getInt(properties, "NameColorChangePrice", NAME_COLOR_COST);
		SERVICE_COOLDOWN_MS = Math.max(1000, Math.min(10000, getInt(properties, "NpcServiceCooldownMs", SERVICE_COOLDOWN_MS)));
	}
	
	private static File resolveConfigFile(String path)
	{
		final File local = new File(path);
		if (local.isFile())
		{
			return local;
		}
		
		final File gameLocal = new File("game/" + path);
		if (gameLocal.isFile())
		{
			return gameLocal;
		}
		
		if (Config.DATAPACK_ROOT != null)
		{
			return new File(Config.DATAPACK_ROOT, path);
		}
		return local;
	}
	
	private static int getInt(Properties properties, String key, int fallback)
	{
		try
		{
			return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)).trim());
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}
	
	private enum NameColorOption
	{
		WHITE(1, "White", "FFFFFF"),
		SILVER(2, "Silver", "C0C0C0"),
		LIGHT_GRAY(3, "Light Gray", "D3D3D3"),
		RED(4, "Red", "FF4040"),
		DARK_RED(5, "Dark Red", "B22222"),
		ORANGE(6, "Orange", "FF8C00"),
		GOLD(7, "Gold", "FFD700"),
		YELLOW(8, "Yellow", "FFFF00"),
		LIME(9, "Lime", "7CFC00"),
		GREEN(10, "Green", "32CD32"),
		EMERALD(11, "Emerald", "00C878"),
		CYAN(12, "Cyan", "00FFFF"),
		SKY_BLUE(13, "Sky Blue", "00BFFF"),
		ROYAL_BLUE(14, "Royal Blue", "4169E1"),
		DARK_BLUE(15, "Dark Blue", "1E3A8A"),
		PURPLE(16, "Purple", "9370DB"),
		VIOLET(17, "Violet", "8A2BE2"),
		MAGENTA(18, "Magenta", "FF00FF"),
		PINK(19, "Pink", "FF69B4"),
		BRONZE(20, "Bronze", "CD7F32");
		
		private static final NameColorOption[] VALUES = values();
		
		private final int id;
		private final String displayName;
		private final String htmlHex;
		private final int serverColor;
		
		NameColorOption(int id, String displayName, String htmlHex)
		{
			this.id = id;
			this.displayName = displayName;
			this.htmlHex = htmlHex;
			serverColor = rgbToServerColor(htmlHex);
		}
		
		private int page()
		{
			return id <= 10 ? 1 : 2;
		}
		
		private static NameColorOption find(int id)
		{
			for (NameColorOption color : VALUES)
			{
				if (color.id == id)
				{
					return color;
				}
			}
			return null;
		}
	}
	
	public static void main(String[] args)
	{
		new MegaraxServices();
	}
}
