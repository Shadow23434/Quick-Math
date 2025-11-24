package com.mathspeed.util;

import java.security.SecureRandom;

public final class UuidUtil {
    private static final SecureRandom RNG = new SecureRandom();

    private UuidUtil() {}

    public static String randomUuid() {
        byte[] b = new byte[16];
        RNG.nextBytes(b);

        // Set version to 4 -> xxxx0100 in the high nibble of byte 6
        b[6] = (byte) ((b[6] & 0x0f) | 0x40);
        // Set variant to RFC 4122 -> 10xxxxxx in the high bits of byte 8
        b[8] = (byte) ((b[8] & 0x3f) | 0x80);

        return toHex(b, 0, 4) + "-" +
               toHex(b, 4, 2) + "-" +
               toHex(b, 6, 2) + "-" +
               toHex(b, 8, 2) + "-" +
               toHex(b, 10, 6);
    }

    private static String toHex(byte[] b, int off, int len) {
        StringBuilder sb = new StringBuilder(len * 2);
        for (int i = 0; i < len; i++) {
            int v = b[off + i] & 0xFF;
            sb.append(Character.forDigit(v >>> 4, 16));
            sb.append(Character.forDigit(v & 0x0F, 16));
        }
        return sb.toString();
    }
}

