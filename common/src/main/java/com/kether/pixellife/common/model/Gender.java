package com.kether.pixellife.common.model;

public enum Gender {
    MALE,
    FEMALE;

    public static Gender random() {
        return Math.random() < 0.5 ? MALE : FEMALE;
    }
}