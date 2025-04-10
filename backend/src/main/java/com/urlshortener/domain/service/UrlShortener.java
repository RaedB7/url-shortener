package com.urlshortener.domain.service;

public class UrlShortener {

    private static final char[] BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final int BASE = 62;

    public String encode(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("ID must be non-negative, got: " + id);
        }
        if (id == 0) {
            return String.valueOf(BASE62[0]);
        }

        StringBuilder sb = new StringBuilder();
        long remaining = id;
        while (remaining > 0) {
            sb.append(BASE62[(int) (remaining % BASE)]);
            remaining /= BASE;
        }
        return sb.reverse().toString();
    }

    public long decode(String code) {
        long result = 0;
        for (int i = 0; i < code.length(); i++) {
            int digit = indexOf(code.charAt(i));
            if (digit < 0) {
                return -1;
            }
            result = result * BASE + digit;
            if (result < 0) {
                return -1;
            }
        }
        return result;
    }

    private int indexOf(char c) {
        for (int i = 0; i < BASE62.length; i++) {
            if (BASE62[i] == c)
                return i;
        }
        return -1;
    }
}
