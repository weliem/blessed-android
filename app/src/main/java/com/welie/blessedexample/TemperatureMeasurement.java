package com.welie.blessedexample;

import com.welie.blessed.BluetoothBytesParser;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import androidx.annotation.NonNull;
import org.jetbrains.annotations.Nullable;

public class TemperatureMeasurement implements Serializable {
    public TemperatureUnit unit;
    public float temperatureValue;
    @Nullable
    public Date timestamp;
    @Nullable
    public TemperatureType type;

    public TemperatureMeasurement(byte[] byteArray) {
        BluetoothBytesParser parser = new BluetoothBytesParser(byteArray);

        // Parse flag byte
        final int flags = parser.getUInt8();
        unit = ((flags & 0x01) > 0 ? TemperatureUnit.Fahrenheit : TemperatureUnit.Celsius);
        final boolean timestampPresent = (flags & 0x02) > 0;
        final boolean typePresent = (flags & 0x04) > 0;

        // Get temperature value
        temperatureValue = parser.getFloat();

        // Get timestamp
        if(timestampPresent) {
            timestamp = parser.getDateTime();
        }

        // Get temperature type
        if(typePresent) {
            int typeValue = parser.getUInt8();
            type = TemperatureType.fromValue(typeValue);
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
        return String.format(Locale.ENGLISH,"%.1f %s (%s), at (%s)", temperatureValue, unit == TemperatureUnit.Celsius ? "celsius" : "fahrenheit", type, formattedTimestamp);
    }
}
