package com.welie.blessedexample;

import com.welie.blessed.BluetoothBytesParser;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;


import static com.welie.blessed.BluetoothBytesParser.*;

public class PulseOximeterSpotMeasurement implements Serializable {
    private final int spO2;
    private final int pulseRate;
    private float pulseAmplitudeIndex;
    private final boolean deviceClockSet;
    private Date timestamp;
    private int measurementStatus;
    private int sensorStatus;

    public PulseOximeterSpotMeasurement(byte[] value) {
        BluetoothBytesParser parser = new BluetoothBytesParser(value);

        int flags = parser.getIntValue(FORMAT_UINT8);
        boolean timestampPresent = (flags & 0x01) > 0;
        boolean measurementStatusPresent = (flags & 0x02) > 0;
        boolean sensorStatusPresent = (flags & 0x04) > 0;
        boolean pulseAmplitudeIndexPresent = (flags & 0x08) > 0;
        deviceClockSet = (flags & 0x10) == 0;

        // Get SpO2 value
        spO2 = parser.getFloatValue(FORMAT_SFLOAT).intValue();

        // Get pulse value
        pulseRate = parser.getFloatValue(FORMAT_SFLOAT).intValue();

        if (timestampPresent) {
            Date timestamp = parser.getDateTime();
            setTimestamp(timestamp);
        } else {
            setTimestamp(Calendar.getInstance().getTime());
        }

        if (measurementStatusPresent) {
            measurementStatus = parser.getIntValue(FORMAT_UINT16);
        }

        if (sensorStatusPresent) {
            sensorStatus = parser.getIntValue(FORMAT_UINT16);
            int reservedByte = parser.getIntValue(FORMAT_UINT8);
        }

        if (pulseAmplitudeIndexPresent) {
            pulseAmplitudeIndex = parser.getFloatValue(FORMAT_SFLOAT);
        }
    }

    public int getSpO2() {
        return spO2;
    }

    public int getPulseRate() {
        return pulseRate;
    }

    public float getPulseAmplitudeIndex() {
        return pulseAmplitudeIndex;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isDeviceClockSet() {
        return deviceClockSet;
    }

    public int getMeasurementStatus() {
        return measurementStatus;
    }

    public int getSensorStatus() {
        return sensorStatus;
    }

    @Override
    public String toString() {
        DateFormat df = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        String formattedTimestamp = df.format(timestamp);
        return String.format("SpO2 %d%% HR: %d PAI: %.1f (%s)", spO2, pulseRate, pulseAmplitudeIndex, formattedTimestamp);
    }
}
