package com.bfgtools.calibration.core;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Persists one privacy-filtered BLE diagnostic session for user-initiated sharing. */
final class DiagnosticRecorder {
    private final Context app;
    private final File directory;
    private final File latestFile;
    private long startedAt;

    DiagnosticRecorder(Context context) {
        app = context.getApplicationContext();
        directory = new File(app.getFilesDir(), "bfg_diagnostics");
        latestFile = new File(directory, "latest_bfg_diagnostic.txt");
    }

    synchronized void start(DeviceRecord record, BfgBleClient.Operation operation, int targetProfile) {
        startedAt = SystemClock.elapsedRealtime();
        if (!directory.exists()) directory.mkdirs();
        String version = "unknown";
        try {
            PackageInfo info = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
            version = info.versionName + " (" + info.getLongVersionCode() + ")";
        } catch (Throwable ignored) {
        }
        StringBuilder header = new StringBuilder();
        header.append("BFG capacity diagnostic\n");
        header.append("format=6\n");
        header.append("started=").append(timestamp()).append('\n');
        header.append("app=").append(version).append('\n');
        header.append("package=").append(app.getPackageName()).append('\n');
        header.append("android=").append(Build.VERSION.RELEASE)
                .append(" api=").append(Build.VERSION.SDK_INT).append('\n');
        header.append("device=").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append('\n');
        header.append("operation=").append(operation).append('\n');
        if (operation == BfgBleClient.Operation.WRITE_DIS_VOLTAGE) {
            header.append("targetDashboardVoltage=").append(targetProfile).append("V\n");
        } else if (operation == BfgBleClient.Operation.REGISTER_SCAN) {
            header.append(String.format(Locale.US, "scanModule=0x%02X\n", targetProfile & 0xFF));
        } else {
            header.append(String.format(Locale.US, "targetProfile=0x%02X\n", targetProfile & 0xFF));
        }
        header.append("vehicleModelHint=").append(modelHint(record)).append('\n');
        header.append("vehicleType=").append(safe(record == null ? "" : record.deviceType)).append('\n');
        header.append("source=").append(safe(record == null ? "" : record.source)).append('\n');
        header.append("vehicleIdentity=redacted\n");
        header.append("--- events ---\n");
        writeBytes(header.toString().getBytes(StandardCharsets.UTF_8), false);
    }

    synchronized void record(String category, String message) {
        if (!latestFile.isFile()) return;
        long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - startedAt);
        String line = String.format(Locale.US, "+%06dms [%s] %s\n",
                elapsed, safe(category), sanitize(message));
        writeBytes(line.getBytes(StandardCharsets.UTF_8), true);
    }

    synchronized void recordResult(BfgBleClient.Result r) {
        if (r == null) return;
        if (r.operation == BfgBleClient.Operation.REGISTER_SCAN) {
            record("SCAN_SUMMARY", String.format(Locale.US,
                    "module=0x%02X responses=%d timeouts=%d writes=false",
                    r.registerScanModule, r.registerScanReplies, r.registerScanTimeouts));
            return;
        }
        record("RESULT", "operation=" + r.operation
                + " requestedProfile=" + hex(r.requestedProfile)
                + " beforeProfile=" + hex(r.beforeProfile)
                + " beforeSoc=" + r.beforeSoc
                + " beforeCapacityRaw=" + r.beforeCapacityRaw
                + " scannedCapacity0E=" + r.scannedCapacity0ERaw
                + " scannedCapacity0F=" + r.scannedCapacity0FRaw
                + " scannedDynamicCapacity=" + r.scannedDynamicCapacityRaw
                + " scannedCoreCapacity=" + r.scannedCoreCapacityRaw
                + " scannedMirrorCapacity=" + r.scannedMirrorCapacityRaw
                + " scannedSelectedCapacity=" + r.scannedSelectedCapacityRaw
                + " scannedSelectedRegister=" + hexRegister(
                r.scannedSelectedCapacityRegister)
                + " capacityScanReason=" + r.capacityScanReason
                + " resolvedSoc=" + r.resolvedBeforeSoc
                + " resolvedCapacity=" + r.resolvedBeforeCapacityRaw
                + " communicationMode=" + r.communicationMode
                + " dashboardVersion=" + hex(r.disDashboardVersionRaw)
                + " colorDisplayVersion=" + hex(r.colorDisplayVersionRaw)
                + " centreControllerVersion=" + hex(r.centreControllerVersionRaw)
                + " disConfigBefore=" + hexWord(r.disConfigRaw)
                + " disConfigTarget=" + hexWord(r.disConfigTargetRaw)
                + " disConfigAfter=" + hexWord(r.disConfigAfterRaw)
                + " disConfigReadbackVerified=" + r.disConfigReadbackVerified
                + " bfgVersion=" + hex(r.disBfgVersionRaw)
                + " dashboardEnergyWh=" + r.disEnergyWhRaw
                + " dashboardRemainingCapacityMah=" + r.disRemainingCapacityRaw
                + " dashboardCalculatedVoltage=" + calculatedVoltage(r)
                + " dashboardNominalVoltage=" + r.dashboardNominalVoltage()
                + " meterNominalVoltage=" + r.meterNominalVoltage()
                + " voltageMismatch=" + r.voltageMismatch()
                + " afterProfile=" + hex(r.afterProfile)
                + " afterCapacityRaw=" + r.afterCapacityRaw
                + " resolvedAfterCapacity=" + r.resolvedAfterCapacityRaw
                + " writeSupported=" + r.writeSupported
                + " writeCommandSent=" + r.writeCommandSent
                + " writeAckSeen=" + r.writeAckSeen
                + " profileReadbackVerified=" + r.profileReadbackVerified
                + " capacityReadbackVerified=" + r.capacityReadbackVerified
                + " verificationRetried=" + r.verificationRetried);
        frame("beforeProfile", r.rawBeforeProfileFrame);
        frame("beforeSoc", r.rawBeforeSocFrame);
        frame("beforeCapacity", r.rawBeforeCapacityFrame);
        frame("scannedCapacity0E", r.rawScannedCapacity0EFrame);
        frame("scannedCapacity0F", r.rawScannedCapacity0FFrame);
        frame("scannedDynamicCapacity", r.rawScannedDynamicCapacityFrame);
        frame("scannedCoreCapacity", r.rawScannedCoreCapacityFrame);
        frame("scannedMirrorCapacity", r.rawScannedMirrorCapacityFrame);
        frame("disDashboardVersion", r.rawDisDashboardVersionFrame);
        frame("disEnergyWh", r.rawDisEnergyWhFrame);
        frame("disRemainingCapacity", r.rawDisRemainingCapacityFrame);
        frame("afterProfile", r.rawAfterProfileFrame);
        frame("afterCapacity", r.rawAfterCapacityFrame);
        frame("writeAck", r.writeAckFrame);
        frame("disBattery", r.rawDisBatteryFrame);
        frame("disVrlaVoltage", r.rawDisVrlaVoltageFrame);
        frame("disBfgVersion", r.rawDisBfgVersionFrame);
        frame("disConfigBefore", r.rawDisConfigFrame);
        frame("disConfigAfter", r.rawDisConfigAfterFrame);
    }

    synchronized boolean hasData() {
        return latestFile.isFile() && latestFile.length() > 0;
    }

    boolean share(Activity activity) {
        File source;
        synchronized (this) {
            if (!hasData()) return false;
            record("EXPORT", "User requested diagnostic export");
            source = latestFile;
        }
        try {
            File shareDir = new File(activity.getCacheDir(), "shared_logs");
            if (!shareDir.exists()) shareDir.mkdirs();
            String name = "BFG_Diagnostic_" + new SimpleDateFormat(
                    "yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt";
            File shared = new File(shareDir, name);
            copy(source, shared);
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", shared);
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, "BFG容量诊断数据")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newUri(activity.getContentResolver(), name, uri));
            activity.startActivity(Intent.createChooser(send, "发送诊断数据"));
            return true;
        } catch (Throwable t) {
            record("EXPORT_ERROR", t.getClass().getSimpleName() + ": " + t.getMessage());
            return false;
        }
    }

    private void frame(String name, String value) {
        if (value != null && !value.isEmpty()) record("FRAME", name + "=" + value);
    }

    private void writeBytes(byte[] bytes, boolean append) {
        try (FileOutputStream out = new FileOutputStream(latestFile, append)) {
            out.write(bytes);
            out.flush();
        } catch (Throwable ignored) {
        }
    }

    private static void copy(File source, File target) throws Exception {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
            out.flush();
        }
    }

    private static String sanitize(String value) {
        String text = safe(value).replace('\r', ' ').replace('\n', ' ');
        if (text.startsWith("PRE_COMM RX plain=")) {
            return "PRE_COMM reply received (authentication material and serial redacted)";
        }
        int auth = text.indexOf("plain=5AA50E3E045D00");
        if (auth >= 0) {
            return text.substring(0, auth) + "plain=5AA50E3E045D00[serial-redacted]";
        }
        return text;
    }

    private static String modelHint(DeviceRecord record) {
        if (record == null) return "unknown";
        return safe(record.deviceType);
    }

    private static String hexWord(int value) {
        return value < 0 ? "unknown" : String.format(Locale.US, "0x%04X", value & 0xFFFF);
    }

    private static String hex(int value) {
        return value < 0 ? "unknown" : String.format(Locale.US, "0x%02X", value & 0xFF);
    }

    private static String hexRegister(int value) {
        return value < 0 ? "unknown" : String.format(Locale.US, "0x%02X", value & 0xFF);
    }

    private static String calculatedVoltage(BfgBleClient.Result result) {
        double value = result.dashboardVoltageReading().calculatedVoltage;
        return Double.isFinite(value) ? String.format(Locale.US, "%.2f", value) : "unknown";
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(new Date());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
