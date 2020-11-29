package com.welie.blessedexample;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.welie.blessed.BluetoothCentral;
import com.welie.blessed.BluetoothPeripheral;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

    private TextView measurementValue;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int ACCESS_LOCATION_REQUEST = 2;
    private final DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        measurementValue = (TextView) findViewById(R.id.bloodPressureValue);

        registerReceiver(locationServiceStateReceiver, new IntentFilter((LocationManager.MODE_CHANGED_ACTION)));
        registerReceiver(bloodPressureDataReceiver, new IntentFilter( BluetoothHandler.MEASUREMENT_BLOODPRESSURE ));
        registerReceiver(temperatureDataReceiver, new IntentFilter( BluetoothHandler.MEASUREMENT_TEMPERATURE ));
        registerReceiver(heartRateDataReceiver, new IntentFilter( BluetoothHandler.MEASUREMENT_HEARTRATE ));
        registerReceiver(pulseOxDataReceiver, new IntentFilter( BluetoothHandler.MEASUREMENT_PULSE_OX ));
        registerReceiver(weightDataReceiver, new IntentFilter(BluetoothHandler.MEASUREMENT_WEIGHT));
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!isBluetoothEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            checkPermissions();
        }
    }

    private boolean isBluetoothEnabled() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter == null) return false;

        return bluetoothAdapter.isEnabled();
    }

    private void initBluetoothHandler()
    {
        BluetoothHandler.getInstance(getApplicationContext());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(locationServiceStateReceiver);
        unregisterReceiver(bloodPressureDataReceiver);
        unregisterReceiver(temperatureDataReceiver);
        unregisterReceiver(heartRateDataReceiver);
        unregisterReceiver(pulseOxDataReceiver);
        unregisterReceiver(weightDataReceiver);
    }

    private final BroadcastReceiver locationServiceStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equals(LocationManager.MODE_CHANGED_ACTION)) {
                boolean isEnabled = areLocationServicesEnabled();
                Timber.i("Location service state changed to: %s", isEnabled ? "on" : "off");
                checkPermissions();
            }
        }
    };

    private final BroadcastReceiver bloodPressureDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothPeripheral peripheral = getPeripheral(intent.getStringExtra(BluetoothHandler.MEASUREMENT_EXTRA_PERIPHERAL));
            BloodPressureMeasurement measurement = (BloodPressureMeasurement) intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_BLOODPRESSURE_EXTRA);
            if (measurement == null) return;

            measurementValue.setText(String.format(Locale.ENGLISH, "%.0f/%.0f %s, %.0f bpm\n%s\n\nfrom %s", measurement.systolic, measurement.diastolic, measurement.isMMHG ? "mmHg" : "kpa", measurement.pulseRate, dateFormat.format(measurement.timestamp), peripheral.getName()));
        }
    };

    private final BroadcastReceiver temperatureDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothPeripheral peripheral = getPeripheral(intent.getStringExtra(BluetoothHandler.MEASUREMENT_EXTRA_PERIPHERAL));
            TemperatureMeasurement measurement = (TemperatureMeasurement) intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_TEMPERATURE_EXTRA);
            if (measurement == null) return;

            measurementValue.setText(String.format(Locale.ENGLISH, "%.1f %s (%s)\n%s\n\nfrom %s", measurement.temperatureValue, measurement.unit == TemperatureUnit.Celsius ? "celsius" : "fahrenheit", measurement.type, dateFormat.format(measurement.timestamp), peripheral.getName()));
        }
    };

    private final BroadcastReceiver heartRateDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            HeartRateMeasurement measurement = (HeartRateMeasurement) intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_HEARTRATE_EXTRA);
            if (measurement == null) return;

            measurementValue.setText(String.format(Locale.ENGLISH, "%d bpm", measurement.pulse));
        }
    };

    private final BroadcastReceiver pulseOxDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothPeripheral peripheral = getPeripheral(intent.getStringExtra(BluetoothHandler.MEASUREMENT_EXTRA_PERIPHERAL));
            PulseOximeterContinuousMeasurement measurement = (PulseOximeterContinuousMeasurement) intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_PULSE_OX_EXTRA_CONTINOUS);
            if (measurement != null) {
                measurementValue.setText(String.format(Locale.ENGLISH, "SpO2 %d%%,  Pulse %d bpm\n\nfrom %s", measurement.getSpO2(), measurement.getPulseRate(), peripheral.getName()));
            }
            PulseOximeterSpotMeasurement spotMeasurement = (PulseOximeterSpotMeasurement) intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_PULSE_OX_EXTRA_SPOT);
            if (spotMeasurement != null) {
                measurementValue.setText(String.format(Locale.ENGLISH, "SpO2 %d%%,  Pulse %d bpm\n%s\n\nfrom %s", spotMeasurement.getSpO2(), spotMeasurement.getPulseRate(), dateFormat.format(spotMeasurement.getTimestamp()), peripheral.getName()));
            }
        }
    };

    private final BroadcastReceiver weightDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothPeripheral peripheral = getPeripheral(intent.getStringExtra(BluetoothHandler.MEASUREMENT_EXTRA_PERIPHERAL));
            WeightMeasurement measurement = (WeightMeasurement) intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_WEIGHT_EXTRA);
            if (measurement != null) {
                measurementValue.setText(String.format(Locale.ENGLISH, "%.1f %s\n%s\n\nfrom %s", measurement.weight, measurement.unit.toString(), dateFormat.format(measurement.timestamp), peripheral.getName()));
            }
        }
    };

    private BluetoothPeripheral getPeripheral(String peripheralAddress) {
        BluetoothCentral central = BluetoothHandler.getInstance(getApplicationContext()).central;
        return central.getPeripheral(peripheralAddress);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] missingPermissions = getMissingPermissions(getRequiredPermissions());
            if (missingPermissions.length > 0) {
                requestPermissions(missingPermissions, ACCESS_LOCATION_REQUEST);
            } else {
                permissionsGranted();
            }
        }
    }

    private String[] getMissingPermissions(String[] requiredPermissions) {
        List<String> missingPermissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String requiredPermission : requiredPermissions) {
                if (getApplicationContext().checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(requiredPermission);
                }
            }
        }
        return missingPermissions.toArray(new String[0]);
    }

    private String[] getRequiredPermissions() {
        int targetSdkVersion = getApplicationInfo().targetSdkVersion;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetSdkVersion >= Build.VERSION_CODES.Q)
            return new String[] {Manifest.permission.ACCESS_FINE_LOCATION};
        else return new String[] {Manifest.permission.ACCESS_COARSE_LOCATION};
    }

    private void permissionsGranted() {
        // Check if Location services are on because they are required to make scanning work
        if (checkLocationServices()) {
            initBluetoothHandler();
        }
    }

    private boolean areLocationServicesEnabled() {
        LocationManager locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            Timber.e("could not get location manager");
            return false;
        }

        boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        return isGpsEnabled || isNetworkEnabled;
    }

    private boolean checkLocationServices() {
        if (!areLocationServicesEnabled()) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Location services are not enabled")
                    .setMessage("Scanning for Bluetooth peripherals requires locations services to be enabled.") // Want to enable?
                    .setPositiveButton("Enable", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.cancel();
                            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // if this button is clicked, just close
                            // the dialog box and do nothing
                            dialog.cancel();
                        }
                    })
                    .create()
                    .show();
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Check if all permission were granted
        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            permissionsGranted();
        } else {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Location permission is required for scanning Bluetooth peripherals")
                    .setMessage("Please grant permissions")
                    .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.cancel();
                            checkPermissions();
                        }
                    })
                    .create()
                    .show();
        }
    }
}
