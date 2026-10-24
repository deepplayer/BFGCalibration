package com.bfgtools.calibration.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TimedRiskGateTest {
    @Test public void dashboardRequiresThirtySecondsAndCheck() {
        long readyAt = 1000L + TimedRiskGate.DASHBOARD_SECONDS * 1000L;
        assertFalse(TimedRiskGate.canProceed(readyAt - 1, readyAt, true));
        assertFalse(TimedRiskGate.canProceed(readyAt, readyAt, false));
        assertTrue(TimedRiskGate.canProceed(readyAt, readyAt, true));
    }

    @Test public void meterRequiresThreeSecondsAndCheck() {
        long readyAt = 1000L + TimedRiskGate.METER_SECONDS * 1000L;
        assertFalse(TimedRiskGate.canProceed(readyAt - 1, readyAt, true));
        assertFalse(TimedRiskGate.canProceed(readyAt, readyAt, false));
        assertTrue(TimedRiskGate.canProceed(readyAt, readyAt, true));
    }
}
