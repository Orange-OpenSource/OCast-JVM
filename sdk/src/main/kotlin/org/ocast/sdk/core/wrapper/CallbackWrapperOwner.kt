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

package org.ocast.sdk.core.wrapper

import org.ocast.sdk.core.models.Consumer

/**
 * This interface is implemented by classes which have a [CallbackWrapper].
 */
interface CallbackWrapperOwner {

    /** The callback wrapper. */
    var callbackWrapper: CallbackWrapper

    /**
     * Wraps and invokes the lambda.
     *
     * @param T The type of the lambda parameter.
     * @param param The parameter passed to the invoked method of the wrapped lambda.
     */
    @JvmDefault
    fun <T> ((T) -> Unit).wrapInvoke(param: T) {
        callbackWrapper.wrap(Consumer<T> { invoke(it) }).run(param)
    }

    /**
     * Wraps and runs the [Consumer].
     *
     * @param T The type of the [Consumer] parameter.
     * @param param The parameter passed to the run method of the wrapped [Consumer].
     */
    @JvmDefault
    fun <T> Consumer<T>.wrapRun(param: T) {
        callbackWrapper.wrap(this).run(param)
    }

    /**
     * Wraps and runs the [Runnable].
     */
    @JvmDefault
    fun Runnable.wrapRun() {
        callbackWrapper.wrap(this).run()
    }

    /**
     * Wraps and performs the given [action] for each element in the [Iterable].
     *
     * @param T The type of the objects contained in the [Iterable].
     * @param action The lambda to wrap and perform.
     */
    @JvmDefault
    fun <T> Iterable<T>.wrapForEach(action: (T) -> Unit) {
        forEach { action.wrapInvoke(it) }
    }
}
