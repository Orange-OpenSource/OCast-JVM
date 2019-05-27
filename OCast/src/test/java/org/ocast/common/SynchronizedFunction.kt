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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * This class represents a function with synchronization capabilities.
 * This is the parent class for all SynchronizedFunctionN classes.
 *
 * @param function The function to synchronize on.
 */
abstract class SynchronizedFunction<R>(protected open val function: Function<R>) {

    /** The latch. */
    private val latch = CountDownLatch(1)

    /**
     * Sets the function as invoked.
     * This releases the waiting threads if waitUntilInvoked has been called before.
     */
    protected fun setInvoked() {
        latch.countDown()
    }

    /**
     * Blocks the current thread until setInvoked is called.
     */
    fun waitUntilInvoked() {
        latch.await()
    }

    /**
     * Blocks the current thread until setInvoked is called, or until the specified timeout has elapsed.
     *
     * @param timeout The maximum time to wait.
     * @param unit The time unit for the [timeout].
     */
    fun waitUntilInvoked(timeout: Long, unit: TimeUnit) {
        latch.await(timeout, unit)
    }
}

/**
 * This class represents a synchronized function with 1 parameter.
 *
 * @param function The function to synchronize on.
 */
class SynchronizedFunction1<R, S>(override val function: Function1<R, S>) : SynchronizedFunction<S>(function), Function1<R, S> {

    override fun invoke(p1: R): S {
        val returnValue = function.invoke(p1)
        setInvoked()

        return returnValue
    }
}