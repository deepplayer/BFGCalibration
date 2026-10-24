package com.bfgtools.calibration.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BfgProfileVoltageTest {
    @Test public void profileVoltageOrderMatchesObservedVehicleProfiles() {
        assertEquals(72, BfgProfileCatalog.nominalVoltage(0x20));
        assertEquals(60, BfgProfileCatalog.nominalVoltage(0x21));
        assertEquals(48, BfgProfileCatalog.nominalVoltage(0x22));
    }
}
