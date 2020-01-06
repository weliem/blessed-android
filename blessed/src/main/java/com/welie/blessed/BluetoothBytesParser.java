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

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

public class BluetoothBytesParser {

    private int offset = 0;
    private byte[] mValue;
    private ByteOrder byteOrder;

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

    //public static final int FORMAT_UINT64 = 0x18;

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
     * Create a BluetoothBytesParser that does not contain a byte array and sets the byteOrder to LITTLE_ENDIAN.
     */
    public BluetoothBytesParser() {
        this(null, LITTLE_ENDIAN);
    }

    /**
     * Create a BluetoothBytesParser that does not contain a byte array and sets the byteOrder.
     */
    public BluetoothBytesParser(ByteOrder byteOrder) {
        this(null, byteOrder);
    }

    /**
     * Create a BluetoothBytesParser and set the byte array and sets the byteOrder to LITTLE_ENDIAN.
     *
     * @param value byte array
     */
    public BluetoothBytesParser(byte[] value) {
        this(value, 0, LITTLE_ENDIAN);
    }

    /**
     * Create a BluetoothBytesParser and set the byte array and byteOrder.
     *
     * @param value     byte array
     * @param byteOrder the byte order to use (either LITTLE_ENDIAN or BIG_ENDIAN)
     */
    public BluetoothBytesParser(byte[] value, ByteOrder byteOrder) {
        this(value, 0, byteOrder);
    }

    /**
     * Create a BluetoothBytesParser, set the byte array, the internal offset and the byteOrder to LITTLE_ENDIAN.
     *
     * @param value  the byte array
     * @param offset the offset from which parsing will start
     */
    public BluetoothBytesParser(byte[] value, int offset) {
        this(value, offset, LITTLE_ENDIAN);
    }

    /**
     * Create a BluetoothBytesParser, set the byte array, the internal offset and the byteOrder.
     *
     * @param value     the byte array
     * @param offset    the offset from which parsing will start
     * @param byteOrder the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     */
    public BluetoothBytesParser(byte[] value, int offset, ByteOrder byteOrder) {
        mValue = value;
        this.offset = offset;
        this.byteOrder = byteOrder;
    }

    /**
     * Return an Integer value of the specified type. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte(s) value
     * @return An Integer object or null in case the byte array was not valid
     */
    public Integer getIntValue(int formatType) {
        Integer result = getIntValue(formatType, offset, byteOrder);
        offset += getTypeLen(formatType);
        return result;
    }

    /**
     * Return an Integer value of the specified type and specified byte order. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType the format type used to interpret the byte(s) value
     * @return an Integer object or null in case the byte array was not valid
     */
    public Integer getIntValue(int formatType, ByteOrder byteOrder) {
        Integer result = getIntValue(formatType, offset, byteOrder);
        offset += getTypeLen(formatType);
        return result;
    }

    /**
     * Return a Long value. This operation will automatically advance the internal offset to the next position.
     *
     * @return an Long object or null in case the byte array was not valid
     */
    public long getLongValue() {
        return getLongValue(byteOrder);
    }

    /**
     * Return a Long value using the specified byte order. This operation will automatically advance the internal offset to the next position.
     *
     * @return an Long object or null in case the byte array was not valid
     */
    public long getLongValue(ByteOrder byteOrder) {
        long result = getLongValue(offset, byteOrder);
        offset += 8;
        return result;
    }

    /**
     * Return a Long value using the specified byte order and offset position. This operation will not advance the internal offset to the next position.
     *
     * @return an Long object or null in case the byte array was not valid
     */
    public long getLongValue(int offset, ByteOrder byteOrder) {
        if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
            long value = 0x00FF & mValue[offset + 7];
            for (int i = 6; i >= 0; i--) {
                value <<= 8;
                value += 0x00FF & mValue[i + offset];
            }
            return value;
        } else {
            long value = 0x00FF & mValue[offset];
            for (int i = 1; i < 8; i++) {
                value <<= 8;
                value += 0x00FF & mValue[i + offset];
            }
            return value;
        }
    }

    /**
     * Return an Integer value of the specified type. This operation will not advance the internal offset to the next position.
     *
     * <p>The formatType parameter determines how the byte array
     * is to be interpreted. For example, settting formatType to
     * {@link #FORMAT_UINT16} specifies that the first two bytes of the
     * byte array at the given offset are interpreted to generate the
     * return value.
     *
     * @param formatType The format type used to interpret the byte array.
     * @param offset     Offset at which the integer value can be found.
     * @param byteOrder  the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @return Cached value of the byte array or null of offset exceeds value size.
     */
    public Integer getIntValue(int formatType, int offset, ByteOrder byteOrder) {
        if ((offset + getTypeLen(formatType)) > mValue.length) return null;

        switch (formatType) {
            case FORMAT_UINT8:
                return unsignedByteToInt(mValue[offset]);

            case FORMAT_UINT16:
                if (byteOrder == LITTLE_ENDIAN)
                    return unsignedBytesToInt(mValue[offset], mValue[offset + 1]);
                else
                    return unsignedBytesToInt(mValue[offset + 1], mValue[offset]);

            case FORMAT_UINT32:
                if (byteOrder == LITTLE_ENDIAN)
                    return unsignedBytesToInt(mValue[offset], mValue[offset + 1],
                            mValue[offset + 2], mValue[offset + 3]);
                else
                    return unsignedBytesToInt(mValue[offset + 3], mValue[offset + 2],
                            mValue[offset + 1], mValue[offset]);

            case FORMAT_SINT8:
                return unsignedToSigned(unsignedByteToInt(mValue[offset]), 8);

            case FORMAT_SINT16:
                if (byteOrder == LITTLE_ENDIAN)
                    return unsignedToSigned(unsignedBytesToInt(mValue[offset],
                            mValue[offset + 1]), 16);
                else
                    return unsignedToSigned(unsignedBytesToInt(mValue[offset + 1],
                            mValue[offset]), 16);

            case FORMAT_SINT32:
                if (byteOrder == LITTLE_ENDIAN)
                    return unsignedToSigned(unsignedBytesToInt(mValue[offset],
                            mValue[offset + 1], mValue[offset + 2], mValue[offset + 3]), 32);
                else
                    return unsignedToSigned(unsignedBytesToInt(mValue[offset + 3],
                            mValue[offset + 2], mValue[offset + 1], mValue[offset]), 32);
        }

        return null;
    }

    /**
     * Return a float value of the specified format. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte array
     * @return The float value at the position of the internal offset
     */
    public Float getFloatValue(int formatType) {
        Float result = getFloatValue(formatType, offset, byteOrder);
        offset += getTypeLen(formatType);
        return result;
    }

    /**
     * Return a float value of the specified format and byte order. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte array
     * @param byteOrder  the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @return The float value at the position of the internal offset
     */
    public Float getFloatValue(int formatType, ByteOrder byteOrder) {
        Float result = getFloatValue(formatType, offset, byteOrder);
        offset += getTypeLen(formatType);
        return result;
    }

    /**
     * Return a float value of the specified format, offset and byte order. This operation will not advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte array
     * @param byteOrder  the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @return The float value at the position of the internal offset
     */
    public Float getFloatValue(int formatType, int offset, ByteOrder byteOrder) {
        if ((offset + getTypeLen(formatType)) > mValue.length) return null;

        switch (formatType) {
            case FORMAT_SFLOAT:
                if (byteOrder == LITTLE_ENDIAN)
                    return bytesToFloat(mValue[offset], mValue[offset + 1]);
                else
                    return bytesToFloat(mValue[offset + 1], mValue[offset]);

            case FORMAT_FLOAT:
                if (byteOrder == LITTLE_ENDIAN)
                    return bytesToFloat(mValue[offset], mValue[offset + 1],
                            mValue[offset + 2], mValue[offset + 3]);
                else
                    return bytesToFloat(mValue[offset + 3], mValue[offset + 2],
                            mValue[offset + 1], mValue[offset]);
        }

        return null;
    }

    /**
     * Return a String from this byte array. This operation will not advance the internal offset to the next position.
     *
     * @return String value representated by the byte array
     */
    public String getStringValue() {
        return getStringValue(offset);
    }

    /**
     * Return a String from this byte array. This operation will not advance the internal offset to the next position.
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
        while (j > 0 && (strBytes[j - 1] == 0 || strBytes[j - 1] == 0x20)) j--;

        // Convert to string
        return new String(strBytes, 0, j, StandardCharsets.ISO_8859_1);
    }

    /**
     * Return a the date represented by the byte array.
     * <p>
     * The byte array must conform to the DateTime specification (year, month, day, hour, min, sec)
     *
     * @return the Date represented by the byte array
     */
    public Date getDateTime() {
        Date result = getDateTime(offset);
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
        // DateTime is always in little endian
        int year = getIntValue(FORMAT_UINT16, offset, LITTLE_ENDIAN);
        offset += getTypeLen(FORMAT_UINT16);
        int month = getIntValue(FORMAT_UINT8, offset, LITTLE_ENDIAN);
        offset += getTypeLen(FORMAT_UINT8);
        int day = getIntValue(FORMAT_UINT8, offset, LITTLE_ENDIAN);
        offset += getTypeLen(FORMAT_UINT8);
        int hour = getIntValue(FORMAT_UINT8, offset, LITTLE_ENDIAN);
        offset += getTypeLen(FORMAT_UINT8);
        int min = getIntValue(FORMAT_UINT8, offset, LITTLE_ENDIAN);
        offset += getTypeLen(FORMAT_UINT8);
        int sec = getIntValue(FORMAT_UINT8, offset, LITTLE_ENDIAN);

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
     * @param value      New value for this byte array
     * @param formatType Integer format type used to transform the value parameter
     * @param offset     Offset at which the value should be placed
     * @return true if the locally stored value has been set
     */
    public boolean setIntValue(int value, int formatType, int offset) {
        prepareArray(offset + getTypeLen(formatType));

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
                if (byteOrder == LITTLE_ENDIAN) {
                    mValue[offset++] = (byte) (value & 0xFF);
                    mValue[offset] = (byte) ((value >> 8) & 0xFF);
                } else {
                    mValue[offset++] = (byte) ((value >> 8) & 0xFF);
                    mValue[offset] = (byte) (value & 0xFF);
                }
                break;

            case FORMAT_SINT32:
                value = intToSignedBits(value, 32);
                // Fall-through intended
            case FORMAT_UINT32:
                if (byteOrder == LITTLE_ENDIAN) {
                    mValue[offset++] = (byte) (value & 0xFF);
                    mValue[offset++] = (byte) ((value >> 8) & 0xFF);
                    mValue[offset++] = (byte) ((value >> 16) & 0xFF);
                    mValue[offset] = (byte) ((value >> 24) & 0xFF);
                } else {
                    mValue[offset++] = (byte) ((value >> 24) & 0xFF);
                    mValue[offset++] = (byte) ((value >> 16) & 0xFF);
                    mValue[offset++] = (byte) ((value >> 8) & 0xFF);
                    mValue[offset] = (byte) (value & 0xFF);
                }
                break;

            default:
                return false;
        }
        return true;
    }

    /**
     * Set byte array to an Integer with specified format.
     *
     * @param value      New value for this byte array
     * @param formatType Integer format type used to transform the value parameter
     * @return true if the locally stored value has been set
     */
    public boolean setIntValue(int value, int formatType) {
        boolean result = setIntValue(value, formatType, offset);
        if (result) {
            offset += getTypeLen(formatType);
        }
        return result;
    }

    /**
     * Set byte array to a long
     *
     * @param value New long value for this byte array
     * @return true if the locally stored value has been set
     */
    public boolean setLong(long value) {
        return setLong(value, offset);
    }

    /**
     * Set byte array to a long
     *
     * @param value  New long value for this byte array
     * @param offset Offset at which the value should be placed
     * @return true if the locally stored value has been set
     */
    public boolean setLong(long value, int offset) {
        prepareArray(offset + 8);

        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            for (int i = 7; i >= 0; i--) {
                mValue[i + offset] = (byte) (value & 0xFF);
                value >>= 8;
            }
        } else {
            for (int i = 0; i < 8; i++) {
                mValue[i + offset] = (byte) (value & 0xFF);
                value >>= 8;
            }
        }

        return true;
    }

    /**
     * Set byte array to a float of the specified type.
     *
     * @param mantissa   Mantissa for this float value
     * @param exponent   exponent value for this float value
     * @param formatType Float format type used to transform the value parameter
     * @param offset     Offset at which the value should be placed
     * @return true if the locally stored value has been set
     */
    public boolean setFloatValue(int mantissa, int exponent, int formatType, int offset) {
        prepareArray(offset + getTypeLen(formatType));

        switch (formatType) {
            case FORMAT_SFLOAT:
                mantissa = intToSignedBits(mantissa, 12);
                exponent = intToSignedBits(exponent, 4);
                if (byteOrder == LITTLE_ENDIAN) {
                    mValue[offset++] = (byte) (mantissa & 0xFF);
                    mValue[offset] = (byte) ((mantissa >> 8) & 0x0F);
                    mValue[offset] += (byte) ((exponent & 0x0F) << 4);
                } else {
                    mValue[offset] = (byte) ((mantissa >> 8) & 0x0F);
                    mValue[offset++] += (byte) ((exponent & 0x0F) << 4);
                    mValue[offset] = (byte) (mantissa & 0xFF);
                }
                break;

            case FORMAT_FLOAT:
                mantissa = intToSignedBits(mantissa, 24);
                exponent = intToSignedBits(exponent, 8);
                if (byteOrder == LITTLE_ENDIAN) {
                    mValue[offset++] = (byte) (mantissa & 0xFF);
                    mValue[offset++] = (byte) ((mantissa >> 8) & 0xFF);
                    mValue[offset++] = (byte) ((mantissa >> 16) & 0xFF);
                    mValue[offset] += (byte) (exponent & 0xFF);
                } else {
                    mValue[offset++] += (byte) (exponent & 0xFF);
                    mValue[offset++] = (byte) ((mantissa >> 16) & 0xFF);
                    mValue[offset++] = (byte) ((mantissa >> 8) & 0xFF);
                    mValue[offset] = (byte) (mantissa & 0xFF);
                }
                break;

            default:
                return false;
        }

        return true;
    }

    /**
     * Create byte[] value from Float usingg a given precision, i.e. number of digits after the comma
     *
     * @param value     Float value to create byte[] from
     * @param precision number of digits after the comma to use
     * @return true if the locally stored value has been set
     */
    public boolean setFloatValue(float value, int precision) {
        float mantissa = (float) (value * Math.pow(10, precision));
        return setFloatValue((int) mantissa, -precision, FORMAT_FLOAT, 0);
    }

    /**
     * Set byte array to a string at current offset
     *
     * @param value String to be added to byte array
     * @return true if the locally stored value has been set
     */
    public boolean setString(String value) {
        if (value != null) {
            setString(value, offset);
            offset += value.getBytes().length;
            return true;
        }
        return false;
    }

    /**
     * Set byte array to a string at specified offset position
     *
     * @param value  String to be added to byte array
     * @param offset the offset to place the string at
     * @return true if the locally stored value has been set
     */
    public boolean setString(String value, int offset) {
        if (value != null) {
            prepareArray(offset + value.length());
            byte[] valueBytes = value.getBytes();
            System.arraycopy(valueBytes, 0, mValue, offset, valueBytes.length);
            return true;
        }
        return false;
    }


    /**
     * Set the locally stored value of this byte array.
     *
     * @param value New value for this byte array
     */
    public void setValue(byte[] value) {
        mValue = value;
    }

    /**
     * Sets the byte array to represent the current date in CurrentTime format
     *
     * @param calendar the calendar object representing the current date
     * @return flase if the calendar object was null, otherwise true
     */
    public boolean setCurrentTime(Calendar calendar) {
        if (calendar == null) return false;
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
     * Sets the byte array to represent the current date in CurrentTime format
     *
     * @param calendar the calendar object representing the current date
     * @return flase if the calendar object was null, otherwise true
     */
    public boolean setDateTime(Calendar calendar) {
        if (calendar == null) return false;
        mValue = new byte[7];
        mValue[0] = (byte) calendar.get(Calendar.YEAR);
        mValue[1] = (byte) (calendar.get(Calendar.YEAR) >> 8);
        mValue[2] = (byte) (calendar.get(Calendar.MONTH) + 1);
        mValue[3] = (byte) calendar.get(Calendar.DATE);
        mValue[4] = (byte) calendar.get(Calendar.HOUR_OF_DAY);
        mValue[5] = (byte) calendar.get(Calendar.MINUTE);
        mValue[6] = (byte) calendar.get(Calendar.SECOND);
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
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * Merge multiple arrays intro one array
     *
     * @param arrays Arrays to merge
     * @return Merge array
     */
    public static byte[] mergeArrays(byte[]... arrays) {
        int size = 0;
        for (byte[] array : arrays) {
            size += array.length;
        }

        byte[] merged = new byte[size];
        int index = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, merged, index, array.length);
            index += array.length;
        }

        return merged;
    }

    /**
     * Get the value of the internal offset
     */
    public int getOffset() {
        return offset;
    }

    /**
     * Set the value of the internal offset
     */
    public void setOffset(int offset) {
        this.offset = offset;
    }

    /**
     * Get the set byte order
     */
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    private void prepareArray(int neededLength) {
        if (mValue == null) mValue = new byte[neededLength];
        if (neededLength > mValue.length) {
            byte[] largerByteArray = new byte[neededLength];
            System.arraycopy(mValue, 0, largerByteArray, 0, mValue.length);
            mValue = largerByteArray;
        }
    }

    @Override
    public String toString() {
        return bytes2String(mValue);
    }
}
