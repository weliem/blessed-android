package com.welie.blessed;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReadResponse {
    public @NotNull final GattStatus status;
    public @Nullable final byte[] value;

    ReadResponse(@NotNull final GattStatus status, @Nullable final byte[] value) {
        this.status = status;
        this.value = value;
    }
}
