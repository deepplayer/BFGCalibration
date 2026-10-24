package com.onebitmonochrome.blacksbbox.bridge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.env.BEnvironment;

/**
 * Narrow, read-only bridge from BlackBox's Ninebot guest database to the
 * integrated BFG activity. The raw database is never exposed.
 */
public final class NinebotVmDbProvider extends ContentProvider {
    private static final String TAG = "NinebotVmDbProvider";
    public static final String AUTHORITY = "com.bfgtools.calibration.imported.vmdb";
    public static final String METHOD_LOAD_DEVICES = "load_devices_v1";

    private static final String NINEBOT_PACKAGE = "cn.ninebot.ninebot";
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_DEVICE_ROWS = 64;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        enforceAuthorizedCaller();
        if (!METHOD_LOAD_DEVICES.equals(method)) {
            throw new IllegalArgumentException("Unsupported method");
        }

        Bundle reply = new Bundle();
        reply.putInt("format_version", FORMAT_VERSION);
        try {
            ArrayList<Bundle> devices = loadDevices();
            reply.putBoolean("ok", true);
            reply.putParcelableArrayList("devices", devices);
            Log.i(TAG, "Returned " + devices.size() + " vehicle record(s) to integrated activity");
        } catch (Throwable error) {
            reply.putBoolean("ok", false);
            reply.putString("error", safeMessage(error));
            Log.w(TAG, "Read-only bridge failed: " + safeMessage(error));
        }
        return reply;
    }

    private void enforceAuthorizedCaller() {
        Context context = getContext();
        if (context == null) {
            throw new SecurityException("Provider context unavailable");
        }
        int callingUid = Binder.getCallingUid();
        // This provider is private to the integrated activity.
        if (callingUid == Process.myUid()) return;
        throw new SecurityException("Caller is not this application");
    }

    private ArrayList<Bundle> loadDevices() throws Exception {
        ArrayList<Bundle> result = new ArrayList<>();
        boolean databaseFound = false;

        for (int userId : discoverVirtualUserIds()) {
            File dbFile = new File(BEnvironment.getDataDatabasesDir(NINEBOT_PACKAGE, userId), "device-db");
            if (!dbFile.isFile()) {
                continue;
            }
            databaseFound = true;

            try {
                BlackBoxCore.get().stopPackage(NINEBOT_PACKAGE, userId);
            } catch (Throwable ignored) {
                // A read-only open below still provides a useful diagnostic if
                // the guest could not be stopped for any reason.
            }

            ensureInsideVirtualRoot(dbFile);
            readDatabase(dbFile, userId, result);
            if (result.size() >= MAX_DEVICE_ROWS) {
                break;
            }
        }

        if (!databaseFound) {
            throw new Exception("未找到九号数据库；请先在虚拟环境启动九号出行并登录账号");
        }
        return result;
    }

    private Set<Integer> discoverVirtualUserIds() {
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        result.add(0);
        File userRoot = BEnvironment.getUserDir(0).getParentFile();
        File[] children = userRoot == null ? null : userRoot.listFiles();
        if (children == null) {
            return result;
        }
        for (File child : children) {
            if (!child.isDirectory() || !child.getName().matches("[0-9]+")) {
                continue;
            }
            try {
                result.add(Integer.parseInt(child.getName()));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private void ensureInsideVirtualRoot(File file) throws Exception {
        String root = BEnvironment.getVirtualRoot().getCanonicalPath() + File.separator;
        String candidate = file.getCanonicalPath();
        if (!candidate.startsWith(root)) {
            throw new SecurityException("Invalid virtual database path");
        }
    }

    private void readDatabase(File dbFile, int userId, ArrayList<Bundle> output) throws Exception {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = SQLiteDatabase.openDatabase(
                    dbFile.getAbsolutePath(),
                    null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            Set<String> columns = tableColumns(db, "DEVICE");
            requireColumn(columns, "mac_address");
            requireColumn(columns, "password");

            String idExpr = columns.contains("_id") ? "_id" : "rowid";
            String snExpr = columns.contains("sn") ? "sn" : "''";
            String nameExpr = columns.contains("name") ? "name" : "''";
            String typeExpr = columns.contains("device_type") ? "device_type" : "''";
            cursor = db.rawQuery("SELECT " + idExpr + ",mac_address," + snExpr + "," + nameExpr
                    + ",password," + typeExpr + " FROM DEVICE WHERE mac_address IS NOT NULL", null);

            while (cursor.moveToNext() && output.size() < MAX_DEVICE_ROWS) {
                byte[] password = readPassword16(cursor, 4);
                if (password == null) {
                    continue;
                }
                Bundle row = new Bundle();
                row.putLong("id", cursor.getLong(0));
                row.putString("mac_address", nullableString(cursor, 1));
                row.putString("sn", nullableString(cursor, 2));
                row.putString("name", nullableString(cursor, 3));
                row.putString("device_type", nullableString(cursor, 5));
                row.putInt("virtual_user_id", userId);
                row.putByteArray("password", password);
                output.add(row);
            }
        } finally {
            if (cursor != null) cursor.close();
            if (db != null) db.close();
        }
    }

    private static Set<String> tableColumns(SQLiteDatabase db, String table) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
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

    private static void requireColumn(Set<String> columns, String name) throws Exception {
        if (!columns.contains(name)) {
            throw new Exception("DEVICE 表缺少字段：" + name);
        }
    }

    private static String nullableString(Cursor cursor, int index) {
        return cursor.isNull(index) ? "" : cursor.getString(index);
    }

    private static byte[] readPassword16(Cursor cursor, int column) {
        try {
            if (cursor.isNull(column)) return null;
            if (cursor.getType(column) == Cursor.FIELD_TYPE_BLOB) {
                byte[] blob = cursor.getBlob(column);
                return blob != null && blob.length == 16 ? blob.clone() : null;
            }
            String raw = cursor.getString(column);
            if (raw == null) return null;
            raw = raw.trim();
            if (raw.startsWith("0x") || raw.startsWith("0X")) raw = raw.substring(2);
            if (raw.length() < 32) return null;
            byte[] decoded = decodeHex(raw.substring(0, 32));
            return decoded.length == 16 ? decoded : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static byte[] decodeHex(String value) {
        int length = value.length();
        if ((length & 1) != 0) throw new IllegalArgumentException("Invalid HEX length");
        byte[] output = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            int high = Character.digit(value.charAt(i), 16);
            int low = Character.digit(value.charAt(i + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid HEX");
            output[i / 2] = (byte) ((high << 4) | low);
        }
        return output;
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = error == null ? "未知错误" : error.getClass().getSimpleName();
        }
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() > 240 ? message.substring(0, 240) : message;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException("Use call(load_devices_v1)");
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read only"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read only"); }
}
