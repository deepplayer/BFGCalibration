package com.bfgtools.calibration.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class RegisterReadPlanTest {
    @Test public void requestsAreReadOnlyAndBoundedToTwoModules() {
        assertArrayEquals(new byte[] {0x5A, (byte) 0xA5, 1, 0x3E, 1, 1, (byte) 0x92, 2},
                RegisterReadPlan.request(RegisterReadPlan.DASHBOARD, 0x92));
        assertArrayEquals(new byte[] {0x5A, (byte) 0xA5, 1, 0x3E, 0x10, 1, 0, 1},
                RegisterReadPlan.request(RegisterReadPlan.METER, 0));
        assertEquals(2, RegisterReadPlan.length(RegisterReadPlan.METER, 0x1C));
        assertFalse(RegisterReadPlan.supports(0x02));
    }

    @Test(expected = IllegalArgumentException.class)
    public void otherControllersCannotBeProbed() {
        RegisterReadPlan.request(0x02, 0x00);
    }
}
