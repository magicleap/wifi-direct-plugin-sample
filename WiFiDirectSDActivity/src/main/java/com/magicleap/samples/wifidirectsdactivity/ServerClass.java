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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Socket Server used by Host device in P2P connections
 * <p>
 * Contains an array of ClientHandlers to manage socket communication with each connected client
 * The size of the fixed thread {@link #pool} determines the maximum number of clients
 */
public class ServerClass extends Thread {
    private static final String TAG = "WDPluginActivity-SC";

    private WDPluginActivity activity;
    private Socket socket;
    private ServerSocket serverSocket;

    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String classId;

    protected String FriendlyServiceName = "";
    private static ArrayList<ClientHandler> clientHandlers = new ArrayList<>();
    private static ExecutorService pool = Executors.newFixedThreadPool(10);

    protected ServerClass(WDPluginActivity activity) {
        try {
            this.activity = activity;
            this.serverSocket = new ServerSocket(8888);
            classId = "ID:" + (int) (Math.random() * 1000);

            Log.d(TAG + "-init", "Server Socket class initialized. Class:" + classId);
        } catch (IOException e) {
            Log.e(TAG + "-init", "Server Socket class initialization error: " + e.toString());
        }
    }

    @Override
    public void run() {
        try {
            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                Log.w(TAG + "-r", "A new socket client has connected to the socket server. Class:" + classId);

                ClientHandler clientHandler = new ClientHandler(activity, socket, this);
                clientHandlers.add(clientHandler);

                pool.execute(clientHandler);
            }
        } catch (IOException e) {
            Log.e(TAG + "-r", "Server start error: " + e.toString());
        }
    }

    protected boolean sendMessageToThisClient(String message, ClientHandler tartgetClientHandler) {
        boolean ret = false;
        Log.d(TAG + "-sMTTC", "Client msg: " + message + "  sent to client: " + tartgetClientHandler.deviceName.toString());
        Iterator<ClientHandler> it = clientHandlers.iterator();
        while (it.hasNext()) {
            ClientHandler clientHandler = it.next();
            if (clientHandler.socket.isClosed()) {
                Log.d(TAG + "-sMTTC", "Removing clientHandler: " + clientHandler.deviceName);
                it.remove();
                continue;
            }

            if (clientHandler == tartgetClientHandler) {
                ret = clientHandler.sendMessage(message);
                if (ret) {
                    Log.d(TAG + "-sMTTC", "Direct message went to: " + clientHandler.deviceName + " sent message: " + message);
                    return true;
                } else {
                    Log.d(TAG + "-sMTTC", "Message send failure: " + clientHandler.deviceName + " sent message: " + message);
                    return false;
                }
            }
        }

        //must not have found this client
        Log.d(TAG + "-sMTTC", "Client for " + tartgetClientHandler.deviceName + " was not found. Direct message sent: " + message + " failed.");
        return false;
    }

    protected boolean broadcastMessage(String message, ClientHandler receivedFromClientHandler) {
        boolean anyFailures = false;
        boolean ret = false;
        Log.d(TAG + "-bM", "Broadcasting msg: " + message + " to client count: " + clientHandlers.size());

        Iterator<ClientHandler> it = clientHandlers.iterator();
        while (it.hasNext()) {
            ClientHandler clientHandler = it.next();
            if (clientHandler.socket.isClosed()) {
                Log.d(TAG + "-bM", "Removing clientHandler: " + clientHandler.deviceName);
                it.remove();
                continue;
            }
            if (clientHandler != receivedFromClientHandler) {
                ret = clientHandler.sendMessage(message);
                if (ret) {
                    Log.d(TAG + "-bM", "Broadcast went to: " + clientHandler.deviceName + " sent message: " + message);
                } else {
                    anyFailures = true;
                    Log.d(TAG + "-bM", "Message send failure: " + clientHandler.deviceName + " sent message: " + message);
                }
            } else {
                Log.d(TAG + "-bM", "Broadcast skipped and not returned to original sender: " + clientHandler.deviceName);
            }

        }
        if (anyFailures) {
            return false;
        } else {
            return true;
        }
    }

    protected void removeClientHandler(ClientHandler clientHandler) {
        clientHandlers.remove(clientHandler);
    }

    protected void closeServerSocket() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
                Log.d(TAG + "-cSS", "ServerSocket closed.");
            }
        } catch (IOException e) {
            Log.e(TAG + "-cSS", "Error closing ServerSocket " + e.toString());
        }
    }
}