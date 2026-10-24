package com.bfgtools.calibration.core;

/** Exact firmware allowlist for DIS 0x92 writes. Unknown values fail closed. */
final class DashboardWritePolicy {
    static final int DASHBOARD = 0x0259;
    static final int COLOR_DISPLAY = 0x0155;
    static final int CENTRE = 0x05CA;
    static final int METER = 0x0429;

    private DashboardWritePolicy() { }

    static boolean allows(int dashboard, int colorDisplay, int centre, int meter) {
        return dashboard == DASHBOARD && colorDisplay == COLOR_DISPLAY
                && centre == CENTRE && meter == METER;
    }

    static String blockedMessage() {
        return "当前车辆不满足仪表盘写入条件，本次未发送写入指令。"
                + "请在参数写入页查看四项固件的核对结果；计量模块临时写入有独立条件。";
    }
}
