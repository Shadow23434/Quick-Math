package com.mathspeed.dao;

import com.mathspeed.model.Player;

public interface PlayerDAO {

    boolean register(String username, String password);
    String hashPassword(String password);
    Player login(String username, String password);
    void updateLastLogin(String username);
    void logout(String username);
}
