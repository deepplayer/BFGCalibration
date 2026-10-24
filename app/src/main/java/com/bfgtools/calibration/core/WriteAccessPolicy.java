package com.bfgtools.calibration.core;

import java.util.Locale;

final class WriteAccessPolicy {
    private WriteAccessPolicy() { }

    static boolean isReadOnlySerial(String serial) {
        return serial != null && serial.trim().toUpperCase(Locale.US).startsWith("N");
    }

    static boolean needsMeterCompatibilityWarning(int rawVersion) {
        // 2.8.6 and 4.2.9 are the explicitly validated meter versions.
        // Unknown and other versions remain usable, but require extra acknowledgement.
        return rawVersion != 0x0286 && rawVersion != 0x0429;
    }
}
