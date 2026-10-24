package com.bfgtools.calibration.core;

/** Keeps an authentication timeout separate from ordinary Bluetooth failures. */
final class AuthRepairOffer {
    private AuthRepairOffer() { }

    static boolean isAuthTimeout(String error) {
        return error != null && error.contains("AUTH无回复");
    }

    static boolean hasExactVehicleIdentity(String mac, String serial) {
        return mac != null && mac.matches("(?i)[0-9A-F]{2}(:[0-9A-F]{2}){5}")
                && serial != null && serial.matches("(?i)[A-Z0-9]{14}");
    }
}
