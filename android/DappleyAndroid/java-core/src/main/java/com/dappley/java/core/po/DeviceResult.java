package com.dappley.java.core.po;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * Device Signature return result
 */
@Data
public class DeviceResult {
    private byte[] signature;
    private List<byte[]> deviceData;
}
