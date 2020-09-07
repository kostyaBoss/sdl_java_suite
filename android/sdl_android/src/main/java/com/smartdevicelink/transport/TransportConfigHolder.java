package com.smartdevicelink.transport;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TransportConfigHolder {

    private static TransportConfigHolder instance;
    BaseTransportConfig transport = null;

    public static TransportConfigHolder getInstance() {
        TransportConfigHolder localInstance = instance;
        if (localInstance == null) {
            synchronized (TransportConfigHolder.class) {
                localInstance = instance;
                if (localInstance == null) {
                    instance = localInstance = new TransportConfigHolder();
                }
            }
        }
        return localInstance;
    }

    /**
     * Provides the same BaseTransportConfig each time called
     * For the first time, creates instance of transport config to hold
     * */

    public BaseTransportConfig provide(
            Context context,
            @NonNull String transportType,
            @NonNull String securityType,
            @NonNull String applicationId,
            @Nullable Integer machinePort,
            @Nullable String machineIP
    ) {
        if (this.transport != null) {
            return this.transport;
        }
        BaseTransportConfig transport = null;
        switch (transportType) {
            case "MULTI":
                int securityLevel;
                switch (securityType) {
                    case "HIGH":
                        securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_HIGH;
                        break;
                    case "MED":
                        securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_MED;
                        break;
                    case "LOW":
                        securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_LOW;
                        break;
                    default:
                        securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF;
                        break;
                }
                transport = new MultiplexTransportConfig(context, applicationId, securityLevel);
                break;
            case "TCP":
                if (machineIP != null && !TextUtils.isEmpty(machineIP) && machinePort != null)
                    transport = new TCPTransportConfig(machinePort, machineIP, true);
                else
                    throw new RuntimeException("Invalid configuration, IP and/or Port should not be null or empty");
                break;
            case "MULTI_HB":
                MultiplexTransportConfig mtc = new MultiplexTransportConfig(
                        context,
                        applicationId,
                        MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF
                );
                mtc.setRequiresHighBandwidth(true);
                transport = mtc;
                break;
        }

        this.transport = transport;
        return this.transport;
    }
}
