package com.welie.blessedexample;

public enum TemperatureType {
    Armpit(1),
    Body(2),
    Ear(3),
    Finger(4),
    GastroIntestinalTract(5),
    Mouth(6),
    Rectum(7),
    Toe(8),
    Tympanum(9);

    TemperatureType(int value)
    {
        this.value = value;
    }

    private int value;

    public int getValue()
    {
        return value;
    }

    public static TemperatureType fromValue(int value) {
        for(TemperatureType type : values()) {
            if(type.getValue() == value)
                return type;
        }
        return null;
    }
}
