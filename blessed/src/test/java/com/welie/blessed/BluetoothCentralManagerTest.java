package com.welie.blessed;

import android.Manifest;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES;
import static android.os.Build.VERSION_CODES.M;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE, sdk = { M }, shadows={ShadowBluetoothLEAdapter.class} )
public class BluetoothCentralManagerTest {
    private BluetoothCentralManager central;
    private ShadowApplication application;
    private ShadowBluetoothLEAdapter bluetoothAdapter;

    private Context context;

    @Mock
    private BluetoothLeScanner scanner;

    @Mock
    private BluetoothCentralManagerCallback callback;

    @Mock
    private BluetoothPeripheralCallback peripheralCallback;

    @Captor
    ArgumentCaptor<List<ScanFilter>> scanFiltersCaptor;

    @Captor
    ArgumentCaptor<ScanSettings> scanSettingsCaptor;

    @Captor
    ArgumentCaptor<ScanCallback> scanCallbackCaptor;

    private final Handler handler = new Handler();

    @Before
    public void setUp() {
        openMocks(this);

        application = shadowOf((Application) ApplicationProvider.getApplicationContext());

        bluetoothAdapter = Shadow.extract(ShadowBluetoothLEAdapter.getDefaultAdapter());
        bluetoothAdapter.setEnabled(true);
        bluetoothAdapter.setBluetoothLeScanner(scanner);

        context = spy(ApplicationProvider.getApplicationContext());

        // Setup hardware features
        PackageManager packageManager = context.getPackageManager();
        ShadowPackageManager shadowPackageManager = shadowOf(packageManager);
        shadowPackageManager.setSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE, true);

        central = new BluetoothCentralManager(context, callback, handler);
    }

    @Test
    public void When_a_unfiltered_scan_is_started_then_startScan_is_called_and_when_a_scanResult_comes_in_onDiscoveredPeripheral_is_called() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

        // When
        central.scanForPeripherals();

        // Then
        verify(scanner).startScan(scanFiltersCaptor.capture(), scanSettingsCaptor.capture(), scanCallbackCaptor.capture());
        assertEquals(0, scanFiltersCaptor.getValue().size());

        // When
        ScanResult scanResult = mock(ScanResult.class);
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getAddress()).thenReturn("12:23:34:98:76:54");
        when(scanResult.getDevice()).thenReturn(device);
        bluetoothAdapter.addDevice(device);
        scanCallbackCaptor.getValue().onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        ArgumentCaptor<BluetoothPeripheral> bluetoothPeripheralCaptor = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<ScanResult> scanResultCaptor = ArgumentCaptor.forClass(ScanResult.class);
        verify(callback).onDiscoveredPeripheral(bluetoothPeripheralCaptor.capture(), scanResultCaptor.capture());

        assertEquals(scanResultCaptor.getValue(), scanResult);
        assertEquals(bluetoothPeripheralCaptor.getValue().getAddress(), "12:23:34:98:76:54");
    }

    @Test
    public void When_a_service_filtered_scan_is_started_then_startScan_is_called_with_a_service_filter_and_when_a_matching_scanResult_comes_in_onDiscoveredPeripheral_is_called() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        UUID BLP_SERVICE_UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb");

        // When
        central.scanForPeripheralsWithServices(new UUID[]{BLP_SERVICE_UUID});

        // Then
        verify(scanner).startScan(scanFiltersCaptor.capture(), scanSettingsCaptor.capture(), scanCallbackCaptor.capture());
        assertEquals(1, scanFiltersCaptor.getValue().size());
        ScanFilter uuidFilter = scanFiltersCaptor.getValue().get(0);
        UUID uuid = uuidFilter.getServiceUuid().getUuid();
        assertEquals(BLP_SERVICE_UUID, uuid);

        // When
        ScanResult scanResult = mock(ScanResult.class);
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getAddress()).thenReturn("12:23:34:98:76:54");
        when(scanResult.getDevice()).thenReturn(device);
        bluetoothAdapter.addDevice(device);
        scanCallbackCaptor.getValue().onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        ArgumentCaptor<BluetoothPeripheral> bluetoothPeripheralCaptor = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<ScanResult> scanResultCaptor = ArgumentCaptor.forClass(ScanResult.class);
        verify(callback).onDiscoveredPeripheral(bluetoothPeripheralCaptor.capture(), scanResultCaptor.capture());

        assertEquals(scanResultCaptor.getValue(), scanResult);
        assertEquals(bluetoothPeripheralCaptor.getValue().getAddress(), "12:23:34:98:76:54");
    }

    @Test
    public void When_an_address_filtered_scan_is_started_then_startScan_is_called_with_an_address_filter_and_when_a_matching_scanResult_comes_in_onDiscoveredPeripheral_is_called() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        String myAddress = "12:23:34:98:76:54";

        // When
        central.scanForPeripheralsWithAddresses(new String[]{myAddress});

        // Then
        verify(scanner).startScan(scanFiltersCaptor.capture(), scanSettingsCaptor.capture(), scanCallbackCaptor.capture());

        List<ScanFilter> filters = scanFiltersCaptor.getValue();
        assertEquals(1, filters.size());

        String address = filters.get(0).getDeviceAddress();
        assertEquals(myAddress, address);

        // When
        ScanResult scanResult = mock(ScanResult.class);
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getAddress()).thenReturn(myAddress);
        when(scanResult.getDevice()).thenReturn(device);
        bluetoothAdapter.addDevice(device);
        scanCallbackCaptor.getValue().onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        ArgumentCaptor<BluetoothPeripheral> bluetoothPeripheralCaptor = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<ScanResult> scanResultCaptor = ArgumentCaptor.forClass(ScanResult.class);
        verify(callback).onDiscoveredPeripheral(bluetoothPeripheralCaptor.capture(), scanResultCaptor.capture());

        assertEquals(scanResultCaptor.getValue(), scanResult);
        assertEquals(bluetoothPeripheralCaptor.getValue().getAddress(), myAddress);
    }

    @Test
    public void When_starting_scanning_for_addressees_that_include_invalid_address_then_only_the_valid_addresses_are_included_in_the_scan() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        String validAddress = "12:23:34:98:76:54";
        String invalidAddress = "23:34:98:76:XX";

        // When
        central.scanForPeripheralsWithAddresses(new String[]{validAddress, invalidAddress});

        // Then
        verify(scanner).startScan(scanFiltersCaptor.capture(), scanSettingsCaptor.capture(), scanCallbackCaptor.capture());
        List<ScanFilter> filters = scanFiltersCaptor.getValue();
        assertEquals(1, filters.size());
        ScanFilter addressFilter = filters.get(0);
        String address = addressFilter.getDeviceAddress();
        assertEquals(validAddress, address);
    }

    @Test
    public void When_scan_filtered_by_name_is_started_then_an_unfiltered_scan_is_started_but_scanResults_containing_the_name_will_be_received() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        String myAddress = "12:23:34:98:76:54";
        String myName = "Polar";

        // When
        central.scanForPeripheralsWithNames(new String[]{myName});

        // Then
        verify(scanner).startScan(scanFiltersCaptor.capture(), scanSettingsCaptor.capture(), scanCallbackCaptor.capture());
        assertEquals(0, scanFiltersCaptor.getValue().size());

        // When
        ScanResult scanResult = mock(ScanResult.class);
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getAddress()).thenReturn(myAddress);
        when(device.getName()).thenReturn("Polar H7");
        when(scanResult.getDevice()).thenReturn(device);
        bluetoothAdapter.addDevice(device);
        scanCallbackCaptor.getValue().onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        ArgumentCaptor<BluetoothPeripheral> bluetoothPeripheralCaptor = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<ScanResult> scanResultCaptor = ArgumentCaptor.forClass(ScanResult.class);
        verify(callback).onDiscoveredPeripheral(bluetoothPeripheralCaptor.capture(), scanResultCaptor.capture());

        assertEquals(scanResultCaptor.getValue(), scanResult);
        assertEquals(bluetoothPeripheralCaptor.getValue().getName(), "Polar H7");
    }

    @Test
    public void When_starting_a_scan_using_filters_then_the_scan_is_started_using_the_same_filters() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        String myAddress = "12:23:34:98:76:54";
        List<ScanFilter> myfilters = new ArrayList<>();
        ScanFilter filter = new ScanFilter.Builder()
                .setDeviceAddress(myAddress)
                .build();
        myfilters.add(filter);

        // When
        central.scanForPeripheralsUsingFilters(myfilters);

        // Then
        verify(scanner).startScan(scanFiltersCaptor.capture(), scanSettingsCaptor.capture(), scanCallbackCaptor.capture());

        // Verify there is only 1 filter set
        List<ScanFilter> filters = scanFiltersCaptor.getValue();
        assertEquals(1, filters.size());

        // Verify the filter only contains the valid address we added
        ScanFilter addressFilter = filters.get(0);
        String address = addressFilter.getDeviceAddress();
        assertEquals(myAddress, address);
    }

    @Test (expected = NullPointerException.class)
    public void scanForPeripheralsWithServicesTestNullTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        central.scanForPeripheralsWithServices(null);
    }

    @Test (expected = IllegalArgumentException.class)
    public void scanForPeripheralsWithServicesTestEmptyTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        central.scanForPeripheralsWithServices(new UUID[0]);
    }

    @Test (expected = NullPointerException.class)
    public void scanForPeripheralsWithAddressesNullTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        central.scanForPeripheralsWithAddresses(null);
    }

    @Test (expected = IllegalArgumentException.class)
    public void scanForPeripheralsWithAddressesEmptyTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        central.scanForPeripheralsWithAddresses(new String[0]);
    }

    @Test (expected = NullPointerException.class)
    public void scanForPeripheralsWithNamesTestNullTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        central.scanForPeripheralsWithNames(null);
    }

    @Test (expected = IllegalArgumentException.class)
    public void scanForPeripheralsWithNamesEmptyTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        central.scanForPeripheralsWithNames(new String[0]);
    }

    @Test (expected = NullPointerException.class)
    public void scanForPeripheralsUsingFiltersNullTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        central.scanForPeripheralsUsingFilters(null);
    }

    @Test (expected = IllegalArgumentException.class)
    public void scanForPeripheralsUsingFiltersEmptyTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        central.scanForPeripheralsUsingFilters(new ArrayList<ScanFilter>());
    }

    @Test
    public void When_starting_a_scan_fails_then_the_error_is_reported() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

        // When
        central.scanForPeripherals();

        // Then
        verify(scanner).startScan(scanFiltersCaptor.capture(), scanSettingsCaptor.capture(), scanCallbackCaptor.capture());

        // When
        scanCallbackCaptor.getValue().onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES.value);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(callback).onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES);
    }

    @Test
    public void scanFailedAutoconnectTest() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.isUncached()).thenReturn(true);

        // When
        central.autoConnectPeripheral(peripheral, peripheralCallback);

        // Then
        verify(scanner).startScan(scanFiltersCaptor.capture(), scanSettingsCaptor.capture(), scanCallbackCaptor.capture());

        // When
        scanCallbackCaptor.getValue().onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES.value);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(callback).onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES);
    }

    @Test
    public void When_starting_a_scan_for_names_fails_then_the_error_is_reported() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        String myName = "Polar";

        // When
        central.scanForPeripheralsWithNames(new String[]{myName});

        // Then
        verify(scanner).startScan(scanFiltersCaptor.capture(), scanSettingsCaptor.capture(), scanCallbackCaptor.capture());

        // When
        scanCallbackCaptor.getValue().onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES.value);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(callback).onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES);
    }

    @Test
    public void Given_a_started_scan_when_stopScan_is_called_the_scan_is_stopped_and_subsequent_calls_to_stopScan_do_nothing() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        central.scanForPeripherals();
        verify(scanner).startScan(scanFiltersCaptor.capture(), scanSettingsCaptor.capture(), scanCallbackCaptor.capture());

        // When
        central.stopScan();

        // Then
        verify(scanner).stopScan(scanCallbackCaptor.getValue());

        // When
        central.stopScan();

        // Then
        verify(scanner, times(1)).stopScan(any(ScanCallback.class));
    }

    @Test
    public void When_setScanMode_is_called_then_the_new_mode_is_used_in_the_next_scan() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

        // When
        central.setScanMode(ScanMode.BALANCED);

        // Then
        central.scanForPeripherals();
        verify(scanner).startScan(scanFiltersCaptor.capture(), scanSettingsCaptor.capture(), scanCallbackCaptor.capture());
        assertEquals(ScanMode.BALANCED.value, scanSettingsCaptor.getValue().getScanMode());
    }

    @Test
    public void When_connectPeripheral_is_called_then_it_connects_and_onConnectedPeripheral_is_called() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.LE);

        // When
        central.connectPeripheral(peripheral, peripheralCallback);

        // Then
        verify(peripheral).connect();
        central.internalCallback.connecting(peripheral);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        verify(callback).onConnectingPeripheral(peripheral);

        // When
        central.internalCallback.connected(peripheral);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(callback).onConnectedPeripheral(peripheral);
    }

    @Test
    public void Given_a_connected_peripheral_when_connectPeripheral_is_called_no_connect_is_attempted() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.LE);

        central.connectPeripheral(peripheral, peripheralCallback);
        central.internalCallback.connected(peripheral);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // When
        central.connectPeripheral(peripheral, peripheralCallback);

        // Then
        verify(peripheral, times(1)).connect();
    }

    @Test
    public void Given_a_connecting_peripheral_when_connectPeripheral_is_called_no_connect_is_attempted() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.LE);
        central.connectPeripheral(peripheral, peripheralCallback);

        // When
        central.connectPeripheral(peripheral, peripheralCallback);

        // Then
        verify(peripheral, times(1)).connect();
    }

    @Test
    public void Given_a_connecting_peripheral_when_the_connection_fails_a_retry_is_done() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.LE);
        central.connectPeripheral(peripheral, peripheralCallback);

        // When
        central.internalCallback.connectFailed(peripheral, HciStatus.ERROR);

        // Then
        verify(callback, never()).onConnectionFailed(peripheral, HciStatus.ERROR);
        verify(peripheral, times(2)).connect();
    }

    @Test
    public void Given_a_connecting_peripheral_when_a_retry_fails_then_connectionFailed_is_called() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.LE);
        central.connectPeripheral(peripheral, peripheralCallback);

        // When
        central.internalCallback.connectFailed(peripheral, HciStatus.ERROR);
        central.internalCallback.connectFailed(peripheral, HciStatus.ERROR);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(callback).onConnectionFailed(peripheral, HciStatus.ERROR);
    }

    @Test
    public void Given_a_connected_peripheral_when_getConnectedPeripherals_is_called_then_it_returns_the_peripheral() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.LE);
        central.connectPeripheral(peripheral, peripheralCallback);
        central.internalCallback.connected(peripheral);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        verify(callback).onConnectedPeripheral(peripheral);

        // When
        List<BluetoothPeripheral> peripherals = central.getConnectedPeripherals();

        // Then
        assertNotNull(peripherals);
        assertEquals(1, peripherals.size());
        assertEquals(peripheral, peripherals.get(0));

        // When
        peripheral.cancelConnection();
        central.internalCallback.disconnected(peripheral, HciStatus.SUCCESS);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        List<BluetoothPeripheral> peripherals2 = central.getConnectedPeripherals();
        assertNotNull(peripherals2);
        assertEquals(0, peripherals2.size());
    }

    @Test
    public void Given_no_connected_peripherals_when_getConnectedPeripherals_is_called_then_it_returns_an_empty_list() {
        // When
        List<BluetoothPeripheral> peripherals = central.getConnectedPeripherals();

        // Then
        assertNotNull(peripherals);
        assertEquals(0, peripherals.size());
    }

    @Test
    public void Given_a_connected_peripheral_when_cancelConnection_is_called_and_the_peripheral_disconnects_then_onDisconnectedPeripheral_is_called() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.LE);
        central.connectPeripheral(peripheral, peripheralCallback);
        verify(peripheral).connect();
        central.internalCallback.connected(peripheral);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        verify(callback).onConnectedPeripheral(peripheral);

        // When
        central.cancelConnection(peripheral);

        // Then
        verify(peripheral).cancelConnection();

        // When
        central.internalCallback.disconnected(peripheral, HciStatus.SUCCESS);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(callback).onDisconnectedPeripheral(peripheral, HciStatus.SUCCESS);
    }

    @Test
    public void Given_a_connecting_peripheral_when_cancelConnection_is_called_and_disconnects_then_onDisconnectedPeripheral_is_called() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.LE);
        central.connectPeripheral(peripheral, peripheralCallback);
        verify(peripheral).connect();

        // When
        central.cancelConnection(peripheral);

        // Then
        verify(peripheral).cancelConnection();

        // When
        central.internalCallback.disconnected(peripheral, HciStatus.SUCCESS);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(callback).onDisconnectedPeripheral(peripheral, HciStatus.SUCCESS);
    }

    @Test
    public void Given_an_autoconnecting_peripheral_when_cancelConnection_is_called_then_onDisconnectedPeripheral_is_called() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.isUncached()).thenReturn(true);
        central.autoConnectPeripheral(peripheral, peripheralCallback);

        // When
        central.cancelConnection(peripheral);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(callback).onDisconnectedPeripheral(peripheral, HciStatus.SUCCESS);
    }

    @Test
    public void Given_a_cached_autoconnecting_peripheral_when_it_connects_then_onConnectedPeripheral_is_called() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.LE);
        central.autoConnectPeripheral(peripheral, peripheralCallback);
        verify(peripheral).autoConnect();

        // When
        central.internalCallback.connecting(peripheral);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(callback).onConnectingPeripheral(peripheral);

        // When
        central.internalCallback.connected(peripheral);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(callback).onConnectedPeripheral(peripheral);
    }

    @Test
    public void Given_an_unchached_peripheral_when_autoConnectPeripheral_is_called_then_a_scan_is_started_until_it_is_found_and_connect_is_called() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getAddress()).thenReturn("12:23:34:98:76:54");
        when(device.getType()).thenReturn(BluetoothDevice.DEVICE_TYPE_LE);
        bluetoothAdapter.addDevice(device);

        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.isUncached()).thenReturn(true);

        // When
        central.autoConnectPeripheral(peripheral, peripheralCallback);

        // Then
        verify(peripheral, never()).autoConnect();
        verify(scanner).startScan(scanFiltersCaptor.capture(), scanSettingsCaptor.capture(), scanCallbackCaptor.capture());

        // When
        ScanResult scanResult = mock(ScanResult.class);
        when(scanResult.getDevice()).thenReturn(device);
        scanCallbackCaptor.getValue().onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult);

        // Then
        verify(scanner).stopScan(scanCallbackCaptor.getValue());
        verify(peripheral).connect();
    }

    @Test
    public void Given_a_connected_peripheral_when_autoConnectPeripheral_is_called_then_it_is_ignored() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.LE);
        central.connectPeripheral(peripheral, peripheralCallback);
        central.internalCallback.connected(peripheral);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        verify(callback).onConnectedPeripheral(peripheral);

        // When
        central.autoConnectPeripheral(peripheral, peripheralCallback);

        // Then
        verify(peripheral, never()).autoConnect();
    }

    @Test
    public void Given_an_unchached_peripheral_when_when_autoConnectPeripheral_is_called_then_a_scan_with_its_address_is_started_and_no_autoConnect_is_called() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.isUncached()).thenReturn(true);

        // When
        central.autoConnectPeripheral(peripheral, peripheralCallback);

        // Then
        verify(peripheral, never()).autoConnect();
        verify(scanner).startScan(scanFiltersCaptor.capture(), scanSettingsCaptor.capture(), scanCallbackCaptor.capture());
        assertEquals(peripheral.getAddress(), scanFiltersCaptor.getValue().get(0).getDeviceAddress());

        // When
        central.autoConnectPeripheral(peripheral, peripheralCallback);

        // Then
        verify(peripheral, never()).autoConnect();
    }

    @Test
    public void Given_a_cached_and_uncached_peripheral_when_autoConnectPeripheralsBatch_is_called_then_autoConnect_is_called_for_the_cached_one_and_a_scan_for_the_unchached_one() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.isUncached()).thenReturn(true);

        BluetoothPeripheral peripheral2 = mock(BluetoothPeripheral.class);
        when(peripheral2.getAddress()).thenReturn("22:23:34:98:76:54");
        when(peripheral2.isUncached()).thenReturn(false);

        Map<BluetoothPeripheral, BluetoothPeripheralCallback> batch = new HashMap<>();
        batch.put(peripheral, peripheralCallback);
        batch.put(peripheral2, peripheralCallback);

        // When
        central.autoConnectPeripheralsBatch(batch);

        // Then
        verify(peripheral, never()).autoConnect();
        verify(peripheral2).autoConnect();
        verify(scanner).startScan(ArgumentMatchers.<ScanFilter>anyList(), any(ScanSettings.class), any(ScanCallback.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void When_getPeripheral_is_called_with_an_invalid_address_then_a_exception_is_thrown() {
        // Get peripheral and supply lowercase mac address, which is not allowed
        central.getPeripheral("ac:de:ef:12:34:56");
    }

    @Test
    public void When_getPeripheral_is_called_with_a_valid_address_then_a_corresponding_peripheral_is_returned() {
        // Given
        String address = "AC:DE:EF:12:34:56";
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getAddress()).thenReturn(address);
        bluetoothAdapter.addDevice(device);

        // Get peripheral and supply lowercase mac address
        BluetoothPeripheral peripheral = central.getPeripheral(address);
        assertNotNull(peripheral);
        assertEquals(address, peripheral.getAddress());
    }

    @Test
    public void Given_a_connected_peripheral_when_getPeripheral_is_called_then_the_same_peripheral_is_returned() {
        // Given
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.LE);
        central.connectPeripheral(peripheral, peripheralCallback);
        verify(peripheral).connect();
        central.internalCallback.connected(peripheral);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        verify(callback).onConnectedPeripheral(peripheral);

        // When
        BluetoothPeripheral peripheral2 = central.getPeripheral("12:23:34:98:76:54");

        // Then
        assertEquals(peripheral, peripheral2);
    }

    @Test
    public void Given_a_peripheral_when_setPinCodeForPeripheral_is_called_with_valid_parameters_then_the_pin_is_stored_for_that_peripheral() {
        // Given
        String address = "12:23:34:98:76:54";
        String pin = "123456";
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn(address);

        // When
        boolean result = central.setPinCodeForPeripheral(address, pin );

        // Then
        assertTrue(result);
        assertEquals(pin, central.internalCallback.getPincode(peripheral));
    }

    @Test
    public void When_setPinCodeForPeripheral_is_called_with_an_invalid_mac_address_then_it_returns_false_and_it_is_not_stored() {
        // Given
        String address = "2:23:34:98:76:54";
        String pin = "123456";
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn(address);

        // When
        boolean result = central.setPinCodeForPeripheral(address, pin);

        assertFalse(result);
        assertNull(central.internalCallback.getPincode(peripheral));
    }

    @Test
    public void When_setPinCodeForPeripheral_is_called_with_an_invalid_pin_then_it_returns_false_and_it_is_not_stored() {
        // Given
        String address = "12:23:34:98:76:54";
        String pin = "123";
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn(address);

        // When
        boolean result = central.setPinCodeForPeripheral(address, pin);

        assertFalse(result);
        assertNull(central.internalCallback.getPincode(peripheral));
    }

    @Test
    public void When_bluetooth_is_turning_off_then_onBluetoothAdapterStateChanged_is_called() {
        // Given
        Intent intent = mock(Intent.class);
        when(intent.getAction()).thenReturn(BluetoothAdapter.ACTION_STATE_CHANGED);
        when(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)).thenReturn(BluetoothAdapter.STATE_TURNING_OFF);

        // When
        central.adapterStateReceiver.onReceive(context, intent);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(callback).onBluetoothAdapterStateChanged(BluetoothAdapter.STATE_TURNING_OFF);
    }

    @Test
    public void Given_a_connected_peripheral_when_bluetooth_is_turned_off_then_it_is_disconnected() {
        // Given
        Intent intent = mock(Intent.class);
        when(intent.getAction()).thenReturn(BluetoothAdapter.ACTION_STATE_CHANGED);
        when(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)).thenReturn(BluetoothAdapter.STATE_OFF);

        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.LE);

        central.connectPeripheral(peripheral, peripheralCallback);

        verify(peripheral).connect();

        // Give connected event and see if we get callback
        central.internalCallback.connected(peripheral);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(callback).onConnectedPeripheral(peripheral);

        // When
        central.adapterStateReceiver.onReceive(context, intent);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(callback).onBluetoothAdapterStateChanged(BluetoothAdapter.STATE_OFF);
        verify(peripheral).disconnectWhenBluetoothOff();
    }

    @Test
    public void When_close_is_called_the_adapter_state_broadcast_receiver_is_unregistered(){
        central.close();
        verify(context).unregisterReceiver(central.adapterStateReceiver);
    }

    @Test
    public void When_bluetooth_is_off_then_a_scan_is_not_attempted() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        bluetoothAdapter.setEnabled(false);
        central.scanForPeripherals();
        verify(scanner, never()).startScan(ArgumentMatchers.<ScanFilter>anyList(), any(ScanSettings.class), any(ScanCallback.class));
    }

    @Test (expected = SecurityException.class)
    public void When_permission_are_not_correct_then_a_scan_is_not_attempted() {
        central.scanForPeripherals();
        verify(scanner, never()).startScan(ArgumentMatchers.<ScanFilter>anyList(), any(ScanSettings.class), any(ScanCallback.class));
    }

    @Test
    public void Given_a_transport_when_getPeripheral_is_called_a_peripheral_is_returned_with_specified_transport() {
        // Given
        String address = "AC:DE:EF:12:34:56";
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getAddress()).thenReturn(address);
        bluetoothAdapter.addDevice(device);

        central.setTransport(Transport.BR_EDR);

        // Get peripheral and supply lowercase mac address
        BluetoothPeripheral peripheral = central.getPeripheral(address);
        assertNotNull(peripheral);
        assertEquals(Transport.BR_EDR, peripheral.getTransport());
    }
}