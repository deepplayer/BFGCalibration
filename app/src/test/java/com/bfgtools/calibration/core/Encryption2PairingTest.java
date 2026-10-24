package com.bfgtools.calibration.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class Encryption2PairingTest {
    @Test public void setPasswordUsesNameSessionAnd32BytePayload() throws Exception {
        byte[] challenge = Hex.decode("00112233445566778899AABBCCDDEEFF");
        byte[] password = new byte[32];
        for (int i = 0; i < password.length; i++) password[i] = (byte) (i * 7 + 3);
        Encryption2 crypto = new Encryption2("ABCD1234EFGH56");
        crypto.establishNameSession(challenge);

        byte[] request = new byte[39];
        System.arraycopy(Hex.decode("5AA5203E045C00"), 0, request, 0, 7);
        System.arraycopy(password, 0, request, 7, 32);
        byte[] encrypted = crypto.encryptSn(request, 5);
        assertEquals(45, encrypted.length);
        Encryption2.Decoded decoded = crypto.decryptSn(encrypted);
        assertTrue(decoded.macOk);
        assertEquals(5, decoded.counter);
        assertArrayEquals(request, decoded.plain);

        byte[] accepted = crypto.encryptSn(Hex.decode("5AA500043E5C01"), 7);
        assertTrue(crypto.decryptSn(accepted).macOk);
        assertArrayEquals(Hex.decode("5AA500043E5C01"), crypto.decryptSn(accepted).plain);

        crypto.establishSession(Arrays.copyOf(password, 16), challenge);
        byte[] serial = "ABCD1234EFGH56".getBytes(StandardCharsets.US_ASCII);
        byte[] auth = new byte[21];
        System.arraycopy(Hex.decode("5AA50E3E045D00"), 0, auth, 0, 7);
        System.arraycopy(serial, 0, auth, 7, 14);
        assertTrue(crypto.decryptSn(crypto.encryptSn(auth, 8)).macOk);
    }
}
