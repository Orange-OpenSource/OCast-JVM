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

class OCastLog {

    companion object {

        private val fqcnIgnore = listOf("java.lang.Thread", "${OCastLog::class.java.name}\$Companion", OCastLog::class.java.name)

        private val tag: String
            get() = Thread.currentThread().stackTrace.firstOrNull { it.className !in fqcnIgnore }?.className ?: ""

        @JvmStatic
        fun error(message: String, throwable: Throwable?) {
            log(Level.SEVERE, message, throwable)
        }

        @JvmStatic
        fun info(message: String) {
            log(Level.INFO, message)
        }

        @JvmStatic
        fun debug(message: String) {
            log(Level.WARNING, message)
        }

        private fun log(level: Level, message: String, throwable: Throwable? = null) {
            Logger.getLogger(tag).log(level, "$tag: $message", throwable)
        }
    }
}