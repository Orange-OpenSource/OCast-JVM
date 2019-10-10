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

import java.net.URL
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before

/**
 * An abstract class for unit tests of HTTP clients.
 */
abstract class HttpClientTest {

    /** The mocked HTTP server. */
    protected val server = MockWebServer()

    /** The base URL of the mocked HTTP server. */
    protected val baseURL: URL
        get() = server.url("/").url()

    @Before
    open fun setUp() {
    }

    @After
    open fun tearDown() {
        try {
            server.shutdown()
        } catch (exception: Exception) {
        }
    }
}
