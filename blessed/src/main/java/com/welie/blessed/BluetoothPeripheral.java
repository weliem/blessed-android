/*
 *   Copyright (c) 2020 Martijn van Welie
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import timber.log.Timber;

import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;
import static android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_BALANCED;
import static android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER;
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

    private static final UUID CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

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

    // Minimal and default MTU
    private static final int DEFAULT_MTU = 23;

    /**
     * Max MTU that Android can handle
     */
    public static final int MAX_MTU = 517;

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

    private static final String NO_VALID_SERVICE_UUID_PROVIDED = "no valid service UUID provided";
    private static final String NO_VALID_CHARACTERISTIC_UUID_PROVIDED = "no valid characteristic UUID provided";
    private static final String NO_VALID_CHARACTERISTIC_PROVIDED = "no valid characteristic provided";
    private static final String NO_VALID_WRITE_TYPE_PROVIDED = "no valid writeType provided";
    private static final String NO_VALID_VALUE_PROVIDED = "no valid value provided";
    private static final String NO_VALID_DESCRIPTOR_PROVIDED = "no valid descriptor provided";
    private static final String PERIPHERAL_NOT_CONNECTECTED = "peripheral not connectected";
    private static final String VALUE_BYTE_ARRAY_IS_EMPTY = "value byte array is empty";
    private static final String VALUE_BYTE_ARRAY_IS_TOO_LONG = "value byte array is too long";

    @NotNull
    private final Context context;

    @NotNull
    private final Handler callbackHandler;

    @NotNull
    private BluetoothDevice device;

    @NotNull
    private final InternalCallback listener;

    @Nullable
    private BluetoothPeripheralCallback peripheralCallback;

    @NotNull
    private final Queue<Runnable> commandQueue = new ConcurrentLinkedQueue<>();

    @Nullable
    private volatile BluetoothGatt bluetoothGatt;

    @Nullable
    private String cachedName;

    @Nullable
    private byte[] currentWriteBytes;

    @NotNull
    private final Set<UUID> notifyingCharacteristics = new HashSet<>();

    @NotNull
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    private Runnable timeoutRunnable;

    @Nullable
    private Runnable discoverServicesRunnable;

    private volatile boolean commandQueueBusy = false;
    private boolean isRetrying;
    private boolean bondLost = false;
    private boolean manuallyBonding = false;
    private boolean discoveryStarted = false;
    private volatile int state = BluetoothProfile.STATE_DISCONNECTED;
    private int nrTries;
    private long connectTimestamp;
    private int currentMtu = DEFAULT_MTU;

    /**
     * This abstract class is used to implement BluetoothGatt callbacks.
     */
    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            cancelConnectionTimer();
            final int previousState = state;
            state = newState;

            final HciStatus hciStatus = HciStatus.fromValue(status);
            if (hciStatus == HciStatus.SUCCESS) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        successfullyConnected();
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        successfullyDisconnected(previousState);
                        break;
                    case BluetoothProfile.STATE_DISCONNECTING:
                        Timber.i("peripheral is disconnecting");
                        break;
                    case BluetoothProfile.STATE_CONNECTING:
                        Timber.i("peripheral is connecting");
                        break;
                    default:
                        Timber.e("unknown state received");
                        break;
                }
            } else {
                connectionStateChangeUnsuccessful(hciStatus, previousState, newState);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            final GattStatus gattStatus = GattStatus.fromValue(status);
            if (gattStatus != GattStatus.SUCCESS) {
                Timber.e("service discovery failed due to internal error '%s', disconnecting", gattStatus);
                disconnect();
                return;
            }

            final List<BluetoothGattService> services = gatt.getServices();
            Timber.i("discovered %d services for '%s'", services.size(), getName());

            // Issue 'connected' since we are now fully connect incl service discovery
            listener.connected(BluetoothPeripheral.this);

            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (peripheralCallback != null) {
                        peripheralCallback.onServicesDiscovered(BluetoothPeripheral.this);
                    }
                }
            });
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            final GattStatus gattStatus = GattStatus.fromValue(status);
            final BluetoothGattCharacteristic parentCharacteristic = descriptor.getCharacteristic();
            if (gattStatus != GattStatus.SUCCESS) {
                Timber.e("failed to write <%s> to descriptor of characteristic <%s> for device: '%s', status '%s' ", bytes2String(currentWriteBytes), parentCharacteristic.getUuid(), getAddress(), gattStatus);
                if (failureThatShouldTriggerBonding(gattStatus)) return;
            }

            // Check if this was the Client Configuration Descriptor
            if (descriptor.getUuid().equals(CCC_DESCRIPTOR_UUID)) {
                if (gattStatus == GattStatus.SUCCESS) {
                    final byte[] value = nonnullOf(descriptor.getValue());
                    if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                            Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
                        notifyingCharacteristics.add(parentCharacteristic.getUuid());
                        if (notifyingCharacteristics.size() > MAX_NOTIFYING_CHARACTERISTICS) {
                            Timber.e("too many (%d) notifying characteristics. The maximum Android can handle is %d", notifyingCharacteristics.size(), MAX_NOTIFYING_CHARACTERISTICS);
                        }
                    } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                        notifyingCharacteristics.remove(parentCharacteristic.getUuid());
                    }
                }

                callbackHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (peripheralCallback != null) {
                            peripheralCallback.onNotificationStateUpdate(BluetoothPeripheral.this, parentCharacteristic, gattStatus);
                        }
                    }
                });
            } else {
                callbackHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (peripheralCallback != null) {
                            peripheralCallback.onDescriptorWrite(BluetoothPeripheral.this, currentWriteBytes, descriptor, gattStatus);
                        }
                    }
                });
            }
            completedCommand();
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            final GattStatus gattStatus = GattStatus.fromValue(status);
            if (gattStatus != GattStatus.SUCCESS) {
                Timber.e("reading descriptor <%s> failed for device '%s, status '%s'", descriptor.getUuid(), getAddress(), gattStatus);
                if (failureThatShouldTriggerBonding(gattStatus)) return;
            }

            final byte[] value = nonnullOf(descriptor.getValue());
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (peripheralCallback != null) {
                        peripheralCallback.onDescriptorRead(BluetoothPeripheral.this, value, descriptor, gattStatus);
                    }
                }
            });
            completedCommand();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            final byte[] value = nonnullOf(characteristic.getValue());
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (peripheralCallback != null) {
                        peripheralCallback.onCharacteristicUpdate(BluetoothPeripheral.this, value, characteristic, GattStatus.SUCCESS);
                    }
                }
            });
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            final GattStatus gattStatus = GattStatus.fromValue(status);
            if (gattStatus != GattStatus.SUCCESS) {
                Timber.e("read failed for characteristic <%s>, status '%s'", characteristic.getUuid(), gattStatus);
                if (failureThatShouldTriggerBonding(gattStatus)) return;
            }

            final byte[] value = nonnullOf(characteristic.getValue());
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (peripheralCallback != null) {
                        peripheralCallback.onCharacteristicUpdate(BluetoothPeripheral.this, value, characteristic, gattStatus);
                    }
                }
            });
            completedCommand();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            final GattStatus gattStatus = GattStatus.fromValue(status);
            if (gattStatus != GattStatus.SUCCESS) {
                Timber.e("writing <%s> to characteristic <%s> failed, status '%s'", bytes2String(currentWriteBytes), characteristic.getUuid(), gattStatus);
                if (failureThatShouldTriggerBonding(gattStatus)) return;
            }

            final byte[] value = nonnullOf(currentWriteBytes);
            currentWriteBytes = null;
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (peripheralCallback != null) {
                        peripheralCallback.onCharacteristicWrite(BluetoothPeripheral.this, value, characteristic, gattStatus);
                    }
                }
            });
            completedCommand();
        }

        private boolean failureThatShouldTriggerBonding(GattStatus gattStatus) {
            if (gattStatus == GattStatus.AUTHORIZATION_FAILED
                    || gattStatus == GattStatus.INSUFFICIENT_AUTHENTICATION
                    || gattStatus == GattStatus.INSUFFICIENT_ENCRYPTION) {
                // Characteristic/descriptor is encrypted and needs bonding, bonding should be in progress already
                // Operation must be retried after bonding is completed.
                // This only seems to happen on Android 5/6/7.
                // On newer versions Android will do retry internally
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    Timber.i("operation will be retried after bonding, bonding should be in progress");
                    return true;
                }
            }
            return false;
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, final int rssi, final int status) {
            final GattStatus gattStatus = GattStatus.fromValue(status);
            if (gattStatus != GattStatus.SUCCESS) {
                Timber.e("reading RSSI failed, status '%s'", GattStatus.fromValue(status));
            }

            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (peripheralCallback != null) {
                        peripheralCallback.onReadRemoteRssi(BluetoothPeripheral.this, rssi, gattStatus);
                    }
                }
            });
            completedCommand();
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, final int mtu, final int status) {
            final GattStatus gattStatus = GattStatus.fromValue(status);
            if (gattStatus != GattStatus.SUCCESS) {
                Timber.e("change MTU failed, status '%s'", GattStatus.fromValue(status));
            }

            currentMtu = mtu;
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (peripheralCallback != null) {
                        peripheralCallback.onMtuChanged(BluetoothPeripheral.this, mtu, gattStatus);
                    }
                }
            });
            completedCommand();
        }
    };

    private void successfullyConnected() {
        int bondstate = device.getBondState();
        long timePassed = SystemClock.elapsedRealtime() - connectTimestamp;
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
                if (bluetoothGatt != null && bluetoothGatt.discoverServices()) {
                    discoveryStarted = true;
                } else {
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
            completeDisconnect(false, HciStatus.SUCCESS);

            // Consider the loss of the bond a connection failure so that a connection retry will take place
            callbackHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    listener.connectFailed(BluetoothPeripheral.this, HciStatus.SUCCESS);
                }
            }, DELAY_AFTER_BOND_LOST); // Give the stack some time to register the bond loss internally. This is needed on most phones...
        } else {
            completeDisconnect(true, HciStatus.SUCCESS);
        }
    }

    private void connectionStateChangeUnsuccessful(HciStatus status, int previousState, int newState) {
        cancelPendingServiceDiscovery();
        boolean servicesDiscovered = !getServices().isEmpty();

        // See if the initial connection failed
        if (previousState == BluetoothProfile.STATE_CONNECTING) {
            long timePassed = SystemClock.elapsedRealtime() - connectTimestamp;
            boolean isTimeout = timePassed > getTimoutThreshold();
            final HciStatus adjustedStatus = (status == HciStatus.ERROR && isTimeout) ? HciStatus.CONNECTION_FAILED_ESTABLISHMENT : status;
            Timber.i("connection failed with status '%s'", adjustedStatus);
            completeDisconnect(false, adjustedStatus);
            listener.connectFailed(BluetoothPeripheral.this, adjustedStatus);
        } else if (previousState == BluetoothProfile.STATE_CONNECTED && newState == BluetoothProfile.STATE_DISCONNECTED && !servicesDiscovered) {
            // We got a disconnection before the services were even discovered
            Timber.i("peripheral '%s' disconnected with status '%s' (%d) before completing service discovery", getName(), status, status.getValue());
            completeDisconnect(false, status);
            listener.connectFailed(BluetoothPeripheral.this, status);
        } else {
            // See if we got connection drop
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Timber.i("peripheral '%s' disconnected with status '%s' (%d)", getName(), status, status.getValue());
            } else {
                Timber.i("unexpected connection state change for '%s' status '%s' (%d)", getName(), status, status.getValue());
            }
            completeDisconnect(true, status);
        }
    }

    private void cancelPendingServiceDiscovery() {
        if (discoverServicesRunnable != null) {
            mainHandler.removeCallbacks(discoverServicesRunnable);
            discoverServicesRunnable = null;
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
                        if (peripheralCallback != null) {
                            peripheralCallback.onBondingStarted(BluetoothPeripheral.this);
                        }
                    }
                });
                break;
            case BOND_BONDED:
                // Bonding succeeded
                Timber.d("bonded with '%s' (%s)", getName(), getAddress());
                callbackHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (peripheralCallback != null) {
                            peripheralCallback.onBondingSucceeded(BluetoothPeripheral.this);
                        }
                    }
                });

                // If bonding was started at connection time, we may still have to discover the services
                // Also make sure we are not starting a discovery while another one is already in progress
                if (bluetoothGatt.getServices().isEmpty() && !discoveryStarted) {
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
                            if (peripheralCallback != null) {
                                peripheralCallback.onBondingFailed(BluetoothPeripheral.this);
                            }
                        }
                    });
                } else {
                    Timber.e("bond lost for '%s'", getName());
                    bondLost = true;

                    // Cancel the discoverServiceRunnable if it is still pending
                    cancelPendingServiceDiscovery();

                    callbackHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (peripheralCallback != null) {
                                peripheralCallback.onBondLost(BluetoothPeripheral.this);
                            }
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
            Timber.d("pairing request received: " + pairingVariantToString(variant) + " (" + variant + ")");

            if (variant == PAIRING_VARIANT_PIN) {
                String pin = listener.getPincode(BluetoothPeripheral.this);
                if (pin != null) {
                    Timber.d("setting PIN code for this peripheral using '%s'", pin);
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
    BluetoothPeripheral(@NotNull Context context, @NotNull BluetoothDevice device, @NotNull InternalCallback listener, @Nullable BluetoothPeripheralCallback peripheralCallback, @Nullable Handler callbackHandler) {
        this.context = Objects.requireNonNull(context, "no valid context provided");
        this.device = Objects.requireNonNull(device, "no valid device provided");
        this.listener = Objects.requireNonNull(listener, "no valid listener provided");
        this.peripheralCallback = peripheralCallback;
        this.callbackHandler = (callbackHandler != null) ? callbackHandler : new Handler(Looper.getMainLooper());
    }

    void setPeripheralCallback(@NotNull BluetoothPeripheralCallback peripheralCallback) {
        this.peripheralCallback = Objects.requireNonNull(peripheralCallback, "no valid peripheral callback provided");
    }

    void setDevice(@NotNull BluetoothDevice bluetoothDevice) {
        this.device = Objects.requireNonNull(bluetoothDevice, "bluetoothdevice is not valid");
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
                    discoveryStarted = false;
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
                    discoveryStarted = false;
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
            Timber.w("cannot cancel connection because no connection attempt is made yet");
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
                    bluetoothGattCallback.onConnectionStateChange(bluetoothGatt, HciStatus.SUCCESS.getValue(), BluetoothProfile.STATE_DISCONNECTED);
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
     * <p>When the disconnection has been completed {@link BluetoothCentralCallback#onDisconnectedPeripheral(BluetoothPeripheral, HciStatus)} will be called.
     */
    private void disconnect() {
        if (state == BluetoothProfile.STATE_CONNECTED || state == BluetoothProfile.STATE_CONNECTING) {
            this.state = BluetoothProfile.STATE_DISCONNECTING;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (state == BluetoothProfile.STATE_DISCONNECTING && bluetoothGatt != null) {
                        bluetoothGatt.disconnect();
                        Timber.i("force disconnect '%s' (%s)", getName(), getAddress());
                    }
                }
            });
        } else {
            listener.disconnected(BluetoothPeripheral.this, HciStatus.SUCCESS);
        }
    }

    void disconnectWhenBluetoothOff() {
        bluetoothGatt = null;
        completeDisconnect(true, HciStatus.SUCCESS);
    }

    /**
     * Complete the disconnect after getting connectionstate == disconnected
     */
    private void completeDisconnect(boolean notify, final HciStatus status) {
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
        if (notify) {
            listener.disconnected(BluetoothPeripheral.this, status);
        }
    }

    /**
     * Get the mac address of the bluetooth peripheral.
     *
     * @return Address of the bluetooth peripheral
     */
    public @NotNull String getAddress() {
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
    public @Nullable String getName() {
        String name = device.getName();
        if (name != null) {
            // Cache the name so that we even know it when bluetooth is switched off
            cachedName = name;
            return name;
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
    public @NotNull List<BluetoothGattService> getServices() {
        if (bluetoothGatt != null) {
            return bluetoothGatt.getServices();
        }
        return Collections.emptyList();
    }

    /**
     * Get the BluetoothGattService object for a service UUID.
     *
     * @param serviceUUID the UUID of the service
     * @return the BluetoothGattService object for the service UUID or null if the peripheral does not have a service with the specified UUID
     */
    public @Nullable BluetoothGattService getService(@NotNull UUID serviceUUID) {
        Objects.requireNonNull(serviceUUID, NO_VALID_SERVICE_UUID_PROVIDED);

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
    public @Nullable BluetoothGattCharacteristic getCharacteristic(@NotNull UUID serviceUUID, @NotNull UUID characteristicUUID) {
        Objects.requireNonNull(serviceUUID, NO_VALID_SERVICE_UUID_PROVIDED);
        Objects.requireNonNull(characteristicUUID, NO_VALID_CHARACTERISTIC_UUID_PROVIDED);

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
     * Returns the currently set MTU
     *
     * @return the MTU
     */
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

    /**
     * Boolean to indicate if the specified characteristic is currently notifying or indicating.
     *
     * @param characteristic the characteristic to check
     * @return true if the characteristic is notifying or indicating, false if it is not
     */
    public boolean isNotifying(@NotNull BluetoothGattCharacteristic characteristic) {
        Objects.requireNonNull(characteristic, NO_VALID_CHARACTERISTIC_PROVIDED);
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
     * @param serviceUUID        the service UUID the characteristic belongs to
     * @param characteristicUUID the characteristic's UUID
     * @return true if the operation was enqueued, false if the characteristic does not support reading or the characteristic was not found
     */
    public boolean readCharacteristic(@NotNull UUID serviceUUID, @NotNull UUID characteristicUUID) {
        Objects.requireNonNull(serviceUUID, NO_VALID_SERVICE_UUID_PROVIDED);
        Objects.requireNonNull(characteristicUUID, NO_VALID_CHARACTERISTIC_UUID_PROVIDED);

        if (!isConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTECTED);
            return false;
        }

        BluetoothGattCharacteristic characteristic = getCharacteristic(serviceUUID, characteristicUUID);
        if (characteristic != null) {
            return readCharacteristic(characteristic);
        }
        return false;
    }

    /**
     * Read the value of a characteristic.
     *
     * <p>The characteristic must support reading it, otherwise the operation will not be enqueued.
     *
     * <p>{@link BluetoothPeripheralCallback#onCharacteristicUpdate(BluetoothPeripheral, byte[], BluetoothGattCharacteristic, GattStatus)}   will be triggered as a result of this call.
     *
     * @param characteristic Specifies the characteristic to read.
     * @return true if the operation was enqueued, false if the characteristic does not support reading or the characteristic was invalid
     */
    public boolean readCharacteristic(@NotNull final BluetoothGattCharacteristic characteristic) {
        Objects.requireNonNull(characteristic, NO_VALID_CHARACTERISTIC_PROVIDED);

        if (!isConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTECTED);
            return false;
        }

        // Check if this characteristic actually has READ property
        if ((characteristic.getProperties() & PROPERTY_READ) == 0) {
            Timber.e("characteristic does not have read property");
            return false;
        }

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
     * @param serviceUUID        the service UUID the characteristic belongs to
     * @param characteristicUUID the characteristic's UUID
     * @param value              the byte array to write
     * @param writeType          the write type to use when writing. Must be WRITE_TYPE_DEFAULT, WRITE_TYPE_NO_RESPONSE or WRITE_TYPE_SIGNED
     * @return true if the operation was enqueued, false if the characteristic does not support reading or the characteristic was not found
     */
    public boolean writeCharacteristic(@NotNull UUID serviceUUID, @NotNull UUID characteristicUUID, @NotNull final byte[] value, @NotNull final WriteType writeType) {
        Objects.requireNonNull(serviceUUID, NO_VALID_SERVICE_UUID_PROVIDED);
        Objects.requireNonNull(characteristicUUID, NO_VALID_CHARACTERISTIC_UUID_PROVIDED);
        Objects.requireNonNull(value, NO_VALID_VALUE_PROVIDED);
        Objects.requireNonNull(writeType, NO_VALID_WRITE_TYPE_PROVIDED);

        if (!isConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTECTED);
            return false;
        }

        BluetoothGattCharacteristic characteristic = getCharacteristic(serviceUUID, characteristicUUID);
        if (characteristic != null) {
            return writeCharacteristic(characteristic, value, writeType);
        }
        return false;
    }

    /**
     * Write a value to a characteristic using the specified write type.
     *
     * <p>All parameters must have a valid value in order for the operation
     * to be enqueued. If the characteristic does not support writing with the specified writeType, the operation will not be enqueued.
     * The length of the byte array to write must be between 1 and getMaximumWriteValueLength(writeType).
     *
     * <p>{@link BluetoothPeripheralCallback#onCharacteristicWrite(BluetoothPeripheral, byte[], BluetoothGattCharacteristic, GattStatus)} will be triggered as a result of this call.
     *
     * @param characteristic the characteristic to write to
     * @param value          the byte array to write
     * @param writeType      the write type to use when writing.
     * @return true if a write operation was succesfully enqueued, otherwise false
     */
    public boolean writeCharacteristic(@NotNull final BluetoothGattCharacteristic characteristic, @NotNull final byte[] value, @NotNull final WriteType writeType) {
        Objects.requireNonNull(characteristic, NO_VALID_CHARACTERISTIC_PROVIDED);
        Objects.requireNonNull(value, NO_VALID_VALUE_PROVIDED);
        Objects.requireNonNull(writeType, NO_VALID_WRITE_TYPE_PROVIDED);

        if (!isConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTECTED);
            return false;
        }

        if (value.length == 0) {
            throw new IllegalArgumentException(VALUE_BYTE_ARRAY_IS_EMPTY);
        }

        if (value.length > getMaximumWriteValueLength(writeType)) {
            throw new IllegalArgumentException(VALUE_BYTE_ARRAY_IS_TOO_LONG);
        }

        // See if a Long Write is being triggered because of the byte array length
        if (value.length > currentMtu - 3 && writeType == WriteType.WITH_RESPONSE) {
            // Android will turn this into a Long Write because it is larger than the MTU - 3.
            // When doing a Long Write the byte array will be automatically split in chunks of size MTU - 3.
            // However, the peripheral's firmware must also support it, so it is not guaranteed to work.
            // Long writes are also very inefficient because of the confirmation of each write operation.
            // So it is better to increase MTU if possible. Hence a warning if this write becomes a long write...
            // See https://stackoverflow.com/questions/48216517/rxandroidble-write-only-sends-the-first-20b
            Timber.w("value byte array is longer than allowed by MTU, write will fail if peripheral does not support long writes");
        }

        // Copy the value to avoid race conditions
        final byte[] bytesToWrite = copyOf(value);

        // Check if this characteristic actually supports this writeType
        int writeProperty;
        final int writeTypeInternal;
        switch (writeType) {
            case WITH_RESPONSE:
                writeProperty = PROPERTY_WRITE;
                writeTypeInternal = WRITE_TYPE_DEFAULT;
                break;
            case WITHOUT_RESPONSE:
                writeProperty = PROPERTY_WRITE_NO_RESPONSE;
                writeTypeInternal = WRITE_TYPE_NO_RESPONSE;
                break;
            case SIGNED:
                writeProperty = PROPERTY_SIGNED_WRITE;
                writeTypeInternal = WRITE_TYPE_SIGNED;
                break;
            default:
                writeProperty = 0;
                writeTypeInternal = 0;
                break;
        }
        if ((characteristic.getProperties() & writeProperty) == 0) {
            Timber.e("characteristic <%s> does not support writeType '%s'", characteristic.getUuid(), writeType);
            return false;
        }

        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    currentWriteBytes = bytesToWrite;
                    characteristic.setWriteType(writeTypeInternal);
                    characteristic.setValue(bytesToWrite);
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
    public boolean readDescriptor(@NotNull final BluetoothGattDescriptor descriptor) {
        Objects.requireNonNull(descriptor, NO_VALID_DESCRIPTOR_PROVIDED);

        if (!isConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTECTED);
            return false;
        }

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
    public boolean writeDescriptor(@NotNull final BluetoothGattDescriptor descriptor, @NotNull final byte[] value) {
        Objects.requireNonNull(descriptor, NO_VALID_DESCRIPTOR_PROVIDED);
        Objects.requireNonNull(value, NO_VALID_VALUE_PROVIDED);

        if (!isConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTECTED);
            return false;
        }

        if (value.length == 0) {
            throw new IllegalArgumentException(VALUE_BYTE_ARRAY_IS_EMPTY);
        }

        if (value.length > getMaximumWriteValueLength(WriteType.WITH_RESPONSE)) {
            throw new IllegalArgumentException(VALUE_BYTE_ARRAY_IS_TOO_LONG);
        }

        // Copy the value to avoid race conditions
        final byte[] bytesToWrite = copyOf(value);

        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    currentWriteBytes = bytesToWrite;
                    descriptor.setValue(bytesToWrite);
                    adjustWriteTypeIfNeeded(descriptor);
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
     * @param serviceUUID        the service UUID the characteristic belongs to
     * @param characteristicUUID the characteristic's UUID
     * @param enable             true for setting notification on, false for turning it off
     * @return true if the operation was enqueued, false the characteristic could not be found or does not support notifications
     */
    public boolean setNotify(@NotNull UUID serviceUUID, @NotNull UUID characteristicUUID, boolean enable) {
        Objects.requireNonNull(serviceUUID, NO_VALID_SERVICE_UUID_PROVIDED);
        Objects.requireNonNull(characteristicUUID, NO_VALID_CHARACTERISTIC_UUID_PROVIDED);

        if (!isConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTECTED);
            return false;
        }

        BluetoothGattCharacteristic characteristic = getCharacteristic(serviceUUID, characteristicUUID);
        if (characteristic != null) {
            return setNotify(characteristic, enable);
        }
        return false;
    }

    /**
     * Set the notification state of a characteristic to 'on' or 'off'. The characteristic must support notifications or indications.
     *
     * <p>{@link BluetoothPeripheralCallback#onNotificationStateUpdate(BluetoothPeripheral, BluetoothGattCharacteristic, GattStatus)} will be triggered as a result of this call.
     *
     * @param characteristic the characteristic to turn notification on/off for
     * @param enable         true for setting notification on, false for turning it off
     * @return true if the operation was enqueued, false if the characteristic doesn't support notification or indications or
     */
    public boolean setNotify(@NotNull final BluetoothGattCharacteristic characteristic, final boolean enable) {
        Objects.requireNonNull(characteristic, NO_VALID_CHARACTERISTIC_PROVIDED);

        if (!isConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTECTED);
            return false;
        }

        // Get the Client Characteristic Configuration Descriptor for the characteristic
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCC_DESCRIPTOR_UUID);
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
                adjustWriteTypeIfNeeded(descriptor);
                if (!bluetoothGatt.writeDescriptor(descriptor)) {
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

    private void adjustWriteTypeIfNeeded(BluetoothGattDescriptor descriptor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // Up to Android 6 there is a bug where Android takes the writeType of the parent characteristic instead of always WRITE_TYPE_DEFAULT
            // See: https://android.googlesource.com/platform/frameworks/base/+/942aebc95924ab1e7ea1e92aaf4e7fc45f695a6c%5E%21/#F0
            final BluetoothGattCharacteristic parentCharacteristic = descriptor.getCharacteristic();
            parentCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        }
    }

    /**
     * Asynchronous method to clear the services cache. Make sure to add a delay when using this!
     *
     * @return true if the method was executed, false if not executed
     */
    public boolean clearServicesCache() {
        if (bluetoothGatt == null) return false;

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
     * <p>{@link BluetoothPeripheralCallback#onReadRemoteRssi(BluetoothPeripheral, int, GattStatus)} will be triggered as a result of this call.
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
            Timber.e("could not enqueue readRemoteRssi command");
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
     * <p>{@link BluetoothPeripheralCallback#onMtuChanged(BluetoothPeripheral, int, GattStatus)} will be triggered as a result of this call.
     *
     * @param mtu the desired MTU size
     * @return true if the operation was enqueued, false otherwise
     */
    public boolean requestMtu(final int mtu) {
        // Make sure mtu is valid
        if (mtu < DEFAULT_MTU || mtu > MAX_MTU) {
            throw new IllegalArgumentException("mtu must be between 23 and 517");
        }

        if (!isConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTECTED);
            return false;
        }

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
            Timber.e("could not enqueue requestMtu command");
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
        // Make sure priority is valid
        if (priority < CONNECTION_PRIORITY_BALANCED || priority > CONNECTION_PRIORITY_LOW_POWER) {
            throw new IllegalArgumentException("connection priority not valid");
        }

        if (!isConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTECTED);
            return false;
        }

        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    if (!bluetoothGatt.requestConnectionPriority(priority)) {
                        Timber.e("could not request connection priority");
                    } else {
                        Timber.d("requesting connection priority %d", priority);
                    }
                    // complete command immediately as there is no callback for it
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
    @NotNull
    private static String bytes2String(@Nullable final byte[] bytes) {
        if (bytes == null) return "";
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
        void connected(@NotNull BluetoothPeripheral device);

        /**
         * Connecting with {@link BluetoothPeripheral} has failed.
         *
         * @param device {@link BluetoothPeripheral} of which connect failed.
         */
        void connectFailed(@NotNull BluetoothPeripheral device, final HciStatus status);

        /**
         * {@link BluetoothPeripheral} has disconnected.
         *
         * @param device {@link BluetoothPeripheral} that disconnected.
         */
        void disconnected(@NotNull BluetoothPeripheral device, final HciStatus status);

        String getPincode(@NotNull BluetoothPeripheral device);

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

                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        bluetoothGattCallback.onConnectionStateChange(bluetoothGatt, HciStatus.CONNECTION_FAILED_ESTABLISHMENT.getValue(), BluetoothProfile.STATE_DISCONNECTED);
                    }
                }, 50);

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

    /**
     * Make a safe copy of a nullable byte array
     *
     * @param source byte array to copy
     * @return non-null copy of the source byte array or an empty array if source was null
     */
    @NotNull
    byte[] copyOf(@Nullable byte[] source) {
        return (source == null) ? new byte[0] : Arrays.copyOf(source, source.length);
    }

    /**
     * Make a byte array nonnull by either returning the original byte array if non-null or an empty bytearray
     *
     * @param source byte array to make nonnull
     * @return the source byte array or an empty array if source was null
     */
    @NotNull
    byte[] nonnullOf(@Nullable byte[] source) {
        return (source == null) ? new byte[0] : source;
    }
}
