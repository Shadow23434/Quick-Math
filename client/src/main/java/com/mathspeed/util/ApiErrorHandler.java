package com.mathspeed.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiErrorHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiErrorHandler.class);

    public static final String MSG_MISSING_CREDENTIALS = "Please enter your username and password.";
    public static final String MSG_INVALID_CREDENTIALS = "Username or password is incorrect.";
    public static final String MSG_AUTH_ERROR = "There was an authentication error. Please try again.";
    public static final String MSG_METHOD_NOT_ALLOWED = "System error (Method not allowed). Please contact support.";
    public static final String MSG_UNKNOWN_ERROR = "An unknown error occurred. Please try again later.";
    public static final String MSG_NETWORK_ERROR = "Cannot connect to server. Please check your internet connection.";

    public static String getUserFriendlyMessage(int statusCode, String serverMessage) {
        String msg = (serverMessage != null) ? serverMessage.trim() : "";
        String lowerMsg = msg.toLowerCase();

        logger.debug("Mapping error - Code: {}, Message: {}", statusCode, msg);

        switch (statusCode) {
            case 400:
                if (lowerMsg.contains("missing credentials") || lowerMsg.contains("missing")) {
                    return MSG_MISSING_CREDENTIALS;
                }
                return msg.isEmpty() ? "Invalid input data." : msg;

            case 401:
                // If server provided a short, non-stack-trace message, prefer showing it
                if (!msg.isEmpty() && msg.length() <= 120 && !msg.matches(".*\\b(exception|trace|stack)\\b.*")) {
                    // If server explicitly returned a helpful message (e.g. "Invalid username/password"), show it.
                    if (lowerMsg.contains("invalid username/password")) {
                        return msg;
                    }
                    // Map generic authentication failures to a friendly message
                    if (lowerMsg.contains("authentication failed") || lowerMsg.contains("authentication error") || lowerMsg.contains("invalid")) {
                        return MSG_INVALID_CREDENTIALS;
                    }
                    // Otherwise return the server's short message
                    return msg;
                }
                return MSG_INVALID_CREDENTIALS;

            case 405:
                return MSG_METHOD_NOT_ALLOWED;

            case 500:
                return "Internal Server Error. Please try again later.";

            default:
                if (!msg.isEmpty() && msg.length() <= 120 && !msg.matches(".*\\b(exception|trace|stack)\\b.*")) {
                    return msg;
                }
                return MSG_UNKNOWN_ERROR;
        }
    }

    public static String getNetworkErrorMessage(Throwable ex) {
        logger.error("Network/System exception:", ex);
        return MSG_NETWORK_ERROR;
    }
}