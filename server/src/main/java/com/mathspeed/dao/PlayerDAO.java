package com.mathspeed.dao;

import com.mathspeed.model.Player;

public interface PlayerDAO {

    boolean insertPlayer(Player player) throws Exception;
    String hashPassword(String password) throws Exception;
    boolean checkPassword(String plain, String hashed) throws Exception;
    Player findPlayer(String username, String password) throws Exception;
    void updateLastLogin(String username) throws Exception;
}
