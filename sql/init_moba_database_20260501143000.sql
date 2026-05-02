-- =============================================
-- MOBA Game Database Schema
-- Generated: 20260501143000
-- =============================================

-- -------------------------------------------
-- Database: moba_business
-- -------------------------------------------
CREATE DATABASE IF NOT EXISTS `moba_business` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `moba_business`;

-- -------------------------------------------
-- Table: t_user
-- User information table
-- -------------------------------------------
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `username` VARCHAR(50) NOT NULL COMMENT 'Username, unique',
    `password` VARCHAR(100) NOT NULL COMMENT 'Password',
    `nickname` VARCHAR(50) NOT NULL COMMENT 'Nickname, unique',
    `level` INT NOT NULL DEFAULT 1 COMMENT 'User level',
    `rank_score` INT NOT NULL DEFAULT 1000 COMMENT 'Ranking score',
    `total_battles` INT NOT NULL DEFAULT 0 COMMENT 'Total battle count',
    `win_count` INT NOT NULL DEFAULT 0 COMMENT 'Win count',
    `lose_count` INT NOT NULL DEFAULT 0 COMMENT 'Lose count',
    `state` VARCHAR(20) NOT NULL DEFAULT 'OFFLINE' COMMENT 'User state: OFFLINE, ONLINE, IN_BATTLE',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_nickname` (`nickname`),
    KEY `idx_state` (`state`),
    KEY `idx_rank_score` (`rank_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User information table';

-- -------------------------------------------
-- Table: t_quest_template
-- Quest template table
-- -------------------------------------------
DROP TABLE IF EXISTS `t_quest_template`;
CREATE TABLE `t_quest_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `quest_code` VARCHAR(50) NOT NULL COMMENT 'Quest code, unique',
    `quest_name` VARCHAR(100) NOT NULL COMMENT 'Quest name',
    `description` VARCHAR(500) DEFAULT NULL COMMENT 'Quest description',
    `quest_type` VARCHAR(20) NOT NULL COMMENT 'Quest type: DAILY, WEEKLY, ACHIEVEMENT, NOVICE, SEASON',
    `category` VARCHAR(30) NOT NULL COMMENT 'Quest category',
    `target_value` INT NOT NULL COMMENT 'Target value to complete',
    `reward_type` VARCHAR(20) NOT NULL COMMENT 'Reward type: GOLD, DIAMOND, EXP, HERO_FRAGMENT, SKIN_FRAGMENT, CHEST, TITLE, AVATAR_FRAME',
    `reward_amount` INT NOT NULL COMMENT 'Reward amount',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT 'Sort order',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Is enabled',
    `required_level` INT DEFAULT 0 COMMENT 'Required level',
    `required_quest_order` INT DEFAULT -1 COMMENT 'Required previous quest order',
    `game_mode_restriction` VARCHAR(50) DEFAULT NULL COMMENT 'Restricted game mode',
    `hero_restriction` VARCHAR(50) DEFAULT NULL COMMENT 'Restricted hero',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_quest_code` (`quest_code`),
    KEY `idx_quest_type` (`quest_type`),
    KEY `idx_category` (`category`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Quest template table';

-- -------------------------------------------
-- Table: t_player_quest
-- Player quest progress table
-- -------------------------------------------
DROP TABLE IF EXISTS `t_player_quest`;
CREATE TABLE `t_player_quest` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `player_id` BIGINT NOT NULL COMMENT 'Player ID',
    `quest_template_id` BIGINT NOT NULL COMMENT 'Quest template ID',
    `quest_code` VARCHAR(50) NOT NULL COMMENT 'Quest code',
    `quest_type` VARCHAR(20) NOT NULL COMMENT 'Quest type',
    `category` VARCHAR(30) NOT NULL COMMENT 'Quest category',
    `current_value` INT NOT NULL DEFAULT 0 COMMENT 'Current progress value',
    `target_value` INT NOT NULL COMMENT 'Target value',
    `state` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Quest state: LOCKED, ACTIVE, COMPLETED, CLAIMED, EXPIRED',
    `reward_type` VARCHAR(20) NOT NULL COMMENT 'Reward type',
    `reward_amount` INT NOT NULL COMMENT 'Reward amount',
    `completed_at` DATETIME DEFAULT NULL COMMENT 'Completed time',
    `claimed_at` DATETIME DEFAULT NULL COMMENT 'Claimed time',
    `expire_at` DATETIME DEFAULT NULL COMMENT 'Expire time',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    PRIMARY KEY (`id`),
    KEY `idx_player_id` (`player_id`),
    KEY `idx_quest_template_id` (`quest_template_id`),
    KEY `idx_quest_code` (`quest_code`),
    KEY `idx_state` (`state`),
    KEY `idx_expire_at` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Player quest progress table';

-- -------------------------------------------
-- Database: moba_battle
-- -------------------------------------------
CREATE DATABASE IF NOT EXISTS `moba_battle` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `moba_battle`;

-- -------------------------------------------
-- Note: moba_battle database is used by moba-battle-server
-- Currently moba-battle-server uses in-memory models for battle state management
-- Battle sessions, room data, and match history are managed in Redis
-- This database can be used for battle replay storage, battle statistics, etc.
-- -------------------------------------------

-- -------------------------------------------
-- Table: t_battle_replay
-- Battle replay storage table (optional)
-- -------------------------------------------
DROP TABLE IF EXISTS `t_battle_replay`;
CREATE TABLE `t_battle_replay` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `battle_id` VARCHAR(64) NOT NULL COMMENT 'Battle ID',
    `replay_data` LONGTEXT COMMENT 'Replay data in JSON format',
    `duration` INT DEFAULT 0 COMMENT 'Battle duration in seconds',
    `game_mode` VARCHAR(20) NOT NULL COMMENT 'Game mode: 3v3v3, 5v5',
    `map_id` VARCHAR(20) NOT NULL COMMENT 'Map ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_battle_id` (`battle_id`),
    KEY `idx_game_mode` (`game_mode`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Battle replay storage table';

-- -------------------------------------------
-- Table: t_battle_statistics
-- Battle statistics table
-- -------------------------------------------
DROP TABLE IF EXISTS `t_battle_statistics`;
CREATE TABLE `t_battle_statistics` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `player_id` BIGINT NOT NULL COMMENT 'Player ID',
    `game_mode` VARCHAR(20) NOT NULL COMMENT 'Game mode',
    `battle_count` INT NOT NULL DEFAULT 0 COMMENT 'Total battle count',
    `win_count` INT NOT NULL DEFAULT 0 COMMENT 'Win count',
    `lose_count` INT NOT NULL DEFAULT 0 COMMENT 'Lose count',
    `total_kills` INT NOT NULL DEFAULT 0 COMMENT 'Total kills',
    `total_deaths` INT NOT NULL DEFAULT 0 COMMENT 'Total deaths',
    `total_assists` INT NOT NULL DEFAULT 0 COMMENT 'Total assists',
    `total_damage_dealt` BIGINT NOT NULL DEFAULT 0 COMMENT 'Total damage dealt',
    `total_damage_taken` BIGINT NOT NULL DEFAULT 0 COMMENT 'Total damage taken',
    `total_healing_done` BIGINT NOT NULL DEFAULT 0 COMMENT 'Total healing done',
    `total_gold_earned` BIGINT NOT NULL DEFAULT 0 COMMENT 'Total gold earned',
    `mvp_count` INT NOT NULL DEFAULT 0 COMMENT 'MVP count',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_player_game_mode` (`player_id`, `game_mode`),
    KEY `idx_player_id` (`player_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Battle statistics table';

-- -------------------------------------------
-- Insert sample quest templates for moba_business
-- -------------------------------------------
INSERT INTO `moba_business`.`t_quest_template` (`quest_code`, `quest_name`, `description`, `quest_type`, `category`, `target_value`, `reward_type`, `reward_amount`, `sort_order`, `enabled`, `required_level`, `game_mode_restriction`) VALUES
('DAILY_WIN_3', 'Daily Victory', 'Win 3 battles today', 'DAILY', 'BATTLE_WIN', 3, 'GOLD', 100, 1, 1, 1, NULL),
('DAILY_PLAY_5', 'Daily Warrior', 'Play 5 battles today', 'DAILY', 'BATTLE_PLAY', 5, 'EXP', 50, 2, 1, 1, NULL),
('DAILY_KILL_10', 'Daily Killer', 'Kill 10 enemies today', 'DAILY', 'KILL_COUNT', 10, 'GOLD', 80, 3, 1, 5, NULL),
('WEEKLY_WIN_20', 'Weekly Champion', 'Win 20 battles this week', 'WEEKLY', 'BATTLE_WIN', 20, 'DIAMOND', 50, 10, 1, 10, NULL),
('ACHIEVE_FIRST_BLOOD', 'First Blood', 'Get first blood in a battle', 'ACHIEVEMENT', 'FIRST_BLOOD', 1, 'GOLD', 200, 100, 1, 1, NULL),
('ACHIEVE_TRIPLE_KILL', 'Triple Kill', 'Achieve a triple kill', 'ACHIEVEMENT', 'TRIPLE_KILL', 1, 'SKIN_FRAGMENT', 10, 101, 1, 5, NULL),
('ACHIEVE_PENTA_KILL', 'Penta Kill', 'Achieve a penta kill', 'ACHIEVEMENT', 'PENTA_KILL', 1, 'HERO_FRAGMENT', 50, 102, 1, 10, NULL),
('NOVICE_START', 'Getting Started', 'Complete your first battle', 'NOVICE', 'BATTLE_PLAY', 1, 'GOLD', 500, 1, 1, 1, NULL);
