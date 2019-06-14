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

package org.ocast.core

import org.json.JSONObject
import org.ocast.core.models.* // ktlint-disable no-wildcard-imports
import org.ocast.core.utils.OCastLog
import org.ocast.discovery.models.UpnpDevice
import java.lang.Exception
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

open class ReferenceDevice(upnpDevice: UpnpDevice) : Device(upnpDevice), WebSocketProvider.Listener {

    companion object {
        internal const val SERVICE_SETTINGS_DEVICE = "org.ocast.settings.device"
        internal const val SERVICE_SETTINGS_INPUT = "org.ocast.settings.input"
        internal const val SERVICE_MEDIA = "org.ocast.media"

        internal const val SERVICE_APPLICATION = "org.ocast.webapp"

        const val DOMAIN_BROWSER = "browser"
        internal const val DOMAIN_SETTINGS = "settings"

        private const val EVENT_MEDIA_PLAYBACK_STATUS = "playbackStatus"
        private const val EVENT_MEDIA_METADATA_CHANGED = "metadataChanged"
        private const val EVENT_DEVICE_UPDATE_STATUS = "updateStatus"
    }

    protected enum class State {
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED
    }

    override fun getSearchTarget() = "urn:cast-ocast-org:service:cast:1"
    override fun getManufacturer() = "Orange SA"

    override fun setApplicationName(applicationName: String?) {
        // Reset state if application is modified
        if (this.applicationName != applicationName) {
            isApplicationRunning = false
        }
        super.setApplicationName(applicationName)
    }

    protected var state = State.DISCONNECTED
    private val sequenceID: AtomicLong = AtomicLong(0)
    protected var clientUuid = UUID.randomUUID().toString()
    protected var isApplicationRunning = false
    protected var webSocket: WebSocketProvider? = null
    protected var onConnected: Runnable? = null
    private val webSocketURL: String
        get() {
            val protocol = if (sslConfiguration != null) "wss" else "ws"
            val port = if (sslConfiguration != null) "4433" else "4434"
            return "$protocol://${applicationURL.host}:$port/ocast"
        }

    protected val replyCallbacksBySequenceID: MutableMap<Long, ReplyCallback<*>> = Collections.synchronizedMap(mutableMapOf())

    //region RemoteDevice

    override fun startApplication(onSuccess: Runnable, onError: Consumer<OCastError>) {
        if (applicationName == null) {
            onError.wrapRun(OCastError("Property applicationName is not defined"))
            return
        }

        when (state) {
            State.CONNECTING -> onError.wrapRun(OCastError("Device is connecting, start cannot be processed yet"))
            State.CONNECTED -> startDialApp(onSuccess, onError)
            State.DISCONNECTING -> onError.wrapRun(OCastError("Device is connecting, start cannot be processed."))
            State.DISCONNECTED -> {
                connect({
                    startDialApp(onSuccess, onError)
                }, {
                    onError.wrapRun(it)
                })
            }
        }
    }

    private fun startDialApp(onSuccess: Runnable, onError: Consumer<OCastError>) {
        // TODO a faire et set isApplicationRunning sur success

        isApplicationRunning = true
    }

    override fun stopApplication(onSuccess: Runnable, onError: Consumer<OCastError>) {
        if (applicationName == null) {
            onError.wrapRun(OCastError("Property applicationName is not defined"))
            return
        }
        isApplicationRunning = false

        // TODO a faire -> Stop DIAL APP ?
    }

    override fun connect(onSuccess: Runnable, onError: Consumer<OCastError>) {
        when (state) {
            State.CONNECTING -> onError.wrapRun(OCastError("Device is already connecting"))
            State.CONNECTED -> onSuccess.wrapRun()
            State.DISCONNECTING -> onError.wrapRun(OCastError("Device is disconnecting"))
            State.DISCONNECTED -> {
                try {
                    webSocket = WebSocketProvider(webSocketURL, sslConfiguration, this)
                    state = State.CONNECTING
                    onConnected = onSuccess
                    webSocket?.connect()
                } catch (ex: Exception) {
                    onError.wrapRun(OCastError("Unable to init SocketProvider"))
                }
            }
        }
    }

    override fun disconnect() {
        if (state != State.DISCONNECTING && state != State.DISCONNECTED) {
            state = State.DISCONNECTING
            onConnected = null
            webSocket?.disconnect()
        }
    }

    //endregion

    //region SocketProviderListener

    override fun onDisconnected(webSocketProvider: WebSocketProvider, error: Throwable?) {
        if (state != State.DISCONNECTED) {
            state = State.DISCONNECTED
            // Send error callback to all waiting commands
            synchronized(replyCallbacksBySequenceID) {
                replyCallbacksBySequenceID.forEach { (_, callback) ->
                    callback.wrapOnError(OCastError("Socket has been disconnected"))
                }
                replyCallbacksBySequenceID.clear()
            }
            onConnected = null
            deviceListener?.onDeviceDisconnected(this, error)
        }
    }

    override fun onConnected(webSocketProvider: WebSocketProvider, url: String) {
        state = State.CONNECTED
        onConnected?.wrapRun()
        onConnected = null
    }

    override fun onDataReceived(webSocketProvider: WebSocketProvider, data: String) {
        try {
            val deviceLayer = OCastRawDeviceLayer.decode(data)
            when (deviceLayer.type) {
                OCastRawDeviceLayer.Type.EVENT -> analyzeEvent(deviceLayer)
                OCastRawDeviceLayer.Type.REPLY -> {
                    val event = replyCallbacksBySequenceID[deviceLayer.identifier]
                    event?.let {
                        if (deviceLayer.status == OCastRawDeviceLayer.Status.OK) {
                            val replyData = OCastReplyDataLayer.decode(deviceLayer.message.data)
                            if (replyData.params.code == OCastError.Status.SUCCESS.code) {
                                val oCastData = OCastRawDataLayer.decode(deviceLayer.message.data)
                                val reply = if (event.replyClass != Unit::class.java) {
                                    OCastDataLayer.decode(oCastData.params, event.replyClass)
                                } else {
                                    Unit
                                }
                                event.wrapOnSuccess(reply)
                            } else {
                                event.wrapOnError(OCastError(replyData.params.code, "Command error code ${replyData.params.code}"))
                            }
                        } else {
                            event.wrapOnError(OCastError("Unknown status value ${deviceLayer.status}"))
                        }
                    }
                }
                OCastRawDeviceLayer.Type.COMMAND -> {}
            }
            // Remove callback
            replyCallbacksBySequenceID.remove(deviceLayer.identifier)
        } catch (e: Exception) {
            OCastLog.error("Receive a bad formatted message: $data", e)
        }
    }

    //endregion

    //region Events

    @Throws(Exception::class)
    private fun analyzeEvent(deviceLayer: OCastRawDeviceLayer) {
        val oCastData = OCastRawDataLayer.decode(deviceLayer.message.data)
        when (deviceLayer.message.service) {
            SERVICE_APPLICATION -> {
                when (OCastDataLayer.decode<WebAppConnectedStatus>(oCastData.params).status) {
                    WebAppStatus.CONNECTED -> {
                        // TODO a finir
                    }
                    WebAppStatus.DISCONNECTED -> isApplicationRunning = false
                    else -> {}
                }
            }
            SERVICE_MEDIA -> {
                when (oCastData.name) {
                    EVENT_MEDIA_PLAYBACK_STATUS -> {
                        val playbackStatus = OCastDataLayer.decode<PlaybackStatusEvent>(oCastData.params)
                        eventListener?.onPlaybackStatus(this, playbackStatus)
                    }
                    EVENT_MEDIA_METADATA_CHANGED -> {
                        val metadataChanged = OCastDataLayer.decode<MetadataChangedEvent>(oCastData.params)
                        eventListener?.onMetadataChanged(this, metadataChanged)
                    }
                }
            }
            SERVICE_SETTINGS_DEVICE -> {
                when (oCastData.name) {
                    EVENT_DEVICE_UPDATE_STATUS -> {
                        val updateStatus = OCastDataLayer.decode<UpdateStatusEvent>(oCastData.params)
                        eventListener?.onUpdateStatus(this, updateStatus)
                    }
                }
            }
            else -> {
                // Custom event
                val params = OCastDataLayer.decode<JSONObject>(oCastData.params)
                val customEvent = CustomEvent(oCastData.name, params)
                eventListener?.onCustomEvent(this, customEvent)
            }
        }
    }

    //endregion

    //region Media commands

    override fun playMedia(position: Double, onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        sendCommand(DOMAIN_BROWSER, MediaMessage(Play(position).build()), onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun stopMedia(onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        sendCommand(DOMAIN_BROWSER, MediaMessage(Stop().build()), onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun pauseMedia(onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        sendCommand(DOMAIN_BROWSER, MediaMessage(Pause().build()), onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun resumeMedia(onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        sendCommand(DOMAIN_BROWSER, MediaMessage(Resume().build()), onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun prepareMedia(url: String, updateFrequency: Int, title: String, subtitle: String?, logo: String?, mediaType: Media.Type, transferMode: Media.TransferMode, autoplay: Boolean, options: JSONObject?, onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        sendCommand(DOMAIN_BROWSER, MediaMessage(Prepare(url, updateFrequency, title, subtitle, logo, mediaType, transferMode, autoplay).options(options).build()), onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun setMediaVolume(volume: Double, onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        sendCommand(DOMAIN_BROWSER, MediaMessage(Volume(volume).build()), onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun setMediaTrack(type: Track.Type, trackID: String, enabled: Boolean, onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        sendCommand(DOMAIN_BROWSER, MediaMessage(Track(type, trackID, enabled).build()), onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun seekMediaTo(position: Double, onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        sendCommand(DOMAIN_BROWSER, MediaMessage(Seek(position).build()), onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun muteMedia(mute: Boolean, onSuccess: Runnable, onError: Consumer<OCastMediaError>) {
        sendCommand(DOMAIN_BROWSER, MediaMessage(Mute(mute).build()), onSuccess, Consumer { onError.run(OCastMediaError(it)) })
    }

    override fun getMediaPlaybackStatus(onSuccess: Consumer<PlaybackStatus>, onError: Consumer<OCastMediaError>) {
        sendCommand(DOMAIN_BROWSER, MediaMessage(GetPlaybackStatus().build()), onSuccess, Consumer { onError.run(OCastMediaError(it)) }, PlaybackStatus::class.java)
    }

    override fun getMediaMetadata(onSuccess: Consumer<Metadata>, onError: Consumer<OCastMediaError>) {
        sendCommand(DOMAIN_BROWSER, MediaMessage(GetMetadata().build()), onSuccess, Consumer { onError.run(OCastMediaError(it)) }, Metadata::class.java)
    }

    //endregion

    //region Custom commands

    override fun sendCustomCommand(name: String, service: String, params: JSONObject, options: JSONObject?, onSuccess: Consumer<CustomReply>, onError: Consumer<OCastError>) {
        sendCommand(DOMAIN_BROWSER, OCastApplicationLayer(service, OCastDataLayerBuilder(name, params, options).build()), onSuccess, onError, CustomReply::class.java)
    }

    override fun sendCustomCommand(name: String, service: String, params: JSONObject, options: JSONObject?, onSuccess: Runnable, onError: Consumer<OCastError>) {
        sendCommand(DOMAIN_BROWSER, OCastApplicationLayer(service, OCastDataLayerBuilder(name, params, options).build()), onSuccess, onError)
    }

    //endregion

    //region Settings device commands

    override fun getUpdateStatus(onSuccess: Consumer<UpdateStatus>, onError: Consumer<OCastDeviceSettingsError>) {
        sendCommand(DOMAIN_SETTINGS, DeviceMessage(GetUpdateStatus().build()), onSuccess, Consumer { onError.run(OCastDeviceSettingsError(it)) }, UpdateStatus::class.java)
    }

    override fun getDeviceID(onSuccess: Consumer<DeviceId>, onError: Consumer<OCastDeviceSettingsError>) {
        sendCommand(DOMAIN_SETTINGS, DeviceMessage(GetDeviceID().build()), onSuccess, Consumer { onError.run(OCastDeviceSettingsError(it)) }, DeviceId::class.java)
    }

    //endregion

    //region Settings input commands

    override fun sendKeyPressed(keyPressed: KeyPressed, onSuccess: Runnable, onError: Consumer<OCastInputSettingsError>) {
        sendCommand(DOMAIN_SETTINGS, InputMessage(keyPressed.build()), onSuccess, Consumer { onError.run(OCastInputSettingsError(it)) })
    }

    override fun sendMouseEvent(mouseEvent: MouseEvent, onSuccess: Runnable, onError: Consumer<OCastInputSettingsError>) {
        sendCommand(DOMAIN_SETTINGS, InputMessage(mouseEvent.build()), onSuccess, Consumer { onError.run(OCastInputSettingsError(it)) })
    }

    override fun sendGamepadEvent(gamepadEvent: GamepadEvent, onSuccess: Runnable, onError: Consumer<OCastInputSettingsError>) {
        sendCommand(DOMAIN_SETTINGS, InputMessage(gamepadEvent.build()), onSuccess, Consumer { onError.run(OCastInputSettingsError(it)) })
    }

    //endregion

    protected fun <T> sendCommand(domain: String, commandMessage: OCastApplicationLayer<T>, onSuccess: Runnable, onError: Consumer<OCastError>) {
        sendCommand(domain, commandMessage, Consumer { onSuccess.run() }, onError, Unit::class.java)
    }

    protected fun <T, U> sendCommand(domain: String, commandMessage: OCastApplicationLayer<T>, onSuccess: Consumer<U>, onError: Consumer<OCastError>, replyClass: Class<U>) {
        val id = generateSequenceID()
        try {
            replyCallbacksBySequenceID[id] = ReplyCallback(replyClass, onSuccess, onError)
            val layerMessage = OCastCommandDeviceLayer(clientUuid, domain, OCastRawDeviceLayer.Type.COMMAND, id, commandMessage).encode()
            sendToWebSocket(id, layerMessage, domain == DOMAIN_BROWSER, onError)
        } catch (e: Exception) {
            replyCallbacksBySequenceID.remove(id)
            onError.wrapRun(OCastError("Unable to get string from data"))
        }
    }

    protected fun sendToWebSocket(id: Long, layerMessage: String, startApplicationIfNeeded: Boolean, onError: Consumer<OCastError>) {
        val send = {
            if (webSocket?.send(layerMessage) == false) {
                replyCallbacksBySequenceID.remove(id)
                onError.wrapRun(OCastError("Unable to send message"))
            }
        }

        // Ne pas lancer la webApp si c'est des settings
        if (!startApplicationIfNeeded || isApplicationRunning) {
            send()
        } else {
            startApplication({
                send()
            }, {
                onError.wrapRun(it)
            })
        }
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <T> ReplyCallback<*>.wrapOnSuccess(params: T) {
        try {
            (this as ReplyCallback<T>).onSuccess.wrapRun(params)
        } catch (ex: Exception) {
            onError.wrapRun(OCastError("Unable to decode reply"))
        }
    }

    protected fun ReplyCallback<*>.wrapOnError(throwable: OCastError) {
        onError.wrapRun(throwable)
    }

    protected fun <T> Consumer<T>.wrapRun(param: T) {
        callbackWrapper.wrap(this).run(param)
    }

    protected fun Runnable.wrapRun() {
        callbackWrapper.wrap(this).run()
    }

    protected fun generateSequenceID(): Long {
        if (sequenceID.get() == Long.MAX_VALUE) {
            sequenceID.set(0)
        }
        return sequenceID.incrementAndGet()
    }
}