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
 * Represents a callback for an asynchronous task, with a [Runnable] as the success lambda.
 *
 * @param onSuccess The success lambda.
 * @param onError The error lambda.
 */
open class RunnableCallback(val onSuccess: Runnable, val onError: Consumer<OCastError>)

/**
 * Represents a callback for an asynchronous task, with a [Consumer] of T as the success lambda.
 *
 * @param onSuccess The success lambda.
 * @param onError The error lambda.
 */
open class ConsumerCallback<T>(val onSuccess: Consumer<T>, val onError: Consumer<OCastError>)

/**
 * Represents a callback for an OCast reply.
 *
 * @param replyClass The expected class of the reply data.
 * @param onSuccess The success lambda.
 * @param onError The error lambda.
 */
class ReplyCallback<T>(val replyClass: Class<T>, onSuccess: Consumer<T>, onError: Consumer<OCastError>) : ConsumerCallback<T>(onSuccess, onError)
