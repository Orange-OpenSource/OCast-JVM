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
import org.ocast.sdk.core.models.Event
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
import org.ocast.sdk.core.models.Service
import org.ocast.sdk.core.models.SetMediaTrackCommandParams
import org.ocast.sdk.core.models.SetMediaVolumeCommandParams
import org.ocast.sdk.core.models.SettingsService
import org.ocast.sdk.core.models.StopMediaCommandParams
import org.ocast.sdk.core.models.UpdateStatus
import org.ocast.sdk.core.models.WebAppConnectedStatusEvent
import org.ocast.sdk.core.models.WebAppStatus
import org.ocast.sdk.core.utils.JsonTools
import org.ocast.sdk.core.utils.OCastLog
import org.ocast.sdk.core.utils.log
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
    protected companion object {

        /**
         * The identifier of the web socket of the reference device.
         *
         * This web socket sends commands and receives replies and events.
         */
        const val REFERENCE_WEB_SOCKET_ID = "REFERENCE_WEB_SOCKET_ID"
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
        OCastLog.debug { "Set state of $friendlyName to ${state.name}" }
    }

    /** The identifier of the last OCast message sent. */
    private val sequenceID = AtomicLong(0)

    /** An identifier which uniquely identifies the device when sending OCast messages. */
    protected var clientUuid = UUID.randomUUID().toString()

    /**
     * A hash map of all the web sockets indexed by their identifier.
     *
     * There is only one web socket in the reference device.
     * This web socket sends commands and receives replies and events.
     */
    private var webSocketsById = hashMapOf<String, WebSocket>()

    /** The web socket URL for the settings. */
    private val settingsWebSocketURL = URI("wss://${dialURL.host}:4433/ocast")

    /** The callback executed when the device connect process is complete. */
    protected var connectCallback: RunnableCallback? = null

    /** The callback executed when the device disconnect process is complete. */
    protected var disconnectCallback: RunnableCallback? = null

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
        applicationName.ifNotNull { applicationName ->
            when (state) {
                State.CONNECTING -> onError.wrapRun(OCastError("Failed to start application $applicationName on $friendlyName, device is connecting").log())
                State.CONNECTED -> startApplication(applicationName, onSuccess, onError)
                State.DISCONNECTING -> onError.wrapRun(OCastError("Failed to start application $applicationName on $friendlyName, device is disconnecting").log())
                State.DISCONNECTED -> onError.wrapRun(OCastError("Failed to start application $applicationName on $friendlyName, device is disconnected").log())
            }
        }.orElse {
            onError.wrapRun(OCastError("Failed to start application on $friendlyName, property applicationName is not defined").log())
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
                onError.wrapRun(OCastError("Failed to start application $name on $friendlyName", throwable).log())
            }
            result.onSuccess { application ->
                isApplicationRunning.set(application.state == DialApplication.State.Running)
                if (isApplicationRunning.get()) {
                    OCastLog.info { "Application $name is already running on $friendlyName" }
                    onSuccess.wrapRun()
                } else {
                    dialClient.startApplication(name) { result ->
                        result.onFailure { throwable ->
                            onError.wrapRun(OCastError("Failed to start application $name on $friendlyName", throwable).log())
                        }
                        result.onSuccess {
                            applicationSemaphore = Semaphore(0)
                            // Semaphore is released when state or application name changes
                            // In these cases onError must be called
                            if (applicationSemaphore?.tryAcquire(60, TimeUnit.SECONDS) == true && applicationName == name && state == State.CONNECTED) {
                                OCastLog.info { "Started application $name on $friendlyName" }
                                onSuccess.wrapRun()
                            } else {
                                onError.wrapRun(OCastError("Failed to start application $name on $friendlyName, the web app connected status event was not received").log())
                            }
                            applicationSemaphore = null
                        }
                    }
                }
            }
        }
    }

    override fun stopApplication(onSuccess: Runnable, onError: Consumer<OCastError>) {
        applicationName.ifNotNull { applicationName ->
            dialClient.stopApplication(applicationName) { result ->
                result.onFailure { throwable ->
                    onError.wrapRun(OCastError("Failed to stop application $applicationName on $friendlyName", throwable).log())
                }
                result.onSuccess {
                    isApplicationRunning.set(false)
                    OCastLog.info { "Stopped application $applicationName on $friendlyName" }
                    onSuccess.wrapRun()
                }
            }
        }.orElse {
            onError.wrapRun(OCastError("Failed to stop application on $friendlyName, property applicationName is not defined").log())
        }
    }

    override fun connect(sslConfiguration: SSLConfiguration?, onSuccess: Runnable, onError: Consumer<OCastError>) {
        when (state) {
            State.CONNECTING -> onError.wrapRun(OCastError("Failed to connect to $friendlyName, device is already connecting").log())
            State.CONNECTED -> {
                OCastLog.info { "Already connected to $friendlyName" }
                onSuccess.wrapRun()
            }
            State.DISCONNECTING -> onError.wrapRun(OCastError("Failed to connect to $friendlyName, device is disconnecting").log())
            State.DISCONNECTED -> {
                onCreateWebSockets(sslConfiguration, Consumer { webSocketsById ->
                    this.webSocketsById = webSocketsById
                    state = State.CONNECTING
                    connectCallback = RunnableCallback(onSuccess, onError)
                    webSocketsById.values.forEach { webSocket ->
                        OCastLog.debug { "Created web socket with ID ${webSocket.id} and url ${webSocket.webSocketURL} for $friendlyName" }
                        webSocket.connect()
                    }
                })
            }
        }
    }

    override fun disconnect(onSuccess: Runnable, onError: Consumer<OCastError>) {
        if (state != State.DISCONNECTING && state != State.DISCONNECTED) {
            state = State.DISCONNECTING
            disconnectCallback = RunnableCallback(onSuccess, onError)
            webSocketsById.values.forEach { it.disconnect() }
        }
    }

    /**
     * Creates all the web sockets.
     *
     * Default behaviour creates only one web socket for the reference device.
     * If you need to override this method to manages multiple web sockets,
     * then you MUST call `onComplete` with a hash map of the created web sockets indexed by their identifier.
     *
     * @param sslConfiguration The SSL configuration if the web sockets to create are secure, or `null` if they are not secure.
     * @param onComplete The operation called when the web sockets are created.
     */
    protected open fun onCreateWebSockets(sslConfiguration: SSLConfiguration?, onComplete: Consumer<HashMap<String, WebSocket>>) {
        applicationName.ifNotNull { applicationName ->
            dialClient.getApplication(applicationName) { result ->
                val webSocketURL = result.getOrNull()?.additionalData?.webSocketURL ?: settingsWebSocketURL
                val webSocket = WebSocket(webSocketURL.toString(), sslConfiguration, this)
                onComplete.run(hashMapOf(REFERENCE_WEB_SOCKET_ID to webSocket))
            }
        }.orElse {
            val webSocket = WebSocket(settingsWebSocketURL.toString(), sslConfiguration, this)
            onComplete.run(hashMapOf(REFERENCE_WEB_SOCKET_ID to webSocket))
        }
    }

    //endregion

    //region WebSocket listener

    override fun onDisconnected(webSocket: WebSocket, error: Throwable?) {
        if (state != State.DISCONNECTED) {
            state = State.DISCONNECTED
            // Send error callback to all waiting commands
            synchronized(replyCallbacksBySequenceID) {
                replyCallbacksBySequenceID.forEach { (identifier, callback) ->
                    callback.onError.wrapRun(OCastError("Failed to receive reply for command with ID $identifier from $friendlyName", error).log())
                }
                replyCallbacksBySequenceID.clear()
            }
            connectCallback.ifNotNull { connectCallback ->
                connectCallback.onError.wrapRun(OCastError("Failed to connect to $friendlyName", error).log())
            }
            disconnectCallback.ifNotNull { disconnectCallback ->
                if (error == null) {
                    OCastLog.info { "Disconnected from $friendlyName" }
                    disconnectCallback.onSuccess.wrapRun()
                } else {
                    disconnectCallback.onError.wrapRun(OCastError("Failed to disconnect from $friendlyName", error).log())
                }
            }
            if (connectCallback == null && disconnectCallback == null) {
                if (error != null) OCastLog.error(error) { "Disconnected from $friendlyName" } else OCastLog.info { "Disconnected from $friendlyName" }
                deviceListener?.onDeviceDisconnected(this, error)
            }
            connectCallback = null
            disconnectCallback = null
            // Disconnect all other web sockets if any
            webSocketsById.values.forEach { otherWebSocket ->
                if (otherWebSocket != webSocket) {
                    otherWebSocket.disconnect()
                }
            }
        }
    }

    override fun onConnected(webSocket: WebSocket) {
        if (webSocketsById.values.all { it.state == WebSocket.State.CONNECTED }) {
            state = State.CONNECTED
            OCastLog.info { "Connected to $friendlyName" }
            connectCallback?.onSuccess?.wrapRun()
            connectCallback = null
        }
    }

    override fun onDataReceived(webSocket: WebSocket, data: String) {
        var deviceLayer: OCastRawDeviceLayer? = null
        var replyCallback: ReplyCallback<*>? = null
        try {
            deviceLayer = JsonTools.decode(data)
            when (deviceLayer.type) {
                OCastRawDeviceLayer.Type.EVENT -> analyzeEvent(deviceLayer)
                OCastRawDeviceLayer.Type.REPLY -> {
                    replyCallback = replyCallbacksBySequenceID[deviceLayer.identifier]
                    replyCallback.ifNotNull {
                        if (deviceLayer.status == OCastRawDeviceLayer.Status.OK) {
                            val replyData = JsonTools.decode<OCastDataLayer<OCastReplyEventParams>>(deviceLayer.message.data)
                            if (replyData.params.code == null || replyData.params.code == OCastError.Status.SUCCESS.code) {
                                val oCastData = JsonTools.decode<OCastRawDataLayer>(deviceLayer.message.data)
                                val reply = if (it.replyClass != Unit::class.java) {
                                    JsonTools.decode(oCastData.params, it.replyClass)
                                } else {
                                    Unit
                                }
                                OCastLog.info { "Received reply of type ${it.replyClass.name} from $friendlyName:\n${deviceLayer.message.data.prependIndent()}" }
                                @Suppress("UNCHECKED_CAST")
                                (it as ReplyCallback<Any?>).onSuccess.wrapRun(reply)
                            } else {
                                it.onError.wrapRun(OCastError(replyData.params.code, "Received reply with params error code ${replyData.params.code} from $friendlyName").log())
                            }
                        } else {
                            it.onError.wrapRun(OCastError(OCastError.Status.DEVICE_LAYER_ERROR.code, "Received reply with device layer error status ${deviceLayer.status} from $friendlyName").log())
                        }
                    }
                }
                OCastRawDeviceLayer.Type.COMMAND -> {}
            }
        } catch (e: Exception) {
            replyCallback?.onError?.wrapRun(OCastError(OCastError.Status.DECODE_ERROR.code, "Received bad formatted message from $friendlyName:\n${data.prependIndent()}").log())
        } finally {
            deviceLayer.ifNotNull {
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
            Service.APPLICATION -> {
                val webAppConnectedStatus = JsonTools.decode<WebAppConnectedStatusEvent>(oCastData.params).status
                OCastLog.info { "Received web app connected status event from $friendlyName:\n${deviceLayer.message.data.prependIndent()}" }
                when (webAppConnectedStatus) {
                    WebAppStatus.CONNECTED -> {
                        isApplicationRunning.set(true)
                        applicationSemaphore?.release()
                    }
                    WebAppStatus.DISCONNECTED -> isApplicationRunning.set(false)
                }
            }
            Service.MEDIA -> {
                when (oCastData.name) {
                    Event.Media.PLAYBACK_STATUS -> {
                        OCastLog.info { "Received media playback status event from $friendlyName:\n${deviceLayer.message.data.prependIndent()}" }
                        val playbackStatus = JsonTools.decode<MediaPlaybackStatus>(oCastData.params)
                        eventListener?.onMediaPlaybackStatus(this, playbackStatus)
                    }
                    Event.Media.METADATA_CHANGED -> {
                        OCastLog.info { "Received media metadata changed event from $friendlyName:\n${deviceLayer.message.data.prependIndent()}" }
                        val metadataChanged = JsonTools.decode<MediaMetadata>(oCastData.params)
                        eventListener?.onMediaMetadataChanged(this, metadataChanged)
                    }
                }
            }
            SettingsService.DEVICE -> {
                when (oCastData.name) {
                    Event.Device.UPDATE_STATUS -> {
                        OCastLog.info { "Received update status event from $friendlyName:\n${deviceLayer.message.data.prependIndent()}" }
                        val updateStatus = JsonTools.decode<UpdateStatus>(oCastData.params)
                        eventListener?.onUpdateStatus(this, updateStatus)
                    }
                }
            }
            else -> {
                // Custom event
                OCastLog.info { "Received custom event from $friendlyName:\n${deviceLayer.message.data.prependIndent()}" }
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
            onError.wrapRun(OCastError("Failed to send command with params ${message.data.params} to $friendlyName, unable to encode device layer", exception).log())
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
            if (webSocketsById[REFERENCE_WEB_SOCKET_ID]?.send(message) == false) {
                replyCallbacksBySequenceID.remove(id)
                onError.wrapRun(OCastError("Failed to send command with ID $id to $friendlyName:\n${message.prependIndent()}").log())
            } else {
                OCastLog.info { "Sent command with ID $id to $friendlyName:\n${message.prependIndent()}" }
            }
        }

        if (!startApplicationIfNeeded || isApplicationRunning.get()) {
            send()
        } else {
            startApplication({
                send()
            }, { error ->
                onError.wrapRun(error.log())
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

    /** Returns the identifier of a web socket, or `null` if it could not be found. */
    private val WebSocket.id: String?
        get() = webSocketsById
            .entries
            .firstOrNull { it.value == this }
            ?.key
}
