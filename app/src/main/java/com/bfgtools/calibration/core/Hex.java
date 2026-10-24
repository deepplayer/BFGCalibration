package com.bfgtools.calibration.core;

final class Hex {
    private static final char[] H = "0123456789ABCDEF".toCharArray();

    static byte[] decode(String s) {
        if (s == null) throw new IllegalArgumentException("null hex");
        s = s.trim();
        if ((s.length() & 1) != 0) throw new IllegalArgumentException("odd hex length");
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int a = Character.digit(s.charAt(i * 2), 16);
            int b = Character.digit(s.charAt(i * 2 + 1), 16);
            if (a < 0 || b < 0) throw new IllegalArgumentException("bad hex");
            out[i] = (byte) ((a << 4) | b);
        }
        return out;
    }

    static String encode(byte[] b) {
        if (b == null) return "";
        char[] out = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xFF;
            out[i * 2] = H[v >>> 4];
            out[i * 2 + 1] = H[v & 15];
        }
        return new String(out);
    }

    static int u8(byte b) { return b & 0xFF; }
}


