CREATE DATABASE IF NOT EXISTS quickmath CHARACTER SET utf8mb4 COLLATE=utf8mb4_unicode_ci;
USE quickmath;

CREATE TABLE IF NOT EXISTS users (
    id CHAR(36) NOT NULL PRIMARY KEY,   -- UUID
    username VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(100),
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS games (
    id CHAR(36) NOT NULL PRIMARY KEY,   -- UUID cho trận
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME NULL,
    ended_at DATETIME NULL,
    total_rounds INT NOT NULL DEFAULT 0, -- (optional) số lượt trong trận, nếu cần
    status ENUM('pending','running','finished','cancelled') NOT NULL DEFAULT 'pending',
    INDEX (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS game_players (
    game_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at DATETIME NULL,
    final_score INT NOT NULL DEFAULT 0,       -- tổng điểm cuối trận (số điểm thu được)
    total_time BIGINT NOT NULL DEFAULT 0,  -- tổng thời gian chơi (ms) dùng để phân xử khi hòa
    result ENUM('win','lose','draw') DEFAULT NULL, -- kết quả trận cho người này (đặt khi trận kết thúc)
    PRIMARY KEY (game_id, user_id),
    CONSTRAINT fk_gp_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_gp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX ix_gp_game (game_id),
    INDEX ix_gp_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;