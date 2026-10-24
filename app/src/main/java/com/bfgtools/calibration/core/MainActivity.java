package com.bfgtools.calibration.core;

import android.Manifest;
import android.webkit.JavascriptInterface;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedCallback;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.onebitmonochrome.blacksbbox.R;
import com.onebitmonochrome.blacksbbox.app.ninebot.NinebotReturnOverlayManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.InstallResult;

public class MainActivity extends Activity implements BfgBleClient.Listener {
    private static final int PAGE_HOME = 0;
    private static final int PAGE_VEHICLES = 1;
    private static final int PAGE_OPERATION = 2;
    private static final int PAGE_SETTINGS = 3;
    private static final int PAGE_WRITE_COMPLETE = 4;
    private static final int REQ_BT = 1001;
    private static final int REQ_IMPORT_NINEBOT_APK = 1002;
    private static final int REQ_PAIRING_LAB = 1003;
    private static final long MAX_IMPORTED_APK_BYTES = 1024L * 1024L * 1024L;
    private static final String PREFS = "bfg_profile_backup";
    private static final String KEY_ROOT_MODE = "root_mode_enabled";
    private static final String KEY_ROOT_FALLBACK = "root_fallback_enabled";
    private static final String KEY_SELECTED_VEHICLE = "selected_vehicle_key";
    private static final String KEY_FIRST_AGREEMENT_ACCEPTED = "first_agreement_accepted";
    private static final Set<String> APPROVED_METER_RISK_SESSION_KEYS = new HashSet<>();
    private static final String KEY_TOOL_NIGHT_MODE_BEFORE_GUEST =
            "tool_night_mode_before_guest";
    private static final String NINEBOT_PACKAGE = "cn.ninebot.ninebot";
    private static final String RELEASE_PROVENANCE_ID = "BFG-AuroraMapleGood-2026";

    private static final String[] VOLTAGE_NAMES = {"72V","60V","48V"};


    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<DeviceRecord> devices = new ArrayList<>();
    private Button loginButton, loadButton, clearVirtualNinebotButton;
    private View readButton;
    private Button compareButton, writeEntryButton, writeButton, restoreButton;
    private Button writeDisVoltageButton, restoreDisVoltageButton;
    private Button writeCompleteDoneButton, writeCompleteRestoreButton;
    private Button exportDiagnosticButton, writeCompleteExportDiagnosticButton;
    private Button capacityPickerButton;
    private Button homeSyncButton, virtualEnvButton, homeImportNinebotButton;
    private Button importNinebotApkButton;
    private TextView status, vehicleListView, result, backupView, logView;
    private TextView sourceModeView, sourceDetailView, sourceReadyBadge;
    private TextView sourceVehicleCountView, sourceSyncTimeView, rootModeStatusView;
    private TextView importedNinebotStatusView;
    private TextView currentVehicleName, currentVehicleMeta, currentVehicleBadge;
    private TextView vehiclePickerTitle, vehiclePickerDetail;
    private TextView operationState, targetSummary, settingsSourceView, settingsAboutVersionView;
    private TextView diagnosticStatusView;
    private TextView writeProgressText, writeInlineResultTitle, writeInlineResultText;
    private TextView writeCompleteBackupSummary, writeCompleteTitle, writeCompleteSubtitle;
    private TextView writeCompleteBefore, writeCompleteAfter;
    private TextView writeCompleteCheckCommand, writeCompleteCheckReadback;
    private TextView homeSocValue, homeVoltageBandValue, homeCapacityValue, homeReadState;
    private TextView connectedVehicleIdentity, connectedVehicleModel;
    private TextView connectedSocValue, connectedBatteryVoltageValue;
    private TextView connectedRemainingCapacityValue;
    private TextView connectedDashboardVoltageValue, connectedVoltageGroupValue, connectedCapacityValue;
    private TextView connectedModeNotice;
    private TextView firmwareDashboardValue, firmwareMeterValue;
    private TextView calibrationVehicleIdentity, calibrationCurrentSummary;
    private TextView calibrationFirmwareSummary, calibrationModeSummary;
    private ViewFlipper pageFlipper;
    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNavigation;
    private MaterialButtonToggleGroup modeToggle;
    private MaterialButtonToggleGroup voltageGroup;
    private LinearLayout capacityGrid;
    private SwitchMaterial rootModeSwitch, rootFallbackSwitch;
    private View writePanel, resultCard, operationStatusCard, technicalDetails, technicalToggleButton;
    private View capacityOptionsContainer, operationScroll;
    private View writeProgressPanel, writeInlineResult;
    private View connectedVehicleCard;
    private View connectedFirmwareTitle, connectedFirmwareRows;
    private Button connectedCalibrateButton;
    private View homeScroll;
    private View vehiclePickerCard;
    private ProgressBar operationProgress;
    private BfgBleClient client;
    private WebView prototypeWeb;
    private boolean prototypeReady;
    private boolean prototypeModalVisible;
    private String prototypeScreen = "home";
    private BfgBleClient.Result prototypeLastRead;
    private String prototypeWriteType = "meter";
    private Future<?> operationPreparation;
    private BfgBleClient.Operation activeBleOperation;
    private int operationGeneration;
    private OnBackInvokedCallback backInvokedCallback;
    private DeviceRecord activeDevice;
    private int lastReadProfile = -1;
    private int lastReadCapacity = -1;
    private int lastReadDisConfigRaw = -1;
    private boolean allowUnverifiedDisWrite;
    private int pendingUnverifiedVoltage;
    private boolean lastReadWriteSupported;
    private CommunicationModeResolver.Mode lastCommunicationMode =
            CommunicationModeResolver.Mode.UNSUPPORTED;
    private boolean running;
    private boolean reloadAfterVirtualLogin;
    private boolean pendingReadAfterCredentials;
    private boolean pendingWriteAfterRead;
    private boolean restoringBackup;
    private boolean changingMode;
    private boolean changingRootMode;
    private boolean pairingLabOpened;
    private boolean pendingReadAfterPairing;
    private boolean pendingPostWriteVerification;
    private Runnable delayedPostWriteRead;
    private int postWriteExpectedProfile = -1;
    private int postWriteExpectedDisRaw = -1;
    private int postWriteCheckAttempts;
    private String pendingRetryWriteRequest;
    private BfgBleClient.Operation pendingPrewriteOperation;
    private DeviceRecord pendingPrewriteDevice;
    private int pendingPrewriteTarget;
    private boolean pendingPrewriteUnverified;
    private boolean prewriteApproved;
    private boolean dashboardRiskAccepted;
    private boolean meterRiskAccepted;
    private String lastAttemptedWriteType = "meter";
    private int lastAttemptedVoltage = -1;
    private double lastAttemptedCapacity = -1;
    private String pendingPairedMac;
    private DeviceRecord pendingPermissionDevice;
    private BfgBleClient.Operation pendingPermissionOperation;
    private int pendingPermissionTarget;
    private int selectedVoltageCode = 1;
    private int selectedProfileIndex = 5;
    private int virtualUserId;
    private DiagnosticRecorder diagnosticRecorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PairingCredentialStore.purgeLegacy(this);
        configureSystemBars();
        registerSystemBackHandler();
        diagnosticRecorder = new DiagnosticRecorder(this);
        virtualUserId = getIntent().getIntExtra("userID", 0);
        reloadAfterVirtualLogin = getIntent().getBooleanExtra(
                NinebotReturnOverlayManager.EXTRA_RETURN_FROM_NINEBOT, false);
        buildUi();
        // Startup only refreshes available data. Login/connection guidance is
        // shown after the user explicitly requests that action, so the rest
        // of the tool remains accessible when no database exists yet.
        if (!AppIntegrity.isTrusted(this)) {
            showIntegrityFailureDialog();
        } else if (getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_FIRST_AGREEMENT_ACCEPTED, false)) {
            loadVehicles(false);
        } else {
            showFirstAgreementDialog();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra(
                NinebotReturnOverlayManager.EXTRA_RETURN_FROM_NINEBOT, false)) {
            reloadAfterVirtualLogin = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The embedded guest can leave night-mode and system-bar flags on the
        // shared task. Always restore the tool chrome when it becomes visible.
        configureSystemBars();
        refreshImportedNinebotUi();
        if (pairingLabOpened && !running) {
            pairingLabOpened = false;
            prototypeGo("home");
        }
        if (reloadAfterVirtualLogin && !running) {
            reloadAfterVirtualLogin = false;
            restoreToolAppearanceAfterNinebot();
            loadVehicles(false);
        }
    }

    private void restoreToolAppearanceAfterNinebot() {
        // The guest app can replace this process' Resources configuration.
        // The tool's own setting is authoritative when we return.
        SharedPreferences appearance = PreferenceManager.getDefaultSharedPreferences(this);
        int currentNightMode = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        int desiredNightMode;
        if (appearance.contains("dark_mode")) {
            desiredNightMode = appearance.getBoolean("dark_mode", false)
                    ? Configuration.UI_MODE_NIGHT_YES
                    : Configuration.UI_MODE_NIGHT_NO;
        } else {
            // No explicit choice: preserve the actual palette shown before
            // entering the guest, which normally follows the phone setting.
            desiredNightMode = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(
                    KEY_TOOL_NIGHT_MODE_BEFORE_GUEST, currentNightMode);
        }
        Configuration restored = new Configuration(getResources().getConfiguration());
        restored.uiMode = (restored.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | desiredNightMode;
        getResources().updateConfiguration(restored,
                getResources().getDisplayMetrics());
        getTheme().applyStyle(R.style.Theme_BlackBox_Ninebot, true);
        // Reinflate so every resolved text/background color comes from the
        // current day/night palette instead of the guest app's stale palette.
        buildUi();
        configureSystemBars();
    }

    private void buildUi() {
        if (prototypeWeb != null) {
            prototypeWeb.removeJavascriptInterface("BfgNative");
            prototypeWeb.destroy();
            prototypeWeb = null;
            prototypeReady = false;
        }
        setContentView(R.layout.activity_ninebot_tool);

        toolbar = findViewById(R.id.ninebot_toolbar);
        pageFlipper = findViewById(R.id.ninebot_pages);
        bottomNavigation = findViewById(R.id.ninebot_bottom_nav);
        applySystemBarInsets(findViewById(R.id.ninebot_root));
        homeScroll = findViewById(R.id.home_scroll);
        status = findViewById(R.id.status);

        sourceModeView = findViewById(R.id.source_mode);
        sourceDetailView = findViewById(R.id.source_detail);
        sourceReadyBadge = findViewById(R.id.source_ready_badge);
        sourceVehicleCountView = findViewById(R.id.source_vehicle_count);
        sourceSyncTimeView = findViewById(R.id.source_sync_time);
        currentVehicleName = findViewById(R.id.current_vehicle_name);
        currentVehicleMeta = findViewById(R.id.current_vehicle_meta);
        currentVehicleBadge = findViewById(R.id.current_vehicle_badge);
        homeSocValue = findViewById(R.id.home_soc_value);
        homeVoltageBandValue = findViewById(R.id.home_voltage_band_value);
        homeCapacityValue = findViewById(R.id.home_capacity_value);
        homeReadState = findViewById(R.id.home_read_state);
        connectedVehicleCard = findViewById(R.id.connected_vehicle_card);
        connectedVehicleIdentity = findViewById(R.id.connected_vehicle_identity);
        connectedVehicleModel = findViewById(R.id.connected_vehicle_model);
        connectedSocValue = findViewById(R.id.connected_soc_value);
        connectedBatteryVoltageValue = findViewById(R.id.connected_battery_voltage_value);
        connectedRemainingCapacityValue = findViewById(
                R.id.connected_remaining_capacity_value);
        connectedDashboardVoltageValue = findViewById(R.id.connected_dashboard_voltage_value);
        connectedVoltageGroupValue = findViewById(R.id.connected_voltage_group_value);
        connectedCapacityValue = findViewById(R.id.connected_capacity_value);
        connectedModeNotice = findViewById(R.id.connected_mode_notice);
        firmwareDashboardValue = findViewById(R.id.firmware_dashboard_value);
        firmwareMeterValue = findViewById(R.id.firmware_meter_value);
        connectedFirmwareTitle = findViewById(R.id.connected_firmware_title);
        connectedFirmwareRows = findViewById(R.id.connected_firmware_rows);
        connectedCalibrateButton = findViewById(R.id.connected_calibrate_button);
        calibrationVehicleIdentity = findViewById(R.id.calibration_vehicle_identity);
        calibrationCurrentSummary = findViewById(R.id.calibration_current_summary);
        calibrationFirmwareSummary = findViewById(R.id.calibration_firmware_summary);
        calibrationModeSummary = findViewById(R.id.calibration_mode_summary);
        settingsSourceView = findViewById(R.id.settings_source);
        settingsAboutVersionView = findViewById(R.id.settings_about_version);
        settingsAboutVersionView.setText("版本：" + installedVersionName());

        loginButton = findViewById(R.id.login_button);
        loadButton = findViewById(R.id.load_button);
        clearVirtualNinebotButton = findViewById(R.id.clear_virtual_ninebot_button);
        importNinebotApkButton = findViewById(R.id.import_ninebot_apk_button);
        importedNinebotStatusView = findViewById(R.id.imported_ninebot_status);
        virtualEnvButton = findViewById(R.id.virtual_env_button);
        homeImportNinebotButton = findViewById(R.id.home_import_ninebot_button);
        homeSyncButton = findViewById(R.id.home_sync_button);
        vehicleListView = findViewById(R.id.vehicle_list);
        vehiclePickerCard = findViewById(R.id.vehicle_picker_card);
        vehiclePickerTitle = findViewById(R.id.vehicle_picker_title);
        vehiclePickerDetail = findViewById(R.id.vehicle_picker_detail);

        readButton = findViewById(R.id.button_read);
        compareButton = findViewById(R.id.button_compare);
        writeEntryButton = findViewById(R.id.write_entry_button);
        writeButton = findViewById(R.id.write_button);
        writeDisVoltageButton = findViewById(R.id.write_dis_voltage_button);
        restoreDisVoltageButton = findViewById(R.id.restore_dis_voltage_button);
        restoreButton = findViewById(R.id.restore_button);
        capacityPickerButton = findViewById(R.id.capacity_picker_button);
        voltageGroup = findViewById(R.id.voltage_group);
        capacityGrid = findViewById(R.id.capacity_grid);
        modeToggle = findViewById(R.id.mode_toggle);
        resultCard = findViewById(R.id.result_card);
        operationStatusCard = findViewById(R.id.operation_status_card);
        writePanel = findViewById(R.id.write_panel);
        technicalDetails = findViewById(R.id.technical_details);
        technicalToggleButton = findViewById(R.id.technical_toggle_button);
        operationScroll = findViewById(R.id.operation_scroll);
        operationProgress = findViewById(R.id.operation_progress);
        operationState = findViewById(R.id.operation_state);
        targetSummary = findViewById(R.id.target_summary);
        capacityOptionsContainer = findViewById(R.id.capacity_options_container);
        writeProgressPanel = findViewById(R.id.write_progress_panel);
        writeProgressText = findViewById(R.id.write_progress_text);
        writeInlineResult = findViewById(R.id.write_inline_result);
        writeInlineResultTitle = findViewById(R.id.write_inline_result_title);
        writeInlineResultText = findViewById(R.id.write_inline_result_text);
        writeCompleteDoneButton = findViewById(R.id.write_complete_done_button);
        writeCompleteRestoreButton = findViewById(R.id.write_complete_restore_button);
        writeCompleteBackupSummary = findViewById(R.id.write_complete_backup_summary);
        writeCompleteTitle = findViewById(R.id.write_complete_title);
        writeCompleteSubtitle = findViewById(R.id.write_complete_subtitle);
        writeCompleteBefore = findViewById(R.id.write_complete_before);
        writeCompleteAfter = findViewById(R.id.write_complete_after);
        writeCompleteCheckCommand = findViewById(R.id.write_complete_check_command);
        writeCompleteCheckReadback = findViewById(R.id.write_complete_check_readback);
        writeCompleteExportDiagnosticButton = findViewById(R.id.write_complete_export_diagnostic_button);
        result = findViewById(R.id.result);
        backupView = findViewById(R.id.backup_view);
        logView = findViewById(R.id.log_view);
        rootModeSwitch = findViewById(R.id.root_mode_switch);
        rootFallbackSwitch = findViewById(R.id.root_fallback_switch);
        rootModeStatusView = findViewById(R.id.root_mode_status);
        exportDiagnosticButton = findViewById(R.id.export_diagnostic_button);
        diagnosticStatusView = findViewById(R.id.diagnostic_status);
        refreshDiagnosticStatus();

        loginButton.setOnClickListener(v -> beginVirtualLogin());
        virtualEnvButton.setOnClickListener(v -> beginVirtualLogin());
        loadButton.setOnClickListener(v -> loadVehicles());
        homeSyncButton.setOnClickListener(v -> loadVehicles());
        clearVirtualNinebotButton.setOnClickListener(v -> confirmClearVirtualNinebotData());
        importNinebotApkButton.setOnClickListener(v -> showNinebotImportOptions());
        homeImportNinebotButton.setOnClickListener(v -> showNinebotImportOptions());
        vehiclePickerCard.setOnClickListener(v -> showVehiclePickerDialog());

        readButton.setOnClickListener(v -> {
            pendingWriteAfterRead = true;
            if (selectedDevice() == null) {
                pendingReadAfterCredentials = true;
                status.setText("正在查找车辆凭据；若尚未准备数据，请导入九号出行 APK 并登录，或使用 Root 读取原机数据库。");
                loadVehicles(true);
            } else {
                confirmVehiclePowered(false, () -> {
                    showOperation(false);
                    startRead();
                });
            }
        });
        compareButton.setOnClickListener(v -> {
            pendingWriteAfterRead = false;
            showOperation(false);
            startCompareRead();
        });
        writeEntryButton.setOnClickListener(v -> {
            pendingWriteAfterRead = true;
            showOperation(false);
            startRead();
        });
        connectedCalibrateButton.setOnClickListener(v -> {
            pendingWriteAfterRead = false;
            showWritePanel();
        });
        writeButton.setOnClickListener(v -> confirmWriteSelected());
        writeDisVoltageButton.setOnClickListener(v -> confirmDisVoltageWrite());
        restoreDisVoltageButton.setOnClickListener(v -> confirmDisVoltageRestore());
        restoreButton.setOnClickListener(v -> confirmRestore());
        writeCompleteDoneButton.setOnClickListener(v -> showPage(PAGE_HOME));
        writeCompleteRestoreButton.setOnClickListener(v -> confirmRestore());
        writeCompleteExportDiagnosticButton.setOnClickListener(v -> exportDiagnosticData());
        findViewById(R.id.write_complete_backup_view).setOnClickListener(v -> showBackupDetails());
        capacityPickerButton.setOnClickListener(v -> toggleCapacityOptions());
        findViewById(R.id.switch_vehicle_button).setOnClickListener(v -> showPage(PAGE_VEHICLES));
        findViewById(R.id.pairing_lab_button).setOnClickListener(v -> {
            pairingLabOpened = true;
            startActivityForResult(new android.content.Intent(this, PairingLabActivity.class),
                    REQ_PAIRING_LAB);
        });
        findViewById(R.id.settings_go_vehicles).setOnClickListener(v -> showPage(PAGE_VEHICLES));
        findViewById(R.id.settings_view_license).setOnClickListener(v -> showLicenseDetails());
        technicalToggleButton.setOnClickListener(v -> toggleTechnicalDetails());
        exportDiagnosticButton.setOnClickListener(v -> exportDiagnosticData());

        voltageGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            int id = checkedId;
            selectedVoltageCode = id == R.id.voltage_72 ? 0 : id == R.id.voltage_48 ? 2 : 1;
            populateCapacityGrid(selectedProfileIndex);
        });

        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        changingRootMode = true;
        rootModeSwitch.setChecked(preferences.getBoolean(KEY_ROOT_MODE, false));
        rootFallbackSwitch.setChecked(preferences.getBoolean(KEY_ROOT_FALLBACK, true));
        changingRootMode = false;
        rootModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (changingRootMode) return;
            preferences.edit().putBoolean(KEY_ROOT_MODE, isChecked).apply();
            refreshRootModeUi();
            if (!running) loadVehicles(false);
        });
        rootFallbackSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (changingRootMode) return;
            preferences.edit().putBoolean(KEY_ROOT_FALLBACK, isChecked).apply();
            refreshRootModeUi();
        });
        refreshRootModeUi();
        refreshImportedNinebotUi();

        modeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || changingMode) return;
            if (checkedId == R.id.calibrate_tab) {
                pendingWriteAfterRead = true;
                if (lastReadProfile >= 0) showWritePanel();
                else if (!running && selectedDevice() != null) startRead();
            } else {
                pendingWriteAfterRead = false;
                writePanel.setVisibility(View.GONE);
            }
        });

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            int target = id == R.id.nav_ninebot_vehicles ? PAGE_VEHICLES
                    : id == R.id.nav_ninebot_settings ? PAGE_SETTINGS : PAGE_HOME;
            return navigateAwayFromCurrentOperation(target);
        });
        toolbar.setNavigationOnClickListener(v -> handleBackNavigation());

        populateCapacityGrid(5);
        resetHomeReadout();
        showPage(PAGE_HOME);
        setBusy(false);
        initializePrototypeUi();
    }

    private void showFirstAgreementDialog() {
        if (isFinishing() || isDestroyed()) return;
        View content = getLayoutInflater().inflate(R.layout.dialog_first_agreement, null);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .setNeutralButton(R.string.ninebot_agreement_details, null)
                .setPositiveButton(R.string.ninebot_agreement_accept, (whichDialog, which) -> {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putBoolean(KEY_FIRST_AGREEMENT_ACCEPTED, true)
                            .apply();
                    loadVehicles(false);
                })
                .setCancelable(false)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(ignored -> {
            fitDialogContentToScreen(dialog, content);
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(v -> showLicenseDetails());
            Button agree = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            long readyAt = SystemClock.elapsedRealtime() + 5000;
            Runnable tick = new Runnable() {
                @Override public void run() {
                    if (!dialog.isShowing()) return;
                    int seconds = (int) Math.max(0,
                            (readyAt - SystemClock.elapsedRealtime() + 999) / 1000);
                    agree.setEnabled(seconds == 0);
                    agree.setText(seconds == 0 ? "同意并继续" : "同意并继续（" + seconds + "秒）");
                    if (seconds > 0) mainHandler.postDelayed(this, 250);
                }
            };
            tick.run();
        });
        dialog.show();
    }

    private void refreshDiagnosticStatus() {
        if (diagnosticStatusView == null || diagnosticRecorder == null) return;
        boolean available = diagnosticRecorder.hasData();
        diagnosticStatusView.setText(available
                ? "已保存最近一次连接诊断。协议阶段和容量回包会写入TXT；车辆完整序列号、蓝牙地址及认证材料已隐藏。"
                : "尚无连接诊断数据。连接车辆完成一次读取或校准后即可导出。" );
        if (exportDiagnosticButton != null) exportDiagnosticButton.setEnabled(available);
        if (writeCompleteExportDiagnosticButton != null) {
            writeCompleteExportDiagnosticButton.setEnabled(available);
        }
    }

    private void exportDiagnosticData() {
        if (diagnosticRecorder == null || !diagnosticRecorder.share(this)) {
            android.widget.Toast.makeText(this,
                    "还没有可导出的诊断数据，请先连接车辆完成一次读取或校准。",
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        refreshDiagnosticStatus();
    }

    private String installedVersionName() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return version == null || version.isEmpty() ? "未知" : version;
        } catch (Throwable ignored) {
            return "未知";
        }
    }

    private void showLicenseDetails() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("关于 BFG电量校准")
                .setMessage("发布署名：极光枫叶good\n发行标识：" + RELEASE_PROVENANCE_ID
                        + "\n\n" + readAssetText("license.txt"))
                .setPositiveButton("知道了", null)
                .show();
    }

    private String readAssetText(String fileName) {
        try (InputStream input = getAssets().open(fileName);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (Throwable t) {
            return "无法读取详细说明：" + t.getMessage();
        }
    }

    private void showIntegrityFailureDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("安装包完整性校验失败")
                .setMessage("当前安装包不是由本工具正式证书签名，可能已被重新打包或篡改。"
                        + "\n\n为保护车辆参数，工具已停止运行。请卸载当前版本，并通过正式发布渠道重新获取。")
                .setPositiveButton("退出", (dialog, which) -> finishAndRemoveTask())
                .setCancelable(false)
                .show();
    }

    private void configureSystemBars() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean darkMode = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int statusBackground = prototypeModalVisible
                ? (darkMode ? Color.rgb(6, 10, 16) : Color.rgb(109, 112, 125))
                : ContextCompat.getColor(this, R.color.ninebot_background);
        View appRoot = findViewById(R.id.ninebot_root);
        if (appRoot != null) appRoot.setBackgroundColor(statusBackground);
        getWindow().setStatusBarColor(statusBackground);
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.ninebot_surface));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }
        boolean darkIcons = !darkMode && !prototypeModalVisible;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(darkIcons);
        controller.setAppearanceLightNavigationBars(darkIcons);
        controller.show(WindowInsetsCompat.Type.systemBars());
    }

    private void registerSystemBackHandler() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        backInvokedCallback = this::handleBackNavigation;
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                backInvokedCallback);
    }

    private void applySystemBarInsets(View root) {
        if (root == null || toolbar == null || bottomNavigation == null) return;
        final int rootLeft = root.getPaddingLeft();
        final int rootRight = root.getPaddingRight();
        final int rootTop = root.getPaddingTop();
        final int rootBottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            // A WebView does not apply View padding to its HTML viewport. Put the entire
            // layout inside the safe area so headers and dialogs cannot sit under bars.
            view.setPadding(rootLeft + bars.left, rootTop + bars.top,
                    rootRight + bars.right, rootBottom + bars.bottom);
            if (prototypeWeb != null) prototypeWeb.setPadding(0, 0, 0, 0);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void showPage(int page) {
        if (page < PAGE_HOME || page > PAGE_WRITE_COMPLETE) page = PAGE_HOME;
        int previousPage = pageFlipper.getDisplayedChild();
        pageFlipper.setDisplayedChild(page);
        if (page == PAGE_OPERATION || page == PAGE_WRITE_COMPLETE) {
            toolbar.setNavigationIcon(R.drawable.ic_ninebot_back);
        } else {
            toolbar.setNavigationIcon((android.graphics.drawable.Drawable) null);
        }

        if (page == PAGE_VEHICLES) {
            toolbar.setTitle(R.string.ninebot_nav_vehicles);
            toolbar.setSubtitle(R.string.ninebot_subtitle_vehicles);
            bottomNavigation.getMenu().findItem(R.id.nav_ninebot_vehicles).setChecked(true);
        } else if (page == PAGE_SETTINGS) {
            toolbar.setTitle(R.string.ninebot_nav_settings);
            toolbar.setSubtitle(R.string.ninebot_subtitle_settings);
            bottomNavigation.getMenu().findItem(R.id.nav_ninebot_settings).setChecked(true);
        } else if (page == PAGE_OPERATION) {
            toolbar.setTitle(R.string.ninebot_operation_title);
            DeviceRecord device = selectedDevice();
            toolbar.setSubtitle(testVehicleLabel(device));
            bottomNavigation.getMenu().findItem(R.id.nav_ninebot_home).setChecked(true);
        } else if (page == PAGE_WRITE_COMPLETE) {
            toolbar.setTitle("电量校准");
            DeviceRecord device = selectedDevice();
            String identity = maskIdentity(testVehicleLabel(device));
            toolbar.setSubtitle(identity + (lastReadProfile >= 0
                    ? " · " + voltageName(lastReadProfile) : ""));
            bottomNavigation.getMenu().findItem(R.id.nav_ninebot_home).setChecked(true);
        } else {
            toolbar.setTitle(R.string.ninebot_title);
            toolbar.setSubtitle("连接、核对，再进行参数写入");
            bottomNavigation.getMenu().findItem(R.id.nav_ninebot_home).setChecked(true);
            if (homeScroll != null) homeScroll.post(() -> homeScroll.scrollTo(0, 0));
        }
        if (prototypeWeb != null) {
            boolean showPrototype = page != PAGE_VEHICLES && page != PAGE_SETTINGS;
            prototypeWeb.setVisibility(showPrototype ? View.VISIBLE : View.GONE);
            // The prototype owns its own toolbar and bottom navigation. Keeping the
            // legacy native bars visible makes them cover the bottom of HTML dialogs.
            toolbar.setVisibility(showPrototype ? View.GONE : View.VISIBLE);
            bottomNavigation.setVisibility(showPrototype ? View.GONE : View.VISIBLE);
            pageFlipper.setVisibility(showPrototype ? View.GONE : View.VISIBLE);
            if (page == PAGE_HOME && (previousPage == PAGE_VEHICLES
                    || previousPage == PAGE_SETTINGS)) prototypeGo("home");
        }
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void initializePrototypeUi() {
        prototypeReady = false;
        prototypeWeb = findViewById(R.id.bfg_prototype_web);
        prototypeWeb.setBackgroundColor(ContextCompat.getColor(this, R.color.ninebot_background));
        prototypeWeb.getSettings().setJavaScriptEnabled(true);
        prototypeWeb.getSettings().setDomStorageEnabled(false);
        prototypeWeb.getSettings().setAllowFileAccess(true);
        prototypeWeb.getSettings().setAllowContentAccess(false);
        prototypeWeb.getSettings().setBlockNetworkLoads(true);
        prototypeWeb.getSettings().setAllowUniversalAccessFromFileURLs(false);
        prototypeWeb.getSettings().setAllowFileAccessFromFileURLs(false);
        prototypeWeb.setVerticalScrollBarEnabled(false);
        prototypeWeb.addJavascriptInterface(new PrototypeBridge(), "BfgNative");
        prototypeWeb.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                prototypeReady = true;
                updatePrototypeData();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }
        });
        prototypeWeb.loadUrl("file:///android_asset/bfg-calibration-flow.html");
        // showPage(PAGE_HOME) runs before this WebView is initialized. Hide the
        // legacy chrome now as well, otherwise its native bottom bar covers
        // the WebView's first-launch dialogs.
        showPage(pageFlipper.getDisplayedChild());
        ViewCompat.requestApplyInsets(findViewById(R.id.ninebot_root));
    }

    private void updatePrototypeData() {
        if (!prototypeReady || prototypeWeb == null) return;
        try {
            JSONObject data = new JSONObject();
            DeviceRecord device = selectedDevice();
            data.put("vehicleSn", device == null ? "" : testVehicleLabel(device));
            data.put("vehicleModel", device == null ? "" : testVehicleDetail(device));
            data.put("officialImported", isVirtualNinebotInstalled());
            data.put("rootMode", getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getBoolean(KEY_ROOT_MODE, false));
            data.put("rootFallback", getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getBoolean(KEY_ROOT_FALLBACK, true));
            try {
                data.put("appVersion", getPackageManager()
                        .getPackageInfo(getPackageName(), 0).versionName);
            } catch (PackageManager.NameNotFoundException ignored) {
                data.put("appVersion", "5.0.35");
            }
            data.put("selectedVehicleIndex", device == null ? -1 : devices.indexOf(device));
            data.put("connected", prototypeLastRead != null && device != null);
            data.put("dark", (getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
            JSONArray vehiclesJson = new JSONArray();
            for (DeviceRecord item : devices) {
                JSONObject vehicle = new JSONObject();
                vehicle.put("sn", testVehicleLabel(item));
                vehicle.put("detail", testVehicleDetail(item));
                vehiclesJson.put(vehicle);
            }
            data.put("vehicles", vehiclesJson);
            Backup firstBackup = getBackup(device);
            Backup recentBackup = getPrewriteBackup(device);
            data.put("firstBackup", firstBackup.valid ? backupDescription(firstBackup) : "尚未建立");
            data.put("recentBackup", recentBackup.valid
                    ? backupDescription(recentBackup) : "尚无写入前快照");
            data.put("firstBackupValid", firstBackup.valid);
            data.put("recentBackupValid", recentBackup.valid);
            data.put("backupAlternativesDiffer", firstBackup.valid && recentBackup.valid
                    && (firstBackup.profile != recentBackup.profile
                    || firstBackup.capacity != recentBackup.capacity));
            SharedPreferences backupPrefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            int lastConfirmedProfile = device == null ? -1 : backupPrefs.getInt(
                    backupKey(device, "last_confirmed_profile"), -1);
            data.put("lastConfirmedTarget", lastConfirmedProfile < 0
                    ? "尚无已确认的写入"
                    : voltageName(lastConfirmedProfile) + " · "
                    + formatCapacityShort(BfgProfileCatalog.expectedCore(lastConfirmedProfile)));
            BfgBleClient.Result read = prototypeLastRead;
            if (read != null) {
                data.put("soc", read.displaySoc() < 0 ? "--" : read.displaySoc());
                data.put("batteryVoltage", formatBatteryVoltage(read.disVrlaVoltageRaw));
                data.put("remainingCapacity", read.disRemainingCapacityRaw < 0
                        ? "--" : PostWriteCheckPolicy.capacityPending(
                        read.displaySoc(), read.disRemainingCapacityRaw)
                        ? "暂未读到" : formatCapacityShort(read.disRemainingCapacityRaw));
                int shownProfile = read.operation == BfgBleClient.Operation.WRITE_PROFILE
                        && read.afterProfile >= 0 ? read.afterProfile : read.beforeProfile;
                int shownCapacity = read.operation == BfgBleClient.Operation.WRITE_PROFILE
                        ? read.displayAfterCapacity() : read.displayBeforeCapacity();
                int shownDashboardRaw = read.operation == BfgBleClient.Operation.WRITE_DIS_VOLTAGE
                        && read.disConfigReadbackVerified ? read.disConfigAfterRaw
                        : read.disConfigRaw;
                int dashboardNominal = read.operation == BfgBleClient.Operation.WRITE_DIS_VOLTAGE
                        && read.disConfigReadbackVerified
                        ? DisVoltageConfig.nominalVoltage(shownDashboardRaw)
                        : read.dashboardNominalVoltage();
                data.put("dashboardVoltage", formatNominalVoltage(dashboardNominal));
                data.put("dashboardCapacity", "未读取到");
                data.put("meterVoltage", shownProfile < 0
                        ? "--" : voltageName(shownProfile));
                data.put("meterCapacity", shownCapacity < 0
                        ? "--" : formatCapacityShort(shownCapacity));
                data.put("dashboardFirmware", formatFwVersion(read.disDashboardVersionRaw));
                data.put("colorDisplayFirmware", formatFwVersion(read.colorDisplayVersionRaw));
                data.put("centreFirmware", formatFwVersion(read.centreControllerVersionRaw));
                data.put("meterFirmware", formatFwVersion(read.disBfgVersionRaw));
            }
            boolean serialReadOnly = device != null
                    && WriteAccessPolicy.isReadOnlySerial(device.effectiveSn);
            data.put("readOnlyVehicle", serialReadOnly);
            data.put("writeSupported", lastReadWriteSupported && !serialReadOnly);
            data.put("dashboardWriteSupported",
                    !serialReadOnly && dashboardWriteAllowed(read)
                            && DisVoltageConfig.nominalVoltage(lastReadDisConfigRaw) > 0);
            data.put("writeType", prototypeWriteType);
            JSONArray capacities = new JSONArray();
            List<Double> values = new ArrayList<>();
            for (int index = 0; index <= 0xF; index++) {
                int profile = (index << 4) | selectedVoltageCode;
                double ah = BfgProfileCatalog.expectedCore(profile) / 1000.0;
                if (!values.contains(ah)) values.add(ah);
            }
            Collections.sort(values);
            for (double value : values) capacities.put(value);
            data.put("availableCapacities", capacities);
            JSONObject capacityByVoltage = new JSONObject();
            for (int voltage : new int[] {48, 60, 72}) {
                int voltageCode = voltage == 72 ? 0 : voltage == 48 ? 2 : 1;
                List<Double> options = new ArrayList<>();
                for (int index = 0; index <= 0xF; index++) {
                    double ah = BfgProfileCatalog.expectedCore((index << 4) | voltageCode)
                            / 1000.0;
                    if (!options.contains(ah)) options.add(ah);
                }
                Collections.sort(options);
                JSONArray optionJson = new JSONArray();
                for (double option : options) optionJson.put(option);
                capacityByVoltage.put(String.valueOf(voltage), optionJson);
            }
            data.put("capacityByVoltage", capacityByVoltage);
            if (lastReadProfile >= 0) {
                data.put("voltage", BfgProfileCatalog.nominalVoltage(lastReadProfile));
                data.put("capacity", BfgProfileCatalog.expectedCore(lastReadProfile) / 1000.0);
            }
            prototypeWeb.evaluateJavascript("window.bfgNativeUpdate(" + data + ")", null);
        } catch (JSONException ignored) {
            // Native calibration remains usable if a display snapshot cannot be formed.
        }
    }

    private void prototypeGo(String screen) {
        prototypeScreen = screen;
        if (prototypeReady && prototypeWeb != null) {
            prototypeWeb.evaluateJavascript("window.bfgNativeGo(" + JSONObject.quote(screen) + ")", null);
        }
    }

    private void prototypeSetWriteStage(String stage) {
        if (prototypeReady && prototypeWeb != null) {
            prototypeWeb.evaluateJavascript("window.bfgNativeUpdate({writeStage:"
                    + JSONObject.quote(stage) + "})", null);
        }
    }

    private final class PrototypeBridge {
        @JavascriptInterface public void action(String action, String value) {
            runOnUiThread(() -> handlePrototypeAction(action, value));
        }
    }

    private void handlePrototypeAction(String action, String value) {
        switch (action) {
            case "screen": prototypeScreen = value; break;
            case "modal-state":
                prototypeModalVisible = "open".equals(value);
                configureSystemBars();
                break;
            case "vehicles": prototypeGo("vehicles"); break;
            case "settings": prototypeGo("settings"); break;
            case "select-vehicle":
                try { selectDevice(Integer.parseInt(value), true); }
                catch (NumberFormatException ignored) { }
                break;
            case "refresh-vehicles": loadVehicles(false); break;
            case "set-root": rootModeSwitch.setChecked(Boolean.parseBoolean(value)); break;
            case "set-fallback": rootFallbackSwitch.setChecked(Boolean.parseBoolean(value)); break;
            case "import-root": importHostNinebotWithRoot(); break;
            case "export-diag": exportDiagnosticData(); break;
            case "scan-dis":
            case "scan-bfg":
                if (selectedDevice() == null) {
                    showPrototypeModal("scan-unavailable", "请先在车辆管理中选择自己的车辆。", "settings");
                } else {
                    prototypeGo("scan-progress");
                    startOperation(selectedDevice(), BfgBleClient.Operation.REGISTER_SCAN,
                            "scan-dis".equals(action) ? 0x01 : 0x10);
                }
                break;
            case "show-license": showLicenseDetails(); break;
            case "restore-first": {
                DeviceRecord device = selectedDevice();
                confirmRestoreTo(device, getBackup(device), "首次原参数");
                break;
            }
            case "restore-prewrite": {
                DeviceRecord device = selectedDevice();
                confirmRestoreTo(device, getPrewriteBackup(device), "最近写入前参数");
                break;
            }
            case "clear-data": confirmClearVirtualNinebotData(); break;
            case "select-apk": chooseNinebotApk(); break;
            case "launch-ninebot": openVirtualNinebot(); break;
            case "refresh-root": loadVehicles(false); break;
            case "missing-vehicle": showNonRootLoginGuide(); break;
            case "pair-scan":
                pairingLabOpened = true;
                startActivityForResult(new Intent(this, PairingLabActivity.class), REQ_PAIRING_LAB);
                break;
            case "retry-connect":
                if (selectedDevice() != null) {
                    prototypeGo("connect-progress");
                    startOperation(selectedDevice(), BfgBleClient.Operation.COMPARE_READ, 0);
                }
                break;
            case "start-repair":
                launchPairingRepair(activeDevice != null ? activeDevice : selectedDevice());
                break;
            case "open-bluetooth-settings":
                startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
                break;
            case "post-write-reread":
                pendingPostWriteVerification = true;
                postWriteCheckAttempts = 0;
                prototypeGo("post-write-check");
                startOperation(selectedDevice(), BfgBleClient.Operation.COMPARE_READ, 0);
                break;
            case "retry-write":
                pendingRetryWriteRequest = value;
                prototypeGo("connect-progress");
                startOperation(selectedDevice(), BfgBleClient.Operation.COMPARE_READ, 0);
                break;
            case "confirm-unverified-dis":
                allowUnverifiedDisWrite = true;
                prototypeGo("write-progress");
                startOperation(selectedDevice(), BfgBleClient.Operation.WRITE_DIS_VOLTAGE,
                        pendingUnverifiedVoltage);
                break;
            case "begin-connect":
            case "refresh-read":
                pendingWriteAfterRead = true;
                showOperation(false);
                startRead();
                break;
            case "do-write": performPrototypeWrite(value); break;
            case "cancel":
                if (running && (activeBleOperation == BfgBleClient.Operation.WRITE_PROFILE
                        || activeBleOperation == BfgBleClient.Operation.WRITE_DIS_VOLTAGE)) {
                    showWriteInProgressWarning();
                    break;
                }
                if (activeBleOperation != null && running
                        && activeBleOperation != BfgBleClient.Operation.WRITE_PROFILE
                        && activeBleOperation != BfgBleClient.Operation.WRITE_DIS_VOLTAGE) {
                    cancelCurrentBleOperation();
                }
                break;
            default: break;
        }
    }

    private void performPrototypeWrite(String json) {
        DeviceRecord device = selectedDevice();
        if (device == null || running || prototypeLastRead == null) {
            prototypeWriteFailure("车辆数据尚未读取完成，请重新连接后再试。");
            return;
        }
        try {
            JSONObject request = new JSONObject(json);
            int voltage = request.getInt("voltage");
            lastAttemptedVoltage = voltage;
            lastAttemptedCapacity = request.getDouble("capacity");
            selectedVoltageCode = voltage == 72 ? 0 : voltage == 48 ? 2 : 1;
            boolean dashboard = "dashboard".equals(request.getString("type"));
            prototypeWriteType = dashboard ? "dashboard" : "meter";
            lastAttemptedWriteType = prototypeWriteType;
            if (dashboard) {
                if (!dashboardWriteAllowed(prototypeLastRead)) {
                    prototypeWriteFailure(DashboardWritePolicy.blockedMessage());
                    return;
                }
                if (DisVoltageConfig.nominalVoltage(lastReadDisConfigRaw) < 0) {
                    prototypeWriteFailure("仪表电压配置没有读取到，请重新连接车辆。");
                    return;
                }
                int rawTarget = DisVoltageConfig.target(lastReadDisConfigRaw, voltage);
                if (rawTarget == lastReadDisConfigRaw) {
                    prototypeWriteFailure("仪表已经是所选电压档位，本次没有发送写入。");
                    return;
                }
                boolean unverified = DisVoltageConfig.requiresExtraWarning(
                        lastReadDisConfigRaw, rawTarget);
                if (unverified) {
                    if (prototypeReady) {
                        pendingUnverifiedVoltage = voltage;
                        showPrototypeModal("unverified-dis",
                                "这组仪表电压编码尚无实车验证。写入可能失败，也可能导致车辆数据显示异常。请确认可恢复原配置后再继续。", "write");
                    } else new MaterialAlertDialogBuilder(this)
                            .setTitle("仪表配置尚未验证")
                            .setMessage("这组仪表编码尚无实车验证。写入可能失败，也可能导致车辆数据显示异常。请确认能够恢复原配置。")
                            .setNegativeButton("取消", (dialog, which) -> prototypeGo("write"))
                            .setPositiveButton("继续尝试", (dialog, which) -> {
                                allowUnverifiedDisWrite = true;
                                startOperation(device, BfgBleClient.Operation.WRITE_DIS_VOLTAGE,
                                        voltage);
                            })
                            .setOnCancelListener(dialog -> prototypeGo("write"))
                            .show();
                } else {
                    allowUnverifiedDisWrite = false;
                    startOperation(device, BfgBleClient.Operation.WRITE_DIS_VOLTAGE, voltage);
                }
                return;
            }
            int requestedMilliAh = (int) Math.round(request.getDouble("capacity") * 1000);
            int index = -1;
            int currentIndex = lastReadProfile < 0 ? -1 : (lastReadProfile >> 4) & 0xF;
            if (currentIndex >= 0 && BfgProfileCatalog.expectedCore(
                    (currentIndex << 4) | selectedVoltageCode) == requestedMilliAh) {
                index = currentIndex;
            }
            for (int i = 0; i <= 0xF; i++) {
                if (index < 0 && BfgProfileCatalog.expectedCore((i << 4) | selectedVoltageCode)
                        == requestedMilliAh) { index = i; break; }
            }
            if (index < 0 || !lastReadWriteSupported || !getBackup(device).valid) {
                prototypeWriteFailure("所选容量未适配或原参数备份未完成，请重新读取车辆数据。");
                return;
            }
            selectedProfileIndex = index;
            restoringBackup = false;
            startOperation(device, BfgBleClient.Operation.WRITE_PROFILE,
                    (selectedProfileIndex << 4) | selectedVoltageCode);
        } catch (JSONException | IllegalArgumentException error) {
            prototypeWriteFailure("目标参数无效，请重新选择电压和容量。");
        }
    }

    private void prototypeWriteFailure(String message) {
        if (!prototypeReady || prototypeWeb == null) return;
        try {
            JSONObject update = new JSONObject();
            update.put("result", "failure");
            update.put("errorMessage", message);
            update.put("screen", "review");
            update.put("modal", "write-failure");
            if (lastAttemptedVoltage > 0) {
                update.put("writeType", lastAttemptedWriteType);
                update.put("voltage", lastAttemptedVoltage);
                if (lastAttemptedCapacity > 0) update.put("capacity", lastAttemptedCapacity);
            }
            prototypeWeb.evaluateJavascript("window.bfgNativeUpdate(" + update + ")", null);
        } catch (JSONException ignored) { }
    }

    private void showOperation(boolean writeMode) {
        showPage(PAGE_OPERATION);
        if (!writeMode) resetConnectedOverview();
        changingMode = true;
        modeToggle.check(writeMode ? R.id.calibrate_tab : R.id.read_only_tab);
        changingMode = false;
        writePanel.setVisibility(writeMode && lastReadProfile >= 0 ? View.VISIBLE : View.GONE);
        resultCard.setVisibility(writeMode && lastReadProfile >= 0 ? View.GONE : View.VISIBLE);
        operationStatusCard.setVisibility(View.VISIBLE);
        if (technicalToggleButton != null) technicalToggleButton.setVisibility(View.GONE);
        if (technicalDetails != null) technicalDetails.setVisibility(View.GONE);
        operationState.setText(writeMode && lastReadProfile < 0
                ? "写入前先读取当前容量档位并建立首次备份"
                : getString(R.string.ninebot_operation_waiting));
    }

    private void confirmVehiclePowered(boolean pairing, Runnable proceed) {
        DeviceRecord device = selectedDevice();
        String identity = device == null ? "将在下一步选择车辆" : testVehicleLabel(device);
        new MaterialAlertDialogBuilder(this)
                .setTitle("请先将车辆开机")
                .setMessage("车辆：" + identity
                        + "\n\n请让车辆保持开机并靠近手机，同时关闭其他正在连接车辆的应用或手机。")
                .setNegativeButton("取消", null)
                .setPositiveButton(pairing ? "车辆已开机，发起配对" : "车辆已开机，发起连接",
                        (dialog, which) -> proceed.run())
                .show();
    }

    private void showWritePanel() {
        if (lastReadProfile < 0) return;
        showPage(PAGE_OPERATION);
        toolbar.setTitle("电量校准");
        DeviceRecord device = selectedDevice();
        toolbar.setSubtitle(maskIdentity(testVehicleLabel(device)));
        if (calibrationFirmwareSummary != null) {
            calibrationFirmwareSummary.setVisibility(View.VISIBLE);
        }
        if (connectedFirmwareTitle != null) connectedFirmwareTitle.setVisibility(View.VISIBLE);
        if (connectedFirmwareRows != null) connectedFirmwareRows.setVisibility(View.VISIBLE);
        writePanel.setVisibility(View.VISIBLE);
        resultCard.setVisibility(View.GONE);
        operationStatusCard.setVisibility(View.GONE);
        connectedVehicleCard.setVisibility(View.GONE);
        modeToggle.setVisibility(View.GONE);
        if (technicalToggleButton != null) technicalToggleButton.setVisibility(View.GONE);
        if (technicalDetails != null) technicalDetails.setVisibility(View.GONE);
        int readVoltageCode = lastReadProfile & 0x0F;
        if (readVoltageCode >= 0 && readVoltageCode <= 2) {
            selectedVoltageCode = readVoltageCode;
            selectedProfileIndex = (lastReadProfile >> 4) & 0x0F;
            int buttonId = selectedVoltageCode == 0 ? R.id.voltage_72
                    : selectedVoltageCode == 2 ? R.id.voltage_48 : R.id.voltage_60;
            voltageGroup.check(buttonId);
            populateCapacityGrid(selectedProfileIndex);
        }
        changingMode = true;
        modeToggle.check(R.id.calibrate_tab);
        changingMode = false;
        updateTargetSummary();
        if (operationScroll != null) operationScroll.post(() -> operationScroll.scrollTo(0, 0));
    }

    private void resetConnectedOverview() {
        if (connectedVehicleCard != null) connectedVehicleCard.setVisibility(View.GONE);
        if (resultCard != null) resultCard.setVisibility(View.VISIBLE);
        if (operationStatusCard != null) operationStatusCard.setVisibility(View.VISIBLE);
        if (technicalToggleButton != null) technicalToggleButton.setVisibility(View.GONE);
        if (technicalDetails != null) technicalDetails.setVisibility(View.GONE);
    }

    private void updateConnectedOverview(BfgBleClient.Result r) {
        if (r == null || connectedVehicleCard == null) return;
        DeviceRecord device = selectedDevice();
        String identity = maskIdentity(testVehicleLabel(device));
        StringBuilder model = new StringBuilder();
        if (device != null && !device.deviceType.isEmpty()) {
            model.append("车型 ").append(device.deviceType);
        }
        if (model.length() == 0) model.append("请确认附近的目标车辆");

        connectedVehicleIdentity.setText(identity);
        toolbar.setSubtitle(identity);
        connectedVehicleModel.setText(model.toString());
        connectedSocValue.setText(r.displaySoc() >= 0 ? r.displaySoc() + "%" : "--%");
        connectedBatteryVoltageValue.setText(formatBatteryVoltage(r.disVrlaVoltageRaw));
        connectedRemainingCapacityValue.setText(r.disRemainingCapacityRaw >= 0
                ? formatCapacityShort(r.disRemainingCapacityRaw) : "--Ah");
        connectedDashboardVoltageValue.setText(formatNominalVoltage(r.dashboardNominalVoltage()));
        connectedVoltageGroupValue.setText(r.beforeProfile >= 0
                ? voltageName(r.beforeProfile) : "--V");
        connectedCapacityValue.setText(r.displayBeforeCapacity() >= 0
                ? formatCapacityShort(r.displayBeforeCapacity()) : "--Ah");
        firmwareDashboardValue.setText(formatFwVersion(r.disDashboardVersionRaw));
        firmwareMeterValue.setText(formatFwVersion(r.disBfgVersionRaw));

        boolean scanCompatibility = r.communicationMode
                == CommunicationModeResolver.Mode.CAPACITY_SCAN_COMPAT;
        if (scanCompatibility) {
            connectedModeNotice.setBackgroundResource(R.drawable.bg_ninebot_warning);
            connectedModeNotice.setTextColor(ContextCompat.getColor(this,
                    R.color.ninebot_warning));
            String voltageNotice = r.voltageMismatch()
                    ? String.format(Locale.US, "仪表%dV、计量模块%dV；",
                    r.dashboardNominalVoltage(), r.meterNominalVoltage()) : "";
            connectedModeNotice.setText("⚠ " + voltageNotice
                    + String.format(Locale.US,
                    "固件版本较旧或常规容量异常，已通过兼容模式识别为%s",
                    formatCapacityShort(r.displayBeforeCapacity())));
        } else if (r.voltageMismatch()) {
            connectedModeNotice.setBackgroundResource(R.drawable.bg_ninebot_warning);
            connectedModeNotice.setTextColor(ContextCompat.getColor(this,
                    R.color.ninebot_warning));
            connectedModeNotice.setText(String.format(Locale.US,
                    "⚠ 仪表为%dV，计量模块为%dV，参数不一致。%s",
                    r.dashboardNominalVoltage(), r.meterNominalVoltage(),
                    r.writeSupported ? "可进入校准"
                            : "计量模块临时写入未适配，可试验仪表电压配置"));
        } else if (r.writeSupported) {
            connectedModeNotice.setBackgroundResource(R.drawable.bg_ninebot_good_badge);
            connectedModeNotice.setTextColor(ContextCompat.getColor(this,
                    R.color.ninebot_good));
            String prefix = r.dashboardNominalVoltage() > 0
                    ? "✓ 仪表与计量模块电压一致 · " : "";
            connectedModeNotice.setText(prefix + "采用标准模式写入");
        } else {
            connectedModeNotice.setBackgroundResource(R.drawable.bg_ninebot_warning);
            connectedModeNotice.setTextColor(ContextCompat.getColor(this,
                    R.color.ninebot_warning));
            connectedModeNotice.setText(DisVoltageConfig.nominalVoltage(r.disConfigRaw) > 0
                    ? "计量模块临时写入未适配；仪表电压实验写入可尝试"
                    : "当前通信组合尚未适配写入，仅支持读取");
        }
        int lastDisTarget = getLastDisWriteTarget(device);
        if (lastDisTarget >= 0 && r.disConfigRaw >= 0) {
            connectedModeNotice.append(String.format(Locale.US,
                    "\n上次仪表实验目标 %02X，本次读取 %02X。若车辆已断电重启，可据此核对是否保持。",
                    lastDisTarget, r.disConfigRaw));
        }
        updateCalibrationSummary(r, identity);
        boolean canEnterCalibration = r.writeSupported
                || DisVoltageConfig.nominalVoltage(r.disConfigRaw) > 0;
        connectedCalibrateButton.setVisibility(canEnterCalibration ? View.VISIBLE : View.GONE);
        connectedCalibrateButton.setEnabled(canEnterCalibration && !running);
        connectedVehicleCard.setVisibility(View.VISIBLE);
        resultCard.setVisibility(View.GONE);
        operationStatusCard.setVisibility(View.GONE);
        if (technicalToggleButton != null) technicalToggleButton.setVisibility(View.GONE);
        if (technicalDetails != null) technicalDetails.setVisibility(View.GONE);
        connectedVehicleCard.post(() -> connectedVehicleCard.requestRectangleOnScreen(
                new Rect(0, 0, connectedVehicleCard.getWidth(), dp(120)), true));
    }

    private void updateCalibrationSummary(BfgBleClient.Result r, String identity) {
        if (r == null || calibrationVehicleIdentity == null) return;
        calibrationVehicleIdentity.setText(identity);
        calibrationCurrentSummary.setText(String.format(Locale.US,
                "仪表 %s · 计量模块 %s · %s · 电量 %s",
                formatNominalVoltage(r.dashboardNominalVoltage()),
                r.beforeProfile >= 0 ? voltageName(r.beforeProfile) : "--V",
                r.displayBeforeCapacity() >= 0
                        ? formatCapacityShort(r.displayBeforeCapacity()) : "--Ah",
                r.displaySoc() >= 0 ? r.displaySoc() + "%" : "--"));
        calibrationFirmwareSummary.setText(String.format(Locale.US,
                "仪表 %s  ·  计量模块 %s",
                formatFwVersion(r.disDashboardVersionRaw),
                formatFwVersion(r.disBfgVersionRaw)));
        String modeText;
        if (r.communicationMode == CommunicationModeResolver.Mode.CAPACITY_SCAN_COMPAT) {
            modeText = "兼容模式 · 已识别当前容量";
        } else {
            modeText = "采用标准模式写入";
        }
        calibrationModeSummary.setText(r.voltageMismatch()
                ? "仪表与计量模块电压档位不一致 · " + modeText : modeText);
        if (r.disConfigRaw >= 0 && r.disConfigRaw <= 0xFF) {
            calibrationModeSummary.append(String.format(Locale.US,
                    " · 仪表配置 %02X", r.disConfigRaw));
        }
    }

    private String formatNominalVoltage(int value) {
        return value > 0 ? value + "V" : "--V";
    }

    private String formatCalculatedVoltage(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%.2fV", value) : "未读取到";
    }

    private String formatBatteryVoltage(int raw) {
        if (raw < 0) return "--V";
        double volts = raw / 100.0;
        if (raw % 100 == 0) return String.format(Locale.US, "%.0fV", volts);
        if (raw % 10 == 0) return String.format(Locale.US, "%.1fV", volts);
        return String.format(Locale.US, "%.2fV", volts);
    }

    private void toggleCapacityOptions() {
        if (capacityOptionsContainer == null || running) return;
        boolean expand = capacityOptionsContainer.getVisibility() != View.VISIBLE;
        capacityOptionsContainer.setVisibility(expand ? View.VISIBLE : View.GONE);
        updateCapacityPickerLabel(expand);
        if (expand) {
            capacityOptionsContainer.setAlpha(0f);
            capacityOptionsContainer.setTranslationY(-dp(8));
            capacityOptionsContainer.animate().alpha(1f).translationY(0f).setDuration(180).start();
        }
    }

    private void updateCapacityPickerLabel(boolean expanded) {
        if (capacityPickerButton == null) return;
        int capacity = expectedCore(profileForIndex(selectedProfileIndex));
        String recommended = isCapacityScanCompatibility()
                && isRecommendedCompatCapacity(capacity) ? "（推荐）" : "";
        capacityPickerButton.setText(String.format(Locale.US, "当前选择：%s%s　　%s",
                formatCapacityShort(capacity), recommended, expanded ? "收起" : "更改"));
    }

    private void toggleTechnicalDetails() {
        boolean show = technicalDetails.getVisibility() != View.VISIBLE;
        technicalDetails.setVisibility(show ? View.VISIBLE : View.GONE);
        Button toggle = findViewById(R.id.technical_toggle_button);
        toggle.setText(show ? R.string.ninebot_technical_hide : R.string.ninebot_technical_toggle);
    }

    private void populateCapacityGrid(int preferredIndex) {
        if (capacityGrid == null) return;
        selectedProfileIndex = Math.max(0, Math.min(0xF, preferredIndex));
        int preferredCapacity = expectedCore(profileForIndex(selectedProfileIndex));

        List<Integer> sortedIndices = new ArrayList<>();
        for (int index = 0; index <= 0xF; index++) sortedIndices.add(index);
        Collections.sort(sortedIndices, (left, right) -> {
            int byCapacity = Integer.compare(
                    expectedCore(profileForIndex(left)),
                    expectedCore(profileForIndex(right)));
            return byCapacity != 0 ? byCapacity : Integer.compare(left, right);
        });

        // The firmware table contains duplicate effective capacities. The simple UI
        // shows each Ah value once while retaining the currently selected profile
        // when its capacity is duplicated.
        Map<Integer, Integer> indexByCapacity = new LinkedHashMap<>();
        for (int index : sortedIndices) {
            int capacity = expectedCore(profileForIndex(index));
            if (!indexByCapacity.containsKey(capacity) ||
                    (capacity == preferredCapacity && index == selectedProfileIndex)) {
                indexByCapacity.put(capacity, index);
            }
        }

        capacityGrid.removeAllViews();
        List<Integer> choices = new ArrayList<>(indexByCapacity.values());
        final int columns = 3;
        for (int start = 0; start < choices.size(); start += columns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            capacityGrid.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            for (int column = 0; column < columns; column++) {
                int position = start + column;
                if (position >= choices.size()) {
                    Space filler = new Space(this);
                    LinearLayout.LayoutParams fillerParams = new LinearLayout.LayoutParams(0, dp(58), 1f);
                    fillerParams.setMargins(dp(4), dp(4), dp(4), dp(4));
                    row.addView(filler, fillerParams);
                    continue;
                }
                int index = choices.get(position);
                int capacity = expectedCore(profileForIndex(index));
                boolean selected = index == selectedProfileIndex;
                boolean recommended = isCapacityScanCompatibility()
                        && isRecommendedCompatCapacity(capacity);
                MaterialButton button = new MaterialButton(this, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
                button.setAllCaps(false);
                button.setText(formatCapacityShort(capacity) + (recommended ? "  推荐" : ""));
                button.setTextSize(recommended ? 14f : 16f);
                button.setGravity(Gravity.CENTER);
                button.setCornerRadius(dp(16));
                button.setStrokeWidth(dp(selected ? 2 : 1));
                button.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this,
                        selected ? R.color.ninebot_blue : R.color.ninebot_line)));
                button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                        selected ? R.color.ninebot_blue : R.color.ninebot_surface)));
                button.setTextColor(ContextCompat.getColor(this,
                        selected ? R.color.ninebot_on_blue : R.color.ninebot_text));
                button.setOnClickListener(v -> {
                    selectedProfileIndex = index;
                    populateCapacityGrid(index);
                    capacityOptionsContainer.setVisibility(View.GONE);
                    updateCapacityPickerLabel(false);
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(58), 1f);
                params.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(button, params);
            }
        }
        updateCapacityPickerLabel(capacityOptionsContainer != null
                && capacityOptionsContainer.getVisibility() == View.VISIBLE);
        updateTargetSummary();
    }

    private void updateTargetSummary() {
        if (targetSummary == null) return;
        int target = profileForIndex(selectedProfileIndex);
        int capacity = expectedCore(target);
        String before = lastReadProfile >= 0
                ? String.format(Locale.US, "当前：%s · %s\n",
                voltageName(lastReadProfile), formatCapacityShort(lastReadCapacity))
                : "当前：等待读取并建立备份\n";
        String compatibilityHint = "";
        if (isCapacityScanCompatibility()) {
            compatibilityHint = isRecommendedCompatCapacity(capacity)
                    ? "\n兼容车型推荐档位"
                    : "\n⚠ 该容量尚未适配，写入可能失败";
        }
        targetSummary.setText(before + String.format(Locale.US,
                "临时计量模块目标：%s · %s%s\n仪表实验目标：%s（不使用上方容量选择）",
                voltageName(target), formatCapacityShort(capacity), compatibilityHint,
                voltageName(target)));
    }

    private boolean isCapacityScanCompatibility() {
        return lastCommunicationMode == CommunicationModeResolver.Mode.CAPACITY_SCAN_COMPAT;
    }

    private boolean isRecommendedCompatCapacity(int capacityMah) {
        return capacityMah == 18000 || capacityMah == 26000;
    }

    private String formatCapacity(int capacityMah) {
        if (capacityMah < 0) return "未知";
        double ah = capacityMah / 1000.0;
        String ahText = capacityMah % 1000 == 0
                ? String.format(Locale.US, "%.0f", ah)
                : String.format(Locale.US, "%.1f", ah);
        return ahText + " Ah（" + capacityMah + " mAh）";
    }

    private String formatCapacityShort(int capacityMah) {
        if (capacityMah < 0) return "未知";
        double ah = capacityMah / 1000.0;
        return capacityMah % 1000 == 0
                ? String.format(Locale.US, "%.0fAh", ah)
                : String.format(Locale.US, "%.1fAh", ah);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateCurrentVehicle() {
        DeviceRecord device = selectedDevice();
        if (device == null) {
            currentVehicleName.setText(R.string.ninebot_vehicle_none);
            currentVehicleMeta.setText(R.string.ninebot_vehicle_none_detail);
            currentVehicleBadge.setText(R.string.ninebot_credentials_missing);
            updateVehiclePickerUi(null);
            return;
        }
        String identity = testVehicleLabel(device);
        currentVehicleName.setText(identity);
        currentVehicleMeta.setText(testVehicleDetail(device));
        currentVehicleBadge.setText("待连接");
        updateVehiclePickerUi(device);
        if (pageFlipper.getDisplayedChild() == PAGE_OPERATION) toolbar.setSubtitle(identity);
    }

    private void updateVehiclePickerUi(DeviceRecord device) {
        if (vehiclePickerTitle == null || vehiclePickerDetail == null) return;
        if (device == null) {
            vehiclePickerTitle.setText("尚未选择车辆");
            vehiclePickerDetail.setText("点击选择，或先读取车辆数据库");
            return;
        }
        vehiclePickerTitle.setText(testVehicleLabel(device));
        vehiclePickerDetail.setText(testVehicleDetail(device));
    }

    private void showVehiclePickerDialog() {
        if (isFinishing() || running) return;
        if (devices.isEmpty()) {
            showNonRootLoginGuide();
            return;
        }

        View content = getLayoutInflater().inflate(R.layout.dialog_vehicle_picker, null);
        LinearLayout list = content.findViewById(R.id.vehicle_choice_list);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .setNegativeButton("取消", null)
                .create();
        int selectedPosition = activeDevice == null ? -1 : devices.indexOf(activeDevice);
        for (int index = 0; index < devices.size(); index++) {
            DeviceRecord device = devices.get(index);
            View option = getLayoutInflater().inflate(R.layout.dialog_vehicle_option, list, false);
            MaterialCardView card = option.findViewById(R.id.vehicle_option_card);
            TextView title = option.findViewById(R.id.vehicle_option_title);
            TextView detail = option.findViewById(R.id.vehicle_option_detail);
            TextView check = option.findViewById(R.id.vehicle_option_check);

            title.setText(testVehicleLabel(device));
            detail.setText(testVehicleDetail(device));

            boolean selected = index == selectedPosition;
            check.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            card.setStrokeWidth(dp(selected ? 2 : 1));
            card.setStrokeColor(ContextCompat.getColor(this,
                    selected ? R.color.ninebot_blue : R.color.ninebot_line));
            if (selected) card.setCardBackgroundColor(
                    ContextCompat.getColor(this, R.color.ninebot_surface_soft));

            final int position = index;
            card.setOnClickListener(v -> {
                selectDevice(position, true);
                dialog.dismiss();
            });
            list.addView(option);
        }
        dialog.show();
    }

    private void resetHomeReadout() {
        if (homeSocValue != null) homeSocValue.setText("--%");
        if (homeVoltageBandValue != null) homeVoltageBandValue.setText("--V");
        if (homeCapacityValue != null) homeCapacityValue.setText("--Ah");
        if (homeReadState != null) homeReadState.setText("尚未读取");
    }

    private void updateHomeReadout(BfgBleClient.Result r) {
        if (r == null) return;
        int profile = r.operation == BfgBleClient.Operation.WRITE_PROFILE
                ? r.afterProfile : r.beforeProfile;
        int capacity = r.operation == BfgBleClient.Operation.WRITE_PROFILE
                ? r.displayAfterCapacity()
                : r.displayBeforeCapacity();
        if (homeSocValue != null) {
            int soc = r.displaySoc();
            homeSocValue.setText(soc >= 0 ? soc + "%" : "--%");
        }
        if (homeVoltageBandValue != null) {
            homeVoltageBandValue.setText(profile >= 0 ? voltageName(profile) : "--V");
        }
        if (homeCapacityValue != null) {
            homeCapacityValue.setText(capacity >= 0 ? formatCapacityShort(capacity) : "--Ah");
        }
        if (homeReadState != null) {
            homeReadState.setText(r.operation == BfgBleClient.Operation.WRITE_DIS_VOLTAGE
                    ? "仪表配置已回读" : r.operation == BfgBleClient.Operation.WRITE_PROFILE
                    ? "临时写入已回读" : "读取完成");
        }
    }

    private void refreshRootModeUi() {
        if (rootModeSwitch == null || rootFallbackSwitch == null || rootModeStatusView == null) return;
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean rootMode = preferences.getBoolean(KEY_ROOT_MODE, false);
        boolean fallback = preferences.getBoolean(KEY_ROOT_FALLBACK, true);
        changingRootMode = true;
        rootModeSwitch.setChecked(rootMode);
        rootFallbackSwitch.setChecked(fallback);
        changingRootMode = false;
        rootModeStatusView.setText(rootMode
                ? "Root 模式已开启：同步时读取原机九号数据库"
                : isVirtualNinebotInstalled()
                ? "普通模式：读取已导入九号数据库"
                : "普通模式尚未就绪：请导入九号出行 APK");
    }

    private String maskIdentity(String value) {
        if (value == null || value.length() < 9) return value == null ? "" : value;
        return value.substring(0, 4) + "••••" + value.substring(value.length() - 4);
    }

    @Override
    public void onBackPressed() {
        handleBackNavigation();
    }

    private void handleBackNavigation() {
        if (pageFlipper == null) {
            finish();
            return;
        }
        if (prototypeWeb != null && prototypeWeb.getVisibility() == View.VISIBLE) {
            if (running && (activeBleOperation == BfgBleClient.Operation.WRITE_PROFILE
                    || activeBleOperation == BfgBleClient.Operation.WRITE_DIS_VOLTAGE)) {
                showWriteInProgressWarning();
                return;
            }
            if (activeBleOperation != null) cancelCurrentBleOperation();
            if ("write".equals(prototypeScreen)) prototypeGo("review");
            else if (!"home".equals(prototypeScreen)) {
                showPage(PAGE_HOME);
                prototypeGo("home");
            } else finish();
            return;
        }
        int page = pageFlipper.getDisplayedChild();
        if (page == PAGE_WRITE_COMPLETE) {
            if (navigateAwayFromCurrentOperation(PAGE_OPERATION)) showWritePanel();
        } else if (page == PAGE_OPERATION && writePanel != null
                && writePanel.getVisibility() == View.VISIBLE && !running) {
            writePanel.setVisibility(View.GONE);
            pendingWriteAfterRead = false;
            toolbar.setTitle(R.string.ninebot_operation_title);
            if (connectedVehicleCard != null) connectedVehicleCard.setVisibility(View.VISIBLE);
            if (operationStatusCard != null) operationStatusCard.setVisibility(View.GONE);
            if (technicalToggleButton != null) technicalToggleButton.setVisibility(View.GONE);
            if (technicalDetails != null) technicalDetails.setVisibility(View.GONE);
            if (operationScroll != null) operationScroll.post(() -> operationScroll.scrollTo(0, 0));
        } else if (page != PAGE_HOME || activeBleOperation != null) {
            navigateAwayFromCurrentOperation(PAGE_HOME);
        } else {
            finish();
        }
    }

    private boolean navigateAwayFromCurrentOperation(int targetPage) {
        if (running && (activeBleOperation == BfgBleClient.Operation.WRITE_PROFILE
                || activeBleOperation == BfgBleClient.Operation.WRITE_DIS_VOLTAGE)) {
            showWriteInProgressWarning();
            return false;
        }
        if (activeBleOperation != null) cancelCurrentBleOperation();
        showPage(targetPage);
        return true;
    }

    private void showWriteInProgressWarning() {
        if (isFinishing()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("正在写入并回读")
                .setMessage("为避免写入状态无法确认，请保持车辆开启并等待当前操作完成。完成后会自动进入结果页面。")
                .setPositiveButton("继续等待", null)
                .show();
    }

    private void cancelCurrentBleOperation() {
        operationGeneration++;
        BfgBleClient runningClient = client;
        client = null;
        if (runningClient != null) runningClient.close();
        Future<?> preparation = operationPreparation;
        operationPreparation = null;
        if (preparation != null) preparation.cancel(true);
        activeBleOperation = null;
        pendingReadAfterCredentials = false;
        pendingWriteAfterRead = false;
        pendingPrewriteOperation = null;
        pendingPrewriteDevice = null;
        prewriteApproved = false;
        restoringBackup = false;
        if (writeProgressPanel != null) writeProgressPanel.setVisibility(View.GONE);
        if (writeInlineResult != null) writeInlineResult.setVisibility(View.GONE);
        if (writeButton != null) writeButton.setText("临时写入计量模块并回读");
        if (operationState != null) operationState.setText("已取消连接");
        if (status != null) status.setText("已取消本次连接，可以立即重新连接车辆并校准。");
        setBusy(false);
    }

    private BfgBleClient.Listener scopedBleListener(int generation) {
        return new BfgBleClient.Listener() {
            private boolean current() {
                return generation == operationGeneration && activeBleOperation != null;
            }

            @Override public void onStatus(String s) {
                if (current()) MainActivity.this.onStatus(s);
            }

            @Override public void onLog(String s) {
                if (current()) MainActivity.this.onLog(s);
            }

            @Override public void onResult(BfgBleClient.Result r) {
                if (!current()) return;
                client = null;
                MainActivity.this.onResult(r);
                activeBleOperation = null;
            }

            @Override public void onError(String s) {
                if (!current()) return;
                DeviceRecord failedDevice = activeDevice;
                boolean authTimedOut = AuthRepairOffer.isAuthTimeout(s);
                boolean wasPostWriteCheck = pendingPostWriteVerification;
                client = null;
                MainActivity.this.onError(s);
                activeBleOperation = null;
                if (authTimedOut && !wasPostWriteCheck) showAuthRepairOffer(failedDevice);
            }
        };
    }

    private void showAuthRepairOffer(DeviceRecord device) {
        if (device == null || isFinishing() || isDestroyed()) return;
        if (prototypeReady) {
            showPrototypeModal("auth-repair", "认证没有回应。请先检查是否有其他手机或应用占用车辆蓝牙。重新配对需要在车上确认，临时密钥仅在本次运行期间有效。", "connect-ready");
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("车辆认证没有回应")
                .setMessage("这可能是已保存的配对凭据失效，也可能是其他应用或手机占用了车辆蓝牙。"
                        + "\n\n建议先关闭占用蓝牙的应用并重试。若仍无回复，可重新配对；"
                        + "只有你确认后才会生成新凭据，并需要在车上按键确认。"
                        + "重新配对可能使九号出行需要再次配对。")
                .setNegativeButton("稍后", null)
                .setNeutralButton("重试连接", (dialog, which) ->
                        startOperation(device, BfgBleClient.Operation.COMPARE_READ, 0))
                .setPositiveButton("重新配对", (dialog, which) -> {
                    launchPairingRepair(device);
                })
                .show();
    }

    private void launchPairingRepair(DeviceRecord device) {
        Intent intent = new Intent(this, PairingLabActivity.class);
        if (device != null && AuthRepairOffer.hasExactVehicleIdentity(
                device.mac, device.effectiveSn)) {
            intent.putExtra(PairingLabActivity.EXTRA_REPAIR_MAC, device.mac);
            intent.putExtra(PairingLabActivity.EXTRA_REPAIR_SERIAL, device.effectiveSn);
        }
        pairingLabOpened = true;
        startActivityForResult(intent, REQ_PAIRING_LAB);
    }

    private void showPrototypeModal(String modal, String message, String screen) {
        if (!prototypeReady || prototypeWeb == null) return;
        try {
            JSONObject update = new JSONObject();
            update.put("modal", modal);
            update.put("errorMessage", message);
            if ("write-success".equals(modal)) update.put("result", "success");
            if (screen != null) update.put("screen", screen);
            prototypeWeb.evaluateJavascript("window.bfgNativeUpdate(" + update + ")", null);
        } catch (JSONException ignored) { }
    }

    private void loadVehicles() {
        loadVehicles(true);
    }

    private void loadVehicles(boolean openLoginOnFailure) {
        if (running) return;
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean rootRequested = preferences.getBoolean(KEY_ROOT_MODE, false);
        boolean allowFallback = preferences.getBoolean(KEY_ROOT_FALLBACK, true);
        setBusy(true);
        status.setText(rootRequested
                ? "正在请求 Root 权限并读取原机九号数据库…"
                : "正在读取已导入九号数据库…");
        operationPreparation = worker.submit(() -> {
            boolean rootAvailable = rootRequested && RootDb.hasRoot();
            try {
                List<DeviceRecord> locallyPaired = PairingCredentialStore.loadRecords(this);
                List<DeviceRecord> found = new ArrayList<>();
                boolean rootSource = rootAvailable;
                boolean fallbackUsed = rootRequested && !rootAvailable;
                boolean localOnly = false;
                try {
                    if (rootRequested && !rootAvailable && !allowFallback) {
                        throw new IllegalStateException("未获得 Root 权限，且自动回退已关闭");
                    }
                    if (rootAvailable) {
                        try {
                            found = RootDb.loadDevices(this, true);
                        } catch (Throwable rootFailure) {
                            if (!allowFallback) throw rootFailure;
                            List<DeviceRecord> virtualFallback = VmDbBridge.tryLoadDevices(this);
                            if (virtualFallback == null) throw rootFailure;
                            found = virtualFallback;
                            rootSource = false;
                            fallbackUsed = true;
                        }
                        if (found.isEmpty()) {
                            List<DeviceRecord> virtualFallback = VmDbBridge.tryLoadDevices(this);
                            if (allowFallback && virtualFallback != null && !virtualFallback.isEmpty()) {
                                found = virtualFallback;
                                rootSource = false;
                                fallbackUsed = true;
                            }
                        }
                    } else {
                        found = RootDb.loadDevices(this, false);
                        rootSource = false;
                    }
                } catch (Throwable databaseFailure) {
                    if (locallyPaired.isEmpty()) throw databaseFailure;
                    found = new ArrayList<>();
                    rootSource = false;
                    fallbackUsed = false;
                    localOnly = true;
                }
                found = new ArrayList<>(found);
                for (DeviceRecord local : locallyPaired) {
                    boolean duplicate = false;
                    for (int i = 0; i < found.size(); i++) {
                        DeviceRecord existing = found.get(i);
                        if (existing.mac.equalsIgnoreCase(local.mac)) {
                            // A freshly confirmed vehicle-side pairing supersedes an older
                            // credential copied from the official app database.
                            found.set(i, local);
                            existing.wipe();
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) found.add(local);
                }
                boolean loadedFromRoot = rootSource;
                boolean loadedByFallback = fallbackUsed;
                boolean loadedLocallyOnly = localOnly;
                int localPairCount = locallyPaired.size();
                List<DeviceRecord> loadedVehicles = found;
                runOnUiThread(() -> {
                    String source = loadedLocallyOnly
                            ? "本机已配对车辆（无需登录账号）"
                            : loadedFromRoot
                            ? "原机九号数据库（使用root权限访问）"
                            : loadedByFallback
                            ? "已导入九号数据库（Root 读取失败后自动回退）"
                            : "工具内已导入九号出行";
                    if (loadedVehicles.isEmpty()) {
                        replaceDevices(loadedVehicles, source);
                        if (loadedFromRoot) {
                            status.setText("原机数据库没有找到带 BLE 凭据的车辆，请确认原机九号账号内有车辆。");
                        } else {
                            status.setText(highlightPhrase(
                                    "普通模式未找到数据库。请先导入九号出行 APK，再登录账号并同步车辆。",
                                    "验证码登录不可用"));
                        }
                    } else {
                        replaceDevices(loadedVehicles, source);
                        status.setText(loadedLocallyOnly
                                ? "已加载 " + localPairCount + " 台本机配对车辆；无需登录账号，可直接连接读取。"
                                : "已读取 " + loadedVehicles.size() + " 台车辆（含本机配对 "
                                + localPairCount + " 台）；蓝牙将由本工具直接连接。");
                    }
                    if (rootModeStatusView != null) {
                        rootModeStatusView.setText(loadedLocallyOnly
                                ? "免登录实验：使用本次运行中的临时配对凭据"
                                : loadedFromRoot
                                ? "Root 已授权：当前读取原机九号数据库"
                                : loadedByFallback
                                ? "Root 未成功，已自动回退到已导入九号数据库"
                                : "普通模式：读取已导入九号数据库");
                    }
                    setBusy(false);
                    if (!loadedVehicles.isEmpty() && pendingReadAfterPairing) {
                        pendingReadAfterPairing = false;
                        showOperation(false);
                        prototypeGo("connect-progress");
                        startRead();
                    } else if (loadedVehicles.isEmpty() && openLoginOnFailure && !loadedFromRoot) {
                        showNonRootLoginGuide();
                    } else if (!loadedVehicles.isEmpty() && pendingReadAfterCredentials) {
                        pendingReadAfterCredentials = false;
                        confirmVehiclePowered(false, () -> {
                            showOperation(pendingWriteAfterRead);
                            startRead();
                        });
                    } else if (loadedVehicles.isEmpty()) {
                        pendingReadAfterCredentials = false;
                        status.setText("返回后仍未找到车辆凭据。请确认九号出行已登录并完成车辆同步，然后重试。");
                    }
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    status.setText((rootRequested ? "读取原机九号数据库失败：" : "读取已导入九号数据库失败：")
                            + t.getMessage());
                    if (rootModeStatusView != null && rootRequested) {
                        rootModeStatusView.setText("Root 模式未就绪：" + t.getMessage());
                    }
                    setBusy(false);
                    if (openLoginOnFailure && (!rootRequested || allowFallback)) {
                        showNonRootLoginGuide();
                    } else {
                        pendingReadAfterCredentials = false;
                    }
                });
            }
        });
    }

    private void beginVirtualLogin() {
        if (isVirtualNinebotInstalled()) showNonRootLoginGuide();
        else showNinebotImportOptions();
    }

    private void showNinebotImportOptions() {
        if (isFinishing() || running) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("准备九号出行")
                .setMessage("工具不内置九号出行。可以选择你自己准备的完整 APK，"
                        + "也可以在已 Root 的手机上读取本机已安装的九号出行。\n\n"
                        + "Root 读取仅复制安装包，不会清除或修改原机九号出行数据。")
                .setNegativeButton("取消", null)
                .setNeutralButton("选择 APK", (dialog, which) -> chooseNinebotApk())
                .setPositiveButton("Root 读取本机", (dialog, which) -> importHostNinebotWithRoot())
                .show();
    }

    private void showNonRootLoginGuide() {
        if (isFinishing() || running) return;
        View content = getLayoutInflater().inflate(R.layout.dialog_non_root_login, null);
        new MaterialAlertDialogBuilder(this)
                .setView(content)
                .setNegativeButton("暂不登录", null)
                .setNeutralButton("重新导入 APK", (dialog, which) -> showNinebotImportOptions())
                .setPositiveButton("打开九号出行", (dialog, which) -> openVirtualNinebot())
                .show();
    }

    private void chooseNinebotApk() {
        if (running || isFinishing()) return;
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.android.package-archive",
                "application/octet-stream"
        });
        try {
            startActivityForResult(picker, REQ_IMPORT_NINEBOT_APK);
        } catch (Throwable error) {
            status.setText("无法打开文件选择器，请确认系统文件管理器可用。");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PAIRING_LAB) {
            pairingLabOpened = false;
            String returnScreen = data == null ? null : data.getStringExtra("bfg_return_screen");
            prototypeGo("vehicles".equals(returnScreen) || "settings".equals(returnScreen)
                    ? returnScreen : "home");
            if (resultCode == RESULT_OK && data != null) {
                pendingPairedMac = data.getStringExtra("bfg_paired_mac");
                pendingReadAfterPairing = pendingPairedMac != null;
            }
            loadVehicles(false);
            return;
        }
        if (requestCode != REQ_IMPORT_NINEBOT_APK) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            status.setText("已取消导入九号出行 APK。");
            return;
        }
        installImportedNinebotApk(data.getData());
    }

    private void installImportedNinebotApk(Uri source) {
        if (running || source == null) return;
        setBusy(true);
        status.setText("正在校验并导入九号出行 APK，请稍候…");
        worker.submit(() -> {
            File stagedApk = null;
            String failure = null;
            try {
                stagedApk = copyImportedApkToCache(source);
                installNinebotApkFile(stagedApk);
            } catch (Throwable error) {
                failure = importFailureMessage(error);
            } finally {
                if (stagedApk != null && stagedApk.exists() && !stagedApk.delete()) {
                    stagedApk.deleteOnExit();
                }
            }
            finishNinebotImport(failure);
        });
    }

    private void importHostNinebotWithRoot() {
        if (running || isFinishing()) return;
        setBusy(true);
        status.setText("正在请求 Root 权限并读取本机九号出行 APK…");
        worker.submit(() -> {
            File stagedApk = null;
            String failure = null;
            try {
                stagedApk = RootDb.copyInstalledNinebotApk(this);
                installNinebotApkFile(stagedApk);
            } catch (Throwable error) {
                failure = importFailureMessage(error);
            } finally {
                if (stagedApk != null && stagedApk.exists() && !stagedApk.delete()) {
                    stagedApk.deleteOnExit();
                }
            }
            finishNinebotImport(failure);
        });
    }

    private void installNinebotApkFile(File apk) throws Exception {
        PackageInfo archive = getPackageManager().getPackageArchiveInfo(
                apk.getAbsolutePath(), PackageManager.GET_META_DATA);
        if (archive == null || archive.packageName == null) {
            throw new Exception("文件不是有效的 Android APK");
        }
        if (!NINEBOT_PACKAGE.equals(archive.packageName)) {
            throw new Exception("所选 APK 不是九号出行，请选择官方九号出行 APK");
        }
        BlackBoxCore core = BlackBoxCore.get();
        core.stopPackage(NINEBOT_PACKAGE, virtualUserId);
        InstallResult install = core.installPackageAsUser(apk, virtualUserId);
        if (install == null || !install.success) {
            throw new Exception("内部安装未完成，请确认 APK 完整并与手机架构兼容");
        }
    }

    private void finishNinebotImport(String failure) {
        runOnUiThread(() -> {
            setBusy(false);
            refreshImportedNinebotUi();
            refreshRootModeUi();
            if (failure != null) {
                status.setText("导入失败：" + failure);
                return;
            }
            status.setText("九号出行已导入，可以打开并登录账号。");
            new MaterialAlertDialogBuilder(this)
                    .setTitle("导入完成")
                    .setMessage("九号出行已安装到工具的独立环境。接下来可打开应用登录；"
                            + "如果只使用 Root 读取原机数据库，也可以暂不登录。")
                    .setNegativeButton("稍后打开", null)
                    .setPositiveButton("现在打开", (dialog, which) -> openVirtualNinebot())
                    .show();
        });
    }

    private File copyImportedApkToCache(Uri source) throws Exception {
        File dir = new File(getCacheDir(), "ninebot-import");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("无法创建导入缓存");
        File target = new File(dir, "selected-ninebot.apk");
        if (target.exists() && !target.delete()) throw new Exception("无法更新导入缓存");
        long total = 0;
        try (InputStream in = getContentResolver().openInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            if (in == null) throw new Exception("无法读取所选 APK");
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (count <= 0) continue;
                total += count;
                if (total > MAX_IMPORTED_APK_BYTES) {
                    throw new Exception("所选 APK 体积异常，已停止导入");
                }
                out.write(buffer, 0, count);
            }
            out.flush();
        } catch (Exception error) {
            target.delete();
            throw error;
        }
        if (total < 1024L * 1024L) {
            target.delete();
            throw new Exception("所选 APK 文件不完整");
        }
        return target;
    }

    private String importFailureMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "无法读取或安装 APK，请更换完整文件后重试";
        }
        String clean = message.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 180 ? clean.substring(0, 180) : clean;
    }

    private boolean isVirtualNinebotInstalled() {
        try {
            return BlackBoxCore.get().isInstalled(NINEBOT_PACKAGE, virtualUserId);
        } catch (Throwable error) {
            return false;
        }
    }

    private void refreshImportedNinebotUi() {
        boolean installed = isVirtualNinebotInstalled();
        if (virtualEnvButton != null) virtualEnvButton.setText("登录九号出行");
        if (loginButton != null) loginButton.setText("登录九号出行");
        String importAction = installed ? "重新导入九号出行 APK" : "导入九号出行 APK";
        if (homeImportNinebotButton != null) homeImportNinebotButton.setText(importAction);
        if (importNinebotApkButton != null) importNinebotApkButton.setText(importAction);
        if (importedNinebotStatusView != null) {
            importedNinebotStatusView.setText(installed
                    ? "已导入，可打开登录；Root 模式也可直接读取原机数据库"
                    : "尚未导入；可选择 APK 或使用 Root 读取本机安装包");
        }
        if (clearVirtualNinebotButton != null) {
            clearVirtualNinebotButton.setEnabled(!running && installed);
        }
    }

    private void confirmClearVirtualNinebotData() {
        if (running || isFinishing()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("清空已导入九号出行数据？")
                .setMessage(
                        "这会删除本工具虚拟环境中九号出行的账号登录状态、缓存和车辆数据库。\n\n" +
                        "不会删除或修改宿主机上的官方九号出行数据。\n\n" +
                        "清空后必须重新使用家庭共享账号和密码登录，操作无法撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认清空", (dialog, which) -> clearVirtualNinebotData())
                .show();
    }

    private void clearVirtualNinebotData() {
        if (running) return;
        setBusy(true);
        status.setText("正在清空已导入九号出行数据…");
        worker.submit(() -> {
            String failure = null;
            try {
                BlackBoxCore core = BlackBoxCore.get();
                core.stopPackage(NINEBOT_PACKAGE, virtualUserId);
                if (core.isInstalled(NINEBOT_PACKAGE, virtualUserId)) {
                    core.clearPackage(NINEBOT_PACKAGE, virtualUserId);
                }
            } catch (Throwable t) {
                failure = t.getClass().getSimpleName() + "：" + t.getMessage();
            }
            String clearFailure = failure;
            runOnUiThread(() -> {
                setBusy(false);
                if (clearFailure != null) {
                    status.setText("清空已导入九号出行数据失败：" + clearFailure);
                    return;
                }
                pendingReadAfterCredentials = false;
                reloadAfterVirtualLogin = false;
                replaceDevices(new ArrayList<>(), "已导入九号出行数据已清空");
                status.setText("已清空工具独立环境中的九号出行数据。宿主机九号出行未受影响。");
                refreshImportedNinebotUi();
                showPage(PAGE_HOME);
            });
        });
    }

    private void openVirtualNinebot() {
        if (running) return;
        if (!isVirtualNinebotInstalled()) {
            showNinebotImportOptions();
            return;
        }
        int nightMode = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt(KEY_TOOL_NIGHT_MODE_BEFORE_GUEST, nightMode)
                .commit();
        try {
            reloadAfterVirtualLogin = true;
            status.setText(highlightPhrase(
                    "请使用账号密码登录；完成后点击右侧“登录完成 / 返回检测”。",
                    "账号密码登录"));
            boolean launched = BlackBoxCore.get().launchApk(NINEBOT_PACKAGE, virtualUserId);
            if (!launched) {
                pendingReadAfterCredentials = false;
                reloadAfterVirtualLogin = false;
                status.setText("九号出行启动失败，请重新导入完整 APK 后再试。");
            }
        } catch (Throwable error) {
            pendingReadAfterCredentials = false;
            reloadAfterVirtualLogin = false;
            status.setText("无法打开九号出行，请重新导入完整 APK 后再试。");
        }
    }

    private void replaceDevices(List<DeviceRecord> found, String source) {
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        String preferredVehicleKey = pendingPairedMac != null
                ? "mac:" + pendingPairedMac
                : activeDevice == null
                ? preferences.getString(KEY_SELECTED_VEHICLE, "")
                : vehicleKey(activeDevice);
        pendingPairedMac = null;
        for (DeviceRecord old : devices) old.wipe();
        devices.clear();
        devices.addAll(found);
        activeDevice = findDeviceByKey(preferredVehicleKey);
        if (activeDevice == null && !devices.isEmpty()) activeDevice = devices.get(0);
        if (activeDevice != null) {
            preferences.edit().putString(KEY_SELECTED_VEHICLE, vehicleKey(activeDevice)).apply();
        }
        lastReadProfile = -1;
        lastReadCapacity = -1;
        lastReadDisConfigRaw = -1;
        prototypeLastRead = null;
        lastCommunicationMode = CommunicationModeResolver.Mode.UNSUPPORTED;
        resetHomeReadout();
        refreshVehicleList(source);
        refreshBackup(activeDevice);
        updateCurrentVehicle();
        updateTargetSummary();
        updatePrototypeData();
    }

    private void refreshVehicleList(String source) {
        StringBuilder text = new StringBuilder();
        text.append("车辆来源：").append(source).append('\n');
        if (devices.isEmpty()) {
            text.append("未找到车辆");
        } else {
            for (DeviceRecord device : devices) {
                text.append("\n").append(testVehicleLabel(device));
                if (!device.deviceType.isEmpty()) text.append("\ntype=").append(device.deviceType);
                text.append("\n凭据：已读取\n");
            }
        }
        vehicleListView.setText(text.toString());

        boolean ready = !devices.isEmpty();
        sourceModeView.setText(ready ? "数据库已绑定" : "尚未绑定数据库");
        sourceDetailView.setText(source + " · " + devices.size() + " 台车辆");
        sourceReadyBadge.setText(ready ? "已就绪" : "未找到车辆");
        sourceVehicleCountView.setText(devices.size() + " 台");
        sourceSyncTimeView.setText("刚刚");
        settingsSourceView.setText(source);
    }

    private DeviceRecord selectedDevice() {
        return activeDevice != null && devices.contains(activeDevice) ? activeDevice : null;
    }

    private String testVehicleLabel(DeviceRecord device) {
        if (device == null) return "当前车辆";
        if (device.effectiveSn != null && !device.effectiveSn.trim().isEmpty()) {
            return device.effectiveSn.trim();
        }
        if (device.name != null && !device.name.trim().isEmpty()) return device.name.trim();
        return device.mac == null || device.mac.trim().isEmpty() ? "当前车辆" : device.mac;
    }

    private String testVehicleDetail(DeviceRecord device) {
        return device != null && !device.deviceType.isEmpty()
                ? "车型 " + device.deviceType + " · 核对序列号确认车型再连接"
                : "核对完整序列号后再连接";
    }

    private void selectDevice(int position, boolean persist) {
        if (position < 0 || position >= devices.size()) return;
        DeviceRecord selected = devices.get(position);
        boolean changed = activeDevice != selected;
        activeDevice = selected;
        if (persist) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_SELECTED_VEHICLE, vehicleKey(selected))
                    .commit();
        }
        if (changed) {
            prototypeLastRead = null;
            lastReadProfile = -1;
            lastReadCapacity = -1;
            lastReadDisConfigRaw = -1;
            lastReadWriteSupported = false;
            lastCommunicationMode = CommunicationModeResolver.Mode.UNSUPPORTED;
            pendingWriteAfterRead = false;
            resetHomeReadout();
            resetConnectedOverview();
        }
        updateCurrentVehicle();
        updateVehiclePickerUi(selected);
        refreshBackup(selected);
        updateTargetSummary();
        setBusy(running);
        updatePrototypeData();
    }

    private DeviceRecord findDeviceByKey(String key) {
        if (key == null || key.isEmpty()) return null;
        for (DeviceRecord device : devices) {
            if (key.equals(vehicleKey(device))) return device;
        }
        return null;
    }

    private String vehicleKey(DeviceRecord device) {
        if (device == null) return "";
        if (!device.mac.isEmpty()) return "mac:" + device.mac;
        if (!device.effectiveSn.isEmpty()) return "sn:" + device.effectiveSn;
        return "id:" + device.id;
    }

    private void startRead() {
        DeviceRecord d = selectedDevice();
        if (d == null || running) return;
        activeDevice = d;
        // The normal calibration entry now uses the verified comparison path:
        // read BFG first, then collect the official DIS fields in the same session.
        startOperation(d, BfgBleClient.Operation.COMPARE_READ, 0);
    }

    private void startCompareRead() {
        DeviceRecord d = selectedDevice();
        if (d == null || running) return;
        activeDevice = d;
        startOperation(d, BfgBleClient.Operation.COMPARE_READ, 0);
    }

    private void confirmWriteSelected() {
        DeviceRecord d = selectedDevice();
        if (d == null || running || lastReadProfile < 0) return;

        Backup backup = getBackup(d);
        if (!backup.valid) {
            status.setText("尚未完成原参数备份，请先重新连接车辆并读取。");
            operationState.setText("等待自动备份");
            return;
        }

        int target = profileForIndex(selectedProfileIndex);
        int capacity = expectedCore(target);
        String group = voltageName(target);

        String identity = maskIdentity(testVehicleLabel(d));
        showWriteDisclaimerConfirmation(
                "确认修改容量档位",
                identity,
                String.format(Locale.US, "%s · %s",
                        voltageName(lastReadProfile), formatCapacityShort(lastReadCapacity)),
                String.format(Locale.US, "%s · %s", group, formatCapacityShort(capacity)),
                capacity,
                "确认写入并回读",
                () -> {
                    restoringBackup = false;
                    startOperation(d, BfgBleClient.Operation.WRITE_PROFILE, target);
                });
    }

    private void confirmDisVoltageWrite() {
        DeviceRecord device = selectedDevice();
        if (device == null || running) return;
        int targetVoltage = selectedVoltageCode == 0 ? 72
                : selectedVoltageCode == 2 ? 48 : 60;
        confirmDisVoltageChange(device, targetVoltage, false);
    }

    private void confirmDisVoltageRestore() {
        DeviceRecord device = selectedDevice();
        if (device == null || running) return;
        int backupRaw = getDisConfigBackup(device);
        if (DisVoltageConfig.nominalVoltage(backupRaw) < 0) {
            status.setText("没有首次仪表配置备份，请先连接车辆读取。");
            return;
        }
        if ((backupRaw & 0xF0) != (lastReadDisConfigRaw & 0xF0)) {
            status.setText("当前仪表配置与首次备份不属于同一组，已停止自动恢复。");
            return;
        }
        confirmDisVoltageChange(device, DisVoltageConfig.nominalVoltage(backupRaw), true);
    }

    private void confirmDisVoltageChange(DeviceRecord device, int targetVoltage,
                                         boolean restoring) {
        if (!dashboardWriteAllowed(prototypeLastRead)) {
            status.setText(DashboardWritePolicy.blockedMessage());
            return;
        }
        if (DisVoltageConfig.nominalVoltage(lastReadDisConfigRaw) < 0) {
            status.setText("还没有读取到有效的仪表电压配置，请重新连接车辆读取。");
            return;
        }
        final int targetRaw;
        try {
            targetRaw = DisVoltageConfig.target(lastReadDisConfigRaw, targetVoltage);
        } catch (IllegalArgumentException error) {
            status.setText(error.getMessage());
            return;
        }
        if (targetRaw == lastReadDisConfigRaw) {
            status.setText("仪表已经是所选电压档位，无需发送写入。");
            return;
        }
        boolean unverified = DisVoltageConfig.requiresExtraWarning(
                lastReadDisConfigRaw, targetRaw);
        String details = String.format(Locale.US,
                "仪表当前配置：%02X（%s）\n拟写入配置：%02X（%dV）\n\n"
                        + "测试版只更改仪表电压配置，不更改容量槽位。"
                        + "写入后会回读；断电后是否保持、是否同步到计量模块，还需要实车验证。",
                lastReadDisConfigRaw, formatNominalVoltage(
                        DisVoltageConfig.nominalVoltage(lastReadDisConfigRaw)),
                targetRaw, targetVoltage);
        if (unverified) {
            details += "\n\n⚠ 当前或目标编码尚未在实车验证。"
                    + "尝试写入可能失败，也可能造成车辆显示或运行异常。"
                    + "请确认能够恢复原配置后再继续。";
        }
        if (restoring) details += "\n\n本次目标是首次读取保存的仪表配置。";
        new MaterialAlertDialogBuilder(this)
                .setTitle(unverified ? "未验证配置，确认尝试？"
                        : restoring ? "恢复首次仪表配置" : "实验：写入仪表电压配置")
                .setMessage(details)
                .setNegativeButton("取消", null)
                .setPositiveButton(unverified ? "接受风险并尝试" : "确认尝试写入", (dialog, which) -> {
                    allowUnverifiedDisWrite = unverified;
                    startOperation(device, BfgBleClient.Operation.WRITE_DIS_VOLTAGE,
                            targetVoltage);
                })
                .show();
    }

    private void confirmRestore() {
        DeviceRecord d = selectedDevice();
        if (d == null || running) return;
        Backup first = getBackup(d);
        Backup recent = getPrewriteBackup(d);
        if (!first.valid && !recent.valid) {
            status.setText("没有这个车辆的有效备份，不能恢复。");
            return;
        }
        if (first.valid && recent.valid
                && (first.profile != recent.profile || first.capacity != recent.capacity)) {
            String recentText = "恢复本次写入前快照（" + voltageName(recent.profile) + " · "
                    + formatCapacityShort(recent.capacity) + "）";
            String firstText = "恢复首次备份（" + voltageName(first.profile) + " · "
                    + formatCapacityShort(first.capacity) + "）";
            new MaterialAlertDialogBuilder(this)
                    .setTitle("选择恢复到哪份备份")
                    .setItems(new String[]{recentText, firstText}, (dialog, which) ->
                            confirmRestoreTo(d, which == 0 ? recent : first,
                                    which == 0 ? "本次写入前快照" : "首次备份"))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        confirmRestoreTo(d, recent.valid ? recent : first,
                recent.valid ? "本次写入前快照" : "首次备份");
    }

    private void confirmRestoreTo(DeviceRecord d, Backup b, String sourceName) {
        if (d == null || !b.valid || running
                || WriteAccessPolicy.isReadOnlySerial(d.effectiveSn)) {
            showPrototypeModal("restore-unavailable",
                    "当前车辆没有可用备份、正在执行其他操作，或该车仅允许读取。请核对车辆后重试。",
                    "settings");
            return;
        }
        String identity = maskIdentity(testVehicleLabel(d));
        String current = lastReadProfile >= 0
                ? String.format(Locale.US, "%s · %s",
                voltageName(lastReadProfile), formatCapacityShort(lastReadCapacity))
                : "当前车辆参数";
        showWriteDisclaimerConfirmation(
                "确认恢复" + sourceName,
                identity,
                current,
                String.format(Locale.US, "%s · %s",
                        voltageName(b.profile), formatCapacityShort(b.capacity)),
                b.capacity,
                "恢复" + sourceName,
                () -> {
                    showPage(PAGE_OPERATION);
                    showWritePanel();
                    restoringBackup = true;
                    startOperation(d, BfgBleClient.Operation.WRITE_PROFILE, b.profile);
                });
    }

    private void showWriteDisclaimerConfirmation(String titleText, String vehicleText,
                                                 String beforeText, String afterText,
                                                 int targetCapacityMah,
                                                 String actionText, Runnable confirmedAction) {
        View content = getLayoutInflater().inflate(R.layout.dialog_confirm_write, null);
        TextView title = content.findViewById(R.id.confirm_write_title);
        TextView vehicle = content.findViewById(R.id.confirm_write_vehicle);
        TextView before = content.findViewById(R.id.confirm_write_before);
        TextView after = content.findViewById(R.id.confirm_write_after);
        TextView compatibilityWarning =
                content.findViewById(R.id.confirm_write_compat_warning);
        androidx.core.widget.NestedScrollView scroll =
                content.findViewById(R.id.confirm_write_scroll);
        CheckBox disclaimerCheck = content.findViewById(R.id.confirm_write_disclaimer_check);
        title.setText(titleText);
        vehicle.setText(vehicleText);
        before.setText(beforeText);
        after.setText(afterText);
        if (isCapacityScanCompatibility()) {
            compatibilityWarning.setVisibility(View.VISIBLE);
            compatibilityWarning.setText(isRecommendedCompatCapacity(targetCapacityMah)
                    ? "兼容车型推荐：18Ah与26Ah的成功率优先。当前选择属于推荐档位，仍需以写入后回读为准。"
                    : "当前容量尚未完成兼容适配，写入可能失败。若回读仍是原档位，表示车辆没有接受写入，请勿连续重复操作。");
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton(actionText, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button positive = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            positive.setEnabled(false);
            fitDialogContentToScreen(dialog, scroll);
            disclaimerCheck.setOnCheckedChangeListener((button, checked) ->
                    positive.setEnabled(checked));
            positive.setOnClickListener(v -> {
                if (!disclaimerCheck.isChecked()) return;
                dialog.dismiss();
                confirmedAction.run();
            });
        });
        dialog.show();
    }

    private void showDashboardRiskDialog(DeviceRecord device, int targetVoltage) {
        BfgBleClient.Result read = prototypeLastRead;
        if (!dashboardWriteAllowed(read)) {
            prewriteApproved = false;
            prototypeWriteFailure(DashboardWritePolicy.blockedMessage());
            return;
        }
        String intro = "本次将写入仪表盘的持久配置，目标为 " + targetVoltage
                + "V。已读取到指定的四项固件版本，但版本匹配不代表这次写入安全。"
                + "请停车并核对车辆、电池和备份后再决定。";
        String critical = "已知在老车型上，写入仪表盘配置后曾发生计量模块损坏。"
                + "不同车辆的寄存器位置和含义可能不同，无法保证写入结果。"
                + "写入可能使车辆无法启动、仪表显示异常或计量模块损坏。"
                + "即使备份了原参数，也可能无法恢复。更换原装计量模块后，"
                + "仪表盘仍可能再次下发配置，使新模块再次受损。";
        Runnable cancel = () -> {
            prewriteApproved = false;
            dashboardRiskAccepted = false;
            prototypeGo("write");
        };
        showTimedRiskDialog("仪表盘永久写入 · 高风险", intro, critical,
                "我已阅读并理解上述已知损坏案例和不可逆风险",
                TimedRiskGate.DASHBOARD_SECONDS,
                "继续查看最后提醒", () -> showTimedRiskDialog(
                        "再次劝告：仍可能造成损坏",
                        "若不确定车辆配置，请取消并保持只读。备份和回读都不能保证断电后的安全性或恢复成功。",
                        "请勿把写入当作维修或官方校准。本人确认仍要继续，并愿意承担因本人选择错误参数或操作不当造成的损失；本确认不排除依法享有的权利。",
                        "我仍决定写入，并理解上述风险", 0,
                        "我执意写入", () -> {
                            dashboardRiskAccepted = true;
                            startOperation(device, BfgBleClient.Operation.WRITE_DIS_VOLTAGE,
                                    targetVoltage);
                        }, cancel), cancel);
    }

    private void showMeterWriteRiskDialog(DeviceRecord device, int targetProfile) {
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        long snapshotTime = preferences.getLong(backupKey(device, "prewrite_time"), 0);
        int snapshotProfile = preferences.getInt(backupKey(device, "prewrite_profile"), -1);
        int snapshotCapacity = preferences.getInt(backupKey(device, "prewrite_capacity"), -1);
        if (snapshotTime <= 0 || snapshotProfile < 0 || snapshotCapacity <= 0
                || prototypeLastRead == null
                || snapshotProfile != prototypeLastRead.beforeProfile
                || snapshotCapacity != prototypeLastRead.displayBeforeCapacity()) {
            prewriteApproved = false;
            prototypeWriteFailure("本次写入前备份未能核实，没有发送写入指令。请重新连接并读取车辆。");
            return;
        }
        String sessionKey = device.mac + ":" + targetProfile;
        if (APPROVED_METER_RISK_SESSION_KEYS.contains(sessionKey)) {
            meterRiskAccepted = true;
            startOperation(device, BfgBleClient.Operation.WRITE_PROFILE, targetProfile);
            return;
        }
        boolean unadapted = prototypeLastRead == null
                || WriteAccessPolicy.needsMeterCompatibilityWarning(
                        prototypeLastRead.disBfgVersionRaw);
        String version = prototypeLastRead == null ? "未读取到"
                : formatFwVersion(prototypeLastRead.disBfgVersionRaw);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(18), dp(20), dp(12));
        addMeterRiskText(body, "写入前确认", 13, R.color.ninebot_blue, true, 0);
        addMeterRiskText(body, "临时写入计量模块", 22, R.color.ninebot_text, true, 8);

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setBackgroundResource(R.drawable.bg_ninebot_info);
        summary.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(14);
        body.addView(summary, summaryParams);
        addMeterRiskText(summary, "车辆  " + maskIdentity(testVehicleLabel(device)),
                15, R.color.ninebot_text, false, 0);
        addMeterRiskText(summary, "当前  " + voltageName(snapshotProfile) + " · "
                        + formatCapacityShort(snapshotCapacity),
                15, R.color.ninebot_text, false, 8);
        addMeterRiskText(summary, "目标  " + voltageName(targetProfile) + " · "
                        + formatCapacityShort(BfgProfileCatalog.expectedCore(targetProfile)),
                15, R.color.ninebot_blue, true, 6);
        addMeterRiskText(body, "计量模块 " + version, 14, R.color.ninebot_muted, false, 10);
        String backupTime = new java.text.SimpleDateFormat("HH:mm:ss", Locale.US)
                .format(new java.util.Date(snapshotTime));
        addMeterRiskText(body, "本次写入前快照已保存 · " + backupTime,
                13, R.color.ninebot_muted, false, 5);

        TextView warning = addMeterRiskText(body,
                "断电后可能恢复原参数。写入可能失败或造成数据显示异常，备份不保证恢复。"
                        + (unadapted ? "\n此计量模块版本尚未验证，可尝试写入，但结果以回读为准。" : ""),
                15, R.color.ninebot_danger, true, 14);
        warning.setBackgroundResource(R.drawable.bg_ninebot_error);
        warning.setPadding(dp(14), dp(13), dp(14), dp(13));

        CheckBox checkbox = new CheckBox(this);
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        checkParams.topMargin = dp(14);
        checkbox.setLayoutParams(checkParams);
        checkbox.setText("我已核对车辆与目标参数，并了解上述风险");
        checkbox.setTextColor(ContextCompat.getColor(this, R.color.ninebot_text));
        checkbox.setTextSize(15);
        checkbox.setMinHeight(dp(48));
        body.addView(checkbox);

        androidx.core.widget.NestedScrollView scroll =
                new androidx.core.widget.NestedScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(body);
        boolean[] accepted = {false};
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("继续写入", null)
                .setCancelable(false)
                .create();
        dialog.setOnDismissListener(ignored -> {
            if (accepted[0]) return;
            prewriteApproved = false;
            meterRiskAccepted = false;
            prototypeGo("write");
        });
        dialog.setOnShowListener(ignored -> {
            fitDialogContentToScreen(dialog, scroll);
            Button positive = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            long readyAt = SystemClock.elapsedRealtime() + TimedRiskGate.METER_SECONDS * 1000L;
            Runnable tick = new Runnable() {
                @Override public void run() {
                    if (!dialog.isShowing()) return;
                    int left = (int) Math.max(0,
                            (readyAt - SystemClock.elapsedRealtime() + 999) / 1000);
                    positive.setEnabled(TimedRiskGate.canProceed(
                            SystemClock.elapsedRealtime(), readyAt, checkbox.isChecked()));
                    positive.setText(left == 0 ? "继续写入" : "继续写入 · " + left + "秒");
                    if (left > 0) mainHandler.postDelayed(this, 250);
                }
            };
            checkbox.setOnCheckedChangeListener((button, checked) -> tick.run());
            positive.setOnClickListener(v -> {
                if (!TimedRiskGate.canProceed(SystemClock.elapsedRealtime(),
                        readyAt, checkbox.isChecked())) return;
                APPROVED_METER_RISK_SESSION_KEYS.add(sessionKey);
                accepted[0] = true;
                dialog.dismiss();
                meterRiskAccepted = true;
                startOperation(device, BfgBleClient.Operation.WRITE_PROFILE, targetProfile);
            });
            tick.run();
        });
        dialog.show();
    }

    private TextView addMeterRiskText(LinearLayout parent, String value, int sp,
                                      int colorRes, boolean bold, int topDp) {
        TextView text = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topDp);
        text.setLayoutParams(params);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(ContextCompat.getColor(this, colorRes));
        if (bold) text.setTypeface(null, android.graphics.Typeface.BOLD);
        text.setLineSpacing(dp(2), 1f);
        parent.addView(text);
        return text;
    }

    private void showTimedRiskDialog(String title, String intro, String critical,
                                     String acknowledgement, int waitSeconds,
                                     String action, Runnable proceed, Runnable cancel) {
        if (isFinishing() || isDestroyed()) {
            cancel.run();
            return;
        }
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(16), dp(20), dp(12));
        TextView description = new TextView(this);
        description.setText(intro);
        description.setTextColor(ContextCompat.getColor(this, R.color.ninebot_text));
        description.setTextSize(16);
        description.setLineSpacing(dp(3), 1f);
        body.addView(description);
        TextView warning = new TextView(this);
        LinearLayout.LayoutParams warningParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        warningParams.topMargin = dp(16);
        warning.setLayoutParams(warningParams);
        warning.setBackgroundResource(R.drawable.bg_ninebot_error);
        warning.setPadding(dp(14), dp(14), dp(14), dp(14));
        warning.setText(critical);
        warning.setTextColor(ContextCompat.getColor(this, R.color.ninebot_danger));
        warning.setTextSize(16);
        warning.setTypeface(null, android.graphics.Typeface.BOLD);
        warning.setLineSpacing(dp(3), 1f);
        body.addView(warning);
        CheckBox checkbox = new CheckBox(this);
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        checkParams.topMargin = dp(14);
        checkbox.setLayoutParams(checkParams);
        checkbox.setText(acknowledgement);
        checkbox.setTextColor(ContextCompat.getColor(this, R.color.ninebot_text));
        checkbox.setTextSize(15);
        checkbox.setMinHeight(dp(48));
        body.addView(checkbox);
        androidx.core.widget.NestedScrollView scroll =
                new androidx.core.widget.NestedScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(body);
        boolean[] accepted = {false};
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton(action, null)
                .setCancelable(false)
                .create();
        dialog.setOnDismissListener(ignored -> {
            if (!accepted[0]) cancel.run();
        });
        dialog.setOnShowListener(ignored -> {
            Button positive = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            positive.setEnabled(false);
            fitDialogContentToScreen(dialog, scroll);
            long readyAt = SystemClock.elapsedRealtime() + waitSeconds * 1000L;
            Runnable tick = new Runnable() {
                @Override public void run() {
                    if (!dialog.isShowing()) return;
                    int left = (int) Math.max(0,
                            (readyAt - SystemClock.elapsedRealtime() + 999) / 1000);
                    positive.setEnabled(TimedRiskGate.canProceed(
                            SystemClock.elapsedRealtime(), readyAt, checkbox.isChecked()));
                    positive.setText(left == 0 ? action : action + "（" + left + "秒）");
                    if (left > 0) mainHandler.postDelayed(this, 250);
                }
            };
            checkbox.setOnCheckedChangeListener((button, checked) ->
                    positive.setEnabled(TimedRiskGate.canProceed(
                            SystemClock.elapsedRealtime(), readyAt, checked)));
            positive.setOnClickListener(v -> {
                if (!TimedRiskGate.canProceed(SystemClock.elapsedRealtime(),
                        readyAt, checkbox.isChecked())) return;
                accepted[0] = true;
                dialog.dismiss();
                proceed.run();
            });
            tick.run();
        });
        dialog.show();
    }

    private void fitDialogContentToScreen(
            androidx.appcompat.app.AlertDialog dialog, View scroll) {
        android.view.Window window = dialog.getWindow();
        if (window == null || scroll == null) return;
        window.setLayout(android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        View decor = window.getDecorView();
        decor.post(() -> {
            if (!dialog.isShowing()) return;
            Rect visibleFrame = new Rect();
            decor.getWindowVisibleDisplayFrame(visibleFrame);
            int screenHeight = visibleFrame.height() > 0
                    ? visibleFrame.height()
                    : getResources().getDisplayMetrics().heightPixels;
            int maximumHeight = Math.max(dp(260), screenHeight - dp(24));
            int nonContentHeight = Math.max(0, decor.getHeight() - scroll.getHeight());
            int maximumContentHeight = Math.max(dp(100), maximumHeight - nonContentHeight);
            if (scroll.getHeight() > maximumContentHeight) {
                android.view.ViewGroup.LayoutParams params = scroll.getLayoutParams();
                params.height = maximumContentHeight;
                scroll.setLayoutParams(params);
            }
            scroll.setVerticalScrollBarEnabled(true);
            scroll.requestLayout();
        });
    }

    private void showBackupDetails() {
        DeviceRecord d = selectedDevice();
        Backup first = getBackup(d);
        Backup recent = getPrewriteBackup(d);
        if (!first.valid && !recent.valid) return;
        String identity = maskIdentity(testVehicleLabel(d));
        new MaterialAlertDialogBuilder(this)
                .setTitle("车辆参数备份")
                .setMessage(String.format(Locale.US,
                        "车辆：%s\n首次备份：%s\n本次写入前快照：%s\n\n恢复时可选择目标。备份不保证车辆硬件异常后仍能恢复。",
                        identity, backupDescription(first), backupDescription(recent)))
                .setPositiveButton("知道了", null)
                .show();
    }

    private String backupDescription(Backup backup) {
        if (!backup.valid) return "未保存";
        return voltageName(backup.profile) + " · " + formatCapacityShort(backup.capacity)
                + "（" + new java.text.SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
                .format(new java.util.Date(backup.time)) + "）";
    }

    private void showWriteComplete(BfgBleClient.Result r, boolean restored) {
        Backup backup = getBackup(activeDevice);
        int displayAfterCapacity = r.displayAfterCapacity();
        Backup recent = getPrewriteBackup(activeDevice);
        writeCompleteBackupSummary.setText("首次备份：" + backupDescription(backup)
                + "\n本次写入前快照：" + backupDescription(recent));
        writeCompleteTitle.setText(restored ? "原参数恢复成功" : "写入并回读成功");
        writeCompleteSubtitle.setText(restored
                ? "原参数已经恢复并完成回读确认。"
                : "参数已经生效，无需返回页面顶部查看。");
        writeCompleteBefore.setText(String.format(Locale.US, "%s · %s",
                voltageName(r.beforeProfile), formatCapacityShort(r.displayBeforeCapacity())));
        writeCompleteAfter.setText(String.format(Locale.US, "%s · %s",
                voltageName(r.afterProfile), formatCapacityShort(displayAfterCapacity)));
        writeCompleteCheckCommand.setText(restored
                ? "✓  恢复指令已完成" : "✓  写入指令已完成");
        writeCompleteCheckReadback.setText(restored
                ? "✓  原参数回读一致"
                : r.communicationMode
                == CommunicationModeResolver.Mode.CAPACITY_SCAN_COMPAT
                ? "✓  兼容扫描Profile档位回读一致"
                : r.capacityReadbackVerified
                ? "✓  容量档位回读一致"
                : "✓  Profile 档位回读一致");
        writeCompleteRestoreButton.setText("选择备份并恢复");
        writeCompleteRestoreButton.setEnabled((backup.valid || recent.valid) && !running);
        showPage(PAGE_WRITE_COMPLETE);
        View scroll = findViewById(R.id.write_complete_scroll);
        if (scroll != null) scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void startOperation(DeviceRecord d, BfgBleClient.Operation op, int target) {
        if (op == BfgBleClient.Operation.WRITE_DIS_VOLTAGE
                && !dashboardWriteAllowed(prototypeLastRead)) {
            prototypeWriteFailure(DashboardWritePolicy.blockedMessage());
            return;
        }
        boolean write = op == BfgBleClient.Operation.WRITE_PROFILE
                || op == BfgBleClient.Operation.WRITE_DIS_VOLTAGE;
        if (write && d != null && WriteAccessPolicy.isReadOnlySerial(d.effectiveSn)) {
            prototypeWriteFailure("该车序列号以 N 开头，本版本仅允许读取数据，不发送任何写入指令。");
            return;
        }
        if (write && !prewriteApproved) {
            if (d == null) {
                prototypeWriteFailure("尚未选择车辆，本次没有发送写入指令。");
                return;
            }
            pendingPrewriteOperation = op;
            pendingPrewriteDevice = d;
            pendingPrewriteTarget = target;
            pendingPrewriteUnverified = allowUnverifiedDisWrite;
            prototypeSetWriteStage("precheck");
            prototypeGo("write-progress");
            startOperation(d, BfgBleClient.Operation.COMPARE_READ, 0);
            return;
        }
        if (prewriteApproved && op == BfgBleClient.Operation.WRITE_DIS_VOLTAGE
                && !dashboardRiskAccepted) {
            showDashboardRiskDialog(d, target);
            return;
        }
        if (prewriteApproved && op == BfgBleClient.Operation.WRITE_PROFILE
                && !meterRiskAccepted) {
            showMeterWriteRiskDialog(d, target);
            return;
        }
        if (write) {
            prewriteApproved = false;
            dashboardRiskAccepted = false;
            meterRiskAccepted = false;
        }
        pendingPermissionDevice = d;
        pendingPermissionOperation = op;
        pendingPermissionTarget = target;
        if (!ensureBluetoothPermissions()) return;
        clearPendingPermissionOperation();
        startOperationConfirmed(d, op, target);
    }

    private void startOperationConfirmed(DeviceRecord d, BfgBleClient.Operation op, int target) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            pendingPrewriteOperation = null;
            status.setText("此设备没有蓝牙硬件。数据库读取和凭据导入/导出仍可使用。");
            showPrototypeModal("bluetooth-off", "此手机没有可用蓝牙设备，暂时无法连接车辆。", "connect-ready");
            return;
        }
        if (!adapter.isEnabled()) {
            pendingPrewriteOperation = null;
            status.setText("请先打开系统蓝牙。应用不会替你重启蓝牙。");
            pendingPostWriteVerification = false;
            showPrototypeModal("bluetooth-off", "系统蓝牙尚未开启。请打开蓝牙后返回重试。",
                    op == BfgBleClient.Operation.PAIR_AND_READ ? "pair-ready" : "connect-ready");
            return;
        }
        activeDevice = d;
        final int expectedDisRaw = lastReadDisConfigRaw;
        final boolean acceptedUnverifiedDis = allowUnverifiedDisWrite;
        allowUnverifiedDisWrite = false;
        final int generation = ++operationGeneration;
        activeBleOperation = op;
        diagnosticRecorder.start(d, op, target);
        diagnosticRecorder.record("UI", "Operation requested; pendingWriteAfterRead="
                + pendingWriteAfterRead + " restoringBackup=" + restoringBackup);
        refreshDiagnosticStatus();
        final BfgBleClient.Listener operationListener = scopedBleListener(generation);
        setBusy(true);
        if (op == BfgBleClient.Operation.WRITE_PROFILE
                || op == BfgBleClient.Operation.WRITE_DIS_VOLTAGE) {
            prototypeSetWriteStage("writing");
            result.setText("写入测试中…");
            showInlineWriteProgress(restoringBackup);
            if (op == BfgBleClient.Operation.WRITE_DIS_VOLTAGE) {
                writeProgressText.setText("正在尝试写入仪表配置并回读…");
                writeButton.setText("临时写入计量模块并回读");
            }
        } else if (op == BfgBleClient.Operation.COMPARE_READ) {
            result.setText("正在读取完整车辆数据：先读 BFG，再读仪表字段…");
        } else {
            result.setText("读取中…");
        }
        logView.setText("");

        worker.submit(() -> {
            try {
                // Release both possible Ninebot processes before the host-side
                // scanner opens GATT. The guest is stopped through BlackBox;
                // the real app is force-stopped only in Root mode.
                try {
                    BlackBoxCore.get().stopPackage(NINEBOT_PACKAGE, virtualUserId);
                } catch (Throwable ignored) {
                }
                boolean rootSource = "ninebot_db".equals(d.source);
                boolean rootMode = getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getBoolean(KEY_ROOT_MODE, false) || rootSource;
                boolean hostNinebotRunning = rootMode && RootDb.isNinebotRunning(this, true);

                String warning = null;
                if (rootMode) {
                    warning = RootDb.tryForceStopNinebot();
                    if (warning == null && RootDb.isNinebotRunning(this, true)) {
                        warning = "主机九号出行仍在后台，无法安全开始扫描";
                    }
                }
                if (warning == null) Thread.sleep(500);
                String stopWarning = warning;
                runOnUiThread(() -> {
                    if (generation != operationGeneration || activeBleOperation != op || !running) {
                        return;
                    }
                    if (rootMode && stopWarning == null) {
                        status.setText(hostNinebotRunning
                                ? "检测到宿主车辆应用在后台，已自动关闭并释放蓝牙；开始扫描车辆…"
                                : "宿主车辆应用未占用蓝牙；开始扫描车辆…");
                    } else if (!rootMode) {
                        status.setText("正在直接连接车辆…");
                    } else {
                        operationListener.onError(stopWarning + "。请关闭宿主九号出行后重试。");
                        return;
                    }
                    BfgBleClient newClient = new BfgBleClient(
                            this, d, op, target, expectedDisRaw,
                            acceptedUnverifiedDis, operationListener);
                    if (generation != operationGeneration || activeBleOperation != op || !running) {
                        newClient.close();
                        return;
                    }
                    client = newClient;
                    newClient.connect();
                });
            } catch (Throwable t) {
                runOnUiThread(() -> operationListener.onError(
                        "准备连接失败：" + t.getMessage()));
            }
        });
    }

    private CharSequence highlightPhrase(String text, String phrase) {
        SpannableString styled = new SpannableString(text);
        int start = text.indexOf(phrase);
        if (start >= 0) {
            styled.setSpan(new ForegroundColorSpan(
                            ContextCompat.getColor(this, R.color.ninebot_danger)),
                    start, start + phrase.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return styled;
    }

    private void showInlineWriteProgress(boolean restoring) {
        if (writeProgressPanel == null) return;
        operationStatusCard.setVisibility(View.GONE);
        if (writeInlineResult != null) writeInlineResult.setVisibility(View.GONE);
        writeProgressText.setText(restoring
                ? R.string.ninebot_restore_progress : R.string.ninebot_write_progress);
        writeButton.setText(restoring ? "正在恢复并回读…" : "正在写入并回读…");
        writeProgressPanel.setVisibility(View.VISIBLE);
        writeProgressPanel.setAlpha(0f);
        writeProgressPanel.setTranslationY(dp(10));
        writeProgressPanel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .start();
        revealInlineStatus(writeProgressPanel);
    }

    private void showInlineWriteResult(String title, String detail, boolean success) {
        if (writeProgressPanel != null) writeProgressPanel.setVisibility(View.GONE);
        writeButton.setText("临时写入计量模块并回读");
        if (calibrationFirmwareSummary != null) {
            calibrationFirmwareSummary.setVisibility(View.GONE);
        }
        if (writeInlineResult == null) return;
        writeInlineResult.setBackgroundResource(success
                ? R.drawable.bg_ninebot_good_badge : R.drawable.bg_ninebot_error);
        writeInlineResultTitle.setText(title);
        writeInlineResultTitle.setTextColor(ContextCompat.getColor(this,
                success ? R.color.ninebot_good : R.color.ninebot_danger));
        writeInlineResultText.setTextColor(ContextCompat.getColor(this, R.color.ninebot_text));
        writeInlineResultText.setText(detail);
        writeInlineResult.setVisibility(View.VISIBLE);
        writeInlineResult.setAlpha(0f);
        writeInlineResult.setScaleX(0.97f);
        writeInlineResult.setScaleY(0.97f);
        writeInlineResult.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240)
                .start();
        revealInlineStatus(writeInlineResult);
    }

    private void revealInlineStatus(View view) {
        view.postDelayed(() -> {
            Rect rect = new Rect(0, 0, view.getWidth(), view.getHeight() + dp(96));
            view.requestRectangleOnScreen(rect, true);
        }, 100);
    }

    private boolean isVehicleConnectionFailure(String message) {
        if (message == null || message.isEmpty()) return false;
        return message.contains("PRE_COMM")
                || message.contains("未扫描到")
                || message.contains("扫描失败")
                || message.contains("连接失败")
                || message.contains("连接超时")
                || message.contains("连接初始化失败")
                || message.contains("GATT连接失败")
                || message.contains("车辆断开连接")
                || message.contains("发现服务失败")
                || message.contains("发现服务超时")
                || message.contains("初始化GATT失败")
                || message.contains("开启Notify失败")
                || message.contains("开启通知超时")
                || message.contains("无法安全开始扫描")
                || message.contains("discoverServices返回false");
    }

    private String vehicleConnectionAdvice(String detail) {
        return "⚠ 车辆连接未完成\n"
                + "请检查主机中的九号出行是否在后台运行，或其他手机已连接该车辆。\n\n"
                + "请关闭占用车辆蓝牙的应用，断开其他手机，唤醒车辆并靠近后重新连接。";
    }

    private String writeReadbackAdvice(boolean connectionFailure, boolean uncertainWrite) {
        if (connectionFailure) {
            return "车辆连接中断，暂时无法确认写入结果。\n\n"
                    + "请检查：\n"
                    + "• 主机中的九号出行是否仍在后台运行\n"
                    + "• 是否有其他手机正在连接车辆\n"
                    + "• 车辆是否已唤醒并靠近手机\n\n"
                    + "排除占用后重新连接并读取当前参数，请勿连续重复写入。";
        }
        if (uncertainWrite) {
            return "写入指令已经发送，但本次未能完成回读确认。\n\n"
                    + "请重新连接并读取当前参数：\n"
                    + "• 显示目标容量：写入已经生效\n"
                    + "• 仍显示原容量：可再次尝试写入\n\n"
                    + "确认当前参数前，请勿连续重复写入。";
        }
        return "本次操作未完成，当前参数没有得到确认。\n\n"
                + "请保持车辆开启并靠近手机，然后重新连接读取当前参数。"
                + "确认读取结果后，再决定是否重新写入或恢复原参数。";
    }

    private void clearConnectionFailureStyle() {
        if (status != null) {
            status.setBackgroundResource(R.drawable.bg_ninebot_info);
            status.setTextColor(ContextCompat.getColor(this, R.color.ninebot_text));
        }
        if (operationState != null) {
            operationState.setTextColor(ContextCompat.getColor(this, R.color.ninebot_text));
        }
    }

    private void showConnectionFailureStyle() {
        if (status != null) {
            status.setBackgroundResource(R.drawable.bg_ninebot_error);
            status.setTextColor(ContextCompat.getColor(this, R.color.ninebot_danger));
        }
        if (operationState != null) {
            operationState.setTextColor(ContextCompat.getColor(this, R.color.ninebot_danger));
        }
    }

    @Override public void onStatus(String s) {
        diagnosticRecorder.record("STATUS", s);
        clearConnectionFailureStyle();
        status.setText(s);
        operationState.setText(s);
        if (writeProgressPanel != null && writeProgressPanel.getVisibility() == View.VISIBLE) {
            writeProgressText.setText(s);
        }
        if (prototypeReady && prototypeWeb != null && s != null) {
            try {
                JSONObject update = new JSONObject();
                update.put("busyMessage", s.length() > 90 ? s.substring(0, 90) : s);
                prototypeWeb.evaluateJavascript("window.bfgNativeUpdate(" + update + ")", null);
            } catch (JSONException ignored) { }
        }
    }

    @Override public void onLog(String s) {
        diagnosticRecorder.record("BLE", s);
        String old = logView.getText().toString();
        if (old.length() > 16000) old = old.substring(old.length() - 10000);
        logView.setText(old + (old.isEmpty() ? "" : "\n") + s);
    }

    @Override public void onResult(BfgBleClient.Result r) {
        diagnosticRecorder.recordResult(r);
        if (r.operation == BfgBleClient.Operation.REGISTER_SCAN) {
            setBusy(false);
            if (prototypeReady && prototypeWeb != null) {
                try {
                    JSONObject update = new JSONObject();
                    update.put("screen", "scan-result");
                    update.put("scanModule", r.registerScanModule == 0x01 ? "仪表盘" : "计量模块");
                    update.put("scanReplies", r.registerScanReplies);
                    update.put("scanTimeouts", r.registerScanTimeouts);
                    update.put("errorMessage", "");
                    prototypeWeb.evaluateJavascript("window.bfgNativeUpdate(" + update + ")", null);
                } catch (JSONException ignored) { }
            }
            return;
        }
        if (pendingPrewriteOperation != null
                && (r.operation == BfgBleClient.Operation.READ_ONLY
                || r.operation == BfgBleClient.Operation.COMPARE_READ)) {
            BfgBleClient.Operation writeOperation = pendingPrewriteOperation;
            DeviceRecord writeDevice = pendingPrewriteDevice;
            int writeTarget = pendingPrewriteTarget;
            boolean unverified = pendingPrewriteUnverified;
            pendingPrewriteOperation = null;
            pendingPrewriteDevice = null;
            setBusy(false);
            if (writeOperation == BfgBleClient.Operation.WRITE_DIS_VOLTAGE
                    && lastReadDisConfigRaw >= 0 && r.disConfigRaw != lastReadDisConfigRaw) {
                prototypeWriteFailure("写入前发现仪表配置已变化，本次没有发送写入指令。请重新核对当前参数后再试。");
                return;
            }
            boolean meterBackupReady = r.beforeProfile >= 0
                    && r.displayBeforeCapacity() > 0 && r.writeSupported;
            boolean dashboardBackupReady = r.disConfigRaw >= 0
                    && DisVoltageConfig.nominalVoltage(r.disConfigRaw) > 0
                    && dashboardWriteAllowed(r);
            if (writeDevice == null
                    || (writeOperation == BfgBleClient.Operation.WRITE_PROFILE
                    && !meterBackupReady)
                    || (writeOperation == BfgBleClient.Operation.WRITE_DIS_VOLTAGE
                    && !dashboardBackupReady)
                    || !savePrewriteSnapshot(writeDevice, r)) {
                prototypeWriteFailure("写入前未能完整读取并保存原参数，本次没有发送写入指令。请保持车辆开机后重试。");
                return;
            }
            prototypeLastRead = r;
            lastReadProfile = r.beforeProfile;
            lastReadCapacity = r.displayBeforeCapacity();
            lastReadDisConfigRaw = r.disConfigRaw;
            lastReadWriteSupported = r.writeSupported;
            diagnosticRecorder.record("BACKUP", "写入前原参数已保存；即将发送写入操作");
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                prewriteApproved = true;
                allowUnverifiedDisWrite = unverified;
                startOperation(writeDevice, writeOperation, writeTarget);
            });
            return;
        }
        boolean finishedPostWriteCheck = pendingPostWriteVerification
                && (r.operation == BfgBleClient.Operation.READ_ONLY
                || r.operation == BfgBleClient.Operation.COMPARE_READ);
        boolean finishedRetryRead = pendingRetryWriteRequest != null
                && (r.operation == BfgBleClient.Operation.READ_ONLY
                || r.operation == BfgBleClient.Operation.COMPARE_READ);
        prototypeLastRead = r;
        lastCommunicationMode = r.communicationMode;
        boolean completedRestore = restoringBackup;
        boolean verifiedWrite = false;
        lastReadProfile = r.operation == BfgBleClient.Operation.WRITE_PROFILE
                ? r.afterProfile : r.beforeProfile;
        lastReadCapacity = r.operation == BfgBleClient.Operation.WRITE_PROFILE
                ? r.displayAfterCapacity()
                : r.displayBeforeCapacity();
        lastReadDisConfigRaw = r.operation == BfgBleClient.Operation.WRITE_DIS_VOLTAGE
                && r.disConfigReadbackVerified ? r.disConfigAfterRaw : r.disConfigRaw;
        if (r.operation == BfgBleClient.Operation.READ_ONLY
                || r.operation == BfgBleClient.Operation.COMPARE_READ) {
            lastReadWriteSupported = r.writeSupported;
            prototypeWriteType = r.writeSupported || !dashboardWriteAllowed(r)
                    ? "meter" : "dashboard";
        }
        updateHomeReadout(r);

        if (activeDevice != null) {
            saveDisConfigBackup(activeDevice, r.disConfigRaw);
            if (r.operation == BfgBleClient.Operation.WRITE_DIS_VOLTAGE
                    && r.writeCommandSent && r.disConfigReadbackVerified) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putInt(backupKey(activeDevice, "dis92_last_target"),
                                r.disConfigAfterRaw).apply();
            }
            Backup existing = getBackup(activeDevice);
            if (!existing.valid) {
                // Only create the persistent safety backup from the pre-operation state.
                saveBackup(activeDevice, r.beforeProfile, r.displayBeforeCapacity());
            }
            refreshBackup(activeDevice);
        }

        if (r.operation == BfgBleClient.Operation.READ_ONLY) {
            String backupStatus = activeDevice != null && getBackup(activeDevice).valid
                    ? "✓ 原参数已保存" : "原参数尚未完整保存，写入前会重新读取并核实备份";
            result.setText(String.format(Locale.US,
                    "读取完成\n\n当前电量：%d%%\n仪表电压档位：%s\n计量模块电压档位：%s\n计量模块容量：%s\n\n%s\n✓ 本次未写入，可进入电量校准。",
                    r.displaySoc(), formatNominalVoltage(r.dashboardNominalVoltage()),
                    voltageName(r.beforeProfile),
                    formatCapacityShort(r.displayBeforeCapacity()), backupStatus));
        } else if (r.operation == BfgBleClient.Operation.COMPARE_READ) {
            DashboardVoltageResolver.Reading dashboardVoltage = r.dashboardVoltageReading();
            result.setText(String.format(Locale.US,
                    "车辆数据读取完成（没有写入）\n\n" +
                    "通信模式：%s\n" +
                    "仪表版本：%s\n" +
                    "计量模块版本：%s\n\n" +
                    "仪表配置电压：%s\n" +
                    "运行数据推算：%s\n" +
                    "计量模块电压：%s\n" +
                    "计量模块容量：%s\n" +
                    "当前电量：%d%%\n" +
                    "当前电池电压：%s\n\n%s",
                    r.communicationModeLabel(),
                    formatFwVersion(r.disDashboardVersionRaw),
                    formatFwVersion(r.disBfgVersionRaw),
                    formatNominalVoltage(r.dashboardNominalVoltage()),
                    formatCalculatedVoltage(dashboardVoltage.calculatedVoltage),
                    voltageName(r.beforeProfile),
                    formatCapacityShort(r.displayBeforeCapacity()),
                    r.displaySoc(), formatBatteryVoltage(r.disVrlaVoltageRaw),
                    r.voltageMismatch()
                            ? "⚠ 仪表与计量模块电压档位不一致"
                            : "✓ 两边电压档位一致"));
        } else if (r.operation == BfgBleClient.Operation.WRITE_DIS_VOLTAGE) {
            boolean changed = r.writeCommandSent && r.disConfigReadbackVerified;
            String before = String.format(Locale.US, "%02X", r.disConfigRaw & 0xFF);
            String after = String.format(Locale.US, "%02X", r.disConfigAfterRaw & 0xFF);
            String summary = changed
                    ? "仪表配置已从 " + before + " 回读为 " + after
                    + "（" + r.requestedDashboardVoltage + "V）。\n"
                    + "请断电重启车辆后再连接读取，验证是否保持。"
                    : "仪表原本就是目标档位，本次没有发送写入。";
            result.setText(summary);
            showInlineWriteResult(changed ? "仪表写入已回读" : "仪表配置未变化",
                    summary + "\n容量是否同步更新仍需单独核对。", changed);
        } else {
            int expected = expectedCore(r.requestedProfile);
            int displayAfterCapacity = r.displayAfterCapacity();
            String actionName = restoringBackup ? "恢复原参数" : "容量档位写入";
            String verdict = r.profileReadbackVerified || r.afterProfile == r.requestedProfile
                    ? "✓ " + actionName + "已回读确认"
                    : "✗ " + actionName + "回读不一致";
            if (r.capacityReadbackVerified && expected > 0 && r.afterCapacityRaw != expected) {
                verdict += "\n⚠ 标称容量与静态表预期不一致";
            } else if (!r.capacityReadbackVerified && r.profileReadbackVerified) {
                verdict += "\n✓ Profile档位已确认；容量寄存器未响应，不影响档位判定";
            }
            result.setText(String.format(Locale.US,
                    "写入前\n  电压档位：%s\n  SOC：%d%%\n  容量档位：%s\n\n" +
                    "写入目标\n  电压档位：%s\n  容量档位：%s\n\n" +
                    "写入后回读\n  电压档位：%s\n  容量档位：%s\n  写入响应：%s\n\n%s\n\n" +
                    (restoringBackup
                            ? "原参数已恢复。首次备份仍保留，可再次使用。"
                            : "写入完成。恢复原参数请到“设置 → 参数备份与恢复”，先核对备份再操作。"),
                    voltageName(r.beforeProfile), r.displaySoc(),
                    formatCapacityShort(r.displayBeforeCapacity()),
                    voltageName(r.requestedProfile), formatCapacityShort(expected),
                    voltageName(r.afterProfile), formatCapacityShort(displayAfterCapacity), r.writeAckSeen ? "已收到" : "未捕获（已用读取回读验证）",
                    verdict));
            showInlineWriteResult(
                    restoringBackup ? "✓ 原参数恢复成功" : "✓ 写入并回读成功",
                    String.format(Locale.US,
                            "%s · %s  →  %s · %s\n%s\n首次备份仍保留，可随时恢复。",
                            voltageName(r.beforeProfile),
                            formatCapacityShort(r.displayBeforeCapacity()),
                            voltageName(r.afterProfile), formatCapacityShort(displayAfterCapacity), verdict),
                    r.profileReadbackVerified || r.afterProfile == r.requestedProfile);
            verifiedWrite = r.profileReadbackVerified || r.afterProfile == r.requestedProfile;
        }
        if (r.operation == BfgBleClient.Operation.READ_ONLY
                || r.operation == BfgBleClient.Operation.COMPARE_READ) {
            updateConnectedOverview(r);
        }
        operationState.setText(r.operation == BfgBleClient.Operation.WRITE_DIS_VOLTAGE
                ? "仪表写入已回读 · 待断电验证"
                : r.operation == BfgBleClient.Operation.WRITE_PROFILE
                ? (restoringBackup ? "原参数恢复完成" : "临时写入完成，可随时恢复原参数")
                : "读取完成 · " + r.communicationModeLabel());
        if ((r.operation == BfgBleClient.Operation.READ_ONLY
                || r.operation == BfgBleClient.Operation.COMPARE_READ)
                && pendingWriteAfterRead
                && (activeDevice == null
                || !WriteAccessPolicy.isReadOnlySerial(activeDevice.effectiveSn))
                && (r.writeSupported
                || (dashboardWriteAllowed(r)
                && DisVoltageConfig.nominalVoltage(r.disConfigRaw) > 0))) {
            pendingWriteAfterRead = false;
            status.setText("读取完成，请先核对车辆、固件和电池信息，再进入电量校准。");
        } else if ((r.operation == BfgBleClient.Operation.READ_ONLY
                || r.operation == BfgBleClient.Operation.COMPARE_READ)
                && pendingWriteAfterRead) {
            pendingWriteAfterRead = false;
            status.setText(activeDevice != null
                    && WriteAccessPolicy.isReadOnlySerial(activeDevice.effectiveSn)
                    ? "该车辆序列号以 N 开头，仅提供只读数据。"
                    : "版本和通信组合尚未通过写入验证，本次只读取数据，不进入电量校准。");
        }
        if (r.operation == BfgBleClient.Operation.WRITE_PROFILE
                || r.operation == BfgBleClient.Operation.WRITE_DIS_VOLTAGE) {
            writePanel.setVisibility(View.VISIBLE);
        }
        updateTargetSummary();
        setBusy(false);
        if (r.operation == BfgBleClient.Operation.WRITE_PROFILE && verifiedWrite) {
            showWriteComplete(r, completedRestore);
        }
        diagnosticRecorder.record("UI", "lastReadProfile=" + lastReadProfile
                + " lastReadCapacity=" + lastReadCapacity
                + " homeCapacity=" + (homeCapacityValue == null
                ? "missing-view" : homeCapacityValue.getText().toString())
                + " verifiedWrite=" + verifiedWrite);
        refreshDiagnosticStatus();
        restoringBackup = false;
        updatePrototypeData();
        if (finishedPostWriteCheck) {
            postWriteCheckAttempts++;
            boolean targetMatches = PostWriteCheckPolicy.targetMatches(
                    r.beforeProfile, postWriteExpectedProfile,
                    r.disConfigRaw, postWriteExpectedDisRaw);
            boolean capacityPending = PostWriteCheckPolicy.capacityPending(
                    r.displaySoc(), r.disRemainingCapacityRaw);
            if (PostWriteCheckPolicy.shouldRetry(postWriteCheckAttempts,
                    targetMatches, capacityPending)) {
                diagnosticRecorder.record("UI", "Post-write verification waiting for vehicle update; attempt="
                        + postWriteCheckAttempts + " targetMatches=" + targetMatches
                        + " capacityPending=" + capacityPending);
                prototypeGo("post-write-check");
                DeviceRecord verifyDevice = activeDevice;
                delayedPostWriteRead = () -> {
                    delayedPostWriteRead = null;
                    if (pendingPostWriteVerification && !isFinishing() && !isDestroyed()) {
                        startOperation(verifyDevice, BfgBleClient.Operation.COMPARE_READ, 0);
                    }
                };
                mainHandler.postDelayed(delayedPostWriteRead, 3000);
            } else {
                pendingPostWriteVerification = false;
                if (!targetMatches) {
                    prototypeWriteFailure("二次核对后，车辆返回的档位仍与目标不一致。本次写入未确认成功；请核对当前参数后再尝试。");
                } else {
                    if (activeDevice != null && postWriteExpectedProfile >= 0) {
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                .putInt(backupKey(activeDevice, "last_confirmed_profile"),
                                        postWriteExpectedProfile)
                                .putLong(backupKey(activeDevice, "last_confirmed_time"),
                                        System.currentTimeMillis())
                                .commit();
                        updatePrototypeData();
                    }
                    showPrototypeModal("write-success", capacityPending
                            ? "目标档位已回读确认。剩余容量暂未更新，请稍后重新读取。恢复原参数请到“设置 → 参数备份与恢复”。"
                            : "二次核对完成，车辆已返回目标档位和最新剩余容量。恢复原参数请到“设置 → 参数备份与恢复”。", "review");
                }
            }
        } else if (finishedRetryRead) {
            String request = pendingRetryWriteRequest;
            pendingRetryWriteRequest = null;
            try {
                JSONObject target = new JSONObject(request);
                JSONObject update = new JSONObject();
                update.put("screen", "write");
                update.put("modal", "write-confirm");
                update.put("result", JSONObject.NULL);
                update.put("errorMessage", "");
                update.put("writeType", target.getString("type"));
                update.put("voltage", target.getInt("voltage"));
                update.put("capacity", target.getDouble("capacity"));
                prototypeWeb.evaluateJavascript("window.bfgNativeUpdate(" + update + ")", null);
            } catch (JSONException error) {
                prototypeWriteFailure("重新读取已完成，请重新选择目标参数后再写入。");
            }
        } else if (r.operation == BfgBleClient.Operation.WRITE_PROFILE
                || r.operation == BfgBleClient.Operation.WRITE_DIS_VOLTAGE) {
            boolean confirmed = r.operation == BfgBleClient.Operation.WRITE_PROFILE
                    ? verifiedWrite : r.writeCommandSent && r.disConfigReadbackVerified;
            if (confirmed) {
                pendingPostWriteVerification = true;
                postWriteExpectedProfile = r.operation == BfgBleClient.Operation.WRITE_PROFILE
                        ? r.requestedProfile : -1;
                postWriteExpectedDisRaw = r.operation == BfgBleClient.Operation.WRITE_DIS_VOLTAGE
                        ? r.disConfigTargetRaw : -1;
                postWriteCheckAttempts = 0;
                prototypeGo("post-write-check");
                DeviceRecord verifyDevice = activeDevice;
                if (delayedPostWriteRead != null) mainHandler.removeCallbacks(delayedPostWriteRead);
                delayedPostWriteRead = () -> {
                    delayedPostWriteRead = null;
                    if (pendingPostWriteVerification && !isFinishing() && !isDestroyed()) {
                        startOperation(verifyDevice, BfgBleClient.Operation.COMPARE_READ, 0);
                    }
                };
                mainHandler.postDelayed(delayedPostWriteRead, 5000);
            } else {
                prototypeWriteFailure("写入结果没有得到确认。请先重新读取车辆数据，再决定是否尝试写入。");
            }
        } else {
            String advisory = activeDevice != null
                    && WriteAccessPolicy.isReadOnlySerial(activeDevice.effectiveSn)
                    ? "该车辆序列号以 N 开头，本工具仅提供只读数据。读取结果仅供参考，请以车辆和官方检测结果为准。"
                    : "车辆数据读取完成。电压、容量和固件识别结果仅供参考；请核对车辆实际配置，不能仅凭本页读数判断电池或车辆状态。";
            showPrototypeModal("read-advisory", advisory, "review");
        }
    }

    @Override public void onError(String s) {
        diagnosticRecorder.record("ERROR", s);
        if (pendingPrewriteOperation != null
                && activeBleOperation == BfgBleClient.Operation.COMPARE_READ) {
            pendingPrewriteOperation = null;
            pendingPrewriteDevice = null;
            setBusy(false);
            prototypeWriteFailure("写入前读取或备份失败，本次没有发送写入指令。请检查车辆连接后重试。");
            return;
        }
        if (activeBleOperation == BfgBleClient.Operation.REGISTER_SCAN) {
            setBusy(false);
            showPrototypeModal("scan-unavailable", "只读扫描中断。已收到的寄存器数据仍可导出诊断文件。", "scan-result");
            return;
        }
        boolean postWriteCheckFailed = pendingPostWriteVerification
                && activeBleOperation == BfgBleClient.Operation.COMPARE_READ;
        if (postWriteCheckFailed) pendingPostWriteVerification = false;
        boolean retryReadFailed = pendingRetryWriteRequest != null
                && activeBleOperation == BfgBleClient.Operation.COMPARE_READ;
        if (retryReadFailed) pendingRetryWriteRequest = null;
        boolean uncertainWrite = s != null && (s.contains("暂时无法完成回读确认")
                || s.contains("这不代表写入失败")
                || s.contains("仪表写入指令已发送"));
        boolean connectionFailure = isVehicleConnectionFailure(s);
        boolean disWrite = activeBleOperation == BfgBleClient.Operation.WRITE_DIS_VOLTAGE;
        boolean writeOperation = activeBleOperation == BfgBleClient.Operation.WRITE_PROFILE
                || disWrite;
        boolean writeWasSent = writeOperation && uncertainWrite;
        String advice;
        String inlineTitle;
        if (writeWasSent) {
            clearConnectionFailureStyle();
            advice = disWrite
                    ? "仪表写入已发送，但未能确认结果。请重新连接读取仪表电压配置；确认前不要重复写入。"
                    : writeReadbackAdvice(connectionFailure, true);
            status.setText(advice);
            operationState.setText("写入已发送，请重新读取确认");
            result.setText(advice);
            inlineTitle = "写入结果等待确认";
        } else if (connectionFailure) {
            showConnectionFailureStyle();
            advice = vehicleConnectionAdvice(null);
            status.setText(advice);
            operationState.setText("车辆连接失败，请检查蓝牙占用");
            result.setText(advice
                    + "\n\n排除占用后，请重新点击“连接车辆并校准”。");
            inlineTitle = "车辆连接未完成";
        } else {
            clearConnectionFailureStyle();
            advice = writeOperation
                    ? (disWrite ? "仪表配置写入未确认。请重新连接读取当前配置。"
                    : "本次临时写入没有完成，请保持车辆开启并靠近手机后重试。")
                    : "车辆数据读取未完成，请保持车辆开启并靠近手机后重新连接。";
            status.setText(advice);
            operationState.setText(writeOperation ? "写入未完成" : "读取未完成");
            result.setText(advice);
            inlineTitle = writeOperation ? "写入未完成" : "读取未完成";
        }
        if (writeProgressPanel != null && writeProgressPanel.getVisibility() == View.VISIBLE) {
            showInlineWriteResult(inlineTitle, advice, false);
        } else {
            writeButton.setText("临时写入计量模块并回读");
        }
        setBusy(false);
        diagnosticRecorder.record("UI", "Operation ended with error; lastReadProfile="
                + lastReadProfile + " lastReadCapacity=" + lastReadCapacity);
        refreshDiagnosticStatus();
        activeBleOperation = null;
        restoringBackup = false;
        if (postWriteCheckFailed) {
            showPrototypeModal("verification-incomplete",
                    "写入后的第一次回读已确认，但等待后的二次核对未完成。请保持车辆开机，重新读取当前参数；确认前不要重复写入。", "review");
        } else if (retryReadFailed) prototypeWriteFailure(
                "再次写入前重新读取车辆失败。请保持车辆开机并靠近手机，然后重试；本次没有发送新的写入指令。");
        else if (writeOperation) prototypeWriteFailure(advice);
        else if (prototypeReady && prototypeWeb != null) {
            try {
                JSONObject update = new JSONObject();
                update.put("errorMessage", advice);
                update.put("busyMessage", "连接未完成");
                update.put("screen", "connect-ready");
                prototypeWeb.evaluateJavascript("window.bfgNativeUpdate(" + update + ")", null);
            } catch (JSONException ignored) { }
        }
    }

    private void setBusy(boolean busy) {
        if (busy) clearConnectionFailureStyle();
        running = busy;
        loginButton.setEnabled(!busy);
        virtualEnvButton.setEnabled(!busy);
        if (homeImportNinebotButton != null) homeImportNinebotButton.setEnabled(!busy);
        if (importNinebotApkButton != null) importNinebotApkButton.setEnabled(!busy);
        loadButton.setEnabled(!busy);
        homeSyncButton.setEnabled(!busy);
        clearVirtualNinebotButton.setEnabled(!busy && isVirtualNinebotInstalled());
        readButton.setEnabled(!busy);
        compareButton.setEnabled(!busy && !devices.isEmpty());
        writeEntryButton.setEnabled(!busy && !devices.isEmpty());
        if (connectedCalibrateButton != null) {
            connectedCalibrateButton.setEnabled(!busy && (lastReadWriteSupported
                    || DisVoltageConfig.nominalVoltage(lastReadDisConfigRaw) > 0));
        }
        DeviceRecord d = selectedDevice();
        writeButton.setEnabled(!busy && d != null && lastReadWriteSupported
                && lastReadProfile >= 0 && getBackup(d).valid);
        writeDisVoltageButton.setEnabled(!busy && d != null
                && DisVoltageConfig.nominalVoltage(lastReadDisConfigRaw) > 0);
        restoreDisVoltageButton.setEnabled(!busy && d != null
                && DisVoltageConfig.nominalVoltage(lastReadDisConfigRaw) > 0
                && DisVoltageConfig.nominalVoltage(getDisConfigBackup(d)) > 0);
        restoreButton.setEnabled(!busy && d != null && getBackup(d).valid);
        if (writeCompleteRestoreButton != null) {
            writeCompleteRestoreButton.setEnabled(!busy && d != null && getBackup(d).valid);
        }
        operationProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (rootModeSwitch != null) rootModeSwitch.setEnabled(!busy);
        if (rootFallbackSwitch != null) rootFallbackSwitch.setEnabled(!busy);
    }


    private String formatFwVersion(int raw) {
        if (raw < 0) return "未读取到";
        int major = (raw >> 8) & 0x0F;
        int minor = (raw >> 4) & 0x0F;
        int patch = raw & 0x0F;
        return String.format(Locale.US, "%d.%d.%d", major, minor, patch);
    }

    private static boolean dashboardWriteAllowed(BfgBleClient.Result read) {
        return read != null && DashboardWritePolicy.allows(
                read.disDashboardVersionRaw, read.colorDisplayVersionRaw,
                read.centreControllerVersionRaw, read.disBfgVersionRaw);
    }

    private static int expectedCoreStatic(int profile) {
        return BfgProfileCatalog.expectedCore(profile);
    }

    private int expectedCore(int profile) {
        return expectedCoreStatic(profile);
    }

    private int profileForIndex(int index) {
        int safeIndex = Math.max(0, Math.min(0xF, index));
        return (safeIndex << 4) | selectedVoltageCode;
    }

    private String voltageName(int profile) {
        int voltageCode = profile & 0x0F;
        if (voltageCode >= 0 && voltageCode <= 2) return VOLTAGE_NAMES[voltageCode];
        return "Unknown";
    }

    private String backupKey(DeviceRecord d, String suffix) {
        return d.mac.replace(":", "_") + "_" + suffix;
    }

    private void saveDisConfigBackup(DeviceRecord device, int raw) {
        if (device == null || DisVoltageConfig.nominalVoltage(raw) < 0) return;
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        String key = backupKey(device, "dis92_original");
        if (!preferences.contains(key)) preferences.edit().putInt(key, raw).apply();
    }

    private int getDisConfigBackup(DeviceRecord device) {
        if (device == null) return -1;
        return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(backupKey(device, "dis92_original"), -1);
    }

    private int getLastDisWriteTarget(DeviceRecord device) {
        if (device == null) return -1;
        return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(backupKey(device, "dis92_last_target"), -1);
    }

    private void saveBackup(DeviceRecord d, int profile, int capacity) {
        if (d == null || profile < 0 || capacity <= 0) return;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt(backupKey(d, "profile"), profile)
                .putInt(backupKey(d, "capacity"), capacity)
                .putLong(backupKey(d, "time"), System.currentTimeMillis())
                .commit();
    }

    private boolean savePrewriteSnapshot(DeviceRecord d, BfgBleClient.Result read) {
        if (d == null || read == null) return false;
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit()
                .putLong(backupKey(d, "prewrite_time"), System.currentTimeMillis());
        if (read.beforeProfile >= 0 && read.displayBeforeCapacity() > 0) {
            editor.putInt(backupKey(d, "prewrite_profile"), read.beforeProfile)
                    .putInt(backupKey(d, "prewrite_capacity"), read.displayBeforeCapacity());
        }
        if (read.disConfigRaw >= 0) {
            editor.putInt(backupKey(d, "prewrite_dis92"), read.disConfigRaw);
        }
        if (!preferences.contains(backupKey(d, "profile"))
                && read.beforeProfile >= 0 && read.displayBeforeCapacity() > 0) {
            editor.putInt(backupKey(d, "profile"), read.beforeProfile)
                    .putInt(backupKey(d, "capacity"), read.displayBeforeCapacity())
                    .putLong(backupKey(d, "time"), System.currentTimeMillis());
        }
        if (!preferences.contains(backupKey(d, "dis92_original"))
                && read.disConfigRaw >= 0) {
            editor.putInt(backupKey(d, "dis92_original"), read.disConfigRaw);
        }
        if (!editor.commit()) return false;
        SharedPreferences saved = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (saved.getLong(backupKey(d, "prewrite_time"), 0) <= 0) return false;
        if (read.beforeProfile >= 0 && read.displayBeforeCapacity() > 0
                && (saved.getInt(backupKey(d, "prewrite_profile"), -1) != read.beforeProfile
                || saved.getInt(backupKey(d, "prewrite_capacity"), -1)
                != read.displayBeforeCapacity())) return false;
        return read.disConfigRaw < 0
                || saved.getInt(backupKey(d, "prewrite_dis92"), -1) == read.disConfigRaw;
    }

    private Backup getPrewriteBackup(DeviceRecord d) {
        Backup backup = new Backup();
        if (d == null) return backup;
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        backup.profile = preferences.getInt(backupKey(d, "prewrite_profile"), -1);
        backup.capacity = preferences.getInt(backupKey(d, "prewrite_capacity"), -1);
        backup.time = preferences.getLong(backupKey(d, "prewrite_time"), 0);
        backup.valid = backup.profile >= 0 && backup.capacity > 0 && backup.time > 0;
        return backup;
    }

    private Backup getBackup(DeviceRecord d) {
        Backup b = new Backup();
        if (d == null) return b;
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String kp = backupKey(d, "profile");
        String kc = backupKey(d, "capacity");
        if (!p.contains(kp) || !p.contains(kc)) return b;
        b.profile = p.getInt(kp, -1);
        b.capacity = p.getInt(kc, -1);
        b.time = p.getLong(backupKey(d, "time"), 0);
        b.valid = b.profile >= 0 && b.capacity > 0 && b.time > 0;
        return b;
    }

    private void refreshBackup(DeviceRecord d) {
        Backup b = getBackup(d);
        if (!b.valid) {
            backupView.setText("尚未建立备份。首次成功读取会自动保存当前容量档位和标称容量。");
            writeButton.setEnabled(false);
            restoreButton.setEnabled(false);
        } else {
            backupView.setText(String.format(Locale.US,
                    "✓ 已自动备份：%s · %s（可随时恢复）",
                    voltageName(b.profile), formatCapacityShort(b.capacity)));
            writeButton.setEnabled(!running && lastReadWriteSupported && lastReadProfile >= 0);
            restoreButton.setEnabled(!running);
        }
        updateTargetSummary();
    }

    private static final class Backup {
        boolean valid;
        int profile;
        int capacity;
        long time;
    }

    @Override protected void onDestroy() {
        if (delayedPostWriteRead != null) mainHandler.removeCallbacks(delayedPostWriteRead);
        pendingPostWriteVerification = false;
        operationGeneration++;
        Future<?> preparation = operationPreparation;
        operationPreparation = null;
        if (preparation != null) preparation.cancel(true);
        if (client != null) client.close();
        client = null;
        activeBleOperation = null;
        if (prototypeWeb != null) {
            prototypeWeb.removeJavascriptInterface("BfgNative");
            prototypeWeb.destroy();
            prototypeWeb = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && backInvokedCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backInvokedCallback);
            backInvokedCallback = null;
        }
        for (DeviceRecord d : devices) d.wipe();
        worker.shutdownNow();
        super.onDestroy();
    }

    private boolean ensureBluetoothPermissions() {
        List<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), REQ_BT);
            if (status != null) status.setText("请授予蓝牙权限。授权完成后会自动继续连接车辆。");
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_BT) return;

        boolean granted = grantResults.length > 0;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }

        DeviceRecord device = pendingPermissionDevice;
        BfgBleClient.Operation operation = pendingPermissionOperation;
        int target = pendingPermissionTarget;
        clearPendingPermissionOperation();

        if (!granted) {
            setBusy(false);
            if (status != null) {
                status.setText("未获得位置或附近设备权限，暂时无法连接车辆。");
            }
            showPrototypeModal("permission-needed", "请允许位置和附近设备权限后重试。", "connect-ready");
            return;
        }

        if (device != null && operation != null && !isFinishing()) {
            if (status != null) status.setText("蓝牙权限已授予，正在继续连接车辆…");
            startOperationConfirmed(device, operation, target);
        }
    }

    private void clearPendingPermissionOperation() {
        pendingPermissionDevice = null;
        pendingPermissionOperation = null;
        pendingPermissionTarget = 0;
    }

}
