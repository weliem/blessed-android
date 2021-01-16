package com.welie.blessed;

import org.jetbrains.annotations.NotNull;

public enum PhyType {
    PHY_LE_1M(1,1),
    PHY_LE_2M (2,2),
    PHY_LE_CODED(3, 4),
    UNKNOWN_PHY_TYPE(-1,-1);

    PhyType(final int value, final int mask) {
        this.value = value;
        this.mask = mask;
    }
    private final int value;
    private final int mask;

    int getValue() {
        return value;
    }
    int getMask() {
        return mask;
    }

    @NotNull
    public static PhyType fromValue(int value) {
        for (PhyType type : values()) {
            if (type.getValue() == value)
                return type;
        }
        return UNKNOWN_PHY_TYPE;
    }
}
