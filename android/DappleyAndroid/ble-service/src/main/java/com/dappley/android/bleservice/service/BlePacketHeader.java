package com.dappley.android.bleservice.service;

import lombok.Data;

@Data
public class BlePacketHeader {
    int  seq;     //   4 Bytes
    int  size;    //   2 Bytes
    int  offset;  //   2 Bytes
}
