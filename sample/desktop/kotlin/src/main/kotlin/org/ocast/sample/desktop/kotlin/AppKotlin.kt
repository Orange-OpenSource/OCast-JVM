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

package org.ocast.sample.desktop.kotlin

import org.ocast.sdk.core.Device
import org.ocast.sdk.core.DeviceCenter
import org.ocast.sdk.core.DeviceListener
import org.ocast.sdk.core.EventListener
import org.ocast.sdk.core.ReferenceDevice
import org.ocast.sdk.core.models.Media
import org.ocast.sdk.core.models.PlaybackStatus
import org.ocast.sdk.core.models.Prepare
import org.ocast.sdk.core.utils.OCastLog
import java.util.concurrent.CountDownLatch
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.system.exitProcess

class AppKotlin : EventListener, DeviceListener {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val main = AppKotlin()
            main.run()
        }
    }

    private val latch = CountDownLatch(1)
    private val logger = Logger.getLogger("sampleAppKotlin")

    private val deviceCenter = DeviceCenter()

    init {
        deviceCenter.addEventListener(this)
        deviceCenter.addDeviceListener(this)
        deviceCenter.registerDevice(ReferenceDevice::class.java)
        OCastLog.level = OCastLog.Level.ALL
    }

    fun run() {
        try {
            logger.log(Level.INFO, "Application launched")
            deviceCenter.resumeDiscovery()
            latch.await()
        } catch (e: Exception) {
            deviceCenter.stopDiscovery()
            logger.log(Level.WARNING, "error:", e)
            Thread.currentThread().interrupt()
        }

        exitProcess(0)
    }

    private fun startApplication(device: Device) {
        device.applicationName = "Orange-DefaultReceiver-DEV"
        device.connect({
            device.startApplication({
                prepareMedia(device)
            }, { oCastError ->
                logger.log(Level.WARNING, "startApplication error: ${oCastError.message}")
            })
        }, { oCastError ->
            logger.log(Level.WARNING, "connect error: ${oCastError.message}")
        })
    }

    private fun prepareMedia(device: Device) {
        val params = Prepare(
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/CastVideos/mp4/BigBuckBunny.mp4",
            1,
            "Big Buck Bunny",
            "sampleAppKotlin",
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
            Media.Type.VIDEO,
            Media.TransferMode.STREAMED,
            true
        )
        device.prepareMedia(params, null, { }, { logger.log(Level.WARNING, "prepareMedia error: ${it.message}") })
    }

    override fun onPlaybackStatus(device: Device, playbackStatus: PlaybackStatus) {
        logger.log(Level.INFO, "[{${device.friendlyName}}] onPlaybackStatus: progress=${playbackStatus.position} state=${playbackStatus.state}")
    }

    override fun onDeviceAdded(device: Device) {
        logger.log(Level.INFO, "onDeviceAdded: ${device.friendlyName}")
        startApplication(device)
    }
}
