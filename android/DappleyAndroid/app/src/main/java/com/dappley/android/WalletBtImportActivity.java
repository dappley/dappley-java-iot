package com.dappley.android;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.dappley.android.bleservice.service.BleWallet;
import com.dappley.android.bleservice.service.BleWalletCallback;
import com.dappley.android.listener.BtnBackListener;
import com.dappley.android.bleservice.service.BleWalletService;
import com.dappley.android.util.Constant;
import com.dappley.android.util.DuplicateUtil;
import com.dappley.android.util.StorageUtil;
import com.dappley.java.core.po.Wallet;
import com.dappley.java.core.util.AddressUtil;

import java.math.BigInteger;
import java.util.concurrent.CompletableFuture;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;


public class WalletBtImportActivity extends AppCompatActivity {
    private static final String TAG = WalletBtImportActivity.class.getSimpleName();

    @BindView(R.id.txt_title)
    TextView tvTitle;
    @BindView(R.id.tv_name)
    TextView tvName;
    @BindView(R.id.bt_address)
    TextView btAddress;
    @BindView(R.id.tv_address)
    TextView tvAddress;
    @BindView(R.id.tv_publickey)
    TextView tvPublicKey;

    private Wallet wallet;
    private BleWalletService btWalletService;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            btWalletService = ((BleWalletService.LocalBinder) service).getService();
            btWalletService.registerWalletCb(walletCallback);
            runOnUiThread(()-> {
                btWalletService.initialize(wallet.getBtAddress());
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            btWalletService.unRegisterWalletCb(walletCallback);
            btWalletService = null;
        }
    };

    private final BleWalletCallback walletCallback =  new BleWalletCallback() {
        @Override
        public void onWalletStatusChanged(int status, String address) {
            if (wallet.getBtAddress().equals(address) == false) {
                return;
            }
            if (status == BleWalletCallback.WALLET_FAILED) {
                Toast.makeText(WalletBtImportActivity.this, R.string.bt_failed, Toast.LENGTH_SHORT).show();
                return;
            } else if (status == BleWalletCallback.WALLET_INITIALIZED) {
                CompletableFuture<byte[]> pubkeyFuture = btWalletService.readWalletPublicKey(wallet.getBtAddress());

                pubkeyFuture.exceptionally((e)-> {
                    return null;
                }).thenAccept((bytes)-> {
                    if (bytes == null) {
                        Toast.makeText(WalletBtImportActivity.this, "Read pubkey failed", Toast.LENGTH_SHORT);
                        Log.w(TAG, "Read Pubkey failed");
                    } else {
                        BigInteger pubkey = new BigInteger(1, bytes);
                        wallet.setPublicKey(pubkey);
                        wallet.setAddress(AddressUtil.getUserAddress(pubkey));
                        runOnUiThread(()-> {
                            tvPublicKey.setText(pubkey.toString());
                            tvAddress.setText(wallet.getAddress());
                        });
                    }
                });
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet_import_bt);

        ButterKnife.bind(this);

        initView();

        initData();

        Intent gattServiceIntent = new Intent(this, BleWalletService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        wallet.setBtAddress(getIntent().getExtras().getString("address"));
        wallet.setBtWallet(true);
        btAddress.setText(wallet.getBtAddress());
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
    public void onDestroy() {
        super.onDestroy();

        if (btWalletService != null) {
            btWalletService.unRegisterWalletCb(walletCallback);
        }

        if (mServiceConnection != null) {
            unbindService(mServiceConnection);
        }

        setResult(ScanBluetoothActivity.REQUEST_FINISH);
    }

    private void initView() {
        tvTitle.setText(R.string.title_import_bt_wallet);
        tvName.setText("Bluetooth Wallet");
    }

    private void initData() {
        wallet = new Wallet();
    }

    @OnClick(R.id.btn_save)
    void saveToLocal() {
        if (DuplicateUtil.dupClickCheck()) {
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, R.string.note_permittion_read, Toast.LENGTH_SHORT).show();
            }
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, Constant.REQ_PERM_STORAGE);
            return;
        }
        try {
            StorageUtil.saveWallet(this, wallet);
            Intent intent = new Intent(Constant.BROAD_WALLET_LIST_UPDATE);
            intent.putExtra("type", Constant.REQ_WALLET_CREATE);
            intent.putExtra("address", wallet.getAddress());
            sendBroadcast(intent);
            finish();
        } catch (Exception e) {
            Log.w(TAG, "Save wallet failed", e);
        }
    }

    @OnClick(R.id.btn_back)
    void onBackBtn() {
        if (btWalletService != null) {
            btWalletService.disconnect(wallet.getBtAddress());
        }
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case Constant.REQ_PERM_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    try {
                        StorageUtil.saveWallet(this, wallet);
                    } catch (Exception e) {
                        Log.w(TAG, "Save wallet failed", e);
                    }
                } else {
                    Toast.makeText(this, R.string.note_permittion_read, Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }
}
