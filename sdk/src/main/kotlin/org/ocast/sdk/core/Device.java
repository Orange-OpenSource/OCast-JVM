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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.ocast.sdk.core.wrapper.CallbackWrapper;
import org.ocast.sdk.core.wrapper.CallbackWrapperOwner;
import org.ocast.sdk.core.models.*;
import org.ocast.sdk.core.wrapper.SimpleCallbackWrapper;
import org.ocast.sdk.discovery.models.UpnpDevice;

import java.net.URL;

public abstract class Device implements CallbackWrapperOwner {

    public enum State {
        
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED
    }

    @NotNull private final UpnpDevice upnpDevice;
    @NotNull private State state = State.DISCONNECTED;
    @Nullable private String applicationName;
    @Nullable private DeviceListener deviceListener;
    @Nullable private EventListener eventListener;
    @NotNull private CallbackWrapper callbackWrapper = new SimpleCallbackWrapper();
    @Nullable protected SSLConfiguration sslConfiguration;

    Device(@NotNull UpnpDevice upnpDevice) {
        this.upnpDevice = upnpDevice;
    }

    // Device configuration

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
    DeviceListener getDeviceListener() {
        return deviceListener;
    }

    void setDeviceListener(@Nullable DeviceListener deviceListener) {
        this.deviceListener = deviceListener;
    }

    @Nullable
    EventListener getEventListener() {
        return eventListener;
    }

    void setEventListener(@Nullable EventListener eventListener) {
        this.eventListener = eventListener;
    }

    @NotNull
    public CallbackWrapper getCallbackWrapper() {
        return callbackWrapper;
    }

    public void setCallbackWrapper(@NotNull CallbackWrapper callbackWrapper) {
        this.callbackWrapper = callbackWrapper;
    }

    @Nullable
    SSLConfiguration getSSLConfiguration() {
        return sslConfiguration;
    }

    public void setSSLConfiguration(@Nullable SSLConfiguration sslConfiguration) {
        this.sslConfiguration = sslConfiguration;
    }

    public abstract @NotNull String getSearchTarget();
    public abstract @NotNull String getManufacturer();

    // Device commands
    public abstract void connect(@NotNull Runnable onSuccess, @NotNull Consumer<OCastError> onError);
    public abstract void disconnect(@NotNull Runnable onSuccess, @NotNull Consumer<OCastError> onError);

    // Application
    public abstract void startApplication(@NotNull Runnable onSuccess, @NotNull Consumer<OCastError> onError);
    public abstract void stopApplication(@NotNull Runnable onSuccess, @NotNull Consumer<OCastError> onError);

    // Media commands
    public abstract void playMedia(double position, @NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);
    public abstract void stopMedia(@NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);
    public abstract void pauseMedia(@NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);
    public abstract void resumeMedia(@NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);
    public abstract void prepareMedia(@NotNull MediaPrepareCommandParams params, @Nullable JSONObject options, @NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);
    public abstract void setMediaVolume(double volume, @NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);
    public abstract void setMediaTrack(@NotNull MediaTrackCommandParams params, @NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);
    public abstract void seekMedia(double position, @NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);
    public abstract void muteMedia(boolean mute, @NotNull Runnable onSuccess, @NotNull Consumer<OCastMediaError> onError);
    public abstract void getMediaPlaybackStatus(@NotNull Consumer<MediaPlaybackStatus> onSuccess, @NotNull Consumer<OCastMediaError> onError);
    public abstract void getMediaMetadata(@NotNull Consumer<MediaMetadata> onSuccess, @NotNull Consumer<OCastMediaError> onError);

    // Settings device commands
    public abstract void getUpdateStatus(@NotNull Consumer<UpdateStatus> onSuccess, @NotNull Consumer<OCastDeviceSettingsError> onError);
    public abstract void getDeviceID(@NotNull Consumer<String> onSuccess, @NotNull Consumer<OCastDeviceSettingsError> onError);

    // Settings input commands
    public abstract void sendKeyEvent(@NotNull KeyEventCommandParams params, @NotNull Runnable onSuccess, @NotNull Consumer<OCastInputSettingsError> onError);
    public abstract void sendMouseEvent(@NotNull MouseEventCommandParams params, @NotNull Runnable onSuccess, @NotNull Consumer<OCastInputSettingsError> onError);
    public abstract void sendGamepadEvent(@NotNull GamepadEventCommandParams params, @NotNull Runnable onSuccess, @NotNull Consumer<OCastInputSettingsError> onError);

    // Custom commands
    public abstract <T> void send(@NotNull OCastApplicationLayer<T> message, @NotNull OCastDomain domain, @NotNull Runnable onSuccess, @NotNull Consumer<OCastError> onError);
    public abstract <T, S> void send(@NotNull OCastApplicationLayer<T> message, @NotNull OCastDomain domain, @NotNull Class<S> replyClass, @NotNull Consumer<S> onSuccess, @NotNull Consumer<OCastError> onError);
}
