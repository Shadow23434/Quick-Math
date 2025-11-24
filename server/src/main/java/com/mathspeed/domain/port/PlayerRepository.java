package com.mathspeed.domain.port;

import com.mathspeed.domain.model.Player;
import java.util.List;

public interface PlayerRepository {
    boolean insertPlayer(Player player) throws Exception;
    String hashPassword(String password) throws Exception;
    boolean checkPassword(String plain, String hashed) throws Exception;
    boolean changePassword(String username, String newPassword) throws Exception;
    Player findPlayer(String username, String password) throws Exception;
    void updateStatus(String username, String status) throws Exception;
    Player getPlayerById(String id) throws Exception;
    boolean existsByUsername(String username) throws Exception;
    boolean existsById(String id) throws Exception;
    List<Player> searchPlayers(String keyword, String excludePlayerId) throws Exception;
    List<Player> getAllPlayers(String excludePlayerId) throws Exception;
    List<Player> getOnlinePlayers(String excludePlayerId) throws Exception;
    int getTotalPlayers() throws Exception;
}
