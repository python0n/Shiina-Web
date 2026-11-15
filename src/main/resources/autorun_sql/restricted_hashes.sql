CREATE TABLE IF NOT EXISTS `sh_restricted_hashes` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `former_user_id` INT NOT NULL,
  `former_name` VARCHAR(32) NOT NULL,
  `uninstall_id` VARCHAR(64) NOT NULL,
  `disk_serial` VARCHAR(64) NOT NULL,
  `adapters` VARCHAR(64) NOT NULL,
  `deleted_at` INT NOT NULL,
  `expires_at` INT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `uninstall_id` (`uninstall_id`),
  KEY `expires_at` (`expires_at`)
) ENGINE = InnoDB;
