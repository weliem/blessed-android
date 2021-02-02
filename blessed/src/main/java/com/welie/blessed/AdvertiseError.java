package com.welie.blessed;

import org.jetbrains.annotations.NotNull;

import static android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED;
import static android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE;
import static android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED;
import static android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR;
import static android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS;

/**
 * This enum describes all possible errors that can occur when trying to start advertising
 */
public enum AdvertiseError {
    /**
     * Failed to start advertising as the advertise data to be broadcasted is larger than 31 bytes.
     */
    DATA_TOO_LARGE(ADVERTISE_FAILED_DATA_TOO_LARGE),

    /**
     * Failed to start advertising because no advertising instance is available.
     */
    TOO_MANY_ADVERTISERS(ADVERTISE_FAILED_TOO_MANY_ADVERTISERS),

    /**
     * Failed to start advertising as the advertising is already started.
     */
    ALREADY_STARTED(ADVERTISE_FAILED_ALREADY_STARTED),

    /**
     * Operation failed due to an internal error.
     */
    INTERNAL_ERROR(ADVERTISE_FAILED_INTERNAL_ERROR),

    /**
     * This feature is not supported on this platform.
     */
    FEATURE_UNSUPPORTED(ADVERTISE_FAILED_FEATURE_UNSUPPORTED),

    UNKNOWN_ERROR(-1);

    final int value;

    AdvertiseError(int value) {
        this.value = value;
    }

    int getValue() {
        return value;
    }

    @NotNull
    static AdvertiseError fromValue(int value) {
        for (AdvertiseError type : values()) {
            if (type.getValue() == value)
                return type;
        }
        return UNKNOWN_ERROR;
    }
}
