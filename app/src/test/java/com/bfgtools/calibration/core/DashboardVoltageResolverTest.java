package com.bfgtools.calibration.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DashboardVoltageResolverTest {
    @Test
    public void resolvesConfirmed60VoltCapture() {
        DashboardVoltageResolver.Reading reading =
                DashboardVoltageResolver.resolve(1075, 17929);

        assertTrue(reading.isKnown());
        assertEquals(60, reading.nominalVoltage);
        assertEquals(59.96, reading.calculatedVoltage, 0.02);
    }

    @Test
    public void resolvesConfirmed72VoltCapture() {
        DashboardVoltageResolver.Reading reading =
                DashboardVoltageResolver.resolve(1310, 18200);

        assertTrue(reading.isKnown());
        assertEquals(72, reading.nominalVoltage);
        assertEquals(71.98, reading.calculatedVoltage, 0.02);
    }

    @Test
    public void rejectsMissingOrImplausibleValues() {
        assertFalse(DashboardVoltageResolver.resolve(-1, 18200).isKnown());
        assertFalse(DashboardVoltageResolver.resolve(1310, 0).isKnown());
        assertFalse(DashboardVoltageResolver.resolve(1310, 5000).isKnown());
    }
}
