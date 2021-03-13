package com.welie.blessed;

import android.Manifest;

import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.pm.PackageManager;
import android.os.Handler;

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
import java.util.List;
import java.util.UUID;

import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES;
import static android.os.Build.VERSION_CODES.M;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.ParcelUuid;

import androidx.test.core.app.ApplicationProvider;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE, sdk = { M }, shadows={ShadowBluetoothLEAdapter.class} )
public class BluetoothCentralManagerTest {
    private BluetoothCentralManager central;
    private ShadowApplication application;
    private ShadowBluetoothLEAdapter bluetoothAdapter;

    @Mock
    private BluetoothLeScanner scanner;

    @Mock
    private BluetoothCentralManagerCallback callback;

    @Mock
    private BluetoothPeripheralCallback peripheralCallback;

    @Captor
    ArgumentCaptor<List<ScanFilter>> scanFiltersCaptor;

    private final Handler handler = new Handler();

    @Before
    public void setUp() {
        openMocks(this);

        application = shadowOf((Application) ApplicationProvider.getApplicationContext());

        bluetoothAdapter = Shadow.extract(ShadowBluetoothLEAdapter.getDefaultAdapter());
        bluetoothAdapter.setEnabled(true);
        bluetoothAdapter.setBluetoothLeScanner(scanner);

        Context context = ApplicationProvider.getApplicationContext();

        // Setup hardware features
        PackageManager packageManager = context.getPackageManager();
        ShadowPackageManager shadowPackageManager = shadowOf(packageManager);
        shadowPackageManager.setSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE, true);

        central = new BluetoothCentralManager(context, callback, handler);
    }

    @Test
    public void scanForPeripheralsTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        central.scanForPeripherals();
        verify(scanner).startScan(ArgumentMatchers.<ScanFilter>anyList(), any(ScanSettings.class), any(ScanCallback.class));

        // Fake scan result
        ScanResult scanResult = mock(ScanResult.class);
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getAddress()).thenReturn("12:23:34:98:76:54");
        when(scanResult.getDevice()).thenReturn(device);
        bluetoothAdapter.addDevice(device);
        central.defaultScanCallback.onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // See if we get it back
        ArgumentCaptor<BluetoothPeripheral> bluetoothPeripheralCaptor = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<ScanResult> scanResultCaptor = ArgumentCaptor.forClass(ScanResult.class);
        verify(callback).onDiscoveredPeripheral(bluetoothPeripheralCaptor.capture(), scanResultCaptor.capture());

        assertEquals(scanResultCaptor.getValue(), scanResult);
        assertEquals(bluetoothPeripheralCaptor.getValue().getAddress(), "12:23:34:98:76:54");
    }


    @Test
    public void scanForPeripheralsWithServicesTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        UUID BLP_SERVICE_UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb");
        central.scanForPeripheralsWithServices(new UUID[]{BLP_SERVICE_UUID});

        // Make sure startScan is called
        ArgumentCaptor<ScanSettings> scanSettingsCaptor = ArgumentCaptor.forClass(ScanSettings.class);
        ArgumentCaptor<ScanCallback> scanCallbackCaptor = ArgumentCaptor.forClass(ScanCallback.class);
        verify(scanner).startScan(scanFiltersCaptor.capture(), scanSettingsCaptor.capture(), scanCallbackCaptor.capture());

        // Verify there is only 1 filter set
        List<ScanFilter> filters = scanFiltersCaptor.getValue();
        assertEquals(1, filters.size());

        // Verify the filter contains the UUID we added
        ScanFilter uuidFilter = filters.get(0);
        ParcelUuid parcelUuid = uuidFilter.getServiceUuid();
        UUID uuid = parcelUuid.getUuid();
        assertEquals(BLP_SERVICE_UUID.toString(), uuid.toString());

        // Fake scan result
        ScanResult scanResult = mock(ScanResult.class);
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getAddress()).thenReturn("12:23:34:98:76:54");
        when(scanResult.getDevice()).thenReturn(device);
        bluetoothAdapter.addDevice(device);
        central.defaultScanCallback.onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // See if we get it back
        ArgumentCaptor<BluetoothPeripheral> bluetoothPeripheralCaptor = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<ScanResult> scanResultCaptor = ArgumentCaptor.forClass(ScanResult.class);
        verify(callback).onDiscoveredPeripheral(bluetoothPeripheralCaptor.capture(), scanResultCaptor.capture());

        assertEquals(scanResultCaptor.getValue(), scanResult);
        assertEquals(bluetoothPeripheralCaptor.getValue().getAddress(), "12:23:34:98:76:54");
    }

    @Test
    public void scanForPeripheralsWithAddressesTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        String myAddress = "12:23:34:98:76:54";
        central.scanForPeripheralsWithAddresses(new String[]{myAddress});

        // Make sure startScan is called
        ArgumentCaptor<ScanSettings> scanSettingsCaptor = ArgumentCaptor.forClass(ScanSettings.class);
        ArgumentCaptor<ScanCallback> scanCallbackCaptor = ArgumentCaptor.forClass(ScanCallback.class);
        verify(scanner).startScan(scanFiltersCaptor.capture(), scanSettingsCaptor.capture(), scanCallbackCaptor.capture());

        // Verify there is only 1 filter set
        List<ScanFilter> filters = scanFiltersCaptor.getValue();
        assertEquals(1, filters.size());

        // Verify the filter contains the address we added
        ScanFilter addressFilter = filters.get(0);
        String address = addressFilter.getDeviceAddress();
        assertEquals(myAddress, address);

        // Fake scan result
        ScanResult scanResult = mock(ScanResult.class);
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getAddress()).thenReturn(myAddress);
        when(scanResult.getDevice()).thenReturn(device);
        bluetoothAdapter.addDevice(device);
        central.defaultScanCallback.onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // See if we get it back
        ArgumentCaptor<BluetoothPeripheral> bluetoothPeripheralCaptor = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<ScanResult> scanResultCaptor = ArgumentCaptor.forClass(ScanResult.class);
        verify(callback).onDiscoveredPeripheral(bluetoothPeripheralCaptor.capture(), scanResultCaptor.capture());

        assertEquals(scanResultCaptor.getValue(), scanResult);
        assertEquals(bluetoothPeripheralCaptor.getValue().getAddress(), myAddress);
    }

    @Test
    public void scanForPeripheralsWithBadAddressesTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        String validAddress = "12:23:34:98:76:54";
        String invalidAddress = "23:34:98:76:XX";

        central.scanForPeripheralsWithAddresses(new String[]{validAddress, invalidAddress});

        // Make sure startScan is called
        ArgumentCaptor<ScanSettings> scanSettingsCaptor = ArgumentCaptor.forClass(ScanSettings.class);
        ArgumentCaptor<ScanCallback> scanCallbackCaptor = ArgumentCaptor.forClass(ScanCallback.class);
        verify(scanner).startScan(scanFiltersCaptor.capture(), scanSettingsCaptor.capture(), scanCallbackCaptor.capture());

        // Verify there is only 1 filter set
        List<ScanFilter> filters = scanFiltersCaptor.getValue();

        // Only the valid address should be added so there should be only 1 address
        assertEquals(1, filters.size());

        // Verify the filter only contains the valid address we added
        ScanFilter addressFilter = filters.get(0);
        String address = addressFilter.getDeviceAddress();
        assertEquals(validAddress, address);
    }

    @Test
    public void scanForPeripheralsWithNamesTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        String myAddress = "12:23:34:98:76:54";
        String myName = "Polar";
        central.scanForPeripheralsWithNames(new String[]{myName});

        // Make sure startScan is called
        ArgumentCaptor<ScanSettings> scanSettingsCaptor = ArgumentCaptor.forClass(ScanSettings.class);
        ArgumentCaptor<ScanCallback> scanCallbackCaptor = ArgumentCaptor.forClass(ScanCallback.class);
        verify(scanner).startScan(scanFiltersCaptor.capture(), scanSettingsCaptor.capture(), scanCallbackCaptor.capture());

        // Verify there is no filter set
        List<ScanFilter> filters = scanFiltersCaptor.getValue();
        assertEquals(0, filters.size());

        // Fake scan result
        ScanResult scanResult = mock(ScanResult.class);
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getAddress()).thenReturn(myAddress);
        when(device.getName()).thenReturn("Polar H7");
        when(scanResult.getDevice()).thenReturn(device);
        bluetoothAdapter.addDevice(device);
        central.scanByNameCallback.onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // See if we get it back
        ArgumentCaptor<BluetoothPeripheral> bluetoothPeripheralCaptor = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<ScanResult> scanResultCaptor = ArgumentCaptor.forClass(ScanResult.class);
        verify(callback).onDiscoveredPeripheral(bluetoothPeripheralCaptor.capture(), scanResultCaptor.capture());

        assertEquals(scanResultCaptor.getValue(), scanResult);
        assertEquals(bluetoothPeripheralCaptor.getValue().getName(), "Polar H7");
    }

    @Test
    public void scanForPeripheralsUsingFiltersTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        String myAddress = "12:23:34:98:76:54";
        List<ScanFilter> myfilters = new ArrayList<>();
        ScanFilter filter = new ScanFilter.Builder()
                .setDeviceAddress(myAddress)
                .build();
        myfilters.add(filter);
        central.scanForPeripheralsUsingFilters(myfilters);

        // Make sure startScan is called
        ArgumentCaptor<ScanSettings> scanSettingsCaptor = ArgumentCaptor.forClass(ScanSettings.class);
        ArgumentCaptor<ScanCallback> scanCallbackCaptor = ArgumentCaptor.forClass(ScanCallback.class);
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
    public void scanFailedTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        central.scanForPeripherals();
        verify(scanner).startScan(ArgumentMatchers.<ScanFilter>anyList(), any(ScanSettings.class), any(ScanCallback.class));

        central.defaultScanCallback.onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES.value);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(callback).onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES);
    }

    @Test
    public void scanFailedAutoconnectTest() {
        central.autoConnectScanCallback.onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES.value);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(callback).onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES);
    }

    @Test
    public void scanForNamesFailedTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        String myName = "Polar";
        central.scanForPeripheralsWithNames(new String[]{myName});

        central.scanByNameCallback.onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES.value);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(callback).onScanFailed(ScanFailure.OUT_OF_HARDWARE_RESOURCES);
    }

    @Test
    public void stopScanTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        central.scanForPeripherals();
        verify(scanner).startScan(ArgumentMatchers.<ScanFilter>anyList(), any(ScanSettings.class), any(ScanCallback.class));

        // Stop scan
        central.stopScan();

        // Check if scan is correctly stopped
        verify(scanner).stopScan(central.defaultScanCallback);

        // Stop scan again
        central.stopScan();

        // Verify that stopScan is not called again
        verify(scanner, times(1)).stopScan(any(ScanCallback.class));
    }

    @Test
    public void connectPeripheralTest() {
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
    }

    @Test
    public void connectPeripheralAlreadyConnectedTest() {
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

        central.connectPeripheral(peripheral, peripheralCallback);

        verify(peripheral, times(1)).connect();
    }

    @Test
    public void connectPeripheralConnectingTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.LE);

        central.connectPeripheral(peripheral, peripheralCallback);

        verify(peripheral).connect();

        central.connectPeripheral(peripheral, peripheralCallback);

        verify(peripheral, times(1)).connect();
    }

    @Test
    public void connectionFailedRetryTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.LE);

        central.connectPeripheral(peripheral, peripheralCallback);

        verify(peripheral).connect();

        // Give connected event and see if we get callback
        central.internalCallback.connectFailed(peripheral, HciStatus.ERROR);

        // We should not get a connection failed but a retry with autoconnect instead
        verify(callback, never()).onConnectionFailed(peripheral, HciStatus.ERROR);
        verify(peripheral, times(2)).connect();
    }

    @Test
    public void connectionFailedAfterRetryTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.LE);

        central.connectPeripheral(peripheral, peripheralCallback);

        verify(peripheral).connect();

        // Give connected event and see if we get callback
        central.internalCallback.connectFailed(peripheral, HciStatus.ERROR);
        central.internalCallback.connectFailed(peripheral, HciStatus.ERROR);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // We should not get a connection failed after 2 failed attempts
        verify(callback).onConnectionFailed(peripheral, HciStatus.ERROR);
    }

    @Test
    public void getConnectedPeripheralsTest() {
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

        List<BluetoothPeripheral> peripherals = central.getConnectedPeripherals();
        assertNotNull(peripherals);
        assertEquals(1, peripherals.size());
        assertEquals(peripheral, peripherals.get(0));

        peripheral.cancelConnection();

        central.internalCallback.disconnected(peripheral, HciStatus.SUCCESS);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        List<BluetoothPeripheral> peripherals2 = central.getConnectedPeripherals();
        assertNotNull(peripherals2);
        assertEquals(0, peripherals2.size());
    }

    @Test
    public void getConnectedPeripheralsNoneTest() {
        List<BluetoothPeripheral> peripherals = central.getConnectedPeripherals();
        assertNotNull(peripherals);
        assertEquals(0, peripherals.size());
    }

    @Test
    public void cancelConnectionPeripheralTest() {
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

        central.cancelConnection(peripheral);

        verify(peripheral).cancelConnection();

        central.internalCallback.disconnected(peripheral, HciStatus.SUCCESS);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(callback).onDisconnectedPeripheral(peripheral, HciStatus.SUCCESS);
    }

    @Test
    public void cancelConnectionUnconnectedPeripheralTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.LE);

        central.connectPeripheral(peripheral, peripheralCallback);

        verify(peripheral).connect();

        central.cancelConnection(peripheral);

        verify(peripheral).cancelConnection();

        central.internalCallback.disconnected(peripheral, HciStatus.SUCCESS);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(callback).onDisconnectedPeripheral(peripheral, HciStatus.SUCCESS);
    }

    @Test
    public void cancelConnectionReconnectingPeripheralTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.UNKNOWN);

        central.autoConnectPeripheral(peripheral, peripheralCallback);

        central.cancelConnection(peripheral);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(callback).onDisconnectedPeripheral(peripheral, HciStatus.SUCCESS);
    }

    @Test
    public void autoconnectTestCached() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.LE);

        central.autoConnectPeripheral(peripheral, peripheralCallback);

        verify(peripheral).autoConnect();

        // Give connected event and see if we get callback
        central.internalCallback.connected(peripheral);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(callback).onConnectedPeripheral(peripheral);
    }

    @Test
    public void autoconnectTestUnCached() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getAddress()).thenReturn("12:23:34:98:76:54");
        when(device.getType()).thenReturn(BluetoothDevice.DEVICE_TYPE_LE);
        bluetoothAdapter.addDevice(device);

        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.UNKNOWN);
        central.autoConnectPeripheral(peripheral, peripheralCallback);

        verify(peripheral, never()).autoConnect();
        verify(scanner).startScan(ArgumentMatchers.<ScanFilter>anyList(), any(ScanSettings.class), any(ScanCallback.class));

        // Fake scan result
        ScanResult scanResult = mock(ScanResult.class);
        when(scanResult.getDevice()).thenReturn(device);
        central.autoConnectScanCallback.onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult);

        verify(peripheral).connect();
    }


    @Test
    public void autoconnectPeripheralConnectedTest() {
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

        central.autoConnectPeripheral(peripheral, peripheralCallback);

        verify(peripheral, never()).autoConnect();
    }

    @Test
    public void autoconnectTwice() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(PeripheralType.UNKNOWN);
        central.autoConnectPeripheral(peripheral, peripheralCallback);

        verify(peripheral, never()).autoConnect();
        verify(scanner).startScan(ArgumentMatchers.<ScanFilter>anyList(), any(ScanSettings.class), any(ScanCallback.class));

        central.autoConnectPeripheral(peripheral, peripheralCallback);

        verify(peripheral, never()).autoConnect();
    }

    @Test(expected = IllegalArgumentException.class)
    public void getPeripheralWrongMacAddressTest() {
        // Get peripheral and supply lowercase mac address, which is not allowed
        central.getPeripheral("ac:de:ef:12:34:56");
    }

    @Test
    public void getPeripheralValidMacAddressTest() {
        // Given
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getAddress()).thenReturn("AC:DE:EF:12:34:56");
        bluetoothAdapter.addDevice(device);

        // Get peripheral and supply lowercase mac address
        BluetoothPeripheral peripheral = central.getPeripheral("AC:DE:EF:12:34:56");
        assertNotNull(peripheral);
    }

    @Test
    public void getPeripheralConnectedTest() {
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

        BluetoothPeripheral peripheral2 = central.getPeripheral("12:23:34:98:76:54");
        assertEquals(peripheral, peripheral2);
    }


    @Test
    public void bluetoothOffTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        bluetoothAdapter.setEnabled(false);
        central.scanForPeripherals();
        verify(scanner, never()).startScan(ArgumentMatchers.<ScanFilter>anyList(), any(ScanSettings.class), any(ScanCallback.class));
    }

    @Test
    public void noPermissionTest() {
        central.scanForPeripherals();
        verify(scanner, never()).startScan(ArgumentMatchers.<ScanFilter>anyList(), any(ScanSettings.class), any(ScanCallback.class));
    }
}