

package com.welie.blessed;

import android.Manifest;

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
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowBluetoothAdapter;
import org.robolectric.shadows.ShadowPackageManager;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES;
import static android.os.Build.VERSION_CODES.M;
import static com.welie.blessed.BluetoothCentral.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES;
import static com.welie.blessed.BluetoothPeripheral.GATT_SUCCESS;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.ParcelUuid;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = { M }, shadows={ShadowBluetoothLEAdapter.class} )
public class BluetoothCentralTest {
    private BluetoothCentral central;
    private ShadowApplication application;
    private ShadowBluetoothLEAdapter bluetoothAdapter;
    private Context context;

    @Mock
    private BluetoothLeScanner scanner;

    @Mock
    private BluetoothCentralCallback callback;

    @Mock
    private BluetoothPeripheralCallback peripheralCallback;

    private Handler handler = new Handler();

    @Before
    public void setUp() throws Exception {
        initMocks(this);

        application = ShadowApplication.getInstance();

        bluetoothAdapter = Shadow.extract(ShadowBluetoothLEAdapter.getDefaultAdapter());
        bluetoothAdapter.setEnabled(true);
        bluetoothAdapter.setBluetoothLeScanner(scanner);

        context = application.getApplicationContext();

        // Setup hardware features
        PackageManager packageManager = context.getPackageManager();
        ShadowPackageManager shadowPackageManager = shadowOf(packageManager);
        shadowPackageManager.setSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE, true);

        central = new BluetoothCentral(context, callback, handler);
    }

    @Test
    public void scanForPeripheralsTest()  throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        central.scanForPeripherals();
        verify(scanner).startScan(anyList(), any(ScanSettings.class), any(ScanCallback.class));

        // Grab the scan callback that is used
        Field field = BluetoothCentral.class.getDeclaredField("scanByServiceUUIDCallback");
        field.setAccessible(true);
        ScanCallback scanCallback = (ScanCallback) field.get(central);

        // Fake scan result
        ScanResult scanResult = mock(ScanResult.class);
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getAddress()).thenReturn("12:23:34:98:76:54");
        when(scanResult.getDevice()).thenReturn(device);
        scanCallback.onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult);

        // See if we get it back
        ArgumentCaptor<BluetoothPeripheral> bluetoothPeripheralCaptor = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<ScanResult> scanResultCaptor = ArgumentCaptor.forClass(ScanResult.class);
        verify(callback).onDiscoveredPeripheral(bluetoothPeripheralCaptor.capture(), scanResultCaptor.capture());

        assertEquals(scanResultCaptor.getValue(), scanResult);
        assertEquals(bluetoothPeripheralCaptor.getValue().getAddress(), "12:23:34:98:76:54");
    }

    @Test
    public void scanForPeripheralsWithServicesTest() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        UUID BLP_SERVICE_UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb");
        central.scanForPeripheralsWithServices(new UUID[]{BLP_SERVICE_UUID});

        // Make sure startScan is called
        ArgumentCaptor<List> scanFiltersCaptor = ArgumentCaptor.forClass(List.class);
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

        // Grab the scan callback that is used
        Field field = BluetoothCentral.class.getDeclaredField("scanByServiceUUIDCallback");
        field.setAccessible(true);
        ScanCallback scanCallback = (ScanCallback) field.get(central);

        // Fake scan result
        ScanResult scanResult = mock(ScanResult.class);
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getAddress()).thenReturn("12:23:34:98:76:54");
        when(scanResult.getDevice()).thenReturn(device);
        scanCallback.onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult);

        // See if we get it back
        ArgumentCaptor<BluetoothPeripheral> bluetoothPeripheralCaptor = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<ScanResult> scanResultCaptor = ArgumentCaptor.forClass(ScanResult.class);
        verify(callback).onDiscoveredPeripheral(bluetoothPeripheralCaptor.capture(), scanResultCaptor.capture());

        assertEquals(scanResultCaptor.getValue(), scanResult);
        assertEquals(bluetoothPeripheralCaptor.getValue().getAddress(), "12:23:34:98:76:54");
    }

    @Test
    public void scanForPeripheralsWithAddressesTest() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        String myAddress = "12:23:34:98:76:54";
        central.scanForPeripheralsWithAddresses(new String[]{myAddress});

        // Make sure startScan is called
        ArgumentCaptor<List> scanFiltersCaptor = ArgumentCaptor.forClass(List.class);
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

        // Grab the scan callback that is used
        Field field = BluetoothCentral.class.getDeclaredField("scanByServiceUUIDCallback");
        field.setAccessible(true);
        ScanCallback scanCallback = (ScanCallback) field.get(central);

        // Fake scan result
        ScanResult scanResult = mock(ScanResult.class);
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getAddress()).thenReturn(myAddress);
        when(scanResult.getDevice()).thenReturn(device);
        scanCallback.onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult);

        // See if we get it back
        ArgumentCaptor<BluetoothPeripheral> bluetoothPeripheralCaptor = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<ScanResult> scanResultCaptor = ArgumentCaptor.forClass(ScanResult.class);
        verify(callback).onDiscoveredPeripheral(bluetoothPeripheralCaptor.capture(), scanResultCaptor.capture());

        assertEquals(scanResultCaptor.getValue(), scanResult);
        assertEquals(bluetoothPeripheralCaptor.getValue().getAddress(), myAddress);
    }

    @Test
    public void scanForPeripheralsWithBadAddressesTest() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        String validAddress = "12:23:34:98:76:54";
        String invalidAddress = "23:34:98:76:XX";

        central.scanForPeripheralsWithAddresses(new String[]{validAddress, invalidAddress});

        // Make sure startScan is called
        ArgumentCaptor<List> scanFiltersCaptor = ArgumentCaptor.forClass(List.class);
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
    public void scanForPeripheralsWithNamesTest() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        String myName = "Polar";
        central.scanForPeripheralsWithNames(new String[]{myName});

        // Make sure startScan is called
        ArgumentCaptor<List> scanFiltersCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<ScanSettings> scanSettingsCaptor = ArgumentCaptor.forClass(ScanSettings.class);
        ArgumentCaptor<ScanCallback> scanCallbackCaptor = ArgumentCaptor.forClass(ScanCallback.class);
        verify(scanner).startScan(scanFiltersCaptor.capture(), scanSettingsCaptor.capture(), scanCallbackCaptor.capture());

        // Verify there is no filter set
        List<ScanFilter> filters = scanFiltersCaptor.getValue();
        assertNull(filters);

        // Grab the scan callback that is used
        Field field = BluetoothCentral.class.getDeclaredField("scanByNameCallback");
        field.setAccessible(true);
        ScanCallback scanCallback = (ScanCallback) field.get(central);

        // Fake scan result
        ScanResult scanResult = mock(ScanResult.class);
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getName()).thenReturn("Polar H7");
        when(scanResult.getDevice()).thenReturn(device);
        scanCallback.onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult);

        // See if we get it back
        ArgumentCaptor<BluetoothPeripheral> bluetoothPeripheralCaptor = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<ScanResult> scanResultCaptor = ArgumentCaptor.forClass(ScanResult.class);
        verify(callback).onDiscoveredPeripheral(bluetoothPeripheralCaptor.capture(), scanResultCaptor.capture());

        assertEquals(scanResultCaptor.getValue(), scanResult);
        assertEquals(bluetoothPeripheralCaptor.getValue().getName(), "Polar H7");
    }

    @Test
    public void scanFailedTest() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        central.scanForPeripherals();
        verify(scanner).startScan(anyList(), any(ScanSettings.class), any(ScanCallback.class));

        // Grab the scan callback that is used
        Field field = BluetoothCentral.class.getDeclaredField("scanByServiceUUIDCallback");
        field.setAccessible(true);
        ScanCallback scanCallback = (ScanCallback) field.get(central);

        scanCallback.onScanFailed(SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES);

        verify(callback).onScanFailed(SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES);
    }

    @Test
    public void scanFailedAutoconnectTest() throws Exception {
        // Grab the scan callback that is used
        Field field = BluetoothCentral.class.getDeclaredField("autoConnectScanCallback");
        field.setAccessible(true);
        ScanCallback scanCallback = (ScanCallback) field.get(central);

        scanCallback.onScanFailed(SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES);

        verify(callback).onScanFailed(SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES);
    }

    @Test
    public void scanForNamesFailedTest() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        String myName = "Polar";
        central.scanForPeripheralsWithNames(new String[]{myName});

        // Grab the scan callback that is used
        Field field = BluetoothCentral.class.getDeclaredField("scanByNameCallback");
        field.setAccessible(true);
        ScanCallback scanCallback = (ScanCallback) field.get(central);

        scanCallback.onScanFailed(SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES);

        verify(callback).onScanFailed(SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES);
    }

    @Test
    public void stopScanTest() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        central.scanForPeripherals();
        verify(scanner).startScan(anyList(), any(ScanSettings.class), any(ScanCallback.class));

        // Grab the scan callback that is used
        Field field = BluetoothCentral.class.getDeclaredField("scanByServiceUUIDCallback");
        field.setAccessible(true);
        ScanCallback scanCallback = (ScanCallback) field.get(central);

        // Stop scan
        central.stopScan();

        // Check if scan is correctly stopped
        verify(scanner).stopScan(scanCallback);

        // Stop scan again
        central.stopScan();

        // Verify that stopScan is not called again
        verify(scanner, times(1)).stopScan(any(ScanCallback.class));
    }

    @Test
    public void connectPeripheralTest() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(BluetoothDevice.DEVICE_TYPE_LE);

        central.connectPeripheral(peripheral, peripheralCallback);

        verify(peripheral).connect();

        // Grab the scan callback that is used
        Field field = BluetoothCentral.class.getDeclaredField("internalCallback");
        field.setAccessible(true);
        BluetoothPeripheral.InternalCallback internalCallback = (BluetoothPeripheral.InternalCallback) field.get(central);

        // Give connected event and see if we get callback
        internalCallback.connected(peripheral);

        verify(callback).onConnectedPeripheral(peripheral);
    }

    @Test
    public void connectPeripheralAlreadyConnectedTest() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(BluetoothDevice.DEVICE_TYPE_LE);

        central.connectPeripheral(peripheral, peripheralCallback);

        verify(peripheral).connect();

        // Grab the scan callback that is used
        Field field = BluetoothCentral.class.getDeclaredField("internalCallback");
        field.setAccessible(true);
        BluetoothPeripheral.InternalCallback internalCallback = (BluetoothPeripheral.InternalCallback) field.get(central);

        // Give connected event and see if we get callback
        internalCallback.connected(peripheral);

        verify(callback).onConnectedPeripheral(peripheral);

        central.connectPeripheral(peripheral, peripheralCallback);

        verify(peripheral, times(1)).connect();
    }

    @Test
    public void connectPeripheralConnectingTest() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(BluetoothDevice.DEVICE_TYPE_LE);

        central.connectPeripheral(peripheral, peripheralCallback);

        verify(peripheral).connect();

        central.connectPeripheral(peripheral, peripheralCallback);

        verify(peripheral, times(1)).connect();
    }

    @Test
    public void connectionFailedRetryTest() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(BluetoothDevice.DEVICE_TYPE_LE);

        central.connectPeripheral(peripheral, peripheralCallback);

        verify(peripheral).connect();

        // Grab the scan callback that is used
        Field field = BluetoothCentral.class.getDeclaredField("internalCallback");
        field.setAccessible(true);
        BluetoothPeripheral.InternalCallback internalCallback = (BluetoothPeripheral.InternalCallback) field.get(central);

        // Give connected event and see if we get callback
        internalCallback.connectFailed(peripheral, 133);

        // We should not get a connection failed but a retry with autoconnect instead
        verify(callback, never()).onConnectionFailed(peripheral, 133);
        verify(peripheral, times(2)).connect();
    }

    @Test
    public void connectionFailedAfterRetryTest() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(BluetoothDevice.DEVICE_TYPE_LE);

        central.connectPeripheral(peripheral, peripheralCallback);

        verify(peripheral).connect();

        // Grab the scan callback that is used
        Field field = BluetoothCentral.class.getDeclaredField("internalCallback");
        field.setAccessible(true);
        BluetoothPeripheral.InternalCallback internalCallback = (BluetoothPeripheral.InternalCallback) field.get(central);

        // Give connected event and see if we get callback
        internalCallback.connectFailed(peripheral, 133);
        internalCallback.connectFailed(peripheral, 133);

        // We should not get a connection failed after 2 failed attempts
        verify(callback).onConnectionFailed(peripheral, 133);
    }

    @Test
    public void getConnectedPeripheralsTest() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(BluetoothDevice.DEVICE_TYPE_LE);

        central.connectPeripheral(peripheral, peripheralCallback);

        verify(peripheral).connect();

        // Grab the scan callback that is used
        Field field = BluetoothCentral.class.getDeclaredField("internalCallback");
        field.setAccessible(true);
        BluetoothPeripheral.InternalCallback internalCallback = (BluetoothPeripheral.InternalCallback) field.get(central);

        // Give connected event and see if we get callback
        internalCallback.connected(peripheral);

        verify(callback).onConnectedPeripheral(peripheral);

        List<BluetoothPeripheral> peripherals = central.getConnectedPeripherals();
        assertNotNull(peripherals);
        assertEquals(1, peripherals.size());
        assertEquals(peripheral, peripherals.get(0));

        peripheral.cancelConnection();

        internalCallback.disconnected(peripheral, GATT_SUCCESS);

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
    public void cancelConnectionPeripheralTest() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(BluetoothDevice.DEVICE_TYPE_LE);

        central.connectPeripheral(peripheral, peripheralCallback);

        verify(peripheral).connect();

        // Grab the scan callback that is used
        Field field = BluetoothCentral.class.getDeclaredField("internalCallback");
        field.setAccessible(true);
        BluetoothPeripheral.InternalCallback internalCallback = (BluetoothPeripheral.InternalCallback) field.get(central);

        // Give connected event and see if we get callback
        internalCallback.connected(peripheral);

        verify(callback).onConnectedPeripheral(peripheral);

        central.cancelConnection(peripheral);

        verify(peripheral).cancelConnection();

        internalCallback.disconnected(peripheral, GATT_SUCCESS);

        verify(callback).onDisconnectedPeripheral(peripheral, GATT_SUCCESS);
    }

    @Test
    public void cancelConnectionUnconnectedPeripheralTest() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(BluetoothDevice.DEVICE_TYPE_LE);

        central.connectPeripheral(peripheral, peripheralCallback);

        verify(peripheral).connect();

        central.cancelConnection(peripheral);

        verify(peripheral).cancelConnection();

        // Grab the scan callback that is used
        Field field = BluetoothCentral.class.getDeclaredField("internalCallback");
        field.setAccessible(true);
        BluetoothPeripheral.InternalCallback internalCallback = (BluetoothPeripheral.InternalCallback) field.get(central);

        internalCallback.disconnected(peripheral, GATT_SUCCESS);

        verify(callback).onDisconnectedPeripheral(peripheral, GATT_SUCCESS);
    }

    @Test
    public void cancelConnectionReconnectingPeripheralTest() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(BluetoothDevice.DEVICE_TYPE_UNKNOWN);

        central.autoConnectPeripheral(peripheral, peripheralCallback);

        central.cancelConnection(peripheral);

        verify(callback).onDisconnectedPeripheral(peripheral, GATT_SUCCESS);
    }

    @Test
    public void autoconnectTestCached() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(BluetoothDevice.DEVICE_TYPE_LE);

        central.autoConnectPeripheral(peripheral, peripheralCallback);

        verify(peripheral).autoConnect();

        // Grab the scan callback that is used
        Field field = BluetoothCentral.class.getDeclaredField("internalCallback");
        field.setAccessible(true);
        BluetoothPeripheral.InternalCallback internalCallback = (BluetoothPeripheral.InternalCallback) field.get(central);

        // Give connected event and see if we get callback
        internalCallback.connected(peripheral);

        verify(callback).onConnectedPeripheral(peripheral);
    }

    @Test
    public void autoconnectTestUnCached() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getAddress()).thenReturn("12:23:34:98:76:54");
        when(device.getType()).thenReturn(BluetoothDevice.DEVICE_TYPE_LE);
        bluetoothAdapter.addDevice(device);

        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(BluetoothDevice.DEVICE_TYPE_UNKNOWN);
        central.autoConnectPeripheral(peripheral, peripheralCallback);

        verify(peripheral, never()).autoConnect();
        verify(scanner).startScan(anyList(), any(ScanSettings.class), any(ScanCallback.class));

        // Grab the scan callback that is used
        Field field = BluetoothCentral.class.getDeclaredField("autoConnectScanCallback");
        field.setAccessible(true);
        ScanCallback scanCallback = (ScanCallback) field.get(central);

        // Fake scan result
        ScanResult scanResult = mock(ScanResult.class);
        when(scanResult.getDevice()).thenReturn(device);
        scanCallback.onScanResult(CALLBACK_TYPE_ALL_MATCHES, scanResult);

        verify(peripheral).connect();
    }


    @Test
    public void autoconnectPeripheralConnectedTest() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(BluetoothDevice.DEVICE_TYPE_LE);

        central.connectPeripheral(peripheral, peripheralCallback);

        verify(peripheral).connect();

        // Grab the scan callback that is used
        Field field = BluetoothCentral.class.getDeclaredField("internalCallback");
        field.setAccessible(true);
        BluetoothPeripheral.InternalCallback internalCallback = (BluetoothPeripheral.InternalCallback) field.get(central);

        // Give connected event and see if we get callback
        internalCallback.connected(peripheral);

        verify(callback).onConnectedPeripheral(peripheral);

        central.autoConnectPeripheral(peripheral, peripheralCallback);

        verify(peripheral, never()).autoConnect();
    }

    @Test
    public void autoconnectTwice() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(BluetoothDevice.DEVICE_TYPE_UNKNOWN);
        central.autoConnectPeripheral(peripheral, peripheralCallback);

        verify(peripheral, never()).autoConnect();
        verify(scanner).startScan(anyList(), any(ScanSettings.class), any(ScanCallback.class));

        central.autoConnectPeripheral(peripheral, peripheralCallback);

        verify(peripheral, never()).autoConnect();
    }

    @Test
    public void getPeripheralWrongMacAddressTest() {
        // Get peripheral and supply lowercase mac address
        BluetoothPeripheral peripheral = central.getPeripheral("ac:de:ef:12:34:56");
        assertNull(peripheral);
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
    public void getPeripheralConnectedTest() throws Exception {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        BluetoothPeripheral peripheral = mock(BluetoothPeripheral.class);
        when(peripheral.getAddress()).thenReturn("12:23:34:98:76:54");
        when(peripheral.getType()).thenReturn(BluetoothDevice.DEVICE_TYPE_LE);

        central.connectPeripheral(peripheral, peripheralCallback);

        verify(peripheral).connect();

        // Grab the scan callback that is used
        Field field = BluetoothCentral.class.getDeclaredField("internalCallback");
        field.setAccessible(true);
        BluetoothPeripheral.InternalCallback internalCallback = (BluetoothPeripheral.InternalCallback) field.get(central);

        // Give connected event and see if we get callback
        internalCallback.connected(peripheral);

        verify(callback).onConnectedPeripheral(peripheral);

        BluetoothPeripheral peripheral2 = central.getPeripheral("12:23:34:98:76:54");
        assertEquals(peripheral, peripheral2);
    }


    @Test
    public void bluetoothOffTest() {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        bluetoothAdapter.setEnabled(false);
        central.scanForPeripherals();
        verify(scanner, never()).startScan(anyList(), any(ScanSettings.class), any(ScanCallback.class));
    }

    @Test
    public void noPermissionTest() {
        central.scanForPeripherals();
        verify(scanner, never()).startScan(anyList(), any(ScanSettings.class), any(ScanCallback.class));
    }
}