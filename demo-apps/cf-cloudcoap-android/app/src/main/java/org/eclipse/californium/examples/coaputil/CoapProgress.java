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

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;

/**
 * Data reported on progress of {@link CoapTask}.
 */
public class CoapProgress {
    /**
     * URI of request as string.
     */
    public String uriString;
    /**
     * Resource ID of state of request.
     */
    public int state;
    /**
     * URI of request.
     */
    public URI uri;
    /**
     * Name of host.
     */
    public String host;
    /**
     * List of addresses of host (DNS).
     */
    public InetAddress hostAddresses[];
    /**
     * Time in nanosecoonds to resolve the hostname with DNS.
     */
    public Long dnsResolveNanoTime;
    /**
     * Time in nanosecoonds to connect the host (DTLS, TCP, or TLS).
     */
    public Long connectNanoTime;
    /**
     * DTLS handshake retransmissions.
     */
    public int dtlsRetransmissions;
    /**
     * CoAP request retransmissions.
     */
    public int coapRetransmissions;
    /**
     * Current retransmissions. Either {@link #dtlsRetransmissions} or {@link #coapRetransmissions}.
     */
    public int retransmissions;
    /**
     * Number of blocks of current response.
     */
    public int blocks;
    /**
     * Received bytes on start of request.
     */
    public long rxStart;
    /**
     * Sent bytes on start of request.
     */
    public long txStart;
    /**
     * Received bytes on end of request.
     */
    public long rxEnd;
    /**
     * Sent bytes on end of request.
     */
    public long txEnd;
    /**
     * Total received bytes indexed by retransmissions.
     * Intended to distinguish between no IP connection and no message from other host.
     */
    public long[] rxTotal;
    /**
     * Tries to reconnect caused by coap retransmissions.
     */
    public boolean connectOnRetry;
    /**
     * Save reported ip-addresses.
     */
    public boolean storeDns;

    public CoapProgress(CoapProgress progress) {
        this.uriString = progress.uriString;
        this.state = progress.state;
        this.uri = progress.uri;
        this.host = progress.host;
        this.hostAddresses = progress.hostAddresses;
        this.dnsResolveNanoTime = progress.dnsResolveNanoTime;
        this.connectNanoTime = progress.connectNanoTime;
        this.dtlsRetransmissions = progress.dtlsRetransmissions;
        this.coapRetransmissions = progress.coapRetransmissions;
        this.retransmissions = progress.retransmissions;
        this.blocks = progress.blocks;
        this.rxStart = progress.rxStart;
        this.txStart = progress.txStart;
        this.rxEnd = progress.rxEnd;
        this.txEnd = progress.txEnd;
        if (progress.rxTotal != null) {
            this.rxTotal = Arrays.copyOf(progress.rxTotal, progress.rxTotal.length);
        }
        this.connectOnRetry = progress.connectOnRetry;
    }

    public CoapProgress(String uriString) {
        this.uriString = uriString;
    }
}
