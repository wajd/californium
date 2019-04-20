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

import org.eclipse.californium.examples.dns.DnsCache;

/**
 * Arguments for {@link CoapTask}.
 */
public class CoapArguments {
    public enum RequestMode {
        ROOT, SMALL, DISCOVER, STATISTIC
    }

    /**
     * Indicates, that the client endpoints has changed.
     */
    public final boolean endpointsChanged;
    /**
     * Use IPv6.
     */
    public final boolean ipv6;
    /**
     * The kernel user-ID.
     */
    public final int kernelUid;
    /**
     * The job ID.
     */
    public final Integer jobId;
    /**
     * URI as string
     */
    public final String uriString;
    /**
     * UUID for request statistic.
     */
    public final String uniqueID;
    /**
     * DNS cache.
     */
    public final DnsCache dnsCache;
    /**
     * Request mode.
     */
    public final RequestMode requestMode;
    /**
     * List of extended host.
     * A host of the plugtest server may also start the extended plugtest server on a different port.
     * A STATISTIC request to a host in this list will uses that the different port for communication.
     */
    public final String extendedHosts[];

    public CoapArguments(boolean endpointsChanged, boolean ipv6, int kernelUid, Integer jobId, String uriString, String uniqueID, DnsCache dnsCache, RequestMode requestMode, String... extendedHosts) {
        this.endpointsChanged = endpointsChanged;
        this.ipv6 = ipv6;
        this.kernelUid = kernelUid;
        this.jobId = jobId;
        this.uriString = uriString;
        this.uniqueID = uniqueID;
        this.dnsCache = dnsCache;
        this.requestMode = requestMode;
        this.extendedHosts = extendedHosts;
    }

    public static class Builder {
        public boolean endpointsChanged;
        public boolean ipv6;
        public int kernalUid;
        public Integer jobId;
        public String uriString;
        public String uniqueID;
        public DnsCache dnsCache;
        public RequestMode requestMode;
        public String extendedHosts[];

        public void setExtendedHosts(String... extendedHosts) {
            this.extendedHosts = extendedHosts;
        }

        public CoapArguments build() {
            return new CoapArguments(endpointsChanged, ipv6, kernalUid, jobId, uriString, uniqueID, dnsCache, requestMode, extendedHosts);
        }
    }
}
