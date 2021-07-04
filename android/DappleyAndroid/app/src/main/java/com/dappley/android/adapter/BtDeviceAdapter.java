package com.dappley.android.adapter;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.dappley.android.R;
import com.dappley.android.ScanBluetoothActivity;
import com.dappley.android.WalletBtImportActivity;
import com.dappley.android.WalletImportActivity;
import com.dappley.android.bleservice.service.BleWalletCallback;
import com.dappley.android.bleservice.service.BleWalletService;

import java.util.ArrayList;

public class BtDeviceAdapter extends ArrayAdapter<BluetoothDevice> {
    private BleWalletService btWalletService;
    private Activity parentActivity;

    static class BtDeviceCallback implements BleWalletCallback {
        Activity context;
        private String btAddress;
        private BleWalletService service;

        public BtDeviceCallback(Activity context, String btAddress, BleWalletService service) {
            this.context = context;
            this.btAddress = btAddress;
            this.service = service;
        }

        @Override
        public void onWalletStatusChanged(int status, String address) {
            if (btAddress.equals(address) == false) {
                return;
            }
            if (status == BleWalletCallback.WALLET_FAILED) {
                Toast.makeText(context, R.string.bt_failed, Toast.LENGTH_SHORT).show();
                return;
            } else if (status == BleWalletCallback.WALLET_CONNECTED) {
                Intent intent = new Intent(context, WalletBtImportActivity.class);
                intent.putExtra("address", btAddress);
                context.startActivityForResult(intent, ScanBluetoothActivity.REQUEST_FINISH);
                service.unRegisterWalletCb(this);
            }
        }
    }

    public void setBtWalletService(BleWalletService btWalletService) {
        this.btWalletService = btWalletService;
    }

    public BtDeviceAdapter(Activity context, ArrayList<BluetoothDevice> devices) {
        super(context, 0, devices);
        this.parentActivity = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        final BluetoothDevice device = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.bt_device_item, parent, false);
        }
        // Lookup view for data population
        TextView deviceName = (TextView) convertView.findViewById(R.id.deviceName);
        TextView deviceMac = (TextView) convertView.findViewById(R.id.deviceMac);

        Button connectButton = (Button)convertView.findViewById(R.id.connect);

        deviceName.setText(device.getName());
        deviceMac.setText(device.getAddress());

        View.OnClickListener connectClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BtDeviceCallback callback = new BtDeviceCallback(parentActivity, device.getAddress(), btWalletService);;
                btWalletService.registerWalletCb(callback);
                btWalletService.connect(device.getAddress());
            }
        };
        connectButton.setOnClickListener(connectClickListener);
        return convertView;
    }
}
