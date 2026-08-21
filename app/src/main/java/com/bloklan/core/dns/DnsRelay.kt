package com.bloklan.core.dns

import android.net.VpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class DnsRelay(
    private val vpnService: VpnService
) {

    suspend fun forwardQuery(
        dnsQueryPayload: ByteArray,
        upstreamServerIp: String,
        timeoutMs: Int = 3000
    ): ByteArray? = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            vpnService.protect(socket)
            socket.soTimeout = timeoutMs

            val upstreamAddr = InetAddress.getByName(upstreamServerIp)
            val sendPacket = DatagramPacket(
                dnsQueryPayload,
                dnsQueryPayload.size,
                upstreamAddr,
                53
            )
            socket.send(sendPacket)

            val receiveBuffer = ByteArray(1500)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            socket.receive(receivePacket)

            val response = ByteArray(receivePacket.length)
            System.arraycopy(receiveBuffer, 0, response, 0, receivePacket.length)
            return@withContext response
        } catch (e: SocketTimeoutException) {
            return@withContext null
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            socket?.close()
        }
    }
}
