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
 * This class represents the possible Phy types
 */
public enum PhyType {
    /**
     * A Physical Layer (PHY) connection of 1 mbit. Compatible with Bluetooth 4.0, 4.1, 4.2 and 5.0
     */
    LE_1M(1,1),

    /**
     * A Physical Layer (PHY) connection of 2 mbit. Requires Bluetooth 5
     */
    LE_2M (2,2),

    /**
     * A Physical Layer (PHY) connection with long range. Requires Bluetooth 5
     */
    LE_CODED(3, 4),

    /**
     * Unknown Phy Type. Not to be used.
     */
    UNKNOWN_PHY_TYPE(-1,-1);

    PhyType(final int value, final int mask) {
        this.value = value;
        this.mask = mask;
    }

    public final int value;
    public final int mask;

    @NotNull
    public static PhyType fromValue(final int value) {
        for (PhyType type : values()) {
            if (type.value == value)
                return type;
        }
        return UNKNOWN_PHY_TYPE;
    }
}
