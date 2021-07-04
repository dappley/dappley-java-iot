package com.dappley.android.sdk.chain;

import android.content.Context;

import com.dappley.android.sdk.db.BlockChainDb;
import com.dappley.android.sdk.db.BlockDb;
import com.dappley.java.core.chain.BlockManager;
import com.dappley.java.core.po.Block;
import com.dappley.java.core.util.HexUtil;
import com.dappley.java.core.util.ObjectUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * Utils of BlockChain service.
 */
public class BlockChainManager {
    /**
     * Genesis block's hash
     */
    private static String genesisHash;

    /**
     * Initialize genesis block and save into block db.
     * @param context
     */
    public static void initGenesisBlock(Context context) {
        BlockChainDb blockChainDb = new BlockChainDb(context);
        BlockDb blockDb = new BlockDb(context);
        if (ObjectUtils.isNotEmpty(blockChainDb.getCurrentHash())
                && blockDb.get(blockChainDb.getCurrentHash()) != null) {
            return;
        }
        Block block = BlockManager.newGenesisBlock();
        if (block == null || block.getHeader() == null || block.getHeader().getHash() == null) {
            return;
        }
        String genesisHash = HexUtil.toHex(block.getHeader().getHash());
        blockChainDb.saveGenesisHash(genesisHash);
        blockChainDb.saveCurrentHash(genesisHash);
        blockDb.save(block);
    }

    /**
     * Returns the genesis hash value of chain
     * @param context
     * @return String genesis hash value
     */
    public static String getGenesisHash(Context context) {
        if (ObjectUtils.isNotEmpty(genesisHash)) {
            return genesisHash;
        }
        // first query from db
        BlockChainDb blockChainDb = new BlockChainDb(context);
        genesisHash = blockChainDb.getGenesisHash();
        if (ObjectUtils.isNotEmpty(genesisHash)) {
            return genesisHash;
        }
        // init genesis block
        initGenesisBlock(context);
        genesisHash = blockChainDb.getGenesisHash();
        return genesisHash;
    }

    /**
     * Add a wallet address into system db.
     * @param context
     * @param walletAddress user's wallet address
     */
    public static void addWalletAddress(Context context, String walletAddress) {
        if (ObjectUtils.isEmpty(walletAddress)) {
            return;
        }
        BlockChainDb blockChainDb = new BlockChainDb(context);
        Set<String> walletAddressSet = blockChainDb.getWalletAddressSet();
        if (walletAddressSet == null) {
            walletAddressSet = new HashSet<>();
        }
        if (walletAddressSet.contains(walletAddress)) {
            return;
        }
        // form related utxos
        UtxoManager.buildUserUtxo(context, walletAddress);

        blockChainDb.saveWalletAddress(walletAddress);
    }

    /**
     * Remove a wallet address into system db.
     * @param context
     * @param walletAddress user's wallet address
     */
    public static void removeWalletAddress(Context context, String walletAddress) {
        if (ObjectUtils.isEmpty(walletAddress)) {
            return;
        }
        BlockChainDb blockChainDb = new BlockChainDb(context);
        Set<String> walletAddressSet = blockChainDb.getWalletAddressSet();
        if (walletAddressSet == null) {
            walletAddressSet = new HashSet<>();
        }
        if (!walletAddressSet.contains(walletAddress)) {
            return;
        }
        walletAddressSet.remove(walletAddress);

        // remove related utxos
        UtxoManager.removeUserUtxo(context, walletAddress);
        blockChainDb.saveWalletAddressSet(walletAddressSet);
    }
}
