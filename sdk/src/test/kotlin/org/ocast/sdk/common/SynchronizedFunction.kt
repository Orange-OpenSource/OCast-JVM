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
import org.ocast.sdk.core.models.Consumer

/**
 * This class represents a function with synchronization capabilities.
 * This is the parent class for all SynchronizedFunctionN classes.
 *
 * @param R The return type of the function.
 * @property function The function to synchronize on.
 * @property count The number of times countDown() must be called before threads can pass through await().
 * @constructor Creates an instance of [SynchronizedFunction].
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
 * This class represents a synchronized function with no parameter.
 *
 * @param R The return type of the function.
 * @param function The function to synchronize on.
 * @param count The number of times countDown() must be called before threads can pass through await().
 * @constructor Creates an instance of [SynchronizedFunction0].
 */
open class SynchronizedFunction0<R>(override val function: Function0<R>, count: Int = 1) : SynchronizedFunction<R>(function, count), Function0<R> {

    override fun invoke(): R {
        val returnValue = function.invoke()
        countDown()

        return returnValue
    }
}

/**
 * This class represents a synchronized function with 1 parameter.
 *
 * @param R The return type of the function.
 * @param T The type of the function parameter.
 * @param function The function to synchronize on.
 * @param count The number of times countDown() must be called before threads can pass through await().
 * @constructor Creates an instance of [SynchronizedFunction1].
 */
open class SynchronizedFunction1<T, R>(override val function: Function1<T, R>, count: Int = 1) : SynchronizedFunction<R>(function, count), Function1<T, R> {

    override fun invoke(p1: T): R {
        val returnValue = function.invoke(p1)
        countDown()

        return returnValue
    }
}

/**
 * This class represents a synchronized runnable.
 *
 * @param runnable The runnable to synchronize on.
 * @param count The number of times countDown() must be called before threads can pass through await().
 * @constructor Creates an instance of [SynchronizedRunnable].
 */
class SynchronizedRunnable(runnable: Runnable, count: Int = 1) : SynchronizedFunction0<Unit>({ runnable.run() }, count), Runnable {

    override fun run() {
        invoke()
    }
}

/**
 * This class represents a synchronized consumer.
 *
 * @param T The type of the consumer parameter.
 * @param consumer The consumer to synchronize on.
 * @param count The number of times countDown() must be called before threads can pass through await()
 * @constructor Creates an instance of [SynchronizedConsumer].
 */
class SynchronizedConsumer<T>(consumer: Consumer<T>, count: Int = 1) : SynchronizedFunction1<T, Unit>({ t: T -> consumer.run(t) }, count), Consumer<T> {

    override fun run(t: T) {
        invoke(t)
    }
}
