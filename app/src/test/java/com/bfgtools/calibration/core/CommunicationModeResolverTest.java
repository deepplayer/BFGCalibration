package com.bfgtools.calibration.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CommunicationModeResolverTest {
    @Test
    public void standardReadbackUsesRawBfgData() {
        CommunicationModeResolver.Decision decision = CommunicationModeResolver.resolve(
                0x51, 99, 26000, 99, 6660,
                0x0259, 0x0429, -1);

        assertEquals(CommunicationModeResolver.Mode.STANDARD, decision.mode);
        assertEquals(99, decision.resolvedSoc);
        assertEquals(26000, decision.resolvedCapacity);
        assertTrue(decision.writeSupported);
    }

    @Test
    public void firmware286WithZeroCoreUsesScannedCapacity() {
        CommunicationModeResolver.Decision decision = CommunicationModeResolver.resolve(
                0x50, 134, 0, 99, 7576,
                0x0432, 0x0286, 26000);

        assertEquals(CommunicationModeResolver.Mode.CAPACITY_SCAN_COMPAT, decision.mode);
        assertEquals(26000, decision.resolvedCapacity);
        assertTrue(decision.writeSupported);
    }

    @Test
    public void firmware286WithNormalCapacityUsesSameStandardWritePath() {
        CommunicationModeResolver.Decision decision = CommunicationModeResolver.resolve(
                0x50, 99, 26000, 99, 7576,
                0x0432, 0x0286, -1);

        assertEquals(CommunicationModeResolver.Mode.STANDARD, decision.mode);
        assertEquals(26000, decision.resolvedCapacity);
        assertTrue(decision.writeSupported);
    }

    @Test
    public void unknownInvalidCombinationDoesNotEnableWriting() {
        CommunicationModeResolver.Decision decision = CommunicationModeResolver.resolve(
                0x50, 134, 0, 99, 7576,
                0x0433, 0x0285, -1);

        assertEquals(CommunicationModeResolver.Mode.UNSUPPORTED, decision.mode);
        assertEquals(-1, decision.resolvedCapacity);
        assertFalse(decision.writeSupported);
    }

    @Test
    public void firmwareVersionDoesNotCreateCapacityWithoutScanEvidence() {
        CommunicationModeResolver.Decision decision = CommunicationModeResolver.resolve(
                0x50, 134, 0, 99, 7576,
                0x0432, 0x0286, -1);

        assertEquals(CommunicationModeResolver.Mode.UNSUPPORTED, decision.mode);
        assertFalse(decision.writeSupported);
    }

    @Test
    public void unverifiedMeter499UsesFocusedCapacityScanCompatibility() {
        CommunicationModeResolver.Decision decision = CommunicationModeResolver.resolve(
                0x01, 91, 20000, 91, 7793,
                0x0222, 0x0499, 20000);

        assertEquals(CommunicationModeResolver.Mode.CAPACITY_SCAN_COMPAT, decision.mode);
        assertEquals(20000, decision.resolvedCapacity);
        assertTrue(decision.writeSupported);
    }

    @Test
    public void invalidCapacityWithoutScanEvidenceStaysReadOnly() {
        CommunicationModeResolver.Decision decision = CommunicationModeResolver.resolve(
                0x21, 99, 0, 99, 6489,
                0x0259, 0x0200, -1);

        assertEquals(CommunicationModeResolver.Mode.UNSUPPORTED, decision.mode);
        assertFalse(decision.writeSupported);
    }
}
