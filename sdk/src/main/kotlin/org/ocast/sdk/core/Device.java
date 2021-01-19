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

package org.ocast.sdk.core;

import java.io.Serializable;
import java.net.URL;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.ocast.sdk.core.models.Consumer;
import org.ocast.sdk.core.models.MediaMetadata;
import org.ocast.sdk.core.models.MediaPlaybackStatus;
import org.ocast.sdk.core.models.OCastApplicationLayer;
import org.ocast.sdk.core.models.OCastDeviceSettingsError;
import org.ocast.sdk.core.models.OCastDomain;
import org.ocast.sdk.core.models.OCastError;
import org.ocast.sdk.core.models.OCastInputSettingsError;
import org.ocast.sdk.core.models.OCastMediaError;
import org.ocast.sdk.core.models.PrepareMediaCommandParams;
import org.ocast.sdk.core.models.SSLConfiguration;
import org.ocast.sdk.core.models.SendGamepadEventCommandParams;
import org.ocast.sdk.core.models.SendKeyEventCommandParams;
import org.ocast.sdk.core.models.SendMouseEventCommandParams;
import org.ocast.sdk.core.models.SetMediaTrackCommandParams;
import org.ocast.sdk.core.models.UpdateStatus;
import org.ocast.sdk.core.models.Volume;
import org.ocast.sdk.core.wrapper.CallbackWrapper;
import org.ocast.sdk.core.wrapper.CallbackWrapperOwner;
import org.ocast.sdk.core.wrapper.SimpleCallbackWrapper;
import org.ocast.sdk.discovery.models.UpnpDevice;

/**
 * Represents a remote OCast device.
 */
public abstract class Device implements CallbackWrapperOwner, Serializable {

    private static final long serialVersionUID = 1;

    /**
     * Represents the state of an OCast device.
     */
    public enum State {

        /** The device is being connected. */
        CONNECTING,

        /** The device is connected. */
        CONNECTED,

        /** The device is being disconnected. */
        DISCONNECTING,

        /** The device is disconnected. */
        DISCONNECTED
    }

    /** The underlying UPnP device. */
    @NotNull private UpnpDevice upnpDevice;

    /** The current state of the device. */
    @NotNull private State state = State.DISCONNECTED;

    /** The name of the web application to use. */
    @Nullable private String applicationName;

    /** The device listener. */
    @Nullable private DeviceListener deviceListener;

    /** The event listener. */
    @Nullable private EventListener eventListener;

    /** The callback wrapper. */
    @NotNull private CallbackWrapper callbackWrapper = new SimpleCallbackWrapper();

    /**
     * Creates an OCast device.
     *
     * @param upnpDevice An UPnP device to create this device from.
     */
    Device(@NotNull UpnpDevice upnpDevice) {
        this.upnpDevice = upnpDevice;
    }

    //region Getters and setters

    /**
     * Returns the underlying associated UPnP device.
     *
     * <p>UPnP devices can be retrieved with an UPnP device description request.</p>
     *
     * @return The UPnP device.
     */
    @NotNull
    UpnpDevice getUpnpDevice() {
        return upnpDevice;
    }

    /**
     * Changes the underlying UPnP device.
     *
     * <p>UPnP devices can be retrieved with an UPnP device description request.</p>
     *
     * @param upnpDevice The new UPnP device.
     */
    void setUpnpDevice(@NotNull UpnpDevice upnpDevice) {
        this.upnpDevice = upnpDevice;
    }

    /**
     * Returns the UPnP identifier of the device.
     *
     * @return The UPnP identifier.
     */
    @NotNull
    public String getUpnpID() {
        return upnpDevice.getId();
    }

    /**
     * Returns the URL of the DIAL service.
     *
     * <p>This URL is used to start or stop web applications on the device.</p>
     *
     * @return The DIAL service URL.
     */
    @NotNull
    protected URL getDialURL() {
        return upnpDevice.getDialURL();
    }

    /**
     * Returns the device host.
     *
     * @return The device host. This is usually an IP address.
     */
    @NotNull
    public String getHost() {
        return getDialURL().getHost();
    }

    /**
     *  Returns the friendly name of the device.
     *
     * @return The friendly name.
     */
    @NotNull
    public String getFriendlyName() {
        return upnpDevice.getFriendlyName();
    }

    /**
     * Returns the model name of the device.
     *
     * @return The model name.
     */
    @NotNull
    public String getModelName() {
        return upnpDevice.getModelName();
    }

    /**
     * Returns the current state of the device.
     *
     * @return The current state.
     */
    @NotNull
    public State getState() {
        return state;
    }

    /**
     * Changes the current state of the device.
     *
     * @param state The new state.
     */
    protected void setState(@NotNull State state) {
        this.state = state;
    }

    /**
     * Returns the name of the web application to use.
     *
     * @return The application name.
     */
    @Nullable
    public String getApplicationName() {
        return applicationName;
    }

    /**
     * Changes the name of the web application to use.
     *
     * <p>You MUST set the application name before calling `startApplication(@NotNull Runnable onSuccess, @NotNull Consumer&lt;OCastError&gt; onError)`
     * or any media command.</p>
     *
     * @param applicationName The new application name.
     */
    public void setApplicationName(@Nullable String applicationName) {
        this.applicationName = applicationName;
    }

    /**
     * Returns the device listener.
     *
     * @return The listener.
     */
    @Nullable
    protected DeviceListener getDeviceListener() {
        return deviceListener;
    }

    /**
     * Changes the device listener.
     *
     * @param deviceListener The new device listener.
     */
    protected void setDeviceListener(@Nullable DeviceListener deviceListener) {
        this.deviceListener = deviceListener;
    }

    /**
     * Returns the event listener.
     *
     * @return The listener.
     */
    @Nullable
    protected EventListener getEventListener() {
        return eventListener;
    }

    /**
     * Changes the event listener.
     *
     * @param eventListener The new event listener.
     */
    protected void setEventListener(@Nullable EventListener eventListener) {
        this.eventListener = eventListener;
    }

    /**
     * Returns the callback wrapper.
     *
     * @return The callback wrapper.
     */
    @NotNull
    public CallbackWrapper getCallbackWrapper() {
        return callbackWrapper;
    }

    /**
     * Changes the callback wrapper.
     *
     * @param callbackWrapper The new callback wrapper.
     */
    public void setCallbackWrapper(@NotNull CallbackWrapper callbackWrapper) {
        this.callbackWrapper = callbackWrapper;
    }

    /**
     * Returns the SSDP search target.
     *
     * @return The search target. This value is used to discover the device on the network.
     */
    public abstract @NotNull String getSearchTarget();

    /**
     * Returns the manufacturer of the device.
     *
     * @return The manufacturer.
     */
    public abstract @NotNull String getManufacturer();

    //endregion

    //region Connection

    /**
     * Connects to the device.
     *
     * @param sslConfiguration The SSL configuration of the web socket used to connect to the device if it is secure, or `null` if the web socket is not secure.
     * @param onSuccess The operation executed if the connection succeeded.
     * @param onError The operation executed if the connection failed.
     */
    public abstract void connect(@Nullable SSLConfiguration sslConfiguration, @NotNull Runnable onSuccess, @NotNull Consumer<OCastError> onError);

    /**
     * Disconnects from the device.
     *
     * @param onSuccess The operation executed if the disconnection succeeded.
     * @param onError The operation executed if the disconnection failed.
     */
    public abstract void disconnect(@NotNull Runnable onSuccess, @NotNull Consumer<OCastError> onError);

    //endregion

    //region Application

    /**
     * Starts the application identified by the return value of `getApplicationName()`.
     *
     * @param onSuccess The operation executed if the application started successfully.
     * @param onError The operation executed if there was an error while starting the application.
     */
    public abstract void startApplication(@NotNull Runnable onSuccess, @NotNull Consumer<OCastError> onError);

    /**
     * Stops the application identified by the return value of `getApplicationName()`.
     *
     * @param onSuccess The operation executed if the application stopped successfully.
     * @param onError The operation executed if there was an error while stopping the application.
     */
    public abstract void stopApplication(@NotNull Runnable onSuccess, @NotNull Consumer<OCastError> onError);

    //endregion

    //region Media commands

    /**
     * Plays a media at the specified position.
     *
     * @param position The position to play the media at, in seconds.
     * @param onSuccess The operation executed if the media played successfully.
     * @param onError The operation executed if there was an error while playing the media.
     */
    public abstract void playMedia(double position, @NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);

    /**
     * Stops the current media.
     *
     * @param onSuccess The operation executed if the media stopped successfully.
     * @param onError The operation executed if there was an error while stopping the media.
     */
    public abstract void stopMedia(@NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);

    /**
     * Pauses the current media.
     *
     * @param onSuccess The operation executed if the media paused successfully.
     * @param onError The operation executed if there was an error while pausing the media.
     */
    public abstract void pauseMedia(@NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);

    /**
     * Resumes the current media.
     *
     * @param onSuccess The operation executed if the media resumed successfully.
     * @param onError The operation executed if there was an error while resuming the media.
     */
    public abstract void resumeMedia(@NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);

    /**
     * Prepares a media to be played.
     *
     * @param params The parameters of the `prepare` command.
     * @param options The options of the `prepare` command.
     * @param onSuccess The operation executed if the media was prepared successfully.
     * @param onError The operation executed if there was an error while preparing the media.
     */
    public abstract void prepareMedia(@NotNull PrepareMediaCommandParams params, @Nullable JSONObject options, @NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);

    /**
     * Changes the media volume.
     *
     * @param volume The volume, ranging from 0.0 to 1.0.
     * @param onSuccess The operation executed if the volume was changed successfully.
     * @param onError The operation executed if there was an error while changing the volume.
     */
    public abstract void setMediaVolume(double volume, @NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);

    /**
     * Changes the current track of the current media.
     *
     * @param params The parameters of the `setMediaTrack` command.
     * @param onSuccess The operation executed if the track was changed successfully.
     * @param onError The operation executed if there was an error while changing the track.
     */
    public abstract void setMediaTrack(@NotNull SetMediaTrackCommandParams params, @NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);

    /**
     * Seeks the current media to the specified position.
     *
     * @param position The position to seek the media to, in seconds.
     * @param onSuccess The operation executed if the media position was changed successfully.
     * @param onError The operation executed if there was an error while seeking the media to the specified position.
     */
    public abstract void seekMedia(double position, @NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);

    /**
     * Mutes or unmutes the current media.
     *
     * @param mute Indicates if the media should be muted or not.
     * @param onSuccess The operation executed if the media was muted or unmuted successfully.
     * @param onError The operation executed if there was an error while muting or unmuting the media.
     */
    public abstract void muteMedia(boolean mute, @NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);

    /**
     * Retrieves the playback status of the current media.
     *
     * @param onSuccess The operation executed if the playback status was retrieved successfully.
     * @param onError The operation executed if there was an error while retrieving the playback status.
     */
    public abstract void getMediaPlaybackStatus(@NotNull Consumer<MediaPlaybackStatus> onSuccess, @NotNull Consumer<OCastMediaError> onError);

    /**
     * Retrieves the metadata of the current media.
     *
     * @param onSuccess The operation executed if the metadata was retrieved successfully.
     * @param onError The operation executed if there was an error while retrieving the metadata.
     */
    public abstract void getMediaMetadata(@NotNull Consumer<MediaMetadata> onSuccess, @NotNull Consumer<OCastMediaError> onError);

    //endregion

    //region Settings device commands

    /**
     * Retrieves the firmware update status of the device.
     *
     * @param onSuccess The operation executed if the update status was retrieved successfully.
     * @param onError The operation executed if there was an error while retrieving the update status.
     */
    public abstract void getUpdateStatus(@NotNull Consumer<UpdateStatus> onSuccess, @NotNull Consumer<OCastDeviceSettingsError> onError);

    /**
     * Retrieves the device identifier.
     *
     * @param onSuccess The operation executed if the device identifier was retrieved successfully.
     * @param onError The operation executed if there was an error while retrieving the device identifier.
     */
    public abstract void getDeviceID(@NotNull Consumer<String> onSuccess, @NotNull Consumer<OCastDeviceSettingsError> onError);

    /**
     * Retrieves the system volume.
     *
     * @param onSuccess The operation executed if the system volume was retrieved successfully.
     * @param onError The operation executed if there was an error while retrieving the system volume.
     */
    public abstract void getVolume(@NotNull Consumer<Volume> onSuccess, @NotNull Consumer<OCastDeviceSettingsError> onError);

    /**
     * Set the system volume.
     *
     * @param level The parameters of the system volume between 0 to 100.
     * @param mute The parameters of the mute state.
     * @param onSuccess The operation executed if the system volume was sent successfully.
     * @param onError The operation executed if there was an error while sending the system volume.
     */
    public abstract void setVolume(int level, boolean mute, @NotNull Runnable onSuccess, @NotNull Consumer<OCastDeviceSettingsError> onError);

    //endregion

    //region Settings input commands

    /**
     * Sends a keyboard event.
     *
     * @param params The parameters of the `sendKeyEvent` command.
     * @param onSuccess The operation executed if the keyboard event was sent successfully.
     * @param onError The operation executed if there was an error while sending the keyboard event.
     */
    public abstract void sendKeyEvent(@NotNull SendKeyEventCommandParams params, @NotNull Runnable onSuccess, @NotNull Consumer<OCastInputSettingsError> onError);

    /**
     * Sends a mouse event.
     *
     * @param params The parameters of the `sendMouseEvent` command.
     * @param onSuccess The operation executed if the mouse event was sent successfully.
     * @param onError The operation executed if there was an error while sending the mouse event.
     */
    public abstract void sendMouseEvent(@NotNull SendMouseEventCommandParams params, @NotNull Runnable onSuccess, @NotNull Consumer<OCastInputSettingsError> onError);

    /**
     * Sends a gamepad event.
     *
     * @param params The parameters of the `sendGamepadEvent` command.
     * @param onSuccess The operation executed if the gamepad event was sent successfully.
     * @param onError The operation executed if there was an error while sending the gamepad event.
     */
    public abstract void sendGamepadEvent(@NotNull SendGamepadEventCommandParams params, @NotNull Runnable onSuccess, @NotNull Consumer<OCastInputSettingsError> onError);

    //endregion

    //region Custom commands

    /**
     * Sends a message which does not expect any reply data to the specified OCast domain.
     *
     * @param message The message to send.
     * @param domain The OCast domain to send the message to.
     * @param onSuccess The operation executed if the message was sent successfully.
     * @param onError The operation executed if there was an error while sending the message.
     * @param <T> The type of the parameters contained in the data layer of the message.
     */
    public abstract <T> void send(@NotNull OCastApplicationLayer<T> message, @NotNull OCastDomain domain, @NotNull Runnable onSuccess, @NotNull Consumer<OCastError> onError);

    /**
     * Sends a message which expects a reply data to the specified OCast domain.
     *
     * @param message The message to send.
     * @param domain The OCast domain to send the message to.
     * @param replyClass The class of the reply data.
     * @param onSuccess The operation executed if the message was sent successfully.
     * @param onError The operation executed if there was an error while sending the message.
     * @param <T> The type of the parameters contained in the data layer of the message.
     * @param <S> The type of the parameters contained in the data layer of the reply.
     */
    public abstract <T, S> void send(@NotNull OCastApplicationLayer<T> message, @NotNull OCastDomain domain, @NotNull Class<S> replyClass, @NotNull Consumer<S> onSuccess, @NotNull Consumer<OCastError> onError);

    //endregion
}
