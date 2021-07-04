package com.dappley.android.bleservice.service;

import java.util.List;

import lombok.Data;

@Data
public class BleTxSignNotify {
    private int  error;
    private int  flags;
    private byte[] signature;
    private List<byte[]> values;
}
