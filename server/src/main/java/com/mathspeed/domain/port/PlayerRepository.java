package com.mathspeed.domain.port;
import com.mathspeed.domain.model.Player;
public interface PlayerRepository {
    boolean insertPlayer(Player player) throws Exception;
    String hashPassword(String password) throws Exception;
    boolean checkPassword(String plain, String hashed) throws Exception;
    Player findPlayer(String username, String password) throws Exception;
    void updateLastLogin(String username) throws Exception;
}
