package com.bfgtools.calibration.core;

/**
 * Resolves the dashboard's nominal voltage from the paired live energy and
 * remaining-capacity values exposed by DIS registers 0x1E and 0x44.
 *
 * The quotient is used only for identification. This class never writes a
 * dashboard register.
 */
final class DashboardVoltageResolver {
    static final class Reading {
        final int nominalVoltage;
        final double calculatedVoltage;

        Reading(int nominalVoltage, double calculatedVoltage) {
            this.nominalVoltage = nominalVoltage;
            this.calculatedVoltage = calculatedVoltage;
        }

        boolean isKnown() {
            return nominalVoltage > 0;
        }
    }

    private static final int[] SUPPORTED_VOLTAGES = {48, 60, 72};
    private static final double MAX_DISTANCE_VOLTS = 4.5;

    private DashboardVoltageResolver() {
    }

    static Reading resolve(int energyWh, int remainingCapacityMah) {
        if (energyWh <= 0 || remainingCapacityMah < 500) {
            return new Reading(-1, Double.NaN);
        }
        double calculated = energyWh * 1000.0 / remainingCapacityMah;
        if (!Double.isFinite(calculated) || calculated < 35.0 || calculated > 85.0) {
            return new Reading(-1, calculated);
        }

        int nearest = -1;
        double distance = Double.MAX_VALUE;
        for (int candidate : SUPPORTED_VOLTAGES) {
            double currentDistance = Math.abs(calculated - candidate);
            if (currentDistance < distance) {
                nearest = candidate;
                distance = currentDistance;
            }
        }
        return new Reading(distance <= MAX_DISTANCE_VOLTS ? nearest : -1, calculated);
    }
}
