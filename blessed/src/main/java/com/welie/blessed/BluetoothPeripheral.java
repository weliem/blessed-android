/*
 *   Copyright (c) 2019 Martijn van Welie
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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import timber.log.Timber;

import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_SIGNED;

/**
 * Represents a remote Bluetooth peripheral and replaces BluetoothDevice and BluetoothGatt
 *
 * <p>A {@link BluetoothPeripheral} lets you create a connection with the peripheral or query information about it.
 * This class is a wrapper around the {@link BluetoothDevice} and takes care of operation queueing, some Android bugs, and provides several convenience functions.
 */
@SuppressWarnings({"SpellCheckingInspection", "unused", "UnusedReturnValue"})
public class BluetoothPeripheral {

    // CCC descriptor UUID
    private static final String CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    // Gatt status values taken from Android source code:
    // https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/android-4.4.4_r2.0.1/stack/include/gatt_api.h

    /**
     * A GATT operation completed successfully
     */
    @SuppressWarnings("WeakerAccess")
    public static final int GATT_SUCCESS = 0;

    /**
     * The connection was terminated because of a L2C failure
     */
    @SuppressWarnings("WeakerAccess")
    public static final int GATT_CONN_L2C_FAILURE = 1;

    /**
     * The connection has timed out
     */
    @SuppressWarnings("WeakerAccess")
    public static final int GATT_CONN_TIMEOUT = 8;

    /**
     * GATT read operation is not permitted
     */
    @SuppressWarnings("WeakerAccess")
    public static final int GATT_READ_NOT_PERMITTED = 2;

    /**
     * GATT write operation is not permitted
     */
    @SuppressWarnings("WeakerAccess")
    public static final int GATT_WRITE_NOT_PERMITTED = 3;

    /**
     * Insufficient authentication for a given operation
     */
    @SuppressWarnings("WeakerAccess")
    public static final int GATT_INSUFFICIENT_AUTHENTICATION = 5;

    /**
     * The given request is not supported
     */
    @SuppressWarnings("WeakerAccess")
    public static final int GATT_REQUEST_NOT_SUPPORTED = 6;

    /**
     * Insufficient encryption for a given operation
     */
    @SuppressWarnings("WeakerAccess")
    public static final int GATT_INSUFFICIENT_ENCRYPTION = 15;

    /**
     * The connection was terminated by the peripheral
     */
    @SuppressWarnings("WeakerAccess")
    public static final int GATT_CONN_TERMINATE_PEER_USER = 19;

    /**
     * The connection was terminated by the local host
     */
    @SuppressWarnings("WeakerAccess")
    public static final int GATT_CONN_TERMINATE_LOCAL_HOST = 22;

    /**
     * The connection lost because of LMP timeout
     */
    @SuppressWarnings("WeakerAccess")
    public static final int GATT_CONN_LMP_TIMEOUT = 34;

    /**
     * The connection was terminated due to MIC failure
     */
    @SuppressWarnings("WeakerAccess")
    public static final int BLE_HCI_CONN_TERMINATED_DUE_TO_MIC_FAILURE = 61;

    /**
     * The connection cannot be established
     */
    @SuppressWarnings("WeakerAccess")
    public static final int GATT_CONN_FAIL_ESTABLISH = 62;

    /**
     * The peripheral has no resources to complete the request
     */
    @SuppressWarnings("WeakerAccess")
    public static final int GATT_NO_RESOURCES = 128;

    /**
     * Something went wrong in the bluetooth stack
     */
    @SuppressWarnings("WeakerAccess")
    public static final int GATT_INTERNAL_ERROR = 129;

    /**
     * The GATT operation could not be executed because the stack is busy
     */
    @SuppressWarnings("WeakerAccess")
    public static final int GATT_BUSY = 132;

    /**
     * Generic error, could be anything
     */
    @SuppressWarnings("WeakerAccess")
    public static final int GATT_ERROR = 133;

    /**
     * Authentication failed
     */
    @SuppressWarnings("WeakerAccess")
    public static final int GATT_AUTH_FAIL = 137;

    /**
     * The connection was cancelled
     */
    @SuppressWarnings("WeakerAccess")
    public static final int GATT_CONN_CANCEL = 256;

    /**
     * Bluetooth device type, Unknown
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEVICE_TYPE_UNKNOWN = 0;

    /**
     * Bluetooth device type, Classic - BR/EDR devices
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEVICE_TYPE_CLASSIC = 1;

    /**
     * Bluetooth device type, Low Energy - LE-only
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEVICE_TYPE_LE = 2;

    /**
     * Bluetooth device type, Dual Mode - BR/EDR/LE
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEVICE_TYPE_DUAL = 3;

    /**
     * Indicates the remote device is not bonded (paired).
     * <p>There is no shared link key with the remote device, so communication
     * (if it is allowed at all) will be unauthenticated and unencrypted.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int BOND_NONE = 10;

    /**
     * Indicates bonding (pairing) is in progress with the remote device.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int BOND_BONDING = 11;

    /**
     * Indicates the remote device is bonded (paired).
     * <p>A shared link keys exists locally for the remote device, so
     * communication can be authenticated and encrypted.
     * <p><i>Being bonded (paired) with a remote device does not necessarily
     * mean the device is currently connected. It just means that the pending
     * procedure was completed at some earlier time, and the link key is still
     * stored locally, ready to use on the next connection.
     * </i>
     */
    @SuppressWarnings("WeakerAccess")
    public static final int BOND_BONDED = 12;

    /**
     * The profile is in disconnected state
     */
    @SuppressWarnings("WeakerAccess")
    public static final int STATE_DISCONNECTED = 0;

    /**
     * The profile is in connecting state
     */
    @SuppressWarnings("WeakerAccess")
    public static final int STATE_CONNECTING = 1;

    /**
     * The profile is in connected state
     */
    @SuppressWarnings("WeakerAccess")
    public static final int STATE_CONNECTED = 2;

    /**
     * The profile is in disconnecting state
     */
    @SuppressWarnings("WeakerAccess")
    public static final int STATE_DISCONNECTING = 3;

    // Maximum number of retries of commands
    private static final int MAX_TRIES = 2;

    // Delay to use when doing a connect
    private static final int DIRECT_CONNECTION_DELAY_IN_MS = 100;

    // Timeout to use if no callback on onConnectionStateChange happens
    private static final int CONNECTION_TIMEOUT_IN_MS = 35000;

    // Samsung phones time out after 5 seconds while most other phone time out after 30 seconds
    private static final int TIMEOUT_THRESHOLD_SAMSUNG = 4500;

    // Most other phone time out after 30 seconds
    private static final int TIMEOUT_THRESHOLD_DEFAULT = 25000;

    // When a bond is lost, the bluetooth stack needs some time to update its internal state
    private static final long DELAY_AFTER_BOND_LOST = 1000L;

    // The maximum number of enabled notifications Android supports (BTA_GATTC_NOTIF_REG_MAX)
    private static final int MAX_NOTIFYING_CHARACTERISTICS = 15;

    // Member variables
    private final Context context;
    private final Handler callbackHandler;
    private final BluetoothDevice device;
    private final InternalCallback listener;
    private BluetoothPeripheralCallback peripheralCallback;
    private final Queue<Runnable> commandQueue = new ConcurrentLinkedQueue<>();
    private boolean commandQueueBusy;
    private boolean isRetrying;
    private boolean bondLost = false;
    private boolean manuallyBonding = false;
    private volatile BluetoothGatt bluetoothGatt;
    private int state;
    private int nrTries;
    private byte[] currentWriteBytes;
    private final Set<UUID> notifyingCharacteristics = new HashSet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private Runnable discoverServicesRunnable;
    private long connectTimestamp;
    private String cachedName;

    /**
     * This abstract class is used to implement BluetoothGatt callbacks.
     */
    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            long timePassed = SystemClock.elapsedRealtime() - connectTimestamp;
            cancelConnectionTimer();
            final int previousState = state;
            state = newState;

            if (status == GATT_SUCCESS) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        successfullyConnected(device.getBondState(), timePassed);
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        successfullyDisconnected(previousState);
                        break;
                    case BluetoothProfile.STATE_DISCONNECTING:
                        Timber.i("peripheral is disconnecting");
                        break;
                    case BluetoothProfile.STATE_CONNECTING:
                        Timber.i("peripheral is connecting");
                    default:
                        Timber.e("unknown state received");
                        break;
                }
            } else {
                connectionStateChangeUnsuccessful(status, previousState, newState, timePassed);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != GATT_SUCCESS) {
                Timber.e("service discovery failed due to internal error '%s', disconnecting", statusToString(status));
                disconnect();
                return;
            }

            final List<BluetoothGattService> services = gatt.getServices();
            Timber.i("discovered %d services for '%s'", services.size(), getName());

            if (listener != null) {
                listener.connected(BluetoothPeripheral.this);
            }

            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onServicesDiscovered(BluetoothPeripheral.this);
                }
            });
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            final BluetoothGattCharacteristic parentCharacteristic = descriptor.getCharacteristic();
            if (status != GATT_SUCCESS) {
                Timber.e("failed to write <%s> to descriptor of characteristic: <%s> for device: '%s', ", bytes2String(currentWriteBytes), parentCharacteristic.getUuid(), getAddress());
            }

            // Check if this was the Client Configuration Descriptor
            if (descriptor.getUuid().equals(UUID.fromString(CCC_DESCRIPTOR_UUID))) {
                if (status == GATT_SUCCESS) {
                    byte[] value = descriptor.getValue();
                    if (value != null) {
                        if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                            Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)){
                            // Notify set to on, add it to the set of notifying characteristics
                            notifyingCharacteristics.add(parentCharacteristic.getUuid());
                            if (notifyingCharacteristics.size() > MAX_NOTIFYING_CHARACTERISTICS) {
                                Timber.e("too many (%d) notifying characteristics. The maximum Android can handle is %d", notifyingCharacteristics.size(), MAX_NOTIFYING_CHARACTERISTICS);
                            }
                        } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)){
                            // Notify was turned off, so remove it from the set of notifying characteristics
                            notifyingCharacteristics.remove(parentCharacteristic.getUuid());
                        } else {
                            Timber.e("unexpected CCC descriptor value");
                        }
                    }
                }

                callbackHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        peripheralCallback.onNotificationStateUpdate(BluetoothPeripheral.this, parentCharacteristic, status);
                    }
                });
            } else {
                callbackHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        peripheralCallback.onDescriptorWrite(BluetoothPeripheral.this, currentWriteBytes, descriptor, status);
                    }
                });
            }
            completedCommand();
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            if (status != GATT_SUCCESS) {
                Timber.e("reading descriptor <%s> failed for device '%s'", descriptor.getUuid(), getAddress());
            }

            final byte[] value = copyOf(descriptor.getValue());
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onDescriptorRead(BluetoothPeripheral.this, value, descriptor, status);
                }
            });
            completedCommand();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            final byte[] value = copyOf(characteristic.getValue());
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onCharacteristicUpdate(BluetoothPeripheral.this, value, characteristic, GATT_SUCCESS);
                }
            });
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            if (status != GATT_SUCCESS) {
                if (status == GATT_AUTH_FAIL || status == GATT_INSUFFICIENT_AUTHENTICATION) {
                    // Characteristic encrypted and needs bonding,
                    // So retry operation after bonding completes
                    // This only seems to happen on Android 5/6/7
                    Timber.w("read needs bonding, bonding in progress");
                    return;
                } else {
                    Timber.e("read failed for characteristic: %s, status %d", characteristic.getUuid(), status);
                    completedCommand();
                    return;
                }
            }

            final byte[] value = copyOf(characteristic.getValue());
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onCharacteristicUpdate(BluetoothPeripheral.this, value, characteristic, status);
                }
            });
            completedCommand();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            if (status != GATT_SUCCESS) {
                if (status == GATT_AUTH_FAIL || status == GATT_INSUFFICIENT_AUTHENTICATION) {
                    // Characteristic encrypted and needs bonding,
                    // So retry operation after bonding completes
                    // This only seems to happen on Android 5/6/7
                    Timber.i("write needs bonding, bonding in progress");
                    return;
                } else {
                    Timber.e("writing <%s> to characteristic <%s> failed, status %s", bytes2String(currentWriteBytes), characteristic.getUuid(), statusToString(status));
                }
            }

            final byte[] value = copyOf(currentWriteBytes);
            currentWriteBytes = null;
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onCharacteristicWrite(BluetoothPeripheral.this, value, characteristic, status);
                }
            });
            completedCommand();
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, final int rssi, final int status) {
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onReadRemoteRssi(BluetoothPeripheral.this, rssi, status);
                }
            });
            completedCommand();
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, final int mtu, final int status) {
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onMtuChanged(BluetoothPeripheral.this, mtu, status);
                }
            });
            completedCommand();
        }
    };

    private void successfullyConnected(int bondstate, long timePassed) {
        Timber.i("connected to '%s' (%s) in %.1fs", getName(), bondStateToString(bondstate), timePassed / 1000.0f);

        if (bondstate == BOND_NONE || bondstate == BOND_BONDED) {
            delayedDiscoverServices(getServiceDiscoveryDelay(bondstate));
        } else if (bondstate == BOND_BONDING) {
            // Apparently the bonding process has already started, so let it complete. We'll do discoverServices once bonding finished
            Timber.i("waiting for bonding to complete");
        }
    }

    private void delayedDiscoverServices(final long delay) {
        discoverServicesRunnable = new Runnable() {
            @Override
            public void run() {
                Timber.d("discovering services of '%s' with delay of %d ms", getName(), delay);
                if (!bluetoothGatt.discoverServices()) {
                    Timber.e("discoverServices failed to start");
                }
                discoverServicesRunnable = null;
            }
        };
        mainHandler.postDelayed(discoverServicesRunnable, delay);
    }

    private long getServiceDiscoveryDelay(int bondstate) {
        long delayWhenBonded = 0;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            // It seems delays when bonded are only needed in versions Nougat or lower
            // This issue was observed on a Nexus 5 (M) and Sony Xperia L1 (N) when connecting to a A&D UA-651BLE
            // The delay is needed when devices have the Service Changed Characteristic.
            // If they don't have it the delay isn't needed but we do it anyway to keep code simple
            delayWhenBonded = 1000L;
        }
        return bondstate == BOND_BONDED ? delayWhenBonded : 0;
    }

    private void successfullyDisconnected(int previousState) {
        if (previousState == BluetoothProfile.STATE_CONNECTED || previousState == BluetoothProfile.STATE_DISCONNECTING) {
            Timber.i("disconnected '%s' on request", getName());
        } else if (previousState == BluetoothProfile.STATE_CONNECTING) {
            Timber.i("cancelling connect attempt");
        }

        if (bondLost) {
            completeDisconnect(false, GATT_SUCCESS);
            if (listener != null) {
                // Consider the loss of the bond a connection failure so that a connection retry will take place
                callbackHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        listener.connectFailed(BluetoothPeripheral.this, GATT_SUCCESS);
                    }
                }, DELAY_AFTER_BOND_LOST); // Give the stack some time to register the bond loss internally. This is needed on most phones...
            }
        } else {
            completeDisconnect(true, GATT_SUCCESS);
        }
    }

    private void connectionStateChangeUnsuccessful(int status, int previousState, int newState, long timePassed) {
        // Check if service discovery completed
        if (discoverServicesRunnable != null) {
            // Service discovery is still pending so cancel it
            mainHandler.removeCallbacks(discoverServicesRunnable);
            discoverServicesRunnable = null;
        }
        boolean servicesDiscovered = !getServices().isEmpty();

        // See if the initial connection failed
        if (previousState == BluetoothProfile.STATE_CONNECTING) {
            boolean isTimeout = timePassed > getTimoutThreshold();
            Timber.i("connection failed with status '%s' (%s)", statusToString(status), isTimeout ? "TIMEOUT" : "ERROR");
            final int adjustedStatus = (status == GATT_ERROR && isTimeout) ? GATT_CONN_TIMEOUT : status;
            completeDisconnect(false, adjustedStatus);
            if (listener != null) {
                listener.connectFailed(BluetoothPeripheral.this, adjustedStatus);
            }
        } else if (previousState == BluetoothProfile.STATE_CONNECTED && newState == BluetoothProfile.STATE_DISCONNECTED && !servicesDiscovered) {
            // We got a disconnection before the services were even discovered
            Timber.i("peripheral '%s' disconnected with status '%s' before completing service discovery", getName(), statusToString(status));
            completeDisconnect(false, status);
            if (listener != null) {
                listener.connectFailed(BluetoothPeripheral.this, status);
            }
        } else {
            // See if we got connection drop
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Timber.i("peripheral '%s' disconnected with status '%s'", getName(), statusToString(status));
            } else {
                Timber.i("unexpected connection state change for '%s' status '%s'", getName(), statusToString(status));
            }
            completeDisconnect(true, status);
        }
    }

    private final BroadcastReceiver bondStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null) return;
            final BluetoothDevice receivedDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (receivedDevice == null) return;

            // Ignore updates for other devices
            if (!receivedDevice.getAddress().equalsIgnoreCase(getAddress())) return;

            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                final int previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);
                handleBondStateChange(bondState, previousBondState);
            }
        }
    };

    private void handleBondStateChange(int bondState, int previousBondState) {
        switch (bondState) {
            case BOND_BONDING:
                Timber.d("starting bonding with '%s' (%s)", getName(), getAddress());
                callbackHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        peripheralCallback.onBondingStarted(BluetoothPeripheral.this);
                    }
                });
                break;
            case BOND_BONDED:
                // Bonding succeeded
                Timber.d("bonded with '%s' (%s)", getName(), getAddress());
                callbackHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        peripheralCallback.onBondingSucceeded(BluetoothPeripheral.this);
                    }
                });

                // If bonding was started at connection time, we may still have to discover the services
                if (bluetoothGatt.getServices().isEmpty()) {
                    delayedDiscoverServices(0);
                }

                // If bonding was triggered by a read/write, we must retry it
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    if (commandQueueBusy && !manuallyBonding) {
                        mainHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Timber.d("retrying command after bonding");
                                retryCommand();
                            }
                        }, 50);
                    }
                }

                // If we are doing a manual bond, complete the command
                if (manuallyBonding) {
                    manuallyBonding = false;
                    completedCommand();
                }
                break;
            case BOND_NONE:
                if (previousBondState == BOND_BONDING) {
                    Timber.e("bonding failed for '%s', disconnecting device", getName());
                    callbackHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            peripheralCallback.onBondingFailed(BluetoothPeripheral.this);
                        }
                    });
                } else {
                    Timber.e("bond lost for '%s'", getName());
                    bondLost = true;

                    // Cancel the discoverServiceRunnable if it is still pending
                    if (discoverServicesRunnable != null) {
                        mainHandler.removeCallbacks(discoverServicesRunnable);
                        discoverServicesRunnable = null;
                    }

                    callbackHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            peripheralCallback.onBondLost(BluetoothPeripheral.this);
                        }
                    });
                }
                disconnect();
                break;
        }
    }

    private final BroadcastReceiver pairingRequestBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null) return;

            // Skip other devices
            if (!device.getAddress().equalsIgnoreCase(getAddress())) return;

            final int variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR);
            Timber.d("pairing request received " + ", pairing variant: " + pairingVariantToString(variant) + " (" + variant + ")");

            if (variant == PAIRING_VARIANT_PIN) {
                String pin = listener.getPincode(BluetoothPeripheral.this);
                if (pin != null) {
                    Timber.d("Setting PIN code for this peripheral using '%s'", pin);
                    device.setPin(pin.getBytes());
                    abortBroadcast();
                }
            }
        }
    };

    /**
     * Constructs a new device wrapper around {@code device}.
     *
     * @param context  Android application environment.
     * @param device   Wrapped Android bluetooth device.
     * @param listener Callback to {@link BluetoothCentral}.
     */
    BluetoothPeripheral(Context context, BluetoothDevice device, InternalCallback listener, BluetoothPeripheralCallback peripheralCallback, Handler callbackHandler) {
        if (context == null || device == null || listener == null) {
            Timber.e("cannot create BluetoothPeripheral because of null values");
        }
        this.context = context;
        this.device = device;
        this.peripheralCallback = peripheralCallback;
        this.listener = listener;
        this.callbackHandler = (callbackHandler != null) ? callbackHandler : new Handler(Looper.getMainLooper());
        this.state = BluetoothProfile.STATE_DISCONNECTED;
        this.commandQueueBusy = false;
    }

    void setPeripheralCallback(BluetoothPeripheralCallback peripheralCallback) {
        this.peripheralCallback = peripheralCallback;
    }

    /**
     * Connect directly with the bluetooth device. This call will timeout in max 30 seconds (5 seconds on Samsung phones)
     */
    void connect() {
        // Make sure we are disconnected before we start making a connection
        if (state == BluetoothProfile.STATE_DISCONNECTED) {
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Connect to device with autoConnect = false
                    Timber.i("connect to '%s' (%s) using TRANSPORT_LE", getName(), getAddress());
                    registerBondingBroadcastReceivers();
                    state = BluetoothProfile.STATE_CONNECTING;
                    bluetoothGatt = connectGattHelper(device, false, bluetoothGattCallback);
                    connectTimestamp = SystemClock.elapsedRealtime();
                    startConnectionTimer(BluetoothPeripheral.this);
                }
            }, DIRECT_CONNECTION_DELAY_IN_MS);
        } else {
            Timber.e("peripheral '%s' not yet disconnected, will not connect", getName());
        }
    }

    /**
     * Try to connect to a device whenever it is found by the OS. This call never times out.
     * Connecting to a device will take longer than when using connect()
     */
    void autoConnect() {
        // Note that this will only work for devices that are known! After turning BT on/off Android doesn't know the device anymore!
        // https://stackoverflow.com/questions/43476369/android-save-ble-device-to-reconnect-after-app-close
        if (state == BluetoothProfile.STATE_DISCONNECTED) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    // Connect to device with autoConnect = true
                    Timber.i("autoConnect to '%s' (%s) using TRANSPORT_LE", getName(), getAddress());
                    registerBondingBroadcastReceivers();
                    state = BluetoothProfile.STATE_CONNECTING;
                    bluetoothGatt = connectGattHelper(device, true, bluetoothGattCallback);
                    connectTimestamp = SystemClock.elapsedRealtime();
                }
            });
        } else {
            Timber.e("peripheral '%s' not yet disconnected, will not connect", getName());
        }
    }

    private void registerBondingBroadcastReceivers() {
        context.registerReceiver(bondStateReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
        context.registerReceiver(pairingRequestBroadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST));
    }

    /**
     * Create a bond with the peripheral.
     *
     * <p>If a (auto)connect has been issued, the bonding command will be enqueued and you will
     * receive updates via the {@link BluetoothPeripheralCallback}. Otherwise the bonding will
     * be done immediately and no updates via the callback will happen.
     *
     * @return true if bonding was started/enqueued, false if not
     */
    public boolean createBond() {
        // Check if we have a Gatt object
        if (bluetoothGatt == null) {
            // No gatt object so no connection issued, do create bond immediately
            return device.createBond();
        }

        // Enqueue the bond command because a connection has been issued or we are already connected
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                manuallyBonding = true;
                if (!device.createBond()) {
                    Timber.e("bonding failed for %s", getAddress());
                    completedCommand();
                } else {
                    Timber.d("manually bonding %s", getAddress());
                    nrTries++;
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Timber.e("could not enqueue bonding command");
        }
        return result;
    }

    /**
     * Request a different connection priority.
     * <p>
     * Use the standard parameters for Android: CONNECTION_PRIORITY_BALANCED, CONNECTION_PRIORITY_HIGH, or CONNECTION_PRIORITY_LOW_POWER. There is no callback for this function.
     *
     * @param priority the requested connection priority
     * @return true if request was enqueued, false if not
     */
    public boolean requestConnectionPriority(final int priority) {

        // Enqueue the request connection priority command and complete is immediately as there is no callback for it
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    if (!bluetoothGatt.requestConnectionPriority(priority)) {
                        Timber.e("could not set connection priority");
                    } else {
                        Timber.d("requesting connection priority %d", priority);
                    }
                    completedCommand();
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Timber.e("could not enqueue request connection priority command");
        }
        return result;
    }

    /**
     * Version of createBond with transport parameter.
     * May use in the future if needed as I never encountered an issue
     */
    private boolean createBond(int transport) {
        Timber.d("bonding using TRANSPORT_LE");
        boolean result = false;
        try {
            Method bondMethod = device.getClass().getMethod("createBond", int.class);
            if (bondMethod != null) {
                result = (boolean) bondMethod.invoke(device, transport);
            }
        } catch (Exception e) {
            Timber.e("could not invoke createBond method");
        }
        return result;
    }

    /**
     * Cancel an active or pending connection.
     * <p>
     * This operation is asynchronous and you will receive a callback on onDisconnectedPeripheral.
     */
    public void cancelConnection() {
        // Check if we have a Gatt object
        if (bluetoothGatt == null) {
            return;
        }

        // Check if we are not already disconnected or disconnecting
        if (state == BluetoothProfile.STATE_DISCONNECTED || state == BluetoothProfile.STATE_DISCONNECTING) {
            return;
        }

        // Cancel the connection timer
        cancelConnectionTimer();

        // Check if we are in the process of connecting
        if (state == BluetoothProfile.STATE_CONNECTING) {
            // Cancel the connection by calling disconnect
            disconnect();

            // Since we will not get a callback on onConnectionStateChange for this, we issue the disconnect ourselves
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    bluetoothGattCallback.onConnectionStateChange(bluetoothGatt, GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED);
                }
            }, 50);
        } else {
            // Cancel active connection and onConnectionStateChange will be called by Android
            disconnect();
        }
    }

    /**
     * Disconnect the bluetooth peripheral.
     *
     * <p>When the disconnection has been completed {@link BluetoothCentralCallback#onDisconnectedPeripheral(BluetoothPeripheral, int)} will be called.
     */
    private void disconnect() {
        if (state == BluetoothProfile.STATE_CONNECTED || state == BluetoothProfile.STATE_CONNECTING) {
            this.state = BluetoothProfile.STATE_DISCONNECTING;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (bluetoothGatt != null) {
                        Timber.i("force disconnect '%s' (%s)", getName(), getAddress());
                        bluetoothGatt.disconnect();
                    }
                }
            });
        } else {
            if (listener != null) {
                listener.disconnected(BluetoothPeripheral.this, GATT_CONN_TERMINATE_LOCAL_HOST);
            }
        }
    }

    void disconnectWhenBluetoothOff() {
        bluetoothGatt = null;
        completeDisconnect(true, GATT_SUCCESS);
    }

    /**
     * Complete the disconnect after getting connectionstate = disconnected
     */
    private void completeDisconnect(boolean notify, final int status) {
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        commandQueue.clear();
        commandQueueBusy = false;
        try {
            context.unregisterReceiver(bondStateReceiver);
            context.unregisterReceiver(pairingRequestBroadcastReceiver);
        } catch (IllegalArgumentException e) {
            // In case bluetooth is off, unregisering broadcast receivers may fail
        }
        bondLost = false;
        if (listener != null && notify) {
            listener.disconnected(BluetoothPeripheral.this, status);
        }
    }

    /**
     * Get the mac address of the bluetooth peripheral.
     *
     * @return Address of the bluetooth peripheral
     */
    public String getAddress() {
        return device.getAddress();
    }

    /**
     * Get the type of the peripheral.
     *
     * @return the device type {@link #DEVICE_TYPE_CLASSIC}, {@link #DEVICE_TYPE_LE} {@link #DEVICE_TYPE_DUAL}. {@link #DEVICE_TYPE_UNKNOWN} if it's not available
     */
    public int getType() {
        return device.getType();
    }

    /**
     * Get the name of the bluetooth peripheral.
     *
     * @return name of the bluetooth peripheral
     */
    public String getName() {
        String name = device.getName();
        if (name != null) {
            // Cache the name so that we even know it when bluetooth is switched off
            cachedName = name;
        }
        return cachedName;
    }

    /**
     * Get the bond state of the bluetooth peripheral.
     *
     * <p>Possible values for the bond state are:
     * {@link #BOND_NONE},
     * {@link #BOND_BONDING},
     * {@link #BOND_BONDED}.
     *
     * @return returns the bond state
     */
    public int getBondState() {
        return device.getBondState();
    }

    /**
     * Get the services supported by the connected bluetooth peripheral.
     * Only services that are also supported by {@link BluetoothCentral} are included.
     *
     * @return Supported services.
     */
    @SuppressWarnings("WeakerAccess")
    public List<BluetoothGattService> getServices() {
        return bluetoothGatt.getServices();
    }

    /**
     * Get the BluetoothGattService object for a service UUID.
     *
     * @param serviceUUID the UUID of the service
     * @return the BluetoothGattService object for the service UUID or null if the peripheral does not have a service with the specified UUID
     */
    public BluetoothGattService getService(UUID serviceUUID) {
        if (bluetoothGatt != null) {
            return bluetoothGatt.getService(serviceUUID);
        } else {
            return null;
        }
    }

    /**
     * Get the BluetoothGattCharacteristic object for a characteristic UUID.
     *
     * @param serviceUUID        the service UUID the characteristic is part of
     * @param characteristicUUID the UUID of the chararacteristic
     * @return the BluetoothGattCharacteristic object for the characteristic UUID or null if the peripheral does not have a characteristic with the specified UUID
     */
    public BluetoothGattCharacteristic getCharacteristic(UUID serviceUUID, UUID characteristicUUID) {
        BluetoothGattService service = getService(serviceUUID);
        if (service != null) {
            return service.getCharacteristic(characteristicUUID);
        } else {
            return null;
        }
    }

    /**
     * Returns the connection state of the peripheral.
     *
     * <p>Possible values for the connection state are:
     * {@link #STATE_CONNECTED},
     * {@link #STATE_CONNECTING},
     * {@link #STATE_DISCONNECTED},
     * {@link #STATE_DISCONNECTING}.
     *
     * @return the connection state.
     */
    public int getState() {
        return state;
    }

    /**
     * Boolean to indicate if the specified characteristic is currently notifying or indicating.
     *
     * @param characteristic the characteristic to check
     * @return true if the characteristic is notifying or indicating, false if it is not
     */
    public boolean isNotifying(BluetoothGattCharacteristic characteristic) {
        return notifyingCharacteristics.contains(characteristic.getUuid());
    }

    private boolean isConnected() {
        return bluetoothGatt != null && state == BluetoothProfile.STATE_CONNECTED;
    }

    /**
     * Read the value of a characteristic.
     *
     * <p>The characteristic must support reading it, otherwise the operation will not be enqueued.
     *
     * <p>{@link BluetoothPeripheralCallback#onCharacteristicUpdate(BluetoothPeripheral, byte[], BluetoothGattCharacteristic, int)}   will be triggered as a result of this call.
     *
     * @param characteristic Specifies the characteristic to read.
     * @return true if the operation was enqueued, false if the characteristic does not support reading or the characteristic was invalid
     */
    public boolean readCharacteristic(final BluetoothGattCharacteristic characteristic) {
        // Check if gatt object is valid
        if (bluetoothGatt == null) {
            Timber.e("gatt is 'null', ignoring read request");
            return false;
        }

        // Check if characteristic is valid
        if (characteristic == null) {
            Timber.e("characteristic is 'null', ignoring read request");
            return false;
        }

        // Check if this characteristic actually has READ property
        if ((characteristic.getProperties() & PROPERTY_READ) == 0) {
            Timber.e("characteristic does not have read property");
            return false;
        }

        // Enqueue the read command now that all checks have been passed
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    if (!bluetoothGatt.readCharacteristic(characteristic)) {
                        Timber.e("readCharacteristic failed for characteristic: %s", characteristic.getUuid());
                        completedCommand();
                    } else {
                        Timber.d("reading characteristic <%s>", characteristic.getUuid());
                        nrTries++;
                    }
                } else {
                    completedCommand();
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Timber.e("could not enqueue read characteristic command");
        }
        return result;
    }

    /**
     * Write a value to a characteristic using the specified write type.
     *
     * <p>All parameters must have a valid value in order for the operation
     * to be enqueued. If the characteristic does not support writing with the specified writeType, the operation will not be enqueued.
     *
     * <p>{@link BluetoothPeripheralCallback#onCharacteristicWrite(BluetoothPeripheral, byte[], BluetoothGattCharacteristic, int)} will be triggered as a result of this call.
     *
     * @param characteristic the characteristic to write to
     * @param value          the byte array to write
     * @param writeType      the write type to use when writing. Must be WRITE_TYPE_DEFAULT, WRITE_TYPE_NO_RESPONSE or WRITE_TYPE_SIGNED
     * @return true if a write operation was succesfully enqueued, otherwise false
     */
    public boolean writeCharacteristic(final BluetoothGattCharacteristic characteristic, final byte[] value, final int writeType) {
        // Check if gatt object is valid
        if (bluetoothGatt == null) {
            Timber.e("gatt is 'null', ignoring read request");
            return false;
        }

        // Check if characteristic is valid
        if (characteristic == null) {
            Timber.e("characteristic is 'null', ignoring write request");
            return false;
        }

        // Check if byte array is valid
        if (value == null) {
            Timber.e("value to write is 'null', ignoring write request");
            return false;
        }

        // Copy the value to avoid race conditions
        final byte[] bytesToWrite = copyOf(value);

        // Check if this characteristic actually supports this writeType
        int writeProperty;
        switch (writeType) {
            case WRITE_TYPE_DEFAULT:
                writeProperty = PROPERTY_WRITE;
                break;
            case WRITE_TYPE_NO_RESPONSE:
                writeProperty = PROPERTY_WRITE_NO_RESPONSE;
                break;
            case WRITE_TYPE_SIGNED:
                writeProperty = PROPERTY_SIGNED_WRITE;
                break;
            default:
                writeProperty = 0;
                break;
        }
        if ((characteristic.getProperties() & writeProperty) == 0) {
            Timber.e("characteristic <%s> does not support writeType '%s'", characteristic.getUuid(), writeTypeToString(writeType));
            return false;
        }

        // Enqueue the write command now that all checks have been passed
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    currentWriteBytes = bytesToWrite;
                    characteristic.setValue(bytesToWrite);
                    characteristic.setWriteType(writeType);
                    if (!bluetoothGatt.writeCharacteristic(characteristic)) {
                        Timber.e("writeCharacteristic failed for characteristic: %s", characteristic.getUuid());
                        completedCommand();
                    } else {
                        Timber.d("writing <%s> to characteristic <%s>", bytes2String(bytesToWrite), characteristic.getUuid());
                        nrTries++;
                    }
                } else {
                    completedCommand();
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Timber.e("could not enqueue write characteristic command");
        }
        return result;
    }


    /**
     * Read the value of a descriptor.
     *
     * @param descriptor the descriptor to read
     * @return true if a write operation was succesfully enqueued, otherwise false
     */
    public boolean readDescriptor(final BluetoothGattDescriptor descriptor) {
        // Check if gatt object is valid
        if (bluetoothGatt == null) {
            Timber.e("gatt is 'null', ignoring read request");
            return false;
        }

        // Check if descriptor is valid
        if (descriptor == null) {
            Timber.e("descriptor is 'null', ignoring read request");
            return false;
        }

        // Enqueue the read command now that all checks have been passed
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    if (!bluetoothGatt.readDescriptor(descriptor)) {
                        Timber.e("readDescriptor failed for characteristic: %s", descriptor.getUuid());
                        completedCommand();
                    } else {
                        nrTries++;
                    }
                } else {
                    completedCommand();
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Timber.e("could not enqueue read descriptor command");
        }
        return result;
    }

    /**
     * Write a value to a descriptor.
     *
     * <p>For turning on/off notifications use {@link BluetoothPeripheral#setNotify(BluetoothGattCharacteristic, boolean)} instead.
     *
     * @param descriptor the descriptor to write to
     * @param value      the value to write
     * @return true if a write operation was succesfully enqueued, otherwise false
     */
    public boolean writeDescriptor(final BluetoothGattDescriptor descriptor, final byte[] value) {
        // Check if gatt object is valid
        if (bluetoothGatt == null) {
            Timber.e("gatt is 'null', ignoring write descriptor request");
            return false;
        }

        // Check if characteristic is valid
        if (descriptor == null) {
            Timber.e("descriptor is 'null', ignoring write request");
            return false;
        }

        // Check if byte array is valid
        if (value == null) {
            Timber.e("value to write is 'null', ignoring write request");
            return false;
        }

        // Copy the value to avoid race conditions
        final byte[] bytesToWrite = copyOf(value);

        // Enqueue the write command now that all checks have been passed
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    currentWriteBytes = bytesToWrite;
                    descriptor.setValue(bytesToWrite);
                    if (!bluetoothGatt.writeDescriptor(descriptor)) {
                        Timber.e("writeDescriptor failed for descriptor: %s", descriptor.getUuid());
                        completedCommand();
                    } else {
                        Timber.d("writing <%s> to descriptor <%s>", bytes2String(bytesToWrite), descriptor.getUuid());
                        nrTries++;
                    }
                } else {
                    completedCommand();
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Timber.e("could not enqueue write descriptor command");
        }
        return result;
    }

    /**
     * Set the notification state of a characteristic to 'on' or 'off'. The characteristic must support notifications or indications.
     *
     * <p>{@link BluetoothPeripheralCallback#onNotificationStateUpdate(BluetoothPeripheral, BluetoothGattCharacteristic, int)} will be triggered as a result of this call.
     *
     * @param characteristic the characteristic to turn notification on/off for
     * @param enable         true for setting notification on, false for turning it off
     * @return true if the operation was enqueued, false if the characteristic doesn't support notification or indications or
     */
    public boolean setNotify(final BluetoothGattCharacteristic characteristic, final boolean enable) {
        // Check if gatt object is valid
        if (bluetoothGatt == null) {
            Timber.e("gatt is 'null', ignoring set notify request");
            return false;
        }

        // Check if characteristic is valid
        if (characteristic == null) {
            Timber.e("characteristic is 'null', ignoring setNotify request");
            return false;
        }

        // Get the Client Configuration Descriptor for the characteristic
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(CCC_DESCRIPTOR_UUID));
        if (descriptor == null) {
            Timber.e("could not get CCC descriptor for characteristic %s", characteristic.getUuid());
            return false;
        }

        // Check if characteristic has NOTIFY or INDICATE properties and set the correct byte value to be written
        byte[] value;
        int properties = characteristic.getProperties();
        if ((properties & PROPERTY_NOTIFY) > 0) {
            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
        } else if ((properties & PROPERTY_INDICATE) > 0) {
            value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
        } else {
            Timber.e("characteristic %s does not have notify or indicate property", characteristic.getUuid());
            return false;
        }
        final byte[] finalValue = enable ? value : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;

        // Queue Runnable to turn on/off the notification now that all checks have been passed
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (!isConnected()) {
                    completedCommand();
                    return;
                }

                // First set notification for Gatt object
                if (!bluetoothGatt.setCharacteristicNotification(characteristic, enable)) {
                    Timber.e("setCharacteristicNotification failed for characteristic: %s", characteristic.getUuid());
                }

                // Then write to descriptor
                currentWriteBytes = finalValue;
                descriptor.setValue(finalValue);
                boolean result;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    result = bluetoothGatt.writeDescriptor(descriptor);
                } else {
                    // Up to Android 6 there is a bug where Android takes the writeType of the parent characteristic instead of always WRITE_TYPE_DEFAULT
                    // See: https://android.googlesource.com/platform/frameworks/base/+/942aebc95924ab1e7ea1e92aaf4e7fc45f695a6c%5E%21/#F0
                    final BluetoothGattCharacteristic parentCharacteristic = descriptor.getCharacteristic();
                    final int originalWriteType = parentCharacteristic.getWriteType();
                    parentCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    result = bluetoothGatt.writeDescriptor(descriptor);
                    parentCharacteristic.setWriteType(originalWriteType);
                }
                if (!result) {
                    Timber.e("writeDescriptor failed for descriptor: %s", descriptor.getUuid());
                    completedCommand();
                } else {
                    nrTries++;
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Timber.e("could not enqueue setNotify command");
        }

        return result;
    }


    /**
     * Asynchronous method to clear the services cache. Make sure to add a delay when using this!
     *
     * @return true if the method was executed, false if not executed
     */
    public boolean clearServicesCache() {
        boolean result = false;
        try {
            Method refreshMethod = bluetoothGatt.getClass().getMethod("refresh");
            if (refreshMethod != null) {
                result = (boolean) refreshMethod.invoke(bluetoothGatt);
            }
        } catch (Exception e) {
            Timber.e("could not invoke refresh method");
        }
        return result;
    }

    /**
     * Read the RSSI for a connected remote peripheral.
     *
     * <p>{@link BluetoothPeripheralCallback#onReadRemoteRssi(BluetoothPeripheral, int, int)} will be triggered as a result of this call.
     *
     * @return true if the operation was enqueued, false otherwise
     */
    public boolean readRemoteRssi() {
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    if (!bluetoothGatt.readRemoteRssi()) {
                        Timber.e("readRemoteRssi failed");
                        completedCommand();
                    }
                } else {
                    Timber.e("cannot get rssi, peripheral not connected");
                    completedCommand();
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Timber.e("could not enqueue setNotify command");
        }

        return result;
    }

    /**
     * Request an MTU size used for a given connection.
     *
     * <p>When performing a write request operation (write without response),
     * the data sent is truncated to the MTU size. This function may be used
     * to request a larger MTU size to be able to send more data at once.
     *
     * <p>{@link BluetoothPeripheralCallback#onMtuChanged(BluetoothPeripheral, int, int)} will be triggered as a result of this call.
     *
     * @param mtu the desired MTU size
     * @return true if the operation was enqueued, false otherwise
     */
    public boolean requestMtu(final int mtu) {
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    if (!bluetoothGatt.requestMtu(mtu)) {
                        Timber.e("requestMtu failed");
                        completedCommand();
                    }
                } else {
                    Timber.e("cannot request MTU, peripheral not connected");
                    completedCommand();
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Timber.e("could not enqueue setNotify command");
        }

        return result;
    }

    /**
     * The current command has been completed, move to the next command in the queue (if any)
     */
    private void completedCommand() {
        isRetrying = false;
        commandQueue.poll();
        commandQueueBusy = false;
        nextCommand();
    }

    /**
     * Retry the current command. Typically used when a read/write fails and triggers a bonding procedure
     */
    private void retryCommand() {
        commandQueueBusy = false;
        Runnable currentCommand = commandQueue.peek();
        if (currentCommand != null) {
            if (nrTries >= MAX_TRIES) {
                // Max retries reached, give up on this one and proceed
                Timber.d("max number of tries reached, not retrying operation anymore");
                commandQueue.poll();
            } else {
                isRetrying = true;
            }
        }
        nextCommand();
    }

    /**
     * Execute the next command in the subscribe queue.
     * A queue is used because the calls have to be executed sequentially.
     * If the read or write fails, the next command in the queue is executed.
     */
    private void nextCommand() {
        synchronized (this) {
            // If there is still a command being executed, then bail out
            if (commandQueueBusy) return;

            // Check if there is something to do at all
            final Runnable bluetoothCommand = commandQueue.peek();
            if (bluetoothCommand == null) return;

            // Check if we still have a valid gatt object
            if (bluetoothGatt == null) {
                Timber.e("gatt is 'null' for peripheral '%s', clearing command queue", getAddress());
                commandQueue.clear();
                commandQueueBusy = false;
                return;
            }

            // Execute the next command in the queue
            commandQueueBusy = true;
            if (!isRetrying) {
                nrTries = 0;
            }
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        bluetoothCommand.run();
                    } catch (Exception ex) {
                        Timber.e(ex, "command exception for device '%s'", getName());
                        completedCommand();
                    }
                }
            });
        }
    }

    private String bondStateToString(final int state) {
        switch (state) {
            case BOND_NONE:
                return "BOND_NONE";
            case BOND_BONDING:
                return "BOND_BONDING";
            case BOND_BONDED:
                return "BOND_BONDED";
            default:
                return "UNKNOWN";
        }
    }

    private String stateToString(final int state) {
        switch (state) {
            case BluetoothProfile.STATE_CONNECTED:
                return "CONNECTED";
            case BluetoothProfile.STATE_CONNECTING:
                return "CONNECTING";
            case BluetoothProfile.STATE_DISCONNECTING:
                return "DISCONNECTING";
            default:
                return "DISCONNECTED";
        }
    }

    private String writeTypeToString(final int writeType) {
        switch (writeType) {
            case WRITE_TYPE_DEFAULT:
                return "WRITE_TYPE_DEFAULT";
            case WRITE_TYPE_NO_RESPONSE:
                return "WRITE_TYPE_NO_RESPONSE";
            case WRITE_TYPE_SIGNED:
                return "WRITE_TYPE_SIGNED";
            default:
                return "unknown writeType";
        }
    }

    private static String statusToString(final int error) {
        switch (error) {
            case GATT_SUCCESS:
                return "SUCCESS";
            case GATT_CONN_L2C_FAILURE:
                return "GATT CONN L2C FAILURE";
            case GATT_CONN_TIMEOUT:
                return "GATT CONN TIMEOUT";  // Connection timed out
            case GATT_CONN_TERMINATE_PEER_USER:
                return "GATT CONN TERMINATE PEER USER";
            case GATT_CONN_TERMINATE_LOCAL_HOST:
                return "GATT CONN TERMINATE LOCAL HOST";
            case BLE_HCI_CONN_TERMINATED_DUE_TO_MIC_FAILURE:
                return "BLE HCI CONN TERMINATED DUE TO MIC FAILURE";
            case GATT_CONN_FAIL_ESTABLISH:
                return "GATT CONN FAIL ESTABLISH";
            case GATT_CONN_LMP_TIMEOUT:
                return "GATT CONN LMP TIMEOUT";
            case GATT_CONN_CANCEL:
                return "GATT CONN CANCEL ";
            case GATT_BUSY:
                return "GATT BUSY";
            case GATT_ERROR:
                return "GATT ERROR"; // Device not reachable
            case GATT_AUTH_FAIL:
                return "GATT AUTH FAIL";  // Device needs to be bonded
            case GATT_NO_RESOURCES:
                return "GATT NO RESOURCES";
            case GATT_INTERNAL_ERROR:
                return "GATT INTERNAL ERROR";
            default:
                return "UNKNOWN (" + error + ")";
        }
    }

    private static final int PAIRING_VARIANT_PIN = 0;
    private static final int PAIRING_VARIANT_PASSKEY = 1;
    private static final int PAIRING_VARIANT_PASSKEY_CONFIRMATION = 2;
    private static final int PAIRING_VARIANT_CONSENT = 3;
    private static final int PAIRING_VARIANT_DISPLAY_PASSKEY = 4;
    private static final int PAIRING_VARIANT_DISPLAY_PIN = 5;
    private static final int PAIRING_VARIANT_OOB_CONSENT = 6;

    private String pairingVariantToString(final int variant) {
        switch (variant) {
            case PAIRING_VARIANT_PIN:
                return "PAIRING_VARIANT_PIN";
            case PAIRING_VARIANT_PASSKEY:
                return "PAIRING_VARIANT_PASSKEY";
            case PAIRING_VARIANT_PASSKEY_CONFIRMATION:
                return "PAIRING_VARIANT_PASSKEY_CONFIRMATION";
            case PAIRING_VARIANT_CONSENT:
                return "PAIRING_VARIANT_CONSENT";
            case PAIRING_VARIANT_DISPLAY_PASSKEY:
                return "PAIRING_VARIANT_DISPLAY_PASSKEY";
            case PAIRING_VARIANT_DISPLAY_PIN:
                return "PAIRING_VARIANT_DISPLAY_PIN";
            case PAIRING_VARIANT_OOB_CONSENT:
                return "PAIRING_VARIANT_OOB_CONSENT";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * Converts byte array to hex string
     *
     * @param bytes the byte array to convert
     * @return String representing the byte array as a HEX string
     */
    private static String bytes2String(final byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    interface InternalCallback {

        /**
         * {@link BluetoothPeripheral} has successfully connected.
         *
         * @param device {@link BluetoothPeripheral} that connected.
         */
        void connected(BluetoothPeripheral device);

        /**
         * Connecting with {@link BluetoothPeripheral} has failed.
         *
         * @param device {@link BluetoothPeripheral} of which connect failed.
         */
        void connectFailed(BluetoothPeripheral device, final int status);

        /**
         * {@link BluetoothPeripheral} has disconnected.
         *
         * @param device {@link BluetoothPeripheral} that disconnected.
         */
        void disconnected(BluetoothPeripheral device, final int status);

        String getPincode(BluetoothPeripheral device);

    }

    /////////////////

    private BluetoothGatt connectGattHelper(BluetoothDevice remoteDevice, boolean autoConnect, BluetoothGattCallback bluetoothGattCallback) {

        if (remoteDevice == null) {
            return null;
        }

        /*
          This bug workaround was taken from the Polidea RxAndroidBle
          Issue that caused a race condition mentioned below was fixed in 7.0.0_r1
          https://android.googlesource.com/platform/frameworks/base/+/android-7.0.0_r1/core/java/android/bluetooth/BluetoothGatt.java#649
          compared to
          https://android.googlesource.com/platform/frameworks/base/+/android-6.0.1_r72/core/java/android/bluetooth/BluetoothGatt.java#739
          issue: https://android.googlesource.com/platform/frameworks/base/+/d35167adcaa40cb54df8e392379dfdfe98bcdba2%5E%21/#F0
          */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || !autoConnect) {
            return connectGattCompat(bluetoothGattCallback, remoteDevice, autoConnect);
        }

        try {
            Object iBluetoothGatt = getIBluetoothGatt(getIBluetoothManager());

            if (iBluetoothGatt == null) {
                Timber.e("could not get iBluetoothGatt object");
                return connectGattCompat(bluetoothGattCallback, remoteDevice, true);
            }

            BluetoothGatt bluetoothGatt = createBluetoothGatt(iBluetoothGatt, remoteDevice);

            if (bluetoothGatt == null) {
                Timber.e("could not create BluetoothGatt object");
                return connectGattCompat(bluetoothGattCallback, remoteDevice, true);
            }

            boolean connectedSuccessfully = connectUsingReflection(remoteDevice, bluetoothGatt, bluetoothGattCallback, true);

            if (!connectedSuccessfully) {
                Timber.i("connection using reflection failed, closing gatt");
                bluetoothGatt.close();
            }

            return bluetoothGatt;
        } catch (NoSuchMethodException
                | IllegalAccessException
                | IllegalArgumentException
                | InvocationTargetException
                | InstantiationException
                | NoSuchFieldException exception) {
            Timber.e("error during reflection");
            return connectGattCompat(bluetoothGattCallback, remoteDevice, true);
        }
    }

    private BluetoothGatt connectGattCompat(BluetoothGattCallback bluetoothGattCallback, BluetoothDevice device, boolean autoConnect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return device.connectGatt(context, autoConnect, bluetoothGattCallback, TRANSPORT_LE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Try to call connectGatt with TRANSPORT_LE parameter using reflection
            try {
                Method connectGattMethod = device.getClass().getMethod("connectGatt", Context.class, boolean.class, BluetoothGattCallback.class, int.class);
                try {
                    return (BluetoothGatt) connectGattMethod.invoke(device, context, autoConnect, bluetoothGattCallback, TRANSPORT_LE);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
        // Fallback on connectGatt without TRANSPORT_LE parameter
        return device.connectGatt(context, autoConnect, bluetoothGattCallback);
    }

    @SuppressWarnings("SameParameterValue")
    private boolean connectUsingReflection(BluetoothDevice device, BluetoothGatt bluetoothGatt, BluetoothGattCallback bluetoothGattCallback, boolean autoConnect)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        setAutoConnectValue(bluetoothGatt, autoConnect);
        Method connectMethod = bluetoothGatt.getClass().getDeclaredMethod("connect", Boolean.class, BluetoothGattCallback.class);
        connectMethod.setAccessible(true);
        return (Boolean) (connectMethod.invoke(bluetoothGatt, true, bluetoothGattCallback));
    }

    private BluetoothGatt createBluetoothGatt(Object iBluetoothGatt, BluetoothDevice remoteDevice)
            throws IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor bluetoothGattConstructor = BluetoothGatt.class.getDeclaredConstructors()[0];
        bluetoothGattConstructor.setAccessible(true);
        if (bluetoothGattConstructor.getParameterTypes().length == 4) {
            return (BluetoothGatt) (bluetoothGattConstructor.newInstance(context, iBluetoothGatt, remoteDevice, TRANSPORT_LE));
        } else {
            return (BluetoothGatt) (bluetoothGattConstructor.newInstance(context, iBluetoothGatt, remoteDevice));
        }
    }

    private Object getIBluetoothGatt(Object iBluetoothManager)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        if (iBluetoothManager == null) {
            return null;
        }

        Method getBluetoothGattMethod = getMethodFromClass(iBluetoothManager.getClass(), "getBluetoothGatt");
        return getBluetoothGattMethod.invoke(iBluetoothManager);
    }

    private Object getIBluetoothManager() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            return null;
        }

        Method getBluetoothManagerMethod = getMethodFromClass(bluetoothAdapter.getClass(), "getBluetoothManager");
        return getBluetoothManagerMethod.invoke(bluetoothAdapter);
    }

    private Method getMethodFromClass(Class<?> cls, String methodName) throws NoSuchMethodException {
        Method method = cls.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method;
    }

    private void setAutoConnectValue(BluetoothGatt bluetoothGatt, boolean autoConnect) throws NoSuchFieldException, IllegalAccessException {
        Field autoConnectField = bluetoothGatt.getClass().getDeclaredField("mAutoConnect");
        autoConnectField.setAccessible(true);
        autoConnectField.setBoolean(bluetoothGatt, autoConnect);
    }

    private void startConnectionTimer(final BluetoothPeripheral peripheral) {
        cancelConnectionTimer();
        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Timber.e("connection timout, disconnecting '%s'", peripheral.getName());
                disconnect();
                completeDisconnect(true, GATT_CONN_TIMEOUT);
                timeoutRunnable = null;
            }
        };

        mainHandler.postDelayed(timeoutRunnable, CONNECTION_TIMEOUT_IN_MS);
    }

    private void cancelConnectionTimer() {
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    private int getTimoutThreshold() {
        String manufacturer = Build.MANUFACTURER;
        if (manufacturer.equals("samsung")) {
            return TIMEOUT_THRESHOLD_SAMSUNG;
        } else {
            return TIMEOUT_THRESHOLD_DEFAULT;
        }
    }

    private byte[] copyOf(byte[] source) {
        if (source == null) return new byte[0];
        final int sourceLength = source.length;
        final byte[] copy = new byte[sourceLength];
        System.arraycopy(source, 0, copy, 0, sourceLength);
        return copy;
    }
}
