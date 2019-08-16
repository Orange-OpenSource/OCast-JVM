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

package org.ocast.sdk.discovery

import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketException
import kotlin.concurrent.thread
import org.ocast.sdk.common.extensions.orTrue
import org.ocast.sdk.core.utils.OCastLog

/**
 * This class represents a UDP socket.
 *
 * @property port The local port of the socket. Specify 0 to bind the socket on a random port.
 * @constructor Creates an instance of [UDPSocket].
 */
internal open class UDPSocket(private val port: Short = 0) {

    /**
     * The companion object.
     */
    companion object {

        /** The name of the thread to receive datagrams. */
        private val RECEIVER_THREAD_NAME = "${UDPSocket::class.simpleName} Receiver Thread"

        /** The size of the buffer to receive datagrams in bytes. */
        private const val RECEIVER_BUFFER_SIZE = 4096
    }

    /** The listener. */
    var listener: Listener? = null

    /** The socket. */
    private var socket: MulticastSocket? = null

    /** The thread to receive datagrams. */
    private var receiverThread: Thread? = null

    /** Indicates if the socket is closed. */
    val isClosed: Boolean
        get() = socket?.isClosed.orTrue()

    /**
     * Opens the socket.
     *
     * @throws IOException If an I/O exception occurs while opening the socket.
     */
    @Throws(IOException::class)
    fun open() {
        if (socket == null || socket?.isClosed == true) {
            socket = createSocket(port)
            OCastLog.debug { "UDP socket with port $port is opened" }
            startReceiverThread()
        }
    }

    /**
     * Closes the socket.
     */
    fun close() {
        receiverThread = null
        // Closing the socket will terminate the receiver thread
        socket?.close()
        OCastLog.debug { "UDP socket with port $port is closed" }
    }

    /**
     * Sends a payload to a given host and port.
     *
     * @param payload The data to send.
     * @param host The destination host name or address.
     * @param port The destination port.
     * @throws IOException If an I/O exception occurs while sending the payload.
     */
    @Throws(IOException::class)
    open fun send(payload: ByteArray, host: String, port: Short) {
        if (socket == null) {
            throw SocketException("Socket is not opened").also { exception ->
                OCastLog.error(exception) { "Failed to send payload on UDP socket with port $port" }
            }
        }

        val address = InetAddress.getByName(host)
        val packet = DatagramPacket(payload, payload.size, address, port.toInt())
        socket?.send(packet)
        OCastLog.debug { "Sent payload on UDP socket with port $port:\n${String(payload)}" }
    }

    /**
     * Creates and returns a socket.
     *
     * @param port The local port of the socket. Specify 0 to bind the socket on a random port.
     * @return The socket.
     */
    protected open fun createSocket(port: Short) = MulticastSocket(port.toInt())

    /**
     * Starts the receiver thread.
     */
    private fun startReceiverThread() {
        if (receiverThread == null) {
            receiverThread = thread(name = RECEIVER_THREAD_NAME) {
                // Keep a reference to the related underlying socket to check if it is closed when catching a SocketException
                val socket = socket
                try {
                    val buffer = ByteArray(RECEIVER_BUFFER_SIZE)
                    val packet = DatagramPacket(buffer, buffer.size)
                    while (true) {
                        socket?.receive(packet)
                        // Take only the needed bytes because packet is reused and old data could be appended at the end
                        val data = packet.data.take(packet.length).toByteArray()
                        OCastLog.debug { "Received data on UDP socket with port $port:\n${String(data)}" }
                        listener?.onDataReceived(this, data, packet.address.hostName)
                    }
                } catch (exception: SocketException) {
                    val error = if (socket?.isClosed == true) null else exception
                    if (error != null) {
                        OCastLog.error(exception) { "UDP socket with port $port did close" }
                    } else {
                        OCastLog.debug { "UDP socket with port $port did close successfully" }
                    }
                    listener?.onSocketClosed(this, error)
                } catch (exception: IOException) {
                    OCastLog.error(exception) { "UDP socket with port $port did close" }
                    listener?.onSocketClosed(this, exception)
                }
            }
        }
    }

    /**
     * A listener of events on a [UDPSocket].
     */
    interface Listener {

        /**
         * Tells the listener that the socket received data from a host.
         *
         * @param socket The socket which informs the listener.
         * @param data The data.
         * @param host The name of the host which sent the data.
         */
        fun onDataReceived(socket: UDPSocket, data: ByteArray, host: String)

        /**
         * Tells the listener that the socket closed.
         *
         * @param socket The socket which informs the listener.
         * @param error The error, or `null` if the socket closed normally.
         */
        fun onSocketClosed(socket: UDPSocket, error: Throwable?)
    }
}
