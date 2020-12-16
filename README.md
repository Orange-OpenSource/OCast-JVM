# OCast-JVM

<table>
<tbody>
<tr>
<td>Licence</td>
<td><a href="https://github.com/Orange-OpenSource/OCast-JVM/blob/master/LICENSE.txt"><img src="https://img.shields.io/badge/licence-APACHE--2-lightgrey.svg"></a></td>
</tr>
<tr>
<td>Build status</td>
<td><a href="https://github.com/Orange-OpenSource/OCast-JVM/actions?query=workflow%3Abuild"><img src="https://github.com/Orange-OpenSource/OCast-JVM/workflows/build/badge.svg"></a></td>
</tr>
<tr>
<td>OCast SDK</td>
<td><a href="https://bintray.com/orange-opensource/maven/ocast-jvm-sdk/_latestVersion"><img src="https://api.bintray.com/packages/orange-opensource/maven/ocast-jvm-sdk/images/download.svg"></a></td>
</tr>
<tr>
<td>OCast media route module for Android</td>
<td><a href="https://bintray.com/orange-opensource/maven/ocast-jvm-mediaroute/_latestVersion"><img src="https://api.bintray.com/packages/orange-opensource/maven/ocast-jvm-mediaroute/images/download.svg"></a></td>
</tr>
<tr>
<td>Documentation</td>
<td><a href="https://orange-opensource.github.io/OCast-JVM/"><img src="https://img.shields.io/badge/javadoc-2.0.3-brightgreen"></a></td>
</tr>
</tbody>
</table>

<br>
The Orange OCast SDK provides all required API methods to implement cast applications to interact with an OCast device.

The sample project aims to demonstrate the basic instruction set of the Orange OCast SDK to help you get started.

## Installation

The Orange OCast SDK is available through [JFrog Bintray](https://bintray.com/orange-opensource/maven). To install it, simply add the following lines to your Gradle file:

```groovy
repositories {
    maven {
        url  "https://dl.bintray.com/orange-opensource/maven" 
    }
}

dependencies {
    implementation "org.ocast:sdk:2.0.3"
    // The following line is only needed if you are using OCast on Android
    // and want to take advantage of the MediaRouter framework
    implementation "org.ocast:mediaroute:2.0.3"
}
```

You can also retrieve the source code to build the project by cloning the repository:

```
git clone https://github.com/Orange-OpenSource/OCast-JVM.git
```

## Usage

### 1. Register your device type

You have to register your device type into the `DeviceCenter`.

:small_orange_diamond: Java
```java
DeviceCenter deviceCenter = new DeviceCenter();
deviceCenter.registerDevice(ReferenceDevice.class);
```

:small_blue_diamond: Kotlin
```kotlin
val deviceCenter = DeviceCenter()
deviceCenter.registerDevice(ReferenceDevice::class.java)
```

### 2. Discovering devices

You need to call the `resumeDiscovery()` method to start the device discovery. Then you can be informed by the `DeviceCenter` by adding a `DeviceListener`.

If devices are found on your network, the `onDevicesAdded(@NotNull List<? extends Device> devices)` method is called.

If devices are lost (network problem or device is turned-off), the `onDevicesRemoved(@NotNull List<? extends Device> devices)` method is called.

:small_orange_diamond: Java
```java
deviceCenter.addDeviceListener(this);
deviceCenter.resumeDiscovery();

// DeviceListener methods
@Override
public void onDevicesAdded(@NotNull List<? extends Device> devices) {}
@Override
public void onDevicesRemoved(@NotNull List<? extends Device> devices) {}
@Override
public void onDiscoveryStopped(@Nullable Throwable error) {}
```

:small_blue_diamond: Kotlin
```kotlin
deviceCenter.addDeviceListener(this)
deviceCenter.resumeDiscovery()

// DeviceListener methods
override fun onDevicesAdded(devices: List<Device>) {}
override fun onDevicesRemoved(devices: List<Device>) {}
override fun onDiscoveryStopped(error: Throwable?) {}
```

You can stop the device discovery by calling the `stopDiscovery()` method. This will call the `onDiscoveryStopped(@Nullable Throwable error)` method. The list of discovered devices will be cleaned, so if you want to keep them you should call `pauseDiscovery()` instead. This is useful to manage application background state.

If a network error occurs, the `onDiscoveryStopped(@Nullable Throwable error)` method is called but the error parameter is filled with the issue reason.

By default, the list of devices is refreshed every 30 seconds. You can decrease this interval by setting the `discoveryInterval` property. You should do this when the list of devices is displayed and restore the default value later.

### 3. Connect to the device

To connect to the device and use OCast media commands on your own application, you must set the device `applicationName` property. Once you are connected to the device, the application is started automatically when you send a media command. You can also manage the application state manually. See [Manage application state](#8-manage-application-state).

:small_orange_diamond: Java
```java
device.setApplicationName("MyWebApp");
```

:small_blue_diamond: Kotlin
```kotlin
device.applicationName = "MyWebApp"
```

If you want to perform a secure connection, you can set an `SSLConfiguration` object with your custom settings. Then you must call the `connect(sslConfiguration: SSLConfiguration?, onSuccess: Runnable, onError: Consumer<OCastError>)` method. Once either `onSuccess` or `onError` is called, you can send commands to your device.

:small_orange_diamond: Java
```java
SSLConfiguration sslConfiguration = new SSLConfiguration(trustManager, socketFactory, hostnameVerifier);
device.connect(sslConfiguration, () -> {
    // Send commands
}, oCastError -> {
    // Manage connection errors
});
```

:small_blue_diamond: Kotlin
```kotlin
val sslConfiguration = SSLConfiguration(trustManager, socketFactory, hostnameVerifier)
device.connect(sslConfiguration, {
    // Send commands
}, { oCastError ->
    // Manage connection errors
})
```

You can disconnect from the device using the `disconnect(@NotNull Runnable onSuccess, @NotNull Consumer<OCastError> onError)` method. This is useful to manage application background state.

If a network error occurs, the `onDeviceDisconnected(@NotNull Device device, @Nullable Throwable error)` method of `DeviceListener` is called with the issue reason.

### 4. Send OCast commands

You can use the OCast commands provided by the SDK in the `Device` abstract class. The command list is described [here](http://www.ocast.org/OCast-Protocol.pdf).

:small_orange_diamond: Java
```java
PrepareMediaCommandParams params = new PrepareMediaCommandParams(
        "http://myMovie.mp4",
        1,
        "Movie Sample",
        "OCast",
        "",
        Media.Type.VIDEO,
        Media.TransferMode.STREAMED,
        true
);
device.prepareMedia(params, null, () -> {}, oCastError -> {});
```

:small_blue_diamond: Kotlin
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

:small_orange_diamond: Java
```java
deviceCenter.addEventListener(this);

// EventListener methods
@Override
public void onMediaPlaybackStatus(@NotNull Device device, @NotNull MediaPlaybackStatus mediaPlaybackStatus) {}
@Override
public void onMediaMetadataChanged(@NotNull Device device, @NotNull MediaMetadata mediaMetadata) {}
@Override
public void onUpdateStatus(@NotNull Device device, @NotNull UpdateStatus updateStatus) {}
```

:small_blue_diamond: Kotlin
```kotlin
deviceCenter.addEventListener(this)

// EventListener methods
override fun onMediaPlaybackStatus(device: Device, mediaPlaybackStatus: MediaPlaybackStatus) {}
override fun onMediaMetadataChanged(device: Device, mediaMetadata: MediaMetadata) {}
override fun onUpdateStatus(device: Device, updateStatus: UpdateStatus) {}
```

### 6. Send custom commands

If you need to send a command not defined in the OCast protocol, you can use the `send(@NotNull OCastApplicationLayer<T> message, @NotNull OCastDomain domain, @NotNull Runnable onSuccess, @NotNull Consumer<OCastError> onError)` method (or its `Consumer` counterpart) of the `Device` abstract class.
The custom command must subclass `OCastCommandParams`.

:small_orange_diamond: Java
```java
class CustomCommandParams extends OCastCommandParams {

    @NotNull
    private String myParameter;

    public CustomCommandParams(@NotNull String myParameter) {
        super("customCommand");
        this.myParameter = myParameter;
    }

    @NotNull
    public String getMyParameter() {
        return myParameter;
    }
}

class CustomReplyParams {

    @NotNull
    private String myValue;

    public CustomReplyParams(@NotNull String myValue) {
        this.myValue = myValue;
    }

    @NotNull
    public String getMyValue() {
        return myValue;
    }
}

OCastDataLayer<OCastCommandParams> data = new CustomCommandParams("paramValue").build();
OCastApplicationLayer<OCastCommandParams> message = new OCastApplicationLayer<>("CustomService", data);
device.send(message, OCastDomain.BROWSER, CustomReplyParams.class, customReplyParams -> {
    // ...
}, oCastError -> {
    // ...
});
````

:small_blue_diamond: Kotlin
```kotlin
class CustomCommandParams(val myParameter: String) : OCastCommandParams("customCommand")
class CustomReplyParams(val myValue: String)

val data = CustomCommandParams("paramValue").build()
val message = OCastApplicationLayer("CustomService", data)
device.send(message, OCastDomain.BROWSER, CustomReplyParams::class.java, { customReplyParams ->
    // ...
}, { oCastError ->
    // ...
})
````

Please note that the Orange OCast SDK uses [Jackson](https://github.com/FasterXML/jackson) under the hood. Thus you can use [Jackson](https://github.com/FasterXML/jackson) annotations when defining your custom commands and replies if needed.

### 7. Receive custom events

If you need to receive an event not defined in the OCast protocol, you can override the `onCustomEvent(@NotNull Device device, @NotNull String name, @NotNull String params)` method of the `EventListerner` interface.

:small_orange_diamond: Java
```java
class CustomEvent {

    @NotNull
    private String myEventValue;

    public CustomEvent(@NotNull String myEventValue) {
        this.myEventValue = myEventValue;
    }

    @NotNull
    public String getMyEventValue() {
        return myEventValue;
    }
}

@Override
public void onCustomEvent(@NotNull Device device, @NotNull String name, @NotNull String params) {
    try {
        CustomEvent customEvent = JsonTools.INSTANCE.decode(params, CustomEvent.class);
        // ...
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

:small_blue_diamond: Kotlin
```kotlin
class CustomEvent(val myEventValue: String)

override fun onCustomEvent(device: Device, name: String, params: String) {
    try {
        val customEvent = JsonTools.decode<CustomEvent>(params)
        // ...
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
```

### 8. Manage application state

You can manage the application state manually. The `startApplication(@NotNull Runnable onSuccess, @NotNull Consumer<OCastError> onError)` method starts the application identified by the `applicationName` property whereas the `stopApplication(@NotNull Runnable onSuccess, @NotNull Consumer<OCastError> onError)` method stops it.

### 9. Android media route module

If you are using the Orange OCast SDK on Android, you may optionally use the OCast media route module. This module allows you to take advantage of the native Android `MediaRouter` framework when interacting with OCast devices.

You do not need to manipulate any instance of `DeviceCenter` when using the OCast media route module. You use the `OCastMediaRouteHelper` instead, and devices are wrapped into instances of `MediaRouter.RouteInfo`.

To use the Android media route module, simply initialize the `OCastMediaRouteHelper` singleton with the list of device types you want to detect, and register a `MediaRouter.Callback` to be notified of the various `MediaRouter` events:

:small_orange_diamond: Java
```java
OCastMediaRouteHelper.INSTANCE.initialize(this, Arrays.asList(ReferenceDevice.class));
OCastMediaRouteHelper.addMediaRouterCallback(mediaRouterCallback)

// MediaRouter.Callback methods
@Override
public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo route) {
    Device device = OCastMediaRouteHelper.INSTANCE.getDeviceFromRoute(route);
    if (device != null) {
        // Set applicationName property and connect to the device
    }
}
@Override
public void onRouteUnselected(MediaRouter router, int type, MediaRouter.RouteInfo info) {}
@Override
public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo info) {}
@Override
public void onRouteRemoved(MediaRouter router, MediaRouter.RouteInfo info) {}
@Override
public void onRouteChanged(MediaRouter router, MediaRouter.RouteInfo info) {}
```

:small_blue_diamond: Kotlin
```kotlin
OCastMediaRouteHelper.initialize(this, listOf(ReferenceDevice::class.java))
OCastMediaRouteHelper.addMediaRouterCallback(mediaRouterCallback)

// MediaRouter.Callback methods
override fun onRouteSelected(router: MediaRouter?, route: MediaRouter.RouteInfo?) {
    OCastMediaRouteHelper.getDeviceFromRoute(route)?.run {
        // Set applicationName property and connect to the device
    }
}
override fun onRouteUnselected(mediaRouter: MediaRouter?, route: MediaRouter.RouteInfo?) {}
override fun onRouteRemoved(router: MediaRouter?, route: MediaRouter.RouteInfo?) {}
override fun onRouteAdded(router: MediaRouter?, route: MediaRouter.RouteInfo?) {}
override fun onRouteChanged(router: MediaRouter?, route: MediaRouter.RouteInfo?) {}
```

As with `DeviceCenter`, it is also possible to add an `EventListener` to receive OCast events:

:small_orange_diamond: Java
```java
OCastMediaRouteHelper.INSTANCE.addEventListener(this);

// EventListener methods
@Override
public void onMediaPlaybackStatus(@NotNull Device device, @NotNull MediaPlaybackStatus mediaPlaybackStatus) {}
@Override
public void onMediaMetadataChanged(@NotNull Device device, @NotNull MediaMetadata mediaMetadata) {}
@Override
public void onUpdateStatus(@NotNull Device device, @NotNull UpdateStatus updateStatus) {}
```

:small_blue_diamond: Kotlin
```kotlin
OCastMediaRouteHelper.addEventListener(this)
override fun onMediaPlaybackStatus(device: Device, mediaPlaybackStatus: MediaPlaybackStatus) {}
override fun onMediaMetadataChanged(device: Device, mediaMetadata: MediaMetadata) {}
override fun onUpdateStatus(device: Device, updateStatus: UpdateStatus) {}
```

The `MediaRouter` framework allows you to display a dialog with the list of detected media routes. To do so you need to create an XML file with the content hereafter and implement the `onCreateOptionsMenu(Menu menu)` method of your activity:

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto">
    <item
        android:id="@+id/item_all_media_route"
        android:title="@string/all_media_route"
        app:actionProviderClass="androidx.mediarouter.app.MediaRouteActionProvider"
        app:showAsAction="always" />

</menu>
```

:small_orange_diamond: Java
```java
@Override
public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);

    getMenuInflater().inflate(R.menu.my_menu, menu);
    MenuItem mediaRouteMenuItem = menu.findItem(R.id.item_all_media_route);
    MediaRouteActionProvider actionProvider = (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
    actionProvider.setRouteSelector(OCastMediaRouteHelper.INSTANCE.getMediaRouteSelector());

    return true;
}
```

:small_blue_diamond: Kotlin
```kotlin
override fun onCreateOptionsMenu(menu: Menu): Boolean {
    super.onCreateOptionsMenu(menu)

    menuInflater.inflate(R.menu.my_menu, menu)
    val mediaRouteMenuItem = menu.findItem(R.id.item_all_media_route)
    val actionProvider = MenuItemCompat.getActionProvider(mediaRouteMenuItem) as MediaRouteActionProvider
    actionProvider.routeSelector = OCastMediaRouteHelper.mediaRouteSelector

    return true
}
```

The Android media route module automatically sets the `discoverInterval` property of the underlying `DeviceCenter` to its minimum value when the media route selection dialog is displayed, and sets it back to its default value when the dialog is dismissed.

## Sample applications

Both Java and Kotlin desktop sample applications as well as a Kotlin Android application are available.

## Logs

The Orange OCast SDK includes specific log messages which can be enabled by changing the `DEBUG` constant of the `OCastLog.Companion` class to `true` and by recompiling the SDK.

The `level` property of the `OCastLog.Companion` class controls the logging output. The default value is `OFF`.

The logs rely on the native Java logging framework, thus you will need to configure the log level of your handlers. If you are working on Android, you will also need to type the following command in a terminal to enable logs with level lower than `INFO`:

```
adb shell setprop log.tag.WebSocket VERBOSE
```

Where `WebSocket` should be replaced by the name of the class which contains the logs to display.

Please note that logs are not enabled on [JFrog Bintray](https://bintray.com/orange-opensource/maven) releases.

## Author

Orange

## License

OCast is licensed under the Apache v2 License. See the LICENSE file for more info.
