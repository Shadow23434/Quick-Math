package com.mathspeed.application.friend;

import com.mathspeed.domain.model.Player;
import com.mathspeed.domain.port.PlayerRepository;

import java.util.List;

public class FriendService {
    private final PlayerRepository playerRepository;

    public FriendService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public boolean playerExistsById(String id) throws Exception {
        return playerRepository.existsById(id);
    }

    public List<Player> listAllPlayers(String requesterId) throws Exception {
        return playerRepository.getAllPlayers(requesterId);
    }

    public List<Player> listOnlinePlayers(String requesterId) throws Exception {
        return playerRepository.getOnlinePlayers(requesterId);
    }

    public List<Player> searchPlayers(String keyword, String requesterId) throws Exception {
        return playerRepository.searchPlayers(keyword, requesterId);
    }
}
