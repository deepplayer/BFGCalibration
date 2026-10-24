package com.bfgtools.calibration.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class DisVoltageConfigTest {
    @Test public void knownFamiliesKeepTheirHighNibble() {
        assertEquals(0xC1, DisVoltageConfig.target(0xC2, 72));
        assertEquals(0xC2, DisVoltageConfig.target(0xC1, 60));
        assertEquals(0x51, DisVoltageConfig.target(0x52, 72));
        assertEquals(0x52, DisVoltageConfig.target(0x51, 60));
        assertEquals(0x53, DisVoltageConfig.target(0x51, 48));
    }

    @Test public void unknownFamilyIsComputableButNeedsWarning() {
        assertEquals(0xD1, DisVoltageConfig.target(0xD2, 72));
        assertTrue(DisVoltageConfig.requiresExtraWarning(0xD2, 0xD1));
        assertTrue(DisVoltageConfig.requiresExtraWarning(0xC1, 0xC3));
        assertFalse(DisVoltageConfig.requiresExtraWarning(0x51, 0x53));
    }

    @Test public void unsupportedCurrentValueIsRejected() {
        assertEquals(-1, DisVoltageConfig.nominalVoltage(0xC0));
        assertEquals(-1, DisVoltageConfig.nominalVoltage(0x01C2));
        try {
            DisVoltageConfig.target(0xC0, 72);
            fail("invalid selector must not be written");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void writePacketUsesDashboardDestinationAndTwoByteLittleEndianValue() {
        assertArrayEquals(new byte[] {0x5A, (byte) 0xA5, 0x02, 0x3E, 0x01,
                0x02, (byte) 0x92, (byte) 0xC1, 0x00},
                DisVoltageConfig.writePacket(0xC1));
    }
}
