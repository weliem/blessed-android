# BLESSED

BLESSED is a very compact BLE library for Android 6 and higher, that makes working with BLE on Android very easy. It takes care of many aspects of working with BLE you would normally have to take care of yourself like:

* *Queueing commands*, so you can don't have to wait anymore for the completion of a command before issueing the next command
* *Bonding*, so you don't have to do anything in order to robustly bond devices
* *Easy scanning*, so you don't have to setup complex scan filters
* *Threadsafe*, so you don't see weird threading related issues anymore
* *Workarounds for known bugs*, so you don't have to research any workarounds
* *Higher abstraction methods for convenience*, so that you don't have to do a lot of low-level management to get stuff done

The library contains of 3 core classes and 2 callback abstract classes:
1. `BluetoothCentral`, and it companion abstract class `BluetoothCentralCallback`
2. `BluetoothPeripheral`, and it's companion abstract class `BluetoothPeripheralCallback`
3. `BluetoothBytesParser`

The `BluetoothCentral` class is used to scan for devices and manage connections. The `BluetoothPeripheral` class is a replacement for the standard Android `BluetoothDevice` and `BluetoothGatt` classes. It wraps all GATT related peripheral functionality.

The BLESSED library was inspired by CoreBluetooth on iOS and provides the same level of abstraction. If you already have developed using CoreBluetooth you can very easily port your code to Android using this library.

## Scanning

There are 3 different scanning methods:

```java
public void scanForPeripheralsWithServices(final UUID[] serviceUUIDs)
public void scanForPeripheralsWithNames(final String[] peripheralNames)
public void scanForPeripheralsWithAddresses(final String[] peripheralAddresses)
```

They all work in the same way and take an array of either service UUIDs, peripheral names or mac addresses. So in order to setup a scan for a device with the Bloodpressure service and connect to it, you do:

```java
private final BluetoothCentralCallback bluetoothCentralCallback = new BluetoothCentralCallback() {
     @Override
     public void onDiscoveredPeripheral(@NonNull final ScanResult scanResult) {
         // Found a peripheral so stop the scan and connect to it
         central.stopScan();
         central.connectPeripheral(scanResult.getDevice(), peripheralCallback);
     }
};

// Create BluetoothCentral and receive callbacks on the main thread
BluetoothCentral central = BluetoothCentral(getApplicationContext(), bluetoothCentralCallback, new Handler(Looper.getMainLooper()));

// Define blood pressure service UUID
UUID BLOODPRESSURE_SERVICE_UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb");

// Scan for peripherals with a certain service UUID
central.scanForPeripheralsWithServices(new UUID[]{BLOODPRESSURE_SERVICE_UUID});
```
**Note** Only 1 of these 3 types of scans can be active at one time! So call stopScan() before calling another scan.

## Connecting to devies

There are 2 ways to connect to a device:
```java
public void connectPeripheral(BluetoothDevice device, BluetoothPeripheralCallback peripheralCallback)
public void autoConnectPeripheral(@NonNull String deviceAddress, BluetoothPeripheralCallback peripheralCallback)
```

The method `connectPeripheral` will try to immediately connect to a device that has already been found using a scan. This method will time out after 30 seconds or less depending on the device manufacturer. 

The method `autoConnectPeripheral` is for re-connecting to known devices for which you already now the device address. The BLE stack will automatically connect to the device when it sees it in its internal scan. Therefor it may take longer to connect to a device but this call will never time out! So you can issue the autoConnect and the device will connected when it is found. This call will also work when the device is not cached by the Android stack.

After issuing a connect call, you will receive one of the following callbacks:
```java
public void onConnectedPeripheral(final BluetoothPeripheral peripheral)
public void onConnectionFailed(final String deviceAddress)
public void onDisconnectedPeripheral(final String deviceAddress)
```

## Service discovery

The BLESSED library will automatically do the service discovery for you and once it is completed you will receive the following callback:

```java
public void onServicesDiscovered(@NonNull final BluetoothPeripheral peripheral)
```
In order to get the services you can use methods like getServices() or getService(UUID). In order to get hold of characteristics you can call getCharacteristic(UUID) on the BluetoothGattService object or call getCharacteristic() on the BluetoothPeripheral object.

## Reading and writing

Reading and writing to characteristics is done using the following methods:

```java
public boolean readCharacteristic(final BluetoothGattCharacteristic characteristic)
public boolean writeCharacteristic(final BluetoothGattCharacteristic characteristic, final byte[] value, final int writeType)
```

Both methods are asynchrous and will be queued up. This mean you will receive a callback once the result of the operation is available.
For read operations you will get a callback on:

```java
public void onCharacteristicUpdate(@NonNull final BluetoothPeripheral peripheral, @NonNull byte[] value, @NonNull final BluetoothGattCharacteristic characteristic)
```
And for write operations you will get a callback on:
```java
public void onCharacteristicWrite(@NonNull final BluetoothPeripheral peripheral, @NonNull byte[] value, @NonNull final BluetoothGattCharacteristic characteristic, final int status)

```

In these callbacks, the *value* parameter is the threadsafe byte array that was received. Use this value instead of the value that is part of the BluetoothGattCharacteristic object since that one may have changed in the mean time because of incoming notifications or write operations.

## Turning notifications on/off

BLESSED provides a method `setNotify` to turn notifications on or off. It will perform all the necessary operators like writing the the Client Characteristic Configuration for you. So all you need to do is:

```java
// See if this peripheral has the Current Time service
if(peripheral.getService(CTS_SERVICE_UUID) != null) {
     BluetoothGattCharacteristic currentTimeCharacteristic = peripheral.getCharacteristic(CTS_SERVICE_UUID, CURRENT_TIME_CHARACTERISTIC_UUID);
     peripheral.setNotify(currentTimeCharacteristic, true);
}
```

Since this is an asynchronous operation you will receive a callback that indicates success or failure:

```java
@Override
public void onNotificationStateUpdate(@NonNull BluetoothPeripheral peripheral, @NonNull BluetoothGattCharacteristic characteristic, int status) {
     if( status == BluetoothGatt.GATT_SUCCESS) {
          if(peripheral.isNotifying(characteristic)) {
               Log.i(TAG, String.format("SUCCESS: Notify set to 'on' for %s", characteristic.getUuid()));
          } else {
               Log.i(TAG, String.format("SUCCESS: Notify set to 'off' for %s", characteristic.getUuid()));
          }
     } else {
          Log.e(TAG, String.format("ERROR: Changing notification state failed for %s", characteristic.getUuid()));
     }
}
```
