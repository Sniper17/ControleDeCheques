package com.controledecheques;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Small encrypted preference store backed by a non-exportable Android Keystore AES key. */
final class SecureStore {
    private static final String PREFS = "cc_secure_v1";
    private static final String ALIAS = "com.controledecheques.localdata.v1";
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private SecureStore() {}

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEY_STORE);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }

    static synchronized boolean contains(Context context, String name) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(name);
    }

    static synchronized String get(Context context, String name) {
        try {
            String packed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(name, null);
            if (packed == null) return null;
            String[] parts = packed.split(":", 2);
            if (parts.length != 2) return null;
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] ciphertext = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) { return null; }
    }

    static synchronized boolean put(Context context, String name, String value) {
        if (name == null || value == null || name.length() > 80 || value.length() > 2_000_000) return false;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            String packed = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
                    Base64.encodeToString(ciphertext, Base64.NO_WRAP);
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(name, packed).commit();
        } catch (Exception e) { return false; }
    }

    static synchronized void remove(Context context, String name) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(name).commit();
    }

    static synchronized void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEY_STORE);
            ks.load(null);
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS);
        } catch (Exception ignored) { }
    }
}
