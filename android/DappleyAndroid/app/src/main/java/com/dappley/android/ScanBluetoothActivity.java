package com.dappley.android;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.dappley.android.adapter.BtDeviceAdapter;
import com.dappley.android.listener.BtnBackListener;
import com.dappley.android.bleservice.service.BleWalletService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class ScanBluetoothActivity extends AppCompatActivity {
    private final static String TAG = ScanBluetoothActivity.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 1;
    public static final int REQUEST_FINISH = 2;
    private static final int ACCESS_LOCATION = 2;
    private static final int SCAN_PERIOD = 2 * 1000;   // 5s

    @BindView(R.id.txt_title)
    TextView titleView;

    @BindView(R.id.btn_back)
    ImageButton btnBack;

    @BindView(R.id.btn_scan)
    ImageButton btnScan;

    @BindView(R.id.device_list)
    ListView deviceListView;

    private BluetoothAdapter bluetoothAdapter;
    private Handler handler;

    private BtDeviceAdapter leDeviceListAdapter;
    private HashSet<String> existDevices = new HashSet<>();

    private BleWalletService btWalletService;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            btWalletService = ((BleWalletService.LocalBinder) service).getService();
            leDeviceListAdapter.setBtWalletService(btWalletService);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            btWalletService = null;
        }
    };

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (callbackType == ScanSettings.CALLBACK_TYPE_MATCH_LOST) {
                        if (existDevices.contains(result.getDevice().getAddress())) {
                            leDeviceListAdapter.remove(result.getDevice());
                            leDeviceListAdapter.notifyDataSetChanged();
                            existDevices.remove(result.getDevice().getAddress());
                        }
                    } else {
                        if (existDevices.contains(result.getDevice().getAddress())) {
                            return;
                        }

                        leDeviceListAdapter.add(result.getDevice());
                        leDeviceListAdapter.notifyDataSetChanged();
                        existDevices.add(result.getDevice().getAddress());
                    }
                }
            });
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result: results) {
                if (existDevices.contains(result.getDevice().getAddress())) {
                    continue;
                }

                leDeviceListAdapter.remove(result.getDevice());
                leDeviceListAdapter.notifyDataSetChanged();
                existDevices.remove(result.getDevice().getAddress());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(TAG, String.format("Start scan bluetooth device failed %d", errorCode));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_bluetooth);
        ButterKnife.bind(this);
        initView();
        initBle();

        Intent gattServiceIntent = new Intent(this, BleWalletService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Log.d("BtDebug", "Enable bt");
            }
        } else if (requestCode == REQUEST_FINISH) {
            finish();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        btWalletService = null;
    }


    private void initBle() {
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private void initView() {
        titleView.setText(R.string.bt_wallet);
        btnBack.setOnClickListener(new BtnBackListener(this));
        final ScanBluetoothActivity currentActivity = this;
        View.OnClickListener scanClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentActivity.scanLeDevice(true);
            }
        };
        btnScan.setOnClickListener(scanClickListener);

        ArrayList<BluetoothDevice> devices = new ArrayList<>();

        leDeviceListAdapter = new BtDeviceAdapter(this, devices);
        deviceListView.setAdapter(leDeviceListAdapter);

        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case ACCESS_LOCATION:
                if (hasAllPermissionGranted(grantResults)) {
                    Log.i("BT", "onRequestPermissionsResult: 用户允许权限");
                } else {
                    Log.i("BT", "onRequestPermissionsResult: 拒绝搜索设备权限");
                }
                break;
        }
    }

    private boolean hasAllPermissionGranted(int[] grantResults) {
        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                return false;
            }
        }
        return true;
    }

    private void scanLeDevice(final boolean enable) {

        int permissionCheck = 0;
        permissionCheck = this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        permissionCheck += this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            //未获得权限
            this.requestPermissions( // 请求授权
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    ACCESS_LOCATION);// 自定义常量,任意整型
            return;
        }

        if (enable) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (btWalletService != null) {
                        btWalletService.stopScanBtDevices(scanCallback);
                    }
                }
            }, SCAN_PERIOD);
            btWalletService.scanBtDevices(scanCallback);
        } else {
            btWalletService.stopScanBtDevices(scanCallback);
        }
    }
}
