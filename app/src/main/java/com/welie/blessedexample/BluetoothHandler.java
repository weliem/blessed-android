package com.welie.blessedexample;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import com.welie.blessed.BluetoothBytesParser;
import com.welie.blessed.BluetoothCentral;
import com.welie.blessed.BluetoothCentralCallback;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.BluetoothPeripheralCallback;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import timber.log.Timber;

import static android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_HIGH;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
import static com.welie.blessed.BluetoothBytesParser.FORMAT_UINT16;
import static com.welie.blessed.BluetoothBytesParser.FORMAT_UINT8;
import static com.welie.blessed.BluetoothBytesParser.bytes2String;
import static com.welie.blessed.BluetoothPeripheral.GATT_SUCCESS;
import static java.lang.Math.abs;

public class BluetoothHandler {

    // UUIDs for the Blood Pressure service (BLP)
    private static final UUID BLP_SERVICE_UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb");
    private static final UUID BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A35-0000-1000-8000-00805f9b34fb");

    // UUIDs for the Health Thermometer service (HTS)
    private static final UUID HTS_SERVICE_UUID = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb");
    private static final UUID TEMPERATURE_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb");

    // UUIDs for the Heart Rate service (HRS)
    private static final UUID HRS_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    private static final UUID HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb");

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
            Timber.i("discovered services");

            // Request a new connection priority
            peripheral.requestConnectionPriority(CONNECTION_PRIORITY_HIGH);

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

            // Turn on notification for Health Thermometer Service
            if(peripheral.getService(HTS_SERVICE_UUID) != null) {
                peripheral.setNotify(peripheral.getCharacteristic(HTS_SERVICE_UUID, TEMPERATURE_MEASUREMENT_CHARACTERISTIC_UUID), true);
            }

            // Turn on notification for Heart Rate  Service
            if(peripheral.getService(HRS_SERVICE_UUID) != null) {
                peripheral.setNotify(peripheral.getCharacteristic(HRS_SERVICE_UUID, HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID), true);
            }
        }

        @Override
        public void onNotificationStateUpdate(BluetoothPeripheral peripheral, BluetoothGattCharacteristic characteristic, int status) {
            if( status == GATT_SUCCESS) {
                if(peripheral.isNotifying(characteristic)) {
                    Timber.i("SUCCESS: Notify set to 'on' for %s", characteristic.getUuid());
                } else {
                    Timber.i("SUCCESS: Notify set to 'off' for %s", characteristic.getUuid());
                }
            } else {
                Timber.e("ERROR: Changing notification state failed for %s", characteristic.getUuid());
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothPeripheral peripheral, byte[] value, BluetoothGattCharacteristic characteristic, int status) {
            if( status == GATT_SUCCESS) {
                Timber.i("SUCCESS: Writing <%s> to <%s>", bytes2String(value), characteristic.getUuid().toString());
            } else {
                Timber.i("ERROR: Failed writing <%s> to <%s>", bytes2String(value), characteristic.getUuid().toString());
            }
        }

        @Override
        public void onCharacteristicUpdate(BluetoothPeripheral peripheral, byte[] value, BluetoothGattCharacteristic characteristic, int status) {
            if(status != GATT_SUCCESS) return;
            UUID characteristicUUID = characteristic.getUuid();
            BluetoothBytesParser parser = new BluetoothBytesParser(value);

            if (characteristicUUID.equals(BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID)) {
                BloodPressureMeasurement measurement = new BloodPressureMeasurement(value);
                Intent intent = new Intent("BluetoothMeasurement");
                intent.putExtra("BloodPressure", measurement);
                context.sendBroadcast(intent);
                Timber.d("%s", measurement);
            }
            else if(characteristicUUID.equals(TEMPERATURE_MEASUREMENT_CHARACTERISTIC_UUID)) {
                TemperatureMeasurement measurement = new TemperatureMeasurement(value);
                Intent intent = new Intent("TemperatureMeasurement");
                intent.putExtra("Temperature", measurement);
                context.sendBroadcast(intent);
                Timber.d("%s", measurement);
            }
            else if(characteristicUUID.equals(HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID)) {
               HeartRateMeasurement measurement = new HeartRateMeasurement(value);
                Intent intent = new Intent("HeartRateMeasurement");
                intent.putExtra("HeartRate", measurement);
                context.sendBroadcast(intent);
                Timber.d("%s", measurement);
            }
            else if(characteristicUUID.equals(CURRENT_TIME_CHARACTERISTIC_UUID)) {
                Date currentTime = parser.getDateTime();
                Timber.i("Received device time: %s", currentTime);

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
                Timber.i("Received battery level %d%%", batteryLevel);
            }
            else if(characteristicUUID.equals(MANUFACTURER_NAME_CHARACTERISTIC_UUID)) {
                String manufacturer = parser.getStringValue(0);
                Timber.i("Received manufacturer: %s", manufacturer);
            }
            else if(characteristicUUID.equals(MODEL_NUMBER_CHARACTERISTIC_UUID)) {
                String modelNumber = parser.getStringValue(0);
                Timber.i("Received modelnumber: %s", modelNumber);
            }
        }
    };

    // Callback for central
    private final BluetoothCentralCallback bluetoothCentralCallback = new BluetoothCentralCallback() {

        @Override
        public void onConnectedPeripheral(BluetoothPeripheral peripheral) {
            Timber.i("connected to '%s'", peripheral.getName());
        }

        @Override
        public void onConnectionFailed(BluetoothPeripheral peripheral, final int status) {
            Timber.e("connection '%s' failed with status %d", peripheral.getName(), status);
        }

        @Override
        public void onDisconnectedPeripheral(final BluetoothPeripheral peripheral, final int status) {
            Timber.i("disconnected '%s' with status %d", peripheral.getName(), status);

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
            Timber.i("Found peripheral '%s'", peripheral.getName());
            central.stopScan();
            central.connectPeripheral(peripheral, peripheralCallback);
        }

        @Override
        public void onBluetoothAdapterStateChanged(int state) {
            Timber.i("bluetooth adapter changed state to %d", state);
            if(state == BluetoothAdapter.STATE_ON) {
                // Bluetooth is on now, start scanning again
                // Scan for peripherals with a certain service UUIDs
                central.startPairingPopupHack();
                central.scanForPeripheralsWithServices(new UUID[]{BLP_SERVICE_UUID, HTS_SERVICE_UUID, HRS_SERVICE_UUID});
            }
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

        // Create BluetoothCentral
        central = new BluetoothCentral(context, bluetoothCentralCallback, new Handler());

        // Scan for peripherals with a certain service UUIDs
        central.startPairingPopupHack();
        central.scanForPeripheralsWithServices(new UUID[]{BLP_SERVICE_UUID, HTS_SERVICE_UUID, HRS_SERVICE_UUID});
    }
}
