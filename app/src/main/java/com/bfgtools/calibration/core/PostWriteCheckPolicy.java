package com.bfgtools.calibration.core;

/** Decides whether a delayed vehicle update needs another read, never another write. */
final class PostWriteCheckPolicy {
    static final int MAX_READS = 3;

    private PostWriteCheckPolicy() { }

    static boolean targetMatches(int actualProfile, int expectedProfile,
                                 int actualDisRaw, int expectedDisRaw) {
        if (expectedProfile >= 0) return actualProfile == expectedProfile;
        if (expectedDisRaw >= 0) return actualDisRaw == expectedDisRaw;
        return false;
    }

    static boolean capacityPending(int soc, int remainingCapacityRaw) {
        return soc > 0 && remainingCapacityRaw == 0;
    }

    static boolean shouldRetry(int readsCompleted, boolean targetMatches,
                               boolean capacityPending) {
        return readsCompleted < MAX_READS && (!targetMatches || capacityPending);
    }
}
