package com.welie.blessedexample;

import androidx.annotation.NonNull;

/**
 * Enum that contains all measurement units as specified here:
 * https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.characteristic.weight_measurement.xml
 */
public enum WeightUnit {
    Unknown {
        @NonNull
        @Override
        public String toString() {
            return "unknown";
        }
    },
    Kilograms {
        @NonNull
        @Override
        public String toString() {
            return "Kg";
        }
    },
    Pounds {
        @NonNull
        @Override
        public String toString() {
            return "lbs";
        }
    },
    Stones {
        @NonNull
        @Override
        public String toString() {
            return "st";
        }
    };
}
