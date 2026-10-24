package com.bfgtools.calibration.core;

/** Shared, statically audited BFG Profile table. */
final class BfgProfileCatalog {
    private static final int[] CORE_BY_INDEX = {
            20000, 10500, 18000, 36000,
            10500, 26000, 14000, 38000,
            13000, 22000, 39000, 45000,
            46000, 55000, 52000, 18000
    };

    private BfgProfileCatalog() {
    }

    static int expectedCore(int profile) {
        int value = profile & 0xFF;
        int voltageCode = value & 0x0F;
        int index = (value >> 4) & 0x0F;
        if (voltageCode < 0 || voltageCode > 2) return -1;

        // Confirmed voltage-specific exception in the BFG Profile table.
        if (index == 5 && voltageCode == 2) return 24500;
        return CORE_BY_INDEX[index];
    }

    static int nominalVoltage(int profile) {
        int voltageCode = profile & 0x0F;
        if (voltageCode == 0) return 72;
        if (voltageCode == 1) return 60;
        if (voltageCode == 2) return 48;
        return -1;
    }
}
