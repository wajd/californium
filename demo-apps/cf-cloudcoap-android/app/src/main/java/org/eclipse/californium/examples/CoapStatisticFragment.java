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
import android.os.AsyncTask;
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
import android.widget.TextView;

import org.eclipse.californium.elements.util.StringUtil;
import org.eclipse.californium.examples.coaputil.ReceivetestClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;

import static org.eclipse.californium.examples.PreferenceIDs.COAP_LOG;
import static org.eclipse.californium.examples.PreferenceIDs.COAP_LOG_DONE;
import static org.eclipse.californium.examples.PreferenceIDs.COAP_NO_RESPONSE_RIDS;
import static org.eclipse.californium.examples.PreferenceIDs.COAP_STATISTIC;
import static org.eclipse.californium.examples.PreferenceIDs.PREF_FILE_LOG;

public class CoapStatisticFragment extends Fragment {

    private static final String LOG_TAG = "stat";

    private static final long MAX_CHARACTERS = 4096 * 8;

    private final SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss dd.MM.yyyy");

    private long last;

    /**
     * Returns a new instance of this fragment.
     */
    public static CoapStatisticFragment newInstance() {
        CoapStatisticFragment fragment = new CoapStatisticFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.i(LOG_TAG, "on create view ...");
        final View rootView = inflater.inflate(R.layout.fragment_statistic, container, false);
        ((TextView) rootView.findViewById(R.id.textStatisticLog)).setMovementMethod(new ScrollingMovementMethod());

        return rootView;
    }

    @Override
    public void onStart() {
        Log.i(LOG_TAG, "on start ...");
        super.onStart();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        preferences.registerOnSharedPreferenceChangeListener(changeListener);
        displayStatistic();
        displayNoResponses();
        displayLog();
    }

    @Override
    public void onStop() {
        Log.i(LOG_TAG, "on stop ...");
        super.onStop();
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .unregisterOnSharedPreferenceChangeListener(changeListener);
    }

    public Context getApplicationContext() {
        return getActivity().getApplicationContext();
    }

    @Nullable
    public final <T extends View> T findViewById(@IdRes int id) {
        return getView().findViewById(id);
    }

    public void setTextViewById(@IdRes int id, CharSequence text) {
        TextView view = findViewById(id);
        if (view != null) {
            view.setText(text);
        } else {
            Log.i(LOG_TAG, "view not found, ignore " + text);
        }
    }

    private void displayStatistic() {
        Statistic statistic = Statistic.getStatistic(getApplicationContext());
        StringBuilder builder = new StringBuilder();
        long success = statistic.success;
        builder.append(getResources().getQuantityString(R.plurals.success, (int) success, success));
        if (success > 0) {
            builder.append("  -  ");
            builder.append(format.format(statistic.lastSuccess));
        }
        String result = builder.toString();
        Log.i(LOG_TAG, " : " + result);
        setTextViewById(R.id.textSuccessStatistic, result);

        builder.setLength(0);
        long retries = statistic.retries;
        builder.append(getResources().getQuantityString(R.plurals.retries, (int) retries, retries));
        if (retries > 0) {
            builder.append("  -  ");
            builder.append(format.format(statistic.lastRetries));
        }
        result = builder.toString();
        Log.i(LOG_TAG, " : " + result);
        setTextViewById(R.id.textRetriesStatistic, result);

        builder.setLength(0);
        long losts = statistic.lostResponses;
        builder.append(getResources().getQuantityString(R.plurals.losts, (int) losts, losts));
        if (losts > 0) {
            builder.append("  -  ");
            builder.append(format.format(statistic.lastLostResponses));
        }
        result = builder.toString();
        Log.i(LOG_TAG, " : " + result);
        setTextViewById(R.id.textDropsStatistic, result);

        builder.setLength(0);
        long failures = statistic.failures;
        builder.append(getResources().getQuantityString(R.plurals.failure, (int) failures, failures));
        if (failures > 0) {
            builder.append("  -  ");
            builder.append(format.format(statistic.lastFailure));
        }
        result = builder.toString();
        Log.i(LOG_TAG, " : " + result);
        setTextViewById(R.id.textFailureStatistic, result);

        builder.setLength(0);
        long connects = statistic.connects;
        builder.append(getResources().getQuantityString(R.plurals.connect, (int) connects, connects));
        if (connects > 0) {
            builder.append("  -  ");
            builder.append(format.format(statistic.lastConnect));
        }
        result = builder.toString();
        Log.i(LOG_TAG, " : " + result);
        setTextViewById(R.id.textConnectStatistic, result);

        builder.setLength(0);
        long restarts = statistic.restarts;
        builder.append(getResources().getQuantityString(R.plurals.restart, (int) restarts, restarts));
        if (restarts > 0) {
            builder.append("  -  ");
            builder.append(format.format(statistic.lastRestart));
        }
        result = builder.toString();
        Log.i(LOG_TAG, " : " + result);
        setTextViewById(R.id.textRestartsStatistic, result);

        result = statistic.getSummary();
        Log.i(LOG_TAG, " : " + result);
        setTextViewById(R.id.textStatisticSummary, result);

    }

    private void displayNoResponses() {
        String result = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(COAP_NO_RESPONSE_RIDS, "");
        if (!result.isEmpty()) {
            result = getResources().getQuantityString(R.plurals.noresponse, ReceivetestClient.parse(result).size(), result);
        }
        Log.i(LOG_TAG, " : " + result);
        setTextViewById(R.id.textNoResponses, result);
    }

    private void displayLog() {
        final File logFile = LoggingCoapTask.getLogFile(getApplicationContext());
        if (logFile != null && logFile.exists()) {
            AsyncTask<File, Void, StringBuilder> reader = new AsyncTask<File, Void, StringBuilder>() {
                @Override
                protected StringBuilder doInBackground(File... files) {
                    return readStatisticLog(files[0]);
                }

                @Override
                protected void onPostExecute(StringBuilder result) {
                    TextView textStatisticLog = findViewById(R.id.textStatisticLog);
                    textStatisticLog.setText(result);
                }
            };
            reader.execute(logFile);
        } else {
            TextView textStatisticLog = findViewById(R.id.textStatisticLog);
            textStatisticLog.setText("");
        }
    }

    private StringBuilder readStatisticLog(File logFile) {
        StringBuilder builder = new StringBuilder();
        try (FileInputStream in = new FileInputStream(logFile)) {
            InputStreamReader reader = new InputStreamReader(in);
            BufferedReader lines = new BufferedReader(reader);
            long length = logFile.length();
            Log.i(LOG_TAG, " file: " + length + " bytes.");
            if (length > MAX_CHARACTERS) {
                in.skip(length - MAX_CHARACTERS);
                lines.readLine();
            }
            String line;
            while ((line = lines.readLine()) != null) {
                builder.append(line);
                builder.append(StringUtil.lineSeparator());
//                Log.i(LOG_TAG, " file: " + line);
            }
        } catch (FileNotFoundException e) {
            Log.i(LOG_TAG, "file: " + logFile + " not found!", e);
        } catch (IOException e) {
            Log.i(LOG_TAG, "i/o-error: " + logFile, e);
        }
        return builder;
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener changeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.i(LOG_TAG, "preference changed: " + key);
            if (getView() != null) {
                if (COAP_STATISTIC.equals(key)) {
                    displayStatistic();
                } else if (COAP_NO_RESPONSE_RIDS.equals(key)) {
                    displayNoResponses();
                } else if (PREF_FILE_LOG.equals(key) || COAP_LOG.equals(key) || COAP_LOG_DONE.equals(key)) {
                    displayLog();
                }
            }
        }
    };
}
