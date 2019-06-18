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

package org.ocast.core.utils

import java.util.logging.Level
import java.util.logging.Logger

/**
 * This class logs OCast messages.
 */
class OCastLog {

    companion object {

        /**
         * Indicates if the OCast logs are enabled.
         * Default value is false.
         */
        @JvmStatic
        var isEnabled = false

        /** The list of FQCNs (Fully Qualified Class Names) that should be ignored when parsing the stacktrace to get the log tag.  */
        private val fqcnIgnore = listOf(Thread::class.java.name, Companion::class.java.name, OCastLog::class.java.name)

        /** The log tag. */
        private val tag: String
            get() = Thread
                .currentThread()
                .stackTrace
                .firstOrNull { it.className !in fqcnIgnore }
                ?.className
                .orEmpty()

        /**
         * Logs an error message.
         * Does nothing if [isEnabled] is false.
         *
         * @param throwable The throwable associated with the error message.
         * @param message A lambda that returns the message to be logged.
         */
        @JvmStatic
        fun error(throwable: Throwable? = null, message: () -> String) {
            log(Level.SEVERE, throwable, message)
        }

        /**
         * Logs an info message.
         * Does nothing if [isEnabled] is false.
         *
         * @param message A lambda that returns the message to be logged.
         */
        @JvmStatic
        fun info(message: () -> String) {
            log(Level.INFO, null, message)
        }

        /**
         * Logs a debug message.
         * Does nothing if [isEnabled] is false.
         *
         * @param message A lambda that returns the message to be logged.
         */
        @JvmStatic
        fun debug(message: () -> String) {
            log(Level.WARNING, null, message)
        }

        /**
         * Logs a message.
         * Does nothing if [isEnabled] is false.
         *
         * @param level The message level.
         * @param throwable The throwable associated with the message.
         * @param message A lambda that returns the message to be logged.
         */
        private fun log(level: Level, throwable: Throwable?, message: () -> String) {
            if (isEnabled) {
                Logger.getLogger(tag).log(level, "$tag: ${message()}", throwable)
            }
        }
    }
}
