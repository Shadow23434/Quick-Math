CREATE DATABASE IF NOT EXISTS quickmath CHARACTER SET utf8mb4 COLLATE=utf8mb4_unicode_ci;
USE quickmath;

CREATE TABLE `players` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `username` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT 'unknown',
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `gender` enum('male','female','other') COLLATE utf8mb4_unicode_ci DEFAULT 'male',
  `avatar_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT 'https://tse1.mm.bing.net/th/id/OIP.pLa0MvBoBWBLYBwKtdbLhQAAAA?rs=1&pid=ImgDetMain&o=7&rm=3',
  `country_code` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT 'vn',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `status` enum('online','in_game','offline') COLLATE utf8mb4_unicode_ci DEFAULT 'offline',
  `last_active_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  KEY `ix_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS matches (
    id CHAR(36) NOT NULL PRIMARY KEY,   -- UUID cho trận
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME NULL,
    ended_at DATETIME NULL,
    total_rounds INT NOT NULL DEFAULT 0, -- (optional) số lượt trong trận, nếu cần
    status ENUM('pending','running','finished','cancelled') NOT NULL DEFAULT 'pending',
    INDEX (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS game_history (
    match_id CHAR(36) NOT NULL,
    player_id CHAR(36) NOT NULL,
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at DATETIME NULL,
    final_score INT NOT NULL DEFAULT 0,       -- tổng điểm cuối trận (số điểm thu được)
    total_time BIGINT NOT NULL DEFAULT 0,  -- tổng thời gian chơi (ms) dùng để phân xử khi hòa
    result ENUM('win','lose','draw') DEFAULT NULL, -- kết quả trận cho người này (đặt khi trận kết thúc)
    PRIMARY KEY (match_id, player_id),
    CONSTRAINT fk_gp_match FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_gp_player FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX ix_gp_match (match_id),
    INDEX ix_gp_player (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;