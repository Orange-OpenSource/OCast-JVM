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
import org.ocast.sdk.core.wrapper.CallbackWrapper;
import org.ocast.sdk.core.wrapper.CallbackWrapperOwner;
import org.ocast.sdk.core.wrapper.SimpleCallbackWrapper;
import org.ocast.sdk.discovery.models.UpnpDevice;

public abstract class Device implements CallbackWrapperOwner, Serializable {

    public enum State {

        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED
    }

    @NotNull private UpnpDevice upnpDevice;
    @NotNull private State state = State.DISCONNECTED;
    @Nullable private String applicationName;
    @Nullable private DeviceListener deviceListener;
    @Nullable private EventListener eventListener;
    @NotNull private CallbackWrapper callbackWrapper = new SimpleCallbackWrapper();

    Device(@NotNull UpnpDevice upnpDevice) {
        this.upnpDevice = upnpDevice;
    }

    // Device configuration

    @NotNull
    UpnpDevice getUpnpDevice() {
        return upnpDevice;
    }

    void setUpnpDevice(@NotNull UpnpDevice upnpDevice) {
        this.upnpDevice = upnpDevice;
    }

    @NotNull
    public String getUpnpID() {
        return upnpDevice.getId();
    }

    @NotNull
    protected URL getDialURL() {
        return upnpDevice.getDialURL();
    }

    @NotNull
    public String getHost() {
        return getDialURL().getHost();
    }

    @NotNull
    public String getFriendlyName() {
        return upnpDevice.getFriendlyName();
    }

    @NotNull
    public String getModelName() {
        return upnpDevice.getModelName();
    }

    @NotNull
    public State getState() {
        return state;
    }

    protected void setState(@NotNull State state) {
        this.state = state;
    }

    @Nullable
    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(@Nullable String applicationName) {
        this.applicationName = applicationName;
    }

    @Nullable
    protected DeviceListener getDeviceListener() {
        return deviceListener;
    }

    protected void setDeviceListener(@Nullable DeviceListener deviceListener) {
        this.deviceListener = deviceListener;
    }

    @Nullable
    protected EventListener getEventListener() {
        return eventListener;
    }

    protected void setEventListener(@Nullable EventListener eventListener) {
        this.eventListener = eventListener;
    }

    @NotNull
    public CallbackWrapper getCallbackWrapper() {
        return callbackWrapper;
    }

    public void setCallbackWrapper(@NotNull CallbackWrapper callbackWrapper) {
        this.callbackWrapper = callbackWrapper;
    }

    public abstract @NotNull String getSearchTarget();

    public abstract @NotNull String getManufacturer();

    // Device commands
    public abstract void connect(@Nullable SSLConfiguration sslConfiguration, @NotNull Runnable onSuccess, @NotNull Consumer<OCastError> onError);

    public abstract void disconnect(@NotNull Runnable onSuccess, @NotNull Consumer<OCastError> onError);

    // Application
    public abstract void startApplication(@NotNull Runnable onSuccess, @NotNull Consumer<OCastError> onError);

    public abstract void stopApplication(@NotNull Runnable onSuccess, @NotNull Consumer<OCastError> onError);

    // Media commands
    public abstract void playMedia(double position, @NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);

    public abstract void stopMedia(@NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);

    public abstract void pauseMedia(@NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);

    public abstract void resumeMedia(@NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);

    public abstract void prepareMedia(@NotNull PrepareMediaCommandParams params, @Nullable JSONObject options, @NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);

    public abstract void setMediaVolume(double volume, @NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);

    public abstract void setMediaTrack(@NotNull SetMediaTrackCommandParams params, @NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);

    public abstract void seekMedia(double position, @NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);

    public abstract void muteMedia(boolean mute, @NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);

    public abstract void getMediaPlaybackStatus(@NotNull Consumer<MediaPlaybackStatus> onSuccess, @NotNull Consumer<OCastMediaError> onError);

    public abstract void getMediaMetadata(@NotNull Consumer<MediaMetadata> onSuccess, @NotNull Consumer<OCastMediaError> onError);

    // Settings device commands
    public abstract void getUpdateStatus(@NotNull Consumer<UpdateStatus> onSuccess, @NotNull Consumer<OCastDeviceSettingsError> onError);

    public abstract void getDeviceID(@NotNull Consumer<String> onSuccess, @NotNull Consumer<OCastDeviceSettingsError> onError);

    // Settings input commands
    public abstract void sendKeyEvent(@NotNull SendKeyEventCommandParams params, @NotNull Runnable onSuccess, @NotNull Consumer<OCastInputSettingsError> onError);

    public abstract void sendMouseEvent(@NotNull SendMouseEventCommandParams params, @NotNull Runnable onSuccess, @NotNull Consumer<OCastInputSettingsError> onError);

    public abstract void sendGamepadEvent(@NotNull SendGamepadEventCommandParams params, @NotNull Runnable onSuccess, @NotNull Consumer<OCastInputSettingsError> onError);

    // Custom commands
    public abstract <T> void send(@NotNull OCastApplicationLayer<T> message, @NotNull OCastDomain domain, @NotNull Runnable onSuccess, @NotNull Consumer<OCastError> onError);

    public abstract <T, S> void send(@NotNull OCastApplicationLayer<T> message, @NotNull OCastDomain domain, @NotNull Class<S> replyClass, @NotNull Consumer<S> onSuccess, @NotNull Consumer<OCastError> onError);
}
