-- Removes the invalid item object reported by ItemData during startup.
-- NPC 9956 (Varka Bloodcaller) is intentionally preserved; only item rows are invalid.
DELETE FROM items WHERE item_id = 9956;
DELETE FROM itemsonground WHERE item_id = 9956;
