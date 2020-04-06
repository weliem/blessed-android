package com.welie.blessedexample;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;

/**
 * Created by jamorham on 06/12/2016.
 */

public class GlucoseMeasurement implements Serializable {

    public static final double MMOLL_TO_MGDL = 18.0182;
    public ByteBuffer data = null;

    private int flags;
    public int sequence;
    public int year;
    public int month;
    public int day;
    public int hour;
    public int minute;
    public int second;
    public int offset;
    public float kgl;
    public float mmol;
    public double mgdl;
    public long time;
    public int sampleType;
    public int sampleLocation;

    public GlucoseMeasurement(byte[] packet) {
        if (packet.length >= 14) {
            data = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);

            flags = data.get(0);
            final boolean timeOffsetPresent = (flags & 0x01) > 0;
            final boolean typeAndLocationPresent = (flags & 0x02) > 0;
            final boolean concentrationUnitKgL = (flags & 0x04) == 0;
            final boolean sensorStatusAnnunciationPresent = (flags & 0x08) > 0;
            final boolean contextInfoFollows = (flags & 0x10) > 0;

            sequence = data.getShort(1);
            year = data.getShort(3);
            month = data.get(5);
            day = data.get(6);
            hour = data.get(7);
            minute = data.get(8);
            second = data.get(9);

            int ptr = 10;
            if (timeOffsetPresent) {
                offset = data.getShort(ptr); // TODO check timeoffset bit!
                ptr += 2;
            }

            if (concentrationUnitKgL) {
                kgl = getSfloat16(data.get(ptr), data.get(ptr + 1));
                mgdl = kgl * 100000;
            } else {
                mmol = getSfloat16(data.get(ptr), data.get(ptr + 1));
                mgdl = mmol * 1000 * MMOLL_TO_MGDL;
            }
            ptr += 2;

            if (typeAndLocationPresent) {
                final int typeAndLocation = data.get(ptr);
                sampleLocation = (typeAndLocation & 0xF0) >> 4;
                sampleType = (typeAndLocation & 0x0F);
                ptr++;
            }

            final Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, day, hour, minute, second);
            time = calendar.getTimeInMillis();
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "Glucose data: mg/dl: " + mgdl + "  mmol/l: " + mmol + "  kg/l: " + kgl
                + "  seq:" + sequence + " sampleType: " + sampleType + "  sampleLocation: " + sampleLocation + "  time: " + hour + ":" + minute + ":" + second
                + "  " + day + "-" + month + "-" + year + " timeoffset: " + offset + " timestamp: " + time;
    }

    public String toStringFormatted() {
        return "Glucose data:\nmg/dl: " + mgdl + "\nmmol/l: " + mmol + "\nkg/l: " + kgl
                + "\nseq:" + sequence + "\nsampleType: " + sampleType + "\nsampleLocation: " + sampleLocation + "\ntime: " + hour + ":" + minute + ":" + second
                + "  " + day + "-" + month + "-" + year + "\ntimeoffset: " + offset + "\ntimestamp: " + time;
    }

    private float getSfloat16(byte b0, byte b1) {
        int mantissa = unsignedToSigned(unsignedByteToInt(b0)
                + ((unsignedByteToInt(b1) & 0x0F) << 8), 12);
        int exponent = unsignedToSigned(unsignedByteToInt(b1) >> 4, 4);
        return (float) (mantissa * Math.pow(10, exponent));
    }

    private int unsignedByteToInt(byte b) {
        return b & 0xFF;
    }

    private int unsignedToSigned(int unsigned, int size) {
        if ((unsigned & (1 << size - 1)) != 0) {
            unsigned = -1 * ((1 << size - 1) - (unsigned & ((1 << size - 1) - 1)));
        }
        return unsigned;
    }
}