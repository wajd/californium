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

public final class DummyCryptoUtility implements CryptoUtility {

    @Override
    public final boolean initKey() {
        return true;
    }

    @Override
    public final void createKey() {
    }

    @Override
    public final void deleteKey() {
    }

    @Override
    public final String decrypt(String value) {
        return value;
    }

    @Override
    public final String encrypt(String value) {
        return value;
    }

    @Override
    public final String decrypt(String value, String context) {
        return value;
    }

    @Override
    public final String encrypt(String value, String context) {
        return value;
    }

}
