package com.welie.blessedexample;

import com.welie.blessed.BluetoothBytesParser;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import androidx.annotation.NonNull;
import org.jetbrains.annotations.Nullable;

public class WeightMeasurement implements Serializable {
    public final double weight;
    public final WeightUnit unit;
    @Nullable
    public Date timestamp;
    @Nullable
    public Integer userID;
    @Nullable
    public Integer BMI;
    @Nullable
    public Integer height;

    public WeightMeasurement(byte[] byteArray) {
        BluetoothBytesParser parser = new BluetoothBytesParser(byteArray);

        // Parse flag byte
        final int flags = parser.getUInt8();
        unit = ((flags & 0x01) > 0) ? WeightUnit.Pounds : WeightUnit.Kilograms;
        final boolean timestampPresent = (flags & 0x02) > 0;
        final boolean userIDPresent = (flags & 0x04) > 0;
        final boolean bmiAndHeightPresent = (flags & 0x08) > 0;

        // Get weight value
        double weightMultiplier = (unit == WeightUnit.Kilograms) ? 0.005 : 0.01;
        weight = parser.getUInt16() * weightMultiplier;

        // Get timestamp if present
        if (timestampPresent) {
            timestamp = parser.getDateTime();
        }

        // Get user ID if present
        if (userIDPresent) {
            userID = parser.getUInt8();
        }

        // Get BMI and Height if present
        if (bmiAndHeightPresent) {
            BMI = parser.getUInt16();
            height = parser.getUInt16();
        }
    }

    @NonNull
    @Override
    public String toString() {
        DateFormat df = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH);
        String formattedTimestamp = timestamp != null ? df.format(timestamp) : "null";
        return String.format(Locale.ENGLISH, "%.1f %s, user %d, BMI %d, height %d at (%s)", weight, unit == WeightUnit.Kilograms ? "kg" : "lb", userID, BMI, height, formattedTimestamp);
    }
}
