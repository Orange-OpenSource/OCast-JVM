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

package org.ocast.sdk.core

import org.json.JSONObject
import org.ocast.sdk.common.extensions.ifNotNull
import org.ocast.sdk.common.extensions.orElse
import org.ocast.sdk.core.models.Consumer
import org.ocast.sdk.core.models.DeviceID
import org.ocast.sdk.core.models.DeviceMessage
import org.ocast.sdk.core.models.GamepadEvent
import org.ocast.sdk.core.models.GetDeviceID
import org.ocast.sdk.core.models.GetMetadata
import org.ocast.sdk.core.models.GetPlaybackStatus
import org.ocast.sdk.core.models.GetUpdateStatus
import org.ocast.sdk.core.models.InputMessage
import org.ocast.sdk.core.models.KeyPressed
import org.ocast.sdk.core.models.Media
import org.ocast.sdk.core.models.MediaMessage
import org.ocast.sdk.core.models.Metadata
import org.ocast.sdk.core.models.MouseEvent
import org.ocast.sdk.core.models.Mute
import org.ocast.sdk.core.models.OCastApplicationLayer
import org.ocast.sdk.core.models.OCastCommandDeviceLayer
import org.ocast.sdk.core.models.OCastDataLayer
import org.ocast.sdk.core.models.OCastDataLayerBuilder
import org.ocast.sdk.core.models.OCastDeviceSettingsError
import org.ocast.sdk.core.models.OCastError
import org.ocast.sdk.core.models.OCastInputSettingsError
import org.ocast.sdk.core.models.OCastMediaError
import org.ocast.sdk.core.models.OCastRawDataLayer
import org.ocast.sdk.core.models.OCastRawDeviceLayer
import org.ocast.sdk.core.models.OCastReplyEventParams
import org.ocast.sdk.core.models.Pause
import org.ocast.sdk.core.models.Play
import org.ocast.sdk.core.models.PlaybackStatus
import org.ocast.sdk.core.models.Prepare
import org.ocast.sdk.core.models.ReplyCallback
import org.ocast.sdk.core.models.Resume
import org.ocast.sdk.core.models.RunnableCallback
import org.ocast.sdk.core.models.Seek
import org.ocast.sdk.core.models.Stop
import org.ocast.sdk.core.models.Track
import org.ocast.sdk.core.models.UpdateStatus
import org.ocast.sdk.core.models.Volume
import org.ocast.sdk.core.models.WebAppConnectedStatus
import org.ocast.sdk.core.models.WebAppStatus
import org.ocast.sdk.core.utils.JsonTools
import org.ocast.sdk.core.utils.OCastLog
import org.ocast.sdk.dial.DialClient
import org.ocast.sdk.dial.models.DialApplication
import org.ocast.sdk.discovery.models.UpnpDevice
import java.net.URI
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

open class ReferenceDevice(upnpDevice: UpnpDevice) : Device(upnpDevice), WebSocket.Listener {

    companion object {

        internal const val SERVICE_SETTINGS_DEVICE = "org.ocast.settings.device"
        internal const val SERVICE_SETTINGS_INPUT = "org.ocast.settings.input"
        internal const val SERVICE_MEDIA = "org.ocast.media"

        internal const val SERVICE_APPLICATION = "org.ocast.webapp"

        private const val EVENT_MEDIA_PLAYBACK_STATUS = "playbackStatus"
        private const val EVENT_MEDIA_METADATA_CHANGED = "metadataChanged"
        private const val EVENT_DEVICE_UPDATE_STATUS = "updateStatus"
    }

    override fun getSearchTarget() = "urn:cast-ocast-org:service:cast:1"
    override fun getManufacturer() = "Orange SA"

    override fun setApplicationName(applicationName: String?) {
        // Reset application state if application name is modified
        if (this.applicationName != applicationName) {
            isApplicationRunning.set(false)
            applicationSemaphore?.release()
        }
        super.setApplicationName(applicationName)
    }

    override fun setState(state: State) {
        if (this.state != state) {
            isApplicationRunning.set(false)
            applicationSemaphore?.release()
        }
        super.setState(state)
    }
    private val sequenceID = AtomicLong(0)
    protected var clientUuid = UUID.randomUUID().toString()
    protected var webSocket: WebSocket? = null
    protected var connectCallback: RunnableCallback? = null
    protected var disconnectCallback: RunnableCallback? = null
    private val settingsWebSocketURL = URI("wss://${dialURL.host}:4433/ocast")

    protected val replyCallbacksBySequenceID: MutableMap<Long, ReplyCallback<*>> = Collections.synchronizedMap(mutableMapOf())
    private val dialClient = DialClient(dialURL)
    protected var isApplicationRunning = AtomicBoolean(false)
    private var applicationSemaphore: Semaphore? = null

    //region RemoteDevice

    override fun startApplication(onSuccess: Runnable, onError: Consumer<OCastError>) {
        applicationName?.ifNotNull { applicationName ->
            when (state) {
                State.CONNECTING -> onError.wrapRun(OCastError("Device is connecting, start cannot be processed yet"))
                State.CONNECTED -> startApplication(applicationName, onSuccess, onError)
                State.DISCONNECTING -> onError.wrapRun(OCastError("Device is connecting, start cannot be processed."))
                State.DISCONNECTED -> onError.wrapRun(OCastError("Device is disconnected, start cannot be processed."))
            }
        }.orElse {
            onError.wrapRun(OCastError("Property applicationName is not defined"))
        }
    }

    private fun startApplication(name: String, onSuccess: Runnable, onError: Consumer<OCastError>) {
        dialClient.getApplication(name) { result ->
            result.onFailure { throwable ->
                onError.wrapRun(OCastError("Failed to start $name, there was an error with the DIAL application information request", throwable))
            }
            result.onSuccess { application ->
                isApplicationRunning.set(application.state == DialApplication.State.Running)
                if (isApplicationRunning.get()) {
                    onSuccess.wrapRun()
                } else {
                    dialClient.startApplication(name) { result ->
                        result.onFailure { throwable ->
                            onError.wrapRun(OCastError("Failed to start $name, there was an error with the DIAL request", throwable))
                        }
                        result.onSuccess {
                            applicationSemaphore = Semaphore(0)
                            // Semaphore is released when state or application name changes
                            // In these cases onError must be called
                            if (applicationSemaphore?.tryAcquire(60, TimeUnit.SECONDS) == true && applicationName == name && state == State.CONNECTED) {
                                onSuccess.wrapRun()
                            } else {
                                onError.wrapRun(OCastError("Failed to start $name, the web app connectedStatus event was not received"))
                            }
                            applicationSemaphore = null
                        }
                    }
                }
            }
        }
    }

    override fun stopApplication(onSuccess: Runnable, onError: Consumer<OCastError>) {
        applicationName?.ifNotNull { applicationName ->
            dialClient.stopApplication(applicationName) { result ->
                result.onFailure { throwable ->
                    onError.wrapRun(OCastError("Failed to stop $applicationName, there was an error with the DIAL request", throwable))
                }
                result.onSuccess {
                    isApplicationRunning.set(false)
                    onSuccess.wrapRun()
                }
            }
        }.orElse {
            onError.wrapRun(OCastError("Property applicationName is not defined"))
        }
    }

    override fun connect(sslConfiguration: SSLConfiguration?, onSuccess: Runnable, onError: Consumer<OCastError>) {
        when (state) {
            State.CONNECTING -> onError.wrapRun(OCastError("Device is already connecting"))
            State.CONNECTED -> onSuccess.wrapRun()
            State.DISCONNECTING -> onError.wrapRun(OCastError("Device is disconnecting"))
            State.DISCONNECTED -> {
                applicationName?.ifNotNull { applicationName ->
                    dialClient.getApplication(applicationName) { result ->
                        val webSocketURL = result.getOrNull()?.additionalData?.webSocketURL ?: settingsWebSocketURL
                        connect(webSocketURL, sslConfiguration, onSuccess, onError)
                    }
                }.orElse {
                    connect(settingsWebSocketURL, sslConfiguration, onSuccess, onError)
                }
            }
        }
    }

    private fun connect(webSocketURL: URI, sslConfiguration: SSLConfiguration?, onSuccess: Runnable, onError: Consumer<OCastError>) {
        webSocket = WebSocket(webSocketURL.toString(), sslConfiguration, this)
        state = State.CONNECTING
        connectCallback = RunnableCallback(onSuccess, onError)
        webSocket?.connect()
    }

    override fun disconnect(onSuccess: Runnable, onError: Consumer<OCastError>) {
        if (state != State.DISCONNECTING && state != State.DISCONNECTED) {
            state = State.DISCONNECTING
            disconnectCallback = RunnableCallback(onSuccess, onError)
            webSocket?.disconnect()
        }
    }

    //endregion

    //region WebSocket listener

    override fun onDisconnected(webSocket: WebSocket, error: Throwable?) {
        if (state != State.DISCONNECTED) {
            state = State.DISCONNECTED
            // Send error callback to all waiting commands
            synchronized(replyCallbacksBySequenceID) {
                replyCallbacksBySequenceID.forEach { (_, callback) ->
                    callback.onError.wrapRun(OCastError("Socket has been disconnected", error))
                }
                replyCallbacksBySequenceID.clear()
            }
            connectCallback?.ifNotNull { connectCallback ->
                connectCallback.onError.wrapRun(OCastError("Socket did not connect", error))
            }
            disconnectCallback?.ifNotNull { disconnectCallback ->
                if (error == null) {
                    disconnectCallback.onSuccess.wrapRun()
                } else {
                    disconnectCallback.onError.wrapRun(OCastError("Socket did not disconnect properly", error))
                }
            }
            if (connectCallback == null && disconnectCallback == null) {
                deviceListener?.onDeviceDisconnected(this, error)
            }
            connectCallback = null
            disconnectCallback = null
        }
    }

    override fun onConnected(webSocket: WebSocket, url: String) {
        state = State.CONNECTED
        connectCallback?.onSuccess?.wrapRun()
        connectCallback = null
    }

    override fun onDataReceived(webSocket: WebSocket, data: String) {
        var deviceLayer: OCastRawDeviceLayer? = null
        try {
            deviceLayer = JsonTools.decode(data)
            when (deviceLayer.type) {
                OCastRawDeviceLayer.Type.EVENT -> analyzeEvent(deviceLayer)
                OCastRawDeviceLayer.Type.REPLY -> {
                    val replyCallback = replyCallbacksBySequenceID[deviceLayer.identifier]
                    replyCallback?.ifNotNull {
                        if (deviceLayer.status == OCastRawDeviceLayer.Status.OK) {
                            val replyData = JsonTools.decode<OCastDataLayer<OCastReplyEventParams>>(deviceLayer.message.data)
                            if (replyData.params.code == OCastError.Status.SUCCESS.code) {
                                val oCastData = JsonTools.decode<OCastRawDataLayer>(deviceLayer.message.data)
                                val reply = if (replyCallback.replyClass != Unit::class.java) {
                                    JsonTools.decode(oCastData.params, replyCallback.replyClass)
                                } else {
                                    Unit
                                }
                                @Suppress("UNCHECKED_CAST")
                                (it as ReplyCallback<Any?>).onSuccess.wrapRun(reply)
                            } else {
                                val code = replyData.params.code ?: OCastError.Status.UNKNOWN_ERROR.code
                                it.onError.wrapRun(OCastError(code, "Command error code ${replyData.params.code}"))
                            }
                        } else {
                            it.onError.wrapRun(OCastError(OCastError.Status.DEVICE_LAYER_ERROR.code, "Bad status value ${deviceLayer.status}"))
                        }
                    }
                }
                OCastRawDeviceLayer.Type.COMMAND -> {}
            }
        } catch (e: Exception) {
            OCastLog.error(e) { "Receive a bad formatted message: $data" }
        } finally {
            deviceLayer?.ifNotNull {
                // Remove callback
                replyCallbacksBySequenceID.remove(it.identifier)
            }
        }
    }

    //endregion

    //region Events

    @Throws(Exception::class)
    private fun analyzeEvent(deviceLayer: OCastRawDeviceLayer) {
        val oCastData = JsonTools.decode<OCastRawDataLayer>(deviceLayer.message.data)
        when (deviceLayer.message.service) {
            SERVICE_APPLICATION -> {
                when (JsonTools.decode<WebAppConnectedStatusEvent>(oCastData.params).status) {
                    WebAppStatus.CONNECTED -> {
                        isApplicationRunning.set(true)
                        applicationSemaphore?.release()
                    }
                    WebAppStatus.DISCONNECTED -> isApplicationRunning.set(false)
                    else -> {}
                }
            }
            SERVICE_MEDIA -> {
                when (oCastData.name) {
                    EVENT_MEDIA_PLAYBACK_STATUS -> {
                        val playbackStatus = JsonTools.decode<MediaPlaybackStatus>(oCastData.params)
                        eventListener?.onMediaPlaybackStatus(this, playbackStatus)
                    }
                    EVENT_MEDIA_METADATA_CHANGED -> {
                        val metadataChanged = JsonTools.decode<MediaMetadata>(oCastData.params)
                        eventListener?.onMediaMetadataChanged(this, metadataChanged)
                    }
                }
            }
            SERVICE_SETTINGS_DEVICE -> {
                when (oCastData.name) {
                    EVENT_DEVICE_UPDATE_STATUS -> {
                        val updateStatus = JsonTools.decode<UpdateStatus>(oCastData.params)
                        eventListener?.onUpdateStatus(this, updateStatus)
                    }
                }
            }
            else -> {
                // Custom event
                val params = JsonTools.decode<JSONObject>(oCastData.params)
                eventListener?.onCustomEvent(this, oCastData.name, params)
            }
        }
    }

    //endregion

    //region Media commands

    override fun playMedia(position: Double, onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(MediaPlayCommandParams(position).build()), OCastDomain.BROWSER, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun stopMedia(onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(MediaStopCommandParams().build()), OCastDomain.BROWSER, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun pauseMedia(onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(MediaPauseCommandParams().build()), OCastDomain.BROWSER, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun resumeMedia(onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(MediaResumeCommandParams().build()), OCastDomain.BROWSER, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun prepareMedia(params: MediaPrepareCommandParams, options: JSONObject?, onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(params.options(options).build()), OCastDomain.BROWSER, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun setMediaVolume(volume: Double, onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(MediaVolumeCommandParams(volume).build()), OCastDomain.BROWSER, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun setMediaTrack(params: MediaTrackCommandParams, onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(params.build()), OCastDomain.BROWSER, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun seekMedia(position: Double, onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(MediaSeekCommandParams(position).build()), OCastDomain.BROWSER, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun muteMedia(mute: Boolean, onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(MediaMuteCommandParams(mute).build()), OCastDomain.BROWSER, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun getMediaPlaybackStatus(onSuccess: Consumer<MediaPlaybackStatus>, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(MediaGetPlaybackStatusCommandParams().build()), OCastDomain.BROWSER, MediaPlaybackStatus::class.java, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun getMediaMetadata(onSuccess: Consumer<MediaMetadata>, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(MediaGetMetadataCommandParams().build()), OCastDomain.BROWSER, MediaMetadata::class.java, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    //endregion

    //region Settings device commands

    override fun getUpdateStatus(onSuccess: Consumer<UpdateStatus>, onError: Consumer<OCastDeviceSettingsError>) {
        send(DeviceMessage(GetUpdateStatusCommandParams().build()), OCastDomain.SETTINGS, UpdateStatus::class.java, onSuccess, Consumer { onError.run(OCastDeviceSettingsError(it)) })
    }

    override fun getDeviceID(onSuccess: Consumer<String>, onError: Consumer<OCastDeviceSettingsError>) {
        send(DeviceMessage(GetDeviceIDCommandParams().build()), OCastDomain.SETTINGS, DeviceID::class.java, { onSuccess.run(it.id) }, { onError.run(OCastDeviceSettingsError(it)) })
    }

    //endregion

    //region Settings input commands

    override fun sendKeyEvent(params: KeyEventCommandParams, onSuccess: Runnable, onError: Consumer<OCastInputSettingsError>) {
        send(InputMessage(params.build()), OCastDomain.SETTINGS, onSuccess, Consumer { onError.run(OCastInputSettingsError(it)) })
    }

    override fun sendMouseEvent(params: MouseEventCommandParams, onSuccess: Runnable, onError: Consumer<OCastInputSettingsError>) {
        send(InputMessage(params.build()), OCastDomain.SETTINGS, onSuccess, Consumer { onError.run(OCastInputSettingsError(it)) })
    }

    override fun sendGamepadEvent(params: GamepadEventCommandParams, onSuccess: Runnable, onError: Consumer<OCastInputSettingsError>) {
        send(InputMessage(params.build()), OCastDomain.SETTINGS, onSuccess, Consumer { onError.run(OCastInputSettingsError(it)) })
    }

    //endregion

    //region Custom commands

    override fun <T : Any?> send(message: OCastApplicationLayer<T>, domain: OCastDomain, onSuccess: Runnable, onError: Consumer<OCastError>) {
        send(message, domain, Unit::class.java, Consumer { onSuccess.run() }, onError)
    }

    override fun <T : Any?, S : Any?> send(message: OCastApplicationLayer<T>, domain: OCastDomain, replyClass: Class<S>, onSuccess: Consumer<S>, onError: Consumer<OCastError>) {
        val id = generateSequenceID()
        try {
            replyCallbacksBySequenceID[id] = ReplyCallback(replyClass, onSuccess, onError)
            val deviceLayer = OCastCommandDeviceLayer(clientUuid, domain.value, OCastRawDeviceLayer.Type.COMMAND, id, message)
            val deviveLayerString = JsonTools.encode(deviceLayer)
            // Do not start application when sending settings commands
            sendToWebSocket(id, deviveLayerString, domain == OCastDomain.BROWSER, onError)
        } catch (exception: Exception) {
            replyCallbacksBySequenceID.remove(id)
            onError.wrapRun(OCastError("Unable to get string from data", exception))
        }
    }

    //endregion

    protected fun sendToWebSocket(id: Long, message: String, startApplicationIfNeeded: Boolean, onError: Consumer<OCastError>) {
        val send = {
            if (webSocket?.send(message) == false) {
                replyCallbacksBySequenceID.remove(id)
                onError.wrapRun(OCastError("Unable to send message"))
            }
        }

        if (!startApplicationIfNeeded || isApplicationRunning.get()) {
            send()
        } else {
            startApplication({
                send()
            }, {
                onError.wrapRun(it)
            })
        }
    }

    protected fun generateSequenceID(): Long {
        if (sequenceID.get() == Long.MAX_VALUE) {
            sequenceID.set(0)
        }
        return sequenceID.incrementAndGet()
    }
}
