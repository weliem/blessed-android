/*
 *   Copyright (c) 2019 Martijn van Welie
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *   SOFTWARE.
 *
 */

package com.welie.blessed;

import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class BluetoothBytesParser {

    private int offset = 0;
    private byte[] mValue;

    /**
     * Characteristic value format type uint8
     */
    public static final int FORMAT_UINT8 = 0x11;

    /**
     * Characteristic value format type uint16
     */
    public static final int FORMAT_UINT16 = 0x12;

    /**
     * Characteristic value format type uint32
     */
    public static final int FORMAT_UINT32 = 0x14;

    /**
     * Characteristic value format type sint8
     */
    public static final int FORMAT_SINT8 = 0x21;

    /**
     * Characteristic value format type sint16
     */
    public static final int FORMAT_SINT16 = 0x22;

    /**
     * Characteristic value format type sint32
     */
    public static final int FORMAT_SINT32 = 0x24;

    /**
     * Characteristic value format type sfloat (16-bit float)
     */
    public static final int FORMAT_SFLOAT = 0x32;

    /**
     * Characteristic value format type float (32-bit float)
     */
    public static final int FORMAT_FLOAT = 0x34;

    /**
     * Create a BluetoothBytesParser that does not contain a byte array
     */
    public BluetoothBytesParser() {
        mValue = null;
        offset = 0;
    }

    /**
     * Create a BluetoothBytesParser and set the byte array
     *
     * @param value byte array
     */
    public BluetoothBytesParser(byte[] value) {
        mValue = value;
        offset = 0;
    }

    /**
     * Create a BluetoothBytesParser, set the byte array and set the internal offset.
     *
     * @param value the byte array
     * @param offset the offset from which parsing will start
     */
    public BluetoothBytesParser(byte[] value, int offset) {
        mValue = value;
        this.offset = offset;
    }

    /**
     * Return an Integer value of the specified type. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType the format type used to interpret the byte(s) value
     * @return an Integer object or null in case the byte array was not valid
     */
    public Integer getIntValue(int formatType) {
        Integer result = getIntValue(formatType, offset);
        offset += getTypeLen(formatType);
        return result;
    }

    /**
     * Return the stored value of this byte array.
     *
     * <p>The formatType parameter determines how the byte array
     * is to be interpreted. For example, settting formatType to
     * {@link #FORMAT_UINT16} specifies that the first two bytes of the
     * byte array at the given offset are interpreted to generate the
     * return value.
     *
     * @param formatType The format type used to interpret the byte array.
     * @param offset Offset at which the integer value can be found.
     * @return Cached value of the byte array or null of offset exceeds value size.
     */
    public Integer getIntValue(int formatType, int offset) {
        if ((offset + getTypeLen(formatType)) > mValue.length) return null;

        switch (formatType) {
            case FORMAT_UINT8:
                return unsignedByteToInt(mValue[offset]);

            case FORMAT_UINT16:
                return unsignedBytesToInt(mValue[offset], mValue[offset + 1]);

            case FORMAT_UINT32:
                return unsignedBytesToInt(mValue[offset], mValue[offset + 1],
                        mValue[offset + 2], mValue[offset + 3]);
            case FORMAT_SINT8:
                return unsignedToSigned(unsignedByteToInt(mValue[offset]), 8);

            case FORMAT_SINT16:
                return unsignedToSigned(unsignedBytesToInt(mValue[offset],
                        mValue[offset + 1]), 16);

            case FORMAT_SINT32:
                return unsignedToSigned(unsignedBytesToInt(mValue[offset],
                        mValue[offset + 1], mValue[offset + 2], mValue[offset + 3]), 32);
        }

        return null;
    }

    /**
     * Return a float value of the specified format. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType
     * @return
     */
    public Float getFloatValue(int formatType) {
        Float result = getFloatValue(formatType, offset);
        offset += getTypeLen(formatType);
        return result;
    }

    /**
     * Return the stored value in this byte array
     *
     * @param formatType The format type used to interpret the byte array
     * @param offset Offset at which the float value can be found.
     * @return Cached value at a given offset or null if the requested offset
     * exceeds the value size.
     */
    public Float getFloatValue(int formatType, int offset) {
        if ((offset + getTypeLen(formatType)) > mValue.length) return null;

        switch (formatType) {
            case FORMAT_SFLOAT:
                return bytesToFloat(mValue[offset], mValue[offset + 1]);

            case FORMAT_FLOAT:
                return bytesToFloat(mValue[offset], mValue[offset + 1],
                        mValue[offset + 2], mValue[offset + 3]);
        }

        return null;
    }

    /**
     * Return the stored value in this byte array.
     *
     * @param offset Offset at which the string value can be found.
     * @return String value representated by the byte array
     */
    public String getStringValue(int offset) {
        // Check if there are enough bytes to parse
        if (mValue == null || offset > mValue.length) return null;

        // Copy all bytes
        byte[] strBytes = new byte[mValue.length - offset];
        for (int i = 0; i != (mValue.length - offset); ++i) strBytes[i] = mValue[offset + i];

        // Get rid of trailing zero/space bytes
        int j = strBytes.length;
        while(j>0 && (strBytes[j-1]== 0 || strBytes[j-1]==0x20)) j--;

        // Convert to string
        return new String(strBytes, 0,j, StandardCharsets.ISO_8859_1);
    }

    /**
     * Return a the date represented by the byte array.
     *
     * The byte array must conform to the DateTime specification (year, month, day, hour, min, sec)
     *
     * @return the Date represented by the byte array
     */
    public Date getDateTime() {
        Date result= getDateTime(offset);
        offset += 7;
        return result;
    }

    /**
     * Get Date from characteristic with offset
     *
     * @param offset Offset of value
     * @return Parsed date from value
     */
    public Date getDateTime(int offset) {

            int year = getIntValue(FORMAT_UINT16, offset);
            offset += getTypeLen(FORMAT_UINT16);
            int month = getIntValue(FORMAT_UINT8, offset);
            offset += getTypeLen(FORMAT_UINT8);
            int day = getIntValue(FORMAT_UINT8, offset);
            offset += getTypeLen(FORMAT_UINT8);
            int hour = getIntValue(FORMAT_UINT8, offset);
            offset += getTypeLen(FORMAT_UINT8);
            int min = getIntValue(FORMAT_UINT8, offset);
            offset += getTypeLen(FORMAT_UINT8);
            int sec = getIntValue(FORMAT_UINT8, offset);

            GregorianCalendar calendar = new GregorianCalendar(year, month - 1, day, hour, min, sec);
            return calendar.getTime();
    }

    /**
     * Get the byte array
     *
     * @return the complete byte array
     */
    public byte[] getValue() {
        return mValue;
    }

    /**
     * Set the locally stored value of this byte array
     *
     * @param value New value for this byte array
     * @param formatType Integer format type used to transform the value parameter
     * @param offset Offset at which the value should be placed
     * @return true if the locally stored value has been set
     */
    public boolean setIntValue(int value, int formatType, int offset) {
        int len = offset + getTypeLen(formatType);
        if (mValue == null) mValue = new byte[len];
        if (len > mValue.length) return false;

        switch (formatType) {
            case FORMAT_SINT8:
                value = intToSignedBits(value, 8);
                // Fall-through intended
            case FORMAT_UINT8:
                mValue[offset] = (byte) (value & 0xFF);
                break;

            case FORMAT_SINT16:
                value = intToSignedBits(value, 16);
                // Fall-through intended
            case FORMAT_UINT16:
                mValue[offset++] = (byte) (value & 0xFF);
                mValue[offset] = (byte) ((value >> 8) & 0xFF);
                break;

            case FORMAT_SINT32:
                value = intToSignedBits(value, 32);
                // Fall-through intended
            case FORMAT_UINT32:
                mValue[offset++] = (byte) (value & 0xFF);
                mValue[offset++] = (byte) ((value >> 8) & 0xFF);
                mValue[offset++] = (byte) ((value >> 16) & 0xFF);
                mValue[offset] = (byte) ((value >> 24) & 0xFF);
                break;

            default:
                return false;
        }
        return true;
    }

    /**
     * Set the locally stored value of this byte array.
     *
     * @param mantissa Mantissa for this float value
     * @param exponent exponent value for this float value
     * @param formatType Float format type used to transform the value parameter
     * @param offset Offset at which the value should be placed
     * @return true if the locally stored value has been set
     */
    public boolean setFloatValue(int mantissa, int exponent, int formatType, int offset) {
        int len = offset + getTypeLen(formatType);
        if (mValue == null) mValue = new byte[len];
        if (len > mValue.length) return false;

        switch (formatType) {
            case FORMAT_SFLOAT:
                mantissa = intToSignedBits(mantissa, 12);
                exponent = intToSignedBits(exponent, 4);
                mValue[offset++] = (byte) (mantissa & 0xFF);
                mValue[offset] = (byte) ((mantissa >> 8) & 0x0F);
                mValue[offset] += (byte) ((exponent & 0x0F) << 4);
                break;

            case FORMAT_FLOAT:
                mantissa = intToSignedBits(mantissa, 24);
                exponent = intToSignedBits(exponent, 8);
                mValue[offset++] = (byte) (mantissa & 0xFF);
                mValue[offset++] = (byte) ((mantissa >> 8) & 0xFF);
                mValue[offset++] = (byte) ((mantissa >> 16) & 0xFF);
                mValue[offset] += (byte) (exponent & 0xFF);
                break;

            default:
                return false;
        }

        return true;
    }

    /**
     * Set the locally stored value of this byte array.
     *
     * @param value New value for this byte array
     * @return true if the locally stored value has been set
     */
    public boolean setValue(String value) {
        mValue = value.getBytes();
        return true;
    }

    /**
     * Sets the byte array to represent the current date in CurrentTime format
     *
     * @param calendar the calendar object representing the current date
     * @return flase if the calendar object was null, otherwise true
     */
    public boolean setCurrentTime(Calendar calendar) {
        if(calendar == null) return false;
        mValue = new byte[10];
        mValue[0] = (byte) calendar.get(Calendar.YEAR);
        mValue[1] = (byte) (calendar.get(Calendar.YEAR) >> 8);
        mValue[2] = (byte) (calendar.get(Calendar.MONTH) + 1);
        mValue[3] = (byte) calendar.get(Calendar.DATE);
        mValue[4] = (byte) calendar.get(Calendar.HOUR_OF_DAY);
        mValue[5] = (byte) calendar.get(Calendar.MINUTE);
        mValue[6] = (byte) calendar.get(Calendar.SECOND);
        mValue[7] = (byte) ((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1);
        mValue[8] = (byte) (calendar.get(Calendar.MILLISECOND) * 256 / 1000);
        mValue[9] = 1;
        return true;
    }

    /**
     * Returns the size of a give value type.
     */
    private int getTypeLen(int formatType) {
        return formatType & 0xF;
    }

    /**
     * Convert a signed byte to an unsigned int.
     */
    private int unsignedByteToInt(byte b) {
        return b & 0xFF;
    }

    /**
     * Convert signed bytes to a 16-bit unsigned int.
     */
    private int unsignedBytesToInt(byte b0, byte b1) {
        return (unsignedByteToInt(b0) + (unsignedByteToInt(b1) << 8));
    }

    /**
     * Convert signed bytes to a 32-bit unsigned int.
     */
    private int unsignedBytesToInt(byte b0, byte b1, byte b2, byte b3) {
        return (unsignedByteToInt(b0) + (unsignedByteToInt(b1) << 8))
                + (unsignedByteToInt(b2) << 16) + (unsignedByteToInt(b3) << 24);
    }

    /**
     * Convert signed bytes to a 16-bit short float value.
     */
    private float bytesToFloat(byte b0, byte b1) {
        int mantissa = unsignedToSigned(unsignedByteToInt(b0)
                + ((unsignedByteToInt(b1) & 0x0F) << 8), 12);
        int exponent = unsignedToSigned(unsignedByteToInt(b1) >> 4, 4);
        return (float) (mantissa * Math.pow(10, exponent));
    }

    /**
     * Convert signed bytes to a 32-bit short float value.
     */
    private float bytesToFloat(byte b0, byte b1, byte b2, byte b3) {
        int mantissa = unsignedToSigned(unsignedByteToInt(b0)
                + (unsignedByteToInt(b1) << 8)
                + (unsignedByteToInt(b2) << 16), 24);
        return (float) (mantissa * Math.pow(10, b3));
    }

    /**
     * Convert an unsigned integer value to a two's-complement encoded
     * signed value.
     */
    private int unsignedToSigned(int unsigned, int size) {
        if ((unsigned & (1 << size - 1)) != 0) {
            unsigned = -1 * ((1 << size - 1) - (unsigned & ((1 << size - 1) - 1)));
        }
        return unsigned;
    }

    /**
     * Convert an integer into the signed bits of a given length.
     */
    private int intToSignedBits(int i, int size) {
        if (i < 0) {
            i = (1 << size - 1) + (i & ((1 << size - 1) - 1));
        }
        return i;
    }

    /**
     * Convert a byte array to a string
     *
     * @param bytes the bytes to convert
     * @return String object that represents the byte array
     */
    public static String bytes2String(final byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for(byte b : bytes){
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return bytes2String(mValue);
    }
}
