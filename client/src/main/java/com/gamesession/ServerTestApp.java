package com.gamesession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Simple Swing-based HTTP test client for testing REST endpoints.
 *
 * Requirements: Java 11+ (uses java.net.http.HttpClient)
 *
 * Features:
 * - Base URL + endpoint input
 * - Method chooser (GET/POST/PUT/PATCH/DELETE)
 * - Headers (one per line, key:value)
 * - Raw body text
 * - Timeout (ms)
 * - Pretty-print JSON (simple formatter)
 * - Shows status, response headers, and response body
 *
 * To compile:
 *   javac -d out src/main/java/com/quickmath/ui/TestClientUI.java
 * To run:
 *   java -cp out com.quickmath.ui.TestClientUI
 */
public class ServerTestApp extends JFrame {
    private final JTextField baseUrlField = new JTextField("http://localhost:8080");
    private final JComboBox<String> methodBox = new JComboBox<>(new String[]{"GET", "POST", "PUT", "PATCH", "DELETE"});
    private final JTextField endpointField = new JTextField("/");
    private final JTextArea headersArea = new JTextArea(6, 30);
    private final JTextArea bodyArea = new JTextArea(10, 30);
    private final JTextField timeoutField = new JTextField("8000");
    private final JCheckBox prettyJsonCheck = new JCheckBox("Hiển thị JSON đẹp", true);

    private final JButton sendBtn = new JButton("Gửi yêu cầu");
    private final JButton clearBtn = new JButton("Xóa kết quả");

    private final JTextArea statusArea = new JTextArea(1, 40);
    private final JTextArea respHeadersArea = new JTextArea(8, 40);
    private final JTextArea respBodyArea = new JTextArea(18, 40);

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public ServerTestApp() {
        super("QuickMath — Server Test UI (Java)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        initLayout();
        attachHandlers();
        pack();
        setLocationRelativeTo(null);
    }

    private void initLayout() {
        JPanel main = new JPanel(new BorderLayout(12, 12));
        main.setBorder(new EmptyBorder(12, 12, 12, 12));
        add(main);

        JLabel title = new JLabel("QuickMath — Server Test UI");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        main.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        main.add(center, BorderLayout.CENTER);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: base URL + method
        c.gridx = 0; c.gridy = 0; c.weightx = 1.0;
        center.add(new JLabel("Base URL"), c);
        c.gridx = 1; c.gridy = 0; c.weightx = 3.0;
        center.add(baseUrlField, c);
        c.gridx = 2; c.gridy = 0; c.weightx = 0.5;
        center.add(new JLabel("Method"), c);
        c.gridx = 3; c.gridy = 0; c.weightx = 0.7;
        center.add(methodBox, c);

        // Row 1: endpoint + timeout
        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        center.add(new JLabel("Endpoint (path)"), c);
        c.gridx = 1; c.gridy = 1; c.gridwidth = 2; c.weightx = 3.0;
        center.add(endpointField, c);
        c.gridx = 3; c.gridy = 1; c.gridwidth = 1; c.weightx = 0.7;
        JPanel tpanel = new JPanel(new BorderLayout(6, 0));
        tpanel.add(new JLabel("Timeout (ms)"), BorderLayout.WEST);
        tpanel.add(timeoutField, BorderLayout.CENTER);
        center.add(tpanel, c);

        // Row 2: headers and body labels
        c.gridx = 0; c.gridy = 2; c.weightx = 0;
        center.add(new JLabel("Headers (key:value per line)"), c);
        c.gridx = 1; c.gridy = 2; c.gridwidth = 3; c.weightx = 3.0;
        center.add(new JLabel("Body (raw)"), c);

        // Row 3: headers & body areas
        c.gridx = 0; c.gridy = 3; c.gridwidth = 1; c.weightx = 1;
        JScrollPane hs = new JScrollPane(headersArea);
        center.add(hs, c);
        c.gridx = 1; c.gridy = 3; c.gridwidth = 3; c.weightx = 3.0;
        JScrollPane bs = new JScrollPane(bodyArea);
        center.add(bs, c);

        // Row 4: buttons + pretty checkbox
        c.gridx = 0; c.gridy = 4; c.gridwidth = 4; c.weightx = 0;
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnPanel.add(sendBtn);
        btnPanel.add(clearBtn);
        btnPanel.add(prettyJsonCheck);
        center.add(btnPanel, c);

        // South: results
        JPanel south = new JPanel(new BorderLayout(8, 8));
        main.add(south, BorderLayout.SOUTH);

        JPanel statusPanel = new JPanel(new BorderLayout(6, 6));
        statusPanel.setBorder(BorderFactory.createTitledBorder("Kết quả"));
        JPanel topStatus = new JPanel(new BorderLayout(8, 8));
        topStatus.add(new JLabel("Trạng thái:"), BorderLayout.WEST);
        statusArea.setEditable(false);
        statusArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        topStatus.add(statusArea, BorderLayout.CENTER);
        statusPanel.add(topStatus, BorderLayout.NORTH);

        JPanel split = new JPanel(new GridLayout(1, 2, 8, 8));
        respHeadersArea.setEditable(false);
        respHeadersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        respBodyArea.setEditable(false);
        respBodyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        split.add(new JScrollPane(respHeadersArea));
        split.add(new JScrollPane(respBodyArea));

        statusPanel.add(split, BorderLayout.CENTER);
        south.add(statusPanel, BorderLayout.CENTER);

        // make some initial placeholders
        statusArea.setText("—");
        respHeadersArea.setText("—");
        respBodyArea.setText("—");
    }

    private void attachHandlers() {
        sendBtn.addActionListener(e -> sendRequestAsync());
        clearBtn.addActionListener(e -> {
            statusArea.setText("—");
            respHeadersArea.setText("—");
            respBodyArea.setText("—");
        });

        // Ctrl+Enter in body -> send
        bodyArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "send");
        bodyArea.getActionMap().put("send", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendRequestAsync();
            }
        });
    }

    private Map<String, String> parseHeaders(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        if (text == null || text.trim().isEmpty()) return out;
        String[] lines = text.split("\\r?\\n");
        for (String l : lines) {
            String s = l.trim();
            if (s.isEmpty()) continue;
            int idx = s.indexOf(':');
            if (idx == -1) continue;
            String k = s.substring(0, idx).trim();
            String v = s.substring(idx + 1).trim();
            if (!k.isEmpty()) out.put(k, v);
        }
        return out;
    }

    private boolean looksLikeJson(String s) {
        if (s == null) return false;
        String t = s.trim();
        return t.startsWith("{") || t.startsWith("[");
    }

    private void sendRequestAsync() {
        sendBtn.setEnabled(false);
        statusArea.setText("Đang gửi...");
        respHeadersArea.setText("—");
        respBodyArea.setText("—");

        String base = baseUrlField.getText().trim();
        String path = endpointField.getText().trim();
        if (base.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập Base URL", "Lỗi", JOptionPane.ERROR_MESSAGE);
            sendBtn.setEnabled(true);
            return;
        }
        URI uri;
        try {
            uri = URI.create(new java.net.URL(new java.net.URL(base), path).toString());
        } catch (Exception ex) {
            statusArea.setText("URL không hợp lệ: " + ex.getMessage());
            sendBtn.setEnabled(true);
            return;
        }

        String method = (String) methodBox.getSelectedItem();
        Map<String, String> headers = parseHeaders(headersArea.getText());
        int timeoutMs = 8000;
        try {
            timeoutMs = Integer.parseInt(timeoutField.getText().trim());
            if (timeoutMs <= 0) timeoutMs = 8000;
        } catch (Exception ignored) {}

        String body = bodyArea.getText();
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .method(method, HttpRequest.BodyPublishers.noBody());

        // If method accepts body, set body
        if (List.of("POST", "PUT", "PATCH", "DELETE").contains(method)) {
            if (body != null && !body.isEmpty()) {
                // if Content-Type not set and body looks like JSON -> set application/json
                boolean hasCT = headers.keySet().stream().anyMatch(k -> k.equalsIgnoreCase("Content-Type"));
                if (!hasCT && looksLikeJson(body)) {
                    headers.put("Content-Type", "application/json");
                }
                rb = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofMillis(timeoutMs))
                        .method(method, HttpRequest.BodyPublishers.ofString(body));
            } else {
                rb = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofMillis(timeoutMs))
                        .method(method, HttpRequest.BodyPublishers.noBody());
            }
        }

        // attach headers
        headers.forEach(rb::header);

        HttpRequest req = rb.build();

        CompletableFuture<HttpResponse<String>> future = client.sendAsync(req, HttpResponse.BodyHandlers.ofString());
        future.whenComplete((resp, err) -> {
            SwingUtilities.invokeLater(() -> {
                sendBtn.setEnabled(true);
                if (err != null) {
                    String msg = err.getMessage();
                    statusArea.setText("Lỗi: " + (msg == null ? err.toString() : msg));
                    respHeadersArea.setText("—");
                    respBodyArea.setText("—");
                } else {
                    statusArea.setText(resp.statusCode() + " " + resp.version());
                    StringBuilder hsb = new StringBuilder();
                    resp.headers().map().forEach((k, vs) -> {
                        for (String v : vs) {
                            hsb.append(k).append(": ").append(v).append("\n");
                        }
                    });
                    respHeadersArea.setText(hsb.length() == 0 ? "—" : hsb.toString());

                    String raw = resp.body();
                    String ct = resp.headers().firstValue("content-type").orElse("");
                    String pretty = raw;
                    if (prettyJsonCheck.isSelected() && (ct.contains("application/json") || looksLikeJson(raw))) {
                        try {
                            pretty = prettyPrintJson(raw);
                        } catch (Exception ex) {
                            // fallback to raw
                            pretty = raw;
                        }
                    }
                    respBodyArea.setText(pretty == null || pretty.isEmpty() ? "—" : pretty);
                }
            });
        });
    }

    /**
     * A small JSON pretty-printer that attempts to format JSON without external libs.
     * It attempts to insert newlines and indentation while respecting quoted strings.
     *
     * This is not a full JSON parser, but it works for typical well-formed JSON.
     */
    private String prettyPrintJson(String json) {
        if (json == null) return "";
        String s = json.trim();
        if (s.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        int indent = 0;
        boolean inQuotes = false;
        boolean escape = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (escape) {
                out.append(ch);
                escape = false;
                continue;
            }
            if (ch == '\\') {
                out.append(ch);
                escape = true;
                continue;
            }
            if (ch == '"') {
                out.append(ch);
                inQuotes = !inQuotes;
                continue;
            }
            if (inQuotes) {
                out.append(ch);
                continue;
            }
            switch (ch) {
                case '{', '[' -> {
                    out.append(ch);
                    out.append('\n');
                    indent++;
                    appendIndent(out, indent);
                }
                case '}', ']' -> {
                    out.append('\n');
                    indent = Math.max(0, indent - 1);
                    appendIndent(out, indent);
                    out.append(ch);
                }
                case ',' -> {
                    out.append(ch);
                    out.append('\n');
                    appendIndent(out, indent);
                }
                case ':' -> {
                    out.append(ch).append(' ');
                }
                default -> {
                    if (Character.isWhitespace(ch)) {
                        // skip
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        return out.toString();
    }

    private void appendIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) sb.append("  ");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ServerTestApp ui = new ServerTestApp();
            ui.setVisible(true);
        });
    }
}