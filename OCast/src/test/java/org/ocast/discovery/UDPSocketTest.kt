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

package org.ocast.discovery

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

/**
 * Unit tests for the [UDPSocket] class.
 */
class UDPSocketTest {

    /**
     * This class is used to stub the receive method of a mocked [MulticastSocket].
     *
     * @param responses The successive responses that the receive method will process. If there is no response left to process, the receive method will hang forever.
     */
    private class MulticastSocketAnswer(private vararg val responses: String) : Answer<Unit> {

        /** The number of times this answer has been called. */
        private var answerCount = 0

        override fun answer(invocation: InvocationOnMock?) {
            if (answerCount < responses.size) {
                Thread.sleep(200)
                val packet = invocation?.getArgument<DatagramPacket>(0)
                packet?.data = responses[answerCount].toByteArray()
                packet?.address = InetAddress.getByName("192.168.1.123")
                answerCount++
            } else {
                // Hang forever
                Thread.sleep(Long.MAX_VALUE)
            }
        }
    }

    /** The socket listener. */
    private val listener = mock<UDPSocket.Listener>()

    /**
     * The underlying multicast socket used by an instance of [UDPSocket].
     * Beware, this variable should be recreated if the open() method of UDPSocket is called more than once, which is not currently the case.
     */
    private val multicastSocket = spy<MulticastSocket>()

    /** The socket. */
    private val socket = object : UDPSocket() {
        override fun createSocket(port: Short) = multicastSocket
    }

    @Before
    fun setUp() {
        socket.listener = listener
    }

    @Test
    fun receiveDatagramPacketCallsListenerOnDataReceived() {
        // Given
        Mockito.doAnswer(MulticastSocketAnswer("firstData", "secondData")).whenever(multicastSocket).receive(any())

        // When
        socket.open()

        // Then
        Thread.sleep(1000)
        val response = argumentCaptor<ByteArray>()
        verify(listener, times(2)).onDataReceived(eq(socket), response.capture(), eq("192.168.1.123"))
        assertEquals(String(response.firstValue), "firstData")
        assertEquals(String(response.secondValue), "secondData")
        verify(listener, never()).onSocketClosed(any(), anyOrNull())
    }

    @Test
    fun receiveIOExceptionCallsListenerOnSocketClosedWithError() {
        // Given
        val exception = IOException()
        doThrow(exception).whenever(multicastSocket).receive(any())

        // When
        socket.open()

        // Then
        Thread.sleep(500)
        verify(listener, never()).onDataReceived(any(), any(), any())
        verify(listener, times(1)).onSocketClosed(eq(socket), eq(exception))
    }

    @Test
    fun closeSocketCallsListenerOnSocketClosedWithoutError() {
        // Given
        socket.open()

        // When
        socket.close()
        socket.close() // Also test that onSocketClosed is called only once

        // Then
        Thread.sleep(500)
        verify(listener, never()).onDataReceived(any(), any(), any())
        verify(listener, times(1)).onSocketClosed(eq(socket), isNull())
    }
}
