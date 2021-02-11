package com.welie.blessed;

import org.jetbrains.annotations.NotNull;

import static android.bluetooth.le.ScanCallback.SCAN_FAILED_ALREADY_STARTED;
import static android.bluetooth.le.ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED;
import static android.bluetooth.le.ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED;
import static android.bluetooth.le.ScanCallback.SCAN_FAILED_INTERNAL_ERROR;

/**
 * This class represents the possible scan failure reasons
 */
public enum ScanFailure {

    /**
     * Failed to start scan as BLE scan with the same settings is already started by the app.
     */
    ALREADY_STARTED(SCAN_FAILED_ALREADY_STARTED),

    /**
     * Failed to start scan as app cannot be registered.
     */
    APPLICATION_REGISTRATION_FAILED(SCAN_FAILED_APPLICATION_REGISTRATION_FAILED),

    /**
     * Failed to start scan due an internal error
     */
    INTERNAL_ERROR(SCAN_FAILED_INTERNAL_ERROR),

    /**
     * Failed to start power optimized scan as this feature is not supported.
     */
    FEATURE_UNSUPPORTED(SCAN_FAILED_FEATURE_UNSUPPORTED),

    /**
     * Failed to start scan as it is out of hardware resources.
     */
    OUT_OF_HARDWARE_RESOURCES(5),

    /**
     * Failed to start scan as application tries to scan too frequently.
     */
    SCANNING_TOO_FREQUENTLY(6),

    UNKNOWN(-1);

    ScanFailure(int value) {
        this.value = value;
    }

    public final int value;

    @NotNull
    public static ScanFailure fromValue(int value) {
        for (ScanFailure type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
