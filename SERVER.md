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

When a remote central connects and tries to read a characteristic, you get a callback on `onCharacteristicRead`. You don't necessarily need to implement it if the characterist already has the proper value. But if you want to update it just before it get returned, you need to override this method. For example, if you implement the current time characteristic and you want to make sure the characteristic value is updated before it is returned, you can do:

```java
@Override
public void onCharacteristicRead(@NotNull BluetoothCentral central, @NotNull BluetoothGattCharacteristic characteristic) {
    currentTime.setValue(getCurrentTime());
}
```

When a write request happens, you get a callback on `onCharacteristicWrite`. If you want to validate the value before it is assigned to the characteristic you can do that by overriding this method. If you consider the value valid, you must return GattStatus.SUCCESS and otherwise you return some other GattStatus value that represents the error. After you return GattStatus.SUCCESS, the value is assigned to the characteristic. Otherwise the characteristics's value will remain unchanged and the remote central will receive an error.

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

Once notifications have been enabled, you can send notifactions by calling:

```java
peripheralManager.notifyCharacteristicChanged(value, characteristic);
```

Note that you have to pass the value for the characteristic. That is there so that you can do high speed notifications as each call to notifyCharacteristicChanged() will be queued up. So you can call this function in a loop and then each command is executed the value of the characteristic will be updated and sent to the remote central.

## Connecting and disconnecting centrals





