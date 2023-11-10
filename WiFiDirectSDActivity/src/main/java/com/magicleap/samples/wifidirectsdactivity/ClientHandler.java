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

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private static final String TAG = "WDPluginActivity-CH";

    private static WDPluginActivity activity;
    private ServerClass mServer;
    protected Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;

    protected String deviceName;

    private String getDeviceName() {
        return deviceName;
    }

    private void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    protected ClientHandler(WDPluginActivity mainActivity, Socket socket, ServerClass mainServer) {
        activity = mainActivity;
        mServer = mainServer;
        try {
            this.socket = socket;
            this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Log.d(TAG + "-init", "ClientHandler inputstream created for: " + deviceName);

            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            Log.d(TAG + "-init", "ClientHandler outputstream created for: " + deviceName);

        } catch (IOException e) {
            closeEverything(socket, bufferedReader, bufferedWriter);
            Log.e(TAG + "-init", "Init error " + e.toString());
        }
    }

    @Override
    public void run() {
        String messageFromClient;

        while (socket.isConnected()) {
            try {
                messageFromClient = bufferedReader.readLine();

                Log.d(TAG + "-r-msgReceived", "Received command string: " + messageFromClient);

                if (messageFromClient == null) {
                    //End of stream, connection likely terminated
                    closeEverything(socket, bufferedReader, bufferedWriter);
                    Log.e(TAG + "-r-msgReceived", "End of stream, incomming msg was null.");

                } else {

                    if (messageFromClient.startsWith(String.valueOf(WDPluginActivity.MsgTypes.DNAME))) {
                        this.deviceName = messageFromClient.replace(String.valueOf(WDPluginActivity.MsgTypes.DNAME), "");
                        Log.d(TAG + "-r-msgReceived", "Client sent their name: " + deviceName + " received: " + messageFromClient);
                        //show the Name received to the Host
                        final String finalConfirmationMessage = "Connection confirmation message from " + deviceName + " received.";
                        Log.d(TAG + "-r-msgReceived", "Confirmation relayed to Server: " + finalConfirmationMessage);
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                activity.OnIncomingMsg(finalConfirmationMessage);
                            }
                        });
                        //send welcome confirmation connection message to this client
                        final String welcomeMessage = "Connection with " + mServer.FriendlyServiceName + " confirmed. Welcome " + deviceName + "!";
                        mServer.sendMessageToThisClient(welcomeMessage, this);

                    } else if (messageFromClient.startsWith(String.valueOf(WDPluginActivity.MsgTypes.UICMD))) {
                        //show the command to the Host
                        final String finalMessage = messageFromClient.substring(6);
                        Log.d(TAG + "-r-msgReceived", "Client sent command: " + finalMessage);
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                activity.OnIncomingCmd(finalMessage);
                            }
                        });
                        //broadcast message
                        mServer.broadcastMessage(finalMessage, this);

                    } else if (messageFromClient.startsWith(String.valueOf(WDPluginActivity.MsgTypes.CHATT))) {
                        //show the command to the Host
                        final String finalMessage = messageFromClient.substring(6);
                        Log.d(TAG + "-r-msgReceived", "Client sent message: " + finalMessage);
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                activity.OnIncomingMsg(finalMessage);
                            }
                        });
                        //broadcast message
                        mServer.broadcastMessage(finalMessage, this);

                    } else {
                        //show the message to the Host
                        final String finalMessage = messageFromClient;
                        if (finalMessage.trim().isEmpty()) {
                            Log.d(TAG + "-r-msgReceived", "unidentified empty message ignored: " + messageFromClient);
                        } else {
                            Log.d(TAG + "-r-msgReceived", "Client sent unidentified type of message: " + finalMessage);
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    activity.OnIncomingMsg(finalMessage);
                                }
                            });
                            //broadcast message
                            mServer.broadcastMessage(finalMessage, this);
                        }
                    }

                    Log.d(TAG + "-r-msgReceived", "ClientHandler for: " + deviceName + " processed message: " + messageFromClient);
                }
            } catch (IOException e) {
                closeEverything(socket, bufferedReader, bufferedWriter);
                Log.e(TAG + "-r-msgReceived", "Message received error " + e.toString());
                break;
            }
        }
    }

    protected boolean sendMessage(String message) {
        try {
            bufferedWriter.write(message + "\n");
            bufferedWriter.newLine();
            bufferedWriter.flush();
            Log.d(TAG + "-sM", "ClientHandler for: " + deviceName + " sent message: " + message);
        } catch (IOException e) {
            closeEverything(socket, bufferedReader, bufferedWriter);
            Log.e(TAG + "-sM", "Send error " + e.toString());
            return false;
        }
        return true;
    }

    private void closeEverything(Socket socket, BufferedReader bufferedReader, BufferedWriter bufferedWriter) {
        try {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
            if (bufferedWriter != null) {
                bufferedWriter.close();
            }
            if (socket != null) {
                socket.close();
            }
            Log.d(TAG + "-cE", "ClientHandler closed");
        } catch (IOException e) {
            Log.e(TAG + "-cE", "ClientHandler close error " + e.toString());
        }
    }
}