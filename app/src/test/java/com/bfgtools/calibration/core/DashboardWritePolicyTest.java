package com.bfgtools.calibration.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DashboardWritePolicyTest {
    @Test public void exactCombinationAllowed() {
        assertTrue(DashboardWritePolicy.allows(0x0259, 0x0155, 0x05CA, 0x0429));
    }

    @Test public void everyMismatchOrMissingValueBlocks() {
        assertFalse(DashboardWritePolicy.allows(-1, 0x0155, 0x05CA, 0x0429));
        assertFalse(DashboardWritePolicy.allows(0x0259, -1, 0x05CA, 0x0429));
        assertFalse(DashboardWritePolicy.allows(0x0259, 0x0155, -1, 0x0429));
        assertFalse(DashboardWritePolicy.allows(0x0259, 0x0155, 0x05CA, -1));
        assertFalse(DashboardWritePolicy.allows(0x0432, 0x0155, 0x05CA, 0x0429));
        assertFalse(DashboardWritePolicy.allows(0x0259, 0x0154, 0x05CA, 0x0429));
        assertFalse(DashboardWritePolicy.allows(0x0259, 0x0155, 0x05C9, 0x0429));
        assertFalse(DashboardWritePolicy.allows(0x0259, 0x0155, 0x05CA, 0x0286));
    }
}
