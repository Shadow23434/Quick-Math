package com.mathspeed.dao.impl;

import com.mathspeed.dao.BaseDAO;
import com.mathspeed.dao.PlayerDAO;
import com.mathspeed.model.Player;

public class PlayerDAOImpl extends BaseDAO implements PlayerDAO {

    public PlayerDAOImpl(){
        super();
    }

    public boolean register(String username, String password){
        // TODO: Implementation here
        return false;
    }
    public String hashPassword(String password){
        // TODO: Implementation here
        return null;
    }
    public Player login(String username, String password){
        //TODO: Implementation here
        return null;
    }
    public void updateLastLogin(String username){
        // TODO: Implementation here
    }
    public void logout(String username){
        // TODO: Implementation here
    }
}
