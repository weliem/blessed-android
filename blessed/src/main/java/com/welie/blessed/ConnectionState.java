package com.welie.blessed;

import org.jetbrains.annotations.NotNull;

public enum ConnectionState {
    /**
     * The peripheral is in disconnected state
     */
    DISCONNECTED(0),

    /**
     * The peripheral is in connecting state
     */
    CONNECTING(1),

    /**
     * The peripheral is in connected state
     */
    CONNECTED(2),

    /**
     * The peripheral is in disconnecting state
     */
    DISCONNECTING(3);

    ConnectionState(int value) {
        this.value = value;
    }

    private final int value;

    @NotNull
    public static ConnectionState fromValue(int value) {
        for (ConnectionState type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return DISCONNECTED;
    }
}
