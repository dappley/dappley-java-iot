package com.dappley.java.core.po;

import lombok.Data;

@Data
public class DeviceInput {
    public static final int INPUT_DATA_FIXED = 0;
    public static final int INPUT_DATA_DEVICE = 1;

    private int inputDataType;
    private byte[] data;

    public DeviceInput(int inputDataType, byte[] data) {
        this.inputDataType = inputDataType;
        this.data = data;
    }
}
