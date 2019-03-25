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
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import static android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED;
import static android.bluetooth.BluetoothDevice.ERROR;
import static android.bluetooth.BluetoothDevice.EXTRA_BOND_STATE;
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
@SuppressWarnings("SpellCheckingInspection")
public class BluetoothPeripheral {
    private static final String TAG = BluetoothPeripheral.class.getSimpleName();
    private static final String CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    // Gatt status values taken from Android source code:
    // https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/android-4.4.4_r2.0.1/stack/include/gatt_api.h

    /** A GATT operation completed successfully */
    public static final int GATT_SUCCESS = 0;

    /** The connection was terminated because of a L2C failure */
    public static final int GATT_CONN_L2C_FAILURE = 1;

    /** The connection has timed out */
    public static final int GATT_CONN_TIMEOUT  = 8;

    /** GATT read operation is not permitted */
    public static final int GATT_READ_NOT_PERMITTED = 2;

    /** GATT write operation is not permitted */
    public static final int GATT_WRITE_NOT_PERMITTED = 3;

    /** Insufficient authentication for a given operation */
    public static final int GATT_INSUFFICIENT_AUTHENTICATION = 5;

    /** The given request is not supported */
    public static final int GATT_REQUEST_NOT_SUPPORTED = 6;

    /** Insufficient encryption for a given operation */
    public static final int GATT_INSUFFICIENT_ENCRYPTION = 15;

    /** The connection was terminated by the peripheral */
    public static final int GATT_CONN_TERMINATE_PEER_USER = 19;

    /** The connection was terminated by the local host */
    public static final int GATT_CONN_TERMINATE_LOCAL_HOST = 22;

    /** The connection lost because of LMP timeout */
    public static final int GATT_CONN_LMP_TIMEOUT = 34;

    /** The connection was terminated due to MIC failure */
    public static final int BLE_HCI_CONN_TERMINATED_DUE_TO_MIC_FAILURE = 61;

    /** The connection cannot be established */
    public static final int GATT_CONN_FAIL_ESTABLISH = 62;

    /** The peripheral has no resources to complete the request */
    public static final int GATT_NO_RESOURCES = 128;

    /** Something went wrong in the bluetooth stack */
    public static final int GATT_INTERNAL_ERROR = 129;

    /** The GATT operation could not be executed because the stack is busy */
    public static final int GATT_BUSY = 132;

    /** Generic error, could be anything */
    public static final int GATT_ERROR = 133;

    /** Authentication failed */
    public static final int GATT_AUTH_FAIL = 137;

    /** The connection was cancelled */
    public static final int GATT_CONN_CANCEL = 256;

    /**
     * Bluetooth device type, Unknown
     */
    public static final int DEVICE_TYPE_UNKNOWN = 0;

    /**
     * Bluetooth device type, Classic - BR/EDR devices
     */
    public static final int DEVICE_TYPE_CLASSIC = 1;

    /**
     * Bluetooth device type, Low Energy - LE-only
     */
    public static final int DEVICE_TYPE_LE = 2;

    /**
     * Bluetooth device type, Dual Mode - BR/EDR/LE
     */
    public static final int DEVICE_TYPE_DUAL = 3;

    /**
     * Indicates the remote device is not bonded (paired).
     * <p>There is no shared link key with the remote device, so communication
     * (if it is allowed at all) will be unauthenticated and unencrypted.
     */
    public static final int BOND_NONE = 10;
    /**
     * Indicates bonding (pairing) is in progress with the remote device.
     */
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
    public static final int BOND_BONDED = 12;

    /** The profile is in disconnected state */
    public int STATE_DISCONNECTED = 0;
    /** The profile is in connecting state */
    public int STATE_CONNECTING = 1;
    /** The profile is in connected state */
    public int STATE_CONNECTED = 2;
    /** The profile is in disconnecting state */
    public int STATE_DISCONNECTING = 3;

    // Constants
    private static final int MAX_TRIES = 2;
    private static final int DIRECT_CONNECTION_DELAY_IN_MS = 100;
    private static final int CONNECTION_TIMEOUT_IN_MS = 35000;
    private static final int MAX_NOTIFYING_CHARACTERISTICS = 15;    // From BTA_GATTC_NOTIF_REG_MAX

    // Member variables
    private Context context;
    private Handler bleHandler;
    private BluetoothDevice device;
    private InternalCallback listener;
    private BluetoothPeripheralCallback peripheralCallback;
    private Queue<Runnable> commandQueue;
    private boolean commandQueueBusy;
    private boolean isRetrying;
    private boolean bondLost = false;
    private BluetoothGatt bluetoothGatt;
    private int state;
    private int nrTries;
    private byte[] currentWriteBytes;
    private Set<UUID> notifyingCharacteristics = new HashSet<>();
    private final Handler timeoutHandler = new Handler();
    private Runnable timeoutRunnable;
    private Runnable discoverServicesRunnable;
    private long connectTimestamp;

    /**
     * This abstract class is used to implement BluetoothGatt callbacks.
     */
    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

        /**
         * Callback indicating when GATT client has connected/disconnected to/from a remote
         * GATT server.
         *
         * @param gatt GATT client
         * @param status Status of the connect or disconnect operation.
         *               {@link BluetoothGatt#GATT_SUCCESS} if the operation succeeds.
         * @param newState Returns the new connection state. Can be one of
         *                  {@link BluetoothProfile#STATE_DISCONNECTED} or
         *                  {@link BluetoothProfile#STATE_CONNECTED}
         */
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            long timePassed = SystemClock.elapsedRealtime() - connectTimestamp;

            cancelConnectionTimer();

            if (status == GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    state = BluetoothProfile.STATE_CONNECTED;
                    int bondstate = device.getBondState();
                    Log.i(TAG, String.format("connected to '%s' (%s) in %.1fs", getName(), bondStateToString(bondstate), timePassed / 1000.0f));

                    // Take action depending on the bond state
                    if(bondstate == BOND_NONE || bondstate == BOND_BONDED) {

                        // Connected to device, now proceed to discover it's services but delay a bit if needed
                        int delayWhenBonded = 0;
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                            // It seems delays when bonded are only needed in versions Nougat or lower
                            // This issue was observed on a Nexus 5 (M) and Sony Xperia L1 (N) when connecting to a A&D UA-651BLE
                            // The delay is needed when devices have the Service Changed Characteristic.
                            // If they don't have it the delay isn't needed but we do it anyway to keep code simple
                            delayWhenBonded = 1000;
                        }
                        final int delay = bondstate == BOND_BONDED ? delayWhenBonded : 0;
                        discoverServicesRunnable = new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, String.format(Locale.ENGLISH, "discovering services of '%s' with delay of %d ms", getName(), delay));
                                boolean result = gatt.discoverServices();
                                if (!result) {
                                    Log.e(TAG, "discoverServices failed to start");
                                }
                                discoverServicesRunnable = null;
                            }
                        };
                        bleHandler.postDelayed(discoverServicesRunnable, delay);
                    } else if (bondstate == BOND_BONDING) {
                        // Apparently the bonding process has already started let it complete
                        Log.i(TAG, "waiting for bonding to complete");
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if(state == BluetoothProfile.STATE_CONNECTED || state == BluetoothProfile.STATE_DISCONNECTING) {
                        Log.i(TAG, String.format("disconnected '%s' on request", getName()));
                    } else if(state == BluetoothProfile.STATE_CONNECTING) {
                        Log.i(TAG, "cancelling connect attempt");
                    }
                    if(bondLost) {
                        completeDisconnect(false, status);
                        if (listener != null) {
                            // Consider the loss of the bond a connection failure so that a connection retry will take place
                            bleHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    listener.connectFailed(BluetoothPeripheral.this, status);
                                }
                            }, 1000); // Give the stack some time to register the bond loss internally. This is needed on most phones...
                        };
                    } else {
                        completeDisconnect(true, status);
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {
                    // Device is disconnection, let it finish...
                    Log.i(TAG, "peripheral is disconnecting");
                    state = BluetoothProfile.STATE_DISCONNECTING;
                } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                    // Device is connection, let it finish...
                    Log.i(TAG, "peripheral is connecting");
                    state = BluetoothProfile.STATE_CONNECTING;
                }
            } else {
                // Check if service discovery completed
                List<BluetoothGattService> services = getServices();
                boolean servicesDiscovered = !services.isEmpty();

                // See if the initial connection failed
                if (state == BluetoothProfile.STATE_CONNECTING) {
                    Log.i(TAG, String.format("connection failed with status '%s'", statusToString(status)));
                    completeDisconnect(false, status);
                    if (listener != null) {
                        listener.connectFailed(BluetoothPeripheral.this, status);
                    }
                } else if(state == BluetoothProfile.STATE_CONNECTED && newState == BluetoothProfile.STATE_DISCONNECTED && !servicesDiscovered) {
                    // We got a disconnection before the services were even discovered
                    Log.i(TAG, String.format("peripheral '%s' disconnected with status '%s' during service discovery", getName(), statusToString(status)));
                    completeDisconnect(false, status);
                    if (listener != null) {
                        listener.connectFailed(BluetoothPeripheral.this, status);
                    }
                } else {
                    // See if we got connection drop
                    if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.i(TAG, String.format("peripheral '%s' disconnected with status '%s'", getName(), statusToString(status)));
                    } else {
                        Log.i(TAG, String.format("unexpected connection state change for '%s' status '%s'", getName(), statusToString(status)));
                    }
                    completeDisconnect(true, status);
                }
            }
        }


        /**
         * Callback invoked when the list of remote services, characteristics and descriptors
         * for the remote device have been updated, ie new services have been discovered.
         *
         * @param gatt GATT client invoked {@link BluetoothGatt#discoverServices}
         * @param status {@link BluetoothGatt#GATT_SUCCESS} if the remote device
         *               has been explored successfully.
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            // Check if the service discovery succeeded. If not disconnect
            if (status == GATT_INTERNAL_ERROR) {
                Log.e(TAG, "ERROR: Services not found");
                disconnect();
                return;
            }
            Log.i(TAG, String.format(Locale.ENGLISH,"discovered %d services for '%s'", gatt.getServices().size(), getName()));
            
            // Let the listeners know we are now properly connected
            if(listener != null) {
                listener.connected(BluetoothPeripheral.this);
            }

            bleHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onServicesDiscovered(BluetoothPeripheral.this);
                }
            });
        }


        /**
         * Callback indicating the result of a descriptor write operation.
         *
         * @param gatt GATT client invoked {@link BluetoothGatt#writeDescriptor}
         * @param descriptor Descriptor that was written to the associated
         *                   remote device.
         * @param status The result of the write operation
         *               {@link BluetoothGatt#GATT_SUCCESS} if the operation succeeds.
         */
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            // Do some checks first
            final BluetoothGattCharacteristic parentCharacteristic = descriptor.getCharacteristic();
            if(status!= GATT_SUCCESS) {
                Log.e(TAG, String.format("ERROR: Write descriptor failed value <%s>, device: %s, characteristic: %s", bytes2String(currentWriteBytes), getAddress(), parentCharacteristic.getUuid()));
            }

            // Check if this was the Client Configuration Descriptor
            if(descriptor.getUuid().equals(UUID.fromString(CCC_DESCRIPTOR_UUID))) {
                if(status==GATT_SUCCESS) {
                    // Check if we were turning notify on or off
                    byte[] value = descriptor.getValue();
                    if (value != null) {
                        if (value[0] != 0) {
                            // Notify set to on, add it to the set of notifying characteristics
                            notifyingCharacteristics.add(parentCharacteristic.getUuid());
                            if(notifyingCharacteristics.size() > MAX_NOTIFYING_CHARACTERISTICS) {
                                Log.e(TAG, String.format("Too many (%d) notifying characteristics. The maximum Android can handle is %d", notifyingCharacteristics.size(), MAX_NOTIFYING_CHARACTERISTICS));
                            }
                        } else {
                            // Notify was turned off, so remove it from the set of notifying characteristics
                            notifyingCharacteristics.remove(parentCharacteristic.getUuid());
                        }
                    }
                }

                // Propagate to callback, even when there was an error
                bleHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        peripheralCallback.onNotificationStateUpdate(BluetoothPeripheral.this, parentCharacteristic, status);
                    }
                });
            } else {
                // Propagate to callback, even when there was an error
                bleHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        peripheralCallback.onDescriptorWrite(BluetoothPeripheral.this, currentWriteBytes, descriptor, status);
                    }
                });
            }
            completedCommand();
        }

        /**
         * Callback indicating the result of a descriptor write operation.
         *
         * @param gatt GATT client invoked {@link BluetoothGatt#writeDescriptor}
         * @param descriptor Descriptor that was written to the associated
         *                   remote device.
         * @param status The result of the write operation
         *               {@link BluetoothGatt#GATT_SUCCESS} if the operation succeeds.
         */
        @Override
        public void onDescriptorRead(BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, int status) {
            if(status!= GATT_SUCCESS) {
                Log.e(TAG, String.format("ERROR: Write descriptor failed device: %s", getAddress()));
            }
            final byte[] value = new byte[descriptor.getValue().length];
            System.arraycopy(descriptor.getValue(), 0, value, 0, descriptor.getValue().length);
            bleHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onDescriptorRead(BluetoothPeripheral.this, value, descriptor);
                }
            });
            completedCommand();
        }

        /**
         * Callback triggered as a result of a remote characteristic notification.
         *
         * @param gatt GATT client the characteristic is associated with
         * @param characteristic Characteristic that has been updated as a result
         *                       of a remote notification event.
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // Copy the byte array so we have a threadsafe copy
            final byte[] value = new byte[characteristic.getValue().length];
            System.arraycopy(characteristic.getValue(), 0, value, 0, characteristic.getValue().length );

            // Characteristic has new value so pass it on to the right service handler
            bleHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onCharacteristicUpdate(BluetoothPeripheral.this, value, characteristic);
                }
            });
        }


        /**
         * Callback reporting the result of a characteristic read operation.
         *
         * @param gatt GATT client invoked {@link BluetoothGatt#readCharacteristic}
         * @param characteristic Characteristic that was read from the associated
         *                       remote device.
         * @param status {@link BluetoothGatt#GATT_SUCCESS} if the read operation
         *               was completed successfully.
         */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {
            // Perform some checks on the status field
            if (status != GATT_SUCCESS) {
                if (status == GATT_AUTH_FAIL || status == GATT_INSUFFICIENT_AUTHENTICATION ) {
                    // Characteristic encrypted and needs bonding,
                    // So retry operation after bonding completes
                    // This only seems to happen on Android 5/6/7
                    Log.w(TAG, "read needs bonding, bonding in progress");
                    return;
                } else {
                    Log.e(TAG, String.format(Locale.ENGLISH,"ERROR: Read failed for characteristic: %s, status %d", characteristic.getUuid(), status));
                    completedCommand();
                    return;
                }
            }

            // Copy the byte array so we have a threadsafe copy
            final byte[] value = new byte[characteristic.getValue().length];
            System.arraycopy(characteristic.getValue(), 0, value, 0, characteristic.getValue().length );

            // Characteristic has been read, pass it on to service handler
            bleHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onCharacteristicUpdate(BluetoothPeripheral.this, value, characteristic);
                }
            });
            completedCommand();
        }


        /**
         * Callback indicating the result of a characteristic write operation.
         *
         * @param gatt GATT client invoked {@link BluetoothGatt#writeCharacteristic}
         * @param characteristic Characteristic that was written to the associated
         *                       remote device.
         * @param status The result of the write operation
         *               {@link BluetoothGatt#GATT_SUCCESS} if the operation succeeds.
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            // Perform some checks on the status field
            if(status!= GATT_SUCCESS) {
                if (status == GATT_AUTH_FAIL || status == GATT_INSUFFICIENT_AUTHENTICATION ) {
                    // Characteristic encrypted and needs bonding,
                    // So retry operation after bonding completes
                    // This only seems to happen on Android 5/6/7
                    Log.i(TAG, "write needs bonding, bonding in progress");
                    return;
                } else {
                    Log.e(TAG, String.format("ERROR: Writing <%s> to characteristic <%s> failed, status %s", bytes2String(currentWriteBytes), characteristic.getUuid(), statusToString(status)));
                    completedCommand();
                    return;
                }
            }

            // Copy the byte array so we have a threadsafe copy
            final byte[] value = new byte[currentWriteBytes.length];
            System.arraycopy(currentWriteBytes, 0, value, 0, currentWriteBytes.length );
            currentWriteBytes = null;

            // Inform the service handler of the write
            bleHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onCharacteristicWrite(BluetoothPeripheral.this, value, characteristic, status);
                }
            });
            completedCommand();
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, final int rssi, final int status) {
            bleHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onReadRemoteRssi(BluetoothPeripheral.this, rssi, status);
                }
            });
            completedCommand();
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, final int mtu, final int status) {
            bleHandler.post(new Runnable() {
                @Override
                public void run() {
                    peripheralCallback.onMtuChanged(BluetoothPeripheral.this, mtu, status);
                }
            });
            completedCommand();
        }
    };

    private final BroadcastReceiver bondStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            // Ignore updates for other devices
            if (bluetoothGatt == null || !device.getAddress().equals(bluetoothGatt.getDevice().getAddress()))
                return;

            // Check if action is valid
            if(action == null) return;

            // Take action depending on new bond state
            if (action.equals(ACTION_BOND_STATE_CHANGED)) {
                final int bondState = intent.getIntExtra(EXTRA_BOND_STATE, ERROR);
                final int previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);

                switch (bondState) {
                    case BOND_BONDING:
                        Log.d(TAG, String.format("starting bonding with '%s' (%s)", device.getName(), device.getAddress()));
                        bleHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                peripheralCallback.onBondingStarted(BluetoothPeripheral.this);
                            }
                        });
                        break;
                    case BOND_BONDED:
                        // Bonding succeeded
                        Log.d(TAG, String.format("bonded with '%s' (%s)", device.getName(), device.getAddress()));
                        bleHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                peripheralCallback.onBondingSucceeded(BluetoothPeripheral.this);
                            }
                        });

                        // If bonding was started at connection time, we may still have to discover the services
                        if(bluetoothGatt.getServices().isEmpty()) {
                            // No services discovered yet so proceed with discovering services
                            bleHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Log.d(TAG, String.format("discovering services of '%s'", getName()));
                                    boolean result = bluetoothGatt.discoverServices();
                                    if (!result) {
                                        Log.e(TAG, "discoverServices failed to start");
                                    }
                                }
                            });
                        }

                        // If bonding was triggered by a read/write, we must retry it
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                            if (commandQueueBusy) {
                                retryCommand();
                            }
                        }
                        break;
                    case BOND_NONE:
                        if(previousBondState == BOND_BONDING) {
                            Log.e(TAG, String.format("bonding failed for '%s', disconnecting device", getName()));
                            bleHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    peripheralCallback.onBondingFailed(BluetoothPeripheral.this);
                                }
                            });

                            // Try to continue the queue if needed
                            if(commandQueueBusy) {
                                completedCommand();
                            }
                        } else {
                            Log.e(TAG, String.format("bond lost for '%s'", getName()));
                            bondLost = true;

                            // Cancel the discoverServiceRunnable if it is still pending
                            if(discoverServicesRunnable != null) {
                                bleHandler.removeCallbacks(discoverServicesRunnable);
                            }

                            bleHandler.post(new Runnable() {
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
        }
    };

    private final BroadcastReceiver pairingRequestBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            // Skip other devices
            if (bluetoothGatt == null || !device.getAddress().equals(bluetoothGatt.getDevice().getAddress()))
                return;

            // String values are used as the constants are not available for Android 4.3.
            final int variant = intent.getIntExtra("android.bluetooth.device.extra.PAIRING_VARIANT"/*BluetoothDevice.EXTRA_PAIRING_VARIANT*/, 0);
            Log.d(TAG, "Pairing request received " +
                    ", pairing variant: " + pairingVariantToString(variant) + " (" + variant + ")");
        }
    };


    /**
     * Constructs a new device wrapper around {@code device}.
     *
     * @param context            Android application environment.
     * @param device             Wrapped Android bluetooth device.
     * @param listener           Callback to {@link BluetoothCentral}.
     */
    BluetoothPeripheral(Context context, BluetoothDevice device, InternalCallback listener, BluetoothPeripheralCallback peripheralCallback, Handler callbackHandler) {
        if(context == null || device == null || listener == null) {
            Log.e(TAG, "cannot create BluetoothPeripheral because of null values");
        }
        this.context = context;
        this.device = device;
        this.peripheralCallback = peripheralCallback;
        this.listener = listener;
        if(callbackHandler != null) {
            this.bleHandler = callbackHandler;
        } else {
            this.bleHandler = new Handler();
        }
        this.commandQueue = new LinkedList<>();
        this.state = BluetoothProfile.STATE_DISCONNECTED;
        this.commandQueueBusy = false;
    }

    public void setPeripheralCallback(BluetoothPeripheralCallback peripheralCallback) {
        this.peripheralCallback = peripheralCallback;
    }

    /**
     * Connect directly with the bluetooth device. This call will timeout in max 30 seconds
     */
    void connect() {
        // Make sure we are disconnected before we start making a connection
        if (state == BluetoothProfile.STATE_DISCONNECTED) {
            // Register bonding broadcast receiver
            context.registerReceiver(bondStateReceiver, new IntentFilter(ACTION_BOND_STATE_CHANGED));
            context.registerReceiver(pairingRequestBroadcastReceiver, new IntentFilter("android.bluetooth.device.action.PAIRING_REQUEST"/*BluetoothDevice.ACTION_PAIRING_REQUEST*/));

            this.state = BluetoothProfile.STATE_CONNECTING;
            bleHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Connect to device with autoConnect = false
                    Log.i(TAG, String.format("connect to '%s' (%s) using TRANSPORT_LE", getName(), getAddress()));
                    bluetoothGatt = device.connectGatt(context, false, bluetoothGattCallback, TRANSPORT_LE);
                    connectTimestamp = SystemClock.elapsedRealtime();
                    startConnectionTimer(BluetoothPeripheral.this);
                }
            }, DIRECT_CONNECTION_DELAY_IN_MS);
        } else {
            Log.e(TAG, "device not disconnected, ignoring connect");
        }
    }

    /**
     * Try to connect to a device whenever it is found by the OS. This call never times out.
     * Connecting to a device will take longer than when using connect()
     */
    void autoConnect() {
        // Note that this will only work for devices that are known! After turning BT on/off Android doesn't know the device anymore!
        // https://stackoverflow.com/questions/43476369/android-save-ble-device-to-reconnect-after-app-close
        if(state == BluetoothProfile.STATE_DISCONNECTED) {
            if (bluetoothGatt == null) {
                bleHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        context.registerReceiver(bondStateReceiver, new IntentFilter(ACTION_BOND_STATE_CHANGED));
                        context.registerReceiver(pairingRequestBroadcastReceiver, new IntentFilter("android.bluetooth.device.action.PAIRING_REQUEST"/*BluetoothDevice.ACTION_PAIRING_REQUEST*/));

                        // Connect to device with autoConnect = true
                        Log.i(TAG, String.format("autoConnect to '%s' (%s) using TRANSPORT_LE", getName(), getAddress()));
                        state = BluetoothProfile.STATE_CONNECTING;
                        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            bluetoothGatt = device.connectGatt(context, true, bluetoothGattCallback, TRANSPORT_LE);
                        } else {
                            // Versions below Nougat had a race condition bug in autoconnect, so use special workaround
                            bluetoothGatt = autoConnectGatt(device,true,bluetoothGattCallback);
                        }
                        connectTimestamp = SystemClock.elapsedRealtime();

                    }
                });
            } else {
                Log.e(TAG, String.format("ERROR: Already have Gatt object for '%s'", getName()));
            }
        } else {
            Log.e(TAG, String.format("ERROR: Peripheral '%s' not yet disconnected", getName()));
        }
    }

    void cancelAutoConnect() {
        if (bluetoothGatt != null) {
            if(BluetoothPeripheral.this.state == BluetoothProfile.STATE_DISCONNECTED || BluetoothPeripheral.this.state == BluetoothProfile.STATE_CONNECTING) {
                // Not connected devices will not receive callback so call close immediately
                disconnect();
                completeDisconnect(false, 0);
            } else {
                // For other connection states, follow normal disconnect flow
                bluetoothGatt.disconnect();
            }
        }
    }

    /**
     * Disconnect the bluetooth peripheral.
     *
     * <p>When the disconnection has been completed {@link BluetoothCentralCallback#onDisconnectedPeripheral(BluetoothPeripheral, int)} will be called.
     */
    public void disconnect() {
        if (state == BluetoothProfile.STATE_CONNECTED || state == BluetoothProfile.STATE_CONNECTING) {
            this.state = BluetoothProfile.STATE_DISCONNECTING;
            bleHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (bluetoothGatt != null) {
                        Log.i(TAG, String.format("force disconnect '%s' (%s), state: %s", getName(), getAddress(), state));
                        bluetoothGatt.disconnect();
                    }
                }
            });
        } else {
            if(listener != null ) {
                listener.disconnected(BluetoothPeripheral.this, GATT_CONN_TERMINATE_LOCAL_HOST );
            }
        }
    }

    /**
     * Complete the disconnect after getting connectionstate = disconnected
     */
    private void completeDisconnect(boolean notify, final int status) {
        state = BluetoothProfile.STATE_DISCONNECTED;
        if(bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        commandQueue.clear();
        commandQueueBusy = false;
        context.unregisterReceiver(bondStateReceiver);
        context.unregisterReceiver(pairingRequestBroadcastReceiver);
        bondLost = false;
        if(listener != null && notify) {
            listener.disconnected(BluetoothPeripheral.this, status);
        }
    }

    /**
     * Get the mac address of the bluetooth peripheral
     *
     * @return Address of the bluetooth peripheral
     */
    public String getAddress() {
        return device.getAddress();
    }

    /**
     * Get the type of the peripheral
     *
     * @return the device type {@link #DEVICE_TYPE_CLASSIC}, {@link #DEVICE_TYPE_LE} {@link #DEVICE_TYPE_DUAL}. {@link #DEVICE_TYPE_UNKNOWN} if it's not available
     */
    public int getType() {
        return device.getType();
    }

    /**
     * Get the name of the bluetooth peripheral
     *
     * @return name of the bluetooth peripheral
     */
    public String getName() {
        return device.getName();
    }

    /**
     * Get the bond state of the bluetooth peripheral
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
    public List<BluetoothGattService> getServices() {
        return bluetoothGatt.getServices();
    }

    /**
     * Get the BluetoothGattService object for a service UUID
     *
     * @param serviceUUID the UUID of the service
     * @return the BluetoothGattService object for the service UUID or null if the peripheral does not have a service with the specified UUID
     */
    public BluetoothGattService getService(UUID serviceUUID) {
        if(bluetoothGatt != null) {
            return bluetoothGatt.getService(serviceUUID);
        } else {
            return null;
        }
    }

    /**
     * Get the BluetoothGattCharacteristic object for a characteristic UUID
     *
     * @param serviceUUID the service UUID the characteristic is part of
     * @param characteristicUUID the UUID of the chararacteristic
     * @return the BluetoothGattCharacteristic object for the characteristic UUID or null if the peripheral does not have a characteristic with the specified UUID
     */
    public BluetoothGattCharacteristic getCharacteristic(UUID serviceUUID, UUID characteristicUUID) {
        BluetoothGattService service = getService(serviceUUID);
        if(service != null) {
            return service.getCharacteristic(characteristicUUID);
        } else {
            return null;
        }
    }

    /**
     * Returns the connection state of the peripheral
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
     * Boolean to indicate if the specified characteristic is currently notifying or indicating
     *
     * @param characteristic the characteristic
     * @return true is the characteristic is notifying or indicating, false if it is not
     */
    public boolean isNotifying(BluetoothGattCharacteristic characteristic) {
        return notifyingCharacteristics.contains(characteristic.getUuid());
    }



    /**
     * Read the value of a characteristic.
     *
     * <p>The characteristic must support reading it, otherwise the operation will not be enqueued.
     *
     * <p>{@link BluetoothPeripheralCallback#onCharacteristicUpdate(BluetoothPeripheral, byte[], BluetoothGattCharacteristic)}  will be triggered as a result of this call.
     *
     * @param characteristic Specifies the characteristic to read.
     * @return true if the operation was enqueued, false if the characteristic does not support reading or the characteristic was invalid
     */
    public boolean readCharacteristic(final BluetoothGattCharacteristic characteristic) {
        if(bluetoothGatt == null) {
            Log.e(TAG, "ERROR: Gatt is 'null', ignoring read request");
            return false;
        }

        // Check if characteristic is valid
        if(characteristic == null) {
            Log.e(TAG, "ERROR: Characteristic is 'null', ignoring read request");
            return false;
        }

        // Check if this characteristic actually has READ property
        if((characteristic.getProperties() & PROPERTY_READ) == 0 ) {
            Log.e(TAG, "ERROR: Characteristic cannot be read");
            return false;
        }

        // Enqueue the read command now that all checks have been passed
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                // Double check if gatt is still valid
                if(bluetoothGatt != null) {
                    if(!bluetoothGatt.readCharacteristic(characteristic)) {
                        Log.e(TAG, String.format("ERROR: readCharacteristic failed for characteristic: %s", characteristic.getUuid()));
                        completedCommand();
                    } else {
                        Log.d(TAG, String.format("reading characteristic <%s>", characteristic.getUuid()));
                        nrTries++;
                    }
                } else {
                    completedCommand();
                }
            }
        });

        if(result) {
            nextCommand();
        } else {
            Log.e(TAG, "ERROR: Could not enqueue read characteristic command");
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
     * @param value the byte array to write
     * @param writeType the write type to use when writing. Must be WRITE_TYPE_DEFAULT, WRITE_TYPE_NO_RESPONSE or WRITE_TYPE_SIGNED
     * @return true if a write operation was succesfully enqueued, otherwise false
     */
    public boolean writeCharacteristic(final BluetoothGattCharacteristic characteristic, final byte[] value, final int writeType) {
        // Check if characteristic is valid
        if(characteristic == null) {
            Log.e(TAG, "ERROR: Characteristic is 'null', ignoring write request");
            return false;
        }

        // Check if byte array is valid
        if(value == null) {
            Log.e(TAG, "ERROR: Value to write is 'null', ignoring write request");
            return false;
        }

        // Copy the value to avoid race conditions
        final byte[] bytesToWrite = new byte[value.length];
        System.arraycopy(value, 0 , bytesToWrite, 0, value.length);

        // Check if this characteristic actually supports this writeType
        int writeProperty;
        switch (writeType) {
            case WRITE_TYPE_DEFAULT: writeProperty = PROPERTY_WRITE; break;
            case WRITE_TYPE_NO_RESPONSE : writeProperty = PROPERTY_WRITE_NO_RESPONSE; break;
            case WRITE_TYPE_SIGNED : writeProperty = PROPERTY_SIGNED_WRITE; break;
            default: writeProperty = 0; break;
        }
        if((characteristic.getProperties() & writeProperty) == 0 ) {
            Log.e(TAG, String.format(Locale.ENGLISH,"ERROR: Characteristic <%s> does not support writeType '%s'", characteristic.getUuid(), writeTypeToString(writeType)));
            return false;
        }

        // Enqueue the write command now that all checks have been passed
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                // Double check if gatt is still valid
                if (bluetoothGatt != null) {
                    currentWriteBytes = bytesToWrite;
                    characteristic.setValue(bytesToWrite);
                    characteristic.setWriteType(writeType);
                    if (!bluetoothGatt.writeCharacteristic(characteristic)) {
                        Log.e(TAG, String.format("ERROR: writeCharacteristic failed for characteristic: %s", characteristic.getUuid()));
                        completedCommand();
                    } else {
                        Log.d(TAG, String.format("writing <%s> to characteristic <%s>", bytes2String(bytesToWrite), characteristic.getUuid()));
                        nrTries++;
                    }
                } else {
                    completedCommand();
                }
            }
        });
        if(result) {
            nextCommand();
        } else {
            Log.e(TAG, "ERROR: Could not enqueue write characteristic command");
        }
        return result;
    }


    /**
     * Read the value of a descriptor
     *
     * @param descriptor the descriptor to read
     * @return true if a write operation was succesfully enqueued, otherwise false
     */
    public boolean readDescriptor(final BluetoothGattDescriptor descriptor) {
        if(bluetoothGatt == null) {
            Log.e(TAG, "ERROR: Gatt is 'null', ignoring read request");
            return false;
        }

        // Check if characteristic is valid
        if(descriptor == null) {
            Log.e(TAG, "ERROR: descriptor is 'null', ignoring read request");
            return false;
        }

        // Enqueue the read command now that all checks have been passed
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                // Double check if gatt is still valid
                if(bluetoothGatt != null) {
                    if(!bluetoothGatt.readDescriptor(descriptor)) {
                        Log.e(TAG, String.format("ERROR: readDescriptor failed for characteristic: %s", descriptor.getUuid()));
                        completedCommand();
                    } else {
                        nrTries++;
                    }
                } else {
                    completedCommand();
                }
            }
        });

        if(result) {
            nextCommand();
        } else {
            Log.e(TAG, "ERROR: Could not enqueue read descriptor command");
        }
        return result;
    }

    /**
     * Write a value to a descriptor
     *
     * <p>For turning on/off notifications use {@link BluetoothPeripheral#setNotify(BluetoothGattCharacteristic, boolean)} instead.
     *
     * @param descriptor the descriptor to write to
     * @param value the value to write
     * @return true if a write operation was succesfully enqueued, otherwise false
     */
    public boolean writeDescriptor(final BluetoothGattDescriptor descriptor, final byte[] value) {
        // Check if characteristic is valid
        if(descriptor == null) {
            Log.e(TAG, "ERROR: descriptor is 'null', ignoring write request");
            return false;
        }

        // Check if byte array is valid
        if(value == null) {
            Log.e(TAG, "ERROR: Value to write is 'null', ignoring write request");
            return false;
        }

        // Copy the value to avoid race conditions
        final byte[] bytesToWrite = new byte[value.length];
        System.arraycopy(value, 0 , bytesToWrite, 0, value.length);

        // Enqueue the write command now that all checks have been passed
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                // Double check if gatt is still valid
                if (bluetoothGatt != null) {
                    currentWriteBytes = bytesToWrite;
                    descriptor.setValue(bytesToWrite);
                    if (!bluetoothGatt.writeDescriptor(descriptor)) {
                        Log.e(TAG, String.format("ERROR: writeDescriptor failed for descriptor: %s", descriptor.getUuid()));
                        completedCommand();
                    } else {
                        Log.d(TAG, String.format("writing <%s> to descriptor <%s>", bytes2String(bytesToWrite), descriptor.getUuid()));
                        nrTries++;
                    }
                } else {
                    completedCommand();
                }
            }
        });

        if(result) {
            nextCommand();
        } else {
            Log.e(TAG, "ERROR: Could not enqueue write descriptor command");
        }
        return result;
    }

        /**
         * Set the notification state of a characteristic to 'on' or 'off'. The characteristic must support notifications or indications.
         *
         * <p>{@link BluetoothPeripheralCallback#onNotificationStateUpdate(BluetoothPeripheral, BluetoothGattCharacteristic, int)} will be triggered as a result of this call.
         *
         * @param characteristic the characteristic to turn notification on/off for
         * @param enable true for setting notification on, false for turning it off
         * @return true if the operation was enqueued, false if the characteristic doesn't support notification or indications or
         */
    public boolean setNotify(BluetoothGattCharacteristic characteristic, final boolean enable) {
        // Check if characteristic is valid
        if(characteristic == null) {
            Log.e(TAG, "ERROR: Characteristic is 'null', ignoring setNotify request");
            return false;
        }

        // Get the Client Configuration Descriptor for the characteristic
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(CCC_DESCRIPTOR_UUID));
        if(descriptor == null) {
            Log.e(TAG, String.format("ERROR: Could not get CCC descriptor for characteristic %s", characteristic.getUuid()));
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
            Log.e(TAG, String.format("ERROR: Characteristic %s does not have notify or indicate property", characteristic.getUuid()));
            return false;
        }
        final byte[] finalValue = enable ? value : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;

        // Queue Runnable to turn on/off the notification now that all checks have been passed
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                // First set notification for Gatt object
                if(!bluetoothGatt.setCharacteristicNotification(descriptor.getCharacteristic(), enable)) {
                    Log.e(TAG, String.format("ERROR: setCharacteristicNotification failed for descriptor: %s", descriptor.getUuid()));
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
                if(!result) {
                    Log.e(TAG, String.format("ERROR: writeDescriptor failed for descriptor: %s", descriptor.getUuid()));
                    completedCommand();
                } else {
                    nrTries++;
                }
            }
        });

        if(result) {
            nextCommand();
        } else {
            Log.e(TAG, "ERROR: Could not enqueue write command");
        }

        return result;
    }


    /**
     * Asynchronous method to clear the services cache. Make sure to add a delay when using this!
     * @return true if the method was executed, false if not executed
     */
    public boolean clearServicesCache()
    {
        boolean result = false;
        try {
            Method refreshMethod = bluetoothGatt.getClass().getMethod("refresh");
            if(refreshMethod != null) {
                result = (boolean) refreshMethod.invoke(bluetoothGatt);
            }
        } catch (Exception e) {
            Log.e(TAG, "ERROR: Could not invoke refresh method");
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
        return commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if(bluetoothGatt != null && state == BluetoothProfile.STATE_CONNECTED) {
                    if (!bluetoothGatt.readRemoteRssi()) {
                        Log.e(TAG, "ERROR: readRemoteRssi failed");
                        completedCommand();
                    }
                } else {
                    Log.e(TAG, "ERROR: cannot get rssi, peripheral not connected");
                    completedCommand();
                }
            }
        });
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
    public boolean requestMtu (final int mtu) {
        return commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if(bluetoothGatt != null && state == BluetoothProfile.STATE_CONNECTED) {
                    if (!bluetoothGatt.requestMtu(mtu)) {
                        Log.e(TAG, "ERROR: requestMtu failed");
                        completedCommand();
                    }
                } else {
                    Log.e(TAG, "ERROR: cannot request MTU, peripheral not connected");
                    completedCommand();
                }
            }
        });
    }

    /**
     * The current command has been completed, move to the next command in the queue (if any)
     */
    private void completedCommand() {
        commandQueueBusy = false;
        isRetrying = false;
        commandQueue.poll();
        nextCommand();
    }

    /**
     * Retry the current command. Typically used when a read/write fails and triggers a bonding procedure
     */
    private void retryCommand() {
        commandQueueBusy = false;
        Runnable currentCommand = commandQueue.peek();
        if(currentCommand != null) {
            if (nrTries >= MAX_TRIES) {
                // Max retries reached, give up on this one and proceed
                Log.v(TAG, "Max number of tries reached, not retrying operation anymore");
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
        // If there is still a command being executed then bail out
        if(commandQueueBusy) {
            return;
        }

        // Check if we still have a valid gatt object
        if (bluetoothGatt == null) {
            Log.e(TAG, String.format("ERROR: GATT is 'null' for peripheral '%s', clearing command queue", getAddress()));
            commandQueue.clear();
            commandQueueBusy = false;
            return;
        }

        // Execute the next command in the queue
        if (commandQueue.size() > 0) {
            final Runnable bluetoothCommand = commandQueue.peek();
            commandQueueBusy = true;
            nrTries = 0;

            bleHandler.post(new Runnable() {
                @Override
                public void run() {
                        try {
                            bluetoothCommand.run();
                        } catch (Exception ex) {
                            Log.e(TAG, String.format("ERROR: Command exception for device '%s'", getName()), ex);
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

    /**
     * Converts the connection state to String value
     *
     * @param state the connection state
     * @return state as String
     */
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
                return "BLE_HCI_CONN_TERMINATED_DUE_TO_MIC_FAILURE";
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
                return "GATT_NO_RESOURCES";
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
     * @param bytes The data
     * @return String represents the data in HEX string
     */
    private static String bytes2String(final byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for(byte b : bytes){
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

    }

    /////////////////

    BluetoothGatt autoConnectGatt(BluetoothDevice remoteDevice, boolean autoConnect, BluetoothGattCallback bluetoothGattCallback) {

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
                Log.i(TAG,"Couldn't get iBluetoothGatt object");
                return connectGattCompat(bluetoothGattCallback, remoteDevice, true);
            }

            BluetoothGatt bluetoothGatt = createBluetoothGatt(iBluetoothGatt, remoteDevice);

            if (bluetoothGatt == null) {
                Log.i(TAG,"Couldn't create BluetoothGatt object");
                return connectGattCompat(bluetoothGattCallback, remoteDevice, true);
            }

            boolean connectedSuccessfully = connectUsingReflection(remoteDevice, bluetoothGatt, bluetoothGattCallback, true);

            if (!connectedSuccessfully) {
                Log.i(TAG,"Connection using reflection failed, closing gatt");
                bluetoothGatt.close();
            }

            return bluetoothGatt;
        } catch (NoSuchMethodException
                | IllegalAccessException
                | IllegalArgumentException
                | InvocationTargetException
                | InstantiationException
                | NoSuchFieldException exception) {
            Log.i(TAG, "Error during reflection");
            return connectGattCompat(bluetoothGattCallback, remoteDevice, true);
        }
    }

    private BluetoothGatt connectGattCompat(BluetoothGattCallback bluetoothGattCallback, BluetoothDevice device, boolean autoConnect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.i(TAG, String.format("Connecting to '%s' (%s) using TRANSPORT_LE", device.getName(), device.getAddress()));
            return device.connectGatt(context, autoConnect, bluetoothGattCallback, TRANSPORT_LE);
        } else {
            Log.i(TAG, String.format("Connecting to '%s' (%s)", device.getName(), device.getAddress()));
            return device.connectGatt(context, autoConnect, bluetoothGattCallback);
        }
    }

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
        //      Log.i(TAG,"Found constructor with args count = " + bluetoothGattConstructor.getParameterTypes().length);

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
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }

        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, String.format("connection timout, disconnecting '%s'", peripheral.getName()));
                disconnect();
                completeDisconnect(true, GATT_CONN_TIMEOUT);
                timeoutRunnable = null;
            }
        };

        timeoutHandler.postDelayed(timeoutRunnable, CONNECTION_TIMEOUT_IN_MS);
    }

    private void cancelConnectionTimer() {
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }
}