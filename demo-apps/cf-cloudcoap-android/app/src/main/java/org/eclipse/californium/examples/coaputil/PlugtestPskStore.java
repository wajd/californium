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
 *    Bosch Software Innovations - initial creation
 ******************************************************************************/
package org.eclipse.californium.examples.coaputil;

import org.eclipse.californium.scandium.dtls.pskstore.StringPskStore;
import org.eclipse.californium.scandium.util.ServerNames;

import java.net.InetSocketAddress;

/**
 * Simple demo psk store used for sandbox only!
 */
public class PlugtestPskStore extends StringPskStore {
    public static final String PSK_IDENTITY_PREFIX = "cali.";
    private static final byte[] PSK_SECRET = ".fornium".getBytes();

    private final String identity;
    private final byte[] secret;

    public PlugtestPskStore(String id, byte[] secret) {
        this.identity = id;
        this.secret = secret;
    }

    public PlugtestPskStore(String id) {
        identity = PSK_IDENTITY_PREFIX + id;
        secret = null;
    }

    @Override
    public byte[] getKey(String identity) {
        if (secret != null) {
            return secret;
        }
        if (identity.startsWith(PSK_IDENTITY_PREFIX)) {
            return PSK_SECRET;
        }
        return null;
    }

    @Override
    public byte[] getKey(ServerNames serverNames, String identity) {
        return getKey(identity);
    }

    @Override
    public String getIdentityAsString(InetSocketAddress inetAddress) {
        return identity;
    }

    @Override
    public String getIdentityAsString(InetSocketAddress peerAddress, ServerNames virtualHost) {
        return getIdentityAsString(peerAddress);
    }

}
