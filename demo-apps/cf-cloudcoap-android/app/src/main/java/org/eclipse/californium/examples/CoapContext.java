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
package org.eclipse.californium.examples;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import org.eclipse.californium.elements.util.ClockUtil;
import org.eclipse.californium.examples.coaputil.CoapArguments;
import org.eclipse.californium.examples.coaputil.PersistentSessionCache;
import org.eclipse.californium.examples.coaputil.SetupCalifornium;
import org.eclipse.californium.examples.dns.DnsCache;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.eclipse.californium.examples.PreferenceIDs.COAP_EMULATOR;
import static org.eclipse.californium.examples.PreferenceIDs.COAP_EXTRA_HOSTNAME;
import static org.eclipse.californium.examples.PreferenceIDs.COAP_UNIQUE_ID;
import static org.eclipse.californium.examples.PreferenceIDs.COAP_USE_DNS_CACHE;
import static org.eclipse.californium.examples.PreferenceIDs.COAP_USE_DTLS_CACHE;
import static org.eclipse.californium.examples.PreferenceIDs.PREF_DESTINATION_HOST;
import static org.eclipse.californium.examples.PreferenceIDs.PREF_DTLS_MODE;
import static org.eclipse.californium.examples.PreferenceIDs.PREF_IP;
import static org.eclipse.californium.examples.PreferenceIDs.PREF_PROTOCOL;
import static org.eclipse.californium.examples.PreferenceIDs.PREF_REQUEST_MODE;

public class CoapContext {

    public static final String MOBILE = "MOBILE";
    public static final String WIFI = "WIFI";
    public static final String EMULATOR_IP = "10.0.2.2";

    // support optional second sandbox
    private static final String EXTRA_HOSTNAME = "coaps.net";

    // support optional emulator's host as sandbox
    private static final boolean EMULATOR_HOST = false;

    static {
        ClockUtil.setRealtimeHandler(new ClockUtil.Realtime() {
            public long nanoRealtime() {
                return SystemClock.elapsedRealtimeNanos();
            }
        });
    }

    private static final String LOG_TAG = "coap-context";
    private static final CoapContext INSTANCE = new CoapContext();

    private final Lock lock = new ReentrantLock();
    private final AtomicInteger usage = new AtomicInteger();

    private Context applicationContext;
    private String emulatorHostname;
    private boolean sessionCacheCleared;
    private boolean endpointsChanged;

    private CoapTaskHandler handler;
    public CoapRequestTask requestTask;
    public PersistentSessionCache sessionCache;
    public DnsCache dnsCache;

    public static class SimpleSession {
        public final String dtlsSession;
        public final long dtlsSessionTime;

        public SimpleSession(final String dtlsSession) {
            this.dtlsSession = dtlsSession;
            this.dtlsSessionTime = System.currentTimeMillis();
        }
    }

    public Map<String, SimpleSession> simpleSessions = new ConcurrentHashMap<>();

    private void ensureApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("No application context, attach() must be called before!");
        }
    }

    public void setup() {
        ensureApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        boolean changed = false;
        SharedPreferences.Editor editor = preferences.edit();
        if (EXTRA_HOSTNAME != null) {
            String host = preferences.getString(COAP_EXTRA_HOSTNAME, "");
            if (!EXTRA_HOSTNAME.equals(host)) {
                editor.putString(COAP_EXTRA_HOSTNAME, EXTRA_HOSTNAME);
                changed = true;
            }
        }
        if (EMULATOR_HOST) {
            if (!preferences.getBoolean(COAP_EMULATOR, false)) {
                editor.putBoolean(COAP_EMULATOR, true);
                changed = true;
            }
        }
        if (changed) {
            editor.apply();
        }
        if (emulatorHostname == null) {
            emulatorHostname = applicationContext.getResources().getString(R.string.emulator_host);
        }
    }

    public void init() {
        ensureApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        String uniqueID = preferences.getString(COAP_UNIQUE_ID, null);
        if (uniqueID == null) {
            uniqueID = UUID.randomUUID().toString();
            preferences.edit().putString(COAP_UNIQUE_ID, uniqueID).apply();
        }
        if (preferences.getBoolean(COAP_USE_DTLS_CACHE, true)) {
            if (sessionCache == null) {
                sessionCache = new PersistentSessionCache(applicationContext);
            }
        } else if (sessionCache != null) {
            sessionCache.clear();
            sessionCache = null;
        }
        if (preferences.getBoolean(COAP_USE_DNS_CACHE, true)) {
            if (dnsCache == null) {
                dnsCache = new DnsCache(applicationContext);
            }
        } else if (dnsCache != null) {
            dnsCache.clear(applicationContext);
            dnsCache = null;
        }
    }

    public void resetSessionCache() {
        if (sessionCache != null) {
            sessionCache.clear();
            sessionCacheCleared = true;
        }
        simpleSessions.clear();
    }

    public void resetDnsCache() {
        ensureApplicationContext();
        if (dnsCache != null) {
            dnsCache.clear(applicationContext);
        }
    }

    public void reset() {
        resetSessionCache();
        resetDnsCache();
        setup();
    }

    public void initCalifornium(SetupCalifornium.InitMode force) {
        try {
            lock.lock();
            init();
            if (sessionCacheCleared && force == SetupCalifornium.InitMode.INIT) {
                force = SetupCalifornium.InitMode.FORCE_DTLS;
            }
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
            String dtlsModeName = preferences.getString(PREF_DTLS_MODE, SetupCalifornium.DtlsSecurityMode.X509.name());
            SetupCalifornium.DtlsSecurityMode dtlsMode = SetupCalifornium.DtlsSecurityMode.valueOf(dtlsModeName);
            String uniqueID = preferences.getString(COAP_UNIQUE_ID, null);
            boolean changed = SetupCalifornium.getInstance().setupEndpoints(force, uniqueID, dtlsMode, sessionCache);
            if (changed) {
                endpointsChanged = true;
            }
            sessionCacheCleared = false;
        } finally {
            lock.unlock();
        }
    }

    public void setHandler(final CoapTaskHandler handler) {
        this.handler = handler;
        if (requestTask != null) {
            requestTask.setHandler(handler);
        }
    }

    public CoapArguments.Builder setup(SetupCalifornium.InitMode force) {
        ensureApplicationContext();
        long start = System.nanoTime();
        try {
            if (lock.tryLock(2000, TimeUnit.MILLISECONDS)) {
                try {
                    initCalifornium(force);

                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);

                    String destination = preferences.getString(PREF_DESTINATION_HOST, "californium.eclipse.org");
                    String protocol = preferences.getString(PREF_PROTOCOL, "coap");
                    String uri = protocol + "://" + destination;
                    uri = uri.replace(emulatorHostname, EMULATOR_IP);

                    String uniqueID = preferences.getString(COAP_UNIQUE_ID, null);
                    String extraHostname = preferences.getString(COAP_EXTRA_HOSTNAME, null);
                    String sandboxHost = applicationContext.getResources().getString(R.string.sandbox_host);

                    boolean ipv6 = useIPv6(preferences);

                    ApplicationInfo info = applicationContext.getApplicationInfo();

                    CoapArguments.Builder builder = new CoapArguments.Builder();
                    builder.endpointsChanged = endpointsChanged;
                    builder.ipv6 = ipv6;
                    builder.kernalUid = info.uid;
                    builder.uriString = uri;
                    builder.uniqueID = uniqueID;
                    builder.dnsCache = dnsCache;
                    builder.requestMode = CoapArguments.RequestMode.STATISTIC;
                    builder.setExtendedHosts(sandboxHost, extraHostname, EMULATOR_IP);

                    endpointsChanged = false;

                    start = System.nanoTime() - start;
                    Log.i(LOG_TAG, "setup: " + TimeUnit.NANOSECONDS.toMillis(start) + "ms");
                    return builder;
                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
        }
        start = System.nanoTime() - start;
        Log.i(LOG_TAG, "busy: " + TimeUnit.NANOSECONDS.toMillis(start) + "ms");
        return null;
    }

    public void executeRequest(String uri, CoapRequestTask task) {
        Log.i(LOG_TAG, "get: " + uri);
        if (this.requestTask != null) {
            this.requestTask.cancel("next manual request",true);
            this.requestTask = null;
        }
        CoapArguments.Builder builder = setup(SetupCalifornium.InitMode.INIT);
        if (builder != null) {
            if (emulatorHostname != null) {
                uri = uri.replace(emulatorHostname, CoapContext.EMULATOR_IP);
            }
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
            String requestModeName = preferences.getString(PREF_REQUEST_MODE, CoapArguments.RequestMode.ROOT.name());

            builder.uriString = uri;
            builder.requestMode = CoapArguments.RequestMode.valueOf(requestModeName);
            CoapArguments arguments = builder.build();
            executeRequest(task, arguments);
        }
    }

    public void executeRequest(CoapRequestTask task, CoapArguments arguments) {
        if (this.requestTask != null) {
            this.requestTask.cancel("next request (ID" + arguments.jobId + ")",true);
        }
        this.requestTask = task;
        this.requestTask.setHandler(handler);
        this.requestTask.execute(arguments);
    }

    public boolean useIPv6(SharedPreferences preferences) {
        String ipv6 = applicationContext.getResources().getString(R.string.ipv6);
        String ipv4 = applicationContext.getResources().getString(R.string.ipv4);
        return preferences.getString(PREF_IP, ipv4).equals(ipv6);
    }

    public String getConnectivityType() {
        ensureApplicationContext();
        ConnectivityManager connectivityManager = (ConnectivityManager) applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            String type = networkInfo.getTypeName();
            if (type.startsWith("MOBILE")) {
                return MOBILE;
            } else if (type.startsWith("WIFI")) {
                return WIFI;
            }
        }
        return null;
    }

    public void attach(Context applicationContext) {
        if (applicationContext == null) {
            throw new IllegalArgumentException("application context must no be null!");
        }
        int use = usage.getAndIncrement();
        Log.i(LOG_TAG, "attach: " + use);
        if (use == 0) {
            if (this.applicationContext == null) {
                Log.i(LOG_TAG, "attach - init");
                this.applicationContext = applicationContext;
                applicationContext.registerComponentCallbacks(callbacks);
                setup();
                init();
            }
        }
    }

    public void detach() {
        int use = usage.decrementAndGet();
        Log.i(LOG_TAG, "detach: " + use);
        if (use == 0 && applicationContext != null) {
            Handler uiHandler = new Handler(Looper.getMainLooper());
            uiHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (usage.get() == 0 && applicationContext != null) {
                        Log.i(LOG_TAG, "detach - reset");
//                        applicationContext.unregisterComponentCallbacks(callbacks);
//                        applicationContext = null;
//                        SetupCalifornium.getInstance().resetEndpoints();
                    }
                }
            }, 5000);
        }
    }

    private ComponentCallbacks2 callbacks = new ComponentCallbacks2() {
        @Override
        public void onConfigurationChanged(Configuration newConfig) {

        }

        @Override
        public void onLowMemory() {

        }

        @Override
        public void onTrimMemory(int level) {

        }
    };

    public static CoapContext getInstance() {
        return INSTANCE;
    }
}
