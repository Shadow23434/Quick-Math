package com.mathspeed.controller;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mathspeed.model.RoundResult;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

import java.util.List;

/**
 * Controller for match_result.fxml
 *
 * - Added handler for "Trang chủ" button (onBackToHome).
 * - Fixed FXML-related issue "Cannot resolve file 'alice'" by avoiding FXML resource-style strings (leading '@')
 *   and ensuring username/display_name labels are populated at runtime rather than hard-coded in FXML.
 */
public class MatchResultController {

    @FXML private Label winnerLabel;
    @FXML private Label summaryLabel;

    @FXML private Label playerDisplayName;
    @FXML private Label playerUsername;
    @FXML private Label playerScore;
    @FXML private Label playerTotalTime;

    @FXML private Label opponentDisplayName;
    @FXML private Label opponentUsername;
    @FXML private Label opponentScore;
    @FXML private Label opponentTotalTime;

    @FXML private ListView<String> roundListView;

    @FXML private Button rematchButton;
    @FXML private Button homeButton;

    @FXML private Label noteLabel;

    private final Gson gson = new Gson();

    // callbacks (set by caller)
    private Runnable onClose;
    private Runnable onBackToLobby;
    private Runnable onRematch;
    private Runnable onBackToHome;

    @FXML
    public void initialize() {
        // default UI state (leave fields blank to be filled at runtime)
        winnerLabel.setText("Kết quả trận đấu");
        summaryLabel.setText("");
        roundListView.getItems().clear();
    }

    public void setOnClose(Runnable r) { this.onClose = r; }
    public void setOnBackToLobby(Runnable r) { this.onBackToLobby = r; }
    public void setOnRematch(Runnable r) { this.onRematch = r; }
    public void setOnBackToHome(Runnable r) { this.onBackToHome = r; }

    /**
     * Populate from raw server JSON (expects GAME_OVER, ROUND_RESULT or similar payload).
     */
    public void populateFromJson(String json) {
        if (json == null || json.isEmpty()) return;
        try {
            JsonElement je = JsonParser.parseString(json);
            if (!je.isJsonObject()) return;
            JsonObject jo = je.getAsJsonObject();

            // Try to extract player info from "players" array (if present)
            String pAId = null, pAusername = null, pAdisplay = null;
            String pBId = null, pBusername = null, pBdisplay = null;

            JsonElement playersEl = jo.get("players");
            if (playersEl != null && playersEl.isJsonArray() && playersEl.getAsJsonArray().size() >= 2) {
                JsonObject a = playersEl.getAsJsonArray().get(0).isJsonObject() ? playersEl.getAsJsonArray().get(0).getAsJsonObject() : null;
                JsonObject b = playersEl.getAsJsonArray().get(1).isJsonObject() ? playersEl.getAsJsonArray().get(1).getAsJsonObject() : null;
                if (a != null) {
                    pAId = a.has("id") ? a.get("id").getAsString() : null;
                    pAusername = a.has("username") ? a.get("username").getAsString() : null;
                    pAdisplay = a.has("display_name") ? a.get("display_name").getAsString() : pAusername;
                }
                if (b != null) {
                    pBId = b.has("id") ? b.get("id").getAsString() : null;
                    pBusername = b.has("username") ? b.get("username").getAsString() : null;
                    pBdisplay = b.has("display_name") ? b.get("display_name").getAsString() : pBusername;
                }
            }

            // Scores and times
            long pAScore = 0, pBScore = 0;
            long pATime = 0, pBTime = 0;

            JsonObject scoresObj = jo.has("scores") && jo.get("scores").isJsonObject() ? jo.getAsJsonObject("scores") : null;
            JsonObject timesObj = jo.has("total_play_time_ms") && jo.get("total_play_time_ms").isJsonObject() ? jo.getAsJsonObject("total_play_time_ms") : null;

            if (scoresObj != null) {
                if (pAId != null && scoresObj.has(pAId)) pAScore = scoresObj.get(pAId).getAsLong();
                if (pBId != null && scoresObj.has(pBId)) pBScore = scoresObj.get(pBId).getAsLong();
                if (pAId == null || pBId == null) {
                    int i = 0;
                    for (var entry : scoresObj.entrySet()) {
                        if (i == 0) pAScore = entry.getValue().getAsLong();
                        else if (i == 1) pBScore = entry.getValue().getAsLong();
                        i++;
                    }
                }
            }

            if (timesObj != null) {
                if (pAId != null && timesObj.has(pAId)) pATime = timesObj.get(pAId).getAsLong();
                if (pBId != null && timesObj.has(pBId)) pBTime = timesObj.get(pBId).getAsLong();
                if (pAId == null || pBId == null) {
                    int i = 0;
                    for (var entry : timesObj.entrySet()) {
                        if (i == 0) pATime = entry.getValue().getAsLong();
                        else if (i == 1) pBTime = entry.getValue().getAsLong();
                        i++;
                    }
                }
            }

            if (pAusername == null) { pAusername = "Player A"; pAdisplay = pAusername; }
            if (pBusername == null) { pBusername = "Player B"; pBdisplay = pBusername; }

            final String finalPAname = pAdisplay;
            final String finalPAuser = pAusername;
            final String finalPBname = pBdisplay;
            final String finalPBuser = pBusername;
            final long finalPScore = pAScore;
            final long finalPTime = pATime;
            final long finalOScore = pBScore;
            final long finalOTime = pBTime;

            String winnerId = null;
            if (jo.has("round_winner")) winnerId = jo.get("round_winner").isJsonNull() ? null : jo.get("round_winner").getAsString();
            if ((winnerId == null || winnerId.isEmpty()) && jo.has("winner")) winnerId = jo.get("winner").isJsonNull() ? null : jo.get("winner").getAsString();

            final String winnerText;
            if (winnerId != null) {
                if (pAId != null && pAId.equals(winnerId)) winnerText = finalPAname;
                else if (pBId != null && pBId.equals(winnerId)) winnerText = finalPBname;
                else winnerText = "Người chiến thắng";
            } else {
                if (finalPScore > finalOScore) winnerText = finalPAname;
                else if (finalOScore > finalPScore) winnerText = finalPBname;
                else winnerText = "Hòa";
            }

            Platform.runLater(() -> {
                winnerLabel.setText("Người chiến thắng: " + winnerText);
                summaryLabel.setText(finalPAname + " " + finalPScore + " - " + finalOScore + " " + finalPBname);

                playerDisplayName.setText(finalPAname);
                playerUsername.setText(finalPAuser != null ? finalPAuser : "");
                playerScore.setText(String.valueOf(finalPScore));
                playerTotalTime.setText(formatMillis(finalPTime));

                opponentDisplayName.setText(finalPBname);
                opponentUsername.setText(finalPBuser != null ? finalPBuser : "");
                opponentScore.setText(String.valueOf(finalOScore));
                opponentTotalTime.setText(formatMillis(finalOTime));

                roundListView.getItems().clear();
                // If detailed round history exists in payload, populate list; otherwise show placeholder
                if (jo.has("round_history")) {
                    try {
                        JsonElement rh = jo.get("round_history");
                        if (rh.isJsonObject()) {
                            JsonObject rhObj = rh.getAsJsonObject();
                            // example structure: { "playerId": [ {round_index:..., correct:..., ...}, ... ], ... }
                            // We'll attempt to display round-by-round per index using playerA's history if present
                            var entries = rhObj.entrySet();
                            for (var entry : entries) {
                                var arr = entry.getValue().getAsJsonArray();
                                for (JsonElement elem : arr) {
                                    JsonObject r = elem.getAsJsonObject();
                                    int idx = r.has("round_index") ? r.get("round_index").getAsInt() : -1;
                                    boolean correct = r.has("correct") && r.get("correct").getAsBoolean();
                                    long rt = r.has("round_play_time_ms") ? r.get("round_play_time_ms").getAsLong() : 0L;
                                    roundListView.getItems().add("Vòng " + (idx + 1) + " - " + (correct ? "Đúng" : "Sai") + " - " + formatMillis(rt));
                                }
                                break; // only show first player's history to avoid duplication
                            }
                        }
                    } catch (Exception ignore) { /* fall back */ }
                }
                if (roundListView.getItems().isEmpty()) {
                    roundListView.getItems().add("Không có dữ liệu chi tiết vòng.");
                }
            });

        } catch (Exception ex) {
            System.err.println("MatchResultController: failed to populate from json: " + ex.getMessage());
        }
    }

    private static String formatMillis(long ms) {
        if (ms <= 0) return "00:00";
        long totalSec = ms / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return String.format("%02d:%02d", min, sec);
    }

    // ----------------- UI Actions -----------------

    @FXML
    private void onClose() {
        if (onClose != null) onClose.run();
    }

    @FXML
    private void onBackToLobby() {
        if (onBackToLobby != null) onBackToLobby.run();
    }

    @FXML
    private void onRematch() {
        if (onRematch != null) onRematch.run();
    }

    @FXML
    private void onBackToHome() {
        if (onBackToHome != null) {
            onBackToHome.run();
            return;
        }
        // default behavior: call back to lobby if home callback not provided
        if (onBackToLobby != null) onBackToLobby.run();
    }
}