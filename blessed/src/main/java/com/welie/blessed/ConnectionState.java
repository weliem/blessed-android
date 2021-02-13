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
 * This class represents the possible connection states
 */
public enum ConnectionState {
    /**
     * The peripheral is disconnected
     */
    DISCONNECTED(0),

    /**
     * The peripheral is connecting
     */
    CONNECTING(1),

    /**
     * The peripheral is connected
     */
    CONNECTED(2),

    /**
     * The peripheral is disconnecting
     */
    DISCONNECTING(3);

    ConnectionState(final int value) {
        this.value = value;
    }

    public final int value;

    @NotNull
    public static ConnectionState fromValue(final int value) {
        for (ConnectionState type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return DISCONNECTED;
    }
}
