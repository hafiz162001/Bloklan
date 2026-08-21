package com.bloklan

import com.bloklan.core.dns.DnsPacketParser
import com.bloklan.core.dns.ParsedDnsQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DnsPacketParserTest {

    @Test
    fun testParseValidIpv4UdpDnsQuery() {
        // Construct a synthetic IPv4 UDP DNS Query packet for "googleads.g.doubleclick.net"
        val domain = "googleads.g.doubleclick.net"
        val dnsPayload = buildDnsQueryPayload(0x1234, domain, 1, 1)

        val totalLength = 20 + 8 + dnsPayload.size
        val packet = ByteArray(totalLength)
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)

        // IP Header
        buf.put(0x45.toByte())
        buf.put(0x00.toByte())
        buf.putShort(totalLength.toShort())
        buf.putShort(0x0001.toShort())
        buf.putShort(0x0000.toShort())
        buf.put(64.toByte())
        buf.put(17.toByte()) // UDP
        buf.putShort(0.toShort())
        buf.put(byteArrayOf(10, 10, 10, 2)) // Src IP
        buf.put(byteArrayOf(10, 10, 10, 1)) // Dst IP

        // UDP Header
        buf.putShort(54321.toShort()) // Src Port
        buf.putShort(53.toShort()) // Dst Port (DNS)
        buf.putShort((8 + dnsPayload.size).toShort())
        buf.putShort(0.toShort())

        // DNS Payload
        buf.put(dnsPayload)

        val parsed = DnsPacketParser.parse(packet, totalLength)
        assertNotNull(parsed)
        assertEquals(0x1234, parsed?.id)
        assertEquals(domain, parsed?.domain)
        assertEquals(1, parsed?.qType)
        assertEquals("A", parsed?.qTypeName)
    }

    @Test
    fun testBlockedResponsePacketCreation() {
        val query = ParsedDnsQuery(
            id = 0x5678,
            domain = "pagead2.googlesyndication.com",
            qType = 1, // Type A
            qClass = 1,
            srcIp = byteArrayOf(10, 10, 10, 2),
            dstIp = byteArrayOf(10, 10, 10, 1),
            srcPort = 44556,
            dstPort = 53,
            rawDnsPayload = ByteArray(12),
            ipHeaderLength = 20
        )

        val responsePacket = DnsPacketParser.createBlockedResponsePacket(query)
        assertTrue(responsePacket.size > 28)

        // Check inverted IPs
        assertEquals(10, responsePacket[12].toInt())
        assertEquals(10, responsePacket[13].toInt())
        assertEquals(10, responsePacket[14].toInt())
        assertEquals(1, responsePacket[15].toInt()) // New Src IP = 10.10.10.1

        assertEquals(10, responsePacket[16].toInt())
        assertEquals(10, responsePacket[17].toInt())
        assertEquals(10, responsePacket[18].toInt())
        assertEquals(2, responsePacket[19].toInt()) // New Dst IP = 10.10.10.2

        // Check UDP Ports
        val srcPort = ((responsePacket[20].toInt() and 0xFF) shl 8) or (responsePacket[21].toInt() and 0xFF)
        val dstPort = ((responsePacket[22].toInt() and 0xFF) shl 8) or (responsePacket[23].toInt() and 0xFF)
        assertEquals(53, srcPort)
        assertEquals(44556, dstPort)
    }

    private fun buildDnsQueryPayload(id: Int, domain: String, qType: Int, qClass: Int): ByteArray {
        val buf = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(id.toShort())
        buf.putShort(0x0100.toShort()) // Standard query, RD=1
        buf.putShort(1.toShort()) // QDCOUNT = 1
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        val labels = domain.split('.')
        for (label in labels) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            buf.put(bytes.size.toByte())
            buf.put(bytes)
        }
        buf.put(0.toByte())

        buf.putShort(qType.toShort())
        buf.putShort(qClass.toShort())

        val res = ByteArray(buf.position())
        System.arraycopy(buf.array(), 0, res, 0, buf.position())
        return res
    }
}
