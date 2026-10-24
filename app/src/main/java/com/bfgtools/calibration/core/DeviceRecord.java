package com.bfgtools.calibration.core;

final class DeviceRecord {
    final long id;
    final String mac;
    final String sn;
    final String name;
    final String effectiveSn;
    final String deviceType;
    final String source;
    final byte[] password16;

    DeviceRecord(long id, String mac, String sn, String name, String deviceType,
                 byte[] password16, String source) {
        this.id = id;
        this.mac = mac == null ? "" : mac.trim().toUpperCase(java.util.Locale.US);
        this.sn = sn == null ? "" : sn.trim();
        this.name = name == null ? "" : name.trim();
        this.effectiveSn = effectiveSn(this.sn, this.name);
        this.deviceType = deviceType == null ? "" : deviceType.trim();
        this.password16 = password16.clone();
        this.source = source == null ? "unknown" : source;
    }

    byte[] passwordCopy() { return password16.clone(); }

    void wipe() { java.util.Arrays.fill(password16, (byte) 0); }

    String displayName() {
        return deviceType.isEmpty() ? "测试车辆" : "测试车辆 · 车型 " + deviceType;
    }

    private static String effectiveSn(String sn, String name) {
        if (sn != null && !sn.trim().isEmpty()) return sn.trim();
        String candidate = name == null ? "" : name.trim();
        return candidate.matches("(?i)[A-Z0-9]{14}") ? candidate : "";
    }

    @Override public String toString() { return displayName(); }
}
