package custom.MegaraxMarketplace;

import org.l2jmobius.gameserver.handler.IParseBoardHandler;
import org.l2jmobius.gameserver.model.actor.Player;

public class MarketplaceBypassHandler implements IParseBoardHandler
{
	private final MarketplaceManager _manager = MarketplaceManager.getInstance();
	private final MarketplaceHtmlBuilder _html = new MarketplaceHtmlBuilder();
	
	@Override
	public String[] getCommandList()
	{
		return new String[]
		{
			"_bbsmarket"
		};
	}
	
	@Override
	public boolean onCommand(String command, Player player)
	{
		if ((player == null) || (command == null))
		{
			return false;
		}
		
		try
		{
			if (command.equals("_bbsmarket"))
			{
				_html.showHome(player);
				return false;
			}
			
			final String[] args = command.split(";", -1);
			final String action = args.length > 1 ? args[1] : "";
			if ("browse".equals(action))
			{
				_html.showBrowse(player, parseBrowse(args));
			}
			else if ("search".equals(action))
			{
				_html.showBrowse(player, parseSearch(args));
			}
			else if ("sell".equals(action))
			{
				_html.showSellList(player, parseInt(args, 2, 1));
			}
			else if ("select".equals(action))
			{
				final MarketplaceManager.SellSession session = _manager.createSellSession(player, parseInt(args, 2, 0));
				if (session != null)
				{
					_html.showSellReview(player, session);
				}
				else
				{
					_html.showSellList(player, 1);
				}
			}
			else if ("quote".equals(action))
			{
				final MarketplaceManager.SellSession session = _manager.getSellSession(player);
				final String token = get(args, 2);
				final long quantity = parseLong(args, 3, session == null ? 0 : session.quantity);
				final long price = parseLong(args, 4, session == null ? 0 : session.price);
				if (_manager.updateSellSession(player, token, quantity, price))
				{
					_html.showSellReview(player, _manager.getSellSession(player));
				}
				else
				{
					_html.showSellList(player, 1);
				}
			}
			else if ("confirmSell".equals(action))
			{
				_manager.confirmListing(player, get(args, 2));
				_html.showMyListings(player);
			}
			else if ("view".equals(action) || "buyConfirm".equals(action))
			{
				_html.showListing(player, parseInt(args, 2, 0));
			}
			else if ("buy".equals(action))
			{
				_manager.buy(player, parseInt(args, 2, 0));
				_html.showBrowse(player, new MarketplaceFilter());
			}
			else if ("myListings".equals(action))
			{
				_html.showMyListings(player);
			}
			else if ("cancel".equals(action))
			{
				_manager.cancelListing(player, parseInt(args, 2, 0));
				_html.showMyListings(player);
			}
			else if ("gains".equals(action))
			{
				_html.showGains(player);
			}
			else if ("claims".equals(action))
			{
				_html.showClaims(player);
			}
			else if ("claim".equals(action))
			{
				_manager.claim(player, parseInt(args, 2, 0));
				_html.showClaims(player);
			}
			else if ("claimGain".equals(action))
			{
				_manager.claim(player, parseInt(args, 2, 0));
				_html.showGains(player);
			}
			else if ("claimAll".equals(action))
			{
				final int count = _manager.claimAll(player);
				player.sendMessage("Reclamaciones completadas: " + count + ".");
				_html.showClaims(player);
			}
			else if ("history".equals(action))
			{
				_html.showHistory(player);
			}
			else if ("rules".equals(action))
			{
				_html.showRules(player);
			}
			else
			{
				_html.showHome(player);
			}
		}
		catch (RuntimeException e)
		{
			MarketplaceLogger.logFailure("FAILED_TRANSACTION", player, 0, e.getMessage());
			player.sendMessage("Marketplace: accion invalida.");
			_html.showHome(player);
		}
		return false;
	}
	
	private MarketplaceFilter parseBrowse(String[] args)
	{
		final MarketplaceFilter filter = new MarketplaceFilter();
		filter.page = parseInt(args, 2, 1);
		filter.category = MarketplaceCategory.fromId(get(args, 3));
		filter.sort = MarketplaceSort.fromId(get(args, 4));
		return filter;
	}
	
	private MarketplaceFilter parseSearch(String[] args)
	{
		final MarketplaceFilter filter = new MarketplaceFilter();
		filter.page = 1;
		filter.search = sanitize(get(args, 2), 40);
		filter.minPrice = parseLong(args, 3, 0);
		filter.maxPrice = parseLong(args, 4, 0);
		filter.seller = sanitize(get(args, 5), 35);
		filter.minEnchant = parseOptionalInt(args, 6, Integer.MIN_VALUE);
		filter.maxEnchant = parseOptionalInt(args, 7, Integer.MAX_VALUE);
		return filter;
	}
	
	private String get(String[] args, int index)
	{
		return args.length > index ? args[index] : "";
	}
	
	private int parseInt(String[] args, int index, int fallback)
	{
		try
		{
			final String value = sanitize(get(args, index), 16);
			if (value.isEmpty())
			{
				return fallback;
			}
			return Integer.parseInt(value);
		}
		catch (RuntimeException e)
		{
			return fallback;
		}
	}
	
	private long parseLong(String[] args, int index, long fallback)
	{
		try
		{
			final String value = sanitize(get(args, index), 18);
			if (value.isEmpty())
			{
				return fallback;
			}
			return Long.parseLong(value);
		}
		catch (RuntimeException e)
		{
			return fallback;
		}
	}
	
	private int parseOptionalInt(String[] args, int index, int fallback)
	{
		try
		{
			final String value = sanitize(get(args, index), 8);
			if (value.isEmpty())
			{
				return fallback;
			}
			return Integer.parseInt(value);
		}
		catch (RuntimeException e)
		{
			return fallback;
		}
	}
	
	private String sanitize(String value, int maxLength)
	{
		if (value == null)
		{
			return "";
		}
		String clean = value.trim();
		if (clean.startsWith("$"))
		{
			return "";
		}
		clean = clean.replace(";", "").replace("<", "").replace(">", "").replace("\"", "").replace("'", "");
		if (clean.length() > maxLength)
		{
			clean = clean.substring(0, maxLength);
		}
		return clean;
	}
}
