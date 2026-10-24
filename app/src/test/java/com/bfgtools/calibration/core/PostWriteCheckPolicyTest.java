package com.bfgtools.calibration.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PostWriteCheckPolicyTest {
    @Test public void mismatchNeverCountsAsConfirmedWrite() {
        assertFalse(PostWriteCheckPolicy.targetMatches(0x51, 0x71, 0x52, -1));
        assertFalse(PostWriteCheckPolicy.targetMatches(0x51, -1, 0x52, 0x51));
        assertTrue(PostWriteCheckPolicy.targetMatches(0x71, 0x71, 0x52, -1));
        assertTrue(PostWriteCheckPolicy.targetMatches(0x51, -1, 0x52, 0x52));
    }

    @Test public void temporaryZeroCapacityRetriesButIsBounded() {
        assertTrue(PostWriteCheckPolicy.capacityPending(28, 0));
        assertFalse(PostWriteCheckPolicy.capacityPending(0, 0));
        assertFalse(PostWriteCheckPolicy.capacityPending(28, 6200));
        assertTrue(PostWriteCheckPolicy.shouldRetry(1, true, true));
        assertTrue(PostWriteCheckPolicy.shouldRetry(2, false, false));
        assertFalse(PostWriteCheckPolicy.shouldRetry(3, true, true));
        assertFalse(PostWriteCheckPolicy.shouldRetry(3, false, false));
    }
}
