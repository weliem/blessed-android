package com.welie.blessed;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class BluetoothCentral {

    @NotNull
    private final String address;

    @Nullable
    private final String name;

    private int currentMtu = 23;

    public BluetoothCentral(@NotNull String address, @Nullable String name) {
        this.address = Objects.requireNonNull(address, "Address is null");
        this.name = name;
    }

    public @NotNull String getAddress() {
        return address;
    }

    public @Nullable String getName() {
        return name;
    }

    public void setCurrentMtu(int currentMtu) {
        this.currentMtu = currentMtu;
    }

    public int getCurrentMtu() {
        return currentMtu;
    }

    /**
     * Get maximum length of byte array that can be written depending on WriteType
     *
     * <p>
     * This value is derived from the current negotiated MTU or the maximum characteristic length (512)
     */
    public int getMaximumWriteValueLength(WriteType writeType) {
        switch (writeType) {
            case WITH_RESPONSE:
                return 512;
            case SIGNED:
                return currentMtu - 15;
            default:
                return currentMtu - 3;
        }
    }
}
