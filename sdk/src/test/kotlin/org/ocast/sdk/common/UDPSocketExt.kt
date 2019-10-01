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

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import java.util.Timer
import kotlin.concurrent.schedule
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Stubber
import org.ocast.sdk.discovery.UDPSocket

/**
 * Stubs the M-SEARCH responses received by the socket.
 * Each argument represents the M-SEARCH responses and their associated delay after an M-SEARCH request is sent by the socket.
 *
 * @param responses The M-SEARCH responses and their associated delay.
 */
internal fun UDPSocket.stubMSearchResponses(vararg responses: List<Pair<String, Long>>) {
    var stubbing: Stubber? = null
    responses.forEach { response ->
        val answer: (InvocationOnMock) -> Unit = {
            response.forEach { ssdpResponse ->
                Timer().schedule(ssdpResponse.second) {
                    listener?.onDataReceived(this@stubMSearchResponses, ssdpResponse.first.toByteArray(), "127.0.0.1")
                }
            }
        }
        // Stub the same answer twice because UDP packets are sent twice
        repeat(2) {
            stubbing = if (stubbing == null) Mockito.doAnswer(answer) else stubbing?.doAnswer(answer)
        }
    }
    stubbing = stubbing?.doAnswer {
        // Following calls Do nothing
    }
    stubbing?.whenever(this)?.send(any(), any(), any())
}
