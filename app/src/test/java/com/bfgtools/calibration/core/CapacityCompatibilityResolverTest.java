package com.bfgtools.calibration.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CapacityCompatibilityResolverTest {
    @Test
    public void abnormalZeroCoreUsesMatching0eAnd0fCapacity() {
        CapacityCompatibilityResolver.Result result = CapacityCompatibilityResolver.resolve(
                26000,
                readings(
                        row(26000, 26000, 26000),
                        row(26000, 26000, 26000),
                        row(2570, 2570, 2570),
                        row(0, 0, 0),
                        row(2, 2, 2)));

        assertEquals(26000, result.selectedCapacity);
        assertEquals(0x0E, result.selectedRegister);
    }

    @Test
    public void unstableNoiseIsRejected() {
        CapacityCompatibilityResolver.Result result = CapacityCompatibilityResolver.resolve(
                26000,
                readings(
                        row(18000, 22000, 26000),
                        row(21000, 23000, 25000),
                        row(2000, 2100, 2200),
                        row(0, 0, 0),
                        row(2, 2, 2)));

        assertEquals(-1, result.selectedCapacity);
        assertEquals(-1, result.selectedRegister);
    }

    @Test
    public void stable0eMatchingProfileCanStandAlone() {
        CapacityCompatibilityResolver.Result result = CapacityCompatibilityResolver.resolve(
                18000,
                readings(
                        row(18000, 18000, 17999),
                        row(-1, -1, -1),
                        row(15000, 14000, 13000),
                        row(0, 0, 0),
                        row(0, 0, 0)));

        assertEquals(18000, result.selectedCapacity);
        assertEquals(0x0E, result.selectedRegister);
    }

    @Test
    public void changingRemainingCapacityIsNotUsedByItself() {
        CapacityCompatibilityResolver.Result result = CapacityCompatibilityResolver.resolve(
                26000,
                readings(
                        row(0, 0, 0),
                        row(0, 0, 0),
                        row(20000, 19950, 19900),
                        row(0, 0, 0),
                        row(2, 2, 2)));

        assertEquals(-1, result.selectedCapacity);
    }

    private static int[][] readings(int[]... rows) {
        return rows;
    }

    private static int[] row(int a, int b, int c) {
        return new int[]{a, b, c};
    }
}
