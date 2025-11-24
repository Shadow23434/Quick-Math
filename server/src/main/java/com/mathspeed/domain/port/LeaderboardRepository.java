package com.mathspeed.domain.port;

import com.mathspeed.domain.model.Player;
import java.util.List;

public interface LeaderboardRepository {
    List<Player> getLeaderboard() throws Exception;
}
