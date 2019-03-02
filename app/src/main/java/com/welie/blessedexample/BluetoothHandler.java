package com.welie.blessedexample;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import com.welie.blessed.BluetoothBytesParser;
import com.welie.blessed.BluetoothCentral;
import com.welie.blessed.BluetoothCentralCallback;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.BluetoothPeripheralCallback;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
import static com.welie.blessed.BluetoothBytesParser.FORMAT_UINT8;
import static com.welie.blessed.BluetoothBytesParser.bytes2String;
import static com.welie.blessed.BluetoothPeripheral.GATT_SUCCESS;
import static java.lang.Math.abs;

public class BluetoothHandler {
    private final String TAG = BluetoothHandler.class.getSimpleName();

    // UUIDs for the Blood Pressure service (BLP)
    private static final UUID BLP_SERVICE_UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb");
    private static final UUID BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A35-0000-1000-8000-00805f9b34fb");

    // UUIDs for the Device Information service (DIS)
    private static final UUID DIS_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
    private static final UUID MANUFACTURER_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");
    private static final UUID MODEL_NUMBER_CHARACTERISTIC_UUID = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb");

    // UUIDs for the Current Time service (CTS)
    private static final UUID CTS_SERVICE_UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");
    private static final UUID CURRENT_TIME_CHARACTERISTIC_UUID = UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb");

    // UUIDs for the Battery Service (BAS)
    private static final UUID BTS_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
    private static final UUID BATTERY_LEVEL_CHARACTERISTIC_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb");

    private static final UUID CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // Local variables
    private BluetoothCentral central;
    private static BluetoothHandler instance = null;
    private Context context;
    private Handler handler = new Handler();
    private int currentTimeCounter = 0;

    // Callback for peripherals
    private final BluetoothPeripheralCallback peripheralCallback = new BluetoothPeripheralCallback() {
        @Override
        public void onServicesDiscovered(BluetoothPeripheral peripheral) {

            // Read manufacturer and model number from the Device Information Service
            if(peripheral.getService(DIS_SERVICE_UUID) != null) {
                peripheral.readCharacteristic(peripheral.getCharacteristic(DIS_SERVICE_UUID, MANUFACTURER_NAME_CHARACTERISTIC_UUID));
                peripheral.readCharacteristic(peripheral.getCharacteristic(DIS_SERVICE_UUID, MODEL_NUMBER_CHARACTERISTIC_UUID));
            }

            // Turn on notifications for Current Time Service
            if(peripheral.getService(CTS_SERVICE_UUID) != null) {
                BluetoothGattCharacteristic currentTimeCharacteristic = peripheral.getCharacteristic(CTS_SERVICE_UUID, CURRENT_TIME_CHARACTERISTIC_UUID);
                peripheral.setNotify(currentTimeCharacteristic, true);

                // If it has the write property we write the current time
                if((currentTimeCharacteristic.getProperties() & PROPERTY_WRITE) > 0) {
                    // Write the current time unless it is an Omron device
                    if(!(peripheral.getName().contains("BLEsmart_"))) {
                        BluetoothBytesParser parser = new BluetoothBytesParser();
                        parser.setCurrentTime(Calendar.getInstance());
                        peripheral.writeCharacteristic(currentTimeCharacteristic, parser.getValue(), WRITE_TYPE_DEFAULT);
                    }
                }
            }

            // Turn on notifications for Battery Service
            if(peripheral.getService(BTS_SERVICE_UUID) != null) {
                peripheral.setNotify(peripheral.getCharacteristic(BTS_SERVICE_UUID, BATTERY_LEVEL_CHARACTERISTIC_UUID), true);
            }

            // Turn on notifications for Blood Pressure Service
            if(peripheral.getService(BLP_SERVICE_UUID) != null) {
                peripheral.setNotify(peripheral.getCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID), true);
            }
        }

        @Override
        public void onNotificationStateUpdate(BluetoothPeripheral peripheral, BluetoothGattCharacteristic characteristic, int status) {
            if( status == GATT_SUCCESS) {
                if(peripheral.isNotifying(characteristic)) {
                    Log.i(TAG, String.format("SUCCESS: Notify set to 'on' for %s", characteristic.getUuid()));
                } else {
                    Log.i(TAG, String.format("SUCCESS: Notify set to 'off' for %s", characteristic.getUuid()));
                }
            } else {
                Log.e(TAG, String.format("ERROR: Changing notification state failed for %s", characteristic.getUuid()));
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothPeripheral peripheral, byte[] value, BluetoothGattCharacteristic characteristic, int status) {
            if( status == GATT_SUCCESS) {
                Log.i(TAG, String.format("SUCCESS: Writing <%s> to <%s>", bytes2String(value), characteristic.getUuid().toString()));
            } else {
                Log.i(TAG, String.format("ERROR: Failed writing <%s> to <%s>", bytes2String(value), characteristic.getUuid().toString()));
            }
        }

        @Override
        public void onCharacteristicUpdate(BluetoothPeripheral peripheral, byte[] value, BluetoothGattCharacteristic characteristic) {
            UUID characteristicUUID = characteristic.getUuid();
            BluetoothBytesParser parser = new BluetoothBytesParser(value);

            if (characteristicUUID.equals(BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID)) {
                BloodPressureMeasurement measurement = new BloodPressureMeasurement(value);
                Intent intent = new Intent("BluetoothMeasurement");
                intent.putExtra("BloodPressure", measurement);
                context.sendBroadcast(intent);
                Log.d(TAG, String.format("%s", measurement));
            }
            else if(characteristicUUID.equals(CURRENT_TIME_CHARACTERISTIC_UUID)) {
                Date currentTime = parser.getDateTime();
                Log.i(TAG, String.format("Received device time: %s", currentTime));

                // Deal with Omron devices where we can only write currentTime under specific conditions
                if(peripheral.getName().contains("BLEsmart_")) {
                    boolean isNotifying = peripheral.isNotifying(peripheral.getCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID));
                    if(isNotifying) currentTimeCounter++;

                    // We can set device time for Omron devices only if it is the first notification and currentTime is more than 10 min from now
                    long interval = abs(Calendar.getInstance().getTimeInMillis() - currentTime.getTime());
                    if (currentTimeCounter == 1 && interval > 10*60*1000) {
                        parser.setCurrentTime(Calendar.getInstance());
                        peripheral.writeCharacteristic(characteristic, parser.getValue(), WRITE_TYPE_DEFAULT);
                    }
                }
            }
            else if(characteristicUUID.equals(BATTERY_LEVEL_CHARACTERISTIC_UUID)) {
                int batteryLevel = parser.getIntValue(FORMAT_UINT8);
                Log.i(TAG, String.format("Received battery level %d%%", batteryLevel));
            }
            else if(characteristicUUID.equals(MANUFACTURER_NAME_CHARACTERISTIC_UUID)) {
                String manufacturer = parser.getStringValue(0);
                Log.i(TAG, String.format("Received manufacturer: %s", manufacturer));
            }
            else if(characteristicUUID.equals(MODEL_NUMBER_CHARACTERISTIC_UUID)) {
                String modelNumber = parser.getStringValue(0);
                Log.i(TAG, String.format("Received modelnumber: %s", modelNumber));
            }
        }
    };

    // Callback for central
    private final BluetoothCentralCallback bluetoothCentralCallback = new BluetoothCentralCallback() {

        @Override
        public void onConnectedPeripheral(BluetoothPeripheral peripheral) {
            Log.i(TAG, String.format("connected to '%s'", peripheral.getName()));
        }

        @Override
        public void onConnectionFailed(BluetoothPeripheral peripheral, final int status) {
            Log.e(TAG, String.format("connection '%s' failed with status %d", peripheral.getName(), status ));
        }

        @Override
        public void onDisconnectedPeripheral(final BluetoothPeripheral peripheral, final int status) {
            Log.i(TAG, String.format("disconnected '%s' with status %d", peripheral.getName(), status));

            // Reconnect to this device when it becomes available again
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    central.autoConnectPeripheral(peripheral, peripheralCallback);
                }
            }, 5000);
        }

        @Override
        public void onDiscoveredPeripheral(BluetoothPeripheral peripheral, ScanResult scanResult) {
            Log.i(TAG, String.format("Found peripheral '%s'", peripheral.getName()));
            central.stopScan();
            central.connectPeripheral(peripheral, peripheralCallback);
        }
    };

    public static synchronized BluetoothHandler getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothHandler(context.getApplicationContext());
        }
        return instance;
    }

    private BluetoothHandler(Context context) {
        this.context = context;
        central = new BluetoothCentral(context, bluetoothCentralCallback, new Handler());

        // Scan for peripherals with a certain service UUID
        central.scanForPeripheralsWithServices(new UUID[]{BLP_SERVICE_UUID});
    }
}
