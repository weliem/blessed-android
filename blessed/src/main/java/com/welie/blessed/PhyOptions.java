package com.welie.blessed;

public enum PhyOptions {
    PHY_OPTION_NO_PREFERRED(0),
    PHY_OPTION_S2(1),
    PHY_OPTION_S8(2);

    PhyOptions(final int value) {
        this.value = value;
    }

    private final int value;

    int getValue() {
        return value;
    }
}
