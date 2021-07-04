package com.dappley.android.bleservice.service;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

/**
 * Ble wallet device packet header
 *     4 Bytes  ---- seq
 *     2 Bytes  ---- size
 *     2 Bytes  ---- offset
 */

public class BleDataSender {
    private int seq;
    private byte[] data;
    private int mtuSize;
    private int nextOffset;
    private int finishSize;
    private int currPacketSize;
    private BluetoothGatt gatt;
    BluetoothGattCharacteristic characteristic;

    private static final int PACKET_HEADER_SIZE = 8;
    private final static String TAG = BleDataSender.class.getSimpleName();

    public BleDataSender(int seq, byte[] data, int mtuSize, BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        this.seq = seq;
        this.data = data;
        //this.mtuSize = mtuSize;
        this.mtuSize = 64;
        this.nextOffset = 0;
        this.finishSize = 0;
        this.gatt = gatt;
        this.characteristic = characteristic;
    }

    public boolean isFinished() {
        return finishSize >= data.length;
    }

    public boolean sendPacket() {
        currPacketSize = data.length - nextOffset;
        if ( PACKET_HEADER_SIZE + currPacketSize > mtuSize) {
            currPacketSize = mtuSize - PACKET_HEADER_SIZE;
        }

        byte[] sendData = new byte[currPacketSize + PACKET_HEADER_SIZE];
        buildPacketHeader(sendData, nextOffset);
        System.arraycopy(data, nextOffset, sendData, PACKET_HEADER_SIZE, currPacketSize);
        characteristic.setValue(sendData);
        Log.i(TAG, String.format("Send data to device seq:%d total_size:%d offset:%d size:%d",  this.seq, data.length, nextOffset, currPacketSize));
        return gatt.writeCharacteristic(characteristic);
    }

    public void finishPacket() {
        nextOffset += currPacketSize;
        finishSize = nextOffset;
        currPacketSize = 0;
    }

    public int getSeq() {
        return seq;
    }

    public int getNextOffset() {
        return nextOffset;
    }

    private void buildPacketHeader(byte[] bytes, int offset) {
        bytes[0] = (byte)(seq & 0XFF);
        bytes[1] = (byte)((seq & 0xFF00) >> 8);
        bytes[2] = (byte)((seq & 0xFF0000) >> 16);
        bytes[3] = (byte)((seq & 0xFF000000) >> 24);

        bytes[4] = (byte)(data.length & 0xFF);
        bytes[5] = (byte)((data.length & 0xFF00) >> 8);

        bytes[6] = (byte)(offset & 0xFF);
        bytes[7] = (byte)((offset & 0xFF00) >> 8);
    }

}
