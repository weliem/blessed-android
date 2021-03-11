package com.welie.blessed;

import static android.bluetooth.le.ScanSettings.SCAN_MODE_BALANCED;
import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY;
import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_POWER;

public enum ScanMode {
    OPPORTUNISTIC(-1),
    BALANCED(SCAN_MODE_BALANCED),
    LOW_LATENCY(SCAN_MODE_LOW_LATENCY),
    LOW_POWER(SCAN_MODE_LOW_POWER);

    ScanMode(final int value) {
        this.value = value;
    }

    final int value;
}
