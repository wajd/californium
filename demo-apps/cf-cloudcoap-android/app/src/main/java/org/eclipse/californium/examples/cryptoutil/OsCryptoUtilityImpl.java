/*******************************************************************************
 * Copyright (c) 2019 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - Initial creation
 ******************************************************************************/
package org.eclipse.californium.examples.cryptoutil;

import android.annotation.TargetApi;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.util.Log;

import org.eclipse.californium.elements.util.Base64;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Enumeration;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;

@TargetApi(Build.VERSION_CODES.M)
public class OsCryptoUtilityImpl implements CryptoUtility {
    private static final String LOG_TAG = "crypto";

    private static final String PROVIDER = "AndroidKeyStore";

    private final String keyAlias;
    private final KeyStore keyStore;
    private final Cipher cipher;
    private SecretKey secretKey;

    public OsCryptoUtilityImpl(String keyAlias) throws GeneralSecurityException {
        this.keyAlias = keyAlias;
        cipher = Cipher.getInstance("AES/GCM/NoPadding");
        keyStore = KeyStore.getInstance(PROVIDER);
        try {
            keyStore.load(null);
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!alias.equals(keyAlias)) {
                Log.i(LOG_TAG, "remove alias: " + alias);
                keyStore.deleteEntry(alias);
            }
        }
    }

    @Override
    public synchronized boolean initKey() {
        boolean loaded = false;
        try {
            secretKey = (SecretKey) keyStore.getKey(keyAlias, null);
            loaded = secretKey != null;
            if (loaded) {
                byte[] encoded = secretKey.getEncoded();
                SecretKeyFactory factory = SecretKeyFactory.getInstance(secretKey.getAlgorithm(), PROVIDER);
                KeyInfo keyInfo = (KeyInfo) factory.getKeySpec(secretKey, KeyInfo.class);
                String security = encoded == null ? "OS" : "App";
                Log.i(LOG_TAG, "key loaded, hw " + keyInfo.isInsideSecureHardware() + ", " + security + " security.");
            } else {
                createKey();
            }
        } catch (GeneralSecurityException e) {
            Log.e(LOG_TAG, "error " + e.getMessage(), e);
        }
        return loaded;
    }

    private SecretKey getKey() {
        if (secretKey == null) {
            createKey();
        }
        return secretKey;
    }

    @Override
    public synchronized void createKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
            kg.init(
                    new KeyGenParameterSpec.Builder(keyAlias,
                            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build());
            secretKey = kg.generateKey();
            SecretKeyFactory factory = SecretKeyFactory.getInstance(secretKey.getAlgorithm(), PROVIDER);
            KeyInfo keyInfo = (KeyInfo) factory.getKeySpec(secretKey, KeyInfo.class);
            Log.i(LOG_TAG, "key created, hw " + keyInfo.isInsideSecureHardware());
            this.secretKey = secretKey;
        } catch (GeneralSecurityException e) {
            Log.e(LOG_TAG, "error " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void deleteKey() {
        try {
            keyStore.deleteEntry(keyAlias);
        } catch (KeyStoreException e) {
            Log.e(LOG_TAG, "error " + e.getMessage(), e);
        }
        secretKey = null;
    }

    @Override
    public synchronized String decrypt(String value) {
        try {
            byte[] data = Base64.decode(value);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            int ivLen = buffer.get();
            byte[] iv = new byte[ivLen];
            buffer.get(iv);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, getKey(), parameterSpec);
            data = new byte[buffer.remaining()];
            buffer.get(data);
            data = cipher.doFinal(data);
            return new String(data);
        } catch (GeneralSecurityException e) {
            Log.e(LOG_TAG, "error " + e.getMessage(), e);
        } catch (IOException e) {
            Log.e(LOG_TAG, "error " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public synchronized String encrypt(String value) {
        try {
            byte[] data = value.getBytes();
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            GCMParameterSpec ivParams = cipher.getParameters().getParameterSpec(GCMParameterSpec.class);
            byte[] iv = ivParams.getIV();
            data = cipher.doFinal(data);
            ByteBuffer buffer = ByteBuffer.allocate(1 + iv.length + data.length);
            buffer.put((byte) iv.length);
            buffer.put(iv);
            buffer.put(data);
            return Base64.encodeBytes(buffer.array());
        } catch (GeneralSecurityException e) {
            Log.e(LOG_TAG, "error " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public synchronized String decrypt(String value, String context) {
        try {
            byte[] data = Base64.decode(value);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            int ivLen = buffer.get();
            byte[] iv = new byte[ivLen];
            buffer.get(iv);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, getKey(), parameterSpec);
            cipher.updateAAD(context.getBytes());
            data = new byte[buffer.remaining()];
            buffer.get(data);
            data = cipher.doFinal(data);
            return new String(data);
        } catch (GeneralSecurityException e) {
            Log.e(LOG_TAG, "error " + e.getMessage(), e);
        } catch (IOException e) {
            Log.e(LOG_TAG, "error " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public synchronized String encrypt(String value, String context) {
        try {
            byte[] data = value.getBytes();
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            GCMParameterSpec ivParams = cipher.getParameters().getParameterSpec(GCMParameterSpec.class);
            byte[] iv = ivParams.getIV();
            cipher.updateAAD(context.getBytes());
            data = cipher.doFinal(data);
            ByteBuffer buffer = ByteBuffer.allocate(1 + iv.length + data.length);
            buffer.put((byte) iv.length);
            buffer.put(iv);
            buffer.put(data);
            return Base64.encodeBytes(buffer.array());
        } catch (GeneralSecurityException e) {
            Log.e(LOG_TAG, "error " + e.getMessage(), e);
        }
        return null;
    }

}
