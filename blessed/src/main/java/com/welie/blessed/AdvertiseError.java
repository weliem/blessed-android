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

    public final int value;

    AdvertiseError(final int value) {
        this.value = value;
    }

    @NotNull
    static AdvertiseError fromValue(final int value) {
        for (AdvertiseError type : values()) {
            if (type.value == value)
                return type;
        }
        return UNKNOWN_ERROR;
    }
}
