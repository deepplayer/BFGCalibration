package com.bfgtools.calibration.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class AuthRepairOfferTest {
    @Test public void offersRepairOnlyForAuthTimeout() {
        assertTrue(AuthRepairOffer.isAuthTimeout("AUTH无回复"));
        assertFalse(AuthRepairOffer.isAuthTimeout("PRE_COMM无回复"));
        assertFalse(AuthRepairOffer.isAuthTimeout("GATT连接失败"));
        assertFalse(AuthRepairOffer.isAuthTimeout("AUTH被车辆拒绝"));
        assertFalse(AuthRepairOffer.isAuthTimeout(null));
    }

    @Test public void directRepairRequiresFullVehicleIdentity() {
        assertTrue(AuthRepairOffer.hasExactVehicleIdentity(
                "AA:BB:CC:DD:EE:FF", "TEST0000000001"));
        assertFalse(AuthRepairOffer.hasExactVehicleIdentity(
                "AA:BB:CC:DD:EE:FF", "SHORT"));
        assertFalse(AuthRepairOffer.hasExactVehicleIdentity(
                "AA:BB:CC:DD:EE:FF", ""));
        assertFalse(AuthRepairOffer.hasExactVehicleIdentity(
                "AA:BB:CC:DD:EE", "TEST0000000001"));
    }
}
