package com.welie.blessedexample;

import androidx.annotation.NonNull;

import com.welie.blessed.BluetoothBytesParser;

import java.io.Serializable;
import java.util.Locale;

public class HeartRateMeasurement implements Serializable {

    public final Integer pulse;

    public HeartRateMeasurement(byte[] value) {
        BluetoothBytesParser parser = new BluetoothBytesParser(value);

        // Parse the flags
        int flags = parser.getUInt8();
        final int unit = flags & 0x01;
        final int sensorContactStatus = (flags & 0x06) >> 1;
        final boolean energyExpenditurePresent = (flags & 0x08) > 0;
        final boolean rrIntervalPresent = (flags & 0x10) > 0;

        // Parse heart rate
        this.pulse = (unit == 0) ? parser.getUInt8() : parser.getUInt16();
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%d", pulse);
    }
}
