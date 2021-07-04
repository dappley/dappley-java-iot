package com.dappley.java.core.service;

import com.dappley.java.core.po.DeviceInput;
import com.dappley.java.core.po.DeviceResult;

import java.util.List;

public interface DeviceService {
    byte[]  getPublicKey();
    DeviceResult signBytes(byte[] data);
    DeviceResult signBytesWithDeviceData(List<DeviceInput> inputs);
}
