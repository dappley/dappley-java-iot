package com.dappley.android.bleservice.service;

public interface BleWalletCallback {
    public static int WALLET_CONNECTING = 0;
    public static int WALLET_CONNECTED = 1;
    public static int WALLET_INITIALIZED = 2;
    public static int WALLET_DISCONNECTING = 3;
    public static int WALLET_DISCONNECTED = 4;
    public static int WALLET_FAILED = 5;

    void onWalletStatusChanged(int status, String address);
}
