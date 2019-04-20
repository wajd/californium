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

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import org.eclipse.californium.elements.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class LegalActivity extends AppCompatActivity {
    private static final String ANDROID_LICENSE_URL = "file:///android_asset/open_source_licenses.html";
    private static final String DATA_PRIVACY_URL = "file:///android_asset/data_privacy.html";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_legal);
        SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        ViewPager viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(sectionsPagerAdapter);
        TabLayout tabs = findViewById(R.id.tabs);
        tabs.setupWithViewPager(viewPager);
        FloatingActionButton fab = findViewById(R.id.fab);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
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
                    return WebViewFragment.newInstanceUrl(ANDROID_LICENSE_URL);
                case 1:
                    String page = loadBase64FromClasspath("about.html");
                    return WebViewFragment.newInstanceData(page, "text/html", "base64");
                default:
                    return WebViewFragment.newInstanceUrl(DATA_PRIVACY_URL);
            }
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 2:
                    return getResources().getString(R.string.title_fragment_android_licenses);
                case 1:
                    return getResources().getString(R.string.title_fragment_californium_licenses);
                default:
                    return getResources().getString(R.string.title_fragment_data_privacy);
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    }

    private static String loadBase64FromClasspath(String resource) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream inStream = LegalActivity.class.getClassLoader().getResourceAsStream(resource);
        if (null != inStream) {
            try {
                byte[] buffer = new byte[2048];
                int size = 0;
                while ((size = inStream.read(buffer)) > 0) {
                    out.write(buffer, 0, size);
                }
            } catch (IOException ex) {
            }
        }
        return Base64.encodeBytes(out.toByteArray());
    }
}