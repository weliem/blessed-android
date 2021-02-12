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

import static android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_BALANCED;
import static android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_HIGH;
import static android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER;

public enum ConnectionPriority {
    /**
     * Use the connection parameters recommended by the Bluetooth SIG.
     * This is the default value if no connection parameter update
     * is requested.
     */
    BALANCED(CONNECTION_PRIORITY_BALANCED),

    /**
     * Request a high priority, low latency connection.
     * An application should only request high priority connection parameters to transfer large
     * amounts of data over LE quickly. Once the transfer is complete, the application should
     * request BALANCED connection parameters to reduce energy use.
     */
    HIGH(CONNECTION_PRIORITY_HIGH),

    /**
     * Request low power, reduced data rate connection parameters.
     */
    LOW_POWER(CONNECTION_PRIORITY_LOW_POWER);

    ConnectionPriority(final int value) {
        this.value = value;
    }

    public final int value;
}
