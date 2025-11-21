package com.mathspeed.protocol;

public enum MessageType {
    // Authentication
    LOGIN_REQUEST,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,

    // Lobby
    PLAYER_LIST_UPDATE,

    // Challenge
    CHALLENGE_REQUEST,
    CHALLENGE_ACCEPT,
    CHALLENGE_REJECT,

    // Game
    GAME_START,
    NEW_QUESTION,
    SUBMIT_ANSWER,
    ANSWER_RESULT,
    GAME_END,
    REMATCH_REQUEST,
    REMATCH_RESPONSE,

    // General
    ERROR,
    DISCONNECT,
}