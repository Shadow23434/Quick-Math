package com.mathspeed.network;

import com.mathspeed.protocol.Message;

/**
 * Listener interface for inbound network events from NetworkController.
 */
public interface NetworkListener {
    void onConnected();
    void onDisconnected();
    void onMessage(Message message);
    void onError(Exception ex);
}

