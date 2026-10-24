package com.bfgtools.calibration.core;

import android.app.ActivityManager;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Root access to Ninebot's local BLE credential database.
 *
 * Compatibility behavior is based on the known-working reference APK:
 * - detects the current Android user, then keeps /data/user/0 and /data/data fallbacks;
 * - scans only the fixed cn.ninebot.ninebot package path in /data/user/*;
 * - tries Magisk/KernelSU mount-master variants (-M, --mount-master, -mm)
 *   plus ordinary su -c;
 * - tries both cat and dd for binary copying;
 * - copies WAL/SHM/journal sidecars when present;
 * - applies a timeout and includes the real root error in failures.
 */
final class RootDb {
    private static final String PKG = "cn.ninebot.ninebot";

    private static final String[] MOUNT_MODES = {
            "-M", "--mount-master", "-mm", ""
    };

    private static final long ROOT_TIMEOUT_SECONDS = 20;

    private RootDb() {}

    static boolean hasRoot() {
        RootCommand result = runRootTextCommand("id -u");
        return result.exitCode == 0 && "0".equals(result.output.trim());
    }

    /** Copies the host-installed Ninebot base APK into this app's private cache. */
    static File copyInstalledNinebotApk(Context context) throws Exception {
        if (!hasRoot()) throw new Exception("未获得 Root 权限");
        RootCommand paths = runRootTextCommand("pm path " + PKG);
        if (paths.exitCode != 0 || paths.output.trim().isEmpty()) {
            throw new Exception("本机未安装九号出行");
        }

        List<String> apkPaths = new ArrayList<>();
        for (String line : paths.output.split("\\r?\\n")) {
            String value = line.trim();
            if (value.startsWith("package:")) value = value.substring(8).trim();
            if (value.endsWith(".apk")) apkPaths.add(value);
        }
        if (apkPaths.isEmpty()) throw new Exception("没有找到九号出行安装包路径");
        if (apkPaths.size() > 1) {
            throw new Exception("本机九号出行为分包安装，无法完整读取；请手动选择完整 APK");
        }

        File dir = new File(context.getCacheDir(), "ninebot-root-import");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("无法创建 Root 导入缓存");
        File target = new File(dir, "host-ninebot.apk");
        String copyError = rootCopy(apkPaths.get(0), target);
        if (copyError != null) throw new Exception("读取本机 APK 失败：" + copyError);
        if (!target.isFile() || target.length() < 1024L * 1024L) {
            target.delete();
            throw new Exception("读取到的本机 APK 不完整");
        }
        return target;
    }

    /** Best-effort host-process check. Root mode is authoritative; normal mode
     * also tries toolbox pidof and ActivityManager before BLE scanning. */
    static boolean isNinebotRunning(Context context, boolean useRoot) {
        if (useRoot) {
            RootCommand result = runRootTextCommand("pidof " + PKG);
            return result.exitCode == 0 && !result.output.trim().isEmpty();
        }

        Process process = null;
        try {
            process = new ProcessBuilder("sh", "-c", "pidof " + PKG).start();
            String output = new String(readFully(process.getInputStream()), StandardCharsets.UTF_8).trim();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0 && !output.isEmpty()) return true;
        } catch (Throwable ignored) {
        } finally {
            if (process != null) {
                try { process.destroy(); } catch (Throwable ignored) {}
            }
        }

        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> processes =
                    manager == null ? null : manager.getRunningAppProcesses();
            if (processes == null) return false;
            for (ActivityManager.RunningAppProcessInfo info : processes) {
                if (info == null) continue;
                if (PKG.equals(info.processName)) return true;
                if (info.pkgList == null) continue;
                for (String pkg : info.pkgList) {
                    if (PKG.equals(pkg)) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Returns null on success, otherwise a user-facing non-fatal diagnostic. */
    static String tryForceStopNinebot() {
        RootCommand r = runRootTextCommand("am force-stop " + PKG);
        if (r.exitCode == 0) return null;
        return "无法自动停止九号，请确认九号未占用车辆蓝牙"
                + "（exit=" + r.exitCode + "）";
    }

    static List<DeviceRecord> loadDevices(Context context) throws Exception {
        return loadDevices(context, hasRoot());
    }

    /** Rooted phones use the real Ninebot app database; non-root phones use BlackBox. */
    static List<DeviceRecord> loadDevices(Context context, boolean rooted) throws Exception {
        if (!rooted) {
            List<DeviceRecord> bridged = VmDbBridge.tryLoadDevices(context);
            if (bridged != null) {
                return bridged;
            }
            throw new Exception("未检测到 Root，且已导入九号出行数据库不可用。"
                    + "请先导入并打开九号出行，登录后再同步车辆。");
        }
        File dir = new File(context.getCacheDir(), "ninebot_db");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("无法创建缓存目录");

        File db = new File(dir, "device-db");
        File wal = new File(dir, "device-db-wal");
        File shm = new File(dir, "device-db-shm");
        File journal = new File(dir, "device-db-journal");
        File[] copies = { db, wal, shm, journal };
        deleteCopies(copies);

        String selectedDb = null;
        StringBuilder errors = new StringBuilder();

        // This is the explicit VM/database-import path. Stopping Ninebot makes a
        // main DB + WAL/SHM snapshot stable; failure is not fatal to the copy.
        tryForceStopNinebot();

        for (String candidate : discoverDbCandidates()) {
            String error = rootCopy(candidate, db);
            if (error == null) {
                selectedDb = candidate;
                break;
            }
            if (errors.length() > 0) errors.append('；');
            errors.append(candidate).append('=').append(error);
        }

        if (selectedDb == null) {
            deleteCopies(copies);
            throw new Exception("已获得 Root，但读取九号数据库失败：" + safeMessage(errors.toString()));
        }

        // Sidecars are optional. Ignore failures exactly like the working reference.
        rootCopy(selectedDb + "-wal", wal);
        rootCopy(selectedDb + "-shm", shm);
        rootCopy(selectedDb + "-journal", journal);

        List<DeviceRecord> result = new ArrayList<>();
        SQLiteDatabase sq = null;
        Cursor c = null;
        try {
            sq = SQLiteDatabase.openDatabase(db.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            Set<String> columns = tableColumns(sq, "DEVICE");
            requireColumn(columns, "mac_address");
            requireColumn(columns, "password");
            String idExpr = columns.contains("_id") ? "_id" : "rowid";
            String snExpr = columns.contains("sn") ? "sn" : "''";
            String nameExpr = columns.contains("name") ? "name" : "''";
            String typeExpr = columns.contains("device_type") ? "device_type" : "''";
            c = sq.rawQuery("SELECT " + idExpr + ",mac_address," + snExpr + "," + nameExpr
                    + ",password," + typeExpr + " FROM DEVICE WHERE mac_address IS NOT NULL", null);
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String mac = c.getString(1);
                String sn = c.isNull(2) ? "" : c.getString(2);
                String name = c.isNull(3) ? "" : c.getString(3);
                String deviceType = c.isNull(5) ? "" : c.getString(5);
                byte[] pw = readPassword16(c, 4);
                if (pw == null) continue;
                result.add(new DeviceRecord(id, mac, sn, name, deviceType, pw, "ninebot_db"));
                java.util.Arrays.fill(pw, (byte) 0);
            }
            return result;
        } finally {
            if (c != null) c.close();
            if (sq != null) sq.close();
            // Never leave a second plaintext copy of the credential DB behind.
            deleteCopies(copies);
        }
    }

    private static List<String> discoverDbCandidates() {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        RootCommand current = runRootTextCommand("cmd activity get-current-user");
        if (current.exitCode == 0) {
            Matcher m = Pattern.compile("(?:^|\\D)(\\d+)(?:\\D|$)").matcher(current.output);
            if (m.find()) paths.add(userDbPath(m.group(1)));
        }

        // Keep both legacy fallbacks from the known-working implementation.
        paths.add("/data/data/" + PKG + "/databases/device-db");
        paths.add(userDbPath("0"));

        RootCommand scan = runRootTextCommand(
                "for f in /data/user/*/" + PKG + "/databases/device-db; do "
                        + "[ -f \"$f\" ] && printf '%s\\n' \"$f\"; done");
        if (scan.exitCode == 0) {
            for (String line : scan.output.split("\\r?\\n")) {
                String candidate = line.trim();
                if (candidate.matches("^/data/user/[0-9]+/cn\\.ninebot\\.ninebot/databases/device-db$")) {
                    paths.add(candidate);
                }
            }
        }
        return new ArrayList<>(paths);
    }

    private static String userDbPath(String userId) {
        return "/data/user/" + userId + "/" + PKG + "/databases/device-db";
    }

    private static Set<String> tableColumns(SQLiteDatabase db, String table) {
        Set<String> result = new LinkedHashSet<>();
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            int nameIndex = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    result.add(cursor.getString(nameIndex).toLowerCase(Locale.US));
                }
            }
        }
        return result;
    }

    private static void requireColumn(Set<String> columns, String column) throws Exception {
        if (!columns.contains(column)) throw new Exception("DEVICE 表缺少字段：" + column);
    }

    private static byte[] readPassword16(Cursor cursor, int column) {
        try {
            if (cursor.isNull(column)) return null;
            if (cursor.getType(column) == Cursor.FIELD_TYPE_BLOB) {
                byte[] blob = cursor.getBlob(column);
                if (blob == null || blob.length != 16) return null;
                return blob.clone();
            }
            String raw = cursor.getString(column);
            if (raw == null) return null;
            raw = raw.trim();
            if (raw.startsWith("0x") || raw.startsWith("0X")) raw = raw.substring(2);
            // Preserve the known-working behavior: decode the first 32 HEX chars
            // directly to 16 bytes. This is not dynamic-config .nb decryption.
            if (raw.length() < 32) return null;
            byte[] decoded = Hex.decode(raw.substring(0, 32));
            return decoded.length == 16 ? decoded : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Copy a root-only file to an app-private destination.
     * Returns null on success, otherwise a compact diagnostic string.
     */
    private static String rootCopy(String source, File dest) {
        String catError = rootCopyAttempt("cat " + shellQuote(source), dest);
        if (catError == null) return null;

        String ddError = rootCopyAttempt("dd if=" + shellQuote(source) + " bs=32768", dest);
        if (ddError == null) return null;

        dest.delete();
        return safeMessage("cat=" + catError + "；dd=" + ddError);
    }

    private static String rootCopyAttempt(String command, File dest) {
        StringBuilder errors = new StringBuilder();
        for (String mountMode : MOUNT_MODES) {
            String error = rootCopyWithMode(command, dest, mountMode);
            if (error == null) return null;

            if (errors.length() > 0) errors.append(" | ");
            errors.append(mountMode.isEmpty() ? "default" : mountMode)
                    .append(':')
                    .append(error);
        }
        return safeMessage(errors.toString());
    }

    private static String rootCopyWithMode(String command, File dest, String mountMode) {
        dest.delete();
        try {
            ProcessBuilder pb;
            if (mountMode.isEmpty()) {
                pb = new ProcessBuilder("su", "-c", command);
            } else {
                pb = new ProcessBuilder("su", mountMode, "-c", command);
            }
            pb.redirectErrorStream(false);
            Process process = pb.start();

            try (InputStream in = process.getInputStream();
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[32768];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (n > 0) out.write(buffer, 0, n);
                }
            }

            String stderr = new String(readFully(process.getErrorStream()), StandardCharsets.UTF_8).trim();
            boolean finished = process.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                dest.delete();
                return "超时";
            }

            int rc = process.exitValue();
            if (rc == 0 && dest.isFile() && dest.length() > 0) {
                return null;
            }

            dest.delete();
            return "rc=" + rc + " " + safeMessage(stderr);
        } catch (Throwable t) {
            dest.delete();
            return t.getClass().getSimpleName() + " " + safeMessage(t.getMessage());
        }
    }

    private static RootCommand runRootTextCommand(String command) {
        RootCommand last = new RootCommand(-1, "未执行");
        for (String mountMode : MOUNT_MODES) {
            Process process = null;
            try {
                ProcessBuilder pb;
                if (mountMode.isEmpty()) {
                    pb = new ProcessBuilder("su", "-c", command);
                } else {
                    pb = new ProcessBuilder("su", mountMode, "-c", command);
                }
                pb.redirectErrorStream(true);
                process = pb.start();

                String output = new String(readFully(process.getInputStream()), StandardCharsets.UTF_8).trim();
                boolean finished = process.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroy();
                    last = new RootCommand(-1, "超时");
                } else {
                    RootCommand r = new RootCommand(process.exitValue(), output);
                    if (r.exitCode == 0) return r;
                    last = r;
                }
            } catch (Throwable t) {
                last = new RootCommand(-1, t.getClass().getSimpleName() + " " + safeMessage(t.getMessage()));
                if (process != null) {
                    try { process.destroy(); } catch (Throwable ignored) {}
                }
            }
        }
        return last;
    }

    private static byte[] readFully(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            if (n > 0) out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static void deleteCopies(File[] files) {
        for (File file : files) {
            if (file != null) file.delete();
        }
    }

    private static String safeMessage(String s) {
        if (s == null || s.trim().isEmpty()) return "无输出";
        String cleaned = s.replace('\n', ' ').replace('\r', ' ').trim();
        return cleaned.length() > 240 ? cleaned.substring(0, 240) : cleaned;
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static final class RootCommand {
        final int exitCode;
        final String output;

        RootCommand(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
        }
    }
}
