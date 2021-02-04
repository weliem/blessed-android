package com.welie.blessed;

import org.jetbrains.annotations.NotNull;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.BOND_NONE;

public enum BondState {
    NONE(BOND_NONE),
    BONDING(BOND_BONDING),
    BONDED(BOND_BONDED);

    BondState(int value) {
        this.value = value;
    }

    private final int value;

    @NotNull
    public static BondState fromValue(int value) {
        for (BondState type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return NONE;
    }
}
