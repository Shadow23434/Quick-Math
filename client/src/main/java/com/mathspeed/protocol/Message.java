package com.mathspeed.protocol;

public class Message {
    private MessageType type;
    private String from;
    private String to;
    private Object data;
    private long timestamp;

    public Message() {
        this.timestamp = System.currentTimeMillis();
    }

    public Message(MessageType type, String from, Object data) {
        this.type = type;
        this.from = from;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}