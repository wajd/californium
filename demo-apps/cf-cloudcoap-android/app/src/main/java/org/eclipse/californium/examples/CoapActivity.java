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

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import org.eclipse.californium.examples.coaputil.PersistentSessionCache;
import org.eclipse.californium.examples.coaputil.SetupCalifornium;
import org.eclipse.californium.examples.cryptoutil.CryptoUtilityManager;
import org.eclipse.californium.examples.dns.DnsCache;

import java.text.SimpleDateFormat;
import java.util.UUID;

import static org.eclipse.californium.examples.PreferenceIDs.COAP_LOG;
import static org.eclipse.californium.examples.PreferenceIDs.COAP_LOG_RSSI;
import static org.eclipse.californium.examples.PreferenceIDs.COAP_NO_RESPONSE_RIDS;
import static org.eclipse.californium.examples.PreferenceIDs.COAP_UNIQUE_ID;
import static org.eclipse.californium.examples.PreferenceIDs.COAP_USE_DNS_CACHE;
import static org.eclipse.californium.examples.PreferenceIDs.COAP_USE_DTLS_CACHE;
import static org.eclipse.californium.examples.PreferenceIDs.PREF_DTLS_MODE;
import static org.eclipse.californium.examples.PreferenceIDs.PREF_FILE_LOG;
import static org.eclipse.californium.examples.PreferenceIDs.PREF_TIMED_REQUEST;

public class CoapActivity extends AppCompatActivity {
    private static final String LOG_TAG = "main";

    private static final int REQUEST_LOG_PERMISSION = 1;
    private static final int REQUEST_RSSI_PERMISSION = 2;

    private final SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss dd.MM.yyyy");

    private String dtlsModeName;
    private int visibility = View.GONE;
    private boolean displayDetails;

    private SectionsPagerAdapter sectionsPagerAdapter;
    private MyOnPageChangeListener pageListener;

    private ViewPager viewPager;

    private CoapRequestFragment coapRequestFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_coap);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Context applicationContext = getApplicationContext();
        CoapContext.getInstance().attach(applicationContext);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        PreferenceManager.setDefaultValues(applicationContext, R.xml.preferences, false);
        if (dtlsModeName == null) {
            dtlsModeName = preferences.getString(PREF_DTLS_MODE, SetupCalifornium.DtlsSecurityMode.X509.name());
        }
        preferences.registerOnSharedPreferenceChangeListener(changeListener);

        JobManager.initialize(applicationContext);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        sectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        pageListener = new MyOnPageChangeListener();
        // Set up the ViewPager with the sections adapter.
        viewPager = findViewById(R.id.container);
        viewPager.setAdapter(sectionsPagerAdapter);
        viewPager.addOnPageChangeListener(pageListener);

        TabLayout tabs = findViewById(R.id.tabs);
        tabs.setupWithViewPager(viewPager);

        requestPermissions();

        resetCoapContext(SetupCalifornium.InitMode.INIT, "on create");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_LOG_PERMISSION: {
                boolean grant = grantResults.length >= 2
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED;
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                preferences.edit().putBoolean(COAP_LOG, grant).apply();
                if (grant && !preferences.contains(COAP_LOG_RSSI)) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                            }, REQUEST_RSSI_PERMISSION);
                }
                break;
            }
            case REQUEST_RSSI_PERMISSION: {
                boolean grant = grantResults.length >= 1
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED;
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                preferences.edit().putBoolean(COAP_LOG_RSSI, grant).apply();
                break;
            }
        }
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof CoapRequestFragment) {
            coapRequestFragment = (CoapRequestFragment) fragment;
            coapRequestFragment.setDetailInfos(displayDetails);
            coapRequestFragment.setProgress(visibility);
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(LOG_TAG, "on destroy ...");
        coapRequestFragment = null;
        viewPager.removeOnPageChangeListener(pageListener);
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .unregisterOnSharedPreferenceChangeListener(changeListener);
        CoapContext.getInstance().detach();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_coap, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Resources resources = getResources();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        MenuItem item = menu.findItem(R.id.action_dtls);
        if (item != null) {
            String text = resources.getString(R.string.action_dtls);
            PersistentSessionCache sessionCache = CoapContext.getInstance().sessionCache;
            if (sessionCache != null) {
                int number = sessionCache.size();
                text += " " + resources.getQuantityString(R.plurals.entries, number, number);
            }
            item.setTitle(text);
            item.setChecked(preferences.getBoolean(COAP_USE_DTLS_CACHE, true));
        }
        item = menu.findItem(R.id.action_dns);
        if (item != null) {
            String text = resources.getString(R.string.action_dns);
            DnsCache dnsCache = CoapContext.getInstance().dnsCache;
            if (dnsCache != null) {
                int number = dnsCache.size();
                text += " " + resources.getQuantityString(R.plurals.entries, number, number);
            }
            item.setTitle(text);
            item.setChecked(preferences.getBoolean(COAP_USE_DNS_CACHE, true));
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        final Context applicationContext = getApplicationContext();
        int id = item.getItemId();

        if (id == R.id.action_reset_communication) {
            Log.i(LOG_TAG, "Reset Communication");
            CoapContext.getInstance().resetSessionCache();
            resetCoapContext(SetupCalifornium.InitMode.FORCE, "on menu");
        } else if (id == R.id.action_reset_statistic) {
            Log.i(LOG_TAG, "Reset Statistik");
            Statistic.clearStatistic(this);
            LoggingCoapTask.cleanup(this);
        } else if (id == R.id.action_start) {
            boolean enable = !item.isChecked();
            item.setChecked(enable);
            if (enable) {
                startService(new Intent(applicationContext, ServerService.class));
            } else {
                stopService(new Intent(applicationContext, ServerService.class));
            }
        } else if (id == R.id.action_info) {
            boolean enable = !item.isChecked();
            item.setChecked(enable);
            displayDetails = enable;
            if (coapRequestFragment != null) {
                coapRequestFragment.setDetailInfos(enable);
            }
        } else if (id == R.id.action_legal) {
            Intent intent = new Intent(this, LegalActivity.class);
            startActivity(intent);
        } else {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);

            if (id == R.id.action_reset_id) {
                String uniqueID = UUID.randomUUID().toString();
                preferences.edit().putString(COAP_UNIQUE_ID, uniqueID).putString(COAP_NO_RESPONSE_RIDS, "").apply();
                CoapContext.getInstance().resetSessionCache();
                resetCoapContext(SetupCalifornium.InitMode.INIT, "reset id");
            } else if (id == R.id.action_dtls) {
                boolean enable = !item.isChecked();
                item.setChecked(enable);
                preferences.edit().putBoolean(COAP_USE_DTLS_CACHE, enable).apply();
                CoapContext.getInstance().init();
                resetCoapContext(SetupCalifornium.InitMode.FORCE_DTLS, "dtls cache");
            } else if (id == R.id.action_dns) {
                boolean enable = !item.isChecked();
                item.setChecked(enable);
                preferences.edit().putBoolean(COAP_USE_DNS_CACHE, enable).apply();
                CoapContext.getInstance().init();
            } else if (id == R.id.action_reset_all) {
                CryptoUtilityManager.utility().deleteKey();
                preferences.edit().clear().apply();
                PreferenceManager.setDefaultValues(applicationContext, R.xml.preferences, true);
                CoapContext.getInstance().reset();
                LoggingCoapTask.cleanup(this);
                resetCoapContext(SetupCalifornium.InitMode.FORCE, "secure clear");
                requestPermissions();
            } else {
                return super.onOptionsItemSelected(item);
            }
        }
        return true;
    }

    private void adjustDtlsMode() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String dtlsModeName = preferences.getString(PREF_DTLS_MODE, SetupCalifornium.DtlsSecurityMode.X509.name());
        if (!dtlsModeName.equals(this.dtlsModeName)) {
            this.dtlsModeName = dtlsModeName;
            CoapContext.getInstance().resetSessionCache();
            resetCoapContext(SetupCalifornium.InitMode.FORCE_DTLS, "dtls mode changed");
        }
    }

    public void resetCoapContext(final SetupCalifornium.InitMode force, final String tag) {
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected void onPreExecute() {
                Log.i(LOG_TAG, tag + " - start initialize CoAP " + force);
                setProgress(View.VISIBLE);
            }

            @Override
            protected Void doInBackground(Void... voids) {
                CoapContext.getInstance().initCalifornium(force);
                return null;
            }

            @Override
            protected void onPostExecute(Void voids) {
                setProgress(View.GONE);
                Log.i(LOG_TAG, tag + " - finished initialize CoAP");
            }

            private void setProgress(int visibility) {
                CoapActivity.this.visibility = visibility;
                if (coapRequestFragment != null) {
                    coapRequestFragment.setProgress(visibility);
                }
            }
        }.execute();
    }

    private void requestPermissions() {
        final Context applicationContext = getApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        if (preferences.getBoolean(PREF_FILE_LOG, false)) {
            if (!preferences.contains(COAP_LOG)) {
                Log.i(LOG_TAG, "request external storage permission");
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }, REQUEST_LOG_PERMISSION);
            } else if (!preferences.contains(COAP_LOG_RSSI)) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        }, REQUEST_RSSI_PERMISSION);
            }
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 2:
                    return SettingsFragment.newInstance();
                case 1:
                    return CoapStatisticFragment.newInstance();
                default:
                    return CoapRequestFragment.newInstance();
            }
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 2:
                    return getResources().getString(R.string.title_fragment_settings);
                case 1:
                    return getResources().getString(R.string.title_fragment_statistics);
                default:
                    return getResources().getString(R.string.title_fragment_request);
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener changeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.i(LOG_TAG, "preference changed: " + key);
            if (PREF_FILE_LOG.equals(key)) {
                requestPermissions();
            } else if (PREF_TIMED_REQUEST.equals(key)) {
                JobManager.initialize(getApplicationContext());
            }
        }
    };

    public class MyOnPageChangeListener extends ViewPager.SimpleOnPageChangeListener {

        public MyOnPageChangeListener() {

        }

        @Override
        public void onPageSelected(int position) {
            if (position < 2) {
                adjustDtlsMode();
            }
        }
    }
}
