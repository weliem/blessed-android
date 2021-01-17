package com.welie.blessed;

public enum PhyOptions {
    /**
     * No preferred option. Use this value in combination with PHY_LE_1M and PHY_LE_2M
     */
    NO_PREFERRED(0),

    /**
     * Prefer 2x range option with throughput of +/- 500 Kbps
     */
    S2(1),

    /**
     * Prefer 4x range option with throughput of +/- 125 Kbps
     */
    S8(2);

    PhyOptions(final int value) {
        this.value = value;
    }

    private final int value;

    int getValue() {
        return value;
    }
}
