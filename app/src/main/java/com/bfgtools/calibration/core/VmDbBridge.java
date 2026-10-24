package com.bfgtools.calibration.core;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Client for the signature-protected, read-only database bridge in BlackBox. */
final class VmDbBridge {
    private static final String TAG = "VmDbBridge";
    private static final Uri BRIDGE_URI =
            Uri.parse("content://com.bfgtools.calibration.imported.vmdb");
    private static final String METHOD_LOAD_DEVICES = "load_devices_v1";
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_DEVICE_ROWS = 64;

    private VmDbBridge() {}

    /**
     * Returns null only when the compatible BlackBox provider is not installed.
     * Provider/security/data errors are surfaced instead of silently using stale data.
     */
    static List<DeviceRecord> tryLoadDevices(Context context) throws Exception {
        Bundle reply;
        try {
            ContentResolver resolver = context.getContentResolver();
            reply = resolver.call(BRIDGE_URI, METHOD_LOAD_DEVICES, null, null);
        } catch (IllegalArgumentException unavailable) {
            return null;
        } catch (SecurityException denied) {
            throw new Exception("内置数据库桥接拒绝访问；请重新安装完整的一体化 APK", denied);
        }

        if (reply == null) {
            throw new Exception("内置数据库桥接未返回结果");
        }
        if (reply.getInt("format_version", -1) != FORMAT_VERSION) {
            throw new Exception("内置数据库桥接版本不兼容");
        }
        if (!reply.getBoolean("ok", false)) {
            String error = reply.getString("error", "未知错误");
            throw new Exception("内置数据库桥接读取失败：" + safeMessage(error));
        }

        ArrayList<Bundle> rows = getRows(reply);
        if (rows == null) {
            throw new Exception("内置数据库桥接返回格式错误");
        }
        if (rows.size() > MAX_DEVICE_ROWS) {
            throw new Exception("内置数据库桥接返回车辆数量异常");
        }

        ArrayList<DeviceRecord> result = new ArrayList<>();
        for (Bundle row : rows) {
            if (row == null) continue;
            byte[] password = row.getByteArray("password");
            if (password == null || password.length != 16) {
                if (password != null) Arrays.fill(password, (byte) 0);
                continue;
            }
            try {
                result.add(new DeviceRecord(
                        row.getLong("id", 0),
                        row.getString("mac_address", ""),
                        row.getString("sn", ""),
                        row.getString("name", ""),
                        row.getString("device_type", ""),
                        password,
                        "blackbox_vm_db"));
            } finally {
                Arrays.fill(password, (byte) 0);
            }
        }
        Log.i(TAG, "Loaded " + result.size() + " vehicle record(s) from BlackBox");
        return result;
    }

    @SuppressWarnings("deprecation")
    private static ArrayList<Bundle> getRows(Bundle reply) {
        return reply.getParcelableArrayList("devices");
    }

    private static String safeMessage(String value) {
        if (value == null || value.trim().isEmpty()) return "无输出";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 240 ? clean.substring(0, 240) : clean;
    }
}
