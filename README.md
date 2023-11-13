# WiFi Direct Service Discovery Sample Unity Plug-in

<table cellspacing="0" cellpadding="0" border="0">
<tr >
<td valign=top width=25%>
<img src="Readme-Imgs/WiFiDirectSampleUnityPluginSplash.png"> 
</td>
<td valign=top>	
Sample Android Plug-in for Unity to use WiFi Direct Service Discovery.
<br><br>
This project is an Android app harness and the plug-in code written in Java is an Android Activity contained in a Java Module.

<H3>Plug-in provides functionality to:</H3>
<ul>
<li>Connect Android devices using WiFi Direct P2P services
<li>Communicate between connected devices over sockets with string-based messaging
</ul>
</td>
</tr>
</table>

## Getting started

This Android Unity plug-in includes all of the Android WiFi Direct sample Java code used to create the plug-in that you are welcome to use as a starting point to create even more robust WiFi Direct plug-ins or projects of your own.

However, it is not necessary to modify or even build this code if your only interest is to use this sample plug-in as is in your own unity project. This repository contains a build of the [WiFiDirectSDActivity-debug.AAR](/WiFiDirectSDActivity/build/outputs/aar/WiFiDirectSDActivity-debug.aar) file located in the project folder

```
\WiFiDirectSDActivity\build\outputs\aar
```

that you can include in your Unity project and begin using right away.<br>
(See below for details on how to [include in your Unity project](#Using-the-Plug-in-in-a-Unity-project), and for this Unity plug-in's [APIs](#Plug-in-APIs))

For those that are interested in working with the plug-in code, after cloning this respository to your local machine, open the project folder in Android Studio or the Android code editor of your choosing.

The `WiFiDirectSDActivity` module contains the source code for a basic Unity Plug-in with peer-to-peer support leveraging Android's flavor of WiFi Direct Service Discovery and a simple implementation of Java Sockets. 

Within Android Studio, build the ARR file by first selecting the module in the `Project` pane, and then selecting the `Make Module 'WiFiDirectPluginHarness.WiFiDirectSDActivity'` from the `Build` menu. The ARR file will get generated in the output folder mentioned above.

>📝NOTE: Once the ARR file has been added into a Unity project, it is advised to close the Unity project, copy over any new build of the ARR file after each modify/rebuild of the plug-in in Android Studio, and then reopen the Unity project in order to see and test any changes. 

## The code modules in this plug-in

The code in this plug-in has been organized into five code modules:
* WDPluginActivity.java

    * Contains the majority of the plug-in logic and overrides the needed `Activity` events

* WiFiDirectBroadcastReceiver.java
    
    * Overrides the needed `BroadcastReceiver` events to handle the relevant WiFi P2P Intents

* ServerClass.java

    * Socket Server class that also manages a multi-threaded array of `ClientHandler` objects when the current device is a WiFi Direct Host managing one or more connected WiFi Direct peer devices

* ClientHandler.java

    * `ClientHandler` that handles socket communication to a peer from the socket server 

* ClientClass.java

    * Client class that handles socket communication to the socket server when the current device is a WiFi Direct peer connected to a WiFi Direct Host


## Android's WiFi Direct Service Discovery Overview


 >Unfortunately, clear documentation on how the Android WiFi Direct Service Discovery APIs work is hard to find.
>
>The following interpretation is based on the research and experimention conducted while creating this sample, however, it should not be considered an authoritative source for how WiFi Direct Service Discovery is implemented in the Android or AOSP operating system.

---
### Basics Activity flow
WiFi Direct Service Discovery involves one device registering and then broadcasting a service that other devices within range can then discover and connect to.

### The plug-in is basically an Android `Activity` that uses the following activitiy lifecycle events in the following way:

#### `onCreate`
* add WiFi Direct P2P `intent-filters` to listen for WiFi Direct related broadcasts
* initialize P2P services
  * check device & hardware capabilities
  * create P2P manager and primary objects
  * warm up services
  * standardize name of device
* request needed permissions 

#### `onResume`
* register broadcast receiver

#### `onPause`
* unregister broadcast receiver

#### `onDestroy`
* close any open connections with other devices
  * update connection status

### The Unity application consuming the plug-in can also initiate the following WiFi Direct actions:

#### `StartHosting`
* instantiate and start service

#### `StartDiscovering`
* initiate service discovery
* listen for service broadcasts

#### `ConnectToService`
* device doing the discovery can initiate a connection to a discovered service host

#### `StopHosting`
* clear local services

#### `StopDiscovering`
* clear service discovery requests

### Other code logic that plays an important role

#### `BroadcastReceiver`
* handles incomming notifications of
  * device changed
  * connection changed
  * peers changed
  * state changed

#### `ConnectionInfoLister`
* listens for connection update details


## Sockets based messages

This plug-in includes a simple version of string-based messaging between peers using Java Sockets.

The WiFi Direct Service Discovery host device in the shared experience runs a socket server and manages a multi-threaded array of client handlers to deal with each peer. 

Peers, on the other hand, act as socket clients that connect and communicate to the host's socket server.

>📝NOTE: By default, the thread pool size has been set to handle 10 simultaneous client handlers which, as is, should accomodate up to 11 devices in a shared experience. However, WiFi Direct Service Discovery is capable of connecting many more P2P devices in a session if you are interested in creating a more robust threading solution to manage socket client handlers.










***

## Using the Plug-in in a Unity project
To use this plug-in:
* Copy the .ARR file into your Unity project under
    * Assets|Plugins|Android
* Modify the package and activity sections of the Android `Manifest` file located in the same folder to include:

```diff

<manifest xmlns:android="http://schemas.android.com/apk/res/android" package= 
+"com.magicleap.samples.wifidirectsdactivity"
 mlns:tools="http://schemas.android.com/tools"&gt
  <application>

	<activity android:name=
+"com.magicleap.samples.wifidirectsdactivity.WDPluginActivity"
	 android:theme="@style/UnityThemeSelector">
	      <intent-filter>
	        <action android:name="android.intent.action.MAIN" />
	        <category android:name="android.intent.category.LAUNCHER" />
	      </intent-filter>
	      <meta-data android:name="unityplayer.UnityActivity" android:value="true" />
	    </activity>
…
```

 * Add a `gamecomponent` to your scene called "`WifiDirectPluginManager`" and attach a script that initializes the plug-in and then sets the Wi-Fi Direct Service Name for your application.

```
	    AndroidJavaClass unityClass;    
	    AndroidJavaObject unityActivity;
		 void Start()
		    {
		
		        unityClass = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
		        unityActivity = unityClass.GetStatic<AndroidJavaObject>("currentActivity");
                unityActivity.Call("SetServiceName", Application.productName);
		
		    }
```
* Use the "`unityActivity`" instance to call plug-in functions

for example:
```
public void StartHosting()
    {
        if (unityActivity != null)
        {
            unityActivity.Call("StartHosting");
        }
    }
```
* Create public methods to handle plug-in callback

for example:
```
public void DisplayHostServiceFriendlyName(string serviceFriendlyName)
    {
     // code to respond to plug-in call to display host service friendly name   
    }
```
For a full working sample of a Unity project that implements this plug-in, please refer to our WiFi-Direct-Shared-Experience-Sample code sample project for Unity.

# Plug-in APIs
---
## Exposed by plug-in



### Set Service Name
```c#
	unityActivity.Call("SetServiceName", newServiceName);
```
#### Passes: string containing the name of the Wi-Fi Direct Service to broadcast or to filter discovery for 
>📝NOTE: By setting the service name to the application name (or any other valid unique name) your application will then have its own service to broadcast, discover, and connect to between peer devices. This simplifies the connection options for the user and makes it much more likely to connect to a compatible connection. This service name should be set at plug-in initialization and typically not changed.
---

### Get Service Name
```c#
	string serviceName = unityActivity.Call<string>("GetServiceName");
```
#### Returns: string containing the service name being used to broadcast, or to filter discovery of Wi-Fi Direct services
---

### Get Device Name
```c#
	string deviceName = unityActivity.Call<string>("GetDeviceName");
```
#### Returns: string containing the name of the current device
---
		
### Start Hosting Service
```c#
	unityActivity.Call("StartHosting");
```	
---

### Stop Hosting Service
```c#
	unityActivity.Call("StopHosting");
```	
---	
### Start Discovery available services
```c#
	unityActivity.Call("StartDiscovering");
```
---
### Stop Discovery available services
```c#
	unityActivity.Call("StopDiscovering");
```
---
### Connect to service
```c#
	unityActivity.Call("ConnectToService", serviceList[index]);
```	
#### Passes: string containing the name of host device broadcasting discovered service
---	
### Send command to Peers
```c#
	unityActivity.Call("SendCommandToPeers", command);	
```
#### Passes: string containing the user interaction command to share with connected peer devices
---	
### Send test message to peers
```c#
	unityActivity.Call("SendMsgToPeer");
```	
---

## Plug-in callbacks to Unity - expects method implementations to handle:

### Related to hosting service:

### Display of the Host Service name/status that you are currently hosting:
```java
	 UnityPlayer.UnitySendMessage("WifiDirectPluginManager","DisplayHostServiceFriendlyName", friendlyServiceName);
```
#### Passes: string containing the friendly name of the hosted WiFi Direct Service

Updated as a result of: 
* StartHosting
* StopHosting 
* Loss of connection
#### Value of empty string (`""`) indicates lost or no connection
--- 


### Displaying a list of peers connected to the active hosted service
```java
	UnityPlayer.UnitySendMessage("WifiDirectPluginManager","DisplayConnectedDevices", devices);
```
#### Passes: comma-delimited string list of device names

Updated from the following plug-in routines:

* ConnectToService
* Peer established connection
* Loss of connection
--- 

### Related to discovery:

#### Displaying a list of available services that have been discovered
```java	
    UnityPlayer.UnitySendMessage("WifiDirectPluginManager","DisplayAvailableServices", services);
```
#### Passes: comma-delimited string list of recently discovered services

Updated as a result of:

* StopDiscovering
* A service is discovered.

--- 

#### Display of the Discovery status
```java	
    UnityPlayer.UnitySendMessage("WifiDirectPluginManager","DisplayDiscoveryStatus",status);
```
#### Passes: string containing the service discovery status

Updated as a result of:
* StartDiscoverying
* StopDiscoverying
* ConnectToService
#### Value of empty string (`""`) indicates Discovery off
--- 

#### Display of the currently connected Discovered service
```java	
    UnityPlayer.UnitySendMessage("WifiDirectPluginManager","DisplayConnectedServiceFriendlyName",friendlyServiceName);
```
#### Passes: string containing the friendly name of the WiFi Direct Service this device is connected to

Updated as a result of:

* ConnectToService
* Loss of connection
#### Value of empty string (`""`) indicates lost or no connection
--- 

### Related to messaging:

#### Display of incoming user or test messages to the user
```java	
    UnityPlayer.UnitySendMessage("WifiDirectPluginManager","DisplayIncomingMsg",msg);
``` 
#### Passes: string containing the user message from other peer device

Updated as a result of:

* Incomming message received
--- 
#### Display of incoming system information messages to the user
```java	
    UnityPlayer.UnitySendMessage("WifiDirectPluginManager","DisplaySystemMsg",msg);
```
#### Passes: string containing the system message

Updated as a result of:

* System message received
--- 
#### Handle the processing of incoming shared user interaction commands
```java	
    UnityPlayer.UnitySendMessage("WifiDirectPluginManager","HandleIncommingCmd",cmd);
```
#### Passes: string containing command and its arguments

Updated as a result of:

*	Incoming shared user interaction command received
>📝NOTE: specific shared user interaction command names, arguements, and formating to be defined by the Unity application that consumes the plug-in.
>
>Refer to the `Magic Leap Dev Sample`: WiFi-Direct-Share-Experience-Sample App for an example of one comma-delieted approach to sharing a number of common user interactions between devices.
--- 




## Forums
If you have questions that are not covered here, please check the Magic Leap  2 Developer Forum: https://forum.magicleap.cloud/


# Copyright
Copyright (c) (2023) Magic Leap, Inc. All Rights Reserved. Use of this file is governed by the Developer Agreement, located here: https://www.magicleap.com/software-license-agreement-ml2 

