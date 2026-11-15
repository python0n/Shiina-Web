CREATE TABLE IF NOT EXISTS `sh_password_reset` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `token_hash` CHAR(64) NOT NULL,
  `user_id` INT NOT NULL,
  `created_at` INT NOT NULL,
  `expires_at` INT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `token_hash` (`token_hash`),
  KEY `user_id` (`user_id`)
) ENGINE = InnoDB;
