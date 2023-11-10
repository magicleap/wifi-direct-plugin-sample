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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientClass extends Thread {
    private static final String TAG = "WDPluginActivity-CC";
    private WDPluginActivity activity;
    private String hostAdd;

    private int hostPort = 8888;
    private int connectTimeout = 1500;

    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String deviceName;

    public ClientClass(WDPluginActivity activity, InetAddress hostAddress, String dName) {
        Log.d(TAG + "-init", "Clientclass instantiated for: " + deviceName + " hostAddress: " + hostAddress.toString() + " " + hostAddress.getHostAddress().toString());
        this.activity = activity;
        socket = new Socket();
        hostAdd = hostAddress.getHostAddress();
        deviceName = dName;
        Log.d(TAG + "-init", "ClientClass initialized. hostAddress: " + hostAddress.toString());
    }

    private void redoSocket() {
        //try to redo the connection
        Log.d(TAG + "-rS", "Attempt to reconnect socekt for: " + deviceName);
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(hostAdd, hostPort), connectTimeout);

            bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Log.d(TAG + "-rS", "Client BufferReader and inputstream created for: " + deviceName);

            bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            Log.d(TAG + "-rS", "Client outputstream created for: " + deviceName);

            //Send server this clients device name
            sendMessage(String.valueOf(WDPluginActivity.MsgTypes.DNAME) + deviceName);

            //restart listener
            this.run();
        } catch (IOException e) {
            closeEverything(socket, bufferedReader, bufferedWriter);
            Log.e(TAG + "-rS", "Initialize ClientClass error " + e.toString());
            activity.ShowToUser("Socket error: " + e);
        }
    }

    protected boolean sendMessage(String message) {
        Log.d(TAG + "-sM", "ClientClass send message. Socket: \n" + socket.toString());
        try {

            if (socket.isClosed()) {
                redoSocket();
            }

            bufferedWriter.write(message + "\n");
            bufferedWriter.newLine();
            bufferedWriter.flush();
            Log.d(TAG + "-sM", "ClientClass sent: " + message + "\n" + socket.getInetAddress().toString() + "\n" + socket.getLocalAddress().toString() + "\n" + socket.getLocalPort() + "\n" + socket.getLocalSocketAddress().toString());
            return true;

        } catch (IOException e) {
            Log.e(TAG + "-sM", "Send message error " + e.toString());
            activity.ShowToUser("SendMessage failed: " + e);
            closeEverything(socket, bufferedReader, bufferedWriter);
            return false;
        }
    }

    @Override
    public void run() {

        Log.i(TAG + "-r", "ClientClass began main run.");

        if (!socket.isConnected()) {

            try {
                socket.connect(new InetSocketAddress(hostAdd, hostPort), connectTimeout);
                bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                Log.d(TAG + "-r", "Client inputstream created for: " + deviceName);

                bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                Log.d(TAG + "-r", "Client outputstream created for: " + deviceName);

                //Send server this clients device name
                sendMessage(String.valueOf(WDPluginActivity.MsgTypes.DNAME) + deviceName);

            } catch (IOException e) {
                closeEverything(socket, bufferedReader, bufferedWriter);
                if (e instanceof java.net.ConnectException) {

                    Log.e(TAG + "-r", "Initialize ClientClass ConnectionException (Class): " + e.getClass() + "\nFull exception: " + e.toString());
                    Log.e(TAG + "-r", "Initialize ClientClass Connection may have just been dropped.");
                } else {
                    Log.e(TAG + "-r", "Initialize ClientClass error: " + e.getClass().getSimpleName() + "\nFull error: " + e.toString());
                }
            }
        }
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG + "-r", "ClientClass began runnable run.");

                String incomingMessage;

                while (socket.isConnected()) {  //&& !socket.isClosed()

                    if (socket.isClosed()) {
                        //try to reinit the connection
                        try {
                            socket = new Socket();
                            socket.connect(new InetSocketAddress(hostAdd, hostPort), connectTimeout);
                            bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            Log.d(TAG + "-r", "Client BufferReader and inputstream created for: " + deviceName);

                            bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                            Log.d(TAG + "-r", "Client outputstream created for: " + deviceName);

                            //Send server this clients device name
                            sendMessage(String.valueOf(WDPluginActivity.MsgTypes.DNAME) + deviceName);

                        } catch (IOException e) {
                            closeEverything(socket, bufferedReader, bufferedWriter);
                            Log.e(TAG + "-r", "Initialize ClientClass error " + e.toString());
                            //break;
                            continue;
                        }
                    }

                    try {
                        incomingMessage = bufferedReader.readLine();
                        Log.d(TAG + "-r-listen", "Received command string: " + incomingMessage);
                        if (incomingMessage == null) {
                            //End of stream, connection likely terminated
                            closeEverything(socket, bufferedReader, bufferedWriter);
                            Log.e(TAG + "-r-listen", "End of stream, incomming msg was null.");

                        } else if (incomingMessage.startsWith(String.valueOf(WDPluginActivity.MsgTypes.UICMD))) {

                            final String finalMessage = incomingMessage.substring(6);
                            Log.d(TAG + "-r-listen", "Received command: " + finalMessage);
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    activity.OnIncomingCmd(finalMessage);
                                }
                            });
                        } else if (incomingMessage.startsWith(String.valueOf(WDPluginActivity.MsgTypes.CHATT))) {

                            final String finalMessage = incomingMessage.substring(6);
                            Log.d(TAG + "-r-listen", "Received message: " + finalMessage);
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    activity.OnIncomingMsg(finalMessage);
                                }
                            });

                        } else {

                            final String finalMessage = incomingMessage;
                            if (finalMessage.trim().isEmpty()) {
                                Log.d(TAG + "-r-listen", "unidentified empty message ignored: " + incomingMessage);
                            } else {
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        activity.OnIncomingMsg(finalMessage);
                                    }
                                });

                                Log.d(TAG + "-r-listen", "unidentified type of message received: " + incomingMessage);
                            }
                        }
                    } catch (IOException e) {
                        closeEverything(socket, bufferedReader, bufferedWriter);
                        Log.e(TAG + "-r-listen", "Incoming message error " + e.toString());
                    }
                }
                Log.i(TAG + "-r", "ClientClass reached end of runnable run.");
            }
        });

        Log.i(TAG + "-r", "ClientClass reached end of run.");
    }

    protected boolean closeDown() {
        //good chance that thread is blocking in a bufferedReader.read state, closing socket just in case
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG + "-cD", "Close socket error " + e.toString());
            }
        }
        closeEverything(socket, bufferedReader, bufferedWriter);
        return true;
    }

    private void closeEverything(Socket socket, BufferedReader bufferedReader, BufferedWriter bufferedWriter) {
        try {
            if (bufferedReader != null) {
                bufferedReader.close();
                Log.d(TAG + "-cE", "Client BufferReader closed for: " + deviceName);
            }
            if (bufferedWriter != null) {
                bufferedWriter.close();
                Log.d(TAG + "-cE", "Client BufferWriter closed for: " + deviceName);
            }
            if (socket != null) {
                socket.close();
            }
            Log.d(TAG + "-cE", "Client socket closed for: " + deviceName);
        } catch (IOException e) {
            Log.e(TAG + "-cE", "Close error " + e.toString());
        }
    }
}
