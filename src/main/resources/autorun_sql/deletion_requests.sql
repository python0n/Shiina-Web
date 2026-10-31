CREATE TABLE IF NOT EXISTS `sh_deletion_requests` (
  `user_id` INT NOT NULL,
  `requested_at` INT NOT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE = InnoDB;
