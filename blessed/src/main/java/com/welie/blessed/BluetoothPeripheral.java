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
import java.util.Locale;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static com.welie.blessed.BluetoothBytesParser.bytes2String;

/**
 * Represents a remote Bluetooth peripheral and replaces BluetoothDevice and BluetoothGatt
 *
 * <p>A {@link BluetoothPeripheral} lets you create a connection with the peripheral or query information about it.
 * This class is a wrapper around the {@link BluetoothDevice} and takes care of operation queueing, some Android bugs, and provides several convenience functions.
 */
@SuppressWarnings({"SpellCheckingInspection", "unused", "UnusedReturnValue"})
public class BluetoothPeripheral {

    private static final String TAG = BluetoothPeripheral.class.getSimpleName();
    private static final UUID CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /**
     * Max MTU that Android can handle
     */
    public static final int MAX_MTU = 517;

    // Minimal and default MTU
    private static final int DEFAULT_MTU = 23;

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

    // The average time it takes to complete requestConnectionPriority
    private static final long AVG_REQUEST_CONNECTION_PRIORITY_DURATION = 500;

    private static final String NO_VALID_SERVICE_UUID_PROVIDED = "no valid service UUID provided";
    private static final String NO_VALID_CHARACTERISTIC_UUID_PROVIDED = "no valid characteristic UUID provided";
    private static final String NO_VALID_CHARACTERISTIC_PROVIDED = "no valid characteristic provided";
    private static final String NO_VALID_WRITE_TYPE_PROVIDED = "no valid writeType provided";
    private static final String NO_VALID_VALUE_PROVIDED = "no valid value provided";
    private static final String NO_VALID_DESCRIPTOR_PROVIDED = "no valid descriptor provided";
    private static final String NO_VALID_PERIPHERAL_CALLBACK_PROVIDED = "no valid peripheral callback provided";
    private static final String NO_VALID_DEVICE_PROVIDED = "no valid device provided";
    private static final String NO_VALID_PRIORITY_PROVIDED = "no valid priority provided";
    private static final String PERIPHERAL_NOT_CONNECTED = "peripheral not connected";
    private static final String VALUE_BYTE_ARRAY_IS_EMPTY = "value byte array is empty";
    private static final String VALUE_BYTE_ARRAY_IS_TOO_LONG = "value byte array is too long";

    // String constants for commands where the callbacks can also happen because the remote peripheral initiated the command
    private static final int IDLE = 0;
    private static final int REQUEST_MTU_COMMAND = 1;
    private static final int SET_PHY_TYPE_COMMAND = 2;

    private @NotNull final Context context;
    private @NotNull final Handler callbackHandler;
    private @NotNull BluetoothDevice device;
    private @NotNull final InternalCallback listener;
    protected @NotNull BluetoothPeripheralCallback peripheralCallback;
    private @NotNull final Queue<Runnable> commandQueue = new ConcurrentLinkedQueue<>();
    private @Nullable volatile BluetoothGatt bluetoothGatt;
    private @NotNull String cachedName = "";
    private @NotNull byte[] currentWriteBytes = new byte[0];
    private int currentCommand = IDLE;
    private @NotNull final Set<BluetoothGattCharacteristic> notifyingCharacteristics = new HashSet<>();
    private @NotNull final Handler mainHandler = new Handler(Looper.getMainLooper());
    private @Nullable Runnable timeoutRunnable;
    private @Nullable Runnable discoverServicesRunnable;

    private volatile boolean commandQueueBusy = false;
    private boolean isRetrying;
    private boolean bondLost = false;
    private boolean manuallyBonding = false;
    private volatile boolean peripheralInitiatedBonding = false;
    private boolean discoveryStarted = false;
    private volatile int state = BluetoothProfile.STATE_DISCONNECTED;
    private int nrTries;
    private long connectTimestamp;
    private int currentMtu = DEFAULT_MTU;
    private Transport transport;

    /**
     * This abstract class is used to implement BluetoothGatt callbacks.
     */
    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(@NotNull final BluetoothGatt gatt, final int status, final int newState) {
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
                        Logger.i(TAG,"peripheral '%s' is disconnecting", getAddress());
                        listener.disconnecting(BluetoothPeripheral.this);
                        break;
                    case BluetoothProfile.STATE_CONNECTING:
                        Logger.i(TAG,"peripheral '%s' is connecting", getAddress());
                        listener.connecting(BluetoothPeripheral.this);
                        break;
                    default:
                        Logger.e(TAG,"unknown state received");
                        break;
                }
            } else {
                connectionStateChangeUnsuccessful(hciStatus, previousState, newState);
            }
        }

        @Override
        public void onServicesDiscovered(@NotNull final BluetoothGatt gatt, final int status) {
            final GattStatus gattStatus = GattStatus.fromValue(status);
            if (gattStatus != GattStatus.SUCCESS) {
                Logger.e(TAG,"service discovery failed due to internal error '%s', disconnecting", gattStatus);
                disconnect();
                return;
            }

            final List<BluetoothGattService> services = gatt.getServices();
            Logger.i(TAG,"discovered %d services for '%s'", services.size(), getName());

            // Issue 'connected' since we are now fully connect incl service discovery
            listener.connected(BluetoothPeripheral.this);

            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onServicesDiscovered(BluetoothPeripheral.this);
                }
            });
        }

        @Override
        public void onDescriptorWrite(@NotNull final BluetoothGatt gatt, @NotNull final BluetoothGattDescriptor descriptor, final int status) {
            final GattStatus gattStatus = GattStatus.fromValue(status);
            final BluetoothGattCharacteristic parentCharacteristic = descriptor.getCharacteristic();
            if (gattStatus != GattStatus.SUCCESS) {
                Logger.e(TAG,"failed to write <%s> to descriptor of characteristic <%s> for device: '%s', status '%s' ", bytes2String(currentWriteBytes), parentCharacteristic.getUuid(), getAddress(), gattStatus);
                if (failureThatShouldTriggerBonding(gattStatus)) return;
            }

            // Check if this was the Client Characteristic Configuration Descriptor
            if (descriptor.getUuid().equals(CCC_DESCRIPTOR_UUID)) {
                if (gattStatus == GattStatus.SUCCESS) {
                    final byte[] value = nonnullOf(descriptor.getValue());
                    if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                            Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
                        notifyingCharacteristics.add(parentCharacteristic);
                    } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                        notifyingCharacteristics.remove(parentCharacteristic);
                    }
                }

                callbackHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        peripheralCallback.onNotificationStateUpdate(BluetoothPeripheral.this, parentCharacteristic, gattStatus);
                    }
                });
            } else {
                callbackHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        peripheralCallback.onDescriptorWrite(BluetoothPeripheral.this, currentWriteBytes, descriptor, gattStatus);
                    }
                });
            }
            completedCommand();
        }

        @Override
        public void onDescriptorRead(@NotNull final BluetoothGatt gatt, @NotNull final BluetoothGattDescriptor descriptor, final int status) {
            final GattStatus gattStatus = GattStatus.fromValue(status);
            if (gattStatus != GattStatus.SUCCESS) {
                Logger.e(TAG,"reading descriptor <%s> failed for device '%s, status '%s'", descriptor.getUuid(), getAddress(), gattStatus);
                if (failureThatShouldTriggerBonding(gattStatus)) return;
            }

            final byte[] value = nonnullOf(descriptor.getValue());
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onDescriptorRead(BluetoothPeripheral.this, value, descriptor, gattStatus);
                }
            });
            completedCommand();
        }

        @Override
        public void onCharacteristicChanged(@NotNull final BluetoothGatt gatt, @NotNull final BluetoothGattCharacteristic characteristic) {
            final byte[] value = nonnullOf(characteristic.getValue());
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onCharacteristicUpdate(BluetoothPeripheral.this, value, characteristic, GattStatus.SUCCESS);
                }
            });
        }

        @Override
        public void onCharacteristicRead(@NotNull final BluetoothGatt gatt, @NotNull final BluetoothGattCharacteristic characteristic, final int status) {
            final GattStatus gattStatus = GattStatus.fromValue(status);
            if (gattStatus != GattStatus.SUCCESS) {
                Logger.e(TAG,"read failed for characteristic <%s>, status '%s'", characteristic.getUuid(), gattStatus);
                if (failureThatShouldTriggerBonding(gattStatus)) return;
            }

            final byte[] value = nonnullOf(characteristic.getValue());
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onCharacteristicUpdate(BluetoothPeripheral.this, value, characteristic, gattStatus);
                }
            });
            completedCommand();
        }

        @Override
        public void onCharacteristicWrite(@NotNull final BluetoothGatt gatt, @NotNull final BluetoothGattCharacteristic characteristic, final int status) {
            final GattStatus gattStatus = GattStatus.fromValue(status);
            if (gattStatus != GattStatus.SUCCESS) {
                Logger.e(TAG,"writing <%s> to characteristic <%s> failed, status '%s'", bytes2String(currentWriteBytes), characteristic.getUuid(), gattStatus);
                if (failureThatShouldTriggerBonding(gattStatus)) return;
            }

            final byte[] value = currentWriteBytes;
            currentWriteBytes = new byte[0];
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onCharacteristicWrite(BluetoothPeripheral.this, value, characteristic, gattStatus);
                }
            });
            completedCommand();
        }

        private boolean failureThatShouldTriggerBonding(@NotNull final GattStatus gattStatus) {
            if (gattStatus == GattStatus.AUTHORIZATION_FAILED
                    || gattStatus == GattStatus.INSUFFICIENT_AUTHENTICATION
                    || gattStatus == GattStatus.INSUFFICIENT_ENCRYPTION) {
                // Characteristic/descriptor is encrypted and needs bonding, bonding should be in progress already
                // Operation must be retried after bonding is completed.
                // This only seems to happen on Android 5/6/7.
                // On newer versions Android will do retry internally
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    Logger.i(TAG,"operation will be retried after bonding, bonding should be in progress");
                    return true;
                }
            }
            return false;
        }

        @Override
        public void onReadRemoteRssi(@NotNull final BluetoothGatt gatt, final int rssi, final int status) {
            final GattStatus gattStatus = GattStatus.fromValue(status);
            if (gattStatus != GattStatus.SUCCESS) {
                Logger.e(TAG,"reading RSSI failed, status '%s'", gattStatus);
            }

            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onReadRemoteRssi(BluetoothPeripheral.this, rssi, gattStatus);
                }
            });
            completedCommand();
        }

        @Override
        public void onMtuChanged(@NotNull final BluetoothGatt gatt, final int mtu, final int status) {
            final GattStatus gattStatus = GattStatus.fromValue(status);
            if (gattStatus != GattStatus.SUCCESS) {
                Logger.e(TAG,"change MTU failed, status '%s'", gattStatus);
            }

            currentMtu = mtu;
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onMtuChanged(BluetoothPeripheral.this, mtu, gattStatus);
                }
            });

            // Only complete the command if we initiated the operation. It can also be initiated by the remote peripheral...
            if (currentCommand == REQUEST_MTU_COMMAND) {
                currentCommand = IDLE;
                completedCommand();
            }
        }

        @Override
        public void onPhyRead(@NotNull final BluetoothGatt gatt, final int txPhy, final int rxPhy, final int status) {
            final GattStatus gattStatus = GattStatus.fromValue(status);
            if (gattStatus != GattStatus.SUCCESS) {
                Logger.e(TAG,"read Phy failed, status '%s'", gattStatus);
            } else {
                Logger.i(TAG,"updated Phy: tx = %s, rx = %s", PhyType.fromValue(txPhy), PhyType.fromValue(rxPhy));
            }

            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onPhyUpdate(BluetoothPeripheral.this, PhyType.fromValue(txPhy), PhyType.fromValue(rxPhy), gattStatus);
                }
            });
            completedCommand();
        }

        @Override
        public void onPhyUpdate(@NotNull final BluetoothGatt gatt, final int txPhy, final int rxPhy, final int status) {
            final GattStatus gattStatus = GattStatus.fromValue(status);
            if (gattStatus != GattStatus.SUCCESS) {
                Logger.e(TAG,"update Phy failed, status '%s'", gattStatus);
            } else {
                Logger.i(TAG,"updated Phy: tx = %s, rx = %s", PhyType.fromValue(txPhy), PhyType.fromValue(rxPhy));
            }

            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onPhyUpdate(BluetoothPeripheral.this, PhyType.fromValue(txPhy), PhyType.fromValue(rxPhy), gattStatus);
                }
            });

            // Only complete the command if we initiated the operation. It can also be initiated by the remote peripheral...
            if (currentCommand == SET_PHY_TYPE_COMMAND) {
                currentCommand = IDLE;
                completedCommand();
            }
        }

        /**
         * This callback is only called from Android 8 (Oreo) or higher. Not all phones seem to call this though...
         */
        public void onConnectionUpdated(@NotNull final BluetoothGatt gatt, final int interval, final int latency, final int timeout, final int status) {
            final GattStatus gattStatus = GattStatus.fromValue(status);
            if (gattStatus == GattStatus.SUCCESS) {
                String msg = String.format(Locale.ENGLISH, "connection parameters: interval=%.1fms latency=%d timeout=%ds", interval * 1.25f, latency, timeout / 100);
                Logger.d(TAG,msg);
            } else {
                Logger.e(TAG,"connection parameters update failed with status '%s'", gattStatus);
            }

            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onConnectionUpdated(BluetoothPeripheral.this, interval, latency, timeout, gattStatus);
                }
            });
        }
    };

    private void successfullyConnected() {
        final BondState bondstate = getBondState();
        final long timePassed = SystemClock.elapsedRealtime() - connectTimestamp;
        Logger.i(TAG,"connected to '%s' (%s) in %.1fs", getName(), bondstate, timePassed / 1000.0f);

        if (bondstate == BondState.NONE || bondstate == BondState.BONDED) {
            delayedDiscoverServices(getServiceDiscoveryDelay(bondstate));
        } else if (bondstate == BondState.BONDING) {
            // Apparently the bonding process has already started, so let it complete. We'll do discoverServices once bonding finished
            Logger.i(TAG,"waiting for bonding to complete");
        }
    }

    private void delayedDiscoverServices(final long delay) {
        discoverServicesRunnable = new Runnable() {
            @Override
            public void run() {
                Logger.d(TAG,"discovering services of '%s' with delay of %d ms", getName(), delay);
                if (bluetoothGatt != null && bluetoothGatt.discoverServices()) {
                    discoveryStarted = true;
                } else {
                    Logger.e(TAG,"discoverServices failed to start");
                }
                discoverServicesRunnable = null;
            }
        };
        mainHandler.postDelayed(discoverServicesRunnable, delay);
    }

    private long getServiceDiscoveryDelay(@NotNull BondState bondstate) {
        long delayWhenBonded = 0;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            // It seems delays when bonded are only needed in versions Nougat or lower
            // This issue was observed on a Nexus 5 (M) and Sony Xperia L1 (N) when connecting to a A&D UA-651BLE
            // The delay is needed when devices have the Service Changed Characteristic.
            // If they don't have it the delay isn't needed but we do it anyway to keep code simple
            delayWhenBonded = 1000L;
        }
        return bondstate == BondState.BONDED ? delayWhenBonded : 0;
    }

    private void successfullyDisconnected(final int previousState) {
        if (previousState == BluetoothProfile.STATE_CONNECTED || previousState == BluetoothProfile.STATE_DISCONNECTING) {
            Logger.i(TAG,"disconnected '%s' on request", getName());
        } else if (previousState == BluetoothProfile.STATE_CONNECTING) {
            Logger.i(TAG,"cancelling connect attempt");
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

    private void connectionStateChangeUnsuccessful(@NotNull final HciStatus status, final int previousState, final int newState) {
        cancelPendingServiceDiscovery();
        final boolean servicesDiscovered = !getServices().isEmpty();

        // See if the initial connection failed
        if (previousState == BluetoothProfile.STATE_CONNECTING) {
            final long timePassed = SystemClock.elapsedRealtime() - connectTimestamp;
            final boolean isTimeout = timePassed > getTimoutThreshold();
            final HciStatus adjustedStatus = (status == HciStatus.ERROR && isTimeout) ? HciStatus.CONNECTION_FAILED_ESTABLISHMENT : status;
            Logger.i(TAG,"connection failed with status '%s'", adjustedStatus);
            completeDisconnect(false, adjustedStatus);
            listener.connectFailed(BluetoothPeripheral.this, adjustedStatus);
        } else if (previousState == BluetoothProfile.STATE_CONNECTED && newState == BluetoothProfile.STATE_DISCONNECTED && !servicesDiscovered) {
            // We got a disconnection before the services were even discovered
            Logger.i(TAG,"peripheral '%s' disconnected with status '%s' (%d) before completing service discovery", getName(), status, status.value);
            completeDisconnect(false, status);
            listener.connectFailed(BluetoothPeripheral.this, status);
        } else {
            // See if we got connection drop
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Logger.i(TAG,"peripheral '%s' disconnected with status '%s' (%d)", getName(), status, status.value);
            } else {
                Logger.i(TAG,"unexpected connection state change for '%s' status '%s' (%d)", getName(), status, status.value);
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

    private void handleBondStateChange(final int bondState, final int previousBondState) {
        switch (bondState) {
            case BOND_BONDING:
                Logger.d(TAG,"starting bonding with '%s' (%s)", getName(), getAddress());
                callbackHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        peripheralCallback.onBondingStarted(BluetoothPeripheral.this);
                    }
                });
                break;
            case BOND_BONDED:
                Logger.d(TAG,"bonded with '%s' (%s)", getName(), getAddress());
                callbackHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        peripheralCallback.onBondingSucceeded(BluetoothPeripheral.this);
                    }
                });

                // If bonding was started at connection time, we may still have to discover the services
                // Also make sure we are not starting a discovery while another one is already in progress
                if (getServices().isEmpty() && !discoveryStarted) {
                    delayedDiscoverServices(0);
                }

                // If bonding was triggered by a read/write, we must retry it
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    if (commandQueueBusy && !manuallyBonding) {
                        mainHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Logger.d(TAG,"retrying command after bonding");
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

                // If the peripheral initated the bonding, continue the queue
                if (peripheralInitiatedBonding) {
                    peripheralInitiatedBonding = false;
                    nextCommand();
                }

                break;
            case BOND_NONE:
                if (previousBondState == BOND_BONDING) {
                    Logger.e(TAG,"bonding failed for '%s', disconnecting device", getName());
                    callbackHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            peripheralCallback.onBondingFailed(BluetoothPeripheral.this);
                        }
                    });
                } else {
                    Logger.e(TAG,"bond lost for '%s'", getName());
                    bondLost = true;

                    // Cancel the discoverServiceRunnable if it is still pending
                    cancelPendingServiceDiscovery();

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
            final BluetoothDevice receivedDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (receivedDevice == null) return;

            // Skip other devices
            if (!receivedDevice.getAddress().equalsIgnoreCase(getAddress())) return;

            final int variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR);
            Logger.d(TAG,"pairing request received: " + pairingVariantToString(variant) + " (" + variant + ")");

            if (variant == PAIRING_VARIANT_PIN) {
                final String pin = listener.getPincode(BluetoothPeripheral.this);
                if (pin != null) {
                    Logger.d(TAG,"setting PIN code for this peripheral using '%s'", pin);
                    receivedDevice.setPin(pin.getBytes());
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
     * @param listener Callback to {@link BluetoothCentralManager}.
     * @param transport Transport to be used during connection phase.
     */
    BluetoothPeripheral(@NotNull final Context context, @NotNull final BluetoothDevice device, @NotNull final InternalCallback listener, @NotNull final BluetoothPeripheralCallback peripheralCallback, @NotNull final Handler callbackHandler, @NotNull final Transport transport) {
        this.context = Objects.requireNonNull(context, "no valid context provided");
        this.device = Objects.requireNonNull(device, NO_VALID_DEVICE_PROVIDED);
        this.listener = Objects.requireNonNull(listener, "no valid listener provided");
        this.peripheralCallback = Objects.requireNonNull(peripheralCallback, NO_VALID_PERIPHERAL_CALLBACK_PROVIDED);
        this.callbackHandler = Objects.requireNonNull(callbackHandler, "no valid callback handler provided");
        this.transport =  Objects.requireNonNull(transport, "no valid transport provided");
    }

    void setPeripheralCallback(@NotNull final BluetoothPeripheralCallback peripheralCallback) {
        this.peripheralCallback = Objects.requireNonNull(peripheralCallback, NO_VALID_PERIPHERAL_CALLBACK_PROVIDED);
    }

    void setDevice(@NotNull final BluetoothDevice bluetoothDevice) {
        this.device = Objects.requireNonNull(bluetoothDevice, NO_VALID_DEVICE_PROVIDED);
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
                    Logger.i(TAG,"connect to '%s' (%s) using transport %s", getName(), getAddress(), transport.name());
                    registerBondingBroadcastReceivers();
                    discoveryStarted = false;
                    bluetoothGatt = connectGattHelper(device, false, bluetoothGattCallback);
                    bluetoothGattCallback.onConnectionStateChange(bluetoothGatt, HciStatus.SUCCESS.value, BluetoothProfile.STATE_CONNECTING);
                    connectTimestamp = SystemClock.elapsedRealtime();
                    startConnectionTimer(BluetoothPeripheral.this);
                }
            }, DIRECT_CONNECTION_DELAY_IN_MS);
        } else {
            Logger.e(TAG,"peripheral '%s' not yet disconnected, will not connect", getName());
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
                    Logger.i(TAG,"autoConnect to '%s' (%s) using transport %s", getName(), getAddress(), transport.name());
                    registerBondingBroadcastReceivers();
                    discoveryStarted = false;
                    bluetoothGatt = connectGattHelper(device, true, bluetoothGattCallback);
                    bluetoothGattCallback.onConnectionStateChange(bluetoothGatt, HciStatus.SUCCESS.value, BluetoothProfile.STATE_CONNECTING);
                    connectTimestamp = SystemClock.elapsedRealtime();
                }
            });
        } else {
            Logger.e(TAG,"peripheral '%s' not yet disconnected, will not connect", getName());
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
        final boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                manuallyBonding = true;
                if (!device.createBond()) {
                    Logger.e(TAG,"bonding failed for %s", getAddress());
                    completedCommand();
                } else {
                    Logger.d(TAG,"manually bonding %s", getAddress());
                    nrTries++;
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Logger.e(TAG,"could not enqueue bonding command");
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
            Logger.w(TAG,"cannot cancel connection because no connection attempt is made yet");
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
                    bluetoothGattCallback.onConnectionStateChange(bluetoothGatt, HciStatus.SUCCESS.value, BluetoothProfile.STATE_DISCONNECTED);
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
     * <p>When the disconnection has been completed {@link BluetoothCentralManagerCallback#onDisconnectedPeripheral(BluetoothPeripheral, HciStatus)} will be called.
     */
    private void disconnect() {
        if (state == BluetoothProfile.STATE_CONNECTED || state == BluetoothProfile.STATE_CONNECTING) {
            bluetoothGattCallback.onConnectionStateChange(bluetoothGatt, HciStatus.SUCCESS.value, BluetoothProfile.STATE_DISCONNECTING);
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (state == BluetoothProfile.STATE_DISCONNECTING && bluetoothGatt != null) {
                        bluetoothGatt.disconnect();
                        Logger.i(TAG,"force disconnect '%s' (%s)", getName(), getAddress());
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
    private void completeDisconnect(final boolean notify, @NotNull final HciStatus status) {
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        commandQueue.clear();
        commandQueueBusy = false;
        notifyingCharacteristics.clear();
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
    @NotNull
    public String getAddress() {
        return device.getAddress();
    }

    /**
     * Get the type of the peripheral.
     *
     * @return the PeripheralType
     */
    @NotNull
    public PeripheralType getType() {
        return PeripheralType.fromValue(device.getType());
    }

    /**
     * Get the name of the bluetooth peripheral.
     *
     * @return name of the bluetooth peripheral
     */
    @NotNull
    public String getName() {
        final String name = device.getName();
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
     * @return the bond state
     */
    @NotNull
    public BondState getBondState() {
        return BondState.fromValue(device.getBondState());
    }

    /**
     * Get the services supported by the connected bluetooth peripheral.
     * Only services that are also supported by {@link BluetoothCentralManager} are included.
     *
     * @return Supported services.
     */
    @SuppressWarnings("WeakerAccess")
    @NotNull
    public List<BluetoothGattService> getServices() {
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
    @Nullable
    public BluetoothGattService getService(@NotNull final UUID serviceUUID) {
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
    @Nullable
    public BluetoothGattCharacteristic getCharacteristic(@NotNull final UUID serviceUUID, @NotNull final UUID characteristicUUID) {
        Objects.requireNonNull(serviceUUID, NO_VALID_SERVICE_UUID_PROVIDED);
        Objects.requireNonNull(characteristicUUID, NO_VALID_CHARACTERISTIC_UUID_PROVIDED);

        final BluetoothGattService service = getService(serviceUUID);
        if (service != null) {
            return service.getCharacteristic(characteristicUUID);
        } else {
            return null;
        }
    }

    /**
     * Returns the connection state of the peripheral.
     *
     * @return the connection state.
     */
    @NotNull
    public ConnectionState getState() {
        return ConnectionState.fromValue(state);
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

    /**
     * Returns the transport used during connection phase.
     * @return the Transport.
     */
    public Transport getTransport() {
        return transport;
    }

    /**
     * Boolean to indicate if the specified characteristic is currently notifying or indicating.
     *
     * @param characteristic the characteristic to check
     * @return true if the characteristic is notifying or indicating, false if it is not
     */
    public boolean isNotifying(@NotNull final BluetoothGattCharacteristic characteristic) {
        Objects.requireNonNull(characteristic, NO_VALID_CHARACTERISTIC_PROVIDED);
        return notifyingCharacteristics.contains(characteristic);
    }

    /**
     * Get all notifying/indicating characteristics
     *
     * @return Set of characteristics or empty set
     */
    public @NotNull Set<BluetoothGattCharacteristic> getNotifyingCharacteristics() {
        return Collections.unmodifiableSet(notifyingCharacteristics);
    }

    private boolean isConnected() {
        return bluetoothGatt != null && state == BluetoothProfile.STATE_CONNECTED;
    }

    private boolean notConnected() {
        return !isConnected();
    }

    /**
     * Check if the peripheral is uncached by the Android BLE stack
     *
     * @return true if unchached, otherwise false
     */
    public boolean isUncached() {
        return getType() == PeripheralType.UNKNOWN;
    }

    /**
     * Read the value of a characteristic.
     *
     * Convenience function to read a characteristic without first having to find it.
     *
     * @param serviceUUID        the service UUID the characteristic belongs to
     * @param characteristicUUID the characteristic's UUID
     * @return true if the operation was enqueued, false if the characteristic was not found
     * @throws IllegalArgumentException if the characteristic does not support reading
     */
    public boolean readCharacteristic(@NotNull final UUID serviceUUID, @NotNull final UUID characteristicUUID) {
        Objects.requireNonNull(serviceUUID, NO_VALID_SERVICE_UUID_PROVIDED);
        Objects.requireNonNull(characteristicUUID, NO_VALID_CHARACTERISTIC_UUID_PROVIDED);

        BluetoothGattCharacteristic characteristic = getCharacteristic(serviceUUID, characteristicUUID);
        if (characteristic != null) {
            return readCharacteristic(characteristic);
        }
        return false;
    }

    /**
     * Read the value of a characteristic.
     *
     * <p>{@link BluetoothPeripheralCallback#onCharacteristicUpdate(BluetoothPeripheral, byte[], BluetoothGattCharacteristic, GattStatus)}   will be triggered as a result of this call.
     *
     * @param characteristic Specifies the characteristic to read.
     * @return true if the operation was enqueued, otherwise false
     * @throws IllegalArgumentException if the characteristic does not support reading
     */
    public boolean readCharacteristic(@NotNull final BluetoothGattCharacteristic characteristic) {
        Objects.requireNonNull(characteristic, NO_VALID_CHARACTERISTIC_PROVIDED);

        if (notConnected()) {
            Logger.e(TAG,PERIPHERAL_NOT_CONNECTED);
            return false;
        }

        if (doesNotSupportReading(characteristic)) {
            String message = String.format("characteristic <%s> does not have read property", characteristic.getUuid());
            throw new IllegalArgumentException(message);
        }

        final boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    if (bluetoothGatt.readCharacteristic(characteristic)) {
                        Logger.d(TAG,"reading characteristic <%s>", characteristic.getUuid());
                        nrTries++;
                    } else {
                        Logger.e(TAG,"readCharacteristic failed for characteristic: %s", characteristic.getUuid());
                        completedCommand();
                    }
                } else {
                    completedCommand();
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Logger.e(TAG,"could not enqueue read characteristic command");
        }
        return result;
    }

    private boolean doesNotSupportReading(@NotNull final BluetoothGattCharacteristic characteristic) {
        return (characteristic.getProperties() & PROPERTY_READ) == 0;
    }

    /**
     * Write a value to a characteristic using the specified write type.
     *
     * Convenience function to write a characteristic without first having to find it.
     * All parameters must have a valid value in order for the operation to be enqueued.
     *
     * @param serviceUUID        the service UUID the characteristic belongs to
     * @param characteristicUUID the characteristic's UUID
     * @param value              the byte array to write
     * @param writeType          the write type to use when writing.
     * @return true if the operation was enqueued, otherwise false
     * @throws IllegalArgumentException if the characteristic does not support writing with the specified writeType or the byte array is empty or too long
     */
    public boolean writeCharacteristic(@NotNull final UUID serviceUUID, @NotNull final UUID characteristicUUID, @NotNull final byte[] value, @NotNull final WriteType writeType) {
        Objects.requireNonNull(serviceUUID, NO_VALID_SERVICE_UUID_PROVIDED);
        Objects.requireNonNull(characteristicUUID, NO_VALID_CHARACTERISTIC_UUID_PROVIDED);
        Objects.requireNonNull(value, NO_VALID_VALUE_PROVIDED);
        Objects.requireNonNull(writeType, NO_VALID_WRITE_TYPE_PROVIDED);

        BluetoothGattCharacteristic characteristic = getCharacteristic(serviceUUID, characteristicUUID);
        if (characteristic != null) {
            return writeCharacteristic(characteristic, value, writeType);
        }
        return false;
    }

    /**
     * Write a value to a characteristic using the specified write type.
     *
     * <p>All parameters must have a valid value in order for the operation to be enqueued.
     * The length of the byte array to write must be between 1 and getMaximumWriteValueLength(writeType).
     *
     * <p>{@link BluetoothPeripheralCallback#onCharacteristicWrite(BluetoothPeripheral, byte[], BluetoothGattCharacteristic, GattStatus)} will be triggered as a result of this call.
     *
     * @param characteristic the characteristic to write to
     * @param value          the byte array to write
     * @param writeType      the write type to use when writing.
     * @return true if a write operation was succesfully enqueued, otherwise false
     * @throws IllegalArgumentException if the characteristic does not support writing with the specified writeType or the byte array is empty or too long
     */
    public boolean writeCharacteristic(@NotNull final BluetoothGattCharacteristic characteristic, @NotNull final byte[] value, @NotNull final WriteType writeType) {
        Objects.requireNonNull(characteristic, NO_VALID_CHARACTERISTIC_PROVIDED);
        Objects.requireNonNull(value, NO_VALID_VALUE_PROVIDED);
        Objects.requireNonNull(writeType, NO_VALID_WRITE_TYPE_PROVIDED);

        if (notConnected()) {
            Logger.e(TAG,PERIPHERAL_NOT_CONNECTED);
            return false;
        }

        if (value.length == 0) {
            throw new IllegalArgumentException(VALUE_BYTE_ARRAY_IS_EMPTY);
        }

        if (value.length > getMaximumWriteValueLength(writeType)) {
            throw new IllegalArgumentException(VALUE_BYTE_ARRAY_IS_TOO_LONG);
        }

        if (doesNotSupportWriteType(characteristic, writeType)) {
            String message = String.format("characteristic <%s> does not support writeType '%s'", characteristic.getUuid(), writeType);
            throw new IllegalArgumentException(message);
        }

        // Copy the value to avoid race conditions
        final byte[] bytesToWrite = copyOf(value);

        final boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    currentWriteBytes = bytesToWrite;
                    characteristic.setWriteType(writeType.writeType);

                    if (willCauseLongWrite(bytesToWrite, writeType)) {
                        // Android will turn this into a Long Write because it is larger than the MTU - 3.
                        // When doing a Long Write the byte array will be automatically split in chunks of size MTU - 3.
                        // However, the peripheral's firmware must also support it, so it is not guaranteed to work.
                        // Long writes are also very inefficient because of the confirmation of each write operation.
                        // So it is better to increase MTU if possible. Hence a warning if this write becomes a long write...
                        // See https://stackoverflow.com/questions/48216517/rxandroidble-write-only-sends-the-first-20b
                        Logger.w(TAG,"value byte array is longer than allowed by MTU, write will fail if peripheral does not support long writes");
                    }
                    characteristic.setValue(bytesToWrite);
                    if (bluetoothGatt.writeCharacteristic(characteristic)) {
                        Logger.d(TAG,"writing <%s> to characteristic <%s>", bytes2String(bytesToWrite), characteristic.getUuid());
                        nrTries++;
                    } else {
                        Logger.e(TAG,"writeCharacteristic failed for characteristic: %s", characteristic.getUuid());
                        completedCommand();
                    }
                } else {
                    completedCommand();
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Logger.e(TAG,"could not enqueue write characteristic command");
        }
        return result;
    }

    private boolean willCauseLongWrite(@NotNull final byte[] value, @NotNull final WriteType writeType) {
        return value.length > currentMtu - 3 && writeType == WriteType.WITH_RESPONSE;
    }

    private boolean doesNotSupportWriteType(@NotNull final BluetoothGattCharacteristic characteristic, @NotNull final WriteType writeType) {
        return (characteristic.getProperties() & writeType.property) == 0;
    }

    /**
     * Read the value of a descriptor.
     *
     * @param descriptor the descriptor to read
     * @return true if a write operation was succesfully enqueued, otherwise false
     */
    public boolean readDescriptor(@NotNull final BluetoothGattDescriptor descriptor) {
        Objects.requireNonNull(descriptor, NO_VALID_DESCRIPTOR_PROVIDED);

        if (notConnected()) {
            Logger.e(TAG,PERIPHERAL_NOT_CONNECTED);
            return false;
        }

        final boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    if (bluetoothGatt.readDescriptor(descriptor)) {
                        Logger.d(TAG,"reading descriptor <%s>", descriptor.getUuid());
                        nrTries++;
                    } else {
                        Logger.e(TAG,"readDescriptor failed for characteristic: %s", descriptor.getUuid());
                        completedCommand();
                    }
                } else {
                    completedCommand();
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Logger.e(TAG,"could not enqueue read descriptor command");
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

        if (notConnected()) {
            Logger.e(TAG,PERIPHERAL_NOT_CONNECTED);
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

        final boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    currentWriteBytes = bytesToWrite;
                    descriptor.setValue(bytesToWrite);
                    if (bluetoothGatt.writeDescriptor(descriptor)) {
                        Logger.d(TAG,"writing <%s> to descriptor <%s>", bytes2String(bytesToWrite), descriptor.getUuid());
                        nrTries++;
                    } else {
                        Logger.e(TAG,"writeDescriptor failed for descriptor: %s", descriptor.getUuid());
                        completedCommand();
                    }
                } else {
                    completedCommand();
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Logger.e(TAG,"could not enqueue write descriptor command");
        }
        return result;
    }

    /**
     * Set the notification state of a characteristic to 'on' or 'off'. The characteristic must support notifications or indications.
     *
     * @param serviceUUID        the service UUID the characteristic belongs to
     * @param characteristicUUID the characteristic's UUID
     * @param enable             true for setting notification on, false for turning it off
     * @return true if the operation was enqueued, otherwise false
     * @throws IllegalArgumentException if the CCC descriptor was not found or the characteristic does not support notifications or indications
     */
    public boolean setNotify(@NotNull final UUID serviceUUID, @NotNull final UUID characteristicUUID, final boolean enable) {
        Objects.requireNonNull(serviceUUID, NO_VALID_SERVICE_UUID_PROVIDED);
        Objects.requireNonNull(characteristicUUID, NO_VALID_CHARACTERISTIC_UUID_PROVIDED);

        final BluetoothGattCharacteristic characteristic = getCharacteristic(serviceUUID, characteristicUUID);
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
     * @return true if the operation was enqueued, otherwise false
     * @throws IllegalArgumentException if the CCC descriptor was not found or the characteristic does not support notifications or indications
     */
    public boolean setNotify(@NotNull final BluetoothGattCharacteristic characteristic, final boolean enable) {
        Objects.requireNonNull(characteristic, NO_VALID_CHARACTERISTIC_PROVIDED);

        if (notConnected()) {
            Logger.e(TAG,PERIPHERAL_NOT_CONNECTED);
            return false;
        }

        // Get the Client Characteristic Configuration Descriptor for the characteristic
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCC_DESCRIPTOR_UUID);
        if (descriptor == null) {
            String message = String.format("could not get CCC descriptor for characteristic %s", characteristic.getUuid());
            throw new IllegalArgumentException(message);
        }

        // Check if characteristic has NOTIFY or INDICATE properties and set the correct byte value to be written
        final byte[] value;
        final int properties = characteristic.getProperties();
        if ((properties & PROPERTY_NOTIFY) > 0) {
            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
        } else if ((properties & PROPERTY_INDICATE) > 0) {
            value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
        } else {
            String message = String.format("characteristic %s does not have notify or indicate property", characteristic.getUuid());
            throw new IllegalArgumentException(message);
        }
        final byte[] finalValue = enable ? value : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;

        final boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (notConnected()) {
                    completedCommand();
                    return;
                }

                // First try to set notification for Gatt object
                if (!bluetoothGatt.setCharacteristicNotification(characteristic, enable)) {
                    Logger.e(TAG,"setCharacteristicNotification failed for characteristic: %s", characteristic.getUuid());
                    completedCommand();
                    return;
                }

                // Then write to CCC descriptor
                adjustWriteTypeIfNeeded(characteristic);
                currentWriteBytes = finalValue;
                descriptor.setValue(finalValue);
                if (bluetoothGatt.writeDescriptor(descriptor)) {
                    nrTries++;
                } else {
                    Logger.e(TAG,"writeDescriptor failed for descriptor: %s", descriptor.getUuid());
                    completedCommand();
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Logger.e(TAG,"could not enqueue setNotify command");
        }
        return result;
    }

    private void adjustWriteTypeIfNeeded(@NotNull final BluetoothGattCharacteristic characteristic) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // Up to Android 6 there is a bug where Android takes the writeType of the parent characteristic instead of always WRITE_TYPE_DEFAULT
            // See: https://android.googlesource.com/platform/frameworks/base/+/942aebc95924ab1e7ea1e92aaf4e7fc45f695a6c%5E%21/#F0
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        }
    }

    /**
     * Read the RSSI for a connected remote peripheral.
     *
     * <p>{@link BluetoothPeripheralCallback#onReadRemoteRssi(BluetoothPeripheral, int, GattStatus)} will be triggered as a result of this call.
     *
     * @return true if the operation was enqueued, false otherwise
     */
    public boolean readRemoteRssi() {
        if (notConnected()) {
            Logger.e(TAG,PERIPHERAL_NOT_CONNECTED);
            return false;
        }

        final boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    if (!bluetoothGatt.readRemoteRssi()) {
                        Logger.e(TAG,"readRemoteRssi failed");
                        completedCommand();
                    }
                } else {
                    completedCommand();
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Logger.e(TAG,"could not enqueue readRemoteRssi command");
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
     * <p>Note that requesting an MTU should only take place once per connection, according to the Bluetooth standard.</p>
     * <p>{@link BluetoothPeripheralCallback#onMtuChanged(BluetoothPeripheral, int, GattStatus)} will be triggered as a result of this call.
     *
     * @param mtu the desired MTU size
     * @return true if the operation was enqueued, false otherwise
     */
    public boolean requestMtu(final int mtu) {
        if (mtu < DEFAULT_MTU || mtu > MAX_MTU) {
            throw new IllegalArgumentException("mtu must be between 23 and 517");
        }

        if (notConnected()) {
            Logger.e(TAG,PERIPHERAL_NOT_CONNECTED);
            return false;
        }

        final boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    if (bluetoothGatt.requestMtu(mtu)) {
                        currentCommand = REQUEST_MTU_COMMAND;
                        Logger.i(TAG,"requesting MTU of %d", mtu);
                    } else {
                        Logger.e(TAG,"requestMtu failed");
                        completedCommand();
                    }
                } else {
                    completedCommand();
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Logger.e(TAG,"could not enqueue requestMtu command");
        }

        return result;
    }

    /**
     * Request a different connection priority.
     *
     * @param priority the requested connection priority
     * @return true if request was enqueued, false if not
     */
    public boolean requestConnectionPriority(@NotNull final ConnectionPriority priority) {
        Objects.requireNonNull(priority, NO_VALID_PRIORITY_PROVIDED);

        if (notConnected()) {
            Logger.e(TAG,PERIPHERAL_NOT_CONNECTED);
            return false;
        }

        final boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    if (bluetoothGatt.requestConnectionPriority(priority.value)) {
                        Logger.d(TAG,"requesting connection priority %s", priority);
                    } else {
                        Logger.e(TAG,"could not request connection priority");
                    }
                }

                // Complete command as there is no reliable callback for this, but allow some time
                callbackHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        completedCommand();
                    }
                }, AVG_REQUEST_CONNECTION_PRIORITY_DURATION);
            }
        });

        if (result) {
            nextCommand();
        } else {
            Logger.e(TAG,"could not enqueue request connection priority command");
        }
        return result;
    }

    /**
     * Set the preferred connection PHY for this app. Please note that this is just a
     * recommendation, whether the PHY change will happen depends on other applications preferences,
     * local and remote controller capabilities. Controller can override these settings.
     * <p>
     * {@link BluetoothPeripheralCallback#onPhyUpdate} will be triggered as a result of this call, even
     * if no PHY change happens. It is also triggered when remote device updates the PHY.
     *
     * @param txPhy      the desired TX PHY
     * @param rxPhy      the desired RX PHY
     * @param phyOptions the desired optional sub-type for PHY_LE_CODED
     * @return true if request was enqueued, false if not
     */
    public boolean setPreferredPhy(@NotNull final PhyType txPhy, @NotNull final PhyType rxPhy, @NotNull final PhyOptions phyOptions) {
        Objects.requireNonNull(txPhy);
        Objects.requireNonNull(rxPhy);
        Objects.requireNonNull(phyOptions);

        if (notConnected()) {
            Logger.e(TAG,PERIPHERAL_NOT_CONNECTED);
            return false;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Logger.e(TAG,"setPreferredPhy requires Android 8.0 or newer");
            return false;
        }

        final boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        currentCommand = SET_PHY_TYPE_COMMAND;
                        Logger.i(TAG,"setting preferred Phy: tx = %s, rx = %s, options = %s", txPhy, rxPhy, phyOptions);
                        bluetoothGatt.setPreferredPhy(txPhy.mask, rxPhy.mask, phyOptions.value);
                    }
                } else {
                    completedCommand();
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Logger.e(TAG,"could not enqueue setPreferredPhy command");
        }
        return result;
    }

    /**
     * Read the current transmitter PHY and receiver PHY of the connection. The values are returned
     * in {@link BluetoothPeripheralCallback#onPhyUpdate}
     */
    public boolean readPhy() {
        if (notConnected()) {
            Logger.e(TAG,PERIPHERAL_NOT_CONNECTED);
            return false;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Logger.e(TAG,"setPreferredPhy requires Android 8.0 or newer");
            return false;
        }

        final boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        bluetoothGatt.readPhy();
                        Logger.d(TAG,"reading Phy");
                    }
                } else {
                    completedCommand();
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Logger.e(TAG,"could not enqueue readyPhy command");
        }
        return result;
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
            Logger.e(TAG,"could not invoke refresh method");
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
        final Runnable currentCommand = commandQueue.peek();
        if (currentCommand != null) {
            if (nrTries >= MAX_TRIES) {
                // Max retries reached, give up on this one and proceed
                Logger.d(TAG,"max number of tries reached, not retrying operation anymore");
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
                Logger.e(TAG,"gatt is 'null' for peripheral '%s', clearing command queue", getAddress());
                commandQueue.clear();
                commandQueueBusy = false;
                return;
            }

            // Check if the peripheral has initiated bonding as this may be a reason for failures
            if (getBondState() == BondState.BONDING) {
                Logger.w(TAG, "bonding is in progress, waiting for bonding to complete");
                peripheralInitiatedBonding = true;
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
                        Logger.e(TAG,"command exception for device '%s'", getName());
                        Logger.e(TAG, ex.toString());
                        completedCommand();
                    }
                }
            });
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

    interface InternalCallback {

        /**
         * Trying to connect to {@link BluetoothPeripheral}
         *
         * @param peripheral {@link BluetoothPeripheral} the peripheral.
         */
        void connecting(@NotNull BluetoothPeripheral peripheral);

        /**
         * {@link BluetoothPeripheral} has successfully connected.
         *
         * @param peripheral {@link BluetoothPeripheral} that connected.
         */
        void connected(@NotNull BluetoothPeripheral peripheral);

        /**
         * Connecting with {@link BluetoothPeripheral} has failed.
         *
         * @param peripheral {@link BluetoothPeripheral} of which connect failed.
         */
        void connectFailed(@NotNull BluetoothPeripheral peripheral, @NotNull final HciStatus status);

        /**
         * Trying to disconnect to {@link BluetoothPeripheral}
         *
         * @param peripheral {@link BluetoothPeripheral} the peripheral.
         */
        void disconnecting(@NotNull BluetoothPeripheral peripheral);

        /**
         * {@link BluetoothPeripheral} has disconnected.
         *
         * @param peripheral {@link BluetoothPeripheral} that disconnected.
         */
        void disconnected(@NotNull BluetoothPeripheral peripheral, @NotNull final HciStatus status);

        String getPincode(@NotNull BluetoothPeripheral peripheral);

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
                Logger.e(TAG,"could not get iBluetoothGatt object");
                return connectGattCompat(bluetoothGattCallback, remoteDevice, true);
            }

            BluetoothGatt bluetoothGatt = createBluetoothGatt(iBluetoothGatt, remoteDevice);

            if (bluetoothGatt == null) {
                Logger.e(TAG,"could not create BluetoothGatt object");
                return connectGattCompat(bluetoothGattCallback, remoteDevice, true);
            }

            boolean connectedSuccessfully = connectUsingReflection(remoteDevice, bluetoothGatt, bluetoothGattCallback, true);

            if (!connectedSuccessfully) {
                Logger.i(TAG,"connection using reflection failed, closing gatt");
                bluetoothGatt.close();
            }

            return bluetoothGatt;
        } catch (NoSuchMethodException
                | IllegalAccessException
                | IllegalArgumentException
                | InvocationTargetException
                | InstantiationException
                | NoSuchFieldException exception) {
            Logger.e(TAG,"error during reflection");
            return connectGattCompat(bluetoothGattCallback, remoteDevice, true);
        }
    }

    private BluetoothGatt connectGattCompat(BluetoothGattCallback bluetoothGattCallback, BluetoothDevice device, boolean autoConnect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return device.connectGatt(context, autoConnect, bluetoothGattCallback, transport.value);
        } else {
            // Try to call connectGatt with transport parameter using reflection
            try {
                Method connectGattMethod = device.getClass().getMethod("connectGatt", Context.class, boolean.class, BluetoothGattCallback.class, int.class);
                try {
                    return (BluetoothGatt) connectGattMethod.invoke(device, context, autoConnect, bluetoothGattCallback, transport.value);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
        // Fallback on connectGatt without transport parameter
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
            return (BluetoothGatt) (bluetoothGattConstructor.newInstance(context, iBluetoothGatt, remoteDevice, transport.value));
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

    private void startConnectionTimer(@NotNull final BluetoothPeripheral peripheral) {
        cancelConnectionTimer();
        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Logger.e(TAG,"connection timout, disconnecting '%s'", peripheral.getName());
                disconnect();

                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        bluetoothGattCallback.onConnectionStateChange(bluetoothGatt, HciStatus.CONNECTION_FAILED_ESTABLISHMENT.value, BluetoothProfile.STATE_DISCONNECTED);
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
        if (manufacturer.equalsIgnoreCase("samsung")) {
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
    byte[] copyOf(@Nullable final byte[] source) {
        return (source == null) ? new byte[0] : Arrays.copyOf(source, source.length);
    }

    /**
     * Make a byte array nonnull by either returning the original byte array if non-null or an empty bytearray
     *
     * @param source byte array to make nonnull
     * @return the source byte array or an empty array if source was null
     */
    @NotNull
    byte[] nonnullOf(@Nullable final byte[] source) {
        return (source == null) ? new byte[0] : source;
    }
}
