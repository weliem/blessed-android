# Creating your own peripheral

This guide describes how to create your own peripheral using the `BluetoothPeripheralManager` class. This class has been designed to make it as easy as possible to develop your own peripheral. An [example project](https://github.com/weliem/bluetooth-server-example) is available.

## Setting up your peripheral

The first thing to do is to create an instance of the `BluetoothPeripheralManager` class:

```java
BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);

BluetoothPeripheralManager peripheralManager = new BluetoothPeripheralManager(context, bluetoothManager, peripheralManagerCallback);
```

Not all Android phones support creating a peripheral. Most recent phones support it but some older ones may not. So make sure you do a check like this:

```java
BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

if (!bluetoothAdapter.isMultipleAdvertisementSupported()) {
    Timber.e("not supporting advertising");
}
```

## Adding services

Now that you created your `BluetoothPeripheralManager` you need to add some services. Which services you add is up to you of course.

Setting up a heartrate service could be done like this:

```java
UUID HRS_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
UUID HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb");

BluetoothGattService service = new BluetoothGattService(HRS_SERVICE_UUID, SERVICE_TYPE_PRIMARY);
BluetoothGattCharacteristic measurement = new BluetoothGattCharacteristic(HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_READ | PROPERTY_INDICATE, PERMISSION_READ);
service.addCharacteristic(measurement);
peripheralManager.add(service);
```

The `add()` method is asynchronous and you will receive a callback via the `BluetoothPeripheralManagerCallback` class on the method `onServiceAdded()`. The `add()` call is enqueued so you can call it several times in a row.

## Starting advertising

To start advertising you call the method `startAdvertising` with the advertise settings, advertise data and scan response. Here is an example:

```java
AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
        .setConnectable(true)
        .setTimeout(0)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
        .build();

AdvertiseData advertiseData = new AdvertiseData.Builder()
        .setIncludeTxPowerLevel(true)
        .addServiceUuid(new ParcelUuid(serviceUUID))
        .build();

AdvertiseData scanResponse = new AdvertiseData.Builder()
        .setIncludeDeviceName(true)
        .build();

peripheralManager.startAdvertising(advertiseSettings, scanResponse, advertiseData);
```
        
## Implementing characteristic read or write requests

When a remote central connects and tries to read a characteristic, you get a callback on `onCharacteristicRead`. 
You need to return a ReadResponse object which must contain a GattStatus object and optionally a byte array. 
If you want to reject the read, just return a GattStatus that is not SUCCESS. If you are returning SUCCESS you must also provide a byte array with the value of the characteristic.\

```java
@Override
public ReadResponse onCharacteristicRead(@NotNull BluetoothCentral central, @NotNull BluetoothGattCharacteristic characteristic) {
    return new ReadResponse(GattStatus.SUCCESS, getCurrentTime());
}
```

When a write request happens, you get a callback on `onCharacteristicWrite`. If you want to validate the value before the write is completed you can do that by overriding this method. If you consider the value valid, you must return GattStatus.SUCCESS and otherwise you return some other GattStatus value that represents the error. 
After you return GattStatus.SUCCESS, the value is assigned to the characteristic (only when api-level is < 33). Otherwise the characteristics's value will remain unchanged and the remote central will receive an error. For example:

```java
@Override
public GattStatus onCharacteristicWrite(@NotNull BluetoothCentral central, @NotNull BluetoothGattCharacteristic characteristic, @NotNull byte[] value) {
    if (isValid(value, characteristic)) {
        return GattStatus.SUCCESS;
    } else {
        // Return an error, typical INVALID_ATTRIBUTE_VALUE_LENGTH or VALUE_NOT_ALLOWED
        return GattStatus.VALUE_NOT_ALLOWED;
    }
}
```

## Implementing descriptor read or write requests

Read or write request for descriptors work in the same way as for characteristics. The only exception is when the descriptor happens to be the CCC descriptor, which is used to turn on/off notifications

## Enabling or disabling of notifications

If you have a characteristic with PROPERTY_INDICATE or PROPERTY_NOTIFY and a CCC descriptor added, then a remote central may 'enable notifications'. Blessed will doublecheck if the the correct descriptor values are written, and if correct it will call either `onNotifyingEnabled` or `onNotifyingDisabled`. It is then your responsibility to actually follow up and do the notifications.


```java
@Override
public void onNotifyingEnabled(@NotNull BluetoothCentral central, @NotNull BluetoothGattCharacteristic characteristic) {
    if (characteristic.getUuid().equals(CURRENT_TIME_CHARACTERISTIC_UUID)) {
        notifyCurrentTime();
    }
}

@Override
public void onNotifyingDisabled(@NotNull BluetoothCentral central, @NotNull BluetoothGattCharacteristic characteristic) {
    if (characteristic.getUuid().equals(CURRENT_TIME_CHARACTERISTIC_UUID)) {
        stopNotifying();
    }
}
```

## Sending notifications

Once notifications have been enabled, you can send notifications by calling:

```java
peripheralManager.notifyCharacteristicChanged(value, characteristic);
```

Note that you have to pass the value for the characteristic. That is there so that you can do high speed notifications as each call to notifyCharacteristicChanged() will be queued up. So you can call this function in a loop and then each command is executed the value of the characteristic will be updated and sent to the remote central.

## Connecting and disconnecting centrals

When a remote central connects or disconnects, the following callbacks are called:

```java
@Override
public void onCentralConnected(@NotNull BluetoothCentral central) {
    // Do something, e.g. initialization of characteristics
}

@Override
public void onCentralDisconnected(@NotNull BluetoothCentral central) {
    if (noCentralsConnected()) {
        stopNotifying();
    }
}
```

Typically, when a central disconnects, you stop notifying and clean up. 

## Long reads and writes

The BluetoothPeripheralManager class supports long reads and writes. It will take care of splitting up characteristic byte arrays in smaller chunks and re-assembling them. Hence, nothing special is needed and they function the same way as normal read and writes.

## Using CentralManager and PeripheralManager at the same time
If you use the BluetoothCentralManager and BluetoothPeripheralManager at the same time, you need to tell the peripheralmanager who the central manager is:

```java
peripheralManager.setCentralManager(central)
```
If you don't do this, the peripheral manager will not be able to distinguish centrals from peripherals and you will see too many connected events.

