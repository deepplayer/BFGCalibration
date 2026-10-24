package com.bfgtools.calibration.core;

/** Monotonic-time gate shared by every parameter-write risk confirmation. */
final class TimedRiskGate {
    static final int DASHBOARD_SECONDS = 30;
    static final int METER_SECONDS = 3;

    private TimedRiskGate() { }

    static boolean canProceed(long elapsedRealtime, long readyAt, boolean checked) {
        return checked && elapsedRealtime >= readyAt;
    }
}
