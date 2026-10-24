package com.bfgtools.calibration.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WriteAccessPolicyTest {
    @Test public void nPrefixIsReadOnly() {
        assertTrue(WriteAccessPolicy.isReadOnlySerial("N1234567890123"));
        assertTrue(WriteAccessPolicy.isReadOnlySerial(" n1234567890123 "));
        assertFalse(WriteAccessPolicy.isReadOnlySerial("J3222"));
        assertFalse(WriteAccessPolicy.isReadOnlySerial(""));
    }

    @Test public void unknownMeterNeedsExtraWarning() {
        assertFalse(WriteAccessPolicy.needsMeterCompatibilityWarning(0x0286));
        assertFalse(WriteAccessPolicy.needsMeterCompatibilityWarning(0x0429));
        assertTrue(WriteAccessPolicy.needsMeterCompatibilityWarning(0x0285));
        assertTrue(WriteAccessPolicy.needsMeterCompatibilityWarning(0x0428));
        assertTrue(WriteAccessPolicy.needsMeterCompatibilityWarning(-1));
    }
}
