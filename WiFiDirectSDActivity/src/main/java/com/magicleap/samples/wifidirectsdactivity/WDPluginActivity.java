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

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.nsd.NsdManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;

import androidx.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.unity3d.player.UnityPlayer;

public class WDPluginActivity extends UnityPlayerActivity {

    private static final int PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION = 1001;
    private static final String TAG = "WDPluginActivity";
    private static final IntentFilter intentFilter = new IntentFilter();

    private WifiP2pDnsSdServiceRequest serviceRequest;
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver receiver;
    private WifiP2pDnsSdServiceInfo serviceInfo;

    private WifiP2pDevice thisDevice;

    private String whoAmI = "I Unknown";
    private String friendlyServiceName;

    private int msgCount = 0;
    private boolean isServiceRegistered = false;
    private boolean isDoingDiscovery = false;
    protected boolean isConnected = false;
    protected boolean isHost;
    private boolean firstTimeHost = true;

    private ServerClass serverClass;
    private ClientClass clientClass;

    private List<WifiP2pDevice> connectedPeers = new ArrayList<>();
    private String[] deviceNameArray;

    ///Config
    private String deviceUser = "Magic Leap User-";
    private String deviceUserIdStr = "" + ((int) (Math.random() * 1000));
    private final int SERVER_PORT = 4545;
    private String serviceName = "_magictest";
    private final String SERVICE_TYPE = "_presence._tcp";

    private final HashMap<String, String> buddies = new HashMap<String, String>();
    private String[] serviceNameArray;
    private String[] peerNameArray;
    private WDPluginActivity activity;

    /**
     * Five character long message type names
     * <p>
     * used as prefixes to identify message types sent to connected devices
     */
    protected enum MsgTypes {
        /**
         * Device name identifier message
         */
        DNAME,
        /**
         * UI Command message
         */
        UICMD,
        /**
         * Chat Text message
         */
        CHATT
    }

    /**
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.activity = this;

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        Log.d(TAG + "-Init", "Broadcast Receiver IntentFilter actions added.");

        if (!initP2p()) {
            finish();
        }

        Log.d(TAG + "-Init", "manager obj check, is null: " + (manager == null));
        Log.d(TAG + "-Init", "CheckPermission:  " + checkSelfPermission((Manifest.permission.ACCESS_FINE_LOCATION)));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION);
            // After this point you wait for callback in
            // onRequestPermissionsResult(int, String[], int[]) overridden method
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG + "-oRPR", "requestCode: " + requestCode);
        Log.d(TAG + "-oRPR", "Permissions: " + permissions);
        Log.d(TAG + "-oRPR", "GrantResults: " + grantResults);
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION:
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG + "-oRPR", "Fine location permission is not granted!");
                    finish();
                } else {
                    Log.d(TAG + "-oRPR", "Fine location permission granted.");
                }
                break;
        }
    }

    /* register the broadcast receiver with the intent values to be matched */
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, intentFilter);
        Log.d(TAG + "-APP-oR", "Broadcast Receiver registered with IntentFilter.");
        Log.d(TAG + "-APP-oR", "App resumed.");
    }

    /* unregister the broadcast receiver */
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
        Log.d(TAG + "-APP-oP", "Broadcast Receiver unregistered.");
        Log.d(TAG + "-APP-oP", "App paused.");
        if (isFinishing()) {
            Log.d(TAG + "-APP-oP", "Is Connected: " + isConnected);
            if (isConnected) {

            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG + "-APP-oD", "Activity is being Destroyed.");
        Log.d(TAG + "-APP-oD", "Activity is finishing: " + isFinishing());
        if (isFinishing()) {
            Log.d(TAG + "-APP-oD", "Is Connected: " + isConnected);
            if (isConnected) {
                manager.removeGroup(channel, new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        //Unity Plugin API Callback:
                        UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayConnectedDevices", "");
                        //Unity Plugin API Callback:
                        UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayConnectedServiceFriendlyName", "");
                        Log.d(TAG + "-APP-oD", "Group removed successfully.");
                        isConnected = false;
                        isHost = false;
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.e(TAG + "-APP-oD", "Group removal failed. Reason: " + reason);
                    }
                });
            }
        }
        super.onDestroy();
    }

    /**
     * Initialize for Peer-to-Peer
     * <p><ul>
     * <li>Checks device, hardware  compatibilities
     * <li>Initializes P2P services, objects, & Broadcast Receiver
     * <li>Sends initial calls to worm up services
     * <li>Ensures standardized device name
     * </ul>
     *
     * @return true if initialization succeeds
     */
    private boolean initP2p() {
        // Device capability definition check
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) {
            Log.e(TAG + "-InitP2p", "Wi-Fi Direct is not supported by this device.");
            return false;
        }
        // Hardware capability check
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifiManager == null) {
            Log.e(TAG + "-InitP2p", "Cannot get Wi-Fi system service.");
            return false;
        } else {
            Log.d(TAG + "-InitP2p", "WiFi Manager: " + wifiManager.toString());
        }
        if (!wifiManager.isP2pSupported()) {
            Log.e(TAG + "-InitP2p", "Wi-Fi Direct is not supported by the hardware or Wi-Fi is off.");
            return false;
        }

        //Initialize manager and channel objects for use throughout
        manager = (WifiP2pManager) UnityPlayer.currentActivity.getApplicationContext().getSystemService(WIFI_P2P_SERVICE);
        if (manager == null) {
            Log.e(TAG + "-InitP2p", "Cannot get Wi-Fi Direct system service.");
            return false;
        } else {
            Log.d(TAG + "-InitP2p", "WiFi P2P Manager: " + manager.toString());
        }
        channel = manager.initialize(this, getMainLooper(), new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected() {
                Log.e(TAG + "-InitP2p-oCD", "Channel disconnected");
            }
        });
        if (channel == null) {
            Log.e(TAG + "-InitP2p", "Cannot initialize Wi-Fi Direct.");
            return false;
        } else {
            Log.d(TAG + "-InitP2p", "Channel: " + channel.toString());
        }
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        Log.d(TAG + "-InitP2p", "Broadcast Receiver instantiated.");

        //nice to know info only
        NsdManager nsdManager = (NsdManager) getApplicationContext().getSystemService(NSD_SERVICE);
        if (nsdManager == null) {
            //("This device does NOT have NSD Service support.");
        } else {
            //("This device DOES appear to have NSD Service support.");
            Log.d(TAG + "-InitP2p", "Device NSD Manager: " + nsdManager.toString());
        }

        //nice to know info only
        WifiRttManager rttManager = (WifiRttManager) getApplicationContext().getSystemService(WIFI_RTT_RANGING_SERVICE);
        if (rttManager == null) {
            //("This device does NOT have RTT support.");
        } else {
            //("This device DOES appears to have support for RTT.");
            Log.d(TAG + "-InitP2p", "Device RTT Manager: " + rttManager.toString());
        }

        //nice to know info only
        boolean hasRTT = getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT);
        Log.i(TAG + "-InitP2p", "Wi-Fi RTT support on this device? : " + hasRTT);

        //nice to know info only
        boolean hasWiFiAware = getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE);
        Log.i(TAG + "-InitP2p", "Wi-Fi Aware support on this device? : " + hasWiFiAware);

        //nice to know info only
        //boolean canRemoveClientFromGroup = manager.isGroupClientRemovalSupported();
        //Log.d(TAG + "-Init", "Group Client Remove Support: " + canRemoveClientFromGroup);
        //(apparently not supported) may require Android 13

        //Clear/Activate/warm-up services
        clearLocalServices();
        prepServiceDiscovery();  //otherwise create service fails on first run
        Disconnect();  //otherwise createGroup fails on first run

        //Request info about this device, standardize name so other ML2 device users recognize it
        manager.requestDeviceInfo(channel, new WifiP2pManager.DeviceInfoListener() {
            @Override
            public void onDeviceInfoAvailable(@Nullable WifiP2pDevice wifiP2pDevice) {
                thisDevice = wifiP2pDevice;
                if (thisDevice != null) {
                    if (!thisDevice.deviceName.contains(deviceUser)) {
                        whoAmI = deviceUser + deviceUserIdStr;
                        setDeviceName(whoAmI);
                    } else {
                        whoAmI = thisDevice.deviceName;
                    }
                    Log.i(TAG + "-InitP2p", "Local Device status -" + (thisDevice != null ? thisDevice.status + "\n" + thisDevice.toString() + " ServiceDiscovery " + thisDevice.isServiceDiscoveryCapable() : "device is null"));
                }
            }
        });

        Log.d(TAG + "-InitP2p", "initP2P completed successfully.");
        return true;
    }

    private void setDeviceName(String devName) {
        try {
            Class[] paramTypes = new Class[3];
            paramTypes[0] = WifiP2pManager.Channel.class;
            paramTypes[1] = String.class;
            paramTypes[2] = WifiP2pManager.ActionListener.class;
            Method setDeviceName = manager.getClass().getMethod(
                    "setDeviceName", paramTypes);
            setDeviceName.setAccessible(true);

            Object arglist[] = new Object[3];
            arglist[0] = channel;
            arglist[1] = devName;
            arglist[2] = new WifiP2pManager.ActionListener() {

                @Override
                public void onSuccess() {
                    Log.d(TAG + "-sDN", "setDeviceName succeeded");
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG + "-sDN", "setDeviceName failed");
                }
            };

            setDeviceName.invoke(manager, arglist);

        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private void prepServiceDiscovery() {
        //triggered at app launch since it seems that the Host service needs to have had the discovery Services launched first
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        manager.addServiceRequest(channel, serviceRequest,
                new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        Log.d(TAG + "-pSD", "Service Request added successfully.");

                        manager.discoverServices(channel,
                                new WifiP2pManager.ActionListener() {

                                    @Override
                                    public void onSuccess() {
                                        Log.d(TAG + "-pSD", "Service Discovery prep initiated successfully.");
                                        clearDiscoveryRequest();
                                    }

                                    @Override
                                    public void onFailure(int code) {
                                        Log.e(TAG + "-pSD", "Service Discovery failed to initiate. Reason: " + code);

                                    }
                                });
                    }

                    @Override
                    public void onFailure(int code) {
                        Log.e(TAG + "-pSD", "Service Request prep add attempt failed. Reason: " + code);
                    }
                });
    }

    private void clearDiscoveryRequest() {

        manager.clearServiceRequests(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG + "-cDR", "Clear discovery service request succeeded.");
                //Unity Plugin API Callback:
                UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayDiscoveryStatus", "");
                isDoingDiscovery = false;
                //updateDiscoverServiceButton();
            }

            @Override
            public void onFailure(int i) {
                Log.e(TAG + "-cDR", "Clear discovery service request failed. Reason: " + i);
                //showToUser("DiscoverServices failed to stop.");
            }
        });
    }

    private void clearLocalServices() {

        manager.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                isServiceRegistered = false;
                Log.d(TAG + "-Service-cLS", "LocalServices cleared.");
            }

            @Override
            public void onFailure(int i) {
                //showToUser("Warning, Local services failed to Stop.");
                Log.e(TAG + "-Service-cLS", "LocalServices failed to clear. Reason " + i);
            }
        });
    }

    private void Disconnect() {
        Log.d(TAG + "-d", "Drop connection initiated.");
        if (manager != null && channel != null) {
            manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                    if (group != null && manager != null && channel != null) {
                        Log.d(TAG + "-d", "Removing Group: \n" + group);
                        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {

                            @Override
                            public void onSuccess() {
                                ShowToUser("Connection Dropped");
                                //Unity Plugin API Callback:
                                UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayConnectedDevices", "");
                                //Unity Plugin API Callback:
                                UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayConnectedServiceFriendlyName", "");
                                Log.d(TAG + "-d", "Group removed successfully.");
                                isConnected = false;
                                isHost = false;
                            }

                            @Override
                            public void onFailure(int reason) {
                                Log.e(TAG + "-d", "Group removal failed. Reason: " + reason);
                            }
                        });
                    }
                }
            });
        }
    }

    //This is required to support the operation of connecting to a device and receiving information about the connection
    WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            Thread handler = null;
            Log.d(TAG + "-CIL", "Connection Info received: " + wifiP2pInfo.toString());
            final InetAddress groupOwnerAddress = wifiP2pInfo.groupOwnerAddress;
            if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
                //This device is the service host
                isHost = true;
                isConnected = true;

                Log.d(TAG + "-CIL", "ServerClass= null: " + (serverClass == null));
                Log.d(TAG + "-CIL", "ServerClass is alive: " + (serverClass != null ? serverClass.isAlive() : "- isnull -"));
                if (firstTimeHost == true | (serverClass != null && serverClass.isAlive() == false)) {
                    serverClass = new ServerClass(activity);
                    serverClass.start();
                }
                serverClass.FriendlyServiceName = friendlyServiceName;
                firstTimeHost = false;
                Log.d(TAG + "-CIL", "Group formed & isGroupOwner. Address(this|GO): " + (thisDevice != null ? thisDevice.deviceAddress.toString() : "device is null") + "|" + groupOwnerAddress.toString());

            } else if (wifiP2pInfo.groupFormed) {
                //This device has a peer has an authorize peer connection to a service host
                isHost = false;
                isConnected = true;

                //This assumes that connection info is only called when connection change is detected.
                //BUT.. maybe also be called when just trying to update connection status
                Log.d(TAG + "-CIL", "Is a peer. Needs a messaging client. ClientClass is null:" + (clientClass == null ? "ClientClass is null" : "ClientClass not null"));
                if (clientClass != null) {
                    Log.d(TAG + "-CIL", "Legacy ClientClass being cleaned up.");
                    clientClass.closeDown();
                }
                clientClass = new ClientClass(activity, groupOwnerAddress, whoAmI);
                clientClass.start();

                Log.d(TAG + "-CIL", "Group formed & not owner. Address:" + (thisDevice != null ? thisDevice.deviceAddress.toString() : "device is null"));

            } else {
                Log.d(TAG + "-CIL", "No group formed.");
                //One of the scenarios this happens is when connection is first made but the Accept connection request is up on the host that needs to authorize connection
                return;
            }
            Log.d(TAG + "-CIL", "Is Connected: " + isConnected + " Is Host: " + isHost);
            if (isConnected) {
                //Update Connected Peers List in UI
                manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                    @Override
                    public void onGroupInfoAvailable(WifiP2pGroup wifiP2pGroup) {

                        if (wifiP2pGroup != null) {
                            Log.d(TAG + "-CIL", "Group Info received: \n" + wifiP2pGroup.toString());

                            if (wifiP2pGroup.isGroupOwner()) {
                                //Service Host

                            } else {
                                //Peer connected to Host
                                //Update Unity with the friendly name of the service it connected to

                                String ownerDeviceAddress = wifiP2pGroup.getOwner().deviceAddress;
                                for (Map.Entry<String, String> buddy : buddies.entrySet()) {
                                    Log.d(TAG + "-CIL", "buddy.getKey:" + (buddy.getKey()) + " vs Group Owner Address:" + (groupOwnerAddress));
                                    if (buddy.getKey().equals(ownerDeviceAddress)) {
                                        //Send friendly service name of connection
                                        //Unity Plugin API Callback:
                                        UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayConnectedServiceFriendlyName", buddy.getValue());
                                        Log.d(TAG + "-CIL", "Sending connected service friendly name to Unity: " + buddy.getValue());
                                        break;
                                    }
                                }
                            }

                            //Build list of connected peers (seems to only be visible from Host
                            connectedPeers.clear();
                            Collection<WifiP2pDevice> groupMembers = wifiP2pGroup.getClientList();
                            Log.d(TAG + "-CIL", "Group members: \n" + groupMembers.toString());

                            if (groupMembers.size() < 1) {
                                //if groupowner and the group has no clients then the host is not connected
                                if (wifiP2pGroup.isGroupOwner()) {
                                    isConnected = false;
                                }
                                Log.d(TAG + "-CIL", "Group Info received but Group size was: " + groupMembers.size());
                            }
                            deviceNameArray = new String[groupMembers.size()];
                            String devices = "";
                            int index = 0;
                            for (WifiP2pDevice member : groupMembers) {
                                connectedPeers.add(member);
                                deviceNameArray[index] = member.deviceName;

                                if (index < groupMembers.size() - 1) {
                                    devices += deviceNameArray[index] + ",";
                                } else {
                                    devices += deviceNameArray[index];
                                }

                                Log.i(TAG + "-CIL", "Peer Device status -" + (member != null ? member.status + " " + member.toString() + " ServiceDiscovery " + member.isServiceDiscoveryCapable() : "device is null"));
                                index++;
                            }
//                            ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_list_item_1, deviceNameArray);
                            //Unity Plugin API Callback:
                            UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayConnectedDevices", devices);

                        } else {
                            Log.d(TAG + "-CIL", "Group Info received but Group was null.");
                        }
                    }
                });
            }
        }
    };

    //region UnityPluginAPIs

    /**
     * Unity Plugin API: to initialize the WiFi Direct ServiceName that will be used for hosting a service, or filtered for in discovery
     *
     * @param newServiceName of the service to broadcast or to filter for
     */
    public void SetServiceName(String newServiceName) {
        Log.d(TAG + "-SSN", "Service Name was: " + serviceName + " Service Name changed to: " + newServiceName);
        serviceName = newServiceName;
    }

    /**
     * Unity Plugin API: to read the current WiFi Direct ServiceName that used for hosting a service, or filtered for in discovery
     */
    public String GetServiceName() {
        return serviceName;
    }

    /**
     * Unity Plugin API: to start broadcasting a WiFi Direct Service for others to discover
     */
    public void StartHosting() {
        startRegistration();
    }

    /**
     * Unity Plugin API: to stop broadcasting a WiFi Direct Service
     */
    public void StopHosting() {
        Disconnect();
        clearLocalServices();

        //Update Unity with cleared Host service friendly name
        //Unity Plugin API Callback:
        UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayHostServiceFriendlyName", "");
    }

    /**
     * Unity Plugin API: to get the Device Name
     *
     * @return the name of this device
     */
    public String GetDeviceName() {
        return whoAmI;
    }

    /**
     * Unity Plugin API: to start WiFi Direct Service Discovery
     */
    public void StartDiscovering() {
        Log.d(TAG + "-StartD", "Step 1");
        buddies.clear();
        Log.d(TAG + "-StartD", "Step 2");
        discoverService();
        Log.d(TAG + "-StartD", "Step 3");
    }

    /**
     * Unity Plugin API: to stop WiFi Direct Service Discovery
     */
    public void StopDiscovering() {
        clearDiscoveryRequest();
        //Unity Plugin API Callback:
        UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayAvailableServices", "");
    }

    /**
     * Unity Plugin API: to connect to WiFi Direct Service
     *
     * @param serviceName the service to connect to
     */
    public void ConnectToService(String serviceName) {

        Log.d(TAG + "-CTS", "Request to connect to service: " + serviceName + " current Vars:" + "\nisConnected " + isConnected + "\nisHost " + isHost + "\nisDoingDiscovery " + isDoingDiscovery + "\nisServiceRegistered " + isServiceRegistered + "\nwhoAmI " + whoAmI);

        // Check first to see if currently connected to a Group
        if (manager != null && channel != null) {
            Log.d(TAG + "-CTS-d", "Requesting Group Info in order to remove any existing Group connection.");

            manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                    Log.d(TAG + "-CTS-d", "Group Info came returned. Vars: " + "\ngroup " + group + "\nmanager " + manager + "\nchannel " + channel);

                    if (group != null && manager != null && channel != null) {

                        //was connected to a group so disconnect first
                        Log.d(TAG + "-CTS-d", "Removing Group:\n" + group);

                        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {

                            @Override
                            public void onSuccess() {
                                //Unity Plugin API Callback:
                                UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayConnectedDevices", "");
                                //Unity Plugin API Callback:
                                UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayConnectedServiceFriendlyName", "");
                                isConnected = false;
                                isHost = false;
                                Log.d(TAG + "-CTS-d", "Group removed successfully.");

                                // after disconnecting it is now okay to go ahead and connect
                                makeNewConnection(serviceName);
                            }

                            @Override
                            public void onFailure(int reason) {
                                Log.e(TAG + "-CTS-d", "Group removal failed. Reason:" + reason);
                            }
                        });
                    } else {
                        //was not previously connected to a group so go ahead and connect
                        makeNewConnection(serviceName);
                    }
                }
            });
        }
    }

    /**
     * Unity Plugin API: to send a command to connected peers
     *
     * @param command the command string to send to connected device
     */
    public void SendCommandToPeers(String command) {
        String commandStr = String.valueOf(MsgTypes.UICMD) + "," + command;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Log.d(TAG + "-SCTP", "Out going command being relayed: " + commandStr);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (isConnected) {
                    if (isHost) {
                        if (serverClass.broadcastMessage(commandStr, null)) {
                            Log.d(TAG + "-SCTP", "Message sent as host: " + commandStr);
                        } else {
                            Log.d(TAG + "-SCTP", "Host Message failed: " + commandStr);
                        }
                    } else {
                        if (clientClass.sendMessage(commandStr)) {
                            Log.d(TAG + "-SCTP", "Message sent as client: " + commandStr);
                        } else {
                            Log.d(TAG + "-SCTP", "Client Message failed: " + commandStr);
                        }
                    }
                }
            }
        });
    }

    /**
     * Unity Plugin API: to send an incremented hardcoded test message to connected peers
     */
    public void SendMsgToPeer() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        String sendMsg = String.valueOf(MsgTypes.CHATT) + "," + whoAmI + " says hi x " + msgCount;
        Log.d(TAG + "-SMTP", "Out going message being relayed: " + sendMsg);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (isConnected) {
                    String msg = "";
                    if (isHost) {
                        if (serverClass.broadcastMessage(sendMsg, null)) {
                            msg = "Message Sent";
                            Log.d(TAG + "-SMTP", "Message sent as host: " + sendMsg);
                        } else {
                            msg = "Message Failed";
                            Log.d(TAG + "-SMTP", "Host Message failed: " + sendMsg);
                        }
                    } else {
                        if (clientClass.sendMessage(sendMsg)) {
                            Log.d(TAG + "-SMTP", "Message sent as client: " + sendMsg);
                            msg = "Message Sent";
                        } else {
                            msg = "Message Failed";
                            Log.d(TAG + "-SMTP", "Client Message failed: " + sendMsg);
                        }
                    }
                    String finalMsg = msg;

                } else {
                    //  Not connected
                }
            }
        });
        msgCount++;
    }

    //endregion

    protected void startRegistration() {
        Log.d(TAG + "-startR", "Service registration started.");

        friendlyServiceName = "HOST:" + whoAmI;

        //  Create a string map containing information about your service.
        Map<String, String> record = new HashMap<String, String>();
        record.put("listenport", String.valueOf(SERVER_PORT));
        record.put("buddyname", friendlyServiceName);
        record.put("available", "visible");

        Log.d(TAG + "-startR", "record created: " + record.toString());

        // Service information.  Pass it an instance name, service type
        // _protocol._transportlayer , and the map containing
        // information other devices will want once they connect to this one.
        serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(serviceName, SERVICE_TYPE, record);
        Log.d(TAG + "-startR", "serviceInfo created: " + serviceInfo.toString());
        Log.d(TAG + "-startR", "manager obj check, is null: " + (manager == null));

        manager.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                // Add the local service, sending the service info, network channel,
                // and listener that will be used to indicate success or failure of
                // the request.
                Log.d(TAG + "-startR", "LocalServices cleared.");
                manager.addLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {

                        manager.createGroup(channel,
                                new WifiP2pManager.ActionListener() {
                                    @Override
                                    public void onSuccess() {
                                        Log.d(TAG + "-startR", "LocalService created group.");
                                        Log.d(TAG + "-startR", "Service: " + serviceName + " Started called: " + friendlyServiceName);

                                        //Update Unity with Host service friendly name
                                        //Unity Plugin API Callback:
                                        UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayHostServiceFriendlyName", friendlyServiceName);

                                        isServiceRegistered = true;
                                        Log.d(TAG + "-startR", "LocalService added. " + serviceInfo.toString());
                                        Log.d(TAG + "-startR", "Current Vars- isConnected: " + isConnected + " isHost: " + isHost + " isDoingDiscovery: " + isDoingDiscovery + " isServiceRegistered: " + isServiceRegistered + " whoAmI: " + whoAmI);
                                    }

                                    @Override
                                    public void onFailure(int i) {
                                        if (i == WifiP2pManager.BUSY) {
                                            ShowToUser("Device was busy, please try again.");
                                        } else if (i == WifiP2pManager.P2P_UNSUPPORTED) {
                                            ShowToUser("Sorry device doesn't support P2P, service creation failed.");
                                        } else {
                                            ShowToUser("Service creation failed, error unknown.");
                                        }

                                        //Update Unity with cleared Host service friendly name
                                        //Unity Plugin API Callback:
                                        UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayHostServiceFriendlyName", "");

                                        Log.e(TAG + "-startR", "LocalService failed to create group. Reason: " + i + " ");
                                    }
                                });
                    }

                    @Override
                    public void onFailure(int arg0) {
                        // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                        ShowToUser("Warning, Service: " + serviceName + " Failed to Start.");

                        //Update Unity with cleared Host service friendly name
                        //Unity Plugin API Callback:
                        UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayHostServiceFriendlyName", "");

                        isServiceRegistered = false;
                        Log.e(TAG + "-startR", "LocalService failed to add. Reason: " + arg0);
                    }
                });
            }

            @Override
            public void onFailure(int i) {
                ShowToUser("Warning, Services failed to clear on Start of : " + serviceName);

                isServiceRegistered = false;
                Log.e(TAG + "-startR", "LocalServices failed to clear. Reason: " + i);
            }
        });
    }

    private void discoverService() {

        Log.d(TAG + "-dS", "manager obj check, is null: " + (manager == null));

        manager.setDnsSdResponseListeners(channel,
                new WifiP2pManager.DnsSdServiceResponseListener() {

                    @Override
                    public void onDnsSdServiceAvailable(String instanceName, String registrationType, WifiP2pDevice resourceType) {
                        //Appears to happens after matching onDnsSdTxtRecordAvailable event

                        Log.d(TAG + "-dS-SA", "Instance Name: " + instanceName + " Service Name: " + serviceName);

                        //Filter for the service we are looking for
                        if (instanceName.equalsIgnoreCase(serviceName)) {
                            //this is the service we are looking for, skip otherwise
                            Log.d(TAG + "-dS-SA", "DNS SD Service Avaialable: " + instanceName + "\nRegType: " + registrationType + "\nDevice resource type: " + resourceType);
                            Log.i(TAG + "-dS-SA", "Disovery Service Avaialable: " + resourceType.isServiceDiscoveryCapable());

                            // Update the device name with the human-friendly version from
                            // the DnsTxtRecord, assuming one arrived.
                            Log.d(TAG + "-dS-SA", "buddies size " + buddies.size() + " in DnsSd call");
                            resourceType.deviceName = buddies
                                    .containsKey(resourceType.deviceAddress) ? buddies
                                    .get(resourceType.deviceAddress) : resourceType.deviceName;

                            Log.i(TAG + "-dS-SA", "resourceType.deviceName set to:" + (buddies.containsKey(resourceType.deviceAddress) ? buddies.get(resourceType.deviceAddress) : resourceType.deviceName));
                            Log.d(TAG + "-dS-SA", "onBonjourServiceAvailable " + instanceName);

                            //Test if this updates a previous connection indicator, or if it messes things up with too many calls
                            manager.requestConnectionInfo(channel, (WifiP2pManager.ConnectionInfoListener) activity.connectionInfoListener);
                        } else {
                            Log.d(TAG + "-dS-SA", "DNS SD Service Avaialable [SKIPPED]: " + instanceName + "\nRegType: " + registrationType + "\nDevice resource type: " + resourceType);
                        }
                    }
                }, new WifiP2pManager.DnsSdTxtRecordListener() {

                    /* Callback includes:
                     * fullDomain: full domain name: e.g "printer._ipp._tcp.local."
                     * record: TXT record dta as a map of key/value pairs.
                     * device: The device running the advertised service.
                     */
                    @Override
                    public void onDnsSdTxtRecordAvailable(String fullDomain, Map<String, String> record, WifiP2pDevice device) {
                        //Appears to happens first, before a matching onDnsSdServiceAvailable event
                        Log.d(TAG + "-dS-TRA", "DNS SD TxtRec Avaialable: " + fullDomain + "\nRecord: " + record + "\nDevice: " + device);
                        Log.i(TAG + "-dS-TRA", "Disovery Service Avaialable: " + device.isServiceDiscoveryCapable());
                        //NOTE: There do not appear to be any updates received when a discovered service stops or changes it record info, so services are persisting on the list after the service is stopped
                        Log.d(TAG + "-dS-TRA", "buddies size " + buddies.size() + " before.\nDevice addr: " + device.deviceAddress + "\nrecord buddy name: " + record.get("buddyname") + "\nprevious buddies list version: " + buddies.get(device.deviceAddress));
                        Log.d(TAG + "-dS-TRA", "Service Name: " + serviceName);

                        //Filter for the service we are looking for
                        if (fullDomain.toLowerCase().startsWith(serviceName.toLowerCase())) {

                            buddies.put(device.deviceAddress, (String) record.get("buddyname"));
                            Log.d(TAG + "-dS-TRA", "buddies size " + buddies.size() + " after");
                            serviceNameArray = new String[buddies.size()];
                            String services = "";
                            int index = 0;
                            for (Map.Entry<String, String> buddy : buddies.entrySet()) {
                                serviceNameArray[index] = buddy.getValue();
                                Log.i(TAG + "-dS-TRA", "serviceNameArray[" + index + "] set to:" + (serviceNameArray[index] != null ? serviceNameArray[index].toString() : " is null "));
                                if (index < buddies.size() - 1) {
                                    services += serviceNameArray[index] + ",";
                                } else {
                                    services += serviceNameArray[index];
                                }

                                index++;
                            }
                            Log.i(TAG + "-dS-TRA", "serviceNameArray: " + (serviceNameArray != null ? serviceNameArray.toString() : " is null "));
//                            ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_list_item_1, serviceNameArray);
//                            Log.i(TAG + "-dS-TRA", "ArrayAdapter: " + (adapter != null ? adapter.toString() : " is null "));

                            //Unity Plugin API Callback:
                            UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayAvailableServices", services);
                        }
                        else {
                            Log.d(TAG + "-dS-TRA", "DNS SD TxtRec Avaialable [SKIPPED]: " + fullDomain + "\nRecord: " + record + "\nDevice: " + device);
                        }
                    }
                }
        );

        manager.clearServiceRequests(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG + "-dS", "Cleared Service Requests.");

                serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();

                manager.addServiceRequest(channel, serviceRequest,
                        new WifiP2pManager.ActionListener() {

                            @Override
                            public void onSuccess() {
                                Log.d(TAG + "-dS", "Service Request added successfully.");

                                manager.discoverServices(channel,
                                        new WifiP2pManager.ActionListener() {

                                            @Override
                                            public void onSuccess() {
                                                UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayDiscoveryStatus", "Looking for Services");

                                                isDoingDiscovery = true;
                                                Log.d(TAG + "-dS", "Service Discovery initiated successfully.");
                                                Log.d(TAG + "-dS", "Current Vars- isConnected: " + isConnected + " isHost: " + isHost + " isDoingDiscovery: " + isDoingDiscovery + " isServiceRegistered: " + isServiceRegistered + " whoAmI: " + whoAmI);
                                            }

                                            @Override
                                            public void onFailure(int code) {
                                                Log.e(TAG + "-dS", "Service Discovery failed to initiate. Reason: " + code);
                                                // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                                                if (code == WifiP2pManager.P2P_UNSUPPORTED) {
                                                    Log.d(TAG + "-dS", "Wi-Fi Direct isn't supported on this device.");

                                                }
                                                UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayDiscoveryStatus", "Initialize error");

                                                isDoingDiscovery = false;
                                            }
                                        });
                            }

                            @Override
                            public void onFailure(int code) {
                                // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY

                                UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayDiscoveryStatus", "Service add error");
                                Log.e(TAG + "-dS", "Service Request add attempt failed. Reason: " + code);
                            }
                        });
            }

            @Override
            public void onFailure(int i) {
                UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayDiscoveryStatus", "Initialize prep error");
                Log.e(TAG + "-dS", "DiscoveryServices failed to stop. Reason: " + i);
            }
        });
    }

    private void makeNewConnection(String serviceName) {
        //Find service device address to connect to and create WifiP2p connection configuration
        WifiP2pConfig config = new WifiP2pConfig();
        for (Map.Entry<String, String> buddy : buddies.entrySet()) {
            if (buddy.getValue().equalsIgnoreCase(serviceName)) {
                config.deviceAddress = buddy.getKey();
            }
        }

        config.groupOwnerIntent = 0; //0 Let service host be group owner

        Log.d(TAG + "-CTS-mNC", "Connectioning to service: " + serviceName);
        Log.d(TAG + "-CTS-mNC", "channel: " + channel);
        Log.d(TAG + "-CTS-mNC", "ConfigInfo: " + config);

        // Connect to service
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG + "-CTS-mNC", "Connection succeeded.\nDevice: " + config.toString());
                isConnected = true;
                manager.requestConnectionInfo(channel, connectionInfoListener);
            }

            @Override
            public void onFailure(int i) {
                //Unity Plugin API Callback:
                UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayConnectedServiceFriendlyName", "");

                if (i == WifiP2pManager.BUSY) {
                    ShowToUser("Device was busy, please try again.");
                } else if (i == WifiP2pManager.P2P_UNSUPPORTED) {
                    ShowToUser("Sorry device doesn't support P2P, connection failed.");
                } else {
                    ShowToUser("Connection failed, error unknown.");
                }

                Log.e(TAG + "-CTS-mNC", "Connection failed. Reason: " + i);
                isConnected = false;
            }
        });
    }

    protected void OnIncomingMsg(String msg) {
        Log.d(TAG + "-OIM", "Incomming message being relayed to Unity: " + msg);
        //Unity Plugin API Callback:
        UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplayIncomingMsg", msg);
    }

    protected void OnIncomingCmd(String cmd) {
        Log.d(TAG + "-OIC", "Incomming command being relayed to Unity: " + cmd);
        //Unity Plugin API Callback:
        UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "HandleIncommingCmd", cmd);
    }

    protected void ShowToUser(String msg) {
        //Unity Plugin API Callback:
        UnityPlayer.UnitySendMessage("WifiDirectPluginManager", "DisplaySystemMsg", msg);

        //log user message
        Log.d(TAG + "-sTU", msg);
    }
}

