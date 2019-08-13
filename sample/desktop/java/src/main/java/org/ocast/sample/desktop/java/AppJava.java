/*
 * Copyright 2019 Orange
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ocast.sample.desktop.java;

import java.io.FileInputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.ocast.sdk.core.Device;
import org.ocast.sdk.core.DeviceCenter;
import org.ocast.sdk.core.DeviceListener;
import org.ocast.sdk.core.EventListener;
import org.ocast.sdk.core.ReferenceDevice;
import org.ocast.sdk.core.models.Media;
import org.ocast.sdk.core.models.MediaPlaybackStatus;
import org.ocast.sdk.core.models.PrepareMediaCommandParams;
import org.ocast.sdk.core.utils.OCastLog;

public class AppJava implements EventListener, DeviceListener {

    private final CountDownLatch latch = new CountDownLatch(1);
    private final Logger logger = Logger.getLogger("sampleAppJava");
    private final DeviceCenter deviceCenter;

    public static void main(String[] args) {
        AppJava main = new AppJava();
        main.run();
    }

    private AppJava() {
        deviceCenter = new DeviceCenter();
        deviceCenter.addDeviceListener(this);
        deviceCenter.addEventListener(this);
        deviceCenter.registerDevice(ReferenceDevice.class);
        try {
            FileInputStream inputStream = new FileInputStream("logging.properties");
            LogManager.getLogManager().readConfiguration(inputStream);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        OCastLog.setLevel(OCastLog.Level.ALL);
    }

    private void run() {
        try {
            logger.log(Level.INFO, "Application launched");
            deviceCenter.resumeDiscovery();
            latch.await();
        } catch (Exception exception) {
            deviceCenter.stopDiscovery();
            logger.log(Level.SEVERE, "error:", exception);
            Thread.currentThread().interrupt();
        }
        System.exit(0);
    }

    private void startApplication(Device device) {
        device.setApplicationName("Orange-DefaultReceiver-DEV");
        device.connect(
            null,
            () -> device.startApplication(
                () -> prepareMedia(device),
                error -> logger.log(Level.WARNING, "startApplication error: " + error.getMessage())
            ),
            error -> logger.log(Level.WARNING, "connect error: " + error.getMessage())
        );
    }

    private void prepareMedia(Device device) {
        PrepareMediaCommandParams params = new PrepareMediaCommandParams(
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/CastVideos/mp4/BigBuckBunny.mp4",
            1,
            "Big Buck Bunny",
            "sampleAppKotlin",
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
            Media.Type.VIDEO,
            Media.TransferMode.STREAMED,
            true
        );
        device.prepareMedia(
            params,
            null,
            () -> {},
            error -> logger.log(Level.WARNING, "prepareMedia error: " + error.getStatus())
        );
    }

    @Override
    public void onMediaPlaybackStatus(@NotNull Device device, @NotNull MediaPlaybackStatus mediaPlaybackStatus) {
        logger.log(Level.INFO, "[" + device.getFriendlyName() + "]" + "onMediaPlaybackStatus: position=" + mediaPlaybackStatus.getPosition() + " state=" + mediaPlaybackStatus.getState());
    }

    @Override
    public void onDevicesAdded(@NotNull List<? extends Device> devices) {
        for (Device device : devices) {
            logger.log(Level.INFO, "onDeviceAdded: " + device.getFriendlyName());
            startApplication(device);
        }
    }
}
