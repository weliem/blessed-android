package com.welie.blessedexample;

import androidx.annotation.NonNull;

import com.welie.blessed.BluetoothBytesParser;

import java.io.Serializable;
import java.util.Locale;

import static com.welie.blessed.BluetoothBytesParser.FORMAT_UINT16;
import static com.welie.blessed.BluetoothBytesParser.FORMAT_UINT8;

public class HeartRateMeasurement implements Serializable {

    public Integer pulse;

    public HeartRateMeasurement(byte[] value) {
        BluetoothBytesParser parser = new BluetoothBytesParser(value);

        // Parse the flags
        int flags = parser.getIntValue(FORMAT_UINT8);
        final int unit = flags & 0x01;
        final int sensorContactStatus = (flags & 0x06) >> 1;
        final boolean energyExpenditurePresent = (flags & 0x08) > 0;
        final boolean rrIntervalPresent = (flags & 0x10) > 0;

        // Parse heart rate
        this.pulse = (unit == 0) ? parser.getIntValue(FORMAT_UINT8) : parser.getIntValue(FORMAT_UINT16);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%d", pulse);
    }
}
