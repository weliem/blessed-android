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

    ScanFailure(final int value) {
        this.value = value;
    }

    public final int value;

    @NotNull
    public static ScanFailure fromValue(final int value) {
        for (ScanFailure type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
