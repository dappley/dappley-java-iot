package com.dappley.android.sdk;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.dappley.android.sdk.chain.BlockChainManager;
import com.dappley.android.sdk.chain.UtxoManager;
import com.dappley.android.sdk.config.Configuration;
import com.dappley.android.sdk.net.LocalDataProvider;
import com.dappley.android.sdk.service.LocalBlockService;
import com.dappley.android.sdk.util.Asserts;
import com.dappley.java.core.chain.TransactionManager;
import com.dappley.java.core.chain.WalletManager;
import com.dappley.java.core.net.DataProvider;
import com.dappley.java.core.net.ProtocalProvider;
import com.dappley.java.core.net.ProtocalProviderBuilder;
import com.dappley.java.core.net.RemoteDataProvider;
import com.dappley.java.core.net.TransactionSender;
import com.dappley.java.core.po.ContractQueryResult;
import com.dappley.java.core.po.SendTxResult;
import com.dappley.java.core.po.ServerNode;
import com.dappley.java.core.po.Transaction;
import com.dappley.java.core.po.Utxo;
import com.dappley.java.core.po.Wallet;
import com.dappley.java.core.service.DeviceService;
import com.dappley.java.core.service.DeviceWalletService;
import com.dappley.java.core.util.AddressUtil;
import com.dappley.java.core.util.HashUtil;
import com.dappley.java.core.util.HexUtil;
import com.dappley.java.core.util.MnemonicLanguage;
import com.dappley.java.core.util.ObjectUtils;

import java.math.BigInteger;
import java.util.List;

/**
 * Dappley Android Sdk client.
 * <p>
 *     Provides different method to synchronize block information from neighbor chain nodes.
 * </p>
 * <p>
 *    DataMode.LOCAL_STORAGE: use local storage with database frame MMKV.
 *    DataMode.REMOTE_ONLINE: use real data from neighbor chain node. This may cause info delay while the node's data is not accurate.
 * </p>
 */
public class Dappley {
    private static final String TAG = "Dappley";
    private static Context context;
    private static DataProvider dataProvider;
    private static TransactionSender transactionSender;

    /**
     * Initialize client
     * @param context
     * @param dataMode choose a data reader mode
     */
    public static void init(Context context, DataMode dataMode) {
        Dappley.context = context;
        ServerNode[] serverNodes = Configuration.getInstance(context).getServerNodes();
        try {
            ProtocalProvider protocalProvider = null;
            if (dataMode == DataMode.LOCAL_STORAGE) {
                dataProvider = new LocalDataProvider(context);

                // start schedule service
                Intent intent = new Intent(context, LocalBlockService.class);
                context.startService(intent);
            } else if (dataMode == DataMode.REMOTE_ONLINE) {
                ProtocalProviderBuilder providerBuilder = new ProtocalProviderBuilder();
                providerBuilder.setType(RemoteDataProvider.RemoteProtocalType.RPC)
                        .setServerNodes(serverNodes);
                protocalProvider = providerBuilder.build();
                dataProvider = new RemoteDataProvider(protocalProvider);
            }
            transactionSender = new TransactionSender(protocalProvider);
        } catch (Exception e) {
            Log.e(TAG, "init: ", e);
        }
    }

    /**
     * Create a new wallet address by mnemonic with english language.
     * <p>Contains mnemonic and private key in wallet.</p>
     * @return Wallet
     */
    public static Wallet createWallet() {
        Wallet wallet = WalletManager.createWallet();
        return wallet;
    }

    /**
     * Create a new wallet address by mnemonic with specified language.
     * <p>Contains mnemonic and private key in wallet.</p>
     * @return Wallet
     */
    public static Wallet createWallet(MnemonicLanguage mnemonicLanguage) {
        Wallet wallet = WalletManager.createWallet(mnemonicLanguage);
        return wallet;
    }

    /**
     * Import wallet info from mnemonic words.
     * <p>Mnemonic are made up of 12 english words within blank as seperator.</p>
     * @param mnemonic words
     * @return Wallet
     */
    public static Wallet importWalletFromMnemonic(String mnemonic) {
        Wallet wallet = WalletManager.importWalletFromMnemonic(mnemonic);
        return wallet;
    }

    /**
     * Import wallet info from mnemonic words with specified language type.
     * <p>Mnemonic are made up of 12 english words within blank as seperator.</p>
     * @param mnemonic         words
     * @param mnemonicLanguage mnemonic language type
     * @return Wallet
     */
    public static Wallet importWalletFromMnemonic(String mnemonic, MnemonicLanguage mnemonicLanguage) {
        Wallet wallet = WalletManager.importWalletFromMnemonic(mnemonic, mnemonicLanguage);
        return wallet;
    }

    /**
     * Import wallet info from privateKey.
     * @param privateKey
     * @return Wallet
     */
    public static Wallet importWalletFromPrivateKey(String privateKey) {
        Wallet wallet = WalletManager.importWalletFromPrivateKey(privateKey);
        return wallet;
    }

    /**
     * Returns balance under current address.
     * @param address wallet address
     * @return BigInteger balance amount
     */
    public static BigInteger getWalletBalance(String address) {
        Asserts.init(context);
        return dataProvider.getBalance(address);
    }

    /**
     * Returns all balances in wallet list.
     * @param wallets wallet list
     * @return List<Wallet> wallet list with balance inside
     */
    public static List<Wallet> getWalletBalances(List<Wallet> wallets) {
        Asserts.init(context);
        BigInteger balance;
        for (Wallet wallet : wallets) {
            balance = dataProvider.getBalance(wallet.getAddress());
            wallet.setBalance(balance);
        }
        return wallets;
    }

    /**
     * Encrypt wallet data with AES.
     * @param wallet data
     * @param password
     * @return Wallet encrypted wallet data
     */
    public static Wallet encryptWallet(Wallet wallet, String password) {
        return WalletManager.encryptWallet(wallet, password);
    }

    /**
     * Decrypt wallet data.
     * @param wallet encrypted wallet data
     * @param password
     * @return Wallet wallet data
     */
    public static Wallet decryptWallet(Wallet wallet, String password) {
        return WalletManager.decryptWallet(wallet, password);
    }

    /**
     * Add a new wallet address into local storage.
     * @param address wallet address
     */
    public static void addAddress(String address) {
        Asserts.init(context);
        if (dataProvider instanceof LocalDataProvider) {
            BlockChainManager.addWalletAddress(context, address);
        }
    }

    /**
     * Remove a wallet address in local storage.
     * @param address wallet address
     */
    public static void removeAddress(String address) {
        Asserts.init(context);
        if (dataProvider instanceof LocalDataProvider) {
            BlockChainManager.removeWalletAddress(context, address);
        }
    }

    /**
     * Convert publicKey hash to wallet address
     * @param pubKeyHash publicKey hash
     * @return String wallet address
     */
    public static String publicKeyToAddress(byte[] pubKeyHash) {
        return AddressUtil.getAddressFromPubKeyHash(pubKeyHash);
    }

    /**
     * Valid the address is legal
     * @param address wallet or contract address
     * @return boolean is legal
     */
    public static boolean validateAddress(String address) {
        return AddressUtil.validateAddress(address);
    }

    /**
     * Valid the contract address is legal
     * @param address contract address
     * @return boolean is legal
     */
    public static boolean validateContractAddress(String address) {
        return AddressUtil.validateContractAddress(address);
    }

    /**
     * Returns paged utxo list.
     * @param address wallet address
     * @param pageIndex current page index
     * @param pageSize
     * @return List<Utxo>
     */
    public static List<Utxo> getUtxos(String address, int pageIndex, int pageSize) {
        Asserts.init(context);
        if (pageIndex <= 0 || pageSize <= 0) {
            return null;
        }
        List<Utxo> utxos = dataProvider.getUtxos(address);
        if (ObjectUtils.isEmpty(utxos)) {
            return null;
        }
        int pageNo = (pageIndex - 1) * pageSize;
        if (utxos.size() <= pageNo) {
            return null;
        }
        int toIndex = pageNo + pageSize;
        if (toIndex > utxos.size()) {
            toIndex = utxos.size();
        }
        List<Utxo> subList = utxos.subList(pageNo, toIndex);
        return subList;
    }

    /**
     * Send a new transaction to blockchain online.
     * @param fromAddress from user's address
     * @param toAddress to user's address
     * @param amount transferred amount
     * @param privateKey from user's privateKey
     * @param tip transaction tip
     * @return SendTxResult transaction committed result
     */
    public static SendTxResult sendTransaction(String fromAddress, String toAddress, BigInteger amount, BigInteger privateKey, BigInteger tip) {
        Asserts.init(context);
        if (!AddressUtil.validateUserAddress(fromAddress)) {
            throw new IllegalArgumentException("fromAddress is illegal !");
        }
        if (!AddressUtil.validateUserAddress(toAddress)) {
            throw new IllegalArgumentException("toAddress is illegal !");
        }
        return sendTransaction(fromAddress, toAddress, amount, privateKey, tip, BigInteger.ZERO, BigInteger.ZERO, null);
    }

    /**
     * Send a new transaction to blockchain online.
     * @param fromAddress from user's address
     * @param contractAddress contract's address
     * @param fee contract transaction fee
     * @param privateKey from user's privateKey
     * @param tip transaction tip
     * @param gasLimit max gas consumption
     * @param gasPrice gas price
     * @param contract contract content
     * @return SendTxResult transaction committed result
     */
    public static SendTxResult sendTransactionWithContract(String fromAddress, String contractAddress, BigInteger fee, BigInteger privateKey, BigInteger tip, BigInteger gasLimit, BigInteger gasPrice, String contract) {
        Asserts.init(context);
        if (!AddressUtil.validateUserAddress(fromAddress)) {
            throw new IllegalArgumentException("fromAddress is illegal !");
        }
        if (!AddressUtil.validateContractAddress(contractAddress)) {
            throw new IllegalArgumentException("contractAddress is illegal !");
        }
        if (contract == null) {
            throw new NullPointerException("contract cannot be null !");
        }
        return sendTransaction(fromAddress, contractAddress, fee, privateKey, tip, gasLimit, gasPrice, contract);
    }

    public static SendTxResult sendBleDeviceTransaction(String fromAddress, String toAddress, BigInteger amount,  BigInteger tip, DeviceService deviceService) {
        Asserts.init(context);
        if (!AddressUtil.validateUserAddress(fromAddress)) {
            throw new IllegalArgumentException("fromAddress is illegal !");
        }
        if (!AddressUtil.validateUserAddress(toAddress)) {
            throw new IllegalArgumentException("toAddress is illegal !");
        }
        return sendBleDeviceHashTransaction(fromAddress, toAddress, amount, tip, BigInteger.ZERO, BigInteger.ZERO, null, deviceService);
    }

    public static SendTxResult sendBleDeviceTransactionWithContract(String fromAddress, String toAddress, BigInteger amount,  BigInteger tip, String contract, DeviceService deviceService) {
        Asserts.init(context);
        if (!AddressUtil.validateUserAddress(fromAddress)) {
            throw new IllegalArgumentException("fromAddress is illegal !");
        }
        if (!AddressUtil.validateContractAddress(toAddress)) {
            throw new IllegalArgumentException("toAddress is illegal !");
        }
        return sendBleDeviceHashTransaction(fromAddress, toAddress, amount, tip, BigInteger.ZERO, BigInteger.ZERO, contract, deviceService);
    }

    public static SendTxResult sendBleDeviceDataTransactionWithContract(String fromAddress, String toAddress, BigInteger amount,  BigInteger tip, BigInteger gasLimit, BigInteger gasPrice, String contractTpl, DeviceService deviceService) {
        Asserts.init(context);
        if (!AddressUtil.validateUserAddress(fromAddress)) {
            throw new IllegalArgumentException("fromAddress is illegal !");
        }
        if (!AddressUtil.validateContractAddress(toAddress)) {
            throw new IllegalArgumentException("toAddress is illegal !");
        }
        return sendBleDeviceDataTransaction(fromAddress, toAddress, amount, tip, gasLimit, gasPrice, contractTpl, deviceService);
    }

    /**
     * Send a new transaction to blockchain online.
     * @param fromAddress from user's address
     * @param toAddress to address
     * @param amount transferred amount
     * @param privateKey from user's privateKey
     * @param tip transaction tip
     * @param gasLimit max gas consumption
     * @param gasPrice gas price
     * @param contract contract content
     * @return SendTxResult transaction committed result
     */
    private static SendTxResult sendTransaction(String fromAddress, String toAddress, BigInteger amount, BigInteger privateKey, BigInteger tip, BigInteger gasLimit, BigInteger gasPrice, String contract) {
        SendTxResult sendTxResult = new SendTxResult();
        if (ObjectUtils.isEmpty(fromAddress) || ObjectUtils.isEmpty(toAddress)) {
            sendTxResult.setCode(SendTxResult.CODE_ERROR_PARAM);
            sendTxResult.setMsg("Param error: fromAddress or toAddress is empty!");
            return sendTxResult;
        }
        List<Utxo> allUtxo = dataProvider.getUtxos(fromAddress);
        if (ObjectUtils.isEmpty(allUtxo)) {
            sendTxResult.setCode(SendTxResult.CODE_ERROR_BALANCE);
            sendTxResult.setMsg("Balance of fromAddress is not enough!");
            return sendTxResult;
        }
        BigInteger totalCost = getTotalUtxoCost(amount, tip, gasLimit, gasPrice);
        List<Utxo> utxos = UtxoManager.getSuitableUtxos(allUtxo, totalCost);
        if (ObjectUtils.isEmpty(utxos)) {
            sendTxResult.setCode(SendTxResult.CODE_ERROR_BALANCE);
            sendTxResult.setMsg("Balance of fromAddress is not enough!");
            return sendTxResult;
        }
        Transaction transaction = TransactionManager.newTransaction(utxos, toAddress, amount, privateKey, tip, gasLimit, gasPrice, contract);
        try {
            sendTxResult = transactionSender.sendTransaction(transaction);
        } catch (Exception e) {
            sendTxResult.setCode(SendTxResult.CODE_ERROR_EXCEPTION);
            sendTxResult.setMsg(e.getMessage());
            Log.e(TAG, "sendTransaction: ", e);
        }
        return sendTxResult;
    }

    private static SendTxResult sendBleDeviceHashTransaction(String fromAddress, String toAddress, BigInteger amount,
                                                             BigInteger tip, BigInteger gasLimit,
                                                             BigInteger gasPrice, String contract,
                                                             DeviceService deviceService) {
        SendTxResult sendTxResult = new SendTxResult();
        if (ObjectUtils.isEmpty(fromAddress) || ObjectUtils.isEmpty(toAddress)) {
            sendTxResult.setCode(SendTxResult.CODE_ERROR_PARAM);
            sendTxResult.setMsg("Param error: fromAddress or toAddress is empty!");
            return sendTxResult;
        }
        List<Utxo> allUtxo = dataProvider.getUtxos(fromAddress);
        if (ObjectUtils.isEmpty(allUtxo)) {
            sendTxResult.setCode(SendTxResult.CODE_ERROR_BALANCE);
            sendTxResult.setMsg("Balance of fromAddress is not enough!");
            return sendTxResult;
        }
        BigInteger totalCost = getTotalUtxoCost(amount, tip, gasLimit, gasPrice);
        List<Utxo> utxos = UtxoManager.getSuitableUtxos(allUtxo, totalCost);
        if (ObjectUtils.isEmpty(utxos)) {
            sendTxResult.setCode(SendTxResult.CODE_ERROR_BALANCE);
            sendTxResult.setMsg("Balance of fromAddress is not enough!");
            return sendTxResult;
        }

        DeviceWalletService deviceWalletService = new DeviceWalletService(deviceService);
        Transaction transaction = deviceWalletService.newTransaction(utxos, toAddress, amount, tip, gasLimit, gasPrice, contract);
        try {
            sendTxResult = transactionSender.sendTransaction(transaction);
        } catch (Exception e) {
            sendTxResult.setCode(SendTxResult.CODE_ERROR_EXCEPTION);
            sendTxResult.setMsg(e.getMessage());
            Log.e(TAG, "sendTransaction: ", e);
        }
        return sendTxResult;
    }

    private static SendTxResult sendBleDeviceDataTransaction(String fromAddress, String toAddress, BigInteger amount,
                                                             BigInteger tip, BigInteger gasLimit,
                                                             BigInteger gasPrice, String contractTpl,
                                                             DeviceService deviceService) {
        SendTxResult sendTxResult = new SendTxResult();
        if (ObjectUtils.isEmpty(fromAddress) || ObjectUtils.isEmpty(toAddress)) {
            sendTxResult.setCode(SendTxResult.CODE_ERROR_PARAM);
            sendTxResult.setMsg("Param error: fromAddress or toAddress is empty!");
            return sendTxResult;
        }
        List<Utxo> allUtxo = dataProvider.getUtxos(fromAddress);
        if (ObjectUtils.isEmpty(allUtxo)) {
            sendTxResult.setCode(SendTxResult.CODE_ERROR_BALANCE);
            sendTxResult.setMsg("Balance of fromAddress is not enough!");
            return sendTxResult;
        }
        BigInteger totalCost = getTotalUtxoCost(amount, tip, gasLimit, gasPrice);
        List<Utxo> utxos = UtxoManager.getSuitableUtxos(allUtxo, totalCost);
        if (ObjectUtils.isEmpty(utxos)) {
            sendTxResult.setCode(SendTxResult.CODE_ERROR_BALANCE);
            sendTxResult.setMsg("Balance of fromAddress is not enough!");
            return sendTxResult;
        }

        DeviceWalletService deviceWalletService = new DeviceWalletService(deviceService);
        Transaction transaction = deviceWalletService.newDeviceDataTransaction(utxos, toAddress, amount, tip, gasLimit, gasPrice, contractTpl);
        try {
            sendTxResult = transactionSender.sendTransaction(transaction);
        } catch (Exception e) {
            sendTxResult.setCode(SendTxResult.CODE_ERROR_EXCEPTION);
            sendTxResult.setMsg(e.getMessage());
            Log.e(TAG, "sendTransaction: ", e);
        }
        return sendTxResult;
    }

    /**
     * Returns total cost of utxo in current tx
     * @param amount transfer amout
     * @param tip transaction tip
     * @param gasLimit contract execution gas limit
     * @param gasPrice contract execution gas price
     * @return BigInterger total cost of utxo values
     */
    private static BigInteger getTotalUtxoCost(BigInteger amount, BigInteger tip, BigInteger gasLimit, BigInteger gasPrice) {
        if (amount == null) {
            return BigInteger.ZERO;
        }
        BigInteger total = amount;
        if (tip != null) {
            total = total.add(tip);
        }
        if (gasLimit != null && gasPrice != null) {
            total = total.add(gasLimit.multiply(gasPrice));
        }
        return total;
    }

    /**
     * Create a new contract address
     * @return String contract address
     */
    public static String createContractAddress() {
        return AddressUtil.createContractAddress();
    }

    /**
     * Return estimated gas count
     * @param fromAddress from address
     * @param privateKey from address's private key
     * @param contractAddress contract address
     * @param contract contract content
     * @return BigInteger gas count
     */
    public static BigInteger estimateGas(String fromAddress, BigInteger privateKey, String contractAddress, String contract) {
        Asserts.init(context);
        if (!AddressUtil.validateUserAddress(fromAddress)) {
            throw new IllegalArgumentException("fromAddress is illegal !");
        }
        if (!AddressUtil.validateContractAddress(contractAddress)) {
            throw new IllegalArgumentException("contractAddress is illegal !");
        }
        if (contract == null) {
            throw new NullPointerException("contract cannot be null !");
        }
        List<Utxo> allUtxo = dataProvider.getUtxos(fromAddress);
        if (ObjectUtils.isEmpty(allUtxo)) {
            return BigInteger.ZERO;
        }
        BigInteger amount = BigInteger.ONE;
        BigInteger tip = BigInteger.ZERO;
        BigInteger gasLimit = BigInteger.ZERO;
        BigInteger gasPrice = BigInteger.ZERO;
        List<Utxo> utxos = UtxoManager.getSuitableUtxos(allUtxo, amount);
        if (ObjectUtils.isEmpty(utxos)) {
            return BigInteger.ZERO;
        }
        Transaction transaction = TransactionManager.newTransaction(utxos, contractAddress, amount, privateKey, tip, gasLimit, gasPrice, contract);
        return dataProvider.estimateGas(transaction);
    }


    public static BigInteger estimateGas(String fromAddress, String contractAddress, String contractTpl, DeviceService deviceService) {
        Asserts.init(context);
        if (!AddressUtil.validateUserAddress(fromAddress)) {
            throw new IllegalArgumentException("fromAddress is illegal !");
        }
        if (!AddressUtil.validateContractAddress(contractAddress)) {
            throw new IllegalArgumentException("contractAddress is illegal !");
        }
        if (contractTpl == null) {
            throw new NullPointerException("contract cannot be null !");
        }
        List<Utxo> allUtxo = dataProvider.getUtxos(fromAddress);
        if (ObjectUtils.isEmpty(allUtxo)) {
            return BigInteger.ZERO;
        }
        BigInteger amount = BigInteger.ONE;
        BigInteger tip = BigInteger.ZERO;
        BigInteger gasLimit = BigInteger.ZERO;
        BigInteger gasPrice = BigInteger.ZERO;
        List<Utxo> utxos = UtxoManager.getSuitableUtxos(allUtxo, amount);
        if (ObjectUtils.isEmpty(utxos)) {
            return BigInteger.ZERO;
        }

        DeviceWalletService deviceWalletService = new DeviceWalletService(deviceService);
        Transaction transaction = deviceWalletService.newDeviceDataTransaction(utxos, contractAddress, amount, tip, gasLimit, gasPrice, contractTpl);

        Log.w(TAG, String.format("estimateGas Transaction transaction hash %s", HexUtil.toHex(transaction.getGenerateIdBytes())));

        BigInteger pubkey = new BigInteger(1, transaction.getTxInputs().get(0).getPublicKey());
        byte[] pubKeyHash = HashUtil.getUserPubKeyHash(pubkey);

        Log.w(TAG, String.format("estimateGas Transaction PubkeyBytes%s hash %s",
                HexUtil.toHex(transaction.getTxInputs().get(0).getPublicKey()),
                HexUtil.toHex(pubKeyHash)));

        return dataProvider.estimateGas(transaction);
    }

    /**
     * Returns current gas price
     * @return BigInteger gas price
     */
    public static BigInteger getGasPrice() {
        return dataProvider.getGasPrice();
    }

    /**
     * Returns data storage query result from blockchain
     * <p>If key is not null, it will return the value of key in storage if there exists.</p>
     * <p>If key is null and value is not null, it still return relevant key and value in storage if there exists.</p>
     * @param contractAddress contract address
     * @param key             storage key
     * @param value           storage value
     * @return ContractQueryResult result
     */
    public static ContractQueryResult contractQuery(String contractAddress, String key, String value) {
        Asserts.init(context);
        if (!AddressUtil.validateContractAddress(contractAddress)) {
            throw new IllegalArgumentException("contractAddress is illegal !");
        }
        if ((key == null || key.length() == 0) && (value == null || value.length() == 0)) {
            throw new NullPointerException("key and value cannot be null at the same time !");
        }
        ContractQueryResult contractQueryResult = dataProvider.contractQuery(contractAddress, key, value);
        return contractQueryResult;
    }

    /**
     * Data storage mode
     * <p>We provides two type of data synchronize mode.
     *  <code>LOCAL_STORAGE</code> will use local disk space to save blockchain datas.
     *  <code>REMOTE_ONLINE</code> will use in-time data from neighbor nodes which is not always be creditable.
     * </p>
     */
    public enum DataMode {
        /**
         * All datas will be stored at local disk.
         */
        LOCAL_STORAGE,
        /**
         * All datas will be get from remove node in real time.
         */
        REMOTE_ONLINE
    }
}
