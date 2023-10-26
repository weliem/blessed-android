package com.welie.blessedexample;

import androidx.annotation.NonNull;

import com.welie.blessed.BluetoothBytesParser;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

public class BloodPressureMeasurement implements Serializable {

    @Nullable
    public Integer userID;
    public Float systolic;
    public Float diastolic;
    public Float meanArterialPressure;
    @Nullable
    public Date timestamp;
    public boolean isMMHG;
    @Nullable
    public Float pulseRate;

    public BloodPressureMeasurement(byte[] value) {
        BluetoothBytesParser parser = new BluetoothBytesParser(value);

        // Parse the flags
        int flags = parser.getUInt8();
        isMMHG = !((flags & 0x01) > 0);
        boolean timestampPresent = (flags & 0x02) > 0;
        boolean pulseRatePresent = (flags & 0x04) > 0;
        boolean userIdPresent = (flags & 0x08) > 0;
        boolean measurementStatusPresent = (flags & 0x10) > 0;

        // Get systolic, diastolic and mean arterial pressure
        systolic = parser.getSFloat();
        diastolic = parser.getSFloat();
        meanArterialPressure = parser.getSFloat();

        // Read timestamp
        if (timestampPresent) {
            timestamp = parser.getDateTime();
        }

        // Read pulse rate
        if (pulseRatePresent) {
            pulseRate = parser.getSFloat();
        }

        // Read userId
        if (userIdPresent) {
            userID = parser.getUInt8();
        }
    }


    @NonNull
    @Override
    public String toString() {
        DateFormat df = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH);
        String formattedTimestamp;
        if(timestamp != null) {
            formattedTimestamp = df.format(timestamp);
        } else {
            formattedTimestamp = "null";
        }
        return String.format(Locale.ENGLISH,"%.0f/%.0f %s, MAP %.0f, %.0f bpm, user %d at (%s)", systolic, diastolic, isMMHG ? "mmHg" : "kPa", meanArterialPressure, pulseRate, userID, formattedTimestamp);
    }
}
