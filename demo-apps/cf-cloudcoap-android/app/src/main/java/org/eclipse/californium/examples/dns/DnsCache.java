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
package org.eclipse.californium.examples.dns;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.eclipse.californium.elements.util.StringUtil;
import org.eclipse.californium.examples.cryptoutil.CryptoUtility;
import org.eclipse.californium.examples.cryptoutil.CryptoUtilityManager;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DnsCache {
    private static final String LOG_TAG = "dns";

    private static final String DNS_STORAGE_ID = "DNS_STORAGE";
    private static final Pattern IP_PATTERN = Pattern
            .compile("(\\[[0-9a-f:]+\\]|[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})");

    private static final long DNS_EXPIRE = 1000 * 60 * 60 * 24; // 24h in ms

    private Map<String, DnsEntry> dnsCache = new ConcurrentHashMap<>();

    private final CryptoUtility utility;

    public DnsCache(Context applicationContext) {
        this.utility = CryptoUtilityManager.utility();
        SharedPreferences sharedPrefs = applicationContext.getSharedPreferences(
                DNS_STORAGE_ID, Context.MODE_PRIVATE);

        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss dd.MM.yyyy");
        Map<String, ?> all = sharedPrefs.getAll();
        SharedPreferences.Editor editor = sharedPrefs.edit();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            Object value = entry.getValue();
            String host = entry.getKey();
            if (value instanceof String) {
                String text = utility.decrypt((String) value, host);
                Log.i(LOG_TAG, "load " + host + " = " + text);
                if (text != null) {
                    try {
                        DnsEntry dnsEntry = fromString(host, text);
                        if (dnsEntry != null) {
                            dnsCache.put(host, dnsEntry);
                            String time = "";
                            try {
                                time = format.format(new Date(dnsEntry.timestamp));
                            } catch (NumberFormatException e) {
                            }
                            Log.i(LOG_TAG, "load " + host + ", " + dnsEntry.addresses[0] + ", " + time);
                            continue;
                        }
                    } catch (RuntimeException e) {
                        Log.i(LOG_TAG, "load " + host + " failed!", e);
                    }
                }
            }
            editor.remove(host);
        }
        if (dnsCache.isEmpty() && !all.isEmpty()) {
            editor.clear();
        }
        editor.apply();
    }

    private final String getKey(String host, String env) {
        return env == null ? host : host + "@" + env;
    }

    public DnsEntry getAddress(String host, String env) {
        Matcher matcher = IP_PATTERN.matcher(host);
        if (matcher.matches()) {
            try {
                return new DnsEntry(InetAddress.getAllByName(host));
            } catch (UnknownHostException e) {
                Log.e(LOG_TAG, "literal ip address: " + host, e);
            }
        }
        DnsEntry entry = dnsCache.get(getKey(host, env));
        if (entry != null && entry.timestamp + DNS_EXPIRE < System.currentTimeMillis()) {
            entry.expired = true;
        }
        return entry;
    }

    public void putAddresses(Context applicationContext, String host, String env, InetAddress addresses[]) {
        String key = getKey(host, env);
        DnsEntry entry = new DnsEntry(addresses);
        dnsCache.put(key, entry);
        String value = toString(entry);
        Log.i(LOG_TAG, "put dns cache " + key + " => " + value);
        SharedPreferences sharedPrefs = applicationContext.getSharedPreferences(
                DNS_STORAGE_ID, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        value = utility.encrypt(value, key);
        editor.putString(key, value);
        editor.apply();
    }

    public void clear(Context applicationContext) {
        Log.i(LOG_TAG, "clear dns cache");
        dnsCache.clear();
        SharedPreferences sharedPrefs = applicationContext.getSharedPreferences(
                DNS_STORAGE_ID, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.clear();
        editor.apply();
    }

    public int size() {
        return dnsCache.size();
    }

    private String toString(DnsEntry entry) {
        StringBuilder builder = new StringBuilder();
        builder.append(entry.timestamp);
        for (InetAddress address : entry.addresses) {
            byte[] baddr = address.getAddress();
            String atext = StringUtil.byteArray2HexString(baddr, StringUtil.NO_SEPARATOR, 0);
            builder.append('@').append(atext);
        }
        return builder.toString();
    }

    private static DnsEntry fromString(String host, String entry) {
        String[] split = entry.split("@");
        if (split.length > 1) {
            try {
                String time = split[0];
                long timestamp = Long.parseLong(time);
                InetAddress addresses[] = new InetAddress[split.length - 1];
                for (int index = 1; index < split.length; ++index) {
                    byte[] baddr = StringUtil.hex2ByteArray(split[index]);
                    addresses[index - 1] = InetAddress.getByAddress(host, baddr);
                }
                return new DnsEntry(addresses, timestamp);
            } catch (IndexOutOfBoundsException e) {
                Log.e(LOG_TAG, "load dns: " + entry, e);
            } catch (NumberFormatException e) {
                Log.e(LOG_TAG, "load dns: " + entry, e);
            } catch (UnknownHostException e) {
                Log.e(LOG_TAG, "load dns: " + entry, e);
            }
        }
        return null;
    }
}
