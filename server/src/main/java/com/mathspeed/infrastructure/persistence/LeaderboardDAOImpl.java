package com.mathspeed.infrastructure.persistence;

import com.mathspeed.domain.model.Player;
import com.mathspeed.domain.port.LeaderboardRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class LeaderboardDAOImpl extends BaseDAO implements LeaderboardRepository {

    @Override
    public List<Player> getLeaderboard() throws Exception {
        String sql =
                "SELECT p.id, p.username, p.display_name, p.avatar_url, " +
                        "SUM(CASE WHEN gh.result = 'win' THEN 1 ELSE 0 END) AS wins, " +
                        "COUNT(gh.match_id) AS games_played " +
                        "FROM players p " +
                        "LEFT JOIN game_history gh ON p.id = gh.player_id " +
                        "GROUP BY p.id " +
                        "ORDER BY wins DESC, games_played DESC " ;



        List<Player> leaderboard = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Player p = new Player();
                p.setId(rs.getString("id"));
                p.setUsername(rs.getString("username"));
                p.setDisplayName(rs.getString("display_name"));
                p.setAvatarUrl(rs.getString("avatar_url"));

                p.setWins(rs.getInt("wins"));
                p.setGamesPlayed(rs.getInt("games_played"));

                leaderboard.add(p);
            }
        }


        return leaderboard;
    }
}
