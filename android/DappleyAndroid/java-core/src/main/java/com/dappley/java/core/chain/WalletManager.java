package com.dappley.java.core.chain;

import com.dappley.java.core.crypto.AesCipher;
import com.dappley.java.core.crypto.Bip39;
import com.dappley.java.core.po.Wallet;
import com.dappley.java.core.util.MnemonicLanguage;

import java.math.BigInteger;

/**
 * Utils of wallet.
 */
public class WalletManager {

    /**
     * Create a new wallet address with English.
     * @return Wallet
     */
    public static Wallet createWallet() {
        String mnemonic = Bip39.generateMnemonic();
        Wallet wallet = new Wallet(mnemonic);
        return wallet;
    }

    /**
     * Create a new wallet address with specified language.
     * @param mnemonicLanguage mnemonic language type
     * @return Wallet
     */
    public static Wallet createWallet(MnemonicLanguage mnemonicLanguage) {
        String mnemonic = Bip39.generateMnemonic(mnemonicLanguage);
        Wallet wallet = new Wallet(mnemonic, mnemonicLanguage);
        return wallet;
    }

    /**
     * Import wallet info by mnemonic words with English language type
     * @param mnemonic
     * @return Wallet
     */
    public static Wallet importWalletFromMnemonic(String mnemonic) {
        Wallet wallet = new Wallet(mnemonic);
        return wallet;
    }

    /**
     * Import wallet info by mnemonic words with specified language type
     * @param mnemonic
     * @param mnemonicLanguage mnemonic language type
     * @return Wallet
     */
    public static Wallet importWalletFromMnemonic(String mnemonic, MnemonicLanguage mnemonicLanguage) {
        Wallet wallet = new Wallet(mnemonic, mnemonicLanguage);
        return wallet;
    }

    /**
     * Import wallet info by privateKey
     * @param privateKey
     * @return Wallet
     */
    public static Wallet importWalletFromPrivateKey(String privateKey) {
        BigInteger privKey = new BigInteger(privateKey, 16);
        Wallet wallet = new Wallet(privKey);
        return wallet;
    }

    /**
     * Encrypt wallet data with AES.
     * @param wallet
     * @param password
     * @return Wallet encrypted wallet data
     */
    public static Wallet encryptWallet(Wallet wallet, String password) {
        if (wallet.getMnemonic() != null && wallet.getMnemonic().length() > 0) {
            String encrypted = AesCipher.encryptToHex(wallet.getMnemonic().getBytes(), password);
            wallet.setEncryptedMnemonic(encrypted);
        }
        if (wallet.getPrivateKey() != null) {
            String encrypted = AesCipher.encryptToHex(wallet.getPrivateKey().toByteArray(), password);
            wallet.setEncryptedPrivateKey(encrypted);
        }
        wallet.setPrivateKey(null);
        wallet.setMnemonic(null);
        return wallet;
    }

    /**
     * Decrypt wallet data.
     * @param wallet   encrypted wallet data
     * @param password
     * @return Wallet wallet data
     */
    public static Wallet decryptWallet(Wallet wallet, String password) {
        if (wallet.getEncryptedMnemonic() != null && wallet.getEncryptedMnemonic().length() > 0) {
            byte[] decrypted = AesCipher.decryptBytesFromHex(wallet.getEncryptedMnemonic(), password);
            if (decrypted != null) {
                wallet.setMnemonic(new String(decrypted));
            }
        }
        if (wallet.getEncryptedPrivateKey() != null && wallet.getEncryptedPrivateKey().length() > 0) {
            byte[] decrypted = AesCipher.decryptBytesFromHex(wallet.getEncryptedPrivateKey(), password);
            if (decrypted != null) {
                wallet.setPrivateKey(new BigInteger(decrypted));
            }
        }
        return wallet;
    }

}
