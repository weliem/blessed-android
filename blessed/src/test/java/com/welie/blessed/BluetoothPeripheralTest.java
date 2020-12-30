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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.util.Arrays;
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
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTING;
import static android.os.Build.VERSION_CODES.M;
import static junit.framework.Assert.assertFalse;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = { M })
public class BluetoothPeripheralTest {
    private BluetoothPeripheral peripheral;
    private Handler handler;
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

    @Before
    public void setUp() throws Exception {
        initMocks(this);

        when(device.getAddress()).thenReturn("12:23:34:98:76:54");
        when(device.connectGatt(any(Context.class), anyBoolean(), any(BluetoothGattCallback.class), anyInt())).thenReturn(gatt);
        when(gatt.getDevice()).thenReturn(device);


        peripheral = new BluetoothPeripheral(context, device, internalCallback, peripheralCallback, handler);
    }

    @Test
    public void connectTest() throws Exception {
        peripheral.connect();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        ArgumentCaptor<BluetoothGattCallback> captor = ArgumentCaptor.forClass(BluetoothGattCallback.class);

        verify(device).connectGatt(any(Context.class), anyBoolean(), captor.capture(), anyInt());

        BluetoothGattCallback callback = captor.getValue();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        assertEquals(STATE_CONNECTED,  peripheral.getState());
    }

    @Test
    public void autoconnectTest() throws Exception {
        peripheral.autoConnect();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        ArgumentCaptor<BluetoothGattCallback> captor = ArgumentCaptor.forClass(BluetoothGattCallback.class);
        ArgumentCaptor<Boolean> autoConnectCaptor = ArgumentCaptor.forClass(Boolean.class);

        verify(device).connectGatt(any(Context.class), autoConnectCaptor.capture(), captor.capture(), anyInt());

        boolean autoconnect = autoConnectCaptor.getValue();
        assertTrue(autoconnect);

        BluetoothGattCallback callback = captor.getValue();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        assertEquals(STATE_CONNECTED,  peripheral.getState());
    }

    @Test
    public void cancelConnectionTest() throws Exception {
        peripheral.connect();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        ArgumentCaptor<BluetoothGattCallback> captor = ArgumentCaptor.forClass(BluetoothGattCallback.class);

        verify(device).connectGatt(any(Context.class), anyBoolean(), captor.capture(), anyInt());

        BluetoothGattCallback callback = captor.getValue();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        peripheral.cancelConnection();

        verify(gatt).disconnect();

        assertEquals(STATE_DISCONNECTING,  peripheral.getState());

        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_DISCONNECTED);

        verify(gatt).close();

        assertEquals(STATE_DISCONNECTED,  peripheral.getState());
    }

    @Test
    public void cancelConnectionAutoConnectTest() throws Exception {
        peripheral.autoConnect();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        ArgumentCaptor<BluetoothGattCallback> captor = ArgumentCaptor.forClass(BluetoothGattCallback.class);

        verify(device).connectGatt(any(Context.class), anyBoolean(), captor.capture(), anyInt());

        peripheral.cancelConnection();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(gatt).disconnect();

        verify(gatt).close();

        assertEquals(STATE_DISCONNECTED,  peripheral.getState());
    }

    @Test
    public void disconnectNullTest() throws Exception {
        peripheral.cancelConnection();

        verify(gatt, never()).disconnect();
    }


    @Test
    public void getAddressTest() throws Exception {
        assertEquals("12:23:34:98:76:54", peripheral.getAddress());
    }


    @Test
    public void setNotifyIndicationTest() throws Exception {
        BluetoothGattCallback callback = connectAndGetCallback();

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_INDICATE,0);
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),0);
        service.addCharacteristic(characteristic);
        characteristic.addDescriptor(descriptor);

        when(gatt.getServices()).thenReturn(Arrays.asList(service));
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        peripheral.setNotify(characteristic, true);
        verify(gatt).setCharacteristicNotification(characteristic, true);
        assertEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE[0], descriptor.getValue()[0]);
        assertEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE[1], descriptor.getValue()[1]);
        verify(gatt).writeDescriptor(descriptor);

        callback.onDescriptorWrite(gatt, descriptor, 0);
        verify(peripheralCallback).onNotificationStateUpdate(peripheral, characteristic, GattStatus.SUCCESS);
    }

    @Test
    public void setNotifyNotificationTest() throws Exception {
        BluetoothGattCallback callback = connectAndGetCallback();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_NOTIFY,0);
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),0);
        service.addCharacteristic(characteristic);
        characteristic.addDescriptor(descriptor);

        when(gatt.getServices()).thenReturn(Arrays.asList(service));

        peripheral.setNotify(characteristic, true);
        verify(gatt).setCharacteristicNotification(characteristic, true);
        assertEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE[0], descriptor.getValue()[0]);
        assertEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE[1], descriptor.getValue()[1]);
        verify(gatt).writeDescriptor(descriptor);

        callback.onDescriptorWrite(gatt, descriptor, 0);
        verify(peripheralCallback).onNotificationStateUpdate(peripheral, characteristic, GattStatus.SUCCESS);
    }

    @Test
    public void setNotifyDisableTest() throws Exception {
        BluetoothGattCallback callback = connectAndGetCallback();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_NOTIFY,0);
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),0);
        service.addCharacteristic(characteristic);
        characteristic.addDescriptor(descriptor);

        when(gatt.getServices()).thenReturn(Arrays.asList(service));

        peripheral.setNotify(characteristic, false);
        verify(gatt).setCharacteristicNotification(characteristic, false);
        assertEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE[0], descriptor.getValue()[0]);
        assertEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE[1], descriptor.getValue()[1]);
        verify(gatt).writeDescriptor(descriptor);

        callback.onDescriptorWrite(gatt, descriptor, 0);

        verify(peripheralCallback).onNotificationStateUpdate(peripheral, characteristic, GattStatus.SUCCESS);
    }

    @Test
    public void readCharacteristicTest() throws Exception {
        BluetoothGattCallback callback = connectAndGetCallback();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), PROPERTY_READ,0);
        characteristic.setValue(new byte[]{0x00});
        when(gatt.readCharacteristic(characteristic)).thenReturn(true);

        peripheral.readCharacteristic(characteristic);

        verify(gatt).readCharacteristic(any(BluetoothGattCharacteristic.class));

        byte[] originalByteArray = new byte[]{0x01};
        characteristic.setValue(originalByteArray);
        callback.onCharacteristicRead(gatt, characteristic, 0);

        ArgumentCaptor<BluetoothPeripheral> captorPeripheral = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<byte[]> captorValue = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<BluetoothGattCharacteristic> captorCharacteristic = ArgumentCaptor.forClass(BluetoothGattCharacteristic.class);
        ArgumentCaptor<GattStatus> captorStatus = ArgumentCaptor.forClass(GattStatus.class);
        verify(peripheralCallback).onCharacteristicUpdate(captorPeripheral.capture(), captorValue.capture(), captorCharacteristic.capture(), captorStatus.capture());

        byte[] value = captorValue.getValue();
        assertEquals(0x01, value[0]);
        assertNotEquals(value, originalByteArray);   // Check if the byte array has been copier
        assertEquals(peripheral, captorPeripheral.getValue());
        assertEquals(characteristic, captorCharacteristic.getValue());
        assertEquals(GattStatus.SUCCESS, (GattStatus) captorStatus.getValue() );
    }

    @Test
    public void readCharacteristicNotConnectedTest() throws Exception {
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), PROPERTY_READ,0);

        when(gatt.readCharacteristic(characteristic)).thenReturn(true);

        peripheral.readCharacteristic(characteristic);

        verify(gatt, never()).readCharacteristic(any(BluetoothGattCharacteristic.class));
    }

    @Test
    public void readCharacteristicNoReadPropertyTest() throws Exception {
        peripheral.connect();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), 0,0);

        peripheral.readCharacteristic(characteristic);

        verify(gatt, never()).readCharacteristic(any(BluetoothGattCharacteristic.class));
    }

    @Test
    public void writeCharacteristicTest() throws Exception {
        BluetoothGattCallback callback = connectAndGetCallback();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), PROPERTY_WRITE,0);

        when(gatt.writeCharacteristic(characteristic)).thenReturn(true);

        peripheral.writeCharacteristic(characteristic, new byte[]{0}, WriteType.WITH_RESPONSE);

        verify(gatt).writeCharacteristic(any(BluetoothGattCharacteristic.class));

        byte[] originalByteArray = new byte[]{0x01};
        characteristic.setValue(originalByteArray);
        callback.onCharacteristicWrite(gatt, characteristic, 0);

        ArgumentCaptor<BluetoothPeripheral> captorPeripheral = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<byte[]> captorValue = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<BluetoothGattCharacteristic> captorCharacteristic = ArgumentCaptor.forClass(BluetoothGattCharacteristic.class);
        ArgumentCaptor<GattStatus> captorStatus = ArgumentCaptor.forClass(GattStatus.class);
        verify(peripheralCallback).onCharacteristicWrite(captorPeripheral.capture(), captorValue.capture(), captorCharacteristic.capture(), captorStatus.capture());

        byte[] value = captorValue.getValue();
        assertEquals(0, value[0]);  // Check if original value is returned and not the one in the characteristic
        assertEquals(peripheral, captorPeripheral.getValue());
        assertEquals(characteristic, captorCharacteristic.getValue());
        assertEquals(GattStatus.SUCCESS, (GattStatus) captorStatus.getValue() );
    }


    @Test
    public void writeCharacteristicNotConnectedTest() throws Exception {
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), PROPERTY_WRITE,0);

        when(gatt.writeCharacteristic(characteristic)).thenReturn(true);

        peripheral.writeCharacteristic(characteristic, new byte[]{0}, WriteType.WITH_RESPONSE);

        verify(gatt, never()).writeCharacteristic(any(BluetoothGattCharacteristic.class));
    }

    @Test
    public void writeCharacteristicNotWritePropertyTest() throws Exception {
        peripheral.connect();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), 0,0);

        peripheral.writeCharacteristic(characteristic, new byte[] { 0, 0 }, WriteType.WITH_RESPONSE);

        verify(gatt, never()).writeCharacteristic(any(BluetoothGattCharacteristic.class));
    }

    @Test
    public void writeCharacteristicNoValueTest() throws Exception {
        peripheral.connect();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), PROPERTY_WRITE,0);

        when(gatt.writeCharacteristic(characteristic)).thenReturn(true);

        peripheral.writeCharacteristic(characteristic, new byte[0], WriteType.WITH_RESPONSE);

        verify(gatt, never()).writeCharacteristic(any(BluetoothGattCharacteristic.class));
    }

    @Test
    public void queueTestConsecutiveReads() throws Exception {
        BluetoothGattCallback callback = connectAndGetCallback();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_READ,0);
        BluetoothGattCharacteristic characteristic2 = new BluetoothGattCharacteristic(UUID.fromString("00002A1E-0000-1000-8000-00805f9b34fb"),PROPERTY_READ,0);
        service.addCharacteristic(characteristic);
        service.addCharacteristic(characteristic2);
        byte[] byteArray = new byte[] {0x01, 0x02, 0x03};
        characteristic.setValue(byteArray);
        characteristic2.setValue(byteArray);
        when(gatt.readCharacteristic(characteristic)).thenReturn(true);

        peripheral.readCharacteristic(characteristic);
        peripheral.readCharacteristic(characteristic2);

        verify(gatt).readCharacteristic(characteristic);

        callback.onCharacteristicRead(gatt, characteristic, GATT_SUCCESS);

        verify(peripheralCallback).onCharacteristicUpdate(peripheral, byteArray, characteristic, GattStatus.SUCCESS);

        verify(gatt).readCharacteristic(characteristic2);

        callback.onCharacteristicRead(gatt, characteristic2, GATT_SUCCESS);

        verify(peripheralCallback).onCharacteristicUpdate(peripheral, byteArray, characteristic2, GattStatus.SUCCESS);
    }

    @Test
    public void queueTestConsecutiveReadsWithError() throws Exception {
        BluetoothGattCallback callback = connectAndGetCallback();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_READ,0);
        BluetoothGattCharacteristic characteristic2 = new BluetoothGattCharacteristic(UUID.fromString("00002A1E-0000-1000-8000-00805f9b34fb"),PROPERTY_READ,0);
        service.addCharacteristic(characteristic);
        service.addCharacteristic(characteristic2);
        byte[] byteArray = new byte[] {0x01, 0x02, 0x03};
        characteristic.setValue(byteArray);
        characteristic2.setValue(byteArray);
        when(gatt.readCharacteristic(characteristic)).thenReturn(true);

        peripheral.readCharacteristic(characteristic);
        peripheral.readCharacteristic(characteristic2);

        verify(gatt).readCharacteristic(characteristic);

        callback.onCharacteristicRead(gatt, characteristic, 128);

        verify(peripheralCallback).onCharacteristicUpdate(peripheral, byteArray, characteristic,GattStatus.NO_RESOURCES);

        verify(gatt).readCharacteristic(characteristic2);

        callback.onCharacteristicRead(gatt, characteristic2, GATT_SUCCESS);

        verify(peripheralCallback).onCharacteristicUpdate(peripheral, byteArray ,characteristic2, GattStatus.SUCCESS);
    }

    @Test
    public void queueTestConsecutiveReadsNoResponse() throws Exception {
        BluetoothGattCallback callback = connectAndGetCallback();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_READ,0);
        BluetoothGattCharacteristic characteristic2 = new BluetoothGattCharacteristic(UUID.fromString("00002A1E-0000-1000-8000-00805f9b34fb"),PROPERTY_READ,0);
        service.addCharacteristic(characteristic);
        service.addCharacteristic(characteristic2);
        byte[] byteArray = new byte[] {0x01, 0x02, 0x03};
        characteristic.setValue(byteArray);
        characteristic2.setValue(byteArray);

        when(gatt.readCharacteristic(characteristic)).thenReturn(true);

        peripheral.readCharacteristic(characteristic);
        peripheral.readCharacteristic(characteristic2);

        verify(gatt).readCharacteristic(characteristic);
        verify(peripheralCallback, never()).onCharacteristicUpdate(peripheral, byteArray, characteristic, GattStatus.SUCCESS);
        verify(gatt, never()).readCharacteristic(characteristic2);
    }

    @Test
    public void notifyTest() {
        BluetoothGattCallback callback = connectAndGetCallback();

        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"), PROPERTY_INDICATE,0);
        peripheral.setNotify(characteristic, true);

        byte[] originalByteArray = new byte[]{0x01};
        characteristic.setValue(originalByteArray);
        callback.onCharacteristicChanged(gatt, characteristic);

        ArgumentCaptor<BluetoothPeripheral> captorPeripheral = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<byte[]> captorValue = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<BluetoothGattCharacteristic> captorCharacteristic = ArgumentCaptor.forClass(BluetoothGattCharacteristic.class);
        ArgumentCaptor<GattStatus> captorStatus = ArgumentCaptor.forClass(GattStatus.class);
        verify(peripheralCallback).onCharacteristicUpdate(captorPeripheral.capture(), captorValue.capture(), captorCharacteristic.capture(), captorStatus.capture());

        byte[] value = captorValue.getValue();
        assertEquals(0x01, value[0]);  // Check if original value is returned and not the one in the characteristic
        assertNotEquals(value, originalByteArray);   // Check if the byte array has been copied
        assertEquals(peripheral, captorPeripheral.getValue());
        assertEquals(characteristic, captorCharacteristic.getValue());
        assertEquals(GattStatus.SUCCESS, (GattStatus) captorStatus.getValue() );
    }

    @Test
    public void readDescriptor() {
        BluetoothGattCallback callback = connectAndGetCallback();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_INDICATE,0);
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(UUID.fromString("00002903-0000-1000-8000-00805f9b34fb"),0);
        service.addCharacteristic(characteristic);
        characteristic.addDescriptor(descriptor);

        when(gatt.getServices()).thenReturn(Arrays.asList(service));

        peripheral.readDescriptor(descriptor);

        verify(gatt).readDescriptor(descriptor);

        byte[] originalByteArray = new byte[]{0x01};
        descriptor.setValue(originalByteArray);
        callback.onDescriptorRead(gatt, descriptor, 0);

        ArgumentCaptor<BluetoothPeripheral> captorPeripheral = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<byte[]> captorValue = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<BluetoothGattDescriptor> captorDescriptor = ArgumentCaptor.forClass(BluetoothGattDescriptor.class);
        ArgumentCaptor<GattStatus> captorStatus = ArgumentCaptor.forClass(GattStatus.class);
        verify(peripheralCallback).onDescriptorRead(captorPeripheral.capture(), captorValue.capture(), captorDescriptor.capture(), captorStatus.capture());

        byte[] value = captorValue.getValue();
        assertEquals(0x01, value[0]);
        assertNotEquals(value, originalByteArray);   // Check if the byte array has been copied
        assertEquals(peripheral, captorPeripheral.getValue());
        assertEquals(descriptor, captorDescriptor.getValue());
        assertEquals(GattStatus.SUCCESS, (GattStatus) captorStatus.getValue() );
    }


    @Test
    public void writeDescriptor() {
        BluetoothGattCallback callback = connectAndGetCallback();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_INDICATE,0);
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(UUID.fromString("00002903-0000-1000-8000-00805f9b34fb"),0);
        service.addCharacteristic(characteristic);
        characteristic.addDescriptor(descriptor);

        when(gatt.getServices()).thenReturn(Arrays.asList(service));
        byte[] originalByteArray = new byte[]{0x01};
        peripheral.writeDescriptor(descriptor, originalByteArray);

        verify(gatt).writeDescriptor(descriptor);

        callback.onDescriptorWrite(gatt, descriptor, 0);

        ArgumentCaptor<BluetoothPeripheral> captorPeripheral = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<byte[]> captorValue = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<BluetoothGattDescriptor> captorDescriptor = ArgumentCaptor.forClass(BluetoothGattDescriptor.class);
        ArgumentCaptor<GattStatus> captorStatus = ArgumentCaptor.forClass(GattStatus.class);
        verify(peripheralCallback).onDescriptorWrite(captorPeripheral.capture(), captorValue.capture(), captorDescriptor.capture(), captorStatus.capture());

        byte[] value = captorValue.getValue();
        assertEquals(0x01, value[0]);
        assertNotEquals(value, originalByteArray);   // Check if the byte array has been copied
        assertEquals(peripheral, captorPeripheral.getValue());
        assertEquals(descriptor, captorDescriptor.getValue());
        assertEquals(GattStatus.SUCCESS, (GattStatus) captorStatus.getValue() );
    }

    @Test
    public void readRemoteRSSITest() {
        BluetoothGattCallback callback = connectAndGetCallback();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        peripheral.readRemoteRssi();

        verify(gatt).readRemoteRssi();

        callback.onReadRemoteRssi(gatt, -40, GATT_SUCCESS);

        verify(peripheralCallback).onReadRemoteRssi(peripheral, -40, GattStatus.SUCCESS);
    }

    @Test
    public void requestMTUTest() {
        BluetoothGattCallback callback = connectAndGetCallback();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        peripheral.requestMtu(32);

        verify(gatt).requestMtu(32);

        callback.onMtuChanged(gatt, 32, GATT_SUCCESS);

        verify(peripheralCallback).onMtuChanged(peripheral, 32, GattStatus.SUCCESS);
    }

    @Test
    public void connectionPriorityTest() throws Exception {
        BluetoothGattCallback callback = connectAndGetCallback();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        peripheral.requestConnectionPriority(CONNECTION_PRIORITY_HIGH);

        verify(gatt).requestConnectionPriority(CONNECTION_PRIORITY_HIGH);
    }

    @Test
    public void createBondWhileConnectedTest() {
        BluetoothGattCallback callback = connectAndGetCallback();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        peripheral.createBond();

        verify(device).createBond();
    }

    @Test
    public void createBondWhileUnConnectedTest() {

        peripheral.createBond();

        verify(device).createBond();
    }

    @Test
    public void queueTestRetryCommand() throws Exception {
        BluetoothGattCallback callback = connectAndGetCallback();
        callback.onConnectionStateChange(gatt, GATT_SUCCESS, STATE_CONNECTED);

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),PROPERTY_READ,0);
        BluetoothGattCharacteristic characteristic2 = new BluetoothGattCharacteristic(UUID.fromString("00002A1E-0000-1000-8000-00805f9b34fb"),PROPERTY_READ,0);
        service.addCharacteristic(characteristic);
        service.addCharacteristic(characteristic2);
        when(gatt.readCharacteristic(characteristic)).thenReturn(true);
        when(gatt.getServices()).thenReturn(Arrays.asList(service));

        peripheral.readCharacteristic(characteristic);

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
    public void onConnectionStateChangedConnectedUnbondedTest() throws Exception {
        when(device.getBondState()).thenReturn(BOND_NONE);

        BluetoothGattCallback callback = connectAndGetCallback();

        callback.onConnectionStateChange(gatt, GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);

        verify(gatt).discoverServices();
    }

    @Test
    public void onConnectionStateChangedConnectedGattFailedTest() throws Exception {
        BluetoothGattCallback callback = connectAndGetCallback();

        callback.onConnectionStateChange(gatt, GATT_FAILURE, STATE_DISCONNECTED);

        verify(gatt).close();
    }

    @Test
    public void onConnectionStateChangedConnectedAlreadyBondedTest() throws Exception {
        when(device.getBondState()).thenReturn(BOND_BONDED);

        BluetoothGattCallback callback = connectAndGetCallback();

        callback.onConnectionStateChange(gatt, GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(gatt).discoverServices();
    }

    @Test
    public void onConnectionStateChangedConnectedBondedingTest() throws Exception {
        when(device.getBondState()).thenReturn(BOND_BONDING);

        BluetoothGattCallback callback = connectAndGetCallback();

        callback.onConnectionStateChange(gatt, GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(gatt, never()).discoverServices();
    }

    @Test
    public void onConnectionStateChangedDisconnectedTest() throws Exception {
        BluetoothGattCallback callback = connectAndGetCallback();

        callback.onConnectionStateChange(gatt, GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED);

        verify(gatt).close();
        verify(internalCallback).disconnected(any(BluetoothPeripheral.class), any(HciStatus.class));
    }

    @Test
    public void onServicesDiscoveredTest() throws Exception {
        BluetoothGattCallback callback = connectAndGetCallback();

        BluetoothGattService service = mock(BluetoothGattService.class);
        when(service.getUuid()).thenReturn(SERVICE_UUID);
        when(gatt.getServices()).thenReturn(Arrays.asList(service));

        BluetoothGattCharacteristic characteristic = mock(BluetoothGattCharacteristic.class);
        when(characteristic.getProperties()).thenReturn(PROPERTY_READ);
        when(service.getCharacteristics()).thenReturn(Arrays.asList(characteristic));

        callback.onServicesDiscovered(gatt, 0);

        List<UUID> expected = Arrays.asList(SERVICE_UUID);

//        assertThat(peripheral.getServices(), is(expected));
    }

    @Test
    public void onServicesDiscoveredCharacteristicNotifyTest() throws Exception {
        BluetoothGattCallback callback = connectAndGetCallback();

        BluetoothGattService service = mock(BluetoothGattService.class);
        when(service.getUuid()).thenReturn(SERVICE_UUID);
        when(gatt.getServices()).thenReturn(Arrays.asList(service));

        BluetoothGattCharacteristic characteristic = mock(BluetoothGattCharacteristic.class);
        when(characteristic.getProperties()).thenReturn(PROPERTY_NOTIFY);
        when(service.getCharacteristics()).thenReturn(Arrays.asList(characteristic));

        callback.onServicesDiscovered(gatt, 0);

        List<UUID> expected = Arrays.asList(SERVICE_UUID);

 //       assertThat(peripheral.getServices(), is(expected));
    }

    @Test
    public void onServicesDiscoveredCharacteristicIndicateTest() throws Exception {
        BluetoothGattCallback callback = connectAndGetCallback();

        BluetoothGattService service = mock(BluetoothGattService.class);
        when(service.getUuid()).thenReturn(SERVICE_UUID);
        when(gatt.getServices()).thenReturn(Arrays.asList(service));

        BluetoothGattCharacteristic characteristic = mock(BluetoothGattCharacteristic.class);
        when(characteristic.getProperties()).thenReturn(PROPERTY_INDICATE);
        when(service.getCharacteristics()).thenReturn(Arrays.asList(characteristic));

        callback.onServicesDiscovered(gatt, 0);

        List<UUID> expected = Arrays.asList(SERVICE_UUID);

//        assertThat(peripheral.getServices(), is(expected));
    }

    @Test
    public void onServicesDiscoveredServicesNotFoundTest() throws Exception {
        BluetoothGattCallback callback = connectAndGetCallback();

        callback.onServicesDiscovered(gatt, 129);

        verify(gatt).disconnect();
    }

    @Test
    public void onBondStateChangedBonded() throws Exception {
        connectAndGetCallback();

        Field field = BluetoothPeripheral.class.getDeclaredField("bondStateReceiver");
        field.setAccessible(true);
        BroadcastReceiver broadcastReceiver = (BroadcastReceiver) field.get(peripheral);

        Intent intent = mock(Intent.class);
        when(intent.getAction()).thenReturn(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        when(intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)).thenReturn(BOND_BONDED);
        when(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)).thenReturn(device);

        broadcastReceiver.onReceive(context, intent);

        verify(gatt).discoverServices();
    }

    @Test
    public void onBondStateChangedNone() throws Exception {
        connectAndGetCallback();

        Field field = BluetoothPeripheral.class.getDeclaredField("bondStateReceiver");
        field.setAccessible(true);
        BroadcastReceiver broadcastReceiver = (BroadcastReceiver) field.get(peripheral);

        Intent intent = mock(Intent.class);
        when(intent.getAction()).thenReturn(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        when(intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)).thenReturn(BluetoothDevice.BOND_NONE);
        when(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)).thenReturn(device);

        broadcastReceiver.onReceive(context, intent);

        verify(gatt).disconnect();
    }


    private BluetoothGattCallback connectAndGetCallback() {
        peripheral.connect();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        ArgumentCaptor<BluetoothGattCallback> bluetoothGattCallbackCaptor = ArgumentCaptor.forClass(BluetoothGattCallback.class);
        verify(device).connectGatt(any(Context.class), anyBoolean(), bluetoothGattCallbackCaptor.capture(), anyInt());

        List<BluetoothGattCallback> capturedGatts = bluetoothGattCallbackCaptor.getAllValues();
        return capturedGatts.get(0);
    }
}