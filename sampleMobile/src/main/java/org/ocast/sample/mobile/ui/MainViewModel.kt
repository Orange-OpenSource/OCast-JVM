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

package org.ocast.sample.mobile.ui

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Transformations
import android.arch.lifecycle.ViewModel
import org.ocast.core.Device
import org.ocast.core.models.Media
import org.ocast.core.models.MetadataChangedEvent
import org.ocast.core.models.PlaybackStatusEvent
import org.ocast.core.models.TrackDescription

class MainViewModel : ViewModel() {

    val selectedDevice = MutableLiveData<Device?>()

    val deviceConnected = MutableLiveData<Boolean>().apply { value = false }

    val playbackStatus = MutableLiveData<PlaybackStatusEvent>()
    val mediaDuration: LiveData<Double> = Transformations.map(playbackStatus) { it?.duration }
    val mediaPosition: LiveData<Double> = Transformations.map(playbackStatus) { it?.position }
    val mediaVolumeLevel: LiveData<Double> = Transformations.map(playbackStatus) { it?.volume?.let { volume -> volume * 1000 } }
    val mediaIsMute: LiveData<Boolean> = Transformations.map(playbackStatus) { it?.isMuted }
    val mediaState: LiveData<Media.PlayerState> = Transformations.map(playbackStatus) { it?.state }

    val mediaMetadata = MutableLiveData<MetadataChangedEvent>()
    val mediaAudioTracks = MutableLiveData<List<TrackDescription?>>()
    val mediaAudioTrack = MutableLiveData<TrackDescription?>()
    val mediaAudioTrackPosition = MutableLiveData<Int>()
    val mediaSubtitleTracks = MutableLiveData<List<TrackDescription?>>()
    val mediaSubtitleTrack = MutableLiveData<TrackDescription?>()
    val mediaSubtitleTrackPosition = MutableLiveData<Int>()
    val mediaTitle: LiveData<String> = Transformations.map(mediaMetadata) { it?.title }

    fun onPlayPauseButtonClick() {
        when (mediaState.value) {
            Media.PlayerState.PLAYING -> selectedDevice.value?.pauseMedia({}, {})
            Media.PlayerState.PAUSED -> selectedDevice.value?.resumeMedia({}, {})
            else -> selectedDevice.value?.playMedia(0.0, {}, {})
        }
    }

    fun onStopButtonClick() {
        selectedDevice.value?.stopMedia({}, {})
    }

    fun onMuteCheckedChanged(isChecked: Boolean) {
        selectedDevice.value?.muteMedia(isChecked, {}, {})
    }

    fun onVolumeChanged(progressValue: Int) {
        selectedDevice.value?.setMediaVolume((progressValue / 1000f).toDouble(), {}, {})
    }

    fun onSeekChanged(progressValue: Int) {
        selectedDevice.value?.seekMediaTo(progressValue.toDouble(), {}, {})
    }
}