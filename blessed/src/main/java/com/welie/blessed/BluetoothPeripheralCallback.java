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

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("SpellCheckingInspection")
public abstract class BluetoothPeripheralCallback {

    /**
     * Callback invoked when the list of remote services, characteristics and descriptors
     * for the remote device have been updated, ie new services have been discovered.
     *
     */
    public void onServicesDiscovered(@NotNull final BluetoothPeripheral peripheral) {}

    /**
     * Callback invoked when the notification state of a characteristic has changed.
     *
     * <p>Use {@link BluetoothPeripheral#isNotifying} to get the current notification state of the characteristic
     *
     * @param peripheral the peripheral
     * @param characteristic the characteristic for which the notification state changed
     * @param status GATT status code
     */
    public void onNotificationStateUpdate(@NotNull final BluetoothPeripheral peripheral, @NotNull final BluetoothGattCharacteristic characteristic, @NotNull final GattStatus status) {}

    /**
     * Callback invoked as the result of a characteristic read operation or notification
     *
     * <p>The value byte array is a threadsafe copy of the byte array contained in the characteristic.
     *
     * @param peripheral the peripheral
     * @param value the new value received
     * @param characteristic the characteristic for which the new value was received
     * @param status GATT status code
     */
    public void onCharacteristicUpdate(@NotNull final BluetoothPeripheral peripheral, @NotNull byte[] value, @NotNull final BluetoothGattCharacteristic characteristic, @NotNull final GattStatus status) {}

    /**
     * Callback indicating the result of a characteristic write operation.
     *
     * <p>The value byte array is a threadsafe copy of the byte array contained in the characteristic.
     *
     * @param peripheral the peripheral
     * @param value the value to be written
     * @param characteristic the characteristic written to
     * @param status GATT status code
     */
    public void onCharacteristicWrite(@NotNull final BluetoothPeripheral peripheral, @NotNull byte[] value, @NotNull final BluetoothGattCharacteristic characteristic, @NotNull final GattStatus status) {}

    /**
     * Callback invoked as the result of a descriptor read operation
     *
     * @param peripheral the peripheral
     * @param value the read value
     * @param descriptor the descriptor that was read
     * @param status GATT status code
     */
    public void onDescriptorRead(@NotNull final BluetoothPeripheral peripheral, @NotNull byte[] value, @NotNull final BluetoothGattDescriptor descriptor, @NotNull final GattStatus status) {}

    /**
     * Callback invoked as the result of a descriptor write operation.
     * This callback is not called for the Client Characteristic Configuration descriptor. Instead the {@link BluetoothPeripheralCallback#onNotificationStateUpdate(BluetoothPeripheral, BluetoothGattCharacteristic, GattStatus)} will be called
     *
     * @param peripheral the peripheral
     * @param value the value that to be written
     * @param descriptor the descriptor written to
     * @param status the GATT status code
     */
    public void onDescriptorWrite(@NotNull final BluetoothPeripheral peripheral, @NotNull byte[] value, @NotNull final BluetoothGattDescriptor descriptor, @NotNull final GattStatus status) {}

    /**
     * Callback invoked when a bonding process is started
     *
     * @param peripheral the peripheral
     */
    public void onBondingStarted(@NotNull final BluetoothPeripheral peripheral) {}

    /**
     * Callback invoked when a bonding process has succeeded
     *
     * @param peripheral the peripheral
     */
    public void onBondingSucceeded(@NotNull final BluetoothPeripheral peripheral) {}

    /**
     * Callback invoked when a bonding process has failed
     *
     * @param peripheral the peripheral
     */
    public void onBondingFailed(@NotNull final BluetoothPeripheral peripheral) {}

    /**
     * Callback invoked when a bond has been lost and the peripheral is not bonded anymore.
     *
     * @param peripheral the peripheral
     */
    public void onBondLost(@NotNull final BluetoothPeripheral peripheral) {}

    /**
     * Callback invoked as the result of a read RSSI operation
     *
     * @param peripheral the peripheral
     * @param rssi the RSSI value
     * @param status GATT status code
     */
    public void onReadRemoteRssi(@NotNull final BluetoothPeripheral peripheral, int rssi, @NotNull GattStatus status) {}

    /**
     * Callback invoked as the result of a MTU request operation
     * @param peripheral the peripheral
     * @param mtu the new MTU
     * @param status GATT status code
     */
    public void onMtuChanged(@NotNull final BluetoothPeripheral peripheral, int mtu, @NotNull GattStatus status) {}
}
