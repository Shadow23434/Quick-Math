package com.mathspeed.client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/**
 * Client compatible with server's ClientHandler command tokens:
 * - LOGIN <username> <password>
 * - GUEST <username>
 * - CHALLENGE <targetUsername>
 * - ACCEPT <challengerUsername>
 * - DECLINE <challengerUsername>
 * - SUBMIT_ANSWER <questionId> <answer>
 * - PING / QUIT
 *
 * Usage:
 *   java com.mathspeed.client.MatchChallengeClient <host> <port> <username> <password> [mode] [opponent-if-auto-challenge]
 *
 * Modes:
 *   interactive     -> type commands manually
 *   auto-challenge  -> send CHALLENGE <opponent> after successful login (requires opponent arg)
 *   auto-accept     -> automatically ACCEPT incoming challenges
 *
 * Behavior changes from previous version:
 * - Reader thread is started first so the client can receive server auto-login (LOGIN_SUCCESS|AUTO).
 * - Client no longer exits immediately when receiving LOGIN_FAILED; allows interactive retry or fallback to GUEST.
 * - For auto modes, if no LOGIN_SUCCESS is received shortly after connect, the client will send GUEST <username>
 *   to register as an ephemeral user (so you can test without DB users).
 * - Client will not send duplicate LOGIN/GUEST if server already auto-assigned the username.
 */
public class MatchChallengeClient {
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String mode;
    private final String autoOpponent; // optional opponent for auto-challenge
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private final Scanner console = new Scanner(System.in);

    // state
    private volatile boolean loggedIn = false;
    private volatile boolean loginAttempted = false; // to avoid duplicate attempts

    public MatchChallengeClient(String host, int port, String username, String password, String mode, String autoOpponent) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.mode = mode == null ? "interactive" : mode;
        this.autoOpponent = autoOpponent;
    }

    public void start() throws Exception {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

        System.out.println("Connected as " + username + " to " + host + ":" + port + " mode=" + mode);

        // start reader thread first so client can receive server auto-assign LOGIN_SUCCESS|AUTO
        Thread reader = new Thread(this::readLoop, "client-reader-" + username);
        reader.setDaemon(true);
        reader.start();

        // Wait briefly for server to possibly auto-assign a username (LOGIN_SUCCESS|AUTO).
        // If not received, proceed to attempt login/guest depending on mode.
        long waitUntil = System.currentTimeMillis() + 1000;
        while (!loggedIn && System.currentTimeMillis() < waitUntil) {
            Thread.sleep(50);
        }

        if (!loggedIn) {
            // Not auto-assigned. If mode is auto-* attempt an automatic registration:
            if ("auto-challenge".equalsIgnoreCase(mode) || "auto-accept".equalsIgnoreCase(mode)) {
                // Try login with provided password first if it makes sense (user may have real creds).
                if (password != null && !password.isEmpty()) {
                    attemptLogin();
                    // wait briefly for response
                    long retryUntil = System.currentTimeMillis() + 800;
                    while (!loggedIn && System.currentTimeMillis() < retryUntil) {
                        Thread.sleep(50);
                    }
                }
                // If still not logged in, fallback to ephemeral guest
                if (!loggedIn) {
                    sendRaw("GUEST " + username);
                    // wait briefly for response
                    long retryUntil = System.currentTimeMillis() + 800;
                    while (!loggedIn && System.currentTimeMillis() < retryUntil) {
                        Thread.sleep(50);
                    }
                }
            } else {
                // interactive mode: prompt user to LOGIN or GUEST
                System.out.println("No auto-login from server. To authenticate, type: LOGIN <username> <password> or GUEST <username>");
            }
        }

        // If we're logged in and auto-challenge mode with opponent provided, send challenge now.
        if (loggedIn && "auto-challenge".equalsIgnoreCase(mode) && autoOpponent != null && !autoOpponent.isEmpty()) {
            sendRaw("CHALLENGE " + autoOpponent);
            System.out.println("Sent CHALLENGE " + autoOpponent);
        }

        // interactive loop or wait for reader to finish
        if ("interactive".equalsIgnoreCase(mode)) {
            while (true) {
                String line = console.nextLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                if ("quit".equalsIgnoreCase(line) || "exit".equalsIgnoreCase(line)) {
                    sendRaw("QUIT");
                    break;
                }
                // shorthands
                if (line.startsWith("/challenge ")) {
                    sendRaw("CHALLENGE " + line.substring("/challenge ".length()).trim());
                } else if (line.startsWith("/accept ")) {
                    sendRaw("ACCEPT " + line.substring("/accept ".length()).trim());
                } else if (line.startsWith("/submit ")) {
                    sendRaw("SUBMIT_ANSWER " + line.substring("/submit ".length()).trim());
                } else {
                    sendRaw(line);
                }
            }
            close();
        } else {
            // auto modes: keep main thread alive until socket closed
            reader.join();
        }
    }

    private void attemptLogin() {
        // Only attempt once per connection to avoid duplicate requests
        if (loginAttempted) return;
        loginAttempted = true;
        sendRaw("LOGIN " + username + " " + password);
    }

    private void readLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                System.out.println("[RECV " + username + "] " + line);
                String type = line.split("\\|", 2)[0];

                switch (type) {
                    case "LOGIN_SUCCESS":
                        loggedIn = true;
                        System.out.println("Login succeeded.");
                        break;
                    case "LOGIN_FAILED":
                        System.out.println("Login failed: " + getPayload(line));
                        // do NOT close automatically; allow manual retry or fallback to GUEST (handled in start())
                        break;
                    case "CHALLENGE_SENT":
                    case "CHALLENGE_RECEIVED":
                    case "CHALLENGE_REQUEST":
                        System.out.println("Challenge event: " + line);
                        if ("auto-accept".equalsIgnoreCase(mode)) {
                            // Incoming format may be CHALLENGE_RECEIVED|<from> or CHALLENGE_REQUEST|<from>
                            String payload = getPayload(line);
                            String from = null;
                            if (payload != null && !payload.isEmpty()) {
                                // payload often is just the challenger username
                                String[] pp = payload.split("\\|");
                                from = pp.length > 0 ? pp[0] : null;
                            }
                            if (from == null || from.isEmpty()) {
                                String[] tokens = line.split("[| ]");
                                if (tokens.length >= 2) from = tokens[1];
                            }
                            if (from != null && !from.isEmpty()) {
                                // Wait briefly to ensure server didn't already auto-accept on behalf of this client
                                sendRaw("ACCEPT " + from);
                                System.out.println("Auto-accepted challenge from " + from);
                            }
                        }
                        break;
                    case "CHALLENGE_ACCEPTED":
                        System.out.println("Challenge accepted: " + getPayload(line));
                        break;
                    case "GAME_START":
                        System.out.println("Game start: " + getPayload(line));
                        break;
                    case "NEW_QUESTION":
                        System.out.println("New question: " + getPayload(line));
                        // optionally auto-submit PASS or trivial answer here
                        break;
                    case "ANSWER_RESULT":
                        System.out.println("Answer result: " + getPayload(line));
                        break;
                    case "GAME_END":
                        System.out.println("Game end: " + getPayload(line));
                        break;
                    case "ERROR":
                        System.out.println("Server error: " + getPayload(line));
                        break;
                    default:
                        System.out.println("Other message: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("Read loop error: " + e.getMessage());
        } finally {
            try { close(); } catch (Exception ignored) {}
        }
    }

    private String getPayload(String line) {
        int idx = line.indexOf('|');
        return idx >= 0 ? line.substring(idx + 1) : "";
    }

    private synchronized void sendRaw(String msg) {
        try {
            out.write(msg);
            if (!msg.endsWith("\n")) out.write("\n");
            out.flush();
            System.out.println("[SENT " + username + "] " + msg);
        } catch (IOException e) {
            System.err.println("Send failed: " + e.getMessage());
            try { close(); } catch (Exception ignored) {}
        }
    }

    private synchronized void close() {
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (Exception ignored) {}
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        System.out.println("Client " + username + " closed.");
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage: java com.mathspeed.client.MatchChallengeClient <host> <port> <username> <password> [mode] [opponent-if-auto-challenge]");
            System.out.println("Modes: interactive | auto-challenge | auto-accept");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String username = args[2];
        String password = args[3];
        String mode = args.length >= 5 ? args[4] : "interactive";
        String opponent = args.length >= 6 ? args[5] : null;

        MatchChallengeClient client = new MatchChallengeClient(host, port, username, password, mode, opponent);
        client.start();
    }
}