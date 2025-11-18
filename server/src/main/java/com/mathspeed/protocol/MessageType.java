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
    NEW_QUESTION,     // legacy / optional: keep if some clients expect it
    SUBMIT_ANSWER,
    ANSWER_RESULT,
    GAME_END,
    REMATCH_REQUEST,
    REMATCH_RESPONSE,

    // Game lifecycle & gameplay (added / recommended)
    MATCH_START_INFO, // contains seed, start_time, countdown_ms, question_count, per_question_seconds
    NEW_ROUND,        // per-round payload (round index, target, deck, time, round_seed...)
    ROUND_RESULT,     // per-round summary (players, scores, times)
    GAME_OVER,        // final game summary (scores, total_play_time_ms, winner, round_history)

    // Forfeit / cancel
    FORFEIT_REQUEST,  // client -> server: player requests/does forfeit while still connected
    FORFEIT_ACK,      // server -> client: acknowledgement / info about forfeit outcome

    // General
    SERVER_BUSY,
    ERROR,
    DISCONNECT
}