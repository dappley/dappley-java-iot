package com.dappley.android.bleservice.service;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.dappley.java.core.po.DeviceInput;
import com.dappley.java.core.po.DeviceResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


public class BleWalletService extends Service {
    static class BleContext {
        BluetoothGatt gatt;
        int           mtu;
        int           seq;
        BleDataSender sender;
        BleServiceContinuation continuation;
    }

    public class LocalBinder extends Binder {
        public BleWalletService getService() {
            return BleWalletService.this;
        }
    }

    private final static String TAG = BleWalletService.class.getSimpleName();
    private final static int    DEFAULT_MTU_SIZE = 128;
    private final static int    BLOCKCHAIN_NOTIFY_PACKET_STATUS = 1;
    private final static int    BLOCKCHAIN_NOTIFY_SIGNATURE_STATUS = 2;

    //"44617070-776f-726b-7353-696700000000"
    public static final  String WALLET_SERVICE = "00000000-6769-5373-6b72-6f7770706144";
    public static final  String PUB_KEY_UUID = "01000000-6769-5373-6b72-6f7770706144";
    public static final  String SIG_UUID = "02000000-6769-5373-6b72-6f7770706144";
    public static final  String SIG_READ_UUID = "03000000-6769-5373-6b72-6f7770706144";

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private HashMap<String, BleContext> connectedGatts = new HashMap<>();
    private List<BleWalletCallback> walletCallbacks = new LinkedList<>();

    private final IBinder mBinder = new LocalBinder();
    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            // Status not success, report failed
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onWalletStateChanged(gatt.getDevice().getAddress(), BleWalletCallback.WALLET_FAILED);
                return;
            }

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                BleContext context = new BleContext();
                Random random = new Random();
                context.continuation = null;
                context.gatt = gatt;
                context.mtu = 0;
                context.seq = random.nextInt();
                connectedGatts.put(gatt.getDevice().getAddress(), context);
                onWalletStateChanged(gatt.getDevice().getAddress(),  BleWalletCallback.WALLET_CONNECTED);
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                connectedGatts.remove(gatt.getDevice().getAddress());
                onWalletStateChanged(gatt.getDevice().getAddress(),  BleWalletCallback.WALLET_DISCONNECTED);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Bluetooth service discovered success");
                enableSignatureReadNotify(gatt);
            } else {
                onWalletStateChanged(gatt.getDevice().getAddress(), BleWalletCallback.WALLET_FAILED);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            BleContext context = connectedGatts.get(gatt.getDevice().getAddress());
            if (context == null || context.continuation == null) {
                return;
            }
            byte[] data = characteristic.getValue();
            context.continuation.onReadData(BluetoothGatt.GATT_SUCCESS, data);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {

            Log.i(TAG, "Write characteristic success");

            BleContext context = connectedGatts.get(gatt.getDevice().getAddress());
            if (context == null || context.continuation == null) {
                return;
            }

            context.continuation.onWriteData(status, null);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.i(TAG, "Notify characteristic");
            BleContext context = connectedGatts.get(gatt.getDevice().getAddress());
            if (context == null || context.continuation == null) {
                return;
            }
            byte[] data = characteristic.getValue();
            context.continuation.onNotifyData(BluetoothGatt.GATT_SUCCESS, data);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.i(TAG, String.format("Bluetooth service get mtu:%d success callback", mtu));
            BleContext context = connectedGatts.get(gatt.getDevice().getAddress());
            if (context == null) {
                return;
            }

            context.mtu = mtu;
            onWalletStateChanged(gatt.getDevice().getAddress(), BleWalletCallback.WALLET_INITIALIZED);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.i(TAG, "Bluetooth service writeDescriptor callback");
            super.onDescriptorWrite(gatt, descriptor, status);

            BleContext context = connectedGatts.get(gatt.getDevice().getAddress());
            if (context == null) {
                return;
            }
            // exchange mtu
            context.gatt.requestMtu(DEFAULT_MTU_SIZE);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        initialize();
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        close();
        super.onDestroy();
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        if (connectedGatts.containsKey(address)) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            return false;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.

        onWalletStateChanged(address, BleWalletCallback.WALLET_CONNECTING);
        BluetoothGatt btGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        return true;
    }

    public boolean initialize(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        BleContext context = connectedGatts.get(address);
        if (context == null) {
            Log.d(TAG, String.format("Device with address:%s not connected", address));
            return false;
        }

        //Discovery service , then enable notify
        context.gatt.discoverServices();

        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect(String address) {
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        if (!connectedGatts.containsKey(address)) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            return;
        }

        BleContext context = connectedGatts.get(address);

        onWalletStateChanged(address, BleWalletCallback.WALLET_DISCONNECTING);
        context.gatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        for (String address : connectedGatts.keySet()) {
            BleContext context = connectedGatts.get(address);
            context.gatt.disconnect();
        }

        connectedGatts.clear();
    }

    public void scanBtDevices(ScanCallback scanCallback) {
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            return;
        }

        List<ScanFilter> filters = new ArrayList<>();
        //ScanFilter.Builder filterBuilder = new ScanFilter.Builder();

        //filterBuilder.setServiceUuid(ParcelUuid.fromString(WALLET_SERVICE));
        //filters.add(filterBuilder.build());

        ScanSettings.Builder settingBuilder = new ScanSettings.Builder();
        scanner.startScan(filters, settingBuilder.build(), scanCallback);
    }

    public void stopScanBtDevices(ScanCallback scanCallback) {
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            return;
        }
        scanner.stopScan(scanCallback);
    }

    public void registerWalletCb(BleWalletCallback cb) {
        walletCallbacks.add(cb);
    }

    public void unRegisterWalletCb(BleWalletCallback cb) {
        walletCallbacks.remove(cb);
    }

    public CompletableFuture<byte[]> readWalletPublicKey(String address) {
        final CompletableFuture<byte[]> future = new CompletableFuture<>();
        final BleContext context = connectedGatts.get(address);
        if (context == null) {
            future.completeExceptionally(new RuntimeException("Not connected"));
            return future;
        }

        if (context.mtu == 0) {
            // MTU Size not exchanged
            future.completeExceptionally(new RuntimeException("MTU size unknown"));
            return future;
        }

        if (context.continuation != null) {
            // Has unfinished request
            future.completeExceptionally(new RuntimeException("Device busy"));
            return future;
        }

        BluetoothGattService walletService = context.gatt.getService(UUID.fromString(WALLET_SERVICE));

        context.continuation = new BleServiceContinuation() {
            @Override
            public void onReadData(int status, byte[] data) {
                // Send data to device failed
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    future.completeExceptionally(new RuntimeException("Read pubkey failed"));
                    context.continuation = null;
                    return;
                }

                future.complete(data);
                context.continuation = null;
            }

            @Override
            public void onNotifyData(int status, byte[] data) {

            }

            @Override
            public void onWriteData(int status, byte[] data) {

            }
        };

        if (!context.gatt.readCharacteristic(walletService.getCharacteristic(UUID.fromString(PUB_KEY_UUID)))) {
            future.completeExceptionally(new RuntimeException("Read pubkey failed"));
            context.continuation = null;
        }

        return future;
    }

    public CompletableFuture<DeviceResult> signHashData(String address, byte[] hash) {
        final CompletableFuture<DeviceResult> future = new CompletableFuture<>();
        final BleContext context = connectedGatts.get(address);
        if (context == null) {
            future.completeExceptionally(new RuntimeException("Not connected"));
            return future;
        }

        if (context.mtu == 0) {
            // MTU Size not exchanged
            future.completeExceptionally(new RuntimeException("MTU size unknown"));
            return future;
        }

        if (context.continuation != null) {
            // Has unfinished request
            future.completeExceptionally(new RuntimeException("Device busy"));
            return future;
        }

        context.seq += 1;

        BluetoothGattService walletService = context.gatt.getService(UUID.fromString(WALLET_SERVICE));
        BluetoothGattCharacteristic characteristic = walletService.getCharacteristic(UUID.fromString(SIG_UUID));
        byte[] value = new byte[36];

        // 2Byte  1  Sign hash data
        value[0] = 1;
        value[1] = 0;

        // sha256 32 bytes
        value[2] = 32;
        value[3] = 0;

        System.arraycopy(hash, 0, value, 4, 32);

        final BleDataSender sender = new BleDataSender(context.seq, value, context.mtu, context.gatt, characteristic);
        characteristic.setValue(value);
        context.sender = sender;
        context.continuation = new BleServiceContinuation() {
            @Override
            public void onNotifyData(int status, byte[] data) {
                // Send data to device failed
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    signFail(future, context,"Send data to device failed");
                    return;
                }

                BleNotifyHeader notifyHeader = parseNotifyHeader(data);
                switch (notifyHeader.getType()) {
                    case BLOCKCHAIN_NOTIFY_PACKET_STATUS: {
                        BleTxDataNotify dataNotify = parseTxDataNotify(data);
                        if (dataNotify.getStatus() != 0) {
                            signFail(future, context,"Send invalid packet");
                            return;
                        }

                        if (dataNotify.getSeq() != context.sender.getSeq() ||
                            dataNotify.getOffset() != context.sender.getNextOffset()) {
                            signFail(future, context,"Packet seq number mismatch");
                            return;
                        }

                        context.sender.finishPacket();
                        break;
                    }

                    case BLOCKCHAIN_NOTIFY_SIGNATURE_STATUS: {
                        BleTxSignNotify signNotify = parseTxSignNotify(data);
                        if (signNotify.getError() != 0) {
                            signFail(future, context, String.format("Sign failed %d", signNotify.getError()));
                            return;
                        }

                        DeviceResult result = new DeviceResult();
                        result.setSignature(signNotify.getSignature());
                        result.setDeviceData(signNotify.getValues());
                        future.complete(result);

                        context.sender = null;
                        context.continuation = null;
                        break;
                    }

                    default: {
                        signFail(future, context,"Receive invalid ble data");
                        return;
                    }
                }
            }

            @Override
            public void onReadData(int status, byte[] data) {

            }

            @Override
            public void onWriteData(int status, byte[] data) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    signFail(future, context,"Send data to device failed");
                    return;
                }

                if (!context.sender.isFinished()) {
                    context.sender.sendPacket();
                }
            }
        };

        if (!sender.sendPacket()) {
            context.continuation = null;
            context.sender = null;
            future.completeExceptionally(new RuntimeException("Send to device failed"));
            return future;
        }

        return future;
    }

    public CompletableFuture<DeviceResult> signWithDeviceData(String address, List<DeviceInput> inputs) {
        final CompletableFuture<DeviceResult> future = new CompletableFuture<>();
        final BleContext context = connectedGatts.get(address);
        if (context == null) {
            future.completeExceptionally(new RuntimeException("Not connected"));
            return future;
        }

        if (context.mtu == 0) {
            // MTU Size not exchanged
            future.completeExceptionally(new RuntimeException("MTU size unknown"));
            return future;
        }

        if (context.continuation != null) {
            // Has unfinished request
            future.completeExceptionally(new RuntimeException("Device busy"));
            return future;
        }

        context.seq += 1;

        BluetoothGattService walletService = context.gatt.getService(UUID.fromString(WALLET_SERVICE));
        BluetoothGattCharacteristic characteristic = walletService.getCharacteristic(UUID.fromString(SIG_UUID));
        int dataSize = getSignDeviceDataSize(inputs);
        byte[] value = new byte[4 + dataSize];

        // 2Byte  1  Sign raw transaction data
        value[0] = 0;
        value[1] = 0;

        // sha256 32 bytes
        value[2] = (byte)(dataSize & 0xFF);
        value[3] = (byte)((dataSize & 0xFF00) >> 8);
        fillSignDeviceData(value, inputs);

        final BleDataSender sender = new BleDataSender(context.seq, value, context.mtu, context.gatt, characteristic);
        characteristic.setValue(value);
        context.sender = sender;
        context.continuation = new BleServiceContinuation() {
            @Override
            public void onNotifyData(int status, byte[] data) {
                // Send data to device failed
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    signFail(future, context,"Send data to device failed");
                    return;
                }

                BleNotifyHeader notifyHeader = parseNotifyHeader(data);
                switch (notifyHeader.getType()) {
                    case BLOCKCHAIN_NOTIFY_PACKET_STATUS: {
                        BleTxDataNotify dataNotify = parseTxDataNotify(data);
                        if (dataNotify.getStatus() != 0) {
                            signFail(future, context,"Send invalid packet");
                            return;
                        }

                        if (dataNotify.getSeq() != context.sender.getSeq() ||
                                dataNotify.getOffset() != context.sender.getNextOffset()) {
                            signFail(future, context,"Packet seq number mismatch");
                            return;
                        }

                        context.sender.finishPacket();
                        break;
                    }

                    case BLOCKCHAIN_NOTIFY_SIGNATURE_STATUS: {
                        BleTxSignNotify signNotify = parseTxSignNotify(data);
                        if (signNotify.getError() != 0) {
                            signFail(future, context, String.format("Sign failed %d", signNotify.getError()));
                            return;
                        }

                        DeviceResult result = new DeviceResult();
                        result.setSignature(signNotify.getSignature());
                        result.setDeviceData(signNotify.getValues());

                        future.complete(result);

                        context.sender = null;
                        context.continuation = null;
                        break;
                    }

                    default: {
                        signFail(future, context,"Receive invalid ble data");
                        return;
                    }
                }
            }

            @Override
            public void onReadData(int status, byte[] data) {

            }

            @Override
            public void onWriteData(int status, byte[] data) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    signFail(future, context,"Send data to device failed");
                    return;
                }

                if (!context.sender.isFinished()) {
                    boolean result = context.sender.sendPacket();
                    Log.i(TAG, "Send packet result " + result);
                }
            }
        };

        if (!sender.sendPacket()) {
            context.continuation = null;
            context.sender = null;
            future.completeExceptionally(new RuntimeException("Send to device failed"));
            return future;
        }

        return future;
    }

    private void signFail(CompletableFuture<DeviceResult> future, BleContext context, String error) {
        Log.i(TAG, error);
        context.sender = null;
        context.continuation = null;
        context.seq += 1;
        future.completeExceptionally(new RuntimeException(error));
    }

    private void onWalletStateChanged(String address, int status) {
        for (BleWalletCallback cb : walletCallbacks) {
            cb.onWalletStatusChanged(status, address);
        }
    }

    private void enableSignatureReadNotify(BluetoothGatt gatt) {
        BluetoothGattService service = gatt.getService(UUID.fromString(WALLET_SERVICE));
        if (service == null) {
            Log.i(TAG, String.format("No service:%s found for bluetooth wallet", WALLET_SERVICE));
            return;
        }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(SIG_READ_UUID));
        if (characteristic == null) {
            Log.i(TAG, String.format("No characteristic:%s found for bluetooth wallet", SIG_READ_UUID));
            return;
        }

        gatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        if (!gatt.writeDescriptor(descriptor)) {
            Log.i(TAG, "Bluetooth service writeDescriptor failed");
        }
    }

    private int getSignDeviceDataSize(List<DeviceInput> inputs) {
        int size = 0;
        for (DeviceInput input: inputs) {
            //2 Bytes Type + 2 Bytes size
            size += 4 + input.getData().length;
        }
        return size;
    }

    private void fillSignDeviceData(byte[] data, List<DeviceInput> inputs) {
        int offset = 4;
        for (DeviceInput input: inputs) {
            // item type
            data[offset + 0] = (byte)input.getInputDataType();
            data[offset + 1] = 0;

            // item size
            data[offset + 2] = (byte)(input.getData().length & 0xFF);
            data[offset + 3] = (byte)((input.getData().length & 0xFF00) >> 8);
            System.arraycopy(input.getData(), 0,  data, offset + 4, input.getData().length);
            offset += 4 + input.getData().length;
        }
    }

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    private boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    private BleNotifyHeader parseNotifyHeader(byte[] data) {
        BleNotifyHeader header = new BleNotifyHeader();
        header.setType((data[0] & 0xFF) | (data[1] << 8));
        header.setSize((data[2] & 0xFF) | (data[3] << 8));
        return header;
    }

    private BleTxDataNotify parseTxDataNotify(byte[] data) {
        BleTxDataNotify dataNotify = new BleTxDataNotify();
        dataNotify.setSeq((data[4] & 0xFF) |
                ((data[5] & 0xFF) << 8) |
                ((data[6] & 0xFF) << 16) |
                ((data[7] & 0xFF) << 24));
        dataNotify.setOffset((data[8] & 0xFF)| ((data[9] & 0xFF) << 8));
        dataNotify.setStatus((data[10] & 0xFF) | ((data[11] & 0xFF) << 8));
        return dataNotify;
    }

    private BleTxSignNotify parseTxSignNotify(byte[] data) {
        BleTxSignNotify signNotify = new BleTxSignNotify();
        signNotify.setError((data[4] & 0xFF) | ((data[5] & 0xFF << 8)));
        signNotify.setFlags((data[6] & 0xFF) | ((data[7] & 0xFF << 8)));
        signNotify.setSignature(Arrays.copyOfRange(data, 8, 72));

        signNotify.setValues(new ArrayList<byte[]>());
        int valueOffset = 72;
        while (valueOffset < 108) {
            int size = 0;
            size = (int)(((data[valueOffset] & 0xFF) | ((data[valueOffset + 1] & 0xFF) << 8)));
            if (size == 0) {
                break;
            }

            byte value[] = new byte[size];
            System.arraycopy(data, valueOffset + 2, value, 0, size);
            signNotify.getValues().add(value);

            valueOffset += 2 + size;
        }

        return signNotify;
    }
}
