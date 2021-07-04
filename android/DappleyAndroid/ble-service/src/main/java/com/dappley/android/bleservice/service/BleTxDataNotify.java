package com.dappley.android.bleservice.service;

import lombok.Data;

@Data
public class BleTxDataNotify {
    private int  seq;
    private int  offset;
    private int  status;
}
