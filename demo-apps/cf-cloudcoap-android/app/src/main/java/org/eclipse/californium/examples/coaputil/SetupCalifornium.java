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
package org.eclipse.californium.examples.coaputil;

import android.util.Log;

import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.network.config.NetworkConfig.Keys;
import org.eclipse.californium.elements.Connector;
import org.eclipse.californium.elements.tcp.TcpClientConnector;
import org.eclipse.californium.elements.tcp.TlsClientConnector;
import org.eclipse.californium.elements.util.SslContextUtil;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.CertificateType;
import org.eclipse.californium.scandium.dtls.SessionCache;
import org.eclipse.californium.scandium.dtls.SingleNodeConnectionIdGenerator;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSessionContext;

public class SetupCalifornium {
    public enum DtlsSecurityMode {
        PSK, RPK, X509
    }

    public enum InitMode {
        INIT, FORCE_DTLS, FORCE
    }

    private static final String LOG_TAG = "setup";

    private static final char[] KEY_STORE_PASSWORD = "endPass".toCharArray();
    private static final String KEY_STORE_LOCATION = "certs/keyStore.p12";
    private static final char[] TRUST_STORE_PASSWORD = "rootPass".toCharArray();
    private static final String TRUST_STORE_LOCATION = "certs/trustStore.p12";
    private static final String CLIENT_NAME = "client";
    private static final String CLIENT_TRUST = "root"; // all certificates in trust store
    private static final int MAX_RESOURCE_SIZE = 8192;

    private static final NetworkConfig CONFIG;
    private static final SetupCalifornium INSTANCE;

    static {
        CONFIG = new NetworkConfig();
        CONFIG.setInt(Keys.PROTOCOL_STAGE_THREAD_COUNT, 2);
        CONFIG.setInt(Keys.MAX_PEER_INACTIVITY_PERIOD, 60 * 60 * 24); // 24h
        CONFIG.setInt(Keys.MAX_ACTIVE_PEERS, 10);
        CONFIG.setInt(Keys.DTLS_CONNECTION_ID_LENGTH, 0); // enable support, but don't use it on incoming traffic
        CONFIG.setInt(Keys.NETWORK_STAGE_RECEIVER_THREAD_COUNT, 2);
        CONFIG.setInt(Keys.NETWORK_STAGE_SENDER_THREAD_COUNT, 2);
        CONFIG.setInt(Keys.MAX_RESOURCE_BODY_SIZE, MAX_RESOURCE_SIZE);
        CONFIG.setInt(Keys.TCP_WORKER_THREADS, 2);
        CONFIG.setInt(Keys.TCP_CONNECT_TIMEOUT, 30000); // 30s
        CONFIG.setInt(Keys.TLS_HANDSHAKE_TIMEOUT, 30000); // 30s
        CONFIG.setInt(Keys.TCP_CONNECTION_IDLE_TIMEOUT, 60 * 60 * 24); // 24h
        CONFIG.setInt(Keys.SECURE_SESSION_TIMEOUT, 60 * 60 * 24); // 24h
        INSTANCE = new SetupCalifornium();
    }

    private boolean initialized;
    private String id;
    private DtlsSecurityMode mode;
    private volatile DTLSConnector dtlsConnector;

    private SslContextUtil.Credentials clientCredentials;
    private Certificate[] trustedCertificates;
    private boolean certificatesLoaded;

    public boolean setupEndpoints(InitMode force, String id, DtlsSecurityMode mode, SessionCache sessionCache) {
        boolean changed = false;
        long start = System.nanoTime();
        long lastStart = start;
        int receiverThreads = CONFIG.getInt(Keys.NETWORK_STAGE_RECEIVER_THREAD_COUNT);
        int senderThreads = CONFIG.getInt(Keys.NETWORK_STAGE_SENDER_THREAD_COUNT);
        int tcpThreads = CONFIG.getInt(Keys.TCP_WORKER_THREADS);
        int tcpConnectTimeout = CONFIG.getInt(Keys.TCP_CONNECT_TIMEOUT);
        int tlsHandshakeTimeout = CONFIG.getInt(Keys.TLS_HANDSHAKE_TIMEOUT);
        int tcpIdleTimeout = CONFIG.getInt(Keys.TCP_CONNECTION_IDLE_TIMEOUT);
        int maxPeers = CONFIG.getInt(Keys.MAX_ACTIVE_PEERS);
        int staleTimeout = CONFIG.getInt(Keys.MAX_PEER_INACTIVITY_PERIOD);
        int sessionTimeout = CONFIG.getInt(Keys.SECURE_SESSION_TIMEOUT);
        Integer cidLength = CONFIG.getOptInteger(Keys.DTLS_CONNECTION_ID_LENGTH);

        if (force == InitMode.FORCE) {
            Log.i(LOG_TAG, "setup: forced! " + mode);
            EndpointManager.reset();
            initialized = false;
            long end = System.nanoTime();
            Log.i(LOG_TAG, "reset: " + TimeUnit.NANOSECONDS.toMillis(end - lastStart) + " ms");
            lastStart = end;
        } else {
            Log.i(LOG_TAG, "setup: initialized = " + initialized + ", " + mode);
        }

        boolean init = initialized;

        if (!certificatesLoaded) {
            try {
                Log.i(LOG_TAG, "setup: load credentials");
                clientCredentials = SslContextUtil.loadCredentials(
                        SslContextUtil.CLASSPATH_SCHEME + KEY_STORE_LOCATION, CLIENT_NAME, KEY_STORE_PASSWORD,
                        KEY_STORE_PASSWORD);
                trustedCertificates = SslContextUtil.loadTrustedCertificates(
                        SslContextUtil.CLASSPATH_SCHEME + TRUST_STORE_LOCATION, CLIENT_TRUST, TRUST_STORE_PASSWORD);
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            long end = System.nanoTime();
            Log.i(LOG_TAG, "load certificates: " + TimeUnit.NANOSECONDS.toMillis(end - lastStart) + " ms");
            lastStart = end;
            certificatesLoaded = true;
        }

        DtlsSecurityMode effectiveMode = mode;
        if (trustedCertificates == null && clientCredentials == null) {
            effectiveMode = DtlsSecurityMode.PSK;
        } else if (trustedCertificates == null && effectiveMode == DtlsSecurityMode.X509) {
            effectiveMode = DtlsSecurityMode.RPK;
        }

        if (!init) {
            Log.i(LOG_TAG, "setup: create UDP and TCP endpoints.");
            long start1 = System.nanoTime();
            Connector tcpConnector = new TcpClientConnector(tcpThreads, tcpConnectTimeout, tcpIdleTimeout);
            CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
            builder.setConnector(tcpConnector);
            builder.setNetworkConfig(CONFIG);
            EndpointManager.getEndpointManager().setDefaultEndpoint(builder.build());

            long start2 = System.nanoTime();
            builder = new CoapEndpoint.Builder();
            builder.setNetworkConfig(CONFIG);
            EndpointManager.getEndpointManager().setDefaultEndpoint(builder.build());

            long start3 = System.nanoTime();
            if (trustedCertificates != null) {
                try {
                    SSLContext sslContext = SslContextUtil.createSSLContext(CLIENT_NAME, clientCredentials.getPrivateKey(),
                            clientCredentials.getCertificateChain(), trustedCertificates);
                    Connector tlsConnector = new TlsClientConnector(sslContext, tcpThreads, tcpConnectTimeout, tlsHandshakeTimeout, tcpIdleTimeout);
                    SSLSessionContext clientSessionContext = sslContext.getClientSessionContext();
                    if (clientSessionContext != null) {
                        clientSessionContext.setSessionTimeout(sessionTimeout);
                        clientSessionContext.setSessionCacheSize(maxPeers);
                        Log.v(LOG_TAG, trustedCertificates.length + " trust anchors loaded.");
                        for (int index = 0; index < trustedCertificates.length; ++index) {
                            Log.v(LOG_TAG, "  [" + index + "] " + trustedCertificates[index]);
                        }
                    }
                    builder = new CoapEndpoint.Builder();
                    builder.setConnector(tlsConnector);
                    builder.setNetworkConfig(CONFIG);
                    EndpointManager.getEndpointManager().setDefaultEndpoint(builder.build());
                    Log.i(LOG_TAG, "setup: create TLS endpoint with " + trustedCertificates.length + " trusted certificates.");
                } catch (GeneralSecurityException e) {
                    e.printStackTrace();
                }
            }
            changed = true;
            long start4 = System.nanoTime();
            Log.i(LOG_TAG, "upd: " + TimeUnit.NANOSECONDS.toMillis(start2 - start1) + " ms");
            Log.i(LOG_TAG, "tcd: " + TimeUnit.NANOSECONDS.toMillis(start3 - start2) + " ms");
            Log.i(LOG_TAG, "tls: " + TimeUnit.NANOSECONDS.toMillis(start4 - start3) + " ms");
        }

        boolean initDtls = !init || force == InitMode.FORCE_DTLS ||
                (effectiveMode != this.mode) ||
                (effectiveMode == DtlsSecurityMode.PSK && !id.equals(this.id));

        if (initDtls) {
            if (init) {
                if (effectiveMode != this.mode) {
                    Log.i(LOG_TAG, "setup: create DTLS endpoint with new mode " + effectiveMode);
                }
                if (effectiveMode == DtlsSecurityMode.PSK && !id.equals(this.id)) {
                    Log.i(LOG_TAG, "setup: create DTLS endpoint with new id " + id);
                }
                if (sessionCache == null) {
                    Log.i(LOG_TAG, "setup: create DTLS endpoint without session cache");
                } else {
                    Log.i(LOG_TAG, "setup: create DTLS endpoint with session cache");
                }
            } else {
                Log.i(LOG_TAG, "setup: create DTLS endpoint with mode " + effectiveMode);
            }
            long time = System.nanoTime();
            DtlsConnectorConfig.Builder dtlsBuilder = new DtlsConnectorConfig.Builder();
            if (effectiveMode == DtlsSecurityMode.PSK) {
                dtlsBuilder.setPskStore(new PlugtestPskStore(id));
            } else if (effectiveMode == DtlsSecurityMode.RPK) {
                dtlsBuilder.setIdentity(clientCredentials.getPrivateKey(), clientCredentials.getCertificateChain(), CertificateType.RAW_PUBLIC_KEY);
                dtlsBuilder.setRpkTrustAll();
            } else if (effectiveMode == DtlsSecurityMode.X509) {
                dtlsBuilder.setIdentity(clientCredentials.getPrivateKey(), clientCredentials.getCertificateChain(), CertificateType.X_509);
                dtlsBuilder.setTrustStore(trustedCertificates);
                dtlsBuilder.setRetransmissionTimeout(2000);
                dtlsBuilder.setMaxRetransmissions(3);
            }
            if (cidLength != null) {
                dtlsBuilder.setConnectionIdGenerator(new SingleNodeConnectionIdGenerator(cidLength));
            }
            dtlsBuilder.setClientOnly();
            dtlsBuilder.setSniEnabled(false);
            dtlsBuilder.setConnectionThreadCount(senderThreads);
            dtlsBuilder.setReceiverThreadCount(receiverThreads);
            dtlsBuilder.setMaxConnections(maxPeers);
            dtlsBuilder.setStaleConnectionThreshold(staleTimeout);
            DTLSConnector dtlsConnector = new DTLSConnector(dtlsBuilder.build(), sessionCache);
            CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
            builder.setConnector(dtlsConnector);
            builder.setNetworkConfig(CONFIG);
            EndpointManager.getEndpointManager().setDefaultEndpoint(builder.build());
            this.mode = effectiveMode;
            this.id = id;
            changed = true;
            time = System.nanoTime() - time;
            Log.i(LOG_TAG, "dtls: " + TimeUnit.NANOSECONDS.toMillis(time) + " ms");
        } else {
            Log.i(LOG_TAG, "setup: coap endpoints already initialized!");
        }
        initialized = true;
        long end = System.nanoTime();
        Log.i(LOG_TAG, "all: " + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms" + (changed ? " (changed)" : " (no change)"));
        return changed;
    }

    public void resetEndpoints() {
        Log.i(LOG_TAG, "setup: reset");
        EndpointManager.reset();
        initialized = false;
    }

    public static SetupCalifornium getInstance() {
        return INSTANCE;
    }
}
