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

package org.ocast.sample.desktop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ocast.core.Device;
import org.ocast.core.DeviceListener;
import org.ocast.core.OCastCenter;
import org.ocast.core.EventListener;
import org.ocast.core.ReferenceDevice;
import org.ocast.core.models.CustomEvent;
import org.ocast.core.models.Media;
import org.ocast.core.models.MetadataChangedEvent;
import org.ocast.core.models.PlaybackStatusEvent;
import org.ocast.core.models.UpdateStatusEvent;
import org.ocast.core.utils.OCastLog;

import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AppJava implements EventListener, DeviceListener {

    private final CountDownLatch latch = new CountDownLatch(1);
    private final Logger logger = Logger.getLogger("sampleAppJava");

    private final OCastCenter oCastCenter;

    public static void main(String[] args) {
        AppJava main = new AppJava();
        main.run();
    }

    private AppJava() {
        oCastCenter = new OCastCenter();
        oCastCenter.addDeviceListener(this);
        oCastCenter.addEventListener(this);
        oCastCenter.registerDevice(ReferenceDevice.class);
        OCastLog.setLevel(OCastLog.Level.ALL);
    }

    private void run() {
        try {
            logger.log(Level.INFO, "Application launched");
            oCastCenter.resumeDiscovery();
            latch.await();
        } catch (Exception e) {
            oCastCenter.stopDiscovery();
            logger.log(Level.WARNING, "error:", e);
            Thread.currentThread().interrupt();
        }
        System.exit(0);
    }

    private void startApplication(Device device) {
        device.setApplicationName("Orange-DefaultReceiver-DEV");
        device.startApplication(
            () -> prepareMedia(device), oCastError -> logger.log(Level.WARNING, "startApplication error: " + oCastError.getErrorMessage())
        );
    }

    private void prepareMedia(Device device) {
        device.prepareMedia("https://commondatastorage.googleapis.com/gtv-videos-bucket/CastVideos/mp4/BigBuckBunny.mp4",
            1,
            "Big Buck Bunny",
            "sampleAppJava",
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
            Media.Type.VIDEO,
            Media.TransferMode.STREAMED,
            true,
            null,
            () -> {}, oCastError -> logger.log(Level.WARNING, "prepareMedia error: " + oCastError.getStatus())
        );
    }

    @Override
    public void onPlaybackStatus(@NotNull Device device, @NotNull PlaybackStatusEvent status) {
        logger.log(Level.INFO, "[" + device.getFriendlyName() + "]" + "onPlaybackStatus: progress=" + status.getPosition());
        if (status.getState() == Media.PlayerState.IDLE) {
            latch.countDown();
        }
    }

    @Override
    public void onMetadataChanged(@NotNull Device device, @NotNull MetadataChangedEvent metadata) {
    }

    @Override
    public void onUpdateStatus(@NotNull Device device, @NotNull UpdateStatusEvent updateStatus) {
    }

    @Override
    public void onCustomEvent(@NotNull Device device, @NotNull CustomEvent customEvent) {
    }

    @Override
    public void onDeviceDisconnected(@NotNull Device device, @Nullable Throwable error) {
    }

    @Override
    public void onDeviceAdded(@NotNull Device device) {
        startApplication(device);
    }

    @Override
    public void onDeviceRemoved(@NotNull Device device) {
    }
}
