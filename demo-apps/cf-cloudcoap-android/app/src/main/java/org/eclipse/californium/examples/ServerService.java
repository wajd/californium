/*******************************************************************************
 * Copyright (c) 2015 Institute for Pervasive Computing, ETH Zurich and others.
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
 *    Matthias Kovatsch - creator and main architect
 ******************************************************************************/
package org.eclipse.californium.examples;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.elements.Connector;
import org.eclipse.californium.elements.tcp.TcpServerConnector;
import org.eclipse.californium.elements.tcp.TlsServerConnector;
import org.eclipse.californium.elements.util.SslContextUtil;
import org.eclipse.californium.examples.coaputil.PlugtestPskStore;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.CertificateType;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSessionContext;

public class ServerService extends Service {

    private static final String LOG_TAG = "server";

    private static final NetworkConfig CONFIG;

    private static final char[] KEY_STORE_PASSWORD = "endPass".toCharArray();
    private static final String KEY_STORE_LOCATION = "certs/keyStore.p12";
    private static final char[] TRUST_STORE_PASSWORD = "rootPass".toCharArray();
    private static final String TRUST_STORE_LOCATION = "certs/trustStore.p12";
    private static final String SERVER_NAME = "server";
    private static final String SERVER_TRUST = null; // all certificates in trust store

    static {
        CONFIG = NetworkConfig.createStandardWithoutFile();
        CONFIG.setInt(NetworkConfig.Keys.MAX_PEER_INACTIVITY_PERIOD, 60 * 60 * 24); // 24h
        CONFIG.setInt(NetworkConfig.Keys.MAX_ACTIVE_PEERS, 100);
        CONFIG.setInt(NetworkConfig.Keys.PROTOCOL_STAGE_THREAD_COUNT, 4);
        CONFIG.setInt(NetworkConfig.Keys.NETWORK_STAGE_RECEIVER_THREAD_COUNT, 2);
        CONFIG.setInt(NetworkConfig.Keys.NETWORK_STAGE_SENDER_THREAD_COUNT, 2);
        CONFIG.setInt(NetworkConfig.Keys.TCP_WORKER_THREADS, 2);
        CONFIG.setInt(NetworkConfig.Keys.TCP_CONNECTION_IDLE_TIMEOUT, 60 * 60 * 24); // 24h
    }

    CoapServer server;

    @Override
    public void onCreate() {
        this.server = new CoapServer();
        server.add(new HelloWorldResource());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(LOG_TAG, "server starting ...");
        int plainPort = CONFIG.getInt(NetworkConfig.Keys.COAP_PORT);
        int securePort = CONFIG.getInt(NetworkConfig.Keys.COAP_SECURE_PORT);
        int tcpThreads = CONFIG.getInt(NetworkConfig.Keys.TCP_WORKER_THREADS);
        int tcpConnectTimeout = CONFIG.getInt(NetworkConfig.Keys.TCP_CONNECT_TIMEOUT);
        int tcpIdleTimeout = CONFIG.getInt(NetworkConfig.Keys.TCP_CONNECTION_IDLE_TIMEOUT);
        int maxPeers = CONFIG.getInt(NetworkConfig.Keys.MAX_ACTIVE_PEERS);
        int staleTimeout = CONFIG.getInt(NetworkConfig.Keys.MAX_PEER_INACTIVITY_PERIOD);
        int sessionTimeout = CONFIG.getInt(NetworkConfig.Keys.SECURE_SESSION_TIMEOUT);

        InetSocketAddress plain = new InetSocketAddress(plainPort);
        InetSocketAddress secure = new InetSocketAddress(securePort);

        CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
        builder.setNetworkConfig(CONFIG);
        builder.setInetSocketAddress(plain);
        server.addEndpoint(builder.build());

        Connector tcpConnector = new TcpServerConnector(plain, tcpThreads, tcpIdleTimeout);
        builder = new CoapEndpoint.Builder();
        builder.setNetworkConfig(CONFIG);
        builder.setConnector(tcpConnector);
        server.addEndpoint(builder.build());

        SslContextUtil.Credentials clientCredentials = null;
        Certificate[] trustedCertificates = null;
        try {
            clientCredentials = SslContextUtil.loadCredentials(
                    SslContextUtil.CLASSPATH_SCHEME + KEY_STORE_LOCATION, SERVER_NAME, KEY_STORE_PASSWORD,
                    KEY_STORE_PASSWORD);
            trustedCertificates = SslContextUtil.loadTrustedCertificates(
                    SslContextUtil.CLASSPATH_SCHEME + TRUST_STORE_LOCATION, SERVER_TRUST, TRUST_STORE_PASSWORD);
            SSLContext sslContext = SslContextUtil.createSSLContext(SERVER_NAME, clientCredentials.getPrivateKey(),
                    clientCredentials.getCertificateChain(), trustedCertificates);
            Connector tlsConnector = new TlsServerConnector(sslContext, TlsServerConnector.ClientAuthMode.WANTED, secure, tcpThreads, tcpConnectTimeout, tcpIdleTimeout);
            SSLSessionContext clientSessionContext = sslContext.getClientSessionContext();
            if (clientSessionContext != null) {
                clientSessionContext.setSessionTimeout(sessionTimeout);
                clientSessionContext.setSessionCacheSize(maxPeers);
            }
            builder = new CoapEndpoint.Builder();
            builder.setConnector(tlsConnector);
            builder.setNetworkConfig(CONFIG);
            server.addEndpoint(builder.build());
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        DtlsConnectorConfig.Builder dtlsBuilder = new DtlsConnectorConfig.Builder();
        dtlsBuilder.setAddress(secure);
        dtlsBuilder.setPskStore(new PlugtestPskStore("android"));
        if (clientCredentials != null) {
            dtlsBuilder.setIdentity(clientCredentials.getPrivateKey(), clientCredentials.getCertificateChain(), CertificateType.RAW_PUBLIC_KEY, CertificateType.X_509);
        }
        if (trustedCertificates != null) {
            dtlsBuilder.setTrustStore(trustedCertificates);
        }
        dtlsBuilder.setRpkTrustAll();
        dtlsBuilder.setRetransmissionTimeout(2000);
        dtlsBuilder.setMaxRetransmissions(3);
        dtlsBuilder.setEarlyStopRetransmission(false);
        dtlsBuilder.setConnectionThreadCount(2);
        dtlsBuilder.setMaxConnections(maxPeers);
        dtlsBuilder.setStaleConnectionThreshold(staleTimeout);

        DTLSConnector dtlsConnector = new DTLSConnector(dtlsBuilder.build());
        builder = new CoapEndpoint.Builder();
        builder.setConnector(dtlsConnector);
        builder.setNetworkConfig(CONFIG);
        server.addEndpoint(builder.build());

        server.start();
        Log.i(LOG_TAG, "server started.");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        server.destroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    class HelloWorldResource extends CoapResource {

        public HelloWorldResource() {

            // set resource identifier
            super("hello");

            // set display name
            getAttributes().setTitle("Hello-World Resource");
        }

        @Override
        public void handleGET(CoapExchange exchange) {

            // respond to the request
            exchange.respond("Hello Android!");
        }
    }
}
