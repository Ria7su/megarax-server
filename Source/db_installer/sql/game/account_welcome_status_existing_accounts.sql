-- Run once before enabling the Welcome Center on an established database.
-- It marks every account already represented in the game database as seen.
INSERT INTO `account_welcome_status`
(`account_name`, `welcome_shown`, `first_shown_at`, `last_opened_at`, `last_character_id`)
SELECT c.`account_name`, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, MAX(c.`charId`)
FROM `characters` c
WHERE c.`account_name` IS NOT NULL AND c.`account_name` <> ''
GROUP BY c.`account_name`
ON DUPLICATE KEY UPDATE `welcome_shown`=1, `updated_at`=CURRENT_TIMESTAMP;

