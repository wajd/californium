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
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

public class WebViewFragment extends Fragment {
    private static final String KEY_URL = "KEY_WEB_URL";
    private static final String KEY_DATA = "KEY_WEB_DATA";
    private static final String KEY_MIME_TYPE = "KEY_WEB_MIME_TYPE";
    private static final String KEY_ENCODING = "KEY_WEB_ENCODING";

    /**
     * Returns a new instance of this fragment.
     */
    public static WebViewFragment newInstanceUrl(String url) {
        WebViewFragment fragment = new WebViewFragment();
        Bundle args = new Bundle();
        args.putCharSequence(KEY_URL, url);
        fragment.setArguments(args);
        return fragment;
    }

    public static WebViewFragment newInstanceData(String data, String mimeType, String encoding) {
        WebViewFragment fragment = new WebViewFragment();
        Bundle args = new Bundle();
        args.putCharSequence(KEY_DATA, data);
        args.putCharSequence(KEY_MIME_TYPE, mimeType);
        args.putCharSequence(KEY_ENCODING, encoding);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        WebView webView = new WebView(getActivity());
        if (getArguments().containsKey(KEY_URL)) {
            final String url = getArguments().getCharSequence(KEY_URL).toString();
            webView.loadUrl(url);
        } else {
            final String data = getArguments().getCharSequence(KEY_DATA).toString();
            final String mimeType = getArguments().getCharSequence(KEY_MIME_TYPE).toString();
            final String encoding = getArguments().getCharSequence(KEY_ENCODING).toString();
            webView.loadData(data, mimeType, encoding);
        }
        return webView;
    }
}
