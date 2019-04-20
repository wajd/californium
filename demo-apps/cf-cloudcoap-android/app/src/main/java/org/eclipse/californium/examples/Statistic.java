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
import android.support.v7.preference.PreferenceManager;

import org.eclipse.californium.elements.util.StringUtil;
import org.eclipse.californium.examples.coaputil.CoapTaskResult;

import java.util.List;

import static org.eclipse.californium.examples.PreferenceIDs.COAP_STATISTIC;

public class Statistic {
    private static final String SEP = ";";

    long success;
    long failures;
    long connects;
    long retries;
    long restarts;
    long lostResponses;
    long rx;
    long tx;
    long rtt;
    long lastSuccess;
    long lastFailure;
    long lastConnect;
    long lastRetries;
    long lastRestart;
    long lastLostResponses;
    long rxLast;
    long txLast;
    long rxAll;
    long txAll;

    public Statistic() {
    }

    public Statistic(String values) {
        this();
        String[] split = values.split(SEP);
        if (split.length == 19) {
            success = parse(split[0]);
            failures = parse(split[1]);
            connects = parse(split[2]);
            retries = parse(split[3]);
            restarts = parse(split[4]);
            lostResponses = parse(split[5]);
            rx = parse(split[6]);
            tx = parse(split[7]);
            rtt = parse(split[8]);
            lastSuccess = parse(split[9]);
            lastFailure = parse(split[10]);
            lastConnect = parse(split[11]);
            lastRetries = parse(split[12]);
            lastRestart = parse(split[13]);
            lastLostResponses = parse(split[14]);
            rxLast = parse(split[15]);
            txLast = parse(split[16]);
            rxAll = parse(split[17]);
            txAll = parse(split[18]);
        }
    }

    public String getSummary() {
        StringBuilder builder = new StringBuilder();
        if (success > 0) {
            long total = failures + success;
            builder.append("avg: tx ").append(tx / success);
            builder.append(" bytes, rx ").append(rx / success);
            builder.append(" bytes, ").append(rtt / success).append(" [ms]");
            builder.append(StringUtil.lineSeparator());
            if (restarts > 0 || retries > 0 || failures > 0) {
                builder.append(((restarts * 100L) + (total / 2)) / total).append("% restarts, ");
                builder.append(((retries * 100L) + (total / 2)) / total).append("% retransmissions, ");
                builder.append(((failures * 100L) + (total / 2)) / total).append("% failures");
                builder.append(StringUtil.lineSeparator());
            }
            builder.append("avg-all: tx ").append(txAll / success);
            builder.append(" bytes, rx ").append(rxAll / success);
            builder.append(" bytes");
            if (lostResponses > 0) {
                builder.append(StringUtil.lineSeparator());
                builder.append("responses lost: ").append(lostResponses);
            }
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(success).append(SEP);
        builder.append(failures).append(SEP);
        builder.append(connects).append(SEP);
        builder.append(retries).append(SEP);
        builder.append(restarts).append(SEP);
        builder.append(lostResponses).append(SEP);
        builder.append(rx).append(SEP);
        builder.append(tx).append(SEP);
        builder.append(rtt).append(SEP);
        builder.append(lastSuccess).append(SEP);
        builder.append(lastFailure).append(SEP);
        builder.append(lastConnect).append(SEP);
        builder.append(lastRetries).append(SEP);
        builder.append(lastRestart).append(SEP);
        builder.append(lastLostResponses).append(SEP);
        builder.append(rxLast).append(SEP);
        builder.append(txLast).append(SEP);
        builder.append(rxAll).append(SEP);
        builder.append(txAll);
        return builder.toString();
    }

    private long parse(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public static Statistic getStatistic(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String value = preferences.getString(COAP_STATISTIC, "");
        return new Statistic(value);
    }

    public static void saveStatistic(Context context, Statistic statistic) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().putString(COAP_STATISTIC, statistic.toString()).apply();
    }

    public static void clearStatistic(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().remove(COAP_STATISTIC).apply();
    }

    public static Statistic updateStatistic(Context context, long startTime, boolean restart, CoapTaskResult result) {
        boolean reset = false;
        Statistic statistic = getStatistic(context);
        if (result.txStart < 0 || result.rxStart < 0 || result.txEnd < 0 || result.rxEnd < 0 ||
                result.txStart > result.txEnd || result.rxStart > result.rxEnd) {
            return statistic;
        }
        if (statistic.success == 0 && statistic.failures == 0) {
            statistic.rxAll = 0;
            statistic.txAll = 0;
            reset = true;
        }
        if (reset || result.rxStart < statistic.rxLast || result.txStart < statistic.txLast) {
            // device restart
            statistic.rxLast = result.rxStart;
            statistic.txLast = result.txStart;
        }
        if (result.connectNanoTime != null) {
            statistic.connects++;
            statistic.lastConnect = startTime;
        }
        if (result.retransmissions > 0) {
            statistic.retries += result.retransmissions;
            statistic.lastRetries = startTime;
        }
        if (restart) {
            statistic.restarts++;
            statistic.lastRestart = startTime;
        }
        statistic.rxAll += result.rxEnd - statistic.rxLast;
        statistic.txAll += result.txEnd - statistic.txLast;
        statistic.rxLast = result.rxEnd;
        statistic.txLast = result.txEnd;
        if (result.response != null) {
            statistic.success++;
            statistic.lastSuccess = startTime;
            statistic.rx += (result.rxEnd - result.rxStart);
            statistic.tx += (result.txEnd - result.txStart);
            statistic.rtt += result.response.advanced().getRTT();
        } else {
            boolean failed = true;
            if (result.error != null) {
                String message = result.error.getMessage();
                // ignore, usually wifi or mobile get's lost during sending
                failed = !message.contains("EPERM") && !message.contains("ENETUNREACH");
            }
            if (failed) {
                statistic.failures++;
                statistic.lastFailure = startTime;
            }
        }
        saveStatistic(context, statistic);
        return statistic;
    }

    public static Statistic updateLostResponses(Context context, List<Long> lostResponseTimes) {
        boolean changed =false;
        Statistic statistic = getStatistic(context);
        for (Long time : lostResponseTimes) {
            if (statistic.lastLostResponses < time) {
                statistic.lostResponses++;
                statistic.lastLostResponses = time;
                changed = true;
            }
        }
        if (changed) {
            saveStatistic(context, statistic);
        }
        return statistic;
    }
}
