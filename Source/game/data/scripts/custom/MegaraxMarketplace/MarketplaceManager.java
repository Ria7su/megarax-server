package custom.MegaraxMarketplace;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.network.serverpackets.CreatureSay;

public final class MarketplaceManager
{
	private static final Logger LOGGER = Logger.getLogger(MarketplaceManager.class.getName());
	private static final MarketplaceManager INSTANCE = new MarketplaceManager();
	private static final long SESSION_TIMEOUT = 5L * 60L * 1000L;
	private static final int EXPIRATION_BATCH_SIZE = 100;
	
	private final MarketplaceDAO _dao = MarketplaceDAO.getInstance();
	private final Map<Integer, SellSession> _sellSessions = new ConcurrentHashMap<>();
	private final Map<String, Long> _cooldowns = new ConcurrentHashMap<>();
	
	private MarketplaceManager()
	{
		MarketplaceConfig.load();
		_dao.init();
		ThreadPool.scheduleAtFixedRate(this::expireListings, 5L * 60L * 1000L, 5L * 60L * 1000L);
		ThreadPool.scheduleAtFixedRate(this::cleanupSessions, 60L * 1000L, 60L * 1000L);
		LOGGER.info("Megarax Player Marketplace loaded.");
	}
	
	public static MarketplaceManager getInstance()
	{
		return INSTANCE;
	}
	
	public void reload()
	{
		MarketplaceConfig.load();
		_dao.init();
	}
	
	public void setEnabled(boolean enabled)
	{
		MarketplaceConfig.ENABLED = enabled;
	}
	
	public MarketplaceDAO getDAO()
	{
		return _dao;
	}
	
	public SellSession createSellSession(Player player, int objectId)
	{
		final Item item = (objectId > 0) && (player != null) ? player.getInventory().getItemByObjectId(objectId) : null;
		final long quantity = item == null ? 0 : (item.isStackable() ? item.getCount() : 1);
		final String reason = MarketplaceItemValidator.validateItemForListing(player, item, quantity);
		if (reason != null)
		{
			if (player != null)
			{
				player.sendMessage(reason);
			}
			return null;
		}
		
		final SellSession session = new SellSession(player.getObjectId(), objectId);
		session.quantity = quantity;
		session.price = MarketplaceConfig.MIN_PRICE;
		_sellSessions.put(player.getObjectId(), session);
		return session;
	}
	
	public SellSession getSellSession(Player player)
	{
		final SellSession session = player == null ? null : _sellSessions.get(player.getObjectId());
		if ((session == null) || session.isExpired())
		{
			if (player != null)
			{
				_sellSessions.remove(player.getObjectId());
			}
			return null;
		}
		return session;
	}
	
	public boolean updateSellSession(Player player, String token, long quantity, long price)
	{
		final SellSession session = getSellSession(player);
		if ((session == null) || !session.token.equals(token))
		{
			player.sendMessage("La sesion de venta expiro. Selecciona el objeto nuevamente.");
			return false;
		}
		final Item item = player.getInventory().getItemByObjectId(session.objectId);
		final String itemReason = MarketplaceItemValidator.validateItemForListing(player, item, quantity);
		if (itemReason != null)
		{
			player.sendMessage(itemReason);
			return false;
		}
		final String priceReason = MarketplaceItemValidator.validatePrice(price);
		if (priceReason != null)
		{
			player.sendMessage(priceReason);
			return false;
		}
		session.quantity = quantity;
		session.price = price;
		session.touchedAt = System.currentTimeMillis();
		return true;
	}
	
	public boolean confirmListing(Player player, String token)
	{
		if (!checkCooldown(player, "list"))
		{
			return false;
		}
		final SellSession session = getSellSession(player);
		if ((session == null) || !session.token.equals(token))
		{
			player.sendMessage("La sesion de venta expiro. Selecciona el objeto nuevamente.");
			return false;
		}
		if (_dao.countActiveListings(player.getObjectId()) >= MarketplaceConfig.MAX_LISTINGS_PER_PLAYER)
		{
			player.sendMessage("Has alcanzado el maximo de publicaciones activas.");
			return false;
		}
		final Item item = player.getInventory().getItemByObjectId(session.objectId);
		final String itemReason = MarketplaceItemValidator.validateItemForListing(player, item, session.quantity);
		if (itemReason != null)
		{
			player.sendMessage(itemReason);
			return false;
		}
		final String priceReason = MarketplaceItemValidator.validatePrice(session.price);
		if (priceReason != null)
		{
			player.sendMessage(priceReason);
			return false;
		}
		
		final MarketplaceItemSnapshot snapshot = MarketplaceItemSnapshot.fromItem(item, session.quantity);
		final long commission = calculateCommission(session.price, MarketplaceConfig.COMMISSION_PERCENT);
		final long revenue = session.price - commission;
		final long expiresAt = System.currentTimeMillis() + (MarketplaceConfig.LISTING_DURATION_HOURS * 60L * 60L * 1000L);
		final int listingId = _dao.insertProcessingListing(player, snapshot, session.price, commission, revenue, expiresAt);
		if (listingId <= 0)
		{
			player.sendMessage("No se pudo crear la publicacion. Intentalo nuevamente.");
			MarketplaceLogger.logFailure("FAILED_TRANSACTION", player, 0, "Could not insert listing.");
			return false;
		}
		
		if (!player.destroyItem(ItemProcessType.SELL, item, (int) session.quantity, player, true))
		{
			_dao.markError(listingId);
			player.sendMessage("No se pudo retirar el objeto del inventario. Publicacion cancelada.");
			MarketplaceLogger.logFailure("FAILED_TRANSACTION", player, listingId, "Could not destroy listed item.");
			return false;
		}
		
		if (!_dao.activateListing(listingId, player.getObjectId()))
		{
			player.sendMessage("El objeto fue retirado pero la publicacion no pudo activarse. Se registro para revision.");
			MarketplaceLogger.logFailure("FAILED_TRANSACTION", player, listingId, "Could not activate listing after item removal.");
			return false;
		}
		
		_sellSessions.remove(player.getObjectId());
		final MarketplaceListing listing = _dao.getListing(listingId);
		MarketplaceLogger.log("LISTED", player, listing, "durationHours=" + MarketplaceConfig.LISTING_DURATION_HOURS);
		player.sendMessage("Tu objeto fue publicado correctamente en el Marketplace.");
		player.sendMessage("La publicacion tiene una duracion de " + MarketplaceConfig.LISTING_DURATION_HOURS + " horas.");
		return true;
	}
	
	public boolean buy(Player buyer, int listingId)
	{
		if (!checkCooldown(buyer, "buy"))
		{
			return false;
		}
		final String playerReason = MarketplaceItemValidator.validatePlayer(buyer);
		if (playerReason != null)
		{
			buyer.sendMessage(playerReason);
			return false;
		}
		
		boolean paid = false;
		long paidAmount = 0;
		MarketplaceListing listing = null;
		try (Connection con = DatabaseFactory.getConnection())
		{
			con.setAutoCommit(false);
			try
			{
				listing = _dao.getListingForUpdate(con, listingId);
				final String validation = validatePurchase(buyer, listing);
				if (validation != null)
				{
					con.rollback();
					buyer.sendMessage(validation);
					return false;
				}
				
				paidAmount = listing.price;
				if (!buyer.destroyItemByItemId(ItemProcessType.FEE, listing.currencyItemId, (int) paidAmount, buyer, true))
				{
					con.rollback();
					buyer.sendMessage("No tienes suficientes Rax Coin.");
					return false;
				}
				paid = true;
				
				final long now = System.currentTimeMillis();
				final long commission = calculateCommission(listing.price, listing.commissionPercent);
				final long revenue = listing.price - commission;
				_dao.markSold(con, listing, buyer, commission, revenue, now);
				listing.buyerCharId = buyer.getObjectId();
				listing.buyerName = buyer.getName();
				listing.status = MarketplaceStatus.SOLD;
				listing.commissionAmount = commission;
				listing.sellerRevenue = revenue;
				
				boolean delivered = false;
				if (MarketplaceItemValidator.canReceiveItem(buyer, listing.item))
				{
					delivered = listing.item.giveTo(buyer, buyer, true) != null;
				}
				if (!delivered)
				{
					_dao.insertClaim(con, buyer.getObjectId(), buyer.getName(), MarketplaceClaimType.ITEM, listing, 0, 0, now);
				}
				
				_dao.insertClaim(con, listing.sellerCharId, listing.sellerName, MarketplaceClaimType.CURRENCY, listing, revenue, listing.currencyItemId, now);
				_dao.insertHistory(con, listing, buyer.getObjectId(), listing.price, commission, revenue, "PURCHASED", buyer, now);
				con.commit();
				
				buyer.sendMessage("El objeto fue comprado correctamente.");
				buyer.sendMessage("Se descontaron " + listing.price + " Rax Coin de tu inventario.");
				if (!delivered)
				{
					buyer.sendMessage("No tienes suficiente espacio en el inventario. El objeto fue enviado a Objetos por reclamar.");
				}
				notifySeller(listing, revenue, commission);
				announceRareSale(buyer, listing);
				MarketplaceLogger.log("PURCHASED", buyer, listing, delivered ? "delivered=true" : "delivered=false claim_created=true");
				return true;
			}
			catch (Exception e)
			{
				rollback(con);
				if (paid)
				{
					refundCurrency(buyer, MarketplaceConfig.CURRENCY_ITEM_ID, paidAmount);
				}
				LOGGER.log(Level.WARNING, "Marketplace: Purchase failed for listing " + listingId + ".", e);
				MarketplaceLogger.logFailure("FAILED_TRANSACTION", buyer, listingId, e.getMessage());
				buyer.sendMessage("La compra no pudo completarse. No se perdio ningun objeto ni moneda.");
			}
			finally
			{
				con.setAutoCommit(true);
			}
		}
		catch (SQLException e)
		{
			if (paid)
			{
				refundCurrency(buyer, MarketplaceConfig.CURRENCY_ITEM_ID, paidAmount);
			}
			LOGGER.log(Level.WARNING, "Marketplace: Could not open purchase transaction.", e);
			MarketplaceLogger.logFailure("FAILED_TRANSACTION", buyer, listingId, e.getMessage());
		}
		return false;
	}
	
	public boolean cancelListing(Player player, int listingId)
	{
		if (!checkCooldown(player, "cancel"))
		{
			return false;
		}
		try (Connection con = DatabaseFactory.getConnection())
		{
			con.setAutoCommit(false);
			try
			{
				final MarketplaceListing listing = _dao.getListingForUpdate(con, listingId);
				if ((listing == null) || (listing.sellerCharId != player.getObjectId()))
				{
					con.rollback();
					player.sendMessage("No puedes cancelar esta publicacion.");
					return false;
				}
				if (listing.status != MarketplaceStatus.ACTIVE)
				{
					con.rollback();
					player.sendMessage("Solo puedes cancelar publicaciones activas.");
					return false;
				}
				final long now = System.currentTimeMillis();
				_dao.markCancelled(con, listing, MarketplaceStatus.CANCELLED, now);
				_dao.insertClaim(con, player.getObjectId(), player.getName(), MarketplaceClaimType.ITEM, listing, 0, 0, now);
				_dao.insertHistory(con, listing, 0, listing.price, 0, 0, "CANCELLED", player, now);
				con.commit();
				listing.status = MarketplaceStatus.CANCELLED;
				MarketplaceLogger.log("CANCELLED", player, listing, "claim_created=true");
				player.sendMessage("Publicacion cancelada. Recupera el objeto en Objetos por reclamar.");
				return true;
			}
			catch (Exception e)
			{
				rollback(con);
				LOGGER.log(Level.WARNING, "Marketplace: Could not cancel listing " + listingId + ".", e);
				MarketplaceLogger.logFailure("FAILED_TRANSACTION", player, listingId, e.getMessage());
				player.sendMessage("No se pudo cancelar la publicacion.");
			}
			finally
			{
				con.setAutoCommit(true);
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace: Could not open cancel transaction.", e);
		}
		return false;
	}
	
	public boolean claim(Player player, int claimId)
	{
		return claim(player, claimId, true);
	}
	
	private boolean claim(Player player, int claimId, boolean checkCooldown)
	{
		if (checkCooldown && !checkCooldown(player, "claim"))
		{
			return false;
		}
		try (Connection con = DatabaseFactory.getConnection())
		{
			con.setAutoCommit(false);
			try
			{
				final MarketplaceClaim claim = _dao.getClaimForUpdate(con, claimId);
				if ((claim == null) || (claim.ownerCharId != player.getObjectId()) || (claim.status != MarketplaceClaimStatus.PENDING))
				{
					con.rollback();
					player.sendMessage("No tienes nada para reclamar en esta entrada.");
					return false;
				}
				
				final long now = System.currentTimeMillis();
				if (claim.claimType == MarketplaceClaimType.CURRENCY)
				{
					if ((claim.currencyAmount <= 0) || (claim.currencyAmount > Integer.MAX_VALUE))
					{
						con.rollback();
						player.sendMessage("La ganancia pendiente tiene una cantidad invalida.");
						return false;
					}
					player.addItem(ItemProcessType.REWARD, claim.currencyItemId, (int) claim.currencyAmount, player, true);
				}
				else
				{
					if (!MarketplaceItemValidator.canReceiveItem(player, claim.item))
					{
						con.rollback();
						player.sendMessage("No tienes suficiente espacio o peso disponible.");
						return false;
					}
					if (claim.item.giveTo(player, player, true) == null)
					{
						con.rollback();
						player.sendMessage("No se pudo entregar el objeto. Intentalo nuevamente.");
						return false;
					}
				}
				
				_dao.markClaimed(con, claim.claimId, now);
				con.commit();
				player.sendMessage(claim.claimType == MarketplaceClaimType.CURRENCY ? "Ganancia reclamada correctamente." : "Objeto reclamado correctamente.");
				MarketplaceLogger.logFailure("CLAIMED", player, claim.listingId, "claimId=" + claim.claimId + " type=" + claim.claimType.name());
				return true;
			}
			catch (Exception e)
			{
				rollback(con);
				LOGGER.log(Level.WARNING, "Marketplace: Could not claim " + claimId + ".", e);
				MarketplaceLogger.logFailure("FAILED_TRANSACTION", player, 0, e.getMessage());
				player.sendMessage("No se pudo reclamar. Intentalo nuevamente.");
			}
			finally
			{
				con.setAutoCommit(true);
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace: Could not open claim transaction.", e);
		}
		return false;
	}
	
	public int claimAll(Player player)
	{
		if (!checkCooldown(player, "claim"))
		{
			return 0;
		}
		int claimed = 0;
		final List<MarketplaceClaim> claims = _dao.getClaims(player.getObjectId());
		for (MarketplaceClaim claim : claims)
		{
			if (!claim(player, claim.claimId, false))
			{
				break;
			}
			claimed++;
		}
		return claimed;
	}
	
	public void expireListings()
	{
		for (Integer listingId : _dao.getExpiredListingIds(EXPIRATION_BATCH_SIZE))
		{
			expireListing(listingId.intValue());
		}
	}
	
	public boolean expireListing(int listingId)
	{
		try (Connection con = DatabaseFactory.getConnection())
		{
			con.setAutoCommit(false);
			try
			{
				final MarketplaceListing listing = _dao.getListingForUpdate(con, listingId);
				if ((listing == null) || (listing.status != MarketplaceStatus.ACTIVE) || (listing.expiresAt > System.currentTimeMillis()))
				{
					con.rollback();
					return false;
				}
				final long now = System.currentTimeMillis();
				_dao.markCancelled(con, listing, MarketplaceStatus.EXPIRED, now);
				_dao.insertClaim(con, listing.sellerCharId, listing.sellerName, MarketplaceClaimType.ITEM, listing, 0, 0, now);
				_dao.insertHistory(con, listing, 0, listing.price, 0, 0, "EXPIRED", null, now);
				con.commit();
				listing.status = MarketplaceStatus.EXPIRED;
				MarketplaceLogger.log("EXPIRED", null, listing, "claim_created=true");
				return true;
			}
			catch (Exception e)
			{
				rollback(con);
				LOGGER.log(Level.WARNING, "Marketplace: Could not expire listing " + listingId + ".", e);
			}
			finally
			{
				con.setAutoCommit(true);
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace: Could not open expiration transaction.", e);
		}
		return false;
	}
	
	public boolean adminRemove(Player admin, int listingId)
	{
		try (Connection con = DatabaseFactory.getConnection())
		{
			con.setAutoCommit(false);
			try
			{
				final MarketplaceListing listing = _dao.getListingForUpdate(con, listingId);
				if ((listing == null) || (listing.status != MarketplaceStatus.ACTIVE))
				{
					con.rollback();
					admin.sendMessage("Only active listings can be removed safely.");
					return false;
				}
				final long now = System.currentTimeMillis();
				_dao.markCancelled(con, listing, MarketplaceStatus.ADMIN_REMOVED, now);
				_dao.insertClaim(con, listing.sellerCharId, listing.sellerName, MarketplaceClaimType.ADMIN_RECOVERY, listing, 0, 0, now);
				_dao.insertHistory(con, listing, 0, listing.price, 0, 0, "ADMIN_REMOVED", admin, now);
				con.commit();
				listing.status = MarketplaceStatus.ADMIN_REMOVED;
				MarketplaceLogger.log("ADMIN_REMOVED", admin, listing, "claim_created=true");
				return true;
			}
			catch (Exception e)
			{
				rollback(con);
				LOGGER.log(Level.WARNING, "Marketplace: Could not admin remove listing " + listingId + ".", e);
				admin.sendMessage("Could not remove listing.");
			}
			finally
			{
				con.setAutoCommit(true);
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Marketplace: Could not open admin transaction.", e);
		}
		return false;
	}
	
	public long calculateCommission(long price, int percent)
	{
		long commission = (price * Math.max(0, percent)) / 100;
		if ((price > 0) && (commission >= price))
		{
			commission = price - 1;
		}
		return Math.max(0, commission);
	}
	
	public long getCurrencyCount(Player player)
	{
		return player == null ? 0 : player.getInventory().getInventoryItemCount(MarketplaceConfig.CURRENCY_ITEM_ID, -1);
	}
	
	private String validatePurchase(Player buyer, MarketplaceListing listing)
	{
		if (listing == null)
		{
			return "Esta publicacion ya no existe.";
		}
		if (listing.status != MarketplaceStatus.ACTIVE)
		{
			return "Esta publicacion ya no esta disponible.";
		}
		if (listing.expiresAt <= System.currentTimeMillis())
		{
			return "Esta publicacion ya vencio.";
		}
		if (listing.sellerCharId == buyer.getObjectId())
		{
			return "No puedes comprar tus propios objetos.";
		}
		if (listing.price < MarketplaceConfig.MIN_PRICE)
		{
			return "El precio almacenado es invalido.";
		}
		if ((listing.price <= 0) || (listing.price > Integer.MAX_VALUE))
		{
			return "El precio excede el limite seguro del servidor.";
		}
		if (getCurrencyCount(buyer) < listing.price)
		{
			return "No tienes suficientes Rax Coin.";
		}
		if (buyer.isProcessingTransaction())
		{
			return "No puedes comprar mientras realizas otra transaccion.";
		}
		return null;
	}
	
	private boolean checkCooldown(Player player, String action)
	{
		if ((player == null) || (MarketplaceConfig.LISTING_COOLDOWN_SECONDS <= 0))
		{
			return true;
		}
		final String key = player.getObjectId() + ":" + action;
		final long now = System.currentTimeMillis();
		final Long previous = _cooldowns.get(key);
		if ((previous != null) && ((now - previous.longValue()) < (MarketplaceConfig.LISTING_COOLDOWN_SECONDS * 1000L)))
		{
			player.sendMessage("Espera unos segundos antes de repetir esa accion.");
			return false;
		}
		_cooldowns.put(key, Long.valueOf(now));
		return true;
	}
	
	private void notifySeller(MarketplaceListing listing, long revenue, long commission)
	{
		final Player seller = World.getInstance().getPlayer(listing.sellerCharId);
		if (seller != null)
		{
			seller.sendMessage("Has vendido " + itemDisplay(listing) + ".");
			seller.sendMessage("Comision Megarax: " + commission + " Rax Coin.");
			seller.sendMessage("Ganancia pendiente: " + revenue + " Rax Coin.");
		}
	}
	
	private void announceRareSale(Player buyer, MarketplaceListing listing)
	{
		if (!MarketplaceConfig.ANNOUNCE_RARE_SALES)
		{
			return;
		}
		if ((listing.price < MarketplaceConfig.RARE_SALE_MIN_PRICE) && (listing.item.enchantLevel < MarketplaceConfig.ANNOUNCE_ENCHANT_LEVEL))
		{
			return;
		}
		final String message = "Marketplace: " + buyer.getName() + " bought " + itemDisplay(listing) + " for " + listing.price + " Rax Coin.";
		for (Player player : World.getInstance().getPlayers())
		{
			player.sendPacket(new CreatureSay(null, ChatType.ANNOUNCEMENT, "Marketplace", message));
		}
	}
	
	private String itemDisplay(MarketplaceListing listing)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append(listing.item.itemName);
		if (listing.item.enchantLevel > 0)
		{
			sb.append(" +").append(listing.item.enchantLevel);
		}
		return sb.toString();
	}
	
	private void refundCurrency(Player buyer, int currencyItemId, long amount)
	{
		if ((buyer != null) && (amount > 0) && (amount <= Integer.MAX_VALUE))
		{
			buyer.addItem(ItemProcessType.REFUND, currencyItemId, (int) amount, buyer, true);
		}
	}
	
	private void rollback(Connection con)
	{
		try
		{
			con.rollback();
		}
		catch (SQLException ignored)
		{
		}
	}
	
	private void cleanupSessions()
	{
		_sellSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
	}
	
	public static class SellSession
	{
		public final int ownerCharId;
		public final int objectId;
		public final String token;
		public long quantity;
		public long price;
		public long touchedAt;
		
		private SellSession(int ownerCharId, int objectId)
		{
			this.ownerCharId = ownerCharId;
			this.objectId = objectId;
			token = UUID.randomUUID().toString().replace("-", "");
			touchedAt = System.currentTimeMillis();
		}
		
		public boolean isExpired()
		{
			return (System.currentTimeMillis() - touchedAt) > SESSION_TIMEOUT;
		}
	}
}
