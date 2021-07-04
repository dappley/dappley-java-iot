package com.dappley.android.sdk.db;

import android.content.Context;

import com.dappley.java.core.po.Transaction;
import com.dappley.java.core.util.HexUtil;
import com.dappley.java.core.util.SerializeUtil;
import com.tencent.mmkv.MMKV;

import java.util.List;

/**
 * Database for Block transaction list.
 * <p>Base on MMKV key-value database. Constructor with context also create a mmkv database object.</p>
 * <p>Values would be temporary saved in cache memory and persisted into disk automatically.</p>
 */
public class TransactionDb {
    private static final String DB_NAME = "transaction";
    private Context context;
    private MMKV mmkv;

    /**
     * Constructor
     * @param context
     */
    public TransactionDb(Context context) {
        this.context = context;
        mmkv = MMKV.mmkvWithID(DB_NAME);
    }

    /**
     * Save object data to database.
     * @param blockHash block hash value
     * @param transactions list object
     * @return boolean true/false
     */
    public boolean save(byte[] blockHash, List<Transaction> transactions) {
        return save(HexUtil.toHex(blockHash), transactions);
    }

    /**
     * Save object data to database.
     * @param blockHash block hash value in Hex format
     * @param transactions list object
     * @return boolean true/false
     */
    public boolean save(String blockHash, List<Transaction> transactions) {
        boolean success = false;
        try {
            mmkv.encode(blockHash, SerializeUtil.encodeList(transactions, Transaction.class));
            success = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return success;
    }

    /**
     * Read object from database.
     * @param blockHash block hash value
     * @return Block If key does not exits, returns null.
     */
    public List<Transaction> get(byte[] blockHash) {
        return get(HexUtil.toHex(blockHash));
    }

    /**
     * Read object from database.
     * @param blockHash A String ID(Key)
     * @return Block If key does not exits, returns null.
     */
    public List<Transaction> get(String blockHash) {
        try {
            byte[] bytes = mmkv.decodeBytes(blockHash);
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            return SerializeUtil.decodeList(bytes, Transaction.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Remove transaction list by block hash
     * @param blockHash block hash
     */
    public void remove(String blockHash) {
        mmkv.removeValueForKey(blockHash);
    }

    /**
     * Remove a set of transaction by block hash
     * @param blockHashes block hash array
     */
    public void remove(String[] blockHashes) {
        mmkv.removeValuesForKeys(blockHashes);
    }

    /**
     * Clear all data
     */
    public void clearAll() {
        mmkv.clearAll();
    }
}
