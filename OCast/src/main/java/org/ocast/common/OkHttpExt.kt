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

package org.ocast.common

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

/**
 * Schedules the OkHttp request to be executed at some point in the future.
 * This method is a convenience method which allows to write OkHttp calls in a more Kotlin way using [Result] and lambdas.
 */
fun Call.enqueue(onComplete: (Result<Response>) -> Unit) {
    enqueue(object : Callback {

        override fun onFailure(call: Call, e: IOException) {
            onComplete(Result.failure(e))
        }

        override fun onResponse(call: Call, response: Response) {
            onComplete(Result.success(response))
        }
    })
}