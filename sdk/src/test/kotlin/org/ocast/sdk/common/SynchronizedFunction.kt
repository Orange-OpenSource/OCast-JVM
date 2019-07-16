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

package org.ocast.sdk.common

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * This class represents a function with synchronization capabilities.
 * This is the parent class for all SynchronizedFunctionN classes.
 *
 * @param function The function to synchronize on.
 * @param count The number of times countDown() must be called before threads can pass through await()
 */
abstract class SynchronizedFunction<R>(protected open val function: Function<R>, private val count: Int) {

    /** The latch. */
    private val latch = CountDownLatch(count)

    /**
     * Decrements the count of the [function] invocation, releasing all waiting threads if the count reaches zero.
     */
    protected fun countDown() {
        latch.countDown()
    }

    /**
     * Causes the current thread to wait until the [function] has been invoked [count] times, unless the thread is interrupted.
     */
    fun await() {
        latch.await()
    }

    /**
     * Causes the current thread to wait until the [function] has been invoked [count] times, unless the thread is interrupted, or the specified waiting time elapses.
     *
     * @param timeout The maximum time to wait.
     * @param unit The time unit for the [timeout].
     */
    fun await(timeout: Long, unit: TimeUnit) {
        latch.await(timeout, unit)
    }
}

/**
 * This class represents a synchronized function with 1 parameter.
 *
 * @param function The function to synchronize on.
 * @param count The number of times countDown() must be called before threads can pass through await()
 */
class SynchronizedFunction1<R, S>(override val function: Function1<R, S>, count: Int = 1) : SynchronizedFunction<S>(function, count), Function1<R, S> {

    override fun invoke(p1: R): S {
        val returnValue = function.invoke(p1)
        countDown()

        return returnValue
    }
}
