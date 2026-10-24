package com.bfgtools.calibration.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Ninebot Encryption2 helper.
 *
 * Password/session keys are held in memory only. This class never logs them.
 */
final class Encryption2 {
    static final byte[] DATA_BASIC = Hex.decode("97CFB802844143DE56002B3B34780A5D");

    static final class Decoded {
        final byte[] plain;
        final int counter;
        final boolean macOk;
        Decoded(byte[] plain, int counter, boolean macOk) {
            this.plain = plain;
            this.counter = counter;
            this.macOk = macOk;
        }
    }

    private final byte[] initialKey;
    private final byte[] nameKeyMaterial;
    private byte[] sessionKey;
    private byte[] authParam;

    Encryption2(String bluetoothName) throws Exception {
        byte[] name = bluetoothName == null ? new byte[0] : bluetoothName.getBytes(StandardCharsets.US_ASCII);
        this.initialKey = deriveKey(name, DATA_BASIC);
        this.nameKeyMaterial = pad16(name.length <= 16 ? name
                : Arrays.copyOfRange(name, name.length - 16, name.length));
    }

    static byte[] deriveKey(byte[] a, byte[] b) throws Exception {
        byte[] p1 = pad16(a);
        byte[] p2 = pad16(b);
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update(p1);
        sha1.update(p2);
        return Arrays.copyOf(sha1.digest(), 16);
    }

    void establishSession(byte[] password16, byte[] authParam16) throws Exception {
        if (password16 == null || password16.length != 16) throw new IllegalArgumentException("password must be 16 bytes");
        if (authParam16 == null || authParam16.length != 16) throw new IllegalArgumentException("authParam must be 16 bytes");
        this.authParam = authParam16.clone();
        this.sessionKey = deriveKey(password16, authParam16);
    }

    void establishNameSession(byte[] authParam16) throws Exception {
        establishSession(nameKeyMaterial, authParam16);
    }

    byte[] encryptPreComm(byte[] plain) throws Exception {
        if (plain.length < 7 || plain[0] != 0x5A || (plain[1] & 0xFF) != 0xA5) {
            throw new IllegalArgumentException("invalid plain frame");
        }
        byte[] body = Arrays.copyOfRange(plain, 3, plain.length);
        byte[] ks = aes(initialKey, DATA_BASIC);
        byte[] encBody = new byte[body.length];
        for (int i = 0; i < body.length; i++) encBody[i] = (byte) (body[i] ^ ks[i & 15]);

        int sum = 0;
        for (byte x : body) sum = (sum + (x & 0xFF)) & 0xFFFF;
        int checksum = (~sum) & 0xFFFF;

        byte[] out = new byte[3 + encBody.length + 6];
        System.arraycopy(plain, 0, out, 0, 3);
        System.arraycopy(encBody, 0, out, 3, encBody.length);
        int t = 3 + encBody.length;
        out[t] = 0;
        out[t + 1] = 0;
        out[t + 2] = (byte) (checksum & 0xFF);
        out[t + 3] = (byte) ((checksum >>> 8) & 0xFF);
        out[t + 4] = 0;
        out[t + 5] = 0;
        return out;
    }

    byte[] decryptPreComm(byte[] encrypted) throws Exception {
        if (encrypted.length < 9) throw new IllegalArgumentException("short Enc2 frame");
        byte[] body = Arrays.copyOfRange(encrypted, 3, encrypted.length - 6);
        byte[] ks = aes(initialKey, DATA_BASIC);
        byte[] plain = new byte[3 + body.length];
        System.arraycopy(encrypted, 0, plain, 0, 3);
        for (int i = 0; i < body.length; i++) plain[3 + i] = (byte) (body[i] ^ ks[i & 15]);
        return plain;
    }

    byte[] encryptSn(byte[] plain, int counter) throws Exception {
        ensureSession();
        if (counter <= 0 || counter > 0xFFFF) throw new IllegalArgumentException("counter out of range");
        byte[] body = Arrays.copyOfRange(plain, 3, plain.length);
        byte[] cipherBody = cryptCtr(body, counter);
        byte[] encryptedTag = encryptedTag(plain, counter);

        byte[] out = new byte[plain.length + 6];
        System.arraycopy(plain, 0, out, 0, 3);
        System.arraycopy(cipherBody, 0, out, 3, cipherBody.length);
        int t = 3 + cipherBody.length;
        System.arraycopy(encryptedTag, 0, out, t, 4);
        out[t + 4] = (byte) ((counter >>> 8) & 0xFF);
        out[t + 5] = (byte) (counter & 0xFF);
        return out;
    }

    Decoded decryptSn(byte[] encrypted) throws Exception {
        ensureSession();
        if (encrypted.length < 9) throw new IllegalArgumentException("short Enc2 frame");
        int counter = ((encrypted[encrypted.length - 2] & 0xFF) << 8) |
                (encrypted[encrypted.length - 1] & 0xFF);
        byte[] cipherBody = Arrays.copyOfRange(encrypted, 3, encrypted.length - 6);
        byte[] plainBody = cryptCtr(cipherBody, counter);
        byte[] plain = new byte[3 + plainBody.length];
        System.arraycopy(encrypted, 0, plain, 0, 3);
        System.arraycopy(plainBody, 0, plain, 3, plainBody.length);
        byte[] expected = encryptedTag(plain, counter);
        byte[] got = Arrays.copyOfRange(encrypted, encrypted.length - 6, encrypted.length - 2);
        return new Decoded(plain, counter, MessageDigest.isEqual(expected, got));
    }

    private byte[] cryptCtr(byte[] in, int counter) throws Exception {
        byte[] out = new byte[in.length];
        byte[] nonce = nonce(counter);
        int blockIndex = 1;
        for (int off = 0; off < in.length; off += 16, blockIndex++) {
            byte[] a = new byte[16];
            a[0] = 0x01;
            System.arraycopy(nonce, 0, a, 1, 13);
            a[14] = 0;
            a[15] = (byte) (blockIndex & 0xFF);
            byte[] ks = aes(sessionKey, a);
            int n = Math.min(16, in.length - off);
            for (int i = 0; i < n; i++) out[off + i] = (byte) (in[off + i] ^ ks[i]);
        }
        return out;
    }

    private byte[] encryptedTag(byte[] plain, int counter) throws Exception {
        byte[] body = Arrays.copyOfRange(plain, 3, plain.length);
        byte[] nonce = nonce(counter);

        byte[] b0 = new byte[16];
        b0[0] = 0x59;
        System.arraycopy(nonce, 0, b0, 1, 13);
        b0[14] = 0;
        b0[15] = (byte) (body.length & 0xFF);
        byte[] x = aes(sessionKey, b0);

        byte[] aad = new byte[16];
        System.arraycopy(plain, 0, aad, 0, Math.min(3, plain.length));
        x = aes(sessionKey, xor16(x, aad));

        for (int off = 0; off < body.length; off += 16) {
            byte[] block = new byte[16];
            int n = Math.min(16, body.length - off);
            System.arraycopy(body, off, block, 0, n);
            x = aes(sessionKey, xor16(x, block));
        }
        byte[] tag = Arrays.copyOf(x, 4);

        byte[] a0 = new byte[16];
        a0[0] = 0x01;
        System.arraycopy(nonce, 0, a0, 1, 13);
        a0[14] = 0;
        a0[15] = 0;
        byte[] s0 = aes(sessionKey, a0);
        for (int i = 0; i < 4; i++) tag[i] ^= s0[i];
        return tag;
    }

    private byte[] nonce(int counter) {
        byte[] n = new byte[13];
        n[0] = 0;
        n[1] = 0;
        n[2] = (byte) ((counter >>> 8) & 0xFF);
        n[3] = (byte) (counter & 0xFF);
        System.arraycopy(authParam, 0, n, 4, 8);
        n[12] = 0;
        return n;
    }

    private static byte[] aes(byte[] key, byte[] block16) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(block16);
    }

    private static byte[] xor16(byte[] a, byte[] b) {
        byte[] out = new byte[16];
        for (int i = 0; i < 16; i++) out[i] = (byte) (a[i] ^ b[i]);
        return out;
    }

    private static byte[] pad16(byte[] in) {
        byte[] out = new byte[16];
        if (in != null) System.arraycopy(in, 0, out, 0, Math.min(16, in.length));
        return out;
    }

    private void ensureSession() {
        if (sessionKey == null || authParam == null) throw new IllegalStateException("session not established");
    }
}

