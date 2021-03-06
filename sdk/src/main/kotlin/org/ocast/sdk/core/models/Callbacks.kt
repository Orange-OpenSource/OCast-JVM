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

package org.ocast.sdk.core.models

/**
 * Represents a callback for an asynchronous task, with a [Runnable] as the success operation.
 *
 * @property onSuccess The operation executed if the task succeeded.
 * @property onError The operation executed if the task failed.
 * @constructor Creates an instance of [RunnableCallback].
 */
open class RunnableCallback(val onSuccess: Runnable, val onError: Consumer<OCastError>)

/**
 * Represents a callback for an asynchronous task, with a [Consumer] of T as the success operation.
 *
 * @param T The type of the [Consumer] parameter.
 * @property onSuccess The operation executed if the task succeeded.
 * @property onError The operation executed if the task failed.
 * @constructor Creates an instance of [ConsumerCallback].
 */
open class ConsumerCallback<T : Any>(val onSuccess: Consumer<T>, val onError: Consumer<OCastError>)

/**
 * Represents a callback for an OCast reply.
 *
 * @param T The type of the expected reply data.
 * @property replyClass The expected class of the reply data.
 * @property onSuccess The operation executed if the task succeeded.
 * @property onError The operation executed if the task failed.
 * @constructor Creates an instance of [ReplyCallback].
 */
class ReplyCallback<T : Any>(val replyClass: Class<T>, onSuccess: Consumer<T>, onError: Consumer<OCastError>) : ConsumerCallback<T>(onSuccess, onError)
