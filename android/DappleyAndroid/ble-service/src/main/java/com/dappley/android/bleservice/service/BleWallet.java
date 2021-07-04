package com.dappley.android.bleservice.service;

import com.dappley.java.core.po.Wallet;

import lombok.Data;

@Data
public class BleWallet {
    private String address;
    private Wallet wallet;
}
