package com.mathspeed.protocol;

public enum MessageType {
    // Authentication
    LOGIN_REQUEST,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    REGISTER_SUCCESS,
    LOGOUT,

    // Connection keepalive
    PING,
    PONG,

    // Lobby
    PLAYER_LIST_UPDATE,

    // Queue / Matchmaking
    JOIN_QUEUE,
    LEAVE_QUEUE,
    QUEUE_JOINED,
    QUEUE_LEFT,

    // Challenge
    CHALLENGE_REQUEST,
    CHALLENGE_SENT,
    CHALLENGE_RECEIVED,
    CHALLENGE_ACCEPTED,
    CHALLENGE_DECLINED,
    CHALLENGE_EXPIRED,
    CHALLENGE_FAILED,
    INFO,

    // Game lifecycle & gameplay (existing)
    GAME_START,
    NEW_QUESTION,
    SUBMIT_ANSWER,
    ANSWER_RESULT,
    GAME_END,
    REMATCH_REQUEST,
    REMATCH_RESPONSE,

    // Game lifecycle & gameplay (new / detailed)
    MATCH_START_INFO,
    NEW_ROUND,
    ROUND_RESULT,
    GAME_OVER,

    // Forfeit / cancel
    FORFEIT_REQUEST,
    FORFEIT_ACK,

    // General
    SERVER_BUSY,
    ERROR,
    DISCONNECT
}