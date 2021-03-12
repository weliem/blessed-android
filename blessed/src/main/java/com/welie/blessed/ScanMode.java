package com.welie.blessed;

import static android.bluetooth.le.ScanSettings.SCAN_MODE_BALANCED;
import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY;
import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_POWER;

/**
 * This class represents the possible scan modes
 */
public enum ScanMode {
    /**
     * A special Bluetooth LE scan mode. Applications using this scan mode will passively listen for
     * other scan results without starting BLE scans themselves.
     */
    OPPORTUNISTIC(-1),

    /**
     * Perform Bluetooth LE scan in balanced power mode. Scan results are returned at a rate that
     * provides a good trade-off between scan frequency and power consumption.
     */
    BALANCED(SCAN_MODE_BALANCED),

    /**
     * Scan using highest duty cycle. It's recommended to only use this mode when the application is
     * running in the foreground.
     */
    LOW_LATENCY(SCAN_MODE_LOW_LATENCY),

    /**
     * Perform Bluetooth LE scan in low power mode. This is the default scan mode as it consumes the
     * least power. This mode is enforced if the scanning application is not in foreground.
     */
    LOW_POWER(SCAN_MODE_LOW_POWER);

    ScanMode(final int value) {
        this.value = value;
    }

    public final int value;
}
