/*
 *   Copyright (c) 2021 Martijn van Welie
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Objects;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

public class BluetoothBytesParser {

    private static final String INVALID_OFFSET = "invalid offset";
    private static final String UNSUPPORTED_FORMAT_TYPE = "unsupported format type";
    private static final String OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO = "offset must be greater or equal to zero";
    private int internalOffset;

    @NotNull
    private byte[] mValue;

    @NotNull
    private final ByteOrder internalByteOrder;

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
     * Create a BluetoothBytesParser containing an empty byte array and sets the byteOrder to LITTLE_ENDIAN.
     *
     */
    public BluetoothBytesParser() {
        this(new byte[0], LITTLE_ENDIAN);
    }

    /**
     * Create a BluetoothBytesParser that contains an empty byte array and sets the byteOrder.
     *
     * @param byteOrder the byte order to use (either LITTLE_ENDIAN or BIG_ENDIAN)
     * @throws NullPointerException if the byteOrder is null
     */
    public BluetoothBytesParser(@NotNull final ByteOrder byteOrder) {
        this(new byte[0], byteOrder);
    }

    /**
     * Create a BluetoothBytesParser containing the byte array and sets the byteOrder to LITTLE_ENDIAN.
     *
     * @param value the byte array
     * @throws NullPointerException if the value is null
     */
    public BluetoothBytesParser(@NotNull final byte[] value) {
        this(value, 0, LITTLE_ENDIAN);
    }

    /**
     * Create a BluetoothBytesParser containing the byte array and byteOrder.
     *
     * @param value     byte array
     * @param byteOrder the byte order to use (either LITTLE_ENDIAN or BIG_ENDIAN)
     * @throws IllegalArgumentException if the byteOrder is not LITTLE_ENDIAN or BIG_ENDIAN
     * @throws NullPointerException if the value or byteOrder are null
     */
    public BluetoothBytesParser(@NotNull final byte[] value, @NotNull final ByteOrder byteOrder) {
        this(value, 0, byteOrder);
    }

    /**
     * Create a BluetoothBytesParser containing the byte array, the offset and the byteOrder to LITTLE_ENDIAN.
     *
     * @param value  the byte array
     * @param offset the offset from which parsing will start
     * @throws IllegalArgumentException if the offset is negative
     * @throws NullPointerException     if the value is null
     */
    public BluetoothBytesParser(@NotNull final byte[] value, final int offset) {
        this(value, offset, LITTLE_ENDIAN);
    }

    /**
     * Create a BluetoothBytesParser containing the byte array, the offset and the byteOrder.
     *
     * @param value     the byte array
     * @param offset    the offset from which parsing will start
     * @param byteOrder the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @throws IllegalArgumentException if the offset is negative or the byteOrder is not LITTLE_ENDIAN or BIG_ENDIAN
     * @throws NullPointerException if the value or byteOrder are null
     */
    public BluetoothBytesParser(@NotNull final byte[] value, final int offset, @NotNull final ByteOrder byteOrder) {
        if (offset < 0) throw new IllegalArgumentException(OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO);
        Objects.requireNonNull(byteOrder);
        if (!(byteOrder == LITTLE_ENDIAN || byteOrder == BIG_ENDIAN)) throw new IllegalArgumentException("unsupported ByteOrder value");

        mValue = Objects.requireNonNull(value);
        this.internalOffset = offset;
        this.internalByteOrder = byteOrder;
    }

    /**
     * Return a Long value. This operation will automatically advance the internal offset to the next position.
     *
     * @return a Long value
     * @throws IllegalArgumentException if there are not enough bytes for a long value
     */
    @NotNull
    public Long getLongValue() {
        return getLongValue(internalByteOrder);
    }

    /**
     * Return a Long value using the specified byte order. This operation will automatically advance the internal offset to the next position.
     *
     * @param byteOrder  the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @return a Long value
     * @throws IllegalArgumentException if there are not enough bytes for a long value
     * @throws NullPointerException if the value of byteOrder is null
     */
    @NotNull
    public Long getLongValue(@NotNull final ByteOrder byteOrder) {
        Objects.requireNonNull(byteOrder);
        long result = getLongValue(internalOffset, byteOrder);
        internalOffset += 8;
        return result;
    }

    /**
     * Return a Long value using the specified byte order and offset position. This operation will not advance the internal offset to the next position.
     *
     * @param offset     Offset at which the Long value can be found.
     * @param byteOrder  the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @return a Long value
     * @throws IllegalArgumentException if the offset is not valid for getting a long value
     * @throws NullPointerException if the value of byteOrder is null
     */
    @NotNull
    public Long getLongValue(final int offset, @NotNull final ByteOrder byteOrder) {
        Objects.requireNonNull(byteOrder);
        if (offset < 0) throw new IllegalArgumentException(OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO);
        if ((offset + 8) > mValue.length) throw new IllegalArgumentException(INVALID_OFFSET);

        if (byteOrder == LITTLE_ENDIAN) {
            long value = 0x00FF & mValue[offset + 7];
            for (int i = 6; i >= 0; i--) {
                value <<= 8;
                value += 0x00FF & mValue[i + offset];
            }
            return value;
        } else if (byteOrder == BIG_ENDIAN) {
            long value = 0x00FF & mValue[offset];
            for (int i = 1; i < 8; i++) {
                value <<= 8;
                value += 0x00FF & mValue[i + offset];
            }
            return value;
        }
        throw new IllegalArgumentException("invalid byte order");
    }

    /**
     * Return an Integer value of the specified type. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte(s) value
     * @return An Integer value
     * @throws IllegalArgumentException if there are not enough bytes for an Integer value
     */
    public Integer getIntValue(final int formatType) {
        Integer result = getIntValue(formatType, internalOffset, internalByteOrder);
        internalOffset += getTypeLen(formatType);
        return result;
    }

    /**
     * Return an Integer value of the specified type and specified byte order. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType the format type used to interpret the byte(s) value
     * @param byteOrder  the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @return an Integer value
     * @throws IllegalArgumentException if there are not enough bytes for an Integer value
     * @throws NullPointerException if the value of byteOrder is null
     */
    public Integer getIntValue(final int formatType, @NotNull final ByteOrder byteOrder) {
        Objects.requireNonNull(byteOrder);
        Integer result = getIntValue(formatType, internalOffset, byteOrder);
        internalOffset += getTypeLen(formatType);
        return result;
    }

    /**
     * Return an Integer value of the specified type. This operation will not advance the internal offset to the next position.
     *
     * The formatType parameter determines how the byte array
     * is to be interpreted. For example, setting formatType to
     * {@link #FORMAT_UINT16} specifies that the first two bytes of the
     * byte array at the given offset are interpreted to generate the
     * return value.
     *
     * @param formatType The format type used to interpret the byte array.
     * @param offset     Offset at which the integer value can be found.
     * @param byteOrder  the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @return an Integer value
     * @throws IllegalArgumentException if there are not enough bytes for an Integer value
     * @throws NullPointerException if the value of byteOrder is null
     */
    @NotNull
    public Integer getIntValue(final int formatType, final int offset, final ByteOrder byteOrder) {
        Objects.requireNonNull(byteOrder);
        if (offset < 0) throw new IllegalArgumentException(OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO);
        if ((offset + getTypeLen(formatType)) > mValue.length) throw new IllegalArgumentException(INVALID_OFFSET);

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

        throw new IllegalArgumentException(UNSUPPORTED_FORMAT_TYPE);
    }

    /**
     * Return a float value of the specified format. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte array
     * @return The float value at the position of the internal offset and set ByteOrder
     * @throws IllegalArgumentException if there are not enough bytes for an Float value
     */
    @NotNull
    public Float getFloatValue(final int formatType) {
        Float result = getFloatValue(formatType, internalOffset, internalByteOrder);
        internalOffset += getTypeLen(formatType);
        return result;
    }

    /**
     * Return a float value of the specified format and byte order. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte array
     * @param byteOrder  the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @return The float value at the position of the internal offset
     * @throws IllegalArgumentException if there are not enough bytes for an Float value
     * @throws NullPointerException if the value of byteOrder is null
     */
    @NotNull
    public Float getFloatValue(final int formatType, @NotNull final ByteOrder byteOrder) {
        Objects.requireNonNull(byteOrder);
        Float result = getFloatValue(formatType, internalOffset, byteOrder);
        internalOffset += getTypeLen(formatType);
        return result;
    }

    /**
     * Return a Float value of the specified format, offset and byte order. This operation will not advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte array (FORMAT_SFLOAT or FORMAT_FLOAT)
     * @param byteOrder  the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @return The float value at the position of the internal offset
     * @throws IllegalArgumentException if there are not enough bytes for an Float value or if the formatType is not FORMAT_SFLOAT or FORMAT_FLOAT
     * @throws NullPointerException if the value of byteOrder is null
     */
    @NotNull
    public Float getFloatValue(final int formatType, final int offset, @NotNull final ByteOrder byteOrder) {
        Objects.requireNonNull(byteOrder);
        if (offset < 0) throw new IllegalArgumentException(OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO);
        if ((offset + getTypeLen(formatType)) > mValue.length) throw new IllegalArgumentException(INVALID_OFFSET);

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

        throw new IllegalArgumentException(UNSUPPORTED_FORMAT_TYPE);
    }

    /**
     * Return a String from this byte array. This operation will not advance the internal offset to the next position.
     *
     * @return String value represented by the byte array
     */
    @NotNull
    public String getStringValue() {
        return getStringValue(internalOffset);
    }

    /**
     * Return a String from this byte array. This operation will not advance the internal offset to the next position.
     *
     * @param offset Offset at which the string value can be found.
     * @return String value represented by the byte array
     * @throws IllegalArgumentException if the offset is invalid
     */
    @NotNull
    public String getStringValue(final int offset) {
        if (offset < 0) throw new IllegalArgumentException(OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO);
        if (mValue == null || offset > mValue.length) throw new IllegalArgumentException(INVALID_OFFSET);

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
     *
     * The byte array must conform to the DateTime specification (year, month, day, hour, min, sec)
     *
     * @return the Date represented by the byte array
     * @throws IllegalArgumentException if there are not enough bytes to get a Date
     */
    @NotNull
    public Date getDateTime() {
        Date result = getDateTime(internalOffset);
        internalOffset += 7;
        return result;
    }

    /**
     * Get Date from characteristic with offset
     *
     * @param offset Offset of value
     * @return Parsed date from value
     * @throws IllegalArgumentException if there are not enough bytes to get a Date
     */
    @NotNull
    public Date getDateTime(final int offset) {
        if (offset < 0) throw new IllegalArgumentException(OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO);
        if ((offset + 7) > mValue.length) throw new IllegalArgumentException(INVALID_OFFSET);

        // DateTime is always in little endian
        int newOffset = offset;
        int year = getIntValue(FORMAT_UINT16, newOffset, LITTLE_ENDIAN);
        newOffset += getTypeLen(FORMAT_UINT16);
        int month = getIntValue(FORMAT_UINT8, newOffset, LITTLE_ENDIAN);
        newOffset += getTypeLen(FORMAT_UINT8);
        int day = getIntValue(FORMAT_UINT8, newOffset, LITTLE_ENDIAN);
        newOffset += getTypeLen(FORMAT_UINT8);
        int hour = getIntValue(FORMAT_UINT8, newOffset, LITTLE_ENDIAN);
        newOffset += getTypeLen(FORMAT_UINT8);
        int min = getIntValue(FORMAT_UINT8, newOffset, LITTLE_ENDIAN);
        newOffset += getTypeLen(FORMAT_UINT8);
        int sec = getIntValue(FORMAT_UINT8, newOffset, LITTLE_ENDIAN);

        GregorianCalendar calendar = new GregorianCalendar(year, month - 1, day, hour, min, sec);
        return calendar.getTime();
    }

    /**
     * Get the byte array
     *
     * @return the complete byte array
     */
    @NotNull
    public byte[] getValue() {
        return mValue;
    }

    /**
     * Read bytes and return the ByteArray of the length passed.  This will increment the internal offset
     *
     * @return the byte array
     * @throws IllegalArgumentException if length is longer than the remaining bytes counting from the internal offset
     */
    @NotNull
    public byte[] getByteArray(final int length) {
        byte[] array = Arrays.copyOfRange(mValue, internalOffset, internalOffset + length);
        internalOffset += length;
        return array;
    }

    /**
     * Set the locally stored value of this byte array
     *
     * @param value      New value for this byte array
     * @param formatType Integer format type used to transform the value parameter
     * @param offset     Offset at which the value should be placed
     * @throws IllegalArgumentException if the formatType is invalid for an Integer
     */
    public void setIntValue(final int value, final int formatType, final int offset) {
        if (offset < 0) throw new IllegalArgumentException(OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO);
        prepareArray(offset + getTypeLen(formatType));

        int newValue = value;
        int newOffset = offset;

        switch (formatType) {
            case FORMAT_SINT8:
                newValue = intToSignedBits(newValue, 8);
                // Fall-through intended
            case FORMAT_UINT8:
                mValue[newOffset] = (byte) (newValue & 0xFF);
                break;

            case FORMAT_SINT16:
                newValue = intToSignedBits(newValue, 16);
                // Fall-through intended
            case FORMAT_UINT16:
                if (internalByteOrder == LITTLE_ENDIAN) {
                    mValue[newOffset++] = (byte) (newValue & 0xFF);
                    mValue[newOffset] = (byte) ((newValue >> 8) & 0xFF);
                } else {
                    mValue[newOffset++] = (byte) ((newValue >> 8) & 0xFF);
                    mValue[newOffset] = (byte) (newValue & 0xFF);
                }
                break;

            case FORMAT_SINT32:
                newValue = intToSignedBits(newValue, 32);
                // Fall-through intended
            case FORMAT_UINT32:
                if (internalByteOrder == LITTLE_ENDIAN) {
                    mValue[newOffset++] = (byte) (newValue & 0xFF);
                    mValue[newOffset++] = (byte) ((newValue >> 8) & 0xFF);
                    mValue[newOffset++] = (byte) ((newValue >> 16) & 0xFF);
                    mValue[newOffset] = (byte) ((newValue >> 24) & 0xFF);
                } else {
                    mValue[newOffset++] = (byte) ((newValue >> 24) & 0xFF);
                    mValue[newOffset++] = (byte) ((newValue >> 16) & 0xFF);
                    mValue[newOffset++] = (byte) ((newValue >> 8) & 0xFF);
                    mValue[newOffset] = (byte) (newValue & 0xFF);
                }
                break;

            default:
                throw new IllegalArgumentException(UNSUPPORTED_FORMAT_TYPE);
        }
    }

    /**
     * Set byte array to an Integer with specified format. This will increment the internal offset
     *
     * @param value      New value for this byte array
     * @param formatType Integer format type used to transform the value parameter
     * @throws IllegalArgumentException if the formatType is invalid for an Integer
     */
    public void setIntValue(final int value, final int formatType) {
        setIntValue(value, formatType, internalOffset);
        internalOffset += getTypeLen(formatType);
    }

    /**
     * Set byte array to a long. This will increment the internal offset
     *
     * @param value New long value for this byte array
     */
    public void setLong(final long value) {
        setLong(value, internalOffset);
        internalOffset += 8;
    }

    /**
     * Set byte array to a long
     *
     * @param value  New long value for this byte array
     * @param offset Offset at which the value should be placed
     * @throws IllegalArgumentException if the offset is invalid
     */
    public void setLong(final long value, final int offset) {
        if (offset < 0) throw new IllegalArgumentException(OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO);
        prepareArray(offset + 8);

        long newValue = value;
        if (internalByteOrder == ByteOrder.BIG_ENDIAN) {
            for (int i = 7; i >= 0; i--) {
                mValue[i + offset] = (byte) (newValue & 0xFF);
                newValue >>= 8;
            }
        } else {
            for (int i = 0; i < 8; i++) {
                mValue[i + offset] = (byte) (newValue & 0xFF);
                newValue >>= 8;
            }
        }
    }

    /**
     * Set byte array to a float of the specified type.
     *
     * @param mantissa   Mantissa for this float value
     * @param exponent   exponent value for this float value
     * @param formatType Float format type used to transform the value parameter
     * @param offset     Offset at which the value should be placed
     * @throws IllegalArgumentException if the offset is invalid or formatType is invalid for a Float
     */
    public void setFloatValue(final int mantissa, final int exponent, final int formatType, final int offset) {
        if (offset < 0) throw new IllegalArgumentException(OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO);
        prepareArray(offset + getTypeLen(formatType));

        int newMantissa = mantissa;
        int newExponent = exponent;
        int newOffset = offset;
        switch (formatType) {
            case FORMAT_SFLOAT:
                newMantissa = intToSignedBits(newMantissa, 12);
                newExponent = intToSignedBits(newExponent, 4);
                if (internalByteOrder == LITTLE_ENDIAN) {
                    mValue[newOffset++] = (byte) (newMantissa & 0xFF);
                    mValue[newOffset] = (byte) ((newMantissa >> 8) & 0x0F);
                    mValue[newOffset] += (byte) ((newExponent & 0x0F) << 4);
                } else {
                    mValue[newOffset] = (byte) ((newMantissa >> 8) & 0x0F);
                    mValue[newOffset++] += (byte) ((newExponent & 0x0F) << 4);
                    mValue[newOffset] = (byte) (mantissa & 0xFF);
                }
                break;

            case FORMAT_FLOAT:
                newMantissa = intToSignedBits(newMantissa, 24);
                newExponent = intToSignedBits(newExponent, 8);
                if (internalByteOrder == LITTLE_ENDIAN) {
                    mValue[newOffset++] = (byte) (newMantissa & 0xFF);
                    mValue[newOffset++] = (byte) ((newMantissa >> 8) & 0xFF);
                    mValue[newOffset++] = (byte) ((newMantissa >> 16) & 0xFF);
                    mValue[newOffset] += (byte) (newExponent & 0xFF);
                } else {
                    mValue[newOffset++] += (byte) (newExponent & 0xFF);
                    mValue[newOffset++] = (byte) ((newMantissa >> 16) & 0xFF);
                    mValue[newOffset++] = (byte) ((newMantissa >> 8) & 0xFF);
                    mValue[newOffset] = (byte) (newMantissa & 0xFF);
                }
                break;
            default:
                throw new IllegalArgumentException(UNSUPPORTED_FORMAT_TYPE);
        }
    }

    /**
     * Set byte array to a Float using a given precision, i.e. number of digits after the comma
     *
     * @param value     Float value to create byte[] from
     * @param precision number of digits after the comma to use
     */
    public void setFloatValue(final float value, final int precision) {
        float mantissa = (float) (value * Math.pow(10, precision));
        setFloatValue((int) mantissa, -precision, FORMAT_FLOAT, internalOffset);
        internalOffset += getTypeLen(FORMAT_FLOAT);
    }

    /**
     * Set byte array to a string at current offset
     *
     * @param value String to be added to byte array
     * @throws NullPointerException if value is null
     */
    public void setString(@NotNull final String value) {
        Objects.requireNonNull(value);
        setString(value, internalOffset);
        internalOffset += value.getBytes().length;
    }

    /**
     * Set byte array to a string at specified offset position
     *
     * @param value  String to be added to byte array
     * @param offset the offset to place the string at
     * @throws NullPointerException if value is null
     */
    public void setString(final String value, final int offset) {
        Objects.requireNonNull(value);
        prepareArray(offset + value.length());
        byte[] valueBytes = value.getBytes();
        System.arraycopy(valueBytes, 0, mValue, offset, valueBytes.length);
    }


    /**
     * Set the locally stored value of this byte array.
     *
     * @param value New value for this byte array
     */
    public void setValue(@NotNull final byte[] value) {
        mValue = Objects.requireNonNull(value);
    }

    /**
     * Sets the byte array to represent the current date in CurrentTime format
     *
     * @param calendar the calendar object representing the current date
     * @throws NullPointerException if calendar is null
     */
    public void setCurrentTime(@NotNull final Calendar calendar) {
        Objects.requireNonNull(calendar);

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
    }

    /**
     * Sets the byte array to represent the current date in CurrentTime format
     *
     * @param calendar the calendar object representing the current date
     * @throws NullPointerException if calendar is null
     */
    public void setDateTime(@NotNull final Calendar calendar) {
        Objects.requireNonNull(calendar);

        mValue = new byte[7];
        mValue[0] = (byte) calendar.get(Calendar.YEAR);
        mValue[1] = (byte) (calendar.get(Calendar.YEAR) >> 8);
        mValue[2] = (byte) (calendar.get(Calendar.MONTH) + 1);
        mValue[3] = (byte) calendar.get(Calendar.DATE);
        mValue[4] = (byte) calendar.get(Calendar.HOUR_OF_DAY);
        mValue[5] = (byte) calendar.get(Calendar.MINUTE);
        mValue[6] = (byte) calendar.get(Calendar.SECOND);
    }

    /**
     * Returns the size of a give value type.
     */
    private int getTypeLen(final int formatType) {
        return formatType & 0xF;
    }

    /**
     * Convert a signed byte to an unsigned int.
     */
    private int unsignedByteToInt(final byte b) {
        return b & 0xFF;
    }

    /**
     * Convert signed bytes to a 16-bit unsigned int.
     */
    private int unsignedBytesToInt(final byte b0, final byte b1) {
        return (unsignedByteToInt(b0) + (unsignedByteToInt(b1) << 8));
    }

    /**
     * Convert signed bytes to a 32-bit unsigned int.
     */
    private int unsignedBytesToInt(final byte b0, final byte b1, final byte b2, final byte b3) {
        return (unsignedByteToInt(b0) + (unsignedByteToInt(b1) << 8))
                + (unsignedByteToInt(b2) << 16) + (unsignedByteToInt(b3) << 24);
    }

    /**
     * Convert signed bytes to a 16-bit short float value.
     */
    private float bytesToFloat(final byte b0, final byte b1) {
        int mantissa = unsignedToSigned(unsignedByteToInt(b0)
                + ((unsignedByteToInt(b1) & 0x0F) << 8), 12);
        int exponent = unsignedToSigned(unsignedByteToInt(b1) >> 4, 4);
        return (float) (mantissa * Math.pow(10, exponent));
    }

    /**
     * Convert signed bytes to a 32-bit short float value.
     */
    private float bytesToFloat(final byte b0, final byte b1, final byte b2, final byte b3) {
        int mantissa = unsignedToSigned(unsignedByteToInt(b0)
                + (unsignedByteToInt(b1) << 8)
                + (unsignedByteToInt(b2) << 16), 24);
        return (float) (mantissa * Math.pow(10, b3));
    }

    /**
     * Convert an unsigned integer value to a two's-complement encoded
     * signed value.
     */
    private int unsignedToSigned(final int unsigned, int size) {
        if ((unsigned & (1 << size - 1)) != 0) {
            return -1 * ((1 << size - 1) - (unsigned & ((1 << size - 1) - 1)));
        }
        return unsigned;
    }

    /**
     * Convert an integer into the signed bits of a given length.
     */
    private int intToSignedBits(final int i, int size) {
        if (i < 0) {
            return (1 << size - 1) + (i & ((1 << size - 1) - 1));
        }
        return i;
    }

    /**
     * Convert a byte array to a string
     *
     * @param bytes the bytes to convert
     * @return String object that represents the byte array
     */
    @NotNull
    public static String bytes2String(@Nullable final byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * Convert a hex string to byte array
     *
     */
    @NotNull
    public static byte[] string2bytes(@Nullable final String hexString) {
        if (hexString == null) return new byte[0];
        byte[] result = new byte[hexString.length() / 2];
        for (int i=0; i < result.length ; i++) {
            int index = i * 2;
            result[i] = (byte) Integer.parseInt(hexString.substring(index, index + 2), 16);
        }
        return result;
    }

    /**
     * Merge multiple arrays intro one array
     *
     * @param arrays Arrays to merge
     * @return Merge array
     */
    @NotNull
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
        return internalOffset;
    }

    /**
     * Set the value of the internal offset
     * @throws IllegalArgumentException if the offset is negative
     */
    public void setOffset(int offset) {
        if (offset < 0) throw new IllegalArgumentException(OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO);
        this.internalOffset = offset;
    }

    /**
     * Get the set byte order
     */
    public @NotNull ByteOrder getByteOrder() {
        return internalByteOrder;
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
