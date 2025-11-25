package com.mathspeed.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mathspeed.model.Player;
import com.mathspeed.model.RoundResult;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.io.StringReader;
import java.util.*;

/**
 * MatchResultController using com.mathspeed.model.Player as player model.
 *
 * - Populate UI with server JSON (GAME_OVER / ROUND_RESULT / GAME_END).
 * - Shows: winner/tie, each player's correct count and total play time.
 * - Usage: load FXML, obtain controller, then call populateFromJson(serverJson).
 */
public class MatchResultController {

    @FXML private Label winnerLabel;
    @FXML private Label summaryLabel;

    @FXML private Label playerAName;
    @FXML private Label playerAScore;
    @FXML private Label playerATime;

    @FXML private Label playerBName;
    @FXML private Label playerBScore;
    @FXML private Label playerBTime;

    @FXML private Button homeButton;
    // optional callback if host app wants to handle navigation
    private Runnable onBackToHome;

    public void setOnBackToHome(Runnable r) { this.onBackToHome = r; }

    @FXML
    private void onBackToHome() {
        if (onBackToHome != null) onBackToHome.run();
    }

    /**
     * Populate UI with server JSON (GAME_OVER / ROUND_RESULT / GAME_END).
     * Shows: winner/tie, each player's correct count and total play time.
     */
    public void populateFromJson(String json) {
        if (json == null || json.trim().isEmpty()) return;
        Platform.runLater(() -> {
            try {
                JsonElement je = JsonParser.parseReader(new StringReader(json));
                if (!je.isJsonObject()) {
                    showErrorState("Dữ liệu không hợp lệ");
                    return;
                }
                JsonObject jo = je.getAsJsonObject();

                // Extract players into model.Player list if present
                List<Player> players = new ArrayList<>();
                if (jo.has("players") && jo.get("players").isJsonArray()) {
                    JsonArray parr = jo.getAsJsonArray("players");
                    for (JsonElement pel : parr) {
                        if (!pel.isJsonObject()) continue;
                        JsonObject p = pel.getAsJsonObject();
                        Player pl = new Player();
                        pl.setId(safeGet(p, "id"));
                        pl.setUsername(safeGet(p, "username"));
                        String dn = safeGet(p, "display_name");
                        if (dn == null || dn.isEmpty()) dn = pl.getUsername();
                        pl.setDisplayName(dn);
                        pl.setAvatarUrl(safeGet(p, "avatar_url"));
                        pl.setCountryCode(safeGet(p, "country_code"));
                        players.add(pl);
                    }
                }

                // Scores and times mapping
                Map<String, Long> scoresMap = new LinkedHashMap<>();
                Map<String, Long> timesMap = new HashMap<>();
                if (jo.has("scores") && jo.get("scores").isJsonObject()) {
                    for (var e : jo.getAsJsonObject("scores").entrySet()) {
                        try { scoresMap.put(e.getKey(), e.getValue().getAsLong()); } catch (Exception ignored) {}
                    }
                }
                if (jo.has("total_play_time_ms") && jo.get("total_play_time_ms").isJsonObject()) {
                    for (var e : jo.getAsJsonObject("total_play_time_ms").entrySet()) {
                        try { timesMap.put(e.getKey(), e.getValue().getAsLong()); } catch (Exception ignored) {}
                    }
                }

                // If players array not present, infer from scoresMap order (create Player models with id only)
                if (players.isEmpty()) {
                    int i = 0;
                    for (var entry : scoresMap.entrySet()) {
                        Player p = new Player();
                        p.setId(entry.getKey());
                        p.setDisplayName("Người chơi " + (i == 0 ? "A" : "B"));
                        players.add(p);
                        i++;
                        if (players.size() >= 2) break;
                    }
                }

                // Ensure exactly two players (pad if necessary)
                while (players.size() < 2) players.add(new Player());

                Player A = players.get(0);
                Player B = players.get(1);

                // Assign scores/times by id if available; otherwise assign by order of scoresMap
                if (A.getId() != null && scoresMap.containsKey(A.getId())) {
                    // nothing else
                }
                if (B.getId() != null && scoresMap.containsKey(B.getId())) {
                    // nothing else
                }

                // If ids present, extract scores
                if (A.getId() != null && scoresMap.containsKey(A.getId())) {
                    try { AScoreSet(A, scoresMap.get(A.getId())); } catch (Exception ignored) {}
                }
                if (B.getId() != null && scoresMap.containsKey(B.getId())) {
                    try { AScoreSet(B, scoresMap.get(B.getId())); } catch (Exception ignored) {}
                }

                // If ids missing or scoresMap wasn't keyed by these ids, assign by order
                if ((A.getId() == null || !scoresMap.containsKey(A.getId())) ||
                        (B.getId() == null || !scoresMap.containsKey(B.getId()))) {
                    int idx = 0;
                    for (var entry : scoresMap.entrySet()) {
                        if (idx == 0) AScoreSet(A, entry.getValue());
                        else if (idx == 1) AScoreSet(B, entry.getValue());
                        idx++;
                    }
                }

                // Assign times by id if available
                if (A.getId() != null && timesMap.containsKey(A.getId())) {
                    ASetTime(A, timesMap.get(A.getId()));
                }
                if (B.getId() != null && timesMap.containsKey(B.getId())) {
                    ASetTime(B, timesMap.get(B.getId()));
                }

                // Determine winner: prefer explicit "winner" field, else compare scores
                String winnerId = null;
                if (jo.has("winner") && !jo.get("winner").isJsonNull()) winnerId = safeGet(jo, "winner");
                if (winnerId == null || winnerId.isEmpty()) {
                    if (jo.has("round_winner") && !jo.get("round_winner").isJsonNull()) winnerId = safeGet(jo, "round_winner");
                }

                boolean tie = false;
                String winnerName = null;
                long aScore = getScore(A);
                long bScore = getScore(B);
                if (winnerId != null && !winnerId.isEmpty()) {
                    if (A.getId() != null && A.getId().equals(winnerId)) winnerName = displayNameOrFallback(A);
                    else if (B.getId() != null && B.getId().equals(winnerId)) winnerName = displayNameOrFallback(B);
                } else {
                    if (aScore == bScore) tie = true;
                    else winnerName = (aScore > bScore) ? displayNameOrFallback(A) : displayNameOrFallback(B);
                }

                // Update UI
                if (tie) {
                    winnerLabel.setText("Hòa");
                    summaryLabel.setText(String.format("%s %d - %d %s", displayNameOrFallback(A), aScore, bScore, displayNameOrFallback(B)));
                } else {
                    winnerLabel.setText("Người chiến thắng: " + (winnerName != null ? winnerName : "—"));
                    summaryLabel.setText(String.format("%s %d - %d %s", displayNameOrFallback(A), aScore, bScore, displayNameOrFallback(B)));
                }

                playerAName.setText(displayNameOrFallback(A));
                playerAScore.setText(String.valueOf(aScore));
                playerATime.setText(formatMillis(getTotalTimeMs(A)));

                playerBName.setText(displayNameOrFallback(B));
                playerBScore.setText(String.valueOf(bScore));
                playerBTime.setText(formatMillis(getTotalTimeMs(B)));

            } catch (Exception ex) {
                showErrorState("Không thể đọc dữ liệu kết quả");
                ex.printStackTrace();
            }
        });
    }

    // Helpers to attach score/time to Player via a simple Map inside the Player object is not present
    // so we store these values in local maps using player's id; to keep simple, we'll store values in transient maps.
    // But because Player model doesn't have score/time fields, we'll use temporary maps here.
    // For simplicity in this controller we keep two maps keyed by Player instance identity (not ideal but acceptable here).
    private final Map<Player, Long> scoreByPlayer = new IdentityHashMap<>();
    private final Map<Player, Long> timeByPlayer = new IdentityHashMap<>();

    private void AScoreSet(Player p, long score) {
        scoreByPlayer.put(p, score);
    }

    private void ASetTime(Player p, long ms) {
        timeByPlayer.put(p, ms);
    }

    private long getScore(Player p) {
        return scoreByPlayer.getOrDefault(p, 0L);
    }

    private long getTotalTimeMs(Player p) {
        return timeByPlayer.getOrDefault(p, 0L);
    }

    private String displayNameOrFallback(Player p) {
        if (p == null) return "Người chơi";
        if (p.getDisplayName() != null && !p.getDisplayName().isEmpty()) return p.getDisplayName();
        if (p.getUsername() != null && !p.getUsername().isEmpty()) return p.getUsername();
        return "Người chơi";
    }

    private void showErrorState(String msg) {
        winnerLabel.setText("Kết quả");
        summaryLabel.setText(msg);
        playerAName.setText("-");
        playerAScore.setText("-");
        playerATime.setText("-");
        playerBName.setText("-");
        playerBScore.setText("-");
        playerBTime.setText("-");
    }

    private static String safeGet(JsonObject o, String key) {
        try {
            if (o != null && o.has(key) && !o.get(key).isJsonNull()) return o.get(key).getAsString();
        } catch (Exception ignored) {}
        return null;
    }

    private static String formatMillis(long ms) {
        if (ms <= 0) return "00:00";
        long totalSec = ms / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return String.format("%02d:%02d", min, sec);
    }

    public void populateFromRoundResult(RoundResult rr) {
        if (rr == null) return;
        Platform.runLater(() -> {
            try {
                List<RoundResult.PlayerResult> players = rr.players;
                RoundResult.PlayerResult A = players != null && players.size() > 0 ? players.get(0) : null;
                RoundResult.PlayerResult B = players != null && players.size() > 1 ? players.get(1) : null;

                String winnerText = "—";
                if (rr.round_winner != null && !rr.round_winner.isEmpty()) {
                    if (A != null && rr.round_winner.equals(A.id)) winnerText = A.username;
                    else if (B != null && rr.round_winner.equals(B.id)) winnerText = B.username;
                    else winnerText = rr.round_winner;
                } else {
                    int aScore = A != null ? A.total_score : 0;
                    int bScore = B != null ? B.total_score : 0;
                    if (aScore > bScore) winnerText = A != null ? A.username : "Người chiến thắng";
                    else if (bScore > aScore) winnerText = B != null ? B.username : "Người chiến thắng";
                    else winnerText = "Hòa";
                }

                winnerLabel.setText("Người chiến thắng: " + winnerText);

                String aName = A != null ? A.username : "Người chơi A";
                String bName = B != null ? B.username : "Người chơi B";
                int aScore = A != null ? A.total_score : 0;
                int bScore = B != null ? B.total_score : 0;
                summaryLabel.setText(String.format("%s %d - %d %s", aName, aScore, bScore, bName));

                playerAName.setText(aName);
                playerAScore.setText(String.valueOf(aScore));
                playerATime.setText(formatMillis(A != null ? A.total_play_time_ms : 0L));

                playerBName.setText(bName);
                playerBScore.setText(String.valueOf(bScore));
                playerBTime.setText(formatMillis(B != null ? B.total_play_time_ms : 0L));
            } catch (Exception ex) {
                // on error, set simple fallback
                winnerLabel.setText("Kết quả");
                summaryLabel.setText("Không thể hiển thị kết quả");
                playerAName.setText("-");
                playerAScore.setText("-");
                playerATime.setText("-");
                playerBName.setText("-");
                playerBScore.setText("-");
                playerBTime.setText("-");
                ex.printStackTrace();
            }
        });
    }
}