ALTER TABLE settings RENAME TO steve_settings;
ALTER TABLE `steve_settings` ADD COLUMN `webhook_enabled` BOOLEAN DEFAULT FALSE;
ALTER TABLE `steve_settings` ADD COLUMN `webhook` VARCHAR(255) DEFAULT NULL;