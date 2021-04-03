package com.welie.blessed;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.ParcelUuid;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.Collections;
import java.util.UUID;

import static android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY;
import static com.welie.blessed.BluetoothPeripheralManager.CCC_DESCRIPTOR_UUID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static android.os.Build.VERSION_CODES.M;
import static org.mockito.MockitoAnnotations.openMocks;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE, sdk = {M})
public class BluetoothPeripheralManagerTest {

    private static final UUID DIS_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");

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

    @Before
    public void setUp() {
        openMocks(this);
        Context context = ApplicationProvider.getApplicationContext();
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

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(server).addService(service);
        assertEquals(1, peripheralManager.commandQueue.size());

        peripheralManager.bluetoothGattServerCallback.onServiceAdded(GattStatus.SUCCESS.value, service);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(peripheralManagerCallback).onServiceAdded(GattStatus.SUCCESS, service);
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
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

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

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralManagerCallback).onCharacteristicRead(any(BluetoothCentral.class), any(BluetoothGattCharacteristic.class));
    }

    @Test
    public void When_a_read_characteristic_request_is_received_then_characteristic_value_is_returned() {
        // Given
        byte[] value = new byte[]{0x00, 0x01, 0x02};
        when(characteristic.getValue()).thenReturn(value);

        // When
        peripheralManager.bluetoothGattServerCallback.onCharacteristicReadRequest(device, 1, 0, characteristic);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).sendResponse(device, 1, GattStatus.SUCCESS.value, 0, value);
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
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).sendResponse(device, 1, GattStatus.SUCCESS.value, 0, firstChunk);

        // When
        peripheralManager.bluetoothGattServerCallback.onCharacteristicReadRequest(device, 2, 22, characteristic);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).sendResponse(device, 2, GattStatus.SUCCESS.value, 22, secondChunk);
    }

    @Test
    public void When_a_read_descriptor_request_is_received_then_onDescriptorRead_is_called() {
        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorReadRequest(device, 1, 0, descriptor);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralManagerCallback).onDescriptorRead(any(BluetoothCentral.class), any(BluetoothGattDescriptor.class));
    }

    @Test
    public void When_a_read_descriptor_request_is_received_then_descriptor_value_is_returned() {
        // Given
        byte[] value = new byte[]{0x00, 0x01, 0x02};
        when(descriptor.getValue()).thenReturn(value);

        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorReadRequest(device, 1, 0, descriptor);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).sendResponse(device, 1, GattStatus.SUCCESS.value, 0, value);
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
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).sendResponse(device, 1, GattStatus.SUCCESS.value, 0, firstChunk);

        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorReadRequest(device, 2, 22, descriptor);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).sendResponse(device, 2, GattStatus.SUCCESS.value, 22, secondChunk);
    }

    @Test
    public void When_a_write_characteristic_request_is_received_then_onCharacteristicWrite_is_called() {
        // Given
        byte[] value = new byte[]{0x00, 0x01, 0x02};
        when(peripheralManagerCallback.onCharacteristicWrite(any(BluetoothCentral.class), any(BluetoothGattCharacteristic.class), any(byte[].class))).thenReturn(GattStatus.SUCCESS);

        // When
        peripheralManager.bluetoothGattServerCallback.onCharacteristicWriteRequest(device, 1, characteristic, false, true, 0, value);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralManagerCallback).onCharacteristicWrite(any(BluetoothCentral.class), any(BluetoothGattCharacteristic.class), any(byte[].class));
    }

    @Test
    public void When_a_write_characteristic_request_is_received_and_approved_then_characteristic_value_is_set_and_confirmed() {
        // Given
        byte[] value = new byte[]{0x00, 0x01, 0x02};
        when(characteristic.getValue()).thenReturn(value);
        when(peripheralManagerCallback.onCharacteristicWrite(any(BluetoothCentral.class), any(BluetoothGattCharacteristic.class), any(byte[].class))).thenReturn(GattStatus.SUCCESS);

        // When
        peripheralManager.bluetoothGattServerCallback.onCharacteristicWriteRequest(device, 1, characteristic, false, true, 0, value);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(characteristic).setValue(value);
        verify(server).sendResponse(device, 1, GattStatus.SUCCESS.value, 0, value);
    }

    @Test
    public void When_a_write_characteristic_request_is_received_and_not_approved_then_characteristic_value_is_not_set_and_confirmed() {
        // Given
        byte[] value = new byte[]{0x00, 0x01, 0x02};
        when(characteristic.getValue()).thenReturn(value);
        when(peripheralManagerCallback.onCharacteristicWrite(any(BluetoothCentral.class), any(BluetoothGattCharacteristic.class), any(byte[].class))).thenReturn(GattStatus.VALUE_NOT_ALLOWED);

        // When
        peripheralManager.bluetoothGattServerCallback.onCharacteristicWriteRequest(device, 1, characteristic, false, true, 0, value);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(characteristic, never()).setValue(value);
        verify(server).sendResponse(device, 1, GattStatus.VALUE_NOT_ALLOWED.value, 0, value);
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
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).sendResponse(device, 1, GattStatus.SUCCESS.value, 0, firstChunk);

        // When
        peripheralManager.bluetoothGattServerCallback.onCharacteristicWriteRequest(device, 2, characteristic, true, true, 18, secondChunk);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).sendResponse(device, 2, GattStatus.SUCCESS.value, 18, secondChunk);

        // When
        peripheralManager.bluetoothGattServerCallback.onExecuteWrite(device, 3, true);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralManagerCallback).onCharacteristicWrite(any(BluetoothCentral.class), any(BluetoothGattCharacteristic.class), any(byte[].class));
        verify(characteristic).setValue(value);
        verify(server).sendResponse(device, 3, GattStatus.SUCCESS.value, 0, null);
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
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).sendResponse(device, 1, GattStatus.SUCCESS.value, 0, firstChunk);

        // When
        peripheralManager.bluetoothGattServerCallback.onCharacteristicWriteRequest(device, 2, characteristic, true, true, 19, secondChunk);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).sendResponse(device, 2, GattStatus.INVALID_OFFSET.value, 19, secondChunk);
    }

    @Test
    public void When_a_CCC_descriptor_is_written_with_valid_indicate_value_then_it_is_set_and_onNotifyEnabled_is_called() {
        // Given
        when(descriptor.getCharacteristic()).thenReturn(characteristic);
        when(characteristic.getProperties()).thenReturn(BluetoothGattCharacteristic.PROPERTY_INDICATE);
        when(descriptor.getPermissions()).thenReturn(BluetoothGattDescriptor.PERMISSION_WRITE);
        when(descriptor.getUuid()).thenReturn(CCC_DESCRIPTOR_UUID);

        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, false, true, 0, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(descriptor).setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        verify(peripheralManagerCallback).onNotifyingEnabled(any(BluetoothCentral.class), any(BluetoothGattCharacteristic.class));
    }

    @Test
    public void When_a_CCC_descriptor_is_written_with_valid_notify_value_then_it_is_set_and_onNotifyEnabled_is_called() {
        // Given
        when(descriptor.getCharacteristic()).thenReturn(characteristic);
        when(characteristic.getProperties()).thenReturn(BluetoothGattCharacteristic.PROPERTY_NOTIFY);
        when(descriptor.getPermissions()).thenReturn(BluetoothGattDescriptor.PERMISSION_WRITE);
        when(descriptor.getUuid()).thenReturn(CCC_DESCRIPTOR_UUID);

        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, false, true, 0, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(descriptor).setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        verify(peripheralManagerCallback).onNotifyingEnabled(any(BluetoothCentral.class), any(BluetoothGattCharacteristic.class));
    }

    @Test
    public void When_a_CCC_descriptor_is_written_with_valid_disable_value_then_it_is_set_and_onNotifyDisable_is_called() {
        // Given
        when(descriptor.getCharacteristic()).thenReturn(characteristic);
        when(characteristic.getProperties()).thenReturn(BluetoothGattCharacteristic.PROPERTY_NOTIFY);
        when(descriptor.getPermissions()).thenReturn(BluetoothGattDescriptor.PERMISSION_WRITE);
        when(descriptor.getUuid()).thenReturn(CCC_DESCRIPTOR_UUID);

        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, false, true, 0, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(descriptor).setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        verify(peripheralManagerCallback).onNotifyingDisabled(any(BluetoothCentral.class), any(BluetoothGattCharacteristic.class));
    }

    @Test
    public void When_a_CCC_descriptor_for_a_indicate_characteristic_is_written_with_invalid_value_then_an_error_is_given() {
        // Given
        when(descriptor.getCharacteristic()).thenReturn(characteristic);
        when(characteristic.getProperties()).thenReturn(BluetoothGattCharacteristic.PROPERTY_INDICATE);
        when(descriptor.getPermissions()).thenReturn(BluetoothGattDescriptor.PERMISSION_WRITE);
        when(descriptor.getUuid()).thenReturn(CCC_DESCRIPTOR_UUID);

        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, false, true, 0, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).sendResponse(device, 1, GattStatus.REQUEST_NOT_SUPPORTED.value, 0, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
    }

    @Test
    public void When_a_CCC_descriptor_for_a_notify_characteristic_is_written_with_invalid_value_then_an_error_is_given() {
        // Given
        when(descriptor.getCharacteristic()).thenReturn(characteristic);
        when(characteristic.getProperties()).thenReturn(BluetoothGattCharacteristic.PROPERTY_NOTIFY);
        when(descriptor.getPermissions()).thenReturn(BluetoothGattDescriptor.PERMISSION_WRITE);
        when(descriptor.getUuid()).thenReturn(CCC_DESCRIPTOR_UUID);

        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, false, true, 0, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).sendResponse(device, 1, GattStatus.REQUEST_NOT_SUPPORTED.value, 0, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
    }

    @Test
    public void When_a_CCC_descriptor_is_written_with_invalid_length_value_then_an_error_is_given() {
        // Given
        when(descriptor.getCharacteristic()).thenReturn(characteristic);
        when(characteristic.getProperties()).thenReturn(BluetoothGattCharacteristic.PROPERTY_NOTIFY);
        when(descriptor.getPermissions()).thenReturn(BluetoothGattDescriptor.PERMISSION_WRITE);
        when(descriptor.getUuid()).thenReturn(CCC_DESCRIPTOR_UUID);
        byte[] value = new byte[]{0x00};

        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, false, true, 0, value);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).sendResponse(device, 1, GattStatus.INVALID_ATTRIBUTE_VALUE_LENGTH.value, 0, value);
    }

    @Test
    public void When_a_CCC_descriptor_is_written_with_invalid_value_then_an_error_is_given() {
        // Given
        when(descriptor.getCharacteristic()).thenReturn(characteristic);
        when(characteristic.getProperties()).thenReturn(BluetoothGattCharacteristic.PROPERTY_NOTIFY);
        when(descriptor.getPermissions()).thenReturn(BluetoothGattDescriptor.PERMISSION_WRITE);
        when(descriptor.getUuid()).thenReturn(CCC_DESCRIPTOR_UUID);
        byte[] value = new byte[]{0x02, 0x01};

        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, false, true, 0, value);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).sendResponse(device, 1, GattStatus.VALUE_NOT_ALLOWED.value, 0, value);
    }

    @Test
    public void When_a_long_write_descriptor_requests_are_received_and_approved_then_descriptor_value_is_set_and_confirmed() {
        // Given
        byte[] value = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24};
        byte[] firstChunk = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18};
        byte[] secondChunk = new byte[]{19, 20, 21, 22, 23, 24};
        when(descriptor.getCharacteristic()).thenReturn(characteristic);
        when(descriptor.getValue()).thenReturn(value);
        when(descriptor.getUuid()).thenReturn(UUID.fromString("00002901-0000-1000-8000-00805f9b34fb"));
        when(peripheralManagerCallback.onDescriptorWrite(any(BluetoothCentral.class), any(BluetoothGattDescriptor.class), any(byte[].class))).thenReturn(GattStatus.SUCCESS);

        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, true, true, 0, firstChunk);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).sendResponse(device, 1, GattStatus.SUCCESS.value, 0, firstChunk);

        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorWriteRequest(device, 2, descriptor, true, true, 18, secondChunk);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).sendResponse(device, 2, GattStatus.SUCCESS.value, 18, secondChunk);

        // When
        peripheralManager.bluetoothGattServerCallback.onExecuteWrite(device, 3, true);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralManagerCallback).onDescriptorWrite(any(BluetoothCentral.class), any(BluetoothGattDescriptor.class), any(byte[].class));
        verify(descriptor).setValue(value);
        verify(server).sendResponse(device, 3, GattStatus.SUCCESS.value, 0, null);
    }

    @Test
    public void When_a_long_write_descriptor_requests_are_received_with_incorrect_offset_then_the_error_invalid_offset_is_given() {
        // Given
        byte[] value = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24};
        byte[] firstChunk = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18};
        byte[] secondChunk = new byte[]{19, 20, 21, 22, 23, 24};
        when(descriptor.getCharacteristic()).thenReturn(characteristic);
        when(descriptor.getValue()).thenReturn(value);
        when(descriptor.getUuid()).thenReturn(UUID.fromString("00002901-0000-1000-8000-00805f9b34fb"));
        when(peripheralManagerCallback.onCharacteristicWrite(any(BluetoothCentral.class), any(BluetoothGattCharacteristic.class), any(byte[].class))).thenReturn(GattStatus.SUCCESS);

        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, true, true, 0, firstChunk);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).sendResponse(device, 1, GattStatus.SUCCESS.value, 0, firstChunk);

        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorWriteRequest(device, 2, descriptor, true, true, 19, secondChunk);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).sendResponse(device, 2, GattStatus.INVALID_OFFSET.value, 19, secondChunk);
    }

    @Test
    public void When_a_write_descriptor_request_is_received_and_approved_then_descriptor_value_is_set_and_confirmed() {
        // Given
        byte[] value = new byte[]{0x00, 0x01, 0x02};
        when(descriptor.getValue()).thenReturn(value);
        when(descriptor.getCharacteristic()).thenReturn(characteristic);
        when(descriptor.getUuid()).thenReturn(UUID.fromString("00002901-0000-1000-8000-00805f9b34fb"));
        when(peripheralManagerCallback.onDescriptorWrite(any(BluetoothCentral.class), any(BluetoothGattDescriptor.class), any(byte[].class))).thenReturn(GattStatus.SUCCESS);

        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, false, true, 0, value);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(descriptor).setValue(value);
        verify(server).sendResponse(device, 1, GattStatus.SUCCESS.value, 0, value);
    }

    @Test
    public void When_long_write_descriptor_requests_are_received_and_cancelled_then_descriptor_value_is_not_set() {
        // Given
        byte[] value = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24};
        byte[] firstChunk = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18};
        when(descriptor.getCharacteristic()).thenReturn(characteristic);
        when(descriptor.getValue()).thenReturn(value);
        when(descriptor.getUuid()).thenReturn(UUID.fromString("00002901-0000-1000-8000-00805f9b34fb"));
        when(peripheralManagerCallback.onDescriptorWrite(any(BluetoothCentral.class), any(BluetoothGattDescriptor.class), any(byte[].class))).thenReturn(GattStatus.SUCCESS);

        // When
        peripheralManager.bluetoothGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, true, true, 0, firstChunk);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).sendResponse(device, 1, GattStatus.SUCCESS.value, 0, firstChunk);

        // When
        peripheralManager.bluetoothGattServerCallback.onExecuteWrite(device, 3, false);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralManagerCallback, never()).onDescriptorWrite(any(BluetoothCentral.class), any(BluetoothGattDescriptor.class), any(byte[].class));
        verify(descriptor, never()).setValue(value);
        verify(server).sendResponse(device, 3, GattStatus.SUCCESS.value, 0, null);
    }

    @Test
    public void When_notifyCharacteristicChanged_is_called_for_a_characteristic_that_does_not_notify_then_false_is_returned() {
        // Given
        when(characteristic.getProperties()).thenReturn(BluetoothGattCharacteristic.PROPERTY_READ);
        byte[] value = new byte[]{0x00, 0x01, 0x02};

        // When
        boolean result = peripheralManager.notifyCharacteristicChanged(value, characteristic);

        // Then
        assertFalse(result);
    }

    @Test
    public void When_notifyCharacteristicChanged_is_called_for_a_notify_characteristic_that_supports_notify_then_a_notification_is_sent() {
        // Given
        when(characteristic.getProperties()).thenReturn(BluetoothGattCharacteristic.PROPERTY_NOTIFY);
        when(characteristic.getValue()).thenReturn(new byte[]{0x00});
        byte[] value = new byte[]{0x00, 0x01, 0x02};

        // When
        peripheralManager.bluetoothGattServerCallback.onConnectionStateChange(device, GattStatus.SUCCESS.value, BluetoothProfile.STATE_CONNECTED);
        when(bluetoothManager.getConnectedDevices(BluetoothGattServer.GATT)).thenReturn(Collections.singletonList(device));
        boolean result = peripheralManager.notifyCharacteristicChanged(value, characteristic);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        assertTrue(result);
        verify(characteristic).setValue(value);

        when(characteristic.getValue()).thenReturn(value);
        verify(server).notifyCharacteristicChanged(device, characteristic, false);
    }

    @Test
    public void When_notifyCharacteristicChanged_is_called_for_a_indicate_characteristic_that_supports_notify_then_a_notification_is_sent() {
        // Given
        when(characteristic.getProperties()).thenReturn(BluetoothGattCharacteristic.PROPERTY_INDICATE);
        when(characteristic.getValue()).thenReturn(new byte[]{0x00});
        byte[] value = new byte[]{0x00, 0x01, 0x02};

        // When
        peripheralManager.bluetoothGattServerCallback.onConnectionStateChange(device, GattStatus.SUCCESS.value, BluetoothProfile.STATE_CONNECTED);
        when(bluetoothManager.getConnectedDevices(BluetoothGattServer.GATT)).thenReturn(Collections.singletonList(device));
        boolean result = peripheralManager.notifyCharacteristicChanged(value, characteristic);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        assertTrue(result);
        verify(characteristic).setValue(value);

        when(characteristic.getValue()).thenReturn(value);
        verify(server).notifyCharacteristicChanged(device, characteristic, true);
    }

    @Test
    public void When_a_notification_has_been_sent_then_onNotificationSent_is_sent() {
        // Given
        byte[] value = new byte[]{0x00, 0x01, 0x02};
        when(characteristic.getValue()).thenReturn(value);
        when(characteristic.getProperties()).thenReturn(BluetoothGattCharacteristic.PROPERTY_INDICATE);
        peripheralManager.bluetoothGattServerCallback.onConnectionStateChange(device, GattStatus.SUCCESS.value, BluetoothProfile.STATE_CONNECTED);
        when(bluetoothManager.getConnectedDevices(BluetoothGattServer.GATT)).thenReturn(Collections.singletonList(device));

        // When
        peripheralManager.notifyCharacteristicChanged(value, characteristic);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(server).notifyCharacteristicChanged(device, characteristic, true);

        peripheralManager.bluetoothGattServerCallback.onNotificationSent(device, GattStatus.SUCCESS.value);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        verify(peripheralManagerCallback).onNotificationSent(any(BluetoothCentral.class), any(byte[].class), any(BluetoothGattCharacteristic.class), any(GattStatus.class));
    }

    @Test
    public void When_a_central_connects_then_onCentralConnected_is_called() {
        // When
        peripheralManager.bluetoothGattServerCallback.onConnectionStateChange(device, GattStatus.SUCCESS.value, BluetoothProfile.STATE_CONNECTED);
        when(bluetoothManager.getConnectedDevices(BluetoothGattServer.GATT)).thenReturn(Collections.singletonList(device));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralManagerCallback).onCentralConnected(any(BluetoothCentral.class));
    }

    @Test
    public void Given_a_connected_central_when_cancelConnection_is_called_then_it_is_disconnected_and_onCentralDisconnected_is_called() {
        // Given
        when(bluetoothAdapter.getRemoteDevice(device.getAddress())).thenReturn(device);
        peripheralManager.bluetoothGattServerCallback.onConnectionStateChange(device, GattStatus.SUCCESS.value, BluetoothProfile.STATE_CONNECTED);
        when(bluetoothManager.getConnectedDevices(BluetoothGattServer.GATT)).thenReturn(Collections.singletonList(device));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        BluetoothCentral central = peripheralManager.getCentral(device.getAddress());

        // When
        peripheralManager.cancelConnection(central);

        // Then
        verify(server).cancelConnection(device);

        // When
        peripheralManager.bluetoothGattServerCallback.onConnectionStateChange(device, 0, GattStatus.SUCCESS.value);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralManagerCallback).onCentralDisconnected(central);
    }
}
