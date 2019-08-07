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

package org.ocast.mediaroute.wrapper

import android.os.Handler
import android.os.Looper
import org.ocast.sdk.core.models.Consumer
import org.ocast.sdk.core.wrapper.CallbackWrapper

/**
 * This class is an implementation of [CallbackWrapper] for Android.
 *
 * It wraps instances of [Consumer] and [Runnable] to run them on the main thread.
 */
class AndroidUIThreadCallbackWrapper : CallbackWrapper {

    /** A handler on the main thread. */
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun <T> wrap(consumer: Consumer<T>): Consumer<T> {
        return Consumer { mainHandler.post { consumer.run(it) } }
    }

    override fun wrap(runnable: Runnable): Runnable {
        return Runnable { mainHandler.post(runnable) }
    }
}
