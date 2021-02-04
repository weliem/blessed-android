package com.welie.blessed;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.UUID;

import static android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static android.os.Build.VERSION_CODES.M;
import static org.robolectric.RuntimeEnvironment.application;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = { M })
public class BluetoothPeripheralManagerTest {

    private static final UUID DIS_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
    private static final UUID MANUFACTURER_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");

    private BluetoothPeripheralManager peripheralManager;

    @Mock
    BluetoothManager bluetoothManager;

    @Mock
    BluetoothAdapter bluetoothAdapter;

    @Mock
    BluetoothLeAdvertiser bluetoothLeAdvertiser;

    @Mock
    BluetoothGattServer server;

    @Mock
    BluetoothPeripheralManagerCallback peripheralManagerCallback;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        Context context = application.getApplicationContext();
        when(bluetoothManager.getAdapter()).thenReturn(bluetoothAdapter);
        when(bluetoothAdapter.getBluetoothLeAdvertiser()).thenReturn(bluetoothLeAdvertiser);
        when(bluetoothManager.openGattServer(any(Context.class), any(BluetoothGattServerCallback.class))).thenReturn(server);
        peripheralManager = new BluetoothPeripheralManager(context, bluetoothManager, peripheralManagerCallback);
    }

    @Test
    public void When_addService_is_called_with_a_valid_service_then_the_operation_is_enqueued_and_onServiceAdded_is_called_upon_completed() {
        BluetoothGattService service = new BluetoothGattService(DIS_SERVICE_UUID, SERVICE_TYPE_PRIMARY);
        when(server.addService(any(BluetoothGattService.class))).thenReturn(true);
        peripheralManager.add(service);

        verify(server, timeout(1000)).addService(service);
        assertEquals(1, peripheralManager.commandQueue.size());

        peripheralManager.bluetoothGattServerCallback.onServiceAdded(GattStatus.SUCCESS.getValue(), service);

        verify(peripheralManagerCallback, timeout(1000)).onServiceAdded(GattStatus.SUCCESS.getValue(), service);
        assertEquals(0, peripheralManager.commandQueue.size());
    }

    @Test
    public void When_removeService_is_called_then_the_service_is_removed() {
        // When
        BluetoothGattService service = new BluetoothGattService(DIS_SERVICE_UUID, SERVICE_TYPE_PRIMARY);
        peripheralManager.remove(service);

        // Then
        verify(server).removeService(service);
    }

    @Test
    public void When_removeAllServices_is_called_then_all_services_are_removed() {
        // When
        peripheralManager.removeAllServices();

        // Then
        verify(server).clearServices();
    }

    @Test
    public void When_getServices_is_called_then_all_services_are_returned() {
        // When
        peripheralManager.getServices();

        // Then
        verify(server).getServices();
    }
}
