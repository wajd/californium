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
 *    Bosch Software Innovations GmbH - initial implementation
 ******************************************************************************/
package org.eclipse.californium.examples.coaputil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.eclipse.californium.elements.util.StringUtil;

/**
 * The ReceivetestClient uses the developer API of Californium to test the
 * communication. The server side keeps track of the last received request and
 * response with that history. So if a request fails, the nest request may show,
 * if the request was lost (not in history) or only the response was lost.
 */
public class ReceivetestClient {

    /**
     * Prefix for request ID.
     */
    public static final String REQUEST_ID_PREFIX = "RID";

    private static final String SEP = ";";

    /**
     * Maximum time difference display as milliseconds.
     */
    private static final int MAX_DIFF_TIME_IN_MILLIS = 30000;

    public static String createRid() {
        return REQUEST_ID_PREFIX + System.currentTimeMillis();
    }

    public static List<String> parse(String noResponseRids) {
        List<String> rids = new ArrayList<>();
        String[] splitErrors = noResponseRids.split(SEP);
        for (String rid : splitErrors) {
            rids.add(rid);
        }
        return rids;
    }

    public static String serialize(List<String> rids) {
        StringBuilder builder = new StringBuilder();
        if (!rids.isEmpty()) {
            for (String rid : rids) {
                builder.append(rid).append(SEP);
            }
            builder.setLength(builder.length() - 1);
        }
        return builder.toString();
    }

    /**
     * Process JSON response.
     *
     * @param payload JSON payload as string
     * @param noResponseRids  request ID of previously failed request.
     * @param verbose {@code true} to interpret the string, either pretty JSON,
     *                or pretty application, if data complies the assumed request
     *                statistic information.
     * @param lostResponseTimes list to be filled with time of lost responses.
     * @return payload as application text, pretty JSON, or just as provided
     * text.
     */
    public static String processJSON(String payload, List<String> noResponseRids, boolean verbose, List<Long> lostResponseTimes) {
        StringBuilder statistic = new StringBuilder();
        JsonElement element = null;
        try {
            JsonParser parser = new JsonParser();
            element = parser.parse(payload);

            if (verbose && element.isJsonArray()) {
                // expected JSON data
                SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss dd.MM");
                try {
                    String first = null;
                    lostResponseTimes.clear();
                    for (JsonElement item : element.getAsJsonArray()) {
                        if (!item.isJsonObject()) {
                            // unexpected =>
                            // stop application pretty printing
                            statistic.setLength(0);
                            break;
                        }
                        JsonObject object = item.getAsJsonObject();
                        if (object.has("rid")) {
                            String rid = object.get("rid").getAsString();
                            long time = object.get("time").getAsLong();
                            if (rid.startsWith(REQUEST_ID_PREFIX)) {
                                if (first == null) {
                                    first = rid;
                                }
                                boolean hit = noResponseRids.contains(rid);
                                rid = rid.substring(REQUEST_ID_PREFIX.length());
                                long requestTime = Long.parseLong(rid);
                                if (hit) {
                                    lostResponseTimes.add(requestTime);
                                }
                                statistic.append("Request: ").append(format.format(requestTime));
                                long diff = time - requestTime;
                                if (-MAX_DIFF_TIME_IN_MILLIS < diff && diff < MAX_DIFF_TIME_IN_MILLIS) {
                                    statistic.append(", received: ").append(diff).append(" ms");
                                } else {
                                    statistic.append(", received: ").append(format.format(time));
                                }
                                if (hit) {
                                    statistic.append(" *");
                                }
                                statistic.append(StringUtil.lineSeparator());
                            } else {
                                statistic.append("Request: ").append(rid);
                                statistic.append(", received: ").append(format.format(time));
                                statistic.append(StringUtil.lineSeparator());
                            }
                        } else {
                            long time = object.get("systemstart").getAsLong();
                            statistic.append("Server's system start: ").append(format.format(time));
                            statistic.append(StringUtil.lineSeparator());
                        }
                    }
                    if (!lostResponseTimes.isEmpty()) {
                        statistic.append(" * lost responses!");
                        statistic.append(StringUtil.lineSeparator());
                    }
                    if (first != null) {
                        while (!noResponseRids.isEmpty()) {
                            if (noResponseRids.get(0).compareTo(first) >= 0) {
                                break;
                            }
                            noResponseRids.remove(0);
                        }
                    }
                } catch (Throwable e) {
                    // unexpected => stop application pretty printing
                    statistic.setLength(0);
                }
            }
            if (statistic.length() == 0) {
                // JSON plain pretty printing
                GsonBuilder builder = new GsonBuilder();
                builder.setPrettyPrinting();
                Gson gson = builder.create();
                gson.toJson(element, statistic);
            }
        } catch (JsonSyntaxException e) {
            // plain payload
            e.printStackTrace();
            statistic.setLength(0);
            statistic.append(payload);
        }
        return statistic.toString();
    }
}
