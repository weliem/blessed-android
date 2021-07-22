package com.welie.blessed;

import android.bluetooth.BluetoothDevice;

import org.jetbrains.annotations.NotNull;


/**
 * This enum describes all possible values for transport.
 */
public enum Transport {
    /**
     * No preference of physical transport for GATT connections to remote dual-mode devices
     */
    AUTO(BluetoothDevice.TRANSPORT_AUTO),

    /**
     * Prefer BR/EDR transport for GATT connections to remote dual-mode devices is necessary.
     */
    BR_EDR(BluetoothDevice.TRANSPORT_BREDR),

    /**
     * Prefer LE transport for GATT connections to remote dual-mode devices
     */
    LE(BluetoothDevice.TRANSPORT_LE);

    public final int value;

    Transport(int value) {
        this.value = value;
    }

    @NotNull
    public static Transport fromValue(final int value) {
        for (Transport transport : values()) {
            if (transport.value == value)
                return transport;
        }
        return AUTO;
    }
}
