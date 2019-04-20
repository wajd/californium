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
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.preference.PreferenceManager;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.elements.DtlsEndpointContext;
import org.eclipse.californium.elements.EndpointContext;
import org.eclipse.californium.elements.util.StringUtil;
import org.eclipse.californium.examples.coaputil.ReceivetestClient;
import org.eclipse.californium.examples.dns.DnsCache;
import org.eclipse.californium.examples.coaputil.CoapProgress;
import org.eclipse.californium.examples.coaputil.CoapTask;
import org.eclipse.californium.examples.coaputil.CoapTaskResult;
import org.eclipse.californium.scandium.dtls.SessionCache;
import org.eclipse.californium.scandium.dtls.SessionId;
import org.eclipse.californium.scandium.dtls.SessionTicket;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.eclipse.californium.core.coap.CoAP.ResponseCode.CONTENT;
import static org.eclipse.californium.core.coap.MediaTypeRegistry.APPLICATION_JSON;
import static org.eclipse.californium.examples.PreferenceIDs.COAP_LOG;
import static org.eclipse.californium.examples.PreferenceIDs.COAP_LOG_RSSI;
import static org.eclipse.californium.examples.PreferenceIDs.COAP_NO_RESPONSE_RIDS;
import static org.eclipse.californium.examples.PreferenceIDs.PREF_FILE_LOG;

public class LoggingCoapTask extends CoapTask {
    private long startTime;
    private long startNano;
    private boolean success;
    private Context applicationContext;
    private String reason;

    protected LoggingCoapTask(Context applicationContext) {
        if (applicationContext instanceof Activity) {
            throw new IllegalArgumentException("Replace ActivityContext by ApplicationContext!");
        }
        this.applicationContext = applicationContext;
    }

    protected boolean isSuccess() {
        return success;
    }

    public void cancel(String reason, boolean mayInterruptIfRunning) {
        if (!isCancelled() && applicationContext != null && this.reason == null) {
            this.reason = reason;
            cancel(mayInterruptIfRunning);
        }
    }

    @Override
    protected void onPreExecute() {
        startTime = System.currentTimeMillis();
        startNano = System.nanoTime();
    }

    @Override
    protected void onProgressUpdate(CoapProgress... state) {
        if (state != null && state.length == 1) {
            CoapProgress progress = state[0];

            if (progress.host != null && progress.hostAddresses != null && progress.storeDns) {
                DnsCache dnsCache = CoapContext.getInstance().dnsCache;
                if (dnsCache != null) {
                    dnsCache.putAddresses(applicationContext, progress.host, null, progress.hostAddresses);
                }
            }
        }
    }

    @Override
    protected void onPostExecute(CoapTaskResult result) {
        success = result.response != null;
        Statistic statistic = null;
        String connectivity = CoapContext.getInstance().getConnectivityType();
        if (connectivity != null) {
            Log.i(LOG_TAG, "result: " + result.response);
            Log.i(LOG_TAG, "error: " + result.error);
            statistic = Statistic.updateStatistic(applicationContext, startTime, arguments.endpointsChanged, result);
        }
        File coaplog = getLogFile(applicationContext);
        if (coaplog != null) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
            boolean rssi = preferences.getBoolean(COAP_LOG_RSSI, false);
            if (rssi) {
                int permission = ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION);
                rssi = permission == PackageManager.PERMISSION_GRANTED;
            }
            if (result.rid != null) {
                String noResponseRids = preferences.getString(COAP_NO_RESPONSE_RIDS, "");
                Log.i(LOG_TAG, "noResponseRids: " + noResponseRids);
                List<String> rids = ReceivetestClient.parse(noResponseRids);
                if (result.response != null) {
                    Response response = result.response.advanced();
                    if ((response.getOptions().getContentFormat() == APPLICATION_JSON) && (response.getCode() == CONTENT)) {
                        List<Long> lostResponseTimes = new ArrayList<>();
                        ReceivetestClient.processJSON(response.getPayloadString(), rids, true, lostResponseTimes);
                        Statistic.updateLostResponses(applicationContext, lostResponseTimes);
                        for (Long time : lostResponseTimes) {
                            Log.i(LOG_TAG, "lost: " + time);
                        }
                    }
                } else {
                    rids.add(result.rid);
                    if (rids.size() > 10) {
                        rids.remove(0);
                    }
                }
                String newNoResponseRids = ReceivetestClient.serialize(rids);
                if (!newNoResponseRids.equals(noResponseRids)) {
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString(COAP_NO_RESPONSE_RIDS, newNoResponseRids);
                    editor.apply();
                    Log.i(LOG_TAG, "new noResponseRids: " + newNoResponseRids);
                }
            }

            printLog(coaplog, result, rssi, connectivity, statistic);

            done(applicationContext);
        }
        applicationContext = null;
    }

    protected void onCancelled(CoapTaskResult result) {
        if (applicationContext != null && arguments != null) {
            File coaplog = getLogFile(applicationContext);
            if (coaplog != null) {
                try (FileOutputStream out = new FileOutputStream(coaplog, true)) {
                    Log.i(LOG_TAG, "cancel task, write to file " + coaplog);
                    PrintWriter writer = new PrintWriter(out);
                    SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
                    StringBuilder text = new StringBuilder();
                    long executeTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
                    text.append("====== ").append(format.format(startTime)).append(" === ");
                    text.append(formatDuration(executeTime)).append(" ======");
                    if (arguments.jobId != null) {
                        text.append(" (ID").append(arguments.jobId).append(")");
                    }
                    if (arguments.endpointsChanged) {
                        text.append(" (new, cancelled)");
                    } else {
                        text.append(" (cancelled)");
                    }
                    writer.println(text);
                    if (reason != null) {
                        writer.println(reason);
                    }
                    writer.flush();
                    Log.i(LOG_TAG, "written " + text + ", " + reason);
                } catch (FileNotFoundException e) {
                    Log.i(LOG_TAG, "file: " + coaplog + " not found!", e);
                } catch (IOException e) {
                    Log.i(LOG_TAG, "i/o-error: " + coaplog, e);
                }
                done(applicationContext);
            }
        }
        onCancelled();
        applicationContext = null;
    }

    public void printLog(File coaplog, CoapTaskResult result, boolean telephonyPermission, String connectivityType, Statistic statistic) {
        try (FileOutputStream out = new FileOutputStream(coaplog, true)) {
            Log.i(LOG_TAG, "write to file " + coaplog);
            PrintWriter writer = new PrintWriter(out);
            SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
            StringBuilder text = new StringBuilder();
            long executeTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
            text.append("====== ").append(format.format(startTime)).append(" === ");
            text.append(formatDuration(executeTime)).append(" ======");
            if (arguments.jobId != null) {
                text.append(" (ID").append(arguments.jobId).append(")");
            }
            if (arguments.endpointsChanged) {
                text.append(" (new)");
            }
            writer.println(text);
            printConnectivity(writer, telephonyPermission, connectivityType);
            text.setLength(0);
            long rx = result.rxEnd - result.rxStart;
            long tx = result.txEnd - result.txStart;
            text.append("tx: ").append(tx).append(" bytes , rx: ").append(rx).append(" bytes");
            writer.println(text);
            if (result.connectOnRetry && result.rxTotal != null) {
                text.setLength(0);
                text.append("rx-total: ");
                for (long totalRx : result.rxTotal) {
                    text.append(totalRx).append(", ");
                }
                text.setLength(text.length() - 2);
                text.append(" bytes");
                writer.println(text);
            }
            if (result.uri != null) {
                try {
                    URI uri = new URI(result.uri.getScheme(), result.uri.getUserInfo(), result.uri.getHost(), result.uri.getPort(), result.uri.getPath(), null, null);
                    writer.println(uri);
                } catch (URISyntaxException e) {
                    Log.e(LOG_TAG, "URI:", e);
                }
            }

            if (result.response != null) {
                Response response = result.response.advanced();
                String payload = response.getPayloadString();
                writer.println(response.getCode() + ": " + payload);
                text.setLength(0);
                text.append("RTT: ").append(formatDuration(response.getRTT()));
                if (result.coapRetransmissions > 0) {
                    text.append(" (").append(result.coapRetransmissions).append(" retransmission)");
                }
                if (response.getPayload() != null) {
                    text.append(", ").append(response.getPayloadSize()).append(" bytes");
                }
                if (result.blocks > 1) {
                    text.append(", ").append(result.blocks).append(" blocks");
                }
                if (result.connectOnRetry) {
                    text.append(", reconnect");
                }
                writer.println(text);
                EndpointContext context = response.getSourceContext();
                Principal identity = context.getPeerIdentity();
                if (identity != null) {
                    writer.println(identity.getName());
                }
                text.setLength(0);
                String session = context.get(DtlsEndpointContext.KEY_SESSION_ID);
                if (session != null) {
                    text.append(session.substring(0, 10));
                    SessionCache sessionCache = CoapContext.getInstance().sessionCache;
                    if (sessionCache != null) {
                        SessionId id = new SessionId(StringUtil.hex2ByteArray(session));
                        SessionTicket ticket = sessionCache.get(id);
                        if (ticket != null) {
                            long timestamp = ticket.getTimestamp();
                            String time = format.format(timestamp);
                            text.append(" - ").append(time);
                        }
                    }
                    if (result.connectNanoTime != null) {
                        text.append(" (");
                        text.append(formatDuration(TimeUnit.NANOSECONDS.toMillis(result.connectNanoTime)));
                        int retries = result.dtlsRetransmissions;
                        if (retries > 0) {
                            text.append(", ").append(retries).append(" retransmissions");
                        }
                        text.append(")");
                    }
                    writer.println(text);
                } else if (result.connectNanoTime != null) {
                    text.append("connect: ");
                    text.append(formatDuration(TimeUnit.NANOSECONDS.toMillis(result.connectNanoTime)));
                    int retries = result.dtlsRetransmissions;
                    if (retries > 0) {
                        text.append(", ").append(retries).append(" retransmissions");
                    }
                    writer.println(text);
                }
                if (result.dnsResolveNanoTime != null) {
                    text.setLength(0);
                    text.append("dns: ").append(result.host).append(" ");
                    text.append(formatDuration(TimeUnit.NANOSECONDS.toMillis(result.dnsResolveNanoTime)));
                    writer.println(text);
                }
            } else {
                if (result.error != null) {
                    result.error.printStackTrace(writer);
                } else {
                    writer.println("no response.");
                }
                if (result.connectOnRetry) {
                    writer.println("reconnect");
                }
                if (result.coapRetransmissions > 0) {
                    writer.println(result.coapRetransmissions + " retransmissions");
                }
                if (result.connectNanoTime != null) {
                    text.setLength(0);
                    text.append("connect: ");
                    text.append(formatDuration(TimeUnit.NANOSECONDS.toMillis(result.connectNanoTime)));
                    if (result.dtlsRetransmissions > 0) {
                        text.append(", ").append(result.dtlsRetransmissions).append(" retransmissions");
                    }
                    writer.println(text);
                }
                if (result.dnsResolveNanoTime != null) {
                    text.setLength(0);
                    text.append("dns: ").append(result.host).append(" ");
                    text.append(formatDuration(TimeUnit.NANOSECONDS.toMillis(result.dnsResolveNanoTime)));
                    writer.println(text);
                }
            }
            if (statistic != null) {
                text.setLength(0);
                text.append("success: ").append(statistic.success);
                text.append(", retransmissions: ").append(statistic.retries);
                text.append(", failures: ").append(statistic.failures);
                text.append(", connects: ").append(statistic.connects);
                writer.println(text);
                writer.println(statistic.getSummary());
            }
            text.setLength(0);
            text.append("----- ").append(format.format(System.currentTimeMillis())).append(" -----");
            writer.println(text);
            writer.flush();
        } catch (FileNotFoundException e) {
            Log.i(LOG_TAG, "file: " + coaplog + " not found!", e);
        } catch (IOException e) {
            Log.i(LOG_TAG, "i/o-error: " + coaplog, e);
        }
    }

    private void printConnectivity(PrintWriter writer, boolean telephonyPermission, String type) {
        if (type == null) {
            writer.println("no network!");
        } else {
            writer.println("network: " + type);
            if (type == CoapContext.MOBILE) {
                if (telephonyPermission) {
                    printTelephony(writer);
                }
            } else if (type == CoapContext.WIFI) {
                printWifi(writer);
            }
        }
    }

    private void printTelephony(PrintWriter writer) {
        TelephonyManager telephonyManager = (TelephonyManager) applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            try {
                List<CellInfo> infos = telephonyManager.getAllCellInfo();
                if (infos != null) {
                    for (CellInfo info : infos) {
                        if (info.isRegistered()) {
                            String tag = null;
                            String net = null;
                            CellSignalStrength cellSignalStrength = null;
                            if (info instanceof CellInfoGsm) {
                                CellInfoGsm cellInfo = (CellInfoGsm) info;
                                cellSignalStrength = cellInfo.getCellSignalStrength();
                                net = cellInfo.getCellIdentity().toString();
                                tag = "gsm-rssi: ";
                            } else if (info instanceof CellInfoLte) {
                                CellInfoLte cellInfo = (CellInfoLte) info;
                                cellSignalStrength = cellInfo.getCellSignalStrength();
                                net = cellInfo.getCellIdentity().toString();
                                tag = "lte-rssi: ";
                            } else if (info instanceof CellInfoCdma) {
                                CellInfoCdma cellInfo = (CellInfoCdma) info;
                                cellSignalStrength = cellInfo.getCellSignalStrength();
                                net = cellInfo.getCellIdentity().toString();
                                tag = "cdma-rssi: ";
                            } else if (info instanceof CellInfoWcdma) {
                                CellInfoWcdma cellInfo = (CellInfoWcdma) info;
                                cellSignalStrength = cellInfo.getCellSignalStrength();
                                net = cellInfo.getCellIdentity().toString();
                                tag = "wcdma-rssi: ";
                            }
                            if (cellSignalStrength != null) {
                                writer.println(tag + cellSignalStrength.getDbm() + " dBm, " + net);
                            } else {
                                writer.println("mobile registered to unknown technic " + info);
                            }
                        }
                    }
                }
            } catch (SecurityException e) {
                Log.i(LOG_TAG, "security-error: ", e);
            }
        }
    }

    private void printWifi(PrintWriter writer) {
        WifiManager wifiManager = (WifiManager) applicationContext.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            WifiInfo info = wifiManager.getConnectionInfo();
            if (info.getNetworkId() != -1) {
                writer.println("rssi: " + info.getRssi() + " dBm, SSID " + info.getSSID());
            }
        }
    }

    public static File getLogFile(Context applicationContext) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        if (preferences.getBoolean(PREF_FILE_LOG, false)) {
            return getFile(applicationContext);
        } else {
            Log.i(LOG_TAG, "preference log disabled!");
            return null;
        }
    }

    public static void cleanup(Context applicationContext) {
        File logFile = getFile(applicationContext);
        if (logFile != null) {
            logFile.delete();
            done(applicationContext);
        }
    }

    private static File getFile(Context applicationContext) {
        File coaplog = null;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        if (preferences.getBoolean(COAP_LOG, false)) {
            int permission = ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (permission == PackageManager.PERMISSION_GRANTED) {
                String state = Environment.getExternalStorageState();
                if (Environment.MEDIA_MOUNTED.equals(state)) {
                    File directory = Environment.getExternalStorageDirectory();
                    coaplog = new File(directory, "coap.log");
                } else {
                    Log.i(LOG_TAG, "log not available!");
                }
            } else {
                Log.i(LOG_TAG, "log not permitted!");
            }
        } else {
            Log.i(LOG_TAG, "log disabled!");
        }
        return coaplog;
    }

    private static void done(Context applicationContext) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        SharedPreferences.Editor edit = preferences.edit();
        edit.putLong(PreferenceIDs.COAP_LOG_DONE, System.currentTimeMillis());
        edit.apply();
    }
}
