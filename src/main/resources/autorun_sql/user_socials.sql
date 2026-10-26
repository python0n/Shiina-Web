CREATE TABLE IF NOT EXISTS `sh_user_socials` (
  `user_id` int NOT NULL,
  `discord` varchar(37) DEFAULT NULL,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB;
