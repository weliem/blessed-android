package com.welie.blessed;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.ParcelUuid;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.verification.Timeout;
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
@Config(constants = BuildConfig.class, sdk = {M})
public class BluetoothPeripheralManagerTest {

    private static final UUID DIS_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
    private static final UUID MANUFACTURER_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");
    private static final int VERIFY_MARGIN = 100;

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

    @Mock
    BluetoothDevice device;

    @Mock
    BluetoothGattCharacteristic characteristic;

    @Mock
    BluetoothGattDescriptor descriptor;

    @Mock
    BluetoothCentral central;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        Context context = application.getApplicationContext();
        when(device.getAddress()).thenReturn("12:23:34:98:76:54");
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

        verify(server, timeout(VERIFY_MARGIN)).addService(service);
        assertEquals(1, peripheralManager.commandQueue.size());

        peripheralManager.bluetoothGattServerCallback.onServiceAdded(GattStatus.SUCCESS.getValue(), service);

        verify(peripheralManagerCallback, timeout(VERIFY_MARGIN)).onServiceAdded(GattStatus.SUCCESS.getValue(), service);
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

    @Test
    public void When_close_is_called_then_advertising_is_stopped_and_the_server_is_closed() {
        // When
        peripheralManager.close();

        // Then
        verify(bluetoothLeAdvertiser).stopAdvertising(any(AdvertiseCallback.class));
        verify(peripheralManagerCallback).onAdvertisingStopped();
        verify(server).close();
    }

    @Test
    public void When_startAdvertising_is_called_then_advertising_is_started() {
        // Given
        when(bluetoothAdapter.isMultipleAdvertisementSupported()).thenReturn(true);
        AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(new ParcelUuid(DIS_SERVICE_UUID))
                .build();

        AdvertiseData scanResponse = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build();

        // When
        peripheralManager.startAdvertising(advertiseSettings, advertiseData, scanResponse);

        // Then
        verify(bluetoothLeAdvertiser).startAdvertising(advertiseSettings, advertiseData, scanResponse, peripheralManager.advertiseCallback);
    }

    @Test
    public void When_a_read_characteristic_request_is_received_then_onCharacteristicRead_is_called() {
        // When
        peripheralManager.bluetoothGattServerCallback.onCharacteristicReadRequest(device, 1, 0, characteristic);

        // Then
        verify(peripheralManagerCallback, timeout(VERIFY_MARGIN)).onCharacteristicRead(any(BluetoothCentral.class), any(BluetoothGattCharacteristic.class));
    }

    @Test
    public void When_a_read_characteristic_request_is_received_then_characteristic_value_is_returned() {
        // Given
        byte[] value = new byte[]{0x00, 0x01, 0x02};
        when(characteristic.getValue()).thenReturn(value);

        // When
        peripheralManager.bluetoothGattServerCallback.onCharacteristicReadRequest(device, 1, 0, characteristic);

        // Then
        verify(server, timeout(VERIFY_MARGIN)).sendResponse(device, 1, GattStatus.SUCCESS.getValue(), 0, value);
    }

    @Test
    public void When_a_long_read_characteristic_request_is_received_then_characteristic_value_is_returned_in_chunks() {
        // Given
        byte[] value = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24};
        byte[] firstChunk = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22};
        byte[] secondChunk = new byte[]{23, 24};
        when(characteristic.getValue()).thenReturn(value);

        // When
        peripheralManager.bluetoothGattServerCallback.onCharacteristicReadRequest(device, 1, 0, characteristic);

        // Then
        verify(server, timeout(VERIFY_MARGIN)).sendResponse(device, 1, GattStatus.SUCCESS.getValue(), 0, firstChunk);

        // When
        peripheralManager.bluetoothGattServerCallback.onCharacteristicReadRequest(device, 2, 22, characteristic);

        // Then
        verify(server, timeout(VERIFY_MARGIN)).sendResponse(device, 2, GattStatus.SUCCESS.getValue(), 22, secondChunk);
    }

    @Test
    public void When_a_read_descriptor_request_is_received_then_onDescriptorRead_is_called() {
        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorReadRequest(device, 1, 0, descriptor);

        // Then
        verify(peripheralManagerCallback, timeout(VERIFY_MARGIN)).onDescriptorRead(any(BluetoothCentral.class), any(BluetoothGattDescriptor.class));
    }

    @Test
    public void When_a_read_descriptor_request_is_received_then_descriptor_value_is_returned() {
        // Given
        byte[] value = new byte[]{0x00, 0x01, 0x02};
        when(descriptor.getValue()).thenReturn(value);

        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorReadRequest(device, 1, 0, descriptor);

        // Then
        verify(server, timeout(VERIFY_MARGIN)).sendResponse(device, 1, GattStatus.SUCCESS.getValue(), 0, value);
    }

    @Test
    public void When_a_long_read_descriptor_request_is_received_then_Descriptor_value_is_returned_in_chunks() {
        // Given
        byte[] value = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24};
        byte[] firstChunk = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22};
        byte[] secondChunk = new byte[]{23, 24};
        when(descriptor.getValue()).thenReturn(value);

        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorReadRequest(device, 1, 0, descriptor);

        // Then
        verify(server, timeout(VERIFY_MARGIN)).sendResponse(device, 1, GattStatus.SUCCESS.getValue(), 0, firstChunk);

        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorReadRequest(device, 2, 22, descriptor);

        // Then
        verify(server, timeout(VERIFY_MARGIN)).sendResponse(device, 2, GattStatus.SUCCESS.getValue(), 22, secondChunk);
    }

    @Test
    public void When_a_write_characteristic_request_is_received_then_onCharacteristicWrite_is_called() {
        // Given
        byte[] value = new byte[]{0x00, 0x01, 0x02};
        when(peripheralManagerCallback.onCharacteristicWrite(any(BluetoothCentral.class), any(BluetoothGattCharacteristic.class), any(byte[].class))).thenReturn(GattStatus.SUCCESS);

        // When
        peripheralManager.bluetoothGattServerCallback.onCharacteristicWriteRequest(device, 1, characteristic, false, true, 0, value);

        // Then
        verify(peripheralManagerCallback, timeout(VERIFY_MARGIN)).onCharacteristicWrite(any(BluetoothCentral.class), any(BluetoothGattCharacteristic.class), any(byte[].class));
    }

    @Test
    public void When_a_write_characteristic_request_is_received_and_approved_then_characteristic_value_is_set_and_confirmed() {
        // Given
        byte[] value = new byte[]{0x00, 0x01, 0x02};
        when(characteristic.getValue()).thenReturn(value);
        when(peripheralManagerCallback.onCharacteristicWrite(any(BluetoothCentral.class), any(BluetoothGattCharacteristic.class), any(byte[].class))).thenReturn(GattStatus.SUCCESS);

        // When
        peripheralManager.bluetoothGattServerCallback.onCharacteristicWriteRequest(device, 1, characteristic, false, true, 0, value);

        // Then
        verify(characteristic, timeout(VERIFY_MARGIN)).setValue(value);
        verify(server, timeout(VERIFY_MARGIN)).sendResponse(device, 1, GattStatus.SUCCESS.getValue(), 0, value);
    }

    @Test
    public void When_a_write_characteristic_request_is_received_and_not_approved_then_characteristic_value_is_not_set_and_confirmed() {
        // Given
        byte[] value = new byte[]{0x00, 0x01, 0x02};
        when(characteristic.getValue()).thenReturn(value);
        when(peripheralManagerCallback.onCharacteristicWrite(any(BluetoothCentral.class), any(BluetoothGattCharacteristic.class), any(byte[].class))).thenReturn(GattStatus.VALUE_NOT_ALLOWED);

        // When
        peripheralManager.bluetoothGattServerCallback.onCharacteristicWriteRequest(device, 1, characteristic, false, true, 0, value);

        // Then
        verify(characteristic, never()).setValue(value);
        verify(server, timeout(VERIFY_MARGIN)).sendResponse(device, 1, GattStatus.VALUE_NOT_ALLOWED.getValue(), 0, value);
    }

    @Test
    public void When_a_long_write_characteristic_requests_are_received_and_approved_then_characteristic_value_is_set_and_confirmed() {
        // Given
        byte[] value = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24};
        byte[] firstChunk = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18};
        byte[] secondChunk = new byte[]{19, 20, 21, 22, 23, 24};
        when(characteristic.getValue()).thenReturn(value);
        when(peripheralManagerCallback.onCharacteristicWrite(any(BluetoothCentral.class), any(BluetoothGattCharacteristic.class), any(byte[].class))).thenReturn(GattStatus.SUCCESS);

        // When
        peripheralManager.bluetoothGattServerCallback.onCharacteristicWriteRequest(device, 1, characteristic, true, true, 0, firstChunk);

        // Then
        verify(server, timeout(VERIFY_MARGIN)).sendResponse(device, 1, GattStatus.SUCCESS.getValue(), 0, firstChunk);

        // When
        peripheralManager.bluetoothGattServerCallback.onCharacteristicWriteRequest(device, 2, characteristic, true, true, 18, secondChunk);

        // Then
        verify(server, timeout(VERIFY_MARGIN)).sendResponse(device, 2, GattStatus.SUCCESS.getValue(), 18, secondChunk);

        // When
        peripheralManager.bluetoothGattServerCallback.onExecuteWrite(device, 3,  true);

        // Then
        verify(peripheralManagerCallback, timeout(VERIFY_MARGIN)).onCharacteristicWrite(any(BluetoothCentral.class), any(BluetoothGattCharacteristic.class), any(byte[].class));
        verify(characteristic, timeout(VERIFY_MARGIN)).setValue(value);
        verify(server, timeout(VERIFY_MARGIN)).sendResponse(device, 3, GattStatus.SUCCESS.getValue(), 0, null);
    }

    @Test
    public void When_a_long_write_characteristic_requests_are_received_with_incorrect_offset_then_the_error_invalid_offset_is_given() {
        // Given
        byte[] value = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24};
        byte[] firstChunk = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18};
        byte[] secondChunk = new byte[]{19, 20, 21, 22, 23, 24};
        when(characteristic.getValue()).thenReturn(value);
        when(peripheralManagerCallback.onCharacteristicWrite(any(BluetoothCentral.class), any(BluetoothGattCharacteristic.class), any(byte[].class))).thenReturn(GattStatus.SUCCESS);

        // When
        peripheralManager.bluetoothGattServerCallback.onCharacteristicWriteRequest(device, 1, characteristic, true, true, 0, firstChunk);

        // Then
        verify(server, timeout(VERIFY_MARGIN)).sendResponse(device, 1, GattStatus.SUCCESS.getValue(), 0, firstChunk);

        // When
        peripheralManager.bluetoothGattServerCallback.onCharacteristicWriteRequest(device, 2, characteristic, true, true, 19, secondChunk);

        // Then
        verify(server, timeout(VERIFY_MARGIN)).sendResponse(device, 2, GattStatus.INVALID_OFFSET.getValue(), 19, secondChunk);
    }
}
