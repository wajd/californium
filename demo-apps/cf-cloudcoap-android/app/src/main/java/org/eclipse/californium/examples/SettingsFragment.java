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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.util.Log;

import static org.eclipse.californium.examples.PreferenceIDs.COAP_EMULATOR;
import static org.eclipse.californium.examples.PreferenceIDs.COAP_EXTRA_HOSTNAME;
import static org.eclipse.californium.examples.PreferenceIDs.PREF_DESTINATION_HOST;
import static org.eclipse.californium.examples.PreferenceIDs.PREF_DTLS_MODE;
import static org.eclipse.californium.examples.PreferenceIDs.PREF_FILE_LOG;
import static org.eclipse.californium.examples.PreferenceIDs.PREF_IP;
import static org.eclipse.californium.examples.PreferenceIDs.PREF_PROTOCOL;
import static org.eclipse.californium.examples.PreferenceIDs.PREF_REQUEST_MODE;
import static org.eclipse.californium.examples.PreferenceIDs.PREF_TIMED_REQUEST;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String LOG_TAG = "settings";

    /**
     * Returns a new instance of this fragment.
     */
    public static SettingsFragment newInstance() {
        SettingsFragment fragment = new SettingsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    private void updateBoolean(SharedPreferences preferences, String key) {
        Preference preference = findPreference(key);
        if (preference instanceof SwitchPreferenceCompat) {
            boolean value = preferences.getBoolean(key, false);
            ((SwitchPreferenceCompat)preference).setChecked(value);
        }
    }

    private void updateSummary(SharedPreferences preferences, String key) {
        Preference preference = findPreference(key);
        if (preference instanceof SwitchPreferenceCompat) {
            return;
        }
        String value = preferences.getString(key, "");
        CharSequence summary = value;
        if (preference instanceof ListPreference) {
            ListPreference list = (ListPreference) preference;
            int index = list.findIndexOfValue(value);
            if (0 <= index) {
                summary = list.getEntries()[index];
                list.setValueIndex(index);
            }
        }
        preference.setSummary(summary);
    }

    private void update(SharedPreferences preferences) {
        updateSummary(preferences, PREF_DESTINATION_HOST);
        updateSummary(preferences, PREF_IP);
        updateSummary(preferences, PREF_PROTOCOL);
        updateSummary(preferences, PREF_DTLS_MODE);
        updateSummary(preferences, PREF_REQUEST_MODE);
        if (JobManager.isSupported()) {
            updateSummary(preferences, PREF_TIMED_REQUEST);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Log.i(LOG_TAG, "on create preferences ...");
        addPreferencesFromResource(R.xml.preferences);
        SharedPreferences preferences = getPreferenceScreen().getSharedPreferences();

        ListPreference preference = (ListPreference) findPreference(PREF_DESTINATION_HOST);
        CharSequence[] entries = preference.getEntries();
        CharSequence[] newEntries = null;
        String hostName = preferences.getString(COAP_EXTRA_HOSTNAME, null);
        if (hostName != null) {
            newEntries = new CharSequence[entries.length + 1];
            newEntries[0] = hostName;
            System.arraycopy(entries, 0, newEntries, 1, entries.length);
            Log.i(LOG_TAG, "extra host added to preferences!");
        }
        if (preferences.getBoolean(COAP_EMULATOR, false)) {
            hostName = getResources().getString(R.string.emulator_host);
            if (newEntries != null) {
                entries = newEntries;
            }
            newEntries = new CharSequence[entries.length + 1];
            newEntries[entries.length] = hostName;
            System.arraycopy(entries, 0, newEntries, 0, entries.length);
            Log.i(LOG_TAG, "emulator host added to preferences!");
        }
        if (newEntries != null) {
            preference.setEntries(newEntries);
            preference.setEntryValues(newEntries);
        }

        preference = (ListPreference) findPreference(PREF_TIMED_REQUEST);
        if (JobManager.isSupported()) {
            initListPreference(preference, R.string.interval_time_min);
        } else {
            CharSequence[] values = preference.getEntryValues();
            entries = preference.getEntries();
            values = new CharSequence[]{values[0]};
            entries = new CharSequence[]{entries[0]};
            preference.setEntries(entries);
            preference.setEntryValues(values);
            preference.setSummary(entries[0]);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.i(LOG_TAG, "on start ... ");
        SharedPreferences preferences = getPreferenceScreen().getSharedPreferences();
        update(preferences);
        updateBoolean(preferences, PREF_FILE_LOG);
    }

    private void initListPreference(ListPreference preference, int textId) {
        CharSequence[] values = preference.getEntryValues();
        CharSequence[] entries = preference.getEntries();
        CharSequence[] newEntries = new CharSequence[values.length];
        newEntries[0] = entries[0];
        for (int index = 1; index < values.length; ++index) {
            String value = values[index].toString();
            value = getResources().getString(textId, value);
            newEntries[index] = value;
        }
        preference.setEntries(newEntries);
        preference.setEntryValues(values);
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PREF_DESTINATION_HOST.equals(key) ||
                PREF_IP.equals(key) ||
                PREF_PROTOCOL.equals(key) ||
                PREF_DTLS_MODE.equals(key) ||
                PREF_REQUEST_MODE.equals(key) ||
                PREF_TIMED_REQUEST.equals(key)) {
            updateSummary(sharedPreferences, key);
        } else if (PREF_FILE_LOG.equals(key)) {
            updateBoolean(sharedPreferences, PREF_FILE_LOG);
        }
    }
}
