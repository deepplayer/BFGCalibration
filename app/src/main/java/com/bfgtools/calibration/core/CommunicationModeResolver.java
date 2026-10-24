package com.bfgtools.calibration.core;

/**
 * Chooses a readback strategy from validated normal or repeated compatibility reads.
 */
final class CommunicationModeResolver {
    enum Mode {
        STANDARD,
        CAPACITY_SCAN_COMPAT,
        UNSUPPORTED
    }

    static final class Decision {
        final Mode mode;
        final int resolvedSoc;
        final int resolvedCapacity;
        final boolean writeSupported;
        final String reason;

        Decision(Mode mode, int resolvedSoc, int resolvedCapacity,
                 boolean writeSupported, String reason) {
            this.mode = mode;
            this.resolvedSoc = resolvedSoc;
            this.resolvedCapacity = resolvedCapacity;
            this.writeSupported = writeSupported;
            this.reason = reason;
        }
    }

    private CommunicationModeResolver() {
    }

    static Decision resolve(int profile,
                            int bfgSoc,
                            int bfgCapacity,
                            int disSoc,
                            int disVoltage,
                            int dashboardVersion,
                            int bfgVersion,
                            int scannedCapacity) {
        int expectedCapacity = BfgProfileCatalog.expectedCore(profile);
        boolean profileValid = expectedCapacity > 0;
        boolean bfgSocValid = isSocValid(bfgSoc);
        boolean disSocValid = isSocValid(disSoc);
        boolean fullBfgReadbackValid = profileValid
                && bfgSocValid
                && bfgCapacity == expectedCapacity;

        boolean compatibilityEvidence = isCapacityPlausible(scannedCapacity);
        boolean anySocValid = bfgSocValid || disSocValid;
        if (profileValid && compatibilityEvidence && anySocValid) {
            return new Decision(
                    Mode.CAPACITY_SCAN_COMPAT,
                    bfgSocValid ? bfgSoc : disSoc,
                    scannedCapacity,
                    true,
                    "固件版本较旧或常规容量异常；已通过重复扫描识别有效容量");
        }

        if (fullBfgReadbackValid) {
            return new Decision(
                    Mode.STANDARD,
                    bfgSoc,
                    bfgCapacity,
                    true,
                    "BFG Profile、SOC和容量回读一致");
        }

        int fallbackSoc = disSocValid ? disSoc : bfgSoc;
        return new Decision(
                Mode.UNSUPPORTED,
                fallbackSoc,
                -1,
                false,
                "通信组合尚未验证，关键BFG回读不一致");
    }

    static String label(Mode mode) {
        if (mode == Mode.STANDARD) return "标准完整回读";
        if (mode == Mode.CAPACITY_SCAN_COMPAT) return "兼容模式";
        return "未验证通信组合";
    }

    private static boolean isSocValid(int value) {
        return value >= 0 && value <= 100;
    }

    private static boolean isCapacityPlausible(int value) {
        return value >= 5000 && value <= 100000;
    }
}
