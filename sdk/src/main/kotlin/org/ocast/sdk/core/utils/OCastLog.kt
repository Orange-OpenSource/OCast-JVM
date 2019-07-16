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

package org.ocast.sdk.core.utils

import java.util.logging.Logger

/**
 * This class logs OCast messages.
 */
class OCastLog {

    /**
     * The companion object.
     */
    companion object {

        /**
         * Indicates if debug is enabled.
         *
         * When `false`, this constant optimizes bytecode by removing code related to logs.
         */
        @PublishedApi
        internal const val DEBUG = false

        /**
         * The current OCast log level.
         *
         * Default value is `Level.OFF`.
         */
        @JvmStatic
        var level = Level.OFF

        /** The list of FQCNs (Fully Qualified Class Names) that should be ignored when parsing the stacktrace to get the log tag.  */
        private val fqcnIgnore = listOf(Thread::class.java.name, Companion::class.java.name, OCastLog::class.java.name)

        /** The log tag. */
        @PublishedApi
        internal val tag: String
            get() = callingStackFrame?.className.orEmpty()

        /** The name of the method which called an [OCastLog] method. */
        @PublishedApi
        internal val methodName: String
            get() = callingStackFrame?.methodName.orEmpty()

        /** The calling stack frame. */
        private val callingStackFrame: StackTraceElement?
            get() = Thread
                .currentThread()
                .stackTrace
                .firstOrNull { it.className !in fqcnIgnore }

        /**
         * Logs an error message.
         *
         * Does nothing if [level] is `Level.OFF`.
         *
         * @param throwable The throwable associated with the error message.
         * @param message A lambda that returns the message to be logged.
         */
        @JvmStatic
        inline fun error(throwable: Throwable? = null, message: () -> String) {
            log(Level.ERROR, throwable, message)
        }

        /**
         * Logs an info message.
         *
         * Does nothing if [level] is `Level.OFF` or `Level.ERROR`.
         *
         * @param message A lambda that returns the message to be logged.
         */
        @JvmStatic
        inline fun info(message: () -> String) {
            log(Level.INFO, null, message)
        }

        /**
         * Logs a debug message.
         *
         * Does nothing if [level] is `Level.OFF`, `Level.ERROR` or `Level.INFO`.
         *
         * @param message A lambda that returns the message to be logged.
         */
        @JvmStatic
        inline fun debug(message: () -> String) {
            log(Level.DEBUG, null, message)
        }

        /**
         * Logs a message.
         *
         * @param level The message level.
         * @param throwable The throwable associated with the message.
         * @param message A lambda that returns the message to be logged.
         */
        @PublishedApi
        internal inline fun log(level: Level, throwable: Throwable?, message: () -> String) {
            if (DEBUG && this.level.loggerLevel.intValue() <= level.loggerLevel.intValue()) {
                Logger.getLogger(tag).log(level.loggerLevel, "$tag: $methodName: ${message()}", throwable)
            }
        }
    }

    /**
     * Represents the OCast log level.
     *
     * @param loggerLevel The corresponding [Logger] level.
     */
    enum class Level(@PublishedApi internal val loggerLevel: java.util.logging.Level) {

        /** Indicates that the log message will not be displayed. */
        OFF(java.util.logging.Level.OFF),

        /** Indicates that the log is an error message. */
        ERROR(java.util.logging.Level.SEVERE),

        /** Indicates that the log is an informational message. */
        INFO(java.util.logging.Level.INFO),

        /** Indicates that the log is a debug message. */
        DEBUG(java.util.logging.Level.FINEST),

        /** Indicates that the log message is always displayed. */
        ALL(java.util.logging.Level.ALL);
    }
}
