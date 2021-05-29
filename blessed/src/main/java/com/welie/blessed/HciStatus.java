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
 * This class describes the HCI error codes as defined in the Bluetooth Standard, Volume 1, Part F, 1.3 HCI Error Code, pages 364-377.
 * See https://www.bluetooth.org/docman/handlers/downloaddoc.ashx?doc_id=478726,
 */
public enum HciStatus {

    /**
     * Command was successful
     */
    SUCCESS(0x00),

    /**
     * The controller does not understand the HCI Command Packet OpCode that the Host sent.
     */
    UNKNOWN_COMMAND(0x01),

    /**
     * The connection identifier used is unknown
     */
    UNKNOWN_CONNECTION_IDENTIFIER(0x02),

    /**
     * A hardware failure has occurred
     */
    HARDWARE_FAILURE(0x03),

    /**
     * a page timed out because of the Page Timeout configuration parameter.
     */
    PAGE_TIMEOUT(0x04),

    /**
     * Pairing or authentication failed due to incorrect results in the pairing or authentication procedure. This could be due to an incorrect PIN or Link Key.
     */
    AUTHENTICATION_FAILURE(0x05),

    /**
     * Pairing failed because of a missing PIN, or authentication failed because of a missing Key.
     */
    PIN_OR_KEY_MISSING(0x06),

    /**
     * The Controller has run out of memory to store new parameters.
     */
    MEMORY_FULL(0x07),

    /**
     * The link supervision timeout has expired for a given connection.
     */
    CONNECTION_TIMEOUT(0x08),

    /**
     * The Controller is already at its limit of the number of connections it can support.
     */
    CONNECTION_LIMIT_EXCEEDED(0x09),

    /**
     * The Controller has reached the limit to the number of synchronous connections that can be achieved to a device.
     */
    MAX_NUM_OF_CONNECTIONS_EXCEEDED(0x0A),

    /**
     * A connection to this device already exists and multiple connections to the same device are not permitted.
     */
    CONNECTION_ALREADY_EXISTS(0x0B),

    /**
     * The command requested cannot be executed because the Controller is in a state where it cannot process this command at this time.
     */
    COMMAND_DISALLOWED(0x0C),

    /**
     * A connection was rejected due to limited resources.
     */
    CONNECTION_REJECTED_LIMITED_RESOURCES(0x0D),

    /**
     * A connection was rejected due to security requirements not being fulfilled, like authentication or pairing.
     */
    CONNECTION_REJECTED_SECURITY_REASONS(0x0E),

    /**
     * connection was rejected because this device does not accept the BD_ADDR
     */
    CONNECTION_REJECTED_UNACCEPTABLE_MAC_ADDRESS(0x0F),

    /**
     * The Connection Accept Timeout has been exceeded for this connection attempt.
     */
    CONNECTION_ACCEPT_TIMEOUT_EXCEEDED(0x10),

    /**
     * A feature or parameter value in the HCI command is not supported.
     */
    UNSUPPORTED_PARAMETER_VALUE(0x11),

    /**
     * At least one of the HCI command parameters is invalid.
     */
    INVALID_COMMAND_PARAMETERS(0x12),

    /**
     * The user on the remote device terminated the connection.
     */
    REMOTE_USER_TERMINATED_CONNECTION(0x13),

    /**
     * The remote device terminated the connection because of low resources.
     */
    REMOTE_DEVICE_TERMINATED_CONNECTION_LOW_RESOURCES(0x14),

    /**
     * The remote device terminated the connection because the device is about to power off.
     */
    REMOTE_DEVICE_TERMINATED_CONNECTION_POWER_OFF(0x15),

    /**
     * The local device terminated the connection.
     */
    CONNECTION_TERMINATED_BY_LOCAL_HOST(0x16),

    /**
     * The Controller is disallowing an authentication or pairing procedure because too little time has elapsed since the last authentication or pairing attempt failed.
     */
    REPEATED_ATTEMPTS(0x17),

    /**
     * The device does not allow pairing
     */
    PAIRING_NOT_ALLOWED(0x18),

    /**
     * The Controller has received an unknown LMP OpCode.
     */
    UNKNOWN_LMP_PDU(0x19),

    /**
     * The remote device does not support the feature associated with the issued command or LMP PDU.
     */
    UNSUPPORTED_REMOTE_FEATURE(0x1A),

    /**
     * The offset requested in the LMP_SCO_link_req PDU has been rejected.
     */
    SCO_OFFSET_REJECTED(0x1B),

    /**
     * The interval requested in the LMP_SCO_link_req PDU has been rejected.
     */
    SCO_INTERVAL_REJECTED(0x1C),

    /**
     * The air mode requested in the LMP_SCO_link_req PDU has been rejected.
     */
    SCO_AIR_MODE_REJECTED(0x1D),

    /**
     * Some LMP PDU / LL Control PDU parameters were invalid.
     */
    INVALID_LMP_OR_LL_PARAMETERS(0x1E),

    /**
     * No other error code specified is appropriate to use
     */
    UNSPECIFIED(0x1F),

    /**
     * An LMP PDU or an LL Control PDU contains at least one parameter value that is not supported by the Controller at this time.
     */
    UNSUPPORTED_LMP_OR_LL_PARAMETER_VALUE(0x20),

    /**
     * a Controller will not allow a role change at this time.
     */
    ROLE_CHANGE_NOT_ALLOWED(0x21),

    /**
     * An LMP transaction failed to respond within the LMP response timeout or an LL transaction failed to respond within the LL response timeout.
     */
    LMP_OR_LL_RESPONSE_TIMEOUT(0x22),

    /**
     * An LMP transaction or LL procedure has collided with the same transaction or procedure that is already in progress.
     */
    LMP_OR_LL_ERROR_TRANS_COLLISION(0x23),

    /**
     * A Controller sent an LMP PDU with an OpCode that was not allowed.
     */
    LMP_PDU_NOT_ALLOWED(0x24),

    /**
     * The requested encryption mode is not acceptable at this time.
     */
    ENCRYPTION_MODE_NOT_ACCEPTABLE(0x25),

    /**
     * A link key cannot be changed because a fixed unit key is being used.
     */
    LINK_KEY_CANNOT_BE_EXCHANGED(0x26),

    /**
     * The requested Quality of Service is not supported.
     */
    REQUESTED_QOS_NOT_SUPPORTED(0x27),

    /**
     * An LMP PDU or LL PDU that includes an instant cannot be performed because the instant when this would have occurred has passed.
     */
    INSTANT_PASSED(0x28),

    /**
     * It was not possible to pair as a unit key was requested and it is not supported.
     */
    PAIRING_WITH_UNIT_KEY_NOT_SUPPORTED(0x29),

    /**
     * An LMP transaction or LL Procedure was started that collides with an ongoing transaction.
     */
    DIFFERENT_TRANSACTION_COLLISION(0x2A),

    /**
     * Undefined error code
     */
    UNDEFINED_0x2B(0x2B),

    /**
     * The specified quality of service parameters could not be accepted at this time, but other parameters may be acceptable.
     */
    QOS_UNACCEPTABLE_PARAMETER(0x2C),

    /**
     * The specified quality of service parameters cannot be accepted and QoS negotiation should be terminated
     */
    QOS_REJECTED(0x2D),

    /**
     * The Controller cannot perform channel assessment because it is not supported.
     */
    CHANNEL_CLASSIFICATION_NOT_SUPPORTED(0x2E),

    /**
     * The HCI command or LMP PDU sent is only possible on an encrypted link.
     */
    INSUFFICIENT_SECURITY(0x2F),

    /**
     * A parameter value requested is outside the mandatory range of parameters for the given HCI command or LMP PDU and the recipient does not accept that value.
     */
    PARAMETER_OUT_OF_RANGE(0x30),

    /**
     * Undefined error
     */
    UNDEFINED_0x31(0x31),

    /**
     * A Role Switch is pending. This can be used when an HCI command or LMP PDU cannot be accepted because of a pending role switch. This can also be used to notify a peer device about a pending role switch.
     */
    ROLE_SWITCH_PENDING(0x32),

    /**
     * Undefined error
     */
    UNDEFINED_0x33(0x33),

    /**
     * The current Synchronous negotiation was terminated with the negotiation state set to Reserved Slot Violation.
     */
    RESERVED_SLOT_VIOLATION(0x34),

    /**
     * A role switch was attempted but it failed and the original piconet structure is restored.
     */
    ROLE_SWITCH_FAILED(0x35),

    /**
     * The extended inquiry response, with the requested requirements for FEC, is too large to fit in any of the packet types supported by the Controller.
     */
    INQUIRY_RESPONSE_TOO_LARGE(0x36),

    /**
     * The IO capabilities request or response was rejected because the sending Host does not support Secure Simple Pairing even though the receiving Link Manager does.
     */
    SECURE_SIMPLE_PAIRING_NOT_SUPPORTED(0x37),

    /**
     * The Host is busy with another pairing operation and unable to support the requested pairing. The receiving device should retry pairing again later.
     */
    HOST_BUSY_PAIRING(0x38),

    /**
     * The Controller could not calculate an appropriate value for the Channel selection operation.
     */
    CONNECTION_REJECTED_NO_SUITABLE_CHANNEL(0x39),

    /**
     * The Controller was busy and unable to process the request.
     */
    CONTROLLER_BUSY(0x3A),

    /**
     * The remote device either terminated the connection or rejected a request because of one or more unacceptable connection parameters.
     */
    UNACCEPTABLE_CONNECTION_PARAMETERS(0x3B),

    /**
     * Advertising for a fixed duration completed or, for directed advertising, that advertising completed without a connection being created.
     */
    ADVERTISING_TIMEOUT(0x3C),

    /**
     * The connection was terminated because the Message Integrity Check (MIC) failed on a received packet.
     */
    CONNECTION_TERMINATED_MIC_FAILURE(0x3D),

    /**
     * The LL initiated a connection but the connection has failed to be established.
     */
    CONNECTION_FAILED_ESTABLISHMENT(0x3E),

    /**
     * The MAC of the 802.11 AMP was requested to connect to a peer, but the connection failed.
     */
    MAC_CONNECTION_FAILED(0x3F),

    /**
     * The master, at this time, is unable to make a coarse adjustment to the piconet clock, using the supplied parameters. Instead the master will attempt to move the clock using clock dragging.
     */
    COARSE_CLOCK_ADJUSTMENT_REJECTED(0x40),

    /**
     * The LMP PDU is rejected because the Type 0 submap is not currently defined.
     */
    TYPE0_SUBMAP_NOT_DEFINED(0x41),

    /**
     * A command was sent from the Host that should identify an Advertising or Sync handle, but the Advertising or Sync handle does not exist.
     */
    UNKNOWN_ADVERTISING_IDENTIFIER(0x42),

    /**
     * The number of operations requested has been reached and has indicated the completion of the activity (e.g., advertising or scanning).
     */
    LIMIT_REACHED(0x43),

    /**
     * A request to the Controller issued by the Host and still pending was successfully canceled.
     */
    OPERATION_CANCELLED_BY_HOST(0x44),

    /**
     * An attempt was made to send or receive a packet that exceeds the maximum allowed packet length.
     */
    PACKET_TOO_LONG(0x45),

    // Additional Android specific errors
    ERROR(0x85),

    /**
     * Failure to register client when trying to connect. Probably because the max (30) of clients has been reached.
     * The most likely fix is to make sure you always call close() after a disconnect happens.
     */
    FAILURE_REGISTERING_CLIENT(0x101),

    /**
     * Used when status code is not defined in the class
     */
    UNKNOWN_STATUS_CODE(0xFFFF);

    HciStatus(final int value) {
        this.value = value;
    }

    public final int value;

    @NotNull
    public static HciStatus fromValue(final int value) {
        for (HciStatus type : values()) {
            if (type.value == value)
                return type;
        }
        return UNKNOWN_STATUS_CODE;
    }
}
