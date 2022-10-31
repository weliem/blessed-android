/*
 *   Copyright (c) 2021 Martijn van Welie
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *   SOFTWARE.
 *
 */

package com.welie.blessed;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * This class represent a remote Central
 */
@SuppressLint("MissingPermission")
public class BluetoothCentral {

    @NotNull protected final BluetoothDevice device;

    private int currentMtu = 23;

    BluetoothCentral(@NotNull BluetoothDevice device) {
        this.device = device;
    }

    public @NotNull String getAddress() {
        return device.getAddress();
    }

    public @NotNull String getName() {
        return device.getName() == null ? "" : device.getName();
    }

    public BondState getBondState() {
        return BondState.fromValue(device.getBondState());
    }

    protected void setCurrentMtu(final int currentMtu) {
        this.currentMtu = currentMtu;
    }

    public int getCurrentMtu() {
        return currentMtu;
    }

    public boolean createBond() { return device.createBond(); }

    public boolean setPairingConfirmation(Boolean confirm) { return device.setPairingConfirmation(confirm); }

    /**
     * Get maximum length of byte array that can be written depending on WriteType
     *
     * This value is derived from the current negotiated MTU or the maximum characteristic length (512)
     */
    public int getMaximumWriteValueLength(@NotNull final WriteType writeType) {
        Objects.requireNonNull(writeType, "writetype is null");

        switch (writeType) {
            case WITH_RESPONSE:
                return 512;
            case SIGNED:
                return currentMtu - 15;
            default:
                return currentMtu - 3;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BluetoothCentral that = (BluetoothCentral) o;
        return device.getAddress().equals(that.device.getAddress());
    }

    @Override
    public int hashCode() {
        return Objects.hash(device);
    }
}
