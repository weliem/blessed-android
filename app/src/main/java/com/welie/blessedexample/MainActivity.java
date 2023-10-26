package com.welie.blessedexample;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.os.Bundle;
import android.widget.TextView;

import com.welie.blessed.BluetoothCentralManager;
import com.welie.blessed.BluetoothPeripheral;

import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

    private TextView measurementValue;

    private final DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH);

    private final ActivityResultLauncher<String[]> blePermissionRequest = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            new ActivityResultCallback<Map<String, Boolean>>() {
                @Override
                public void onActivityResult(Map<String, Boolean> result) {
                    for(String key : result.keySet()) {
                        Timber.d("%s, %s", key, result);
                    }
                }
            });

    private final ActivityResultLauncher<Intent> enableBleRequest = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        onResume();
                    }
                }
            });

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        measurementValue = (TextView) findViewById(R.id.bloodPressureValue);

        registerReceiver(locationServiceStateReceiver, new IntentFilter((LocationManager.MODE_CHANGED_ACTION)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? Context.RECEIVER_NOT_EXPORTED : 0 ;
            registerReceiver(bloodPressureDataReceiver, new IntentFilter( BluetoothHandler.MEASUREMENT_BLOODPRESSURE ), flags);
            registerReceiver(temperatureDataReceiver, new IntentFilter( BluetoothHandler.MEASUREMENT_TEMPERATURE ), flags);
            registerReceiver(heartRateDataReceiver, new IntentFilter( BluetoothHandler.MEASUREMENT_HEARTRATE ), flags);
            registerReceiver(pulseOxDataReceiver, new IntentFilter( BluetoothHandler.MEASUREMENT_PULSE_OX ), flags);
            registerReceiver(weightDataReceiver, new IntentFilter(BluetoothHandler.MEASUREMENT_WEIGHT), flags);
            registerReceiver(glucoseDataReceiver, new IntentFilter(BluetoothHandler.MEASUREMENT_GLUCOSE), flags);
        } else {
            registerReceiver(bloodPressureDataReceiver, new IntentFilter( BluetoothHandler.MEASUREMENT_BLOODPRESSURE ));
            registerReceiver(temperatureDataReceiver, new IntentFilter( BluetoothHandler.MEASUREMENT_TEMPERATURE ));
            registerReceiver(heartRateDataReceiver, new IntentFilter( BluetoothHandler.MEASUREMENT_HEARTRATE ));
            registerReceiver(pulseOxDataReceiver, new IntentFilter( BluetoothHandler.MEASUREMENT_PULSE_OX ));
            registerReceiver(weightDataReceiver, new IntentFilter(BluetoothHandler.MEASUREMENT_WEIGHT));
            registerReceiver(glucoseDataReceiver, new IntentFilter(BluetoothHandler.MEASUREMENT_GLUCOSE));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (getBluetoothManager().getAdapter() != null) {
            if (!isBluetoothEnabled()) {
                enableBleRequest.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            } else {
                checkPermissions();
            }
        } else {
            Timber.e("This device has no Bluetooth hardware");
        }
    }

    private boolean isBluetoothEnabled() {
        BluetoothAdapter bluetoothAdapter = getBluetoothManager().getAdapter();
        if(bluetoothAdapter == null) return false;

        return bluetoothAdapter.isEnabled();
    }

    private void initBluetoothHandler()
    {
        BluetoothHandler.getInstance(getApplicationContext());
    }

    @NotNull
    private BluetoothManager getBluetoothManager() {
        return Objects.requireNonNull((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE),"cannot get BluetoothManager");
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
        unregisterReceiver(glucoseDataReceiver);
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

            measurementValue.setText(String.format(Locale.ENGLISH, "%s\n\nfrom %s", measurement, peripheral.getName()));
        }
    };

    private final BroadcastReceiver temperatureDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothPeripheral peripheral = getPeripheral(intent.getStringExtra(BluetoothHandler.MEASUREMENT_EXTRA_PERIPHERAL));
            TemperatureMeasurement measurement = (TemperatureMeasurement) intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_TEMPERATURE_EXTRA);
            if (measurement == null) return;

            measurementValue.setText(String.format(Locale.ENGLISH, "%s\n\nfrom %s", measurement, peripheral.getName()));
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
            PulseOximeterContinuousMeasurement measurement = (PulseOximeterContinuousMeasurement) intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_PULSE_OX_EXTRA_CONTINUOUS);
            if (measurement != null) {
                measurementValue.setText(String.format(Locale.ENGLISH, "SpO2 %d%%,  Pulse %d bpm\n\nfrom %s", measurement.getSpO2(), measurement.getPulseRate(), peripheral.getName()));
            }
            PulseOximeterSpotMeasurement spotMeasurement = (PulseOximeterSpotMeasurement) intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_PULSE_OX_EXTRA_SPOT);
            if (spotMeasurement != null) {
                measurementValue.setText(String.format(Locale.ENGLISH, "%s\n\nfrom %s", spotMeasurement, peripheral.getName()));
            }
        }
    };

    private final BroadcastReceiver weightDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothPeripheral peripheral = getPeripheral(intent.getStringExtra(BluetoothHandler.MEASUREMENT_EXTRA_PERIPHERAL));
            WeightMeasurement measurement = (WeightMeasurement) intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_WEIGHT_EXTRA);
            if (measurement != null) {
                measurementValue.setText(String.format(Locale.ENGLISH, "%s\n\nfrom %s", measurement, peripheral.getName()));
            }
        }
    };

    private final BroadcastReceiver glucoseDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothPeripheral peripheral = getPeripheral(intent.getStringExtra(BluetoothHandler.MEASUREMENT_EXTRA_PERIPHERAL));
            GlucoseMeasurement measurement = (GlucoseMeasurement) intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_GLUCOSE_EXTRA);
            if (measurement != null) {
                measurementValue.setText(String.format(Locale.ENGLISH, "%s\n\nfrom %s", measurement, peripheral.getName()));
            }
        }
    };

    private BluetoothPeripheral getPeripheral(String peripheralAddress) {
        BluetoothCentralManager central = BluetoothHandler.getInstance(getApplicationContext()).central;
        return central.getPeripheral(peripheralAddress);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] missingPermissions = getMissingPermissions(getRequiredPermissions());
            if (missingPermissions.length > 0) {
                blePermissionRequest.launch(missingPermissions);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetSdkVersion >= Build.VERSION_CODES.S) {
            return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetSdkVersion >= Build.VERSION_CODES.Q) {
            return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        } else return new String[]{Manifest.permission.ACCESS_COARSE_LOCATION};
    }

    private void permissionsGranted() {
        // Check if Location services are on because they are required to make scanning work for SDK < 31
        int targetSdkVersion = getApplicationInfo().targetSdkVersion;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && targetSdkVersion < Build.VERSION_CODES.S) {
            if (checkLocationServices()) {
                initBluetoothHandler();
            }
        } else {
            initBluetoothHandler();
        }
    }

    private boolean areLocationServicesEnabled() {
        LocationManager locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            Timber.e("could not get location manager");
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return locationManager.isLocationEnabled();
        } else {
            boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            return isGpsEnabled || isNetworkEnabled;
        }
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
}
