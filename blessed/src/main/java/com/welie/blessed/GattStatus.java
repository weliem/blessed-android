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

/**
 * The GattStatus describes the result of a GATT operation.
 *
 * <p>Note that most of these error codes correspond to the ATT error codes as defined in the Bluetooth Standard, Volume 3, Part F, 3.4.1 Error handling p1491)
 * See https://www.bluetooth.org/docman/handlers/downloaddoc.ashx?doc_id=478726
 * </p>
 *
 * <p>Gatt status values as defined in the Android source code:
 * https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/master/stack/include/gatt_api.h
 * </p>
 */
public enum GattStatus {

    /**
     * Operation completed successfully
     */
    SUCCESS(0x00),

    /**
     * The attribute handle given was not valid on this server
     */
    INVALID_HANDLE(0x01),

    /**
     * The attribute cannot be read.
     */
    READ_NOT_PERMITTED(0x02),

    /**
     * The attribute cannot be written.
     */
    WRITE_NOT_PERMITTED(0x03),

    /**
     * The attribute PDU was invalid.
     */
    INVALID_PDU(0x04),

    /**
     * The attribute requires authentication before it can be read or written.
     */
    INSUFFICIENT_AUTHENTICATION(0x05),

    /**
     * Attribute server does not support the request received from the client.
     */
    REQUEST_NOT_SUPPORTED(0x06),

    /**
     * Offset specified was past the end of the attribute.
     */
    INVALID_OFFSET(0x07),

    /**
     * The attribute requires authorization before it can be read or written.
     */
    INSUFFICIENT_AUTHORIZATION(0x08),

    /**
     * Too many prepare writes have been queued.
     */
    PREPARE_QUEUE_FULL(0x09),

    /**
     * No attribute found within the given attribute handle range.
     */
    ATTRIBUTE_NOT_FOUND(0x0A),

    /**
     * The attribute cannot be read using the ATT_READ_BLOB_REQ PDU.
     */
    ATTRIBUTE_NOT_LONG(0x0B),

    /**
     * The Encryption Key Size used for encrypting this link is insufficient.
     */
    INSUFFICIENT_ENCRYPTION_KEY_SIZE(0x0C),

    /**
     * The attribute value length is invalid for the operation.
     */
    INVALID_ATTRIBUTE_VALUE_LENGTH(0x0D),

    /**
     * The attribute request that was requested has encountered an error that was unlikely, and therefore could not be completed as requested.
     */
    UNLIKELY_ERROR(0x0E),

    /**
     * The attribute requires encryption before it can be read or written.
     */
    INSUFFICIENT_ENCRYPTION(0x0F),

    /**
     * The attribute type is not a supported grouping attribute as defined by a higher layer specification.
     */
    UNSUPPORTED_GROUP_TYPE(0x10),

    /**
     * Insufficient Resources to complete the request.
     */
    INSUFFICIENT_RESOURCES(0x11),

    /**
     * The server requests the client to rediscover the database.
     */
    DATABASE_OUT_OF_SYNC(0x12),

    /**
     * The attribute parameter value was not allowed
     */
    VALUE_NOT_ALLOWED(0x13),

    // (0x80 – 0x9F) - Application error code defined by a higher layer specification.
    // So the following codes are Android specific

    /**
     * No resources
     */
    NO_RESOURCES(0x80),

    /**
     * An internal error has occurred
     */
    INTERNAL_ERROR(0x81),

    /**
     * Wrong state
     */
    WRONG_STATE(0x82),

    /**
     * Database is full
     */
    DB_FULL(0x83),

    /**
     * Busy
     */
    BUSY(0x84),

    /**
     * Undefined GATT error occurred
     */
    ERROR(0x85),

    /**
     * Command has been queued up
     */
    CMD_STARTED(0x86),

    /**
     * Illegal parameter
     */
    ILLEGAL_PARAMETER(0x87),

    /**
     * Operation is pending
     */
    PENDING(0x88),

    /**
     * Authorization failed, typically because bonding failed
     */
    AUTHORIZATION_FAILED(0x89),

    /**
     * More
     */
    MORE(0x8a),

    /**
     * Invalid configuration
     */
    INVALID_CFG(0x8b),

    /**
     * Service started
     */
    SERVICE_STARTED(0x8c),

    /**
     * No Man-in-the-middle protection
     */
    ENCRYPTED_NO_MITM(0x8d),

    /**
     * Not encrypted
     */
    NOT_ENCRYPTED(0x8e),

    /**
     * Command is sent but L2CAP channel is congested
     */
    CONNECTION_CONGESTED(0x8f),

    // (0xE0 – 0xFF) - Common profile and service error codes defined in Core Specification Supplement, Part B.

    /**
     * Client Characteristic Configuration Descriptor error
     */
    CCCD_CFG_ERROR(0x00FD),

    /**
     * Procedure in progress
     */
    PROCEDURE_IN_PROGRESS(0x00FE),

    /**
     * Value out of range
     */
    VALUE_OUT_OF_RANGE(0x00FF),

    // Other errors codes that are Android specific

    /**
     * L2CAP connection cancelled
     */
    CONNECTION_CANCELLED(0x0100),

    /**
     * Failure to register client when trying to connect. Probably because the max (30) of clients has been reached.
     * The most likely fix is to make sure you always call close() after a disconnect happens.
     */
    FAILURE_REGISTERING_CLIENT(0x101),

    /**
     * Used when status code is not defined in the class
     */
    UNKNOWN_STATUS_CODE(0xFFFF);

    GattStatus(final int value) {
        this.value = value;
    }

    public final int value;

    @NotNull
    public static GattStatus fromValue(final int value) {
        for (GattStatus type : values()) {
            if (type.value == value)
                return type;
        }
        return UNKNOWN_STATUS_CODE;
    }
}
