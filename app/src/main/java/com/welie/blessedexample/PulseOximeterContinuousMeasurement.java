package com.welie.blessedexample;


import com.welie.blessed.BluetoothBytesParser;

import java.io.Serializable;

import static com.welie.blessed.BluetoothBytesParser.*;

public class PulseOximeterContinuousMeasurement implements Serializable {

    private final int SpO2;

    private final int pulseRate;

    private int SpO2Fast;

    private int pulseRateFast;

    private int SpO2Slow;

    private int pulseRateSlow;

    private float pulseAmplitudeIndex;

    private int measurementStatus;

    private int sensorStatus;

    public PulseOximeterContinuousMeasurement(byte[] value) {
        BluetoothBytesParser parser = new BluetoothBytesParser(value);

        int flags = parser.getUInt8();
        boolean spo2FastPresent = (flags & 0x01) > 0;
        boolean spo2SlowPresent = (flags & 0x02) > 0;
        boolean measurementStatusPresent = (flags & 0x04) > 0;
        boolean sensorStatusPresent = (flags & 0x08) > 0;
        boolean pulseAmplitudeIndexPresent = (flags & 0x10) > 0;

        SpO2 = parser.getSFloat().intValue();
        pulseRate = parser.getSFloat().intValue();

        if (spo2FastPresent) {
            SpO2Fast = parser.getSFloat().intValue();
            pulseRateFast = parser.getSFloat().intValue();
        }

        if (spo2SlowPresent) {
            SpO2Slow = parser.getSFloat().intValue();
            pulseRateSlow = parser.getSFloat().intValue();
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

    public int getSpO2() {
        return SpO2;
    }

    public int getPulseRate() {
        return pulseRate;
    }

    public int getSpO2Fast() {
        return SpO2Fast;
    }

    public int getPulseRateFast() {
        return pulseRateFast;
    }

    public int getSpO2Slow() {
        return SpO2Slow;
    }

    public int getPulseRateSlow() {
        return pulseRateSlow;
    }

    public float getPulseAmplitudeIndex() {
        return pulseAmplitudeIndex;
    }

    public int getMeasurementStatus() {
        return measurementStatus;
    }

    public int getSensorStatus() {
        return sensorStatus;
    }

    @Override
    public String toString() {
        if (SpO2 == 2047 || pulseRate == 2047) {
            return "invalid measurement";
        }
        return String.format("SpO2 %d%%, Pulse %d bpm, PAI %.1f", SpO2, pulseRate, pulseAmplitudeIndex);
    }
}
