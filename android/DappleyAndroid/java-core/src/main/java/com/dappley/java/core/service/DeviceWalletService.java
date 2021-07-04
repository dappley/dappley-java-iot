package com.dappley.java.core.service;

import com.dappley.java.core.chain.TransactionManager;
import com.dappley.java.core.po.DeviceInput;
import com.dappley.java.core.po.DeviceResult;
import com.dappley.java.core.po.Transaction;
import com.dappley.java.core.po.TxInput;
import com.dappley.java.core.po.TxOutput;
import com.dappley.java.core.po.Utxo;
import com.dappley.java.core.util.AddressUtil;
import com.dappley.java.core.util.ByteUtil;
import com.dappley.java.core.util.HashUtil;
import com.dappley.java.core.util.HexUtil;
import com.dappley.java.core.util.ObjectUtils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeviceWalletService {
    private DeviceService deviceService;

    public DeviceWalletService(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    public Transaction newTransaction(List<Utxo> utxos, String toAddress, BigInteger amount, BigInteger tip, BigInteger gasLimit, BigInteger gasPrice, String contract) {
        Transaction transaction = new Transaction();
        // add vin list and return the total amount of vin values
        BigInteger totalAmount = buildVin(transaction, utxos);

        BigInteger change = TransactionManager.calculateChange(totalAmount, amount, tip, gasLimit, gasPrice);

        // add vout list. If there is change is this transaction, vout list wound have two elements, or have just one to coin receiver.
        buildVout(transaction, toAddress, amount, change, contract);

        // add default tip value
        transaction.setTip(tip);

        // gas limit and price
        transaction.setGasLimit(gasLimit);
        transaction.setGasPrice(gasPrice);

        if (contract != null && contract.length() > 0) {
            transaction.setType(Transaction.TxTypeContract);
        } else {
            transaction.setType(Transaction.TxTypeNormal);
        }

        // generate Id
        transaction.createId();

        // sign transaction input list
        sign(transaction, utxos);

        byte[] ids = transaction.generateId();

        return transaction;
    }

    public Transaction newDeviceDataTransaction(List<Utxo> utxos, String toAddress, BigInteger amount, BigInteger tip, BigInteger gasLimit, BigInteger gasPrice, String contractTemplate) {
        Transaction transaction = new Transaction();

        BigInteger totalAmount = buildVin(transaction, utxos);

        BigInteger change = TransactionManager.calculateChange(totalAmount, amount, tip, gasLimit, gasPrice);

        // add vout list. If there is change is this transaction, vout list wound have two elements, or have just one to coin receiver.
        buildVout(transaction, toAddress, amount, change, contractTemplate);

        // add default tip value
        transaction.setTip(tip);

        // gas limit and price
        transaction.setGasLimit(gasLimit);
        transaction.setGasPrice(gasPrice);

        if (contractTemplate != null && contractTemplate.length() > 0) {
            transaction.setType(Transaction.TxTypeContract);
        } else {
            transaction.setType(Transaction.TxTypeNormal);
        }

        // sign transaction input list
        signWithDeviceData(transaction, utxos);

        // generate Id
        transaction.createId();

        return transaction;
    }

    private BigInteger buildVin(Transaction transaction, List<Utxo> utxos) {
        TxInput txInput;
        // save total amount value of all txInput value
        BigInteger totalAmount = BigInteger.ZERO;
        for (Utxo utxo : utxos) {
            if (utxo == null) {
                continue;
            }
            txInput = new TxInput();
            txInput.setTxId(utxo.getTxId());
            txInput.setVout(utxo.getVoutIndex());
            // add from publicKey value
            txInput.setPublicKey(deviceService.getPublicKey());
            totalAmount = totalAmount.add(utxo.getAmount());
            transaction.addTxInput(txInput);
        }
        return totalAmount;
    }

    private void buildVout(Transaction transaction, String toAddress, BigInteger amount, BigInteger change, String contract) {
        if (change != null && change.compareTo(BigInteger.ZERO) < 0) {
            return;
        }
        TxOutput txOutput = new TxOutput();
        if (ObjectUtils.isNotEmpty(contract)) {
            if (ObjectUtils.isEmpty(toAddress)) {
                toAddress = AddressUtil.createContractAddress();
            }
            // set contract output
            txOutput.setContract(contract);
            txOutput.setPublicKeyHash(HashUtil.getPublicKeyHash(toAddress));
            txOutput.setValue(ByteUtil.bigInteger2Bytes(BigInteger.ZERO));
            transaction.addTxOutput(txOutput);
        }
        // send to toAddress
        txOutput = new TxOutput();
        txOutput.setPublicKeyHash(HashUtil.getPublicKeyHash(toAddress));
        txOutput.setValue(ByteUtil.bigInteger2Bytes(amount));
        transaction.addTxOutput(txOutput);

        // if change is greater than 0, we need to give change to from address
        if (change != null && change.compareTo(BigInteger.ZERO) > 0) {
            txOutput = new TxOutput();
            // set from address's pubKeyHash
            byte[] myPubKeyHash = HashUtil.getUserPubKeyHash(deviceService.getPublicKey());
            txOutput.setPublicKeyHash(myPubKeyHash);
            txOutput.setValue(ByteUtil.bigInteger2Bytes(change));
            transaction.addTxOutput(txOutput);
        }
    }

    private void sign(Transaction transaction, List<Utxo> utxos) {
        List<TxInput> txInputs = transaction.getTxInputs();
        if (ObjectUtils.isEmpty(txInputs)) {
            return;
        }
        // format previous transaction data
        Map<String, Utxo> utxoMap = TransactionManager.getPrevUtxos(utxos);

        // no need to validate inputs

        // get a trimedCopy of old transaction
        Transaction transactionCopy = transaction.trimedCopy();

        // calculate sign value
        buildSignValue(transaction, utxoMap, transactionCopy);
    }

    private void buildSignValue(Transaction transaction, Map<String, Utxo> utxoMap, Transaction transactionCopy) {
        List<TxInput> txCopyInputs = transactionCopy.getTxInputs();
        TxInput txCopyInput = null;
        byte[] oldPubKey = null;
        for (int i = 0; i < txCopyInputs.size(); i++) {
            txCopyInput = txCopyInputs.get(i);
            oldPubKey = txCopyInput.getPublicKey();
            Utxo utxo = utxoMap.get(HexUtil.toHex(txCopyInput.getTxId()) + "-" + txCopyInput.getVout());
            // temporarily add pubKeyHash to pubKey property
            txCopyInput.setPublicKey(utxo.getPublicKeyHash());

            // get deepClone's hash value
            byte[] txCopyHash = transactionCopy.generateId();

            // recover old pubKey
            txCopyInput.setPublicKey(oldPubKey);

            DeviceResult deviceResult = deviceService.signBytes(txCopyHash);

            // Update original transaction data with vin's signature.
            transaction.getTxInputs().get(i).setSignature(deviceResult.getSignature());
        }
    }

    private void signWithDeviceData(Transaction transaction, List<Utxo> utxos) {
        List<TxInput> txInputs = transaction.getTxInputs();
        if (ObjectUtils.isEmpty(txInputs)) {
            return;
        }
        // format previous transaction data
        Map<String, Utxo> utxoMap = TransactionManager.getPrevUtxos(utxos);

        // no need to validate inputs

        // get a trimedCopy of old transaction
        Transaction transactionCopy = transaction.trimedCopy();

        // calculate sign value
        buildSignValueWithDeviceData(transaction, utxoMap, transactionCopy);
    }

    private void buildSignValueWithDeviceData(Transaction transaction, Map<String, Utxo> utxoMap, Transaction transactionCopy) {
        List<TxInput> txCopyInputs = transactionCopy.getTxInputs();
        TxInput txCopyInput = null;
        byte[] oldPubKey = null;

        int index = 0;
        // We need get device data for generate first signature,
        if (transaction.getType() == Transaction.TxTypeContract &&
                transaction.getTxOutputs().get(0).getContract().contains("{}")) {
            index = 1;
            txCopyInput = txCopyInputs.get(0);
            oldPubKey = txCopyInput.getPublicKey();
            Utxo utxo = utxoMap.get(HexUtil.toHex(txCopyInput.getTxId()) + "-" + txCopyInput.getVout());
            // temporarily add pubKeyHash to pubKey property
            txCopyInput.setPublicKey(utxo.getPublicKeyHash());

            List<DeviceInput> inputs = buildDeviceInputs(transactionCopy);
            DeviceResult deviceResult = deviceService.signBytesWithDeviceData(inputs);
            // recover old pubKey
            txCopyInput.setPublicKey(oldPubKey);

            // Update original transaction data with vin's signature.
            transaction.getTxInputs().get(0).setSignature(deviceResult.getSignature());
            if (deviceResult.getDeviceData() != null) {
                String contract = transaction.getTxOutputs().get(0).getContract();
                for (byte[] deviceData: deviceResult.getDeviceData()) {
                    try {
                        contract = contract.replaceFirst("\\{\\}", new String(deviceData, "UTF-8"));
                    } catch (Exception e) {
                        log.error("Convert device data to string failed", e);
                    }
                }
                transaction.getTxOutputs().get(0).setContract(contract);
                transactionCopy.getTxOutputs().get(0).setContract(contract);
            }
        }

        for (; index < txCopyInputs.size(); index++) {
            txCopyInput = txCopyInputs.get(index);
            oldPubKey = txCopyInput.getPublicKey();
            Utxo utxo = utxoMap.get(HexUtil.toHex(txCopyInput.getTxId()) + "-" + txCopyInput.getVout());
            // temporarily add pubKeyHash to pubKey property
            txCopyInput.setPublicKey(utxo.getPublicKeyHash());

            // get deepClone's hash value
            byte[] txCopyHash = transactionCopy.generateId();

            // recover old pubKey
            txCopyInput.setPublicKey(oldPubKey);

            DeviceResult deviceResult = deviceService.signBytes(txCopyHash);

            // Update original transaction data with vin's signature.
            transaction.getTxInputs().get(index).setSignature(deviceResult.getSignature());
        }
    }

    private List<DeviceInput>  buildDeviceInputs(Transaction transaction) {
        List<DeviceInput> inputs = new ArrayList<>();
        // serialize transaction
        List<byte[]> bytesList = new LinkedList<>();

        // add vin
        List<TxInput> txInputs = transaction.getTxInputs();
        if (txInputs != null) {
            for (TxInput txInput : txInputs) {
                // txid
                bytesList.add(txInput.getTxId() == null ? null : txInput.getTxId());
                // vout
                bytesList.add(ByteUtil.int2Bytes(txInput.getVout()));
                // pubkey
                bytesList.add(txInput.getPublicKey() == null ? null : txInput.getPublicKey());
            }
        }

        // add vout
        List<TxOutput> txOutputs = transaction.getTxOutputs();
        if (txOutputs != null) {
            int startIndex = 0;
            if (transaction.getType() == Transaction.TxTypeContract) {
                startIndex = 1;
                TxOutput contractOutput = txOutputs.get(0);
                bytesList.add(contractOutput.getValue() == null ? null : contractOutput.getValue());
                // pubKeyHash
                bytesList.add(contractOutput.getPublicKeyHash() == null ? null : contractOutput.getPublicKeyHash());
                Pattern p = Pattern.compile("(.*?)(\\{\\})(.*)");
                String text = contractOutput.getContract();
                Matcher m = p.matcher(text);
                int paramIndex = 0;
                while (m.find()) {
                    bytesList.add(ByteUtil.string2Bytes(m.group(1)));
                    DeviceInput fixedInput = new DeviceInput(DeviceInput.INPUT_DATA_FIXED, ByteUtil.joinBytes(bytesList));
                    inputs.add(fixedInput);
                    byte[] paramBytes = new byte[2];
                    paramBytes[0] = (byte)(paramIndex & 0xFF);
                    paramBytes[1] = (byte)((paramIndex & 0xFF00) >> 8);
                    DeviceInput deviceInput = new DeviceInput(DeviceInput.INPUT_DATA_DEVICE, paramBytes);
                    inputs.add(deviceInput);
                    // Create new buffer
                    bytesList = new LinkedList<>();

                    text = m.group(3);
                    m = p.matcher(text);
                    paramIndex++;
                }
                bytesList.add(ByteUtil.string2Bytes(text));
            }

            for (; startIndex < txOutputs.size(); startIndex++) {
                TxOutput txOutput = txOutputs.get(startIndex);
                // value
                bytesList.add(txOutput.getValue() == null ? null : txOutput.getValue());
                // pubKeyHash
                bytesList.add(txOutput.getPublicKeyHash() == null ? null : txOutput.getPublicKeyHash());
                bytesList.add(txOutput.getContract() == null ? null : ByteUtil.string2Bytes(txOutput.getContract()));
            }
        }
        // add tip
        if (transaction.getTip() != null) {
            bytesList.add(ByteUtil.bigInteger2Bytes(transaction.getTip()));
        }

        if (transaction.getGasLimit() != null) {
            bytesList.add(ByteUtil.bigInteger2Bytes(transaction.getGasLimit()));
        }
        if (transaction.getGasPrice() != null) {
            bytesList.add(ByteUtil.bigInteger2Bytes(transaction.getGasPrice()));
        }
        if (transaction.getType() > Transaction.TxTypeDefault) {
            bytesList.add(ByteUtil.int2Bytes(transaction.getType()));
        }

        DeviceInput fixedInput = new DeviceInput(DeviceInput.INPUT_DATA_FIXED, ByteUtil.joinBytes(bytesList));
        inputs.add(fixedInput);

        return inputs;
    }

}
