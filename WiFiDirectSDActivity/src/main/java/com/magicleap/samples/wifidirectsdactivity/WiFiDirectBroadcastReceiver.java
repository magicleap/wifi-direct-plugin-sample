package com.magicleap.samples.wifidirectsdactivity;
// %BANNER_BEGIN%
// ---------------------------------------------------------------------
// %COPYRIGHT_BEGIN%
// Copyright (c) (2023) Magic Leap, Inc. All Rights Reserved.
// Use of this file is governed by the Software License Agreement, located here: https://www.magicleap.com/software-license-agreement-ml2
// Terms and conditions applicable to third-party materials accompanying this distribution may also be found in the top-level NOTICE file appearing herein.
// %COPYRIGHT_END%
// ---------------------------------------------------------------------
// %BANNER_END%

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.util.Log;

import com.unity3d.player.UnityPlayer;

public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "WDPluginActivity-BR";
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private WDPluginActivity activity;

    /**
     * BroadcastReceiver that handles Wifi Direct intents
     *
     * @param manager  WifiP2pManager system service
     * @param channel  Wifi p2p channel
     * @param activity Android Activity passed as reference for access from the receiver
     */
    protected WiFiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel, WDPluginActivity activity) {
        super();
        this.manager = manager;
        this.channel = channel;
        this.activity = activity;
        Log.d(TAG + "-Init", "Broadcast Receiver obj initialized.");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG + "-oR", "BroadcastReceiver received: action- " + action + " context- " + context.toString() + " intent- " + intent.toString());
        if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            Log.d(TAG + "-oR-CCA", "BroadcastReceiver entered handler: " + action);
            if (manager == null) {
                return;
            }

            //for debuging...
            WifiP2pInfo p2pInfo = (WifiP2pInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
            Log.d(TAG + "-oR-CCA", "WifiP2pInfo " + (p2pInfo != null ? p2pInfo.toString() : "null"));
            WifiP2pGroup p2pGroup = (WifiP2pGroup) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
            Log.d(TAG + "-oR-CCA", "WifiP2pGroup " + (p2pGroup != null ? p2pGroup.toString() : "null"));

            // if a group just got formed we should request connection info
            if (p2pInfo.groupFormed) {
                Log.d(TAG + "-oR-CCA", "Group formed. Requesting network info");
                manager.requestConnectionInfo(channel, (WifiP2pManager.ConnectionInfoListener) activity.connectionInfoListener);
            } else if (isP2PNetworkAvailable((Application) context.getApplicationContext())) {
                Log.d(TAG + "-oR-CCA", "Connected to p2p network. Requesting network info");
                manager.requestConnectionInfo(channel, (WifiP2pManager.ConnectionInfoListener) activity.connectionInfoListener);
            } else {
                // No P2P network connection
                if (activity.isConnected) {
                    //Connection dropped
                    activity.isConnected = false;
                    if (activity.isHost) {
                        //Unity Plugin API Callback:
                        UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayConnectedDevices", "");
                        activity.isHost = false;
                    } else {
                        //Unity Plugin API Callback:
                        UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayConnectedServiceFriendlyName", "");
                    }
                }
                Log.d(TAG + "-oR-CCA", "Connection changed, currently no P2P Connection");
            }
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            Log.d(TAG + "-oR-TDCA", "BroadcastReceiver entered handler: " + action);
            WifiP2pDevice device = (WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
            Log.i(TAG + "-oR-TDCA", "Local Device status :" + (device != null ? device.status + " " + device.toString() + "\nisGroupOwner: " + device.isGroupOwner() + "\nServiceDiscovery " + device.isServiceDiscoveryCapable() : "device is null"));

        } else if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                // Wifi Direct mode is enabled
                Log.d(TAG + "-oR-SCA", "WiFi Direct mode is enabled: " + state);
            } else {
                activity.ShowToUser("WiFi Direct mode is disabled on this device. You may need to turn it on under settings.");
                Log.d(TAG + "-oR-SCA", "WiFi Direct mode is disabled: " + state);
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            WifiP2pDeviceList peersList = intent.getParcelableExtra(WifiP2pManager.EXTRA_P2P_DEVICE_LIST);
            Log.d(TAG + "-oR-PCA", "Peers changed: " + (peersList.toString().length() > 0 ? peersList.toString() : "No Peers"));
            for (WifiP2pDevice device : peersList.getDeviceList()) {
                Log.d(TAG + "-oR-PCA", "Device: " + device.toString());
                Log.i(TAG + "-oR-PCA", "Service Discovery Capable: " + device.isServiceDiscoveryCapable());
            }
        }
    }

    private Boolean isP2PNetworkAvailable(Application application) {
        Log.d(TAG + "-iNA", "BroadcastReceiver BuildVer: " + Build.VERSION.SDK_INT);

        ConnectivityManager connectivityManager = (ConnectivityManager) application.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = connectivityManager.getActiveNetwork();
        Log.d(TAG + "-iNA", "network: " + (network != null ? network.toString() : "null"));
        if (network == null) return false;
        NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(network);
        Log.d(TAG + "-iNA", "NetworkCapabilities  " + (actNw != null ? actNw.toString() : "null"));
        Log.d(TAG + "-iNA", "has NetworkCapabilities transport_wifi " + (actNw != null ? String.valueOf(actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) : "null"));
        Log.d(TAG + "-iNA", "has NetworkCapabilities Net_capability_wifi_p2p " + (actNw != null ? String.valueOf(actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_WIFI_P2P)) : "null"));

        return actNw != null && actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_WIFI_P2P);
    }
}
