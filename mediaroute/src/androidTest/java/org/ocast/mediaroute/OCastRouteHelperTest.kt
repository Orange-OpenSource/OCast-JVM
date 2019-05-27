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

package org.ocast.mediaroute

import android.os.Handler
import android.os.Looper
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.support.v7.media.MediaRouter
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ocast.mediaroute.models.MediaRouteDevice
import org.ocast.core.ReferenceDevice
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class OCastRouteHelperTest {

    private lateinit var fakeDevice: FakeDeviceMediaRouteProvider
    private lateinit var oCastRouteHelper: OCastRouteHelper
    private val handler = Handler(Looper.getMainLooper())
    private var mediaRouterCallback: MediaRouter.Callback = MediaRouterCallback()
    private var mediaRouteDevice: MediaRouteDevice? = null

    @Before
    fun setUp() {
        val appContext = InstrumentationRegistry.getTargetContext()

        handler.post {
            mediaRouteDevice = null

            fakeDevice = FakeDeviceMediaRouteProvider(appContext)

            oCastRouteHelper = OCastRouteHelper(appContext, listOf(ReferenceDevice::class.java))
            oCastRouteHelper.addMediaRouterCallback(mediaRouterCallback)
            oCastRouteHelper.addMediaRouteProvider(fakeDevice)
        }
    }

    @After
    fun tearDown() {
        oCastRouteHelper.removeMediaRouteProvider(fakeDevice)
        oCastRouteHelper.removeMediaRouterCallback(mediaRouterCallback)
    }

    private val latch = CountDownLatch(1)

    @Test
    fun callsListenerOnDeviceAdded() {

        latch.await(10, TimeUnit.SECONDS)

        Assert.assertEquals(mediaRouteDevice?.friendlyName, FakeDeviceMediaRouteProvider.FAKE_DEVICE_NAME)
        Assert.assertEquals(mediaRouteDevice?.uuid, FakeDeviceMediaRouteProvider.FAKE_DEVICE_UUID)
    }

    private inner class MediaRouterCallback : MediaRouter.Callback() {

        override fun onRouteAdded(router: MediaRouter?, routeInfo: MediaRouter.RouteInfo?) {
            mediaRouteDevice = routeInfo?.extras?.get(MediaRouteDevice.EXTRA_DEVICE) as? MediaRouteDevice
            latch.countDown()
        }
    }
}
