package com.bfgtools.calibration.core;

import java.util.Arrays;

/** Selects a trustworthy full-capacity value from repeated, read-only BFG probes. */
final class CapacityCompatibilityResolver {
    static final int[] REGISTERS = {0x0E, 0x0F, 0x1A, 0x1C, 0x1E};
    static final int REPEATS = 3;

    static final class Result {
        final int[] stableValues;
        final int selectedCapacity;
        final int selectedRegister;
        final String reason;

        Result(int[] stableValues, int selectedCapacity, int selectedRegister, String reason) {
            this.stableValues = stableValues;
            this.selectedCapacity = selectedCapacity;
            this.selectedRegister = selectedRegister;
            this.reason = reason;
        }

        int valueForRegister(int register) {
            for (int i = 0; i < REGISTERS.length; i++) {
                if (REGISTERS[i] == register) return stableValues[i];
            }
            return -1;
        }
    }

    private CapacityCompatibilityResolver() {
    }

    static Result resolve(int expectedCapacity, int[][] readings) {
        int[] stable = new int[REGISTERS.length];
        Arrays.fill(stable, -1);
        for (int i = 0; i < stable.length; i++) {
            if (readings != null && i < readings.length) {
                stable[i] = stableValue(readings[i]);
            }
        }

        int cap0e = stable[0];
        int cap0f = stable[1];
        int cap1a = stable[2];
        int cap1c = stable[3];
        int cap1e = stable[4];

        // The abnormal 0Ah vehicle returned the same 26000mAh word from 0x0E/0x0F.
        // Agreement between those two independent reads is the strongest evidence.
        if (isPlausible(cap0e) && cap0e == cap0f) {
            return selected(stable, cap0e, 0x0E, "0x0E与0x0F稳定一致");
        }

        // A stable candidate matching the current Profile table is safe to prefer.
        if (isPlausible(expectedCapacity)) {
            if (cap0e == expectedCapacity) {
                return selected(stable, cap0e, 0x0E, "0x0E与Profile容量一致");
            }
            if (cap0f == expectedCapacity) {
                return selected(stable, cap0f, 0x0F, "0x0F与Profile容量一致");
            }
            if (cap1c == expectedCapacity) {
                return selected(stable, cap1c, 0x1C, "0x1C与Profile容量一致");
            }
        }

        // Cross-register agreement is stronger than any single plausible number.
        if (isPlausible(cap1c) && (cap1c == cap0e || cap1c == cap0f)) {
            return selected(stable, cap1c, 0x1C, "常规容量与兼容候选一致");
        }
        if (isPlausible(cap1a) && cap1a == cap1e) {
            return selected(stable, cap1a, 0x1A, "0x1A与0x1E稳定一致");
        }

        // 0x0E/0x0F are the confirmed fallback locations. A value must be stable in
        // at least two of three reads before reaching this point.
        if (isPlausible(cap0e)) {
            return selected(stable, cap0e, 0x0E, "0x0E连续读取稳定");
        }
        if (isPlausible(cap0f)) {
            return selected(stable, cap0f, 0x0F, "0x0F连续读取稳定");
        }
        if (isPlausible(cap1c)) {
            return selected(stable, cap1c, 0x1C, "0x1C连续读取稳定");
        }

        return new Result(stable, -1, -1, "未找到稳定且合理的容量值");
    }

    private static Result selected(int[] stable, int capacity, int register, String reason) {
        return new Result(stable, capacity, register, reason);
    }

    private static int stableValue(int[] values) {
        if (values == null || values.length == 0) return -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] < 0) continue;
            int matches = 0;
            for (int value : values) {
                if (value == values[i]) matches++;
            }
            if (matches >= 2) return values[i];
        }
        return -1;
    }

    static boolean isPlausible(int value) {
        return value >= 5000 && value <= 100000;
    }
}

