# Megarax Player Marketplace

## Installation

1. Execute `db_installer/sql/game/marketplace.sql` on the game database.
2. Review `game/config/Custom/Marketplace.ini` and set `MarketplaceCurrencyItemId` to the live Rax Coin item id.
3. Review `game/config/Custom/MarketplaceRestrictedItems.ini`.
4. Restart the game server or reload handlers/configs from the admin panel:
   - `//reload config`
   - `//reload handler`
5. Open the Community Board and use `Marketplace` from the custom navigation.

The module registers:

- Community Board: `_bbsmarket`
- Admin command: `//marketplace`

## Admin Commands

- `//marketplace reload`
- `//marketplace info <listingId>`
- `//marketplace remove <listingId>`
- `//marketplace return <listingId>`
- `//marketplace seller <playerName>`
- `//marketplace buyer <playerName>`
- `//marketplace disable`
- `//marketplace enable`

Administrative removals never destroy the item silently. Active listings are moved to claims.

## Mandatory Test Cases

1. Publish a normal weapon.
2. Publish a +20 weapon.
3. Publish a partial stackable amount.
4. Try to publish an equipped item.
5. Try to publish a no-trade item.
6. Try to publish Rax Coin.
7. Buy with enough Rax Coin.
8. Buy without enough Rax Coin.
9. Two players buy the same listing at the same time.
10. Seller cancels while another player buys.
11. Double-click Buy.
12. Double-click Claim.
13. Buy with full inventory.
14. Buy while overweight.
15. Let a listing expire.
16. Restart with active listings.
17. Disconnect during purchase.
18. Force an SQL error during purchase on a test database.
19. Seller offline during sale.
20. Buyer offline after creating an item claim.
21. Restart before claiming seller revenue.
22. Search with special characters.
23. Use an extremely large price.
24. Use a negative quantity.
25. Send a manipulated bypass.
26. Send an object id belonging to another player.

Expected result: no item or Rax Coin is duplicated or lost; failed operations leave the listing, claim, or refund in a recoverable state.

## Anti-Exploit Protections

- Active listings are locked with `SELECT ... FOR UPDATE` before purchase, cancellation, expiration, or admin removal.
- Public listing state changes are guarded by status transitions.
- Seller revenue is created as a pending claim instead of direct offline delivery.
- Buyer item delivery falls back to an item claim when space or weight is not available.
- Claims are locked and marked by status to stop double-claim bypass spam.
- Listing sessions use a per-player random token and expire quickly.
- The server revalidates item ownership, quantity, equipped state, tradeability, restrictions, price, and player state before listing.
- The server reads price, seller, item id, quantity, and commission from the database during purchase.
- Prepared statements are used for database input.
- Search text is length-limited and escaped for SQL LIKE.
- Cooldowns are applied to listing, buying, cancelling, and claiming actions.
- Currency item and Adena sales are blocked by default.
- Items are removed from inventory when listed, so private stores and trades cannot use the same object.
- Logs are written to `game/log/marketplace.log` when enabled.
