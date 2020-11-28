package com.welie.blessed;

public enum BluetoothGattError {

    // Note that most of these error codes correspond to the ATT error codes as defined in the Bluetooth Standard, Volume 3, Part F, 3.4.1 Error handling p1491)
    // See https://www.bluetooth.org/docman/handlers/downloaddoc.ashx?doc_id=478726,

    /**
     * Operation completed successfully
     */
    GATT_SUCCESS(0x00),

    /**
     * The attribute handle given was not valid on this server
     */
    GATT_INVALID_HANDLE(0x01),

    /**
     * The attribute cannot be read.
     */
    GATT_READ_NOT_PERMITTED(0x02),

    /**
     * The attribute cannot be written.
     */
    GATT_WRITE_NOT_PERMITTED(0x03),

    /**
     * The attribute PDU was invalid.
     */
    GATT_INVALID_PDU(0x04),

    /**
     * The attribute requires authentication before it can be read or written.
     */
    GATT_INSUFFICIENT_AUTHENTICATION(0x05),

    /**
     * Attribute server does not support the request received from the client.
     */
    GATT_REQUEST_NOT_SUPPORTED(0x06),

    /**
     * Offset specified was past the end of the attribute.
     */
    GATT_INVALID_OFFSET(0x07),

    /**
     * The attribute requires authorization before it can be read or written.
     */
    GATT_INSUFFICIENT_AUTHORIZATION(0x08),

    /**
     * Too many prepare writes have been queued.
     */
    GATT_PREPARE_QUEUE_FULL(0x09),

    /**
     * No attribute found within the given attribute handle range.
     */
    GATT_ATTRIBUTE_NOT_FOUND(0x0A),

    /**
     * The attribute cannot be read using the ATT_READ_BLOB_REQ PDU.
     */
    GATT_ATTRIBUTE_NOT_LONG(0x0B),

    /**
     * The Encryption Key Size used for encrypting this link is insufficient.
     */
    GATT_INSUFFICIENT_ENCRYPTION_KEY_SIZE(0x0C),

    /**
     * The attribute value length is invalid for the operation.
     */
    GATT_INVALID_ATTRIBUTE_VALUE_LENGTH(0x0D),

    /**
     * The attribute request that was requested has encountered an error that was unlikely, and therefore could not be completed as requested.
     */
    GATT_UNLIKELY_ERROR(0x0E),

    /**
     * The attribute requires encryption before it can be read or written.
     */
    GATT_INSUFFICIENT_ENCRYPTION(0x0F),

    /**
     * The attribute type is not a supported grouping attribute as defined by a higher layer specification.
     */
    GATT_UNSUPPORTED_GROUP_TYPE(0x10),

    /**
     * Insufficient Resources to complete the request.
     */
    GATT_INSUFFICIENT_RESOURCES(0x11),

    /**
     * The server requests the client to redis- cover the database.
     */
    GATT_DATABASE_OUT_OF_SYNC(0x12),

    /**
     * The attribute parameter value was not allowed
     */
    GATT_VALUE_NOT_ALLOWED(0x13),

    // (0x80 – 0x9F) - Application error code defined by a higher layer specification.
    // So the following codes are Android specific

    /**
     * No resources
     */
    GATT_NO_RESOURCES(0x80),

    /**
     * An internal error has occurred
     */
    GATT_INTERNAL_ERROR(0x81),

    /**
     * Wrong state
     */
    GATT_WRONG_STATE(0x82),

    /**
     * Database is full
     */
    GATT_DB_FULL(0x83),

    /**
     * Busy
     */
    GATT_BUSY(0x84),

    /**
     * Undefined GATT error occurred
     */
    GATT_ERROR(0x85),

    /**
     * Command has been queued up
     */
    GATT_CMD_STARTED(0x86),

    /**
     * Illegal parameter
     */
    GATT_ILLEGAL_PARAMETER(0x87),

    /**
     * Operation is pending
     */
    GATT_PENDING(0x88),

    /**
     * Authorization failed
     */
    GATT_AUTH_FAIL(0x89),

    /**
     * More
     */
    GATT_MORE(0x8a),

    /**
     * Invalid configuration
     */
    GATT_INVALID_CFG(0x8b),

    /**
     * Service started
     */
    GATT_SERVICE_STARTED(0x8c),

    /**
     * No Man-in-the-middle protection
     */
    GATT_ENCRYPED_NO_MITM(0x8d),

    /**
     * Not encrypted
     */
    GATT_NOT_ENCRYPTED(0x8e),

    /**
     * Command is sent but L2CAP channel is congested
     */
    GATT_CONNECTION_CONGESTED(0x8f),

    // (0xE0 – 0xFF) - Common profile and service error codes defined in Core Specification Supplement, Part B.

    // Other errors codes that are Android specific

    /**
     * L2CAP connection cancelled
     */
    GATT_CONN_CANCEL(0x0100),

    /**
     * Failure
     */
    GATT_FAILURE(0x101);

    BluetoothGattError(int value) {
        this.value = value;
    }

    private final int value;

    public int getValue() {
        return value;
    }

    public static BluetoothGattError fromValue(int value) {
        for (BluetoothGattError type : values()) {
            if (type.getValue() == value)
                return type;
        }
        return null;
    }
}
