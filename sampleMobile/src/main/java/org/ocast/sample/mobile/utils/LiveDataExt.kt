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

package org.ocast.sample.mobile.utils

import android.arch.lifecycle.MutableLiveData
import android.os.Looper

/**
 * Posts or sets value of a MutableLiveData according to the current thread
 */
fun <T> MutableLiveData<T>.updateValue(value: T?) {
    if (isMainThread()) {
        // Current thread is main thread
        this.value = value
    } else {
        postValue(value)
    }
}

/**
 * @return true if we are in Main Thread, false otherwise
 */
private fun isMainThread() = Looper.myLooper() == Looper.getMainLooper()
