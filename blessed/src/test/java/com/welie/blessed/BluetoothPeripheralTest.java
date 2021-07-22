package com.welie.blessed;

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
import android.os.Handler;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_HIGH;
import static android.bluetooth.BluetoothGatt.GATT_FAILURE;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.O_MR1;
import static com.welie.blessed.ConnectionState.CONNECTED;
import static com.welie.blessed.ConnectionState.CONNECTING;
import static com.welie.blessed.ConnectionState.DISCONNECTED;
import static com.welie.blessed.ConnectionState.DISCONNECTING;
import static junit.framework.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

@RunWith(RobolectricTestRunner.class)
@Config( manifest=Config.NONE, sdk = { M })
public class BluetoothPeripheralTest {
    private BluetoothPeripheral peripheral;
    private final Handler handler = new Handler();
    public static final UUID SERVICE_UUID = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb");

    @Mock
    private BluetoothPeripheral.InternalCallback internalCallback;

    @Mock
    private BluetoothPeripheralCallback peripheralCallback;

    @Mock
    private Context context;

    @Mock
    private BluetoothDevice device;

    @Mock
    private BluetoothGatt gatt;

    @Captor
    ArgumentCaptor<BluetoothPeripheral> captorPeripheral;

    @Captor
    ArgumentCaptor<byte[]> captorValue;

    @Captor
    ArgumentCaptor<BluetoothGattCharacteristic> captorCharacteristic;

    @Captor
    ArgumentCaptor<BluetoothGattDescriptor> captorDescriptor;

    @Captor
    ArgumentCaptor<GattStatus> captorGattStatus;

    private final Transport transport = Transport.LE;

    @Before
    public void setUp() {
        openMocks(this);

        when(device.getAddress()).thenReturn("12:23:34:98:76:54");
        when(device.connectGatt(any(Context.class), anyBoolean(), any(BluetoothGattCallback.class), anyInt())).thenReturn(gatt);
        when(gatt.getDevice()).thenReturn(device);

        peripheral = new BluetoothPeripheral(context, device, internalCallback, peripheralCallback, handler, transport);
    }

    @Test
    public void Given_a_not_connected_peripheral_when_connect_is_called_and_succeeds_then_the_state_is_connected() {
        // When
        peripheral.connect();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals(CONNECTING,  peripheral.getState());

        ArgumentCaptor<BluetoothGattCallback> captor = ArgumentCaptor.forClass(BluetoothGattCallback.class);
        verify(device).connectGatt(any(Context.class), anyBoolean(), captor.capture(), eq(transport.value));
        BluetoothGattCallback callback = captor.getValue();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        // Then
        assertEquals(CONNECTED,  peripheral.getState());
    }

    @Test
    public void Given_a_not_connected_peripheral_when_autoConnect_is_called_and_succeeds_then_the_state_is_connected() {
        // When
        peripheral.autoConnect();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals(CONNECTING,  peripheral.getState());

        ArgumentCaptor<BluetoothGattCallback> captor = ArgumentCaptor.forClass(BluetoothGattCallback.class);
        ArgumentCaptor<Boolean> autoConnectCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(device).connectGatt(any(Context.class), autoConnectCaptor.capture(), captor.capture(), anyInt());
        assertTrue(autoConnectCaptor.getValue());
        BluetoothGattCallback callback = captor.getValue();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        // Then
        assertEquals(CONNECTED,  peripheral.getState());
    }

    @Test
    public void Given_a_connected_peripheral_when_cancelConnection_is_called_then_disconnect_is_called_and_state_is_disconnected_when_successful() {
        // Given
        peripheral.connect();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        ArgumentCaptor<BluetoothGattCallback> captor = ArgumentCaptor.forClass(BluetoothGattCallback.class);
        verify(device).connectGatt(any(Context.class), anyBoolean(), captor.capture(), anyInt());
        BluetoothGattCallback callback = captor.getValue();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // When
        peripheral.cancelConnection();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).disconnect();
        assertEquals(DISCONNECTING,  peripheral.getState());

        // When
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_DISCONNECTED);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).close();
        assertEquals(DISCONNECTED,  peripheral.getState());
    }

    @Test
    public void Given_an_autoConnect_pending_when_cancelConnection_is_called_then_a_disconnect_is_done() {
        // Given
        peripheral.autoConnect();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // When
        peripheral.cancelConnection();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).disconnect();
        verify(gatt).close();
        assertEquals(DISCONNECTED,  peripheral.getState());
    }

    @Test
    public void When_calling_cancelConnection_on_an_unconnected_peripheral_then_disconnect_is_not_called()  {
        // When
        peripheral.cancelConnection();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt, never()).disconnect();
    }

    @Test
    public void When_calling_getAddress_then_the_peripherals_address_is_returned() {
        assertEquals("12:23:34:98:76:54", peripheral.getAddress());
    }

    @Test
    public void Given_a_connected_peripheral_when_the_name_is_null_then_an_empty_string_is_returned() {
        // Given
        when(device.getName()).thenReturn(null);
        connectAndGetCallback();

        // When
        String name = peripheral.getName();

        // Then
        assertNotNull(name);
        assertEquals("", name);
    }

    @Test
    public void Given_a_connected_peripheral_when_the_name_becomes_null_after_having_a_name_then_first_name_is_returned() {
        // Given
        when(device.getName()).thenReturn("first");
        connectAndGetCallback();

        // When
        String name = peripheral.getName();

        // Then
        assertNotNull(name);
        assertEquals("first", name);

        // When
        when(device.getName()).thenReturn(null);

        // Then
        assertEquals("first", peripheral.getName());
    }

    @Test
    public void Given_a_connected_peripheral_and_services_discovered_when_getService_is_called_the_right_service_is_returned() {
        // Given
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        when(gatt.getService(SERVICE_UUID)).thenReturn(service);
        connectAndGetCallback();

        // When
        BluetoothGattService receivedService = peripheral.getService(SERVICE_UUID);

        // Then
        assertNotNull(receivedService);
        assertEquals(service,receivedService);
    }

    @Test
    public void Given_a_connected_peripheral_and_services_discovered_when_getService_for_unknown_service_is_called_then_null_is_returned() {
        // Given
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        when(gatt.getService(SERVICE_UUID)).thenReturn(service);
        connectAndGetCallback();

        // When
        BluetoothGattService receivedService = peripheral.getService(UUID.fromString("00001000-0000-1000-8000-00805f9b34fb"));

        // Then
        assertNull(receivedService);
    }

    @Test
    public void Given_a_connected_peripheral_and_services_discovered_when_getCharacteristic_is_called_the_right_characteristic_is_returned() {
        // Given
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_INDICATE,0);
        service.addCharacteristic(characteristic);
        when(gatt.getService(SERVICE_UUID)).thenReturn(service);
        connectAndGetCallback();

        // When
        BluetoothGattCharacteristic receivedCharacteristic = peripheral.getCharacteristic(SERVICE_UUID, UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"));

        // Then
        assertNotNull(receivedCharacteristic);
        assertEquals(characteristic,receivedCharacteristic);
    }

    @Test
    public void Given_a_connected_peripheral_and_services_discovered_when_getCharacteristic_with_unknownUUID_is_called_then_null_is_returned() {
        // Given
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_INDICATE,0);
        service.addCharacteristic(characteristic);
        when(gatt.getService(SERVICE_UUID)).thenReturn(service);
        connectAndGetCallback();

        // When
        BluetoothGattCharacteristic receivedCharacteristic = peripheral.getCharacteristic(SERVICE_UUID, UUID.fromString("00002AAA-0000-1000-8000-00805f9b34fb"));

        // Then
        assertNull(receivedCharacteristic);
    }

    @Test
    public void Given_a_connected_peripheral_with_a_characteristic_supporting_indications_when_setNotify_with_true_is_called_then_the_indication_is_enabled() {
        // Given
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_INDICATE,0);
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),0);
        service.addCharacteristic(characteristic);
        characteristic.addDescriptor(descriptor);

        when(gatt.getServices()).thenReturn(Collections.singletonList(service));
        when(gatt.setCharacteristicNotification(characteristic, true)).thenReturn(true);
        BluetoothGattCallback callback = connectAndGetCallback();

        // When
        peripheral.setNotify(characteristic, true);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).setCharacteristicNotification(characteristic, true);
        assertEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE[0], descriptor.getValue()[0]);
        assertEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE[1], descriptor.getValue()[1]);
        verify(gatt).writeDescriptor(descriptor);

        callback.onDescriptorWrite(gatt, descriptor, 0);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(peripheralCallback).onNotificationStateUpdate(peripheral, characteristic, GattStatus.SUCCESS);
        assertTrue(peripheral.isNotifying(characteristic));
        assertEquals(1, peripheral.getNotifyingCharacteristics().size());
    }

    @Test
    public void Given_a_connected_peripheral_with_a_characteristic_supporting_indications_when_setNotifyUsingUUID_with_true_is_called_then_the_indication_is_enabled() {
        // Given
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_INDICATE,0);
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),0);
        service.addCharacteristic(characteristic);
        characteristic.addDescriptor(descriptor);

        when(gatt.getService(SERVICE_UUID)).thenReturn(service);
        when(gatt.getServices()).thenReturn(Collections.singletonList(service));
        when(gatt.setCharacteristicNotification(characteristic, true)).thenReturn(true);
        BluetoothGattCallback callback = connectAndGetCallback();

        // When
        peripheral.setNotify(SERVICE_UUID,UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb") , true);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).setCharacteristicNotification(characteristic, true);
        assertEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE[0], descriptor.getValue()[0]);
        assertEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE[1], descriptor.getValue()[1]);
        verify(gatt).writeDescriptor(descriptor);

        callback.onDescriptorWrite(gatt, descriptor, 0);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(peripheralCallback).onNotificationStateUpdate(peripheral, characteristic, GattStatus.SUCCESS);
        assertTrue(peripheral.isNotifying(characteristic));
        assertEquals(1, peripheral.getNotifyingCharacteristics().size());
    }

    @Test
    public void Given_a_connected_peripheral_with_a_characteristic_supporting_notifications_when_setNotify_with_true_is_called_then_the_notification_is_enabled() {
        // Given
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_NOTIFY,0);
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),0);
        service.addCharacteristic(characteristic);
        characteristic.addDescriptor(descriptor);

        when(gatt.getServices()).thenReturn(Collections.singletonList(service));
        when(gatt.setCharacteristicNotification(characteristic, true)).thenReturn(true);
        BluetoothGattCallback callback = connectAndGetCallback();

        // When
        peripheral.setNotify(characteristic, true);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt, timeout(1000)).setCharacteristicNotification(characteristic, true);
        assertEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE[0], descriptor.getValue()[0]);
        assertEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE[1], descriptor.getValue()[1]);
        verify(gatt, timeout(1000)).writeDescriptor(descriptor);

        callback.onDescriptorWrite(gatt, descriptor, 0);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(peripheralCallback).onNotificationStateUpdate(peripheral, characteristic, GattStatus.SUCCESS);
        assertTrue(peripheral.isNotifying(characteristic));
        assertEquals(1, peripheral.getNotifyingCharacteristics().size());
    }

    @Test
    public void Given_a_connected_peripheral_with_a_characteristic_supporting_notifications_when_setNotify_with_false_is_called_then_the_notification_is_disabled()  {
        // Given
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_NOTIFY,0);
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),0);
        service.addCharacteristic(characteristic);
        characteristic.addDescriptor(descriptor);

        when(gatt.getServices()).thenReturn(Collections.singletonList(service));
        when(gatt.setCharacteristicNotification(characteristic, false)).thenReturn(true);
        BluetoothGattCallback callback = connectAndGetCallback();

        // When
        peripheral.setNotify(characteristic, false);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).setCharacteristicNotification(characteristic, false);
        assertEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE[0], descriptor.getValue()[0]);
        assertEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE[1], descriptor.getValue()[1]);
        verify(gatt).writeDescriptor(descriptor);

        callback.onDescriptorWrite(gatt, descriptor, 0);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(peripheralCallback).onNotificationStateUpdate(peripheral, characteristic, GattStatus.SUCCESS);
        assertFalse(peripheral.isNotifying(characteristic));
        assertEquals(0, peripheral.getNotifyingCharacteristics().size());
    }

    @Test
    public void Given_a_connected_peripheral_when_readCharacteristic_is_called_then_the_received_value_is_returned()  {
        // Given
        BluetoothGattCallback callback = connectAndGetCallback();

        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), PROPERTY_READ,0);
        characteristic.setValue(new byte[]{0x00});
        when(gatt.readCharacteristic(characteristic)).thenReturn(true);

        // When
        peripheral.readCharacteristic(characteristic);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).readCharacteristic(characteristic);

        byte[] value = new byte[]{0x01};
        characteristic.setValue(value);
        callback.onCharacteristicRead(gatt, characteristic, 0);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(peripheralCallback).onCharacteristicUpdate(captorPeripheral.capture(), captorValue.capture(), captorCharacteristic.capture(), captorGattStatus.capture());

        byte[] receivedValue = captorValue.getValue();
        assertEquals(0x01, receivedValue[0]);
        assertEquals(peripheral, captorPeripheral.getValue());
        assertEquals(characteristic, captorCharacteristic.getValue());
        assertEquals(GattStatus.SUCCESS, captorGattStatus.getValue() );
    }

    @Test
    public void Given_a_connected_peripheral_when_readCharacteristic_using_UUID_is_called_then_the_received_value_is_returned()  {
        // Given
        BluetoothGattCallback callback = connectAndGetCallback();

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), PROPERTY_READ,0);
        service.addCharacteristic(characteristic);
        characteristic.setValue(new byte[]{0x00});
        when(gatt.getService(SERVICE_UUID)).thenReturn(service);
        when(gatt.readCharacteristic(characteristic)).thenReturn(true);

        // When
        peripheral.readCharacteristic(SERVICE_UUID, UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).readCharacteristic(characteristic);

        byte[] value = new byte[]{0x01};
        characteristic.setValue(value);
        callback.onCharacteristicRead(gatt, characteristic, 0);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(peripheralCallback).onCharacteristicUpdate(captorPeripheral.capture(), captorValue.capture(), captorCharacteristic.capture(), captorGattStatus.capture());

        byte[] receivedValue = captorValue.getValue();
        assertEquals(0x01, receivedValue[0]);
        assertEquals(peripheral, captorPeripheral.getValue());
        assertEquals(characteristic, captorCharacteristic.getValue());
        assertEquals(GattStatus.SUCCESS, captorGattStatus.getValue() );
    }

    @Test
    public void Given_an_unconnected_peripheral_when_readCharacteristic_is_called_then_the_characteristic_is_not_read() {
        // Given
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), PROPERTY_READ,0);

        // When
        peripheral.readCharacteristic(characteristic);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt, never()).readCharacteristic(characteristic);
    }

    @Test (expected = IllegalArgumentException.class)
    public void Given_a_connected_peripheral_without_a_readable_characteristic_when_readCharacteristic_is_called_then_the_characteristic_is_not_read() {
        // Given
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), 0,0);
        connectAndGetCallback();

        // When
        peripheral.readCharacteristic(characteristic);
    }

    @Test
    public void Given_a_connected_peripheral_when_writeCharacteristic_WithResponse_is_called_then_the_value_is_written_and_a_correct_response_is_received() {
        // Given
        BluetoothGattCallback callback = connectAndGetCallback();

        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), PROPERTY_WRITE,0);
        when(gatt.writeCharacteristic(characteristic)).thenReturn(true);

        // When
        peripheral.writeCharacteristic(characteristic, new byte[]{5}, WriteType.WITH_RESPONSE);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).writeCharacteristic(characteristic);
        assertEquals(5, characteristic.getValue()[0]);
        assertEquals(WRITE_TYPE_DEFAULT, characteristic.getWriteType());

        // When
        byte[] valueAfterWrite = new byte[]{0x01};
        characteristic.setValue(valueAfterWrite);
        callback.onCharacteristicWrite(gatt, characteristic, 0);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralCallback).onCharacteristicWrite(captorPeripheral.capture(), captorValue.capture(), captorCharacteristic.capture(), captorGattStatus.capture());

        byte[] value = captorValue.getValue();
        assertEquals(5, value[0]);  // Check if original value is returned and not the one in the characteristic
        assertEquals(peripheral, captorPeripheral.getValue());
        assertEquals(characteristic, captorCharacteristic.getValue());
        assertEquals(GattStatus.SUCCESS, captorGattStatus.getValue() );
    }

    @Test
    public void Given_a_connected_peripheral_when_writeCharacteristic_WithResponse_usingUUID_is_called_then_the_value_is_written_and_a_correct_response_is_received() {
        // Given
        BluetoothGattCallback callback = connectAndGetCallback();

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), PROPERTY_WRITE,0);
        service.addCharacteristic(characteristic);
        when(gatt.writeCharacteristic(characteristic)).thenReturn(true);
        when(gatt.getService(SERVICE_UUID)).thenReturn(service);

        // When
        peripheral.writeCharacteristic(SERVICE_UUID, UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), new byte[]{5}, WriteType.WITH_RESPONSE);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).writeCharacteristic(characteristic);
        assertEquals(5, characteristic.getValue()[0]);
        assertEquals(WRITE_TYPE_DEFAULT, characteristic.getWriteType());

        // When
        byte[] valueAfterWrite = new byte[]{0x01};
        characteristic.setValue(valueAfterWrite);
        callback.onCharacteristicWrite(gatt, characteristic, 0);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralCallback).onCharacteristicWrite(captorPeripheral.capture(), captorValue.capture(), captorCharacteristic.capture(), captorGattStatus.capture());

        byte[] value = captorValue.getValue();
        assertEquals(5, value[0]);  // Check if original value is returned and not the one in the characteristic
        assertEquals(peripheral, captorPeripheral.getValue());
        assertEquals(characteristic, captorCharacteristic.getValue());
        assertEquals(GattStatus.SUCCESS, captorGattStatus.getValue());
    }

    @Test
    public void Given_a_connected_peripheral_when_writeCharacteristic_WithoutResponse_is_called_then_the_value_is_written_and_a_correct_response_is_received() {
        // Given
        BluetoothGattCallback callback = connectAndGetCallback();

        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), PROPERTY_WRITE_NO_RESPONSE,0);
        when(gatt.writeCharacteristic(characteristic)).thenReturn(true);

        // When
        peripheral.writeCharacteristic(characteristic, new byte[]{0}, WriteType.WITHOUT_RESPONSE);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).writeCharacteristic(characteristic);
        assertEquals(0, characteristic.getValue()[0]);
        assertEquals(WRITE_TYPE_NO_RESPONSE, characteristic.getWriteType());

        // When
        byte[] originalByteArray = new byte[]{0x01};
        characteristic.setValue(originalByteArray);
        callback.onCharacteristicWrite(gatt, characteristic, GATT_SUCCESS);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralCallback).onCharacteristicWrite(captorPeripheral.capture(), captorValue.capture(), captorCharacteristic.capture(), captorGattStatus.capture());

        byte[] value = captorValue.getValue();
        assertEquals(0, value[0]);  // Check if original value is returned and not the one in the characteristic
        assertEquals(peripheral, captorPeripheral.getValue());
        assertEquals(characteristic, captorCharacteristic.getValue());
        assertEquals(GattStatus.SUCCESS, captorGattStatus.getValue() );
    }


    @Test
    public void Given_an_unconnected_peripheral_when_writeCharacteristic_is_called_then_the_characteristic_is_not_written() {
        // Given
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), PROPERTY_WRITE,0);

        // When
        peripheral.writeCharacteristic(characteristic, new byte[]{0}, WriteType.WITH_RESPONSE);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt, never()).writeCharacteristic(characteristic);
    }

    @Test (expected = IllegalArgumentException.class)
    public void Given_a_connected_peripheral_with_not_writable_characteristic_when_writeCharacteristic_is_called_then_the_value_is_not_written() {
        // Given
        connectAndGetCallback();
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), 0,0);

        // When
        peripheral.writeCharacteristic(characteristic, new byte[] { 0, 0 }, WriteType.WITH_RESPONSE);
    }

    @Test (expected = IllegalArgumentException.class)
    public void Given_a_connected_peripheral_when_writeCharacteristic_is_called_with_an_empty_value_then_an_exception_is_thrown() {
        // Given
        connectAndGetCallback();
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), PROPERTY_WRITE,0);

        // When
        peripheral.writeCharacteristic(characteristic, new byte[0], WriteType.WITH_RESPONSE);
    }

    @Test
    public void Given_a_connected_peripheral_when_readCharacteristic_is_called_twice_then_they_are_done_sequentially() {
        // Given
        BluetoothGattCallback callback = connectAndGetCallback();

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_READ,0);
        BluetoothGattCharacteristic characteristic2 = new BluetoothGattCharacteristic(UUID.fromString("00002A1E-0000-1000-8000-00805f9b34fb"),PROPERTY_READ,0);
        service.addCharacteristic(characteristic);
        service.addCharacteristic(characteristic2);
        byte[] byteArray = new byte[] {0x01, 0x02, 0x03};
        characteristic.setValue(byteArray);
        characteristic2.setValue(byteArray);
        when(gatt.readCharacteristic(characteristic)).thenReturn(true);

        // When
        peripheral.readCharacteristic(characteristic);
        peripheral.readCharacteristic(characteristic2);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt, times(1)).readCharacteristic(characteristic);

        // Confirm read
        callback.onCharacteristicRead(gatt, characteristic, GATT_SUCCESS);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralCallback).onCharacteristicUpdate(peripheral, byteArray, characteristic, GattStatus.SUCCESS);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        verify(gatt).readCharacteristic(characteristic2);

        // Confirm read
        callback.onCharacteristicRead(gatt, characteristic2, GATT_SUCCESS);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralCallback).onCharacteristicUpdate(peripheral, byteArray, characteristic2, GattStatus.SUCCESS);
    }

    @Test
    public void Given_a_connected_peripheral_when_readCharacteristic_is_called_twice_then_they_are_done_sequentially_even_when_errors_occur() {
        // Given
        BluetoothGattCallback callback = connectAndGetCallback();

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_READ,0);
        BluetoothGattCharacteristic characteristic2 = new BluetoothGattCharacteristic(UUID.fromString("00002A1E-0000-1000-8000-00805f9b34fb"),PROPERTY_READ,0);
        service.addCharacteristic(characteristic);
        service.addCharacteristic(characteristic2);
        byte[] byteArray = new byte[] {0x01, 0x02, 0x03};
        characteristic.setValue(byteArray);
        characteristic2.setValue(byteArray);
        when(gatt.readCharacteristic(characteristic)).thenReturn(true);

        // When
        peripheral.readCharacteristic(characteristic);
        peripheral.readCharacteristic(characteristic2);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt, times(1)).readCharacteristic(characteristic);

        // Confirm read
        callback.onCharacteristicRead(gatt, characteristic, 128);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralCallback).onCharacteristicUpdate(peripheral, byteArray, characteristic,GattStatus.NO_RESOURCES);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        verify(gatt, times(1)).readCharacteristic(characteristic2);

        // Confirm read
        callback.onCharacteristicRead(gatt, characteristic2, GATT_SUCCESS);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralCallback).onCharacteristicUpdate(peripheral, byteArray ,characteristic2, GattStatus.SUCCESS);
    }

    @Test
    public void Given_a_connected_peripheral_when_two_reads_are_done_but_no_reply_is_received_then_the_second_read_is_not_done()  {
        // Given
        connectAndGetCallback();

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_READ,0);
        BluetoothGattCharacteristic characteristic2 = new BluetoothGattCharacteristic(UUID.fromString("00002A1E-0000-1000-8000-00805f9b34fb"),PROPERTY_READ,0);
        service.addCharacteristic(characteristic);
        service.addCharacteristic(characteristic2);
        byte[] byteArray = new byte[] {0x01, 0x02, 0x03};
        characteristic.setValue(byteArray);
        characteristic2.setValue(byteArray);

        when(gatt.readCharacteristic(characteristic)).thenReturn(true);

        // When
        peripheral.readCharacteristic(characteristic);
        peripheral.readCharacteristic(characteristic2);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).readCharacteristic(characteristic);
        verify(peripheralCallback, never()).onCharacteristicUpdate(peripheral, byteArray, characteristic, GattStatus.SUCCESS);
        verify(gatt, never()).readCharacteristic(characteristic2);
    }

    @Test
    public void Given_a_connected_peripheral_with_notifications_on_when_a_notification_is_received_onCharacteristicUpdate_is_called() {
        // Given
        BluetoothGattCallback callback = connectAndGetCallback();
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), PROPERTY_INDICATE,0);
        BluetoothGattDescriptor cccd = new BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),0);
        characteristic.addDescriptor(cccd);
        peripheral.setNotify(characteristic, true);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // When
        byte[] originalByteArray = new byte[]{0x01};
        characteristic.setValue(originalByteArray);
        callback.onCharacteristicChanged(gatt, characteristic);
        characteristic.setValue(new byte[]{0x00});
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(peripheralCallback).onCharacteristicUpdate(captorPeripheral.capture(), captorValue.capture(), captorCharacteristic.capture(), captorGattStatus.capture());

        byte[] value = captorValue.getValue();
        assertEquals(0x01, value[0]);
        assertEquals(peripheral, captorPeripheral.getValue());
        assertEquals(characteristic, captorCharacteristic.getValue());
        assertEquals(GattStatus.SUCCESS, captorGattStatus.getValue());
    }

    @Test
    public void Given_a_connected_peripheral_when_a_readDescriptor_is_done_then_the_descriptor_is_read_and_the_onDescriptorRead_is_called_with_the_result() {
        // Given
        BluetoothGattCallback callback = connectAndGetCallback();

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_INDICATE,0);
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(UUID.fromString("00002903-0000-1000-8000-00805f9b34fb"),0);
        service.addCharacteristic(characteristic);
        characteristic.addDescriptor(descriptor);

        when(gatt.getServices()).thenReturn(Collections.singletonList(service));

        // When
        peripheral.readDescriptor(descriptor);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).readDescriptor(descriptor);

        // When
        byte[] originalByteArray = new byte[]{0x01};
        descriptor.setValue(originalByteArray);
        callback.onDescriptorRead(gatt, descriptor, GATT_SUCCESS);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralCallback).onDescriptorRead(captorPeripheral.capture(), captorValue.capture(), captorDescriptor.capture(), captorGattStatus.capture());

        byte[] value = captorValue.getValue();
        assertEquals(0x01, value[0]);
        assertEquals(peripheral, captorPeripheral.getValue());
        assertEquals(descriptor, captorDescriptor.getValue());
        assertEquals(GattStatus.SUCCESS, captorGattStatus.getValue());
    }


    @Test
    public void Given_a_connected_peripheral_when_writeDescriptor_is_called_then_the_descriptor_is_written_and_the_response_is_received() {
        // Given
        BluetoothGattCallback callback = connectAndGetCallback();

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_INDICATE,0);
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(UUID.fromString("00002903-0000-1000-8000-00805f9b34fb"),0);
        service.addCharacteristic(characteristic);
        characteristic.addDescriptor(descriptor);
        when(gatt.getServices()).thenReturn(Collections.singletonList(service));

        // When
        byte[] originalByteArray = new byte[]{0x01};
        peripheral.writeDescriptor(descriptor, originalByteArray);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).writeDescriptor(descriptor);

        // When
        callback.onDescriptorWrite(gatt, descriptor, 0);
        descriptor.setValue(new byte[]{0x00});
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralCallback).onDescriptorWrite(captorPeripheral.capture(), captorValue.capture(), captorDescriptor.capture(), captorGattStatus.capture());

        byte[] value = captorValue.getValue();
        assertEquals(0x01, value[0]);
        assertNotEquals(value, originalByteArray);
        assertEquals(peripheral, captorPeripheral.getValue());
        assertEquals(descriptor, captorDescriptor.getValue());
        assertEquals(GattStatus.SUCCESS, captorGattStatus.getValue());
    }

    @Test
    public void Given_a_connected_peripheral_when_readRemoteRssi_is_called_then_the_rssi_is_read_and_the_result_is_received() {
        // Given
        BluetoothGattCallback callback = connectAndGetCallback();

        // When
        peripheral.readRemoteRssi();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).readRemoteRssi();

        // When
        callback.onReadRemoteRssi(gatt, -40, GATT_SUCCESS);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(peripheralCallback).onReadRemoteRssi(peripheral, -40, GattStatus.SUCCESS);
    }

    @Test
    public void Given_a_connected_peripheral_when_requestMTU_is_called_then_the_MTU_is_requested_and_the_result_is_received() {
        // Given
        BluetoothGattCallback callback = connectAndGetCallback();

        // When
        peripheral.requestMtu(32);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).requestMtu(32);

        // When
        callback.onMtuChanged(gatt, 32, GATT_SUCCESS);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralCallback).onMtuChanged(peripheral, 32, GattStatus.SUCCESS);
    }

    @Test
    public void Given_a_connected_peripheral_when_requestConnectionPriority_is_called_then_a_connection_priority_is_requested() {
        // Given
        connectAndGetCallback();

        // When
        peripheral.requestConnectionPriority(ConnectionPriority.HIGH);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).requestConnectionPriority(CONNECTION_PRIORITY_HIGH);
    }

    @Test
    public void Given_a_connected_peripheral_when_createBond_is_called_then_the_bond_is_created() {
        // Given
        connectAndGetCallback();

        // When
        peripheral.createBond();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(device).createBond();
    }

    @Test
    public void Given_an_unconnected_peripheral_when_createBond_is_called_then_the_bond_is_created() {
        peripheral.createBond();

        verify(device).createBond();
    }

    @Test
    public void Given_a_connected_peripheral_when_a_read_triggers_a_bond_the_read_is_retried() throws Exception {
        // Given
        BluetoothGattCallback callback = connectAndGetCallback();

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_READ,0);
        BluetoothGattCharacteristic characteristic2 = new BluetoothGattCharacteristic(UUID.fromString("00002A1E-0000-1000-8000-00805f9b34fb"),PROPERTY_READ,0);
        service.addCharacteristic(characteristic);
        service.addCharacteristic(characteristic2);
        when(gatt.readCharacteristic(characteristic)).thenReturn(true);
        when(gatt.getServices()).thenReturn(Collections.singletonList(service));

        // When
        peripheral.readCharacteristic(characteristic);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).readCharacteristic(characteristic);

        // Trigger bonding to start
        callback.onCharacteristicRead(gatt, characteristic, 5);

        Field field = BluetoothPeripheral.class.getDeclaredField("bondStateReceiver");
        field.setAccessible(true);
        BroadcastReceiver broadcastReceiver = (BroadcastReceiver) field.get(peripheral);

        Intent intent = mock(Intent.class);
        when(intent.getAction()).thenReturn(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        when(intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)).thenReturn(BOND_BONDED);
        when(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)).thenReturn(device);

        broadcastReceiver.onReceive(context, intent);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        verify(gatt, times(2)).readCharacteristic(characteristic);
    }

    @Test
    public void When_a_peripheral_connects_then_the_services_are_discovered() {
        // Given
        when(device.getBondState()).thenReturn(BOND_NONE);

        // When
        connectAndGetCallback();

        // Then
        verify(gatt).discoverServices();
    }

    @Test
    public void Given_a_connected_peripheral_when_the_connected_drops_unexpected_then_the_gatt_is_closed() {
        // Given
        BluetoothGattCallback callback = connectAndGetCallback();

        // When
        callback.onConnectionStateChange(gatt, GATT_FAILURE, STATE_DISCONNECTED);

        // Then
        verify(gatt).close();
    }

    @Test
    public void Given_a_unconnected_bonded_peripheral_when_it_connects_services_are_discovered()  {
        // Given
        when(device.getBondState()).thenReturn(BOND_BONDED);

        // When
        connectAndGetCallback();

        // Then
        verify(gatt).discoverServices();
    }

    @Test
    public void Given_a_unconnected_peripheral_when_it_connects_and_bonding_is_in_progress_then_discoverServices_is_not_called() {
        // When
        when(device.getBondState()).thenReturn(BOND_BONDING);
        connectAndGetCallback();

        // Then
        verify(gatt, never()).discoverServices();
    }

    @Test
    public void Given_a_connected_peripheral_when_a_disconnect_happens_then_gatt_is_closed_and_the_disconnected_event_is_sent() {
        // Given
        BluetoothGattCallback callback = connectAndGetCallback();

        // When
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED);

        // Then
        verify(gatt).close();
        verify(internalCallback).disconnected(peripheral, HciStatus.SUCCESS);
    }

    @Test
    public void Given_a_connected_peripheral_when_onServicesDiscovered_is_called_then_the_list_of_services_is_returned() {
        // Given
        BluetoothGattCallback callback = connectAndGetCallback();

        BluetoothGattService service = mock(BluetoothGattService.class);
        when(service.getUuid()).thenReturn(SERVICE_UUID);
        when(gatt.getServices()).thenReturn(Collections.singletonList(service));

        BluetoothGattCharacteristic characteristic = mock(BluetoothGattCharacteristic.class);
        when(characteristic.getProperties()).thenReturn(PROPERTY_READ);
        when(service.getCharacteristics()).thenReturn(Collections.singletonList(characteristic));

        // When
        callback.onServicesDiscovered(gatt, 0);

        // Then
        List<BluetoothGattService> expected = Collections.singletonList(service);
        assertEquals(expected, peripheral.getServices());
        assertEquals(1, peripheral.getServices().get(0).getCharacteristics().size());
    }

    @Test
    public void Given_a_connected_peripheral_when_service_discovery_fails_then_a_disconnect_is_issued() {
        // Given
        BluetoothGattCallback callback = connectAndGetCallback();

        // When
        callback.onServicesDiscovered(gatt, 129);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).disconnect();
    }

    @Test
    public void Given_a_connected_peripheral_when_bonding_succeeds_and_services_were_not_discovered_then_service_discovery_is_started() throws Exception {
        // Given
        connectAndGetCallback();

        Field field = BluetoothPeripheral.class.getDeclaredField("bondStateReceiver");
        field.setAccessible(true);
        BroadcastReceiver broadcastReceiver = (BroadcastReceiver) field.get(peripheral);

        Intent intent = mock(Intent.class);
        when(intent.getAction()).thenReturn(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        when(intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)).thenReturn(BOND_BONDED);
        when(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)).thenReturn(device);

        // When
        broadcastReceiver.onReceive(context, intent);

        // Then
        verify(gatt).discoverServices();
    }

    @Test
    public void Given_a_bonded_connected_peripheral_when_bond_is_lost_then_a_disconnect_is_issued() throws Exception {
        // Given
        when(device.getBondState()).thenReturn(BOND_BONDED);
        connectAndGetCallback();

        Field field = BluetoothPeripheral.class.getDeclaredField("bondStateReceiver");
        field.setAccessible(true);
        BroadcastReceiver broadcastReceiver = (BroadcastReceiver) field.get(peripheral);

        Intent intent = mock(Intent.class);
        when(intent.getAction()).thenReturn(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        when(intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)).thenReturn(BluetoothDevice.BOND_NONE);
        when(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)).thenReturn(device);

        // When
        broadcastReceiver.onReceive(context, intent);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).disconnect();
    }

    @Test
    @Config( sdk = { O_MR1 })
    public void Given_a_connected_peripheral_when_requestPhy_is_called_the_Phy_is_requested() {
        // Given
        BluetoothGattCallback callback = connectAndGetCallback();

        // When
        peripheral.setPreferredPhy(PhyType.LE_2M, PhyType.LE_2M, PhyOptions.NO_PREFERRED);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).setPreferredPhy(PhyType.LE_2M.value, PhyType.LE_2M.value, PhyOptions.NO_PREFERRED.value);

        // When
        callback.onPhyUpdate(gatt, PhyType.LE_2M.value, PhyType.LE_2M.value, GATT_SUCCESS);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralCallback).onPhyUpdate(peripheral,PhyType.LE_2M, PhyType.LE_2M, GattStatus.SUCCESS );
    }

    @Test
    @Config( sdk = { O_MR1 })
    public void Given_a_connected_peripheral_when_readPhy_is_called_the_Phy_is_read() {
        // Given
        BluetoothGattCallback callback = connectAndGetCallback();

        // When
        peripheral.readPhy();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(gatt).readPhy();

        // When
        callback.onPhyRead(gatt, PhyType.LE_2M.value, PhyType.LE_2M.value, GATT_SUCCESS);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Then
        verify(peripheralCallback).onPhyUpdate(peripheral,PhyType.LE_2M, PhyType.LE_2M, GattStatus.SUCCESS );
    }

    @Test
    public void testPeripheralCallbackEmptyNoCrash() {
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_NOTIFY,0);
        peripheral.peripheralCallback.onCharacteristicUpdate(peripheral, new byte[]{0x00}, characteristic, GattStatus.ATTRIBUTE_NOT_FOUND);
    }

    private BluetoothGattCallback connectAndGetCallback() {
        peripheral.connect();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        ArgumentCaptor<BluetoothGattCallback> bluetoothGattCallbackCaptor = ArgumentCaptor.forClass(BluetoothGattCallback.class);
        verify(device).connectGatt(any(Context.class), anyBoolean(), bluetoothGattCallbackCaptor.capture(), anyInt());

        BluetoothGattCallback callback = bluetoothGattCallbackCaptor.getValue();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        return callback;
    }
}