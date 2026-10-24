package com.bfgtools.calibration.core;

/** Bounded diagnostic reads for the dashboard and the BFG metering module. */
final class RegisterReadPlan {
    static final int DASHBOARD = 0x01;
    static final int METER = 0x10;
    static final int FIRST = 0x00;
    static final int LAST = 0xFF;

    private RegisterReadPlan() { }

    static boolean supports(int module) {
        return module == DASHBOARD || module == METER;
    }

    static int length(int module, int index) {
        if (!supports(module) || index < FIRST || index > LAST)
            throw new IllegalArgumentException("Unsupported register scan target");
        return module == METER && (index == 0x00 || index == 0x02) ? 1 : 2;
    }

    static byte[] request(int module, int index) {
        return new byte[] { 0x5A, (byte) 0xA5, 0x01, 0x3E,
                (byte) module, 0x01, (byte) index, (byte) length(module, index) };
    }
}
