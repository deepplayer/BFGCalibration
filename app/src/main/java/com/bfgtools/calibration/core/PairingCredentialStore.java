package com.bfgtools.calibration.core;

import android.content.Context;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pairing keys live only for this app process; a process restart requires pairing again. */
final class PairingCredentialStore {
    private static final String LEGACY_PREFS = "bfg_pairing_lab_credentials";
    private static final String LEGACY_KEY_ALIAS = "bfg_pairing_lab_key_v1";
    private static final Map<String, Entry> SESSION = new HashMap<>();

    private PairingCredentialStore() { }

    static synchronized void purgeLegacy(Context context) {
        // The previous release persisted keys. Remove them once so the new contract is real.
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (store.containsAlias(LEGACY_KEY_ALIAS)) store.deleteEntry(LEGACY_KEY_ALIAS);
        } catch (Exception ignored) { }
    }

    static synchronized byte[] load(Context context, String mac) {
        Entry entry = SESSION.get(key(mac));
        return entry == null ? null : Arrays.copyOf(entry.password, entry.password.length);
    }

    static synchronized boolean save(Context context, String mac, String serial, byte[] password32) {
        if (mac == null || mac.isEmpty() || serial == null
                || !serial.matches("(?i)[A-Z0-9]{14}")
                || password32 == null || password32.length != 32) return false;
        Entry old = SESSION.put(key(mac), new Entry(serial, Arrays.copyOf(password32, 32)));
        if (old != null) Arrays.fill(old.password, (byte) 0);
        return true;
    }

    static synchronized List<DeviceRecord> loadRecords(Context context) {
        List<DeviceRecord> records = new ArrayList<>();
        for (Map.Entry<String, Entry> item : SESSION.entrySet()) {
            String compact = item.getKey();
            if (compact.length() != 12) continue;
            StringBuilder mac = new StringBuilder(17);
            for (int i = 0; i < compact.length(); i += 2) {
                if (i > 0) mac.append(':');
                mac.append(compact, i, i + 2);
            }
            Entry entry = item.getValue();
            records.add(new DeviceRecord(-1, mac.toString(), entry.serial, entry.serial, "",
                    Arrays.copyOf(entry.password, 16), "local_pair"));
        }
        return records;
    }

    private static String key(String mac) {
        return mac == null ? "" : mac.replace(":", "").toUpperCase(Locale.US);
    }

    private static final class Entry {
        final String serial;
        final byte[] password;
        Entry(String serial, byte[] password) {
            this.serial = serial;
            this.password = password;
        }
    }
}
