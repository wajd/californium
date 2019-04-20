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
package org.eclipse.californium.examples.coaputil;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.eclipse.californium.elements.util.Base64;
import org.eclipse.californium.elements.util.DatagramReader;
import org.eclipse.californium.elements.util.DatagramWriter;
import org.eclipse.californium.examples.cryptoutil.CryptoUtility;
import org.eclipse.californium.examples.cryptoutil.CryptoUtilityManager;
import org.eclipse.californium.scandium.dtls.ClientSessionCache;
import org.eclipse.californium.scandium.dtls.DTLSSession;
import org.eclipse.californium.scandium.dtls.SessionId;
import org.eclipse.californium.scandium.dtls.SessionTicket;

import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PersistentSessionCache implements ClientSessionCache {
    private static final String LOG_TAG = "dtls";

    private static final String DTLS_STORAGE_ID = "DTLS_STORAGE";
    private static final String DTLS_TAG_ADDRESS = "addr";
    private static final String DTLS_TAG_PORT = "port";
    private static final String DTLS_TAG_ID = "id";
    private static final String DTLS_TAG_TICKET = "ticket";

    private final Map<InetSocketAddress, ClientSession> connectionTickets = new ConcurrentHashMap<>();
    private final Map<SessionId, ClientSession> sessionTickets = new ConcurrentHashMap<>();
    private final Context applicationContext;
    private final Handler handler;
    private final CryptoUtility utility;

    public PersistentSessionCache(Context applicationContext) {
        if (applicationContext instanceof Activity) {
            throw new IllegalArgumentException("Replace ActivityContext by ApplicationContext!");
        }
        this.applicationContext = applicationContext;
        this.handler = new Handler(applicationContext.getMainLooper());

        this.utility = CryptoUtilityManager.utility();

        SharedPreferences sharedPrefs = applicationContext.getSharedPreferences(
                DTLS_STORAGE_ID, Context.MODE_PRIVATE);
        Map<String, ?> all = sharedPrefs.getAll();

        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss dd.MM.yyyy");
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String text = utility.decrypt((String) value, entry.getKey());
                if (text == null) {
                    continue;
                }
                ClientSession clientSession = ClientSession.fromString(text);
                if (clientSession != null) {
                    String time = "";
                    try {
                        time = format.format(new Date(clientSession.ticket.getTimestamp()));
                    } catch (NumberFormatException e) {
                    }
                    Log.i(LOG_TAG, "load session for " + entry.getKey() + " " + time);
                    Log.i(LOG_TAG, "     session id " + clientSession.id);
                    connectionTickets.put(clientSession.peer, clientSession);
                    sessionTickets.put(clientSession.id, clientSession);
                }
            }
        }
        if (connectionTickets.isEmpty() && !all.isEmpty()) {
            sharedPrefs.edit().clear().apply();
        }
    }

    public int size() {
        return connectionTickets.size();
    }

    public void clear() {
        Log.i(LOG_TAG, "clear session cache");
        connectionTickets.clear();
        sessionTickets.clear();
        SharedPreferences sharedPrefs = applicationContext.getSharedPreferences(
                DTLS_STORAGE_ID, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.clear();
        editor.apply();
    }

    @Override
    public Iterator<InetSocketAddress> iterator() {
        return connectionTickets.keySet().iterator();
    }

    @Override
    public SessionTicket getSessionTicket(InetSocketAddress peer) {
        ClientSession clientSession = connectionTickets.get(peer);
        return clientSession == null ? null : clientSession.ticket;
    }

    @Override
    public SessionId getSessionIdentity(InetSocketAddress peer) {
        ClientSession clientSession = connectionTickets.get(peer);
        return clientSession == null ? null : clientSession.id;
    }

    @Override
    public void put(DTLSSession session) {
        final InetSocketAddress peer = session.getPeer();
        final SessionTicket ticket = session.getSessionTicket();
        final SessionId id = session.getSessionIdentifier();
        final ClientSession clientSession = new ClientSession(peer, id, ticket);
        final ClientSession previousClientSession = connectionTickets.put(peer, clientSession);
        sessionTickets.put(id, clientSession);
        if (clientSession.equals(previousClientSession)) {
            Log.i(LOG_TAG, "keep same session for " + clientSession.getKey() + ", " + id);
            return;
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                SharedPreferences sharedPrefs = applicationContext.getSharedPreferences(
                        DTLS_STORAGE_ID, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPrefs.edit();
                String value = utility.encrypt(clientSession.toString(), clientSession.getKey());
                editor.putString(clientSession.getKey(), value);
                editor.apply();
                String time = "";
                try {
                    SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss dd.MM.yyyy");
                    time = format.format(new Date(clientSession.ticket.getTimestamp()));
                } catch (NumberFormatException e) {
                }
                Log.i(LOG_TAG, "save session for " + clientSession.getKey() + ", " + time);
                Log.i(LOG_TAG, "     session id " + id);
            }
        });
    }

    @Override
    public SessionTicket get(SessionId id) {
        ClientSession clientSession = sessionTickets.get(id);
        return clientSession == null ? null : clientSession.ticket;
    }

    @Override
    public void remove(SessionId id) {
        Log.i(LOG_TAG, "remove session " + id);
        final ClientSession session = sessionTickets.remove(id);
        if (session != null) {
            connectionTickets.remove(session.peer);
        }
    }

    private static class ClientSession {
        private final int hashCode;
        private final InetSocketAddress peer;
        private final SessionId id;
        private final SessionTicket ticket;

        private ClientSession(final InetSocketAddress peer, final SessionId id, final SessionTicket ticket) {
            this.peer = peer;
            this.id = id;
            this.ticket = ticket;
            this.hashCode = id.hashCode();
        }

        public String getKey() {
            return PersistentSessionCache.getKey(peer);
        }

        public String toString() {
            DatagramWriter writer = new DatagramWriter();
            ticket.encode(writer);
            JsonObject element = new JsonObject();
            element.addProperty(DTLS_TAG_ADDRESS, peer.getAddress().getHostAddress());
            element.addProperty(DTLS_TAG_PORT, peer.getPort());
            element.addProperty(DTLS_TAG_ID, Base64.encodeBytes(id.getBytes()));
            element.addProperty(DTLS_TAG_TICKET, Base64.encodeBytes(writer.toByteArray()));
            GsonBuilder builder = new GsonBuilder();
            Gson gson = builder.create();
            return gson.toJson(element);
        }

        public int hashCode() {
            return hashCode;
        }

        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || object.getClass() != getClass()) {
                return false;
            }
            ClientSession other = (ClientSession) object;
            if (!peer.equals(other.peer)) {
                return false;
            }
            if (!id.equals(other.id)) {
                return false;
            }
            return ticket.equals(other.ticket);
        }

        public static ClientSession fromString(String value) {
            try {
                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(value);
                if (element.isJsonObject()) {
                    JsonObject object = element.getAsJsonObject();
                    String address = object.get(DTLS_TAG_ADDRESS).getAsString();
                    int port = object.get(DTLS_TAG_PORT).getAsInt();
                    byte[] idBytes = Base64.decode(object.get(DTLS_TAG_ID).getAsString());
                    byte[] ticketBytes = Base64.decode(object.get(DTLS_TAG_TICKET).getAsString());
                    InetSocketAddress peer = new InetSocketAddress(address, port);
                    SessionId id = new SessionId(idBytes);
                    SessionTicket ticket = SessionTicket.decode(new DatagramReader(ticketBytes));
                    return new ClientSession(peer, id, ticket);
                }
            } catch (Throwable t) {
            }
            return null;
        }
    }

    public static String getKey(InetSocketAddress peer) {
        return peer.getAddress().getHostAddress() + ":" + peer.getPort();
    }
}
