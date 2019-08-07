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

import java.net.URI
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject
import org.ocast.sdk.common.extensions.ifNotNull
import org.ocast.sdk.common.extensions.orElse
import org.ocast.sdk.core.models.Consumer
import org.ocast.sdk.core.models.DeviceID
import org.ocast.sdk.core.models.DeviceMessage
import org.ocast.sdk.core.models.GetDeviceIDCommandParams
import org.ocast.sdk.core.models.GetMediaMetadataCommandParams
import org.ocast.sdk.core.models.GetMediaPlaybackStatusCommandParams
import org.ocast.sdk.core.models.GetUpdateStatusCommandParams
import org.ocast.sdk.core.models.InputMessage
import org.ocast.sdk.core.models.MediaMessage
import org.ocast.sdk.core.models.MediaMetadata
import org.ocast.sdk.core.models.MediaPlaybackStatus
import org.ocast.sdk.core.models.MuteMediaCommandParams
import org.ocast.sdk.core.models.OCastApplicationLayer
import org.ocast.sdk.core.models.OCastCommandDeviceLayer
import org.ocast.sdk.core.models.OCastDataLayer
import org.ocast.sdk.core.models.OCastDeviceSettingsError
import org.ocast.sdk.core.models.OCastDomain
import org.ocast.sdk.core.models.OCastError
import org.ocast.sdk.core.models.OCastInputSettingsError
import org.ocast.sdk.core.models.OCastMediaError
import org.ocast.sdk.core.models.OCastRawDataLayer
import org.ocast.sdk.core.models.OCastRawDeviceLayer
import org.ocast.sdk.core.models.OCastReplyEventParams
import org.ocast.sdk.core.models.PauseMediaCommandParams
import org.ocast.sdk.core.models.PlayMediaCommandParams
import org.ocast.sdk.core.models.PrepareMediaCommandParams
import org.ocast.sdk.core.models.ReplyCallback
import org.ocast.sdk.core.models.ResumeMediaCommandParams
import org.ocast.sdk.core.models.RunnableCallback
import org.ocast.sdk.core.models.SSLConfiguration
import org.ocast.sdk.core.models.SeekMediaCommandParams
import org.ocast.sdk.core.models.SendGamepadEventCommandParams
import org.ocast.sdk.core.models.SendKeyEventCommandParams
import org.ocast.sdk.core.models.SendMouseEventCommandParams
import org.ocast.sdk.core.models.SetMediaTrackCommandParams
import org.ocast.sdk.core.models.SetMediaVolumeCommandParams
import org.ocast.sdk.core.models.StopMediaCommandParams
import org.ocast.sdk.core.models.UpdateStatus
import org.ocast.sdk.core.models.WebAppConnectedStatusEvent
import org.ocast.sdk.core.models.WebAppStatus
import org.ocast.sdk.core.utils.JsonTools
import org.ocast.sdk.core.utils.OCastLog
import org.ocast.sdk.dial.DialClient
import org.ocast.sdk.dial.models.DialApplication
import org.ocast.sdk.discovery.models.UpnpDevice

/**
 * The reference OCast device.
 *
 * @constructor Creates an instance of [ReferenceDevice].
 */
open class ReferenceDevice(upnpDevice: UpnpDevice) : Device(upnpDevice), WebSocket.Listener {

    /**
     * The companion object.
     */
    companion object {

        /** The name of the device settings service. */
        internal const val SERVICE_SETTINGS_DEVICE = "org.ocast.settings.device"

        /** The name of the input settings service. */
        internal const val SERVICE_SETTINGS_INPUT = "org.ocast.settings.input"

        /** The name of the media service. */
        internal const val SERVICE_MEDIA = "org.ocast.media"

        /** The name of the web application service. */
        internal const val SERVICE_APPLICATION = "org.ocast.webapp"

        /** The name of the media playback status event. */
        private const val EVENT_MEDIA_PLAYBACK_STATUS = "playbackStatus"

        /** The name of the media metadata changed event. */
        private const val EVENT_MEDIA_METADATA_CHANGED = "metadataChanged"

        /** The name of the firmware update status event. */
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

    /** The identifier of the last OCast message sent. */
    private val sequenceID = AtomicLong(0)

    /** An identifier which uniquely identifies the device when sending OCast messages. */
    protected var clientUuid = UUID.randomUUID().toString()

    /** The web socket. */
    protected var webSocket: WebSocket? = null

    /** The callback executed when the device connect process is complete. */
    protected var connectCallback: RunnableCallback? = null

    /** The callback executed when the device disconnect process is complete. */
    protected var disconnectCallback: RunnableCallback? = null

    /** The web socket URL for the settings. */
    private val settingsWebSocketURL = URI("wss://${dialURL.host}:4433/ocast")

    /** A map of callbacks that will be executed when receiving reply messages, indexed by the identifier of their associated command message. */
    protected val replyCallbacksBySequenceID: MutableMap<Long, ReplyCallback<*>> = Collections.synchronizedMap(mutableMapOf())

    /** The DIAL client. */
    private val dialClient = DialClient(dialURL)

    /** A boolean which indicates if the web application specified by `applicationName` is currently running or not. */
    protected var isApplicationRunning = AtomicBoolean(false)

    /** A semaphore to wait when starting a web application. */
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

    /**
     * Starts a web application.
     *
     * @param name The name of the application to start.
     * @param onSuccess The operation executed if the application started successfully.
     * @param onError The operation executed if there was an error while starting the application.
     */
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
                                onError.wrapRun(OCastError("Failed to start $name, the web app connected status event was not received"))
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

    /**
     * Connects to the device.
     *
     * @param webSocketURL The web socket URL.
     * @param sslConfiguration The SSL configuration of the web socket used to connect to the device if it is secure, or `null` if the web socket is not secure.
     * @param onSuccess The operation executed if the connection succeeded.
     * @param onError The operation executed if the connection failed.
     */
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

    override fun onConnected(webSocket: WebSocket) {
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
                            if (replyData.params.code == null || replyData.params.code == OCastError.Status.SUCCESS.code) {
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

    /**
     * Checks if the specified device layer embeds an OCast event.
     *
     * @param deviceLayer The device layer to analyze.
     * @throws Exception If an error occurs while analyzing the device layer.
     */
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
                eventListener?.onCustomEvent(this, oCastData.name, oCastData.params)
            }
        }
    }

    //endregion

    //region Media commands

    override fun playMedia(position: Double, onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(PlayMediaCommandParams(position).build()), OCastDomain.BROWSER, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun stopMedia(onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(StopMediaCommandParams().build()), OCastDomain.BROWSER, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun pauseMedia(onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(PauseMediaCommandParams().build()), OCastDomain.BROWSER, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun resumeMedia(onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(ResumeMediaCommandParams().build()), OCastDomain.BROWSER, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun prepareMedia(params: PrepareMediaCommandParams, options: JSONObject?, onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(params.options(options).build()), OCastDomain.BROWSER, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun setMediaVolume(volume: Double, onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(SetMediaVolumeCommandParams(volume).build()), OCastDomain.BROWSER, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun setMediaTrack(params: SetMediaTrackCommandParams, onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(params.build()), OCastDomain.BROWSER, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun seekMedia(position: Double, onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(SeekMediaCommandParams(position).build()), OCastDomain.BROWSER, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun muteMedia(mute: Boolean, onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(MuteMediaCommandParams(mute).build()), OCastDomain.BROWSER, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun getMediaPlaybackStatus(onSuccess: Consumer<MediaPlaybackStatus>, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(GetMediaPlaybackStatusCommandParams().build()), OCastDomain.BROWSER, MediaPlaybackStatus::class.java, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun getMediaMetadata(onSuccess: Consumer<MediaMetadata>, onError: Consumer<OCastMediaError>) {
        send(MediaMessage(GetMediaMetadataCommandParams().build()), OCastDomain.BROWSER, MediaMetadata::class.java, onSuccess, Consumer { onError.run(OCastMediaError(it)) })
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

    override fun sendKeyEvent(params: SendKeyEventCommandParams, onSuccess: Runnable, onError: Consumer<OCastInputSettingsError>) {
        send(InputMessage(params.build()), OCastDomain.SETTINGS, onSuccess, Consumer { onError.run(OCastInputSettingsError(it)) })
    }

    override fun sendMouseEvent(params: SendMouseEventCommandParams, onSuccess: Runnable, onError: Consumer<OCastInputSettingsError>) {
        send(InputMessage(params.build()), OCastDomain.SETTINGS, onSuccess, Consumer { onError.run(OCastInputSettingsError(it)) })
    }

    override fun sendGamepadEvent(params: SendGamepadEventCommandParams, onSuccess: Runnable, onError: Consumer<OCastInputSettingsError>) {
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
            val deviceLayer = OCastCommandDeviceLayer(clientUuid, domain.value, id, message)
            val deviveLayerString = JsonTools.encode(deviceLayer)
            // Do not start application when sending settings commands
            sendToWebSocket(id, deviveLayerString, domain == OCastDomain.BROWSER, onError)
        } catch (exception: Exception) {
            replyCallbacksBySequenceID.remove(id)
            onError.wrapRun(OCastError("Unable to get string from data", exception))
        }
    }

    //endregion

    /**
     * Sends a message on the web socket.
     *
     * @param id The identifier of the message to send.
     * @param message The message to send.
     * @param startApplicationIfNeeded Indicates if the web application should be started before sending the message.
     * @param onError The operation executed if there was an error while sending the message.
     */
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

    /**
     * Generates a message identifier.
     *
     * @return The message identifier.
     */
    protected fun generateSequenceID(): Long {
        if (sequenceID.get() == Long.MAX_VALUE) {
            sequenceID.set(0)
        }
        return sequenceID.incrementAndGet()
    }
}
