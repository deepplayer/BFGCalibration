package com.bfgtools.calibration.core;

/** DIS 0x92 voltage selector. This is independent of the BFG Profile encoding. */
final class DisVoltageConfig {
    private DisVoltageConfig() { }

    static int nominalVoltage(int raw) {
        if (raw < 0 || raw > 0xFF) return -1;
        switch (raw & 0x0F) {
            case 1: return 72;
            case 2: return 60;
            case 3: return 48;
            default: return -1;
        }
    }

    static int target(int currentRaw, int voltage) {
        if (nominalVoltage(currentRaw) < 0) {
            throw new IllegalArgumentException("仪表配置值无法识别，禁止猜测写入");
        }
        final int lowNibble;
        switch (voltage) {
            case 72: lowNibble = 1; break;
            case 60: lowNibble = 2; break;
            case 48: lowNibble = 3; break;
            default: throw new IllegalArgumentException("不支持的电压档位");
        }
        return (currentRaw & 0xF0) | lowNibble;
    }

    static boolean isObserved(int raw) {
        return raw == 0xC1 || raw == 0xC2
                || raw == 0x51 || raw == 0x52 || raw == 0x53;
    }

    static boolean requiresExtraWarning(int currentRaw, int targetRaw) {
        return !isObserved(currentRaw) || !isObserved(targetRaw);
    }

    static byte[] writePacket(int targetRaw) {
        if (nominalVoltage(targetRaw) < 0) {
            throw new IllegalArgumentException("无效的仪表配置目标值");
        }
        // Ninebot packet: length 2, phone -> DIS, write-with-reply, 0x92, LE16.
        return new byte[] {0x5A, (byte) 0xA5, 0x02, 0x3E, 0x01, 0x02,
                (byte) 0x92, (byte) targetRaw, 0x00};
    }
}
