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

import org.hamcrest.CoreMatchers.everyItem
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.collection.IsIn
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Unit tests for OCast error classes.
 */
@RunWith(Enclosed::class)
class OCastErrorTest {

    @RunWith(Parameterized::class)
    class WithParameterizedStatusEnumClasses<T>(private val statusEnumClass: Class<T>) where T : Enum<T>, T : ErrorStatus {

        companion object {

            @JvmStatic
            @Parameterized.Parameters
            fun data() = listOf(
                OCastDeviceSettingsError.Status::class.java,
                OCastInputSettingsError.Status::class.java,
                OCastMediaError.Status::class.java
            )
        }

        @Test
        fun parameterizedStatusEnumClassValuesContainOCastErrorStatusEnumValues() {
            // Given
            val oCastErrorStatuses = OCastError.Status.values().map { it.code to it.name }
            val statuses = statusEnumClass.enumConstants.map { it.code to it.name }

            // When

            // Then
            assertThat(oCastErrorStatuses, everyItem(IsIn(statuses)))
        }
    }
}
