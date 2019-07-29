# OCast-JVM

[![License](https://img.shields.io/badge/licence-APACHE--2-lightgrey.svg)](https://github.com/Orange-OpenSource/OCast-iOS/blob/master/LICENSE)

[![Build Status](https://travis-ci.org/Orange-OpenSource/OCast-JVM.svg?branch=master)](https://travis-ci.org/Orange-OpenSource/OCast-JVM)

Download the OCast SDK

[ ![Download](https://api.bintray.com/packages/orange-opensource/maven/ocast-jvm-sdk/images/download.svg) ](https://bintray.com/orange-opensource/maven/ocast-jvm-sdk/_latestVersion)

Download the MediaRoute module for Android

[ ![Download](https://api.bintray.com/packages/orange-opensource/maven/ocast-jvm-mediaroute/images/download.svg) ](https://bintray.com/orange-opensource/maven/ocast-jvm-mediaroute/_latestVersion)

<br>
The Orange OCast SDK provides all required API methods to implement cast applications to interact with an OCast device.

The sample project aims to demonstrate the basic instruction set of the Orange OCast SDK to help you get started.

## Installation

OCast is available through [JFrog Bintray](https://bintray.com/orange-opensource/maven). To install it, simply add the following lines to your Gradle file:

```groovy
repositories {
    maven {
        url  "https://dl.bintray.com/orange-opensource/maven" 
    }
}
dependencies {
    implementation "org.ocast:sdk:2.0.0"
    implementation "org.ocast:mediaroute:2.0.0"
}
```

You can also retrieve the source code to build the project by cloning the repository:

```
git clone https://github.com/Orange-OpenSource/OCast-JVM.git
```

## Usage

### 1. Register your device type

You have to register your device type into the `DeviceCenter`.

```kotlin
val deviceCenter = DeviceCenter()
deviceCenter.registerDevice(ReferenceDevice::class.java)
```

### 2. Discovering devices

You need to call the `resumeDiscovery()` method to start the device discovery. Then you can be informed by the `DeviceCenter` by adding a `DeviceListener`.

If devices are found on your network, the `onDeviceAdded(devices: List<Device>)` method is called.

If devices are lost (network problem or device is turned-off), the `onDeviceRemoved(devices: List<Device>)` method is called.

```kotlin
deviceCenter.addDeviceListener(this)
deviceCenter.resumeDiscovery()
// DeviceListener methods
override fun onDevicesAdded(devices: List<Device>) {}
override fun onDevicesRemoved(devices: List<Device>) {}
override fun onDiscoveryStopped(error: Throwable?) {}
```

You can stop the device discovery by calling the `stopDiscovery()` method. This will call the `onDiscoveryStopped(error: Throwable?)` method. The list of discovered devices will be cleaned, so if you want to keep them you should call `pauseDiscovery()` instead. This is useful to manage application background state.

If a network error occurs, the `onDiscoveryStopped(error: Throwable?)` method is called but the error parameter is filled with the issue reason.

By default, the list of devices is refreshed every 30 seconds. You can decrease this interval by setting the `discoveryInterval` property. You should do this when the list of devices is displayed and restore the default value later.

### 3. Connect to the device

To connect to the device and use OCast media commands on your own application, you must set the device `applicationName` property. When you connect to the device, the application is started automatically if needed. You can also manage the application state manually. See [Manage application state](#8-manage-application-state).

```kotlin
device.applicationName = "MyWebApp"
```

If you want to perform a secure connection, you can set an `SSLConfiguration` object with your custom settings. Then you must call the `connect(sslConfiguration: SSLConfiguration?, onSuccess: Runnable, onError: Consumer<OCastError>)` method. Once either `onSuccess` or `onError` is called, you can send commands to your device.

```kotlin
val sslConfiguration = SSLConfiguration(trustManager, socketFactory, hostnameVerifier)
device.connect(sslConfiguration, {
    // Send commands
}, { error ->
    // Manage connection errors
})
```

You can disconnect from the device using the `disconnect(onSuccess: Runnable, onError: Consumer<OCastError>)` method. This is useful to manage application background state.

If a network error occurs, the `onDeviceDisconnected(device: Device, error: Throwable?)` method of `DeviceListener` is called with the issue reason.

### 4. Send OCast commands

You can use the OCast commands provided by the SDK in the `Device` abstract class. The command list is described here: http://www.ocast.org/OCast-Protocol.pdf

```kotlin
val params = PrepareMediaCommandParams(
    "http://myMovie.mp4",
    1,
    "Movie Sample",
    "OCast",
    "",
    Media.Type.VIDEO,
    Media.TransferMode.BUFFERED,
    true
)
device.prepareMedia(params, null, {}, {})
```

### 5. Receive OCast events

The device can send events defined in the OCast protocol. The various methods of the `EventListener` interface will be called depending on the event.

```kotlin
deviceCenter.addEventListener(this)
override fun onMediaPlaybackStatus(device: Device, mediaPlaybackStatus: MediaPlaybackStatus) {}
override fun onMediaMetadataChanged(device: Device, mediaMetadata: MediaMetadata) {}
override fun onUpdateStatus(device: Device, updateStatus: UpdateStatus) {}
```

### 6. Send custom commands

If you need to send a command not defined in the OCast protocol, you can use the `send(message: OCastApplicationLayer<T>, domain: OCastDomain, onSuccess: Runnable, onError: Consumer<OCastError>)` method (or its `Consumer` counterpart) of the `Device` abstract class.
The custom command must subclass `OCastCommandParams`.

```kotlin
class CustomCommandParams(val myParameter: String) : OCastCommandParams("customCommand")
class CustomReplyParams(myValue: String)
val message = OCastApplicationLayer("CustomService", CustomCommandParams("paramValue").build())
device.send(message, OCastDomain.BROWSER, CustomReplyParams::class.java, { reply ->
    // ...
}, { error ->
    // ...
})
````

Please note that OCast SDK uses [Jackson](https://github.com/FasterXML/jackson) under the hood. Thus you can use [Jackson](https://github.com/FasterXML/jackson) annotations when defining your custom commands and replies if needed.

### 7. Receive custom events

If you need to receive an event not defined in the OCast protocol, you can override the `onCustomEvent(device: Device, name: String, params: String)` method of the `EventListerner` interface.

```kotlin
class CustomEvent(val myEventValue: String)
override fun onCustomEvent(device: Device, name: String, params: String) {
    val customEvent = JsonTools.decode<CustomEvent>(params)
    // ...
}
```

### 8. Manage application state

You can manage the application state manually. The `startApplication(onSuccess: Runnable, onError: Consumer<OCastError>)` method starts the application identified by the `applicationName` property whereas the `stopApplication(onSuccess: Runnable, onError: Consumer<OCastError>)` method stops it.

## Sample applications

Both Java and Kotlin desktop sample applications as well as a Kotlin Android application are available.

## Author

Orange

## License

OCast is licensed under the Apache v2 License. See the LICENSE file for more info.
