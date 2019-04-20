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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.preference.PreferenceManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;

import org.eclipse.californium.core.WebLink;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.elements.DtlsEndpointContext;
import org.eclipse.californium.elements.util.StringUtil;
import org.eclipse.californium.examples.coaputil.CoapProgress;
import org.eclipse.californium.examples.coaputil.CoapTaskResult;
import org.eclipse.californium.examples.coaputil.PersistentSessionCache;
import org.eclipse.californium.examples.coaputil.ReceivetestClient;
import org.eclipse.californium.scandium.dtls.SessionCache;
import org.eclipse.californium.scandium.dtls.SessionId;
import org.eclipse.californium.scandium.dtls.SessionTicket;

import java.nio.channels.ClosedChannelException;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.eclipse.californium.core.coap.CoAP.ResponseCode.CONTENT;
import static org.eclipse.californium.core.coap.MediaTypeRegistry.APPLICATION_JSON;
import static org.eclipse.californium.examples.PreferenceIDs.COAP_NO_RESPONSE_RIDS;
import static org.eclipse.californium.examples.PreferenceIDs.PREF_DESTINATION_HOST;
import static org.eclipse.californium.examples.PreferenceIDs.PREF_PROTOCOL;

public class CoapRequestFragment extends Fragment implements CoapTaskHandler {

    private static final String LOG_TAG = "request";

    private final SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss dd.MM.yyyy");

    private float textSizeNormal;
    private float textSizeSmall;
    private boolean details;
    private int visibility = View.GONE;

    /**
     * Returns a new instance of this fragment.
     */
    public static CoapRequestFragment newInstance() {
        CoapRequestFragment fragment = new CoapRequestFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(LOG_TAG, "on create ...");

        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        textSizeNormal = getResources().getDimension(R.dimen.text_size_normal) / scaledDensity;
        textSizeSmall = getResources().getDimension(R.dimen.text_size_small) / scaledDensity;

        Log.i(LOG_TAG, "Textsize: " + textSizeNormal + " " + textSizeSmall);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.i(LOG_TAG, "on create view ...");
        final View rootView = inflater.inflate(R.layout.fragment_request, container, false);
        TextView content = rootView.findViewById(R.id.textContent);
        content.setMovementMethod(new ScrollingMovementMethod());
        rootView.findViewById(R.id.progressBar).setVisibility(visibility);

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.i(LOG_TAG, "on start ... ");
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        preferences.registerOnSharedPreferenceChangeListener(changeListener);
        findTextViewById(R.id.textContent).setTextSize(textSizeNormal);
        CoapContext.getInstance().setHandler(this);
        Button button = findViewById(R.id.buttonGet);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
                TextView uri = findViewById(R.id.editUri);
                CoapRequestTask requestTask = new CoapRequestTask(getApplicationContext());
                CoapContext.getInstance().executeRequest(uri.getText().toString(), requestTask);
            }
        });
        setUriFromPreferences(preferences);
        setDetailInfos(details);
    }

    @Override
    public void onStop() {
        Log.i(LOG_TAG, "on stop ... ");
        super.onStop();
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .unregisterOnSharedPreferenceChangeListener(changeListener);
        CoapContext.getInstance().setHandler(null);
    }

    public void setDetailInfos(boolean enable) {
        Log.i(LOG_TAG, "details " + enable);
        details = enable;
        if (getView() != null) {
            int visibility = enable ? View.VISIBLE : View.GONE;
            findViewById(R.id.textRequestUri).setVisibility(visibility);
            findViewById(R.id.textSecurity).setVisibility(visibility);
            findViewById(R.id.textSession).setVisibility(visibility);
            findViewById(R.id.textTxRx).setVisibility(visibility);
        }
    }

    public void setProgress(int visibility) {
        this.visibility = visibility;
        if (getView() != null) {
            View bar = findViewById(R.id.progressBar);
            if (bar != null) {
                bar.setVisibility(visibility);
            }
        }
    }

    private void setUriFromPreferences(SharedPreferences preferences) {
        String destination = preferences.getString(PREF_DESTINATION_HOST, "californium.eclipse.org");
        String protocol = preferences.getString(PREF_PROTOCOL, "coap");
        String uri = protocol + "://" + destination;
        Log.i(LOG_TAG, "Update URI: " + uri);
        setTextViewById(R.id.editUri, uri);
    }

    private void resetDisplay() {
        // reset text fields
        setTextViewById(R.id.textRequestUri, "");
        setTextViewById(R.id.textCode, "");
        setTextViewById(R.id.textCodeName, "...");
        setTextViewById(R.id.textRtt, "");
        setTextViewById(R.id.textSecurity, "");
        setTextViewById(R.id.textSession, "");
        setTextViewById(R.id.textTxRx, "");
        setTextViewById(R.id.textTransfer, "");
        setTextViewById(R.id.textContent, "");
        findTextViewById(R.id.textContent).setTextSize(textSizeNormal);
    }

    private void updateDisplay(CoapProgress progress) {
        String stateDescripiton = getResources().getString(progress.state);
        if (progress.blocks > 1) {
            String blocks = getResources().getString(R.string.blocks, progress.blocks);
            stateDescripiton += " " + blocks;
        }
        if (progress.retransmissions > 0) {
            if (progress.blocks > 1) {
                stateDescripiton += ",";
            }
            String retries = getResources().getQuantityString(R.plurals.retries, progress.retransmissions, progress.retransmissions);
            stateDescripiton += " (" + retries + ")";
        }
        Log.i(LOG_TAG, "progress: " + stateDescripiton);
        setTextViewById(R.id.textCodeName, stateDescripiton);
        if (progress.uriString != null) {
            if (progress.dnsResolveNanoTime != null) {
                long time = TimeUnit.NANOSECONDS.toMillis(progress.dnsResolveNanoTime);
                setTextViewById(R.id.textRequestUri, progress.uriString + " (" + time + " ms)");
            } else {
                setTextViewById(R.id.textRequestUri, progress.uriString);
            }
        }
    }

    protected void resultDisplay(CoapTaskResult result) {
        if (getView() == null) {
            return;
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        String noResponses = preferences.getString(COAP_NO_RESPONSE_RIDS, "");

        if (result.uri != null) {
            if (result.dnsResolveNanoTime != null) {
                long time = TimeUnit.NANOSECONDS.toMillis(result.dnsResolveNanoTime);
                setTextViewById(R.id.textRequestUri, result.uriString + " (" + time + " ms)");
            } else {
                setTextViewById(R.id.textRequestUri, result.uriString);
            }
        }
        long rx = result.rxEnd - result.rxStart;
        long tx = result.txEnd - result.txStart;
        String txrx = getResources().getString(R.string.txrx, tx, rx);
        setTextViewById(R.id.textTxRx, txrx);

        if (result.response != null) {
            setTextViewById(R.id.textCode, result.response.getCode().toString());
            setTextViewById(R.id.textCodeName, result.response.getCode().name());
            Response advanced = result.response.advanced();

            StringBuilder text = new StringBuilder();
            Long rtt = advanced.getRTT();
            int retransmission = result.coapRetransmissions;
            if (rtt != null) {
                text.append(rtt).append(" ms");
            }
            if (retransmission > 0) {
                if (text.length() > 0) {
                    text.append(" (");
                } else {
                    text.append("(");
                }
                text.append(retransmission).append(" retransmissons)");
            }
            setTextViewById(R.id.textRtt, text);

            Principal peer = advanced.getSourceContext().getPeerIdentity();
            if (peer != null) {
                setTextViewById(R.id.textSecurity, peer.getName());
            }
            String session = advanced.getSourceContext().get(DtlsEndpointContext.KEY_SESSION_ID);
            if (session != null) {
                long timestamp = -1;
                SessionId id = new SessionId(StringUtil.hex2ByteArray(session));
                SessionTicket ticket = null;
                SessionCache sessionCache = CoapContext.getInstance().sessionCache;
                if (sessionCache != null) {
                    ticket = sessionCache.get(id);
                }
                if (ticket != null) {
                    timestamp = ticket.getTimestamp();
                } else {
                    String key = PersistentSessionCache.getKey(advanced.getSourceContext().getPeerAddress());
                    Map<String, CoapContext.SimpleSession> sessions = CoapContext.getInstance().simpleSessions;
                    CoapContext.SimpleSession simpleSession = sessions.get(key);
                    if (simpleSession == null || !simpleSession.dtlsSession.equals(session)) {
                        simpleSession = new CoapContext.SimpleSession(session);
                        sessions.put(key, simpleSession);
                    }
                    timestamp = simpleSession.dtlsSessionTime;
                }
                text = new StringBuilder();
                text.append(session.substring(0, 10));
                Log.v(LOG_TAG, "session " + text + ", ticket: " + (ticket != null));
                String time = format.format(timestamp);
                text.append(" - ").append(time);
                if (result.connectNanoTime != null) {
                    text.append(" (");
                    text.append(TimeUnit.NANOSECONDS.toMillis(result.connectNanoTime)).append(" ms");
                    if (result.dtlsRetransmissions > 0) {
                        text.append(", ").append(result.dtlsRetransmissions).append(" retransmissions");
                    }
                    if (result.connectOnRetry) {
                        text.append(", reconnect");
                    }
                    text.append(")");
                }
                setTextViewById(R.id.textSession, text);
            }

            text = new StringBuilder();
            if (advanced.getPayload() != null) {
                text.append(advanced.getPayload().length).append(" bytes");
            }
            if (result.blocks > 1) {
                if (text.length() > 0) {
                    text.append(", ");
                }
                text.append(result.blocks).append(" blocks");
            }
            setTextViewById(R.id.textTransfer, text);
            TextView content = findViewById(R.id.textContent);
            String payload = advanced.getPayloadString();
            if (result.links != null) {
                text = new StringBuilder();
                for (WebLink link : result.links) {
                    text.append(link).append(StringUtil.lineSeparator());
                }
                payload = text.toString();
                Log.i(LOG_TAG, "payload textsize: " + textSizeSmall + ", " + content.getTextSize());
                content.setTextSize(textSizeSmall);
            } else if ((advanced.getOptions().getContentFormat() == APPLICATION_JSON) && (advanced.getCode() == CONTENT)) {
                Log.i(LOG_TAG, "payload textsize: " + textSizeSmall + ", " + content.getTextSize());
                List<Long> lostResponseTimes = new ArrayList<>();
                List<String> rids = ReceivetestClient.parse(noResponses);
                payload = ReceivetestClient.processJSON(payload, rids, true, lostResponseTimes);
            } else {
                Log.i(LOG_TAG, "payload: " + payload);
                if (50 < payload.indexOf('\n')) {
                    Log.i(LOG_TAG, "payload textsize: " + textSizeSmall + ", " + content.getTextSize());
                    content.setTextSize(textSizeSmall);
                }
            }
            content.setText(payload);
        } else {
            if (result.rid != null) {
                setTextViewById(R.id.textContent, result.rid);
            }
            showDetails(result);
            if (result.error != null) {
                Throwable sendError = result.error;
                while (sendError.getCause() != null) {
                    sendError = sendError.getCause();
                }
                String message;
                if (sendError instanceof ClosedChannelException) {
                    message = "connection closed!";
                    if (sendError.getMessage() != null && !sendError.getMessage().isEmpty()) {
                        message += " (" + sendError.getMessage() + ")";
                    }
                } else if (sendError instanceof RuntimeException || sendError.getClass().equals(Exception.class)) {
                    message = sendError.getMessage();
                } else {
                    message = sendError.toString();
                }
                setTextViewById(R.id.textCode, "Error:");
                setTextViewById(R.id.textCodeName, message);
            } else {
                setTextViewById(R.id.textCodeName, "No response");
            }
        }
    }

    private void showDetails(CoapTaskResult result) {
        if (result.coapRetransmissions > 0) {
            setTextViewById(R.id.textRtt, result.coapRetransmissions + " retransmissons");
        }
        if (result.connectNanoTime != null) {
            StringBuilder text = new StringBuilder();
            text.append("connect: ");
            text.append(TimeUnit.NANOSECONDS.toMillis(result.connectNanoTime)).append(" ms");
            if (result.dtlsRetransmissions > 0) {
                text.append(", ").append(result.dtlsRetransmissions).append(" retransmissions");
            }
            if (result.connectOnRetry) {
                text.append(", reconnect");
            }
            setTextViewById(R.id.textSession, text);
        }
    }

    public Context getApplicationContext() {
        return getActivity().getApplicationContext();
    }

    @Nullable
    public final <T extends View> T findViewById(@IdRes int id) {
        return getView().findViewById(id);
    }

    @Nullable
    public final TextView findTextViewById(@IdRes int id) {
        return findViewById(id);
    }

    public void setTextViewById(@IdRes int id, CharSequence text) {
        TextView view = findViewById(id);
        if (view != null) {
            view.setText(text);
        } else {
            Log.i(LOG_TAG, "view not found, ignore " + text);
        }
    }

    @Override
    public void onReset() {
        if (getView() != null) {
            resetDisplay();
        }
    }

    @Override
    public void onProgressUpdate(CoapProgress state) {
        if (getView() != null) {
            updateDisplay(state);
        }
    }

    @Override
    public void onResult(CoapTaskResult result) {
        if (getView() != null) {
            resultDisplay(result);
        }
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener changeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.i(LOG_TAG, "preference changed: " + key);
            if (PREF_DESTINATION_HOST.equals(key) || PREF_PROTOCOL.equals(key)) {
                if (getView() != null) {
                    setUriFromPreferences(sharedPreferences);
                }
            }
        }
    };

}
