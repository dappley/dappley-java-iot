package com.dappley.android.bleservice.service;

public interface BleServiceContinuation {
    void onNotifyData(int status, byte[] data);
    void onReadData(int status, byte[] data);
    void onWriteData(int status, byte[] data);
}
