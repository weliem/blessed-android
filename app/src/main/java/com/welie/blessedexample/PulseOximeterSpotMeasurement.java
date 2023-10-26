package com.welie.blessedexample;

import com.welie.blessed.BluetoothBytesParser;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import androidx.annotation.NonNull;

import org.jetbrains.annotations.Nullable;

public class PulseOximeterSpotMeasurement implements Serializable {
    public final int spO2;
    public final int pulseRate;
    public float pulseAmplitudeIndex;
    public final boolean deviceClockSet;
    @Nullable
    public Date timestamp;
    public int measurementStatus;
    public int sensorStatus;

    public PulseOximeterSpotMeasurement(byte[] value) {
        BluetoothBytesParser parser = new BluetoothBytesParser(value);

        int flags = parser.getUInt8();
        boolean timestampPresent = (flags & 0x01) > 0;
        boolean measurementStatusPresent = (flags & 0x02) > 0;
        boolean sensorStatusPresent = (flags & 0x04) > 0;
        boolean pulseAmplitudeIndexPresent = (flags & 0x08) > 0;
        deviceClockSet = (flags & 0x10) == 0;

        // Get SpO2 value
        spO2 = parser.getSFloat().intValue();

        // Get pulse value
        pulseRate = parser.getSFloat().intValue();

        if (timestampPresent) {
            timestamp = parser.getDateTime();
        }

        if (measurementStatusPresent) {
            measurementStatus = parser.getUInt16();
        }

        if (sensorStatusPresent) {
            sensorStatus = parser.getUInt16();
            int reservedByte = parser.getUInt8();
        }

        if (pulseAmplitudeIndexPresent) {
            pulseAmplitudeIndex = parser.getSFloat();
        }
    }

    @NonNull
    @Override
    public String toString() {
        DateFormat df = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss",  Locale.ENGLISH);
        String formattedTimestamp = timestamp != null ? df.format(timestamp) : "null";
        return String.format( Locale.ENGLISH, "SpO2 %d%% HR: %d PAI: %.1f (%s)", spO2, pulseRate, pulseAmplitudeIndex, formattedTimestamp);
    }
}
