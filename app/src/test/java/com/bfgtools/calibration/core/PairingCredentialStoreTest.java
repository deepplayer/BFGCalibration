package com.bfgtools.calibration.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public final class PairingCredentialStoreTest {
    @Test public void keysStayInSessionMemoryAndAreCopied() {
        String mac = "AA:BB:CC:DD:EE:89";
        byte[] original = new byte[32];
        Arrays.fill(original, (byte) 7);
        assertTrue(PairingCredentialStore.save(null, mac, "TEST0000000001", original));
        original[0] = 9;
        byte[] loaded = PairingCredentialStore.load(null, mac);
        assertNotNull(loaded);
        assertEquals(7, loaded[0]);
        loaded[1] = 2;
        assertEquals(7, PairingCredentialStore.load(null, mac)[1]);
        byte[] expected = new byte[16];
        Arrays.fill(expected, (byte) 7);
        assertArrayEquals(expected, Arrays.copyOf(PairingCredentialStore.load(null, mac), 16));
        assertNull(PairingCredentialStore.load(null, "AA:BB:CC:DD:EE:88"));
    }
}
