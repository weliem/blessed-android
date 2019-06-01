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

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Central class to connect and communicate with bluetooth peripherals.
 */
@SuppressWarnings("SpellCheckingInspection")
public class BluetoothCentral {
    private final String TAG = BluetoothCentral.class.getSimpleName();

    // Private constants
    private static final long SCAN_TIMEOUT = 180_000L;
    private static final int SCAN_RESTART_DELAY = 2*1000;
    private static final int MAX_CONNECTION_RETRIES = 1;
    private static final int MAX_CONNECTED_PERIPHERALS = 7;

    // Scanning error codes
    public static final int SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES = 5;

    // Private enums
    private enum BluetoothCentralMode {IDLE, SCANNING, CONNECTING}

    // Private variables
    private final Context context;
    private final Handler callBackHandler;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothScanner;
    private BluetoothLeScanner autoConnectScanner;
    private final BluetoothCentralCallback bluetoothCentralCallback;
    private final Map<String, BluetoothPeripheral> connectedPeripherals;
    private final Map<String, BluetoothPeripheral> unconnectedPeripherals;
    private BluetoothCentralMode mode = BluetoothCentralMode.IDLE;
    private final List<String> reconnectPeripheralAddresses;
    private final Map<String, BluetoothPeripheralCallback> reconnectCallbacks;
    private String[] scanPeripheralNames;
    private final Handler timeoutHandler;
    private Runnable timeoutRunnable;
    private final Handler autoConnectHandler;
    private Runnable autoConnectRunnable;
    private final Object connectLock = new Object();
    private ScanCallback currentCallback;
    private List<ScanFilter> currentFilters;
    private ScanSettings scanSettings;
    private final ScanSettings autoConnectScanSettings;
    private final Map<String, Integer> connectionRetries = new HashMap<>();

    //region Callbacks

    /**
     * Callback for scan by peripheral name. Do substring filtering before forwarding result.
     */
    private final ScanCallback scanByNameCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            synchronized (this) {
                String deviceName = result.getDevice().getName();
                if(deviceName != null) {
                    for(String name : scanPeripheralNames) {
                        if (deviceName.contains(name)) {
                            callBackHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if(currentCallback != null) {
                                        BluetoothPeripheral peripheral = new BluetoothPeripheral(context, result.getDevice(), internalCallback, null, callBackHandler);
                                        bluetoothCentralCallback.onDiscoveredPeripheral(peripheral, result);
                                    }
                                }
                            });
                            return;
                        }
                    }
                }
            }
        }

        @Override
        public void onScanFailed(final int errorCode) {
            Log.e(TAG, String.format("scan failed with error code %d", errorCode));
            callBackHandler.post(new Runnable() {
                @Override
                public void run() {
                    bluetoothCentralCallback.onScanFailed(errorCode);
                }
            });
        }
    };

    /**
     * Callback for scan by service UUID
     */
    private final ScanCallback scanByServiceUUIDCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            synchronized (this) {
                callBackHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if(currentCallback != null) {
                            BluetoothPeripheral peripheral = new BluetoothPeripheral(context, result.getDevice(), internalCallback, null, callBackHandler);
                            bluetoothCentralCallback.onDiscoveredPeripheral(peripheral, result);
                        }
                    }
                });
            }
        }

        @Override
        public void onScanFailed(final int errorCode) {
            Log.e(TAG, String.format("scan failed with error code %d", errorCode));
            callBackHandler.post(new Runnable() {
                @Override
                public void run() {
                     bluetoothCentralCallback.onScanFailed(errorCode);
                }
            });
        }
    };

    private final ScanCallback autoConnectScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            synchronized (this) {
                // Get the deviceAdress and corresponding callback
                String deviceAddress = result.getDevice().getAddress();
                Log.d(TAG, String.format("peripheral with address '%s' found", deviceAddress));
                BluetoothPeripheralCallback callback = reconnectCallbacks.get(deviceAddress);

                // Clean up first
                reconnectPeripheralAddresses.remove(deviceAddress);
                reconnectCallbacks.remove(deviceAddress);

                // If we have all devices, stop the scan
                if(reconnectPeripheralAddresses.size() == 0) {
                    autoConnectScanner.stopScan(autoConnectScanCallback);
                    autoConnectScanner = null;
                    cancelAutoConnectTimer();
                }

                // The device is now cached so issue normal connect
                connectPeripheral(getPeripheral(deviceAddress), callback);

                // If there are any devices left, restart the reconnection scan
                if(reconnectPeripheralAddresses.size() > 0) {
                    scanForAutoConnectPeripherals();
                }
            }
        }

        @Override
        public void onScanFailed(final int errorCode) {
            Log.e(TAG, String.format("autoconnect scan failed with error code %d", errorCode));
            callBackHandler.post(new Runnable() {
                @Override
                public void run() {
                    bluetoothCentralCallback.onScanFailed(errorCode);
                }
            });
        }
    };

    /**
     * Callback from each connected peripheral
     */
    private final BluetoothPeripheral.InternalCallback internalCallback = new BluetoothPeripheral.InternalCallback() {

        /**
         * Successfully connected with the peripheral, add it to the connected peripherals list
         * and notify the listener.
         * @param peripheral {@link BluetoothPeripheral} that connected.
         */
        @Override
        public void connected(final BluetoothPeripheral peripheral) {
            updateMode(BluetoothCentralMode.IDLE);

            // Remove peripheral from retry map if it is there
            connectionRetries.remove(peripheral.getAddress());

            // Do some administration work
            connectedPeripherals.put(peripheral.getAddress(), peripheral);
            if(connectedPeripherals.size() == MAX_CONNECTED_PERIPHERALS) {
                Log.w(TAG, "maximum amount (7) of connected peripherals reached");
            }

            // Remove peripheral from unconnected peripherals map if it is there
            if(unconnectedPeripherals.get(peripheral.getAddress()) != null) {
                unconnectedPeripherals.remove(peripheral.getAddress());
            }

            // Inform the listener that we are now connected
            callBackHandler.post(new Runnable() {
                @Override
                public void run() {
                    bluetoothCentralCallback.onConnectedPeripheral(peripheral);
                }
            });
        }

        /**
         * The connection with the peripheral failed, remove it from the connected peripheral list
         * and notify the listener.
         * @param peripheral {@link BluetoothPeripheral} of which connect failed.
         */
        @Override
        public void connectFailed(final BluetoothPeripheral peripheral, final int status) {
            updateMode(BluetoothCentralMode.IDLE);

            // Remove from unconnected peripherals list
            if(unconnectedPeripherals.get(peripheral.getAddress()) != null) {
                unconnectedPeripherals.remove(peripheral.getAddress());
            }

            // Get the number of retries for this peripheral
            int nrRetries = 0;
            if(connectionRetries.get(peripheral.getAddress()) != null) {
                Integer retries = connectionRetries.get(peripheral.getAddress());
                if(retries != null) nrRetries = retries;
            }

            // Retry connection or conclude the connection has failed
            if(nrRetries < MAX_CONNECTION_RETRIES && status != BluetoothPeripheral.GATT_CONN_TIMEOUT) {
                Log.i(TAG, String.format("retrying connection to '%s' (%s)",peripheral.getName(), peripheral.getAddress()));
                nrRetries++;
                connectionRetries.put(peripheral.getAddress(), nrRetries);
                unconnectedPeripherals.put(peripheral.getAddress(), peripheral);
                peripheral.autoConnect();
            } else {
                Log.i(TAG, String.format("connection to '%s' (%s) failed", peripheral.getName(), peripheral.getAddress()));
                callBackHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        bluetoothCentralCallback.onConnectionFailed(peripheral, status);
                    }
                });
            }
        }

        /**
         * The peripheral disconnected, remove it from the connected peripherals list
         * and notify the listener.
         * @param peripheral {@link BluetoothPeripheral} that disconnected.
         */
        @Override
        public void disconnected(final BluetoothPeripheral peripheral, final int status) {
            // Remove it from the connected peripherals map
            connectedPeripherals.remove(peripheral.getAddress());

            // Make sure it is also not in unconnected peripherals map
            if(unconnectedPeripherals.get(peripheral.getAddress()) != null) {
                unconnectedPeripherals.remove(peripheral.getAddress());
            }

            // Check if we were still not fully connected, if so reset scanMode so scanning can be restarted
            if(mode == BluetoothCentralMode.CONNECTING) {
                mode = BluetoothCentralMode.IDLE;
            }

            // Trigger callback
            callBackHandler.post(new Runnable() {
                @Override
                public void run() {
                    bluetoothCentralCallback.onDisconnectedPeripheral(peripheral, status);
                }
            });
        }
    };

    //endregion

    /**
     * Construct a new BluetoothCentral object
     *
     * @param context Android application environment.
     * @param bluetoothCentralCallback the callback to call for updates
     * @param handler Handler to use for callbacks.
     */
    public BluetoothCentral(Context context, BluetoothCentralCallback bluetoothCentralCallback, Handler handler) {
        if(context == null) {
            Log.e(TAG, "context is 'null', cannot create BluetoothCentral");
        }
        if(bluetoothCentralCallback == null) {
            Log.e(TAG, "callback is 'null', cannot create BluetoothCentral");
        }
        this.context = context;
        this.bluetoothCentralCallback = bluetoothCentralCallback;
        if(handler != null) {
            this.callBackHandler = handler;
        } else {
            this.callBackHandler = new Handler();
        }
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.connectedPeripherals = new HashMap<>();
        this.unconnectedPeripherals = new HashMap<>();
        this.reconnectCallbacks = new HashMap<>();
        this.timeoutHandler = new Handler();
        this.autoConnectHandler = new Handler();
        this.reconnectPeripheralAddresses = new ArrayList<>();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.autoConnectScanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                    .setReportDelay(0L)
                    .build();
        } else {
            this.autoConnectScanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                    .setReportDelay(0L)
                    .build();
        }
        setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
    }

    /**
     * Set the default scanMode.
     *
     * <p>Must be ScanSettings.SCAN_MODE_LOW_POWER, ScanSettings.SCAN_MODE_LOW_LATENCY, ScanSettings.SCAN_MODE_BALANCED or ScanSettings.SCAN_MODE_OPPORTUNISTIC.
     * The default value is SCAN_MODE_LOW_LATENCY.
     *
     * @param scanMode the scanMode to set
     * @return true if a valid scanMode was provided, otherwise false
     */
    public boolean setScanMode(int scanMode) {
        if(scanMode == ScanSettings.SCAN_MODE_LOW_POWER ||
            scanMode == ScanSettings.SCAN_MODE_LOW_LATENCY ||
            scanMode == ScanSettings.SCAN_MODE_BALANCED ||
            scanMode == ScanSettings.SCAN_MODE_OPPORTUNISTIC) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                this.scanSettings = new ScanSettings.Builder()
                        .setScanMode(scanMode)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                        .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                        .setReportDelay(0L)
                        .build();
            } else {
                this.scanSettings = new ScanSettings.Builder()
                        .setScanMode(scanMode)
                        .setReportDelay(0L)
                        .build();
            }
            return true;
        }
        return false;
    }

    private void startScan(List<ScanFilter> filters, ScanSettings scanSettings, ScanCallback scanCallback) {
        // Check is BLE is available, enabled and all permission granted
        if(!isBleReady()) return;

        // Make sure we are not already scanning, we only want one scan at the time
        if(currentCallback != null) {
            Log.e(TAG, "ERROR: Other scan still active, please stop other scan first");
            return;
        }

        // Get a new scanner object
        if(bluetoothScanner == null) {
            bluetoothScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        // If get scanner was succesful, start the scan
        if (bluetoothScanner != null) {
            // Start the scanner
            updateMode(BluetoothCentralMode.SCANNING);
            currentCallback = scanCallback;
            currentFilters = filters;
            bluetoothScanner.startScan(filters, scanSettings, scanCallback);
            Log.i(TAG, "scan started");
        }  else {
            Log.e(TAG, "ERROR: Start scanning failed");
            updateMode(BluetoothCentralMode.IDLE);
        }
    }

    /**
     * Scan for peripherals that advertise at least one of the specified service UUIDs
     *
     * @param serviceUUIDs an array of service UUIDs
     */
    public void scanForPeripheralsWithServices(final UUID[] serviceUUIDs) {
        // Build filters list
        currentFilters = null;
        if(serviceUUIDs != null) {
            currentFilters = new ArrayList<>();
            for (UUID serviceUUID : serviceUUIDs) {
                ScanFilter filter = new ScanFilter.Builder()
                        .setServiceUuid(new ParcelUuid(serviceUUID))
                        .build();
                currentFilters.add(filter);
            }
        }
        startScan(currentFilters, scanSettings, scanByServiceUUIDCallback);
    }

    /**
     * Scan for peripherals with advertisement names containing any of the specified peripheral names.
     *
     * <p>Substring matching is used so only a partial peripheral names has to be supplied.
     *
     * @param peripheralNames array of partial peripheral names
     */
    public void scanForPeripheralsWithNames(final String[] peripheralNames) {
        // Start the scanner with no filter because we'll do the filtering ourselves
        scanPeripheralNames = peripheralNames;
        startScan(null, scanSettings, scanByNameCallback);
    }

    /**
     * Scan for peripherals that have any of the specified peripheral mac addresses
     *
     * @param peripheralAddresses array of peripheral mac addresses to scan for
     */
    public void scanForPeripheralsWithAddresses(final String[] peripheralAddresses) {
        // Build filters list
        List<ScanFilter> filters = null;
        if (peripheralAddresses != null) {
            filters = new ArrayList<>();
            for (String address : peripheralAddresses) {
                if(BluetoothAdapter.checkBluetoothAddress(address)) {
                    ScanFilter filter = new ScanFilter.Builder()
                            .setDeviceAddress(address)
                            .build();
                    filters.add(filter);
                } else {
                    Log.e(TAG, String.format("%s is not a valid address. Make sure all alphabetic characters are uppercase.", address));
                }
            }
        }
        startScan(filters, scanSettings, scanByServiceUUIDCallback);
    }

    /**
     * Scan for any peripheral that is advertising
     *
     */
    public void scanForPeripherals() {
        startScan(null, scanSettings, scanByServiceUUIDCallback);
    }

    private void scanForAutoConnectPeripherals() {
        // Check is BLE is available, enabled and all permission granted
        if(!isBleReady()) return;

        // Stop previous autoconnect scans if any
        if(autoConnectScanner != null) {
            autoConnectScanner.stopScan(autoConnectScanCallback);
            autoConnectScanner = null;
            cancelAutoConnectTimer();
        }

        // Start the scanner
        autoConnectScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (autoConnectScanner != null) {
            List<ScanFilter> filters = null;
            if (reconnectPeripheralAddresses != null) {
                filters = new ArrayList<>();
                for (String address : reconnectPeripheralAddresses) {
                    ScanFilter filter = new ScanFilter.Builder()
                            .setDeviceAddress(address)
                            .build();
                    filters.add(filter);
                }
            }

            // Start the scanner
            autoConnectScanner.startScan(filters, autoConnectScanSettings, autoConnectScanCallback);
            setAutoConnectTimer();
        }  else {
            Log.e(TAG, "ERROR: Start scanning failed");
        }
    }

    /**
     * Stop scanning for peripherals
     */
    public void stopScan() {
        if(mode == BluetoothCentralMode.SCANNING) {
            Log.i(TAG, "Stop scanning");
            cancelTimeoutTimer();
            if (bluetoothScanner != null) {
                bluetoothScanner.stopScan(currentCallback);
                updateMode(BluetoothCentralMode.IDLE);
            }
            currentCallback = null;
            currentFilters = null;
        }
    }

    /**
     * Connect to a known peripheral immediately. The peripheral must have been found by scanning for this call to succeed. This method will time out in max 30 seconds.
     * If the peripheral is already connected, no connection attempt will be made. This method is asynchronous.
     *
     * @param peripheral BLE peripheral to connect with
     */
    public void connectPeripheral(BluetoothPeripheral peripheral, BluetoothPeripheralCallback peripheralCallback) {
        synchronized (connectLock) {
            // Make sure peripheral is valid
            if(peripheral == null) {
                Log.e(TAG, "no valid peripheral specified, aborting connection");
                return;
            }

            // Check if we are already connected to this peripheral
            if (connectedPeripherals.containsKey(peripheral.getAddress())) {
                Log.w(TAG, String.format("WARNING: Already connected to %s'", peripheral.getAddress()));
                return;
            }

            // Check if we already have an outstanding connection request for this peripheral
            if (unconnectedPeripherals.containsKey(peripheral.getAddress())) {
                Log.w(TAG, String.format("WARNING: Already connecting to %s'", peripheral.getAddress()));
                return;
            }

            // Check if the peripheral is cached or not. If not, abort connection
            int deviceType = peripheral.getType();
            if(deviceType == BluetoothDevice.DEVICE_TYPE_UNKNOWN) {
                // The peripheral is not cached so we cannot autoconnect
                Log.e(TAG,String.format("peripheral with address '%s' not in Bluetooth cache, aborting connection", peripheral.getAddress()));
                return;
            }

            // Check if the peripheral supports BLE
            if(!(deviceType == BluetoothDevice.DEVICE_TYPE_LE || deviceType == BluetoothDevice.DEVICE_TYPE_DUAL)) {
                // This device does not support Bluetooth LE, so we cannot connect
                Log.e(TAG, "peripheral does not support Bluetooth LE");
                return;
            }

            // It is all looking good! Set the callback and prepare to connect
            peripheral.setPeripheralCallback(peripheralCallback);
            unconnectedPeripherals.put(peripheral.getAddress(), peripheral);

            // Now connect
            updateMode(BluetoothCentralMode.CONNECTING);
            peripheral.connect();
        }
    }

    /**
     * Automatically connect to a peripheral when it is advertising. It is not necessary to scan for the peripheral first. This call is asynchronous and will not time out.
     *
     * @param peripheral the peripheral
     */
    public void autoConnectPeripheral(BluetoothPeripheral peripheral, BluetoothPeripheralCallback peripheralCallback) {
        // Make sure we are the only ones executing this method
        synchronized (connectLock) {
            // Make sure peripheral is valid
            if(peripheral == null) {
                Log.e(TAG, "No valid peripheral specified, aborting connection");
                return;
            }

            // Check if we are already connected to this peripheral
            if (connectedPeripherals.containsKey(peripheral.getAddress())) {
                Log.w(TAG, String.format("WARNING: Already connected to %s'", peripheral.getAddress()));
                return;
            }

            // Check if we are not already asking this peripheral for data
            if (unconnectedPeripherals.get(peripheral.getAddress()) != null) {
                Log.w(TAG, String.format("WARNING: Already issued autoconnect for '%s' ", peripheral.getAddress()));
                return;
            }

            // Check if the peripheral is cached or not
            int deviceType = peripheral.getType();
            if(deviceType == BluetoothDevice.DEVICE_TYPE_UNKNOWN) {
                // The peripheral is not cached so we cannot autoconnect
                Log.d(TAG,String.format("peripheral with address '%s' not in Bluetooth cache, autoconnecting by scanning", peripheral.getAddress()));
                autoConnectPeripheralByScan(peripheral.getAddress(), peripheralCallback);
                return;
            }

            // Check if the peripheral supports BLE
            if(!(deviceType == BluetoothDevice.DEVICE_TYPE_LE || deviceType == BluetoothDevice.DEVICE_TYPE_DUAL)) {
                // This device does not support Bluetooth LE, so we cannot connect
                Log.e(TAG, "peripheral does not support Bluetooth LE");
                return;
            }

            // It is all looking good! Set the callback and prepare for autoconnect
            peripheral.setPeripheralCallback(peripheralCallback);
            unconnectedPeripherals.put(peripheral.getAddress(), peripheral);

            // Autoconnect to this peripheral
            peripheral.autoConnect();
        }
    }

    private void autoConnectPeripheralByScan(String peripheralAddress, BluetoothPeripheralCallback peripheralCallback) {
        // Check if this peripheral is already on the list or not
        if(reconnectPeripheralAddresses.contains(peripheralAddress)) {
            Log.w(TAG, "WARNING: Peripheral already on list for reconnection");
            return;
        }

        // Add this peripheral to the list
        reconnectPeripheralAddresses.add(peripheralAddress);
        reconnectCallbacks.put(peripheralAddress, peripheralCallback);

        // Scan to peripherals that need to be autoConnected
        scanForAutoConnectPeripherals();
    }

    /**
     * Cancel an active or pending connection for a peripheral
     *
     * @param peripheral the peripheral
     */
    public void cancelConnection(final BluetoothPeripheral peripheral) {
        // Check if peripheral is valid
        if(peripheral == null) {
            Log.e(TAG, "ERROR: cannot cancel connection, peripheral is null");
            return;
        }

        // Get the peripheral address
        String peripheralAddress = peripheral.getAddress();

        // First check if we are doing a reconnection scan for this peripheral
        if(reconnectPeripheralAddresses.contains(peripheralAddress)) {
            // Clean up first
            reconnectPeripheralAddresses.remove(peripheralAddress);
            reconnectCallbacks.remove(peripheralAddress);
            autoConnectScanner.stopScan(autoConnectScanCallback);
            autoConnectScanner = null;
            cancelAutoConnectTimer();
            Log.d(TAG, String.format("cancelling autoconnect for %s", peripheralAddress));

            // If there are any devices left, restart the reconnection scan
            if(reconnectPeripheralAddresses.size() > 0) {
                scanForAutoConnectPeripherals();
            }

            callBackHandler.post(new Runnable() {
                @Override
                public void run() {
                    bluetoothCentralCallback.onDisconnectedPeripheral(peripheral, BluetoothPeripheral.GATT_SUCCESS);
                }
            });

            return;
        }

        // Check if it is an unconnected peripheral
        if(unconnectedPeripherals.containsKey(peripheralAddress)) {
            BluetoothPeripheral unconnectedPeripheral = unconnectedPeripherals.get(peripheralAddress);
            if(unconnectedPeripheral != null) {
                unconnectedPeripheral.cancelConnection();
            }
            return;
        }

        // Check if this is a connected peripheral
        if(connectedPeripherals.containsKey(peripheralAddress)) {
            BluetoothPeripheral connectedPeripheral = connectedPeripherals.get(peripheralAddress);
            if(connectedPeripheral != null) {
                connectedPeripheral.cancelConnection();
            }
        } else {
            Log.e(TAG, String.format("cannot cancel connection to unknown peripheral %s", peripheralAddress));
        }
    }


    /**
     * Get a peripheral object matching the specified mac address
     *
     * @param peripheralAddress mac address
     * @return a BluetoothPeripheral object matching the specified mac address or null if it was not found
     */
    public BluetoothPeripheral getPeripheral(String peripheralAddress) {
        // Check if it is valid address
        if(!BluetoothAdapter.checkBluetoothAddress(peripheralAddress)) {
            Log.e(TAG, String.format("%s is not a valid address. Make sure all alphabetic characters are uppercase.", peripheralAddress));
            return null;
        }

        // Lookup or create BluetoothPeripheral object
        if(connectedPeripherals.containsKey(peripheralAddress)) {
            return connectedPeripherals.get(peripheralAddress);
        } else if(unconnectedPeripherals.containsKey(peripheralAddress)) {
            return unconnectedPeripherals.get(peripheralAddress);
        } else {
            return new BluetoothPeripheral(context, bluetoothAdapter.getRemoteDevice(peripheralAddress), internalCallback, null, callBackHandler);
        }
    }

    /**
     * Get the list of connected peripherals
     *
     * @return list of connected peripherals
     */
    public List<BluetoothPeripheral> getConnectedPeripherals() {
        return new ArrayList<>(connectedPeripherals.values());
    }

    private boolean isBleReady() {
        if(isBleSupported()) {
            if(isBleEnabled()) {
                return permissionsGranted();
            }
        }
        return false;
    }

    private boolean isBleSupported() {
        if(bluetoothAdapter != null && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return true;
        }

        Log.e(TAG, "ERROR: BLE not supported");
        return false;
    }

    private boolean isBleEnabled() {
        if (bluetoothAdapter.isEnabled()) {
            return true;
        }
        Log.e(TAG, "ERROR: Bluetooth disabled");
        return false;
    }

    private boolean permissionsGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "ERROR: No location permission, cannot scan");
            return false;
        } else {
            return true;
        }
    }

    /**
     * Set scan timeout timer, timeout time is {@code SCAN_TIMEOUT}.
     * If timeout is executed the scan is stopped and automatically restarted. This is done to avoid Android 9 scan restrictions
     */
    private void setScanTimer() {
        // Cancel runnable if it exists
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }

        // Prepare runnable
        this.timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "scanning timeout, restarting scan");
                final ScanCallback callback = currentCallback;
                stopScan();

                // Restart the scan and timer
                callBackHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startScan(currentFilters, scanSettings, callback);
                    }
                }, SCAN_RESTART_DELAY);
            }
        };

        // Start timer
        timeoutHandler.postDelayed(timeoutRunnable, SCAN_TIMEOUT);
    }

    /**
     * Cancel the scan timeout timer
     */
    private void cancelTimeoutTimer() {
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    /**
     * Set scan timeout timer, timeout time is {@code SCAN_TIMEOUT}.
     * If timeout is executed the scan is stopped and automatically restarted. This is done to avoid Android 9 scan restrictions
     */
    private void setAutoConnectTimer() {
        // Cancel runnanle if it exists
        cancelAutoConnectTimer();

        // Prepare autoconnect runnable
        autoConnectRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "autoconnect scan timeout, restarting scan");

                // Stop previous autoconnect scans if any
                if(autoConnectScanner != null) {
                    autoConnectScanner.stopScan(autoConnectScanCallback);
                    autoConnectScanner = null;
                }

                // Restart the auto connect scan and timer
                callBackHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        scanForAutoConnectPeripherals();
                    }
                }, SCAN_RESTART_DELAY);
            }
        };

        // Start autoconnect timer
        autoConnectHandler.postDelayed(autoConnectRunnable, SCAN_TIMEOUT);
    }

    /**
     * Cancel the scan timeout timer
     */
    private void cancelAutoConnectTimer() {
        if (autoConnectRunnable != null) {
            autoConnectHandler.removeCallbacks(autoConnectRunnable);
            autoConnectRunnable = null;
        }
    }

    /**
     * Update the scanmode of central and set timers
     *
     * @param newMode New scanmode of central
     */
    private void updateMode(BluetoothCentralMode newMode) {
        mode = newMode;

        switch (newMode) {
            case IDLE:
                break;
            case SCANNING:
                setScanTimer();
                break;
            case CONNECTING:
                break;
        }
    }

    /**
     * Remove bond for a peripheral
     *
     * @param peripheralAddress the address of the peripheral
     * @return true if the peripheral was succesfully unpaired or it wasn't paired, false if it was paired and removing it failed
     */
    public boolean removeBond(String peripheralAddress) {
        boolean result;
        BluetoothDevice peripheralToUnBond = null;

        // Get the set of bonded devices
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

        // See if the device is bonded
        if (bondedDevices.size() > 0) {
            for(BluetoothDevice device : bondedDevices) {
                if(device.getAddress().equals(peripheralAddress)) {
                    peripheralToUnBond = device;
                }
            }
        } else {
            return true;
        }

        // Try to remove the bond
        if(peripheralToUnBond != null) {
            try {
                Method method = peripheralToUnBond.getClass().getMethod("removeBond", (Class[]) null);
                result = (boolean) method.invoke(peripheralToUnBond, (Object[]) null);
                if (result) {
                    Log.i(TAG, String.format("Succesfully removed bond for '%s'", peripheralToUnBond.getName()));
                }
                return result;
            } catch (Exception e) {
                Log.i(TAG, "ERROR: could not remove bond");
                e.printStackTrace();
                return false;
            }
        } else {
            return true;
        }
    }

    /*
     * Make the pairing popup appear in the foreground by doing a 1 sec discovery.
     * If the pairing popup is shown within 60 seconds, it will be shown in the foreground
     *
     */
    public void startPairingPopupHack() {
        // Check if we are on a Samsung device because those don't need the hack
        String manufacturer = Build.MANUFACTURER;
        if(!manufacturer.equals("samsung")) {
            bluetoothAdapter.startDiscovery();

            callBackHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "popup hack completed");
                    bluetoothAdapter.cancelDiscovery();
                }
            }, 1000);
        }
    }
}
