package com.mathspeed.adapter.network.protocol;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
public class Message {
    private MessageType type;
    private Object payload;
    private long timestamp;
    public Message() {
        this.timestamp = System.currentTimeMillis();
    }
    public Message(MessageType type, Object payload) {
        this.type = type;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    private static final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").create();
    public String toJson() { return gson.toJson(this); }
    public static Message fromJson(String json) { return gson.fromJson(json, Message.class); }
    @Override
    public String toString() {
        return "Message{type=" + type + ", payload=" + payload + ", timestamp=" + timestamp + '}';
    }
}
