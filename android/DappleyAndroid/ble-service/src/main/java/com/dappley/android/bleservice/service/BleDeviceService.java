package com.dappley.android.bleservice.service;

import android.util.Log;

import com.dappley.java.core.po.DeviceInput;
import com.dappley.java.core.po.DeviceResult;
import com.dappley.java.core.po.Wallet;
import com.dappley.java.core.service.DeviceService;
import com.dappley.java.core.util.HashUtil;

import java.util.List;

public class BleDeviceService implements DeviceService {
    private Wallet wallet;
    private BleWalletService walletService;

    private final static String TAG = BleDeviceService.class.getSimpleName();

    public BleDeviceService(Wallet wallet, BleWalletService walletService) {
        this.wallet = wallet;
        this.walletService = walletService;
    }

    @Override
    public byte[] getPublicKey() {
        return HashUtil.getPubKeyBytes(wallet.getPublicKey());
    }

    @Override
    public DeviceResult signBytes(byte[] data) {
        try {
            return this.walletService.signHashData(wallet.getBtAddress(), data).get();
        } catch (Exception e) {
            Log.w(TAG, "Sign hash data failed", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public DeviceResult signBytesWithDeviceData(List<DeviceInput> inputs) {
        try {
            return this.walletService.signWithDeviceData(wallet.getBtAddress(), inputs).get();
        } catch (Exception e) {
            Log.w(TAG, "Get pubkey failed", e);
            throw new RuntimeException(e);
        }
    }
}
