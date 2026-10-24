package com.bfgtools.calibration.core;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * Ninebot BFG profile tester.
 *
 * Supported operations:
 *  - read Profile/SOC/capacity_core
 *  - write BFG register 0x00 (Profile byte), or experimentally write DIS 0x92
 *  - verify each write by reading back the same register
 *
 * It does NOT implement OTA, arbitrary RAM/NVM writes, or vehicle control commands.
 */
final class BfgBleClient {
    enum Operation { READ_ONLY, COMPARE_READ, WRITE_PROFILE, WRITE_DIS_VOLTAGE, PAIR_AND_READ, REGISTER_SCAN }

    interface Listener {
        void onStatus(String s);
        void onLog(String s);
        void onResult(Result r);
        void onError(String s);
    }

    static final class Result {
        Operation operation;
        boolean pairingConfirmed;
        int requestedProfile = -1;
        int requestedDashboardVoltage = -1;
        int beforeProfile = -1;
        int beforeSoc = -1;
        int beforeCapacityRaw = -1;
        int scannedCapacity0ERaw = -1;
        int scannedCapacity0FRaw = -1;
        int scannedDynamicCapacityRaw = -1;
        int scannedCoreCapacityRaw = -1;
        int scannedMirrorCapacityRaw = -1;
        int scannedSelectedCapacityRaw = -1;
        int scannedSelectedCapacityRegister = -1;
        String capacityScanReason = "";
        int resolvedBeforeSoc = -1;
        int resolvedBeforeCapacityRaw = -1;
        int disDashboardVersionRaw = -1;
        int colorDisplayVersionRaw = -1;
        int centreControllerVersionRaw = -1;
        int disEnergyWhRaw = -1;
        int disRemainingCapacityRaw = -1;
        int disBatteryRaw = -1;
        int disVrlaVoltageRaw = -1;
        int disBfgVersionRaw = -1;
        int disConfigRaw = -1;
        int registerScanModule = -1;
        int registerScanReplies;
        int registerScanTimeouts;
        int disConfigTargetRaw = -1;
        int disConfigAfterRaw = -1;
        boolean disConfigReadbackVerified;
        int afterProfile = -1;
        int afterCapacityRaw = -1;
        int resolvedAfterCapacityRaw = -1;
        CommunicationModeResolver.Mode communicationMode =
                CommunicationModeResolver.Mode.UNSUPPORTED;
        String communicationModeReason = "";
        boolean writeSupported;
        boolean writeCommandSent;
        boolean writeAckSeen;
        boolean profileReadbackVerified;
        boolean capacityReadbackVerified;
        boolean verificationRetried;
        String writeAckFrame = "";
        String rawBeforeProfileFrame = "";
        String rawBeforeSocFrame = "";
        String rawBeforeCapacityFrame = "";
        String rawScannedCapacity0EFrame = "";
        String rawScannedCapacity0FFrame = "";
        String rawScannedDynamicCapacityFrame = "";
        String rawScannedCoreCapacityFrame = "";
        String rawScannedMirrorCapacityFrame = "";
        String rawDisDashboardVersionFrame = "";
        String rawColorDisplayVersionFrame = "";
        String rawCentreControllerVersionFrame = "";
        String rawDisEnergyWhFrame = "";
        String rawDisRemainingCapacityFrame = "";
        String rawDisBatteryFrame = "";
        String rawDisVrlaVoltageFrame = "";
        String rawDisBfgVersionFrame = "";
        String rawDisConfigFrame = "";
        String rawDisConfigAfterFrame = "";
        String rawAfterProfileFrame = "";
        String rawAfterCapacityFrame = "";

        int displaySoc() {
            return resolvedBeforeSoc >= 0 ? resolvedBeforeSoc : beforeSoc;
        }

        int displayBeforeCapacity() {
            return resolvedBeforeCapacityRaw >= 0
                    ? resolvedBeforeCapacityRaw
                    : communicationMode == CommunicationModeResolver.Mode.UNSUPPORTED
                    ? -1 : beforeCapacityRaw;
        }

        int displayAfterCapacity() {
            if (resolvedAfterCapacityRaw >= 0) return resolvedAfterCapacityRaw;
            if (afterCapacityRaw >= 0) return afterCapacityRaw;
            return BfgProfileCatalog.expectedCore(requestedProfile);
        }

        String communicationModeLabel() {
            return CommunicationModeResolver.label(communicationMode);
        }

        DashboardVoltageResolver.Reading dashboardVoltageReading() {
            return DashboardVoltageResolver.resolve(disEnergyWhRaw, disRemainingCapacityRaw);
        }

        int dashboardNominalVoltage() {
            int configured = DisVoltageConfig.nominalVoltage(disConfigRaw);
            return configured > 0 ? configured : dashboardVoltageReading().nominalVoltage;
        }

        int meterNominalVoltage() {
            return BfgProfileCatalog.nominalVoltage(beforeProfile);
        }

        boolean voltageMismatch() {
            int dashboard = dashboardNominalVoltage();
            int meter = meterNominalVoltage();
            return dashboard > 0 && meter > 0 && dashboard != meter;
        }
    }

    private enum State {
        IDLE, SCANNING, CONNECTING, DISCOVERING, SUBSCRIBING,
        WAIT_PRECOMM, WAIT_PAIR_AUTH_RETRIES, WAIT_PAIR_CONFIRM, WAIT_AUTH,
        WAIT_BEFORE_PROFILE, WAIT_BEFORE_SOC, WAIT_BEFORE_CAPACITY,
        WAIT_DIS_DASHBOARD_VERSION, WAIT_DIS_ENERGY_WH, WAIT_DIS_REMAINING_CAPACITY,
        WAIT_DIS_BATTERY, WAIT_DIS_VRLA_VOLTAGE, WAIT_DIS_BFG_VERSION,
        WAIT_COLOR_DISPLAY_VERSION, WAIT_CENTRE_CONTROLLER_VERSION,
        WAIT_DIS_CONFIG, WAIT_DIS_WRITE_ACK, WAIT_DIS_AFTER,
        WAIT_CAPACITY_COMPAT_SCAN, WAIT_REGISTER_SCAN,
        WAIT_WRITE_ACK, WAIT_AFTER_PROFILE, WAIT_AFTER_CAPACITY,
        DONE
    }

    private static final UUID SERVICE = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID TX = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID RX = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final int MAX_PROFILE_VERIFY_ATTEMPTS = 4;
    private static final int MAX_CAPACITY_VERIFY_ATTEMPTS = 3;

    // Phone/client=0x3E. BLE board=0x04. BFG=0x10.
    private static final byte[] PRECOMM = Hex.decode("5AA5003E045B00");
    private static final byte[] READ_PROFILE = Hex.decode("5AA5013E10010001");
    private static final byte[] READ_SOC = Hex.decode("5AA5013E10010201");
    private static final byte[] READ_CAPACITY = Hex.decode("5AA5013E10011C02");

    // Official app dynamic config (device_type 88 / 14354 / 14360):
    // final dashboard battery/SOC and VRLA voltage are read from DIS, not directly from BFG.
    private static final byte[] READ_DIS_BATTERY = Hex.decode("5AA5013E0101B502");
    private static final byte[] READ_DIS_VRLA_VOLTAGE = Hex.decode("5AA5013E0101B102");
    private static final byte[] READ_DIS_BFG_VERSION = Hex.decode("5AA5013E01013D02");
    private static final byte[] READ_DIS_DASHBOARD_VERSION = Hex.decode("5AA5013E01011A02");
    private static final byte[] READ_COLOR_DISPLAY_VERSION = Hex.decode("5AA5013E0101D102");
    private static final byte[] READ_CENTRE_CONTROLLER_VERSION = Hex.decode("5AA5013E09010202");
    // Confirmed paired DIS values: energy(Wh) / remaining capacity(Ah) = nominal voltage.
    private static final byte[] READ_DIS_ENERGY_WH = Hex.decode("5AA5013E01011E02");
    private static final byte[] READ_DIS_REMAINING_CAPACITY = Hex.decode("5AA5013E01014402");
    private static final byte[] READ_DIS_CONFIG = Hex.decode("5AA5013E01019202");
    private static final int MAX_DIS_VERIFY_ATTEMPTS = 4;

    private final Context context;
    private final DeviceRecord record;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Result result = new Result();
    private final Operation operation;
    private final int targetProfile;
    private final int expectedDisConfigRaw;
    private final boolean allowUnverifiedDis;

    private BluetoothGatt gatt;
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private BluetoothGattCharacteristic tx;
    private Encryption2 crypto;
    private State state = State.IDLE;
    private int nextCounter = 2;
    private boolean finished;
    private boolean verifyScheduled;
    private boolean profileRetryPending;
    private int profileVerifyAttempts;
    private int capacityVerifyAttempts;
    private int disVerifyAttempts;
    private final int[][] capacityScanValues = new int
            [CapacityCompatibilityResolver.REGISTERS.length]
            [CapacityCompatibilityResolver.REPEATS];
    private final String[] capacityScanLastFrames = new String
            [CapacityCompatibilityResolver.REGISTERS.length];
    private int capacityScanRegisterIndex;
    private int capacityScanRepeatIndex;
    private int registerScanIndex;
    private int registerScanModule;
    private int timeoutToken;
    private byte[] password16;
    private byte[] pairingPassword32;
    private byte[] pairingChallenge16;
    private byte[] pairingSerial14;
    private String scannedName;
    private String lastNotifyHex = "";
    private long lastNotifyAt;

    BfgBleClient(Context context, DeviceRecord record, Operation operation, int targetProfile,
                 int expectedDisConfigRaw, boolean allowUnverifiedDis, Listener listener) {
        this.context = context.getApplicationContext();
        this.record = record;
        this.operation = operation;
        this.targetProfile = targetProfile & 0xFF;
        this.expectedDisConfigRaw = expectedDisConfigRaw;
        this.allowUnverifiedDis = allowUnverifiedDis;
        this.listener = listener;
        result.operation = operation;
        result.requestedProfile = operation == Operation.WRITE_PROFILE ? this.targetProfile : -1;
        result.requestedDashboardVoltage = operation == Operation.WRITE_DIS_VOLTAGE
                ? targetProfile : -1;
    }

    @SuppressLint("MissingPermission")
    void connect() {
        try {
            if ((operation == Operation.WRITE_PROFILE || operation == Operation.WRITE_DIS_VOLTAGE)
                    && WriteAccessPolicy.isReadOnlySerial(record.effectiveSn)) {
                throw new Exception("该序列号以 N 开头，仅允许读取，不发送任何写入指令。");
            }
            android.bluetooth.BluetoothAdapter adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                throw new Exception("系统蓝牙未开启");
            }
            byte[] locallyPaired = operation == Operation.PAIR_AND_READ
                    ? null : PairingCredentialStore.load(context, record.mac);
            password16 = locallyPaired == null
                    ? record.passwordCopy() : Arrays.copyOf(locallyPaired, 16);
            if (locallyPaired != null) Arrays.fill(locallyPaired, (byte) 0);
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) {
                connectDevice(adapter.getRemoteDevice(record.mac));
                return;
            }

            state = State.SCANNING;
            status("正在扫描目标车辆；无需先连接官方APP…");
            scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    if (finished || state != State.SCANNING || result == null) return;
                    BluetoothDevice device = result.getDevice();
                    if (device == null || !isExpectedDevice(result)) return;
                    scannedName = result.getScanRecord() == null ? null
                            : result.getScanRecord().getDeviceName();
                    stopScan();
                    connectDevice(device);
                }

                @Override
                public void onScanFailed(int errorCode) {
                    fail("BLE扫描失败 error=" + errorCode);
                }
            };
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            scanner.startScan(null, settings, scanCallback);
            timeout(State.SCANNING, 10000, "未扫描到目标车辆；请唤醒车辆并靠近手机后重试");
        } catch (Throwable t) {
            fail("连接初始化失败：" + clean(t));
        }
    }

    @SuppressLint("MissingPermission")
    private void connectDevice(BluetoothDevice device) {
        try {
            String btName = scannedName;
            if (btName == null || btName.isEmpty()) btName = device.getName();
            if (btName == null || btName.isEmpty()) btName = (record.name != null && !record.name.isEmpty()) ? record.name : record.sn;
            crypto = new Encryption2(btName);
            state = State.CONNECTING;
            status("已扫描到目标车辆；正在以 LE 模式连接…");
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE);
            timeout(State.CONNECTING, 12000, "连接超时");
        } catch (Throwable t) {
            fail("连接初始化失败：" + clean(t));
        }
    }

    @SuppressLint("MissingPermission")
    private boolean isExpectedDevice(ScanResult result) {
        BluetoothDevice device = result.getDevice();
        if (device != null && record.mac.equalsIgnoreCase(device.getAddress())) {
            return true;
        }

        String advertisedName = result.getScanRecord() == null
                ? null : result.getScanRecord().getDeviceName();
        if (matchesKnownName(advertisedName)) return true;
        if (device != null && matchesKnownName(device.getName())) return true;

        byte[] advertisement = result.getScanRecord() == null
                ? null : result.getScanRecord().getBytes();
        String effectiveSn = record.effectiveSn;
        return advertisement != null && effectiveSn != null && !effectiveSn.isEmpty()
                && contains(advertisement, effectiveSn.getBytes(StandardCharsets.US_ASCII));
    }

    private boolean matchesKnownName(String candidate) {
        if (candidate == null || candidate.trim().isEmpty()) return false;
        String value = candidate.trim();
        return (!record.name.isEmpty() && value.equalsIgnoreCase(record.name))
                || (!record.sn.isEmpty() && value.equalsIgnoreCase(record.sn))
                || (!record.effectiveSn.isEmpty() && value.equalsIgnoreCase(record.effectiveSn));
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        if (needle.length == 0 || needle.length > haystack.length) return false;
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        try {
            if (scanner != null && scanCallback != null) scanner.stopScan(scanCallback);
        } catch (Throwable ignored) {
        }
        scanCallback = null;
        scanner = null;
    }

    @SuppressLint("MissingPermission")
    void close() {
        finished = true;
        state = State.DONE;
        stopScan();
        try { if (gatt != null) gatt.disconnect(); } catch (Throwable ignored) {}
        try { if (gatt != null) gatt.close(); } catch (Throwable ignored) {}
        gatt = null;
        wipePassword();
        wipePairingPassword();
    }

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (finished) return;
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (handlePostWriteConnectionLoss("GATT status=" + status)) return;
                fail("GATT连接失败 status=" + status + " newState=" + newState);
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                status("BLE已连接；请求MTU…");
                boolean requested = false;
                try { requested = g.requestMtu(512); } catch (Throwable ignored) {}
                if (!requested) discover(g);
                else timeout(State.CONNECTING, 2500, null);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED && state != State.DONE) {
                if (handlePostWriteConnectionLoss("车辆断开连接")) return;
                fail("车辆断开连接");
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int statusCode) {
            if (finished) return;
            log("MTU=" + mtu + " status=" + statusCode);
            discover(g);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int statusCode) {
            if (finished) return;
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                fail("发现服务失败 status=" + statusCode);
                return;
            }
            try {
                BluetoothGattService s = g.getService(SERVICE);
                if (s == null) throw new Exception("没有找到九号 Legacy UART Service");
                tx = s.getCharacteristic(TX);
                BluetoothGattCharacteristic rx = s.getCharacteristic(RX);
                if (tx == null || rx == null) throw new Exception("缺少 0002/0003 特征");
                state = State.SUBSCRIBING;
                status("找到九号BLE通道；开启通知…");
                if (!g.setCharacteristicNotification(rx, true)) throw new Exception("setCharacteristicNotification失败");
                BluetoothGattDescriptor d = rx.getDescriptor(CCCD);
                if (d == null) throw new Exception("Notify特征没有CCCD");
                writeDescriptor(g, d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                timeout(State.SUBSCRIBING, 5000, "开启通知超时");
            } catch (Throwable t) {
                fail("初始化GATT失败：" + clean(t));
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor descriptor, int statusCode) {
            if (finished || state != State.SUBSCRIBING) return;
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                fail("开启Notify失败 status=" + statusCode);
                return;
            }
            try {
                byte[] encrypted = crypto.encryptPreComm(PRECOMM);
                if (encrypted.length != 13) throw new Exception("PRE_COMM加密长度异常");
                state = State.WAIT_PRECOMM;
                status("Notify已开启；发送 PRE_COMM…");
                sendRaw(encrypted);
                timeout(State.WAIT_PRECOMM, 5000, "PRE_COMM无回复");
            } catch (Throwable t) {
                fail("PRE_COMM失败：" + clean(t));
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic characteristic, byte[] value) {
            handleNotify(value == null ? new byte[0] : value.clone());
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic characteristic) {
            byte[] v = characteristic.getValue();
            handleNotify(v == null ? new byte[0] : v.clone());
        }
    };

    @SuppressLint("MissingPermission")
    private void discover(BluetoothGatt g) {
        if (finished || state == State.DISCOVERING || state == State.SUBSCRIBING || state.ordinal() > State.SUBSCRIBING.ordinal()) return;
        state = State.DISCOVERING;
        status("发现GATT服务…");
        if (!g.discoverServices()) fail("discoverServices返回false");
        else timeout(State.DISCOVERING, 6000, "发现服务超时");
    }

    private synchronized void handleNotify(byte[] encrypted) {
        if (finished || encrypted.length == 0) return;
        String h = Hex.encode(encrypted);
        long now = android.os.SystemClock.elapsedRealtime();
        if (h.equals(lastNotifyHex) && now - lastNotifyAt < 150) return;
        lastNotifyHex = h;
        lastNotifyAt = now;
        try {
            if (state == State.WAIT_PRECOMM) {
                byte[] plain = crypto.decryptPreComm(encrypted);
                log("PRE_COMM 回复已收到；车辆身份数据已隐藏");
                if (!isFrame(plain, 0x04, 0x3E, 0x5B)) throw new Exception("PRE_COMM回复格式不符");
                if (plain.length < 37) throw new Exception("PRE_COMM回复过短");
                int index = Hex.u8(plain[6]);
                byte[] authParam = Arrays.copyOfRange(plain, 7, 23);
                byte[] serialBytes = Arrays.copyOfRange(plain, 23, 37);
                String serial = new String(serialBytes, StandardCharsets.US_ASCII).trim();
                if (serial.isEmpty()) throw new Exception("PRE_COMM未返回车辆SN");
                if (operation == Operation.PAIR_AND_READ) {
                    if (!record.effectiveSn.isEmpty()
                            && !record.effectiveSn.equalsIgnoreCase(serial)) {
                        throw new Exception("车辆序列号与所选设备不一致，已停止配对");
                    }
                    pairingChallenge16 = authParam;
                    pairingSerial14 = serialBytes;
                    pairingPassword32 = new byte[32];
                    new SecureRandom().nextBytes(pairingPassword32);
                    crypto.establishSession(Arrays.copyOf(pairingPassword32, 16), authParam);
                    state = State.WAIT_PAIR_AUTH_RETRIES;
                    nextCounter = 2;
                    status("车辆已识别；正在请求配对…");
                    sendPairAuthRetry(0);
                    return;
                }
                if (index == 0) throw new Exception("车辆没有已保存BLE密码；请先完成车辆配对");
                crypto.establishSession(password16, authParam);
                wipePassword();
                byte[] authPlain = buildAuth(serialBytes);
                state = State.WAIT_AUTH;
                nextCounter = 2;
                status("PRE_COMM成功；AUTH…");
                sendSn(authPlain);
                timeout(State.WAIT_AUTH, 5000, "AUTH无回复");
                return;
            }

            if (state == State.WAIT_PAIR_AUTH_RETRIES) {
                // A newly generated key is not yet accepted by the vehicle. The
                // official flow sends three AUTH probes before SET_PWD.
                return;
            }

            Encryption2.Decoded d = crypto.decryptSn(encrypted);
            log("RX state=" + state + " ctr=" + d.counter + " mac="
                    + (d.macOk ? "OK" : "FAIL") + " plain=" + Hex.encode(d.plain));
            if (!d.macOk) throw new Exception("Encryption2 MAC校验失败");
            nextCounter = Math.max(nextCounter, d.counter + 1);

            switch (state) {
                case WAIT_PAIR_CONFIRM:
                    if (!isFrame(d.plain, 0x04, 0x3E, 0x5C) || d.plain.length != 7)
                        throw new Exception("车辆返回了无法识别的配对状态");
                    if (Hex.u8(d.plain[6]) == 0) {
                        status("请在车辆上按键确认配对（60 秒内）");
                        break;
                    }
                    if (Hex.u8(d.plain[6]) != 1)
                        throw new Exception("车辆拒绝了配对请求");
                    if (!PairingCredentialStore.save(context, record.mac,
                            new String(pairingSerial14, StandardCharsets.US_ASCII), pairingPassword32))
                        throw new Exception("车端已确认，但临时密钥建立失败；请重新配对");
                    result.pairingConfirmed = true;
                    crypto.establishSession(Arrays.copyOf(pairingPassword32, 16), pairingChallenge16);
                    wipePairingPassword();
                    state = State.WAIT_AUTH;
                    status("车端已确认；正在验证新配对凭据…");
                    sendSn(buildAuth(pairingSerial14));
                    timeout(State.WAIT_AUTH, 6000, "车端已确认，但新凭据认证无回复；请重新连接验证");
                    break;
                case WAIT_AUTH:
                    if (!isFrame(d.plain, 0x04, 0x3E, 0x5D) || d.plain.length < 7 || Hex.u8(d.plain[6]) != 1)
                        throw new Exception("AUTH被车辆拒绝");
                    if (operation == Operation.REGISTER_SCAN) {
                        registerScanModule = targetProfile;
                        if (!RegisterReadPlan.supports(registerScanModule))
                            throw new Exception("只读扫描只支持仪表盘和计量模块");
                        result.registerScanModule = registerScanModule;
                        registerScanIndex = 0;
                        requestRegisterScan();
                        break;
                    }
                    state = State.WAIT_BEFORE_PROFILE;
                    status("认证成功；读取写入前 Profile…");
                    sendSn(READ_PROFILE);
                    timeout(State.WAIT_BEFORE_PROFILE, 4000, "读取Profile无回复");
                    break;

                case WAIT_BEFORE_PROFILE:
                    requireReadAck(d.plain, 0x00, 1);
                    result.beforeProfile = Hex.u8(d.plain[7]);
                    result.rawBeforeProfileFrame = Hex.encode(d.plain);
                    state = State.WAIT_BEFORE_SOC;
                    status("读取写入前 SOC…");
                    sendSn(READ_SOC);
                    timeout(State.WAIT_BEFORE_SOC, 4000, "读取SOC无回复");
                    break;

                case WAIT_REGISTER_SCAN:
                    int readLength = registerScanLength(registerScanModule, registerScanIndex);
                    if (!isReadAckFrom(d.plain, registerScanModule, registerScanIndex, readLength)) {
                        log("寄存器扫描忽略非当前响应：" + Hex.encode(d.plain));
                        break;
                    }
                    result.registerScanReplies++;
                    log(String.format(Locale.US,
                            "REGISTER_READ module=0x%02X index=0x%02X length=%d data=%s",
                            registerScanModule, registerScanIndex, readLength,
                            Hex.encode(Arrays.copyOfRange(d.plain, 7, 7 + readLength))));
                    advanceRegisterScan();
                    break;

                case WAIT_BEFORE_SOC:
                    requireReadAck(d.plain, 0x02, 1);
                    result.beforeSoc = Hex.u8(d.plain[7]);
                    result.rawBeforeSocFrame = Hex.encode(d.plain);
                    state = State.WAIT_BEFORE_CAPACITY;
                    status("读取写入前 capacity_core…");
                    sendSn(READ_CAPACITY);
                    timeout(State.WAIT_BEFORE_CAPACITY, 4000, "读取容量参数无回复");
                    break;

                case WAIT_BEFORE_CAPACITY:
                    requireReadAck(d.plain, 0x1C, 2);
                    result.beforeCapacityRaw = Hex.u8(d.plain[7]) | (Hex.u8(d.plain[8]) << 8);
                    result.rawBeforeCapacityFrame = Hex.encode(d.plain);
                    log("CAPACITY_BEFORE reg=0x1C le16=" + result.beforeCapacityRaw
                            + " frameLen=" + d.plain.length);
                    beginCommunicationIdentification();
                    break;

                case WAIT_DIS_DASHBOARD_VERSION:
                    if (!isReadAckFrom(d.plain, 0x01, 0x1A, 2)) {
                        log("仪表版本阶段忽略其他通知：" + Hex.encode(d.plain));
                        break;
                    }
                    result.disDashboardVersionRaw = readLe16(d.plain, 7);
                    result.rawDisDashboardVersionFrame = Hex.encode(d.plain);
                    state = State.WAIT_DIS_ENERGY_WH;
                    status("仪表版本读取完成；识别仪表标称电压…");
                    sendSn(READ_DIS_ENERGY_WH);
                    timeout(State.WAIT_DIS_ENERGY_WH, 4000,
                            "读取仪表能量参数无回复");
                    break;

                case WAIT_DIS_ENERGY_WH:
                    if (!isReadAckFrom(d.plain, 0x01, 0x1E, 2)) {
                        log("仪表能量参数阶段忽略其他通知：" + Hex.encode(d.plain));
                        break;
                    }
                    result.disEnergyWhRaw = readLe16(d.plain, 7);
                    result.rawDisEnergyWhFrame = Hex.encode(d.plain);
                    state = State.WAIT_DIS_REMAINING_CAPACITY;
                    status("仪表能量参数读取完成；读取剩余容量…");
                    sendSn(READ_DIS_REMAINING_CAPACITY);
                    timeout(State.WAIT_DIS_REMAINING_CAPACITY, 4000,
                            "读取仪表剩余容量无回复");
                    break;

                case WAIT_DIS_REMAINING_CAPACITY:
                    if (!isReadAckFrom(d.plain, 0x01, 0x44, 2)) {
                        log("仪表剩余容量阶段忽略其他通知：" + Hex.encode(d.plain));
                        break;
                    }
                    result.disRemainingCapacityRaw = readLe16(d.plain, 7);
                    result.rawDisRemainingCapacityFrame = Hex.encode(d.plain);
                    state = State.WAIT_DIS_BATTERY;
                    status("仪表参数读取完成；读取车辆电量…");
                    sendSn(READ_DIS_BATTERY);
                    timeout(State.WAIT_DIS_BATTERY, 4000,
                            "读取DIS rBattery(0xB5)无回复");
                    break;

                case WAIT_DIS_BATTERY:
                    if (!isReadAckFrom(d.plain, 0x01, 0xB5, 2)) {
                        log("DIS电量阶段忽略其他通知：" + Hex.encode(d.plain));
                        break;
                    }
                    result.disBatteryRaw = readLe16(d.plain, 7);
                    result.rawDisBatteryFrame = Hex.encode(d.plain);
                    state = State.WAIT_DIS_VRLA_VOLTAGE;
                    status("DIS SOC完成；读取DIS铅酸电压…");
                    sendSn(READ_DIS_VRLA_VOLTAGE);
                    timeout(State.WAIT_DIS_VRLA_VOLTAGE, 4000, "读取DIS rVrlaVoltage(0xB1)无回复");
                    break;

                case WAIT_DIS_VRLA_VOLTAGE:
                    if (!isReadAckFrom(d.plain, 0x01, 0xB1, 2)) {
                        log("DIS电压阶段忽略其他通知：" + Hex.encode(d.plain));
                        break;
                    }
                    result.disVrlaVoltageRaw = readLe16(d.plain, 7);
                    result.rawDisVrlaVoltageFrame = Hex.encode(d.plain);
                    state = State.WAIT_DIS_BFG_VERSION;
                    status("车辆电压读取完成；读取计量模块版本…");
                    sendSn(READ_DIS_BFG_VERSION);
                    timeout(State.WAIT_DIS_BFG_VERSION, 4000, "读取DIS rBFGV(0x3D)无回复");
                    break;

                case WAIT_DIS_BFG_VERSION:
                    if (!isReadAckFrom(d.plain, 0x01, 0x3D, 2)) {
                        log("计量模块版本阶段忽略其他通知：" + Hex.encode(d.plain));
                        break;
                    }
                    result.disBfgVersionRaw = readLe16(d.plain, 7);
                    result.rawDisBfgVersionFrame = Hex.encode(d.plain);
                    readColorDisplayVersion();
                    break;

                case WAIT_COLOR_DISPLAY_VERSION:
                    if (!isReadAckFrom(d.plain, 0x01, 0xD1, 2)) {
                        log("彩屏仪表版本阶段忽略其他通知：" + Hex.encode(d.plain));
                        break;
                    }
                    result.colorDisplayVersionRaw = readLe16(d.plain, 7);
                    result.rawColorDisplayVersionFrame = Hex.encode(d.plain);
                    readCentreControllerVersion();
                    break;

                case WAIT_CENTRE_CONTROLLER_VERSION:
                    if (!isReadAckFrom(d.plain, 0x09, 0x02, 2)) {
                        log("中控版本阶段忽略其他通知：" + Hex.encode(d.plain));
                        break;
                    }
                    result.centreControllerVersionRaw = readLe16(d.plain, 7);
                    result.rawCentreControllerVersionFrame = Hex.encode(d.plain);
                    readDisConfig();
                    break;

                case WAIT_DIS_CONFIG:
                    if (!isReadAckFrom(d.plain, 0x01, 0x92, 2)) {
                        log("仪表配置读取阶段忽略其他通知：" + Hex.encode(d.plain));
                        break;
                    }
                    result.disConfigRaw = readLe16(d.plain, 7);
                    result.rawDisConfigFrame = Hex.encode(d.plain);
                    log(String.format(Locale.US, "DIS_CONFIG_BEFORE raw=0x%04X voltage=%d",
                            result.disConfigRaw, DisVoltageConfig.nominalVoltage(result.disConfigRaw)));
                    afterDisConfigRead();
                    break;

                case WAIT_DIS_WRITE_ACK:
                    if (isFrame(d.plain, 0x01, 0x3E, 0x05)) {
                        result.writeAckSeen = true;
                        result.writeAckFrame = Hex.encode(d.plain);
                        log("DIS_CONFIG_WRITE_ACK=" + result.writeAckFrame);
                        main.postDelayed(this::verifyAfterDisWriteSafely, 700);
                    } else {
                        log("仪表写入确认阶段忽略其他通知：" + Hex.encode(d.plain));
                    }
                    break;

                case WAIT_DIS_AFTER:
                    if (!isReadAckFrom(d.plain, 0x01, 0x92, 2)) {
                        log("仪表配置回读阶段忽略其他通知：" + Hex.encode(d.plain));
                        break;
                    }
                    result.disConfigAfterRaw = readLe16(d.plain, 7);
                    result.rawDisConfigAfterFrame = Hex.encode(d.plain);
                    if (result.disConfigAfterRaw == result.disConfigTargetRaw) {
                        result.disConfigReadbackVerified = true;
                        finish("仪表电压配置已回读为目标值；断电保持仍需实车验证。");
                    } else if (disVerifyAttempts < MAX_DIS_VERIFY_ATTEMPTS) {
                        result.verificationRetried = true;
                        main.postDelayed(this::retryDisVerificationSafely, 1000);
                    } else {
                        fail("仪表配置连续回读仍与目标不一致，请重新连接读取当前配置。");
                    }
                    break;

                case WAIT_CAPACITY_COMPAT_SCAN:
                    int expectedRegister = CapacityCompatibilityResolver.REGISTERS[
                            capacityScanRegisterIndex];
                    if (!isReadAckFrom(d.plain, 0x10, expectedRegister, 2)) {
                        log(String.format(Locale.US,
                                "兼容容量扫描0x%02X阶段忽略其他通知：%s",
                                expectedRegister, Hex.encode(d.plain)));
                        break;
                    }
                    int scannedValue = readLe16(d.plain, 7);
                    capacityScanValues[capacityScanRegisterIndex][capacityScanRepeatIndex]
                            = scannedValue;
                    capacityScanLastFrames[capacityScanRegisterIndex] = Hex.encode(d.plain);
                    log(String.format(Locale.US,
                            "CAPACITY_COMPAT_RX reg=0x%02X pass=%d value=%d",
                            expectedRegister, capacityScanRepeatIndex + 1, scannedValue));
                    advanceCapacityCompatibilityScan();
                    break;

                case WAIT_WRITE_ACK:
                    if (isFrame(d.plain, 0x10, 0x3E, 0x05)) {
                        result.writeAckSeen = true;
                        result.writeAckFrame = Hex.encode(d.plain);
                        log("WRITE_ACK=" + result.writeAckFrame);
                        scheduleVerifySoon();
                    } else {
                        log("写入等待阶段忽略非WRITE_ACK帧：" + Hex.encode(d.plain));
                    }
                    break;

                case WAIT_AFTER_PROFILE:
                    if (!isReadAckFrom(d.plain, 0x10, 0x00, 1)) {
                        log("Profile回读阶段忽略其他通知：" + Hex.encode(d.plain));
                        break;
                    }
                    result.afterProfile = Hex.u8(d.plain[7]);
                    result.rawAfterProfileFrame = Hex.encode(d.plain);
                    if (result.afterProfile != targetProfile) {
                        if (profileVerifyAttempts < MAX_PROFILE_VERIFY_ATTEMPTS) {
                            result.verificationRetried = true;
                            timeoutToken++;
                            status(String.format(
                                    "暂时仍是旧档位 0x%02X；等待车辆保存后再次回读（%d/%d）…",
                                    result.afterProfile, profileVerifyAttempts + 1,
                                    MAX_PROFILE_VERIFY_ATTEMPTS));
                            if (!profileRetryPending) {
                                profileRetryPending = true;
                                main.postDelayed(this::retryProfileVerificationSafely, 1000);
                            }
                            break;
                        }
                        throw new Exception(String.format(
                                "连续%d次回读仍为0x%02X，目标为0x%02X",
                                profileVerifyAttempts, result.afterProfile, targetProfile));
                    }
                    result.profileReadbackVerified = true;
                    if (result.communicationMode
                            == CommunicationModeResolver.Mode.CAPACITY_SCAN_COMPAT) {
                        result.resolvedAfterCapacityRaw =
                                BfgProfileCatalog.expectedCore(targetProfile);
                        finish(String.format(Locale.US,
                                "Profile 0x%02X 已通过%s回读确认。",
                                targetProfile, result.communicationModeLabel()));
                        break;
                    }
                    state = State.WAIT_AFTER_CAPACITY;
                    status("Profile回读完成；读取新的 capacity_core…");
                    requestCapacityVerification();
                    break;

                case WAIT_AFTER_CAPACITY:
                    if (!isReadAckFrom(d.plain, 0x10, 0x1C, 2)) {
                        log("容量回读阶段忽略其他通知：" + Hex.encode(d.plain));
                        break;
                    }
                    result.afterCapacityRaw = Hex.u8(d.plain[7]) | (Hex.u8(d.plain[8]) << 8);
                    result.rawAfterCapacityFrame = Hex.encode(d.plain);
                    result.capacityReadbackVerified = true;
                    result.resolvedAfterCapacityRaw = result.afterCapacityRaw;
                    log("CAPACITY_AFTER reg=0x1C le16=" + result.afterCapacityRaw
                            + " frameLen=" + d.plain.length);
                    finish(String.format("Profile 0x%02X 已写入并回读确认。", targetProfile));
                    break;

                default:
                    log("忽略当前状态通知：" + state + " " + Hex.encode(d.plain));
                    break;
            }
        } catch (Throwable t) {
            fail("协议处理失败：" + clean(t));
        }
    }

    private void beginCommunicationIdentification() throws Exception {
        state = State.WAIT_DIS_DASHBOARD_VERSION;
        status("识别车辆通信模式：读取仪表版本…");
        sendSn(READ_DIS_DASHBOARD_VERSION);
        timeout(State.WAIT_DIS_DASHBOARD_VERSION, 4000,
                "读取仪表版本(0x1A)无回复");
    }

    private int registerScanLength(int module, int index) {
        return RegisterReadPlan.length(module, index);
    }

    private void requestRegisterScan() throws Exception {
        if (registerScanIndex > 0xFF) {
            finish(String.format(Locale.US, "只读扫描完成：0x%02X 模块，%d 个地址有响应。",
                    registerScanModule, result.registerScanReplies));
            return;
        }
        state = State.WAIT_REGISTER_SCAN;
        if (registerScanIndex % 16 == 0) status(String.format(Locale.US,
                "正在只读扫描 %s：%d/256（可取消）",
                registerScanModule == 0x01 ? "仪表盘" : "计量模块", registerScanIndex));
        sendSn(RegisterReadPlan.request(registerScanModule, registerScanIndex));
        timeout(State.WAIT_REGISTER_SCAN, 650, null);
    }

    private void advanceRegisterScan() {
        timeoutToken++;
        registerScanIndex++;
        main.postDelayed(() -> {
            synchronized (BfgBleClient.this) {
                if (finished || state != State.WAIT_REGISTER_SCAN) return;
                try { requestRegisterScan(); }
                catch (Throwable error) { fail("继续只读扫描失败：" + clean(error)); }
            }
        }, 50);
    }

    private void readDisConfig() throws Exception {
        state = State.WAIT_DIS_CONFIG;
        status("正在读取仪表电压配置…");
        sendSn(READ_DIS_CONFIG);
        timeout(State.WAIT_DIS_CONFIG, 4000, "仪表电压配置没有回复");
    }

    private void readColorDisplayVersion() throws Exception {
        state = State.WAIT_COLOR_DISPLAY_VERSION;
        status("正在读取彩屏仪表版本…");
        sendSn(READ_COLOR_DISPLAY_VERSION);
        timeout(State.WAIT_COLOR_DISPLAY_VERSION, 3000, null);
    }

    private void readCentreControllerVersion() throws Exception {
        state = State.WAIT_CENTRE_CONTROLLER_VERSION;
        status("正在读取中控版本…");
        sendSn(READ_CENTRE_CONTROLLER_VERSION);
        timeout(State.WAIT_CENTRE_CONTROLLER_VERSION, 3000, null);
    }

    private void afterDisConfigRead() throws Exception {
        if (needsCapacityCompatibilityScan()) beginCapacityCompatibilityScan();
        else completeCommunicationIdentification();
    }

    private boolean needsCapacityCompatibilityScan() {
        boolean knownMeterVersion = result.disBfgVersionRaw == 0x0429
                || result.disBfgVersionRaw == 0x0286;
        int expected = BfgProfileCatalog.expectedCore(result.beforeProfile);
        boolean capacityInvalid = !CapacityCompatibilityResolver.isPlausible(
                result.beforeCapacityRaw)
                || (expected > 0 && result.beforeCapacityRaw != expected);
        return !knownMeterVersion || capacityInvalid;
    }

    private void beginCapacityCompatibilityScan() throws Exception {
        for (int[] values : capacityScanValues) Arrays.fill(values, -1);
        Arrays.fill(capacityScanLastFrames, "");
        capacityScanRegisterIndex = 0;
        capacityScanRepeatIndex = 0;
        state = State.WAIT_CAPACITY_COMPAT_SCAN;
        status("容量读取异常；正在使用兼容模式核对容量信息…");
        log(String.format(Locale.US,
                "CAPACITY_COMPAT_SCAN_BEGIN bfgVersion=%s profile=0x%02X core=%d "
                        + "registers=0E,0F,1A,1C,1E repeats=%d writes=false",
                formatVersionRaw(result.disBfgVersionRaw), result.beforeProfile & 0xFF,
                result.beforeCapacityRaw, CapacityCompatibilityResolver.REPEATS));
        requestCurrentCapacityCompatibilityRegister();
    }

    private void requestCurrentCapacityCompatibilityRegister() throws Exception {
        int register = CapacityCompatibilityResolver.REGISTERS[capacityScanRegisterIndex];
        int progress = capacityScanRegisterIndex * CapacityCompatibilityResolver.REPEATS
                + capacityScanRepeatIndex + 1;
        int total = CapacityCompatibilityResolver.REGISTERS.length
                * CapacityCompatibilityResolver.REPEATS;
        status(String.format(Locale.US,
                "兼容模式正在核对容量信息（%d/%d）…", progress, total));
        sendSn(buildReadBfgWord(register));
        timeout(State.WAIT_CAPACITY_COMPAT_SCAN, 4000,
                String.format(Locale.US, "读取兼容容量地址0x%02X无回复", register));
    }

    private void advanceCapacityCompatibilityScan() throws Exception {
        capacityScanRepeatIndex++;
        if (capacityScanRepeatIndex >= CapacityCompatibilityResolver.REPEATS) {
            capacityScanRepeatIndex = 0;
            capacityScanRegisterIndex++;
        }
        if (capacityScanRegisterIndex >= CapacityCompatibilityResolver.REGISTERS.length) {
            finishCapacityCompatibilityScan();
        } else {
            requestCurrentCapacityCompatibilityRegister();
        }
    }

    private void finishCapacityCompatibilityScan() throws Exception {
        int expected = BfgProfileCatalog.expectedCore(result.beforeProfile);
        CapacityCompatibilityResolver.Result scan = CapacityCompatibilityResolver.resolve(
                expected, capacityScanValues);
        result.scannedCapacity0ERaw = scan.valueForRegister(0x0E);
        result.scannedCapacity0FRaw = scan.valueForRegister(0x0F);
        result.scannedDynamicCapacityRaw = scan.valueForRegister(0x1A);
        result.scannedCoreCapacityRaw = scan.valueForRegister(0x1C);
        result.scannedMirrorCapacityRaw = scan.valueForRegister(0x1E);
        result.scannedSelectedCapacityRaw = scan.selectedCapacity;
        result.scannedSelectedCapacityRegister = scan.selectedRegister;
        result.capacityScanReason = scan.reason;
        result.rawScannedCapacity0EFrame = capacityScanLastFrames[0];
        result.rawScannedCapacity0FFrame = capacityScanLastFrames[1];
        result.rawScannedDynamicCapacityFrame = capacityScanLastFrames[2];
        result.rawScannedCoreCapacityFrame = capacityScanLastFrames[3];
        result.rawScannedMirrorCapacityFrame = capacityScanLastFrames[4];
        log(String.format(Locale.US,
                "CAPACITY_COMPAT_SCAN_RESULT 0E=%d 0F=%d 1A=%d 1C=%d 1E=%d "
                        + "selected=%d source=%s reason=%s",
                result.scannedCapacity0ERaw, result.scannedCapacity0FRaw,
                result.scannedDynamicCapacityRaw, result.scannedCoreCapacityRaw,
                result.scannedMirrorCapacityRaw, result.scannedSelectedCapacityRaw,
                result.scannedSelectedCapacityRegister < 0 ? "none"
                        : String.format(Locale.US, "0x%02X",
                        result.scannedSelectedCapacityRegister),
                result.capacityScanReason));
        completeCommunicationIdentification();
    }

    private void resolveCommunicationMode() {
        CommunicationModeResolver.Decision decision = CommunicationModeResolver.resolve(
                result.beforeProfile,
                result.beforeSoc,
                result.beforeCapacityRaw,
                result.disBatteryRaw,
                result.disVrlaVoltageRaw,
                result.disDashboardVersionRaw,
                result.disBfgVersionRaw,
                result.scannedSelectedCapacityRaw);
        result.communicationMode = decision.mode;
        result.communicationModeReason = decision.reason;
        result.resolvedBeforeSoc = decision.resolvedSoc;
        result.resolvedBeforeCapacityRaw = decision.resolvedCapacity;
        result.writeSupported = decision.writeSupported;
        DashboardVoltageResolver.Reading dashboardVoltage = result.dashboardVoltageReading();
        log(String.format(Locale.US,
                "COMM_MODE=%s dashboard=%s bfg=%s disConfig=0x%04X dashboardEnergyWh=%d "
                        + "dashboardRemainingCapacity=%d dashboardCalculatedVoltage=%.2f "
                        + "dashboardNominalVoltage=%d meterNominalVoltage=%d voltageMismatch=%s "
                        + "rawSoc=%d disSoc=%d rawCapacity=%d scannedDynamic=%d "
                        + "scannedMirror=%d scannedSelected=%d selectedRegister=%s "
                        + "resolvedCapacity=%d reason=%s",
                decision.mode,
                formatVersionRaw(result.disDashboardVersionRaw),
                formatVersionRaw(result.disBfgVersionRaw),
                result.disConfigRaw,
                result.disEnergyWhRaw,
                result.disRemainingCapacityRaw,
                dashboardVoltage.calculatedVoltage,
                result.dashboardNominalVoltage(),
                result.meterNominalVoltage(),
                result.voltageMismatch(),
                result.beforeSoc,
                result.disBatteryRaw,
                result.beforeCapacityRaw,
                result.scannedDynamicCapacityRaw,
                result.scannedMirrorCapacityRaw,
                result.scannedSelectedCapacityRaw,
                result.scannedSelectedCapacityRegister < 0 ? "none"
                        : String.format(Locale.US, "0x%02X",
                        result.scannedSelectedCapacityRegister),
                result.resolvedBeforeCapacityRaw,
                decision.reason));
    }

    private void completeCommunicationIdentification() throws Exception {
        resolveCommunicationMode();
        if (operation == Operation.WRITE_DIS_VOLTAGE) {
            beginDisVoltageWrite();
        } else if (!result.writeSupported && operation == Operation.WRITE_PROFILE) {
            fail("当前仪表与计量模块组合尚未通过写入验证；"
                    + "本次没有发送写入。请先导出诊断数据用于适配。");
        } else if (operation == Operation.WRITE_PROFILE) {
            beginProfileWrite();
        } else {
            finish("车辆数据读取完成；已自动识别通信模式，本次没有写入。");
        }
    }

    private void beginProfileWrite() throws Exception {
        if (WriteAccessPolicy.isReadOnlySerial(record.effectiveSn)) {
            fail("该序列号以 N 开头，仅允许读取，不发送任何写入指令。");
            return;
        }
        state = State.WAIT_WRITE_ACK;
        verifyScheduled = false;
        profileVerifyAttempts = 0;
        capacityVerifyAttempts = 0;
        byte[] write = buildWriteProfile(targetProfile);
        status(String.format(Locale.US, "%s；准备写入 Profile 0x%02X…",
                result.communicationModeLabel(), targetProfile));
        sendSn(write);
        result.writeCommandSent = true;
        // CMD 0x02 should reply with CMD 0x05. Even if the ACK is lost,
        // verify by a fresh READ instead of retransmitting the WRITE.
        main.postDelayed(this::verifyAfterWriteSafely, 2400);
    }

    private void beginDisVoltageWrite() throws Exception {
        if (WriteAccessPolicy.isReadOnlySerial(record.effectiveSn)) {
            fail("该序列号以 N 开头，仅允许读取，不发送任何写入指令。");
            return;
        }
        if (!DashboardWritePolicy.allows(result.disDashboardVersionRaw,
                result.colorDisplayVersionRaw, result.centreControllerVersionRaw,
                result.disBfgVersionRaw)) {
            fail(DashboardWritePolicy.blockedMessage());
            return;
        }
        if (result.disConfigRaw != expectedDisConfigRaw) {
            fail("仪表配置在确认后发生变化；本次未写入，请重新连接读取。");
            return;
        }
        int targetRaw;
        try {
            targetRaw = DisVoltageConfig.target(result.disConfigRaw, targetProfile);
        } catch (IllegalArgumentException error) {
            fail(error.getMessage());
            return;
        }
        if (DisVoltageConfig.requiresExtraWarning(result.disConfigRaw, targetRaw)
                && !allowUnverifiedDis) {
            fail("该仪表配置尚未验证；本次未写入。");
            return;
        }
        result.disConfigTargetRaw = targetRaw;
        if (targetRaw == result.disConfigRaw) {
            result.disConfigAfterRaw = targetRaw;
            result.disConfigReadbackVerified = true;
            finish("仪表已是所选电压档位，无需写入。");
            return;
        }
        state = State.WAIT_DIS_WRITE_ACK;
        disVerifyAttempts = 0;
        status("正在尝试写入仪表电压配置；之后会自动回读…");
        sendSn(DisVoltageConfig.writePacket(targetRaw));
        result.writeCommandSent = true;
        log(String.format(Locale.US, "DIS_CONFIG_WRITE before=0x%02X target=0x%02X",
                result.disConfigRaw, targetRaw));
        // A lost ACK must not cause a second write. Verification only repeats reads.
        main.postDelayed(this::verifyAfterDisWriteSafely, 2200);
    }

    private void verifyAfterDisWriteSafely() {
        synchronized (this) {
            if (finished || state != State.WAIT_DIS_WRITE_ACK) return;
            try {
                state = State.WAIT_DIS_AFTER;
                requestDisVerification();
            } catch (Throwable error) {
                fail("仪表写入后无法回读：" + clean(error));
            }
        }
    }

    private void retryDisVerificationSafely() {
        synchronized (this) {
            if (finished || state != State.WAIT_DIS_AFTER) return;
            try {
                requestDisVerification();
            } catch (Throwable error) {
                fail("仪表配置再次回读失败：" + clean(error));
            }
        }
    }

    private void requestDisVerification() throws Exception {
        disVerifyAttempts++;
        status(String.format(Locale.US, "仪表写入已发送，正在回读配置（%d/%d）…",
                disVerifyAttempts, MAX_DIS_VERIFY_ATTEMPTS));
        sendSn(READ_DIS_CONFIG);
        timeout(State.WAIT_DIS_AFTER, 4500, "仪表配置回读无回复");
    }

    private String formatVersionRaw(int raw) {
        if (raw < 0) return "unknown";
        int major = (raw >> 8) & 0x0F;
        int minor = (raw >> 4) & 0x0F;
        int patch = raw & 0x0F;
        return String.format(Locale.US, "%d.%d.%d", major, minor, patch);
    }

    private void scheduleVerifySoon() {
        if (verifyScheduled || finished || state != State.WAIT_WRITE_ACK) return;
        verifyScheduled = true;
        main.postDelayed(this::verifyAfterWriteSafely, 800);
    }

    private void verifyAfterWriteSafely() {
        synchronized (this) {
            if (finished || state != State.WAIT_WRITE_ACK) return;
            try {
                state = State.WAIT_AFTER_PROFILE;
                requestProfileVerification();
            } catch (Throwable t) {
                fail("写入后验证失败：" + clean(t));
            }
        }
    }

    private void retryProfileVerificationSafely() {
        synchronized (this) {
            profileRetryPending = false;
            if (finished || state != State.WAIT_AFTER_PROFILE) return;
            try {
                requestProfileVerification();
            } catch (Throwable t) {
                fail("写入后再次回读失败：" + clean(t));
            }
        }
    }

    private void requestProfileVerification() throws Exception {
        profileRetryPending = false;
        profileVerifyAttempts++;
        if (profileVerifyAttempts > 1) result.verificationRetried = true;
        status(String.format("写入已发送；正在回读 Profile（%d/%d）…",
                profileVerifyAttempts, MAX_PROFILE_VERIFY_ATTEMPTS));
        sendSn(READ_PROFILE);
        timeout(State.WAIT_AFTER_PROFILE, 4500, "写入后Profile回读无回复");
    }

    private void requestCapacityVerification() throws Exception {
        capacityVerifyAttempts++;
        if (capacityVerifyAttempts > 1) result.verificationRetried = true;
        sendSn(READ_CAPACITY);
        timeout(State.WAIT_AFTER_CAPACITY, 4500, "写入后读取容量参数无回复");
    }

    private boolean handlePostWriteConnectionLoss(String reason) {
        if (operation == Operation.WRITE_DIS_VOLTAGE && result.writeCommandSent) {
            if (result.disConfigReadbackVerified) {
                finish("仪表配置已回读确认；随后连接中断。断电保持仍需验证。");
            } else {
                fail("仪表写入指令已发送，但连接中断，结果尚未确认；请重新连接读取当前配置。");
            }
            return true;
        }
        if (operation != Operation.WRITE_PROFILE || !result.writeCommandSent) return false;
        if (result.profileReadbackVerified || result.afterProfile == targetProfile) {
            result.profileReadbackVerified = true;
            finish("Profile已回读为目标档位；随后连接中断，按已确认写入成功处理。" + reason);
        } else {
            fail("写入指令已发送，但" + reason
                    + "，暂时无法完成回读确认；这不代表写入失败，请重新连接读取当前参数。");
        }
        return true;
    }

    private byte[] buildWriteProfile(int profile) {
        // Ninebot packet: 5A A5 LEN SRC DST CMD INDEX DATA
        // LEN is data segment length only. CMD 0x02 = write with reply.
        return new byte[] {
                0x5A, (byte) 0xA5, 0x01,
                0x3E, 0x10, 0x02, 0x00, (byte) (profile & 0xFF)
        };
    }

    private byte[] buildReadBfgWord(int register) {
        return new byte[] {
                0x5A, (byte) 0xA5, 0x01,
                0x3E, 0x10, 0x01, (byte) (register & 0xFF), 0x02
        };
    }

    private byte[] buildAuth(byte[] serial14) {
        if (serial14.length != 14) throw new IllegalArgumentException("SN must be 14 bytes");
        byte[] out = new byte[7 + 14];
        byte[] h = Hex.decode("5AA50E3E045D00");
        System.arraycopy(h, 0, out, 0, h.length);
        System.arraycopy(serial14, 0, out, 7, 14);
        return out;
    }

    private void sendPairAuthRetry(int attempt) {
        main.postDelayed(() -> {
            if (finished || state != State.WAIT_PAIR_AUTH_RETRIES) return;
            try {
                if (attempt < 3) {
                    sendSn(buildAuth(pairingSerial14));
                    sendPairAuthRetry(attempt + 1);
                } else {
                    crypto.establishNameSession(pairingChallenge16);
                    byte[] plain = new byte[7 + 32];
                    System.arraycopy(Hex.decode("5AA5203E045C00"), 0, plain, 0, 7);
                    System.arraycopy(pairingPassword32, 0, plain, 7, 32);
                    int counter = nextCounter++;
                    state = State.WAIT_PAIR_CONFIRM;
                    log("TX ctr=" + counter + " SET_PWD 内容已隐藏");
                    sendRaw(crypto.encryptSn(plain, counter));
                    Arrays.fill(plain, (byte) 0);
                    status("配对请求已发出；请在车辆上按键确认");
                    timeout(State.WAIT_PAIR_CONFIRM, 60000, "配对确认超时；请唤醒车辆后重试");
                }
            } catch (Throwable t) {
                fail("配对请求未完成：" + clean(t));
            }
        }, attempt == 0 ? 0 : 510);
    }

    private void requireReadAck(byte[] p, int index, int dataLen) throws Exception {
        requireReadAckFrom(p, 0x10, index, dataLen);
    }

    private void requireReadAckFrom(byte[] p, int src, int index, int dataLen) throws Exception {
        if (!isFrame(p, src, 0x3E, 0x04))
            throw new Exception(String.format("不是0x%02X READ_ACK: %s", src, Hex.encode(p)));
        if (p.length < 7 + dataLen) throw new Exception("READ_ACK长度不足");
        if (Hex.u8(p[6]) != index)
            throw new Exception(String.format("READ_ACK寄存器不匹配，期望0x%02X 实际0x%02X", index, Hex.u8(p[6])));
    }

    private boolean isReadAckFrom(byte[] p, int src, int index, int dataLen) {
        return isFrame(p, src, 0x3E, 0x04)
                && p.length >= 7 + dataLen
                && Hex.u8(p[6]) == index;
    }

    private int readLe16(byte[] p, int offset) {
        return Hex.u8(p[offset]) | (Hex.u8(p[offset + 1]) << 8);
    }

    private boolean isFrame(byte[] p, int src, int dst, int cmd) {
        return p != null && p.length >= 7 && Hex.u8(p[0]) == 0x5A && Hex.u8(p[1]) == 0xA5 &&
                Hex.u8(p[3]) == src && Hex.u8(p[4]) == dst && Hex.u8(p[5]) == cmd;
    }

    private void sendSn(byte[] plain) throws Exception {
        int c = nextCounter++;
        byte[] encrypted = crypto.encryptSn(plain, c);
        log(isFrame(plain, 0x3E, 0x04, 0x5D)
                ? "TX ctr=" + c + " AUTH 身份数据已隐藏"
                : "TX ctr=" + c + " plain=" + Hex.encode(plain));
        sendRaw(encrypted);
    }

    @SuppressLint("MissingPermission")
    private void sendRaw(byte[] data) throws Exception {
        if (gatt == null || tx == null) throw new Exception("GATT TX未就绪");
        int props = tx.getProperties();
        int writeType = ((props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)
                ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        boolean ok;
        if (Build.VERSION.SDK_INT >= 33) {
            int rc = gatt.writeCharacteristic(tx, data, writeType);
            ok = (rc == BluetoothGatt.GATT_SUCCESS);
        } else {
            //noinspection deprecation
            tx.setWriteType(writeType);
            //noinspection deprecation
            tx.setValue(data);
            //noinspection deprecation
            ok = gatt.writeCharacteristic(tx);
        }
        if (!ok) throw new Exception("writeCharacteristic失败");
    }

    @SuppressLint("MissingPermission")
    private void writeDescriptor(BluetoothGatt g, BluetoothGattDescriptor d, byte[] value) throws Exception {
        boolean ok;
        if (Build.VERSION.SDK_INT >= 33) {
            int rc = g.writeDescriptor(d, value);
            ok = (rc == BluetoothGatt.GATT_SUCCESS);
        } else {
            //noinspection deprecation
            d.setValue(value);
            //noinspection deprecation
            ok = g.writeDescriptor(d);
        }
        if (!ok) throw new Exception("writeDescriptor失败");
    }

    private void timeout(State expected, long ms, String error) {
        final int token = ++timeoutToken;
        main.postDelayed(() -> {
            synchronized (this) {
                if (finished || state != expected || token != timeoutToken) return;
                if (expected == State.WAIT_REGISTER_SCAN) {
                    result.registerScanTimeouts++;
                    advanceRegisterScan();
                    return;
                }
                if (expected == State.WAIT_DIS_DASHBOARD_VERSION) {
                    try {
                        log("仪表版本地址无回复；继续通过数据一致性识别通信模式。");
                        state = State.WAIT_DIS_ENERGY_WH;
                        sendSn(READ_DIS_ENERGY_WH);
                        timeout(State.WAIT_DIS_ENERGY_WH, 4000,
                                "读取仪表能量参数无回复");
                    } catch (Throwable t) {
                        fail("继续读取仪表能量参数失败：" + clean(t));
                    }
                    return;
                }
                if (expected == State.WAIT_DIS_ENERGY_WH) {
                    try {
                        log("仪表能量参数无回复；继续读取剩余容量。");
                        state = State.WAIT_DIS_REMAINING_CAPACITY;
                        sendSn(READ_DIS_REMAINING_CAPACITY);
                        timeout(State.WAIT_DIS_REMAINING_CAPACITY, 4000,
                                "读取仪表剩余容量无回复");
                    } catch (Throwable t) {
                        fail("继续读取仪表剩余容量失败：" + clean(t));
                    }
                    return;
                }
                if (expected == State.WAIT_DIS_REMAINING_CAPACITY) {
                    try {
                        log("仪表剩余容量无回复；继续读取车辆电量。");
                        state = State.WAIT_DIS_BATTERY;
                        sendSn(READ_DIS_BATTERY);
                        timeout(State.WAIT_DIS_BATTERY, 4000,
                                "读取DIS rBattery(0xB5)无回复");
                    } catch (Throwable t) {
                        fail("继续读取车辆电量失败：" + clean(t));
                    }
                    return;
                }
                if (expected == State.WAIT_DIS_BATTERY) {
                    try {
                        log("DIS电量地址无回复；继续读取电压用于通信识别。");
                        state = State.WAIT_DIS_VRLA_VOLTAGE;
                        sendSn(READ_DIS_VRLA_VOLTAGE);
                        timeout(State.WAIT_DIS_VRLA_VOLTAGE, 4000,
                                "读取DIS rVrlaVoltage(0xB1)无回复");
                    } catch (Throwable t) {
                        fail("继续读取DIS电压失败：" + clean(t));
                    }
                    return;
                }
                if (expected == State.WAIT_DIS_VRLA_VOLTAGE) {
                    try {
                        log("DIS电压地址无回复；继续读取计量模块版本。");
                        state = State.WAIT_DIS_BFG_VERSION;
                        sendSn(READ_DIS_BFG_VERSION);
                        timeout(State.WAIT_DIS_BFG_VERSION, 4000,
                                "读取DIS rBFGV(0x3D)无回复");
                    } catch (Throwable t) {
                        fail("继续读取计量模块版本失败：" + clean(t));
                    }
                    return;
                }
                if (expected == State.WAIT_DIS_BFG_VERSION) {
                    try {
                        log("计量模块版本地址无回复；继续读取彩屏仪表版本。");
                        readColorDisplayVersion();
                    } catch (Throwable t) {
                        fail("通信模式识别失败：" + clean(t));
                    }
                    return;
                }
                if (expected == State.WAIT_COLOR_DISPLAY_VERSION) {
                    try {
                        log("彩屏仪表版本无回复；仪表盘写入将被禁止。");
                        readCentreControllerVersion();
                    } catch (Throwable t) {
                        fail("继续读取中控版本失败：" + clean(t));
                    }
                    return;
                }
                if (expected == State.WAIT_CENTRE_CONTROLLER_VERSION) {
                    try {
                        log("中控版本无回复；仪表盘写入将被禁止。");
                        readDisConfig();
                    } catch (Throwable t) {
                        fail("继续读取仪表配置失败：" + clean(t));
                    }
                    return;
                }
                if (expected == State.WAIT_DIS_CONFIG) {
                    if (operation == Operation.WRITE_DIS_VOLTAGE) {
                        fail("仪表配置未读取到，本次没有发送写入。");
                    } else {
                        try {
                            log("仪表配置无回复；使用原有读数继续。");
                            afterDisConfigRead();
                        } catch (Throwable t) {
                            fail("继续读取车辆数据失败：" + clean(t));
                        }
                    }
                    return;
                }
                if (expected == State.WAIT_DIS_AFTER && result.writeCommandSent) {
                    if (disVerifyAttempts < MAX_DIS_VERIFY_ATTEMPTS) {
                        result.verificationRetried = true;
                        retryDisVerificationSafely();
                    } else {
                        fail("仪表写入指令已发送，但多次回读无回复；请重新连接读取当前配置。");
                    }
                    return;
                }
                if (expected == State.WAIT_CAPACITY_COMPAT_SCAN) {
                    try {
                        int register = CapacityCompatibilityResolver.REGISTERS[
                                capacityScanRegisterIndex];
                        log(String.format(Locale.US,
                                "兼容容量地址0x%02X第%d次无回复；继续扫描。",
                                register, capacityScanRepeatIndex + 1));
                        capacityScanValues[capacityScanRegisterIndex]
                                [capacityScanRepeatIndex] = -1;
                        advanceCapacityCompatibilityScan();
                    } catch (Throwable t) {
                        fail("继续兼容容量扫描失败：" + clean(t));
                    }
                    return;
                }
                if (expected == State.WAIT_AFTER_PROFILE
                        && result.writeCommandSent
                        && profileVerifyAttempts < MAX_PROFILE_VERIFY_ATTEMPTS) {
                    result.verificationRetried = true;
                    try {
                        requestProfileVerification();
                    } catch (Throwable t) {
                        fail("写入后再次回读失败：" + clean(t));
                    }
                    return;
                }
                if (expected == State.WAIT_AFTER_CAPACITY && result.profileReadbackVerified) {
                    if (capacityVerifyAttempts < MAX_CAPACITY_VERIFY_ATTEMPTS) {
                        result.verificationRetried = true;
                        try {
                            status(String.format("Profile已确认；再次读取容量参数（%d/%d）…",
                                    capacityVerifyAttempts + 1, MAX_CAPACITY_VERIFY_ATTEMPTS));
                            requestCapacityVerification();
                        } catch (Throwable t) {
                            finish("Profile已回读确认；容量参数再次读取失败，按Profile确认写入成功。");
                        }
                    } else {
                        finish("Profile已回读确认；容量参数多次无响应，按Profile确认写入成功。");
                    }
                    return;
                }
                if (expected == State.WAIT_AFTER_PROFILE && result.writeCommandSent) {
                    fail("写入指令已发送，但连续" + profileVerifyAttempts
                            + "次未收到Profile回读，暂时无法完成回读确认；"
                            + "这不代表写入失败，请重新连接读取当前参数。");
                    return;
                }
                if (error != null) fail(error);
                else if (expected == State.CONNECTING && gatt != null) discover(gatt);
            }
        }, ms);
    }

    private void finish(String s) {
        if (finished) return;
        state = State.DONE;
        finished = true;
        status(s);
        main.post(() -> listener.onResult(result));
        disconnectOnly();
    }

    @SuppressLint("MissingPermission")
    private void disconnectOnly() {
        stopScan();
        try { if (gatt != null) gatt.disconnect(); } catch (Throwable ignored) {}
        try { if (gatt != null) gatt.close(); } catch (Throwable ignored) {}
        gatt = null;
        wipePassword();
        wipePairingPassword();
    }

    private void fail(String s) {
        if (finished) return;
        finished = true;
        state = State.DONE;
        wipePassword();
        wipePairingPassword();
        disconnectOnly();
        main.post(() -> listener.onError(s));
    }

    private void status(String s) { main.post(() -> listener.onStatus(s)); }
    private void log(String s) { main.post(() -> listener.onLog(s)); }

    private String clean(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.isEmpty()) ? t.getClass().getSimpleName() : m;
    }

    private void wipePassword() {
        if (password16 != null) Arrays.fill(password16, (byte) 0);
        password16 = null;
    }

    private void wipePairingPassword() {
        if (pairingPassword32 != null) Arrays.fill(pairingPassword32, (byte) 0);
        pairingPassword32 = null;
    }
}
