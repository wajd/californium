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

import java.net.InetAddress;

public class DnsEntry {
    public boolean expired;
    public final long timestamp;
    public final InetAddress addresses[];

    public DnsEntry(final InetAddress addresses[]) {
        this.addresses = addresses;
        this.timestamp = System.currentTimeMillis();
    }

    public DnsEntry(final InetAddress addresses[], final long timestamp) {
        this.addresses = addresses;
        this.timestamp = timestamp;
    }
}
