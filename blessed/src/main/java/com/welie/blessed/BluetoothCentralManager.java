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

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central Manager class to scan and connect with bluetooth peripherals.
 */
@SuppressWarnings({"SpellCheckingInspection", "WeakerAccess", "UnusedReturnValue", "MissingPermission"})
public class BluetoothCentralManager {

    private static final String TAG = BluetoothCentralManager.class.getSimpleName();
    private static final long SCAN_TIMEOUT = 180_000L;
    private static final int SCAN_RESTART_DELAY = 1000;
    private static final int MAX_CONNECTION_RETRIES = 1;
    private static final Transport DEFAULT_TRANSPORT = Transport.LE;

    private static final String NO_PERIPHERAL_ADDRESS_PROVIDED = "no peripheral address provided";
    private static final String NO_VALID_PERIPHERAL_PROVIDED = "no valid peripheral provided";
    private static final String NO_VALID_PERIPHERAL_CALLBACK_SPECIFIED = "no valid peripheral callback specified";
    private static final String CANNOT_CONNECT_TO_PERIPHERAL_BECAUSE_BLUETOOTH_IS_OFF = "cannot connect to peripheral because Bluetooth is off";

    private @NotNull final Context context;
    private @NotNull final Handler callBackHandler;
    private @NotNull final BluetoothAdapter bluetoothAdapter;
    private @Nullable volatile BluetoothLeScanner bluetoothScanner;
    private @Nullable volatile BluetoothLeScanner autoConnectScanner;
    private @NotNull final BluetoothCentralManagerCallback bluetoothCentralManagerCallback;
    protected @NotNull final Map<String, BluetoothPeripheral> connectedPeripherals = new ConcurrentHashMap<>();
    protected @NotNull final Map<String, BluetoothPeripheral> unconnectedPeripherals = new ConcurrentHashMap<>();
    private @NotNull final Map<String, BluetoothPeripheral> scannedPeripherals = new ConcurrentHashMap<>();
    private @NotNull final List<String> reconnectPeripheralAddresses = new ArrayList<>();
    private @NotNull final Map<String, BluetoothPeripheralCallback> reconnectCallbacks = new ConcurrentHashMap<>();
    private @NotNull String[] scanPeripheralNames = new String[0];
    private @NotNull final Handler mainHandler = new Handler(Looper.getMainLooper());
    private @Nullable Runnable timeoutRunnable;
    private @Nullable Runnable autoConnectRunnable;
    private @NotNull final Object connectLock = new Object();
    private @NotNull final Object scanLock = new Object();
    private @Nullable volatile ScanCallback currentCallback;
    private @Nullable List<ScanFilter> currentFilters;
    private @NotNull ScanSettings scanSettings;
    private @NotNull final ScanSettings autoConnectScanSettings;
    private @NotNull final Map<String, Integer> connectionRetries = new ConcurrentHashMap<>();
    private @NotNull final Map<String, String> pinCodes = new ConcurrentHashMap<>();
    private @NotNull Transport transport = DEFAULT_TRANSPORT;

    //region Callbacks

    private final ScanCallback scanByNameCallback = new ScanCallback() {
        @Override
        public void onScanResult(final int callbackType, final ScanResult result) {
            synchronized (this) {
                final String deviceName = result.getDevice().getName();
                if (deviceName == null) return;

                for (String name : scanPeripheralNames) {
                    if (deviceName.contains(name)) {
                        sendScanResult(result);
                        return;
                    }
                }
            }
        }

        @Override
        public void onScanFailed(final int errorCode) {
            stopScan();
            sendScanFailed(ScanFailure.fromValue(errorCode));
        }
    };

    private final ScanCallback defaultScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(final int callbackType, final ScanResult result) {
            synchronized (this) {
                sendScanResult(result);
            }
        }

        @Override
        public void onScanFailed(final int errorCode) {
            stopScan();
            sendScanFailed(ScanFailure.fromValue(errorCode));
        }
    };

    private void sendScanResult(@NotNull final ScanResult result) {
        callBackHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isScanning()) {
                    final BluetoothPeripheral peripheral = getPeripheral(result.getDevice().getAddress());
                    peripheral.setDevice(result.getDevice());
                    bluetoothCentralManagerCallback.onDiscoveredPeripheral(peripheral, result);
                }
            }
        });
    }

    private void sendScanFailed(@NotNull final ScanFailure scanFailure) {
        currentCallback = null;
        currentFilters = null;
        callBackHandler.post(new Runnable() {
            @Override
            public void run() {
                Logger.e(TAG,"scan failed with error code %d (%s)", scanFailure.value, scanFailure);
                bluetoothCentralManagerCallback.onScanFailed(scanFailure);
            }
        });
    }

    private final ScanCallback autoConnectScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(final int callbackType, final ScanResult result) {
            synchronized (this) {
                if (!isAutoScanning()) return;

                Logger.d(TAG,"peripheral with address '%s' found", result.getDevice().getAddress());
                stopAutoconnectScan();

                final String deviceAddress = result.getDevice().getAddress();
                final BluetoothPeripheral peripheral = unconnectedPeripherals.get(deviceAddress);
                final BluetoothPeripheralCallback callback = reconnectCallbacks.get(deviceAddress);

                reconnectPeripheralAddresses.remove(deviceAddress);
                reconnectCallbacks.remove(deviceAddress);
                removePeripheralFromCaches(deviceAddress);

                if (peripheral != null && callback != null) {
                    connectPeripheral(peripheral, callback);
                }

                if (reconnectPeripheralAddresses.size() > 0) {
                    scanForAutoConnectPeripherals();
                }
            }
        }

        @Override
        public void onScanFailed(final int errorCode) {
            final ScanFailure scanFailure = ScanFailure.fromValue(errorCode);
            Logger.e(TAG,"autoConnect scan failed with error code %d (%s)", errorCode, scanFailure);
            stopAutoconnectScan();
            callBackHandler.post(new Runnable() {
                @Override
                public void run() {
                    bluetoothCentralManagerCallback.onScanFailed(scanFailure);
                }
            });
        }
    };

    protected final BluetoothPeripheral.InternalCallback internalCallback = new BluetoothPeripheral.InternalCallback() {
        @Override
        public void connecting(@NotNull final BluetoothPeripheral peripheral) {
            callBackHandler.post(new Runnable() {
                @Override
                public void run() {
                    bluetoothCentralManagerCallback.onConnectingPeripheral(peripheral);
                }
            });
        }

        @Override
        public void connected(@NotNull final BluetoothPeripheral peripheral) {
            final String peripheralAddress = peripheral.getAddress();
            removePeripheralFromCaches(peripheralAddress);
            connectedPeripherals.put(peripheralAddress, peripheral);

            callBackHandler.post(new Runnable() {
                @Override
                public void run() {
                    bluetoothCentralManagerCallback.onConnectedPeripheral(peripheral);
                }
            });
        }

        @Override
        public void connectFailed(@NotNull final BluetoothPeripheral peripheral, @NotNull final HciStatus status) {
            final String peripheralAddress = peripheral.getAddress();

            // Get the number of retries for this peripheral
            int nrRetries = 0;
            final Integer retries = connectionRetries.get(peripheralAddress);
            if (retries != null) nrRetries = retries;

            removePeripheralFromCaches(peripheralAddress);

            // Retry connection or conclude the connection has failed
            if (nrRetries < MAX_CONNECTION_RETRIES && status != HciStatus.CONNECTION_FAILED_ESTABLISHMENT) {
                Logger.i(TAG,"retrying connection to '%s' (%s)", peripheral.getName(), peripheralAddress);
                nrRetries++;
                connectionRetries.put(peripheralAddress, nrRetries);
                unconnectedPeripherals.put(peripheralAddress, peripheral);
                peripheral.connect();
            } else {
                Logger.i(TAG,"connection to '%s' (%s) failed", peripheral.getName(), peripheralAddress);
                callBackHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        bluetoothCentralManagerCallback.onConnectionFailed(peripheral, status);
                    }
                });
            }
        }

        @Override
        public void disconnecting(@NotNull final BluetoothPeripheral peripheral) {
            callBackHandler.post(new Runnable() {
                @Override
                public void run() {
                    bluetoothCentralManagerCallback.onDisconnectingPeripheral(peripheral);
                }
            });
        }

        @Override
        public void disconnected(@NotNull final BluetoothPeripheral peripheral, @NotNull final HciStatus status) {
            removePeripheralFromCaches(peripheral.getAddress());
            callBackHandler.post(new Runnable() {
                @Override
                public void run() {
                    bluetoothCentralManagerCallback.onDisconnectedPeripheral(peripheral, status);
                }
            });
        }

        @Override
        public @Nullable String getPincode(@NotNull final BluetoothPeripheral peripheral) {
            return pinCodes.get(peripheral.getAddress());
        }
    };

    private void removePeripheralFromCaches(String peripheralAddress) {
        connectedPeripherals.remove(peripheralAddress);
        unconnectedPeripherals.remove(peripheralAddress);
        scannedPeripherals.remove(peripheralAddress);
        connectionRetries.remove(peripheralAddress);
    }

    //endregion

    /**
     * Construct a new BluetoothCentralManager object
     *
     * @param context                  Android application environment.
     * @param bluetoothCentralManagerCallback the callback to call for updates
     * @param handler                  Handler to use for callbacks.
     */
    public BluetoothCentralManager(@NotNull final Context context, @NotNull final BluetoothCentralManagerCallback bluetoothCentralManagerCallback, @NotNull final Handler handler) {
        this.context = Objects.requireNonNull(context, "no valid context provided");
        this.bluetoothCentralManagerCallback = Objects.requireNonNull(bluetoothCentralManagerCallback, "no valid bluetoothCallback provided");
        this.callBackHandler = Objects.requireNonNull(handler, "no valid handler provided");
        final BluetoothManager manager = Objects.requireNonNull((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE),"cannot get BluetoothManager");
        this.bluetoothAdapter = Objects.requireNonNull(manager.getAdapter(), "no bluetooth adapter found");
        this.autoConnectScanSettings = getScanSettings(ScanMode.LOW_POWER);
        this.scanSettings = getScanSettings(ScanMode.LOW_LATENCY);

        // Register for broadcasts on BluetoothAdapter state change
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(adapterStateReceiver, filter);
    }

    /**
     * Closes BluetoothCentralManager and cleans up internals. BluetoothCentralManager will not work anymore after this is called.
     */
    public void close() {
        scannedPeripherals.clear();
        unconnectedPeripherals.clear();
        connectedPeripherals.clear();
        reconnectCallbacks.clear();
        reconnectPeripheralAddresses.clear();
        connectionRetries.clear();
        pinCodes.clear();
        context.unregisterReceiver(adapterStateReceiver);
    }

    /**
     * Enable logging
     */
    public void enableLogging() {
        Logger.enabled = true;
    }

    /**
     * Disable logging
     */
    public void disableLogging() {
        Logger.enabled = false;
    }

    private @NotNull ScanSettings getScanSettings(@NotNull final ScanMode scanMode) {
        Objects.requireNonNull(scanMode, "scanMode is null");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return new ScanSettings.Builder()
                    .setScanMode(scanMode.value)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                    .setReportDelay(0L)
                    .build();
        } else {
            return new ScanSettings.Builder()
                    .setScanMode(scanMode.value)
                    .setReportDelay(0L)
                    .build();
        }
    }

    /**
     * Set the default scanMode.
     *
     * @param scanMode the scanMode to set
     */
    public void setScanMode(@NotNull final ScanMode scanMode) {
        Objects.requireNonNull(scanMode);

        scanSettings = getScanSettings(scanMode);
    }

    /**
     * Get the transport to be used during connection phase.
     *
     * @return transport
     */
    public @NotNull Transport getTransport() {
        return transport;
    }

    /**
     * Set the transport to be used when creating instances of {@link BluetoothPeripheral}.
     *
     * @param transport the Transport to set
     */
    public void setTransport(@NotNull final Transport transport) {
        this.transport = Objects.requireNonNull(transport, "not a valid transport");
    }

    private void startScan(@NotNull final List<ScanFilter> filters, @NotNull final ScanSettings scanSettings, @NotNull final ScanCallback scanCallback) {
        if (bleNotReady()) return;

        if (isScanning()) {
            Logger.e(TAG,"other scan still active, stopping scan");
            stopScan();
        }

        if (bluetoothScanner == null) {
            bluetoothScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        if (bluetoothScanner != null) {
            setScanTimer();
            currentCallback = scanCallback;
            currentFilters = filters;
            bluetoothScanner.startScan(filters, scanSettings, scanCallback);
            Logger.i(TAG,"scan started");
        } else {
            Logger.e(TAG,"starting scan failed");
        }
    }

    /**
     * Scan for peripherals that advertise at least one of the specified service UUIDs.
     *
     * @param serviceUUIDs an array of service UUIDs
     */
    public void scanForPeripheralsWithServices(@NotNull final UUID[] serviceUUIDs) {
        Objects.requireNonNull(serviceUUIDs, "no service UUIDs supplied");

        if (serviceUUIDs.length == 0) {
            throw new IllegalArgumentException("at least one service UUID  must be supplied");
        }

        final List<ScanFilter> filters = new ArrayList<>();
        for (UUID serviceUUID : serviceUUIDs) {
            ScanFilter filter = new ScanFilter.Builder()
                    .setServiceUuid(new ParcelUuid(serviceUUID))
                    .build();
            filters.add(filter);
        }

        startScan(filters, scanSettings, defaultScanCallback);
    }

    /**
     * Scan for peripherals with advertisement names containing any of the specified peripheral names.
     *
     * <p>Substring matching is used so only a partial peripheral names has to be supplied.
     *
     * @param peripheralNames array of partial peripheral names
     */
    public void scanForPeripheralsWithNames(@NotNull final String[] peripheralNames) {
        Objects.requireNonNull(peripheralNames, "no peripheral names supplied");

        if (peripheralNames.length == 0) {
            throw new IllegalArgumentException("at least one peripheral name must be supplied");
        }

        // Start the scanner with no filter because we'll do the filtering ourselves
        scanPeripheralNames = peripheralNames;
        startScan(Collections.<ScanFilter>emptyList(), scanSettings, scanByNameCallback);
    }

    /**
     * Scan for peripherals that have any of the specified peripheral mac addresses.
     *
     * @param peripheralAddresses array of peripheral mac addresses to scan for
     */
    public void scanForPeripheralsWithAddresses(@NotNull final String[] peripheralAddresses) {
        Objects.requireNonNull(peripheralAddresses, "no peripheral addresses supplied");

        if (peripheralAddresses.length == 0) {
            throw new IllegalArgumentException("at least one peripheral address must be supplied");
        }

        final List<ScanFilter> filters = new ArrayList<>();
        for (String address : peripheralAddresses) {
            if (BluetoothAdapter.checkBluetoothAddress(address)) {
                ScanFilter filter = new ScanFilter.Builder()
                        .setDeviceAddress(address)
                        .build();
                filters.add(filter);
            } else {
                Logger.e(TAG,"%s is not a valid address. Make sure all alphabetic characters are uppercase.", address);
            }
        }

        startScan(filters, scanSettings, defaultScanCallback);
    }

    /**
     * Scan for any peripheral that matches the supplied filters
     *
     * @param filters A list of ScanFilters
     */
    public void scanForPeripheralsUsingFilters(@NotNull final List<ScanFilter> filters) {
        Objects.requireNonNull(filters, "no filters supplied");

        if (filters.isEmpty()) {
            throw new IllegalArgumentException("at least one scan filter must be supplied");
        }

        startScan(filters, scanSettings, defaultScanCallback);
    }

    /**
     * Scan for any peripheral that is advertising.
     */
    public void scanForPeripherals() {
        startScan(Collections.<ScanFilter>emptyList(), scanSettings, defaultScanCallback);
    }

    /**
     * Scan for peripherals that need to be autoconnected but are not cached
     */
    private void scanForAutoConnectPeripherals() {
        if (bleNotReady()) return;

        if (autoConnectScanner != null) {
            stopAutoconnectScan();
        }

        autoConnectScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (autoConnectScanner != null) {
            final List<ScanFilter> filters = new ArrayList<>();
            for (String address : reconnectPeripheralAddresses) {
                ScanFilter filter = new ScanFilter.Builder()
                        .setDeviceAddress(address)
                        .build();
                filters.add(filter);
            }

            autoConnectScanner.startScan(filters, autoConnectScanSettings, autoConnectScanCallback);
            Logger.d(TAG,"started scanning to autoconnect peripherals (" + reconnectPeripheralAddresses.size() + ")");
            setAutoConnectTimer();
        } else {
            Logger.e(TAG,"starting autoconnect scan failed");
        }
    }

    private void stopAutoconnectScan() {
        cancelAutoConnectTimer();
        if (autoConnectScanner != null) {
            try {
                autoConnectScanner.stopScan(autoConnectScanCallback);
            } catch (Exception ignore) {}
            autoConnectScanner = null;
            Logger.i(TAG,"autoscan stopped");
        }
    }

    private boolean isAutoScanning() {
        return autoConnectScanner != null;
    }

    /**
     * Stop scanning for peripherals.
     */
    public void stopScan() {
        synchronized (scanLock) {
            cancelTimeoutTimer();
            if (isScanning()) {
                // Note that we can't call stopScan if the adapter is off
                // On some phones like the Nokia 8, the adapter will be already off at this point
                // So add a try/catch to handle any exceptions
                try {
                    if (bluetoothScanner != null) {
                        bluetoothScanner.stopScan(currentCallback);
                        currentCallback = null;
                        currentFilters = null;
                        Logger.i(TAG, "scan stopped");
                    }
                } catch (Exception ignore) {
                    Logger.e(TAG, "caught exception in stopScan");
                }
            } else {
                Logger.i(TAG, "no scan to stop because no scan is running");
            }

            bluetoothScanner = null;
            scannedPeripherals.clear();
        }
    }

    /**
     * Check if a scanning is active
     *
     * @return true if a scan is active, otherwise false
     */
    public boolean isScanning() {
        return (bluetoothScanner != null && currentCallback != null);
    }

    /**
     * Connect to a known peripheral immediately. The peripheral must have been found by scanning for this call to succeed. This method will time out in max 30 seconds on most phones and in 5 seconds on Samsung phones.
     * If the peripheral is already connected, no connection attempt will be made. This method is asynchronous and there can be only one outstanding connect.
     *
     * @param peripheral BLE peripheral to connect with
     */
    public void connectPeripheral(@NotNull final BluetoothPeripheral peripheral, @NotNull final BluetoothPeripheralCallback peripheralCallback) {
        synchronized (connectLock) {
            Objects.requireNonNull(peripheral, NO_VALID_PERIPHERAL_PROVIDED);
            Objects.requireNonNull(peripheralCallback, NO_VALID_PERIPHERAL_CALLBACK_SPECIFIED);

            if (connectedPeripherals.containsKey(peripheral.getAddress())) {
                Logger.w(TAG,"already connected to %s'", peripheral.getAddress());
                return;
            }

            if (unconnectedPeripherals.containsKey(peripheral.getAddress())) {
                Logger.w(TAG,"already connecting to %s'", peripheral.getAddress());
                return;
            }

            if (!bluetoothAdapter.isEnabled()) {
                Logger.e(TAG, CANNOT_CONNECT_TO_PERIPHERAL_BECAUSE_BLUETOOTH_IS_OFF);
                return;
            }

            // Check if the peripheral is cached or not. If not, issue a warning because connection may fail
            // This is because Android will guess the address type and when incorrect it will fail
            if (peripheral.isUncached()) {
                Logger.w(TAG,"peripheral with address '%s' is not in the Bluetooth cache, hence connection may fail", peripheral.getAddress());
            }

            peripheral.setPeripheralCallback(peripheralCallback);
            scannedPeripherals.remove(peripheral.getAddress());
            unconnectedPeripherals.put(peripheral.getAddress(), peripheral);
            peripheral.connect();
        }
    }

    /**
     * Connect to a known peripheral and bond immediately. The peripheral must have been found by scanning for this call to succeed. This method will time out in max 30 seconds on most phones and in 5 seconds on Samsung phones.
     * If the peripheral is already connected, no connection attempt will be made. This method is asynchronous and there can be only one outstanding connect.
     *
     * @param peripheral BLE peripheral to connect with
     */
    public void createBond(@NotNull final BluetoothPeripheral peripheral, @NotNull final BluetoothPeripheralCallback peripheralCallback) {
        synchronized (connectLock) {
            Objects.requireNonNull(peripheral, NO_VALID_PERIPHERAL_PROVIDED);
            Objects.requireNonNull(peripheralCallback, NO_VALID_PERIPHERAL_CALLBACK_SPECIFIED);

            if (connectedPeripherals.containsKey(peripheral.getAddress())) {
                Logger.w(TAG,"already connected to %s'", peripheral.getAddress());
                return;
            }

            if (unconnectedPeripherals.containsKey(peripheral.getAddress())) {
                Logger.w(TAG,"already connecting to %s'", peripheral.getAddress());
                return;
            }

            if (!bluetoothAdapter.isEnabled()) {
                Logger.e(TAG, CANNOT_CONNECT_TO_PERIPHERAL_BECAUSE_BLUETOOTH_IS_OFF);
                return;
            }

            // Check if the peripheral is cached or not. If not, issue a warning because connection may fail
            // This is because Android will guess the address type and when incorrect it will fail
            if (peripheral.isUncached()) {
                Logger.w(TAG,"peripheral with address '%s' is not in the Bluetooth cache, hence connection may fail", peripheral.getAddress());
            }

            peripheral.setPeripheralCallback(peripheralCallback);
            peripheral.createBond();
        }
    }

    /**
     * Automatically connect to a peripheral when it is advertising. It is not necessary to scan for the peripheral first. This call is asynchronous and will not time out.
     *
     * @param peripheral the peripheral
     */
    public void autoConnectPeripheral(@NotNull final BluetoothPeripheral peripheral, @NotNull final BluetoothPeripheralCallback peripheralCallback) {
        synchronized (connectLock) {
            Objects.requireNonNull(peripheral, NO_VALID_PERIPHERAL_PROVIDED);
            Objects.requireNonNull(peripheralCallback, NO_VALID_PERIPHERAL_CALLBACK_SPECIFIED);

            if (connectedPeripherals.containsKey(peripheral.getAddress())) {
                Logger.w(TAG,"already connected to %s'", peripheral.getAddress());
                return;
            }

            if (unconnectedPeripherals.get(peripheral.getAddress()) != null) {
                Logger.w(TAG,"already issued autoconnect for '%s' ", peripheral.getAddress());
                return;
            }

            if (!bluetoothAdapter.isEnabled()) {
                Logger.e(TAG, CANNOT_CONNECT_TO_PERIPHERAL_BECAUSE_BLUETOOTH_IS_OFF);
                return;
            }

            // Check if the peripheral is uncached and start autoConnectPeripheralByScan
            if (peripheral.isUncached()) {
                Logger.d(TAG,"peripheral with address '%s' not in Bluetooth cache, autoconnecting by scanning", peripheral.getAddress());
                scannedPeripherals.remove(peripheral.getAddress());
                unconnectedPeripherals.put(peripheral.getAddress(), peripheral);
                autoConnectPeripheralByScan(peripheral.getAddress(), peripheralCallback);
                return;
            }

            if (peripheral.getType() == PeripheralType.CLASSIC) {
                Logger.e(TAG,"peripheral does not support Bluetooth LE");
                return;
            }

            peripheral.setPeripheralCallback(peripheralCallback);
            scannedPeripherals.remove(peripheral.getAddress());
            unconnectedPeripherals.put(peripheral.getAddress(), peripheral);
            peripheral.autoConnect();
        }
    }

    private void autoConnectPeripheralByScan(String peripheralAddress, BluetoothPeripheralCallback peripheralCallback) {
        if (reconnectPeripheralAddresses.contains(peripheralAddress)) {
            Logger.w(TAG,"peripheral already on list for reconnection");
            return;
        }

        reconnectPeripheralAddresses.add(peripheralAddress);
        reconnectCallbacks.put(peripheralAddress, peripheralCallback);
        scanForAutoConnectPeripherals();
    }

    /**
     * Cancel an active or pending connection for a peripheral.
     *
     * @param peripheral the peripheral
     */
    public void cancelConnection(@NotNull final BluetoothPeripheral peripheral) {
        Objects.requireNonNull(peripheral, NO_VALID_PERIPHERAL_PROVIDED);

        // First check if we are doing a reconnection scan for this peripheral
        final String peripheralAddress = peripheral.getAddress();
        if (reconnectPeripheralAddresses.contains(peripheralAddress)) {
            reconnectPeripheralAddresses.remove(peripheralAddress);
            reconnectCallbacks.remove(peripheralAddress);
            unconnectedPeripherals.remove(peripheralAddress);
            stopAutoconnectScan();
            Logger.d(TAG,"cancelling autoconnect for %s", peripheralAddress);
            callBackHandler.post(new Runnable() {
                @Override
                public void run() {
                    bluetoothCentralManagerCallback.onDisconnectedPeripheral(peripheral, HciStatus.SUCCESS);
                }
            });

            // If there are any devices left, restart the reconnection scan
            if (reconnectPeripheralAddresses.size() > 0) {
                scanForAutoConnectPeripherals();
            }
            return;
        }

        // Only cancel connectioins if it is an known peripheral
        if (unconnectedPeripherals.containsKey(peripheralAddress) || connectedPeripherals.containsKey(peripheralAddress)) {
            peripheral.cancelConnection();
        } else {
            Logger.e(TAG,"cannot cancel connection to unknown peripheral %s", peripheralAddress);
        }
    }

    /**
     * Autoconnect to a batch of peripherals.
     * <p>
     * Use this function to autoConnect to a batch of peripherals, instead of calling autoConnect on each of them.
     * Calling autoConnect on many peripherals may cause Android scanning limits to kick in, which is avoided by using autoConnectPeripheralsBatch.
     *
     * @param batch the map of peripherals and their callbacks to autoconnect to
     */
    public void autoConnectPeripheralsBatch(@NotNull final Map<BluetoothPeripheral, BluetoothPeripheralCallback> batch) {
        Objects.requireNonNull(batch, "no valid batch provided");

        if (!bluetoothAdapter.isEnabled()) {
            Logger.e(TAG, CANNOT_CONNECT_TO_PERIPHERAL_BECAUSE_BLUETOOTH_IS_OFF);
            return;
        }

        // Find the uncached peripherals and issue autoConnectPeripheral for the cached ones
        final Map<BluetoothPeripheral, BluetoothPeripheralCallback> uncachedPeripherals = new HashMap<>();
        for (BluetoothPeripheral peripheral : batch.keySet()) {
            if (peripheral.isUncached()) {
                uncachedPeripherals.put(peripheral, batch.get(peripheral));
            } else {
                autoConnectPeripheral(peripheral, batch.get(peripheral));
            }
        }

        // Add uncached peripherals to list of peripherals to scan for
        if (!uncachedPeripherals.isEmpty()) {
            for (BluetoothPeripheral peripheral : uncachedPeripherals.keySet()) {
                final String peripheralAddress = peripheral.getAddress();
                reconnectPeripheralAddresses.add(peripheralAddress);
                reconnectCallbacks.put(peripheralAddress, uncachedPeripherals.get(peripheral));
                unconnectedPeripherals.put(peripheralAddress, peripheral);
            }
            scanForAutoConnectPeripherals();
        }
    }

    /**
     * Get a peripheral object matching the specified mac address.
     *
     * @param peripheralAddress mac address
     * @return a BluetoothPeripheral object matching the specified mac address or null if it was not found
     */
    public @NotNull BluetoothPeripheral getPeripheral(@NotNull final String peripheralAddress) {
        Objects.requireNonNull(peripheralAddress, NO_PERIPHERAL_ADDRESS_PROVIDED);

        if (!BluetoothAdapter.checkBluetoothAddress(peripheralAddress)) {
            final String message = String.format("%s is not a valid bluetooth address. Make sure all alphabetic characters are uppercase.", peripheralAddress);
            throw new IllegalArgumentException(message);
        }

        if (connectedPeripherals.containsKey(peripheralAddress)) {
            return Objects.requireNonNull(connectedPeripherals.get(peripheralAddress));
        } else if (unconnectedPeripherals.containsKey(peripheralAddress)) {
            return Objects.requireNonNull(unconnectedPeripherals.get(peripheralAddress));
        } else if (scannedPeripherals.containsKey(peripheralAddress)) {
            return Objects.requireNonNull(scannedPeripherals.get(peripheralAddress));
        } else {
            final BluetoothPeripheral peripheral = new BluetoothPeripheral(context, bluetoothAdapter.getRemoteDevice(peripheralAddress), internalCallback, new BluetoothPeripheralCallback.NULL(), callBackHandler, transport);
            scannedPeripherals.put(peripheralAddress, peripheral);
            return peripheral;
        }
    }

    /**
     * Get the list of connected peripherals.
     *
     * @return list of connected peripherals
     */
    public @NotNull List<BluetoothPeripheral> getConnectedPeripherals() {
        return new ArrayList<>(connectedPeripherals.values());
    }

    private boolean bleNotReady() {
        if (isBleSupported()) {
            if (isBluetoothEnabled()) {
                return !permissionsGranted();
            }
        }
        return true;
    }

    private boolean isBleSupported() {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return true;
        }

        Logger.e(TAG,"BLE not supported");
        return false;
    }

    /**
     * Check if Bluetooth is enabled
     *
     * @return true is Bluetooth is enabled, otherwise false
     */
    public boolean isBluetoothEnabled() {
        if (bluetoothAdapter.isEnabled()) {
            return true;
        }
        Logger.e(TAG,"Bluetooth disabled");
        return false;
    }

    private boolean permissionsGranted() {
        final int targetSdkVersion = context.getApplicationInfo().targetSdkVersion;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetSdkVersion >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("app does not have BLUETOOTH_SCAN permission, cannot start scan");
            }
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("app does not have BLUETOOTH_CONNECT permission, cannot connect");
            } else return true;
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetSdkVersion >= Build.VERSION_CODES.Q) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("app does not have ACCESS_FINE_LOCATION permission, cannot start scan");
            } else return true;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("app does not have ACCESS_COARSE_LOCATION permission, cannot start scan");
            } else return true;
        } else {
            return true;
        }
    }

    /**
     * Set scan timeout timer, timeout time is {@code SCAN_TIMEOUT}.
     * If timeout is executed the scan is stopped and automatically restarted. This is done to avoid Android 9 scan restrictions
     */
    private void setScanTimer() {
        cancelTimeoutTimer();

        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Logger.d(TAG,"scanning timeout, restarting scan");
                final ScanCallback callback = currentCallback;
                final List<ScanFilter> filters = currentFilters != null ? currentFilters : Collections.<ScanFilter>emptyList();
                stopScan();

                // Restart the scan and timer
                callBackHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            startScan(filters, scanSettings, callback);
                        }
                    }
                }, SCAN_RESTART_DELAY);
            }
        };

        mainHandler.postDelayed(timeoutRunnable, SCAN_TIMEOUT);
    }

    /**
     * Cancel the scan timeout timer
     */
    private void cancelTimeoutTimer() {
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    /**
     * Set scan timeout timer, timeout time is {@code SCAN_TIMEOUT}.
     * If timeout is executed the scan is stopped and automatically restarted. This is done to avoid Android 9 scan restrictions
     */
    private void setAutoConnectTimer() {
        cancelAutoConnectTimer();
        autoConnectRunnable = new Runnable() {
            @Override
            public void run() {
                Logger.d(TAG,"autoconnect scan timeout, restarting scan");

                // Stop previous autoconnect scans if any
                stopAutoconnectScan();

                // Restart the auto connect scan and timer
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        scanForAutoConnectPeripherals();
                    }
                }, SCAN_RESTART_DELAY);
            }
        };

        mainHandler.postDelayed(autoConnectRunnable, SCAN_TIMEOUT);
    }

    /**
     * Cancel the scan timeout timer
     */
    private void cancelAutoConnectTimer() {
        if (autoConnectRunnable != null) {
            mainHandler.removeCallbacks(autoConnectRunnable);
            autoConnectRunnable = null;
        }
    }

    /**
     * Set a fixed PIN code for a peripheral that asks for a PIN code during bonding.
     * <p>
     * This PIN code will be used to programmatically bond with the peripheral when it asks for a PIN code. The normal PIN popup will not appear anymore.
     * </p>
     * Note that this only works for peripherals with a fixed PIN code.
     *
     * @param peripheralAddress the address of the peripheral
     * @param pin               the 6 digit PIN code as a string, e.g. "123456"
     * @return true if the pin code and peripheral address are valid and stored internally
     */
    public boolean setPinCodeForPeripheral(@NotNull final String peripheralAddress, @NotNull final String pin) {
        Objects.requireNonNull(peripheralAddress, NO_PERIPHERAL_ADDRESS_PROVIDED);
        Objects.requireNonNull(pin, "no pin provided");

        if (!BluetoothAdapter.checkBluetoothAddress(peripheralAddress)) {
            Logger.e(TAG,"%s is not a valid address. Make sure all alphabetic characters are uppercase.", peripheralAddress);
            return false;
        }

        if (pin.length() != 6) {
            Logger.e(TAG,"%s is not 6 digits long", pin);
            return false;
        }

        pinCodes.put(peripheralAddress, pin);
        return true;
    }

    /**
     * Remove bond for a peripheral.
     *
     * @param peripheralAddress the address of the peripheral
     * @return true if the peripheral was succesfully bonded or it wasn't bonded, false if it was bonded and removing it failed
     */
    public boolean removeBond(@NotNull final String peripheralAddress) {
        Objects.requireNonNull(peripheralAddress, NO_PERIPHERAL_ADDRESS_PROVIDED);

        // Get the set of bonded devices
        final Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

        // See if the device is bonded
        BluetoothDevice peripheralToUnBond = null;
        if (bondedDevices.size() > 0) {
            for (BluetoothDevice device : bondedDevices) {
                if (device.getAddress().equals(peripheralAddress)) {
                    peripheralToUnBond = device;
                }
            }
        } else {
            return true;
        }

        // Try to remove the bond
        if (peripheralToUnBond != null) {
            try {
                Method method = peripheralToUnBond.getClass().getMethod("removeBond", (Class[]) null);
                boolean result = (boolean) method.invoke(peripheralToUnBond, (Object[]) null);
                if (result) {
                    Logger.i(TAG,"Succesfully removed bond for '%s'", peripheralToUnBond.getName());
                }
                return result;
            } catch (Exception e) {
                Logger.i(TAG,"could not remove bond");
                e.printStackTrace();
                return false;
            }
        } else {
            return true;
        }
    }

    /**
     * Make the pairing popup appear in the foreground by doing a 1 sec discovery.
     * <p>
     * If the pairing popup is shown within 60 seconds, it will be shown in the foreground.
     */
    public void startPairingPopupHack() {
        // Check if we are on a Samsung device because those don't need the hack
        final String manufacturer = Build.MANUFACTURER;
        if (!manufacturer.equalsIgnoreCase("samsung")) {
            if (bleNotReady()) return;

            bluetoothAdapter.startDiscovery();

            callBackHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Logger.d(TAG,"popup hack completed");
                    bluetoothAdapter.cancelDiscovery();
                }
            }, 1000);
        }
    }

    /**
     * Some phones, like Google/Pixel phones, don't automatically disconnect devices so this method does it manually
     */
    private void cancelAllConnectionsWhenBluetoothOff() {
        Logger.d(TAG,"disconnect all peripherals because bluetooth is off");
        // Call cancelConnection for connected peripherals
        for (final BluetoothPeripheral peripheral : connectedPeripherals.values()) {
            peripheral.disconnectWhenBluetoothOff();
        }
        connectedPeripherals.clear();

        // Call cancelConnection for unconnected peripherals
        for (final BluetoothPeripheral peripheral : unconnectedPeripherals.values()) {
            peripheral.disconnectWhenBluetoothOff();
        }
        unconnectedPeripherals.clear();

        // Clean up autoconnect by scanning information
        reconnectPeripheralAddresses.clear();
        reconnectCallbacks.clear();
    }

    protected final BroadcastReceiver adapterStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null) return;

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                handleAdapterState(state);
                callBackHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        bluetoothCentralManagerCallback.onBluetoothAdapterStateChanged(state);
                    }
                });
            }
        }
    };

    private void handleAdapterState(final int state) {
        switch (state) {
            case BluetoothAdapter.STATE_OFF:
                // Check if there are any connected peripherals or connections in progress
                if (connectedPeripherals.size() > 0 || unconnectedPeripherals.size() > 0) {
                    cancelAllConnectionsWhenBluetoothOff();
                }
                Logger.d(TAG,"bluetooth turned off");
                break;
            case BluetoothAdapter.STATE_TURNING_OFF:
                // Disconnect connected peripherals
                for (final BluetoothPeripheral peripheral : connectedPeripherals.values()) {
                    peripheral.cancelConnection();
                }

                // Disconnect unconnected peripherals
                for (final BluetoothPeripheral peripheral : unconnectedPeripherals.values()) {
                    peripheral.cancelConnection();
                }

                // Clean up autoconnect by scanning information
                reconnectPeripheralAddresses.clear();
                reconnectCallbacks.clear();

                // Stop all scans so that we are back in a clean state
                if (isScanning()) {
                    stopScan();
                }

                if(isAutoScanning()) {
                    stopAutoconnectScan();
                }

                cancelTimeoutTimer();
                cancelAutoConnectTimer();
                autoConnectScanner = null;
                bluetoothScanner = null;
                Logger.d(TAG,"bluetooth turning off");
                break;
            case BluetoothAdapter.STATE_ON:
                Logger.d(TAG,"bluetooth turned on");

                // On some phones like Nokia 8, this scanner may still have an older active scan from us
                // This happens when bluetooth is toggled. So make sure it is gone.
                bluetoothScanner = bluetoothAdapter.getBluetoothLeScanner();
                if (bluetoothScanner != null && currentCallback != null) {
                    try {
                        bluetoothScanner.stopScan(currentCallback);
                    } catch (Exception ignore) {}
                }
                currentCallback = null;
                currentFilters = null;
                break;
            case BluetoothAdapter.STATE_TURNING_ON:
                Logger.d(TAG,"bluetooth turning on");
                break;
        }
    }
}
