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

    // Map from player id -> display name provided either by JSON players array or injected by caller
    private Map<String, String> idToDisplayName = new HashMap<>();

    /**
     * Allow host to inject mapping id -> display name (useful when server result JSON doesn't include players[]).
     */
    public void setIdToDisplayName(Map<String, String> map) {
        if (map == null) return;
        this.idToDisplayName.clear();
        this.idToDisplayName.putAll(map);
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

                // Extract players into model.Player list if present and populate idToDisplayName
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
                        if (pl.getId() != null && dn != null) {
                            idToDisplayName.put(pl.getId(), dn);
                        }
                    }
                }

                // Scores and times mapping keyed by player id
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
                        // prefer display name injected via idToDisplayName if available
                        String dn = idToDisplayName.get(entry.getKey());
                        if (dn == null || dn.isEmpty()) dn = "Người chơi " + (i == 0 ? "A" : "B");
                        p.setDisplayName(dn);
                        players.add(p);
                        i++;
                        if (players.size() >= 2) break;
                    }
                }

                // Ensure exactly two players (pad if necessary)
                while (players.size() < 2) players.add(new Player());

                Player A = players.get(0);
                Player B = players.get(1);

                // Use id-keyed maps for scores and times
                // Populate local score/time maps
                scoreById.clear();
                timeById.clear();

                // Use scoresMap directly (if scoresMap keys are ids)
                int idx = 0;
                for (var entry : scoresMap.entrySet()) {
                    String id = entry.getKey();
                    long val = entry.getValue();
                    // if id matches A or B use that id, else if A/B have null id, assign by order
                    if (A.getId() != null && A.getId().equals(id)) scoreById.put(id, val);
                    else if (B.getId() != null && B.getId().equals(id)) scoreById.put(id, val);
                    else {
                        // fallback: assign by order if A/B ids not matched
                        if (idx == 0 && A.getId() != null) scoreById.put(A.getId(), val);
                        else if (idx == 0 && A.getId() == null) {
                            // assign to generated A (use its id if any)
                        } else if (idx == 1 && B.getId() != null) scoreById.put(B.getId(), val);
                        idx++;
                    }
                }

                // If direct mapping above didn't fill (e.g. A/B ids null), assign by order to A/B instances via their ids (may be null)
                if (!scoresMap.isEmpty()) {
                    idx = 0;
                    for (var entry : scoresMap.entrySet()) {
                        if (idx == 0) {
                            if (A.getId() != null) scoreById.put(A.getId(), entry.getValue());
                        } else if (idx == 1) {
                            if (B.getId() != null) scoreById.put(B.getId(), entry.getValue());
                        }
                        idx++;
                    }
                }

                // Assign times by id
                for (var e : timesMap.entrySet()) {
                    timeById.put(e.getKey(), e.getValue());
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
                    if (winnerLabel != null) winnerLabel.setText("Hòa");
                    if (summaryLabel != null) summaryLabel.setText(String.format("%s %d - %d %s", displayNameOrFallback(A), aScore, bScore, displayNameOrFallback(B)));
                } else {
                    if (winnerLabel != null) winnerLabel.setText("Người chiến thắng: " + (winnerName != null ? winnerName : "—"));
                    if (summaryLabel != null) summaryLabel.setText(String.format("%s %d - %d %s", displayNameOrFallback(A), aScore, bScore, displayNameOrFallback(B)));
                }

                if (playerAName != null) playerAName.setText(displayNameOrFallback(A));
                if (playerAScore != null) playerAScore.setText(String.valueOf(aScore));
                if (playerATime != null) playerATime.setText(formatMillis(getTotalTimeMs(A)));

                if (playerBName != null) playerBName.setText(displayNameOrFallback(B));
                if (playerBScore != null) playerBScore.setText(String.valueOf(bScore));
                if (playerBTime != null) playerBTime.setText(formatMillis(getTotalTimeMs(B)));

            } catch (Exception ex) {
                showErrorState("Không thể đọc dữ liệu kết quả");
                ex.printStackTrace();
            }
        });
    }

    // Use id-keyed maps for score/time to avoid instance-identity issues
    private final Map<String, Long> scoreById = new HashMap<>();
    private final Map<String, Long> timeById = new HashMap<>();

    private void AScoreSet(Player p, long score) {
        if (p != null && p.getId() != null) scoreById.put(p.getId(), score);
    }

    private void ASetTime(Player p, long ms) {
        if (p != null && p.getId() != null) timeById.put(p.getId(), ms);
    }

    private long getScore(Player p) {
        if (p != null && p.getId() != null) return scoreById.getOrDefault(p.getId(), 0L);
        return 0L;
    }

    private long getTotalTimeMs(Player p) {
        if (p != null && p.getId() != null) return timeById.getOrDefault(p.getId(), 0L);
        return 0L;
    }

    private String displayNameOrFallback(Player p) {
        if (p == null) return "Người chơi";
        if (p.getId() != null && idToDisplayName.containsKey(p.getId())) {
            String dn = idToDisplayName.get(p.getId());
            if (dn != null && !dn.isEmpty()) return dn;
        }
        if (p.getDisplayName() != null && !p.getDisplayName().isEmpty()) return p.getDisplayName();
        if (p.getUsername() != null && !p.getUsername().isEmpty()) return p.getUsername();
        return "Người chơi";
    }

    private void showErrorState(String msg) {
        if (winnerLabel != null) winnerLabel.setText("Kết quả");
        if (summaryLabel != null) summaryLabel.setText(msg);
        if (playerAName != null) playerAName.setText("-");
        if (playerAScore != null) playerAScore.setText("-");
        if (playerATime != null) playerATime.setText("-");
        if (playerBName != null) playerBName.setText("-");
        if (playerBScore != null) playerBScore.setText("-");
        if (playerBTime != null) playerBTime.setText("-");
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

                // prefer display_name mapping (if caller supplied id->display_name) when possible
                String aName = "Người chơi A";
                String bName = "Người chơi B";
                if (A != null) {
                    if (A.id != null && idToDisplayName.containsKey(A.id)) aName = idToDisplayName.get(A.id);
                    else if (A.username != null && !A.username.isEmpty()) aName = A.username;
                }
                if (B != null) {
                    if (B.id != null && idToDisplayName.containsKey(B.id)) bName = idToDisplayName.get(B.id);
                    else if (B.username != null && !B.username.isEmpty()) bName = B.username;
                }

                String winnerText = "—";
                if (rr.round_winner != null && !rr.round_winner.isEmpty()) {
                    if (A != null && rr.round_winner.equals(A.id)) winnerText = aName;
                    else if (B != null && rr.round_winner.equals(B.id)) winnerText = bName;
                    else winnerText = rr.round_winner;
                } else {
                    int aScore = A != null ? A.total_score : 0;
                    int bScore = B != null ? B.total_score : 0;
                    if (aScore > bScore) winnerText = aName;
                    else if (bScore > aScore) winnerText = bName;
                    else winnerText = "Hòa";
                }

                if (winnerLabel != null) winnerLabel.setText("Người chiến thắng: " + winnerText);

                int aScore = A != null ? A.total_score : 0;
                int bScore = B != null ? B.total_score : 0;
                if (summaryLabel != null) summaryLabel.setText(String.format("%s %d - %d %s", aName, aScore, bScore, bName));

                if (playerAName != null) playerAName.setText(aName);
                if (playerAScore != null) playerAScore.setText(String.valueOf(aScore));
                if (playerATime != null) playerATime.setText(formatMillis(A != null ? A.total_play_time_ms : 0L));

                if (playerBName != null) playerBName.setText(bName);
                if (playerBScore != null) playerBScore.setText(String.valueOf(bScore));
                if (playerBTime != null) playerBTime.setText(formatMillis(B != null ? B.total_play_time_ms : 0L));
            } catch (Exception ex) {
                // on error, set simple fallback
                if (winnerLabel != null) winnerLabel.setText("Kết quả");
                if (summaryLabel != null) summaryLabel.setText("Không thể hiển thị kết quả");
                if (playerAName != null) playerAName.setText("-");
                if (playerAScore != null) playerAScore.setText("-");
                if (playerATime != null) playerATime.setText("-");
                if (playerBName != null) playerBName.setText("-");
                if (playerBScore != null) playerBScore.setText("-");
                if (playerBTime != null) playerBTime.setText("-");
                ex.printStackTrace();
            }
        });
    }
}