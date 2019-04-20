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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.eclipse.californium.examples.coaputil.CoapProgress;
import org.eclipse.californium.examples.coaputil.CoapTaskResult;

/**
 * CoAP GET task
 */
class CoapRequestTask extends LoggingCoapTask {

    private CoapProgress progress;
    private CoapTaskResult result;
    private CoapTaskHandler handler;

    CoapRequestTask(Context applicationContext) {
        super(applicationContext);
    }

    public void setHandler(final CoapTaskHandler handler) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                setHandlerUI(handler);
            }
        });
    }

    private void setHandlerUI(final CoapTaskHandler handler) {
        if (this.handler != handler) {
            this.handler = handler;
            if (handler != null) {
                if (result != null) {
                    handler.onResult(result);
                } else if (progress != null) {
                    handler.onProgressUpdate(progress);
                } else {
                    handler.onReset();
                }
            }
        }
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        progress = null;
        result = null;
        if (handler != null) {
            handler.onReset();
        }
    }

    @Override
    protected void onProgressUpdate(CoapProgress... state) {
        if (state != null && state.length == 1) {
            progress = state[0];
            if (handler != null && progress != null) {
                handler.onProgressUpdate(progress);
            }
        }
        super.onProgressUpdate(state);
    }

    @Override
    protected void onPostExecute(CoapTaskResult result) {
        this.result = result;
        if (handler != null && result != null) {
            handler.onResult(result);
        }
        super.onPostExecute(result);
    }
}
