package com.bfgtools.calibration.core;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.appbar.MaterialToolbar;
import com.onebitmonochrome.blacksbbox.R;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** First-pairing flow used when no valid vehicle credential is available. */
public final class PairingLabActivity extends Activity {
    static final String EXTRA_REPAIR_MAC = "bfg_repair_mac";
    static final String EXTRA_REPAIR_SERIAL = "bfg_repair_serial";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> seen = new HashSet<>();
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private BfgBleClient client;
    private LinearLayout candidates;
    private TextView status;
    private MaterialButton scanButton;
    private String pendingRepairMac;
    private String pendingRepairSerial;
    private WebView prototypeWeb;
    private boolean prototypeReady;
    private long scanGeneration;
    private long connectionGeneration;
    private final List<String> candidateMacs = new ArrayList<>();
    private final List<String> candidateSerials = new ArrayList<>();

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pairing_lab);
        updateModalSystemBars(false);
        View root = findViewById(R.id.pairing_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.navigationBars());
            view.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        MaterialToolbar toolbar = findViewById(R.id.pairing_toolbar);
        toolbar.setNavigationOnClickListener(v -> finishPairing());
        status = findViewById(R.id.pairing_status);
        scanButton = findViewById(R.id.pairing_scan_button);
        scanButton.setOnClickListener(v -> scan());
        candidates = findViewById(R.id.pairing_candidates);
        findViewById(R.id.pairing_back_button).setOnClickListener(v -> finishPairing());
        initializePrototypeUi();

        if (savedInstanceState == null) {
            String mac = getIntent().getStringExtra(EXTRA_REPAIR_MAC);
            String serial = getIntent().getStringExtra(EXTRA_REPAIR_SERIAL);
            if (AuthRepairOffer.hasExactVehicleIdentity(mac, serial)) {
                pendingRepairMac = mac;
                pendingRepairSerial = serial;
                status.setText("认证无回复。请核对车辆后决定是否重新配对。");
                candidateMacs.add(mac);
                candidateSerials.add(serial);
            }
        }
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void initializePrototypeUi() {
        prototypeWeb = findViewById(R.id.pairing_prototype_web);
        prototypeWeb.getSettings().setJavaScriptEnabled(true);
        prototypeWeb.getSettings().setDomStorageEnabled(false);
        prototypeWeb.getSettings().setAllowFileAccess(true);
        prototypeWeb.getSettings().setAllowContentAccess(false);
        prototypeWeb.getSettings().setBlockNetworkLoads(true);
        prototypeWeb.getSettings().setAllowUniversalAccessFromFileURLs(false);
        prototypeWeb.getSettings().setAllowFileAccessFromFileURLs(false);
        prototypeWeb.addJavascriptInterface(new PairingBridge(), "BfgNative");
        prototypeWeb.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                prototypeReady = true;
                if (pendingRepairMac != null) {
                    pushPrototype("pair-ready", "旧凭据失效，请核对车辆后重新配对。");
                    try {
                        JSONObject update = new JSONObject();
                        update.put("vehicleSn", pendingRepairSerial);
                        update.put("selectedPairIndex", 0);
                        view.evaluateJavascript("window.bfgNativeUpdate(" + update + ")", null);
                    } catch (JSONException ignored) { }
                } else {
                    pushPrototype("pair-list", "");
                    scan();
                }
            }
        });
        prototypeWeb.loadUrl("file:///android_asset/bfg-calibration-flow.html");
    }

    private final class PairingBridge {
        @JavascriptInterface public void action(String action, String value) {
            runOnUiThread(() -> {
                if ("pair-scan".equals(action)) scan();
                else if ("modal-state".equals(action)) updateModalSystemBars("open".equals(value));
                else if ("pair-select".equals(action)) stopScan();
                else if ("open-bluetooth-settings".equals(action))
                    startActivity(new android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
                else if ("begin-pair".equals(action)) {
                    if (!permissionsReady()) {
                        pushPrototype("pair-ready", "请先允许附近设备权限，再点击发起配对。");
                        return;
                    }
                    int index;
                    try { index = Integer.parseInt(value); } catch (NumberFormatException e) { index = -1; }
                    if (index >= 0 && index < candidateMacs.size()) {
                        stopScan();
                        beginConnection(candidateMacs.get(index), candidateSerials.get(index),
                                BfgBleClient.Operation.PAIR_AND_READ);
                    } else pushPrototype("pair-list", "请重新扫描并选择车辆。");
                } else if ("cancel".equals(action) || "vehicles".equals(action)
                        || "settings".equals(action)) finishPairing();
                else if ("screen".equals(action) && ("home".equals(value)
                        || "vehicles".equals(value) || "settings".equals(value))) {
                    android.content.Intent response = new android.content.Intent();
                    response.putExtra("bfg_return_screen", value);
                    setResult(RESULT_CANCELED, response);
                    finishPairing();
                }
            });
        }
    }

    private void pushPrototype(String screen, String message) {
        if (!prototypeReady || prototypeWeb == null) return;
        try {
            JSONObject data = new JSONObject();
            data.put("screen", screen);
            data.put("dark", (getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES);
            data.put("errorMessage", message);
            JSONArray vehicles = new JSONArray();
            for (String serial : candidateSerials) {
                JSONObject item = new JSONObject();
                item.put("sn", serial);
                item.put("detail", "核对序列号，确认是自己的车辆");
                vehicles.put(item);
            }
            data.put("vehicles", vehicles);
            prototypeWeb.evaluateJavascript("window.bfgNativeUpdate(" + data + ")", null);
        } catch (JSONException ignored) { }
    }

    private void pushBusy(String value) {
        if (!prototypeReady || prototypeWeb == null) return;
        try {
            JSONObject update = new JSONObject();
            update.put("busyMessage", value);
            prototypeWeb.evaluateJavascript("window.bfgNativeUpdate(" + update + ")", null);
        } catch (JSONException ignored) { }
    }

    private void pushScanProgress(boolean scanning, int secondsLeft) {
        if (!prototypeReady || prototypeWeb == null) return;
        try {
            JSONObject update = new JSONObject();
            update.put("pairScanning", scanning);
            update.put("pairScanSeconds", Math.max(0, secondsLeft));
            prototypeWeb.evaluateJavascript("window.bfgNativeUpdate(" + update + ")", null);
        } catch (JSONException ignored) { }
    }

    private void cancelActiveClient() {
        connectionGeneration++;
        BfgBleClient active = client;
        client = null;
        if (active != null) active.close();
    }

    private void finishPairing() {
        stopScan();
        cancelActiveClient();
        finish();
    }

    @Override @Deprecated public void onBackPressed() {
        finishPairing();
    }

    private void offerPendingRepair() {
        if (pendingRepairMac == null || isFinishing() || isDestroyed()) return;
        if (!permissionsReady()) return;
        String mac = pendingRepairMac;
        String serial = pendingRepairSerial;
        pendingRepairMac = null;
        pendingRepairSerial = null;
        confirmNewPair(mac, serial);
    }

    private boolean permissionsReady() {
        List<String> needed = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (needed.isEmpty()) return true;
        requestPermissions(needed.toArray(new String[0]), 901);
        return false;
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 901) {
            boolean granted = results.length > 0;
            for (int result : results) granted &= result == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingRepairMac != null) pushPrototype("pair-ready", "");
            else if (granted) scan();
            else showPrototypeModal("permission-needed", "需要位置和附近设备权限才能扫描车辆。", "pair-list");
        }
    }

    @SuppressLint("MissingPermission")
    private void scan() {
        if (!permissionsReady()) return;
        if (client != null) {
            cancelActiveClient();
        }
        stopScan();
        seen.clear();
        candidates.removeAllViews();
        candidateMacs.clear();
        candidateSerials.clear();
        pushPrototype("pair-list", "");
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            status.setText("请先开启系统蓝牙。");
            showPrototypeModal("bluetooth-off", "系统蓝牙尚未开启。请打开蓝牙后返回重新扫描。", "pair-list");
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            status.setText("手机蓝牙扫描不可用。");
            return;
        }
        status.setText("正在扫描 12 秒；请唤醒车辆并靠近手机…");
        pushBusy("正在扫描附近车辆…");
        scanButton.setEnabled(false);
        scanCallback = new ScanCallback() {
            @Override public void onScanResult(int callbackType, ScanResult result) {
                if (result == null || result.getDevice() == null) return;
                BluetoothDevice device = result.getDevice();
                String name = result.getScanRecord() == null ? null : result.getScanRecord().getDeviceName();
                if (name == null || name.trim().isEmpty()) name = device.getName();
                if (name == null || !name.matches("(?i)[A-Z0-9]{14}")) return;
                String mac = device.getAddress();
                if (mac == null || !seen.add(mac)) return;
                String serial = name.toUpperCase(Locale.US);
                candidateMacs.add(mac);
                candidateSerials.add(serial);
                pushPrototype("pair-list", "");
                View choice = getLayoutInflater().inflate(
                        R.layout.item_pairing_candidate, candidates, false);
                TextView serialView = choice.findViewById(R.id.pairing_candidate_serial);
                TextView macView = choice.findViewById(R.id.pairing_candidate_mac);
                serialView.setText(serial);
                macView.setText("蓝牙地址  " + mac);
                choice.setOnClickListener(v -> confirmPair(mac, serial));
                candidates.addView(choice);
            }

            @Override public void onScanFailed(int errorCode) {
                status.setText("扫描失败（" + errorCode + "）。请检查蓝牙权限或关闭占用蓝牙的 App。");
                pushPrototype("pair-list", "扫描失败，请检查蓝牙权限和其他应用占用情况。");
                stopScan();
            }
        };
        try {
            scanner.startScan(null, new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback);
        } catch (Exception error) {
            stopScan();
            pushPrototype("pair-list", "无法启动蓝牙扫描，请检查附近设备和位置权限后重试。");
            return;
        }
        final long thisScan = ++scanGeneration;
        final long scanEndsAt = android.os.SystemClock.elapsedRealtime() + 12000;
        pushScanProgress(true, 12);
        Runnable progressTick = new Runnable() {
            @Override public void run() {
                if (scanCallback == null || scanGeneration != thisScan) return;
                int left = (int) Math.max(0,
                        (scanEndsAt - android.os.SystemClock.elapsedRealtime() + 999) / 1000);
                pushScanProgress(true, left);
                if (left > 0) main.postDelayed(this, 250);
            }
        };
        main.postDelayed(progressTick, 250);
        main.postDelayed(() -> {
            if (scanCallback == null || scanGeneration != thisScan) return;
            stopScan();
            status.setText(seen.isEmpty() ? "没有找到广播序列号的车辆；请唤醒车辆后重试。"
                    : "扫描结束。请核对序列号并选择自己的车辆。");
            if (seen.isEmpty()) pushPrototype("pair-list", "没有找到车辆，请唤醒车辆并重试。");
        }, 12000);
    }

    private void confirmPair(String mac, String serial) {
        stopScan();
        new MaterialAlertDialogBuilder(this)
                .setTitle("请先将车辆开机")
                .setMessage("车辆序列号：" + serial
                        + "\n\n请让车辆保持开机并靠近手机，同时关闭其他正在连接车辆的应用或手机。")
                .setNegativeButton("取消", null)
                .setPositiveButton("车辆已开机，继续", (dialog, which) ->
                        confirmPairAfterPowerOn(mac, serial))
                .show();
    }

    private void confirmPairAfterPowerOn(String mac, String serial) {
        byte[] saved = PairingCredentialStore.load(this, mac);
        if (saved != null) {
            java.util.Arrays.fill(saved, (byte) 0);
            new MaterialAlertDialogBuilder(this)
                    .setTitle("已保存此车的配对凭据")
                    .setMessage("车辆序列号：" + serial
                            + "\n\n可直接连接读取，无需再次按车上按钮。只有旧凭据失效时才重新配对。")
                    .setNegativeButton("取消", null)
                    .setNeutralButton("重新配对", (dialog, which) -> confirmNewPair(mac, serial))
                    .setPositiveButton("连接读取", (dialog, which) ->
                            beginConnection(mac, serial, BfgBleClient.Operation.COMPARE_READ))
                    .show();
            return;
        }
        confirmNewPair(mac, serial);
    }

    private void confirmNewPair(String mac, String serial) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("确认临时密钥配对")
                .setMessage("车辆序列号：" + serial + "\n\n请再次核对这是你的车辆，并准备在车上按键确认。"
                        + "配对过程不读取九号账号或数据库；成功后，官方 App 的旧凭据可能需要重新配对。"
                        + "临时密钥只在本次运行期间有效，彻底结束应用后需要重新配对。本次不会写入车辆电量参数。")
                .setNegativeButton("取消", null)
                .setPositiveButton("发起配对", (dialog, which) ->
                        beginConnection(mac, serial, BfgBleClient.Operation.PAIR_AND_READ))
                .show();
    }

    private void beginConnection(String mac, String serial, BfgBleClient.Operation operation) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            showPrototypeModal("bluetooth-off", "系统蓝牙尚未开启。请打开后重新发起配对。", "pair-ready");
            return;
        }
        DeviceRecord record = new DeviceRecord(-1, mac, serial, serial, "", new byte[16], "pair_lab");
        status.setText("正在连接 " + serial + "；请保持车辆开启。");
        scanButton.setEnabled(false);
        cancelActiveClient();
        final long thisConnection = connectionGeneration;
        client = new BfgBleClient(this, record, operation,
                0, -1, false, new BfgBleClient.Listener() {
            @Override public void onStatus(String value) {
                if (thisConnection != connectionGeneration || isFinishing()) return;
                status.setText(value);
                pushBusy(value);
            }
            @Override public void onLog(String value) { /* Credentials and raw frames never enter the UI. */ }
            @Override public void onResult(BfgBleClient.Result result) {
                if (thisConnection != connectionGeneration || isFinishing()) return;
                client = null;
                scanButton.setEnabled(true);
                int capacity = result.displayBeforeCapacity();
                status.setText((result.pairingConfirmed ? "配对并读取成功" : "连接并读取成功")
                        + "\n仪表校准电压："
                        + result.dashboardNominalVoltage() + "V\n计量校准电压："
                        + result.meterNominalVoltage() + "V\n电量："
                        + result.displaySoc() + "%\n容量："
                        + (capacity > 0 ? String.format(Locale.US, "%.1fAh", capacity / 1000.0) : "未确认"));
                android.content.Intent response = new android.content.Intent();
                response.putExtra("bfg_paired_mac", mac);
                setResult(RESULT_OK, response);
                finish();
            }
            @Override public void onError(String error) {
                if (thisConnection != connectionGeneration || isFinishing()) return;
                client = null;
                scanButton.setEnabled(true);
                status.setText("配对/读取未完成：" + error);
                pushPrototype("pair-ready", "配对未完成，请确认车辆已开机并重试。");
                if (operation != BfgBleClient.Operation.PAIR_AND_READ
                        && AuthRepairOffer.isAuthTimeout(error)
                        && !isFinishing()) {
                    if (prototypeReady) {
                        showPrototypeModal("auth-repair", "认证没有回应。请确认没有其他手机占用蓝牙；重新配对需要在车上确认。", "pair-ready");
                        return;
                    }
                    new MaterialAlertDialogBuilder(PairingLabActivity.this)
                            .setTitle("车辆认证没有回应")
                            .setMessage("先确认没有其他应用或手机占用车辆蓝牙。"
                                    + "若本机配对凭据已失效，可以重新配对，"
                                    + "但需要再次核对车辆并在车上按键确认。")
                            .setNegativeButton("稍后", null)
                            .setPositiveButton("重新配对", (dialog, which) ->
                                    confirmNewPair(mac, serial))
                            .show();
                }
            }
        });
        client.connect();
    }

    private void showPrototypeModal(String modal, String message, String screen) {
        if (!prototypeReady || prototypeWeb == null) return;
        try {
            JSONObject update = new JSONObject();
            update.put("modal", modal);
            update.put("errorMessage", message);
            update.put("screen", screen);
            prototypeWeb.evaluateJavascript("window.bfgNativeUpdate(" + update + ")", null);
        } catch (JSONException ignored) { }
    }

    private void updateModalSystemBars(boolean modalOpen) {
        boolean darkMode = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int statusBackground = modalOpen
                ? (darkMode ? Color.rgb(6, 10, 16) : Color.rgb(109, 112, 125))
                : getColor(R.color.ninebot_background);
        View appRoot = findViewById(R.id.pairing_root);
        if (appRoot != null) appRoot.setBackgroundColor(statusBackground);
        getWindow().setStatusBarColor(statusBackground);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!darkMode && !modalOpen);
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        scanGeneration++;
        if (scanner != null && scanCallback != null) {
            try { scanner.stopScan(scanCallback); } catch (Exception ignored) { }
        }
        scanner = null;
        scanCallback = null;
        pushScanProgress(false, 0);
        if (scanButton != null) scanButton.setEnabled(true);
    }

    @Override protected void onDestroy() {
        stopScan();
        cancelActiveClient();
        if (prototypeWeb != null) {
            prototypeWeb.removeJavascriptInterface("BfgNative");
            prototypeWeb.destroy();
            prototypeWeb = null;
        }
        super.onDestroy();
    }
}
