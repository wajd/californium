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

import android.os.Build;
import android.util.Log;

import java.security.GeneralSecurityException;

public final class CryptoUtilityManager {
    private static final String LOG_TAG = "crypto";
    private static final String KEY_ALIAS = "cf-secure";

    private static final CryptoUtility utility;

    static {
        CryptoUtility temp = null;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                temp = new OsCryptoUtilityImpl(KEY_ALIAS);
                temp.initKey();
            } catch (GeneralSecurityException e) {
                Log.e(LOG_TAG, "error:", e);
            }
        }
        if (temp == null) {
            temp = new DummyCryptoUtility();
        }
        utility = temp;
    }

    public static CryptoUtility utility() {
        return utility;
    }
}
