CREATE TABLE IF NOT EXISTS `sh_restricted_hash_hits` (
  `user_id` INT NOT NULL,
  `hash_id` INT NOT NULL,
  PRIMARY KEY (`user_id`, `hash_id`)
) ENGINE = InnoDB;
